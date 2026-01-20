package com.ceap.workflow.filter

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ceap.filters.*
import com.ceap.model.Candidate
import com.ceap.model.RejectionRecord
import com.ceap.model.config.FilterChainConfig
import com.ceap.model.config.FilterConfig
import com.ceap.workflow.common.WorkflowMetricsPublisher
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Lambda handler for Filter stage of batch ingestion workflow.
 * 
 * Responsibilities:
 * - Execute filter chain on candidate batches
 * - Track rejection reasons for each filter
 * - Pass eligible candidates to next stage
 * - Publish filter metrics
 * 
 * Validates: Requirements 4.1, 4.2, 8.1
 */
class FilterHandler : RequestHandler<FilterInput, FilterResponse> {
    
    private val logger = LoggerFactory.getLogger(FilterHandler::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val metricsPublisher = WorkflowMetricsPublisher()
    
    // Create default filter chain with all filters
    private fun createFilterChainExecutor(): FilterChainExecutor {
        val filters = listOf<Filter>(
            EligibilityFilter(),
            TrustFilter(),
            QualityFilter(),
            BusinessRuleFilter()
        )
        
        val config = FilterChainConfig(
            filters = listOf(
                FilterConfig(
                    filterId = "eligibility",
                    filterType = "eligibility",
                    enabled = true,
                    parameters = emptyMap(),
                    order = 0
                ),
                FilterConfig(
                    filterId = "trust",
                    filterType = "trust",
                    enabled = true,
                    parameters = emptyMap(),
                    order = 1
                ),
                FilterConfig(
                    filterId = "quality",
                    filterType = "quality",
                    enabled = true,
                    parameters = emptyMap(),
                    order = 2
                ),
                FilterConfig(
                    filterId = "business_rule",
                    filterType = "business_rule",
                    enabled = true,
                    parameters = emptyMap(),
                    order = 3
                )
            )
        )
        
        return FilterChainExecutor(filters, config)
    }
    
    override fun handleRequest(input: FilterInput, context: Context): FilterResponse {
        val requestId = context.awsRequestId
        val startTime = System.currentTimeMillis()
        
        logger.info("Starting Filter stage: requestId={}, candidateCount={}, programId={}", 
            requestId, input.candidates.size, input.programId)
        
        try {
            val candidates = input.candidates
            val programId = input.programId
            val marketplace = input.marketplace
            val executionId = input.executionId
            
            if (candidates.isEmpty()) {
                logger.info("No candidates to filter")
                return FilterResponse(
                    candidates = emptyList(),
                    rejectedCandidates = emptyList(),
                    metrics = FilterMetrics(
                        inputCount = 0,
                        passedCount = 0,
                        rejectedCount = 0,
                        rejectionReasons = emptyMap()
                    ),
                    programId = programId,
                    marketplace = marketplace,
                    executionId = executionId
                )
            }
            
            logger.info("Executing filter chain: candidateCount={}", candidates.size)
            
            // Create filter chain executor
            val filterChainExecutor = createFilterChainExecutor()
            
            // Execute filter chain on each candidate
            val passedCandidates = mutableListOf<Candidate>()
            val rejectedCandidates = mutableListOf<RejectedCandidateInfo>()
            val rejectionReasons = mutableMapOf<String, Int>()
            
            for (candidate in candidates) {
                val result = filterChainExecutor.execute(candidate)
                
                if (result.passed) {
                    passedCandidates.add(result.candidate)
                } else {
                    // Track rejection reasons
                    for (rejection in result.rejectionHistory) {
                        val reasonCode = rejection.reasonCode
                        rejectionReasons[reasonCode] = rejectionReasons.getOrDefault(reasonCode, 0) + 1
                        
                        rejectedCandidates.add(
                            RejectedCandidateInfo(
                                customerId = candidate.customerId,
                                subjectId = candidate.subject.id,
                                filterId = rejection.filterId,
                                reason = rejection.reason,
                                reasonCode = rejection.reasonCode
                            )
                        )
                    }
                }
            }
            
            logger.info("Filter chain completed: inputCount={}, passedCount={}, rejectedCount={}, rejectionReasons={}", 
                candidates.size, passedCandidates.size, rejectedCandidates.size, rejectionReasons)
            
            // Publish metrics
            val duration = System.currentTimeMillis() - startTime
            metricsPublisher.publishFilterMetrics(
                programId = programId,
                marketplace = marketplace,
                inputCount = candidates.size,
                passedCount = passedCandidates.size,
                rejectedCount = rejectedCandidates.size,
                rejectionReasons = rejectionReasons,
                durationMs = duration
            )
            
            // Return response with passed candidates and metrics
            return FilterResponse(
                candidates = passedCandidates,
                rejectedCandidates = rejectedCandidates,
                metrics = FilterMetrics(
                    inputCount = candidates.size,
                    passedCount = passedCandidates.size,
                    rejectedCount = rejectedCandidates.size,
                    rejectionReasons = rejectionReasons
                ),
                programId = programId,
                marketplace = marketplace,
                executionId = executionId
            )
            
        } catch (e: Exception) {
            logger.error("Filter stage failed", e)
            throw RuntimeException("Filter stage failed: ${e.message}", e)
        }
    }
}

/**
 * Input to Filter stage
 */
data class FilterInput(
    val candidates: List<Candidate>,
    val programId: String,
    val marketplace: String,
    val executionId: String
)

/**
 * Response from Filter stage
 */
data class FilterResponse(
    val candidates: List<Candidate>,
    val rejectedCandidates: List<RejectedCandidateInfo>,
    val metrics: FilterMetrics,
    val programId: String,
    val marketplace: String,
    val executionId: String
)

/**
 * Information about a rejected candidate
 */
data class RejectedCandidateInfo(
    val customerId: String,
    val subjectId: String,
    val filterId: String,
    val reason: String,
    val reasonCode: String
)

/**
 * Metrics from Filter stage
 */
data class FilterMetrics(
    val inputCount: Int,
    val passedCount: Int,
    val rejectedCount: Int,
    val rejectionReasons: Map<String, Int>
)
