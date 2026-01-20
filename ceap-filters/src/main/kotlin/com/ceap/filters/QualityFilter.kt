package com.ceap.filters

import com.ceap.model.Candidate
import com.ceap.model.RejectionRecord
import com.ceap.model.config.FilterConfig
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Quality filter that evaluates candidate quality metrics.
 * 
 * Checks quality scores, confidence levels, and data completeness.
 */
class QualityFilter : Filter {
    
    private val logger = LoggerFactory.getLogger(QualityFilter::class.java)
    
    private lateinit var filterId: String
    private var minQualityScore: Double = 0.6
    private var minConfidence: Double = 0.7
    private var requireMediaEligibility: Boolean = false
    
    override fun getFilterId(): String = filterId
    
    override fun getFilterType(): String = "quality"
    
    override fun filter(candidate: Candidate): FilterResult {
        logger.debug("Evaluating quality for candidate: ${candidate.customerId}")
        
        // Check quality score
        val qualityScore = candidate.scores?.get("quality")
        
        if (qualityScore == null) {
            return FilterResult.reject(
                candidate,
                RejectionRecord(
                    filterId = filterId,
                    reason = "Quality score not available",
                    reasonCode = "QUALITY_SCORE_MISSING",
                    timestamp = Instant.now()
                )
            )
        }
        
        if (qualityScore.value < minQualityScore) {
            return FilterResult.reject(
                candidate,
                RejectionRecord(
                    filterId = filterId,
                    reason = "Quality score ${qualityScore.value} below minimum threshold $minQualityScore",
                    reasonCode = "QUALITY_SCORE_LOW",
                    timestamp = Instant.now()
                )
            )
        }
        
        // Check confidence level
        val confidence = qualityScore.confidence
        if (confidence != null && confidence < minConfidence) {
            return FilterResult.reject(
                candidate,
                RejectionRecord(
                    filterId = filterId,
                    reason = "Quality score confidence $confidence below minimum threshold $minConfidence",
                    reasonCode = "CONFIDENCE_LOW",
                    timestamp = Instant.now()
                )
            )
        }
        
        // Check media eligibility if required
        if (requireMediaEligibility) {
            val mediaEligible = candidate.attributes.mediaEligible
            if (mediaEligible == null || !mediaEligible) {
                return FilterResult.reject(
                    candidate,
                    RejectionRecord(
                        filterId = filterId,
                        reason = "Media eligibility required but not met",
                        reasonCode = "MEDIA_INELIGIBLE",
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
            (params["minQualityScore"] as? Number)?.let {
                minQualityScore = it.toDouble()
            }
            (params["minConfidence"] as? Number)?.let {
                minConfidence = it.toDouble()
            }
            (params["requireMediaEligibility"] as? Boolean)?.let {
                requireMediaEligibility = it
            }
        }
        
        logger.info("Configured QualityFilter: minQualityScore=$minQualityScore, minConfidence=$minConfidence, requireMediaEligibility=$requireMediaEligibility")
    }
}
