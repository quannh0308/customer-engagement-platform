package com.ceap.serving

import com.ceap.model.*
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import java.time.Duration
import java.time.Instant

/**
 * Property-based tests for eligibility refresh correctness.
 * 
 * **Feature: solicitation-platform, Property 18: Eligibility refresh correctness**
 * 
 * **Validates: Requirements 6.4**
 * 
 * For any candidate, if real-time eligibility refresh is requested and the candidate
 * is stale (older than threshold), the system must re-validate eligibility and update
 * the candidate's eligibility status.
 */
class EligibilityRefreshPropertyTest {
    
    @Property(tries = 100)
    fun `stale candidates are detected correctly`(
        @ForAll("stalenessHours") stalenessHours: Long
    ) {
        // Given: A candidate with specific update time
        val now = Instant.now()
        val updatedAt = now.minusSeconds(stalenessHours * 3600)
        val candidate = createCandidate(updatedAt = updatedAt)
        
        // And: An eligibility checker with threshold
        val threshold = Duration.ofHours(24)
        val checker = DefaultEligibilityChecker(stalenessThreshold = threshold)
        
        // When: We check if candidate is stale
        val isStale = checker.isStale(candidate)
        
        // Then: Staleness should match expectation (>= threshold is stale)
        val expectedStale = stalenessHours >= 24
        assertThat(isStale).isEqualTo(expectedStale)
    }
    
    @Property(tries = 100)
    fun `expired candidates are not eligible`() {
        // Given: A candidate that has expired
        val now = Instant.now()
        val candidate = createCandidate(expiresAt = now.minusSeconds(3600))
        
        // And: An eligibility checker
        val checker = DefaultEligibilityChecker()
        
        // When: We check eligibility
        val isEligible = checker.isEligible(candidate)
        
        // Then: Candidate should not be eligible
        assertThat(isEligible).isFalse()
    }
    
    @Property(tries = 100)
    fun `candidates with old events are not eligible`(
        @ForAll("eventAgeDays") eventAgeDays: Long
    ) {
        // Given: A candidate with event of specific age
        val now = Instant.now()
        val eventDate = now.minusSeconds(eventAgeDays * 86400)
        val candidate = createCandidate(eventDate = eventDate)
        
        // And: An eligibility checker with 30-day window
        val checker = DefaultEligibilityChecker(eventStalenessWindow = Duration.ofDays(30))
        
        // When: We check eligibility
        val isEligible = checker.isEligible(candidate)
        
        // Then: Eligibility should match expectation (< 30 days is eligible)
        val expectedEligible = eventAgeDays < 30
        assertThat(isEligible).isEqualTo(expectedEligible)
    }
    
    @Property(tries = 100)
    fun `candidates without channel eligibility are not eligible`() {
        // Given: A candidate with no channel eligibility
        val candidate = createCandidate(
            channelEligibility = mapOf(
                "email" to false,
                "in-app" to false,
                "push" to false
            )
        )
        
        // And: An eligibility checker
        val checker = DefaultEligibilityChecker()
        
        // When: We check eligibility
        val isEligible = checker.isEligible(candidate)
        
        // Then: Candidate should not be eligible
        assertThat(isEligible).isFalse()
    }
    
    @Property(tries = 100)
    fun `fresh candidates with valid data are eligible`() {
        // Given: A fresh candidate with valid data
        val now = Instant.now()
        val candidate = createCandidate(
            updatedAt = now.minusSeconds(3600), // 1 hour ago
            eventDate = now.minusSeconds(86400), // 1 day ago
            expiresAt = now.plusSeconds(86400 * 30), // 30 days from now
            channelEligibility = mapOf("email" to true)
        )
        
        // And: An eligibility checker
        val checker = DefaultEligibilityChecker()
        
        // When: We check eligibility
        val isEligible = checker.isEligible(candidate)
        
        // Then: Candidate should be eligible
        assertThat(isEligible).isTrue()
    }
    
    @Property(tries = 100)
    fun `scores older than threshold make candidate stale`(
        @ForAll("scoreAgeHours") scoreAgeHours: Long
    ) {
        // Given: A candidate with scores of specific age
        val now = Instant.now()
        val scoreTimestamp = now.minusSeconds(scoreAgeHours * 3600)
        val candidate = createCandidate(
            updatedAt = now.minusSeconds(3600), // Recent update
            scoreTimestamp = scoreTimestamp
        )
        
        // And: An eligibility checker with 12-hour score threshold
        val checker = DefaultEligibilityChecker(scoreStalenessDuration = Duration.ofHours(12))
        
        // When: We check if candidate is stale
        val isStale = checker.isStale(candidate)
        
        // Then: Staleness should match expectation (>= threshold is stale)
        val expectedStale = scoreAgeHours >= 12
        assertThat(isStale).isEqualTo(expectedStale)
    }
    
    @Property(tries = 100)
    fun `always fresh checker never marks candidates as stale`(
        @ForAll("stalenessHours") stalenessHours: Long
    ) {
        // Given: A candidate with any update time
        val now = Instant.now()
        val updatedAt = now.minusSeconds(stalenessHours * 3600)
        val candidate = createCandidate(updatedAt = updatedAt)
        
        // And: An always fresh checker
        val checker = AlwaysFreshEligibilityChecker()
        
        // When: We check if candidate is stale
        val isStale = checker.isStale(candidate)
        
        // Then: Candidate should never be stale
        assertThat(isStale).isFalse()
    }
    
    // Arbitraries
    
    @Provide
    fun stalenessHours(): Arbitrary<Long> {
        return Arbitraries.longs().between(0, 72) // 0 to 72 hours
    }
    
    @Provide
    fun eventAgeDays(): Arbitrary<Long> {
        return Arbitraries.longs().between(0, 60) // 0 to 60 days
    }
    
    @Provide
    fun scoreAgeHours(): Arbitrary<Long> {
        return Arbitraries.longs().between(0, 48) // 0 to 48 hours
    }
    
    private fun createCandidate(
        updatedAt: Instant = Instant.now(),
        eventDate: Instant = Instant.now().minusSeconds(86400),
        expiresAt: Instant = Instant.now().plusSeconds(86400 * 30),
        channelEligibility: Map<String, Boolean> = mapOf("email" to true),
        scoreTimestamp: Instant = Instant.now()
    ): Candidate {
        val now = Instant.now()
        
        return Candidate(
            customerId = "test-customer",
            context = listOf(
                Context(type = "program", id = "test-program"),
                Context(type = "marketplace", id = "US")
            ),
            subject = Subject(
                type = "product",
                id = "test-product",
                metadata = null
            ),
            scores = mapOf(
                "model1" to Score(
                    modelId = "model1",
                    value = 0.8,
                    confidence = 0.9,
                    timestamp = scoreTimestamp,
                    metadata = null
                )
            ),
            attributes = CandidateAttributes(
                eventDate = eventDate,
                deliveryDate = null,
                timingWindow = null,
                orderValue = null,
                mediaEligible = true,
                channelEligibility = channelEligibility
            ),
            metadata = CandidateMetadata(
                createdAt = now,
                updatedAt = updatedAt,
                expiresAt = expiresAt,
                version = 1,
                sourceConnectorId = "test-connector",
                workflowExecutionId = "test-execution"
            ),
            rejectionHistory = null
        )
    }
}
