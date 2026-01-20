package com.ceap.serving

import com.ceap.serving.v1.*
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat

/**
 * Property 59: V1 usage tracking
 * 
 * **Validates: Requirements 20.3**
 * 
 * For any v1 API request, usage metrics must be recorded including endpoint,
 * customer, and timestamp to support migration planning.
 */
class V1UsageTrackingPropertyTest {
    
    @Property(tries = 100)
    fun `V1 usage tracker records all single requests`(
        @ForAll("endpoints") endpoint: String,
        @ForAll("customerIds") customerId: String,
        @ForAll("channels") channel: String?,
        @ForAll("programs") program: String?
    ) {
        // Setup
        val tracker = InMemoryV1UsageTracker()
        val initialStats = tracker.getUsageStats()
        val initialCount = initialStats.totalRequests
        
        // Execute: Track a request
        tracker.trackRequest(
            endpoint = endpoint,
            customerId = customerId,
            channel = channel,
            program = program
        )
        
        // Verify: Request was tracked
        val stats = tracker.getUsageStats()
        
        // Total requests incremented
        assertThat(stats.totalRequests).isEqualTo(initialCount + 1)
        
        // Endpoint tracked
        assertThat(stats.requestsByEndpoint).containsKey(endpoint)
        assertThat(stats.requestsByEndpoint[endpoint]).isGreaterThan(0)
        
        // Customer tracked
        assertThat(stats.uniqueCustomers).isGreaterThan(0)
        
        // Channel tracked if provided
        if (channel != null) {
            assertThat(stats.requestsByChannel).containsKey(channel)
            assertThat(stats.requestsByChannel[channel]).isGreaterThan(0)
        }
        
        // Program tracked if provided
        if (program != null) {
            assertThat(stats.requestsByProgram).containsKey(program)
            assertThat(stats.requestsByProgram[program]).isGreaterThan(0)
        }
        
        // Timestamps set
        assertThat(stats.firstRequestTime).isNotNull()
        assertThat(stats.lastRequestTime).isNotNull()
        assertThat(stats.lastRequestTime).isAfterOrEqualTo(stats.firstRequestTime)
    }
    
    @Property(tries = 100)
    fun `V1 usage tracker records all batch requests`(
        @ForAll("endpoints") endpoint: String,
        @ForAll("customerIdLists") customerIds: List<String>,
        @ForAll("channels") channel: String?,
        @ForAll("programs") program: String?
    ) {
        // Setup
        val tracker = InMemoryV1UsageTracker()
        val initialStats = tracker.getUsageStats()
        val initialCount = initialStats.totalRequests
        val initialUniqueCustomers = initialStats.uniqueCustomers
        
        // Execute: Track a batch request
        tracker.trackBatchRequest(
            endpoint = endpoint,
            customerIds = customerIds,
            channel = channel,
            program = program
        )
        
        // Verify: Batch request was tracked
        val stats = tracker.getUsageStats()
        
        // Total requests incremented by 1 (batch counts as 1 request)
        assertThat(stats.totalRequests).isEqualTo(initialCount + 1)
        
        // Endpoint tracked
        assertThat(stats.requestsByEndpoint).containsKey(endpoint)
        
        // All customers tracked
        assertThat(stats.uniqueCustomers).isGreaterThanOrEqualTo(initialUniqueCustomers + customerIds.toSet().size)
        
        // Channel tracked if provided
        if (channel != null) {
            assertThat(stats.requestsByChannel).containsKey(channel)
        }
        
        // Program tracked if provided
        if (program != null) {
            assertThat(stats.requestsByProgram).containsKey(program)
        }
    }
    
    @Property(tries = 50)
    fun `V1 usage tracker accumulates metrics over multiple requests`(
        @ForAll("requestSequences") requests: List<RequestData>
    ) {
        // Setup
        val tracker = InMemoryV1UsageTracker()
        
        // Execute: Track multiple requests
        requests.forEach { request ->
            tracker.trackRequest(
                endpoint = request.endpoint,
                customerId = request.customerId,
                channel = request.channel,
                program = request.program
            )
        }
        
        // Verify: All requests were tracked
        val stats = tracker.getUsageStats()
        
        assertThat(stats.totalRequests).isEqualTo(requests.size.toLong())
        
        // Verify unique customers
        val uniqueCustomers = requests.map { it.customerId }.toSet()
        assertThat(stats.uniqueCustomers).isEqualTo(uniqueCustomers.size)
        
        // Verify endpoint counts
        val endpointCounts = requests.groupingBy { it.endpoint }.eachCount()
        endpointCounts.forEach { (endpoint, count) ->
            assertThat(stats.requestsByEndpoint[endpoint]).isEqualTo(count.toLong())
        }
        
        // Verify channel counts
        val channelCounts = requests.mapNotNull { it.channel }.groupingBy { it }.eachCount()
        channelCounts.forEach { (channel, count) ->
            assertThat(stats.requestsByChannel[channel]).isEqualTo(count.toLong())
        }
        
        // Verify program counts
        val programCounts = requests.mapNotNull { it.program }.groupingBy { it }.eachCount()
        programCounts.forEach { (program, count) ->
            assertThat(stats.requestsByProgram[program]).isEqualTo(count.toLong())
        }
    }
    
    @Property(tries = 50)
    fun `V1 usage tracker maintains timestamp ordering`(
        @ForAll("requestSequences") requests: List<RequestData>
    ) {
        Assume.that(requests.isNotEmpty())
        
        // Setup
        val tracker = InMemoryV1UsageTracker()
        
        // Execute: Track requests sequentially
        requests.forEach { request ->
            tracker.trackRequest(
                endpoint = request.endpoint,
                customerId = request.customerId,
                channel = request.channel,
                program = request.program
            )
            Thread.sleep(1) // Ensure timestamps differ
        }
        
        // Verify: Timestamps are ordered
        val stats = tracker.getUsageStats()
        
        assertThat(stats.firstRequestTime).isNotNull()
        assertThat(stats.lastRequestTime).isNotNull()
        assertThat(stats.lastRequestTime).isAfterOrEqualTo(stats.firstRequestTime)
    }
    
    // Arbitraries for generating test data
    
    @Provide
    fun endpoints(): Arbitrary<String> {
        return Arbitraries.of(
            "getCandidatesForCustomer",
            "getCandidatesForCustomers",
            "deleteCandidate",
            "markCandidateConsumed"
        )
    }
    
    @Provide
    fun customerIds(): Arbitrary<String> {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
    }
    
    @Provide
    fun customerIdLists(): Arbitrary<List<String>> {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
            .list().ofMinSize(1).ofMaxSize(10)
    }
    
    @Provide
    fun channels(): Arbitrary<String?> {
        return Arbitraries.of("email", "in-app", "push", "sms").injectNull(0.2)
    }
    
    @Provide
    fun programs(): Arbitrary<String?> {
        return Arbitraries.of("reviews", "ratings", "surveys", "feedback").injectNull(0.2)
    }
    
    @Provide
    fun requestSequences(): Arbitrary<List<RequestData>> {
        return Combinators.combine(
            endpoints(),
            customerIds(),
            channels(),
            programs()
        ).`as` { endpoint, customerId, channel, program ->
            RequestData(endpoint, customerId, channel, program)
        }.list().ofMinSize(1).ofMaxSize(20)
    }
    
    data class RequestData(
        val endpoint: String,
        val customerId: String,
        val channel: String?,
        val program: String?
    )
}
