package com.estudos.contabancaria.adapter.out.metrics;

import com.estudos.contabancaria.domain.model.Transaction;
import com.estudos.contabancaria.domain.port.out.BusinessMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Adaptador de métricas de negócio com Micrometer (exposto via /actuator/prometheus).
 *
 * <p>Usa apenas tags de <b>baixa cardinalidade e não-sensíveis</b> (type, status, reason, result).
 * Identificadores como accountId/transactionId NUNCA viram tag — vazariam dados e explodiriam
 * a cardinalidade.
 */
@Component
public class MicrometerBusinessMetrics implements BusinessMetrics {

    private final MeterRegistry registry;

    public MicrometerBusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void authorizationResolved(Transaction transaction) {
        String reason = transaction.failureReason() == null ? "none" : transaction.failureReason().name();
        registry.counter("authorizations.total",
                "type", transaction.type().name(),
                "status", transaction.status().name(),
                "reason", reason).increment();
    }

    @Override
    public void accountRegistered(boolean created) {
        registry.counter("accounts.registered.total",
                "result", created ? "created" : "duplicate").increment();
    }
}
