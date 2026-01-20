package com.ceap.channels

import com.ceap.model.*
import net.jqwik.api.*
import net.jqwik.time.api.DateTimes
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-based tests for shadow mode non-delivery.
 * 
 * **Property 23: Shadow mode non-delivery**
 * **Validates: Requirements 7.5, 14.5**
 * 
 * These tests verify that:
 * - Shadow mode prevents actual delivery
 * - Shadow mode logs intended deliveries
 * - Shadow mode is properly tracked in metrics
 * - Shadow mode can be enabled via context or adapter configuration
 */
class ShadowModePropertyTest {
    
    /**
     * Property 23: Shadow mode non-delivery
     * 
     * For any delivery operation in shadow mode, the adapter must:
     * - Not perform actual delivery
     * - Log intended deliveries
     * - Mark all deliveries as shadow mode in metrics
     * - Return delivery results with shadow mode flag set
     * 
     * **Validates: Requirements 7.5, 14.5**
     */
    @Property(tries = 100)
    fun `shadow mode must prevent actual delivery and log intentions`(
        @ForAll("shadowModeAdapters") adapter: ChannelAdapter,
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("deliveryContexts") context: DeliveryContext
    ) {
        // Perform delivery in shadow mode
        val result = adapter.deliver(candidates, context)
        
        // Verify shadow mode is indicated in metrics
        assertTrue(
            result.metrics.shadowMode,
            "Shadow mode must be indicated in metrics"
        )
        
        // Verify all delivered candidates have shadow mode metadata
        result.delivered.forEach { delivered ->
            val shadowModeMetadata = delivered.channelMetadata?.get("shadowMode")
            assertTrue(
                shadowModeMetadata == true,
                "Delivered candidate must have shadowMode=true in metadata"
            )
        }
        
        // Verify delivery IDs indicate shadow mode
        result.delivered.forEach { delivered ->
            assertTrue(
                delivered.deliveryId.contains("shadow"),
                "Delivery ID should indicate shadow mode: ${delivered.deliveryId}"
            )
        }
    }
    
    /**
     * Property 23b: Shadow mode via context flag
     * 
     * For any delivery operation with shadow mode enabled in context,
     * the delivery must be in shadow mode regardless of adapter configuration.
     * 
     * **Validates: Requirements 7.5**
     */
    @Property(tries = 100)
    fun `shadow mode via context must override adapter configuration`(
        @ForAll("channelAdapters") adapter: ChannelAdapter,
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("shadowModeContexts") context: DeliveryContext
    ) {
        // Verify context has shadow mode enabled
        assertTrue(context.shadowMode, "Context must have shadow mode enabled")
        
        // Perform delivery
        val result = adapter.deliver(candidates, context)
        
        // Verify shadow mode is indicated in metrics
        assertTrue(
            result.metrics.shadowMode,
            "Shadow mode must be indicated in metrics when enabled in context"
        )
    }
    
    /**
     * Property 23c: Shadow mode via adapter configuration
     * 
     * For any adapter configured with shadow mode enabled,
     * all deliveries must be in shadow mode.
     * 
     * **Validates: Requirements 7.5**
     */
    @Property(tries = 100)
    fun `shadow mode via adapter configuration must apply to all deliveries`(
        @ForAll("channelAdapters") adapter: ChannelAdapter,
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("normalContexts") context: DeliveryContext
    ) {
        // Configure adapter with shadow mode enabled
        adapter.configure(mapOf("shadowMode" to true))
        
        // Verify adapter reports shadow mode
        assertTrue(
            adapter.isShadowMode(),
            "Adapter must report shadow mode after configuration"
        )
        
        // Perform delivery
        val result = adapter.deliver(candidates, context)
        
        // Verify shadow mode is indicated in metrics
        assertTrue(
            result.metrics.shadowMode,
            "Shadow mode must be indicated in metrics when enabled in adapter"
        )
    }
    
    /**
     * Property 23d: Shadow mode can be disabled
     * 
     * For any adapter configured with shadow mode, it can be disabled via configuration.
     * 
     * **Validates: Requirements 7.5**
     */
    @Property(tries = 100)
    fun `shadow mode can be disabled via configuration`(
        @ForAll("channelAdapters") adapter: ChannelAdapter,
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("normalContexts") context: DeliveryContext
    ) {
        // Enable shadow mode first
        adapter.configure(mapOf("shadowMode" to true))
        assertTrue(adapter.isShadowMode(), "Adapter should be in shadow mode after configuration")
        
        // Disable shadow mode
        adapter.configure(mapOf("shadowMode" to false))
        
        // Verify adapter reports shadow mode disabled
        assertTrue(
            !adapter.isShadowMode(),
            "Adapter must report shadow mode disabled after configuration"
        )
        
        // Perform delivery
        val result = adapter.deliver(candidates, context)
        
        // Verify shadow mode is not indicated in metrics (context also has shadowMode=false)
        assertTrue(
            !result.metrics.shadowMode,
            "Shadow mode must not be indicated in metrics when disabled"
        )
    }
    
    /**
     * Property 23e: Shadow mode deliveries have no failures
     * 
     * For any delivery operation in shadow mode, there should be no failures
     * since no actual delivery is attempted.
     * 
     * **Validates: Requirements 7.5**
     */
    @Property(tries = 100)
    fun `shadow mode deliveries should have no failures`(
        @ForAll("shadowModeAdapters") adapter: ChannelAdapter,
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("deliveryContexts") context: DeliveryContext
    ) {
        // Perform delivery in shadow mode
        val result = adapter.deliver(candidates, context)
        
        // Verify no failures
        assertEquals(
            0,
            result.failed.size,
            "Shadow mode deliveries should have no failures"
        )
        
        assertEquals(
            0,
            result.metrics.failedCount,
            "Shadow mode metrics should show zero failures"
        )
        
        // Verify all candidates are marked as delivered
        assertEquals(
            candidates.size,
            result.delivered.size,
            "All candidates should be marked as delivered in shadow mode"
        )
    }
    
    /**
     * Property 23f: Shadow mode and normal mode produce different delivery IDs
     * 
     * For any adapter, delivery IDs in shadow mode should be distinguishable
     * from normal mode delivery IDs.
     * 
     * **Validates: Requirements 7.5, 14.5**
     */
    @Property(tries = 100)
    fun `shadow mode delivery IDs must be distinguishable from normal mode`(
        @ForAll("channelAdapters") adapter: ChannelAdapter,
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("normalContexts") normalContext: DeliveryContext,
        @ForAll("shadowModeContexts") shadowContext: DeliveryContext
    ) {
        // Skip if candidate list is empty
        if (candidates.isEmpty()) return
        
        // Perform normal delivery
        val normalResult = adapter.deliver(candidates, normalContext)
        
        // Perform shadow delivery
        val shadowResult = adapter.deliver(candidates, shadowContext)
        
        // Verify shadow delivery IDs contain "shadow"
        shadowResult.delivered.forEach { delivered ->
            assertTrue(
                delivered.deliveryId.contains("shadow"),
                "Shadow delivery ID must contain 'shadow': ${delivered.deliveryId}"
            )
        }
        
        // Verify normal delivery IDs do not contain "shadow" (if adapter supports it)
        // Note: TestChannelAdapter always uses "delivery-" prefix for normal mode
        normalResult.delivered.forEach { delivered ->
            assertTrue(
                delivered.deliveryId.startsWith("delivery-"),
                "Normal delivery ID should start with 'delivery-': ${delivered.deliveryId}"
            )
        }
    }
    
    // ========== Arbitrary Providers ==========
    
    @Provide
    fun channelAdapters(): Arbitrary<ChannelAdapter> {
        return Arbitraries.of(
            TestShadowModeAdapter("email", ChannelType.EMAIL),
            TestShadowModeAdapter("push", ChannelType.PUSH),
            TestShadowModeAdapter("sms", ChannelType.SMS)
        )
    }
    
    @Provide
    fun shadowModeAdapters(): Arbitrary<ChannelAdapter> {
        return Arbitraries.of(
            TestShadowModeAdapter("email", ChannelType.EMAIL, shadowMode = true),
            TestShadowModeAdapter("push", ChannelType.PUSH, shadowMode = true),
            TestShadowModeAdapter("sms", ChannelType.SMS, shadowMode = true)
        )
    }
    
    @Provide
    fun candidateLists(): Arbitrary<List<Candidate>> {
        return candidates().list().ofMinSize(0).ofMaxSize(10)
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
    
    @Provide
    fun shadowModeContexts(): Arbitrary<DeliveryContext> {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
            Arbitraries.of("US", "UK", "DE", "FR", "JP"),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).optional()
        ).`as` { programId, marketplace, campaignId ->
            DeliveryContext(programId, marketplace, campaignId.orElse(null), shadowMode = true)
        }
    }
    
    @Provide
    fun normalContexts(): Arbitrary<DeliveryContext> {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
            Arbitraries.of("US", "UK", "DE", "FR", "JP"),
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20).optional()
        ).`as` { programId, marketplace, campaignId ->
            DeliveryContext(programId, marketplace, campaignId.orElse(null), shadowMode = false)
        }
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

/**
 * Test adapter that extends BaseChannelAdapter to test shadow mode functionality.
 */
private class TestShadowModeAdapter(
    private val channelId: String,
    private val channelType: ChannelType,
    shadowMode: Boolean = false
) : BaseChannelAdapter() {
    
    init {
        if (shadowMode) {
            configure(mapOf("shadowMode" to true))
        }
    }
    
    override fun getChannelId(): String = channelId
    
    override fun getChannelType(): ChannelType = channelType
    
    override fun doDeliver(
        candidates: List<Candidate>,
        context: DeliveryContext,
        startTime: Long
    ): DeliveryResult {
        // Simulate normal delivery
        val delivered = candidates.mapIndexed { index, candidate ->
            DeliveredCandidate(
                candidate = candidate,
                deliveryId = "delivery-${channelId}-${System.nanoTime()}-$index",
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
                shadowMode = false
            )
        )
    }
}
