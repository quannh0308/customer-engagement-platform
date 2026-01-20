package com.ceap.filters

import com.ceap.model.Candidate
import com.ceap.model.RejectionRecord
import com.ceap.model.config.FilterConfig
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Trust filter that evaluates candidate trustworthiness.
 * 
 * Checks trust-related attributes such as customer trust score,
 * fraud indicators, and account status.
 */
class TrustFilter : Filter {
    
    private val logger = LoggerFactory.getLogger(TrustFilter::class.java)
    
    private lateinit var filterId: String
    private var minTrustScore: Double = 0.5
    private var checkFraudIndicators: Boolean = true
    
    override fun getFilterId(): String = filterId
    
    override fun getFilterType(): String = "trust"
    
    override fun filter(candidate: Candidate): FilterResult {
        logger.debug("Evaluating trust for candidate: ${candidate.customerId}")
        
        // Check if trust score exists in scores
        val trustScore = candidate.scores?.get("trust")
        
        if (trustScore == null) {
            return FilterResult.reject(
                candidate,
                RejectionRecord(
                    filterId = filterId,
                    reason = "Trust score not available",
                    reasonCode = "TRUST_SCORE_MISSING",
                    timestamp = Instant.now()
                )
            )
        }
        
        // Check if trust score meets minimum threshold
        if (trustScore.value < minTrustScore) {
            return FilterResult.reject(
                candidate,
                RejectionRecord(
                    filterId = filterId,
                    reason = "Trust score ${trustScore.value} below minimum threshold $minTrustScore",
                    reasonCode = "TRUST_SCORE_LOW",
                    timestamp = Instant.now()
                )
            )
        }
        
        // Check fraud indicators if enabled
        if (checkFraudIndicators) {
            val fraudScore = candidate.scores?.get("fraud")
            if (fraudScore != null && fraudScore.value > 0.5) {
                return FilterResult.reject(
                    candidate,
                    RejectionRecord(
                        filterId = filterId,
                        reason = "High fraud score detected: ${fraudScore.value}",
                        reasonCode = "FRAUD_DETECTED",
                        timestamp = Instant.now()
                    )
                )
            }
        }
        
        return FilterResult.pass(candidate)
    }
    
    override fun configure(config: FilterConfig) {
        this.filterId = config.filterId
        
        config.parameters?.let { params ->
            (params["minTrustScore"] as? Number)?.let {
                minTrustScore = it.toDouble()
            }
            (params["checkFraudIndicators"] as? Boolean)?.let {
                checkFraudIndicators = it
            }
        }
        
        logger.info("Configured TrustFilter: minTrustScore=$minTrustScore, checkFraudIndicators=$checkFraudIndicators")
    }
}
