package com.estudos.contabancaria.adapter.in.messaging;

import com.estudos.contabancaria.domain.model.AccountStatus;
import com.estudos.contabancaria.domain.port.in.RegisterAccountCommand;
import com.estudos.contabancaria.domain.port.in.RegisterAccountUseCase;
import com.estudos.contabancaria.observability.Mdc;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Adaptador de entrada: consome mensagens de abertura de conta do SQS.
 *
 * <p>Idempotência é garantida pela porta (createIfAbsent). Exceções não tratadas fazem a
 * mensagem retornar à fila e, após {@code maxReceiveCount}, ir para a DLQ.
 */
@Slf4j
@Component
public class SqsAccountListener {

    private final RegisterAccountUseCase registerAccount;

    public SqsAccountListener(RegisterAccountUseCase registerAccount) {
        this.registerAccount = registerAccount;
    }

    @SqsListener("${app.sqs.account-created-queue}")
    public void onAccountCreated(AccountCreatedMessage message) {
        MDC.put(Mdc.REQUEST_ID, UUID.randomUUID().toString());
        try {
            AccountCreatedMessage.AccountPayload account = message.account();
            if (account == null || account.id() == null) {
                throw new IllegalArgumentException("invalid account message: missing account/id");
            }

            Instant createdAt = Instant.ofEpochSecond(Long.parseLong(account.createdAt()));
            AccountStatus status = parseStatus(account.status());

            registerAccount.register(new RegisterAccountCommand(
                    account.id(), account.owner(), createdAt, status));
        } finally {
            MDC.remove(Mdc.REQUEST_ID);
        }
    }

    /** Status desconhecido é tratado defensivamente como DISABLED. */
    private static AccountStatus parseStatus(String raw) {
        try {
            return AccountStatus.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("unknown account status '{}', defaulting to DISABLED", raw);
            return AccountStatus.DISABLED;
        }
    }
}
