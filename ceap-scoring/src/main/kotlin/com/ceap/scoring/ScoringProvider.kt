package com.ceap.scoring

import com.ceap.model.Candidate
import com.ceap.model.Score

/**
 * Interface for scoring providers that evaluate candidates using ML models.
 * 
 * Implementations of this interface integrate with various ML platforms
 * (e.g., SageMaker, custom models) to score candidates for solicitation targeting.
 * 
 * **Requirements**: 3.1, 3.4
 */
interface ScoringProvider {
    
    /**
     * Returns the unique identifier for this scoring model.
     * 
     * @return Model identifier (e.g., "propensity-v1", "quality-score-v2")
     */
    fun getModelId(): String
    
    /**
     * Returns the version of this scoring model.
     * 
     * @return Model version string (e.g., "1.0.0", "2023-11-15")
     */
    fun getModelVersion(): String
    
    /**
     * Returns the list of required features for scoring.
     * 
     * These features must be retrieved from the feature store before
     * scoring can be performed.
     * 
     * @return List of required feature names
     */
    fun getRequiredFeatures(): List<String>
    
    /**
     * Scores a single candidate using the ML model.
     * 
     * @param candidate The candidate to score
     * @param features Feature values retrieved from the feature store
     * @return Score object with value, confidence, and metadata
     * @throws ScoringException if scoring fails
     */
    suspend fun scoreCandidate(candidate: Candidate, features: FeatureMap): Score
    
    /**
     * Scores a batch of candidates using the ML model.
     * 
     * Implementations should optimize batch scoring for efficiency
     * (e.g., using batch inference endpoints).
     * 
     * @param candidates List of candidates to score
     * @param features List of feature maps corresponding to each candidate
     * @return List of scores in the same order as input candidates
     * @throws ScoringException if scoring fails
     */
    suspend fun scoreBatch(candidates: List<Candidate>, features: List<FeatureMap>): List<Score>
    
    /**
     * Performs a health check on the scoring model endpoint.
     * 
     * @return Health status indicating if the model is available
     */
    suspend fun healthCheck(): HealthStatus
    
    /**
     * Returns a fallback score when the model is unavailable or fails.
     * 
     * The fallback score should be conservative and safe for production use.
     * 
     * @param candidate The candidate that needs a fallback score
     * @return Fallback score with appropriate confidence level
     */
    fun getFallbackScore(candidate: Candidate): Score
}

/**
 * Map of feature values for a candidate.
 * 
 * @property customerId Customer identifier
 * @property subjectId Subject identifier
 * @property features Map of feature names to values
 */
data class FeatureMap(
    val customerId: String,
    val subjectId: String,
    val features: Map<String, FeatureValue>
)

/**
 * Represents a feature value with type information.
 * 
 * @property value The feature value (can be numeric, string, boolean, or list)
 * @property type The type of the feature value
 */
data class FeatureValue(
    val value: Any,
    val type: FeatureType
)

/**
 * Types of feature values supported by the scoring engine.
 */
enum class FeatureType {
    NUMERIC,
    STRING,
    BOOLEAN,
    LIST
}

/**
 * Health status of a scoring provider.
 * 
 * @property healthy Whether the provider is healthy and available
 * @property message Optional message describing the health status
 * @property lastChecked Timestamp of the last health check
 */
data class HealthStatus(
    val healthy: Boolean,
    val message: String? = null,
    val lastChecked: Long = System.currentTimeMillis()
)

/**
 * Exception thrown when scoring operations fail.
 */
class ScoringException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

