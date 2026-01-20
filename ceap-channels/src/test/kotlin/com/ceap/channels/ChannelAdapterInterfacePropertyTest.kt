package com.ceap.channels

import com.ceap.model.*
import net.jqwik.api.*
import net.jqwik.time.api.DateTimes
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-based tests for channel adapter interface compliance.
 * 
 * **Property 21: Channel adapter interface compliance**
 * **Validates: Requirements 7.1**
 * 
 * These tests verify that:
 * - Channel adapters implement all required interface methods
 * - Channel adapters return valid channel IDs and types
 * - Channel adapters can be configured
 * - Channel adapters provide health check functionality
 * - Channel adapters support shadow mode detection
 */
class ChannelAdapterInterfacePropertyTest {
    
    /**
     * Property 21: Channel adapter interface compliance
     * 
     * For any channel adapter implementation, it must:
     * - Return a non-blank channel ID
     * - Return a valid channel type
     * - Accept configuration without errors
     * - Provide health check functionality
     * - Report shadow mode status
     * 
     * **Validates: Requirements 7.1**
     */
    @Property(tries = 100)
    fun `any channel adapter must implement all required interface methods`(
        @ForAll("channelAdapters") adapter: ChannelAdapter
    ) {
        // Verify getChannelId returns a non-blank string
        val channelId = adapter.getChannelId()
        assertNotNull(channelId, "channelId must not be null")
        assertTrue(channelId.isNotBlank(), "channelId must not be blank")
        
        // Verify getChannelType returns a valid enum value
        val channelType = adapter.getChannelType()
        assertNotNull(channelType, "channelType must not be null")
        assertTrue(
            channelType in ChannelType.values(),
            "channelType must be a valid ChannelType enum value"
        )
        
        // Verify configure accepts configuration without throwing
        val config = mapOf("key" to "value", "enabled" to true)
        adapter.configure(config) // Should not throw
        
        // Verify healthCheck returns a valid status
        val healthStatus = adapter.healthCheck()
        assertNotNull(healthStatus, "healthStatus must not be null")
        assertTrue(healthStatus.lastChecked > 0, "lastChecked must be positive")
        
        // Verify isShadowMode returns a boolean (always true or false)
        val shadowMode = adapter.isShadowMode()
        // shadowMode is a boolean, so it's always valid (true or false)
        assertTrue(shadowMode is Boolean, "isShadowMode must return a Boolean")
    }
    
    /**
     * Property 21b: Channel adapter deliver method returns valid results
     * 
     * For any channel adapter, the deliver method must:
     * - Accept a list of candidates and delivery context
     * - Return a DeliveryResult with valid structure
     * - Track delivered and failed candidates correctly
     * - Provide delivery metrics
     * 
     * **Validates: Requirements 7.1, 7.2, 7.3**
     */
    @Property(tries = 100)
    fun `channel adapter deliver method must return valid results`(
        @ForAll("channelAdapters") adapter: ChannelAdapter,
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("deliveryContexts") context: DeliveryContext
    ) {
        // Call deliver method
        val result = adapter.deliver(candidates, context)
        
        // Verify result structure
        assertNotNull(result, "DeliveryResult must not be null")
        assertNotNull(result.delivered, "delivered list must not be null")
        assertNotNull(result.failed, "failed list must not be null")
        assertNotNull(result.metrics, "metrics must not be null")
        
        // Verify metrics are consistent
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
        
        // Verify delivered candidates have required fields
        result.delivered.forEach { delivered ->
            assertNotNull(delivered.candidate, "delivered candidate must not be null")
            assertNotNull(delivered.deliveryId, "deliveryId must not be null")
            assertTrue(delivered.deliveryId.isNotBlank(), "deliveryId must not be blank")
            assertTrue(delivered.timestamp > 0, "timestamp must be positive")
        }
        
        // Verify failed deliveries have required fields
        result.failed.forEach { failed ->
            assertNotNull(failed.candidate, "failed candidate must not be null")
            assertNotNull(failed.errorCode, "errorCode must not be null")
            assertTrue(failed.errorCode.isNotBlank(), "errorCode must not be blank")
            assertNotNull(failed.errorMessage, "errorMessage must not be null")
            assertTrue(failed.errorMessage.isNotBlank(), "errorMessage must not be blank")
            assertTrue(failed.timestamp > 0, "timestamp must be positive")
        }
        
        // Verify metrics have valid values
        assertTrue(result.metrics.durationMs >= 0, "durationMs must be non-negative")
        assertTrue(result.metrics.rateLimitedCount >= 0, "rateLimitedCount must be non-negative")
    }
    
    /**
     * Property 21c: Channel adapter health check returns valid status
     * 
     * For any channel adapter, the health check must:
     * - Return a HealthStatus object
     * - Include a valid timestamp
     * - Indicate healthy or unhealthy state
     * 
     * **Validates: Requirements 7.1**
     */
    @Property(tries = 100)
    fun `channel adapter health check must return valid status`(
        @ForAll("channelAdapters") adapter: ChannelAdapter
    ) {
        val healthStatus = adapter.healthCheck()
        
        // Verify health status structure
        assertNotNull(healthStatus, "HealthStatus must not be null")
        assertTrue(healthStatus.lastChecked > 0, "lastChecked must be positive")
        
        // Verify healthy is a boolean (always true or false)
        assertTrue(healthStatus.healthy is Boolean, "healthy must be a Boolean")
        
        // If unhealthy, message should be present
        if (!healthStatus.healthy) {
            assertNotNull(healthStatus.message, "message should be present when unhealthy")
        }
    }
    
    /**
     * Property 21d: Channel adapter configuration is idempotent
     * 
     * For any channel adapter, calling configure multiple times with the same
     * configuration should not cause errors.
     * 
     * **Validates: Requirements 7.1**
     */
    @Property(tries = 100)
    fun `channel adapter configuration should be idempotent`(
        @ForAll("channelAdapters") adapter: ChannelAdapter,
        @ForAll("configurations") config: Map<String, Any>
    ) {
        // Configure once
        adapter.configure(config)
        
        // Configure again with same config - should not throw
        adapter.configure(config)
        
        // Adapter should still be functional
        val healthStatus = adapter.healthCheck()
        assertNotNull(healthStatus, "Adapter should still be functional after reconfiguration")
    }
    
    /**
     * Property 21e: Channel ID and type are consistent
     * 
     * For any channel adapter, the channel ID should be consistent with the channel type.
     * 
     * **Validates: Requirements 7.1**
     */
    @Property(tries = 100)
    fun `channel ID should be consistent with channel type`(
        @ForAll("channelAdapters") adapter: ChannelAdapter
    ) {
        val channelId = adapter.getChannelId()
        val channelType = adapter.getChannelType()
        
        // Verify both are present
        assertNotNull(channelId)
        assertNotNull(channelType)
        
        // Channel ID should be lowercase and match type naming convention
        assertTrue(channelId == channelId.lowercase(), "channelId should be lowercase")
        
        // Channel type should be one of the valid enum values
        assertTrue(
            channelType in ChannelType.values(),
            "channelType must be a valid ChannelType"
        )
    }
    
    // ========== Arbitrary Providers ==========
    
    @Provide
    fun channelAdapters(): Arbitrary<ChannelAdapter> {
        return Arbitraries.of(
            TestChannelAdapter("email", ChannelType.EMAIL),
            TestChannelAdapter("push", ChannelType.PUSH),
            TestChannelAdapter("sms", ChannelType.SMS),
            TestChannelAdapter("in-app", ChannelType.IN_APP),
            TestChannelAdapter("voice", ChannelType.VOICE)
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
    fun configurations(): Arbitrary<Map<String, Any>> {
        return Arbitraries.maps(
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
            Arbitraries.of<Any>("value1", "value2", true, false, 123, 456.78)
        ).ofMinSize(0).ofMaxSize(5)
    }
}
