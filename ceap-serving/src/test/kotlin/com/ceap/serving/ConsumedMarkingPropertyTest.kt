package com.ceap.serving

import com.ceap.model.*
import com.ceap.storage.CandidateRepository
import com.ceap.storage.StorageException
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant

/**
 * Property-based test for consumed marking.
 * 
 * **Property 53: Consumed marking**
 * 
 * *For any* candidate that is delivered, when it is marked as consumed,
 * it must have consumed=true, consumedAt timestamp, and consumedChannel set.
 * 
 * **Validates: Requirements 17.4**
 */
class ConsumedMarkingPropertyTest {
    
    @Property
    fun `marking candidate as consumed sets consumed fields`(
        @ForAll("candidates") candidate: Candidate,
        @ForAll("channels") channelId: String
    ) {
        // Arrange
        val repository = InMemoryCandidateRepository()
        val servingAPI = ServingAPIImpl(
            candidateRepository = repository,
            rankingStrategy = SimpleRankingStrategy()
        )
        
        // Create candidate in storage
        repository.create(candidate)
        
        // Extract context values
        val programContext = candidate.context.find { it.type == "program" }
        val marketplaceContext = candidate.context.find { it.type == "marketplace" }
        
        assertThat(programContext).isNotNull
        assertThat(marketplaceContext).isNotNull
        
        // Verify candidate is not consumed initially
        assertThat(candidate.attributes.consumed).isFalse()
        assertThat(candidate.attributes.consumedAt).isNull()
        assertThat(candidate.attributes.consumedChannel).isNull()
        
        // Act - Mark candidate as consumed
        val deliveryTimestamp = Instant.now()
        val markConsumedRequest = MarkConsumedRequest(
            customerId = candidate.customerId,
            programId = programContext!!.id,
            marketplaceId = marketplaceContext!!.id,
            subjectType = candidate.subject.type,
            subjectId = candidate.subject.id,
            deliveryTimestamp = deliveryTimestamp,
            channelId = channelId
        )
        
        val response = servingAPI.markCandidateConsumed(markConsumedRequest)
        
        // Assert - Operation was successful
        assertThat(response.success).isTrue()
        assertThat(response.candidate).isNotNull
        
        // Assert - Consumed fields are set correctly
        val updatedCandidate = response.candidate!!
        assertThat(updatedCandidate.attributes.consumed).isTrue()
        assertThat(updatedCandidate.attributes.consumedAt).isEqualTo(deliveryTimestamp)
        assertThat(updatedCandidate.attributes.consumedChannel).isEqualTo(channelId)
        
        // Assert - Version was incremented
        assertThat(updatedCandidate.metadata.version).isEqualTo(candidate.metadata.version + 1)
        
        // Assert - Updated timestamp was set
        assertThat(updatedCandidate.metadata.updatedAt).isAfterOrEqualTo(candidate.metadata.updatedAt)
    }
    
    @Provide
    fun candidates(): Arbitrary<Candidate> {
        return Arbitraries.create {
            val customerId = "customer-${Arbitraries.integers().between(1, 1000).sample()}"
            val programId = "program-${Arbitraries.integers().between(1, 10).sample()}"
            val marketplaceId = "marketplace-${Arbitraries.integers().between(1, 5).sample()}"
            val subjectType = Arbitraries.of("product", "video", "track", "service").sample()
            val subjectId = "subject-${Arbitraries.integers().between(1, 10000).sample()}"
            
            Candidate(
                customerId = customerId,
                context = listOf(
                    Context(type = "program", id = programId),
                    Context(type = "marketplace", id = marketplaceId)
                ),
                subject = Subject(
                    type = subjectType,
                    id = subjectId
                ),
                scores = null,
                attributes = CandidateAttributes(
                    eventDate = Instant.now(),
                    channelEligibility = mapOf("email" to true),
                    consumed = false,
                    consumedAt = null,
                    consumedChannel = null
                ),
                metadata = CandidateMetadata(
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    expiresAt = Instant.now().plusSeconds(86400),
                    version = 1,
                    sourceConnectorId = "test-connector",
                    workflowExecutionId = "test-execution"
                )
            )
        }
    }
    
    @Provide
    fun channels(): Arbitrary<String> {
        return Arbitraries.of("email", "push", "in-app", "sms")
    }
}
