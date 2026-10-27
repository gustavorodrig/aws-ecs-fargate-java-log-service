package com.gustavorodrig.log.config.local;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.BillingMode;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ResourceInUseException;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
@EnableDynamoDBRepositories(basePackages = "com.gustavorodrig.log.repository")
public class DynamoDBConfigLocal {

    @Value("${aws.local.endpoint:http://localhost:4566}")
    private String localEndpoint;

    private static final Logger LOG = LoggerFactory.getLogger(DynamoDBConfigLocal.class);
    private static final String TABLE_NAME = "product-events";

    @Bean(destroyMethod = "shutdown")
    @Primary
    public AmazonDynamoDB amazonDynamoDB() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        localEndpoint, Regions.SA_EAST_1.getName()))
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();

        try {
            CreateTableRequest request = new CreateTableRequest()
                    .withTableName(TABLE_NAME)
                    .withKeySchema(
                            new KeySchemaElement("pk", KeyType.HASH),
                            new KeySchemaElement("sk", KeyType.RANGE))
                    .withAttributeDefinitions(
                            new AttributeDefinition("pk", ScalarAttributeType.S),
                            new AttributeDefinition("sk", ScalarAttributeType.S))
                    .withBillingMode(BillingMode.PAY_PER_REQUEST);

            try {
                client.createTable(request);
                LOG.info("Created local DynamoDB table {}", TABLE_NAME);
            } catch (ResourceInUseException e) {
                LOG.info("Local DynamoDB table {} already exists", TABLE_NAME);
            }

            new DynamoDB(client).getTable(TABLE_NAME).waitForActive();
            return client;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            client.shutdown();
            throw new IllegalStateException("Interrupted while waiting for local DynamoDB table", e);
        } catch (RuntimeException e) {
            client.shutdown();
            throw e;
        }
    }

    @Bean
    @Primary
    public DynamoDBMapperConfig dynamoDBMapperConfig() {
        return DynamoDBMapperConfig.DEFAULT;
    }

    @Bean
    @Primary
    public DynamoDBMapper dynamoDBMapper(AmazonDynamoDB amazonDynamoDB,
                                        DynamoDBMapperConfig config) {
        return new DynamoDBMapper(amazonDynamoDB, config);
    }
}
