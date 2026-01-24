package com.ceap.serving

import com.ceap.model.*
import com.ceap.storage.CandidateRepository
import com.ceap.storage.StorageException
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.*
import java.time.Instant

/**
 * Property-based tests for serving API fallback behavior.
 * 
 * **Feature: ceap-platform, Property 19: Serving API fallback behavior**
 * 
 * **Validates: Requirements 6.5**
 * 
 * For any serving request, if dependencies fail (scoring, eligibility checks), the API
 * must return cached or stale results (if available) and log the degradation, rather
 * than failing the request.
 */
class FallbackBehaviorPropertyTest {
    
    @Property(tries = 100)
    fun `fallback returns cached response when available`(
        @ForAll("request") request: GetCandidatesRequest
    ) {
        // Given: A cache with a stored response
        val cache = InMemoryResponseCache()
        val cachedResponse = GetCandidatesResponse(
            candidates = listOf(createCandidate()),
            metadata = ResponseMetadata(
                totalCount = 1,
                filteredCount = 1
            ),
            latencyMs = 10
        )
        val cacheKey = "${request.customerId}:${request.program}:${request.channel}:${request.marketplace}:${request.limit}"
        cache.put(cacheKey, cachedResponse)
        
        // And: A repository that throws an exception
        val repository = mock<CandidateRepository> {
            on { queryByProgramAndChannel(any(), any(), any(), any()) } doThrow StorageException("Test failure")
        }
        
        // And: A fallback handler
        val fallbackHandler = DefaultFallbackHandler(repository, cache)
        
        // When: Fallback is triggered
        val response = fallbackHandler.handleFailure(request, StorageException("Test"), 50)
        
        // Then: Response should be from cache
        assertThat(response.candidates).hasSize(1)
        assertThat(response.metadata.cacheHit).isTrue()
        assertThat(response.metadata.degraded).isTrue()
    }
    
    @Property(tries = 100)
    fun `fallback returns empty response when cache is empty and query fails`(
        @ForAll("request") request: GetCandidatesRequest
    ) {
        // Given: An empty cache
        val cache = InMemoryResponseCache()
        
        // And: A repository that throws an exception
        val repository = mock<CandidateRepository> {
            on { queryByProgramAndChannel(any(), any(), any(), any()) } doThrow StorageException("Test failure")
        }
        
        // And: A circuit breaker that allows requests
        val circuitBreaker = SimpleCircuitBreaker()
        
        // And: A fallback handler
        val fallbackHandler = DefaultFallbackHandler(repository, cache, circuitBreaker)
        
        // When: Fallback is triggered
        val response = fallbackHandler.handleFailure(request, StorageException("Test"), 50)
        
        // Then: Response should be empty and degraded
        assertThat(response.candidates).isEmpty()
        assertThat(response.metadata.degraded).isTrue()
        assertThat(response.metadata.totalCount).isEqualTo(0)
    }
    
    @Property(tries = 100)
    fun `fallback attempts alternative query when circuit breaker allows`(
        @ForAll("request") request: GetCandidatesRequest
    ) {
        // Given: An empty cache
        val cache = InMemoryResponseCache()
        
        // And: A repository that returns fallback results
        val fallbackCandidate = createCandidate()
        val repository = mock<CandidateRepository> {
            on { queryByProgramAndChannel(any(), any(), any(), any()) } doReturn listOf(fallbackCandidate)
        }
        
        // And: A circuit breaker that allows requests
        val circuitBreaker = SimpleCircuitBreaker()
        
        // And: A fallback handler
        val fallbackHandler = DefaultFallbackHandler(repository, cache, circuitBreaker)
        
        // When: Fallback is triggered with program and channel
        val requestWithProgramAndChannel = request.copy(
            program = "test-program",
            channel = "email"
        )
        val response = fallbackHandler.handleFailure(requestWithProgramAndChannel, StorageException("Test"), 50)
        
        // Then: Response should contain fallback results
        assertThat(response.candidates).hasSize(1)
        assertThat(response.metadata.degraded).isTrue()
        
        // And: Repository should have been called
        verify(repository).queryByProgramAndChannel(eq("test-program"), eq("email"), any(), any())
    }
    
    @Property(tries = 100)
    fun `circuit breaker opens after threshold failures`() {
        // Given: A circuit breaker with threshold of 5
        val circuitBreaker = SimpleCircuitBreaker(failureThreshold = 5)
        
        // When: We record 5 failures
        repeat(5) {
            circuitBreaker.recordFailure()
        }
        
        // Then: Circuit breaker should not allow requests
        assertThat(circuitBreaker.allowRequest()).isFalse()
    }
    
    @Property(tries = 100)
    fun `circuit breaker closes after successful recovery`() {
        // Given: A circuit breaker that is open with short recovery timeout
        val circuitBreaker = SimpleCircuitBreaker(
            failureThreshold = 3,
            recoveryTimeout = java.time.Duration.ofMillis(50)
        )
        repeat(3) {
            circuitBreaker.recordFailure()
        }
        
        // Verify it's open
        assertThat(circuitBreaker.allowRequest()).isFalse()
        
        // When: We wait for recovery timeout
        Thread.sleep(100) // Wait longer than recovery timeout
        
        // Then: Circuit breaker should allow a test request (HALF_OPEN)
        assertThat(circuitBreaker.allowRequest()).isTrue()
        
        // When: We record success
        circuitBreaker.recordSuccess()
        
        // Then: Circuit breaker should be closed and allow requests
        assertThat(circuitBreaker.allowRequest()).isTrue()
    }
    
    @Property(tries = 100)
    fun `cache evicts old entries when full`() {
        // Given: A cache with max size of 2
        val cache = InMemoryResponseCache(maxSize = 2)
        
        // When: We add 3 entries
        val response1 = createResponse(1)
        val response2 = createResponse(2)
        val response3 = createResponse(3)
        
        cache.put("key1", response1)
        Thread.sleep(10) // Ensure different timestamps
        cache.put("key2", response2)
        Thread.sleep(10)
        cache.put("key3", response3)
        
        // Then: Oldest entry should be evicted
        assertThat(cache.get("key1")).isNull()
        assertThat(cache.get("key2")).isNotNull()
        assertThat(cache.get("key3")).isNotNull()
    }
    
    @Property(tries = 100)
    fun `cache respects TTL`() {
        // Given: A cache with 100ms TTL
        val cache = InMemoryResponseCache(ttl = java.time.Duration.ofMillis(100))
        
        // When: We add an entry and wait for TTL to expire
        val response = createResponse(1)
        cache.put("key1", response)
        
        // Then: Entry should be available immediately
        assertThat(cache.get("key1")).isNotNull()
        
        // When: We wait for TTL to expire
        Thread.sleep(150)
        
        // Then: Entry should be evicted
        assertThat(cache.get("key1")).isNull()
    }
    
    // Arbitraries
    
    @Provide
    fun request(): Arbitrary<GetCandidatesRequest> {
        return Arbitraries.of(
            GetCandidatesRequest(
                customerId = "cust1",
                channel = "email",
                program = "prog1",
                marketplace = "US",
                limit = 10
            ),
            GetCandidatesRequest(
                customerId = "cust2",
                channel = "in-app",
                program = "prog2",
                marketplace = "UK",
                limit = 5
            ),
            GetCandidatesRequest(
                customerId = "cust3",
                channel = null,
                program = null,
                marketplace = "US",
                limit = 20
            )
        )
    }
    
    private fun createCandidate(): Candidate {
        val now = Instant.now()
        
        return Candidate(
            customerId = "test-customer",
            context = listOf(
                Context(type = "program", id = "test-program"),
                Context(type = "marketplace", id = "US")
            ),
            subject = Subject(
                type = "product",
                id = "test-product",
                metadata = null
            ),
            scores = mapOf(
                "model1" to Score(
                    modelId = "model1",
                    value = 0.8,
                    confidence = 0.9,
                    timestamp = now,
                    metadata = null
                )
            ),
            attributes = CandidateAttributes(
                eventDate = now.minusSeconds(86400),
                deliveryDate = null,
                timingWindow = null,
                orderValue = null,
                mediaEligible = true,
                channelEligibility = mapOf("email" to true)
            ),
            metadata = CandidateMetadata(
                createdAt = now,
                updatedAt = now,
                expiresAt = now.plusSeconds(86400 * 30),
                version = 1,
                sourceConnectorId = "test-connector",
                workflowExecutionId = "test-execution"
            ),
            rejectionHistory = null
        )
    }
    
    private fun createResponse(id: Int): GetCandidatesResponse {
        return GetCandidatesResponse(
            candidates = listOf(createCandidate()),
            metadata = ResponseMetadata(
                totalCount = 1,
                filteredCount = 1
            ),
            latencyMs = 10
        )
    }
}
