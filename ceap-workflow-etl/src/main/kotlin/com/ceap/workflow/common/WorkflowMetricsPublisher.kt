package com.ceap.workflow.common

import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.Dimension
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import java.time.Instant

/**
 * Publisher for workflow metrics to CloudWatch.
 * 
 * Publishes metrics at each workflow stage including:
 * - Processed counts
 * - Passed counts
 * - Rejected counts
 * - Error counts
 * - Stage duration
 * 
 * Validates: Requirements 8.4, 12.1
 */
class WorkflowMetricsPublisher {
    
    private val logger = LoggerFactory.getLogger(WorkflowMetricsPublisher::class.java)
    private val cloudWatchClient: CloudWatchClient = CloudWatchClient.create()
    private val namespace = "CeapPlatform/Workflow"
    
    /**
     * Publish ETL stage metrics
     */
    fun publishETLMetrics(
        programId: String,
        marketplace: String,
        processedCount: Int,
        transformedCount: Int,
        errorCount: Int,
        durationMs: Long
    ) {
        try {
            val dimensions = listOf(
                createDimension("ProgramId", programId),
                createDimension("Marketplace", marketplace),
                createDimension("Stage", "ETL")
            )
            
            val metrics = listOf(
                createMetric("ProcessedCount", processedCount.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("TransformedCount", transformedCount.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("ErrorCount", errorCount.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("StageDuration", durationMs.toDouble(), StandardUnit.MILLISECONDS, dimensions)
            )
            
            publishMetrics(metrics)
            
            logger.info("Published ETL metrics", mapOf(
                "programId" to programId,
                "marketplace" to marketplace,
                "processedCount" to processedCount,
                "transformedCount" to transformedCount,
                "errorCount" to errorCount
            ))
            
        } catch (e: Exception) {
            logger.error("Failed to publish ETL metrics", e)
        }
    }
    
    /**
     * Publish Filter stage metrics
     */
    fun publishFilterMetrics(
        programId: String,
        marketplace: String,
        inputCount: Int,
        passedCount: Int,
        rejectedCount: Int,
        rejectionReasons: Map<String, Int>,
        durationMs: Long
    ) {
        try {
            val dimensions = listOf(
                createDimension("ProgramId", programId),
                createDimension("Marketplace", marketplace),
                createDimension("Stage", "Filter")
            )
            
            val metrics = mutableListOf(
                createMetric("InputCount", inputCount.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("PassedCount", passedCount.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("RejectedCount", rejectedCount.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("StageDuration", durationMs.toDouble(), StandardUnit.MILLISECONDS, dimensions)
            )
            
            // Add metrics for each rejection reason
            for ((reasonCode, count) in rejectionReasons) {
                val reasonDimensions = dimensions + createDimension("ReasonCode", reasonCode)
                metrics.add(
                    createMetric("RejectionsByReason", count.toDouble(), StandardUnit.COUNT, reasonDimensions)
                )
            }
            
            publishMetrics(metrics)
            
            logger.info("Published Filter metrics", mapOf(
                "programId" to programId,
                "marketplace" to marketplace,
                "inputCount" to inputCount,
                "passedCount" to passedCount,
                "rejectedCount" to rejectedCount
            ))
            
        } catch (e: Exception) {
            logger.error("Failed to publish Filter metrics", e)
        }
    }
    
    /**
     * Publish Score stage metrics
     */
    fun publishScoreMetrics(
        programId: String,
        marketplace: String,
        inputCount: Int,
        scoredCount: Int,
        fallbackCount: Int,
        errorCount: Int,
        durationMs: Long
    ) {
        try {
            val dimensions = listOf(
                createDimension("ProgramId", programId),
                createDimension("Marketplace", marketplace),
                createDimension("Stage", "Score")
            )
            
            val metrics = listOf(
                createMetric("InputCount", inputCount.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("ScoredCount", scoredCount.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("FallbackCount", fallbackCount.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("ErrorCount", errorCount.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("StageDuration", durationMs.toDouble(), StandardUnit.MILLISECONDS, dimensions)
            )
            
            publishMetrics(metrics)
            
            logger.info("Published Score metrics", mapOf(
                "programId" to programId,
                "marketplace" to marketplace,
                "inputCount" to inputCount,
                "scoredCount" to scoredCount,
                "fallbackCount" to fallbackCount
            ))
            
        } catch (e: Exception) {
            logger.error("Failed to publish Score metrics", e)
        }
    }
    
    /**
     * Publish Store stage metrics
     */
    fun publishStoreMetrics(
        programId: String,
        marketplace: String,
        inputCount: Int,
        storedCount: Int,
        failedCount: Int,
        durationMs: Long
    ) {
        try {
            val dimensions = listOf(
                createDimension("ProgramId", programId),
                createDimension("Marketplace", marketplace),
                createDimension("Stage", "Store")
            )
            
            val metrics = listOf(
                createMetric("InputCount", inputCount.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("StoredCount", storedCount.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("FailedCount", failedCount.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("StageDuration", durationMs.toDouble(), StandardUnit.MILLISECONDS, dimensions)
            )
            
            publishMetrics(metrics)
            
            logger.info("Published Store metrics", mapOf(
                "programId" to programId,
                "marketplace" to marketplace,
                "inputCount" to inputCount,
                "storedCount" to storedCount,
                "failedCount" to failedCount
            ))
            
        } catch (e: Exception) {
            logger.error("Failed to publish Store metrics", e)
        }
    }
    
    /**
     * Publish workflow completion metrics
     */
    fun publishWorkflowCompletionMetrics(
        programId: String,
        marketplace: String,
        totalProcessed: Int,
        totalStored: Int,
        totalRejected: Int,
        totalErrors: Int,
        totalDurationMs: Long,
        success: Boolean
    ) {
        try {
            val dimensions = listOf(
                createDimension("ProgramId", programId),
                createDimension("Marketplace", marketplace),
                createDimension("Stage", "Completion")
            )
            
            val metrics = listOf(
                createMetric("TotalProcessed", totalProcessed.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("TotalStored", totalStored.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("TotalRejected", totalRejected.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("TotalErrors", totalErrors.toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("WorkflowDuration", totalDurationMs.toDouble(), StandardUnit.MILLISECONDS, dimensions),
                createMetric("WorkflowSuccess", if (success) 1.0 else 0.0, StandardUnit.COUNT, dimensions)
            )
            
            publishMetrics(metrics)
            
            logger.info("Published workflow completion metrics", mapOf(
                "programId" to programId,
                "marketplace" to marketplace,
                "totalProcessed" to totalProcessed,
                "totalStored" to totalStored,
                "success" to success
            ))
            
        } catch (e: Exception) {
            logger.error("Failed to publish workflow completion metrics", e)
        }
    }
    
    private fun createDimension(name: String, value: String): Dimension {
        return Dimension.builder()
            .name(name)
            .value(value)
            .build()
    }
    
    private fun createMetric(
        name: String,
        value: Double,
        unit: StandardUnit,
        dimensions: List<Dimension>
    ): MetricDatum {
        return MetricDatum.builder()
            .metricName(name)
            .value(value)
            .unit(unit)
            .timestamp(Instant.now())
            .dimensions(dimensions)
            .build()
    }
    
    private fun publishMetrics(metrics: List<MetricDatum>) {
        val request = PutMetricDataRequest.builder()
            .namespace(namespace)
            .metricData(metrics)
            .build()
        
        cloudWatchClient.putMetricData(request)
    }
}
