package com.solicitation.scoring

import mu.KotlinLogging
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

/**
 * Circuit breaker implementation for protecting against cascading failures.
 * 
 * Implements the circuit breaker pattern with three states:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Failures exceeded threshold, requests fail fast
 * - HALF_OPEN: Testing if service has recovered
 * 
 * **Requirements**: 9.3
 */
class CircuitBreaker(
    private val name: String,
    private val config: CircuitBreakerConfig = CircuitBreakerConfig()
) {
    
    private val state = AtomicReference(CircuitBreakerState.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val lastFailureTime = AtomicLong(0)
    private val lastStateChange = AtomicLong(System.currentTimeMillis())
    
    /**
     * Executes an operation with circuit breaker protection.
     * 
     * @param operation The operation to execute
     * @return Result of the operation
     * @throws CircuitBreakerOpenException if circuit is open
     */
    suspend fun <T> execute(operation: suspend () -> T): T {
        val currentState = state.get()
        
        when (currentState) {
            CircuitBreakerState.OPEN -> {
                if (shouldAttemptReset()) {
                    logger.info { "Circuit breaker $name transitioning to HALF_OPEN" }
                    state.set(CircuitBreakerState.HALF_OPEN)
                    lastStateChange.set(System.currentTimeMillis())
                } else {
                    logger.debug { "Circuit breaker $name is OPEN, failing fast" }
                    throw CircuitBreakerOpenException("Circuit breaker $name is OPEN")
                }
            }
            CircuitBreakerState.HALF_OPEN -> {
                // In half-open state, allow limited requests through
            }
            CircuitBreakerState.CLOSED -> {
                // Normal operation
            }
        }
        
        return try {
            val result = operation()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure(e)
            throw e
        }
    }
    
    /**
     * Records a successful operation.
     */
    private fun onSuccess() {
        val currentState = state.get()
        
        when (currentState) {
            CircuitBreakerState.HALF_OPEN -> {
                val successes = successCount.incrementAndGet()
                if (successes >= config.successThreshold) {
                    logger.info { "Circuit breaker $name transitioning to CLOSED after $successes successes" }
                    state.set(CircuitBreakerState.CLOSED)
                    failureCount.set(0)
                    successCount.set(0)
                    lastStateChange.set(System.currentTimeMillis())
                }
            }
            CircuitBreakerState.CLOSED -> {
                // Reset failure count on success
                if (failureCount.get() > 0) {
                    failureCount.set(0)
                }
            }
            CircuitBreakerState.OPEN -> {
                // Should not happen, but reset if it does
                logger.warn { "Unexpected success in OPEN state for circuit breaker $name" }
            }
        }
    }
    
    /**
     * Records a failed operation.
     */
    private fun onFailure(exception: Exception) {
        lastFailureTime.set(System.currentTimeMillis())
        
        val currentState = state.get()
        
        when (currentState) {
            CircuitBreakerState.HALF_OPEN -> {
                logger.warn { "Circuit breaker $name transitioning to OPEN after failure in HALF_OPEN state" }
                state.set(CircuitBreakerState.OPEN)
                failureCount.set(0)
                successCount.set(0)
                lastStateChange.set(System.currentTimeMillis())
            }
            CircuitBreakerState.CLOSED -> {
                val failures = failureCount.incrementAndGet()
                if (failures >= config.failureThreshold) {
                    logger.warn { "Circuit breaker $name transitioning to OPEN after $failures failures" }
                    state.set(CircuitBreakerState.OPEN)
                    lastStateChange.set(System.currentTimeMillis())
                }
            }
            CircuitBreakerState.OPEN -> {
                // Already open, just log
                logger.debug { "Circuit breaker $name is OPEN, failure recorded" }
            }
        }
    }
    
    /**
     * Checks if the circuit breaker should attempt to reset.
     * 
     * @return true if enough time has passed since opening
     */
    private fun shouldAttemptReset(): Boolean {
        val timeSinceStateChange = System.currentTimeMillis() - lastStateChange.get()
        return timeSinceStateChange >= config.resetTimeoutMs
    }
    
    /**
     * Gets the current state of the circuit breaker.
     * 
     * @return Current circuit breaker state
     */
    fun getState(): CircuitBreakerState {
        return state.get()
    }
    
    /**
     * Gets metrics about the circuit breaker.
     * 
     * @return Circuit breaker metrics
     */
    fun getMetrics(): CircuitBreakerMetrics {
        return CircuitBreakerMetrics(
            name = name,
            state = state.get(),
            failureCount = failureCount.get(),
            successCount = successCount.get(),
            lastFailureTime = if (lastFailureTime.get() > 0) {
                Instant.ofEpochMilli(lastFailureTime.get())
            } else null,
            lastStateChange = Instant.ofEpochMilli(lastStateChange.get())
        )
    }
    
    /**
     * Manually resets the circuit breaker to CLOSED state.
     * 
     * Use with caution - typically the circuit breaker should reset automatically.
     */
    fun reset() {
        logger.info { "Manually resetting circuit breaker $name to CLOSED" }
        state.set(CircuitBreakerState.CLOSED)
        failureCount.set(0)
        successCount.set(0)
        lastStateChange.set(System.currentTimeMillis())
    }
}

/**
 * States of a circuit breaker.
 */
enum class CircuitBreakerState {
    /**
     * Normal operation - requests pass through.
     */
    CLOSED,
    
    /**
     * Failure threshold exceeded - requests fail fast.
     */
    OPEN,
    
    /**
     * Testing if service has recovered - limited requests allowed.
     */
    HALF_OPEN
}

/**
 * Configuration for circuit breaker behavior.
 * 
 * @property failureThreshold Number of failures before opening circuit
 * @property successThreshold Number of successes in HALF_OPEN before closing
 * @property resetTimeoutMs Time to wait before attempting reset (milliseconds)
 */
data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,
    val successThreshold: Int = 2,
    val resetTimeoutMs: Long = 60000 // 1 minute
)

/**
 * Metrics about circuit breaker state and behavior.
 * 
 * @property name Circuit breaker name
 * @property state Current state
 * @property failureCount Current failure count
 * @property successCount Current success count (in HALF_OPEN state)
 * @property lastFailureTime Timestamp of last failure
 * @property lastStateChange Timestamp of last state change
 */
data class CircuitBreakerMetrics(
    val name: String,
    val state: CircuitBreakerState,
    val failureCount: Int,
    val successCount: Int,
    val lastFailureTime: Instant?,
    val lastStateChange: Instant
)

/**
 * Exception thrown when circuit breaker is open.
 */
class CircuitBreakerOpenException(message: String) : RuntimeException(message)

