package com.estudos.contabancaria.adapter.out.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

/**
 * Readiness do DynamoDB: verifica conectividade + existência da tabela via describeTable
 * (operação leve). Não expõe mensagens internas de exceção — apenas o tipo do erro.
 */
@Component("dynamoDb")
public class DynamoDbHealthIndicator implements HealthIndicator {

    private final DynamoDbClient client;
    private final String tableName;

    public DynamoDbHealthIndicator(DynamoDbClient client,
                                   @Value("${app.dynamodb.table-name:ledger}") String tableName) {
        this.client = client;
        this.tableName = tableName;
    }

    @Override
    public Health health() {
        try {
            TableDescription table = client.describeTable(
                    DescribeTableRequest.builder().tableName(tableName).build()).table();
            return Health.up()
                    .withDetail("table", tableName)
                    .withDetail("status", table.tableStatusAsString())
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("table", tableName)
                    .withDetail("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}
