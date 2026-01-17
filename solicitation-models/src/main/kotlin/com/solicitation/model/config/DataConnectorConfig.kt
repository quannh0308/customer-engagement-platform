package com.solicitation.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

/**
 * Configuration for a data connector that ingests candidate data.
 *
 * @property connectorId Unique identifier for the data connector
 * @property connectorType Type of connector (e.g., "kinesis", "s3", "api")
 * @property enabled Whether this connector is enabled
 * @property sourceConfig Source-specific configuration parameters
 */
data class DataConnectorConfig(
    @field:NotBlank
    @JsonProperty("connectorId")
    val connectorId: String,
    
    @field:NotBlank
    @JsonProperty("connectorType")
    val connectorType: String,
    
    @field:NotNull
    @JsonProperty("enabled")
    val enabled: Boolean,
    
    @JsonProperty("sourceConfig")
    val sourceConfig: Map<String, Any>? = null
)
