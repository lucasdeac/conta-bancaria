package com.estudos.contabancaria.domain.model;

import java.time.Instant;
import java.util.Currency;

/**
 * Conta bancária. Saldo inicial é ZERO na abertura. A moeda é definida na criação
 * (premissa P9 — conta single-currency, padrão configurável).
 */
public record Account(
        String id,
        String owner,
        Money balance,
        AccountStatus status,
        Instant createdAt,
        long version) {

    /** Abre uma nova conta com saldo zero e status ENABLED. */
    public static Account opened(String id, String owner, Instant createdAt, Currency currency) {
        return new Account(id, owner, Money.zero(currency), AccountStatus.ENABLED, createdAt, 0L);
    }

    public boolean isEnabled() {
        return status == AccountStatus.ENABLED;
    }
}
