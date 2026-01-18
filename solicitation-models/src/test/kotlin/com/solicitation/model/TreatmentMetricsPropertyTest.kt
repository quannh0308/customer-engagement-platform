package com.solicitation.model

import net.jqwik.api.*
import net.jqwik.api.constraints.NotBlank
import net.jqwik.api.constraints.Positive
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-based tests for treatment-specific metrics collection.
 *
 * **Validates: Requirements 11.4**
 */
class TreatmentMetricsPropertyTest {
    
    /**
     * Property 36: Treatment-specific metrics collection
     *
     * For any experiment, metrics must be collected separately for each treatment group,
     * enabling comparison of treatment performance.
     */
    @Property(tries = 100)
    fun `metrics are collected separately per treatment group`(
        @ForAll @NotBlank experimentId: String,
        @ForAll @NotBlank treatment1Id: String,
        @ForAll @NotBlank treatment2Id: String,
        @ForAll @Positive candidateCount: Int
    ) {
        // Assume treatment IDs are different
        if (treatment1Id == treatment2Id) {
            return
        }
        
        // Initialize metrics for both treatments
        var treatment1Metrics: ExperimentMetrics? = null
        var treatment2Metrics: ExperimentMetrics? = null
        
        // Record candidate creations for both treatments
        repeat(candidateCount.coerceAtMost(100)) {
            treatment1Metrics = ExperimentMetricsCollector.recordCandidateCreated(
                experimentId, treatment1Id, treatment1Metrics
            )
            treatment2Metrics = ExperimentMetricsCollector.recordCandidateCreated(
                experimentId, treatment2Id, treatment2Metrics
            )
        }
        
        // Verify metrics are tracked separately
        assertNotNull(treatment1Metrics, "Treatment 1 metrics should exist")
        assertNotNull(treatment2Metrics, "Treatment 2 metrics should exist")
        
        assertEquals(
            experimentId,
            treatment1Metrics!!.experimentId,
            "Treatment 1 should have correct experiment ID"
        )
        assertEquals(
            experimentId,
            treatment2Metrics!!.experimentId,
            "Treatment 2 should have correct experiment ID"
        )
        
        assertEquals(
            treatment1Id,
            treatment1Metrics!!.treatmentId,
            "Treatment 1 should have correct treatment ID"
        )
        assertEquals(
            treatment2Id,
            treatment2Metrics!!.treatmentId,
            "Treatment 2 should have correct treatment ID"
        )
        
        // Both should have same candidate count
        assertEquals(
            treatment1Metrics!!.candidatesCreated,
            treatment2Metrics!!.candidatesCreated,
            "Both treatments should have same candidate count"
        )
    }
    
    /**
     * Property: Candidate creation increments count
     *
     * Recording a candidate creation should increment the candidates created count.
     */
    @Property(tries = 100)
    fun `recording candidate creation increments count`(
        @ForAll @NotBlank experimentId: String,
        @ForAll @NotBlank treatmentId: String,
        @ForAll @Positive iterations: Int
    ) {
        var metrics: ExperimentMetrics? = null
        val actualIterations = iterations.coerceAtMost(1000)
        
        repeat(actualIterations) {
            metrics = ExperimentMetricsCollector.recordCandidateCreated(
                experimentId, treatmentId, metrics
            )
        }
        
        assertNotNull(metrics)
        assertEquals(
            actualIterations.toLong(),
            metrics!!.candidatesCreated,
            "Candidates created count should match iterations"
        )
    }
    
    /**
     * Property: Delivery recording increments count
     *
     * Recording a candidate delivery should increment the candidates delivered count.
     */
    @Property(tries = 100)
    fun `recording candidate delivery increments count`(
        @ForAll @NotBlank experimentId: String,
        @ForAll @NotBlank treatmentId: String,
        @ForAll @Positive deliveries: Int
    ) {
        var metrics: ExperimentMetrics? = null
        val actualDeliveries = deliveries.coerceAtMost(1000)
        
        repeat(actualDeliveries) {
            metrics = ExperimentMetricsCollector.recordCandidateDelivered(
                experimentId, treatmentId, metrics
            )
        }
        
        assertNotNull(metrics)
        assertEquals(
            actualDeliveries.toLong(),
            metrics!!.candidatesDelivered,
            "Candidates delivered count should match deliveries"
        )
    }
    
    /**
     * Property: Response recording increments count and updates conversion rate
     *
     * Recording a response should increment the response count and update conversion rate.
     */
    @Property(tries = 100)
    fun `recording response increments count and updates conversion rate`(
        @ForAll @NotBlank experimentId: String,
        @ForAll @NotBlank treatmentId: String,
        @ForAll @Positive deliveries: Int,
        @ForAll @Positive responses: Int
    ) {
        val actualDeliveries = deliveries.coerceAtMost(1000)
        val actualResponses = responses.coerceAtMost(actualDeliveries)
        
        var metrics: ExperimentMetrics? = null
        
        // Record deliveries first
        repeat(actualDeliveries) {
            metrics = ExperimentMetricsCollector.recordCandidateDelivered(
                experimentId, treatmentId, metrics
            )
        }
        
        // Record responses
        repeat(actualResponses) {
            metrics = ExperimentMetricsCollector.recordResponseReceived(
                experimentId, treatmentId, metrics
            )
        }
        
        assertNotNull(metrics)
        assertEquals(
            actualResponses.toLong(),
            metrics!!.responsesReceived,
            "Responses received count should match responses"
        )
        
        // Verify conversion rate
        val expectedRate = actualResponses.toDouble() / actualDeliveries.toDouble()
        assertNotNull(metrics!!.conversionRate, "Conversion rate should be calculated")
        assertEquals(
            expectedRate,
            metrics!!.conversionRate!!,
            0.0001,
            "Conversion rate should be responses / deliveries"
        )
    }
    
    /**
     * Property: Conversion rate is null when no deliveries
     *
     * If no candidates have been delivered, conversion rate should be null.
     */
    @Property(tries = 100)
    fun `conversion rate is null when no deliveries`(
        @ForAll @NotBlank experimentId: String,
        @ForAll @NotBlank treatmentId: String
    ) {
        val metrics = ExperimentMetrics(
            experimentId = experimentId,
            treatmentId = treatmentId,
            candidatesCreated = 10,
            candidatesDelivered = 0,
            responsesReceived = 0,
            aggregatedAt = Instant.now()
        )
        
        val conversionRate = ExperimentMetricsCollector.calculateConversionRate(metrics)
        assertTrue(
            conversionRate == null,
            "Conversion rate should be null when no deliveries"
        )
    }
    
    /**
     * Property: Treatment comparison is consistent
     *
     * Comparing treatments should be consistent with their conversion rates.
     */
    @Property(tries = 100)
    fun `treatment comparison is consistent with conversion rates`(
        @ForAll @NotBlank experimentId: String,
        @ForAll @NotBlank treatment1Id: String,
        @ForAll @NotBlank treatment2Id: String,
        @ForAll @Positive deliveries: Int,
        @ForAll @Positive responses1: Int,
        @ForAll @Positive responses2: Int
    ) {
        if (treatment1Id == treatment2Id) {
            return
        }
        
        val actualDeliveries = deliveries.coerceAtMost(1000)
        val actualResponses1 = responses1.coerceAtMost(actualDeliveries)
        val actualResponses2 = responses2.coerceAtMost(actualDeliveries)
        
        val metrics1 = ExperimentMetrics(
            experimentId = experimentId,
            treatmentId = treatment1Id,
            candidatesCreated = actualDeliveries.toLong(),
            candidatesDelivered = actualDeliveries.toLong(),
            responsesReceived = actualResponses1.toLong(),
            conversionRate = actualResponses1.toDouble() / actualDeliveries.toDouble(),
            aggregatedAt = Instant.now()
        )
        
        val metrics2 = ExperimentMetrics(
            experimentId = experimentId,
            treatmentId = treatment2Id,
            candidatesCreated = actualDeliveries.toLong(),
            candidatesDelivered = actualDeliveries.toLong(),
            responsesReceived = actualResponses2.toLong(),
            conversionRate = actualResponses2.toDouble() / actualDeliveries.toDouble(),
            aggregatedAt = Instant.now()
        )
        
        val comparison = ExperimentMetricsCollector.compareTreatments(metrics1, metrics2)
        val rate1 = metrics1.conversionRate!!
        val rate2 = metrics2.conversionRate!!
        
        when {
            rate1 > rate2 -> assertTrue(comparison > 0, "Treatment 1 should be better")
            rate1 < rate2 -> assertTrue(comparison < 0, "Treatment 2 should be better")
            else -> assertEquals(0, comparison, "Treatments should be equal")
        }
    }
    
    /**
     * Property: Metrics aggregation timestamp is updated
     *
     * Each metrics update should update the aggregatedAt timestamp.
     */
    @Property(tries = 100)
    fun `metrics aggregation timestamp is updated on each event`(
        @ForAll @NotBlank experimentId: String,
        @ForAll @NotBlank treatmentId: String
    ) {
        val initialMetrics = ExperimentMetrics(
            experimentId = experimentId,
            treatmentId = treatmentId,
            candidatesCreated = 0,
            candidatesDelivered = 0,
            responsesReceived = 0,
            aggregatedAt = Instant.now().minusSeconds(60)
        )
        
        // Record an event
        val updatedMetrics = ExperimentMetricsCollector.recordCandidateCreated(
            experimentId, treatmentId, initialMetrics
        )
        
        // Verify timestamp was updated
        assertTrue(
            updatedMetrics.aggregatedAt.isAfter(initialMetrics.aggregatedAt),
            "Aggregation timestamp should be updated"
        )
    }
}
