package com.estudos.contabancaria.domain.port.out;

import com.estudos.contabancaria.domain.model.Transaction;

import java.util.Optional;

/**
 * Porta de saída para persistência de transações. O {@code transactionId} é a chave
 * de idempotência.
 */
public interface TransactionRepository {

    /**
     * Reserva (claim) a transação em estado PENDING apenas se ainda não existir.
     * @return true se reservou; false se já existia (retry/duplicata).
     */
    boolean claim(Transaction transaction);

    Optional<Transaction> findById(String transactionId);

    /** Resolve a transação para SUCCEEDED ou FAILED. */
    void resolve(Transaction transaction);

    /** Extrato de uma conta, mais recentes primeiro, paginado por cursor opaco. */
    StatementPage findByAccount(String accountId, int limit, String cursor);
}
