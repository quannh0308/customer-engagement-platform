package com.ceap.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank

/**
 * Represents a dimension of context for a candidate.
 * Examples: marketplace, program, vertical
 *
 * @property type Type of context (e.g., "marketplace", "program", "vertical")
 * @property id Identifier for this context dimension
 */
data class Context(
    @field:NotBlank
    @JsonProperty("type")
    val type: String,
    
    @field:NotBlank
    @JsonProperty("id")
    val id: String
)
