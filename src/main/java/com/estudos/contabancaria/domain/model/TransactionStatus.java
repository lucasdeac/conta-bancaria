package com.estudos.contabancaria.domain.model;

public enum TransactionStatus {
    /** Claim de idempotência criado, ainda não resolvido. */
    PENDING,
    SUCCEEDED,
    FAILED
}
