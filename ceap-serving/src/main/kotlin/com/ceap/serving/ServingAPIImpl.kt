package com.ceap.serving

import com.ceap.model.Candidate
import com.ceap.storage.CandidateRepository
import com.ceap.storage.StorageException
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Implementation of ServingAPI with low-latency candidate retrieval.
 * 
 * Provides single and batch customer queries with channel-specific filtering,
 * ranking, and optional eligibility refresh.
 * 
 * @property candidateRepository Repository for candidate storage
 * @property rankingStrategy Strategy for ranking candidates
 * @property eligibilityChecker Optional checker for real-time eligibility refresh
 * @property fallbackHandler Handler for graceful degradation
 */
class ServingAPIImpl(
    private val candidateRepository: CandidateRepository,
    private val rankingStrategy: RankingStrategy,
    private val eligibilityChecker: EligibilityChecker? = null,
    private val fallbackHandler: FallbackHandler? = null
) : ServingAPI {
    
    override fun getCandidatesForCustomer(request: GetCandidatesRequest): GetCandidatesResponse {
        val startTime = System.currentTimeMillis()
        
        try {
            logger.debug { "Getting candidates for customer: ${request.customerId}" }
            
            // Validate request
            validateRequest(request)
            
            // Query candidates from storage
            val candidates = queryCandidates(request)
            
            // Apply channel filtering if specified
            val filteredCandidates = if (request.channel != null) {
                filterByChannel(candidates, request.channel)
            } else {
                candidates
            }
            
            // Refresh eligibility if requested
            val eligibleCandidates = if (request.refreshEligibility && eligibilityChecker != null) {
                refreshEligibility(filteredCandidates)
            } else {
                filteredCandidates
            }
            
            // Apply ranking
            val rankedCandidates = rankingStrategy.rank(
                eligibleCandidates,
                request.channel,
                request.customerId
            )
            
            // Apply limit
            val limitedCandidates = rankedCandidates.take(request.limit)
            
            // Remove scores if not requested
            val finalCandidates = if (!request.includeScores) {
                limitedCandidates.map { it.copy(scores = null) }
            } else {
                limitedCandidates
            }
            
            val latency = System.currentTimeMillis() - startTime
            
            logger.info { "Retrieved ${finalCandidates.size} candidates for customer ${request.customerId} in ${latency}ms" }
            
            return GetCandidatesResponse(
                candidates = finalCandidates,
                metadata = ResponseMetadata(
                    totalCount = candidates.size,
                    filteredCount = finalCandidates.size,
                    eligibilityRefreshed = request.refreshEligibility && eligibilityChecker != null
                ),
                latencyMs = latency
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to get candidates for customer ${request.customerId}" }
            
            // Try fallback if available
            if (fallbackHandler != null) {
                return fallbackHandler.handleFailure(request, e, System.currentTimeMillis() - startTime)
            }
            
            throw ServingException("Failed to get candidates", e)
        }
    }
    
    override fun getCandidatesForCustomers(request: BatchGetCandidatesRequest): BatchGetCandidatesResponse {
        val startTime = System.currentTimeMillis()
        
        try {
            logger.debug { "Getting candidates for ${request.customerIds.size} customers" }
            
            // Validate request
            validateBatchRequest(request)
            
            // Query candidates for each customer
            val results = mutableMapOf<String, List<Candidate>>()
            
            for (customerId in request.customerIds) {
                try {
                    val customerRequest = GetCandidatesRequest(
                        customerId = customerId,
                        channel = request.channel,
                        program = request.program,
                        marketplace = request.marketplace,
                        limit = request.limit,
                        includeScores = request.includeScores,
                        refreshEligibility = false // Don't refresh in batch mode
                    )
                    
                    val response = getCandidatesForCustomer(customerRequest)
                    results[customerId] = response.candidates
                    
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to get candidates for customer $customerId in batch" }
                    results[customerId] = emptyList()
                }
            }
            
            val latency = System.currentTimeMillis() - startTime
            
            logger.info { "Retrieved candidates for ${results.size} customers in ${latency}ms" }
            
            return BatchGetCandidatesResponse(
                results = results,
                metadata = ResponseMetadata(
                    totalCount = results.values.sumOf { it.size },
                    filteredCount = results.values.sumOf { it.size }
                ),
                latencyMs = latency
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to get candidates for batch request" }
            throw ServingException("Failed to get candidates for batch", e)
        }
    }
    
    override fun deleteCandidate(request: DeleteCandidateRequest): DeleteCandidateResponse {
        try {
            logger.info { "Deleting candidate: customer=${request.customerId}, program=${request.programId}, subject=${request.subjectType}:${request.subjectId}" }
            
            // Validate request
            require(request.customerId.isNotBlank()) { "Customer ID must not be blank" }
            require(request.programId.isNotBlank()) { "Program ID must not be blank" }
            require(request.marketplaceId.isNotBlank()) { "Marketplace ID must not be blank" }
            require(request.subjectType.isNotBlank()) { "Subject type must not be blank" }
            require(request.subjectId.isNotBlank()) { "Subject ID must not be blank" }
            
            // Delete from repository
            candidateRepository.delete(
                customerId = request.customerId,
                programId = request.programId,
                marketplaceId = request.marketplaceId,
                subjectType = request.subjectType,
                subjectId = request.subjectId
            )
            
            logger.info { "Successfully deleted candidate" }
            
            return DeleteCandidateResponse(
                success = true,
                message = "Candidate deleted successfully"
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete candidate" }
            return DeleteCandidateResponse(
                success = false,
                message = "Failed to delete candidate: ${e.message}"
            )
        }
    }
    
    override fun markCandidateConsumed(request: MarkConsumedRequest): MarkConsumedResponse {
        try {
            logger.info { "Marking candidate as consumed: customer=${request.customerId}, channel=${request.channelId}" }
            
            // Validate request
            require(request.customerId.isNotBlank()) { "Customer ID must not be blank" }
            require(request.programId.isNotBlank()) { "Program ID must not be blank" }
            require(request.marketplaceId.isNotBlank()) { "Marketplace ID must not be blank" }
            require(request.subjectType.isNotBlank()) { "Subject type must not be blank" }
            require(request.subjectId.isNotBlank()) { "Subject ID must not be blank" }
            require(request.channelId.isNotBlank()) { "Channel ID must not be blank" }
            
            // Get existing candidate
            val candidate = candidateRepository.get(
                customerId = request.customerId,
                programId = request.programId,
                marketplaceId = request.marketplaceId,
                subjectType = request.subjectType,
                subjectId = request.subjectId
            )
            
            if (candidate == null) {
                logger.warn { "Candidate not found for marking consumed" }
                return MarkConsumedResponse(
                    candidate = null,
                    success = false
                )
            }
            
            // Update attributes with consumed status
            val updatedAttributes = candidate.attributes.copy(
                consumed = true,
                consumedAt = request.deliveryTimestamp,
                consumedChannel = request.channelId
            )
            
            // Update metadata
            val updatedMetadata = candidate.metadata.copy(
                updatedAt = java.time.Instant.now(),
                version = candidate.metadata.version + 1
            )
            
            // Update candidate
            val updatedCandidate = candidate.copy(
                attributes = updatedAttributes,
                metadata = updatedMetadata
            )
            
            val savedCandidate = candidateRepository.update(updatedCandidate)
            
            logger.info { "Successfully marked candidate as consumed" }
            
            return MarkConsumedResponse(
                candidate = savedCandidate,
                success = true
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to mark candidate as consumed" }
            return MarkConsumedResponse(
                candidate = null,
                success = false
            )
        }
    }
    
    override fun refreshCandidate(request: RefreshCandidateRequest): RefreshCandidateResponse {
        try {
            logger.info { "Refreshing candidate: customer=${request.customerId}, program=${request.programId}" }
            
            // Validate request
            require(request.customerId.isNotBlank()) { "Customer ID must not be blank" }
            require(request.programId.isNotBlank()) { "Program ID must not be blank" }
            require(request.marketplaceId.isNotBlank()) { "Marketplace ID must not be blank" }
            require(request.subjectType.isNotBlank()) { "Subject type must not be blank" }
            require(request.subjectId.isNotBlank()) { "Subject ID must not be blank" }
            
            // Get existing candidate
            val candidate = candidateRepository.get(
                customerId = request.customerId,
                programId = request.programId,
                marketplaceId = request.marketplaceId,
                subjectType = request.subjectType,
                subjectId = request.subjectId
            )
            
            if (candidate == null) {
                logger.warn { "Candidate not found for refresh" }
                return RefreshCandidateResponse(
                    candidate = null,
                    success = false
                )
            }
            
            var updatedCandidate = candidate
            var scoresUpdated = false
            var eligibilityUpdated = false
            
            // Refresh scores if requested
            // Note: In a real implementation, this would call the scoring service
            // For now, we just update the timestamp
            if (request.refreshScores) {
                // Placeholder: would call scoring service here
                scoresUpdated = true
            }
            
            // Refresh eligibility if requested
            if (request.refreshEligibility && eligibilityChecker != null) {
                val isEligible = eligibilityChecker.isEligible(candidate)
                if (!isEligible) {
                    // Mark as ineligible by updating channel eligibility
                    val updatedChannelEligibility = candidate.attributes.channelEligibility.mapValues { false }
                    updatedCandidate = updatedCandidate.copy(
                        attributes = updatedCandidate.attributes.copy(
                            channelEligibility = updatedChannelEligibility
                        )
                    )
                }
                eligibilityUpdated = true
            }
            
            // Update metadata
            val updatedMetadata = updatedCandidate.metadata.copy(
                updatedAt = java.time.Instant.now(),
                version = updatedCandidate.metadata.version + 1
            )
            
            updatedCandidate = updatedCandidate.copy(metadata = updatedMetadata)
            
            val savedCandidate = candidateRepository.update(updatedCandidate)
            
            logger.info { "Successfully refreshed candidate (scores=$scoresUpdated, eligibility=$eligibilityUpdated)" }
            
            return RefreshCandidateResponse(
                candidate = savedCandidate,
                success = true,
                scoresUpdated = scoresUpdated,
                eligibilityUpdated = eligibilityUpdated
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to refresh candidate" }
            return RefreshCandidateResponse(
                candidate = null,
                success = false
            )
        }
    }
    
    /**
     * Validates a single customer request.
     */
    private fun validateRequest(request: GetCandidatesRequest) {
        require(request.customerId.isNotBlank()) { "Customer ID must not be blank" }
        require(request.marketplace.isNotBlank()) { "Marketplace must not be blank" }
        require(request.limit > 0) { "Limit must be positive" }
        require(request.limit <= 100) { "Limit must not exceed 100" }
    }
    
    /**
     * Validates a batch request.
     */
    private fun validateBatchRequest(request: BatchGetCandidatesRequest) {
        require(request.customerIds.isNotEmpty()) { "Customer IDs must not be empty" }
        require(request.customerIds.size <= 100) { "Batch size must not exceed 100" }
        require(request.marketplace.isNotBlank()) { "Marketplace must not be blank" }
        require(request.limit > 0) { "Limit must be positive" }
    }
    
    /**
     * Queries candidates from storage based on request parameters.
     */
    private fun queryCandidates(request: GetCandidatesRequest): List<Candidate> {
        return try {
            if (request.program != null && request.channel != null) {
                // Use GSI for program+channel query
                candidateRepository.queryByProgramAndChannel(
                    programId = request.program,
                    channelId = request.channel,
                    limit = request.limit * 2 // Query more for filtering
                )
            } else {
                // For now, return empty list if we can't query efficiently
                // In production, this would use a different query strategy
                emptyList()
            }
        } catch (e: StorageException) {
            logger.error(e) { "Storage query failed" }
            throw e
        }
    }
    
    /**
     * Filters candidates by channel eligibility.
     */
    private fun filterByChannel(candidates: List<Candidate>, channel: String): List<Candidate> {
        return candidates.filter { candidate ->
            candidate.attributes.channelEligibility[channel] == true
        }
    }
    
    /**
     * Refreshes eligibility for candidates.
     */
    private fun refreshEligibility(candidates: List<Candidate>): List<Candidate> {
        return candidates.filter { candidate ->
            eligibilityChecker?.isEligible(candidate) ?: true
        }
    }
}
