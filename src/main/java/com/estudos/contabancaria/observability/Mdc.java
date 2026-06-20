package com.estudos.contabancaria.observability;

/** Chaves de MDC usadas no logging estruturado. */
public final class Mdc {

    private Mdc() {
    }

    /** Identificador da unidade de trabalho (requisição HTTP ou mensagem) propagado nos logs. */
    public static final String REQUEST_ID = "requestId";
}
