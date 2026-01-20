package com.ceap.filters

import com.ceap.model.Candidate
import com.ceap.model.CandidateAttributes
import com.ceap.model.CandidateMetadata
import com.ceap.model.Context
import com.ceap.model.RejectionRecord
import com.ceap.model.Subject
import com.ceap.model.config.FilterChainConfig
import com.ceap.model.config.FilterConfig
import net.jqwik.api.*
import java.time.Instant

/**
 * Property 10: Rejection tracking completeness
 * 
 * Validates: Requirements 4.2, 4.6
 * 
 * Verifies that all rejections are tracked with reasons and that
 * rejection metadata is complete.
 */
class RejectionTrackingPropertyTest {
    
    @Property(tries = 100)
    fun `all rejections are tracked with complete metadata`(
        @ForAll("candidates") candidate: Candidate
    ) {
        // Create a filter that always rejects
        val rejectingFilter = RejectingFilter(
            id = "rejecting-filter",
            reason = "Test rejection",
            reasonCode = "TEST_REJECT"
        )
        
        val filterConfig = FilterConfig(
            filterId = "rejecting-filter",
            filterType = "rejecting",
            enabled = true,
            parameters = null,
            order = 0
        )
        
        val chainConfig = FilterChainConfig(filters = listOf(filterConfig))
        val executor = FilterChainExecutor(listOf(rejectingFilter), chainConfig)
        
        // Execute the filter chain
        val result = executor.execute(candidate)
        
        // Verify rejection was tracked
        assert(!result.passed) { "Expected candidate to be rejected" }
        assert(result.rejectionHistory.isNotEmpty()) { "Expected rejection history to be populated" }
        
        // Verify rejection metadata is complete
        val rejection = result.rejectionHistory.first()
        assert(rejection.filterId.isNotBlank()) { "Filter ID must not be blank" }
        assert(rejection.reason.isNotBlank()) { "Reason must not be blank" }
        assert(rejection.reasonCode.isNotBlank()) { "Reason code must not be blank" }
        assert(rejection.timestamp != null) { "Timestamp must not be null" }
        
        // Verify rejection is in candidate's rejection history
        assert(result.candidate.rejectionHistory != null) { "Candidate rejection history must not be null" }
        assert(result.candidate.rejectionHistory!!.isNotEmpty()) { "Candidate rejection history must not be empty" }
    }
    
    @Property(tries = 100)
    fun `multiple rejections are all tracked`(
        @ForAll("candidates") candidate: Candidate,
        @ForAll("rejectionCount") rejectionCount: Int
    ) {
        // Create multiple rejecting filters
        val filters = (0 until rejectionCount).map { index ->
            RejectingFilter(
                id = "filter-$index",
                reason = "Rejection $index",
                reasonCode = "REJECT_$index"
            )
        }
        
        val filterConfigs = (0 until rejectionCount).map { index ->
            FilterConfig(
                filterId = "filter-$index",
                filterType = "rejecting",
                enabled = true,
                parameters = null,
                order = index
            )
        }
        
        val chainConfig = FilterChainConfig(filters = filterConfigs)
        val executor = FilterChainExecutor(filters, chainConfig)
        
        // Execute the filter chain
        val result = executor.execute(candidate)
        
        // Verify all rejections were tracked (only first rejection stops the chain)
        assert(!result.passed) { "Expected candidate to be rejected" }
        assert(result.rejectionHistory.size == 1) { 
            "Expected 1 rejection (chain stops at first rejection), got ${result.rejectionHistory.size}" 
        }
        
        // Verify the rejection is from the first filter
        val rejection = result.rejectionHistory.first()
        assert(rejection.filterId == "filter-0") { 
            "Expected rejection from filter-0, got ${rejection.filterId}" 
        }
    }
    
    @Property(tries = 100)
    fun `passing filters do not add rejection records`(
        @ForAll("candidates") candidate: Candidate
    ) {
        // Create a filter that always passes
        val passingFilter = PassingFilter(id = "passing-filter")
        
        val filterConfig = FilterConfig(
            filterId = "passing-filter",
            filterType = "passing",
            enabled = true,
            parameters = null,
            order = 0
        )
        
        val chainConfig = FilterChainConfig(filters = listOf(filterConfig))
        val executor = FilterChainExecutor(listOf(passingFilter), chainConfig)
        
        // Execute the filter chain
        val result = executor.execute(candidate)
        
        // Verify no rejections were tracked
        assert(result.passed) { "Expected candidate to pass" }
        assert(result.rejectionHistory.isEmpty()) { "Expected no rejection history" }
        assert(result.candidate.rejectionHistory == null || result.candidate.rejectionHistory!!.isEmpty()) {
            "Expected candidate rejection history to be empty"
        }
    }
    
    @Provide
    fun candidates(): Arbitrary<Candidate> {
        return Arbitraries.create {
            Candidate(
                customerId = "customer-${Arbitraries.integers().between(1, 1000).sample()}",
                context = listOf(
                    Context(type = "marketplace", id = "US"),
                    Context(type = "program", id = "test-program")
                ),
                subject = Subject(
                    type = "product",
                    id = "product-${Arbitraries.integers().between(1, 1000).sample()}",
                    metadata = null
                ),
                scores = null,
                attributes = CandidateAttributes(
                    eventDate = Instant.now(),
                    deliveryDate = null,
                    timingWindow = "7d",
                    orderValue = null,
                    mediaEligible = true,
                    channelEligibility = mapOf("email" to true, "push" to true)
                ),
                metadata = CandidateMetadata(
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    expiresAt = Instant.now().plusSeconds(86400),
                    version = 1L,
                    sourceConnectorId = "test-connector",
                    workflowExecutionId = "test-execution"
                ),
                rejectionHistory = null
            )
        }
    }
    
    @Provide
    fun rejectionCount(): Arbitrary<Int> {
        return Arbitraries.integers().between(1, 5)
    }
    
    /**
     * Filter that always rejects candidates.
     */
    private class RejectingFilter(
        private val id: String,
        private val reason: String,
        private val reasonCode: String
    ) : Filter {
        
        override fun getFilterId(): String = id
        
        override fun getFilterType(): String = "rejecting"
        
        override fun filter(candidate: Candidate): FilterResult {
            return FilterResult.reject(
                candidate,
                RejectionRecord(
                    filterId = id,
                    reason = reason,
                    reasonCode = reasonCode,
                    timestamp = Instant.now()
                )
            )
        }
        
        override fun configure(config: FilterConfig) {
            // No configuration needed
        }
    }
    
    /**
     * Filter that always passes candidates.
     */
    private class PassingFilter(private val id: String) : Filter {
        
        override fun getFilterId(): String = id
        
        override fun getFilterType(): String = "passing"
        
        override fun filter(candidate: Candidate): FilterResult {
            return FilterResult.pass(candidate)
        }
        
        override fun configure(config: FilterConfig) {
            // No configuration needed
        }
    }
}
