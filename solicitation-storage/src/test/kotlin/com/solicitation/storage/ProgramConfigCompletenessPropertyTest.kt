package com.solicitation.storage

import com.solicitation.model.config.*
import com.solicitation.storage.arbitraries.ConfigArbitraries
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat

/**
 * Property 31: Program configuration completeness
 * 
 * **Validates: Requirements 10.2**
 * 
 * For any valid program configuration, it must specify at least one data source,
 * one filter, one scoring model, and one channel.
 */
class ProgramConfigCompletenessPropertyTest {
    
    @Property
    fun `valid program config must have all required components`(
        @ForAll("validProgramConfigs") config: ProgramConfig
    ) {
        val validator = ProgramConfigValidator()
        val result = validator.validate(config)
        
        // Valid configs should pass validation
        assertThat(result.isValid).isTrue()
        assertThat(result.errors).isEmpty()
        
        // Must have at least one of each required component
        assertThat(config.dataConnectors).isNotEmpty()
        assertThat(config.scoringModels).isNotEmpty()
        assertThat(config.filterChain.filters).isNotEmpty()
        assertThat(config.channels).isNotEmpty()
        assertThat(config.marketplaces).isNotEmpty()
    }
    
    @Property
    fun `program config missing data connectors should fail validation`(
        @ForAll("programConfigsWithoutDataConnectors") config: ProgramConfig
    ) {
        val validator = ProgramConfigValidator()
        val result = validator.validate(config)
        
        assertThat(result.isValid).isFalse()
        assertThat(result.errors).anyMatch { it.contains("dataConnectors") }
    }
    
    @Property
    fun `program config missing scoring models should fail validation`(
        @ForAll("programConfigsWithoutScoringModels") config: ProgramConfig
    ) {
        val validator = ProgramConfigValidator()
        val result = validator.validate(config)
        
        assertThat(result.isValid).isFalse()
        assertThat(result.errors).anyMatch { it.contains("scoringModels") }
    }
    
    @Property
    fun `program config missing filters should fail validation`(
        @ForAll("programConfigsWithoutFilters") config: ProgramConfig
    ) {
        val validator = ProgramConfigValidator()
        val result = validator.validate(config)
        
        assertThat(result.isValid).isFalse()
        assertThat(result.errors).anyMatch { it.contains("filters") }
    }
    
    @Property
    fun `program config missing channels should fail validation`(
        @ForAll("programConfigsWithoutChannels") config: ProgramConfig
    ) {
        val validator = ProgramConfigValidator()
        val result = validator.validate(config)
        
        assertThat(result.isValid).isFalse()
        assertThat(result.errors).anyMatch { it.contains("channels") }
    }
    
    @Provide
    fun validProgramConfigs(): Arbitrary<ProgramConfig> {
        return ConfigArbitraries.programConfigs()
    }
    
    @Provide
    fun programConfigsWithoutDataConnectors(): Arbitrary<ProgramConfig> {
        return validProgramConfigs().map { config ->
            config.copy(dataConnectors = emptyList())
        }
    }
    
    @Provide
    fun programConfigsWithoutScoringModels(): Arbitrary<ProgramConfig> {
        return validProgramConfigs().map { config ->
            config.copy(scoringModels = emptyList())
        }
    }
    
    @Provide
    fun programConfigsWithoutFilters(): Arbitrary<ProgramConfig> {
        return validProgramConfigs().map { config ->
            config.copy(filterChain = FilterChainConfig(filters = emptyList()))
        }
    }
    
    @Provide
    fun programConfigsWithoutChannels(): Arbitrary<ProgramConfig> {
        return validProgramConfigs().map { config ->
            config.copy(channels = emptyList())
        }
    }
}
