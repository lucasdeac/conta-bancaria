package com.estudos.contabancaria.application;

import com.estudos.contabancaria.domain.model.Account;
import com.estudos.contabancaria.domain.model.AccountStatus;
import com.estudos.contabancaria.domain.model.Money;
import com.estudos.contabancaria.domain.port.out.AccountRepository;
import com.estudos.contabancaria.domain.port.out.StatementPage;
import com.estudos.contabancaria.domain.port.out.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountQueryServiceTest {

    private static final Currency BRL = Currency.getInstance("BRL");

    @Mock
    AccountRepository accountRepository;
    @Mock
    TransactionRepository transactionRepository;

    @Test
    void getBalanceReturnsAccountWhenPresent() {
        AccountQueryService service = new AccountQueryService(accountRepository, transactionRepository);
        Account account = new Account("acc", "owner", Money.ofMinor(18312, "BRL"),
                AccountStatus.ENABLED, Instant.now(), 1L);
        when(accountRepository.findById("acc")).thenReturn(Optional.of(account));

        assertThat(service.getBalance("acc")).get()
                .extracting(a -> a.balance().toMinor())
                .isEqualTo(18312L);
    }

    @Test
    void getBalanceEmptyWhenAbsent() {
        AccountQueryService service = new AccountQueryService(accountRepository, transactionRepository);
        when(accountRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(service.getBalance("missing")).isEmpty();
    }

    @Test
    void getStatementDelegatesToRepository() {
        AccountQueryService service = new AccountQueryService(accountRepository, transactionRepository);
        StatementPage page = new StatementPage(List.of(), "cursor-123");
        when(transactionRepository.findByAccount("acc", 50, null)).thenReturn(page);

        StatementPage result = service.getStatement("acc", 50, null);

        assertThat(result.nextCursor()).isEqualTo("cursor-123");
    }
}
