package com.estudos.contabancaria.adapter.out.persistence;

import com.estudos.contabancaria.adapter.out.metrics.MicrometerBusinessMetrics;
import com.estudos.contabancaria.application.AuthorizeTransactionService;
import com.estudos.contabancaria.domain.model.Account;
import com.estudos.contabancaria.domain.model.Money;
import com.estudos.contabancaria.domain.model.TransactionStatus;
import com.estudos.contabancaria.domain.model.TransactionType;
import com.estudos.contabancaria.domain.port.in.AuthorizationResult;
import com.estudos.contabancaria.domain.port.in.AuthorizeTransactionCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idempotência ponta a ponta no nível de aplicação, contra DynamoDB real:
 * um POST duplicado (mesmo transactionId) faz replay sem aplicar o valor duas vezes.
 */
class AuthorizationIdempotencyIT extends AbstractDynamoIT {

    private static final Currency BRL = Currency.getInstance("BRL");

    private DynamoAccountRepository accounts;
    private AuthorizeTransactionService service;

    @BeforeEach
    void init() {
        accounts = new DynamoAccountRepository(client, TABLE);
        DynamoTransactionRepository transactions = new DynamoTransactionRepository(client, TABLE);
        MicrometerBusinessMetrics metrics =
                new MicrometerBusinessMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        service = new AuthorizeTransactionService(accounts, transactions, metrics, Clock.systemUTC());
    }

    @Test
    void duplicateTransactionIsAppliedOnlyOnce() {
        String accountId = UUID.randomUUID().toString();
        accounts.createIfAbsent(Account.opened(accountId, "owner", Instant.now(), BRL));

        String txnId = UUID.randomUUID().toString();
        AuthorizeTransactionCommand command = new AuthorizeTransactionCommand(
                txnId, accountId, TransactionType.CREDIT, Money.of(new BigDecimal("50.00"), "BRL"));

        AuthorizationResult first = service.authorize(command);
        AuthorizationResult replay = service.authorize(command); // mesmo txnId

        assertThat(first.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(replay.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);

        // saldo aplicado UMA vez (50,00), não dobrado (100,00)
        assertThat(accounts.findById(accountId).orElseThrow().balance().toMinor()).isEqualTo(5000L);
        assertThat(replay.accountBalance().toMinor()).isEqualTo(5000L);
    }
}
