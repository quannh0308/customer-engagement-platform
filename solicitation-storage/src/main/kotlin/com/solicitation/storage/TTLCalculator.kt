package com.solicitation.storage

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Calculates TTL (Time To Live) for candidates based on program configuration.
 * 
 * TTL is used by DynamoDB to automatically delete expired candidates.
 */
object TTLCalculator {
    
    /**
     * Calculates the expiration timestamp for a candidate.
     * 
     * @param createdAt When the candidate was created
     * @param ttlDays Number of days until expiration
     * @return Unix timestamp (seconds) when the candidate should expire
     */
    fun calculateExpirationTime(createdAt: Instant, ttlDays: Int): Instant {
        require(ttlDays > 0) { "TTL days must be positive" }
        return createdAt.plus(ttlDays.toLong(), ChronoUnit.DAYS)
    }
    
    /**
     * Calculates the expiration timestamp from now.
     * 
     * @param ttlDays Number of days until expiration
     * @return Unix timestamp (seconds) when the candidate should expire
     */
    fun calculateExpirationTimeFromNow(ttlDays: Int): Instant {
        return calculateExpirationTime(Instant.now(), ttlDays)
    }
    
    /**
     * Checks if a candidate has expired.
     * 
     * @param expiresAt Expiration timestamp
     * @return true if the candidate has expired, false otherwise
     */
    fun isExpired(expiresAt: Instant): Boolean {
        return Instant.now().isAfter(expiresAt)
    }
    
    /**
     * Calculates remaining time until expiration.
     * 
     * @param expiresAt Expiration timestamp
     * @return Number of seconds until expiration (negative if already expired)
     */
    fun remainingSeconds(expiresAt: Instant): Long {
        return ChronoUnit.SECONDS.between(Instant.now(), expiresAt)
    }
}
