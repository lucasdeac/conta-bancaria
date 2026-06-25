package com.estudos.contabancaria.adapter.out.persistence;

import com.estudos.contabancaria.domain.model.Account;
import com.estudos.contabancaria.domain.model.AccountStatus;
import com.estudos.contabancaria.domain.model.Money;
import com.estudos.contabancaria.domain.port.out.AccountRepository;
import com.estudos.contabancaria.domain.port.out.BalanceMutationResult;
import com.estudos.contabancaria.domain.port.out.BalanceMutationResult.Outcome;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.estudos.contabancaria.adapter.out.persistence.DynamoAttributes.numberValue;
import static com.estudos.contabancaria.adapter.out.persistence.DynamoAttributes.readLong;
import static com.estudos.contabancaria.adapter.out.persistence.DynamoAttributes.readString;
import static com.estudos.contabancaria.adapter.out.persistence.DynamoAttributes.stringValue;

/**
 * Adaptador de persistência de contas em DynamoDB.
 *
 * <p>O coração é o <b>update condicional atômico</b>: a ConditionExpression valida existência,
 * status e saldo na mesma operação de escrita — sem lock e sem race. Em caso de falha,
 * {@code ReturnValuesOnConditionCheckFailure=ALL_OLD} devolve o item para classificar o motivo
 * sem uma leitura extra.
 */
@Repository
public class DynamoAccountRepository implements AccountRepository {

    private final DynamoDbClient client;
    private final String tableName;

    public DynamoAccountRepository(DynamoDbClient client,
                                   @Value("${app.dynamodb.table-name:ledger}") String tableName) {
        this.client = client;
        this.tableName = tableName;
    }

    private static Map<String, AttributeValue> key(String accountId) {
        return Map.of("PK", stringValue("ACC#" + accountId), "SK", stringValue("#META"));
    }

    @Override
    @Retry(name = "dynamodb")
    @CircuitBreaker(name = "dynamodb")
    public boolean createIfAbsent(Account account) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", stringValue("ACC#" + account.id()));
        item.put("SK", stringValue("#META"));
        item.put("accountId", stringValue(account.id()));
        item.put("owner", stringValue(account.owner()));
        item.put("balanceMinor", numberValue(account.balance().toMinor()));
        item.put("currency", stringValue(account.balance().currency().getCurrencyCode()));
        item.put("status", stringValue(account.status().name()));
        item.put("createdAt", stringValue(account.createdAt().toString()));
        item.put("version", numberValue(account.version()));
        try {
            client.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .conditionExpression("attribute_not_exists(PK)")
                    .build());
            return true;
        } catch (ConditionalCheckFailedException alreadyExists) {
            return false; // idempotente: redelivery do SQS não recria/zera o saldo
        }
    }

    @Override
    @Retry(name = "dynamodb")
    @CircuitBreaker(name = "dynamodb")
    public Optional<Account> findById(String accountId) {
        Map<String, AttributeValue> item = client.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(key(accountId))
                .consistentRead(true)
                .build()).item();
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toAccount(item));
    }

    @Override
    @Retry(name = "dynamodb")
    @CircuitBreaker(name = "dynamodb")
    public BalanceMutationResult applyCredit(String accountId, long amountMinor, String currencyCode) {
        return mutate(accountId, amountMinor, currencyCode, true);
    }

    @Override
    @Retry(name = "dynamodb")
    @CircuitBreaker(name = "dynamodb")
    public BalanceMutationResult applyDebit(String accountId, long amountMinor, String currencyCode) {
        return mutate(accountId, amountMinor, currencyCode, false);
    }

    private BalanceMutationResult mutate(String accountId, long amountMinor, String currencyCode, boolean credit) {
        String operator = credit ? "+" : "-";

        Map<String, AttributeValue> values = Map.of(
                ":amount", numberValue(amountMinor),
                ":increment", numberValue(1),
                ":enabled", stringValue(AccountStatus.ENABLED.name()),
                ":currency", stringValue(currencyCode));

        // Soma (crédito) ou subtrai (débito) o valor e incrementa a versão.
        String updateExpression =
                "SET balanceMinor = balanceMinor " + operator + " :amount, version = version + :increment";

        // Conta deve existir, estar habilitada e na mesma moeda; débito exige ainda saldo suficiente.
        String condition = "attribute_exists(PK) AND #status = :enabled AND currency = :currency"
                + (credit ? "" : " AND balanceMinor >= :amount");

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key(accountId))
                .updateExpression(updateExpression)
                .conditionExpression(condition)
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(values)
                .returnValues(ReturnValue.ALL_NEW)
                .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
                .build();

        try {
            UpdateItemResponse response = client.updateItem(request);
            Map<String, AttributeValue> updated = response.attributes();
            Money balance = Money.ofMinor(readLong(updated, "balanceMinor"), readString(updated, "currency"));
            return BalanceMutationResult.applied(balance);
        } catch (ConditionalCheckFailedException e) {
            return classifyFailure(e.item(), currencyCode, credit);
        }
    }

    private static BalanceMutationResult classifyFailure(Map<String, AttributeValue> old,
                                                         String currencyCode, boolean credit) {
        if (old == null || old.isEmpty()) {
            return BalanceMutationResult.failed(Outcome.ACCOUNT_NOT_FOUND);
        }
        Money currentBalance = Money.ofMinor(readLong(old, "balanceMinor"), readString(old, "currency"));
        if (!AccountStatus.ENABLED.name().equals(readString(old, "status"))) {
            return BalanceMutationResult.failed(Outcome.ACCOUNT_DISABLED, currentBalance);
        }
        if (!currencyCode.equals(readString(old, "currency"))) {
            return BalanceMutationResult.failed(Outcome.CURRENCY_MISMATCH, currentBalance);
        }
        // Crédito só falha por inexistência/status/moeda; débito também por saldo insuficiente.
        return credit
                ? BalanceMutationResult.failed(Outcome.ACCOUNT_NOT_FOUND)
                : BalanceMutationResult.failed(Outcome.INSUFFICIENT_BALANCE, currentBalance);
    }

    private static Account toAccount(Map<String, AttributeValue> item) {
        Money balance = Money.ofMinor(readLong(item, "balanceMinor"), readString(item, "currency"));
        return new Account(
                readString(item, "accountId"),
                readString(item, "owner"),
                balance,
                AccountStatus.valueOf(readString(item, "status")),
                Instant.parse(readString(item, "createdAt")),
                readLong(item, "version"));
    }
}
