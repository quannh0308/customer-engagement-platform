package com.solicitation.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import java.time.Instant

/**
 * Represents a score from an ML model.
 *
 * @property modelId Identifier of the model that produced this score
 * @property value Score value (typically 0.0 to 1.0)
 * @property confidence Confidence in the score (0.0 to 1.0)
 * @property timestamp When the score was computed
 * @property metadata Optional metadata about the scoring
 */
data class Score(
    @field:NotBlank
    @JsonProperty("modelId")
    val modelId: String,
    
    @field:NotNull
    @JsonProperty("value")
    val value: Double,
    
    @field:Min(0)
    @field:Max(1)
    @JsonProperty("confidence")
    val confidence: Double? = null,
    
    @field:NotNull
    @JsonProperty("timestamp")
    val timestamp: Instant,
    
    @JsonProperty("metadata")
    val metadata: Map<String, Any>? = null
)
