package com.ceap.channels

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Rate limiter for channel adapters.
 * 
 * Tracks delivery counts per channel and enforces rate limits.
 * Uses a sliding window approach to track deliveries over time.
 * 
 * @property maxDeliveriesPerWindow Maximum number of deliveries allowed per time window
 * @property windowDurationMs Duration of the time window in milliseconds
 */
class RateLimiter(
    private val maxDeliveriesPerWindow: Int,
    private val windowDurationMs: Long = 60_000L // Default: 1 minute
) {
    
    /**
     * Tracks delivery counts per channel.
     * Key: channelId, Value: DeliveryWindow
     */
    private val deliveryWindows = ConcurrentHashMap<String, DeliveryWindow>()
    
    /**
     * Checks if a delivery is allowed for the given channel.
     * 
     * @param channelId Channel identifier
     * @param count Number of deliveries to check (default: 1)
     * @return true if delivery is allowed, false if rate limited
     */
    fun isAllowed(channelId: String, count: Int = 1): Boolean {
        val window = deliveryWindows.computeIfAbsent(channelId) {
            DeliveryWindow(maxDeliveriesPerWindow, windowDurationMs)
        }
        
        return window.tryAcquire(count)
    }
    
    /**
     * Records a delivery for the given channel.
     * 
     * This should be called after a successful delivery to update the rate limit counter.
     * 
     * @param channelId Channel identifier
     * @param count Number of deliveries to record (default: 1)
     */
    fun recordDelivery(channelId: String, count: Int = 1) {
        val window = deliveryWindows.computeIfAbsent(channelId) {
            DeliveryWindow(maxDeliveriesPerWindow, windowDurationMs)
        }
        
        window.record(count)
    }
    
    /**
     * Gets the current delivery count for the given channel within the current window.
     * 
     * @param channelId Channel identifier
     * @return Current delivery count
     */
    fun getCurrentCount(channelId: String): Int {
        val window = deliveryWindows[channelId] ?: return 0
        return window.getCurrentCount()
    }
    
    /**
     * Gets the remaining capacity for the given channel within the current window.
     * 
     * @param channelId Channel identifier
     * @return Remaining capacity (number of deliveries that can still be made)
     */
    fun getRemainingCapacity(channelId: String): Int {
        val window = deliveryWindows[channelId]
            ?: return maxDeliveriesPerWindow
        return window.getRemainingCapacity()
    }
    
    /**
     * Resets the rate limiter for the given channel.
     * 
     * @param channelId Channel identifier
     */
    fun reset(channelId: String) {
        deliveryWindows.remove(channelId)
    }
    
    /**
     * Resets the rate limiter for all channels.
     */
    fun resetAll() {
        deliveryWindows.clear()
    }
    
    /**
     * Represents a sliding time window for tracking deliveries.
     */
    private class DeliveryWindow(
        private val maxDeliveries: Int,
        private val windowDurationMs: Long
    ) {
        private val count = AtomicLong(0)
        private val windowStart = AtomicLong(System.currentTimeMillis())
        
        /**
         * Tries to acquire capacity for the given number of deliveries.
         * 
         * @param requestedCount Number of deliveries to acquire
         * @return true if capacity was acquired, false if rate limited
         */
        fun tryAcquire(requestedCount: Int): Boolean {
            resetWindowIfExpired()
            
            val currentCount = count.get()
            return (currentCount + requestedCount) <= maxDeliveries
        }
        
        /**
         * Records the given number of deliveries.
         * 
         * @param deliveryCount Number of deliveries to record
         */
        fun record(deliveryCount: Int) {
            resetWindowIfExpired()
            count.addAndGet(deliveryCount.toLong())
        }
        
        /**
         * Gets the current delivery count within the window.
         * 
         * @return Current count
         */
        fun getCurrentCount(): Int {
            resetWindowIfExpired()
            return count.get().toInt()
        }
        
        /**
         * Gets the remaining capacity within the window.
         * 
         * @return Remaining capacity
         */
        fun getRemainingCapacity(): Int {
            resetWindowIfExpired()
            return maxOf(0, maxDeliveries - count.get().toInt())
        }
        
        /**
         * Resets the window if it has expired.
         */
        private fun resetWindowIfExpired() {
            val now = System.currentTimeMillis()
            val start = windowStart.get()
            
            if (now - start >= windowDurationMs) {
                // Window has expired, reset
                windowStart.set(now)
                count.set(0)
            }
        }
    }
}
