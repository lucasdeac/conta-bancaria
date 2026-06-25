package com.estudos.contabancaria.adapter.out.persistence;

import com.estudos.contabancaria.adapter.out.metrics.MicrometerBusinessMetrics;
import com.estudos.contabancaria.application.AuthorizeTransactionService;
import com.estudos.contabancaria.domain.model.Account;
import com.estudos.contabancaria.domain.model.Money;
import com.estudos.contabancaria.domain.model.TransactionType;
import com.estudos.contabancaria.domain.port.in.AuthorizeTransactionCommand;
import com.estudos.contabancaria.domain.port.out.StatementPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StatementQueryIT extends AbstractDynamoIT {

    private static final Currency BRL = Currency.getInstance("BRL");

    private DynamoAccountRepository accounts;
    private DynamoTransactionRepository transactions;
    private AuthorizeTransactionService service;

    @BeforeEach
    void init() {
        accounts = new DynamoAccountRepository(client, TABLE);
        transactions = new DynamoTransactionRepository(client, TABLE);
        MicrometerBusinessMetrics metrics =
                new MicrometerBusinessMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        service = new AuthorizeTransactionService(accounts, transactions, metrics, Clock.systemUTC());
    }

    private void authorize(String accountId, TransactionType type, String value) {
        service.authorize(new AuthorizeTransactionCommand(
                UUID.randomUUID().toString(), accountId, type, Money.of(new BigDecimal(value), "BRL")));
    }

    @Test
    void statementReturnsAccountTransactionsMostRecentFirst() {
        String accountId = UUID.randomUUID().toString();
        accounts.createIfAbsent(Account.opened(accountId, "owner", Instant.now(), BRL));

        authorize(accountId, TransactionType.CREDIT, "100.00");
        authorize(accountId, TransactionType.DEBIT, "30.00");
        authorize(accountId, TransactionType.DEBIT, "10.00");

        StatementPage page = transactions.findByAccount(accountId, 10, null);

        assertThat(page.items()).hasSize(3);
        // todas as transações pertencem à conta consultada
        assertThat(page.items()).allMatch(t -> t.accountId().equals(accountId));
    }

    @Test
    void statementPaginatesWithCursor() {
        String accountId = UUID.randomUUID().toString();
        accounts.createIfAbsent(Account.opened(accountId, "owner", Instant.now(), BRL));
        authorize(accountId, TransactionType.CREDIT, "10.00");
        authorize(accountId, TransactionType.CREDIT, "10.00");
        authorize(accountId, TransactionType.CREDIT, "10.00");

        StatementPage first = transactions.findByAccount(accountId, 2, null);
        assertThat(first.items()).hasSize(2);
        assertThat(first.nextCursor()).isNotNull();

        StatementPage second = transactions.findByAccount(accountId, 2, first.nextCursor());
        assertThat(second.items()).hasSize(1);
        assertThat(second.nextCursor()).isNull();
    }
}
