package com.solicitation.storage

import com.solicitation.model.config.*
import com.solicitation.storage.arbitraries.ConfigArbitraries
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat

/**
 * Property 33: Marketplace configuration override
 * 
 * **Validates: Requirements 10.4**
 * 
 * For any program with marketplace-specific overrides, the override configuration
 * must take precedence over the default program configuration for that marketplace.
 */
class MarketplaceConfigOverridePropertyTest {
    
    @Property
    fun `marketplace override should take precedence over base config for enabled flag`(
        @ForAll("validProgramConfigs") baseConfig: ProgramConfig,
        @ForAll marketplace: String,
        @ForAll overrideEnabled: Boolean
    ) {
        // Apply override
        val overrides = mapOf("enabled" to overrideEnabled)
        val resultConfig = applyMarketplaceOverrides(baseConfig, marketplace, overrides)
        
        // Override should take precedence
        assertThat(resultConfig.enabled).isEqualTo(overrideEnabled)
    }
    
    @Property
    fun `marketplace override should take precedence for candidateTTLDays`(
        @ForAll("validProgramConfigs") baseConfig: ProgramConfig,
        @ForAll marketplace: String,
        @ForAll("ttlDays") overrideTTL: Int
    ) {
        // Apply override
        val overrides = mapOf("candidateTTLDays" to overrideTTL)
        val resultConfig = applyMarketplaceOverrides(baseConfig, marketplace, overrides)
        
        // Override should take precedence
        assertThat(resultConfig.candidateTTLDays).isEqualTo(overrideTTL)
    }
    
    @Property
    fun `marketplace override should take precedence for reactiveEnabled`(
        @ForAll("validProgramConfigs") baseConfig: ProgramConfig,
        @ForAll marketplace: String,
        @ForAll overrideReactive: Boolean
    ) {
        // Apply override
        val overrides = mapOf("reactiveEnabled" to overrideReactive)
        val resultConfig = applyMarketplaceOverrides(baseConfig, marketplace, overrides)
        
        // Override should take precedence
        assertThat(resultConfig.reactiveEnabled).isEqualTo(overrideReactive)
    }
    
    @Property
    fun `empty marketplace override should return base config unchanged`(
        @ForAll("validProgramConfigs") baseConfig: ProgramConfig,
        @ForAll marketplace: String
    ) {
        // Apply empty override
        val overrides = emptyMap<String, Any>()
        val resultConfig = applyMarketplaceOverrides(baseConfig, marketplace, overrides)
        
        // Should be identical to base config
        assertThat(resultConfig).isEqualTo(baseConfig)
    }
    
    @Property
    fun `multiple marketplace overrides should all be applied`(
        @ForAll("validProgramConfigs") baseConfig: ProgramConfig,
        @ForAll marketplace: String,
        @ForAll overrideEnabled: Boolean,
        @ForAll("ttlDays") overrideTTL: Int,
        @ForAll overrideReactive: Boolean
    ) {
        // Apply multiple overrides
        val overrides = mapOf(
            "enabled" to overrideEnabled,
            "candidateTTLDays" to overrideTTL,
            "reactiveEnabled" to overrideReactive
        )
        val resultConfig = applyMarketplaceOverrides(baseConfig, marketplace, overrides)
        
        // All overrides should be applied
        assertThat(resultConfig.enabled).isEqualTo(overrideEnabled)
        assertThat(resultConfig.candidateTTLDays).isEqualTo(overrideTTL)
        assertThat(resultConfig.reactiveEnabled).isEqualTo(overrideReactive)
    }
    
    @Property
    fun `non-overridden fields should retain base config values`(
        @ForAll("validProgramConfigs") baseConfig: ProgramConfig,
        @ForAll marketplace: String,
        @ForAll overrideEnabled: Boolean
    ) {
        // Apply single override
        val overrides = mapOf("enabled" to overrideEnabled)
        val resultConfig = applyMarketplaceOverrides(baseConfig, marketplace, overrides)
        
        // Non-overridden fields should remain unchanged
        assertThat(resultConfig.programId).isEqualTo(baseConfig.programId)
        assertThat(resultConfig.programName).isEqualTo(baseConfig.programName)
        assertThat(resultConfig.marketplaces).isEqualTo(baseConfig.marketplaces)
        assertThat(resultConfig.dataConnectors).isEqualTo(baseConfig.dataConnectors)
        assertThat(resultConfig.scoringModels).isEqualTo(baseConfig.scoringModels)
        assertThat(resultConfig.filterChain).isEqualTo(baseConfig.filterChain)
        assertThat(resultConfig.channels).isEqualTo(baseConfig.channels)
    }
    
    @Provide
    fun ttlDays(): Arbitrary<Int> {
        return Arbitraries.integers().between(1, 365)
    }
    
    @Provide
    fun validProgramConfigs(): Arbitrary<ProgramConfig> {
        return ConfigArbitraries.programConfigs()
    }
}
