package com.estudos.contabancaria.adapter.out.health;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqsHealthIndicatorTest {

    @Mock
    SqsAsyncClient client;

    @Test
    void upWhenQueueResolves() {
        when(client.getQueueUrl(any(GetQueueUrlRequest.class))).thenReturn(
                CompletableFuture.completedFuture(GetQueueUrlResponse.builder()
                        .queueUrl("http://localhost:4566/000000000000/conta-bancaria-criada").build()));

        Health health = new SqsHealthIndicator(client, "conta-bancaria-criada").health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void downWhenQueueLookupFails() {
        CompletableFuture<GetQueueUrlResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("queue not found"));
        when(client.getQueueUrl(any(GetQueueUrlRequest.class))).thenReturn(failed);

        Health health = new SqsHealthIndicator(client, "conta-bancaria-criada").health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }
}
