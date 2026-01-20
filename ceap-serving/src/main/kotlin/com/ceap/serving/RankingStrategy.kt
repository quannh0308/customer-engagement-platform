package com.ceap.serving

import com.ceap.model.Candidate

/**
 * Strategy for ranking candidates based on channel and customer context.
 * 
 * Different channels may have different ranking algorithms based on
 * their specific requirements and user experience considerations.
 */
interface RankingStrategy {
    
    /**
     * Ranks candidates for a specific channel and customer.
     * 
     * @param candidates List of candidates to rank
     * @param channel Channel identifier (e.g., "email", "in-app", "push")
     * @param customerId Customer identifier for personalization
     * @return Ranked list of candidates (highest priority first)
     */
    fun rank(
        candidates: List<Candidate>,
        channel: String?,
        customerId: String
    ): List<Candidate>
}

/**
 * Default ranking strategy that uses score-based ranking with channel-specific adjustments.
 * 
 * Ranking logic:
 * 1. Primary: Score value (higher is better)
 * 2. Secondary: Event date (more recent is better)
 * 3. Tertiary: Subject type priority (configurable per channel)
 */
class DefaultRankingStrategy : RankingStrategy {
    
    companion object {
        // Channel-specific subject type priorities
        private val CHANNEL_PRIORITIES = mapOf(
            "email" to listOf("product", "service", "event"),
            "in-app" to listOf("product", "video", "track"),
            "push" to listOf("event", "product", "service"),
            "voice" to listOf("product", "service"),
            "sms" to listOf("event", "product")
        )
    }
    
    override fun rank(
        candidates: List<Candidate>,
        channel: String?,
        customerId: String
    ): List<Candidate> {
        
        val subjectPriorities = channel?.let { CHANNEL_PRIORITIES[it] } ?: emptyList()
        
        return candidates.sortedWith(
            compareByDescending<Candidate> { getPrimaryScore(it) }
                .thenByDescending { it.attributes.eventDate }
                .thenBy { getSubjectPriority(it.subject.type, subjectPriorities) }
        )
    }
    
    /**
     * Gets the primary score for a candidate.
     * Uses the first available score, or 0.0 if no scores exist.
     */
    private fun getPrimaryScore(candidate: Candidate): Double {
        return candidate.scores?.values?.firstOrNull()?.value ?: 0.0
    }
    
    /**
     * Gets the priority index for a subject type.
     * Lower index = higher priority.
     */
    private fun getSubjectPriority(subjectType: String, priorities: List<String>): Int {
        val index = priorities.indexOf(subjectType)
        return if (index >= 0) index else Int.MAX_VALUE
    }
}

/**
 * Score-based ranking strategy that only considers ML model scores.
 * 
 * Useful for channels where score is the primary ranking factor.
 */
class ScoreBasedRankingStrategy : RankingStrategy {
    
    override fun rank(
        candidates: List<Candidate>,
        channel: String?,
        customerId: String
    ): List<Candidate> {
        return candidates.sortedByDescending { candidate ->
            candidate.scores?.values?.maxOfOrNull { it.value } ?: 0.0
        }
    }
}

/**
 * Recency-based ranking strategy that prioritizes recent events.
 * 
 * Useful for time-sensitive channels like push notifications.
 */
class RecencyBasedRankingStrategy : RankingStrategy {
    
    override fun rank(
        candidates: List<Candidate>,
        channel: String?,
        customerId: String
    ): List<Candidate> {
        return candidates.sortedByDescending { it.attributes.eventDate }
    }
}

/**
 * Composite ranking strategy that combines multiple strategies.
 * 
 * Applies strategies in sequence, with each strategy refining the ranking.
 */
class CompositeRankingStrategy(
    private val strategies: List<RankingStrategy>
) : RankingStrategy {
    
    override fun rank(
        candidates: List<Candidate>,
        channel: String?,
        customerId: String
    ): List<Candidate> {
        var ranked = candidates
        
        for (strategy in strategies) {
            ranked = strategy.rank(ranked, channel, customerId)
        }
        
        return ranked
    }
}
