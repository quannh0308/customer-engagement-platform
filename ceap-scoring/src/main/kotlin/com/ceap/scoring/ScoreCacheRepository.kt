package com.ceap.scoring

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import mu.KotlinLogging
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Repository for score cache operations in DynamoDB.
 * 
 * Provides methods to read, write, and invalidate cached scores
 * with automatic TTL management.
 * 
 * **Requirements**: 3.5
 */
class ScoreCacheRepository(
    private val dynamoDbClient: DynamoDbClient,
    private val tableName: String = "ceap-score-cache",
    private val config: ScoreCacheConfig = ScoreCacheConfig()
) {
    
    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
    
    /**
     * Retrieves a cached score if available and not expired.
     * 
     * @param customerId Customer identifier
     * @param subjectId Subject identifier
     * @param modelId Model identifier
     * @return Cached score if found and valid, null otherwise
     */
    fun get(customerId: String, subjectId: String, modelId: String): ScoreCache? {
        if (!config.enabled) {
            logger.debug { "Score caching is disabled" }
            return null
        }
        
        val cacheKey = ScoreCache.createCacheKey(customerId, subjectId, modelId)
        
        try {
            val request = GetItemRequest.builder()
                .tableName(tableName)
                .key(mapOf("cacheKey" to AttributeValue.builder().s(cacheKey).build()))
                .build()
            
            val response = dynamoDbClient.getItem(request)
            
            if (!response.hasItem()) {
                logger.debug { "Cache miss for key: $cacheKey" }
                return null
            }
            
            val scoreCache = deserializeScoreCache(response.item())
            
            if (scoreCache.isExpired()) {
                logger.debug { "Cache entry expired for key: $cacheKey" }
                return null
            }
            
            logger.debug { "Cache hit for key: $cacheKey" }
            return scoreCache
            
        } catch (e: Exception) {
            logger.warn(e) { "Failed to retrieve cached score for key: $cacheKey" }
            return null
        }
    }
    
    /**
     * Stores a score in the cache with TTL.
     * 
     * @param customerId Customer identifier
     * @param subjectId Subject identifier
     * @param modelVersion Model version
     * @param score Score to cache
     */
    fun put(
        customerId: String,
        subjectId: String,
        modelVersion: String,
        score: com.ceap.model.Score
    ) {
        if (!config.enabled) {
            logger.debug { "Score caching is disabled, skipping cache write" }
            return
        }
        
        val ttl = config.getTTLForModel(score.modelId)
        val scoreCache = ScoreCache.fromScore(customerId, subjectId, modelVersion, score, ttl)
        
        try {
            val item = serializeScoreCache(scoreCache)
            
            val request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build()
            
            dynamoDbClient.putItem(request)
            
            logger.debug { "Cached score for key: ${scoreCache.cacheKey}, TTL: ${ttl}s" }
            
        } catch (e: Exception) {
            logger.warn(e) { "Failed to cache score for key: ${scoreCache.cacheKey}" }
            // Don't throw - caching failures should not break scoring
        }
    }
    
    /**
     * Batch retrieves cached scores for multiple candidates.
     * 
     * @param keys List of (customerId, subjectId, modelId) tuples
     * @return Map of cache keys to cached scores (only valid entries)
     */
    fun batchGet(keys: List<Triple<String, String, String>>): Map<String, ScoreCache> {
        if (!config.enabled || keys.isEmpty()) {
            return emptyMap()
        }
        
        try {
            val cacheKeys = keys.map { (customerId, subjectId, modelId) ->
                ScoreCache.createCacheKey(customerId, subjectId, modelId)
            }
            
            val keysAndAttributes = KeysAndAttributes.builder()
                .keys(cacheKeys.map { key ->
                    mapOf("cacheKey" to AttributeValue.builder().s(key).build())
                })
                .build()
            
            val request = BatchGetItemRequest.builder()
                .requestItems(mapOf(tableName to keysAndAttributes))
                .build()
            
            val response = dynamoDbClient.batchGetItem(request)
            
            val results = mutableMapOf<String, ScoreCache>()
            
            response.responses()[tableName]?.forEach { item ->
                val scoreCache = deserializeScoreCache(item)
                if (!scoreCache.isExpired()) {
                    results[scoreCache.cacheKey] = scoreCache
                }
            }
            
            logger.debug { "Batch cache lookup: ${keys.size} requested, ${results.size} hits" }
            
            return results
            
        } catch (e: Exception) {
            logger.warn(e) { "Failed to batch retrieve cached scores" }
            return emptyMap()
        }
    }
    
    /**
     * Invalidates a cached score.
     * 
     * @param customerId Customer identifier
     * @param subjectId Subject identifier
     * @param modelId Model identifier
     */
    fun invalidate(customerId: String, subjectId: String, modelId: String) {
        val cacheKey = ScoreCache.createCacheKey(customerId, subjectId, modelId)
        
        try {
            val request = DeleteItemRequest.builder()
                .tableName(tableName)
                .key(mapOf("cacheKey" to AttributeValue.builder().s(cacheKey).build()))
                .build()
            
            dynamoDbClient.deleteItem(request)
            
            logger.debug { "Invalidated cache for key: $cacheKey" }
            
        } catch (e: Exception) {
            logger.warn(e) { "Failed to invalidate cache for key: $cacheKey" }
        }
    }
    
    /**
     * Invalidates all cached scores for a customer.
     * 
     * @param customerId Customer identifier
     */
    fun invalidateForCustomer(customerId: String) {
        // Note: This requires a GSI on customerId for efficient querying
        // For now, we'll log a warning that this is not implemented
        logger.warn { "Invalidating all scores for customer $customerId is not yet implemented" }
        // TODO: Implement with GSI query + batch delete
    }
    
    /**
     * Serializes a ScoreCache object to DynamoDB item format.
     */
    private fun serializeScoreCache(scoreCache: ScoreCache): Map<String, AttributeValue> {
        return mapOf(
            "cacheKey" to AttributeValue.builder().s(scoreCache.cacheKey).build(),
            "customerId" to AttributeValue.builder().s(scoreCache.customerId).build(),
            "subjectId" to AttributeValue.builder().s(scoreCache.subjectId).build(),
            "modelId" to AttributeValue.builder().s(scoreCache.modelId).build(),
            "modelVersion" to AttributeValue.builder().s(scoreCache.modelVersion).build(),
            "scoreValue" to AttributeValue.builder().n(scoreCache.scoreValue.toString()).build(),
            "confidence" to AttributeValue.builder().n((scoreCache.confidence ?: 0.0).toString()).build(),
            "timestamp" to AttributeValue.builder().s(scoreCache.timestamp.toString()).build(),
            "expiresAt" to AttributeValue.builder().n(scoreCache.expiresAt.epochSecond.toString()).build(),
            "metadata" to AttributeValue.builder().s(
                objectMapper.writeValueAsString(scoreCache.metadata ?: emptyMap<String, Any>())
            ).build()
        )
    }
    
    /**
     * Deserializes a DynamoDB item to a ScoreCache object.
     */
    private fun deserializeScoreCache(item: Map<String, AttributeValue>): ScoreCache {
        val metadataJson = item["metadata"]?.s() ?: "{}"
        val metadata = objectMapper.readValue(metadataJson, Map::class.java) as? Map<String, Any>
        
        return ScoreCache(
            cacheKey = item["cacheKey"]?.s() ?: "",
            customerId = item["customerId"]?.s() ?: "",
            subjectId = item["subjectId"]?.s() ?: "",
            modelId = item["modelId"]?.s() ?: "",
            modelVersion = item["modelVersion"]?.s() ?: "",
            scoreValue = item["scoreValue"]?.n()?.toDouble() ?: 0.0,
            confidence = item["confidence"]?.n()?.toDouble(),
            timestamp = Instant.parse(item["timestamp"]?.s() ?: Instant.now().toString()),
            expiresAt = Instant.ofEpochSecond(item["expiresAt"]?.n()?.toLong() ?: 0),
            metadata = metadata
        )
    }
}

