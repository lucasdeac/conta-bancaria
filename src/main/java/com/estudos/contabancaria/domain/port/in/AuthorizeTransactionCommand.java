package com.estudos.contabancaria.domain.port.in;

import com.estudos.contabancaria.domain.model.Money;
import com.estudos.contabancaria.domain.model.TransactionType;

/** Comando para autorizar uma transação. O {@code transactionId} é a chave de idempotência. */
public record AuthorizeTransactionCommand(
        String transactionId,
        String accountId,
        TransactionType type,
        Money amount) {
}
