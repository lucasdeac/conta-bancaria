package com.estudos.contabancaria.domain.port.out;

import com.estudos.contabancaria.domain.model.Money;

/**
 * Resultado de uma tentativa de mutação atômica de saldo (crédito/débito).
 * Expresso em termos de domínio — o adaptador traduz a falha da ConditionExpression
 * (via ALL_OLD) para um destes desfechos, sem leitura adicional.
 *
 * <p>Em desfechos de falha onde a conta existe, {@code resultingBalance} carrega o saldo
 * atual (inalterado); em {@code ACCOUNT_NOT_FOUND} é {@code null}.
 */
public record BalanceMutationResult(Outcome outcome, Money resultingBalance) {

    public enum Outcome {
        APPLIED,
        INSUFFICIENT_BALANCE,
        ACCOUNT_DISABLED,
        ACCOUNT_NOT_FOUND,
        CURRENCY_MISMATCH
    }

    public static BalanceMutationResult applied(Money resultingBalance) {
        return new BalanceMutationResult(Outcome.APPLIED, resultingBalance);
    }

    public static BalanceMutationResult failed(Outcome outcome) {
        return new BalanceMutationResult(outcome, null);
    }

    public static BalanceMutationResult failed(Outcome outcome, Money currentBalance) {
        return new BalanceMutationResult(outcome, currentBalance);
    }

    public boolean isApplied() {
        return outcome == Outcome.APPLIED;
    }
}
