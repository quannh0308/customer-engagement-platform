package com.ceap.serving

import com.ceap.model.*
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant

/**
 * Property-based test for candidate refresh.
 * 
 * **Property 54: Candidate refresh**
 * 
 * *For any* active candidate, when it is refreshed, the version must be incremented
 * and the updatedAt timestamp must be updated.
 * 
 * **Validates: Requirements 17.5**
 */
class CandidateRefreshPropertyTest {
    
    @Property
    fun `refreshing candidate updates version and timestamp`(
        @ForAll("candidates") candidate: Candidate,
        @ForAll refreshScores: Boolean,
        @ForAll refreshEligibility: Boolean
    ) {
        // Arrange
        val repository = InMemoryCandidateRepository()
        val eligibilityChecker = TestEligibilityChecker()
        val servingAPI = ServingAPIImpl(
            candidateRepository = repository,
            rankingStrategy = SimpleRankingStrategy(),
            eligibilityChecker = eligibilityChecker
        )
        
        // Create candidate in storage
        repository.create(candidate)
        
        // Extract context values
        val programContext = candidate.context.find { it.type == "program" }
        val marketplaceContext = candidate.context.find { it.type == "marketplace" }
        
        assertThat(programContext).isNotNull
        assertThat(marketplaceContext).isNotNull
        
        val originalVersion = candidate.metadata.version
        val originalUpdatedAt = candidate.metadata.updatedAt
        
        // Act - Refresh candidate
        val refreshRequest = RefreshCandidateRequest(
            customerId = candidate.customerId,
            programId = programContext!!.id,
            marketplaceId = marketplaceContext!!.id,
            subjectType = candidate.subject.type,
            subjectId = candidate.subject.id,
            refreshScores = refreshScores,
            refreshEligibility = refreshEligibility
        )
        
        val response = servingAPI.refreshCandidate(refreshRequest)
        
        // Assert - Operation was successful
        assertThat(response.success).isTrue()
        assertThat(response.candidate).isNotNull
        
        // Assert - Version was incremented
        val refreshedCandidate = response.candidate!!
        assertThat(refreshedCandidate.metadata.version).isEqualTo(originalVersion + 1)
        
        // Assert - Updated timestamp was set
        assertThat(refreshedCandidate.metadata.updatedAt).isAfterOrEqualTo(originalUpdatedAt)
        
        // Assert - Refresh flags match request
        if (refreshScores) {
            assertThat(response.scoresUpdated).isTrue()
        }
        if (refreshEligibility) {
            assertThat(response.eligibilityUpdated).isTrue()
        }
    }
    
    @Property
    fun `refreshing ineligible candidate updates channel eligibility`(
        @ForAll("candidates") candidate: Candidate
    ) {
        // Arrange
        val repository = InMemoryCandidateRepository()
        val eligibilityChecker = TestEligibilityChecker(alwaysEligible = false)
        val servingAPI = ServingAPIImpl(
            candidateRepository = repository,
            rankingStrategy = SimpleRankingStrategy(),
            eligibilityChecker = eligibilityChecker
        )
        
        // Create candidate in storage with eligible channels
        val eligibleCandidate = candidate.copy(
            attributes = candidate.attributes.copy(
                channelEligibility = mapOf("email" to true, "push" to true)
            )
        )
        repository.create(eligibleCandidate)
        
        // Extract context values
        val programContext = eligibleCandidate.context.find { it.type == "program" }
        val marketplaceContext = eligibleCandidate.context.find { it.type == "marketplace" }
        
        assertThat(programContext).isNotNull
        assertThat(marketplaceContext).isNotNull
        
        // Act - Refresh candidate with eligibility check
        val refreshRequest = RefreshCandidateRequest(
            customerId = eligibleCandidate.customerId,
            programId = programContext!!.id,
            marketplaceId = marketplaceContext!!.id,
            subjectType = eligibleCandidate.subject.type,
            subjectId = eligibleCandidate.subject.id,
            refreshScores = false,
            refreshEligibility = true
        )
        
        val response = servingAPI.refreshCandidate(refreshRequest)
        
        // Assert - Operation was successful
        assertThat(response.success).isTrue()
        assertThat(response.candidate).isNotNull
        
        // Assert - All channels are now ineligible
        val refreshedCandidate = response.candidate!!
        assertThat(refreshedCandidate.attributes.channelEligibility.values).allMatch { !it }
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

/**
 * Test eligibility checker for testing.
 */
private class TestEligibilityChecker(private val alwaysEligible: Boolean = true) : EligibilityChecker {
    override fun isEligible(candidate: Candidate): Boolean {
        return alwaysEligible
    }
    
    override fun isStale(candidate: Candidate): Boolean {
        return false
    }
}
