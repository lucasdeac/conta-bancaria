package com.estudos.contabancaria.adapter.out.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;

import java.util.concurrent.TimeUnit;

/**
 * Readiness do SQS: resolve a URL da fila (operação leve), com timeout curto.
 * Não expõe mensagens internas de exceção — apenas o tipo do erro.
 */
@Component("sqs")
public class SqsHealthIndicator implements HealthIndicator {

    private final SqsAsyncClient client;
    private final String queueName;

    public SqsHealthIndicator(SqsAsyncClient client,
                              @Value("${app.sqs.account-created-queue:conta-bancaria-criada}") String queueName) {
        this.client = client;
        this.queueName = queueName;
    }

    @Override
    public Health health() {
        try {
            client.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build())
                    .get(2, TimeUnit.SECONDS);
            return Health.up().withDetail("queue", queueName).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down().withDetail("queue", queueName).withDetail("error", "interrupted").build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("queue", queueName)
                    .withDetail("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}
