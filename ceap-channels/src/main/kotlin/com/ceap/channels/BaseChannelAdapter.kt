package com.ceap.channels

import com.ceap.model.Candidate
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Base implementation of ChannelAdapter providing common functionality.
 * 
 * This abstract class provides:
 * - Shadow mode support with logging
 * - Configuration management
 * - Health check implementation
 * - Template method pattern for delivery
 * 
 * Subclasses must implement:
 * - getChannelId()
 * - getChannelType()
 * - doDeliver() - actual delivery logic
 */
abstract class BaseChannelAdapter : ChannelAdapter {
    
    /**
     * Shadow mode flag. When true, deliveries are logged but not actually sent.
     */
    protected var shadowMode: Boolean = false
        private set
    
    /**
     * Channel configuration map.
     */
    protected var config: Map<String, Any> = emptyMap()
        private set
    
    /**
     * Delivers candidates through this channel.
     * 
     * If shadow mode is enabled (either in context or adapter configuration),
     * the delivery is logged but not actually performed.
     * 
     * @param candidates List of candidates to deliver
     * @param context Delivery context including shadow mode flag
     * @return Delivery result with delivered candidates, failures, and metrics
     */
    override fun deliver(candidates: List<Candidate>, context: DeliveryContext): DeliveryResult {
        val startTime = System.currentTimeMillis()
        val effectiveShadowMode = context.shadowMode || shadowMode
        
        return if (effectiveShadowMode) {
            // Shadow mode: log intended delivery without actually sending
            deliverInShadowMode(candidates, context, startTime)
        } else {
            // Normal mode: perform actual delivery
            doDeliver(candidates, context, startTime)
        }
    }
    
    /**
     * Performs actual delivery of candidates.
     * 
     * Subclasses must implement this method to provide channel-specific delivery logic.
     * 
     * @param candidates List of candidates to deliver
     * @param context Delivery context
     * @param startTime Start time of the delivery operation (epoch millis)
     * @return Delivery result
     */
    protected abstract fun doDeliver(
        candidates: List<Candidate>,
        context: DeliveryContext,
        startTime: Long
    ): DeliveryResult
    
    /**
     * Handles delivery in shadow mode.
     * 
     * Logs intended deliveries without actually sending them.
     * 
     * @param candidates List of candidates to deliver
     * @param context Delivery context
     * @param startTime Start time of the delivery operation (epoch millis)
     * @return Delivery result with simulated deliveries
     */
    private fun deliverInShadowMode(
        candidates: List<Candidate>,
        context: DeliveryContext,
        startTime: Long
    ): DeliveryResult {
        logger.info {
            "Shadow mode delivery for channel ${getChannelId()}: " +
            "programId=${context.programId}, " +
            "marketplace=${context.marketplace}, " +
            "candidateCount=${candidates.size}"
        }
        
        // Log each candidate that would be delivered
        val delivered = candidates.mapIndexed { index, candidate ->
            val deliveryId = "shadow-${getChannelId()}-${System.nanoTime()}-$index"
            val timestamp = System.currentTimeMillis()
            
            logger.debug {
                "Shadow delivery: deliveryId=$deliveryId, " +
                "customerId=${candidate.customerId}, " +
                "subjectType=${candidate.subject.type}, " +
                "subjectId=${candidate.subject.id}"
            }
            
            DeliveredCandidate(
                candidate = candidate,
                deliveryId = deliveryId,
                timestamp = timestamp,
                channelMetadata = mapOf(
                    "shadowMode" to true,
                    "channel" to getChannelId()
                )
            )
        }
        
        val durationMs = System.currentTimeMillis() - startTime
        
        return DeliveryResult(
            delivered = delivered,
            failed = emptyList(),
            metrics = DeliveryMetrics(
                totalCandidates = candidates.size,
                deliveredCount = delivered.size,
                failedCount = 0,
                durationMs = durationMs,
                rateLimitedCount = 0,
                shadowMode = true
            )
        )
    }
    
    /**
     * Configures the channel adapter.
     * 
     * Extracts shadow mode flag from configuration if present.
     * Subclasses can override to handle additional configuration.
     * 
     * @param config Channel configuration map
     */
    override fun configure(config: Map<String, Any>) {
        this.config = config
        this.shadowMode = config["shadowMode"] as? Boolean ?: false
        
        logger.info {
            "Configured channel ${getChannelId()}: shadowMode=$shadowMode, " +
            "configKeys=${config.keys.joinToString()}"
        }
    }
    
    /**
     * Performs a health check on the channel adapter.
     * 
     * Default implementation returns healthy status.
     * Subclasses can override to provide channel-specific health checks.
     * 
     * @return Health status
     */
    override fun healthCheck(): HealthStatus {
        return HealthStatus(
            healthy = true,
            message = "Channel ${getChannelId()} is operational",
            lastChecked = System.currentTimeMillis()
        )
    }
    
    /**
     * Returns whether this adapter is in shadow mode.
     * 
     * @return true if in shadow mode, false otherwise
     */
    override fun isShadowMode(): Boolean = shadowMode
}
