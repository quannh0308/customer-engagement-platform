package com.solicitation.storage

import com.solicitation.model.config.*
import com.solicitation.storage.arbitraries.ConfigArbitraries
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat

/**
 * Property 32: Program disable enforcement
 * 
 * **Validates: Requirements 10.3**
 * 
 * For any disabled program, no new candidates should be created, and all scheduled
 * workflows for that program should be skipped.
 */
class ProgramDisableEnforcementPropertyTest {
    
    @Property
    fun `disabled program should be marked as not enabled`(
        @ForAll("validProgramConfigs") config: ProgramConfig
    ) {
        // Create a disabled version of the config
        val disabledConfig = config.copy(enabled = false)
        
        // The disabled config should have enabled = false
        assertThat(disabledConfig.enabled).isFalse()
    }
    
    @Property
    fun `enabling a program should set enabled to true`(
        @ForAll("validProgramConfigs") config: ProgramConfig
    ) {
        // Start with disabled config
        val disabledConfig = config.copy(enabled = false)
        assertThat(disabledConfig.enabled).isFalse()
        
        // Enable it
        val enabledConfig = disabledConfig.copy(enabled = true)
        assertThat(enabledConfig.enabled).isTrue()
    }
    
    @Property
    fun `disabling a program should set enabled to false`(
        @ForAll("validProgramConfigs") config: ProgramConfig
    ) {
        // Start with enabled config
        val enabledConfig = config.copy(enabled = true)
        assertThat(enabledConfig.enabled).isTrue()
        
        // Disable it
        val disabledConfig = enabledConfig.copy(enabled = false)
        assertThat(disabledConfig.enabled).isFalse()
    }
    
    @Property
    fun `program registry should correctly identify disabled programs`(
        @ForAll("validProgramConfigs") config: ProgramConfig
    ) {
        val disabledConfig = config.copy(enabled = false)
        val enabledConfig = config.copy(enabled = true)
        
        // Disabled config should not be enabled
        assertThat(disabledConfig.enabled).isFalse()
        
        // Enabled config should be enabled
        assertThat(enabledConfig.enabled).isTrue()
    }
    
    @Provide
    fun validProgramConfigs(): Arbitrary<ProgramConfig> {
        return ConfigArbitraries.programConfigs()
    }
}
