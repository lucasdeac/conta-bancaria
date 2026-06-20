package com.estudos.contabancaria.adapter.in.web;

import com.estudos.contabancaria.observability.Mdc;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Garante um requestId por requisição no MDC (e no header de resposta).
 *
 * <p>O valor pode vir do cliente, mas é validado por <b>allow-list</b> antes de entrar no log
 * (mitiga log forging via CRLF); se inválido/ausente, geramos um UUID.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        MDC.put(Mdc.REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(Mdc.REQUEST_ID);
        }
    }

    /** Aceita o valor do cliente apenas se passar na allow-list; senão gera um novo. */
    static String resolve(String headerValue) {
        if (headerValue != null && SAFE.matcher(headerValue).matches()) {
            return headerValue;
        }
        return UUID.randomUUID().toString();
    }
}
