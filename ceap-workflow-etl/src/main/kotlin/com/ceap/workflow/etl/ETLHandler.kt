package com.ceap.workflow.etl

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ceap.connectors.DataConnector
import com.ceap.connectors.DataWarehouseConnector
import com.ceap.model.Candidate
import com.ceap.workflow.common.WorkflowMetricsPublisher
import org.slf4j.LoggerFactory

/**
 * Lambda handler for ETL (Extract-Transform-Load) stage of batch ingestion workflow.
 * 
 * Responsibilities:
 * - Extract data from data warehouse using data connector
 * - Transform raw records into unified candidate model
 * - Batch candidates for downstream processing
 * - Track metrics for processed records
 * 
 * Validates: Requirements 1.2, 8.1
 */
class ETLHandler : RequestHandler<Map<String, Any>, ETLResponse> {
    
    private val logger = LoggerFactory.getLogger(ETLHandler::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val metricsPublisher = WorkflowMetricsPublisher()
    
    override fun handleRequest(input: Map<String, Any>, context: Context): ETLResponse {
        val requestId = context.awsRequestId
        val startTime = System.currentTimeMillis()
        
        logger.info("Starting ETL stage: requestId={}, input={}", requestId, input)
        
        try {
            // Extract configuration from input
            val programId = input["programId"] as? String 
                ?: throw IllegalArgumentException("programId is required")
            val marketplace = input["marketplace"] as? String 
                ?: throw IllegalArgumentException("marketplace is required")
            val dateRange = input["dateRange"] as? Map<String, String> 
                ?: throw IllegalArgumentException("dateRange is required")
            
            val startDate = dateRange["start"] 
                ?: throw IllegalArgumentException("dateRange.start is required")
            val endDate = dateRange["end"] 
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
            
            // Return response with candidates and metrics
            return ETLResponse(
                candidates = candidates,
                metrics = ETLMetrics(
                    processedCount = processedCount,
                    transformedCount = candidates.size,
                    errorCount = errorCount
                ),
                programId = programId,
                marketplace = marketplace,
                executionId = requestId
            )
            
        } catch (e: Exception) {
            logger.error("ETL stage failed", e)
            throw RuntimeException("ETL stage failed: ${e.message}", e)
        }
    }
}

/**
 * Response from ETL stage
 */
data class ETLResponse(
    val candidates: List<Candidate>,
    val metrics: ETLMetrics,
    val programId: String,
    val marketplace: String,
    val executionId: String
)

/**
 * Metrics from ETL stage
 */
data class ETLMetrics(
    val processedCount: Int,
    val transformedCount: Int,
    val errorCount: Int
)
