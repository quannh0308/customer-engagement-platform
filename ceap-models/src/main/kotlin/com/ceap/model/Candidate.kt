package com.ceap.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.Valid
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull

/**
 * Represents a customer engagement candidate - a potential opportunity to engage
 * customer response for a specific subject.
 *
 * @property customerId Unique identifier for the customer
 * @property context Multi-dimensional context describing the customer engagement scenario
 * @property subject The subject being engaged for response
 * @property scores Scores from various ML models (key: modelId, value: Score)
 * @property attributes Attributes describing the customer engagement opportunity
 * @property metadata System-level metadata for tracking
 * @property rejectionHistory History of rejections (if any)
 */
data class Candidate(
    @field:NotNull
    @JsonProperty("customerId")
    val customerId: String,
    
    @field:NotEmpty
    @field:Valid
    @JsonProperty("context")
    val context: List<Context>,
    
    @field:NotNull
    @field:Valid
    @JsonProperty("subject")
    val subject: Subject,
    
    @JsonProperty("scores")
    val scores: Map<String, Score>? = null,
    
    @field:NotNull
    @field:Valid
    @JsonProperty("attributes")
    val attributes: CandidateAttributes,
    
    @field:NotNull
    @field:Valid
    @JsonProperty("metadata")
    val metadata: CandidateMetadata,
    
    @JsonProperty("rejectionHistory")
    val rejectionHistory: List<RejectionRecord>? = null
)
