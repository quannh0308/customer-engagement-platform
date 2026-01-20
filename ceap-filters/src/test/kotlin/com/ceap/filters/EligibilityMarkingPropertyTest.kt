package com.ceap.filters

import com.ceap.model.Candidate
import com.ceap.model.CandidateAttributes
import com.ceap.model.CandidateMetadata
import com.ceap.model.Context
import com.ceap.model.Subject
import com.ceap.model.config.FilterChainConfig
import com.ceap.model.config.FilterConfig
import net.jqwik.api.*
import java.time.Instant

/**
 * Property 11: Eligibility marking
 * 
 * Validates: Requirements 4.4
 * 
 * Verifies that eligible candidates are marked correctly and that
 * channel eligibility flags are set appropriately.
 */
class EligibilityMarkingPropertyTest {
    
    @Property(tries = 100)
    fun `candidates with required channels pass eligibility filter`(
        @ForAll("candidatesWithChannels") candidate: Candidate,
        @ForAll("requiredChannels") requiredChannels: List<String>
    ) {
        // Ensure candidate has all required channels
        val eligibilityMap = requiredChannels.associateWith { true }
        val candidateWithChannels = candidate.copy(
            attributes = candidate.attributes.copy(
                channelEligibility = eligibilityMap
            )
        )
        
        // Create eligibility filter with required channels
        val eligibilityFilter = EligibilityFilter()
        val filterConfig = FilterConfig(
            filterId = "eligibility-filter",
            filterType = "eligibility",
            enabled = true,
            parameters = mapOf("requiredChannels" to requiredChannels),
            order = 0
        )
        
        eligibilityFilter.configure(filterConfig)
        
        // Execute filter
        val result = eligibilityFilter.filter(candidateWithChannels)
        
        // Verify candidate passed
        assert(result.passed) { 
            "Expected candidate with all required channels to pass, but was rejected: ${result.rejectionRecord?.reason}" 
        }
    }
    
    @Property(tries = 100)
    fun `candidates missing required channels are rejected`(
        @ForAll("candidatesWithChannels") candidate: Candidate,
        @ForAll("requiredChannels") requiredChannels: List<String>
    ) {
        // Ensure candidate is missing at least one required channel
        if (requiredChannels.isEmpty()) return
        
        // Remove duplicates and ensure we have at least one unique channel to exclude
        val uniqueRequiredChannels = requiredChannels.distinct()
        if (uniqueRequiredChannels.isEmpty()) return
        
        // Create eligibility map with all but the last unique channel
        val eligibilityMap = uniqueRequiredChannels.dropLast(1).associateWith { true }
        val candidateWithMissingChannel = candidate.copy(
            attributes = candidate.attributes.copy(
                channelEligibility = eligibilityMap
            )
        )
        
        // Create eligibility filter with all unique required channels
        val eligibilityFilter = EligibilityFilter()
        val filterConfig = FilterConfig(
            filterId = "eligibility-filter",
            filterType = "eligibility",
            enabled = true,
            parameters = mapOf("requiredChannels" to uniqueRequiredChannels),
            order = 0
        )
        
        eligibilityFilter.configure(filterConfig)
        
        // Execute filter
        val result = eligibilityFilter.filter(candidateWithMissingChannel)
        
        // Verify candidate was rejected
        assert(!result.passed) { 
            "Expected candidate missing required channels to be rejected" 
        }
        assert(result.rejectionRecord != null) { 
            "Expected rejection record for missing channels" 
        }
        assert(result.rejectionRecord!!.reasonCode == "CHANNEL_INELIGIBLE") {
            "Expected CHANNEL_INELIGIBLE reason code, got ${result.rejectionRecord!!.reasonCode}"
        }
    }
    
    @Property(tries = 100)
    fun `channel eligibility flags are preserved through filter chain`(
        @ForAll("candidatesWithChannels") candidate: Candidate
    ) {
        // Create a passing filter
        val passingFilter = object : Filter {
            override fun getFilterId(): String = "passing-filter"
            override fun getFilterType(): String = "passing"
            override fun filter(candidate: Candidate): FilterResult = FilterResult.pass(candidate)
            override fun configure(config: FilterConfig) {}
        }
        
        val filterConfig = FilterConfig(
            filterId = "passing-filter",
            filterType = "passing",
            enabled = true,
            parameters = null,
            order = 0
        )
        
        val chainConfig = FilterChainConfig(filters = listOf(filterConfig))
        val executor = FilterChainExecutor(listOf(passingFilter), chainConfig)
        
        // Store original channel eligibility
        val originalEligibility = candidate.attributes.channelEligibility
        
        // Execute filter chain
        val result = executor.execute(candidate)
        
        // Verify channel eligibility is preserved
        assert(result.candidate.attributes.channelEligibility == originalEligibility) {
            "Channel eligibility should be preserved through filter chain"
        }
    }
    
    @Property(tries = 100)
    fun `candidates with no eligible channels are rejected when channels required`(
        @ForAll("candidatesWithChannels") candidate: Candidate,
        @ForAll("requiredChannels") requiredChannels: List<String>
    ) {
        if (requiredChannels.isEmpty()) return
        
        // Create candidate with no eligible channels
        val candidateWithNoChannels = candidate.copy(
            attributes = candidate.attributes.copy(
                channelEligibility = emptyMap()
            )
        )
        
        // Create eligibility filter with required channels
        val eligibilityFilter = EligibilityFilter()
        val filterConfig = FilterConfig(
            filterId = "eligibility-filter",
            filterType = "eligibility",
            enabled = true,
            parameters = mapOf("requiredChannels" to requiredChannels),
            order = 0
        )
        
        eligibilityFilter.configure(filterConfig)
        
        // Execute filter
        val result = eligibilityFilter.filter(candidateWithNoChannels)
        
        // Verify candidate was rejected
        assert(!result.passed) { 
            "Expected candidate with no eligible channels to be rejected" 
        }
    }
    
    @Provide
    fun candidatesWithChannels(): Arbitrary<Candidate> {
        return Arbitraries.create {
            val channels = listOf("email", "push", "sms", "in-app")
            val eligibilityMap = channels.associateWith { 
                Arbitraries.of(true, false).sample() 
            }
            
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
                    channelEligibility = eligibilityMap
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
    fun requiredChannels(): Arbitrary<List<String>> {
        return Arbitraries.of("email", "push", "sms", "in-app")
            .list()
            .ofMinSize(1)
            .ofMaxSize(3)
    }
}
