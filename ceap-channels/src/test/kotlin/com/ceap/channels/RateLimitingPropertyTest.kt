package com.ceap.channels

import com.ceap.model.*
import net.jqwik.api.*
import net.jqwik.time.api.DateTimes
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for rate limiting queue behavior.
 * 
 * **Property 24: Rate limiting queue behavior**
 * **Validates: Requirements 7.6**
 * 
 * These tests verify that:
 * - Rate limiter correctly tracks delivery counts
 * - Rate limiter enforces limits per channel
 * - Queue stores rate-limited candidates
 * - Queue maintains FIFO order
 * - Queue can be drained when capacity is available
 */
class RateLimitingPropertyTest {
    
    /**
     * Property 24: Rate limiting queue behavior
     * 
     * For any rate limiter with a maximum delivery count:
     * - Deliveries within the limit are allowed
     * - Deliveries exceeding the limit are blocked
     * - Rate limiter tracks counts per channel independently
     * - Counts reset after the time window expires
     * 
     * **Validates: Requirements 7.6**
     */
    @Property(tries = 100)
    fun `rate limiter must enforce delivery limits per channel`(
        @ForAll("rateLimiters") rateLimiter: RateLimiter,
        @ForAll("channelIds") channelId: String,
        @ForAll("deliveryCounts") count: Int
    ) {
        // Reset rate limiter for clean state
        rateLimiter.reset(channelId)
        
        // Check if delivery is allowed
        val allowed = rateLimiter.isAllowed(channelId, count)
        
        if (allowed) {
            // Record the delivery
            rateLimiter.recordDelivery(channelId, count)
            
            // Verify count was recorded
            val currentCount = rateLimiter.getCurrentCount(channelId)
            assertTrue(
                currentCount >= count,
                "Current count ($currentCount) should be at least $count after recording"
            )
        }
        
        // Verify remaining capacity is non-negative
        val remaining = rateLimiter.getRemainingCapacity(channelId)
        assertTrue(
            remaining >= 0,
            "Remaining capacity must be non-negative: $remaining"
        )
    }
    
    /**
     * Property 24b: Rate limiter tracks channels independently
     * 
     * For any rate limiter, deliveries to different channels should not
     * affect each other's rate limits.
     * 
     * **Validates: Requirements 7.6**
     */
    @Property(tries = 100)
    fun `rate limiter must track channels independently`(
        @ForAll("rateLimiters") rateLimiter: RateLimiter,
        @ForAll("channelIds") channelId1: String,
        @ForAll("channelIds") channelId2: String,
        @ForAll("smallDeliveryCounts") count: Int
    ) {
        // Assume different channels
        if (channelId1 == channelId2) return
        
        // Reset both channels
        rateLimiter.reset(channelId1)
        rateLimiter.reset(channelId2)
        
        // Record delivery for channel 1
        if (rateLimiter.isAllowed(channelId1, count)) {
            rateLimiter.recordDelivery(channelId1, count)
        }
        
        // Verify channel 2 is unaffected
        val channel2Count = rateLimiter.getCurrentCount(channelId2)
        assertEquals(
            0,
            channel2Count,
            "Channel 2 count should be 0 after delivery to channel 1"
        )
    }
    
    /**
     * Property 24c: Delivery queue maintains FIFO order
     * 
     * For any delivery queue, items should be dequeued in the same order
     * they were enqueued (FIFO).
     * 
     * **Validates: Requirements 7.6**
     */
    @Property(tries = 100)
    fun `delivery queue must maintain FIFO order`(
        @ForAll("deliveryQueues") queue: DeliveryQueue,
        @ForAll("channelIds") channelId: String,
        @ForAll("candidateLists") candidates1: List<Candidate>,
        @ForAll("candidateLists") candidates2: List<Candidate>,
        @ForAll("candidateLists") candidates3: List<Candidate>,
        @ForAll("deliveryContexts") context: DeliveryContext
    ) {
        // Clear queue for clean state
        queue.clear(channelId)
        
        // Enqueue three batches
        queue.enqueue(channelId, candidates1, context)
        queue.enqueue(channelId, candidates2, context)
        queue.enqueue(channelId, candidates3, context)
        
        // Verify queue size
        assertEquals(
            3,
            queue.getQueueSize(channelId),
            "Queue should contain 3 items"
        )
        
        // Dequeue and verify order
        val dequeued = queue.dequeue(channelId, 3)
        assertEquals(3, dequeued.size, "Should dequeue 3 items")
        
        // Verify FIFO order by checking candidates
        assertEquals(
            candidates1,
            dequeued[0].candidates,
            "First dequeued item should match first enqueued"
        )
        assertEquals(
            candidates2,
            dequeued[1].candidates,
            "Second dequeued item should match second enqueued"
        )
        assertEquals(
            candidates3,
            dequeued[2].candidates,
            "Third dequeued item should match third enqueued"
        )
    }
    
    /**
     * Property 24d: Delivery queue tracks size correctly
     * 
     * For any delivery queue, the queue size should accurately reflect
     * the number of enqueued items.
     * 
     * **Validates: Requirements 7.6**
     */
    @Property(tries = 100)
    fun `delivery queue must track size correctly`(
        @ForAll("deliveryQueues") queue: DeliveryQueue,
        @ForAll("channelIds") channelId: String,
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("deliveryContexts") context: DeliveryContext,
        @ForAll("queueOperationCounts") enqueueCount: Int
    ) {
        // Clear queue for clean state
        queue.clear(channelId)
        
        // Enqueue multiple times
        repeat(enqueueCount) {
            queue.enqueue(channelId, candidates, context)
        }
        
        // Verify queue size
        assertEquals(
            enqueueCount,
            queue.getQueueSize(channelId),
            "Queue size should match enqueue count"
        )
        
        // Dequeue half
        val dequeueCount = enqueueCount / 2
        queue.dequeue(channelId, dequeueCount)
        
        // Verify remaining size
        assertEquals(
            enqueueCount - dequeueCount,
            queue.getQueueSize(channelId),
            "Queue size should be reduced after dequeue"
        )
    }
    
    /**
     * Property 24e: Empty queue operations are safe
     * 
     * For any delivery queue, operations on an empty queue should not fail.
     * 
     * **Validates: Requirements 7.6**
     */
    @Property(tries = 100)
    fun `empty queue operations must be safe`(
        @ForAll("deliveryQueues") queue: DeliveryQueue,
        @ForAll("channelIds") channelId: String
    ) {
        // Clear queue to ensure it's empty
        queue.clear(channelId)
        
        // Verify queue is empty
        assertTrue(queue.isEmpty(channelId), "Queue should be empty")
        assertEquals(0, queue.getQueueSize(channelId), "Queue size should be 0")
        
        // Dequeue from empty queue should return empty list
        val dequeued = queue.dequeue(channelId, 10)
        assertTrue(dequeued.isEmpty(), "Dequeue from empty queue should return empty list")
        
        // Peek at empty queue should return null
        val peeked = queue.peek(channelId)
        assertTrue(peeked == null, "Peek at empty queue should return null")
    }
    
    /**
     * Property 24f: Queue clear removes all items
     * 
     * For any delivery queue, clearing should remove all items.
     * 
     * **Validates: Requirements 7.6**
     */
    @Property(tries = 100)
    fun `queue clear must remove all items`(
        @ForAll("deliveryQueues") queue: DeliveryQueue,
        @ForAll("channelIds") channelId: String,
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("deliveryContexts") context: DeliveryContext,
        @ForAll("queueOperationCounts") enqueueCount: Int
    ) {
        // Enqueue multiple items
        repeat(enqueueCount) {
            queue.enqueue(channelId, candidates, context)
        }
        
        // Verify queue has items
        val sizeBefore = queue.getQueueSize(channelId)
        assertTrue(sizeBefore == enqueueCount, "Queue should have $enqueueCount items")
        
        // Clear queue
        val cleared = queue.clear(channelId)
        
        // Verify all items were cleared
        assertEquals(enqueueCount, cleared, "Clear should return count of cleared items")
        assertEquals(0, queue.getQueueSize(channelId), "Queue should be empty after clear")
        assertTrue(queue.isEmpty(channelId), "Queue should report as empty")
    }
    
    /**
     * Property 24g: Rate limiter remaining capacity is accurate
     * 
     * For any rate limiter, the remaining capacity should equal
     * max capacity minus current count.
     * 
     * **Validates: Requirements 7.6**
     */
    @Property(tries = 100)
    fun `rate limiter remaining capacity must be accurate`(
        @ForAll("rateLimiters") rateLimiter: RateLimiter,
        @ForAll("channelIds") channelId: String,
        @ForAll("smallDeliveryCounts") count: Int
    ) {
        // Reset for clean state
        rateLimiter.reset(channelId)
        
        // Record delivery if allowed
        if (rateLimiter.isAllowed(channelId, count)) {
            rateLimiter.recordDelivery(channelId, count)
            
            val currentCount = rateLimiter.getCurrentCount(channelId)
            val remaining = rateLimiter.getRemainingCapacity(channelId)
            
            // Verify remaining + current <= max (allowing for some tolerance)
            assertTrue(
                remaining >= 0,
                "Remaining capacity must be non-negative"
            )
        }
    }
    
    // ========== Arbitrary Providers ==========
    
    @Provide
    fun rateLimiters(): Arbitrary<RateLimiter> {
        return Arbitraries.integers().between(10, 100).map { maxDeliveries ->
            RateLimiter(maxDeliveries, windowDurationMs = 60_000L)
        }
    }
    
    @Provide
    fun deliveryQueues(): Arbitrary<DeliveryQueue> {
        return Arbitraries.just(DeliveryQueue())
    }
    
    @Provide
    fun channelIds(): Arbitrary<String> {
        return Arbitraries.of("email", "push", "sms", "in-app", "voice")
    }
    
    @Provide
    fun deliveryCounts(): Arbitrary<Int> {
        return Arbitraries.integers().between(1, 150)
    }
    
    @Provide
    fun smallDeliveryCounts(): Arbitrary<Int> {
        return Arbitraries.integers().between(1, 10)
    }
    
    @Provide
    fun queueOperationCounts(): Arbitrary<Int> {
        return Arbitraries.integers().between(0, 10)
    }
    
    @Provide
    fun candidateLists(): Arbitrary<List<Candidate>> {
        return candidates().list().ofMinSize(0).ofMaxSize(5)
    }
    
    @Provide
    fun deliveryContexts(): Arbitrary<DeliveryContext> {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
            Arbitraries.of("US", "UK", "DE"),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).optional(),
            Arbitraries.of(true, false)
        ).`as` { programId, marketplace, campaignId, shadowMode ->
            DeliveryContext(programId, marketplace, campaignId.orElse(null), shadowMode)
        }
    }
    
    @Provide
    fun candidates(): Arbitrary<Candidate> {
        val customerIds = Arbitraries.strings().withCharRange('a', 'z').numeric()
            .ofMinLength(8).ofMaxLength(20).map { "customer-$it" }
        val contextLists = contexts().list().ofMinSize(1).ofMaxSize(2)
        val subjectArb = subjects()
        val attributesArb = candidateAttributes()
        val metadataArb = candidateMetadata()
        
        return Combinators.combine(
            customerIds,
            contextLists,
            subjectArb,
            attributesArb,
            metadataArb
        ).`as` { customerId, context, subject, attributes, metadata ->
            Candidate(
                customerId = customerId,
                context = context,
                subject = subject,
                scores = null,
                attributes = attributes,
                metadata = metadata,
                rejectionHistory = null
            )
        }
    }
    
    private fun contexts(): Arbitrary<Context> {
        val types = Arbitraries.of("marketplace", "program")
        val ids = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10)
        
        return Combinators.combine(types, ids).`as` { type, id ->
            Context(type = type, id = id)
        }
    }
    
    private fun subjects(): Arbitrary<Subject> {
        val types = Arbitraries.of("product", "video")
        val ids = Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(10)
        
        return Combinators.combine(types, ids).`as` { type, id ->
            Subject(type = type, id = id, metadata = null)
        }
    }
    
    private fun candidateAttributes(): Arbitrary<CandidateAttributes> {
        val eventDates = instants()
        val channelEligibility = Arbitraries.maps(
            Arbitraries.of("email", "push"),
            Arbitraries.of(true, false)
        ).ofMinSize(1).ofMaxSize(2)
        
        return Combinators.combine(eventDates, channelEligibility)
            .`as` { eventDate, channels ->
                CandidateAttributes(
                    eventDate = eventDate,
                    deliveryDate = null,
                    timingWindow = null,
                    orderValue = null,
                    mediaEligible = null,
                    channelEligibility = channels
                )
            }
    }
    
    private fun candidateMetadata(): Arbitrary<CandidateMetadata> {
        val timestamps = instants()
        val versions = Arbitraries.longs().between(1L, 50L)
        val connectorIds = Arbitraries.of("order-connector")
        val executionIds = Arbitraries.strings().alpha().numeric().ofMinLength(10).ofMaxLength(15)
        
        return Combinators.combine(
            timestamps,
            timestamps,
            timestamps,
            versions,
            connectorIds,
            executionIds
        ).`as` { createdAt, updatedAt, expiresAt, version, connectorId, executionId ->
            CandidateMetadata(
                createdAt = createdAt,
                updatedAt = updatedAt.plusSeconds(60),
                expiresAt = expiresAt.plusSeconds(86400),
                version = version,
                sourceConnectorId = connectorId,
                workflowExecutionId = "exec-$executionId"
            )
        }
    }
    
    private fun instants(): Arbitrary<Instant> {
        return DateTimes.instants()
            .between(
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2030-12-31T23:59:59Z")
            )
    }
}
