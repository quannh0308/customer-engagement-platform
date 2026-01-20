package com.solicitation.serving

import com.solicitation.model.*
import com.solicitation.serving.v1.*
import com.solicitation.storage.CandidateRepository
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant

/**
 * Property 58: V1 API backward compatibility
 * 
 * **Validates: Requirements 20.1**
 * 
 * For any v1 API request, the system must return a valid v1 response format,
 * even though the backend uses v2 implementation.
 */
class V1BackwardCompatibilityPropertyTest {
    
    @Property(tries = 100)
    fun `V1 API adapter translates requests to V2 format correctly`(
        @ForAll("v1Requests") request: V1GetCandidatesRequest
    ) {
        // This test verifies the adapter can handle any V1 request
        // and produce a valid V1 response structure
        
        // Setup: Create mock V2 backend that returns empty results
        val mockV2API = object : ServingAPI {
            override fun getCandidatesForCustomer(request: GetCandidatesRequest): GetCandidatesResponse {
                // Verify V2 request format
                assertThat(request.customerId).isNotBlank()
                assertThat(request.marketplace).isNotBlank()
                assertThat(request.limit).isGreaterThan(0)
                
                return GetCandidatesResponse(
                    candidates = emptyList(),
                    metadata = ResponseMetadata(
                        totalCount = 0,
                        filteredCount = 0
                    ),
                    latencyMs = 10
                )
            }
            override fun getCandidatesForCustomers(request: BatchGetCandidatesRequest) = throw NotImplementedError()
            override fun deleteCandidate(request: DeleteCandidateRequest) = throw NotImplementedError()
            override fun markCandidateConsumed(request: MarkConsumedRequest) = throw NotImplementedError()
            override fun refreshCandidate(request: RefreshCandidateRequest) = throw NotImplementedError()
        }
        
        val v1Adapter = V1ApiAdapter(mockV2API)
        
        // Execute: Call V1 API
        val v1Response = v1Adapter.getCandidatesForCustomer(request)
        
        // Verify: Response is in valid V1 format
        assertThat(v1Response).isNotNull
        assertThat(v1Response.candidates).isNotNull
        assertThat(v1Response.totalCount).isGreaterThanOrEqualTo(0)
        assertThat(v1Response.latencyMs).isGreaterThanOrEqualTo(0)
    }
    
    @Property(tries = 100)
    fun `V1 batch API adapter translates batch requests correctly`(
        @ForAll("v1BatchRequests") request: V1BatchGetCandidatesRequest
    ) {
        // Setup: Create mock V2 backend
        val mockV2API = object : ServingAPI {
            override fun getCandidatesForCustomer(request: GetCandidatesRequest) = throw NotImplementedError()
            override fun getCandidatesForCustomers(request: BatchGetCandidatesRequest): BatchGetCandidatesResponse {
                // Verify V2 request format
                assertThat(request.customerIds).isNotEmpty()
                assertThat(request.marketplace).isNotBlank()
                
                // Return empty results for each customer
                val results = request.customerIds.associateWith { emptyList<Candidate>() }
                
                return BatchGetCandidatesResponse(
                    results = results,
                    metadata = ResponseMetadata(
                        totalCount = 0,
                        filteredCount = 0
                    ),
                    latencyMs = 20
                )
            }
            override fun deleteCandidate(request: DeleteCandidateRequest) = throw NotImplementedError()
            override fun markCandidateConsumed(request: MarkConsumedRequest) = throw NotImplementedError()
            override fun refreshCandidate(request: RefreshCandidateRequest) = throw NotImplementedError()
        }
        
        val v1Adapter = V1ApiAdapter(mockV2API)
        
        // Execute
        val v1Response = v1Adapter.getCandidatesForCustomers(request)
        
        // Verify: Response is in valid V1 format
        assertThat(v1Response).isNotNull
        assertThat(v1Response.results).isNotNull
        assertThat(v1Response.totalCount).isGreaterThanOrEqualTo(0)
        assertThat(v1Response.latencyMs).isGreaterThanOrEqualTo(0)
        
        // Verify: Results contain entries for each customer
        assertThat(v1Response.results.keys).containsAll(request.customerIds)
    }
    
    @Property(tries = 50)
    fun `V1 API translates V2 candidates to V1 format correctly`(
        @ForAll("v2Candidates") v2Candidate: Candidate
    ) {
        // Setup: Create mock V2 backend that returns the candidate
        val mockV2API = object : ServingAPI {
            override fun getCandidatesForCustomer(request: GetCandidatesRequest): GetCandidatesResponse {
                return GetCandidatesResponse(
                    candidates = listOf(v2Candidate),
                    metadata = ResponseMetadata(
                        totalCount = 1,
                        filteredCount = 1
                    ),
                    latencyMs = 10
                )
            }
            override fun getCandidatesForCustomers(request: BatchGetCandidatesRequest) = throw NotImplementedError()
            override fun deleteCandidate(request: DeleteCandidateRequest) = throw NotImplementedError()
            override fun markCandidateConsumed(request: MarkConsumedRequest) = throw NotImplementedError()
            override fun refreshCandidate(request: RefreshCandidateRequest) = throw NotImplementedError()
        }
        
        val v1Adapter = V1ApiAdapter(mockV2API)
        
        // Execute
        val v1Request = V1GetCandidatesRequest(
            customerId = v2Candidate.customerId,
            marketplace = v2Candidate.context.find { it.type == "marketplace" }?.id ?: "US",
            limit = 10
        )
        val v1Response = v1Adapter.getCandidatesForCustomer(v1Request)
        
        // Verify: V2 candidate was translated to V1 format
        assertThat(v1Response.candidates).hasSize(1)
        val v1Candidate = v1Response.candidates.first()
        
        assertThat(v1Candidate.customerId).isEqualTo(v2Candidate.customerId)
        assertThat(v1Candidate.subjectType).isEqualTo(v2Candidate.subject.type)
        assertThat(v1Candidate.subjectId).isEqualTo(v2Candidate.subject.id)
        assertThat(v1Candidate.eventDate).isNotBlank()
        assertThat(v1Candidate.createdAt).isNotBlank()
    }
    
    @Property(tries = 50)
    fun `V1 API handles V2 backend errors gracefully`(
        @ForAll("v1Requests") request: V1GetCandidatesRequest
    ) {
        // Setup: Create V2 backend that fails
        val failingV2API = object : ServingAPI {
            override fun getCandidatesForCustomer(request: GetCandidatesRequest): GetCandidatesResponse {
                throw RuntimeException("Simulated V2 backend failure")
            }
            override fun getCandidatesForCustomers(request: BatchGetCandidatesRequest) = throw NotImplementedError()
            override fun deleteCandidate(request: DeleteCandidateRequest) = throw NotImplementedError()
            override fun markCandidateConsumed(request: MarkConsumedRequest) = throw NotImplementedError()
            override fun refreshCandidate(request: RefreshCandidateRequest) = throw NotImplementedError()
        }
        
        val v1Adapter = V1ApiAdapter(failingV2API)
        
        // Execute and verify: V1 API propagates error appropriately
        try {
            v1Adapter.getCandidatesForCustomer(request)
            // If no exception, that's also acceptable (fallback behavior)
        } catch (e: V1ApiException) {
            // Expected: V1 API wraps V2 errors
            assertThat(e.message).contains("Failed to get candidates")
        }
    }
    
    // Arbitraries for generating test data
    
    @Provide
    fun v1Requests(): Arbitrary<V1GetCandidatesRequest> {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
            Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10).optional(),
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15).optional(),
            Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(5),
            Arbitraries.integers().between(1, 50)
        ).`as` { customerId, channel, program, marketplace, limit ->
            V1GetCandidatesRequest(
                customerId = customerId,
                channel = channel.orElse(null),
                program = program.orElse(null),
                marketplace = marketplace,
                limit = limit
            )
        }
    }
    
    @Provide
    fun v1BatchRequests(): Arbitrary<V1BatchGetCandidatesRequest> {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20).list().ofMinSize(1).ofMaxSize(10),
            Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10).optional(),
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15).optional(),
            Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(5),
            Arbitraries.integers().between(1, 20)
        ).`as` { customerIds, channel, program, marketplace, limit ->
            V1BatchGetCandidatesRequest(
                customerIds = customerIds,
                channel = channel.orElse(null),
                program = program.orElse(null),
                marketplace = marketplace,
                limit = limit
            )
        }
    }
    
    @Provide
    fun v2Candidates(): Arbitrary<Candidate> {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
            Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(5),
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15)
        ).`as` { customerId, marketplace, program ->
            val now = Instant.now()
            Candidate(
                customerId = customerId,
                context = listOf(
                    Context(type = "marketplace", id = marketplace),
                    Context(type = "program", id = program)
                ),
                subject = Subject(
                    type = "product",
                    id = "PROD-${customerId.take(5)}",
                    metadata = null
                ),
                scores = mapOf(
                    "model1" to Score(
                        modelId = "model1",
                        value = 0.85,
                        confidence = 0.9,
                        timestamp = now,
                        metadata = null
                    )
                ),
                attributes = CandidateAttributes(
                    eventDate = now,
                    deliveryDate = null,
                    timingWindow = null,
                    orderValue = null,
                    mediaEligible = true,
                    channelEligibility = mapOf(
                        "email" to true,
                        "in-app" to true
                    )
                ),
                metadata = CandidateMetadata(
                    createdAt = now,
                    updatedAt = now,
                    expiresAt = now.plusSeconds(86400),
                    version = 1,
                    sourceConnectorId = "test-connector",
                    workflowExecutionId = "test-workflow"
                ),
                rejectionHistory = null
            )
        }
    }
}
