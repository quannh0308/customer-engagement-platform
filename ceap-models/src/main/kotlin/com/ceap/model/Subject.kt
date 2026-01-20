package com.ceap.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank

/**
 * Represents the subject being solicited for response.
 * Examples: product, video, music track, service, event
 *
 * @property type Type of subject (e.g., "product", "video", "track", "service")
 * @property id Unique identifier for the subject
 * @property metadata Optional metadata about the subject
 */
data class Subject(
    @field:NotBlank
    @JsonProperty("type")
    val type: String,
    
    @field:NotBlank
    @JsonProperty("id")
    val id: String,
    
    @JsonProperty("metadata")
    val metadata: Map<String, Any>? = null
)
