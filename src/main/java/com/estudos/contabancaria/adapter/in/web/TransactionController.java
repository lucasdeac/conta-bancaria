package com.estudos.contabancaria.adapter.in.web;

import com.estudos.contabancaria.adapter.in.web.dto.AuthorizationResponse;
import com.estudos.contabancaria.adapter.in.web.dto.TransactionRequest;
import com.estudos.contabancaria.domain.model.Money;
import com.estudos.contabancaria.domain.model.TransactionStatus;
import com.estudos.contabancaria.domain.port.in.AuthorizationResult;
import com.estudos.contabancaria.domain.port.in.AuthorizeTransactionCommand;
import com.estudos.contabancaria.domain.port.in.AuthorizeTransactionUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;

@RestController
@Validated
public class TransactionController {

    private final AuthorizeTransactionUseCase authorizeTransaction;
    private final ZoneId responseZone;

    public TransactionController(AuthorizeTransactionUseCase authorizeTransaction,
                                 @Value("${app.timezone:America/Sao_Paulo}") String timezone) {
        this.authorizeTransaction = authorizeTransaction;
        this.responseZone = ZoneId.of(timezone);
    }

    @PostMapping("/transactions/{transactionId}")
    public ResponseEntity<AuthorizationResponse> authorize(
            @PathVariable
            @Pattern(regexp = WebPatterns.UUID, message = "transactionId must be a UUID") String transactionId,
            @Valid @RequestBody TransactionRequest request) {

        // Constrói o Money do domínio aqui — escala/moeda inválidas viram 400 antes de qualquer escrita.
        Money amount = Money.of(request.amount().value(), request.amount().currency());

        AuthorizeTransactionCommand command = new AuthorizeTransactionCommand(
                transactionId, request.accountId(), request.type(), amount);

        AuthorizationResult result = authorizeTransaction.authorize(command);
        AuthorizationResponse body = ResponseMapper.toResponse(result, responseZone);

        HttpStatus status = result.transaction().status() == TransactionStatus.SUCCEEDED
                ? HttpStatus.OK
                : HttpStatus.UNPROCESSABLE_ENTITY;

        return ResponseEntity.status(status).body(body);
    }
}
