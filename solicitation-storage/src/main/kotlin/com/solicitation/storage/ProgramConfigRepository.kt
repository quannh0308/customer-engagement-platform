package com.solicitation.storage

import com.solicitation.model.config.*
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

/**
 * Repository for managing program configurations in DynamoDB.
 */
class ProgramConfigRepository(
    private val dynamoDbClient: DynamoDbClient,
    private val tableName: String = DynamoDBConfig.TableNames.PROGRAM_CONFIG
) {
    
    private val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
    
    fun save(config: ProgramConfig) {
        val item = mutableMapOf<String, AttributeValue>()
        
        item["PK"] = AttributeValue.builder().s("PROGRAM#${config.programId}").build()
        item["SK"] = AttributeValue.builder().s("CONFIG").build()
        item["programId"] = AttributeValue.builder().s(config.programId).build()
        item["programName"] = AttributeValue.builder().s(config.programName).build()
        item["enabled"] = AttributeValue.builder().bool(config.enabled).build()
        item["marketplaces"] = AttributeValue.builder().s(objectMapper.writeValueAsString(config.marketplaces)).build()
        item["dataConnectors"] = AttributeValue.builder().s(objectMapper.writeValueAsString(config.dataConnectors)).build()
        item["scoringModels"] = AttributeValue.builder().s(objectMapper.writeValueAsString(config.scoringModels)).build()
        item["filterChain"] = AttributeValue.builder().s(objectMapper.writeValueAsString(config.filterChain)).build()
        item["channels"] = AttributeValue.builder().s(objectMapper.writeValueAsString(config.channels)).build()
        
        config.batchSchedule?.let {
            item["batchSchedule"] = AttributeValue.builder().s(it).build()
        }
        
        item["reactiveEnabled"] = AttributeValue.builder().bool(config.reactiveEnabled).build()
        item["candidateTTLDays"] = AttributeValue.builder().n(config.candidateTTLDays.toString()).build()
        
        config.timingWindowDays?.let {
            item["timingWindowDays"] = AttributeValue.builder().n(it.toString()).build()
        }
        
        dynamoDbClient.putItem(
            PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build()
        )
    }
    
    fun findByProgramId(programId: String): ProgramConfig? {
        val response = dynamoDbClient.getItem(
            GetItemRequest.builder()
                .tableName(tableName)
                .key(mapOf(
                    "PK" to AttributeValue.builder().s("PROGRAM#$programId").build(),
                    "SK" to AttributeValue.builder().s("CONFIG").build()
                ))
                .build()
        )
        
        return response.item()?.let { item ->
            deserializeProgramConfig(item)
        }
    }
    
    fun findByMarketplace(marketplace: String): List<ProgramConfig> {
        val response = dynamoDbClient.query(
            QueryRequest.builder()
                .tableName(tableName)
                .indexName("MarketplaceIndex")
                .keyConditionExpression("GSI1PK = :marketplace")
                .expressionAttributeValues(mapOf(
                    ":marketplace" to AttributeValue.builder().s("MARKETPLACE#$marketplace").build()
                ))
                .build()
        )
        
        return response.items().mapNotNull { item ->
            deserializeProgramConfig(item)
        }
    }
    
    fun findAll(): List<ProgramConfig> {
        val response = dynamoDbClient.scan(
            ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("SK = :config")
                .expressionAttributeValues(mapOf(
                    ":config" to AttributeValue.builder().s("CONFIG").build()
                ))
                .build()
        )
        
        return response.items().mapNotNull { item ->
            deserializeProgramConfig(item)
        }
    }
    
    fun delete(programId: String) {
        dynamoDbClient.deleteItem(
            DeleteItemRequest.builder()
                .tableName(tableName)
                .key(mapOf(
                    "PK" to AttributeValue.builder().s("PROGRAM#$programId").build(),
                    "SK" to AttributeValue.builder().s("CONFIG").build()
                ))
                .build()
        )
    }
    
    fun updateEnabled(programId: String, enabled: Boolean) {
        dynamoDbClient.updateItem(
            UpdateItemRequest.builder()
                .tableName(tableName)
                .key(mapOf(
                    "PK" to AttributeValue.builder().s("PROGRAM#$programId").build(),
                    "SK" to AttributeValue.builder().s("CONFIG").build()
                ))
                .updateExpression("SET enabled = :enabled")
                .expressionAttributeValues(mapOf(
                    ":enabled" to AttributeValue.builder().bool(enabled).build()
                ))
                .build()
        )
    }
    
    fun saveMarketplaceOverride(override: MarketplaceConfigOverride) {
        val item = mapOf(
            "PK" to AttributeValue.builder().s("PROGRAM#${override.programId}").build(),
            "SK" to AttributeValue.builder().s("MARKETPLACE#${override.marketplace}").build(),
            "programId" to AttributeValue.builder().s(override.programId).build(),
            "marketplace" to AttributeValue.builder().s(override.marketplace).build(),
            "overrides" to AttributeValue.builder().s(objectMapper.writeValueAsString(override.overrides)).build()
        )
        
        dynamoDbClient.putItem(
            PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build()
        )
    }
    
    fun findMarketplaceOverride(programId: String, marketplace: String): MarketplaceConfigOverride? {
        val response = dynamoDbClient.getItem(
            GetItemRequest.builder()
                .tableName(tableName)
                .key(mapOf(
                    "PK" to AttributeValue.builder().s("PROGRAM#$programId").build(),
                    "SK" to AttributeValue.builder().s("MARKETPLACE#$marketplace").build()
                ))
                .build()
        )
        
        return response.item()?.let { item ->
            MarketplaceConfigOverride(
                programId = item["programId"]?.s() ?: return null,
                marketplace = item["marketplace"]?.s() ?: return null,
                overrides = objectMapper.readValue(
                    item["overrides"]?.s() ?: return null,
                    objectMapper.typeFactory.constructMapType(Map::class.java, String::class.java, Any::class.java)
                )
            )
        }
    }
    
    fun findByProgramIdAndMarketplace(programId: String, marketplace: String): ProgramConfig? {
        val baseConfig = findByProgramId(programId) ?: return null
        val override = findMarketplaceOverride(programId, marketplace)
        
        return if (override != null) {
            applyMarketplaceOverrides(baseConfig, marketplace, override.overrides)
        } else {
            baseConfig
        }
    }
    
    private fun deserializeProgramConfig(item: Map<String, AttributeValue>): ProgramConfig? {
        return try {
            ProgramConfig(
                programId = item["programId"]?.s() ?: return null,
                programName = item["programName"]?.s() ?: return null,
                enabled = item["enabled"]?.bool() ?: return null,
                marketplaces = objectMapper.readValue(
                    item["marketplaces"]?.s() ?: return null,
                    objectMapper.typeFactory.constructCollectionType(List::class.java, String::class.java)
                ),
                dataConnectors = objectMapper.readValue(
                    item["dataConnectors"]?.s() ?: return null,
                    objectMapper.typeFactory.constructCollectionType(List::class.java, DataConnectorConfig::class.java)
                ),
                scoringModels = objectMapper.readValue(
                    item["scoringModels"]?.s() ?: return null,
                    objectMapper.typeFactory.constructCollectionType(List::class.java, ScoringModelConfig::class.java)
                ),
                filterChain = objectMapper.readValue(
                    item["filterChain"]?.s() ?: return null,
                    FilterChainConfig::class.java
                ),
                channels = objectMapper.readValue(
                    item["channels"]?.s() ?: return null,
                    objectMapper.typeFactory.constructCollectionType(List::class.java, ChannelConfig::class.java)
                ),
                batchSchedule = item["batchSchedule"]?.s(),
                reactiveEnabled = item["reactiveEnabled"]?.bool() ?: return null,
                candidateTTLDays = item["candidateTTLDays"]?.n()?.toInt() ?: return null,
                timingWindowDays = item["timingWindowDays"]?.n()?.toInt()
            )
        } catch (e: Exception) {
            null
        }
    }
}
