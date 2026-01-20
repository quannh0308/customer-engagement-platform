package com.ceap.serving.v1

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * V1 API models for backward compatibility.
 * 
 * These models represent the legacy API format that clients are currently using.
 * The V1 adapter translates between these models and the V2 backend implementation.
 */

/**
 * V1 request for getting candidates for a customer.
 * 
 * Legacy format with simplified structure.
 */
data class V1GetCandidatesRequest(
    @JsonProperty("customer_id")
    val customerId: String,
    
    @JsonProperty("channel")
    val channel: String? = null,
    
    @JsonProperty("program")
    val program: String? = null,
    
    @JsonProperty("marketplace")
    val marketplace: String,
    
    @JsonProperty("limit")
    val limit: Int = 10
)

/**
 * V1 response for getting candidates.
 * 
 * Legacy format with simplified candidate structure.
 */
data class V1GetCandidatesResponse(
    @JsonProperty("candidates")
    val candidates: List<V1Candidate>,
    
    @JsonProperty("total_count")
    val totalCount: Int,
    
    @JsonProperty("latency_ms")
    val latencyMs: Long
)

/**
 * V1 candidate model.
 * 
 * Simplified representation compared to V2.
 */
data class V1Candidate(
    @JsonProperty("customer_id")
    val customerId: String,
    
    @JsonProperty("subject_type")
    val subjectType: String,
    
    @JsonProperty("subject_id")
    val subjectId: String,
    
    @JsonProperty("program_id")
    val programId: String,
    
    @JsonProperty("marketplace_id")
    val marketplaceId: String,
    
    @JsonProperty("score")
    val score: Double? = null,
    
    @JsonProperty("event_date")
    val eventDate: String,
    
    @JsonProperty("created_at")
    val createdAt: String
)

/**
 * V1 batch request for getting candidates for multiple customers.
 */
data class V1BatchGetCandidatesRequest(
    @JsonProperty("customer_ids")
    val customerIds: List<String>,
    
    @JsonProperty("channel")
    val channel: String? = null,
    
    @JsonProperty("program")
    val program: String? = null,
    
    @JsonProperty("marketplace")
    val marketplace: String,
    
    @JsonProperty("limit")
    val limit: Int = 10
)

/**
 * V1 batch response.
 */
data class V1BatchGetCandidatesResponse(
    @JsonProperty("results")
    val results: Map<String, List<V1Candidate>>,
    
    @JsonProperty("total_count")
    val totalCount: Int,
    
    @JsonProperty("latency_ms")
    val latencyMs: Long
)

/**
 * V1 error response.
 */
data class V1ErrorResponse(
    @JsonProperty("error")
    val error: String,
    
    @JsonProperty("message")
    val message: String,
    
    @JsonProperty("status_code")
    val statusCode: Int
)
