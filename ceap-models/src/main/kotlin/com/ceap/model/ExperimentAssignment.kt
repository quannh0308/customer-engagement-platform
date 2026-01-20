package com.ceap.model

import com.ceap.model.config.ExperimentConfig
import com.ceap.model.config.TreatmentGroup
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Handles deterministic treatment assignment for A/B testing experiments.
 *
 * Uses consistent hashing to ensure the same customer always receives the same
 * treatment for a given experiment, providing stable and reproducible assignments.
 */
object ExperimentAssignment {
    
    /**
     * Assigns a customer to a treatment group deterministically.
     *
     * Uses SHA-256 hashing of customerId + experimentId to generate a consistent
     * hash value, then maps it to a treatment group based on allocation percentages.
     *
     * @param customerId The customer identifier
     * @param experiment The experiment configuration
     * @return The assigned treatment group, or null if experiment is disabled or invalid
     */
    fun assignTreatment(customerId: String, experiment: ExperimentConfig): TreatmentGroup? {
        if (!experiment.enabled || experiment.treatmentGroups.isEmpty()) {
            return null
        }
        
        // Validate allocation percentages sum to 100
        val totalAllocation = experiment.treatmentGroups.sumOf { it.allocationPercentage }
        if (totalAllocation != 100) {
            throw IllegalArgumentException(
                "Treatment group allocations must sum to 100, got $totalAllocation for experiment ${experiment.experimentId}"
            )
        }
        
        // Generate consistent hash
        val hashInput = "$customerId:${experiment.experimentId}"
        val hash = sha256Hash(hashInput)
        
        // Map hash to 0-99 range
        val bucket = (hash % 100).toInt()
        
        // Find treatment group based on allocation
        var cumulativeAllocation = 0
        for (treatment in experiment.treatmentGroups) {
            cumulativeAllocation += treatment.allocationPercentage
            if (bucket < cumulativeAllocation) {
                return treatment
            }
        }
        
        // Fallback to last treatment (should not happen if allocations sum to 100)
        return experiment.treatmentGroups.last()
    }
    
    /**
     * Generates a SHA-256 hash of the input string and returns a positive long value.
     *
     * @param input The string to hash
     * @return A positive long value derived from the hash
     */
    private fun sha256Hash(input: String): Long {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        
        // Convert first 8 bytes to long
        var hash = 0L
        for (i in 0 until 8) {
            hash = (hash shl 8) or (hashBytes[i].toLong() and 0xFF)
        }
        
        // Return absolute value to ensure positive
        return if (hash < 0) -hash else hash
    }
}
