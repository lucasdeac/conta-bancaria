package com.estudos.contabancaria.domain.port.in;

import com.estudos.contabancaria.domain.model.Account;
import com.estudos.contabancaria.domain.port.out.StatementPage;

import java.util.Optional;

/** Porta de entrada para consultas: saldo e extrato. */
public interface AccountQueryUseCase {

    /** Saldo atual da conta. {@code empty} se a conta não existe. */
    Optional<Account> getBalance(String accountId);

    /** Extrato paginado (mais recentes primeiro). */
    StatementPage getStatement(String accountId, int limit, String cursor);
}
