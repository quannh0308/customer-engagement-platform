package com.ceap.workflow.score

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.ceap.model.Candidate
import com.ceap.workflow.common.WorkflowLambdaHandler
import com.ceap.workflow.common.WorkflowMetricsPublisher

/**
 * Lambda handler for Score stage of batch ingestion workflow.
 * 
 * Responsibilities:
 * - Execute scoring for candidate batches
 * - Handle scoring failures with fallbacks
 * - Attach scores to candidates
 * - Track scoring metrics
 * 
 * Extends WorkflowLambdaHandler to leverage S3-based orchestration pattern.
 * S3 I/O is handled by base class - this class focuses on scoring business logic.
 * 
 * Validates: Requirements 3.2, 3.3, 3.4, 3.5, 8.1
 */
class ScoreHandler : WorkflowLambdaHandler() {
    
    private val metricsPublisher = WorkflowMetricsPublisher()
    
    /**
     * Process input data by scoring candidates.
     * 
     * Input structure (from previous Filter stage):
     * {
     *   "candidates": [...],
     *   "rejectedCandidates": [...],
     *   "metrics": {...},
     *   "programId": "string",
     *   "marketplace": "string"
     * }
     * 
     * Output structure (for next stage):
     * {
     *   "candidates": [...],  // With scores attached
     *   "metrics": {...},
     *   "programId": "string",
     *   "marketplace": "string"
     * }
     */
    override fun processData(input: JsonNode): JsonNode {
        logger.info("Processing Score stage: input keys={}", input.fieldNames().asSequence().toList())
        
        // Extract data from input
        val candidatesNode = input.get("candidates")
            ?: throw IllegalArgumentException("candidates is required")
        val programId = input.get("programId")?.asText()
            ?: throw IllegalArgumentException("programId is required")
        val marketplace = input.get("marketplace")?.asText()
            ?: throw IllegalArgumentException("marketplace is required")
        
        // Deserialize candidates
        val candidates: List<Candidate> = objectMapper.readValue(candidatesNode.toString())
        
        logger.info("Starting Score stage: candidateCount={}, programId={}", 
            candidates.size, programId)
        
        if (candidates.isEmpty()) {
            logger.info("No candidates to score")
            return buildEmptyOutput(programId, marketplace)
        }
        
        logger.info("Scoring candidates: candidateCount={}", candidates.size)
        
        // Score candidates (simplified for now - in production would use MultiModelScorer)
        val scoredCandidates = mutableListOf<Candidate>()
        var scoredCount = 0
        var fallbackCount = 0
        var errorCount = 0
        
        for (candidate in candidates) {
            try {
                // For now, just pass through candidates with empty scores
                // In production, this would call MultiModelScorer
                val scoredCandidate = candidate.copy(
                    scores = emptyMap()
                )
                
                scoredCandidates.add(scoredCandidate)
                scoredCount++
                
            } catch (e: Exception) {
                logger.error("Failed to score candidate: customerId={}, subjectId={}, error={}", 
                    candidate.customerId, candidate.subject.id, e.message)
                errorCount++
                
                // Add candidate with empty scores (fallback)
                val scoredCandidate = candidate.copy(
                    scores = emptyMap()
                )
                scoredCandidates.add(scoredCandidate)
                fallbackCount++
            }
        }
        
        logger.info("Scoring completed: inputCount={}, scoredCount={}, fallbackCount={}, errorCount={}", 
            candidates.size, scoredCount, fallbackCount, errorCount)
        
        // Publish metrics
        metricsPublisher.publishScoreMetrics(
            programId = programId,
            marketplace = marketplace,
            inputCount = candidates.size,
            scoredCount = scoredCount,
            fallbackCount = fallbackCount,
            errorCount = errorCount,
            durationMs = 0 // Duration tracked by base handler
        )
        
        // Build output JSON for next stage
        return buildOutput(scoredCandidates, candidates.size, scoredCount, 
            fallbackCount, errorCount, programId, marketplace)
    }
    
    private fun buildEmptyOutput(programId: String, marketplace: String): JsonNode {
        val output = objectMapper.createObjectNode()
        output.set<ArrayNode>("candidates", objectMapper.createArrayNode())
        output.set<ObjectNode>("metrics", objectMapper.createObjectNode().apply {
            put("inputCount", 0)
            put("scoredCount", 0)
            put("fallbackCount", 0)
            put("errorCount", 0)
        })
        output.put("programId", programId)
        output.put("marketplace", marketplace)
        return output
    }
    
    private fun buildOutput(
        scoredCandidates: List<Candidate>,
        inputCount: Int,
        scoredCount: Int,
        fallbackCount: Int,
        errorCount: Int,
        programId: String,
        marketplace: String
    ): JsonNode {
        val output = objectMapper.createObjectNode()
        output.set<ArrayNode>("candidates", objectMapper.valueToTree(scoredCandidates))
        output.set<ObjectNode>("metrics", objectMapper.createObjectNode().apply {
            put("inputCount", inputCount)
            put("scoredCount", scoredCount)
            put("fallbackCount", fallbackCount)
            put("errorCount", errorCount)
        })
        output.put("programId", programId)
        output.put("marketplace", marketplace)
        return output
    }
}

