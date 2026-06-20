package com.estudos.contabancaria.domain.model;

import java.time.Instant;

/**
 * Transação de autorização. Identificada pelo {@code id} (vem da URL) — chave de idempotência.
 * Ciclo: PENDING (claim) -> SUCCEEDED | FAILED.
 */
public record Transaction(
        String id,
        String accountId,
        TransactionType type,
        Money amount,
        TransactionStatus status,
        FailureReason failureReason,
        Money resultingBalance,
        Instant timestamp) {

    /** Cria o claim inicial (PENDING) para idempotência. */
    public static Transaction pending(String id, String accountId, TransactionType type,
                                      Money amount, Instant timestamp) {
        return new Transaction(id, accountId, type, amount, TransactionStatus.PENDING,
                null, null, timestamp);
    }

    public Transaction succeededWith(Money resultingBalance) {
        return new Transaction(id, accountId, type, amount, TransactionStatus.SUCCEEDED,
                null, resultingBalance, timestamp);
    }

    public Transaction failedWith(FailureReason reason) {
        return new Transaction(id, accountId, type, amount, TransactionStatus.FAILED,
                reason, null, timestamp);
    }
}
