package com.ceap.filters

import com.ceap.model.Candidate
import com.ceap.model.RejectionRecord
import com.ceap.model.config.FilterChainConfig
import com.ceap.model.config.FilterConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

/**
 * Executes a chain of filters on candidates.
 * 
 * Filters are executed in the order specified by their configuration.
 * Rejection tracking is maintained throughout the chain.
 */
class FilterChainExecutor(
    private val filters: List<Filter>,
    private val config: FilterChainConfig
) {
    
    private val logger = LoggerFactory.getLogger(FilterChainExecutor::class.java)
    
    init {
        // Configure all filters by matching filter type to config
        val filterConfigMap = config.filters.associateBy { it.filterType }
        filters.forEach { filter ->
            val filterConfig = filterConfigMap[filter.getFilterType()]
            if (filterConfig != null) {
                filter.configure(filterConfig)
            } else {
                logger.warn("No configuration found for filter type: ${filter.getFilterType()}")
            }
        }
    }
    
    /**
     * Execute the filter chain on a single candidate.
     * 
     * @param candidate The candidate to filter
     * @return FilterChainResult containing the filtered candidate and rejection history
     */
    fun execute(candidate: Candidate): FilterChainResult {
        val sortedFilters = getSortedEnabledFilters()
        var currentCandidate = candidate
        val rejections = mutableListOf<RejectionRecord>()
        
        for (filter in sortedFilters) {
            logger.debug("Executing filter: ${filter.getFilterId()} on candidate: ${candidate.customerId}")
            
            val result = try {
                filter.filter(currentCandidate)
            } catch (e: Exception) {
                logger.error("Filter ${filter.getFilterId()} threw exception", e)
                // Create rejection record for filter failure
                FilterResult.reject(
                    currentCandidate,
                    RejectionRecord(
                        filterId = filter.getFilterId(),
                        reason = "Filter execution failed: ${e.message}",
                        reasonCode = "FILTER_ERROR",
                        timestamp = java.time.Instant.now()
                    )
                )
            }
            
            if (!result.passed) {
                // Candidate rejected, add rejection record
                result.rejectionRecord?.let { rejections.add(it) }
                
                // Update candidate with rejection history
                currentCandidate = currentCandidate.copy(
                    rejectionHistory = (currentCandidate.rejectionHistory ?: emptyList()) + rejections
                )
                
                logger.info(
                    "Candidate ${candidate.customerId} rejected by filter ${filter.getFilterId()}: " +
                    "${result.rejectionRecord?.reason}"
                )
                
                return FilterChainResult(
                    candidate = currentCandidate,
                    passed = false,
                    rejectionHistory = rejections
                )
            }
            
            currentCandidate = result.candidate
        }
        
        logger.info("Candidate ${candidate.customerId} passed all filters")
        
        return FilterChainResult(
            candidate = currentCandidate,
            passed = true,
            rejectionHistory = emptyList()
        )
    }
    
    /**
     * Execute the filter chain on multiple candidates in parallel.
     * 
     * @param candidates The candidates to filter
     * @return List of FilterChainResult for each candidate
     */
    suspend fun executeParallel(candidates: List<Candidate>): List<FilterChainResult> = coroutineScope {
        candidates.map { candidate ->
            async {
                execute(candidate)
            }
        }.awaitAll()
    }
    
    /**
     * Get filters sorted by their configured order, excluding disabled filters.
     */
    private fun getSortedEnabledFilters(): List<Filter> {
        val filterConfigMap = config.filters
            .filter { it.enabled }
            .associateBy { it.filterId }
        
        return filters
            .filter { filterConfigMap.containsKey(it.getFilterId()) }
            .sortedBy { filter ->
                filterConfigMap[filter.getFilterId()]?.order ?: Int.MAX_VALUE
            }
    }
}

/**
 * Result of executing a filter chain on a candidate.
 * 
 * @property candidate The candidate after filtering (with updated rejection history if rejected)
 * @property passed Whether the candidate passed all filters
 * @property rejectionHistory List of rejection records if the candidate was rejected
 */
data class FilterChainResult(
    val candidate: Candidate,
    val passed: Boolean,
    val rejectionHistory: List<RejectionRecord>
)
