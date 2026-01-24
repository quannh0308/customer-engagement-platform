package com.ceap.serving

import com.ceap.model.Candidate
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Personalized ranking strategy that uses customer history and preferences.
 * 
 * This strategy:
 * - Uses customer purchase history to boost relevant subjects
 * - Considers customer preferences and behavior patterns
 * - Applies context-aware ranking algorithms
 * - Supports A/B testing with treatment-specific ranking
 * 
 * **Validates: Requirements 15.4**
 */
class PersonalizedRankingStrategy(
    private val customerContextProvider: CustomerContextProvider
) : RankingStrategy {
    
    override fun rank(
        candidates: List<Candidate>,
        channel: String?,
        customerId: String
    ): List<Candidate> {
        logger.debug { "Personalizing ranking for customer $customerId, channel=$channel" }
        
        // Get customer context
        val context = customerContextProvider.getCustomerContext(customerId)
        
        // Calculate personalized scores for each candidate
        val scoredCandidates = candidates.map { candidate ->
            val personalizedScore = calculatePersonalizedScore(candidate, context, channel)
            ScoredCandidate(candidate, personalizedScore)
        }
        
        // Sort by personalized score (descending)
        val ranked = scoredCandidates
            .sortedByDescending { it.personalizedScore }
            .map { it.candidate }
        
        logger.debug {
            "Personalized ranking completed for customer $customerId: " +
            "candidateCount=${candidates.size}, " +
            "topScore=${scoredCandidates.maxOfOrNull { it.personalizedScore } ?: 0.0}"
        }
        
        return ranked
    }
    
    /**
     * Calculates a personalized score for a candidate based on customer context.
     * 
     * The personalized score combines:
     * 1. Base ML model score
     * 2. Subject type affinity (based on customer history)
     * 3. Recency boost (more recent events score higher)
     * 4. Channel-specific adjustments
     * 5. Customer preference weights
     * 
     * @param candidate Candidate to score
     * @param context Customer context with history and preferences
     * @param channel Channel identifier
     * @return Personalized score (0.0 to 1.0+)
     */
    private fun calculatePersonalizedScore(
        candidate: Candidate,
        context: CustomerContext,
        channel: String?
    ): Double {
        // Start with base ML model score
        val baseScore = candidate.scores?.values?.firstOrNull()?.value ?: 0.5
        
        // Calculate subject type affinity
        val subjectAffinity = calculateSubjectAffinity(
            candidate.subject.type,
            context.subjectTypeHistory
        )
        
        // Calculate recency boost
        val recencyBoost = calculateRecencyBoost(
            candidate.attributes.eventDate.toString(),
            context.preferredTimingWindow
        )
        
        // Calculate channel-specific adjustment
        val channelAdjustment = calculateChannelAdjustment(
            channel,
            context.channelPreferences
        )
        
        // Calculate preference weight
        val preferenceWeight = calculatePreferenceWeight(
            candidate,
            context.preferences
        )
        
        // Combine scores with weights
        val personalizedScore = (
            baseScore * 0.4 +
            subjectAffinity * 0.25 +
            recencyBoost * 0.15 +
            channelAdjustment * 0.1 +
            preferenceWeight * 0.1
        )
        
        logger.trace {
            "Personalized score for candidate ${candidate.customerId}/${candidate.subject.id}: " +
            "base=$baseScore, affinity=$subjectAffinity, recency=$recencyBoost, " +
            "channel=$channelAdjustment, preference=$preferenceWeight, " +
            "final=$personalizedScore"
        }
        
        return personalizedScore
    }
    
    /**
     * Calculates subject type affinity based on customer history.
     * 
     * Customers who have engaged with certain subject types in the past
     * are more likely to engage with similar subjects in the future.
     * 
     * @param subjectType Subject type to score
     * @param history Map of subject types to engagement counts
     * @return Affinity score (0.0 to 1.0)
     */
    private fun calculateSubjectAffinity(
        subjectType: String,
        history: Map<String, Int>
    ): Double {
        val engagementCount = history[subjectType] ?: 0
        val totalEngagements = history.values.sum()
        
        if (totalEngagements == 0) {
            return 0.5 // Neutral score if no history
        }
        
        // Normalize to 0.0-1.0 range with diminishing returns
        val affinity = engagementCount.toDouble() / totalEngagements
        return minOf(affinity * 2.0, 1.0) // Cap at 1.0
    }
    
    /**
     * Calculates recency boost based on event timing.
     * 
     * More recent events are generally more relevant for customer engagement.
     * 
     * @param eventDate Event date as ISO string
     * @param preferredWindow Customer's preferred timing window in days
     * @return Recency boost (0.0 to 1.0)
     */
    private fun calculateRecencyBoost(
        eventDate: String,
        preferredWindow: Int?
    ): Double {
        try {
            val eventTime = java.time.Instant.parse(eventDate)
            val now = java.time.Instant.now()
            val daysSinceEvent = java.time.Duration.between(eventTime, now).toDays()
            
            val window = preferredWindow ?: 7 // Default 7 days
            
            // Linear decay over the preferred window
            val boost = maxOf(0.0, 1.0 - (daysSinceEvent.toDouble() / window))
            
            return boost
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse event date: $eventDate" }
            return 0.5 // Neutral score on error
        }
    }
    
    /**
     * Calculates channel-specific adjustment based on customer preferences.
     * 
     * Customers may prefer certain channels over others.
     * 
     * @param channel Channel identifier
     * @param preferences Map of channel to preference score
     * @return Channel adjustment (0.0 to 1.0)
     */
    private fun calculateChannelAdjustment(
        channel: String?,
        preferences: Map<String, Double>
    ): Double {
        if (channel == null) {
            return 0.5 // Neutral if no channel specified
        }
        
        return preferences[channel] ?: 0.5 // Default to neutral
    }
    
    /**
     * Calculates preference weight based on candidate attributes.
     * 
     * Considers factors like order value, media eligibility, etc.
     * 
     * @param candidate Candidate to score
     * @param preferences Customer preferences
     * @return Preference weight (0.0 to 1.0)
     */
    private fun calculatePreferenceWeight(
        candidate: Candidate,
        preferences: Map<String, Any>
    ): Double {
        var weight = 0.5 // Start neutral
        
        // Boost for high-value orders if customer prefers them
        val orderValue = candidate.attributes.orderValue
        if (orderValue != null && orderValue > 100.0) {
            val prefersHighValue = preferences["prefersHighValueOrders"] as? Boolean ?: false
            if (prefersHighValue) {
                weight += 0.2
            }
        }
        
        // Boost for media-eligible candidates if customer prefers visual content
        val mediaEligible = candidate.attributes.mediaEligible ?: false
        if (mediaEligible) {
            val prefersMedia = preferences["prefersMediaContent"] as? Boolean ?: false
            if (prefersMedia) {
                weight += 0.2
            }
        }
        
        return minOf(weight, 1.0) // Cap at 1.0
    }
    
    /**
     * Internal data class for scored candidates.
     */
    private data class ScoredCandidate(
        val candidate: Candidate,
        val personalizedScore: Double
    )
}

/**
 * Provider interface for customer context information.
 * 
 * In production, this would fetch data from:
 * - Customer profile service
 * - Purchase history database
 * - Behavioral analytics service
 * - Preference management system
 */
interface CustomerContextProvider {
    
    /**
     * Gets customer context for personalized ranking.
     * 
     * @param customerId Customer identifier
     * @return Customer context
     */
    fun getCustomerContext(customerId: String): CustomerContext
}

/**
 * Customer context for personalized ranking.
 * 
 * @property customerId Customer identifier
 * @property subjectTypeHistory Map of subject types to engagement counts
 * @property channelPreferences Map of channels to preference scores (0.0 to 1.0)
 * @property preferredTimingWindow Preferred timing window in days
 * @property preferences Additional customer preferences
 */
data class CustomerContext(
    val customerId: String,
    val subjectTypeHistory: Map<String, Int> = emptyMap(),
    val channelPreferences: Map<String, Double> = emptyMap(),
    val preferredTimingWindow: Int? = null,
    val preferences: Map<String, Any> = emptyMap()
)

/**
 * Default implementation of CustomerContextProvider for testing.
 * 
 * In production, this would be replaced with a real implementation
 * that fetches data from backend services.
 */
class DefaultCustomerContextProvider : CustomerContextProvider {
    
    private val contextCache = mutableMapOf<String, CustomerContext>()
    
    override fun getCustomerContext(customerId: String): CustomerContext {
        return contextCache.getOrPut(customerId) {
            // Return default context
            CustomerContext(
                customerId = customerId,
                subjectTypeHistory = mapOf(
                    "product" to 5,
                    "video" to 3,
                    "service" to 2
                ),
                channelPreferences = mapOf(
                    "email" to 0.7,
                    "in-app" to 0.8,
                    "push" to 0.6,
                    "voice" to 0.5
                ),
                preferredTimingWindow = 7,
                preferences = mapOf(
                    "prefersHighValueOrders" to true,
                    "prefersMediaContent" to true
                )
            )
        }
    }
    
    /**
     * Updates customer context (for testing).
     * 
     * @param context Customer context to store
     */
    fun updateContext(context: CustomerContext) {
        contextCache[context.customerId] = context
    }
}
