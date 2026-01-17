package com.solicitation.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotNull
import java.time.Instant

/**
 * Attributes describing the solicitation opportunity.
 *
 * @property eventDate When the triggering event occurred
 * @property deliveryDate Preferred delivery date (optional)
 * @property timingWindow Timing window for solicitation (optional)
 * @property orderValue Order value if applicable (optional)
 * @property mediaEligible Whether media (images/video) is eligible
 * @property channelEligibility Channel eligibility flags
 */
data class CandidateAttributes(
    @field:NotNull
    @JsonProperty("eventDate")
    val eventDate: Instant,
    
    @JsonProperty("deliveryDate")
    val deliveryDate: Instant? = null,
    
    @JsonProperty("timingWindow")
    val timingWindow: String? = null,
    
    @JsonProperty("orderValue")
    val orderValue: Double? = null,
    
    @JsonProperty("mediaEligible")
    val mediaEligible: Boolean? = null,
    
    @field:NotNull
    @JsonProperty("channelEligibility")
    val channelEligibility: Map<String, Boolean>
)
