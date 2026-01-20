package com.ceap.workflow.completion

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ceap.workflow.common.WorkflowMetricsPublisher
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.eventbridge.EventBridgeClient
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry
import java.time.Instant

/**
 * Lambda handler for workflow completion stage.
 * 
 * Responsibilities:
 * - Publish completion metrics
 * - Trigger downstream processes (data warehouse export)
 * - Send completion notifications
 * 
 * Validates: Requirements 8.6
 */
class CompletionHandler : RequestHandler<CompletionInput, CompletionResponse> {
    
    private val logger = LoggerFactory.getLogger(CompletionHandler::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val metricsPublisher = WorkflowMetricsPublisher()
    private val eventBridgeClient: EventBridgeClient = EventBridgeClient.create()
    
    override fun handleRequest(input: CompletionInput, context: Context): CompletionResponse {
        val requestId = context.awsRequestId
        logger.info("Starting workflow completion: requestId={}, programId={}, executionId={}", 
            requestId, input.programId, input.executionId)
        
        try {
            val programId = input.programId
            val marketplace = input.marketplace
            val executionId = input.executionId
            val workflowStartTime = input.workflowStartTime
            
            // Calculate totals from all stages
            val totalProcessed = input.etlMetrics.processedCount
            val totalStored = input.storeMetrics.storedCount
            val totalRejected = input.filterMetrics.rejectedCount
            val totalErrors = input.etlMetrics.errorCount + 
                             input.scoreMetrics.errorCount + 
                             input.storeMetrics.failedCount
            
            val totalDuration = System.currentTimeMillis() - workflowStartTime
            val success = input.storeMetrics.success && totalErrors == 0
            
            logger.info("Workflow summary: programId={}, marketplace={}, totalProcessed={}, totalStored={}, totalRejected={}, totalErrors={}, durationMs={}, success={}", 
                programId, marketplace, totalProcessed, totalStored, totalRejected, totalErrors, totalDuration, success)
            
            // Publish completion metrics
            metricsPublisher.publishWorkflowCompletionMetrics(
                programId = programId,
                marketplace = marketplace,
                totalProcessed = totalProcessed,
                totalStored = totalStored,
                totalRejected = totalRejected,
                totalErrors = totalErrors,
                totalDurationMs = totalDuration,
                success = success
            )
            
            // Trigger downstream processes
            triggerDownstreamProcesses(
                programId = programId,
                marketplace = marketplace,
                executionId = executionId,
                totalStored = totalStored,
                success = success
            )
            
            logger.info("Workflow completion finished: programId={}, success={}", programId, success)
            
            return CompletionResponse(
                success = success,
                totalProcessed = totalProcessed,
                totalStored = totalStored,
                totalRejected = totalRejected,
                totalErrors = totalErrors,
                durationMs = totalDuration,
                programId = programId,
                marketplace = marketplace,
                executionId = executionId
            )
            
        } catch (e: Exception) {
            logger.error("Workflow completion failed", e)
            throw RuntimeException("Workflow completion failed: ${e.message}", e)
        }
    }
    
    /**
     * Trigger downstream processes like data warehouse export
     */
    private fun triggerDownstreamProcesses(
        programId: String,
        marketplace: String,
        executionId: String,
        totalStored: Int,
        success: Boolean
    ) {
        try {
            // Create event for data warehouse export
            val eventDetail = mapOf(
                "programId" to programId,
                "marketplace" to marketplace,
                "executionId" to executionId,
                "totalStored" to totalStored,
                "success" to success,
                "timestamp" to Instant.now().toString()
            )
            
            val event = PutEventsRequestEntry.builder()
                .source("solicitation.workflow")
                .detailType("WorkflowCompleted")
                .detail(objectMapper.writeValueAsString(eventDetail))
                .build()
            
            val request = PutEventsRequest.builder()
                .entries(event)
                .build()
            
            eventBridgeClient.putEvents(request)
            
            logger.info("Triggered downstream processes: programId={}, marketplace={}, executionId={}", 
                programId, marketplace, executionId)
            
        } catch (e: Exception) {
            logger.error("Failed to trigger downstream processes", e)
            // Don't fail the workflow if downstream trigger fails
        }
    }
}

/**
 * Input to completion handler
 */
data class CompletionInput(
    val programId: String,
    val marketplace: String,
    val executionId: String,
    val workflowStartTime: Long,
    val etlMetrics: ETLMetricsInput,
    val filterMetrics: FilterMetricsInput,
    val scoreMetrics: ScoreMetricsInput,
    val storeMetrics: StoreMetricsInput
)

data class ETLMetricsInput(
    val processedCount: Int,
    val transformedCount: Int,
    val errorCount: Int
)

data class FilterMetricsInput(
    val inputCount: Int,
    val passedCount: Int,
    val rejectedCount: Int
)

data class ScoreMetricsInput(
    val inputCount: Int,
    val scoredCount: Int,
    val fallbackCount: Int,
    val errorCount: Int
)

data class StoreMetricsInput(
    val inputCount: Int,
    val storedCount: Int,
    val failedCount: Int,
    val success: Boolean
)

/**
 * Response from completion handler
 */
data class CompletionResponse(
    val success: Boolean,
    val totalProcessed: Int,
    val totalStored: Int,
    val totalRejected: Int,
    val totalErrors: Int,
    val durationMs: Long,
    val programId: String,
    val marketplace: String,
    val executionId: String
)
