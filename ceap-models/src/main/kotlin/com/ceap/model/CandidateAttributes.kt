package com.ceap.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotNull
import java.time.Instant

/**
 * Attributes describing the customer engagement opportunity.
 *
 * @property eventDate When the triggering event occurred
 * @property deliveryDate Preferred delivery date (optional)
 * @property timingWindow Timing window for customer engagement (optional)
 * @property orderValue Order value if applicable (optional)
 * @property mediaEligible Whether media (images/video) is eligible
 * @property channelEligibility Channel eligibility flags
 * @property consumed Whether the candidate has been consumed (delivered)
 * @property consumedAt When the candidate was consumed (optional)
 * @property consumedChannel Channel through which it was consumed (optional)
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
    val channelEligibility: Map<String, Boolean>,
    
    @JsonProperty("consumed")
    val consumed: Boolean = false,
    
    @JsonProperty("consumedAt")
    val consumedAt: Instant? = null,
    
    @JsonProperty("consumedChannel")
    val consumedChannel: String? = null
)
