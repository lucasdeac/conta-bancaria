package com.estudos.contabancaria.adapter.in.web.dto;

import com.estudos.contabancaria.adapter.in.web.dto.AuthorizationResponse.TransactionView;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Resposta do extrato (página). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatementResponse(
        String accountId,
        List<TransactionView> items,
        String nextCursor) {
}
