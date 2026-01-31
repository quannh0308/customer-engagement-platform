package com.ceap.workflow.filter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.ceap.filters.*
import com.ceap.model.Candidate
import com.ceap.model.config.FilterChainConfig
import com.ceap.model.config.FilterConfig
import com.ceap.workflow.common.WorkflowLambdaHandler
import com.ceap.workflow.common.WorkflowMetricsPublisher

/**
 * Lambda handler for Filter stage of batch ingestion workflow.
 * 
 * Responsibilities:
 * - Execute filter chain on candidate batches
 * - Track rejection reasons for each filter
 * - Pass eligible candidates to next stage
 * - Publish filter metrics
 * 
 * Extends WorkflowLambdaHandler to leverage S3-based orchestration pattern.
 * S3 I/O is handled by base class - this class focuses on filtering business logic.
 * 
 * Validates: Requirements 3.3, 3.4, 3.5, 4.1, 4.2, 8.1
 */
class FilterHandler : WorkflowLambdaHandler() {
    
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
    
    /**
     * Process input data by executing filter chain on candidates.
     * 
     * Input structure (from previous ETL stage):
     * {
     *   "candidates": [...],
     *   "metrics": {...},
     *   "programId": "string",
     *   "marketplace": "string"
     * }
     * 
     * Output structure (for next stage):
     * {
     *   "candidates": [...],  // Only passed candidates
     *   "rejectedCandidates": [...],
     *   "metrics": {...},
     *   "programId": "string",
     *   "marketplace": "string"
     * }
     */
    override fun processData(input: JsonNode): JsonNode {
        logger.info("Processing Filter stage: input keys={}", input.fieldNames().asSequence().toList())
        
        // Extract data from input
        val candidatesNode = input.get("candidates")
            ?: throw IllegalArgumentException("candidates is required")
        val programId = input.get("programId")?.asText()
            ?: throw IllegalArgumentException("programId is required")
        val marketplace = input.get("marketplace")?.asText()
            ?: throw IllegalArgumentException("marketplace is required")
        
        // Deserialize candidates
        val candidates: List<Candidate> = objectMapper.readValue(candidatesNode.toString())
        
        logger.info("Starting Filter stage: candidateCount={}, programId={}", 
            candidates.size, programId)
        
        if (candidates.isEmpty()) {
            logger.info("No candidates to filter")
            return buildEmptyOutput(programId, marketplace)
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
        metricsPublisher.publishFilterMetrics(
            programId = programId,
            marketplace = marketplace,
            inputCount = candidates.size,
            passedCount = passedCandidates.size,
            rejectedCount = rejectedCandidates.size,
            rejectionReasons = rejectionReasons,
            durationMs = 0 // Duration tracked by base handler
        )
        
        // Build output JSON for next stage
        return buildOutput(passedCandidates, rejectedCandidates, candidates.size, 
            passedCandidates.size, rejectedCandidates.size, rejectionReasons, 
            programId, marketplace)
    }
    
    private fun buildEmptyOutput(programId: String, marketplace: String): JsonNode {
        val output = objectMapper.createObjectNode()
        output.set<ArrayNode>("candidates", objectMapper.createArrayNode())
        output.set<ArrayNode>("rejectedCandidates", objectMapper.createArrayNode())
        output.set<ObjectNode>("metrics", objectMapper.createObjectNode().apply {
            put("inputCount", 0)
            put("passedCount", 0)
            put("rejectedCount", 0)
            set<ObjectNode>("rejectionReasons", objectMapper.createObjectNode())
        })
        output.put("programId", programId)
        output.put("marketplace", marketplace)
        return output
    }
    
    private fun buildOutput(
        passedCandidates: List<Candidate>,
        rejectedCandidates: List<RejectedCandidateInfo>,
        inputCount: Int,
        passedCount: Int,
        rejectedCount: Int,
        rejectionReasons: Map<String, Int>,
        programId: String,
        marketplace: String
    ): JsonNode {
        val output = objectMapper.createObjectNode()
        output.set<ArrayNode>("candidates", objectMapper.valueToTree(passedCandidates))
        output.set<ArrayNode>("rejectedCandidates", objectMapper.valueToTree(rejectedCandidates))
        output.set<ObjectNode>("metrics", objectMapper.createObjectNode().apply {
            put("inputCount", inputCount)
            put("passedCount", passedCount)
            put("rejectedCount", rejectedCount)
            set<ObjectNode>("rejectionReasons", objectMapper.valueToTree(rejectionReasons))
        })
        output.put("programId", programId)
        output.put("marketplace", marketplace)
        return output
    }
}

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

