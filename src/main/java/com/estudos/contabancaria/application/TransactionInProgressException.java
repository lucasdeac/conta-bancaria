package com.estudos.contabancaria.application;

/**
 * Lançada quando um reenvio chega para uma transação ainda em PENDING (claim feito, mas não
 * resolvido — provável crash no meio do processamento). Caminho de reconciliação.
 */
public class TransactionInProgressException extends RuntimeException {

    public TransactionInProgressException(String transactionId) {
        super("transaction still in progress: " + transactionId);
    }
}
