package com.ceap.filters

import com.ceap.model.Candidate
import com.ceap.model.RejectionRecord
import com.ceap.model.config.FilterConfig
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Business rule filter that applies business-specific rules to candidates.
 * 
 * Evaluates business constraints such as order value thresholds,
 * delivery date requirements, and program-specific rules.
 */
class BusinessRuleFilter : Filter {
    
    private val logger = LoggerFactory.getLogger(BusinessRuleFilter::class.java)
    
    private lateinit var filterId: String
    private var minOrderValue: Double? = null
    private var maxDaysSinceEvent: Long? = null
    private var requireDeliveryDate: Boolean = false
    
    override fun getFilterId(): String = filterId
    
    override fun getFilterType(): String = "business_rule"
    
    override fun filter(candidate: Candidate): FilterResult {
        logger.debug("Evaluating business rules for candidate: ${candidate.customerId}")
        
        // Check minimum order value if configured
        minOrderValue?.let { minValue ->
            val orderValue = candidate.attributes.orderValue
            if (orderValue == null) {
                return FilterResult.reject(
                    candidate,
                    RejectionRecord(
                        filterId = filterId,
                        reason = "Order value not available",
                        reasonCode = "ORDER_VALUE_MISSING",
                        timestamp = Instant.now()
                    )
                )
            }
            
            if (orderValue < minValue) {
                return FilterResult.reject(
                    candidate,
                    RejectionRecord(
                        filterId = filterId,
                        reason = "Order value $orderValue below minimum threshold $minValue",
                        reasonCode = "ORDER_VALUE_LOW",
                        timestamp = Instant.now()
                    )
                )
            }
        }
        
        // Check days since event if configured
        maxDaysSinceEvent?.let { maxDays ->
            val eventDate = candidate.attributes.eventDate
            val daysSinceEvent = ChronoUnit.DAYS.between(eventDate, Instant.now())
            
            if (daysSinceEvent > maxDays) {
                return FilterResult.reject(
                    candidate,
                    RejectionRecord(
                        filterId = filterId,
                        reason = "Event occurred $daysSinceEvent days ago, exceeds maximum of $maxDays days",
                        reasonCode = "EVENT_TOO_OLD",
                        timestamp = Instant.now()
                    )
                )
            }
        }
        
        // Check delivery date requirement
        if (requireDeliveryDate) {
            val deliveryDate = candidate.attributes.deliveryDate
            if (deliveryDate == null) {
                return FilterResult.reject(
                    candidate,
                    RejectionRecord(
                        filterId = filterId,
                        reason = "Delivery date is required but not provided",
                        reasonCode = "DELIVERY_DATE_MISSING",
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
            (params["minOrderValue"] as? Number)?.let {
                minOrderValue = it.toDouble()
            }
            (params["maxDaysSinceEvent"] as? Number)?.let {
                maxDaysSinceEvent = it.toLong()
            }
            (params["requireDeliveryDate"] as? Boolean)?.let {
                requireDeliveryDate = it
            }
        }
        
        logger.info("Configured BusinessRuleFilter: minOrderValue=$minOrderValue, maxDaysSinceEvent=$maxDaysSinceEvent, requireDeliveryDate=$requireDeliveryDate")
    }
}
