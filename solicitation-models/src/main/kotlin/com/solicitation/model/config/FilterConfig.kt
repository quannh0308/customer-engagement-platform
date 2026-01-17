package com.solicitation.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.PositiveOrZero

/**
 * Configuration for a single filter in the filter chain.
 *
 * @property filterId Unique identifier for the filter
 * @property filterType Type of filter (e.g., "eligibility", "quality", "timing")
 * @property enabled Whether this filter is enabled
 * @property parameters Filter-specific configuration parameters
 * @property order Execution order in the filter chain (0-based)
 */
data class FilterConfig(
    @field:NotBlank
    @JsonProperty("filterId")
    val filterId: String,
    
    @field:NotBlank
    @JsonProperty("filterType")
    val filterType: String,
    
    @field:NotNull
    @JsonProperty("enabled")
    val enabled: Boolean,
    
    @JsonProperty("parameters")
    val parameters: Map<String, Any>? = null,
    
    @field:PositiveOrZero
    @JsonProperty("order")
    val order: Int
)
