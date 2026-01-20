package com.ceap.common.observability

import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import software.amazon.awssdk.services.cloudwatch.model.Dimension
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Aggregates rejection reasons and publishes metrics to CloudWatch.
 * 
 * This class tracks rejections by filter type and reason code,
 * enabling analysis of why candidates are being rejected.
 * 
 * Requirements:
 * - Req 12.3: Rejection reason aggregation
 */
class RejectionMetricsAggregator(
    private val cloudWatchClient: CloudWatchClient = CloudWatchClient.create(),
    private val namespace: String = "SolicitationPlatform/Rejections"
) {
    
    private val logger = LoggerFactory.getLogger(RejectionMetricsAggregator::class.java)
    
    // Aggregation maps: filterType -> reasonCode -> count
    private val rejectionCounts = ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicInteger>>()
    
    /**
     * Records a rejection for aggregation.
     * 
     * @param filterType The type of filter that rejected the candidate
     * @param reasonCode The machine-readable reason code
     * @param programId The program ID for dimensional metrics
     * @param marketplace The marketplace for dimensional metrics
     */
    fun recordRejection(
        filterType: String,
        reasonCode: String,
        programId: String? = null,
        marketplace: String? = null
    ) {
        val filterMap = rejectionCounts.computeIfAbsent(filterType) { ConcurrentHashMap() }
        val counter = filterMap.computeIfAbsent(reasonCode) { AtomicInteger(0) }
        counter.incrementAndGet()
        
        logger.debug("Recorded rejection: filterType=$filterType, reasonCode=$reasonCode")
    }
    
    /**
     * Publishes aggregated rejection metrics to CloudWatch.
     * 
     * @param programId The program ID
     * @param marketplace The marketplace
     */
    fun publishMetrics(programId: String, marketplace: String) {
        try {
            val metrics = mutableListOf<MetricDatum>()
            
            for ((filterType, reasonMap) in rejectionCounts) {
                for ((reasonCode, counter) in reasonMap) {
                    val count = counter.get()
                    if (count > 0) {
                        val dimensions = listOf(
                            createDimension("ProgramId", programId),
                            createDimension("Marketplace", marketplace),
                            createDimension("FilterType", filterType),
                            createDimension("ReasonCode", reasonCode)
                        )
                        
                        metrics.add(
                            createMetric("RejectionCount", count.toDouble(), StandardUnit.COUNT, dimensions)
                        )
                    }
                }
            }
            
            if (metrics.isNotEmpty()) {
                val request = PutMetricDataRequest.builder()
                    .namespace(namespace)
                    .metricData(metrics)
                    .build()
                
                cloudWatchClient.putMetricData(request)
                
                logger.info("Published ${metrics.size} rejection metrics for program=$programId, marketplace=$marketplace")
            }
            
        } catch (e: Exception) {
            logger.error("Failed to publish rejection metrics", e)
        }
    }
    
    /**
     * Publishes aggregated rejection metrics and resets counters.
     * 
     * @param programId The program ID
     * @param marketplace The marketplace
     */
    fun publishAndReset(programId: String, marketplace: String) {
        publishMetrics(programId, marketplace)
        reset()
    }
    
    /**
     * Gets the current rejection counts for analysis.
     * 
     * @return Map of filterType -> reasonCode -> count
     */
    fun getRejectionCounts(): Map<String, Map<String, Int>> {
        return rejectionCounts.mapValues { (_, reasonMap) ->
            reasonMap.mapValues { (_, counter) -> counter.get() }
        }
    }
    
    /**
     * Gets rejection counts for a specific filter type.
     * 
     * @param filterType The filter type
     * @return Map of reasonCode -> count
     */
    fun getRejectionCountsForFilter(filterType: String): Map<String, Int> {
        return rejectionCounts[filterType]?.mapValues { (_, counter) -> counter.get() } ?: emptyMap()
    }
    
    /**
     * Resets all rejection counters.
     */
    fun reset() {
        rejectionCounts.clear()
        logger.debug("Reset rejection counters")
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
}
