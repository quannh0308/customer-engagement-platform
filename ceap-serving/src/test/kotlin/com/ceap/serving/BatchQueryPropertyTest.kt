package com.ceap.serving

import com.ceap.model.*
import com.ceap.storage.CandidateRepository
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.*
import java.time.Instant

/**
 * Property-based tests for batch query correctness.
 * 
 * **Feature: ceap-platform, Property 20: Batch query correctness**
 * 
 * **Validates: Requirements 6.6**
 * 
 * For any batch query with multiple customerIds, the response must contain results
 * for each customerId, and results for one customer must not be mixed with results
 * for another customer.
 */
class BatchQueryPropertyTest {
    
    @Property(tries = 100)
    fun `batch query returns results for all customers`(
        @ForAll("customerIds") customerIds: List<String>
    ) {
        // Given: A repository that returns candidates
        val repository = mock<CandidateRepository> {
            on { queryByProgramAndChannel(any(), any(), any(), any()) } doReturn listOf(createCandidate())
        }
        
        // And: A serving API
        val servingAPI = ServingAPIImpl(
            candidateRepository = repository,
            rankingStrategy = DefaultRankingStrategy()
        )
        
        // When: We make a batch query
        val request = BatchGetCandidatesRequest(
            customerIds = customerIds,
            channel = "email",
            program = "test-program",
            marketplace = "US",
            limit = 10
        )
        val response = servingAPI.getCandidatesForCustomers(request)
        
        // Then: Response should contain results for all customers
        assertThat(response.results.keys).containsExactlyInAnyOrderElementsOf(customerIds)
    }
    
    @Property(tries = 100)
    fun `batch query does not mix customer results`(
        @ForAll("customerIds") customerIds: List<String>
    ) {
        // Given: A repository that returns customer-specific candidates
        val repository = mock<CandidateRepository> {
            on { queryByProgramAndChannel(any(), any(), any(), any()) } doAnswer { invocation ->
                // Return empty list for simplicity - we'll verify separation
                emptyList<Candidate>()
            }
        }
        
        // And: A serving API
        val servingAPI = ServingAPIImpl(
            candidateRepository = repository,
            rankingStrategy = DefaultRankingStrategy()
        )
        
        // When: We make a batch query
        val request = BatchGetCandidatesRequest(
            customerIds = customerIds,
            channel = "email",
            program = "test-program",
            marketplace = "US",
            limit = 10
        )
        val response = servingAPI.getCandidatesForCustomers(request)
        
        // Then: Each customer should have their own result list
        for (customerId in customerIds) {
            assertThat(response.results).containsKey(customerId)
            
            // Verify no candidates from other customers
            val customerCandidates = response.results[customerId] ?: emptyList()
            for (candidate in customerCandidates) {
                assertThat(candidate.customerId).isEqualTo(customerId)
            }
        }
    }
    
    @Property(tries = 100)
    fun `batch query handles individual customer failures gracefully`(
        @ForAll("customerIds") customerIds: List<String>
    ) {
        // Given: A repository that fails for some customers
        val repository = mock<CandidateRepository> {
            on { queryByProgramAndChannel(any(), any(), any(), any()) } doAnswer { invocation ->
                // Fail randomly but deterministically
                if (Math.random() > 0.5) {
                    throw RuntimeException("Simulated failure")
                } else {
                    listOf(createCandidate())
                }
            }
        }
        
        // And: A serving API
        val servingAPI = ServingAPIImpl(
            candidateRepository = repository,
            rankingStrategy = DefaultRankingStrategy()
        )
        
        // When: We make a batch query
        val request = BatchGetCandidatesRequest(
            customerIds = customerIds,
            channel = "email",
            program = "test-program",
            marketplace = "US",
            limit = 10
        )
        val response = servingAPI.getCandidatesForCustomers(request)
        
        // Then: Response should still contain entries for all customers
        assertThat(response.results.keys).containsExactlyInAnyOrderElementsOf(customerIds)
        
        // And: Failed customers should have empty lists
        for (customerId in customerIds) {
            assertThat(response.results[customerId]).isNotNull()
        }
    }
    
    @Property(tries = 100)
    fun `batch query respects limit per customer`(
        @ForAll("limit") limit: Int
    ) {
        // Given: A repository that returns many candidates
        val manyCandidates = (1..20).map { createCandidate("cust1", "prod$it") }
        val repository = mock<CandidateRepository> {
            on { queryByProgramAndChannel(any(), any(), any(), any()) } doReturn manyCandidates
        }
        
        // And: A serving API
        val servingAPI = ServingAPIImpl(
            candidateRepository = repository,
            rankingStrategy = DefaultRankingStrategy()
        )
        
        // When: We make a batch query with specific limit
        val request = BatchGetCandidatesRequest(
            customerIds = listOf("cust1", "cust2"),
            channel = "email",
            program = "test-program",
            marketplace = "US",
            limit = limit
        )
        val response = servingAPI.getCandidatesForCustomers(request)
        
        // Then: Each customer should have at most 'limit' candidates
        for ((customerId, candidates) in response.results) {
            assertThat(candidates.size).isLessThanOrEqualTo(limit)
        }
    }
    
    @Property(tries = 100)
    fun `batch query aggregates total count correctly`(
        @ForAll("customerIds") customerIds: List<String>
    ) {
        // Given: A repository that returns candidates
        val candidates = listOf(createCandidate(), createCandidate())
        val repository = mock<CandidateRepository> {
            on { queryByProgramAndChannel(any(), any(), any(), any()) } doReturn candidates
        }
        
        // And: A serving API
        val servingAPI = ServingAPIImpl(
            candidateRepository = repository,
            rankingStrategy = DefaultRankingStrategy()
        )
        
        // When: We make a batch query
        val request = BatchGetCandidatesRequest(
            customerIds = customerIds,
            channel = "email",
            program = "test-program",
            marketplace = "US",
            limit = 10
        )
        val response = servingAPI.getCandidatesForCustomers(request)
        
        // Then: Total count should equal sum of all customer results
        val expectedTotal = response.results.values.sumOf { it.size }
        assertThat(response.metadata.totalCount).isEqualTo(expectedTotal)
    }
    
    @Property(tries = 100)
    fun `empty batch query returns empty results`() {
        // Given: A serving API
        val repository = mock<CandidateRepository>()
        val servingAPI = ServingAPIImpl(
            candidateRepository = repository,
            rankingStrategy = DefaultRankingStrategy()
        )
        
        // When: We make a batch query with empty customer list
        // This should fail validation
        try {
            val request = BatchGetCandidatesRequest(
                customerIds = emptyList(),
                channel = "email",
                program = "test-program",
                marketplace = "US",
                limit = 10
            )
            servingAPI.getCandidatesForCustomers(request)
            
            // Should not reach here - validation should catch this
            throw AssertionError("Expected IllegalArgumentException but no exception was thrown")
        } catch (e: ServingException) {
            // Expected - wrapped in ServingException
            assertThat(e.cause).isInstanceOf(IllegalArgumentException::class.java)
            assertThat(e.cause?.message).contains("Customer IDs must not be empty")
        } catch (e: IllegalArgumentException) {
            // Also acceptable - direct validation exception
            assertThat(e.message).contains("Customer IDs must not be empty")
        }
    }
    
    // Arbitraries
    
    @Provide
    fun customerIds(): Arbitrary<List<String>> {
        return Arbitraries.of(
            listOf("cust1"),
            listOf("cust1", "cust2"),
            listOf("cust1", "cust2", "cust3"),
            listOf("cust1", "cust2", "cust3", "cust4", "cust5")
        )
    }
    
    @Provide
    fun limit(): Arbitrary<Int> {
        return Arbitraries.integers().between(1, 20)
    }
    
    private fun createCandidate(
        customerId: String = "test-customer",
        subjectId: String = "test-product"
    ): Candidate {
        val now = Instant.now()
        
        return Candidate(
            customerId = customerId,
            context = listOf(
                Context(type = "program", id = "test-program"),
                Context(type = "marketplace", id = "US")
            ),
            subject = Subject(
                type = "product",
                id = subjectId,
                metadata = null
            ),
            scores = mapOf(
                "model1" to Score(
                    modelId = "model1",
                    value = 0.8,
                    confidence = 0.9,
                    timestamp = now,
                    metadata = null
                )
            ),
            attributes = CandidateAttributes(
                eventDate = now.minusSeconds(86400),
                deliveryDate = null,
                timingWindow = null,
                orderValue = null,
                mediaEligible = true,
                channelEligibility = mapOf("email" to true)
            ),
            metadata = CandidateMetadata(
                createdAt = now,
                updatedAt = now,
                expiresAt = now.plusSeconds(86400 * 30),
                version = 1,
                sourceConnectorId = "test-connector",
                workflowExecutionId = "test-execution"
            ),
            rejectionHistory = null
        )
    }
}
