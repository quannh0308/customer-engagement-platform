package com.ceap.channels

import com.ceap.model.*
import net.jqwik.api.*
import net.jqwik.time.api.DateTimes
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-based tests for email campaign automation.
 * 
 * **Property 42: Email campaign automation**
 * **Validates: Requirements 14.1**
 * 
 * For any set of email candidates selected for delivery, a campaign must be created
 * automatically with the correct program template and recipient list.
 */
class EmailCampaignAutomationPropertyTest {
    
    /**
     * Property 42: Email campaign automation
     * 
     * For any set of email candidates selected for delivery, a campaign must be created
     * automatically with the correct program template and recipient list.
     * 
     * **Validates: Requirements 14.1**
     */
    @Property(tries = 100)
    fun `email campaigns must be created automatically for selected candidates`(
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
        // Setup email adapter with template for this program
        val adapter = EmailChannelAdapter()
        val templateId = "template-$programId"
        adapter.configure(
            mapOf(
                "templates" to mapOf(
                    programId to mapOf(
                        "templateId" to templateId,
                        "subject" to "Test Subject",
                        "fromAddress" to "test@example.com",
                        "fromName" to "Test Sender"
                    )
                )
            )
        )
        
        // Create delivery context
        val campaignId = "campaign-${System.nanoTime()}"
        val context = DeliveryContext(
            programId = programId,
            marketplace = marketplace,
            campaignId = campaignId,
            shadowMode = false
        )
        
        // Deliver candidates
        val result = adapter.deliver(candidates, context)
        
        // Verify campaign was created (deliveries were attempted)
        assertNotNull(result, "DeliveryResult must not be null")
        
        // Verify all delivered candidates have campaign metadata
        result.delivered.forEach { delivered ->
            assertNotNull(delivered.channelMetadata, "Channel metadata must be present")
            assertEquals(
                campaignId,
                delivered.channelMetadata!!["campaignId"],
                "Campaign ID must match"
            )
            assertEquals(
                templateId,
                delivered.channelMetadata!!["templateId"],
                "Template ID must match program template"
            )
            assertEquals(
                "email",
                delivered.channelMetadata!!["channel"],
                "Channel must be email"
            )
        }
        
        // Verify metrics are consistent
        assertEquals(
            candidates.size,
            result.metrics.totalCandidates,
            "Total candidates must match input"
        )
        
        val totalProcessed = result.delivered.size + result.failed.size
        assertEquals(
            candidates.size,
            totalProcessed,
            "All candidates must be either delivered or failed"
        )
    }
    
    /**
     * Property 42b: Campaign creation with empty candidate list
     * 
     * For an empty candidate list, the adapter should handle gracefully without errors.
     * 
     * **Validates: Requirements 14.1**
     */
    @Property(tries = 50)
    fun `email adapter must handle empty candidate lists gracefully`(
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
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
            campaignId = "campaign-empty",
            shadowMode = false
        )
        
        // Deliver empty list
        val result = adapter.deliver(emptyList(), context)
        
        // Verify result is valid
        assertNotNull(result)
        assertEquals(0, result.delivered.size, "No candidates should be delivered")
        assertEquals(0, result.failed.size, "No candidates should fail")
        assertEquals(0, result.metrics.totalCandidates, "Total candidates should be 0")
    }
    
    /**
     * Property 42c: Campaign creation without template configuration
     * 
     * For a program without a configured template, all candidates should fail
     * with appropriate error codes.
     * 
     * **Validates: Requirements 14.1**
     */
    @Property(tries = 50)
    fun `email adapter must fail gracefully when template is not configured`(
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
        Assume.that(candidates.isNotEmpty())
        
        // Create adapter without template configuration
        val adapter = EmailChannelAdapter()
        adapter.configure(emptyMap())
        
        val context = DeliveryContext(
            programId = programId,
            marketplace = marketplace,
            campaignId = "campaign-no-template",
            shadowMode = false
        )
        
        // Deliver candidates
        val result = adapter.deliver(candidates, context)
        
        // Verify all candidates failed
        assertEquals(0, result.delivered.size, "No candidates should be delivered without template")
        assertEquals(candidates.size, result.failed.size, "All candidates should fail")
        
        // Verify error codes
        result.failed.forEach { failed ->
            assertEquals(
                "NO_TEMPLATE",
                failed.errorCode,
                "Error code should indicate missing template"
            )
            assertTrue(
                failed.errorMessage.contains("No email template"),
                "Error message should mention missing template"
            )
        }
    }
    
    /**
     * Property 42d: Campaign recipient list matches input candidates
     * 
     * For any set of candidates, the delivered list should only contain candidates
     * from the input list (no duplicates or extra candidates).
     * 
     * **Validates: Requirements 14.1**
     */
    @Property(tries = 100)
    fun `campaign recipient list must match input candidates`(
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
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
        
        // Verify delivered candidates are from input list
        val inputCustomerIds = candidates.map { it.customerId }.toSet()
        result.delivered.forEach { delivered ->
            assertTrue(
                inputCustomerIds.contains(delivered.candidate.customerId),
                "Delivered candidate must be from input list"
            )
        }
        
        // Verify failed candidates are from input list
        result.failed.forEach { failed ->
            assertTrue(
                inputCustomerIds.contains(failed.candidate.customerId),
                "Failed candidate must be from input list"
            )
        }
        
        // Verify no duplicates in delivered list
        val deliveredCustomerIds = result.delivered.map { it.candidate.customerId }
        assertEquals(
            deliveredCustomerIds.size,
            deliveredCustomerIds.toSet().size,
            "No duplicate deliveries should occur"
        )
    }
    
    // ========== Arbitrary Providers ==========
    
    @Provide
    fun candidateLists(): Arbitrary<List<Candidate>> {
        return candidates().list().ofMinSize(0).ofMaxSize(20)
    }
    
    @Provide
    fun candidates(): Arbitrary<Candidate> {
        val customerIds = Arbitraries.strings().withCharRange('a', 'z').numeric()
            .ofMinLength(8).ofMaxLength(20).map { "customer-${System.nanoTime()}-$it" }
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
}
