package com.estudos.contabancaria.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private static final Currency BRL = Currency.getInstance("BRL");
    private static final Currency JPY = Currency.getInstance("JPY");

    @Test
    void normalizesScaleToCurrencyFractionDigits() {
        Money money = Money.of(new BigDecimal("10"), "BRL");
        assertThat(money.value()).isEqualByComparingTo("10.00");
        assertThat(money.toMinor()).isEqualTo(1000L);
    }

    @Test
    void convertsToAndFromMinorUnits() {
        assertThat(Money.ofMinor(18312, "BRL").value()).isEqualByComparingTo("183.12");
        assertThat(Money.of(new BigDecimal("97.07"), "BRL").toMinor()).isEqualTo(9707L);
    }

    @Test
    void handlesCurrencyWithZeroFractionDigits() {
        Money money = Money.ofMinor(500, "JPY");
        assertThat(money.value()).isEqualByComparingTo("500");
        assertThat(Money.of(new BigDecimal("500"), "JPY").toMinor()).isEqualTo(500L);
    }

    @Test
    void zeroHasCorrectScale() {
        assertThat(Money.zero(BRL).toMinor()).isZero();
        assertThat(Money.zero(BRL).value()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsNegativeValue() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("-1.00"), "BRL"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTooManyDecimalsForCurrency() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("10.005"), "BRL"))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void sameCurrencyComparison() {
        assertThat(Money.zero(BRL).sameCurrency(Money.of(new BigDecimal("1.00"), "BRL"))).isTrue();
        assertThat(Money.zero(BRL).sameCurrency(Money.ofMinor(100, "JPY"))).isFalse();
    }
}
