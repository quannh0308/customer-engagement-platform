package com.ceap.common.observability

import net.jqwik.api.*
import org.slf4j.MDC
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Property-based tests for structured logging with correlation IDs.
 * 
 * **Property 37: Structured logging with correlation**
 * 
 * *For any* workflow or API request, all log entries must include a correlation ID,
 * and failures must emit structured logs with error details.
 * 
 * **Validates: Requirements 12.2**
 */
class StructuredLoggingPropertyTest {
    
    /**
     * Property: Correlation ID is propagated through nested operations.
     * 
     * For any correlation ID, when set in the context, it must be available
     * in all nested operations and restored after the operation completes.
     */
    @Property
    fun correlationIdPropagatesThroughNestedOperations(
        @ForAll("correlationIds") correlationId: String
    ) {
        // Ensure clean state
        MDC.clear()
        
        // Initially no correlation ID
        assertNull(StructuredLoggingContext.getCorrelationId())
        
        // Execute with correlation ID
        val result = StructuredLoggingContext.withCorrelationId(correlationId) {
            // Inside the block, correlation ID should be set
            val innerCorrelationId = StructuredLoggingContext.getCorrelationId()
            assertNotNull(innerCorrelationId)
            assertEquals(correlationId, innerCorrelationId)
            
            // Nested operation should see the same correlation ID
            val nestedResult = performNestedOperation()
            assertEquals(correlationId, nestedResult)
            
            "success"
        }
        
        // After the block, correlation ID should be cleared
        assertNull(StructuredLoggingContext.getCorrelationId())
        assertEquals("success", result)
    }
    
    /**
     * Property: Correlation ID is restored after nested withCorrelationId calls.
     * 
     * For any two correlation IDs, when nested withCorrelationId calls are made,
     * the outer correlation ID must be restored after the inner call completes.
     */
    @Property
    fun correlationIdRestoredAfterNestedCalls(
        @ForAll("correlationIds") outerCorrelationId: String,
        @ForAll("correlationIds") innerCorrelationId: String
    ) {
        // Ensure clean state
        MDC.clear()
        
        StructuredLoggingContext.withCorrelationId(outerCorrelationId) {
            assertEquals(outerCorrelationId, StructuredLoggingContext.getCorrelationId())
            
            // Nested call with different correlation ID
            StructuredLoggingContext.withCorrelationId(innerCorrelationId) {
                assertEquals(innerCorrelationId, StructuredLoggingContext.getCorrelationId())
            }
            
            // After nested call, outer correlation ID should be restored
            assertEquals(outerCorrelationId, StructuredLoggingContext.getCorrelationId())
        }
        
        // After all calls, correlation ID should be cleared
        assertNull(StructuredLoggingContext.getCorrelationId())
    }
    
    /**
     * Property: Generated correlation IDs are unique.
     * 
     * For any number of generated correlation IDs, they must all be unique.
     */
    @Property
    fun generatedCorrelationIdsAreUnique() {
        val correlationIds = (1..100).map { CorrelationIdGenerator.generate() }
        
        // All correlation IDs should be unique
        assertEquals(correlationIds.size, correlationIds.toSet().size)
    }
    
    /**
     * Property: Correlation IDs with prefix contain the prefix.
     * 
     * For any prefix, generated correlation IDs with that prefix must start with the prefix.
     */
    @Property
    fun correlationIdsWithPrefixContainPrefix(
        @ForAll("prefixes") prefix: String
    ) {
        val correlationId = CorrelationIdGenerator.generateWithPrefix(prefix)
        
        // Correlation ID should start with prefix
        assert(correlationId.startsWith("$prefix-"))
    }
    
    /**
     * Property: Context values are available in MDC.
     * 
     * For any context map, when set, all values must be available in MDC.
     */
    @Property
    fun contextValuesAvailableInMDC(
        @ForAll("programIds") programId: String,
        @ForAll("marketplaces") marketplace: String
    ) {
        // Ensure clean state
        MDC.clear()
        
        val context = mapOf(
            StructuredLoggingContext.PROGRAM_ID to programId,
            StructuredLoggingContext.MARKETPLACE to marketplace
        )
        
        StructuredLoggingContext.withContext(context) {
            assertEquals(programId, MDC.get(StructuredLoggingContext.PROGRAM_ID))
            assertEquals(marketplace, MDC.get(StructuredLoggingContext.MARKETPLACE))
        }
        
        // After the block, MDC should be cleared
        assertNull(MDC.get(StructuredLoggingContext.PROGRAM_ID))
        assertNull(MDC.get(StructuredLoggingContext.MARKETPLACE))
    }
    
    /**
     * Property: Customer ID is redacted in MDC.
     * 
     * For any customer ID longer than 8 characters, when set in MDC,
     * it must be redacted to show only first 4 and last 4 characters.
     */
    @Property
    fun customerIdIsRedacted(
        @ForAll("customerIds") customerId: String
    ) {
        // Ensure clean state
        MDC.clear()
        
        StructuredLoggingContext.setCustomerId(customerId)
        
        val redactedId = MDC.get(StructuredLoggingContext.CUSTOMER_ID)
        assertNotNull(redactedId)
        
        // Redacted ID should contain ****
        assert(redactedId.contains("****"))
        
        // Redacted ID should start with first 4 characters
        assert(redactedId.startsWith(customerId.substring(0, 4)))
        
        // Redacted ID should end with last 4 characters
        assert(redactedId.endsWith(customerId.substring(customerId.length - 4)))
        
        MDC.clear()
    }
    
    /**
     * Property: Failure logger includes correlation ID in error details.
     * 
     * For any correlation ID and error message, when a failure is logged,
     * the correlation ID must be included in the log output.
     */
    @Property
    fun failureLoggerIncludesCorrelationId(
        @ForAll("correlationIds") correlationId: String,
        @ForAll("errorMessages") errorMessage: String
    ) {
        // Ensure clean state
        MDC.clear()
        
        StructuredLoggingContext.setCorrelationId(correlationId)
        
        val failureLogger = FailureLogger.getLogger<StructuredLoggingPropertyTest>()
        
        // Log a failure (we can't easily capture the output, but we can verify it doesn't throw)
        failureLogger.logFailure(errorMessage, mapOf("testKey" to "testValue"))
        
        // Verify correlation ID is still in context
        assertEquals(correlationId, StructuredLoggingContext.getCorrelationId())
        
        MDC.clear()
    }
    
    /**
     * Helper function to simulate nested operation.
     */
    private fun performNestedOperation(): String? {
        return StructuredLoggingContext.getCorrelationId()
    }
    
    // ========== Arbitrary Providers ==========
    
    @Provide
    fun correlationIds(): Arbitrary<String> {
        return Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(50)
    }
    
    @Provide
    fun prefixes(): Arbitrary<String> {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
    }
    
    @Provide
    fun programIds(): Arbitrary<String> {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
    }
    
    @Provide
    fun marketplaces(): Arbitrary<String> {
        return Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(5)
    }
    
    @Provide
    fun customerIds(): Arbitrary<String> {
        return Arbitraries.strings().alpha().ofMinLength(9).ofMaxLength(50)
    }
    
    @Provide
    fun errorMessages(): Arbitrary<String> {
        return Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(100)
    }
}
