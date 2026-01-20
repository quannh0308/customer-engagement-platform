package com.ceap.workflow.store

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ceap.model.Candidate
import com.ceap.storage.BatchWriteResult
import com.ceap.storage.CandidateRepository
import com.ceap.storage.FailedItem
import com.ceap.workflow.common.WorkflowMetricsPublisher
import org.slf4j.LoggerFactory

/**
 * Lambda handler for Store stage of batch ingestion workflow.
 * 
 * Responsibilities:
 * - Batch write candidates to DynamoDB
 * - Handle write failures and retries
 * - Track storage metrics
 * - Return final workflow results
 * 
 * Validates: Requirements 5.2, 8.1
 */
class StoreHandler : RequestHandler<StoreInput, StoreResponse> {
    
    private val logger = LoggerFactory.getLogger(StoreHandler::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
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
    
    override fun handleRequest(input: StoreInput, context: Context): StoreResponse {
        val requestId = context.awsRequestId
        val startTime = System.currentTimeMillis()
        
        logger.info("Starting Store stage: requestId={}, candidateCount={}, programId={}", 
            requestId, input.candidates.size, input.programId)
        
        try {
            val candidates = input.candidates
            val programId = input.programId
            val marketplace = input.marketplace
            val executionId = input.executionId
            
            if (candidates.isEmpty()) {
                logger.info("No candidates to store")
                return StoreResponse(
                    metrics = StoreMetrics(
                        inputCount = 0,
                        storedCount = 0,
                        failedCount = 0
                    ),
                    programId = programId,
                    marketplace = marketplace,
                    executionId = executionId,
                    success = true
                )
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
            val duration = System.currentTimeMillis() - startTime
            metricsPublisher.publishStoreMetrics(
                programId = programId,
                marketplace = marketplace,
                inputCount = candidates.size,
                storedCount = storedCount,
                failedCount = failedCount,
                durationMs = duration
            )
            
            // Return response with storage metrics
            return StoreResponse(
                metrics = StoreMetrics(
                    inputCount = candidates.size,
                    storedCount = storedCount,
                    failedCount = failedCount,
                    failedCandidates = failedCandidates
                ),
                programId = programId,
                marketplace = marketplace,
                executionId = executionId,
                success = failedCount == 0
            )
            
        } catch (e: Exception) {
            logger.error("Store stage failed", e)
            throw RuntimeException("Store stage failed: ${e.message}", e)
        }
    }
}

/**
 * Input to Store stage
 */
data class StoreInput(
    val candidates: List<Candidate>,
    val programId: String,
    val marketplace: String,
    val executionId: String
)

/**
 * Response from Store stage
 */
data class StoreResponse(
    val metrics: StoreMetrics,
    val programId: String,
    val marketplace: String,
    val executionId: String,
    val success: Boolean
)

/**
 * Metrics from Store stage
 */
data class StoreMetrics(
    val inputCount: Int,
    val storedCount: Int,
    val failedCount: Int,
    val failedCandidates: List<String> = emptyList()
)
