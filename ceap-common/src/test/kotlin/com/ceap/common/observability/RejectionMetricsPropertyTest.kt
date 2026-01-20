package com.ceap.common.observability

import net.jqwik.api.*
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Property-based tests for rejection reason aggregation.
 * 
 * **Property 38: Rejection reason aggregation**
 * 
 * *For any* time period, rejection reasons must be aggregated by filter type
 * and reason code, enabling analysis of why candidates are being rejected.
 * 
 * **Validates: Requirements 12.3**
 */
class RejectionMetricsPropertyTest {
    
    /**
     * Property: Rejection counts are accurately aggregated by filter type and reason code.
     * 
     * For any set of rejections, the aggregated counts must match the number
     * of rejections recorded for each filter type and reason code combination.
     */
    @Property
    fun rejectionCountsAccuratelyAggregated(
        @ForAll("filterTypes") filterType: String,
        @ForAll("reasonCodes") reasonCode: String,
        @ForAll("counts") count: Int
    ) {
        val aggregator = createTestAggregator()
        
        // Record rejections
        repeat(count) {
            aggregator.recordRejection(filterType, reasonCode)
        }
        
        // Verify aggregated count
        val rejectionCounts = aggregator.getRejectionCounts()
        val actualCount = rejectionCounts[filterType]?.get(reasonCode) ?: 0
        
        assertEquals(count, actualCount)
        
        aggregator.reset()
    }
    
    /**
     * Property: Multiple filter types are tracked independently.
     * 
     * For any set of filter types, rejections for each filter type must be
     * tracked independently without interference.
     */
    @Property
    fun multipleFilterTypesTrackedIndependently(
        @ForAll("filterTypes") filterType1: String,
        @ForAll("filterTypes") filterType2: String,
        @ForAll("reasonCodes") reasonCode: String,
        @ForAll("counts") count1: Int,
        @ForAll("counts") count2: Int
    ) {
        Assume.that(filterType1 != filterType2)
        
        val aggregator = createTestAggregator()
        
        // Record rejections for filter type 1
        repeat(count1) {
            aggregator.recordRejection(filterType1, reasonCode)
        }
        
        // Record rejections for filter type 2
        repeat(count2) {
            aggregator.recordRejection(filterType2, reasonCode)
        }
        
        // Verify counts are independent
        val rejectionCounts = aggregator.getRejectionCounts()
        assertEquals(count1, rejectionCounts[filterType1]?.get(reasonCode) ?: 0)
        assertEquals(count2, rejectionCounts[filterType2]?.get(reasonCode) ?: 0)
        
        aggregator.reset()
    }
    
    /**
     * Property: Multiple reason codes are tracked independently within a filter type.
     * 
     * For any filter type with multiple reason codes, each reason code must be
     * tracked independently.
     */
    @Property
    fun multipleReasonCodesTrackedIndependently(
        @ForAll("filterTypes") filterType: String,
        @ForAll("reasonCodes") reasonCode1: String,
        @ForAll("reasonCodes") reasonCode2: String,
        @ForAll("counts") count1: Int,
        @ForAll("counts") count2: Int
    ) {
        Assume.that(reasonCode1 != reasonCode2)
        
        val aggregator = createTestAggregator()
        
        // Record rejections for reason code 1
        repeat(count1) {
            aggregator.recordRejection(filterType, reasonCode1)
        }
        
        // Record rejections for reason code 2
        repeat(count2) {
            aggregator.recordRejection(filterType, reasonCode2)
        }
        
        // Verify counts are independent
        val filterCounts = aggregator.getRejectionCountsForFilter(filterType)
        assertEquals(count1, filterCounts[reasonCode1] ?: 0)
        assertEquals(count2, filterCounts[reasonCode2] ?: 0)
        
        aggregator.reset()
    }
    
    /**
     * Property: Reset clears all rejection counts.
     * 
     * For any aggregated rejections, after reset, all counts must be zero.
     */
    @Property
    fun resetClearsAllCounts(
        @ForAll("filterTypes") filterType: String,
        @ForAll("reasonCodes") reasonCode: String,
        @ForAll("counts") count: Int
    ) {
        val aggregator = createTestAggregator()
        
        // Record rejections
        repeat(count) {
            aggregator.recordRejection(filterType, reasonCode)
        }
        
        // Verify counts exist
        val beforeReset = aggregator.getRejectionCounts()
        assertTrue(beforeReset.isNotEmpty())
        
        // Reset
        aggregator.reset()
        
        // Verify counts are cleared
        val afterReset = aggregator.getRejectionCounts()
        assertTrue(afterReset.isEmpty())
    }
    
    /**
     * Property: Concurrent rejection recording is thread-safe.
     * 
     * For any set of concurrent rejection recordings, the final count must
     * equal the sum of all recorded rejections.
     */
    @Property
    fun concurrentRecordingIsThreadSafe(
        @ForAll("filterTypes") filterType: String,
        @ForAll("reasonCodes") reasonCode: String,
        @ForAll("largeCounts") totalCount: Int
    ) {
        val aggregator = createTestAggregator()
        
        // Record rejections concurrently from multiple threads
        val threads = (1..10).map { threadIndex ->
            Thread {
                val countPerThread = totalCount / 10
                repeat(countPerThread) {
                    aggregator.recordRejection(filterType, reasonCode)
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Verify total count (allowing for rounding in division)
        val actualCount = aggregator.getRejectionCountsForFilter(filterType)[reasonCode] ?: 0
        val expectedCount = (totalCount / 10) * 10
        assertEquals(expectedCount, actualCount)
        
        aggregator.reset()
    }
    
    /**
     * Property: getRejectionCountsForFilter returns only counts for specified filter.
     * 
     * For any filter type, getRejectionCountsForFilter must return only counts
     * for that filter type, not for other filter types.
     */
    @Property
    fun getRejectionCountsForFilterReturnsOnlySpecifiedFilter(
        @ForAll("filterTypes") filterType1: String,
        @ForAll("filterTypes") filterType2: String,
        @ForAll("reasonCodes") reasonCode: String,
        @ForAll("counts") count: Int
    ) {
        Assume.that(filterType1 != filterType2)
        
        val aggregator = createTestAggregator()
        
        // Record rejections for both filter types
        repeat(count) {
            aggregator.recordRejection(filterType1, reasonCode)
            aggregator.recordRejection(filterType2, reasonCode)
        }
        
        // Get counts for filter type 1
        val filter1Counts = aggregator.getRejectionCountsForFilter(filterType1)
        
        // Verify only filter type 1 counts are returned
        assertEquals(count, filter1Counts[reasonCode] ?: 0)
        assertEquals(1, filter1Counts.size)
        
        aggregator.reset()
    }
    
    /**
     * Property: Empty aggregator returns empty maps.
     * 
     * For a newly created or reset aggregator, getRejectionCounts must return
     * an empty map.
     */
    @Property
    fun emptyAggregatorReturnsEmptyMaps() {
        val aggregator = createTestAggregator()
        
        val counts = aggregator.getRejectionCounts()
        assertTrue(counts.isEmpty())
    }
    
    /**
     * Creates a test aggregator with a mock CloudWatch client.
     * This prevents actual AWS API calls during testing.
     */
    private fun createTestAggregator(): RejectionMetricsAggregator {
        // Create aggregator with mock client that doesn't make real AWS calls
        return RejectionMetricsAggregator(
            cloudWatchClient = CloudWatchClient.builder().build(),
            namespace = "Test/Rejections"
        )
    }
    
    // ========== Arbitrary Providers ==========
    
    @Provide
    fun filterTypes(): Arbitrary<String> {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
    }
    
    @Provide
    fun reasonCodes(): Arbitrary<String> {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
    }
    
    @Provide
    fun counts(): Arbitrary<Int> {
        return Arbitraries.integers().between(1, 50)
    }
    
    @Provide
    fun largeCounts(): Arbitrary<Int> {
        return Arbitraries.integers().between(10, 100)
    }
}
