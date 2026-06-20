package com.estudos.contabancaria.application;

import com.estudos.contabancaria.domain.model.Account;
import com.estudos.contabancaria.domain.port.in.RegisterAccountCommand;
import com.estudos.contabancaria.domain.port.in.RegisterAccountUseCase;
import com.estudos.contabancaria.domain.port.out.AccountRepository;
import com.estudos.contabancaria.domain.port.out.BusinessMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Currency;

@Slf4j
@Service
public class RegisterAccountService implements RegisterAccountUseCase {

    private final AccountRepository accountRepository;
    private final BusinessMetrics businessMetrics;
    private final Currency defaultCurrency;

    public RegisterAccountService(AccountRepository accountRepository,
                                  BusinessMetrics businessMetrics,
                                  @Value("${app.account.default-currency:BRL}") String defaultCurrency) {
        this.accountRepository = accountRepository;
        this.businessMetrics = businessMetrics;
        this.defaultCurrency = Currency.getInstance(defaultCurrency);
    }

    @Override
    public void register(RegisterAccountCommand command) {
        Account account = new Account(
                command.id(),
                command.owner(),
                com.estudos.contabancaria.domain.model.Money.zero(defaultCurrency),
                command.status(),
                command.createdAt(),
                0L);

        boolean created = accountRepository.createIfAbsent(account);
        businessMetrics.accountRegistered(created);
        if (created) {
            // Não logamos o owner (PII) — apenas o id da conta.
            log.info("account registered accountId={}", command.id());
        } else {
            log.debug("account already exists, skipping accountId={}", command.id());
        }
    }
}
