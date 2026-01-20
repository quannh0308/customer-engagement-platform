package com.ceap.workflow.common

import net.jqwik.api.*
import net.jqwik.api.constraints.IntRange
import net.jqwik.api.constraints.StringLength
import org.assertj.core.api.Assertions.assertThat

/**
 * Property-based tests for program failure isolation.
 * 
 * **Property 39: Program failure isolation**
 * 
 * *For any* program workflow failure, other programs' workflows must continue
 * executing normally without being affected by the failure.
 * 
 * **Validates: Requirements 13.1**
 */
class ProgramFailureIsolationPropertyTest {
    
    /**
     * Property 39: Program failure isolation
     * 
     * When one program's workflow fails, other programs' workflows must
     * continue executing successfully.
     */
    @Property(tries = 100)
    fun `program failure does not affect other programs`(
        @ForAll @StringLength(min = 3, max = 20) failingProgramId: String,
        @ForAll @StringLength(min = 3, max = 20) successfulProgramId1: String,
        @ForAll @StringLength(min = 3, max = 20) successfulProgramId2: String,
        @ForAll @IntRange(min = 1, max = 100) successValue1: Int,
        @ForAll @IntRange(min = 1, max = 100) successValue2: Int
    ) {
        // Ensure program IDs are unique
        Assume.that(failingProgramId != successfulProgramId1)
        Assume.that(failingProgramId != successfulProgramId2)
        Assume.that(successfulProgramId1 != successfulProgramId2)
        
        val isolator = ProgramWorkflowIsolator(maxConcurrentPrograms = 5, defaultTimeoutMs = 5000)
        
        try {
            // Create workflows: one that fails, two that succeed
            val workflows = mapOf(
                failingProgramId to {
                    throw RuntimeException("Simulated failure for $failingProgramId")
                },
                successfulProgramId1 to {
                    // Simulate some work
                    Thread.sleep(10)
                    successValue1
                },
                successfulProgramId2 to {
                    // Simulate some work
                    Thread.sleep(10)
                    successValue2
                }
            )
            
            // Execute all workflows concurrently
            val results = isolator.executeMultipleIsolated(workflows)
            
            // Verify the failing program failed
            val failingResult = results[failingProgramId]
            assertThat(failingResult).isInstanceOf(ProgramWorkflowIsolator.ExecutionResult.Failure::class.java)
            
            // Verify the successful programs succeeded
            val successResult1 = results[successfulProgramId1]
            assertThat(successResult1).isInstanceOf(ProgramWorkflowIsolator.ExecutionResult.Success::class.java)
            if (successResult1 is ProgramWorkflowIsolator.ExecutionResult.Success) {
                assertThat(successResult1.result).isEqualTo(successValue1)
            }
            
            val successResult2 = results[successfulProgramId2]
            assertThat(successResult2).isInstanceOf(ProgramWorkflowIsolator.ExecutionResult.Success::class.java)
            if (successResult2 is ProgramWorkflowIsolator.ExecutionResult.Success) {
                assertThat(successResult2.result).isEqualTo(successValue2)
            }
            
            // Verify failure count is tracked only for the failing program
            assertThat(isolator.getConsecutiveFailures(failingProgramId)).isGreaterThan(0)
            assertThat(isolator.getConsecutiveFailures(successfulProgramId1)).isEqualTo(0)
            assertThat(isolator.getConsecutiveFailures(successfulProgramId2)).isEqualTo(0)
            
        } finally {
            isolator.shutdown()
        }
    }
    
    /**
     * Property: Multiple failures in one program don't affect others
     * 
     * Even with repeated failures in one program, other programs continue
     * to execute successfully.
     */
    @Property(tries = 50)
    fun `repeated failures in one program do not affect others`(
        @ForAll @StringLength(min = 3, max = 20) failingProgramId: String,
        @ForAll @StringLength(min = 3, max = 20) successfulProgramId: String,
        @ForAll @IntRange(min = 2, max = 5) failureCount: Int
    ) {
        Assume.that(failingProgramId != successfulProgramId)
        
        val isolator = ProgramWorkflowIsolator(maxConcurrentPrograms = 5, defaultTimeoutMs = 5000)
        
        try {
            // Execute multiple times
            repeat(failureCount) { iteration ->
                val workflows = mapOf(
                    failingProgramId to {
                        throw RuntimeException("Failure iteration $iteration")
                    },
                    successfulProgramId to {
                        Thread.sleep(10)
                        iteration
                    }
                )
                
                val results = isolator.executeMultipleIsolated(workflows)
                
                // Verify failing program failed
                assertThat(results[failingProgramId])
                    .isInstanceOf(ProgramWorkflowIsolator.ExecutionResult.Failure::class.java)
                
                // Verify successful program succeeded
                val successResult = results[successfulProgramId]
                assertThat(successResult)
                    .isInstanceOf(ProgramWorkflowIsolator.ExecutionResult.Success::class.java)
                if (successResult is ProgramWorkflowIsolator.ExecutionResult.Success) {
                    assertThat(successResult.result).isEqualTo(iteration)
                }
            }
            
            // Verify failure count accumulated only for failing program
            assertThat(isolator.getConsecutiveFailures(failingProgramId)).isEqualTo(failureCount)
            assertThat(isolator.getConsecutiveFailures(successfulProgramId)).isEqualTo(0)
            
        } finally {
            isolator.shutdown()
        }
    }
    
    /**
     * Property: Timeout in one program doesn't affect others
     * 
     * When one program times out, other programs continue executing normally.
     */
    @Property(tries = 50)
    fun `timeout in one program does not affect others`(
        @ForAll @StringLength(min = 3, max = 20) timeoutProgramId: String,
        @ForAll @StringLength(min = 3, max = 20) successfulProgramId: String,
        @ForAll @IntRange(min = 1, max = 100) successValue: Int
    ) {
        Assume.that(timeoutProgramId != successfulProgramId)
        
        val isolator = ProgramWorkflowIsolator(maxConcurrentPrograms = 5, defaultTimeoutMs = 5000)
        
        try {
            val workflows = mapOf(
                timeoutProgramId to {
                    // Simulate long-running operation that will timeout
                    Thread.sleep(10000)
                    "should not complete"
                },
                successfulProgramId to {
                    Thread.sleep(10)
                    successValue
                }
            )
            
            // Execute with short timeout
            val results = isolator.executeMultipleIsolated(workflows, timeoutMs = 100)
            
            // Verify timeout program timed out
            assertThat(results[timeoutProgramId])
                .isInstanceOf(ProgramWorkflowIsolator.ExecutionResult.Timeout::class.java)
            
            // Verify successful program succeeded
            val successResult = results[successfulProgramId]
            assertThat(successResult)
                .isInstanceOf(ProgramWorkflowIsolator.ExecutionResult.Success::class.java)
            if (successResult is ProgramWorkflowIsolator.ExecutionResult.Success) {
                assertThat(successResult.result).isEqualTo(successValue)
            }
            
        } finally {
            isolator.shutdown()
        }
    }
}
