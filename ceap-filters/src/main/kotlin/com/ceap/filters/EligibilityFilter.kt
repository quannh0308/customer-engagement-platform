package com.ceap.filters

import com.ceap.model.Candidate
import com.ceap.model.RejectionRecord
import com.ceap.model.config.FilterConfig
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Eligibility filter that checks if a candidate is eligible for customer engagement.
 * 
 * Evaluates channel eligibility, timing windows, and other eligibility criteria.
 */
class EligibilityFilter : Filter {
    
    private val logger = LoggerFactory.getLogger(EligibilityFilter::class.java)
    
    private lateinit var filterId: String
    private var requiredChannels: List<String> = emptyList()
    private var checkTimingWindow: Boolean = true
    
    override fun getFilterId(): String = filterId
    
    override fun getFilterType(): String = "eligibility"
    
    override fun filter(candidate: Candidate): FilterResult {
        logger.debug("Evaluating eligibility for candidate: ${candidate.customerId}")
        
        // Check channel eligibility
        if (requiredChannels.isNotEmpty()) {
            val eligibleChannels = candidate.attributes.channelEligibility
                .filterValues { it }
                .keys
            
            val missingChannels = requiredChannels.filter { it !in eligibleChannels }
            
            if (missingChannels.isNotEmpty()) {
                return FilterResult.reject(
                    candidate,
                    RejectionRecord(
                        filterId = filterId,
                        reason = "Not eligible for required channels: ${missingChannels.joinToString(", ")}",
                        reasonCode = "CHANNEL_INELIGIBLE",
                        timestamp = Instant.now()
                    )
                )
            }
        }
        
        // Check timing window if enabled
        if (checkTimingWindow) {
            val timingWindow = candidate.attributes.timingWindow
            if (timingWindow == null || timingWindow.isEmpty()) {
                return FilterResult.reject(
                    candidate,
                    RejectionRecord(
                        filterId = filterId,
                        reason = "Timing window not specified",
                        reasonCode = "TIMING_WINDOW_MISSING",
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
            @Suppress("UNCHECKED_CAST")
            (params["requiredChannels"] as? List<String>)?.let {
                requiredChannels = it
            }
            (params["checkTimingWindow"] as? Boolean)?.let {
                checkTimingWindow = it
            }
        }
        
        logger.info("Configured EligibilityFilter: requiredChannels=$requiredChannels, checkTimingWindow=$checkTimingWindow")
    }
}
