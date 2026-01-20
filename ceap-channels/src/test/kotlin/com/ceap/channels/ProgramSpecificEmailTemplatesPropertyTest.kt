package com.ceap.channels

import com.ceap.model.*
import net.jqwik.api.*
import net.jqwik.time.api.DateTimes
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-based tests for program-specific email templates.
 * 
 * **Property 43: Program-specific email templates**
 * **Validates: Requirements 14.2**
 * 
 * For any email delivery, the template used must match the program configuration,
 * and different programs must be able to use different templates.
 */
class ProgramSpecificEmailTemplatesPropertyTest {
    
    /**
     * Property 43: Program-specific email templates
     * 
     * For any email delivery, the template used must match the program configuration,
     * and different programs must be able to use different templates.
     * 
     * **Validates: Requirements 14.2**
     */
    @Property(tries = 100)
    fun `email deliveries must use program-specific templates`(
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
        Assume.that(candidates.isNotEmpty())
        
        // Setup email adapter with template for this program
        val adapter = EmailChannelAdapter()
        val templateId = "template-$programId"
        adapter.configure(
            mapOf(
                "templates" to mapOf(
                    programId to mapOf(
                        "templateId" to templateId,
                        "subject" to "Subject for $programId",
                        "fromAddress" to "$programId@example.com",
                        "fromName" to "Sender for $programId"
                    )
                )
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
        
        // Verify all delivered candidates use the correct template
        result.delivered.forEach { delivered ->
            assertNotNull(delivered.channelMetadata, "Channel metadata must be present")
            assertEquals(
                templateId,
                delivered.channelMetadata!!["templateId"],
                "Template ID must match program-specific template"
            )
        }
    }
    
    /**
     * Property 43b: Different programs use different templates
     * 
     * When delivering candidates for different programs, each program must use
     * its own configured template.
     * 
     * **Validates: Requirements 14.2**
     */
    @Property(tries = 100)
    fun `different programs must use different templates`(
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("programPairs") programPair: Pair<String, String>,
        @ForAll("marketplaces") marketplace: String
    ) {
        Assume.that(candidates.isNotEmpty())
        Assume.that(programPair.first != programPair.second)
        
        val program1 = programPair.first
        val program2 = programPair.second
        
        // Setup email adapter with templates for both programs
        val adapter = EmailChannelAdapter()
        val template1Id = "template-$program1"
        val template2Id = "template-$program2"
        
        adapter.configure(
            mapOf(
                "templates" to mapOf(
                    program1 to mapOf(
                        "templateId" to template1Id,
                        "subject" to "Subject for $program1",
                        "fromAddress" to "$program1@example.com",
                        "fromName" to "Sender for $program1"
                    ),
                    program2 to mapOf(
                        "templateId" to template2Id,
                        "subject" to "Subject for $program2",
                        "fromAddress" to "$program2@example.com",
                        "fromName" to "Sender for $program2"
                    )
                )
            )
        )
        
        // Deliver for program 1
        val context1 = DeliveryContext(
            programId = program1,
            marketplace = marketplace,
            campaignId = "campaign-1-${System.nanoTime()}",
            shadowMode = false
        )
        val result1 = adapter.deliver(candidates, context1)
        
        // Deliver for program 2
        val context2 = DeliveryContext(
            programId = program2,
            marketplace = marketplace,
            campaignId = "campaign-2-${System.nanoTime()}",
            shadowMode = false
        )
        val result2 = adapter.deliver(candidates, context2)
        
        // Verify program 1 deliveries use template 1
        result1.delivered.forEach { delivered ->
            assertEquals(
                template1Id,
                delivered.channelMetadata!!["templateId"],
                "Program 1 deliveries must use template 1"
            )
        }
        
        // Verify program 2 deliveries use template 2
        result2.delivered.forEach { delivered ->
            assertEquals(
                template2Id,
                delivered.channelMetadata!!["templateId"],
                "Program 2 deliveries must use template 2"
            )
        }
        
        // Verify templates are different
        assertTrue(
            template1Id != template2Id,
            "Different programs must use different templates"
        )
    }
    
    /**
     * Property 43c: Template configuration is per-program
     * 
     * Each program can have its own template configuration independently.
     * 
     * **Validates: Requirements 14.2**
     */
    @Property(tries = 50)
    fun `template configuration must be independent per program`(
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("programIds") program1: String,
        @ForAll("programIds") program2: String,
        @ForAll("marketplaces") marketplace: String
    ) {
        Assume.that(candidates.isNotEmpty())
        Assume.that(program1 != program2)
        
        // Setup adapter with template only for program1
        val adapter = EmailChannelAdapter()
        val template1Id = "template-$program1"
        
        adapter.configure(
            mapOf(
                "templates" to mapOf(
                    program1 to mapOf(
                        "templateId" to template1Id,
                        "subject" to "Subject for $program1",
                        "fromAddress" to "$program1@example.com",
                        "fromName" to "Sender for $program1"
                    )
                )
            )
        )
        
        // Deliver for program1 - should succeed
        val context1 = DeliveryContext(
            programId = program1,
            marketplace = marketplace,
            campaignId = "campaign-1-${System.nanoTime()}",
            shadowMode = false
        )
        val result1 = adapter.deliver(candidates, context1)
        
        // Verify program1 deliveries succeeded
        assertTrue(
            result1.delivered.isNotEmpty() || result1.failed.all { it.errorCode != "NO_TEMPLATE" },
            "Program1 deliveries should succeed or fail for reasons other than missing template"
        )
        
        // Deliver for program2 - should fail due to missing template
        val context2 = DeliveryContext(
            programId = program2,
            marketplace = marketplace,
            campaignId = "campaign-2-${System.nanoTime()}",
            shadowMode = false
        )
        val result2 = adapter.deliver(candidates, context2)
        
        // Verify program2 deliveries failed due to missing template
        assertEquals(
            0,
            result2.delivered.size,
            "Program2 deliveries should fail without template"
        )
        assertTrue(
            result2.failed.all { it.errorCode == "NO_TEMPLATE" },
            "All program2 failures should be due to missing template"
        )
    }
    
    /**
     * Property 43d: Template metadata is included in delivery result
     * 
     * For any successful delivery, the template information must be included
     * in the channel metadata.
     * 
     * **Validates: Requirements 14.2**
     */
    @Property(tries = 100)
    fun `template metadata must be included in delivery results`(
        @ForAll("candidateLists") candidates: List<Candidate>,
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
        Assume.that(candidates.isNotEmpty())
        
        val adapter = EmailChannelAdapter()
        val templateId = "template-$programId"
        val subject = "Custom Subject for $programId"
        val fromAddress = "custom-$programId@example.com"
        
        adapter.configure(
            mapOf(
                "templates" to mapOf(
                    programId to mapOf(
                        "templateId" to templateId,
                        "subject" to subject,
                        "fromAddress" to fromAddress,
                        "fromName" to "Custom Sender"
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
        
        // Verify template metadata is present in all deliveries
        result.delivered.forEach { delivered ->
            val metadata = delivered.channelMetadata
            assertNotNull(metadata, "Channel metadata must be present")
            
            // Verify template ID is included
            assertTrue(
                metadata.containsKey("templateId"),
                "Template ID must be in metadata"
            )
            assertEquals(
                templateId,
                metadata["templateId"],
                "Template ID must match configured template"
            )
            
            // Verify channel is email
            assertEquals(
                "email",
                metadata["channel"],
                "Channel must be email"
            )
        }
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
    fun marketplaces(): Arbitrary<String> {
        return Arbitraries.of("US", "UK", "DE", "FR", "JP", "CA")
    }
}
