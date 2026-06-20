package com.estudos.contabancaria.application;

import com.estudos.contabancaria.domain.model.Account;
import com.estudos.contabancaria.domain.model.FailureReason;
import com.estudos.contabancaria.domain.model.Money;
import com.estudos.contabancaria.domain.model.Transaction;
import com.estudos.contabancaria.domain.model.TransactionStatus;
import com.estudos.contabancaria.domain.model.TransactionType;
import com.estudos.contabancaria.domain.port.in.AuthorizationResult;
import com.estudos.contabancaria.domain.port.in.AuthorizeTransactionCommand;
import com.estudos.contabancaria.domain.port.in.AuthorizeTransactionUseCase;
import com.estudos.contabancaria.domain.port.out.AccountRepository;
import com.estudos.contabancaria.domain.port.out.BalanceMutationResult;
import com.estudos.contabancaria.domain.port.out.BusinessMetrics;
import com.estudos.contabancaria.domain.port.out.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Orquestra a autorização: claim idempotente -> mutação atômica de saldo -> resolução.
 * Reenvios de uma transação já processada retornam o resultado salvo (replay).
 */
@Service
public class AuthorizeTransactionService implements AuthorizeTransactionUseCase {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BusinessMetrics businessMetrics;
    private final Clock clock;

    public AuthorizeTransactionService(AccountRepository accountRepository,
                                       TransactionRepository transactionRepository,
                                       BusinessMetrics businessMetrics,
                                       Clock clock) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.businessMetrics = businessMetrics;
        this.clock = clock;
    }

    @Override
    public AuthorizationResult authorize(AuthorizeTransactionCommand command) {
        Instant now = Instant.now(clock);
        Transaction pending = Transaction.pending(
                command.transactionId(), command.accountId(), command.type(), command.amount(), now);

        boolean claimed = transactionRepository.claim(pending);
        if (!claimed) {
            Transaction existing = transactionRepository.findById(command.transactionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "claim conflict but transaction not found: " + command.transactionId()));
            return replay(existing);
        }

        long amountMinor = command.amount().toMinor();
        String currencyCode = command.amount().currency().getCurrencyCode();

        BalanceMutationResult mutation = command.type() == TransactionType.CREDIT
                ? accountRepository.applyCredit(command.accountId(), amountMinor, currencyCode)
                : accountRepository.applyDebit(command.accountId(), amountMinor, currencyCode);

        Transaction resolved = mutation.isApplied()
                ? pending.succeededWith(mutation.resultingBalance())
                : pending.failedWith(toReason(mutation.outcome()));

        transactionRepository.resolve(resolved);
        // Métrica registrada só no caminho de decisão real (replays não passam por aqui).
        businessMetrics.authorizationResolved(resolved);
        return new AuthorizationResult(resolved, command.accountId(), mutation.resultingBalance());
    }

    private AuthorizationResult replay(Transaction existing) {
        if (existing.status() == TransactionStatus.PENDING) {
            throw new TransactionInProgressException(existing.id());
        }
        Money balance = existing.resultingBalance();
        if (balance == null) {
            // FAILED não persiste saldo; busca o saldo atual (se a conta existir).
            balance = accountRepository.findById(existing.accountId())
                    .map(Account::balance)
                    .orElse(null);
        }
        return new AuthorizationResult(existing, existing.accountId(), balance);
    }

    private static FailureReason toReason(BalanceMutationResult.Outcome outcome) {
        return switch (outcome) {
            case INSUFFICIENT_BALANCE -> FailureReason.INSUFFICIENT_BALANCE;
            case ACCOUNT_DISABLED -> FailureReason.ACCOUNT_DISABLED;
            case ACCOUNT_NOT_FOUND -> FailureReason.ACCOUNT_NOT_FOUND;
            case CURRENCY_MISMATCH -> FailureReason.CURRENCY_MISMATCH;
            case APPLIED -> throw new IllegalArgumentException("APPLIED is not a failure outcome");
        };
    }
}
