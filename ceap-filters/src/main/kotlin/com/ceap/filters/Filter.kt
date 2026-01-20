package com.ceap.filters

import com.ceap.model.Candidate
import com.ceap.model.config.FilterConfig

/**
 * Interface for all candidate filters.
 * 
 * Filters evaluate candidates and determine if they should be rejected
 * or allowed to proceed through the pipeline.
 */
interface Filter {
    
    /**
     * Get the unique identifier for this filter.
     * 
     * @return Filter ID
     */
    fun getFilterId(): String
    
    /**
     * Get the type of this filter.
     * 
     * @return Filter type (e.g., "trust", "eligibility", "business_rule", "quality")
     */
    fun getFilterType(): String
    
    /**
     * Filter a candidate and return the result.
     * 
     * @param candidate The candidate to filter
     * @return FilterResult indicating if the candidate passed or was rejected
     */
    fun filter(candidate: Candidate): FilterResult
    
    /**
     * Configure the filter with the provided configuration.
     * 
     * @param config Filter configuration
     */
    fun configure(config: FilterConfig)
}
