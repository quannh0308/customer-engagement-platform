package com.ceap.common.observability

import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.Dimension
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks and attributes costs per program for chargeback.
 * 
 * Monitors resource usage across different dimensions:
 * - Storage operations (DynamoDB reads/writes)
 * - Compute operations (Lambda invocations, duration)
 * - API calls (external service calls)
 * - Data transfer (bytes in/out)
 * 
 * Publishes cost metrics to CloudWatch with program-specific tags
 * for cost allocation and chargeback reporting.
 * 
 * Validates: Requirements 13.4
 */
class ProgramCostTracker(
    private val cloudWatchClient: CloudWatchClient? = null
) {
    
    private val logger = LoggerFactory.getLogger(ProgramCostTracker::class.java)
    private val namespace = "CeapPlatform/Costs"
    
    /**
     * Tracks cost metrics per program.
     * Key: programId, Value: ProgramCostMetrics
     */
    private val programCosts = ConcurrentHashMap<String, ProgramCostMetrics>()
    
    /**
     * Cost metrics for a program.
     */
    private data class ProgramCostMetrics(
        val programId: String,
        val storageReads: AtomicLong = AtomicLong(0),
        val storageWrites: AtomicLong = AtomicLong(0),
        val lambdaInvocations: AtomicLong = AtomicLong(0),
        val lambdaDurationMs: AtomicLong = AtomicLong(0),
        val apiCalls: AtomicLong = AtomicLong(0),
        val dataTransferBytes: AtomicLong = AtomicLong(0)
    )
    
    /**
     * Records storage read operations for a program.
     * 
     * @param programId Program identifier
     * @param count Number of read operations
     */
    fun recordStorageReads(programId: String, count: Int = 1) {
        val metrics = getOrCreateMetrics(programId)
        metrics.storageReads.addAndGet(count.toLong())
        
        logger.debug("Recorded storage reads: programId={}, count={}", programId, count)
    }
    
    /**
     * Records storage write operations for a program.
     * 
     * @param programId Program identifier
     * @param count Number of write operations
     */
    fun recordStorageWrites(programId: String, count: Int = 1) {
        val metrics = getOrCreateMetrics(programId)
        metrics.storageWrites.addAndGet(count.toLong())
        
        logger.debug("Recorded storage writes: programId={}, count={}", programId, count)
    }
    
    /**
     * Records Lambda invocation for a program.
     * 
     * @param programId Program identifier
     * @param durationMs Duration of the invocation in milliseconds
     */
    fun recordLambdaInvocation(programId: String, durationMs: Long) {
        val metrics = getOrCreateMetrics(programId)
        metrics.lambdaInvocations.incrementAndGet()
        metrics.lambdaDurationMs.addAndGet(durationMs)
        
        logger.debug("Recorded Lambda invocation: programId={}, durationMs={}", programId, durationMs)
    }
    
    /**
     * Records API call for a program.
     * 
     * @param programId Program identifier
     * @param count Number of API calls
     */
    fun recordApiCalls(programId: String, count: Int = 1) {
        val metrics = getOrCreateMetrics(programId)
        metrics.apiCalls.addAndGet(count.toLong())
        
        logger.debug("Recorded API calls: programId={}, count={}", programId, count)
    }
    
    /**
     * Records data transfer for a program.
     * 
     * @param programId Program identifier
     * @param bytes Number of bytes transferred
     */
    fun recordDataTransfer(programId: String, bytes: Long) {
        val metrics = getOrCreateMetrics(programId)
        metrics.dataTransferBytes.addAndGet(bytes)
        
        logger.debug("Recorded data transfer: programId={}, bytes={}", programId, bytes)
    }
    
    /**
     * Gets the current cost metrics for a program.
     * 
     * @param programId Program identifier
     * @return Cost summary for the program
     */
    fun getCostSummary(programId: String): CostSummary {
        val metrics = programCosts[programId]
        
        return if (metrics != null) {
            CostSummary(
                programId = programId,
                storageReads = metrics.storageReads.get(),
                storageWrites = metrics.storageWrites.get(),
                lambdaInvocations = metrics.lambdaInvocations.get(),
                lambdaDurationMs = metrics.lambdaDurationMs.get(),
                apiCalls = metrics.apiCalls.get(),
                dataTransferBytes = metrics.dataTransferBytes.get()
            )
        } else {
            CostSummary(programId = programId)
        }
    }
    
    /**
     * Gets cost summaries for all programs.
     * 
     * @return Map of programId to cost summary
     */
    fun getAllCostSummaries(): Map<String, CostSummary> {
        return programCosts.mapValues { (programId, _) ->
            getCostSummary(programId)
        }
    }
    
    /**
     * Publishes cost metrics to CloudWatch for a specific program.
     * 
     * @param programId Program identifier
     */
    fun publishCostMetrics(programId: String) {
        if (cloudWatchClient == null) {
            logger.warn("CloudWatch client not configured, skipping metric publication")
            return
        }
        
        val metrics = programCosts[programId] ?: return
        
        try {
            val dimensions = listOf(
                createDimension("ProgramId", programId)
            )
            
            val metricData = listOf(
                createMetric("StorageReads", metrics.storageReads.get().toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("StorageWrites", metrics.storageWrites.get().toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("LambdaInvocations", metrics.lambdaInvocations.get().toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("LambdaDurationMs", metrics.lambdaDurationMs.get().toDouble(), StandardUnit.MILLISECONDS, dimensions),
                createMetric("ApiCalls", metrics.apiCalls.get().toDouble(), StandardUnit.COUNT, dimensions),
                createMetric("DataTransferBytes", metrics.dataTransferBytes.get().toDouble(), StandardUnit.BYTES, dimensions)
            )
            
            publishMetrics(metricData)
            
            logger.info("Published cost metrics for program: programId={}", programId)
            
        } catch (e: Exception) {
            logger.error("Failed to publish cost metrics: programId={}", programId, e)
        }
    }
    
    /**
     * Publishes cost metrics to CloudWatch for all programs.
     */
    fun publishAllCostMetrics() {
        programCosts.keys.forEach { programId ->
            publishCostMetrics(programId)
        }
    }
    
    /**
     * Resets cost metrics for a specific program.
     * 
     * @param programId Program identifier
     */
    fun reset(programId: String) {
        programCosts.remove(programId)
        logger.info("Reset cost metrics for program: programId={}", programId)
    }
    
    /**
     * Resets cost metrics for all programs.
     */
    fun resetAll() {
        programCosts.clear()
        logger.info("Reset cost metrics for all programs")
    }
    
    /**
     * Gets or creates metrics for a program.
     */
    private fun getOrCreateMetrics(programId: String): ProgramCostMetrics {
        return programCosts.computeIfAbsent(programId) {
            ProgramCostMetrics(programId)
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
        if (cloudWatchClient == null) {
            return
        }
        
        val request = PutMetricDataRequest.builder()
            .namespace(namespace)
            .metricData(metrics)
            .build()
        
        cloudWatchClient.putMetricData(request)
    }
    
    /**
     * Summary of costs for a program.
     */
    data class CostSummary(
        val programId: String,
        val storageReads: Long = 0,
        val storageWrites: Long = 0,
        val lambdaInvocations: Long = 0,
        val lambdaDurationMs: Long = 0,
        val apiCalls: Long = 0,
        val dataTransferBytes: Long = 0
    ) {
        /**
         * Calculates estimated cost in USD based on AWS pricing.
         * 
         * Note: These are approximate costs and should be adjusted based on
         * actual AWS pricing in your region and any volume discounts.
         */
        fun estimatedCostUSD(): Double {
            // Approximate AWS pricing (as of 2024)
            val dynamoReadCost = 0.00025 / 1000 // $0.25 per million reads
            val dynamoWriteCost = 0.00125 / 1000 // $1.25 per million writes
            val lambdaCost = 0.0000166667 / 1000 // $0.20 per 1M requests + $0.0000166667 per GB-second
            val apiCallCost = 0.001 // Approximate cost per API call
            val dataTransferCost = 0.09 / (1024 * 1024 * 1024) // $0.09 per GB
            
            return (storageReads * dynamoReadCost) +
                   (storageWrites * dynamoWriteCost) +
                   (lambdaInvocations * lambdaCost) +
                   (apiCalls * apiCallCost) +
                   (dataTransferBytes * dataTransferCost)
        }
    }
}
