package com.ceap.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import java.time.Instant

/**
 * Records why a candidate was rejected by a filter.
 *
 * @property filterId ID of the filter that rejected the candidate
 * @property reason Human-readable rejection reason
 * @property reasonCode Machine-readable reason code
 * @property timestamp When the rejection occurred
 */
data class RejectionRecord(
    @field:NotBlank
    @JsonProperty("filterId")
    val filterId: String,
    
    @field:NotBlank
    @JsonProperty("reason")
    val reason: String,
    
    @field:NotBlank
    @JsonProperty("reasonCode")
    val reasonCode: String,
    
    @field:NotNull
    @JsonProperty("timestamp")
    val timestamp: Instant
)
