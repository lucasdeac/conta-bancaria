package com.estudos.contabancaria.adapter.in.web.dto;

import com.estudos.contabancaria.domain.model.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Corpo do POST /transactions/{transactionId}. Validação allow-list no controller.
 */
public record TransactionRequest(

        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "accountId must be a UUID")
        String accountId,

        @NotNull(message = "type is required")
        TransactionType type,

        @NotNull(message = "amount is required")
        @Valid
        MoneyDto amount) {

    public record MoneyDto(

            @NotNull(message = "amount.value is required")
            @Positive(message = "amount.value must be positive")
            BigDecimal value,

            @NotBlank
            @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be an ISO 4217 code")
            String currency) {
    }
}
