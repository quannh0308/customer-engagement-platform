package com.solicitation.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.Valid
import javax.validation.constraints.NotEmpty

/**
 * Configuration for the filter chain that processes candidates.
 *
 * @property filters List of filters to apply in order
 */
data class FilterChainConfig(
    @field:NotEmpty
    @field:Valid
    @JsonProperty("filters")
    val filters: List<FilterConfig>
)
