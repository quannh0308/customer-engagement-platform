package com.solicitation.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

/**
 * Configuration for a scoring model that evaluates candidates.
 *
 * @property modelId Unique identifier for the scoring model
 * @property modelType Type of model (e.g., "ml", "rule-based", "heuristic")
 * @property enabled Whether this model is enabled
 * @property modelConfig Model-specific configuration parameters
 */
data class ScoringModelConfig(
    @field:NotBlank
    @JsonProperty("modelId")
    val modelId: String,
    
    @field:NotBlank
    @JsonProperty("modelType")
    val modelType: String,
    
    @field:NotNull
    @JsonProperty("enabled")
    val enabled: Boolean,
    
    @JsonProperty("modelConfig")
    val modelConfig: Map<String, Any>? = null
)
