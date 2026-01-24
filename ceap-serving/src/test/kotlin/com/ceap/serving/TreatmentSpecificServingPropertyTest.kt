package com.ceap.serving

import com.ceap.model.*
import com.ceap.model.config.ExperimentConfig
import com.ceap.model.config.TreatmentGroup
import net.jqwik.api.*
import net.jqwik.api.arbitraries.ListArbitrary
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant

/**
 * Property-based tests for treatment-specific candidate serving.
 * 
 * **Feature: ceap-platform, Property 48: Treatment-specific candidate serving**
 * **Validates: Requirements 15.5**
 */
class TreatmentSpecificServingPropertyTest {
    
    /**
     * Property 48: Treatment-specific candidate serving
     * 
     * For any serving request during an active A/B test, the returned candidates
     * must match the customer's assigned treatment group.
     * 
     * This property verifies that:
     * 1. Treatment assignment is deterministic for the same customer
     * 2. Candidates are filtered according to treatment configuration
     * 3. Only candidates matching treatment criteria are returned
     * 4. Treatment assignment is consistent across multiple requests
     */
    @Property(tries = 100)
    fun treatmentAssignmentIsDeterministic(
        @ForAll("customerId") customerId: String,
        @ForAll("experimentId") experimentId: String,
        @ForAll("experiment") experiment: ExperimentConfig
    ) {
        val assignment = TreatmentAssignment()
        assignment.configureExperiments(listOf(experiment))
        
        // Assign treatment multiple times
        val treatment1 = assignment.assignTreatment(customerId, experimentId)
        val treatment2 = assignment.assignTreatment(customerId, experimentId)
        val treatment3 = assignment.assignTreatment(customerId, experimentId)
        
        // All assignments should be the same
        assertThat(treatment1).isEqualTo(treatment2)
        assertThat(treatment2).isEqualTo(treatment3)
        
        // Treatment should be one of the configured treatments
        if (treatment1 != null) {
            val treatmentIds = experiment.treatmentGroups.map { it.treatmentId }
            assertThat(treatmentIds).contains(treatment1)
        }
    }
    
    /**
     * Property: Different customers get distributed across treatments
     * 
     * When multiple customers are assigned to an experiment, they should be
     * distributed across the available treatments (not all in one treatment).
     */
    @Property(tries = 100)
    fun treatmentsAreDistributedAcrossCustomers(
        @ForAll("customerIds") customerIds: List<String>,
        @ForAll("experiment") experiment: ExperimentConfig
    ) {
        Assume.that(customerIds.size >= 10)
        Assume.that(experiment.treatmentGroups.size >= 2)
        
        val assignment = TreatmentAssignment()
        assignment.configureExperiments(listOf(experiment))
        
        // Assign treatments to all customers
        val assignments = customerIds.mapNotNull { customerId ->
            assignment.assignTreatment(customerId, experiment.experimentId)
        }
        
        // Count assignments per treatment
        val treatmentCounts = assignments.groupingBy { it }.eachCount()
        
        // At least 2 different treatments should be assigned
        assertThat(treatmentCounts.keys.size).isGreaterThanOrEqualTo(2)
        
        // No treatment should have 0 assignments (with enough customers)
        experiment.treatmentGroups.forEach { treatment ->
            val count = treatmentCounts[treatment.treatmentId] ?: 0
            // With 10+ customers and 2+ treatments, each should get at least 1
            assertThat(count).isGreaterThan(0)
        }
    }
    
    /**
     * Property: Candidates are filtered by treatment configuration
     * 
     * When filtering candidates by treatment, only candidates that match
     * the treatment's criteria should be returned.
     */
    @Property(tries = 100)
    fun candidatesAreFilteredByTreatment(
        @ForAll("candidates") candidates: List<Candidate>,
        @ForAll("customerId") customerId: String,
        @ForAll("experimentWithFilters") experiment: ExperimentConfig
    ) {
        Assume.that(candidates.isNotEmpty())
        
        val assignment = TreatmentAssignment()
        assignment.configureExperiments(listOf(experiment))
        
        // Filter candidates by treatment
        val filtered = assignment.filterByTreatment(candidates, customerId, "test-program")
        
        // Get the assigned treatment
        val treatmentId = assignment.assignTreatment(customerId, experiment.experimentId)
        val treatment = experiment.treatmentGroups.find { it.treatmentId == treatmentId }
        
        if (treatment != null) {
            // All filtered candidates should match treatment criteria
            filtered.forEach { candidate ->
                val config = treatment.config ?: emptyMap()
                
                // Check channel filter
                val channelFilter = config["channelFilter"] as? String
                if (channelFilter != null) {
                    val channelEligible = candidate.attributes.channelEligibility[channelFilter]
                    assertThat(channelEligible).isTrue()
                }
                
                // Check score threshold
                val scoreThreshold = (config["scoreThreshold"] as? Number)?.toDouble()
                if (scoreThreshold != null) {
                    val maxScore = candidate.scores?.values?.maxOfOrNull { it.value } ?: 0.0
                    assertThat(maxScore).isGreaterThanOrEqualTo(scoreThreshold)
                }
                
                // Check subject type filter
                @Suppress("UNCHECKED_CAST")
                val subjectTypeFilter = config["subjectTypeFilter"] as? List<String>
                if (subjectTypeFilter != null) {
                    assertThat(subjectTypeFilter).contains(candidate.subject.type)
                }
            }
            
            // Filtered list should be a subset of original
            assertThat(candidates).containsAll(filtered)
        }
    }
    
    /**
     * Property: Treatment assignment is consistent across multiple filter calls
     * 
     * The same customer should get the same treatment assignment and
     * the same filtered candidates across multiple calls.
     */
    @Property(tries = 100)
    fun treatmentFilteringIsConsistent(
        @ForAll("candidates") candidates: List<Candidate>,
        @ForAll("customerId") customerId: String,
        @ForAll("experiment") experiment: ExperimentConfig
    ) {
        Assume.that(candidates.isNotEmpty())
        
        val assignment = TreatmentAssignment()
        assignment.configureExperiments(listOf(experiment))
        
        // Filter candidates multiple times
        val filtered1 = assignment.filterByTreatment(candidates, customerId, "test-program")
        val filtered2 = assignment.filterByTreatment(candidates, customerId, "test-program")
        val filtered3 = assignment.filterByTreatment(candidates, customerId, "test-program")
        
        // All filtered lists should be identical
        assertThat(filtered1).isEqualTo(filtered2)
        assertThat(filtered2).isEqualTo(filtered3)
    }
    
    /**
     * Property: Disabled experiments don't affect candidate filtering
     * 
     * When an experiment is disabled, candidates should not be filtered.
     */
    @Property(tries = 100)
    fun disabledExperimentsDoNotFilter(
        @ForAll("candidates") candidates: List<Candidate>,
        @ForAll("customerId") customerId: String,
        @ForAll("disabledExperiment") experiment: ExperimentConfig
    ) {
        Assume.that(candidates.isNotEmpty())
        Assume.that(!experiment.enabled)
        
        val assignment = TreatmentAssignment()
        assignment.configureExperiments(listOf(experiment))
        
        // Filter candidates
        val filtered = assignment.filterByTreatment(candidates, customerId, "test-program")
        
        // All candidates should be returned (no filtering)
        assertThat(filtered).isEqualTo(candidates)
    }
    
    // ========== Arbitraries ==========
    
    @Provide
    fun candidates(): ListArbitrary<Candidate> {
        return Arbitraries.of(
            createCandidate("product", 0.8, mapOf("email" to true, "in-app" to true)),
            createCandidate("video", 0.6, mapOf("email" to true, "push" to true)),
            createCandidate("service", 0.9, mapOf("in-app" to true, "voice" to true)),
            createCandidate("product", 0.4, mapOf("email" to true, "in-app" to false)),
            createCandidate("track", 0.7, mapOf("push" to true, "voice" to true))
        ).list().ofMinSize(1).ofMaxSize(10)
    }
    
    @Provide
    fun customerId(): Arbitrary<String> {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(5)
            .ofMaxLength(20)
    }
    
    @Provide
    fun customerIds(): ListArbitrary<String> {
        return Arbitraries.strings()
            .alpha()
            .ofMinLength(5)
            .ofMaxLength(20)
            .list()
            .ofMinSize(10)
            .ofMaxSize(50)
            .uniqueElements()
    }
    
    @Provide
    fun experimentId(): Arbitrary<String> {
        return Arbitraries.of("exp-1", "exp-2", "exp-3")
    }
    
    @Provide
    fun experiment(): Arbitrary<ExperimentConfig> {
        return Combinators.combine(
            Arbitraries.of("exp-1", "exp-2", "exp-3"),
            Arbitraries.of("Experiment 1", "Experiment 2", "Experiment 3"),
            treatmentGroups()
        ).`as` { expId, expName, treatments ->
            ExperimentConfig(
                experimentId = expId,
                experimentName = expName,
                enabled = true,
                startDate = Instant.now(),
                endDate = null,
                treatmentGroups = treatments,
                experimentType = "channel",
                description = "Test experiment"
            )
        }
    }
    
    @Provide
    fun experimentWithFilters(): Arbitrary<ExperimentConfig> {
        return Combinators.combine(
            Arbitraries.of("exp-filter-1", "exp-filter-2"),
            Arbitraries.of("Filter Experiment 1", "Filter Experiment 2"),
            treatmentGroupsWithFilters()
        ).`as` { expId, expName, treatments ->
            ExperimentConfig(
                experimentId = expId,
                experimentName = expName,
                enabled = true,
                startDate = Instant.now(),
                endDate = null,
                treatmentGroups = treatments,
                experimentType = "channel",
                description = "Test experiment with filters"
            )
        }
    }
    
    @Provide
    fun disabledExperiment(): Arbitrary<ExperimentConfig> {
        return experiment().map { it.copy(enabled = false) }
    }
    
    @Provide
    fun treatmentGroups(): Arbitrary<List<TreatmentGroup>> {
        return Arbitraries.of(
            listOf(
                TreatmentGroup(
                    treatmentId = "control",
                    treatmentName = "Control",
                    allocationPercentage = 50,
                    config = null
                ),
                TreatmentGroup(
                    treatmentId = "treatment-a",
                    treatmentName = "Treatment A",
                    allocationPercentage = 50,
                    config = null
                )
            ),
            listOf(
                TreatmentGroup(
                    treatmentId = "control",
                    treatmentName = "Control",
                    allocationPercentage = 34,
                    config = null
                ),
                TreatmentGroup(
                    treatmentId = "treatment-a",
                    treatmentName = "Treatment A",
                    allocationPercentage = 33,
                    config = null
                ),
                TreatmentGroup(
                    treatmentId = "treatment-b",
                    treatmentName = "Treatment B",
                    allocationPercentage = 33,
                    config = null
                )
            )
        )
    }
    
    @Provide
    fun treatmentGroupsWithFilters(): Arbitrary<List<TreatmentGroup>> {
        return Arbitraries.just(
            listOf(
                TreatmentGroup(
                    treatmentId = "control",
                    treatmentName = "Control",
                    allocationPercentage = 34,
                    config = null
                ),
                TreatmentGroup(
                    treatmentId = "high-score",
                    treatmentName = "High Score Treatment",
                    allocationPercentage = 33,
                    config = mapOf(
                        "channelFilter" to "email",
                        "scoreThreshold" to 0.7
                    )
                ),
                TreatmentGroup(
                    treatmentId = "product-only",
                    treatmentName = "Product Only Treatment",
                    allocationPercentage = 33,
                    config = mapOf(
                        "subjectTypeFilter" to listOf("product")
                    )
                )
            )
        )
    }
    
    // ========== Helper Methods ==========
    
    private fun createCandidate(
        subjectType: String,
        score: Double,
        channelEligibility: Map<String, Boolean>
    ): Candidate {
        val now = Instant.now()
        return Candidate(
            customerId = "customer-${System.nanoTime()}",
            context = listOf(
                Context(type = "marketplace", id = "US"),
                Context(type = "program", id = "test-program")
            ),
            subject = Subject(
                type = subjectType,
                id = "subject-${System.nanoTime()}"
            ),
            scores = mapOf(
                "model-1" to Score(
                    modelId = "model-1",
                    value = score,
                    confidence = 0.9,
                    timestamp = now
                )
            ),
            attributes = CandidateAttributes(
                eventDate = now,
                channelEligibility = channelEligibility
            ),
            metadata = CandidateMetadata(
                createdAt = now,
                updatedAt = now,
                expiresAt = now.plusSeconds(86400),
                version = 1,
                sourceConnectorId = "test-connector",
                workflowExecutionId = "test-execution"
            )
        )
    }
}
