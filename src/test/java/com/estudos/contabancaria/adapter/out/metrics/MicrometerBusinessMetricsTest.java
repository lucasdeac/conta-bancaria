package com.estudos.contabancaria.adapter.out.metrics;

import com.estudos.contabancaria.domain.model.FailureReason;
import com.estudos.contabancaria.domain.model.Money;
import com.estudos.contabancaria.domain.model.Transaction;
import com.estudos.contabancaria.domain.model.TransactionType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerBusinessMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerBusinessMetrics metrics = new MicrometerBusinessMetrics(registry);

    private static Transaction tx(TransactionType type) {
        return Transaction.pending("txn", "acc", type, Money.of(new BigDecimal("10.00"), "BRL"), Instant.now());
    }

    @Test
    void countsApprovedAuthorizationWithTags() {
        metrics.authorizationResolved(tx(TransactionType.CREDIT).succeededWith(Money.ofMinor(1000, "BRL")));

        double count = registry.get("authorizations.total")
                .tags("type", "CREDIT", "status", "SUCCEEDED", "reason", "none")
                .counter().count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void countsRejectedAuthorizationWithReasonTag() {
        metrics.authorizationResolved(tx(TransactionType.DEBIT).failedWith(FailureReason.INSUFFICIENT_BALANCE));

        double count = registry.get("authorizations.total")
                .tags("type", "DEBIT", "status", "FAILED", "reason", "INSUFFICIENT_BALANCE")
                .counter().count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void countsAccountRegistrationByResult() {
        metrics.accountRegistered(true);
        metrics.accountRegistered(false);

        assertThat(registry.get("accounts.registered.total").tag("result", "created").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("accounts.registered.total").tag("result", "duplicate").counter().count()).isEqualTo(1.0);
    }
}
