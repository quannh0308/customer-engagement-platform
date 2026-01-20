package com.ceap.channels

import com.ceap.model.*
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant

/**
 * End-to-end property test for channel delivery.
 * 
 * **Validates: Requirements 14.1, 14.2, 14.3, 14.4**
 * 
 * Tests email campaign creation, in-app serving, shadow mode,
 * and delivery tracking.
 */
class ChannelDeliveryEndToEndPropertyTest {
    
    @Property(tries = 50)
    fun `email channel creates campaigns and tracks delivery`(
        @ForAll("validCandidates") candidates: List<Candidate>,
        @ForAll("validProgramIds") programId: String,
        @ForAll("validMarketplaces") marketplace: String
    ): Boolean {
        // Given: Email channel adapter with configuration
        val emailAdapter = EmailChannelAdapter()
        emailAdapter.configure(mapOf(
            "templates" to mapOf(
                programId to mapOf(
                    "templateId" to "test-template",
                    "subject" to "We'd love your feedback",
                    "fromAddress" to "noreply@test.com",
                    "fromName" to "Test Platform",
                    "unsubscribeUrl" to "https://test.com/unsubscribe"
                )
            ),
            "frequencyCaps" to mapOf(
                programId to mapOf(
                    "maxEmailsPerWindow" to 3,
                    "windowDays" to 7
                )
            )
        ))
        
        // Given: Delivery context
        val context = DeliveryContext(
            programId = programId,
            marketplace = marketplace,
            campaignId = "test-campaign-${System.currentTimeMillis()}",
            shadowMode = false
        )
        
        // When: Deliver candidates via email
        val result = emailAdapter.deliver(candidates, context)
        
        // Then: Delivery result is valid
        assertThat(result.delivered).isNotNull
        assertThat(result.failed).isNotNull
        assertThat(result.metrics).isNotNull
        
        // Then: Total candidates match input
        assertThat(result.metrics.totalCandidates).isEqualTo(candidates.size)
        assertThat(result.delivered.size + result.failed.size).isEqualTo(candidates.size)
        
        // Then: Delivered candidates have delivery IDs
        result.delivered.forEach { delivered ->
            assertThat(delivered.deliveryId).isNotBlank()
            assertThat(delivered.channelMetadata).containsKey("campaignId")
            assertThat(delivered.channelMetadata?.get("campaignId")).isEqualTo(context.campaignId)
        }
        
        // Then: Failed deliveries have error codes
        result.failed.forEach { failed ->
            assertThat(failed.errorCode).isNotBlank()
            assertThat(failed.errorMessage).isNotBlank()
        }
        
        return true
    }
    
    @Property(tries = 50)
    fun `email channel enforces opt-out correctly`(
        @ForAll("validCandidates") candidates: List<Candidate>,
        @ForAll("validProgramIds") programId: String,
        @ForAll("validMarketplaces") marketplace: String
    ): Boolean {
        // Given: Email channel adapter
        val emailAdapter = EmailChannelAdapter()
        emailAdapter.configure(mapOf(
            "templates" to mapOf(
                programId to mapOf(
                    "templateId" to "test-template",
                    "subject" to "Test",
                    "fromAddress" to "test@test.com",
                    "fromName" to "Test"
                )
            )
        ))
        
        // Given: Some customers are opted out
        if (candidates.isNotEmpty()) {
            val optedOutCustomer = candidates.first().customerId
            emailAdapter.addOptOut(optedOutCustomer)
            
            // Given: Delivery context
            val context = DeliveryContext(
                programId = programId,
                marketplace = marketplace,
                campaignId = null,
                shadowMode = false
            )
            
            // When: Deliver candidates
            val result = emailAdapter.deliver(candidates, context)
            
            // Then: Opted out customer is in failed list
            val optedOutFailure = result.failed.find { 
                it.candidate.customerId == optedOutCustomer 
            }
            assertThat(optedOutFailure).isNotNull
            assertThat(optedOutFailure?.errorCode).isEqualTo("OPTED_OUT")
        }
        
        return true
    }
    
    @Property(tries = 50)
    fun `email channel tracks delivery status`(
        @ForAll("validCandidates") candidates: List<Candidate>,
        @ForAll("validProgramIds") programId: String,
        @ForAll("validMarketplaces") marketplace: String
    ): Boolean {
        // Given: Email channel adapter
        val emailAdapter = EmailChannelAdapter()
        emailAdapter.configure(mapOf(
            "templates" to mapOf(
                programId to mapOf(
                    "templateId" to "test-template",
                    "subject" to "Test",
                    "fromAddress" to "test@test.com",
                    "fromName" to "Test"
                )
            )
        ))
        
        // Given: Delivery context
        val context = DeliveryContext(
            programId = programId,
            marketplace = marketplace,
            campaignId = "test-campaign",
            shadowMode = false
        )
        
        // When: Deliver candidates
        val result = emailAdapter.deliver(candidates, context)
        
        // Then: Can retrieve delivery tracking for delivered emails
        result.delivered.forEach { delivered ->
            val tracking = emailAdapter.getDeliveryTracking(delivered.deliveryId)
            assertThat(tracking).isNotNull
            assertThat(tracking?.deliveryId).isEqualTo(delivered.deliveryId)
            assertThat(tracking?.customerId).isEqualTo(delivered.candidate.customerId)
            assertThat(tracking?.status).isEqualTo(EmailStatus.SENT)
        }
        
        return true
    }
    
    @Property(tries = 50)
    fun `in-app channel supports shadow mode`(
        @ForAll("validCandidates") candidates: List<Candidate>,
        @ForAll("validProgramIds") programId: String,
        @ForAll("validMarketplaces") marketplace: String
    ): Boolean {
        // Given: In-app channel adapter in shadow mode
        val inAppAdapter = InAppChannelAdapter()
        inAppAdapter.configure(mapOf(
            "shadowMode" to true
        ))
        
        // Given: Delivery context
        val context = DeliveryContext(
            programId = programId,
            marketplace = marketplace,
            campaignId = null,
            shadowMode = true
        )
        
        // When: Deliver candidates in shadow mode
        val result = inAppAdapter.deliver(candidates, context)
        
        // Then: Shadow mode is indicated in metrics
        assertThat(result.metrics.shadowMode).isTrue()
        
        // Then: All candidates are "delivered" (simulated)
        assertThat(result.delivered.size).isEqualTo(candidates.size)
        assertThat(result.failed).isEmpty()
        
        return true
    }
    
    @Provide
    fun validCandidates(): Arbitrary<List<Candidate>> {
        return Arbitraries.integers().between(1, 5).flatMap { count ->
            val candidates = (1..count).map { i ->
                createTestCandidate("CUST-$i", "PROD-$i")
            }
            Arbitraries.just(candidates)
        }
    }
    
    @Provide
    fun validProgramIds(): Arbitrary<String> {
        return Arbitraries.of(
            "product-reviews",
            "video-ratings",
            "music-feedback",
            "service-surveys"
        )
    }
    
    @Provide
    fun validMarketplaces(): Arbitrary<String> {
        return Arbitraries.of("US", "UK", "DE", "JP", "FR", "CA")
    }
    
    private fun createTestCandidate(customerId: String, subjectId: String): Candidate {
        val now = Instant.now()
        return Candidate(
            customerId = customerId,
            context = listOf(
                Context(type = "marketplace", id = "US"),
                Context(type = "program", id = "product-reviews")
            ),
            subject = Subject(
                type = "product",
                id = subjectId,
                metadata = null
            ),
            scores = null,
            attributes = CandidateAttributes(
                eventDate = now,
                deliveryDate = null,
                timingWindow = null,
                orderValue = 99.99,
                mediaEligible = true,
                channelEligibility = mapOf(
                    "email" to true,
                    "in-app" to true,
                    "push" to true
                )
            ),
            metadata = CandidateMetadata(
                createdAt = now,
                updatedAt = now,
                expiresAt = now.plusSeconds(30 * 24 * 60 * 60),
                version = 1L,
                sourceConnectorId = "test-connector",
                workflowExecutionId = "test-execution"
            ),
            rejectionHistory = null
        )
    }
}
