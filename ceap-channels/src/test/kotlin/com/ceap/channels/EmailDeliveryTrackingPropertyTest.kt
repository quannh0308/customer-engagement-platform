package com.ceap.channels

import com.ceap.model.*
import net.jqwik.api.*
import net.jqwik.time.api.DateTimes
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property-based tests for email delivery tracking.
 * 
 * **Property 46: Email delivery tracking**
 * **Validates: Requirements 14.6**
 * 
 * For any email campaign sent, delivery status and open rates must be tracked
 * and recorded for each recipient.
 */
class EmailDeliveryTrackingPropertyTest {
    
    /**
     * Property 46: Email delivery tracking
     * 
     * For any email campaign sent, delivery status and open rates must be tracked
     * and recorded for each recipient.
     * 
     * **Validates: Requirements 14.6**
     */
    @Property(tries = 100)
    fun `email deliveries must be tracked with status and timestamps`(
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
        Assume.that(candidates.isNotEmpty())
        
        // Setup email adapter
        val adapter = EmailChannelAdapter()
        adapter.configure(
            mapOf(
                "templates" to mapOf(
                    programId to mapOf(
                        "templateId" to "template-$programId",
                        "subject" to "Test Subject",
                        "fromAddress" to "test@example.com",
                        "fromName" to "Test Sender"
                    )
                )
            )
        )
        
        val campaignId = "campaign-${System.nanoTime()}"
        val context = DeliveryContext(
            programId = programId,
            marketplace = marketplace,
            campaignId = campaignId,
            shadowMode = false
        )
        
        // Deliver candidates
        val result = adapter.deliver(candidates, context)
        
        // Verify all delivered candidates have tracking information
        result.delivered.forEach { delivered ->
            val deliveryId = delivered.deliveryId
            assertNotNull(deliveryId, "Delivery ID must be present")
            
            // Get tracking information
            val tracking = adapter.getDeliveryTracking(deliveryId)
            assertNotNull(tracking, "Tracking information must be available")
            
            // Verify tracking fields
            assertEquals(deliveryId, tracking.deliveryId, "Delivery ID must match")
            assertEquals(delivered.candidate.customerId, tracking.customerId, "Customer ID must match")
            assertEquals(campaignId, tracking.campaignId, "Campaign ID must match")
            assertTrue(tracking.sentAt > 0, "Sent timestamp must be positive")
            assertEquals(EmailStatus.SENT, tracking.status, "Initial status must be SENT")
            assertNull(tracking.openedAt, "Opened timestamp should be null initially")
        }
    }
    
    /**
     * Property 46b: Email open events must be tracked
     * 
     * When an email is opened, the tracking information must be updated with
     * the open status and timestamp.
     * 
     * **Validates: Requirements 14.6**
     */
    @Property(tries = 100)
    fun `email open events must be tracked`(
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
        Assume.that(candidates.isNotEmpty())
        
        val adapter = EmailChannelAdapter()
        adapter.configure(
            mapOf(
                "templates" to mapOf(
                    programId to mapOf(
                        "templateId" to "template-$programId",
                        "subject" to "Test Subject",
                        "fromAddress" to "test@example.com",
                        "fromName" to "Test Sender"
                    )
                )
            )
        )
        
        val context = DeliveryContext(
            programId = programId,
            marketplace = marketplace,
            campaignId = "campaign-${System.nanoTime()}",
            shadowMode = false
        )
        
        val result = adapter.deliver(candidates, context)
        
        // Record open events for all delivered emails
        result.delivered.forEach { delivered ->
            val deliveryId = delivered.deliveryId
            
            // Get initial tracking
            val trackingBefore = adapter.getDeliveryTracking(deliveryId)
            assertNotNull(trackingBefore)
            assertEquals(EmailStatus.SENT, trackingBefore.status)
            assertNull(trackingBefore.openedAt)
            
            // Record open event
            adapter.recordEmailOpen(deliveryId)
            
            // Get updated tracking
            val trackingAfter = adapter.getDeliveryTracking(deliveryId)
            assertNotNull(trackingAfter)
            assertEquals(EmailStatus.OPENED, trackingAfter.status, "Status must be OPENED")
            assertNotNull(trackingAfter.openedAt, "Opened timestamp must be set")
            assertTrue(
                trackingAfter.openedAt!! >= trackingAfter.sentAt,
                "Opened timestamp must be >= sent timestamp"
            )
        }
    }
    
    /**
     * Property 46c: Campaign metrics must be calculated correctly
     * 
     * For any campaign, the metrics (total sent, total opened, open rate) must
     * be calculated correctly based on tracking data.
     * 
     * **Validates: Requirements 14.6**
     */
    @Property(tries = 100)
    fun `campaign metrics must be calculated correctly`(
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String,
        @ForAll("openRates") openRate: Double
    ) {
        Assume.that(candidates.isNotEmpty())
        Assume.that(openRate >= 0.0 && openRate <= 1.0)
        
        val adapter = EmailChannelAdapter()
        adapter.configure(
            mapOf(
                "templates" to mapOf(
                    programId to mapOf(
                        "templateId" to "template-$programId",
                        "subject" to "Test Subject",
                        "fromAddress" to "test@example.com",
                        "fromName" to "Test Sender"
                    )
                )
            )
        )
        
        val campaignId = "campaign-${System.nanoTime()}"
        val context = DeliveryContext(
            programId = programId,
            marketplace = marketplace,
            campaignId = campaignId,
            shadowMode = false
        )
        
        val result = adapter.deliver(candidates, context)
        
        // Simulate opens based on open rate
        val numToOpen = (result.delivered.size * openRate).toInt()
        result.delivered.take(numToOpen).forEach { delivered ->
            adapter.recordEmailOpen(delivered.deliveryId)
        }
        
        // Get campaign metrics
        val metrics = adapter.getCampaignMetrics(campaignId)
        
        // Verify metrics
        assertEquals(campaignId, metrics.campaignId, "Campaign ID must match")
        assertEquals(result.delivered.size, metrics.totalSent, "Total sent must match delivered count")
        assertEquals(numToOpen, metrics.totalOpened, "Total opened must match simulated opens")
        
        // Verify open rate calculation
        val expectedOpenRate = if (result.delivered.isEmpty()) 0.0 else numToOpen.toDouble() / result.delivered.size
        assertEquals(
            expectedOpenRate,
            metrics.openRate,
            0.01,
            "Open rate must be calculated correctly"
        )
    }
    
    /**
     * Property 46d: Tracking must be per delivery
     * 
     * Each delivery must have its own tracking information, independent of other deliveries.
     * 
     * **Validates: Requirements 14.6**
     */
    @Property(tries = 100)
    fun `tracking must be independent per delivery`(
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
        Assume.that(candidates.size >= 2)
        
        val adapter = EmailChannelAdapter()
        adapter.configure(
            mapOf(
                "templates" to mapOf(
                    programId to mapOf(
                        "templateId" to "template-$programId",
                        "subject" to "Test Subject",
                        "fromAddress" to "test@example.com",
                        "fromName" to "Test Sender"
                    )
                )
            )
        )
        
        val context = DeliveryContext(
            programId = programId,
            marketplace = marketplace,
            campaignId = "campaign-${System.nanoTime()}",
            shadowMode = false
        )
        
        val result = adapter.deliver(candidates, context)
        Assume.that(result.delivered.size >= 2)
        
        val delivery1 = result.delivered[0]
        val delivery2 = result.delivered[1]
        
        // Record open for first delivery only
        adapter.recordEmailOpen(delivery1.deliveryId)
        
        // Verify first delivery is opened
        val tracking1 = adapter.getDeliveryTracking(delivery1.deliveryId)
        assertNotNull(tracking1)
        assertEquals(EmailStatus.OPENED, tracking1.status)
        assertNotNull(tracking1.openedAt)
        
        // Verify second delivery is still sent (not opened)
        val tracking2 = adapter.getDeliveryTracking(delivery2.deliveryId)
        assertNotNull(tracking2)
        assertEquals(EmailStatus.SENT, tracking2.status)
        assertNull(tracking2.openedAt)
    }
    
    /**
     * Property 46e: Tracking must include campaign ID
     * 
     * All tracking information must include the campaign ID for grouping and reporting.
     * 
     * **Validates: Requirements 14.6**
     */
    @Property(tries = 100)
    fun `tracking must include campaign ID`(
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
        Assume.that(candidates.isNotEmpty())
        
        val adapter = EmailChannelAdapter()
        adapter.configure(
            mapOf(
                "templates" to mapOf(
                    programId to mapOf(
                        "templateId" to "template-$programId",
                        "subject" to "Test Subject",
                        "fromAddress" to "test@example.com",
                        "fromName" to "Test Sender"
                    )
                )
            )
        )
        
        val campaignId = "campaign-${System.nanoTime()}"
        val context = DeliveryContext(
            programId = programId,
            marketplace = marketplace,
            campaignId = campaignId,
            shadowMode = false
        )
        
        val result = adapter.deliver(candidates, context)
        
        // Verify all tracking includes campaign ID
        result.delivered.forEach { delivered ->
            val tracking = adapter.getDeliveryTracking(delivered.deliveryId)
            assertNotNull(tracking)
            assertEquals(campaignId, tracking.campaignId, "Campaign ID must be included in tracking")
        }
    }
    
    /**
     * Property 46f: Tracking for non-existent delivery returns null
     * 
     * Requesting tracking for a non-existent delivery ID should return null.
     * 
     * **Validates: Requirements 14.6**
     */
    @Property(tries = 50)
    fun `tracking for non-existent delivery must return null`(
        @ForAll("deliveryIds") nonExistentDeliveryId: String
    ) {
        val adapter = EmailChannelAdapter()
        adapter.configure(emptyMap())
        
        val tracking = adapter.getDeliveryTracking(nonExistentDeliveryId)
        assertNull(tracking, "Tracking for non-existent delivery should be null")
    }
    
    // ========== Arbitrary Providers ==========
    
    @Provide
    fun candidateLists(): Arbitrary<List<Candidate>> {
        return candidates().list().ofMinSize(1).ofMaxSize(10)
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
    fun programIds(): Arbitrary<String> {
        return Arbitraries.of("product-reviews", "video-ratings", "music-feedback", "service-reviews")
    }
    
    @Provide
    fun marketplaces(): Arbitrary<String> {
        return Arbitraries.of("US", "UK", "DE", "FR", "JP", "CA")
    }
    
    @Provide
    fun openRates(): Arbitrary<Double> {
        return Arbitraries.doubles().between(0.0, 1.0)
    }
    
    @Provide
    fun deliveryIds(): Arbitrary<String> {
        return Arbitraries.strings().alpha().numeric().ofMinLength(10).ofMaxLength(30)
            .map { "delivery-$it" }
    }
}
