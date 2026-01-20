package com.ceap.channels

import com.ceap.model.Candidate

/**
 * Interface for channel adapters that deliver candidates through various channels.
 * 
 * Channel adapters are responsible for:
 * - Delivering candidates to customers through specific channels (email, push, SMS, etc.)
 * - Tracking delivery status and metrics
 * - Supporting shadow mode for testing without actual delivery
 * - Implementing rate limiting and queueing
 * - Providing health checks and configuration
 *
 * @see DeliveryResult
 * @see DeliveryContext
 */
interface ChannelAdapter {
    
    /**
     * Returns the unique identifier for this channel.
     * 
     * @return Channel ID (e.g., "email", "push", "sms", "in-app", "voice")
     */
    fun getChannelId(): String
    
    /**
     * Returns the type of this channel.
     * 
     * @return Channel type enum value
     */
    fun getChannelType(): ChannelType
    
    /**
     * Delivers candidates to customers through this channel.
     * 
     * @param candidates List of candidates to deliver
     * @param context Delivery context including program, marketplace, and shadow mode flag
     * @return Delivery result with delivered candidates, failures, and metrics
     */
    fun deliver(candidates: List<Candidate>, context: DeliveryContext): DeliveryResult
    
    /**
     * Configures the channel adapter with channel-specific settings.
     * 
     * @param config Channel configuration
     */
    fun configure(config: Map<String, Any>)
    
    /**
     * Performs a health check on the channel adapter.
     * 
     * @return Health status indicating if the adapter is operational
     */
    fun healthCheck(): HealthStatus
    
    /**
     * Returns whether this adapter is currently in shadow mode.
     * 
     * In shadow mode, the adapter logs intended deliveries without actually sending them.
     * 
     * @return true if in shadow mode, false otherwise
     */
    fun isShadowMode(): Boolean
}

/**
 * Enum representing different channel types.
 */
enum class ChannelType {
    EMAIL,
    IN_APP,
    PUSH,
    VOICE,
    SMS
}

/**
 * Health status of a channel adapter.
 * 
 * @property healthy Whether the adapter is healthy and operational
 * @property message Optional message describing the health status
 * @property lastChecked Timestamp of the last health check
 */
data class HealthStatus(
    val healthy: Boolean,
    val message: String? = null,
    val lastChecked: Long = System.currentTimeMillis()
)
