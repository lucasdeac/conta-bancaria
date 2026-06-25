package com.estudos.contabancaria.domain.port.out;

import com.estudos.contabancaria.domain.model.Account;

import java.util.Optional;

/**
 * Porta de saída para persistência de contas. Implementada por um adaptador
 * (ex.: DynamoDB) na camada de infraestrutura.
 */
public interface AccountRepository {

    /**
     * Cria a conta apenas se ainda não existir (idempotente).
     * @return true se criou; false se já existia.
     */
    boolean createIfAbsent(Account account);

    Optional<Account> findById(String accountId);

    /**
     * Crédito atômico: soma o valor ao saldo, exigindo conta existente, habilitada e
     * na mesma moeda.
     */
    BalanceMutationResult applyCredit(String accountId, long amountMinor, String currencyCode);

    /**
     * Débito atômico: subtrai o valor, exigindo conta existente, habilitada, mesma moeda e
     * saldo suficiente. Saldo insuficiente NÃO altera o saldo.
     */
    BalanceMutationResult applyDebit(String accountId, long amountMinor, String currencyCode);
}
