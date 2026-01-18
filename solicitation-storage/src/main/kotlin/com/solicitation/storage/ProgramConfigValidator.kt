package com.solicitation.storage

import com.solicitation.model.config.ProgramConfig

/**
 * Validates program configurations to ensure they meet all requirements.
 */
class ProgramConfigValidator {
    
    /**
     * Validation result containing any errors found.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String>
    )
    
    /**
     * Validates a program configuration.
     * 
     * @param config Program configuration to validate
     * @return Validation result with any errors
     */
    fun validate(config: ProgramConfig): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate required fields
        if (config.programId.isBlank()) {
            errors.add("programId: must not be blank")
        }
        
        if (config.programName.isBlank()) {
            errors.add("programName: must not be blank")
        }
        
        // Must have at least one data connector
        if (config.dataConnectors.isEmpty()) {
            errors.add("dataConnectors: must have at least one data connector")
        }
        
        // Must have at least one scoring model
        if (config.scoringModels.isEmpty()) {
            errors.add("scoringModels: must have at least one scoring model")
        }
        
        // Must have at least one filter
        if (config.filterChain.filters.isEmpty()) {
            errors.add("filterChain.filters: must have at least one filter")
        }
        
        // Must have at least one channel
        if (config.channels.isEmpty()) {
            errors.add("channels: must have at least one channel")
        }
        
        // Must have at least one marketplace
        if (config.marketplaces.isEmpty()) {
            errors.add("marketplaces: must have at least one marketplace")
        }
        
        // Validate candidateTTLDays is positive
        if (config.candidateTTLDays <= 0) {
            errors.add("candidateTTLDays: must be positive")
        }
        
        // Validate timingWindowDays if present
        config.timingWindowDays?.let { days ->
            if (days <= 0) {
                errors.add("timingWindowDays: must be positive if specified")
            }
        }
        
        // Validate batch schedule format if present (basic check)
        config.batchSchedule?.let { schedule ->
            if (schedule.isBlank()) {
                errors.add("batchSchedule: must not be blank if specified")
            }
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
}
