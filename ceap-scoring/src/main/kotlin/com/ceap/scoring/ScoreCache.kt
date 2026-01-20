package com.ceap.scoring

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Represents a cached score entry in DynamoDB.
 * 
 * Cached scores are stored with a TTL to ensure freshness and
 * reduce unnecessary model invocations.
 * 
 * **Requirements**: 3.5
 * 
 * @property cacheKey Composite key: customerId#subjectId#modelId
 * @property customerId Customer identifier
 * @property subjectId Subject identifier
 * @property modelId Model identifier
 * @property modelVersion Model version when score was computed
 * @property scoreValue The score value
 * @property confidence Confidence in the score
 * @property timestamp When the score was computed
 * @property expiresAt TTL timestamp for automatic deletion
 * @property metadata Optional metadata about the score
 */
data class ScoreCache(
    @JsonProperty("cacheKey")
    val cacheKey: String,
    
    @JsonProperty("customerId")
    val customerId: String,
    
    @JsonProperty("subjectId")
    val subjectId: String,
    
    @JsonProperty("modelId")
    val modelId: String,
    
    @JsonProperty("modelVersion")
    val modelVersion: String,
    
    @JsonProperty("scoreValue")
    val scoreValue: Double,
    
    @JsonProperty("confidence")
    val confidence: Double?,
    
    @JsonProperty("timestamp")
    val timestamp: Instant,
    
    @JsonProperty("expiresAt")
    val expiresAt: Instant,
    
    @JsonProperty("metadata")
    val metadata: Map<String, Any>? = null
) {
    companion object {
        /**
         * Creates a cache key from customer, subject, and model identifiers.
         * 
         * @param customerId Customer identifier
         * @param subjectId Subject identifier
         * @param modelId Model identifier
         * @return Composite cache key
         */
        fun createCacheKey(customerId: String, subjectId: String, modelId: String): String {
            return "$customerId#$subjectId#$modelId"
        }
        
        /**
         * Creates a ScoreCache entry from a Score object.
         * 
         * @param customerId Customer identifier
         * @param subjectId Subject identifier
         * @param modelVersion Model version
         * @param score Score object to cache
         * @param ttlSeconds TTL in seconds
         * @return ScoreCache entry
         */
        fun fromScore(
            customerId: String,
            subjectId: String,
            modelVersion: String,
            score: com.ceap.model.Score,
            ttlSeconds: Long
        ): ScoreCache {
            val cacheKey = createCacheKey(customerId, subjectId, score.modelId)
            val expiresAt = Instant.now().plusSeconds(ttlSeconds)
            
            return ScoreCache(
                cacheKey = cacheKey,
                customerId = customerId,
                subjectId = subjectId,
                modelId = score.modelId,
                modelVersion = modelVersion,
                scoreValue = score.value,
                confidence = score.confidence,
                timestamp = score.timestamp,
                expiresAt = expiresAt,
                metadata = score.metadata
            )
        }
    }
    
    /**
     * Converts this cache entry to a Score object.
     * 
     * @return Score object
     */
    fun toScore(): com.ceap.model.Score {
        return com.ceap.model.Score(
            modelId = modelId,
            value = scoreValue,
            confidence = confidence,
            timestamp = timestamp,
            metadata = metadata
        )
    }
    
    /**
     * Checks if this cache entry has expired.
     * 
     * @return true if expired, false otherwise
     */
    fun isExpired(): Boolean {
        return Instant.now().isAfter(expiresAt)
    }
}

/**
 * Configuration for score caching.
 * 
 * @property enabled Whether score caching is enabled
 * @property ttlSeconds Default TTL for cached scores in seconds
 * @property modelTTLOverrides Model-specific TTL overrides
 */
data class ScoreCacheConfig(
    val enabled: Boolean = true,
    val ttlSeconds: Long = 3600, // 1 hour default
    val modelTTLOverrides: Map<String, Long> = emptyMap()
) {
    /**
     * Gets the TTL for a specific model.
     * 
     * @param modelId Model identifier
     * @return TTL in seconds
     */
    fun getTTLForModel(modelId: String): Long {
        return modelTTLOverrides[modelId] ?: ttlSeconds
    }
}

