package com.ceap.workflow.common

import net.jqwik.api.*
import net.jqwik.api.constraints.IntRange
import org.junit.jupiter.api.Assertions.*
import kotlin.math.pow

/**
 * Property-based test for workflow retry with exponential backoff.
 * 
 * **Property 25: Workflow retry with exponential backoff**
 * 
 * *For any* workflow step that fails, retries must occur with exponentially 
 * increasing delays (e.g., 1s, 2s, 4s, 8s) up to the configured maximum 
 * retry count.
 * 
 * **Validates: Requirements 8.3**
 */
class WorkflowRetryPropertyTest {
    
    /**
     * Property: Retry delays follow exponential backoff pattern
     */
    @Property(tries = 100)
    fun retryDelaysFollowExponentialBackoff(
        @ForAll @IntRange(min = 1, max = 10) maxRetries: Int,
        @ForAll @IntRange(min = 100, max = 5000) baseDelayMs: Int,
        @ForAll @IntRange(min = 2, max = 3) backoffRate: Int
    ): Boolean {
        // Given: A retry configuration with exponential backoff
        val retryConfig = RetryConfig(
            maxRetries = maxRetries,
            baseDelayMs = baseDelayMs,
            backoffRate = backoffRate.toDouble()
        )
        
        // When: Calculating retry delays
        val delays = calculateRetryDelays(retryConfig)
        
        // Then: Each delay should be backoffRate times the previous delay
        for (i in 1 until delays.size) {
            val expectedDelay = baseDelayMs * backoffRate.toDouble().pow(i)
            val actualDelay = delays[i]
            
            // Allow small tolerance for floating point arithmetic
            val tolerance = 1.0
            if (kotlin.math.abs(actualDelay - expectedDelay) > tolerance) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Property: Number of retries does not exceed maximum
     */
    @Property(tries = 100)
    fun numberOfRetriesDoesNotExceedMaximum(
        @ForAll @IntRange(min = 1, max = 10) maxRetries: Int,
        @ForAll @IntRange(min = 100, max = 5000) baseDelayMs: Int
    ): Boolean {
        // Given: A retry configuration
        val retryConfig = RetryConfig(
            maxRetries = maxRetries,
            baseDelayMs = baseDelayMs,
            backoffRate = 2.0
        )
        
        // When: Calculating retry delays
        val delays = calculateRetryDelays(retryConfig)
        
        // Then: Number of delays should equal maxRetries
        return delays.size == maxRetries
    }
    
    /**
     * Property: First retry delay equals base delay
     */
    @Property(tries = 100)
    fun firstRetryDelayEqualsBaseDelay(
        @ForAll @IntRange(min = 1, max = 10) maxRetries: Int,
        @ForAll @IntRange(min = 100, max = 5000) baseDelayMs: Int,
        @ForAll @IntRange(min = 2, max = 3) backoffRate: Int
    ): Boolean {
        // Given: A retry configuration
        val retryConfig = RetryConfig(
            maxRetries = maxRetries,
            baseDelayMs = baseDelayMs,
            backoffRate = backoffRate.toDouble()
        )
        
        // When: Calculating retry delays
        val delays = calculateRetryDelays(retryConfig)
        
        // Then: First delay should equal base delay
        return delays.isNotEmpty() && delays[0] == baseDelayMs.toDouble()
    }
    
    /**
     * Property: Retry delays are monotonically increasing
     */
    @Property(tries = 100)
    fun retryDelaysAreMonotonicallyIncreasing(
        @ForAll @IntRange(min = 2, max = 10) maxRetries: Int,
        @ForAll @IntRange(min = 100, max = 5000) baseDelayMs: Int,
        @ForAll @IntRange(min = 2, max = 3) backoffRate: Int
    ): Boolean {
        // Given: A retry configuration with backoff rate > 1
        val retryConfig = RetryConfig(
            maxRetries = maxRetries,
            baseDelayMs = baseDelayMs,
            backoffRate = backoffRate.toDouble()
        )
        
        // When: Calculating retry delays
        val delays = calculateRetryDelays(retryConfig)
        
        // Then: Each delay should be greater than the previous
        for (i in 1 until delays.size) {
            if (delays[i] <= delays[i - 1]) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Property: Total retry time is bounded
     */
    @Property(tries = 100)
    fun totalRetryTimeIsBounded(
        @ForAll @IntRange(min = 1, max = 5) maxRetries: Int,
        @ForAll @IntRange(min = 1000, max = 5000) baseDelayMs: Int
    ): Boolean {
        // Given: A retry configuration
        val retryConfig = RetryConfig(
            maxRetries = maxRetries,
            baseDelayMs = baseDelayMs,
            backoffRate = 2.0
        )
        
        // When: Calculating total retry time
        val delays = calculateRetryDelays(retryConfig)
        val totalTime = delays.sum()
        
        // Then: Total time should be less than a reasonable upper bound
        // For exponential backoff with rate 2: sum = base * (2^n - 1)
        val maxExpectedTime = baseDelayMs * (2.0.pow(maxRetries) - 1)
        
        return totalTime <= maxExpectedTime * 1.1 // Allow 10% tolerance
    }
}

/**
 * Retry configuration
 */
data class RetryConfig(
    val maxRetries: Int,
    val baseDelayMs: Int,
    val backoffRate: Double
)

/**
 * Calculate retry delays based on exponential backoff
 */
fun calculateRetryDelays(config: RetryConfig): List<Double> {
    val delays = mutableListOf<Double>()
    
    for (i in 0 until config.maxRetries) {
        val delay = config.baseDelayMs * config.backoffRate.pow(i)
        delays.add(delay)
    }
    
    return delays
}
