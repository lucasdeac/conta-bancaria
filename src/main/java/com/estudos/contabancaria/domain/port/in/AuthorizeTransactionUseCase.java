package com.estudos.contabancaria.domain.port.in;

/** Porta de entrada: autoriza uma transação (CREDIT/DEBIT) de forma idempotente. */
public interface AuthorizeTransactionUseCase {

    AuthorizationResult authorize(AuthorizeTransactionCommand command);
}
