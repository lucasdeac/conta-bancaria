package com.estudos.contabancaria.adapter.in.web.dto;

import com.estudos.contabancaria.domain.model.FailureReason;
import com.estudos.contabancaria.domain.model.TransactionStatus;
import com.estudos.contabancaria.domain.model.TransactionType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/** Resposta da autorização (transação + conta), no formato do enunciado. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthorizationResponse(
        TransactionView transaction,
        AccountView account) {

    public record TransactionView(
            String id,
            TransactionType type,
            MoneyView amount,
            TransactionStatus status,
            FailureReason failureReason,
            String timestamp) {
    }

    public record AccountView(
            String id,
            MoneyView balance) {
    }

    public record MoneyView(
            BigDecimal value,
            String currency) {
    }
}
