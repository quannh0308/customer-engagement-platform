package com.ceap.channels

import net.jqwik.api.*
import net.jqwik.api.constraints.IntRange
import net.jqwik.api.constraints.StringLength
import org.assertj.core.api.Assertions.assertThat

/**
 * Property-based tests for program-specific throttling.
 * 
 * **Property 40: Program-specific throttling**
 * 
 * *For any* program that exceeds its rate limits, only that program should be
 * throttled, and other programs should continue operating at their normal rates.
 * 
 * **Validates: Requirements 13.3**
 */
class ProgramSpecificThrottlingPropertyTest {
    
    /**
     * Property 40: Program-specific throttling
     * 
     * When one program exceeds its rate limit, only that program is throttled.
     * Other programs continue operating normally.
     */
    @Property(tries = 100)
    fun `throttling one program does not affect others`(
        @ForAll @StringLength(min = 3, max = 20) throttledProgramId: String,
        @ForAll @StringLength(min = 3, max = 20) normalProgramId1: String,
        @ForAll @StringLength(min = 3, max = 20) normalProgramId2: String,
        @ForAll @IntRange(min = 5, max = 20) throttledLimit: Int,
        @ForAll @IntRange(min = 50, max = 100) normalLimit: Int
    ) {
        // Ensure program IDs are unique
        Assume.that(throttledProgramId != normalProgramId1)
        Assume.that(throttledProgramId != normalProgramId2)
        Assume.that(normalProgramId1 != normalProgramId2)
        
        val rateLimiter = ProgramRateLimiter(defaultMaxDeliveriesPerWindow = 100)
        
        // Set different limits for each program
        rateLimiter.setProgramLimit(throttledProgramId, throttledLimit)
        rateLimiter.setProgramLimit(normalProgramId1, normalLimit)
        rateLimiter.setProgramLimit(normalProgramId2, normalLimit)
        
        // Exceed the limit for the throttled program
        repeat(throttledLimit + 5) {
            rateLimiter.recordDelivery(throttledProgramId)
        }
        
        // Use some capacity for normal programs (but not exceed)
        val normalUsage = minOf(normalLimit / 2, 10)
        repeat(normalUsage) {
            rateLimiter.recordDelivery(normalProgramId1)
            rateLimiter.recordDelivery(normalProgramId2)
        }
        
        // Verify throttled program is throttled
        assertThat(rateLimiter.isThrottled(throttledProgramId)).isTrue()
        assertThat(rateLimiter.isAllowed(throttledProgramId)).isFalse()
        assertThat(rateLimiter.getRemainingCapacity(throttledProgramId)).isEqualTo(0)
        
        // Verify normal programs are NOT throttled
        assertThat(rateLimiter.isThrottled(normalProgramId1)).isFalse()
        assertThat(rateLimiter.isAllowed(normalProgramId1)).isTrue()
        assertThat(rateLimiter.getRemainingCapacity(normalProgramId1)).isGreaterThan(0)
        
        assertThat(rateLimiter.isThrottled(normalProgramId2)).isFalse()
        assertThat(rateLimiter.isAllowed(normalProgramId2)).isTrue()
        assertThat(rateLimiter.getRemainingCapacity(normalProgramId2)).isGreaterThan(0)
    }
    
    /**
     * Property: Each program has independent rate limit tracking
     * 
     * Rate limits are tracked independently per program.
     */
    @Property(tries = 100)
    fun `each program has independent rate limit tracking`(
        @ForAll @StringLength(min = 3, max = 20) programId1: String,
        @ForAll @StringLength(min = 3, max = 20) programId2: String,
        @ForAll @IntRange(min = 10, max = 50) limit1: Int,
        @ForAll @IntRange(min = 10, max = 50) limit2: Int,
        @ForAll @IntRange(min = 1, max = 10) usage1: Int,
        @ForAll @IntRange(min = 1, max = 10) usage2: Int
    ) {
        Assume.that(programId1 != programId2)
        Assume.that(usage1 <= limit1)
        Assume.that(usage2 <= limit2)
        
        val rateLimiter = ProgramRateLimiter()
        
        // Set different limits
        rateLimiter.setProgramLimit(programId1, limit1)
        rateLimiter.setProgramLimit(programId2, limit2)
        
        // Record different usage
        repeat(usage1) {
            rateLimiter.recordDelivery(programId1)
        }
        repeat(usage2) {
            rateLimiter.recordDelivery(programId2)
        }
        
        // Verify independent tracking
        assertThat(rateLimiter.getCurrentCount(programId1)).isEqualTo(usage1)
        assertThat(rateLimiter.getCurrentCount(programId2)).isEqualTo(usage2)
        
        assertThat(rateLimiter.getRemainingCapacity(programId1)).isEqualTo(limit1 - usage1)
        assertThat(rateLimiter.getRemainingCapacity(programId2)).isEqualTo(limit2 - usage2)
    }
    
    /**
     * Property: Throttling status is program-specific
     * 
     * The throttling status of one program doesn't affect the status of others.
     */
    @Property(tries = 100)
    fun `throttling status is program specific`(
        @ForAll @StringLength(min = 3, max = 20) programId1: String,
        @ForAll @StringLength(min = 3, max = 20) programId2: String,
        @ForAll @StringLength(min = 3, max = 20) programId3: String,
        @ForAll @IntRange(min = 5, max = 20) limit: Int
    ) {
        Assume.that(programId1 != programId2)
        Assume.that(programId1 != programId3)
        Assume.that(programId2 != programId3)
        
        val rateLimiter = ProgramRateLimiter()
        
        // Set same limit for all programs
        rateLimiter.setProgramLimit(programId1, limit)
        rateLimiter.setProgramLimit(programId2, limit)
        rateLimiter.setProgramLimit(programId3, limit)
        
        // Exceed limit for program1
        repeat(limit + 1) {
            rateLimiter.recordDelivery(programId1)
        }
        
        // Use half capacity for program2
        repeat(limit / 2) {
            rateLimiter.recordDelivery(programId2)
        }
        
        // Don't use any capacity for program3
        
        // Get throttling status for all programs
        val status = rateLimiter.getThrottlingStatus()
        
        // Verify program1 is throttled
        assertThat(status[programId1]?.isThrottled).isTrue()
        assertThat(status[programId1]?.remainingCapacity).isEqualTo(0)
        
        // Verify program2 is not throttled
        assertThat(status[programId2]?.isThrottled).isFalse()
        assertThat(status[programId2]?.remainingCapacity).isGreaterThan(0)
        
        // Verify program3 has full capacity (no usage recorded, so not in status map)
        // Program3 won't be in the status map if it has no recorded usage
        val program3Status = status[programId3]
        if (program3Status != null) {
            assertThat(program3Status.isThrottled).isFalse()
            assertThat(program3Status.remainingCapacity).isEqualTo(limit)
        } else {
            // If not in map, it means no usage, which is also valid
            assertThat(rateLimiter.getRemainingCapacity(programId3)).isEqualTo(limit)
        }
    }
    
    /**
     * Property: Resetting one program doesn't affect others
     * 
     * When one program's rate limiter is reset, other programs' limits remain intact.
     */
    @Property(tries = 100)
    fun `resetting one program does not affect others`(
        @ForAll @StringLength(min = 3, max = 20) resetProgramId: String,
        @ForAll @StringLength(min = 3, max = 20) otherProgramId: String,
        @ForAll @IntRange(min = 10, max = 50) limit: Int,
        @ForAll @IntRange(min = 5, max = 20) usage: Int
    ) {
        Assume.that(resetProgramId != otherProgramId)
        Assume.that(usage <= limit)
        
        val rateLimiter = ProgramRateLimiter()
        
        // Set limits and record usage for both programs
        rateLimiter.setProgramLimit(resetProgramId, limit)
        rateLimiter.setProgramLimit(otherProgramId, limit)
        
        repeat(usage) {
            rateLimiter.recordDelivery(resetProgramId)
            rateLimiter.recordDelivery(otherProgramId)
        }
        
        // Verify both have usage
        assertThat(rateLimiter.getCurrentCount(resetProgramId)).isEqualTo(usage)
        assertThat(rateLimiter.getCurrentCount(otherProgramId)).isEqualTo(usage)
        
        // Reset one program
        rateLimiter.reset(resetProgramId)
        
        // Verify reset program has no usage
        assertThat(rateLimiter.getCurrentCount(resetProgramId)).isEqualTo(0)
        assertThat(rateLimiter.getRemainingCapacity(resetProgramId)).isEqualTo(limit)
        
        // Verify other program still has usage
        assertThat(rateLimiter.getCurrentCount(otherProgramId)).isEqualTo(usage)
        assertThat(rateLimiter.getRemainingCapacity(otherProgramId)).isEqualTo(limit - usage)
    }
}
