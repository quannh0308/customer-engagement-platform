package com.ceap.serving

import com.ceap.model.*
import com.ceap.serving.v1.*
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * Property 60: Shadow mode isolation
 * 
 * **Validates: Requirements 20.4**
 * 
 * For any request in shadow mode, v2 processing must execute in parallel with v1,
 * but v2 results must not affect the v1 response returned to the client.
 */
class ShadowModeIsolationPropertyTest {
    
    @Property(tries = 100)
    fun `Shadow mode V2 execution does not affect V1 response`(
        @ForAll("v1Requests") request: V1GetCandidatesRequest,
        @ForAll("v2Candidates") v2Candidate: Candidate
    ) {
        // Setup: Create mock V1 and V2 backends that return different results
        val v1Candidate = createV1Candidate(request.customerId, "v1-subject")
        val v2CandidateModified = v2Candidate.copy(
            customerId = request.customerId,
            subject = v2Candidate.subject.copy(id = "v2-subject")
        )
        
        val v1API = createMockServingAPI(listOf(v1Candidate))
        val v2API = createMockServingAPI(listOf(v2CandidateModified))
        
        val v1Adapter = V1ApiAdapter(v1API)
        val shadowAdapter = V1ShadowModeAdapter(
            v1Adapter = v1Adapter,
            v2ServingAPI = v2API,
            shadowModeEnabled = true
        )
        
        // Execute: Call with shadow mode enabled
        val response = shadowAdapter.getCandidatesForCustomer(request)
        
        // Wait a bit for shadow execution to complete
        Thread.sleep(100)
        
        // Verify: Response contains V1 data, not V2 data
        if (response.candidates.isNotEmpty()) {
            response.candidates.forEach { candidate ->
                // V1 candidates have "v1" in subject ID
                assertThat(candidate.subjectId).contains("v1")
                assertThat(candidate.subjectId).doesNotContain("v2")
            }
        }
    }
    
    @Property(tries = 100)
    fun `Shadow mode V2 errors do not cause V1 request to fail`(
        @ForAll("v1Requests") request: V1GetCandidatesRequest,
        @ForAll("v2Candidates") v2Candidate: Candidate
    ) {
        // Setup: Create V1 backend that works and V2 backend that fails
        val v1Candidate = createV1Candidate(request.customerId, "v1-subject")
        val v1API = createMockServingAPI(listOf(v1Candidate))
        
        // V2 backend that always fails
        val failingV2API = object : ServingAPI {
            override fun getCandidatesForCustomer(request: GetCandidatesRequest): GetCandidatesResponse {
                throw RuntimeException("V2 backend failure")
            }
            override fun getCandidatesForCustomers(request: BatchGetCandidatesRequest) = throw RuntimeException("V2 failure")
            override fun deleteCandidate(request: DeleteCandidateRequest) = throw RuntimeException("V2 failure")
            override fun markCandidateConsumed(request: MarkConsumedRequest) = throw RuntimeException("V2 failure")
            override fun refreshCandidate(request: RefreshCandidateRequest) = throw RuntimeException("V2 failure")
        }
        
        val v1Adapter = V1ApiAdapter(v1API)
        val shadowAdapter = V1ShadowModeAdapter(
            v1Adapter = v1Adapter,
            v2ServingAPI = failingV2API,
            shadowModeEnabled = true
        )
        
        // Execute: Call with shadow mode enabled (V2 will fail)
        val response = shadowAdapter.getCandidatesForCustomer(request)
        
        // Wait for shadow execution to complete (and fail)
        Thread.sleep(100)
        
        // Verify: V1 request succeeded despite V2 failure
        assertThat(response).isNotNull
        if (response.candidates.isNotEmpty()) {
            response.candidates.forEach { candidate ->
                assertThat(candidate.subjectId).contains("v1")
            }
        }
    }
    
    @Property(tries = 50)
    fun `Shadow mode can be enabled and disabled dynamically`(
        @ForAll("v1Requests") request: V1GetCandidatesRequest,
        @ForAll shadowModeEnabled: Boolean,
        @ForAll("v2Candidates") v2Candidate: Candidate
    ) {
        // Setup
        val candidate = v2Candidate.copy(customerId = request.customerId)
        val servingAPI = createMockServingAPI(listOf(candidate))
        
        val v1Adapter = V1ApiAdapter(servingAPI)
        val shadowAdapter = V1ShadowModeAdapter(
            v1Adapter = v1Adapter,
            v2ServingAPI = servingAPI,
            shadowModeEnabled = false // Start disabled
        )
        
        // Execute: Set shadow mode state
        shadowAdapter.setShadowModeEnabled(shadowModeEnabled)
        
        // Verify: Shadow mode state matches
        assertThat(shadowAdapter.isShadowModeEnabled()).isEqualTo(shadowModeEnabled)
        
        // Execute request
        val response = shadowAdapter.getCandidatesForCustomer(request)
        
        // Verify: Request succeeds regardless of shadow mode state
        assertThat(response).isNotNull
    }
    
    @Property(tries = 50)
    fun `Shadow mode V2 execution happens asynchronously`(
        @ForAll("v1Requests") request: V1GetCandidatesRequest,
        @ForAll("v2Candidates") v2Candidate: Candidate
    ) {
        // Setup: Create V2 backend that tracks execution
        val v2ExecutionCount = AtomicInteger(0)
        val candidate = v2Candidate.copy(customerId = request.customerId)
        
        val v1API = createMockServingAPI(listOf(candidate))
        
        val v2API = object : ServingAPI by v1API {
            override fun getCandidatesForCustomer(request: GetCandidatesRequest): GetCandidatesResponse {
                v2ExecutionCount.incrementAndGet()
                Thread.sleep(50) // Simulate slow V2 processing
                return v1API.getCandidatesForCustomer(request)
            }
        }
        
        val v1Adapter = V1ApiAdapter(v1API)
        val shadowAdapter = V1ShadowModeAdapter(
            v1Adapter = v1Adapter,
            v2ServingAPI = v2API,
            shadowModeEnabled = true
        )
        
        // Execute: Call with shadow mode
        val startTime = System.currentTimeMillis()
        val response = shadowAdapter.getCandidatesForCustomer(request)
        val v1Latency = System.currentTimeMillis() - startTime
        
        // Verify: V1 response returned quickly (not blocked by V2)
        assertThat(v1Latency).isLessThan(100) // Should be much faster than V2's 50ms sleep
        assertThat(response).isNotNull
        
        // Wait for V2 to complete
        Thread.sleep(200)
        
        // Verify: V2 was executed asynchronously
        assertThat(v2ExecutionCount.get()).isGreaterThan(0)
    }
    
    @Property(tries = 50)
    fun `Shadow mode batch requests isolate V2 from V1`(
        @ForAll("v1BatchRequests") request: V1BatchGetCandidatesRequest,
        @ForAll("v2Candidates") v2Candidate: Candidate
    ) {
        // Setup: Create different data for V1 and V2
        val v1Candidates = request.customerIds.map { customerId ->
            createV1Candidate(customerId, "v1-subject-$customerId")
        }
        
        val v2Candidates = request.customerIds.map { customerId ->
            v2Candidate.copy(
                customerId = customerId,
                subject = v2Candidate.subject.copy(id = "v2-subject-$customerId")
            )
        }
        
        val v1API = createMockServingAPI(v1Candidates)
        val v2API = createMockServingAPI(v2Candidates)
        
        val v1Adapter = V1ApiAdapter(v1API)
        val shadowAdapter = V1ShadowModeAdapter(
            v1Adapter = v1Adapter,
            v2ServingAPI = v2API,
            shadowModeEnabled = true
        )
        
        // Execute
        val response = shadowAdapter.getCandidatesForCustomers(request)
        
        // Wait for shadow execution
        Thread.sleep(100)
        
        // Verify: Response contains V1 data only
        response.results.values.flatten().forEach { candidate ->
            assertThat(candidate.subjectId).contains("v1")
            assertThat(candidate.subjectId).doesNotContain("v2")
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
            Arbitraries.integers().between(1, 20)
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
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20).list().ofMinSize(1).ofMaxSize(5),
            Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(10).optional(),
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(15).optional(),
            Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(5),
            Arbitraries.integers().between(1, 10)
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
                scores = null,
                attributes = CandidateAttributes(
                    eventDate = now,
                    deliveryDate = null,
                    timingWindow = null,
                    orderValue = null,
                    mediaEligible = true,
                    channelEligibility = mapOf("email" to true)
                ),
                metadata = CandidateMetadata(
                    createdAt = now,
                    updatedAt = now,
                    expiresAt = now.plusSeconds(86400),
                    version = 1,
                    sourceConnectorId = "test",
                    workflowExecutionId = "test"
                ),
                rejectionHistory = null
            )
        }
    }
    
    // Helper methods
    
    private fun createMockServingAPI(candidates: List<Candidate>): ServingAPI {
        return object : ServingAPI {
            override fun getCandidatesForCustomer(request: GetCandidatesRequest): GetCandidatesResponse {
                return GetCandidatesResponse(
                    candidates = candidates,
                    metadata = ResponseMetadata(
                        totalCount = candidates.size,
                        filteredCount = candidates.size
                    ),
                    latencyMs = 10
                )
            }
            override fun getCandidatesForCustomers(request: BatchGetCandidatesRequest): BatchGetCandidatesResponse {
                val results = request.customerIds.associateWith { customerId ->
                    candidates.filter { it.customerId == customerId }
                }
                return BatchGetCandidatesResponse(
                    results = results,
                    metadata = ResponseMetadata(
                        totalCount = candidates.size,
                        filteredCount = candidates.size
                    ),
                    latencyMs = 20
                )
            }
            override fun deleteCandidate(request: DeleteCandidateRequest) = throw NotImplementedError()
            override fun markCandidateConsumed(request: MarkConsumedRequest) = throw NotImplementedError()
            override fun refreshCandidate(request: RefreshCandidateRequest) = throw NotImplementedError()
        }
    }
    
    private fun createV1Candidate(customerId: String, subjectId: String): Candidate {
        val now = Instant.now()
        return Candidate(
            customerId = customerId,
            context = listOf(
                Context(type = "marketplace", id = "US"),
                Context(type = "program", id = "test-program")
            ),
            subject = Subject(
                type = "product",
                id = subjectId,
                metadata = null
            ),
            scores = null,
            attributes = CandidateAttributes(
                eventDate = now,
                deliveryDate = null,
                timingWindow = null,
                orderValue = null,
                mediaEligible = true,
                channelEligibility = mapOf("email" to true)
            ),
            metadata = CandidateMetadata(
                createdAt = now,
                updatedAt = now,
                expiresAt = now.plusSeconds(86400),
                version = 1,
                sourceConnectorId = "test",
                workflowExecutionId = "test"
            ),
            rejectionHistory = null
        )
    }
    
    private fun createV2Candidates(
        customerId: String,
        marketplace: String,
        program: String?,
        suffix: String
    ): List<Candidate> {
        val programId = program ?: "default-program"
        val now = Instant.now()
        
        return listOf(
            Candidate(
                customerId = customerId,
                context = listOf(
                    Context(type = "marketplace", id = marketplace),
                    Context(type = "program", id = programId)
                ),
                subject = Subject(
                    type = "product",
                    id = "PROD-${customerId.take(5)}-$suffix",
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
                        "in-app" to true,
                        "push" to false
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
        )
    }
}
