package com.estudos.contabancaria.adapter.out.persistence;

import com.estudos.contabancaria.domain.model.FailureReason;
import com.estudos.contabancaria.domain.model.Money;
import com.estudos.contabancaria.domain.model.Transaction;
import com.estudos.contabancaria.domain.model.TransactionStatus;
import com.estudos.contabancaria.domain.model.TransactionType;
import com.estudos.contabancaria.domain.port.out.StatementPage;
import com.estudos.contabancaria.domain.port.out.TransactionRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.estudos.contabancaria.adapter.out.persistence.DynamoAttributes.numberValue;
import static com.estudos.contabancaria.adapter.out.persistence.DynamoAttributes.readLong;
import static com.estudos.contabancaria.adapter.out.persistence.DynamoAttributes.readString;
import static com.estudos.contabancaria.adapter.out.persistence.DynamoAttributes.stringValue;

/**
 * Adaptador de persistência de transações em DynamoDB.
 *
 * <p>O {@code claim} (PutItem com {@code attribute_not_exists(PK)}) garante a idempotência:
 * reenvios/duplicatas não criam transação nova. O item também alimenta o GSI1 (extrato).
 */
@Repository
public class DynamoTransactionRepository implements TransactionRepository {

    private final DynamoDbClient client;
    private final String tableName;

    public DynamoTransactionRepository(DynamoDbClient client,
                                       @Value("${app.dynamodb.table-name:ledger}") String tableName) {
        this.client = client;
        this.tableName = tableName;
    }

    private static Map<String, AttributeValue> key(String transactionId) {
        return Map.of("PK", stringValue("TXN#" + transactionId), "SK", stringValue("#META"));
    }

    @Override
    @Retry(name = "dynamodb")
    @CircuitBreaker(name = "dynamodb")
    public boolean claim(Transaction transaction) {
        String timestamp = transaction.timestamp().toString();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", stringValue("TXN#" + transaction.id()));
        item.put("SK", stringValue("#META"));
        item.put("transactionId", stringValue(transaction.id()));
        item.put("accountId", stringValue(transaction.accountId()));
        item.put("type", stringValue(transaction.type().name()));
        item.put("amountMinor", numberValue(transaction.amount().toMinor()));
        item.put("currency", stringValue(transaction.amount().currency().getCurrencyCode()));
        item.put("status", stringValue(transaction.status().name()));
        item.put("timestamp", stringValue(timestamp));
        // GSI1 — extrato por conta, ordenado por tempo
        item.put("GSI1PK", stringValue("ACC#" + transaction.accountId()));
        item.put("GSI1SK", stringValue(timestamp + "#TXN#" + transaction.id()));
        try {
            client.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .conditionExpression("attribute_not_exists(PK)")
                    .build());
            return true;
        } catch (ConditionalCheckFailedException duplicate) {
            return false; // já processada → caminho de replay idempotente
        }
    }

    @Override
    @Retry(name = "dynamodb")
    @CircuitBreaker(name = "dynamodb")
    public Optional<Transaction> findById(String transactionId) {
        Map<String, AttributeValue> item = client.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(key(transactionId))
                .consistentRead(true)
                .build()).item();
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toTransaction(item));
    }

    @Override
    @Retry(name = "dynamodb")
    @CircuitBreaker(name = "dynamodb")
    public void resolve(Transaction transaction) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":st", stringValue(transaction.status().name()));
        StringBuilder update = new StringBuilder("SET #st = :st");

        if (transaction.status() == TransactionStatus.SUCCEEDED && transaction.resultingBalance() != null) {
            update.append(", resultingBalanceMinor = :rb");
            values.put(":rb", numberValue(transaction.resultingBalance().toMinor()));
        }
        if (transaction.status() == TransactionStatus.FAILED && transaction.failureReason() != null) {
            update.append(", failureReason = :fr");
            values.put(":fr", stringValue(transaction.failureReason().name()));
        }

        client.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key(transaction.id()))
                .updateExpression(update.toString())
                .conditionExpression("attribute_exists(PK)")
                .expressionAttributeNames(Map.of("#st", "status"))
                .expressionAttributeValues(values)
                .build());
    }

    @Override
    @Retry(name = "dynamodb")
    @CircuitBreaker(name = "dynamodb")
    public StatementPage findByAccount(String accountId, int limit, String cursor) {
        QueryRequest.Builder query = QueryRequest.builder()
                .tableName(tableName)
                .indexName("GSI1")
                .keyConditionExpression("GSI1PK = :pk")
                .expressionAttributeValues(Map.of(":pk", stringValue("ACC#" + accountId)))
                .scanIndexForward(false) // mais recentes primeiro
                .limit(limit);

        Map<String, AttributeValue> startKey = decodeCursor(cursor);
        if (startKey != null) {
            query.exclusiveStartKey(startKey);
        }

        QueryResponse response = client.query(query.build());
        List<Transaction> items = response.items().stream()
                .map(DynamoTransactionRepository::toTransaction)
                .toList();

        Map<String, AttributeValue> lastKey = response.lastEvaluatedKey();
        String nextCursor = (lastKey == null || lastKey.isEmpty()) ? null : encodeCursor(lastKey);
        return new StatementPage(items, nextCursor);
    }

    /** Codifica a LastEvaluatedKey (atributos string) num token opaco base64. */
    private static String encodeCursor(Map<String, AttributeValue> lastKey) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, AttributeValue> entry : lastKey.entrySet()) {
            sb.append(entry.getKey()).append('\t').append(entry.getValue().s()).append('\n');
        }
        return Base64.getUrlEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, AttributeValue> decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        Map<String, AttributeValue> key = new HashMap<>();
        for (String line : decoded.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            int tab = line.indexOf('\t');
            key.put(line.substring(0, tab), stringValue(line.substring(tab + 1)));
        }
        return key;
    }

    private static Transaction toTransaction(Map<String, AttributeValue> item) {
        String currency = readString(item, "currency");
        Money amount = Money.ofMinor(readLong(item, "amountMinor"), currency);
        String failure = readString(item, "failureReason");
        Money resultingBalance = item.containsKey("resultingBalanceMinor")
                ? Money.ofMinor(readLong(item, "resultingBalanceMinor"), currency)
                : null;
        return new Transaction(
                readString(item, "transactionId"),
                readString(item, "accountId"),
                TransactionType.valueOf(readString(item, "type")),
                amount,
                TransactionStatus.valueOf(readString(item, "status")),
                failure == null ? null : FailureReason.valueOf(failure),
                resultingBalance,
                Instant.parse(readString(item, "timestamp")));
    }
}
