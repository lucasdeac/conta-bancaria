package com.estudos.contabancaria.adapter.out.persistence;

import com.estudos.contabancaria.domain.model.Account;
import com.estudos.contabancaria.domain.model.AccountStatus;
import com.estudos.contabancaria.domain.model.Money;
import com.estudos.contabancaria.domain.port.out.BalanceMutationResult;
import com.estudos.contabancaria.domain.port.out.BalanceMutationResult.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class DynamoAccountRepositoryIT extends AbstractDynamoIT {

    private static final Currency BRL = Currency.getInstance("BRL");

    private DynamoAccountRepository accounts;

    @BeforeEach
    void init() {
        accounts = new DynamoAccountRepository(client, TABLE);
    }

    private String openAccount(AccountStatus status) {
        String id = UUID.randomUUID().toString();
        accounts.createIfAbsent(new Account(id, "owner-" + id, Money.zero(BRL), status, Instant.now(), 0L));
        return id;
    }

    @Test
    void createIfAbsentIsIdempotent() {
        String id = UUID.randomUUID().toString();
        Account account = Account.opened(id, "owner", Instant.now(), BRL);

        assertThat(accounts.createIfAbsent(account)).isTrue();
        assertThat(accounts.createIfAbsent(account)).isFalse(); // segunda vez não recria/zera
        assertThat(accounts.findById(id)).get()
                .extracting(a -> a.balance().toMinor())
                .isEqualTo(0L);
    }

    @Test
    void creditThenDebitUpdatesBalance() {
        String id = openAccount(AccountStatus.ENABLED);

        assertThat(accounts.applyCredit(id, 10000, "BRL").resultingBalance().toMinor()).isEqualTo(10000L);
        assertThat(accounts.applyDebit(id, 3000, "BRL").resultingBalance().toMinor()).isEqualTo(7000L);
    }

    @Test
    void debitWithInsufficientBalanceKeepsBalanceUnchanged() {
        String id = openAccount(AccountStatus.ENABLED);
        accounts.applyCredit(id, 5000, "BRL");

        BalanceMutationResult result = accounts.applyDebit(id, 9000, "BRL");

        assertThat(result.outcome()).isEqualTo(Outcome.INSUFFICIENT_BALANCE);
        assertThat(accounts.findById(id).orElseThrow().balance().toMinor()).isEqualTo(5000L); // intacto
    }

    @Test
    void currencyMismatchIsRejected() {
        String id = openAccount(AccountStatus.ENABLED);
        assertThat(accounts.applyCredit(id, 1000, "USD").outcome()).isEqualTo(Outcome.CURRENCY_MISMATCH);
    }

    @Test
    void disabledAccountIsRejected() {
        String id = openAccount(AccountStatus.DISABLED);
        assertThat(accounts.applyCredit(id, 1000, "BRL").outcome()).isEqualTo(Outcome.ACCOUNT_DISABLED);
    }

    @Test
    void unknownAccountIsNotFound() {
        assertThat(accounts.applyDebit(UUID.randomUUID().toString(), 100, "BRL").outcome())
                .isEqualTo(Outcome.ACCOUNT_NOT_FOUND);
    }

    @Test
    void concurrentDebitsNeverGoNegative() throws Exception {
        String id = openAccount(AccountStatus.ENABLED);
        accounts.applyCredit(id, 10000, "BRL"); // saldo = 100,00 (10000 minor)

        int threads = 20;          // 20 débitos de 10,00 = potencial 200,00 > saldo
        long debitMinor = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(1);

        List<Callable<BalanceMutationResult>> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                ready.await();
                return accounts.applyDebit(id, debitMinor, "BRL");
            });
        }

        List<Future<BalanceMutationResult>> futures = new java.util.ArrayList<>();
        for (Callable<BalanceMutationResult> task : tasks) {
            futures.add(pool.submit(task));
        }
        ready.countDown(); // dispara todos ~simultaneamente

        long applied = 0;
        for (Future<BalanceMutationResult> f : futures) {
            if (f.get(30, TimeUnit.SECONDS).isApplied()) {
                applied++;
            }
        }
        pool.shutdown();

        // Exatamente 10 débitos cabem no saldo; os outros 10 são recusados.
        assertThat(applied).isEqualTo(10L);
        assertThat(accounts.findById(id).orElseThrow().balance().toMinor()).isZero();
    }
}
