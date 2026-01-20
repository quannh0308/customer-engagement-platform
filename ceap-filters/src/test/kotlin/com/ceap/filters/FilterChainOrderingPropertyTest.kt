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
 * Property 9: Filter chain ordering
 * 
 * Validates: Requirements 4.1
 * 
 * Verifies that filters execute in the configured order and that
 * order is preserved across executions.
 */
class FilterChainOrderingPropertyTest {
    
    @Property(tries = 100)
    fun `filters execute in configured order`(
        @ForAll("candidates") candidate: Candidate,
        @ForAll("filterOrders") filterOrders: List<Int>
    ) {
        // Create tracking filters that record execution order
        val executionOrder = mutableListOf<String>()
        val filters = filterOrders.mapIndexed { index, order ->
            TrackingFilter("filter-$index", executionOrder)
        }
        
        // Create filter chain config with specified orders
        val filterConfigs = filterOrders.mapIndexed { index, order ->
            FilterConfig(
                filterId = "filter-$index",
                filterType = "tracking",
                enabled = true,
                parameters = null,
                order = order
            )
        }
        
        val chainConfig = FilterChainConfig(filters = filterConfigs)
        val executor = FilterChainExecutor(filters, chainConfig)
        
        // Execute the filter chain
        executor.execute(candidate)
        
        // Verify filters executed in order
        val expectedOrder = filterOrders.indices
            .sortedBy { filterOrders[it] }
            .map { "filter-$it" }
        
        assert(executionOrder == expectedOrder) {
            "Expected execution order $expectedOrder but got $executionOrder"
        }
    }
    
    @Property(tries = 100)
    fun `filter order is preserved across multiple executions`(
        @ForAll("candidates") candidate: Candidate,
        @ForAll("filterOrders") filterOrders: List<Int>
    ) {
        // Create tracking filters
        val filters = filterOrders.mapIndexed { index, _ ->
            TrackingFilter("filter-$index", mutableListOf())
        }
        
        // Create filter chain config
        val filterConfigs = filterOrders.mapIndexed { index, order ->
            FilterConfig(
                filterId = "filter-$index",
                filterType = "tracking",
                enabled = true,
                parameters = null,
                order = order
            )
        }
        
        val chainConfig = FilterChainConfig(filters = filterConfigs)
        val executor = FilterChainExecutor(filters, chainConfig)
        
        // Execute multiple times and track order
        val executionOrders = mutableListOf<List<String>>()
        repeat(3) {
            val executionOrder = mutableListOf<String>()
            val trackingFilters = filterOrders.mapIndexed { index, _ ->
                TrackingFilter("filter-$index", executionOrder)
            }
            val trackingExecutor = FilterChainExecutor(trackingFilters, chainConfig)
            trackingExecutor.execute(candidate)
            executionOrders.add(executionOrder.toList())
        }
        
        // Verify all executions had the same order
        val firstOrder = executionOrders.first()
        assert(executionOrders.all { it == firstOrder }) {
            "Filter order not consistent across executions: $executionOrders"
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
    fun filterOrders(): Arbitrary<List<Int>> {
        return Arbitraries.integers()
            .between(0, 10)
            .list()
            .ofMinSize(2)
            .ofMaxSize(5)
    }
    
    /**
     * Filter that tracks when it is executed.
     */
    private class TrackingFilter(
        private val id: String,
        private val executionOrder: MutableList<String>
    ) : Filter {
        
        override fun getFilterId(): String = id
        
        override fun getFilterType(): String = "tracking"
        
        override fun filter(candidate: Candidate): FilterResult {
            executionOrder.add(id)
            return FilterResult.pass(candidate)
        }
        
        override fun configure(config: FilterConfig) {
            // No configuration needed
        }
    }
}
