package com.solicitation.channels

import com.solicitation.model.*
import net.jqwik.api.*
import net.jqwik.time.api.DateTimes
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Property-based tests for opt-out enforcement.
 * 
 * **Property 44: Opt-out enforcement**
 * **Validates: Requirements 14.3**
 * 
 * For any customer who has opted out of emails, they must be excluded from all
 * email campaigns, regardless of program.
 */
class OptOutEnforcementPropertyTest {
    
    /**
     * Property 44: Opt-out enforcement
     * 
     * For any customer who has opted out of emails, they must be excluded from all
     * email campaigns, regardless of program.
     * 
     * **Validates: Requirements 14.3**
     */
    @Property(tries = 100)
    fun `opted-out customers must be excluded from all email campaigns`(
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("optOutCustomerIds") optOutCustomerIds: Set<String>,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
        Assume.that(candidates.isNotEmpty())
        
        // Setup email adapter with opt-out list
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
                ),
                "optOutList" to optOutCustomerIds.toList()
            )
        )
        
        // Create delivery context
        val context = DeliveryContext(
            programId = programId,
            marketplace = marketplace,
            campaignId = "campaign-${System.nanoTime()}",
            shadowMode = false
        )
        
        // Deliver candidates
        val result = adapter.deliver(candidates, context)
        
        // Verify no opted-out customers were delivered
        val deliveredCustomerIds = result.delivered.map { it.candidate.customerId }.toSet()
        val optedOutDelivered = deliveredCustomerIds.intersect(optOutCustomerIds)
        
        assertTrue(
            optedOutDelivered.isEmpty(),
            "No opted-out customers should be delivered: $optedOutDelivered"
        )
        
        // Verify opted-out customers are in failed list with correct error code
        val optedOutCandidates = candidates.filter { optOutCustomerIds.contains(it.customerId) }
        val optedOutFailures = result.failed.filter { it.errorCode == "OPTED_OUT" }
        
        assertEquals(
            optedOutCandidates.size,
            optedOutFailures.size,
            "All opted-out candidates should be in failed list"
        )
        
        // Verify error messages
        optedOutFailures.forEach { failure ->
            assertTrue(
                optOutCustomerIds.contains(failure.candidate.customerId),
                "Failed candidate must be in opt-out list"
            )
            assertEquals(
                "OPTED_OUT",
                failure.errorCode,
                "Error code must be OPTED_OUT"
            )
            assertFalse(
                failure.retryable,
                "Opt-out failures should not be retryable"
            )
        }
    }
    
    /**
     * Property 44b: Opt-out enforcement across programs
     * 
     * For any customer who has opted out, they must be excluded from campaigns
     * across all programs, not just specific programs.
     * 
     * **Validates: Requirements 14.3**
     */
    @Property(tries = 100)
    fun `opt-out enforcement must apply across all programs`(
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("customerIds") optOutCustomerId: String,
        @ForAll("programPairs") programPair: Pair<String, String>,
        @ForAll("marketplaces") marketplace: String
    ) {
        Assume.that(candidates.isNotEmpty())
        Assume.that(programPair.first != programPair.second)
        
        // Create candidates with the opted-out customer ID
        val candidatesWithOptOut = candidates.map { candidate ->
            candidate.copy(customerId = optOutCustomerId)
        }
        
        val program1 = programPair.first
        val program2 = programPair.second
        
        // Setup email adapter with opt-out list
        val adapter = EmailChannelAdapter()
        adapter.configure(
            mapOf(
                "templates" to mapOf(
                    program1 to mapOf(
                        "templateId" to "template-$program1",
                        "subject" to "Subject 1",
                        "fromAddress" to "test1@example.com",
                        "fromName" to "Sender 1"
                    ),
                    program2 to mapOf(
                        "templateId" to "template-$program2",
                        "subject" to "Subject 2",
                        "fromAddress" to "test2@example.com",
                        "fromName" to "Sender 2"
                    )
                ),
                "optOutList" to listOf(optOutCustomerId)
            )
        )
        
        // Deliver for program 1
        val context1 = DeliveryContext(
            programId = program1,
            marketplace = marketplace,
            campaignId = "campaign-1-${System.nanoTime()}",
            shadowMode = false
        )
        val result1 = adapter.deliver(candidatesWithOptOut, context1)
        
        // Deliver for program 2
        val context2 = DeliveryContext(
            programId = program2,
            marketplace = marketplace,
            campaignId = "campaign-2-${System.nanoTime()}",
            shadowMode = false
        )
        val result2 = adapter.deliver(candidatesWithOptOut, context2)
        
        // Verify no deliveries in either program
        assertEquals(
            0,
            result1.delivered.size,
            "No deliveries should occur for opted-out customer in program 1"
        )
        assertEquals(
            0,
            result2.delivered.size,
            "No deliveries should occur for opted-out customer in program 2"
        )
        
        // Verify all failures are due to opt-out
        assertTrue(
            result1.failed.all { it.errorCode == "OPTED_OUT" },
            "All program 1 failures should be due to opt-out"
        )
        assertTrue(
            result2.failed.all { it.errorCode == "OPTED_OUT" },
            "All program 2 failures should be due to opt-out"
        )
    }
    
    /**
     * Property 44c: Opt-out status check
     * 
     * The adapter must provide a way to check if a customer is opted out.
     * 
     * **Validates: Requirements 14.3**
     */
    @Property(tries = 100)
    fun `adapter must correctly report opt-out status`(
        @ForAll("customerIds") customerId: String,
        @ForAll("optOutCustomerIds") optOutCustomerIds: Set<String>
    ) {
        val adapter = EmailChannelAdapter()
        adapter.configure(
            mapOf(
                "optOutList" to optOutCustomerIds.toList()
            )
        )
        
        // Check opt-out status
        val isOptedOut = adapter.isOptedOut(customerId)
        
        // Verify status matches opt-out list
        assertEquals(
            optOutCustomerIds.contains(customerId),
            isOptedOut,
            "Opt-out status must match opt-out list"
        )
    }
    
    /**
     * Property 44d: Dynamic opt-out management
     * 
     * The adapter must support adding and removing customers from the opt-out list.
     * 
     * **Validates: Requirements 14.3**
     */
    @Property(tries = 100)
    fun `adapter must support dynamic opt-out management`(
        @ForAll("customerIds") customerId: String
    ) {
        val adapter = EmailChannelAdapter()
        adapter.configure(emptyMap())
        
        // Initially not opted out
        assertFalse(
            adapter.isOptedOut(customerId),
            "Customer should not be opted out initially"
        )
        
        // Add to opt-out list
        adapter.addOptOut(customerId)
        assertTrue(
            adapter.isOptedOut(customerId),
            "Customer should be opted out after adding"
        )
        
        // Remove from opt-out list
        adapter.removeOptOut(customerId)
        assertFalse(
            adapter.isOptedOut(customerId),
            "Customer should not be opted out after removing"
        )
    }
    
    /**
     * Property 44e: Opt-out takes precedence over other filters
     * 
     * Even if a customer passes all other filters (frequency cap, eligibility),
     * they must be excluded if opted out.
     * 
     * **Validates: Requirements 14.3**
     */
    @Property(tries = 100)
    fun `opt-out must take precedence over other filters`(
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("customerIds") optOutCustomerId: String,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
        Assume.that(candidates.isNotEmpty())
        
        // Create candidates with the opted-out customer ID
        val candidatesWithOptOut = candidates.map { candidate ->
            candidate.copy(customerId = optOutCustomerId)
        }
        
        // Setup adapter with generous frequency cap (should allow delivery)
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
                ),
                "frequencyCaps" to mapOf(
                    programId to mapOf(
                        "maxEmailsPerWindow" to 100,
                        "windowDays" to 1
                    )
                ),
                "optOutList" to listOf(optOutCustomerId)
            )
        )
        
        val context = DeliveryContext(
            programId = programId,
            marketplace = marketplace,
            campaignId = "campaign-${System.nanoTime()}",
            shadowMode = false
        )
        
        val result = adapter.deliver(candidatesWithOptOut, context)
        
        // Verify no deliveries despite generous frequency cap
        assertEquals(
            0,
            result.delivered.size,
            "No deliveries should occur for opted-out customer"
        )
        
        // Verify all failures are due to opt-out, not frequency cap
        assertTrue(
            result.failed.all { it.errorCode == "OPTED_OUT" },
            "All failures should be due to opt-out, not frequency cap"
        )
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
    fun programPairs(): Arbitrary<Pair<String, String>> {
        val programs = Arbitraries.of("product-reviews", "video-ratings", "music-feedback", "service-reviews")
        return Combinators.combine(programs, programs).`as` { p1, p2 -> Pair(p1, p2) }
    }
    
    @Provide
    fun customerIds(): Arbitrary<String> {
        return Arbitraries.strings().withCharRange('a', 'z').numeric()
            .ofMinLength(8).ofMaxLength(20).map { "customer-$it" }
    }
    
    @Provide
    fun optOutCustomerIds(): Arbitrary<Set<String>> {
        return customerIds().set().ofMinSize(0).ofMaxSize(5)
    }
    
    @Provide
    fun marketplaces(): Arbitrary<String> {
        return Arbitraries.of("US", "UK", "DE", "FR", "JP", "CA")
    }
}
