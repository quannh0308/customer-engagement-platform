package com.ceap.serving

import com.ceap.model.Candidate
import com.ceap.storage.CandidateRepository
import com.ceap.storage.StorageException
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.time.Instant
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Interface for handling failures and providing fallback responses.
 * 
 * Implements graceful degradation when dependencies fail.
 */
interface FallbackHandler {
    
    /**
     * Handles a failure and returns a fallback response.
     * 
     * @param request Original request
     * @param error Error that occurred
     * @param elapsedMs Elapsed time before failure
     * @return Fallback response
     */
    fun handleFailure(
        request: GetCandidatesRequest,
        error: Throwable,
        elapsedMs: Long
    ): GetCandidatesResponse
}

/**
 * Default fallback handler with caching and circuit breaker.
 * 
 * Fallback strategy:
 * 1. Return cached results if available
 * 2. Return empty results with degraded flag
 * 3. Log degradation for monitoring
 * 
 * @property candidateRepository Repository for attempting fallback queries
 * @property cache In-memory cache for fallback responses
 * @property circuitBreaker Circuit breaker for dependency protection
 */
class DefaultFallbackHandler(
    private val candidateRepository: CandidateRepository,
    private val cache: ResponseCache = InMemoryResponseCache(),
    private val circuitBreaker: CircuitBreaker = SimpleCircuitBreaker()
) : FallbackHandler {
    
    override fun handleFailure(
        request: GetCandidatesRequest,
        error: Throwable,
        elapsedMs: Long
    ): GetCandidatesResponse {
        
        logger.warn(error) { "Serving request failed, attempting fallback for customer ${request.customerId}" }
        
        // Try to get cached response
        val cacheKey = buildCacheKey(request)
        val cachedResponse = cache.get(cacheKey)
        
        if (cachedResponse != null) {
            logger.info { "Returning cached response for customer ${request.customerId}" }
            return cachedResponse.copy(
                metadata = cachedResponse.metadata.copy(
                    cacheHit = true,
                    degraded = true
                ),
                latencyMs = elapsedMs
            )
        }
        
        // Try alternative query if circuit breaker allows
        if (circuitBreaker.allowRequest()) {
            try {
                val fallbackCandidates = attemptFallbackQuery(request)
                
                if (fallbackCandidates.isNotEmpty()) {
                    logger.info { "Fallback query succeeded for customer ${request.customerId}" }
                    circuitBreaker.recordSuccess()
                    
                    val response = GetCandidatesResponse(
                        candidates = fallbackCandidates,
                        metadata = ResponseMetadata(
                            totalCount = fallbackCandidates.size,
                            filteredCount = fallbackCandidates.size,
                            degraded = true
                        ),
                        latencyMs = elapsedMs
                    )
                    
                    // Cache the fallback response
                    cache.put(cacheKey, response)
                    
                    return response
                }
            } catch (e: Exception) {
                logger.error(e) { "Fallback query also failed" }
                circuitBreaker.recordFailure()
            }
        }
        
        // Return empty degraded response
        logger.warn { "Returning empty degraded response for customer ${request.customerId}" }
        
        return GetCandidatesResponse(
            candidates = emptyList(),
            metadata = ResponseMetadata(
                totalCount = 0,
                filteredCount = 0,
                degraded = true
            ),
            latencyMs = elapsedMs
        )
    }
    
    /**
     * Attempts a fallback query with reduced requirements.
     */
    private fun attemptFallbackQuery(request: GetCandidatesRequest): List<Candidate> {
        return try {
            // Try simpler query without all filters
            if (request.program != null && request.channel != null) {
                candidateRepository.queryByProgramAndChannel(
                    programId = request.program,
                    channelId = request.channel,
                    limit = request.limit
                )
            } else {
                emptyList()
            }
        } catch (e: StorageException) {
            logger.error(e) { "Fallback query failed" }
            emptyList()
        }
    }
    
    /**
     * Builds a cache key from request parameters.
     */
    private fun buildCacheKey(request: GetCandidatesRequest): String {
        return "${request.customerId}:${request.program}:${request.channel}:${request.marketplace}:${request.limit}"
    }
}

/**
 * Interface for response caching.
 */
interface ResponseCache {
    
    /**
     * Gets a cached response.
     * 
     * @param key Cache key
     * @return Cached response or null if not found
     */
    fun get(key: String): GetCandidatesResponse?
    
    /**
     * Puts a response in cache.
     * 
     * @param key Cache key
     * @param response Response to cache
     */
    fun put(key: String, response: GetCandidatesResponse)
    
    /**
     * Clears the cache.
     */
    fun clear()
}

/**
 * In-memory response cache with TTL.
 * 
 * @property ttl Time-to-live for cached entries
 * @property maxSize Maximum number of entries
 */
class InMemoryResponseCache(
    private val ttl: Duration = Duration.ofMinutes(5),
    private val maxSize: Int = 1000
) : ResponseCache {
    
    private data class CacheEntry(
        val response: GetCandidatesResponse,
        val timestamp: Instant
    )
    
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    
    override fun get(key: String): GetCandidatesResponse? {
        val entry = cache[key] ?: return null
        
        val age = Duration.between(entry.timestamp, Instant.now())
        if (age > ttl) {
            cache.remove(key)
            return null
        }
        
        return entry.response
    }
    
    override fun put(key: String, response: GetCandidatesResponse) {
        // Evict oldest entries if cache is full
        if (cache.size >= maxSize) {
            val oldestKey = cache.entries
                .minByOrNull { it.value.timestamp }
                ?.key
            
            oldestKey?.let { cache.remove(it) }
        }
        
        cache[key] = CacheEntry(response, Instant.now())
    }
    
    override fun clear() {
        cache.clear()
    }
}

/**
 * Interface for circuit breaker pattern.
 */
interface CircuitBreaker {
    
    /**
     * Checks if a request should be allowed.
     * 
     * @return true if request allowed, false if circuit is open
     */
    fun allowRequest(): Boolean
    
    /**
     * Records a successful request.
     */
    fun recordSuccess()
    
    /**
     * Records a failed request.
     */
    fun recordFailure()
}

/**
 * Simple circuit breaker implementation.
 * 
 * States:
 * - CLOSED: Normal operation
 * - OPEN: Failing, reject requests
 * - HALF_OPEN: Testing recovery
 * 
 * @property failureThreshold Number of failures before opening
 * @property recoveryTimeout Duration before attempting recovery
 */
class SimpleCircuitBreaker(
    private val failureThreshold: Int = 5,
    private val recoveryTimeout: Duration = Duration.ofSeconds(30)
) : CircuitBreaker {
    
    private enum class State { CLOSED, OPEN, HALF_OPEN }
    
    @Volatile
    private var state = State.CLOSED
    
    @Volatile
    private var failureCount = 0
    
    @Volatile
    private var lastFailureTime: Instant? = null
    
    override fun allowRequest(): Boolean {
        return when (state) {
            State.CLOSED -> true
            State.OPEN -> {
                // Check if recovery timeout has passed
                val lastFailure = lastFailureTime
                if (lastFailure != null) {
                    val timeSinceFailure = Duration.between(lastFailure, Instant.now())
                    if (timeSinceFailure > recoveryTimeout) {
                        logger.info { "Circuit breaker entering HALF_OPEN state" }
                        state = State.HALF_OPEN
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            State.HALF_OPEN -> true
        }
    }
    
    override fun recordSuccess() {
        when (state) {
            State.HALF_OPEN -> {
                logger.info { "Circuit breaker closing after successful recovery" }
                state = State.CLOSED
                failureCount = 0
                lastFailureTime = null
            }
            State.CLOSED -> {
                // Reset failure count on success
                failureCount = 0
            }
            State.OPEN -> {
                // Ignore success in open state
            }
        }
    }
    
    override fun recordFailure() {
        failureCount++
        lastFailureTime = Instant.now()
        
        when (state) {
            State.CLOSED -> {
                if (failureCount >= failureThreshold) {
                    logger.warn { "Circuit breaker opening after $failureCount failures" }
                    state = State.OPEN
                }
            }
            State.HALF_OPEN -> {
                logger.warn { "Circuit breaker reopening after failure in HALF_OPEN state" }
                state = State.OPEN
            }
            State.OPEN -> {
                // Already open
            }
        }
    }
}
