package com.solicitation.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.Valid
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull
import java.time.Instant

/**
 * Configuration for an A/B test experiment.
 *
 * Experiments enable testing different solicitation strategies (timing, channels,
 * scoring thresholds, messaging) to optimize conversion rates.
 *
 * @property experimentId Unique identifier for the experiment
 * @property experimentName Human-readable name for the experiment
 * @property enabled Whether this experiment is currently active
 * @property startDate When the experiment starts
 * @property endDate When the experiment ends (optional, null means ongoing)
 * @property treatmentGroups List of treatment groups with their configurations
 * @property experimentType Type of experiment (timing, channel, scoring, messaging)
 * @property description Description of what is being tested
 */
data class ExperimentConfig(
    @field:NotBlank
    @JsonProperty("experimentId")
    val experimentId: String,
    
    @field:NotBlank
    @JsonProperty("experimentName")
    val experimentName: String,
    
    @field:NotNull
    @JsonProperty("enabled")
    val enabled: Boolean,
    
    @field:NotNull
    @JsonProperty("startDate")
    val startDate: Instant,
    
    @JsonProperty("endDate")
    val endDate: Instant? = null,
    
    @field:NotEmpty
    @field:Valid
    @JsonProperty("treatmentGroups")
    val treatmentGroups: List<TreatmentGroup>,
    
    @field:NotBlank
    @JsonProperty("experimentType")
    val experimentType: String,
    
    @JsonProperty("description")
    val description: String? = null
)

/**
 * Represents a treatment group in an experiment.
 *
 * @property treatmentId Unique identifier for this treatment
 * @property treatmentName Human-readable name for this treatment
 * @property allocationPercentage Percentage of customers allocated to this treatment (0-100)
 * @property config Treatment-specific configuration overrides
 */
data class TreatmentGroup(
    @field:NotBlank
    @JsonProperty("treatmentId")
    val treatmentId: String,
    
    @field:NotBlank
    @JsonProperty("treatmentName")
    val treatmentName: String,
    
    @field:Min(0)
    @field:Max(100)
    @JsonProperty("allocationPercentage")
    val allocationPercentage: Int,
    
    @JsonProperty("config")
    val config: Map<String, Any>? = null
)
