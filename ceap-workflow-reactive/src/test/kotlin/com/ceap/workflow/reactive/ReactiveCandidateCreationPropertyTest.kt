package com.ceap.workflow.reactive

import net.jqwik.api.*
import net.jqwik.api.constraints.AlphaChars
import net.jqwik.api.constraints.NotBlank
import net.jqwik.api.constraints.Positive
import org.assertj.core.api.Assertions.assertThat
import com.ceap.model.Candidate
import com.ceap.storage.CandidateRepository
import java.time.Instant

/**
 * Property-based test for reactive candidate creation.
 * 
 * **Property 28: Reactive candidate creation**
 * 
 * *For any* eligible customer event in reactive mode, a candidate must be created 
 * and stored, and the candidate must be available for serving immediately after storage.
 * 
 * **Validates: Requirements 9.4**
 */
class ReactiveCandidateCreationPropertyTest {
    
    private val mockRepository = MockCandidateRepository()
    private val mockDeduplicationTracker = MockEventDeduplicationTracker()
    private val handler = ReactiveHandler(
        candidateRepository = mockRepository,
        deduplicationTracker = mockDeduplicationTracker
    )
    
    @Property(tries = 50)
    @Label("Property 28: Reactive candidate creation - eligible events create stored candidates")
    fun eligibleEventCreatesStoredCandidate(
        @ForAll @NotBlank @AlphaChars customerId: String,
        @ForAll @NotBlank @AlphaChars subjectId: String,
        @ForAll @NotBlank @AlphaChars programId: String,
        @ForAll @NotBlank @AlphaChars marketplace: String
    ): Boolean {
        // Clear state before each test
        mockRepository.clear()
        mockDeduplicationTracker.clear()
        
        // Given: An eligible customer event
        val event = mapOf(
            "detail" to mapOf(
                "customerId" to customerId,
                "eventType" to "OrderDelivered",
                "subjectType" to "product",
                "subjectId" to subjectId,
                "programId" to programId,
                "marketplace" to marketplace,
                "eventDate" to Instant.now().toString(),
                "metadata" to mapOf<String, Any>()
            )
        )
        
        // When: The reactive workflow processes the event
        val response = try {
            handler.handleRequest(event, createMockContext())
        } catch (e: Exception) {
            // If processing fails, the property doesn't hold
            return false
        }
        
        // Then: A candidate must be created
        if (!response.success || !response.candidateCreated) {
            return false
        }
        
        // And: The candidate must have an ID
        if (response.candidateId == null) {
            return false
        }
        
        // And: The candidate must be stored in the repository
        return mockRepository.size() == 1
    }
    
    @Property(tries = 50)
    @Label("Property 28: Reactive candidate creation - candidates are immediately queryable")
    fun candidatesAreImmediatelyQueryable(
        @ForAll @NotBlank @AlphaChars customerId: String,
        @ForAll @NotBlank @AlphaChars subjectId: String,
        @ForAll @NotBlank @AlphaChars programId: String,
        @ForAll @NotBlank @AlphaChars marketplace: String
    ): Boolean {
        // Clear state before each test
        mockRepository.clear()
        mockDeduplicationTracker.clear()
        
        // Given: An eligible customer event that creates a candidate
        val event = mapOf(
            "detail" to mapOf(
                "customerId" to customerId,
                "eventType" to "OrderDelivered",
                "subjectType" to "product",
                "subjectId" to subjectId,
                "programId" to programId,
                "marketplace" to marketplace,
                "eventDate" to Instant.now().toString(),
                "metadata" to mapOf<String, Any>()
            )
        )
        
        val response = try {
            handler.handleRequest(event, createMockContext())
        } catch (e: Exception) {
            return false
        }
        
        if (!response.candidateCreated) {
            return false
        }
        
        // When: We query for the candidate immediately after creation
        val retrieved = mockRepository.get(customerId, programId, marketplace, "product", subjectId)
        
        // Then: The candidate must be retrievable
        return retrieved != null && retrieved.customerId == customerId
    }
    
    @Property(tries = 50)
    @Label("Property 28: Reactive candidate creation - execution time is sub-second")
    fun executionTimeIsSubSecond(
        @ForAll @NotBlank @AlphaChars customerId: String,
        @ForAll @NotBlank @AlphaChars subjectId: String,
        @ForAll @NotBlank @AlphaChars programId: String,
        @ForAll @NotBlank @AlphaChars marketplace: String
    ): Boolean {
        // Clear state before each test
        mockRepository.clear()
        mockDeduplicationTracker.clear()
        
        // Given: An eligible customer event
        val event = mapOf(
            "detail" to mapOf(
                "customerId" to customerId,
                "eventType" to "OrderDelivered",
                "subjectType" to "product",
                "subjectId" to subjectId,
                "programId" to programId,
                "marketplace" to marketplace,
                "eventDate" to Instant.now().toString(),
                "metadata" to mapOf<String, Any>()
            )
        )
        
        // When: The reactive workflow processes the event
        val response = try {
            handler.handleRequest(event, createMockContext())
        } catch (e: Exception) {
            return false
        }
        
        // Then: Execution time must be less than 1 second (1000ms)
        // This validates Requirement 9.1: create candidate within 1 second end-to-end
        return response.executionTimeMs < 1000
    }
    
    private fun createMockContext(): com.amazonaws.services.lambda.runtime.Context {
        return object : com.amazonaws.services.lambda.runtime.Context {
            override fun getAwsRequestId() = "test-request-${System.currentTimeMillis()}"
            override fun getLogGroupName() = "test-log-group"
            override fun getLogStreamName() = "test-log-stream"
            override fun getFunctionName() = "test-function"
            override fun getFunctionVersion() = "1"
            override fun getInvokedFunctionArn() = "arn:aws:lambda:us-east-1:123456789:function:test"
            override fun getIdentity() = null
            override fun getClientContext() = null
            override fun getRemainingTimeInMillis() = 60000
            override fun getMemoryLimitInMB() = 1024
            override fun getLogger() = object : com.amazonaws.services.lambda.runtime.LambdaLogger {
                override fun log(message: String?) {
                    println(message)
                }
                override fun log(message: ByteArray?) {
                    message?.let { println(String(it)) }
                }
            }
        }
    }
}
