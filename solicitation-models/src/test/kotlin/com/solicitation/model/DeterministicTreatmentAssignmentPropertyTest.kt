package com.solicitation.model

import com.solicitation.model.config.ExperimentConfig
import com.solicitation.model.config.TreatmentGroup
import net.jqwik.api.*
import net.jqwik.api.constraints.IntRange
import net.jqwik.api.constraints.NotBlank
import net.jqwik.api.constraints.Size
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Property-based tests for deterministic treatment assignment.
 *
 * **Validates: Requirements 11.1**
 */
class DeterministicTreatmentAssignmentPropertyTest {
    
    /**
     * Property 34: Deterministic treatment assignment
     *
     * For any customer and experiment, the assigned treatment group must be
     * deterministic (same customer always gets same treatment) and stable
     * across multiple requests.
     */
    @Property(tries = 100)
    fun `treatment assignment is deterministic for same customer and experiment`(
        @ForAll @NotBlank customerId: String,
        @ForAll("experimentConfig") experiment: ExperimentConfig
    ) {
        // Assign treatment multiple times
        val treatment1 = ExperimentAssignment.assignTreatment(customerId, experiment)
        val treatment2 = ExperimentAssignment.assignTreatment(customerId, experiment)
        val treatment3 = ExperimentAssignment.assignTreatment(customerId, experiment)
        
        // All assignments should be identical
        assertEquals(treatment1, treatment2, "Treatment assignment should be deterministic")
        assertEquals(treatment2, treatment3, "Treatment assignment should be stable")
    }
    
    /**
     * Property: Treatment assignment respects allocation percentages
     *
     * Over many customers, the distribution of treatments should approximately
     * match the configured allocation percentages.
     */
    @Property(tries = 10)
    fun `treatment assignment respects allocation percentages`(
        @ForAll("experimentConfig") experiment: ExperimentConfig
    ) {
        if (!experiment.enabled || experiment.treatmentGroups.isEmpty()) {
            return
        }
        
        // Generate 1000 customer IDs and assign treatments
        val assignments = (1..1000).map { i ->
            val customerId = "customer-$i"
            ExperimentAssignment.assignTreatment(customerId, experiment)
        }
        
        // Count assignments per treatment
        val treatmentCounts = assignments
            .filterNotNull()
            .groupingBy { it.treatmentId }
            .eachCount()
        
        // Verify each treatment got approximately the right percentage (within 5% tolerance)
        for (treatment in experiment.treatmentGroups) {
            val count = treatmentCounts[treatment.treatmentId] ?: 0
            val actualPercentage = (count.toDouble() / 1000.0) * 100.0
            val expectedPercentage = treatment.allocationPercentage.toDouble()
            val tolerance = 5.0
            
            val withinTolerance = Math.abs(actualPercentage - expectedPercentage) <= tolerance
            assert(withinTolerance) {
                "Treatment ${treatment.treatmentId} expected ${expectedPercentage}% but got ${actualPercentage}%"
            }
        }
    }
    
    /**
     * Property: Different customers get potentially different treatments
     *
     * For a 50/50 split, we should see both treatments assigned across
     * a reasonable sample of customers.
     */
    @Property(tries = 10)
    fun `different customers can get different treatments`(
        @ForAll("experimentConfig") experiment: ExperimentConfig
    ) {
        if (!experiment.enabled || experiment.treatmentGroups.size < 2) {
            return
        }
        
        // Generate 100 customer IDs and assign treatments
        val treatments = (1..100).map { i ->
            val customerId = "customer-$i"
            ExperimentAssignment.assignTreatment(customerId, experiment)
        }.filterNotNull()
        
        // Should have at least 2 different treatments assigned
        val uniqueTreatments = treatments.map { it.treatmentId }.toSet()
        assert(uniqueTreatments.size >= 2) {
            "Expected multiple treatments to be assigned, but only got: $uniqueTreatments"
        }
    }
    
    // Arbitraries
    
    @Provide
    fun experimentConfig(): Arbitrary<ExperimentConfig> {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(30),
            Arbitraries.of(true, false),
            Arbitraries.longs().between(
                Instant.now().minusSeconds(86400).toEpochMilli(),
                Instant.now().toEpochMilli()
            ),
            treatmentGroups(),
            Arbitraries.of("timing", "channel", "scoring", "messaging")
        ).`as` { id, name, enabled, startMillis, treatments, type ->
            ExperimentConfig(
                experimentId = id,
                experimentName = name,
                enabled = enabled,
                startDate = Instant.ofEpochMilli(startMillis),
                endDate = null,
                treatmentGroups = treatments,
                experimentType = type,
                description = "Test experiment"
            )
        }
    }
    
    @Provide
    fun treatmentGroups(): Arbitrary<List<TreatmentGroup>> {
        return Arbitraries.integers().between(2, 4).flatMap { numTreatments ->
            // Generate allocations that sum to 100
            val allocations = generateAllocations(numTreatments)
            
            Arbitraries.shuffle(allocations).map { allocs ->
                allocs.mapIndexed { index, allocation ->
                    TreatmentGroup(
                        treatmentId = "treatment-$index",
                        treatmentName = "Treatment $index",
                        allocationPercentage = allocation,
                        config = null
                    )
                }
            }
        }
    }
    
    private fun generateAllocations(numTreatments: Int): List<Int> {
        if (numTreatments == 1) return listOf(100)
        if (numTreatments == 2) return listOf(50, 50)
        if (numTreatments == 3) return listOf(34, 33, 33)
        return listOf(25, 25, 25, 25)
    }
}
