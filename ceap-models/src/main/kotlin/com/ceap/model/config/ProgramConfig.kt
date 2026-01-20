package com.ceap.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive

/**
 * Configuration for a solicitation program.
 *
 * A program represents an independent solicitation configuration with specific rules,
 * channels, and data sources. Programs can be enabled/disabled and configured per marketplace.
 *
 * @property programId Unique identifier for the program
 * @property programName Human-readable name for the program
 * @property enabled Whether this program is enabled
 * @property marketplaces List of marketplace IDs where this program is active
 * @property dataConnectors List of data connectors that feed this program
 * @property scoringModels List of scoring models to evaluate candidates
 * @property filterChain Filter chain configuration for candidate filtering
 * @property channels List of delivery channels for this program
 * @property batchSchedule Cron expression for batch processing schedule (optional)
 * @property reactiveEnabled Whether reactive (real-time) processing is enabled
 * @property candidateTTLDays Time-to-live for candidates in days
 * @property timingWindowDays Timing window for solicitation in days (optional)
 * @property experiments List of A/B test experiments for this program (optional)
 */
data class ProgramConfig(
    @field:NotBlank
    @JsonProperty("programId")
    val programId: String,
    
    @field:NotBlank
    @JsonProperty("programName")
    val programName: String,
    
    @field:NotNull
    @JsonProperty("enabled")
    val enabled: Boolean,
    
    @field:NotEmpty
    @JsonProperty("marketplaces")
    val marketplaces: List<String>,
    
    @field:NotEmpty
    @field:Valid
    @JsonProperty("dataConnectors")
    val dataConnectors: List<DataConnectorConfig>,
    
    @field:NotEmpty
    @field:Valid
    @JsonProperty("scoringModels")
    val scoringModels: List<ScoringModelConfig>,
    
    @field:NotNull
    @field:Valid
    @JsonProperty("filterChain")
    val filterChain: FilterChainConfig,
    
    @field:NotEmpty
    @field:Valid
    @JsonProperty("channels")
    val channels: List<ChannelConfig>,
    
    @JsonProperty("batchSchedule")
    val batchSchedule: String? = null,
    
    @field:NotNull
    @JsonProperty("reactiveEnabled")
    val reactiveEnabled: Boolean,
    
    @field:Positive
    @JsonProperty("candidateTTLDays")
    val candidateTTLDays: Int,
    
    @JsonProperty("timingWindowDays")
    val timingWindowDays: Int? = null,
    
    @field:Valid
    @JsonProperty("experiments")
    val experiments: List<ExperimentConfig>? = null
)
