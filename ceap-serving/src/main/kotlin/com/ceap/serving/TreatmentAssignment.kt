package com.ceap.serving

import com.ceap.model.Candidate
import com.ceap.model.config.ExperimentConfig
import com.ceap.model.config.TreatmentGroup
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Treatment assignment for A/B testing.
 * 
 * Provides deterministic assignment of customers to treatment groups
 * and filtering of candidates based on treatment assignments.
 * 
 * **Validates: Requirements 11.1, 15.5**
 */
class TreatmentAssignment {
    
    /**
     * Active experiments configuration.
     * Key: experimentId, Value: experiment configuration
     */
    private val activeExperiments = mutableMapOf<String, ExperimentConfig>()
    
    /**
     * Configures active experiments.
     * 
     * @param experiments List of experiment configurations
     */
    fun configureExperiments(experiments: List<ExperimentConfig>) {
        activeExperiments.clear()
        experiments.forEach { experiment ->
            activeExperiments[experiment.experimentId] = experiment
            logger.info {
                "Configured experiment ${experiment.experimentId}: " +
                "treatments=${experiment.treatmentGroups.map { it.treatmentId }}, " +
                "enabled=${experiment.enabled}"
            }
        }
    }
    
    /**
     * Assigns a customer to a treatment group for an experiment.
     * 
     * Assignment is deterministic based on customer ID and experiment ID,
     * ensuring the same customer always gets the same treatment.
     * 
     * @param customerId Customer identifier
     * @param experimentId Experiment identifier
     * @return Treatment ID, or null if experiment not found or disabled
     */
    fun assignTreatment(customerId: String, experimentId: String): String? {
        val experiment = activeExperiments[experimentId]
        if (experiment == null || !experiment.enabled) {
            return null
        }
        
        // Use existing ExperimentAssignment logic
        val treatment = com.ceap.model.ExperimentAssignment.assignTreatment(customerId, experiment)
        
        logger.debug {
            "Assigned customer $customerId to treatment ${treatment?.treatmentId} " +
            "for experiment $experimentId"
        }
        
        return treatment?.treatmentId
    }
    
    /**
     * Gets the experiment assignment for a customer.
     * 
     * @param customerId Customer identifier
     * @param programId Program identifier (not used in current implementation, for future use)
     * @return Treatment group, or null if no active experiments
     */
    fun getExperimentAssignment(customerId: String, programId: String): TreatmentGroup? {
        // Find active experiment (for now, just use the first active one)
        val experiment = activeExperiments.values.firstOrNull { it.enabled } ?: return null
        
        return com.ceap.model.ExperimentAssignment.assignTreatment(customerId, experiment)
    }
    
    /**
     * Filters candidates based on treatment assignment.
     * 
     * Returns only candidates that match the customer's assigned treatment.
     * 
     * @param candidates List of candidates to filter
     * @param customerId Customer identifier
     * @param programId Program identifier
     * @return Filtered list of candidates matching the treatment
     */
    fun filterByTreatment(
        candidates: List<Candidate>,
        customerId: String,
        programId: String
    ): List<Candidate> {
        val treatment = getExperimentAssignment(customerId, programId) ?: return candidates
        
        // Apply treatment-specific filters from config
        val filtered = candidates.filter { candidate ->
            matchesTreatment(candidate, treatment)
        }
        
        logger.debug {
            "Filtered candidates by treatment ${treatment.treatmentId}: " +
            "originalCount=${candidates.size}, " +
            "filteredCount=${filtered.size}"
        }
        
        return filtered
    }
    
    /**
     * Checks if a candidate matches a treatment configuration.
     * 
     * @param candidate Candidate to check
     * @param treatment Treatment group
     * @return true if candidate matches treatment, false otherwise
     */
    private fun matchesTreatment(candidate: Candidate, treatment: TreatmentGroup): Boolean {
        // Get treatment config
        val config = treatment.config ?: return true
        
        // Check channel filter
        val channelFilter = config["channelFilter"] as? String
        if (channelFilter != null) {
            val channelEligible = candidate.attributes.channelEligibility[channelFilter]
            if (channelEligible != true) {
                return false
            }
        }
        
        // Check score threshold
        val scoreThreshold = (config["scoreThreshold"] as? Number)?.toDouble()
        if (scoreThreshold != null) {
            val maxScore = candidate.scores?.values?.maxOfOrNull { it.value } ?: 0.0
            if (maxScore < scoreThreshold) {
                return false
            }
        }
        
        // Check subject type filter
        @Suppress("UNCHECKED_CAST")
        val subjectTypeFilter = config["subjectTypeFilter"] as? List<String>
        if (subjectTypeFilter != null) {
            if (!subjectTypeFilter.contains(candidate.subject.type)) {
                return false
            }
        }
        
        return true
    }
}
