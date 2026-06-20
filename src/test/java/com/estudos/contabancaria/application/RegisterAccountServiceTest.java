package com.estudos.contabancaria.application;

import com.estudos.contabancaria.domain.model.Account;
import com.estudos.contabancaria.domain.model.AccountStatus;
import com.estudos.contabancaria.domain.port.in.RegisterAccountCommand;
import com.estudos.contabancaria.domain.port.out.AccountRepository;
import com.estudos.contabancaria.domain.port.out.BusinessMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterAccountServiceTest {

    @Mock
    AccountRepository accountRepository;
    @Mock
    BusinessMetrics businessMetrics;

    @Test
    void createsAccountWithZeroBalanceInDefaultCurrency() {
        RegisterAccountService service = new RegisterAccountService(accountRepository, businessMetrics, "BRL");
        when(accountRepository.createIfAbsent(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        Instant createdAt = Instant.ofEpochSecond(1634874339L);
        service.register(new RegisterAccountCommand("acc-1", "owner-1", createdAt, AccountStatus.ENABLED));

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        org.mockito.Mockito.verify(accountRepository).createIfAbsent(captor.capture());
        Account saved = captor.getValue();

        assertThat(saved.id()).isEqualTo("acc-1");
        assertThat(saved.owner()).isEqualTo("owner-1");
        assertThat(saved.status()).isEqualTo(AccountStatus.ENABLED);
        assertThat(saved.createdAt()).isEqualTo(createdAt);
        assertThat(saved.version()).isZero();
        assertThat(saved.balance().toMinor()).isZero();
        assertThat(saved.balance().currency().getCurrencyCode()).isEqualTo("BRL");
    }

    @Test
    void isIdempotentWhenAccountAlreadyExists() {
        RegisterAccountService service = new RegisterAccountService(accountRepository, businessMetrics, "BRL");
        when(accountRepository.createIfAbsent(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        // não deve lançar — duplicata é caminho normal
        service.register(new RegisterAccountCommand("acc-1", "owner-1",
                Instant.ofEpochSecond(1634874339L), AccountStatus.ENABLED));
    }
}
