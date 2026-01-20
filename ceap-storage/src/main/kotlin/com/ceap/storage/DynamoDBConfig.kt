package com.ceap.storage

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.net.URI

/**
 * Configuration for DynamoDB client.
 * 
 * Provides factory methods for creating DynamoDB clients for different environments.
 */
object DynamoDBConfig {
    
    /**
     * Creates a DynamoDB client for AWS.
     * 
     * @param region AWS region (default: us-east-1)
     * @return Configured DynamoDB client
     */
    fun createClient(region: String = "us-east-1"): DynamoDbClient {
        return DynamoDbClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()
    }
    
    /**
     * Creates a DynamoDB client for local testing.
     * 
     * @param endpoint Local DynamoDB endpoint (default: http://localhost:8000)
     * @return Configured DynamoDB client for local testing
     */
    fun createLocalClient(endpoint: String = "http://localhost:8000"): DynamoDbClient {
        return DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()
    }
    
    /**
     * Table name constants.
     */
    object TableNames {
        const val CANDIDATES = "solicitation-candidates"
        const val PROGRAM_CONFIG = "solicitation-program-config"
        const val SCORE_CACHE = "solicitation-score-cache"
    }
    
    /**
     * GSI name constants.
     */
    object IndexNames {
        const val PROGRAM_CHANNEL_INDEX = "ProgramChannelIndex"
        const val PROGRAM_DATE_INDEX = "ProgramDateIndex"
    }
}
