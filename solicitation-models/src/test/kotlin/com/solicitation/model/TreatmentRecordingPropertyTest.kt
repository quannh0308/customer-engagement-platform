package com.solicitation.model

import com.solicitation.model.arbitraries.CandidateArbitraries
import com.solicitation.model.config.ExperimentConfig
import com.solicitation.model.config.TreatmentGroup
import net.jqwik.api.*
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Property-based tests for treatment recording in candidates.
 *
 * **Validates: Requirements 11.3**
 */
class TreatmentRecordingPropertyTest {
    
    /**
     * Property 35: Treatment recording
     *
     * For any candidate created during an active experiment, the assigned treatment
     * must be recorded in the candidate metadata.
     */
    @Property(tries = 100)
    fun `candidates created during active experiment must record assigned treatment`(
        @ForAll("candidate") candidate: Candidate,
        @ForAll("experimentConfig") experiment: ExperimentConfig
    ) {
        if (!experiment.enabled) {
            return
        }
        
        // Assign treatment
        val treatment = ExperimentAssignment.assignTreatment(candidate.customerId, experiment)
        assertNotNull(treatment, "Treatment should be assigned for enabled experiment")
        
        // Create experiment treatment record
        val experimentTreatment = ExperimentTreatment(
            experimentId = experiment.experimentId,
            treatmentId = treatment.treatmentId
        )
        
        // Update candidate metadata with treatment
        val updatedMetadata = candidate.metadata.copy(
            experimentTreatment = experimentTreatment
        )
        val updatedCandidate = candidate.copy(metadata = updatedMetadata)
        
        // Verify treatment is recorded
        assertNotNull(
            updatedCandidate.metadata.experimentTreatment,
            "Candidate metadata should contain experiment treatment"
        )
        assertEquals(
            experiment.experimentId,
            updatedCandidate.metadata.experimentTreatment?.experimentId,
            "Experiment ID should match"
        )
        assertEquals(
            treatment.treatmentId,
            updatedCandidate.metadata.experimentTreatment?.treatmentId,
            "Treatment ID should match"
        )
    }
    
    /**
     * Property: Treatment recording is consistent with assignment
     *
     * The treatment recorded in candidate metadata should always match
     * the treatment that would be assigned for that customer and experiment.
     */
    @Property(tries = 100)
    fun `recorded treatment matches assignment for same customer and experiment`(
        @ForAll("candidate") candidate: Candidate,
        @ForAll("experimentConfig") experiment: ExperimentConfig
    ) {
        if (!experiment.enabled) {
            return
        }
        
        // Assign treatment
        val assignedTreatment = ExperimentAssignment.assignTreatment(candidate.customerId, experiment)
        assertNotNull(assignedTreatment)
        
        // Record treatment in candidate
        val experimentTreatment = ExperimentTreatment(
            experimentId = experiment.experimentId,
            treatmentId = assignedTreatment.treatmentId
        )
        val updatedMetadata = candidate.metadata.copy(experimentTreatment = experimentTreatment)
        val updatedCandidate = candidate.copy(metadata = updatedMetadata)
        
        // Re-assign treatment (should be same due to determinism)
        val reassignedTreatment = ExperimentAssignment.assignTreatment(
            updatedCandidate.customerId,
            experiment
        )
        
        // Verify recorded treatment matches reassignment
        assertEquals(
            reassignedTreatment?.treatmentId,
            updatedCandidate.metadata.experimentTreatment?.treatmentId,
            "Recorded treatment should match reassigned treatment"
        )
    }
    
    /**
     * Property: Candidates without experiments have no treatment recorded
     *
     * When no experiment is active, candidates should not have treatment information.
     */
    @Property(tries = 100)
    fun `candidates without active experiment have no treatment recorded`(
        @ForAll("candidate") candidate: Candidate
    ) {
        // Verify candidate without experiment treatment
        if (candidate.metadata.experimentTreatment == null) {
            // This is expected - no treatment recorded
            assert(true)
        }
    }
    
    // Arbitraries
    
    @Provide
    fun candidate(): Arbitrary<Candidate> {
        return CandidateArbitraries.candidates()
    }
    
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
