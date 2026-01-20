package com.ceap.scoring

import com.ceap.model.Candidate
import com.ceap.model.Score
import mu.KotlinLogging
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Manages fallback strategies for scoring failures.
 * 
 * Provides multiple fallback options when scoring fails:
 * 1. Cached score (if available and not expired)
 * 2. Provider's fallback score
 * 3. Configured default score
 * 
 * **Requirements**: 3.4, 9.3
 */
class ScoringFallback(
    private val scoreCacheRepository: ScoreCacheRepository,
    private val config: ScoringFallbackConfig = ScoringFallbackConfig()
) {
    
    /**
     * Gets a fallback score when scoring fails.
     * 
     * Tries fallback strategies in order:
     * 1. Cached score (if available)
     * 2. Provider's fallback score
     * 3. Default fallback score
     * 
     * @param candidate Candidate that needs a score
     * @param provider Scoring provider that failed
     * @param failure Exception that caused the failure
     * @return Fallback score
     */
    fun getFallbackScore(
        candidate: Candidate,
        provider: ScoringProvider,
        failure: Exception
    ): Score {
        val modelId = provider.getModelId()
        val subjectId = candidate.subject.id
        
        logger.warn(failure) { 
            "Scoring failed for customer ${candidate.customerId}, model $modelId - using fallback" 
        }
        
        // Strategy 1: Try cached score
        val cachedScore = scoreCacheRepository.get(candidate.customerId, subjectId, modelId)
        if (cachedScore != null) {
            logger.info { 
                "Using cached fallback score for customer ${candidate.customerId}, model $modelId" 
            }
            return cachedScore.toScore().copy(
                metadata = (cachedScore.metadata ?: emptyMap<String, Any>()) + mapOf<String, Any>(
                    "fallback" to true,
                    "fallbackStrategy" to "cached",
                    "originalFailure" to (failure.message ?: "Unknown error")
                )
            )
        }
        
        // Strategy 2: Try provider's fallback score
        return try {
            val providerFallback = provider.getFallbackScore(candidate)
            logger.info { 
                "Using provider fallback score for customer ${candidate.customerId}, model $modelId" 
            }
            providerFallback.copy(
                metadata = (providerFallback.metadata ?: emptyMap<String, Any>()) + mapOf<String, Any>(
                    "fallback" to true,
                    "fallbackStrategy" to "provider",
                    "originalFailure" to (failure.message ?: "Unknown error")
                )
            )
        } catch (e: Exception) {
            logger.warn(e) { 
                "Provider fallback failed for model $modelId, using default fallback" 
            }
            
            // Strategy 3: Use default fallback score
            getDefaultFallbackScore(modelId, failure)
        }
    }
    
    /**
     * Gets a default fallback score when all other strategies fail.
     * 
     * @param modelId Model identifier
     * @param failure Original failure exception
     * @return Default fallback score
     */
    private fun getDefaultFallbackScore(modelId: String, failure: Exception): Score {
        logger.info { "Using default fallback score for model $modelId" }
        
        return Score(
            modelId = modelId,
            value = config.defaultScore,
            confidence = config.defaultConfidence,
            timestamp = Instant.now(),
            metadata = mapOf<String, Any>(
                "fallback" to true,
                "fallbackStrategy" to "default",
                "originalFailure" to (failure.message ?: "Unknown error"),
                "defaultScore" to true
            )
        )
    }
    
    /**
     * Checks if a score is a fallback score.
     * 
     * @param score Score to check
     * @return true if the score is a fallback score
     */
    fun isFallbackScore(score: Score): Boolean {
        return score.metadata?.get("fallback") == true
    }
    
    /**
     * Gets the fallback strategy used for a score.
     * 
     * @param score Score to check
     * @return Fallback strategy name, or null if not a fallback score
     */
    fun getFallbackStrategy(score: Score): String? {
        return score.metadata?.get("fallbackStrategy") as? String
    }
}

/**
 * Configuration for scoring fallback behavior.
 * 
 * @property defaultScore Default score value when all fallbacks fail
 * @property defaultConfidence Default confidence when all fallbacks fail
 * @property logFailures Whether to log scoring failures
 */
data class ScoringFallbackConfig(
    val defaultScore: Double = 0.5,
    val defaultConfidence: Double = 0.1,
    val logFailures: Boolean = true
)

/**
 * Scoring provider with circuit breaker protection.
 * 
 * Wraps a scoring provider with circuit breaker and fallback logic.
 */
class ProtectedScoringProvider(
    private val provider: ScoringProvider,
    private val circuitBreaker: CircuitBreaker,
    private val fallback: ScoringFallback
) : ScoringProvider {
    
    override fun getModelId(): String = provider.getModelId()
    
    override fun getModelVersion(): String = provider.getModelVersion()
    
    override fun getRequiredFeatures(): List<String> = provider.getRequiredFeatures()
    
    override suspend fun scoreCandidate(candidate: Candidate, features: FeatureMap): Score {
        return try {
            circuitBreaker.execute {
                provider.scoreCandidate(candidate, features)
            }
        } catch (e: CircuitBreakerOpenException) {
            logger.warn { "Circuit breaker open for model ${provider.getModelId()}, using fallback" }
            fallback.getFallbackScore(candidate, provider, e)
        } catch (e: Exception) {
            logger.warn(e) { "Scoring failed for model ${provider.getModelId()}, using fallback" }
            fallback.getFallbackScore(candidate, provider, e)
        }
    }
    
    override suspend fun scoreBatch(
        candidates: List<Candidate>,
        features: List<FeatureMap>
    ): List<Score> {
        return try {
            circuitBreaker.execute {
                provider.scoreBatch(candidates, features)
            }
        } catch (e: CircuitBreakerOpenException) {
            logger.warn { "Circuit breaker open for model ${provider.getModelId()}, using fallback for batch" }
            candidates.map { candidate ->
                fallback.getFallbackScore(candidate, provider, e)
            }
        } catch (e: Exception) {
            logger.warn(e) { "Batch scoring failed for model ${provider.getModelId()}, using fallback" }
            candidates.map { candidate ->
                fallback.getFallbackScore(candidate, provider, e)
            }
        }
    }
    
    override suspend fun healthCheck(): HealthStatus {
        return try {
            provider.healthCheck()
        } catch (e: Exception) {
            logger.warn(e) { "Health check failed for model ${provider.getModelId()}" }
            HealthStatus(
                healthy = false,
                message = "Health check failed: ${e.message}"
            )
        }
    }
    
    override fun getFallbackScore(candidate: Candidate): Score {
        return provider.getFallbackScore(candidate)
    }
    
    /**
     * Gets the circuit breaker metrics for this provider.
     * 
     * @return Circuit breaker metrics
     */
    fun getCircuitBreakerMetrics(): CircuitBreakerMetrics {
        return circuitBreaker.getMetrics()
    }
}

