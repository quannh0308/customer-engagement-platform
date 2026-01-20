package com.ceap.storage

import net.jqwik.api.*
import net.jqwik.api.constraints.IntRange
import net.jqwik.time.api.DateTimes
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Property-based tests for TTL configuration.
 * 
 * **Property 51: TTL configuration**
 * **Validates: Requirements 17.1**
 * 
 * Verifies that TTL is calculated correctly based on program config
 * and TTL attribute is set on all candidates.
 */
class TTLConfigurationPropertyTest {
    
    @Property(tries = 100)
    fun `TTL is calculated correctly from creation time`(
        @ForAll("createdAt") createdAt: Instant,
        @ForAll("ttlDays") ttlDays: Int
    ) {
        // When: Calculating expiration time
        val expiresAt = TTLCalculator.calculateExpirationTime(createdAt, ttlDays)
        
        // Then: Expiration is exactly ttlDays after creation
        val expectedExpiration = createdAt.plus(ttlDays.toLong(), ChronoUnit.DAYS)
        assertThat(expiresAt).isEqualTo(expectedExpiration)
        
        // And: Expiration is in the future relative to creation
        assertThat(expiresAt).isAfter(createdAt)
    }
    
    @Property(tries = 100)
    fun `TTL calculation is consistent for same inputs`(
        @ForAll("createdAt") createdAt: Instant,
        @ForAll("ttlDays") ttlDays: Int
    ) {
        // When: Calculating expiration time multiple times
        val expiresAt1 = TTLCalculator.calculateExpirationTime(createdAt, ttlDays)
        val expiresAt2 = TTLCalculator.calculateExpirationTime(createdAt, ttlDays)
        
        // Then: Results are identical
        assertThat(expiresAt1).isEqualTo(expiresAt2)
    }
    
    @Property(tries = 100)
    fun `TTL increases with more days`(
        @ForAll("createdAt") createdAt: Instant,
        @ForAll("smallTtlDays") ttlDays1: Int,
        @ForAll("largeTtlDays") ttlDays2: Int
    ) {
        // Given: Two different TTL periods where ttlDays2 > ttlDays1
        Assume.that(ttlDays2 > ttlDays1)
        
        // When: Calculating expiration times
        val expiresAt1 = TTLCalculator.calculateExpirationTime(createdAt, ttlDays1)
        val expiresAt2 = TTLCalculator.calculateExpirationTime(createdAt, ttlDays2)
        
        // Then: Longer TTL results in later expiration
        assertThat(expiresAt2).isAfter(expiresAt1)
    }
    
    @Property(tries = 100)
    fun `TTL calculation rejects non-positive days`(
        @ForAll("createdAt") createdAt: Instant,
        @ForAll("invalidTtlDays") invalidTtlDays: Int
    ) {
        // When: Attempting to calculate TTL with non-positive days
        // Then: An exception is thrown
        assertThatThrownBy {
            TTLCalculator.calculateExpirationTime(createdAt, invalidTtlDays)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("TTL days must be positive")
    }
    
    @Property(tries = 100)
    fun `expired candidates are correctly identified`(
        @ForAll("pastInstant") expiresAt: Instant
    ) {
        // Given: An expiration time in the past
        Assume.that(expiresAt.isBefore(Instant.now()))
        
        // When: Checking if expired
        val isExpired = TTLCalculator.isExpired(expiresAt)
        
        // Then: The candidate is identified as expired
        assertThat(isExpired).isTrue()
    }
    
    @Property(tries = 100)
    fun `non-expired candidates are correctly identified`(
        @ForAll("futureInstant") expiresAt: Instant
    ) {
        // Given: An expiration time in the future
        Assume.that(expiresAt.isAfter(Instant.now()))
        
        // When: Checking if expired
        val isExpired = TTLCalculator.isExpired(expiresAt)
        
        // Then: The candidate is identified as not expired
        assertThat(isExpired).isFalse()
    }
    
    @Property(tries = 100)
    fun `remaining seconds is positive for future expiration`(
        @ForAll("ttlDays") ttlDays: Int
    ) {
        // Given: An expiration time in the future
        val expiresAt = TTLCalculator.calculateExpirationTimeFromNow(ttlDays)
        
        // When: Calculating remaining seconds
        val remaining = TTLCalculator.remainingSeconds(expiresAt)
        
        // Then: Remaining time is positive
        assertThat(remaining).isPositive()
        
        // And: Remaining time is approximately ttlDays in seconds
        val expectedSeconds = ttlDays * 86400L
        assertThat(remaining).isBetween(expectedSeconds - 10, expectedSeconds + 10)
    }
    
    @Property(tries = 100)
    fun `remaining seconds is negative for past expiration`(
        @ForAll("pastInstant") expiresAt: Instant
    ) {
        // Given: An expiration time in the past
        Assume.that(expiresAt.isBefore(Instant.now()))
        
        // When: Calculating remaining seconds
        val remaining = TTLCalculator.remainingSeconds(expiresAt)
        
        // Then: Remaining time is negative
        assertThat(remaining).isNegative()
    }
    
    @Property(tries = 100)
    fun `TTL from now is always in the future`(
        @ForAll("ttlDays") ttlDays: Int
    ) {
        // When: Calculating expiration from now
        val expiresAt = TTLCalculator.calculateExpirationTimeFromNow(ttlDays)
        
        // Then: Expiration is in the future
        assertThat(expiresAt).isAfter(Instant.now())
        
        // And: Expiration is not expired
        assertThat(TTLCalculator.isExpired(expiresAt)).isFalse()
    }
    
    @Property(tries = 100)
    fun `TTL calculation handles edge cases correctly`(
        @ForAll("createdAt") createdAt: Instant
    ) {
        // When: Calculating TTL for 1 day
        val expiresAt1Day = TTLCalculator.calculateExpirationTime(createdAt, 1)
        
        // Then: Expiration is exactly 24 hours later
        val daysDifference = ChronoUnit.DAYS.between(createdAt, expiresAt1Day)
        assertThat(daysDifference).isEqualTo(1L)
        
        // When: Calculating TTL for 365 days
        val expiresAt365Days = TTLCalculator.calculateExpirationTime(createdAt, 365)
        
        // Then: Expiration is exactly 365 days later
        val yearDifference = ChronoUnit.DAYS.between(createdAt, expiresAt365Days)
        assertThat(yearDifference).isEqualTo(365L)
    }
    
    @Property(tries = 100)
    fun `TTL calculation is timezone independent`(
        @ForAll("ttlDays") ttlDays: Int
    ) {
        // Given: Two timestamps at different times
        val createdAt1 = Instant.parse("2024-01-01T00:00:00Z")
        val createdAt2 = Instant.parse("2024-01-01T12:00:00Z")
        
        // When: Calculating expiration times
        val expiresAt1 = TTLCalculator.calculateExpirationTime(createdAt1, ttlDays)
        val expiresAt2 = TTLCalculator.calculateExpirationTime(createdAt2, ttlDays)
        
        // Then: The difference between expirations equals the difference between creations
        val creationDiff = ChronoUnit.SECONDS.between(createdAt1, createdAt2)
        val expirationDiff = ChronoUnit.SECONDS.between(expiresAt1, expiresAt2)
        assertThat(expirationDiff).isEqualTo(creationDiff)
    }
    
    // Arbitrary generators
    
    @Provide
    fun createdAt(): Arbitrary<Instant> {
        return DateTimes.instants()
            .between(
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T23:59:59Z")
            )
    }
    
    @Provide
    fun ttlDays(): Arbitrary<Int> {
        return Arbitraries.integers().between(1, 365)
    }
    
    @Provide
    fun smallTtlDays(): Arbitrary<Int> {
        return Arbitraries.integers().between(1, 100)
    }
    
    @Provide
    fun largeTtlDays(): Arbitrary<Int> {
        return Arbitraries.integers().between(101, 365)
    }
    
    @Provide
    fun invalidTtlDays(): Arbitrary<Int> {
        return Arbitraries.integers().between(-100, 0)
    }
    
    @Provide
    fun pastInstant(): Arbitrary<Instant> {
        return DateTimes.instants()
            .between(
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.now().minusSeconds(3600) // At least 1 hour in the past
            )
    }
    
    @Provide
    fun futureInstant(): Arbitrary<Instant> {
        return DateTimes.instants()
            .between(
                Instant.now().plusSeconds(3600), // At least 1 hour in the future
                Instant.parse("2030-12-31T23:59:59Z")
            )
    }
}
