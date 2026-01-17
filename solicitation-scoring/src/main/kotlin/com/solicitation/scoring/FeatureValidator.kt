package com.solicitation.scoring

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Validates that required features are present and valid for scoring.
 * 
 * **Requirements**: 3.2
 */
class FeatureValidator {
    
    /**
     * Validates that all required features are present in the feature map.
     * 
     * @param requiredFeatures List of required feature names
     * @param features Feature map to validate
     * @return Validation result with details
     */
    fun validate(requiredFeatures: List<String>, features: FeatureMap): FeatureValidationResult {
        val missingFeatures = requiredFeatures.filter { it !in features.features.keys }
        
        if (missingFeatures.isNotEmpty()) {
            logger.warn { 
                "Missing required features for customer ${features.customerId}: $missingFeatures" 
            }
            return FeatureValidationResult(
                valid = false,
                missingFeatures = missingFeatures,
                invalidFeatures = emptyList()
            )
        }
        
        // Check for invalid feature values (null, NaN, etc.)
        val invalidFeatures = requiredFeatures.filter { featureName ->
            val featureValue = features.features[featureName]
            featureValue == null || !isValidFeatureValue(featureValue)
        }
        
        if (invalidFeatures.isNotEmpty()) {
            logger.warn { 
                "Invalid feature values for customer ${features.customerId}: $invalidFeatures" 
            }
            return FeatureValidationResult(
                valid = false,
                missingFeatures = emptyList(),
                invalidFeatures = invalidFeatures
            )
        }
        
        logger.debug { 
            "All ${requiredFeatures.size} required features validated for customer ${features.customerId}" 
        }
        
        return FeatureValidationResult(
            valid = true,
            missingFeatures = emptyList(),
            invalidFeatures = emptyList()
        )
    }
    
    /**
     * Validates features for multiple candidates in batch.
     * 
     * @param requiredFeatures List of required feature names
     * @param featureMaps List of feature maps to validate
     * @return List of validation results in the same order
     */
    fun validateBatch(
        requiredFeatures: List<String>,
        featureMaps: List<FeatureMap>
    ): List<FeatureValidationResult> {
        return featureMaps.map { features ->
            validate(requiredFeatures, features)
        }
    }
    
    /**
     * Checks if a feature value is valid (not null, not NaN for numeric values).
     * 
     * @param featureValue Feature value to check
     * @return true if valid, false otherwise
     */
    private fun isValidFeatureValue(featureValue: FeatureValue): Boolean {
        return when (featureValue.type) {
            FeatureType.NUMERIC -> {
                val value = featureValue.value
                when (value) {
                    is Double -> !value.isNaN() && !value.isInfinite()
                    is Float -> !value.isNaN() && !value.isInfinite()
                    is Number -> true
                    else -> false
                }
            }
            FeatureType.STRING -> {
                featureValue.value is String && featureValue.value.isNotBlank()
            }
            FeatureType.BOOLEAN -> {
                featureValue.value is Boolean
            }
            FeatureType.LIST -> {
                featureValue.value is List<*>
            }
        }
    }
    
    /**
     * Creates a detailed error message for validation failures.
     * 
     * @param result Validation result
     * @return Error message describing the validation failure
     */
    fun createErrorMessage(result: FeatureValidationResult): String {
        val messages = mutableListOf<String>()
        
        if (result.missingFeatures.isNotEmpty()) {
            messages.add("Missing features: ${result.missingFeatures.joinToString(", ")}")
        }
        
        if (result.invalidFeatures.isNotEmpty()) {
            messages.add("Invalid features: ${result.invalidFeatures.joinToString(", ")}")
        }
        
        return messages.joinToString("; ")
    }
}

/**
 * Result of feature validation.
 * 
 * @property valid Whether all required features are present and valid
 * @property missingFeatures List of missing feature names
 * @property invalidFeatures List of invalid feature names
 */
data class FeatureValidationResult(
    val valid: Boolean,
    val missingFeatures: List<String>,
    val invalidFeatures: List<String>
) {
    /**
     * Returns true if validation passed.
     */
    fun isValid(): Boolean = valid
    
    /**
     * Returns true if validation failed.
     */
    fun isInvalid(): Boolean = !valid
}

