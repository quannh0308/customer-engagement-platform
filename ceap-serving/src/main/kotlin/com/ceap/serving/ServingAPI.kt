package com.ceap.serving

import com.ceap.model.Candidate

/**
 * Low-latency serving API for retrieving eligible candidates for customers.
 * 
 * Provides endpoints for single and batch customer queries with channel-specific
 * filtering and ranking, as well as lifecycle management operations.
 */
interface ServingAPI {
    
    /**
     * Retrieves candidates for a single customer.
     * 
     * @param request Request parameters for candidate retrieval
     * @return Response containing candidates and metadata
     */
    fun getCandidatesForCustomer(request: GetCandidatesRequest): GetCandidatesResponse
    
    /**
     * Retrieves candidates for multiple customers in a single request.
     * 
     * @param request Batch request parameters
     * @return Response containing candidates per customer
     */
    fun getCandidatesForCustomers(request: BatchGetCandidatesRequest): BatchGetCandidatesResponse
    
    /**
     * Manually deletes a candidate from storage.
     * 
     * @param request Request parameters for candidate deletion
     * @return Response indicating success or failure
     */
    fun deleteCandidate(request: DeleteCandidateRequest): DeleteCandidateResponse
    
    /**
     * Marks a candidate as consumed after delivery.
     * 
     * @param request Request parameters for marking consumed
     * @return Response with updated candidate
     */
    fun markCandidateConsumed(request: MarkConsumedRequest): MarkConsumedResponse
    
    /**
     * Refreshes a candidate's score and eligibility.
     * 
     * @param request Request parameters for candidate refresh
     * @return Response with refreshed candidate
     */
    fun refreshCandidate(request: RefreshCandidateRequest): RefreshCandidateResponse
}

/**
 * Request for retrieving candidates for a single customer.
 * 
 * @property customerId Customer identifier
 * @property channel Optional channel filter (e.g., "email", "in-app", "push")
 * @property program Optional program filter
 * @property marketplace Marketplace identifier
 * @property limit Maximum number of candidates to return (default 10)
 * @property includeScores Whether to include score details in response
 * @property refreshEligibility Whether to perform real-time eligibility refresh
 */
data class GetCandidatesRequest(
    val customerId: String,
    val channel: String? = null,
    val program: String? = null,
    val marketplace: String,
    val limit: Int = 10,
    val includeScores: Boolean = false,
    val refreshEligibility: Boolean = false
)

/**
 * Response containing candidates for a single customer.
 * 
 * @property candidates List of eligible candidates
 * @property metadata Response metadata
 * @property latencyMs Request processing latency in milliseconds
 */
data class GetCandidatesResponse(
    val candidates: List<Candidate>,
    val metadata: ResponseMetadata,
    val latencyMs: Long
)

/**
 * Request for retrieving candidates for multiple customers.
 * 
 * @property customerIds List of customer identifiers
 * @property channel Optional channel filter
 * @property program Optional program filter
 * @property marketplace Marketplace identifier
 * @property limit Maximum number of candidates per customer
 * @property includeScores Whether to include score details
 */
data class BatchGetCandidatesRequest(
    val customerIds: List<String>,
    val channel: String? = null,
    val program: String? = null,
    val marketplace: String,
    val limit: Int = 10,
    val includeScores: Boolean = false
)

/**
 * Response containing candidates for multiple customers.
 * 
 * @property results Map of customerId to their candidates
 * @property metadata Response metadata
 * @property latencyMs Request processing latency in milliseconds
 */
data class BatchGetCandidatesResponse(
    val results: Map<String, List<Candidate>>,
    val metadata: ResponseMetadata,
    val latencyMs: Long
)

/**
 * Metadata about the serving response.
 * 
 * @property totalCount Total number of candidates found
 * @property filteredCount Number of candidates after filtering
 * @property cacheHit Whether the response was served from cache
 * @property eligibilityRefreshed Whether eligibility was refreshed
 * @property degraded Whether the response is degraded due to failures
 */
data class ResponseMetadata(
    val totalCount: Int,
    val filteredCount: Int,
    val cacheHit: Boolean = false,
    val eligibilityRefreshed: Boolean = false,
    val degraded: Boolean = false
)

/**
 * Exception thrown when serving operations fail.
 */
class ServingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Request for deleting a candidate.
 * 
 * @property customerId Customer identifier
 * @property programId Program identifier
 * @property marketplaceId Marketplace identifier
 * @property subjectType Subject type
 * @property subjectId Subject identifier
 */
data class DeleteCandidateRequest(
    val customerId: String,
    val programId: String,
    val marketplaceId: String,
    val subjectType: String,
    val subjectId: String
)

/**
 * Response for candidate deletion.
 * 
 * @property success Whether the deletion was successful
 * @property message Optional message
 */
data class DeleteCandidateResponse(
    val success: Boolean,
    val message: String? = null
)

/**
 * Request for marking a candidate as consumed.
 * 
 * @property customerId Customer identifier
 * @property programId Program identifier
 * @property marketplaceId Marketplace identifier
 * @property subjectType Subject type
 * @property subjectId Subject identifier
 * @property deliveryTimestamp When the candidate was delivered
 * @property channelId Channel through which it was delivered
 */
data class MarkConsumedRequest(
    val customerId: String,
    val programId: String,
    val marketplaceId: String,
    val subjectType: String,
    val subjectId: String,
    val deliveryTimestamp: java.time.Instant,
    val channelId: String
)

/**
 * Response for marking consumed.
 * 
 * @property candidate Updated candidate with consumed status
 * @property success Whether the operation was successful
 */
data class MarkConsumedResponse(
    val candidate: Candidate?,
    val success: Boolean
)

/**
 * Request for refreshing a candidate.
 * 
 * @property customerId Customer identifier
 * @property programId Program identifier
 * @property marketplaceId Marketplace identifier
 * @property subjectType Subject type
 * @property subjectId Subject identifier
 * @property refreshScores Whether to refresh scores
 * @property refreshEligibility Whether to refresh eligibility
 */
data class RefreshCandidateRequest(
    val customerId: String,
    val programId: String,
    val marketplaceId: String,
    val subjectType: String,
    val subjectId: String,
    val refreshScores: Boolean = true,
    val refreshEligibility: Boolean = true
)

/**
 * Response for candidate refresh.
 * 
 * @property candidate Refreshed candidate
 * @property success Whether the refresh was successful
 * @property scoresUpdated Whether scores were updated
 * @property eligibilityUpdated Whether eligibility was updated
 */
data class RefreshCandidateResponse(
    val candidate: Candidate?,
    val success: Boolean,
    val scoresUpdated: Boolean = false,
    val eligibilityUpdated: Boolean = false
)
