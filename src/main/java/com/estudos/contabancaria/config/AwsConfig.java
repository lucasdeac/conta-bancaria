package com.estudos.contabancaria.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

/**
 * Configuração do cliente DynamoDB.
 *
 * <p>Local (endpoint sobrescrito): usa credenciais estáticas de env var, com default "test"
 * do LocalStack/DynamoDB Local — <b>não são segredos reais</b>.
 * <p>Produção (sem endpoint): usa {@link DefaultCredentialsProvider}, que resolve a IAM role
 * da task — <b>sem credenciais hardcoded</b>.
 */
@Configuration
public class AwsConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(
            @Value("${app.dynamodb.endpoint:}") String endpoint,
            @Value("${spring.cloud.aws.region.static:sa-east-1}") String region,
            @Value("${AWS_ACCESS_KEY_ID:test}") String accessKey,
            @Value("${AWS_SECRET_ACCESS_KEY:test}") String secretKey) {

        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region));

        if (StringUtils.hasText(endpoint)) {
            // Modo local: endpoint do DynamoDB Local + credenciais dummy.
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)));
        } else {
            // Modo produção: IAM role via cadeia de credenciais padrão.
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }
}
