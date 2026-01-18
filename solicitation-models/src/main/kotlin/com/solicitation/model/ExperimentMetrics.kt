package com.solicitation.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.PositiveOrZero
import java.time.Instant

/**
 * Metrics collected for a specific treatment group in an experiment.
 *
 * Enables comparison of treatment performance by tracking key metrics
 * separately for each treatment group.
 *
 * @property experimentId ID of the experiment
 * @property treatmentId ID of the treatment group
 * @property candidatesCreated Number of candidates created for this treatment
 * @property candidatesDelivered Number of candidates delivered for this treatment
 * @property responsesReceived Number of responses received for this treatment
 * @property conversionRate Conversion rate (responses / delivered)
 * @property aggregatedAt When these metrics were last aggregated
 */
data class ExperimentMetrics(
    @field:NotBlank
    @JsonProperty("experimentId")
    val experimentId: String,
    
    @field:NotBlank
    @JsonProperty("treatmentId")
    val treatmentId: String,
    
    @field:PositiveOrZero
    @JsonProperty("candidatesCreated")
    val candidatesCreated: Long,
    
    @field:PositiveOrZero
    @JsonProperty("candidatesDelivered")
    val candidatesDelivered: Long,
    
    @field:PositiveOrZero
    @JsonProperty("responsesReceived")
    val responsesReceived: Long,
    
    @JsonProperty("conversionRate")
    val conversionRate: Double? = null,
    
    @field:NotNull
    @JsonProperty("aggregatedAt")
    val aggregatedAt: Instant,
    
    @JsonProperty("additionalMetrics")
    val additionalMetrics: Map<String, Double>? = null
)

/**
 * Collector for treatment-specific metrics.
 *
 * Aggregates metrics per treatment group to enable A/B test analysis.
 */
object ExperimentMetricsCollector {
    
    /**
     * Records a candidate creation event for a treatment.
     *
     * @param experimentId The experiment ID
     * @param treatmentId The treatment ID
     * @param metrics Current metrics (will be updated)
     * @return Updated metrics with incremented candidate count
     */
    fun recordCandidateCreated(
        experimentId: String,
        treatmentId: String,
        metrics: ExperimentMetrics?
    ): ExperimentMetrics {
        val current = metrics ?: ExperimentMetrics(
            experimentId = experimentId,
            treatmentId = treatmentId,
            candidatesCreated = 0,
            candidatesDelivered = 0,
            responsesReceived = 0,
            aggregatedAt = Instant.now()
        )
        
        return current.copy(
            candidatesCreated = current.candidatesCreated + 1,
            aggregatedAt = Instant.now()
        )
    }
    
    /**
     * Records a candidate delivery event for a treatment.
     *
     * @param experimentId The experiment ID
     * @param treatmentId The treatment ID
     * @param metrics Current metrics (will be updated)
     * @return Updated metrics with incremented delivery count
     */
    fun recordCandidateDelivered(
        experimentId: String,
        treatmentId: String,
        metrics: ExperimentMetrics?
    ): ExperimentMetrics {
        val current = metrics ?: ExperimentMetrics(
            experimentId = experimentId,
            treatmentId = treatmentId,
            candidatesCreated = 0,
            candidatesDelivered = 0,
            responsesReceived = 0,
            aggregatedAt = Instant.now()
        )
        
        return current.copy(
            candidatesDelivered = current.candidatesDelivered + 1,
            aggregatedAt = Instant.now()
        )
    }
    
    /**
     * Records a response received event for a treatment.
     *
     * @param experimentId The experiment ID
     * @param treatmentId The treatment ID
     * @param metrics Current metrics (will be updated)
     * @return Updated metrics with incremented response count and updated conversion rate
     */
    fun recordResponseReceived(
        experimentId: String,
        treatmentId: String,
        metrics: ExperimentMetrics?
    ): ExperimentMetrics {
        val current = metrics ?: ExperimentMetrics(
            experimentId = experimentId,
            treatmentId = treatmentId,
            candidatesCreated = 0,
            candidatesDelivered = 0,
            responsesReceived = 0,
            aggregatedAt = Instant.now()
        )
        
        val newResponseCount = current.responsesReceived + 1
        val newConversionRate = if (current.candidatesDelivered > 0) {
            newResponseCount.toDouble() / current.candidatesDelivered.toDouble()
        } else {
            null
        }
        
        return current.copy(
            responsesReceived = newResponseCount,
            conversionRate = newConversionRate,
            aggregatedAt = Instant.now()
        )
    }
    
    /**
     * Calculates conversion rate for given metrics.
     *
     * @param metrics The metrics to calculate conversion rate for
     * @return Conversion rate (responses / delivered), or null if no deliveries
     */
    fun calculateConversionRate(metrics: ExperimentMetrics): Double? {
        return if (metrics.candidatesDelivered > 0) {
            metrics.responsesReceived.toDouble() / metrics.candidatesDelivered.toDouble()
        } else {
            null
        }
    }
    
    /**
     * Compares two treatments based on conversion rate.
     *
     * @param treatment1Metrics Metrics for treatment 1
     * @param treatment2Metrics Metrics for treatment 2
     * @return Positive if treatment1 is better, negative if treatment2 is better, 0 if equal
     */
    fun compareTreatments(
        treatment1Metrics: ExperimentMetrics,
        treatment2Metrics: ExperimentMetrics
    ): Int {
        val rate1 = calculateConversionRate(treatment1Metrics) ?: 0.0
        val rate2 = calculateConversionRate(treatment2Metrics) ?: 0.0
        
        return rate1.compareTo(rate2)
    }
}
