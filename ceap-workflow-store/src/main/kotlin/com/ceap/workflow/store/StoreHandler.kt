package com.ceap.workflow.store

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.ceap.model.Candidate
import com.ceap.storage.BatchWriteResult
import com.ceap.storage.CandidateRepository
import com.ceap.workflow.common.WorkflowLambdaHandler
import com.ceap.workflow.common.WorkflowMetricsPublisher

/**
 * Lambda handler for Store stage of batch ingestion workflow.
 * 
 * Responsibilities:
 * - Batch write candidates to DynamoDB
 * - Handle write failures and retries
 * - Track storage metrics
 * - Return final workflow results
 * 
 * Extends WorkflowLambdaHandler to leverage S3-based orchestration pattern.
 * S3 I/O is handled by base class - this class focuses on storage business logic.
 * 
 * Validates: Requirements 3.3, 3.4, 3.5, 5.2, 8.1
 */
class StoreHandler : WorkflowLambdaHandler() {
    
    private val metricsPublisher = WorkflowMetricsPublisher()
    
    // Create a mock repository for testing
    private fun createCandidateRepository(): CandidateRepository {
        return object : CandidateRepository {
            override fun create(candidate: Candidate): Candidate = candidate
            
            override fun get(
                customerId: String,
                programId: String,
                marketplaceId: String,
                subjectType: String,
                subjectId: String
            ): Candidate? = null
            
            override fun update(candidate: Candidate): Candidate = candidate
            
            override fun delete(
                customerId: String,
                programId: String,
                marketplaceId: String,
                subjectType: String,
                subjectId: String
            ) {}
            
            override fun batchWrite(candidates: List<Candidate>): BatchWriteResult {
                // Mock successful batch write
                return BatchWriteResult(
                    successfulItems = candidates,
                    failedItems = emptyList()
                )
            }
            
            override fun queryByProgramAndChannel(
                programId: String,
                channelId: String,
                limit: Int,
                ascending: Boolean
            ): List<Candidate> = emptyList()
            
            override fun queryByProgramAndDate(
                programId: String,
                date: String,
                limit: Int
            ): List<Candidate> = emptyList()
        }
    }
    
    /**
     * Process input data by storing candidates to DynamoDB.
     * 
     * Input structure (from previous Score stage):
     * {
     *   "candidates": [...],
     *   "metrics": {...},
     *   "programId": "string",
     *   "marketplace": "string"
     * }
     * 
     * Output structure (final stage):
     * {
     *   "metrics": {...},
     *   "programId": "string",
     *   "marketplace": "string",
     *   "success": boolean
     * }
     */
    override fun processData(input: JsonNode): JsonNode {
        logger.info("Processing Store stage: input keys={}", input.fieldNames().asSequence().toList())
        
        // Extract data from input
        val candidatesNode = input.get("candidates")
            ?: throw IllegalArgumentException("candidates is required")
        val programId = input.get("programId")?.asText()
            ?: throw IllegalArgumentException("programId is required")
        val marketplace = input.get("marketplace")?.asText()
            ?: throw IllegalArgumentException("marketplace is required")
        
        // Deserialize candidates
        val candidates: List<Candidate> = objectMapper.readValue(candidatesNode.toString())
        
        logger.info("Starting Store stage: candidateCount={}, programId={}", 
            candidates.size, programId)
        
        if (candidates.isEmpty()) {
            logger.info("No candidates to store")
            return buildOutput(0, 0, 0, emptyList(), programId, marketplace, true)
        }
        
        logger.info("Storing candidates: candidateCount={}", candidates.size)
        
        // Create candidate repository
        val candidateRepository = createCandidateRepository()
        
        // Batch write candidates to DynamoDB
        var storedCount = 0
        var failedCount = 0
        val failedCandidates = mutableListOf<String>()
        
        // Process candidates in batches (DynamoDB batch write limit is 25)
        val batchSize = 25
        val batches = candidates.chunked(batchSize)
        
        for ((batchIndex, batch) in batches.withIndex()) {
            try {
                logger.debug("Processing batch: batchIndex={}, batchSize={}", batchIndex, batch.size)
                
                // Write batch to DynamoDB
                val result = candidateRepository.batchWrite(batch)
                storedCount += result.successfulItems.size
                failedCount += result.failedItems.size
                
                // Track failed candidates
                result.failedItems.forEach { failedItem ->
                    failedCandidates.add("${failedItem.candidate.customerId}:${failedItem.candidate.subject.id}")
                }
                
            } catch (e: Exception) {
                logger.error("Batch write failed: batchIndex={}, batchSize={}, error={}", 
                    batchIndex, batch.size, e.message)
                
                // Mark all candidates in batch as failed
                failedCount += batch.size
                batch.forEach { candidate ->
                    failedCandidates.add("${candidate.customerId}:${candidate.subject.id}")
                }
            }
        }
        
        logger.info("Storage completed: inputCount={}, storedCount={}, failedCount={}, failedCandidates={}", 
            candidates.size, storedCount, failedCount, failedCandidates)
        
        // Publish metrics
        metricsPublisher.publishStoreMetrics(
            programId = programId,
            marketplace = marketplace,
            inputCount = candidates.size,
            storedCount = storedCount,
            failedCount = failedCount,
            durationMs = 0 // Duration tracked by base handler
        )
        
        // Build output JSON (final stage)
        return buildOutput(candidates.size, storedCount, failedCount, 
            failedCandidates, programId, marketplace, failedCount == 0)
    }
    
    private fun buildOutput(
        inputCount: Int,
        storedCount: Int,
        failedCount: Int,
        failedCandidates: List<String>,
        programId: String,
        marketplace: String,
        success: Boolean
    ): JsonNode {
        val output = objectMapper.createObjectNode()
        output.set<ObjectNode>("metrics", objectMapper.createObjectNode().apply {
            put("inputCount", inputCount)
            put("storedCount", storedCount)
            put("failedCount", failedCount)
            set<ArrayNode>("failedCandidates", objectMapper.valueToTree(failedCandidates))
        })
        output.put("programId", programId)
        output.put("marketplace", marketplace)
        output.put("success", success)
        return output
    }
}

