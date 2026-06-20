package com.estudos.contabancaria.domain.port.in;

import com.estudos.contabancaria.domain.model.AccountStatus;

import java.time.Instant;

/** Comando para registrar uma conta recebida da fila de abertura de contas. */
public record RegisterAccountCommand(
        String id,
        String owner,
        Instant createdAt,
        AccountStatus status) {
}
