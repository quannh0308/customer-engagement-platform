package com.ceap.common.observability

import net.jqwik.api.*
import net.jqwik.api.constraints.IntRange
import net.jqwik.api.constraints.LongRange
import net.jqwik.api.constraints.StringLength
import org.assertj.core.api.Assertions.assertThat

/**
 * Property-based tests for program cost attribution.
 * 
 * **Property 41: Program cost attribution**
 * 
 * *For any* resource usage (storage, compute, API calls), costs must be
 * attributed to the specific program that generated the usage.
 * 
 * **Validates: Requirements 13.4**
 */
class ProgramCostAttributionPropertyTest {
    
    /**
     * Property 41: Program cost attribution
     * 
     * Resource usage is correctly attributed to the specific program.
     */
    @Property(tries = 100)
    fun `resource usage is attributed to correct program`(
        @ForAll @StringLength(min = 3, max = 20) programId: String,
        @ForAll @IntRange(min = 1, max = 100) storageReads: Int,
        @ForAll @IntRange(min = 1, max = 100) storageWrites: Int,
        @ForAll @IntRange(min = 1, max = 50) lambdaInvocations: Int,
        @ForAll @LongRange(min = 100, max = 10000) lambdaDurationMs: Long,
        @ForAll @IntRange(min = 1, max = 50) apiCalls: Int,
        @ForAll @LongRange(min = 1000, max = 1000000) dataTransferBytes: Long
    ) {
        // Create cost tracker without CloudWatch client (won't publish metrics in test)
        val costTracker = ProgramCostTracker()
        
        // Record various resource usage for the program
        repeat(storageReads) {
            costTracker.recordStorageReads(programId)
        }
        
        repeat(storageWrites) {
            costTracker.recordStorageWrites(programId)
        }
        
        repeat(lambdaInvocations) {
            costTracker.recordLambdaInvocation(programId, lambdaDurationMs)
        }
        
        repeat(apiCalls) {
            costTracker.recordApiCalls(programId)
        }
        
        costTracker.recordDataTransfer(programId, dataTransferBytes)
        
        // Get cost summary for the program
        val summary = costTracker.getCostSummary(programId)
        
        // Verify all usage is correctly attributed
        assertThat(summary.programId).isEqualTo(programId)
        assertThat(summary.storageReads).isEqualTo(storageReads.toLong())
        assertThat(summary.storageWrites).isEqualTo(storageWrites.toLong())
        assertThat(summary.lambdaInvocations).isEqualTo(lambdaInvocations.toLong())
        assertThat(summary.lambdaDurationMs).isEqualTo(lambdaDurationMs * lambdaInvocations)
        assertThat(summary.apiCalls).isEqualTo(apiCalls.toLong())
        assertThat(summary.dataTransferBytes).isEqualTo(dataTransferBytes)
        
        // Verify estimated cost is positive
        assertThat(summary.estimatedCostUSD()).isGreaterThan(0.0)
    }
    
    /**
     * Property: Each program has independent cost tracking
     * 
     * Costs are tracked independently per program.
     */
    @Property(tries = 100)
    fun `each program has independent cost tracking`(
        @ForAll @StringLength(min = 3, max = 20) programId1: String,
        @ForAll @StringLength(min = 3, max = 20) programId2: String,
        @ForAll @IntRange(min = 1, max = 50) reads1: Int,
        @ForAll @IntRange(min = 1, max = 50) reads2: Int,
        @ForAll @IntRange(min = 1, max = 50) writes1: Int,
        @ForAll @IntRange(min = 1, max = 50) writes2: Int
    ) {
        Assume.that(programId1 != programId2)
        
        val costTracker = ProgramCostTracker()
        
        // Record different usage for each program
        repeat(reads1) {
            costTracker.recordStorageReads(programId1)
        }
        repeat(reads2) {
            costTracker.recordStorageReads(programId2)
        }
        
        repeat(writes1) {
            costTracker.recordStorageWrites(programId1)
        }
        repeat(writes2) {
            costTracker.recordStorageWrites(programId2)
        }
        
        // Get summaries
        val summary1 = costTracker.getCostSummary(programId1)
        val summary2 = costTracker.getCostSummary(programId2)
        
        // Verify independent tracking
        assertThat(summary1.storageReads).isEqualTo(reads1.toLong())
        assertThat(summary1.storageWrites).isEqualTo(writes1.toLong())
        
        assertThat(summary2.storageReads).isEqualTo(reads2.toLong())
        assertThat(summary2.storageWrites).isEqualTo(writes2.toLong())
        
        // Verify costs are different if usage is different
        if (reads1 != reads2 || writes1 != writes2) {
            assertThat(summary1.estimatedCostUSD()).isNotEqualTo(summary2.estimatedCostUSD())
        }
    }
    
    /**
     * Property: Cost attribution is cumulative
     * 
     * Multiple operations accumulate costs for the same program.
     */
    @Property(tries = 100)
    fun `cost attribution is cumulative`(
        @ForAll @StringLength(min = 3, max = 20) programId: String,
        @ForAll @IntRange(min = 1, max = 20) operationCount: Int,
        @ForAll @IntRange(min = 1, max = 10) readsPerOperation: Int
    ) {
        val costTracker = ProgramCostTracker()
        
        // Record operations multiple times
        repeat(operationCount) {
            repeat(readsPerOperation) {
                costTracker.recordStorageReads(programId)
            }
        }
        
        // Verify cumulative tracking
        val summary = costTracker.getCostSummary(programId)
        val expectedReads = operationCount * readsPerOperation
        assertThat(summary.storageReads).isEqualTo(expectedReads.toLong())
    }
    
    /**
     * Property: Resetting one program doesn't affect others
     * 
     * When one program's costs are reset, other programs' costs remain intact.
     */
    @Property(tries = 100)
    fun `resetting one program does not affect others`(
        @ForAll @StringLength(min = 3, max = 20) resetProgramId: String,
        @ForAll @StringLength(min = 3, max = 20) otherProgramId: String,
        @ForAll @IntRange(min = 10, max = 50) usage: Int
    ) {
        Assume.that(resetProgramId != otherProgramId)
        
        val costTracker = ProgramCostTracker()
        
        // Record usage for both programs
        repeat(usage) {
            costTracker.recordStorageReads(resetProgramId)
            costTracker.recordStorageReads(otherProgramId)
        }
        
        // Verify both have usage
        assertThat(costTracker.getCostSummary(resetProgramId).storageReads).isEqualTo(usage.toLong())
        assertThat(costTracker.getCostSummary(otherProgramId).storageReads).isEqualTo(usage.toLong())
        
        // Reset one program
        costTracker.reset(resetProgramId)
        
        // Verify reset program has no usage
        assertThat(costTracker.getCostSummary(resetProgramId).storageReads).isEqualTo(0)
        
        // Verify other program still has usage
        assertThat(costTracker.getCostSummary(otherProgramId).storageReads).isEqualTo(usage.toLong())
    }
    
    /**
     * Property: All programs can be tracked simultaneously
     * 
     * Multiple programs can have their costs tracked at the same time.
     */
    @Property(tries = 50)
    fun `multiple programs can be tracked simultaneously`(
        @ForAll @StringLength(min = 3, max = 20) programId1: String,
        @ForAll @StringLength(min = 3, max = 20) programId2: String,
        @ForAll @StringLength(min = 3, max = 20) programId3: String,
        @ForAll @IntRange(min = 1, max = 30) usage1: Int,
        @ForAll @IntRange(min = 1, max = 30) usage2: Int,
        @ForAll @IntRange(min = 1, max = 30) usage3: Int
    ) {
        Assume.that(programId1 != programId2)
        Assume.that(programId1 != programId3)
        Assume.that(programId2 != programId3)
        
        val costTracker = ProgramCostTracker()
        
        // Record different usage for each program
        repeat(usage1) { costTracker.recordStorageReads(programId1) }
        repeat(usage2) { costTracker.recordStorageReads(programId2) }
        repeat(usage3) { costTracker.recordStorageReads(programId3) }
        
        // Get all summaries
        val allSummaries = costTracker.getAllCostSummaries()
        
        // Verify all programs are tracked
        assertThat(allSummaries).containsKeys(programId1, programId2, programId3)
        assertThat(allSummaries[programId1]?.storageReads).isEqualTo(usage1.toLong())
        assertThat(allSummaries[programId2]?.storageReads).isEqualTo(usage2.toLong())
        assertThat(allSummaries[programId3]?.storageReads).isEqualTo(usage3.toLong())
    }
}
