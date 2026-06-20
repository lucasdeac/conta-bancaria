package com.estudos.contabancaria.adapter.in.web.dto;

import com.estudos.contabancaria.adapter.in.web.dto.AuthorizationResponse.MoneyView;

/** Resposta de consulta de saldo. */
public record BalanceResponse(String id, MoneyView balance) {
}
