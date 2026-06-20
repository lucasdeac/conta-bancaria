package com.estudos.contabancaria.application;

import com.estudos.contabancaria.domain.model.Account;
import com.estudos.contabancaria.domain.model.AccountStatus;
import com.estudos.contabancaria.domain.model.FailureReason;
import com.estudos.contabancaria.domain.model.Money;
import com.estudos.contabancaria.domain.model.Transaction;
import com.estudos.contabancaria.domain.model.TransactionStatus;
import com.estudos.contabancaria.domain.model.TransactionType;
import com.estudos.contabancaria.domain.port.in.AuthorizationResult;
import com.estudos.contabancaria.domain.port.in.AuthorizeTransactionCommand;
import com.estudos.contabancaria.domain.port.out.AccountRepository;
import com.estudos.contabancaria.domain.port.out.BalanceMutationResult;
import com.estudos.contabancaria.domain.port.out.BusinessMetrics;
import com.estudos.contabancaria.domain.port.out.BalanceMutationResult.Outcome;
import com.estudos.contabancaria.domain.port.out.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizeTransactionServiceTest {

    private static final String TXN = "8e8ae808-b154-48b5-9f3e-553935cc4543";
    private static final String ACC = "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-07-08T18:57:55Z"), ZoneOffset.UTC);

    @Mock
    AccountRepository accountRepository;
    @Mock
    TransactionRepository transactionRepository;
    @Mock
    BusinessMetrics businessMetrics;

    AuthorizeTransactionService service;

    @BeforeEach
    void setUp() {
        service = new AuthorizeTransactionService(accountRepository, transactionRepository, businessMetrics, CLOCK);
    }

    private AuthorizeTransactionCommand command(TransactionType type, String value, String currency) {
        return new AuthorizeTransactionCommand(TXN, ACC, type, Money.of(new BigDecimal(value), currency));
    }

    @Test
    void creditIsApprovedAndResolvedAsSucceeded() {
        when(transactionRepository.claim(any())).thenReturn(true);
        when(accountRepository.applyCredit(eq(ACC), eq(9707L), eq("BRL")))
                .thenReturn(BalanceMutationResult.applied(Money.ofMinor(18312, "BRL")));

        AuthorizationResult result = service.authorize(command(TransactionType.CREDIT, "97.07", "BRL"));

        assertThat(result.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(result.accountBalance().toMinor()).isEqualTo(18312L);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).resolve(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(TransactionStatus.SUCCEEDED);
    }

    @Test
    void debitWithInsufficientBalanceIsFailedAndBalanceUnchanged() {
        when(transactionRepository.claim(any())).thenReturn(true);
        when(accountRepository.applyDebit(eq(ACC), eq(10000L), eq("BRL")))
                .thenReturn(BalanceMutationResult.failed(Outcome.INSUFFICIENT_BALANCE, Money.ofMinor(5000, "BRL")));

        AuthorizationResult result = service.authorize(command(TransactionType.DEBIT, "100.00", "BRL"));

        assertThat(result.transaction().status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(result.transaction().failureReason()).isEqualTo(FailureReason.INSUFFICIENT_BALANCE);
        assertThat(result.accountBalance().toMinor()).isEqualTo(5000L); // saldo intacto
    }

    @Test
    void debitOnDisabledAccountIsFailed() {
        when(transactionRepository.claim(any())).thenReturn(true);
        when(accountRepository.applyDebit(any(), anyLongMatcher(), any()))
                .thenReturn(BalanceMutationResult.failed(Outcome.ACCOUNT_DISABLED, Money.ofMinor(1000, "BRL")));

        AuthorizationResult result = service.authorize(command(TransactionType.DEBIT, "10.00", "BRL"));

        assertThat(result.transaction().failureReason()).isEqualTo(FailureReason.ACCOUNT_DISABLED);
    }

    @Test
    void currencyMismatchIsFailed() {
        when(transactionRepository.claim(any())).thenReturn(true);
        when(accountRepository.applyCredit(any(), anyLongMatcher(), eq("USD")))
                .thenReturn(BalanceMutationResult.failed(Outcome.CURRENCY_MISMATCH, Money.ofMinor(1000, "BRL")));

        AuthorizationResult result = service.authorize(command(TransactionType.CREDIT, "10.00", "USD"));

        assertThat(result.transaction().failureReason()).isEqualTo(FailureReason.CURRENCY_MISMATCH);
    }

    @Test
    void accountNotFoundIsFailed() {
        when(transactionRepository.claim(any())).thenReturn(true);
        when(accountRepository.applyDebit(any(), anyLongMatcher(), any()))
                .thenReturn(BalanceMutationResult.failed(Outcome.ACCOUNT_NOT_FOUND));

        AuthorizationResult result = service.authorize(command(TransactionType.DEBIT, "10.00", "BRL"));

        assertThat(result.transaction().status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(result.transaction().failureReason()).isEqualTo(FailureReason.ACCOUNT_NOT_FOUND);
    }

    @Test
    void replaysExistingSucceededTransactionWithoutTouchingBalance() {
        Transaction existing = Transaction.pending(TXN, ACC, TransactionType.CREDIT,
                        Money.of(new BigDecimal("97.07"), "BRL"), Instant.now(CLOCK))
                .succeededWith(Money.ofMinor(18312, "BRL"));
        when(transactionRepository.claim(any())).thenReturn(false);
        when(transactionRepository.findById(TXN)).thenReturn(Optional.of(existing));

        AuthorizationResult result = service.authorize(command(TransactionType.CREDIT, "97.07", "BRL"));

        assertThat(result.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(result.accountBalance().toMinor()).isEqualTo(18312L);
        verify(accountRepository, never()).applyCredit(any(), anyLongMatcher(), any());
        verify(transactionRepository, never()).resolve(any());
    }

    @Test
    void replayOfPendingTransactionThrowsInProgress() {
        Transaction pending = Transaction.pending(TXN, ACC, TransactionType.DEBIT,
                Money.of(new BigDecimal("10.00"), "BRL"), Instant.now(CLOCK));
        when(transactionRepository.claim(any())).thenReturn(false);
        when(transactionRepository.findById(TXN)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.authorize(command(TransactionType.DEBIT, "10.00", "BRL")))
                .isInstanceOf(TransactionInProgressException.class);
    }

    @Test
    void replayOfFailedTransactionFetchesCurrentBalance() {
        Transaction existing = Transaction.pending(TXN, ACC, TransactionType.DEBIT,
                        Money.of(new BigDecimal("100.00"), "BRL"), Instant.now(CLOCK))
                .failedWith(FailureReason.INSUFFICIENT_BALANCE);
        when(transactionRepository.claim(any())).thenReturn(false);
        when(transactionRepository.findById(TXN)).thenReturn(Optional.of(existing));
        Account account = new Account(ACC, "owner", Money.ofMinor(5000, "BRL"),
                AccountStatus.ENABLED, Instant.now(CLOCK), 3L);
        when(accountRepository.findById(ACC)).thenReturn(Optional.of(account));

        AuthorizationResult result = service.authorize(command(TransactionType.DEBIT, "100.00", "BRL"));

        assertThat(result.transaction().status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(result.accountBalance().toMinor()).isEqualTo(5000L);
    }

    // Helper para legibilidade dos matchers de long
    private static long anyLongMatcher() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
