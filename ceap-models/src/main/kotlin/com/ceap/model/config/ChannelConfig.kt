package com.ceap.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

/**
 * Configuration for a delivery channel.
 *
 * @property channelId Unique identifier for the channel
 * @property channelType Type of channel (e.g., "email", "push", "sms", "in-app")
 * @property enabled Whether this channel is enabled
 * @property shadowMode Whether to run in shadow mode (log but don't deliver)
 * @property config Channel-specific configuration parameters
 */
data class ChannelConfig(
    @field:NotBlank
    @JsonProperty("channelId")
    val channelId: String,
    
    @field:NotBlank
    @JsonProperty("channelType")
    val channelType: String,
    
    @field:NotNull
    @JsonProperty("enabled")
    val enabled: Boolean,
    
    @field:NotNull
    @JsonProperty("shadowMode")
    val shadowMode: Boolean,
    
    @JsonProperty("config")
    val config: Map<String, Any>? = null
)
