package com.ceap.channels

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.LoggerFactory

/**
 * Program-specific rate limiter for channel adapters.
 * 
 * Tracks delivery counts per program and enforces rate limits independently.
 * When a program exceeds its rate limit, only that program is throttled.
 * Other programs continue operating at their normal rates.
 * 
 * Uses a sliding window approach to track deliveries over time.
 * 
 * Validates: Requirements 13.3
 */
class ProgramRateLimiter(
    private val defaultMaxDeliveriesPerWindow: Int = 1000,
    private val windowDurationMs: Long = 60_000L // Default: 1 minute
) {
    
    private val logger = LoggerFactory.getLogger(ProgramRateLimiter::class.java)
    
    /**
     * Tracks delivery windows per program.
     * Key: programId, Value: ProgramDeliveryWindow
     */
    private val programWindows = ConcurrentHashMap<String, ProgramDeliveryWindow>()
    
    /**
     * Program-specific rate limit configuration.
     * Key: programId, Value: max deliveries per window
     */
    private val programLimits = ConcurrentHashMap<String, Int>()
    
    /**
     * Sets the rate limit for a specific program.
     * 
     * @param programId Program identifier
     * @param maxDeliveriesPerWindow Maximum deliveries allowed per window
     */
    fun setProgramLimit(programId: String, maxDeliveriesPerWindow: Int) {
        programLimits[programId] = maxDeliveriesPerWindow
        logger.info("Set rate limit for program: programId={}, limit={}", programId, maxDeliveriesPerWindow)
    }
    
    /**
     * Checks if a delivery is allowed for the given program.
     * 
     * @param programId Program identifier
     * @param count Number of deliveries to check (default: 1)
     * @return true if delivery is allowed, false if rate limited
     */
    fun isAllowed(programId: String, count: Int = 1): Boolean {
        val maxDeliveries = programLimits.getOrDefault(programId, defaultMaxDeliveriesPerWindow)
        val window = programWindows.computeIfAbsent(programId) {
            ProgramDeliveryWindow(programId, maxDeliveries, windowDurationMs)
        }
        
        val allowed = window.tryAcquire(count)
        
        if (!allowed) {
            logger.warn(
                "Program rate limit exceeded: programId={}, current={}, max={}, requested={}",
                programId, window.getCurrentCount(), maxDeliveries, count
            )
        }
        
        return allowed
    }
    
    /**
     * Records a delivery for the given program.
     * 
     * This should be called after a successful delivery to update the rate limit counter.
     * 
     * @param programId Program identifier
     * @param count Number of deliveries to record (default: 1)
     */
    fun recordDelivery(programId: String, count: Int = 1) {
        val maxDeliveries = programLimits.getOrDefault(programId, defaultMaxDeliveriesPerWindow)
        val window = programWindows.computeIfAbsent(programId) {
            ProgramDeliveryWindow(programId, maxDeliveries, windowDurationMs)
        }
        
        window.record(count)
        
        logger.debug(
            "Recorded delivery for program: programId={}, count={}, current={}, max={}",
            programId, count, window.getCurrentCount(), maxDeliveries
        )
    }
    
    /**
     * Gets the current delivery count for the given program within the current window.
     * 
     * @param programId Program identifier
     * @return Current delivery count
     */
    fun getCurrentCount(programId: String): Int {
        val window = programWindows[programId] ?: return 0
        return window.getCurrentCount()
    }
    
    /**
     * Gets the remaining capacity for the given program within the current window.
     * 
     * @param programId Program identifier
     * @return Remaining capacity (number of deliveries that can still be made)
     */
    fun getRemainingCapacity(programId: String): Int {
        val maxDeliveries = programLimits.getOrDefault(programId, defaultMaxDeliveriesPerWindow)
        val window = programWindows[programId] ?: return maxDeliveries
        return window.getRemainingCapacity()
    }
    
    /**
     * Checks if a program is currently throttled.
     * 
     * @param programId Program identifier
     * @return true if program is throttled (at or over limit), false otherwise
     */
    fun isThrottled(programId: String): Boolean {
        return getRemainingCapacity(programId) <= 0
    }
    
    /**
     * Gets throttling status for all programs.
     * 
     * @return Map of programId to throttled status
     */
    fun getThrottlingStatus(): Map<String, ThrottlingStatus> {
        return programWindows.mapValues { (programId, window) ->
            val maxDeliveries = programLimits.getOrDefault(programId, defaultMaxDeliveriesPerWindow)
            ThrottlingStatus(
                programId = programId,
                currentCount = window.getCurrentCount(),
                maxDeliveries = maxDeliveries,
                remainingCapacity = window.getRemainingCapacity(),
                isThrottled = window.getRemainingCapacity() <= 0
            )
        }
    }
    
    /**
     * Resets the rate limiter for the given program.
     * 
     * @param programId Program identifier
     */
    fun reset(programId: String) {
        programWindows.remove(programId)
        logger.info("Reset rate limiter for program: programId={}", programId)
    }
    
    /**
     * Resets the rate limiter for all programs.
     */
    fun resetAll() {
        programWindows.clear()
        logger.info("Reset rate limiter for all programs")
    }
    
    /**
     * Throttling status for a program.
     */
    data class ThrottlingStatus(
        val programId: String,
        val currentCount: Int,
        val maxDeliveries: Int,
        val remainingCapacity: Int,
        val isThrottled: Boolean
    )
    
    /**
     * Represents a sliding time window for tracking deliveries for a specific program.
     */
    private class ProgramDeliveryWindow(
        private val programId: String,
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
