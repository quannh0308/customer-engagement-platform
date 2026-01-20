package com.ceap.serving

import com.ceap.model.*
import com.ceap.storage.CandidateRepository
import com.ceap.storage.StorageException
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant

/**
 * Property-based test for manual candidate deletion.
 * 
 * **Property 52: Manual deletion**
 * 
 * *For any* candidate in storage, when it is manually deleted via API,
 * it must be removed from storage and subsequent queries must not return it.
 * 
 * **Validates: Requirements 17.3**
 */
class ManualDeletionPropertyTest {
    
    @Property
    fun `manual deletion removes candidate from storage`(
        @ForAll("candidates") candidate: Candidate
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
        
        // Verify candidate exists before deletion
        val beforeDeletion = repository.get(
            customerId = candidate.customerId,
            programId = programContext!!.id,
            marketplaceId = marketplaceContext!!.id,
            subjectType = candidate.subject.type,
            subjectId = candidate.subject.id
        )
        assertThat(beforeDeletion).isNotNull
        
        // Act - Delete candidate
        val deleteRequest = DeleteCandidateRequest(
            customerId = candidate.customerId,
            programId = programContext.id,
            marketplaceId = marketplaceContext.id,
            subjectType = candidate.subject.type,
            subjectId = candidate.subject.id
        )
        
        val response = servingAPI.deleteCandidate(deleteRequest)
        
        // Assert - Deletion was successful
        assertThat(response.success).isTrue()
        
        // Assert - Candidate no longer exists in storage
        val afterDeletion = repository.get(
            customerId = candidate.customerId,
            programId = programContext.id,
            marketplaceId = marketplaceContext.id,
            subjectType = candidate.subject.type,
            subjectId = candidate.subject.id
        )
        assertThat(afterDeletion).isNull()
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
                    channelEligibility = mapOf("email" to true)
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
}
