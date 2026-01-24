package com.ceap.serving

import com.ceap.model.*
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant

/**
 * Property-based tests for channel-specific ranking consistency.
 * 
 * **Feature: ceap-platform, Property 17: Channel-specific ranking consistency**
 * 
 * **Validates: Requirements 6.3**
 * 
 * For any set of candidates for a customer and channel, applying the channel-specific
 * ranking algorithm multiple times with the same input must produce the same ordering.
 */
class RankingConsistencyPropertyTest {
    
    @Property(tries = 100)
    fun `ranking algorithm produces consistent ordering for same input`(
        @ForAll("candidates") candidates: List<Candidate>,
        @ForAll("channel") channel: String,
        @ForAll("customerId") customerId: String
    ) {
        // Given: A ranking strategy
        val rankingStrategy = DefaultRankingStrategy()
        
        // When: We rank the same candidates multiple times
        val ranking1 = rankingStrategy.rank(candidates, channel, customerId)
        val ranking2 = rankingStrategy.rank(candidates, channel, customerId)
        val ranking3 = rankingStrategy.rank(candidates, channel, customerId)
        
        // Then: All rankings should be identical
        assertThat(ranking1).isEqualTo(ranking2)
        assertThat(ranking2).isEqualTo(ranking3)
        
        // And: The order should be deterministic
        assertThat(ranking1.map { it.customerId to it.subject.id })
            .isEqualTo(ranking2.map { it.customerId to it.subject.id })
    }
    
    @Property(tries = 100)
    fun `score-based ranking is consistent`(
        @ForAll("candidates") candidates: List<Candidate>,
        @ForAll("channel") channel: String,
        @ForAll("customerId") customerId: String
    ) {
        // Given: A score-based ranking strategy
        val rankingStrategy = ScoreBasedRankingStrategy()
        
        // When: We rank the same candidates multiple times
        val ranking1 = rankingStrategy.rank(candidates, channel, customerId)
        val ranking2 = rankingStrategy.rank(candidates, channel, customerId)
        
        // Then: Rankings should be identical
        assertThat(ranking1).isEqualTo(ranking2)
    }
    
    @Property(tries = 100)
    fun `recency-based ranking is consistent`(
        @ForAll("candidates") candidates: List<Candidate>,
        @ForAll("channel") channel: String,
        @ForAll("customerId") customerId: String
    ) {
        // Given: A recency-based ranking strategy
        val rankingStrategy = RecencyBasedRankingStrategy()
        
        // When: We rank the same candidates multiple times
        val ranking1 = rankingStrategy.rank(candidates, channel, customerId)
        val ranking2 = rankingStrategy.rank(candidates, channel, customerId)
        
        // Then: Rankings should be identical
        assertThat(ranking1).isEqualTo(ranking2)
    }
    
    @Property(tries = 100)
    fun `ranking preserves all candidates`(
        @ForAll("candidates") candidates: List<Candidate>,
        @ForAll("channel") channel: String,
        @ForAll("customerId") customerId: String
    ) {
        // Given: A ranking strategy
        val rankingStrategy = DefaultRankingStrategy()
        
        // When: We rank candidates
        val ranked = rankingStrategy.rank(candidates, channel, customerId)
        
        // Then: All candidates should be present
        assertThat(ranked).hasSameSizeAs(candidates)
        assertThat(ranked).containsExactlyInAnyOrderElementsOf(candidates)
    }
    
    // Arbitraries
    
    @Provide
    fun candidates(): Arbitrary<List<Candidate>> {
        return Arbitraries.of(
            emptyList(),
            listOf(createCandidate("cust1", "prod1", 0.9)),
            listOf(
                createCandidate("cust1", "prod1", 0.9),
                createCandidate("cust1", "prod2", 0.7)
            ),
            listOf(
                createCandidate("cust1", "prod1", 0.9),
                createCandidate("cust1", "prod2", 0.7),
                createCandidate("cust1", "prod3", 0.5)
            )
        )
    }
    
    @Provide
    fun channel(): Arbitrary<String> {
        return Arbitraries.of("email", "in-app", "push", "voice", "sms")
    }
    
    @Provide
    fun customerId(): Arbitrary<String> {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
    }
    
    private fun createCandidate(customerId: String, subjectId: String, score: Double): Candidate {
        val now = Instant.now()
        
        return Candidate(
            customerId = customerId,
            context = listOf(
                Context(type = "program", id = "test-program"),
                Context(type = "marketplace", id = "US")
            ),
            subject = Subject(
                type = "product",
                id = subjectId,
                metadata = null
            ),
            scores = mapOf(
                "model1" to Score(
                    modelId = "model1",
                    value = score,
                    confidence = 0.8,
                    timestamp = now,
                    metadata = null
                )
            ),
            attributes = CandidateAttributes(
                eventDate = now.minusSeconds(3600),
                deliveryDate = null,
                timingWindow = null,
                orderValue = null,
                mediaEligible = true,
                channelEligibility = mapOf(
                    "email" to true,
                    "in-app" to true,
                    "push" to false
                )
            ),
            metadata = CandidateMetadata(
                createdAt = now,
                updatedAt = now,
                expiresAt = now.plusSeconds(86400 * 30),
                version = 1,
                sourceConnectorId = "test-connector",
                workflowExecutionId = "test-execution"
            ),
            rejectionHistory = null
        )
    }
}
