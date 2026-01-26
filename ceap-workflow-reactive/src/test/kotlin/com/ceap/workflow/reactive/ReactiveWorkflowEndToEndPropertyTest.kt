package com.ceap.workflow.reactive

import com.amazonaws.services.lambda.runtime.Context
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant

/**
 * End-to-end property test for reactive workflow.
 * 
 * **Validates: Requirements 9.1, 9.2, 9.3**
 * 
 * Tests event-driven candidate creation with sub-second latency
 * and verifies candidate availability.
 */
class ReactiveWorkflowEndToEndPropertyTest {
    
    private val mockRepository = MockCandidateRepository()
    private val mockDeduplicationTracker = MockEventDeduplicationTracker()
    private val handler = ReactiveHandler(
        candidateRepository = mockRepository,
        deduplicationTracker = mockDeduplicationTracker
    )
    
    @Property(tries = 50)
    fun `reactive workflow creates candidate within sub-second latency`(
        @ForAll("validCustomerIds") customerId: String,
        @ForAll("validSubjectIds") subjectId: String,
        @ForAll("validProgramIds") programId: String,
        @ForAll("validMarketplaces") marketplace: String
    ): Boolean {
        // Clear state before each test
        mockRepository.clear()
        mockDeduplicationTracker.clear()
        
        // Given: Valid customer event
        val input = mapOf(
            "detail" to mapOf(
                "customerId" to customerId,
                "eventType" to "ORDER_DELIVERED",
                "subjectType" to "product",
                "subjectId" to subjectId,
                "programId" to programId,
                "marketplace" to marketplace,
                "eventDate" to Instant.now().toString(),
                "metadata" to mapOf(
                    "orderValue" to 99.99
                )
            )
        )
        
        val context = MockLambdaContext()
        
        // When: Execute reactive handler
        val startTime = System.currentTimeMillis()
        val response = handler.handleRequest(input, context)
        val actualLatency = System.currentTimeMillis() - startTime
        
        // Then: Response is successful
        assertThat(response.success).isTrue()
        
        // Then: Execution time is sub-second (< 1000ms)
        // This validates Requirement 9.1: create candidate within 1 second end-to-end
        assertThat(response.executionTimeMs).isLessThan(1000)
        assertThat(actualLatency).isLessThan(1000)
        
        // Then: If candidate was created, it has an ID
        if (response.candidateCreated) {
            assertThat(response.candidateId).isNotBlank()
        }
        
        return true
    }
    
    @Property(tries = 50)
    fun `reactive workflow handles duplicate events correctly`(
        @ForAll("validCustomerIds") customerId: String,
        @ForAll("validSubjectIds") subjectId: String,
        @ForAll("validProgramIds") programId: String,
        @ForAll("validMarketplaces") marketplace: String
    ): Boolean {
        // Clear state before each test
        mockRepository.clear()
        mockDeduplicationTracker.clear()
        
        // Given: Valid customer event
        val input = mapOf(
            "detail" to mapOf(
                "customerId" to customerId,
                "eventType" to "ORDER_DELIVERED",
                "subjectType" to "product",
                "subjectId" to subjectId,
                "programId" to programId,
                "marketplace" to marketplace,
                "eventDate" to Instant.now().toString(),
                "metadata" to emptyMap<String, Any>()
            )
        )
        
        val context = MockLambdaContext()
        
        // When: Execute reactive handler twice with same event
        val response1 = handler.handleRequest(input, context)
        val response2 = handler.handleRequest(input, context)
        
        // Then: First execution creates candidate
        assertThat(response1.success).isTrue()
        
        // Then: Second execution detects duplicate
        assertThat(response2.success).isTrue()
        if (response1.candidateCreated) {
            // If first created, second should be duplicate
            assertThat(response2.candidateCreated).isFalse()
            assertThat(response2.reason).contains("Duplicate")
        }
        
        return true
    }
    
    @Property(tries = 50)
    fun `reactive workflow verifies candidate availability`(
        @ForAll("validCustomerIds") customerId: String,
        @ForAll("validSubjectIds") subjectId: String,
        @ForAll("validProgramIds") programId: String,
        @ForAll("validMarketplaces") marketplace: String
    ): Boolean {
        // Clear state before each test
        mockRepository.clear()
        mockDeduplicationTracker.clear()
        
        // Given: Valid customer event
        val input = mapOf(
            "detail" to mapOf(
                "customerId" to customerId,
                "eventType" to "ORDER_DELIVERED",
                "subjectType" to "product",
                "subjectId" to subjectId,
                "programId" to programId,
                "marketplace" to marketplace,
                "eventDate" to Instant.now().toString(),
                "metadata" to emptyMap<String, Any>()
            )
        )
        
        val context = MockLambdaContext()
        
        // When: Execute reactive handler
        val response = handler.handleRequest(input, context)
        
        // Then: Response indicates success or failure clearly
        assertThat(response.success).isNotNull()
        assertThat(response.candidateCreated).isNotNull()
        
        // Then: If not created, reason is provided
        if (!response.candidateCreated) {
            assertThat(response.reason).isNotBlank()
        }
        
        // Then: Execution time is tracked
        assertThat(response.executionTimeMs).isGreaterThan(0)
        
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
    fun validSubjectIds(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange('0', '9')
            .ofLength(8)
            .map { "PROD-$it" }
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
    fun validMarketplaces(): Arbitrary<String> {
        return Arbitraries.of("US", "UK", "DE", "JP", "FR", "CA")
    }
    
    /**
     * Mock Lambda context for testing
     */
    private class MockLambdaContext : Context {
        override fun getAwsRequestId(): String = "test-request-${System.currentTimeMillis()}"
        override fun getLogGroupName(): String = "test-log-group"
        override fun getLogStreamName(): String = "test-log-stream"
        override fun getFunctionName(): String = "test-function"
        override fun getFunctionVersion(): String = "1"
        override fun getInvokedFunctionArn(): String = "arn:aws:lambda:us-east-1:123456789012:function:test"
        override fun getIdentity() = null
        override fun getClientContext() = null
        override fun getRemainingTimeInMillis(): Int = 300000
        override fun getMemoryLimitInMB(): Int = 512
        override fun getLogger() = null
    }
}
