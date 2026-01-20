package com.ceap.channels

import com.ceap.model.*
import net.jqwik.api.*
import net.jqwik.time.api.DateTimes
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-based tests for delivery status tracking.
 * 
 * **Property 22: Delivery status tracking**
 * **Validates: Requirements 7.3**
 * 
 * These tests verify that:
 * - Delivery results accurately track delivered and failed candidates
 * - Delivery metrics are consistent with actual delivery outcomes
 * - Delivery IDs are unique and properly assigned
 * - Timestamps are properly recorded for all deliveries
 */
class DeliveryStatusTrackingPropertyTest {
    
    /**
     * Property 22: Delivery status tracking
     * 
     * For any delivery operation, the delivery result must:
     * - Track all delivered candidates with unique delivery IDs
     * - Track all failed deliveries with error information
     * - Provide accurate metrics (counts, duration)
     * - Ensure delivered + failed counts <= total candidates
     * 
     * **Validates: Requirements 7.3**
     */
    @Property(tries = 100)
    fun `delivery results must accurately track delivered and failed candidates`(
        @ForAll("channelAdapters") adapter: ChannelAdapter,
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("deliveryContexts") context: DeliveryContext
    ) {
        // Perform delivery
        val result = adapter.deliver(candidates, context)
        
        // Verify all delivered candidates have unique delivery IDs
        val deliveryIds = result.delivered.map { it.deliveryId }
        assertEquals(
            deliveryIds.size,
            deliveryIds.toSet().size,
            "All delivery IDs must be unique"
        )
        
        // Verify all delivered candidates have valid timestamps
        result.delivered.forEach { delivered ->
            assertTrue(
                delivered.timestamp > 0,
                "Delivery timestamp must be positive"
            )
        }
        
        // Verify all failed deliveries have error information
        result.failed.forEach { failed ->
            assertNotNull(failed.errorCode, "Failed delivery must have error code")
            assertTrue(failed.errorCode.isNotBlank(), "Error code must not be blank")
            assertNotNull(failed.errorMessage, "Failed delivery must have error message")
            assertTrue(failed.errorMessage.isNotBlank(), "Error message must not be blank")
            assertTrue(failed.timestamp > 0, "Failure timestamp must be positive")
        }
        
        // Verify metrics consistency
        val totalProcessed = result.delivered.size + result.failed.size
        assertTrue(
            totalProcessed <= candidates.size,
            "Total processed (${totalProcessed}) must not exceed input size (${candidates.size})"
        )
        
        assertEquals(
            result.delivered.size,
            result.metrics.deliveredCount,
            "Delivered count in metrics must match delivered list size"
        )
        
        assertEquals(
            result.failed.size,
            result.metrics.failedCount,
            "Failed count in metrics must match failed list size"
        )
        
        assertEquals(
            candidates.size,
            result.metrics.totalCandidates,
            "Total candidates in metrics must match input size"
        )
        
        // Verify duration is non-negative
        assertTrue(
            result.metrics.durationMs >= 0,
            "Duration must be non-negative"
        )
    }
    
    /**
     * Property 22b: Delivery IDs must be unique across multiple deliveries
     * 
     * For any sequence of delivery operations, all delivery IDs must be unique.
     * 
     * **Validates: Requirements 7.3**
     */
    @Property(tries = 100)
    fun `delivery IDs must be unique across multiple deliveries`(
        @ForAll("channelAdapters") adapter: ChannelAdapter,
        @ForAll("candidateLists") candidates1: List<Candidate>,
        @ForAll("candidateLists") candidates2: List<Candidate>,
        @ForAll("deliveryContexts") context: DeliveryContext
    ) {
        // Perform two deliveries
        val result1 = adapter.deliver(candidates1, context)
        val result2 = adapter.deliver(candidates2, context)
        
        // Collect all delivery IDs
        val allDeliveryIds = result1.delivered.map { it.deliveryId } +
                            result2.delivered.map { it.deliveryId }
        
        // Verify all delivery IDs are unique
        assertEquals(
            allDeliveryIds.size,
            allDeliveryIds.toSet().size,
            "All delivery IDs across multiple deliveries must be unique"
        )
    }
    
    /**
     * Property 22c: Delivered candidates must reference original candidates
     * 
     * For any delivery operation, each delivered candidate must reference
     * one of the original input candidates.
     * 
     * **Validates: Requirements 7.3**
     */
    @Property(tries = 100)
    fun `delivered candidates must reference original input candidates`(
        @ForAll("channelAdapters") adapter: ChannelAdapter,
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("deliveryContexts") context: DeliveryContext
    ) {
        // Perform delivery
        val result = adapter.deliver(candidates, context)
        
        // Verify each delivered candidate is from the input list
        result.delivered.forEach { delivered ->
            assertTrue(
                candidates.contains(delivered.candidate),
                "Delivered candidate must be from the input list"
            )
        }
        
        // Verify each failed candidate is from the input list
        result.failed.forEach { failed ->
            assertTrue(
                candidates.contains(failed.candidate),
                "Failed candidate must be from the input list"
            )
        }
    }
    
    /**
     * Property 22d: Delivery timestamps must be in chronological order
     * 
     * For any delivery operation, timestamps should be reasonable and
     * within the delivery operation timeframe.
     * 
     * **Validates: Requirements 7.3**
     */
    @Property(tries = 100)
    fun `delivery timestamps must be reasonable`(
        @ForAll("channelAdapters") adapter: ChannelAdapter,
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("deliveryContexts") context: DeliveryContext
    ) {
        val startTime = System.currentTimeMillis()
        
        // Perform delivery
        val result = adapter.deliver(candidates, context)
        
        val endTime = System.currentTimeMillis()
        
        // Verify all timestamps are within the delivery timeframe
        result.delivered.forEach { delivered ->
            assertTrue(
                delivered.timestamp >= startTime,
                "Delivery timestamp must be after operation start"
            )
            assertTrue(
                delivered.timestamp <= endTime + 1000, // Allow 1 second buffer
                "Delivery timestamp must be before operation end (with buffer)"
            )
        }
        
        result.failed.forEach { failed ->
            assertTrue(
                failed.timestamp >= startTime,
                "Failure timestamp must be after operation start"
            )
            assertTrue(
                failed.timestamp <= endTime + 1000, // Allow 1 second buffer
                "Failure timestamp must be before operation end (with buffer)"
            )
        }
    }
    
    /**
     * Property 22e: Metrics must be consistent with delivery outcomes
     * 
     * For any delivery operation, the metrics must accurately reflect
     * the actual delivery outcomes.
     * 
     * **Validates: Requirements 7.3**
     */
    @Property(tries = 100)
    fun `metrics must be consistent with delivery outcomes`(
        @ForAll("channelAdapters") adapter: ChannelAdapter,
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("deliveryContexts") context: DeliveryContext
    ) {
        // Perform delivery
        val result = adapter.deliver(candidates, context)
        
        // Verify total candidates
        assertEquals(
            candidates.size,
            result.metrics.totalCandidates,
            "Total candidates must match input size"
        )
        
        // Verify delivered count
        assertEquals(
            result.delivered.size,
            result.metrics.deliveredCount,
            "Delivered count must match delivered list size"
        )
        
        // Verify failed count
        assertEquals(
            result.failed.size,
            result.metrics.failedCount,
            "Failed count must match failed list size"
        )
        
        // Verify rate limited count is non-negative
        assertTrue(
            result.metrics.rateLimitedCount >= 0,
            "Rate limited count must be non-negative"
        )
        
        // Verify shadow mode flag matches context
        assertEquals(
            context.shadowMode,
            result.metrics.shadowMode,
            "Shadow mode in metrics must match context"
        )
    }
    
    /**
     * Property 22f: Empty candidate list should produce empty results
     * 
     * For any delivery operation with an empty candidate list,
     * the result should have zero delivered and failed candidates.
     * 
     * **Validates: Requirements 7.3**
     */
    @Property(tries = 100)
    fun `empty candidate list should produce empty results`(
        @ForAll("channelAdapters") adapter: ChannelAdapter,
        @ForAll("deliveryContexts") context: DeliveryContext
    ) {
        // Perform delivery with empty list
        val result = adapter.deliver(emptyList(), context)
        
        // Verify empty results
        assertEquals(0, result.delivered.size, "Delivered list should be empty")
        assertEquals(0, result.failed.size, "Failed list should be empty")
        assertEquals(0, result.metrics.totalCandidates, "Total candidates should be 0")
        assertEquals(0, result.metrics.deliveredCount, "Delivered count should be 0")
        assertEquals(0, result.metrics.failedCount, "Failed count should be 0")
    }
    
    // ========== Arbitrary Providers ==========
    
    @Provide
    fun channelAdapters(): Arbitrary<ChannelAdapter> {
        return Arbitraries.of(
            TestChannelAdapter("email", ChannelType.EMAIL),
            TestChannelAdapter("push", ChannelType.PUSH),
            TestChannelAdapter("sms", ChannelType.SMS)
        )
    }
    
    @Provide
    fun candidateLists(): Arbitrary<List<Candidate>> {
        return candidates().list().ofMinSize(0).ofMaxSize(10)
    }
    
    @Provide
    fun candidates(): Arbitrary<Candidate> {
        val customerIds = Arbitraries.strings().withCharRange('a', 'z').numeric()
            .ofMinLength(8).ofMaxLength(20).map { "customer-$it" }
        val contextLists = contexts().list().ofMinSize(1).ofMaxSize(3)
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
    
    @Provide
    fun deliveryContexts(): Arbitrary<DeliveryContext> {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
            Arbitraries.of("US", "UK", "DE", "FR", "JP"),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).optional(),
            Arbitraries.of(true, false)
        ).`as` { programId, marketplace, campaignId, shadowMode ->
            DeliveryContext(programId, marketplace, campaignId.orElse(null), shadowMode)
        }
    }
    
    private fun contexts(): Arbitrary<Context> {
        val types = Arbitraries.of("marketplace", "program", "vertical")
        val ids = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(20)
        
        return Combinators.combine(types, ids).`as` { type, id ->
            Context(type = type, id = id)
        }
    }
    
    private fun subjects(): Arbitrary<Subject> {
        val types = Arbitraries.of("product", "video", "track", "service")
        val ids = Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(15)
        
        return Combinators.combine(types, ids).`as` { type, id ->
            Subject(type = type, id = id, metadata = null)
        }
    }
    
    private fun candidateAttributes(): Arbitrary<CandidateAttributes> {
        val eventDates = instants()
        val channelEligibility = Arbitraries.maps(
            Arbitraries.of("email", "push", "sms", "in-app"),
            Arbitraries.of(true, false)
        ).ofMinSize(1).ofMaxSize(4)
        
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
        val versions = Arbitraries.longs().between(1L, 100L)
        val connectorIds = Arbitraries.of("order-connector", "review-connector")
        val executionIds = Arbitraries.strings().alpha().numeric().ofMinLength(10).ofMaxLength(20)
        
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
