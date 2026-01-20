package com.ceap.serving.v1

import mu.KotlinLogging
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

/**
 * Tracks V1 API usage for migration planning.
 * 
 * Records metrics about V1 API usage including:
 * - Endpoint usage counts
 * - Customer usage patterns
 * - Channel and program distribution
 * - Timestamps for trend analysis
 * 
 * This data helps migration engineers understand V1 API usage patterns
 * and plan the deprecation timeline.
 */
interface V1UsageTracker {
    
    /**
     * Tracks a single customer request.
     */
    fun trackRequest(
        endpoint: String,
        customerId: String,
        channel: String?,
        program: String?
    )
    
    /**
     * Tracks a batch request.
     */
    fun trackBatchRequest(
        endpoint: String,
        customerIds: List<String>,
        channel: String?,
        program: String?
    )
    
    /**
     * Gets usage statistics.
     */
    fun getUsageStats(): V1UsageStats
}

/**
 * Usage statistics for V1 API.
 */
data class V1UsageStats(
    val totalRequests: Long,
    val requestsByEndpoint: Map<String, Long>,
    val uniqueCustomers: Int,
    val requestsByChannel: Map<String, Long>,
    val requestsByProgram: Map<String, Long>,
    val firstRequestTime: Instant?,
    val lastRequestTime: Instant?
)

/**
 * In-memory implementation of V1UsageTracker.
 * 
 * Stores usage metrics in memory for quick access. In production,
 * this would be backed by CloudWatch Metrics or a database.
 */
class InMemoryV1UsageTracker : V1UsageTracker {
    
    private val totalRequests = AtomicLong(0)
    private val requestsByEndpoint = ConcurrentHashMap<String, AtomicLong>()
    private val uniqueCustomers = ConcurrentHashMap.newKeySet<String>()
    private val requestsByChannel = ConcurrentHashMap<String, AtomicLong>()
    private val requestsByProgram = ConcurrentHashMap<String, AtomicLong>()
    
    @Volatile
    private var firstRequestTime: Instant? = null
    
    @Volatile
    private var lastRequestTime: Instant? = null
    
    override fun trackRequest(
        endpoint: String,
        customerId: String,
        channel: String?,
        program: String?
    ) {
        val timestamp = Instant.now()
        
        // Update counters
        totalRequests.incrementAndGet()
        requestsByEndpoint.computeIfAbsent(endpoint) { AtomicLong(0) }.incrementAndGet()
        uniqueCustomers.add(customerId)
        
        if (channel != null) {
            requestsByChannel.computeIfAbsent(channel) { AtomicLong(0) }.incrementAndGet()
        }
        
        if (program != null) {
            requestsByProgram.computeIfAbsent(program) { AtomicLong(0) }.incrementAndGet()
        }
        
        // Update timestamps
        updateTimestamps(timestamp)
        
        // Log usage
        logger.info { 
            "V1 API usage: endpoint=$endpoint, customer=$customerId, channel=$channel, program=$program" 
        }
    }
    
    override fun trackBatchRequest(
        endpoint: String,
        customerIds: List<String>,
        channel: String?,
        program: String?
    ) {
        val timestamp = Instant.now()
        
        // Update counters
        totalRequests.incrementAndGet()
        requestsByEndpoint.computeIfAbsent(endpoint) { AtomicLong(0) }.incrementAndGet()
        uniqueCustomers.addAll(customerIds)
        
        if (channel != null) {
            requestsByChannel.computeIfAbsent(channel) { AtomicLong(0) }.incrementAndGet()
        }
        
        if (program != null) {
            requestsByProgram.computeIfAbsent(program) { AtomicLong(0) }.incrementAndGet()
        }
        
        // Update timestamps
        updateTimestamps(timestamp)
        
        // Log usage
        logger.info { 
            "V1 API batch usage: endpoint=$endpoint, customers=${customerIds.size}, channel=$channel, program=$program" 
        }
    }
    
    override fun getUsageStats(): V1UsageStats {
        return V1UsageStats(
            totalRequests = totalRequests.get(),
            requestsByEndpoint = requestsByEndpoint.mapValues { it.value.get() },
            uniqueCustomers = uniqueCustomers.size,
            requestsByChannel = requestsByChannel.mapValues { it.value.get() },
            requestsByProgram = requestsByProgram.mapValues { it.value.get() },
            firstRequestTime = firstRequestTime,
            lastRequestTime = lastRequestTime
        )
    }
    
    /**
     * Updates first and last request timestamps.
     */
    private fun updateTimestamps(timestamp: Instant) {
        if (firstRequestTime == null) {
            firstRequestTime = timestamp
        }
        lastRequestTime = timestamp
    }
}

/**
 * CloudWatch-based implementation of V1UsageTracker.
 * 
 * Publishes usage metrics to CloudWatch for monitoring and alerting.
 * This would be the production implementation.
 */
class CloudWatchV1UsageTracker : V1UsageTracker {
    
    override fun trackRequest(
        endpoint: String,
        customerId: String,
        channel: String?,
        program: String?
    ) {
        // In production, this would publish to CloudWatch Metrics
        logger.info { 
            "CloudWatch metric: V1APIRequest endpoint=$endpoint, customer=$customerId, channel=$channel, program=$program" 
        }
        
        // Example CloudWatch metric dimensions:
        // - MetricName: V1APIRequest
        // - Dimensions: Endpoint, Channel, Program
        // - Value: 1
        // - Unit: Count
    }
    
    override fun trackBatchRequest(
        endpoint: String,
        customerIds: List<String>,
        channel: String?,
        program: String?
    ) {
        // In production, this would publish to CloudWatch Metrics
        logger.info { 
            "CloudWatch metric: V1APIBatchRequest endpoint=$endpoint, customers=${customerIds.size}, channel=$channel, program=$program" 
        }
    }
    
    override fun getUsageStats(): V1UsageStats {
        // In production, this would query CloudWatch Metrics
        // For now, return empty stats
        return V1UsageStats(
            totalRequests = 0,
            requestsByEndpoint = emptyMap(),
            uniqueCustomers = 0,
            requestsByChannel = emptyMap(),
            requestsByProgram = emptyMap(),
            firstRequestTime = null,
            lastRequestTime = null
        )
    }
}
