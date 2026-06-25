package com.estudos.contabancaria.domain.port.out;

import com.estudos.contabancaria.domain.model.Transaction;

/**
 * Porta de saída para métricas de negócio. O domínio/aplicação reporta eventos de negócio;
 * o adaptador (Micrometer) os traduz em métricas. Mantém a aplicação livre de framework.
 */
public interface BusinessMetrics {

    /** Registra a decisão de uma autorização já resolvida (SUCCEEDED/FAILED). */
    void authorizationResolved(Transaction transaction);

    /** Registra o registro de uma conta (criada vs. duplicata idempotente). */
    void accountRegistered(boolean created);
}
