package com.estudos.contabancaria.adapter.in.web;

import com.estudos.contabancaria.application.TransactionInProgressException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Tratamento global de erros. Retorna mensagens genéricas ao cliente — <b>sem stack trace
 * nem detalhes internos</b>. O detalhe completo é logado apenas no servidor.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorResponse(String code, String message) {
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            IllegalArgumentException.class, ArithmeticException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(Exception ex) {
        log.debug("validation failed: {}", ex.getMessage());
        return new ErrorResponse("VALIDATION_ERROR", "Invalid request.");
    }

    @ExceptionHandler(TransactionInProgressException.class)
    public ResponseEntity<ErrorResponse> handleInProgress(TransactionInProgressException ex) {
        log.warn("{}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("TRANSACTION_IN_PROGRESS",
                        "Transaction is still being processed. Retry shortly."));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleUnexpected(Exception ex) {
        // Log completo no servidor; nada de interno vaza para o cliente.
        log.error("unexpected error", ex);
        return new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred.");
    }
}
