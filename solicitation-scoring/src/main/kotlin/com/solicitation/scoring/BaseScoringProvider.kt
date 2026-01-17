package com.solicitation.scoring

import com.solicitation.model.Candidate
import com.solicitation.model.Score
import mu.KotlinLogging
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Base implementation of ScoringProvider with common functionality.
 * 
 * Provides default implementations for batch scoring, health checks,
 * and fallback score generation. Subclasses only need to implement
 * the core scoring logic.
 * 
 * **Requirements**: 3.1, 3.4
 */
abstract class BaseScoringProvider : ScoringProvider {
    
    /**
     * Default batch scoring implementation that scores candidates individually.
     * 
     * Subclasses can override this for optimized batch processing.
     */
    override suspend fun scoreBatch(
        candidates: List<Candidate>,
        features: List<FeatureMap>
    ): List<Score> {
        require(candidates.size == features.size) {
            "Candidates and features lists must have the same size"
        }
        
        logger.debug { "Batch scoring ${candidates.size} candidates with model ${getModelId()}" }
        
        return candidates.zip(features).map { (candidate, featureMap) ->
            try {
                scoreCandidate(candidate, featureMap)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to score candidate ${candidate.customerId}, using fallback" }
                getFallbackScore(candidate)
            }
        }
    }
    
    /**
     * Default health check implementation.
     * 
     * Subclasses should override this to check actual model endpoint health.
     */
    override suspend fun healthCheck(): HealthStatus {
        return HealthStatus(
            healthy = true,
            message = "Default health check - override for actual endpoint check"
        )
    }
    
    /**
     * Default fallback score implementation.
     * 
     * Returns a conservative score with low confidence.
     * Subclasses can override for model-specific fallback logic.
     */
    override fun getFallbackScore(candidate: Candidate): Score {
        logger.info { "Generating fallback score for candidate ${candidate.customerId}" }
        
        return Score(
            modelId = getModelId(),
            value = 0.5, // Neutral score
            confidence = 0.1, // Low confidence
            timestamp = Instant.now(),
            metadata = mapOf(
                "fallback" to true,
                "reason" to "Model unavailable or scoring failed"
            )
        )
    }
    
    /**
     * Validates that all required features are present in the feature map.
     * 
     * @param features Feature map to validate
     * @throws ScoringException if required features are missing
     */
    protected fun validateFeatures(features: FeatureMap) {
        val requiredFeatures = getRequiredFeatures()
        val missingFeatures = requiredFeatures.filter { it !in features.features.keys }
        
        if (missingFeatures.isNotEmpty()) {
            throw ScoringException(
                "Missing required features for model ${getModelId()}: $missingFeatures"
            )
        }
    }
    
    /**
     * Extracts a numeric feature value from the feature map.
     * 
     * @param features Feature map
     * @param featureName Name of the feature to extract
     * @param defaultValue Default value if feature is missing or invalid
     * @return Numeric feature value
     */
    protected fun getNumericFeature(
        features: FeatureMap,
        featureName: String,
        defaultValue: Double = 0.0
    ): Double {
        val featureValue = features.features[featureName] ?: return defaultValue
        
        return when (featureValue.type) {
            FeatureType.NUMERIC -> {
                when (val value = featureValue.value) {
                    is Number -> value.toDouble()
                    else -> defaultValue
                }
            }
            else -> defaultValue
        }
    }
    
    /**
     * Extracts a string feature value from the feature map.
     * 
     * @param features Feature map
     * @param featureName Name of the feature to extract
     * @param defaultValue Default value if feature is missing or invalid
     * @return String feature value
     */
    protected fun getStringFeature(
        features: FeatureMap,
        featureName: String,
        defaultValue: String = ""
    ): String {
        val featureValue = features.features[featureName] ?: return defaultValue
        
        return when (featureValue.type) {
            FeatureType.STRING -> featureValue.value.toString()
            else -> defaultValue
        }
    }
    
    /**
     * Extracts a boolean feature value from the feature map.
     * 
     * @param features Feature map
     * @param featureName Name of the feature to extract
     * @param defaultValue Default value if feature is missing or invalid
     * @return Boolean feature value
     */
    protected fun getBooleanFeature(
        features: FeatureMap,
        featureName: String,
        defaultValue: Boolean = false
    ): Boolean {
        val featureValue = features.features[featureName] ?: return defaultValue
        
        return when (featureValue.type) {
            FeatureType.BOOLEAN -> {
                when (val value = featureValue.value) {
                    is Boolean -> value
                    else -> defaultValue
                }
            }
            else -> defaultValue
        }
    }
}

