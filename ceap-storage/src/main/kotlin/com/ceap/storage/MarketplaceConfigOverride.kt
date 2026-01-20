package com.ceap.storage

import com.ceap.model.config.ProgramConfig
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents marketplace-specific configuration overrides for a program.
 * 
 * Allows programs to have different configurations per marketplace while
 * maintaining a base configuration.
 * 
 * @property programId Program identifier
 * @property marketplace Marketplace identifier
 * @property overrides Map of configuration field names to override values
 */
data class MarketplaceConfigOverride(
    @JsonProperty("programId")
    val programId: String,
    
    @JsonProperty("marketplace")
    val marketplace: String,
    
    @JsonProperty("overrides")
    val overrides: Map<String, Any>
)

/**
 * Applies marketplace-specific overrides to a program configuration.
 * 
 * @param baseConfig Base program configuration
 * @param marketplace Marketplace to apply overrides for
 * @param overrides Marketplace-specific overrides
 * @return Program configuration with overrides applied
 */
fun applyMarketplaceOverrides(
    baseConfig: ProgramConfig,
    marketplace: String,
    overrides: Map<String, Any>
): ProgramConfig {
    // If no overrides for this marketplace, return base config
    if (overrides.isEmpty()) {
        return baseConfig
    }
    
    // Apply overrides to create new config
    return baseConfig.copy(
        enabled = (overrides["enabled"] as? Boolean) ?: baseConfig.enabled,
        batchSchedule = (overrides["batchSchedule"] as? String) ?: baseConfig.batchSchedule,
        reactiveEnabled = (overrides["reactiveEnabled"] as? Boolean) ?: baseConfig.reactiveEnabled,
        candidateTTLDays = (overrides["candidateTTLDays"] as? Int) ?: baseConfig.candidateTTLDays,
        timingWindowDays = (overrides["timingWindowDays"] as? Int) ?: baseConfig.timingWindowDays
    )
}
