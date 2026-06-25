package com.estudos.contabancaria.adapter.out.health;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamoDbHealthIndicatorTest {

    @Mock
    DynamoDbClient client;

    @Test
    void upWhenTableIsReachable() {
        when(client.describeTable(any(DescribeTableRequest.class))).thenReturn(
                DescribeTableResponse.builder()
                        .table(TableDescription.builder().tableName("ledger").tableStatus(TableStatus.ACTIVE).build())
                        .build());

        Health health = new DynamoDbHealthIndicator(client, "ledger").health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", "ACTIVE");
    }

    @Test
    void downWhenClientThrows() {
        when(client.describeTable(any(DescribeTableRequest.class))).thenThrow(new RuntimeException("boom"));

        Health health = new DynamoDbHealthIndicator(client, "ledger").health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        // não vaza a mensagem interna, só o tipo do erro
        assertThat(health.getDetails().get("error")).isEqualTo("RuntimeException");
    }
}
