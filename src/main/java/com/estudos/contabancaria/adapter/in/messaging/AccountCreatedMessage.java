package com.estudos.contabancaria.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload da fila {@code conta-bancaria-criada}.
 * Ex.: {@code {"account":{"id":"...","owner":"...","created_at":"1634874339","status":"ENABLED"}}}
 * <p>{@code created_at} chega como string com epoch em segundos.
 */
public record AccountCreatedMessage(@JsonProperty("account") AccountPayload account) {

    public record AccountPayload(
            @JsonProperty("id") String id,
            @JsonProperty("owner") String owner,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("status") String status) {
    }
}
