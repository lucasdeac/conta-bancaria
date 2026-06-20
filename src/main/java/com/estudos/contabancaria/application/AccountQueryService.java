package com.estudos.contabancaria.application;

import com.estudos.contabancaria.domain.model.Account;
import com.estudos.contabancaria.domain.port.in.AccountQueryUseCase;
import com.estudos.contabancaria.domain.port.out.AccountRepository;
import com.estudos.contabancaria.domain.port.out.StatementPage;
import com.estudos.contabancaria.domain.port.out.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AccountQueryService implements AccountQueryUseCase {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountQueryService(AccountRepository accountRepository,
                               TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public Optional<Account> getBalance(String accountId) {
        return accountRepository.findById(accountId);
    }

    @Override
    public StatementPage getStatement(String accountId, int limit, String cursor) {
        return transactionRepository.findByAccount(accountId, limit, cursor);
    }
}
