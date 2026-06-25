package com.estudos.contabancaria.domain.port.out;

import com.estudos.contabancaria.domain.model.Transaction;

import java.util.List;

/**
 * Página do extrato. {@code nextCursor} é um token opaco para a próxima página
 * ({@code null} quando não há mais resultados).
 */
public record StatementPage(List<Transaction> items, String nextCursor) {
}
