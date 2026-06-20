package com.estudos.contabancaria.domain.port.in;

import com.estudos.contabancaria.domain.model.Money;
import com.estudos.contabancaria.domain.model.Transaction;

/**
 * Resultado de uma autorização: a transação resolvida e o saldo da conta a ser exposto.
 * {@code accountBalance} pode ser {@code null} quando a conta não existe.
 */
public record AuthorizationResult(
        Transaction transaction,
        String accountId,
        Money accountBalance) {
}
