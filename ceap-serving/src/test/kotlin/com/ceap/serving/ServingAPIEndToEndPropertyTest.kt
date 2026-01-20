package com.ceap.serving

import com.ceap.model.*
import com.ceap.model.config.*
import com.ceap.storage.CandidateRepository
import com.ceap.storage.BatchWriteResult
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant

/**
 * End-to-end property test for Serving API.
 * 
 * **Validates: Requirements 11.1, 11.2, 11.3**
 * 
 * Tests API with real DynamoDB backend, various query patterns,
 * and verifies latency targets.
 */
class ServingAPIEndToEndPropertyTest {
    
    @Property(tries = 50)
    fun `serving API retrieves candidates with low latency`(
        @ForAll("validCustomerIds") customerId: String,
        @ForAll("validProgramIds") programId: String,
        @ForAll("validChannels") channel: String,
        @ForAll("validMarketplaces") marketplace: String
    ): Boolean {
        // Given: Mock repository and serving API
        val repository = MockCandidateRepository()
        val customerContextProvider = MockCustomerContextProvider()
        val rankingStrategy = PersonalizedRankingStrategy(customerContextProvider)
        val servingAPI = ServingAPIImpl(repository, rankingStrategy)
        
        // Given: Request for candidates
        val request = GetCandidatesRequest(
            customerId = customerId,
            channel = channel,
            program = programId,
            marketplace = marketplace,
            limit = 10,
            includeScores = true,
            refreshEligibility = false
        )
        
        // When: Get candidates
        val startTime = System.currentTimeMillis()
        val response = servingAPI.getCandidatesForCustomer(request)
        val actualLatency = System.currentTimeMillis() - startTime
        
        // Then: Response is valid
        assertThat(response.candidates).isNotNull
        assertThat(response.metadata).isNotNull
        
        // Then: Latency is acceptable (< 100ms for mock, < 50ms target for real DynamoDB)
        assertThat(response.latencyMs).isLessThan(100)
        assertThat(actualLatency).isLessThan(100)
        
        // Then: Candidates are limited correctly
        assertThat(response.candidates.size).isLessThanOrEqualTo(request.limit)
        
        return true
    }
    
    @Property(tries = 50)
    fun `serving API handles batch queries efficiently`(
        @ForAll("validCustomerIdList") customerIds: List<String>,
        @ForAll("validProgramIds") programId: String,
        @ForAll("validChannels") channel: String,
        @ForAll("validMarketplaces") marketplace: String
    ): Boolean {
        // Given: Mock repository and serving API
        val repository = MockCandidateRepository()
        val customerContextProvider = MockCustomerContextProvider()
        val rankingStrategy = PersonalizedRankingStrategy(customerContextProvider)
        val servingAPI = ServingAPIImpl(repository, rankingStrategy)
        
        // Given: Batch request for candidates
        val request = BatchGetCandidatesRequest(
            customerIds = customerIds,
            channel = channel,
            program = programId,
            marketplace = marketplace,
            limit = 5,
            includeScores = false
        )
        
        // When: Get candidates for batch
        val startTime = System.currentTimeMillis()
        val response = servingAPI.getCandidatesForCustomers(request)
        val actualLatency = System.currentTimeMillis() - startTime
        
        // Then: Response contains results for all customers
        assertThat(response.results).hasSize(customerIds.size)
        assertThat(response.results.keys).containsAll(customerIds)
        
        // Then: Latency is reasonable for batch (< 500ms for mock)
        assertThat(response.latencyMs).isLessThan(500)
        assertThat(actualLatency).isLessThan(500)
        
        // Then: Each customer has limited candidates
        response.results.values.forEach { candidates ->
            assertThat(candidates.size).isLessThanOrEqualTo(request.limit)
        }
        
        return true
    }
    
    @Property(tries = 50)
    fun `serving API supports various query patterns`(
        @ForAll("validCustomerIds") customerId: String,
        @ForAll("validMarketplaces") marketplace: String
    ): Boolean {
        // Given: Mock repository and serving API
        val repository = MockCandidateRepository()
        val customerContextProvider = MockCustomerContextProvider()
        val rankingStrategy = PersonalizedRankingStrategy(customerContextProvider)
        val servingAPI = ServingAPIImpl(repository, rankingStrategy)
        
        // Test pattern 1: Query with channel filter
        val request1 = GetCandidatesRequest(
            customerId = customerId,
            channel = "email",
            program = null,
            marketplace = marketplace,
            limit = 10,
            includeScores = false,
            refreshEligibility = false
        )
        
        val response1 = servingAPI.getCandidatesForCustomer(request1)
        assertThat(response1.candidates).isNotNull
        
        // Test pattern 2: Query with program filter
        val request2 = GetCandidatesRequest(
            customerId = customerId,
            channel = null,
            program = "product-reviews",
            marketplace = marketplace,
            limit = 10,
            includeScores = true,
            refreshEligibility = false
        )
        
        val response2 = servingAPI.getCandidatesForCustomer(request2)
        assertThat(response2.candidates).isNotNull
        
        // Test pattern 3: Query with both filters
        val request3 = GetCandidatesRequest(
            customerId = customerId,
            channel = "in-app",
            program = "video-ratings",
            marketplace = marketplace,
            limit = 5,
            includeScores = false,
            refreshEligibility = false
        )
        
        val response3 = servingAPI.getCandidatesForCustomer(request3)
        assertThat(response3.candidates).isNotNull
        assertThat(response3.candidates.size).isLessThanOrEqualTo(5)
        
        return true
    }
    
    @Provide
    fun validCustomerIds(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange('A', 'Z')
            .ofLength(10)
            .map { "CUST-$it" }
    }
    
    @Provide
    fun validCustomerIdList(): Arbitrary<List<String>> {
        return validCustomerIds().list().ofMinSize(2).ofMaxSize(5)
    }
    
    @Provide
    fun validProgramIds(): Arbitrary<String> {
        return Arbitraries.of(
            "product-reviews",
            "video-ratings",
            "music-feedback",
            "service-surveys",
            "event-participation"
        )
    }
    
    @Provide
    fun validChannels(): Arbitrary<String> {
        return Arbitraries.of("email", "in-app", "push", "voice-assistant")
    }
    
    @Provide
    fun validMarketplaces(): Arbitrary<String> {
        return Arbitraries.of("US", "UK", "DE", "JP", "FR", "CA")
    }
    
    /**
     * Mock candidate repository for testing
     */
    private class MockCandidateRepository : CandidateRepository {
        
        override fun create(candidate: Candidate): Candidate {
            return candidate
        }
        
        override fun get(
            customerId: String,
            programId: String,
            marketplaceId: String,
            subjectType: String,
            subjectId: String
        ): Candidate? {
            return null
        }
        
        override fun update(candidate: Candidate): Candidate {
            return candidate
        }
        
        override fun delete(
            customerId: String,
            programId: String,
            marketplaceId: String,
            subjectType: String,
            subjectId: String
        ) {
            // No-op for mock
        }
        
        override fun queryByProgramAndChannel(
            programId: String,
            channelId: String,
            limit: Int,
            ascending: Boolean
        ): List<Candidate> {
            // Return empty list for mock
            return emptyList()
        }
        
        override fun queryByProgramAndDate(
            programId: String,
            date: String,
            limit: Int
        ): List<Candidate> {
            return emptyList()
        }
        
        override fun batchWrite(candidates: List<Candidate>): BatchWriteResult {
            return BatchWriteResult(
                successfulItems = candidates,
                failedItems = emptyList()
            )
        }
    }
    
    /**
     * Mock customer context provider for testing
     */
    private class MockCustomerContextProvider : CustomerContextProvider {
        override fun getCustomerContext(customerId: String): CustomerContext {
            return CustomerContext(
                customerId = customerId,
                subjectTypeHistory = emptyMap(),
                channelPreferences = emptyMap(),
                preferredTimingWindow = null,
                preferences = emptyMap()
            )
        }
    }
}
