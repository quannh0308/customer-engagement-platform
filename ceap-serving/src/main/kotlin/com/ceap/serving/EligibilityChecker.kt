package com.ceap.serving

import com.ceap.model.Candidate
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Interface for checking candidate eligibility in real-time.
 * 
 * Provides staleness detection and eligibility refresh capabilities
 * for serving API requests.
 */
interface EligibilityChecker {
    
    /**
     * Checks if a candidate is still eligible.
     * 
     * @param candidate Candidate to check
     * @return true if eligible, false otherwise
     */
    fun isEligible(candidate: Candidate): Boolean
    
    /**
     * Checks if a candidate is stale and needs refresh.
     * 
     * @param candidate Candidate to check
     * @return true if stale, false otherwise
     */
    fun isStale(candidate: Candidate): Boolean
}

/**
 * Default eligibility checker with staleness detection.
 * 
 * Considers candidates stale if:
 * - Updated more than stalenessThreshold ago
 * - Scores are older than scoreStalenessDuration
 * - Event date is in the past beyond eventStalenessWindow
 * 
 * @property stalenessThreshold Duration after which candidates are considered stale
 * @property scoreStalenessDuration Duration after which scores are considered stale
 * @property eventStalenessWindow Duration after event date when candidate becomes stale
 */
class DefaultEligibilityChecker(
    private val stalenessThreshold: Duration = Duration.ofHours(24),
    private val scoreStalenessDuration: Duration = Duration.ofHours(12),
    private val eventStalenessWindow: Duration = Duration.ofDays(30)
) : EligibilityChecker {
    
    override fun isEligible(candidate: Candidate): Boolean {
        val now = Instant.now()
        
        // Check if candidate has expired
        if (candidate.metadata.expiresAt.isBefore(now) || candidate.metadata.expiresAt.equals(now)) {
            logger.debug { "Candidate expired: ${candidate.customerId}" }
            return false
        }
        
        // Check if event is too old
        val eventAge = Duration.between(candidate.attributes.eventDate, now)
        if (eventAge >= eventStalenessWindow) {
            logger.debug { "Event too old: ${candidate.customerId}, age=$eventAge" }
            return false
        }
        
        // Check if candidate has any channel eligibility
        if (candidate.attributes.channelEligibility.values.none { it }) {
            logger.debug { "No channel eligibility: ${candidate.customerId}" }
            return false
        }
        
        return true
    }
    
    override fun isStale(candidate: Candidate): Boolean {
        val now = Instant.now()
        
        // Check if candidate metadata is stale
        val updateAge = Duration.between(candidate.metadata.updatedAt, now)
        if (updateAge >= stalenessThreshold) {
            logger.debug { "Candidate metadata stale: ${candidate.customerId}, age=$updateAge" }
            return true
        }
        
        // Check if scores are stale
        val scoresStale = candidate.scores?.values?.any { score ->
            val scoreAge = Duration.between(score.timestamp, now)
            scoreAge >= scoreStalenessDuration
        } ?: false
        
        if (scoresStale) {
            logger.debug { "Candidate scores stale: ${candidate.customerId}" }
            return true
        }
        
        return false
    }
}

/**
 * Strict eligibility checker that always considers candidates fresh.
 * 
 * Useful for testing or when staleness checks are not required.
 */
class AlwaysFreshEligibilityChecker : EligibilityChecker {
    
    override fun isEligible(candidate: Candidate): Boolean {
        val now = Instant.now()
        
        // Only check expiration
        if (candidate.metadata.expiresAt.isBefore(now)) {
            return false
        }
        
        // Check channel eligibility
        return candidate.attributes.channelEligibility.values.any { it }
    }
    
    override fun isStale(candidate: Candidate): Boolean {
        return false
    }
}

/**
 * Configurable eligibility checker with custom rules.
 * 
 * @property rules List of eligibility rules to apply
 */
class ConfigurableEligibilityChecker(
    private val rules: List<EligibilityRule>
) : EligibilityChecker {
    
    override fun isEligible(candidate: Candidate): Boolean {
        return rules.all { rule -> rule.check(candidate) }
    }
    
    override fun isStale(candidate: Candidate): Boolean {
        val now = Instant.now()
        val updateAge = Duration.between(candidate.metadata.updatedAt, now)
        return updateAge > Duration.ofHours(24)
    }
}

/**
 * Interface for custom eligibility rules.
 */
interface EligibilityRule {
    
    /**
     * Checks if a candidate passes this rule.
     * 
     * @param candidate Candidate to check
     * @return true if passes, false otherwise
     */
    fun check(candidate: Candidate): Boolean
}

/**
 * Rule that checks if candidate has not expired.
 */
class NotExpiredRule : EligibilityRule {
    override fun check(candidate: Candidate): Boolean {
        return candidate.metadata.expiresAt.isAfter(Instant.now())
    }
}

/**
 * Rule that checks if candidate has channel eligibility.
 */
class HasChannelEligibilityRule : EligibilityRule {
    override fun check(candidate: Candidate): Boolean {
        return candidate.attributes.channelEligibility.values.any { it }
    }
}

/**
 * Rule that checks if event is within acceptable age.
 */
class EventAgeRule(
    private val maxAge: Duration = Duration.ofDays(30)
) : EligibilityRule {
    override fun check(candidate: Candidate): Boolean {
        val eventAge = Duration.between(candidate.attributes.eventDate, Instant.now())
        return eventAge <= maxAge
    }
}
