package com.solicitation.channels

import com.solicitation.model.*
import net.jqwik.api.*
import net.jqwik.time.api.DateTimes
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for email frequency capping.
 * 
 * **Property 45: Email frequency capping**
 * **Validates: Requirements 14.4**
 * 
 * For any customer, the number of emails sent within a configured time window
 * must not exceed the frequency cap.
 */
class EmailFrequencyCappingPropertyTest {
    
    /**
     * Property 45: Email frequency capping
     * 
     * For any customer, the number of emails sent within a configured time window
     * must not exceed the frequency cap.
     * 
     * **Validates: Requirements 14.4**
     */
    @Property(tries = 100)
    fun `email frequency must not exceed configured cap`(
        @ForAll("customerIds") customerId: String,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String,
        @ForAll("frequencyCaps") frequencyCap: FrequencyCap
    ) {
        Assume.that(frequencyCap.maxEmailsPerWindow > 0)
        Assume.that(frequencyCap.windowDays > 0)
        
        // Setup email adapter with frequency cap
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
                        "maxEmailsPerWindow" to frequencyCap.maxEmailsPerWindow,
                        "windowDays" to frequencyCap.windowDays
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
        
        // Create candidate for this customer
        val candidate = createCandidate(customerId)
        
        // Send emails up to the cap
        var deliveredCount = 0
        var cappedCount = 0
        
        for (i in 1..(frequencyCap.maxEmailsPerWindow + 5)) {
            val result = adapter.deliver(listOf(candidate), context)
            
            if (result.delivered.isNotEmpty()) {
                deliveredCount++
            } else if (result.failed.any { it.errorCode == "FREQUENCY_CAP_EXCEEDED" }) {
                cappedCount++
            }
        }
        
        // Verify frequency cap was enforced
        assertTrue(
            deliveredCount <= frequencyCap.maxEmailsPerWindow,
            "Delivered count ($deliveredCount) must not exceed cap (${frequencyCap.maxEmailsPerWindow})"
        )
        
        // Verify some deliveries were capped
        assertTrue(
            cappedCount > 0,
            "Some deliveries should be capped after exceeding limit"
        )
    }
    
    /**
     * Property 45b: Frequency cap is per customer
     * 
     * Frequency caps must be enforced independently per customer.
     * 
     * **Validates: Requirements 14.4**
     */
    @Property(tries = 50)
    fun `frequency cap must be enforced per customer independently`(
        @ForAll("customerPairs") customerPair: Pair<String, String>,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
        Assume.that(customerPair.first != customerPair.second)
        
        val customer1 = customerPair.first
        val customer2 = customerPair.second
        
        // Setup adapter with frequency cap of 2 emails per 7 days
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
                        "maxEmailsPerWindow" to 2,
                        "windowDays" to 7
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
        
        // Send 2 emails to customer1
        val candidate1 = createCandidate(customer1)
        adapter.deliver(listOf(candidate1), context)
        adapter.deliver(listOf(candidate1), context)
        
        // Third email to customer1 should be capped
        val result1 = adapter.deliver(listOf(candidate1), context)
        assertTrue(
            result1.failed.any { it.errorCode == "FREQUENCY_CAP_EXCEEDED" },
            "Customer1 should be frequency capped after 2 emails"
        )
        
        // Customer2 should still be able to receive emails
        val candidate2 = createCandidate(customer2)
        val result2 = adapter.deliver(listOf(candidate2), context)
        assertTrue(
            result2.delivered.isNotEmpty(),
            "Customer2 should not be affected by customer1's frequency cap"
        )
    }
    
    /**
     * Property 45c: Frequency cap window is time-based
     * 
     * Frequency caps must respect the configured time window.
     * 
     * **Validates: Requirements 14.4**
     */
    @Property(tries = 50)
    fun `frequency cap must respect time window`(
        @ForAll("customerIds") customerId: String,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
        // Setup adapter with frequency cap of 2 emails per 7 days
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
                        "maxEmailsPerWindow" to 2,
                        "windowDays" to 7
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
        
        val candidate = createCandidate(customerId)
        
        // Send 2 emails
        val result1 = adapter.deliver(listOf(candidate), context)
        val result2 = adapter.deliver(listOf(candidate), context)
        
        // Verify both were delivered
        assertEquals(1, result1.delivered.size, "First email should be delivered")
        assertEquals(1, result2.delivered.size, "Second email should be delivered")
        
        // Third email should be capped
        val result3 = adapter.deliver(listOf(candidate), context)
        assertTrue(
            result3.failed.any { it.errorCode == "FREQUENCY_CAP_EXCEEDED" },
            "Third email should be frequency capped"
        )
        
        // Verify frequency cap check
        val frequencyCap = FrequencyCap(maxEmailsPerWindow = 2, windowDays = 7)
        assertTrue(
            adapter.isFrequencyCapped(customerId, frequencyCap, programId),
            "Customer should be frequency capped"
        )
    }
    
    /**
     * Property 45d: Frequency cap is per program
     * 
     * Frequency caps must be configured and enforced per program.
     * 
     * **Validates: Requirements 14.4**
     */
    @Property(tries = 50)
    fun `frequency cap must be per program`(
        @ForAll("customerIds") customerId: String,
        @ForAll("programPairs") programPair: Pair<String, String>,
        @ForAll("marketplaces") marketplace: String
    ) {
        Assume.that(programPair.first != programPair.second)
        
        val program1 = programPair.first
        val program2 = programPair.second
        
        // Setup adapter with different frequency caps per program
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
                "frequencyCaps" to mapOf(
                    program1 to mapOf(
                        "maxEmailsPerWindow" to 1,
                        "windowDays" to 7
                    ),
                    program2 to mapOf(
                        "maxEmailsPerWindow" to 3,
                        "windowDays" to 7
                    )
                )
            )
        )
        
        val candidate = createCandidate(customerId)
        
        // Send 1 email for program1
        val context1 = DeliveryContext(
            programId = program1,
            marketplace = marketplace,
            campaignId = "campaign-1-${System.nanoTime()}",
            shadowMode = false
        )
        val result1a = adapter.deliver(listOf(candidate), context1)
        assertEquals(1, result1a.delivered.size, "First email for program1 should be delivered")
        
        // Second email for program1 should be capped
        val result1b = adapter.deliver(listOf(candidate), context1)
        assertTrue(
            result1b.failed.any { it.errorCode == "FREQUENCY_CAP_EXCEEDED" },
            "Second email for program1 should be capped (cap=1)"
        )
        
        // Program2 should have its own cap (3 emails)
        val context2 = DeliveryContext(
            programId = program2,
            marketplace = marketplace,
            campaignId = "campaign-2-${System.nanoTime()}",
            shadowMode = false
        )
        
        // Send 3 emails for program2 - all should succeed
        val result2a = adapter.deliver(listOf(candidate), context2)
        val result2b = adapter.deliver(listOf(candidate), context2)
        val result2c = adapter.deliver(listOf(candidate), context2)
        
        assertEquals(1, result2a.delivered.size, "First email for program2 should be delivered")
        assertEquals(1, result2b.delivered.size, "Second email for program2 should be delivered")
        assertEquals(1, result2c.delivered.size, "Third email for program2 should be delivered")
        
        // Fourth email for program2 should be capped
        val result2d = adapter.deliver(listOf(candidate), context2)
        assertTrue(
            result2d.failed.any { it.errorCode == "FREQUENCY_CAP_EXCEEDED" },
            "Fourth email for program2 should be capped (cap=3)"
        )
    }
    
    /**
     * Property 45e: Frequency cap failures are retryable
     * 
     * Frequency cap failures should be marked as retryable since they may
     * succeed after the time window expires.
     * 
     * **Validates: Requirements 14.4**
     */
    @Property(tries = 50)
    fun `frequency cap failures must be retryable`(
        @ForAll("customerIds") customerId: String,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
        // Setup adapter with frequency cap
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
                        "maxEmailsPerWindow" to 1,
                        "windowDays" to 7
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
        
        val candidate = createCandidate(customerId)
        
        // Send first email
        adapter.deliver(listOf(candidate), context)
        
        // Second email should be capped
        val result = adapter.deliver(listOf(candidate), context)
        
        val cappedFailures = result.failed.filter { it.errorCode == "FREQUENCY_CAP_EXCEEDED" }
        assertTrue(
            cappedFailures.isNotEmpty(),
            "Should have frequency cap failures"
        )
        
        // Verify all frequency cap failures are retryable
        cappedFailures.forEach { failure ->
            assertTrue(
                failure.retryable,
                "Frequency cap failures should be retryable"
            )
        }
    }
    
    // ========== Helper Methods ==========
    
    private fun createCandidate(customerId: String): Candidate {
        return Candidate(
            customerId = customerId,
            context = listOf(Context(type = "marketplace", id = "US")),
            subject = Subject(type = "product", id = "PROD123", metadata = null),
            scores = null,
            attributes = CandidateAttributes(
                eventDate = Instant.now(),
                deliveryDate = null,
                timingWindow = null,
                orderValue = null,
                mediaEligible = null,
                channelEligibility = mapOf("email" to true)
            ),
            metadata = CandidateMetadata(
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                expiresAt = Instant.now().plusSeconds(86400),
                version = 1,
                sourceConnectorId = "test-connector",
                workflowExecutionId = "exec-test"
            ),
            rejectionHistory = null
        )
    }
    
    // ========== Arbitrary Providers ==========
    
    @Provide
    fun customerIds(): Arbitrary<String> {
        return Arbitraries.strings().withCharRange('a', 'z').numeric()
            .ofMinLength(8).ofMaxLength(20).map { "customer-$it" }
    }
    
    @Provide
    fun customerPairs(): Arbitrary<Pair<String, String>> {
        return Combinators.combine(customerIds(), customerIds()).`as` { c1, c2 -> Pair(c1, c2) }
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
    fun marketplaces(): Arbitrary<String> {
        return Arbitraries.of("US", "UK", "DE", "FR", "JP", "CA")
    }
    
    @Provide
    fun frequencyCaps(): Arbitrary<FrequencyCap> {
        val maxEmails = Arbitraries.integers().between(1, 10)
        val windowDays = Arbitraries.integers().between(1, 30)
        
        return Combinators.combine(maxEmails, windowDays).`as` { max, days ->
            FrequencyCap(maxEmailsPerWindow = max, windowDays = days)
        }
    }
}
