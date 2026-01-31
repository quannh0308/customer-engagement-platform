package com.ceap.workflow.etl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.ceap.model.Candidate
import com.ceap.workflow.common.WorkflowLambdaHandler
import com.ceap.workflow.common.WorkflowMetricsPublisher

/**
 * Lambda handler for ETL (Extract-Transform-Load) stage of batch ingestion workflow.
 * 
 * Responsibilities:
 * - Extract data from data warehouse using data connector
 * - Transform raw records into unified candidate model
 * - Batch candidates for downstream processing
 * - Track metrics for processed records
 * 
 * Extends WorkflowLambdaHandler to leverage S3-based orchestration pattern.
 * S3 I/O is handled by base class - this class focuses on ETL business logic.
 * 
 * Validates: Requirements 1.2, 3.3, 3.4, 3.5, 8.1
 */
class ETLHandler : WorkflowLambdaHandler() {
    
    private val metricsPublisher = WorkflowMetricsPublisher()
    
    /**
     * Process input data by extracting and transforming records into candidates.
     * 
     * Input structure (from initialData in first stage):
     * {
     *   "programId": "string",
     *   "marketplace": "string",
     *   "dateRange": {
     *     "start": "string",
     *     "end": "string"
     *   }
     * }
     * 
     * Output structure (for next stage):
     * {
     *   "candidates": [...],
     *   "metrics": {...},
     *   "programId": "string",
     *   "marketplace": "string",
     *   "executionId": "string"
     * }
     */
    override fun processData(input: JsonNode): JsonNode {
        val startTime = System.currentTimeMillis()
        
        logger.info("Processing ETL stage: input={}", input)
        
        // Extract configuration from input
        val programId = input.get("programId")?.asText()
            ?: throw IllegalArgumentException("programId is required")
        val marketplace = input.get("marketplace")?.asText()
            ?: throw IllegalArgumentException("marketplace is required")
        val dateRange = input.get("dateRange")
            ?: throw IllegalArgumentException("dateRange is required")
        
        val startDate = dateRange.get("start")?.asText()
            ?: throw IllegalArgumentException("dateRange.start is required")
        val endDate = dateRange.get("end")?.asText()
            ?: throw IllegalArgumentException("dateRange.end is required")
        
        logger.info("Extracting data: programId={}, marketplace={}, startDate={}, endDate={}", 
            programId, marketplace, startDate, endDate)
        
        // Extract and transform data using data connector
        val candidates = mutableListOf<Candidate>()
        var processedCount = 0
        var errorCount = 0
        
        // In a real implementation, this would iterate through data warehouse records
        // For now, we'll simulate the extraction process
        try {
            // TODO: Implement actual data extraction from data warehouse
            // val records = dataConnector.extractData(config, dateRange)
            // for (record in records) {
            //     try {
            //         val candidate = dataConnector.transformToCandidate(record, config)
            //         candidates.add(candidate)
            //         processedCount++
            //     } catch (e: Exception) {
            //         logger.error("Failed to transform record", e)
            //         errorCount++
            //     }
            // }
            
            logger.info("Data extraction completed: processedCount={}, errorCount={}, candidateCount={}", 
                processedCount, errorCount, candidates.size)
            
        } catch (e: Exception) {
            logger.error("Data extraction failed", e)
            throw e
        }
        
        // Publish metrics
        val duration = System.currentTimeMillis() - startTime
        metricsPublisher.publishETLMetrics(
            programId = programId,
            marketplace = marketplace,
            processedCount = processedCount,
            transformedCount = candidates.size,
            errorCount = errorCount,
            durationMs = duration
        )
        
        // Build output JSON for next stage
        val output = objectMapper.createObjectNode()
        output.set<ArrayNode>("candidates", objectMapper.valueToTree(candidates))
        output.set<ObjectNode>("metrics", objectMapper.createObjectNode().apply {
            put("processedCount", processedCount)
            put("transformedCount", candidates.size)
            put("errorCount", errorCount)
        })
        output.put("programId", programId)
        output.put("marketplace", marketplace)
        
        return output
    }
}

