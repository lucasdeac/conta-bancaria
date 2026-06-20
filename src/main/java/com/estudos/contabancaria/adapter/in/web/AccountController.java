package com.estudos.contabancaria.adapter.in.web;

import com.estudos.contabancaria.adapter.in.web.dto.BalanceResponse;
import com.estudos.contabancaria.adapter.in.web.dto.StatementResponse;
import com.estudos.contabancaria.domain.port.in.AccountQueryUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;

@RestController
@Validated
public class AccountController {

    private final AccountQueryUseCase accountQuery;
    private final ZoneId responseZone;

    public AccountController(AccountQueryUseCase accountQuery,
                            @Value("${app.timezone:America/Sao_Paulo}") String timezone) {
        this.accountQuery = accountQuery;
        this.responseZone = ZoneId.of(timezone);
    }

    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<BalanceResponse> balance(
            @PathVariable @Pattern(regexp = WebPatterns.UUID, message = "accountId must be a UUID")
            String accountId) {
        return accountQuery.getBalance(accountId)
                .map(account -> ResponseEntity.ok(ResponseMapper.toBalance(account)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/accounts/{accountId}/statement")
    public StatementResponse statement(
            @PathVariable @Pattern(regexp = WebPatterns.UUID, message = "accountId must be a UUID")
            String accountId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) String cursor) {
        return ResponseMapper.toStatement(accountId, accountQuery.getStatement(accountId, limit, cursor), responseZone);
    }
}
