package com.estudos.contabancaria.adapter.out.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Base para testes de integração contra um DynamoDB real (LocalStack via Testcontainers).
 * Usa container singleton compartilhado entre as classes — sobe uma vez por JVM.
 */
public abstract class AbstractDynamoIT {

    protected static final String TABLE = "ledger";

    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.7.2"))
            .withServices(LocalStackContainer.Service.DYNAMODB);

    protected static DynamoDbClient client;

    @BeforeAll
    static void startInfra() {
        if (!LOCALSTACK.isRunning()) {
            LOCALSTACK.start();
        }
        if (client == null) {
            client = DynamoDbClient.builder()
                    .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
                    .region(Region.of(LOCALSTACK.getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                    .build();
            createTable();
        }
    }

    private static void createTable() {
        try {
            client.createTable(CreateTableRequest.builder()
                    .tableName(TABLE)
                    .attributeDefinitions(
                            ad("PK"), ad("SK"), ad("GSI1PK"), ad("GSI1SK"))
                    .keySchema(
                            ks("PK", KeyType.HASH), ks("SK", KeyType.RANGE))
                    .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                            .indexName("GSI1")
                            .keySchema(ks("GSI1PK", KeyType.HASH), ks("GSI1SK", KeyType.RANGE))
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .build())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
            client.waiter().waitUntilTableExists(b -> b.tableName(TABLE));
        } catch (ResourceInUseException alreadyExists) {
            // tabela já criada por outra classe de teste
        }
    }

    private static AttributeDefinition ad(String name) {
        return AttributeDefinition.builder().attributeName(name).attributeType(ScalarAttributeType.S).build();
    }

    private static KeySchemaElement ks(String name, KeyType type) {
        return KeySchemaElement.builder().attributeName(name).keyType(type).build();
    }
}
