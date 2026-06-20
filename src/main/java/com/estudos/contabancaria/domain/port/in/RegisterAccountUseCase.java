package com.estudos.contabancaria.domain.port.in;

/** Porta de entrada: registra uma conta nova (saldo zero), de forma idempotente. */
public interface RegisterAccountUseCase {

    void register(RegisterAccountCommand command);
}
