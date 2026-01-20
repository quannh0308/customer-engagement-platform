package com.ceap.channels

import com.ceap.model.Candidate

/**
 * Test implementation of ChannelAdapter for property testing.
 * 
 * This adapter simulates successful delivery of all candidates
 * and can be configured for shadow mode testing.
 */
class TestChannelAdapter(
    private val channelId: String,
    private val channelType: ChannelType,
    private var shadowMode: Boolean = false
) : ChannelAdapter {
    
    override fun getChannelId(): String = channelId
    
    override fun getChannelType(): ChannelType = channelType
    
    override fun deliver(candidates: List<Candidate>, context: DeliveryContext): DeliveryResult {
        val startTime = System.currentTimeMillis()
        
        // Simulate delivery
        val delivered = candidates.map { candidate ->
            DeliveredCandidate(
                candidate = candidate,
                deliveryId = "delivery-${channelId}-${System.nanoTime()}",
                timestamp = System.currentTimeMillis(),
                channelMetadata = mapOf("channel" to channelId)
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
                shadowMode = context.shadowMode || shadowMode
            )
        )
    }
    
    override fun configure(config: Map<String, Any>) {
        // Update shadow mode if present in config
        shadowMode = config["shadowMode"] as? Boolean ?: shadowMode
    }
    
    override fun healthCheck(): HealthStatus {
        return HealthStatus(
            healthy = true,
            message = "Test adapter is healthy",
            lastChecked = System.currentTimeMillis()
        )
    }
    
    override fun isShadowMode(): Boolean = shadowMode
}
