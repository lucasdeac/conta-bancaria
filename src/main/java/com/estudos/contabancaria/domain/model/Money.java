package com.estudos.contabancaria.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Value Object monetário. Usa {@link BigDecimal} — nunca double/float — e centraliza a
 * conversão de/para "minor units" (centavos), unidade de armazenamento no DynamoDB.
 *
 * <p>A escala é definida pela moeda (ISO 4217), ex.: BRL/USD = 2 casas, JPY = 0 casas.
 */
public record Money(BigDecimal value, Currency currency) {

    public Money {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(currency, "currency");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("monetary value must not be negative");
        }
        value = value.setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY);
    }

    public static Money of(BigDecimal value, String currencyCode) {
        return new Money(value, Currency.getInstance(currencyCode));
    }

    /** Constrói a partir de minor units (ex.: 18312 + BRL -> R$ 183,12). */
    public static Money ofMinor(long minor, String currencyCode) {
        Currency c = Currency.getInstance(currencyCode);
        BigDecimal v = BigDecimal.valueOf(minor, c.getDefaultFractionDigits());
        return new Money(v, c);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    /** Converte para minor units (centavos) para persistência/comparação atômica. */
    public long toMinor() {
        return value.movePointRight(currency.getDefaultFractionDigits()).longValueExact();
    }

    public boolean sameCurrency(Money other) {
        return currency.equals(other.currency);
    }
}
