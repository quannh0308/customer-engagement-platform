package com.ceap.workflow.reactive

import com.ceap.model.Candidate
import com.ceap.model.CandidateAttributes
import com.ceap.model.CandidateMetadata
import com.ceap.model.Context
import com.ceap.model.Subject
import com.ceap.storage.CandidateRepository
import com.ceap.storage.BatchWriteResult
import com.ceap.storage.FailedItem
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant

/**
 * Property-based tests for opt-out candidate deletion.
 * 
 * **Property 56: Opt-out candidate deletion**
 * **Validates: Requirements 18.5**
 * 
 * For any customer who opts out, all their candidates must be deleted
 * within 24 hours (typically immediately).
 */
class OptOutCandidateDeletionPropertyTest {
    
    /**
     * Property: All candidates for opted-out customer are deleted.
     * 
     * For any customer with N candidates in a program, after opt-out:
     * - All N candidates must be deleted
     * - Query for customer should return empty list
     */
    @Property(tries = 10)
    fun `all candidates for opted out customer are deleted`(
        @ForAll customerId: String,
        @ForAll programId: String
    ) {
        Assume.that(customerId.isNotEmpty())
        Assume.that(programId.isNotEmpty())
        
        // Create mock repository
        val repository = InMemoryCandidateRepository()
        
        // Create a fixed number of candidates for testing
        val candidateCount = 3
        val customerCandidates = (1..candidateCount).map { i ->
            candidate().copy(
                customerId = customerId,
                subject = candidate().subject.copy(id = "SUBJ$i"),
                context = listOf(
                    Context(type = "program", id = programId),
                    Context(type = "marketplace", id = "US")
                )
            )
        }
        
        customerCandidates.forEach { repository.create(it) }
        
        // Verify candidates exist
        val beforeOptOut = repository.queryByCustomerId(customerId)
        assertThat(beforeOptOut).hasSize(candidateCount)
        
        // Create opt-out handler
        val handler = OptOutHandler(repository)
        
        // Create opt-out event with programId
        val optOutEvent = mapOf(
            "detail" to mapOf(
                "customerId" to customerId,
                "eventType" to "OPT_OUT",
                "programId" to programId,
                "timestamp" to Instant.now().toString()
            )
        )
        
        // Process opt-out
        val response = handler.handleRequest(optOutEvent, MockLambdaContext())
        
        // Verify operation succeeded
        assertThat(response.success).isTrue()
        
        // Verify all candidates deleted
        assertThat(response.deletedCount).isEqualTo(candidateCount)
        
        // Verify query returns empty
        val afterOptOut = repository.queryByCustomerId(customerId)
        assertThat(afterOptOut).isEmpty()
    }
    
    /**
     * Property: Program-specific opt-out only deletes candidates for that program.
     * 
     * For any customer with candidates in multiple programs, after program-specific opt-out:
     * - Only candidates for that program are deleted
     * - Candidates for other programs remain
     */
    @Property(tries = 10)
    fun `program specific opt out only deletes candidates for that program`(
        @ForAll customerId: String,
        @ForAll programId: String,
        @ForAll otherProgramId: String
    ) {
        Assume.that(customerId.isNotEmpty())
        Assume.that(programId.isNotEmpty())
        Assume.that(otherProgramId.isNotEmpty())
        Assume.that(programId != otherProgramId)
        
        val repository = InMemoryCandidateRepository()
        
        // Create candidates for target program
        val targetCount = 2
        val targetProgramCandidates = (1..targetCount).map { i ->
            candidate().copy(
                customerId = customerId,
                subject = candidate().subject.copy(id = "TARGET$i"),
                context = listOf(
                    Context(type = "program", id = programId),
                    Context(type = "marketplace", id = "US")
                )
            )
        }
        
        // Create candidates for other program
        val otherCount = 2
        val otherProgramCandidates = (1..otherCount).map { i ->
            candidate().copy(
                customerId = customerId,
                subject = candidate().subject.copy(id = "OTHER$i"),
                context = listOf(
                    Context(type = "program", id = otherProgramId),
                    Context(type = "marketplace", id = "US")
                )
            )
        }
        
        // Store all candidates
        (targetProgramCandidates + otherProgramCandidates).forEach { repository.create(it) }
        
        // Verify all candidates exist
        val beforeOptOut = repository.queryByCustomerId(customerId)
        assertThat(beforeOptOut).hasSize(targetCount + otherCount)
        
        // Create opt-out handler
        val handler = OptOutHandler(repository)
        
        // Create program-specific opt-out event
        val optOutEvent = mapOf(
            "detail" to mapOf(
                "customerId" to customerId,
                "eventType" to "OPT_OUT",
                "programId" to programId,
                "timestamp" to Instant.now().toString()
            )
        )
        
        // Process opt-out
        val response = handler.handleRequest(optOutEvent, MockLambdaContext())
        
        // Verify only target program candidates deleted
        assertThat(response.success).isTrue()
        assertThat(response.deletedCount).isEqualTo(targetCount)
        
        // Verify other program candidates remain
        val afterOptOut = repository.queryByCustomerId(customerId)
        assertThat(afterOptOut).hasSize(otherCount)
        
        // Verify remaining candidates are from other program
        afterOptOut.forEach { candidate ->
            val programContext = candidate.context.find { it.type == "program" }
            assertThat(programContext?.id).isEqualTo(otherProgramId)
        }
    }
    
    /**
     * Property: Opt-out deletion completes within reasonable time.
     * 
     * For any customer with candidates, opt-out deletion should complete quickly
     * (well within 24 hour requirement).
     */
    @Property
    fun `opt out deletion completes within reasonable time`(
        @ForAll customerId: String,
        @ForAll programId: String,
        @ForAll("candidateList") candidates: List<Candidate>
    ) {
        Assume.that(customerId.isNotEmpty())
        Assume.that(programId.isNotEmpty())
        Assume.that(candidates.isNotEmpty())
        Assume.that(candidates.size <= 20)
        
        val repository = InMemoryCandidateRepository()
        
        // Store candidates
        val customerCandidates = candidates.map { 
            it.copy(
                customerId = customerId,
                context = listOf(
                    Context(type = "program", id = programId),
                    Context(type = "marketplace", id = "US")
                )
            )
        }
        customerCandidates.forEach { repository.create(it) }
        
        val handler = OptOutHandler(repository)
        
        val optOutEvent = mapOf(
            "detail" to mapOf(
                "customerId" to customerId,
                "eventType" to "OPT_OUT",
                "programId" to programId,
                "timestamp" to Instant.now().toString()
            )
        )
        
        // Process opt-out and measure time
        val response = handler.handleRequest(optOutEvent, MockLambdaContext())
        
        // Verify completed successfully
        assertThat(response.success).isTrue()
        
        // Verify completed within reasonable time (24 hours = 86400000 ms)
        // For in-memory operations, should be much faster (< 1 second)
        assertThat(response.executionTimeMs).isLessThan(86400000L)
        
        // For practical purposes, should be very fast
        assertThat(response.executionTimeMs).isLessThan(5000L) // 5 seconds
    }
    
    /**
     * Property: Opt-out for non-existent customer succeeds with zero deletions.
     * 
     * For any customer with no candidates, opt-out should succeed gracefully.
     */
    @Property
    fun `opt out for non existent customer succeeds with zero deletions`(
        @ForAll customerId: String,
        @ForAll programId: String
    ) {
        Assume.that(customerId.isNotEmpty())
        Assume.that(programId.isNotEmpty())
        
        val repository = InMemoryCandidateRepository()
        val handler = OptOutHandler(repository)
        
        val optOutEvent = mapOf(
            "detail" to mapOf(
                "customerId" to customerId,
                "eventType" to "OPT_OUT",
                "programId" to programId,
                "timestamp" to Instant.now().toString()
            )
        )
        
        val response = handler.handleRequest(optOutEvent, MockLambdaContext())
        
        // Should succeed with zero deletions
        assertThat(response.success).isTrue()
        assertThat(response.deletedCount).isEqualTo(0)
    }
    
    /**
     * Property: Multiple opt-outs for same customer are idempotent.
     * 
     * For any customer, processing opt-out multiple times should be safe.
     */
    @Property(tries = 10)
    fun `multiple opt outs for same customer are idempotent`(
        @ForAll customerId: String,
        @ForAll programId: String
    ) {
        Assume.that(customerId.isNotEmpty())
        Assume.that(programId.isNotEmpty())
        
        val repository = InMemoryCandidateRepository()
        
        // Create fixed candidates
        val candidateCount = 3
        val customerCandidates = (1..candidateCount).map { i ->
            candidate().copy(
                customerId = customerId,
                subject = candidate().subject.copy(id = "SUBJ$i"),
                context = listOf(
                    Context(type = "program", id = programId),
                    Context(type = "marketplace", id = "US")
                )
            )
        }
        customerCandidates.forEach { repository.create(it) }
        
        val handler = OptOutHandler(repository)
        
        val optOutEvent = mapOf(
            "detail" to mapOf(
                "customerId" to customerId,
                "eventType" to "OPT_OUT",
                "programId" to programId,
                "timestamp" to Instant.now().toString()
            )
        )
        
        // First opt-out
        val response1 = handler.handleRequest(optOutEvent, MockLambdaContext())
        assertThat(response1.success).isTrue()
        assertThat(response1.deletedCount).isEqualTo(candidateCount)
        
        // Second opt-out (should be idempotent)
        val response2 = handler.handleRequest(optOutEvent, MockLambdaContext())
        assertThat(response2.success).isTrue()
        assertThat(response2.deletedCount).isEqualTo(0) // Nothing to delete
        
        // Verify still empty
        val afterSecondOptOut = repository.queryByCustomerId(customerId)
        assertThat(afterSecondOptOut).isEmpty()
    }
    
    // Arbitrary generators
    
    @Provide
    fun candidateList(): Arbitrary<List<Candidate>> {
        return Arbitraries.integers().between(1, 10).flatMap { size ->
            Arbitraries.create { candidate() }.list().ofSize(size)
        }
    }
    
    private fun candidate(): Candidate {
        val now = Instant.now()
        return Candidate(
            customerId = "CUST${(1000..9999).random()}",
            context = listOf(
                Context(type = "marketplace", id = "US"),
                Context(type = "program", id = "program-${(1..5).random()}")
            ),
            subject = Subject(
                type = "product",
                id = "PROD${(1000..9999).random()}",
                metadata = null
            ),
            scores = null,
            attributes = CandidateAttributes(
                eventDate = now,
                deliveryDate = null,
                timingWindow = null,
                orderValue = null,
                mediaEligible = null,
                channelEligibility = mapOf("email" to true)
            ),
            metadata = CandidateMetadata(
                createdAt = now,
                updatedAt = now,
                expiresAt = now.plusSeconds(30 * 24 * 60 * 60),
                version = 1L,
                sourceConnectorId = "test",
                workflowExecutionId = "test-exec"
            ),
            rejectionHistory = null
        )
    }
}

/**
 * In-memory candidate repository for testing.
 */
class InMemoryCandidateRepository : CandidateRepository {
    private val candidates = mutableMapOf<String, Candidate>()
    
    // Helper method for tests
    fun queryByCustomerId(customerId: String): List<Candidate> {
        return candidates.values.filter { it.customerId == customerId }
    }
    
    override fun create(candidate: Candidate): Candidate {
        val key = makeKey(candidate)
        candidates[key] = candidate
        return candidate
    }
    
    override fun get(
        customerId: String,
        programId: String,
        marketplaceId: String,
        subjectType: String,
        subjectId: String
    ): Candidate? {
        val key = "$customerId:$programId:$marketplaceId:$subjectType:$subjectId"
        return candidates[key]
    }
    
    override fun update(candidate: Candidate): Candidate {
        val key = makeKey(candidate)
        candidates[key] = candidate.copy(
            metadata = candidate.metadata.copy(version = candidate.metadata.version + 1)
        )
        return candidates[key]!!
    }
    
    override fun delete(
        customerId: String,
        programId: String,
        marketplaceId: String,
        subjectType: String,
        subjectId: String
    ) {
        val key = "$customerId:$programId:$marketplaceId:$subjectType:$subjectId"
        candidates.remove(key)
    }
    
    override fun batchWrite(candidates: List<Candidate>): BatchWriteResult {
        val successful = mutableListOf<Candidate>()
        val failed = mutableListOf<FailedItem>()
        
        for (candidate in candidates) {
            try {
                create(candidate)
                successful.add(candidate)
            } catch (e: Exception) {
                failed.add(FailedItem(candidate, e.message ?: "Unknown error"))
            }
        }
        
        return BatchWriteResult(successful, failed)
    }
    
    override fun queryByProgramAndChannel(
        programId: String,
        channelId: String,
        limit: Int,
        ascending: Boolean
    ): List<Candidate> {
        return candidates.values
            .filter { candidate ->
                candidate.context.any { ctx -> ctx.type == "program" && ctx.id == programId }
            }
            .take(limit)
    }
    
    override fun queryByProgramAndDate(
        programId: String,
        date: String,
        limit: Int
    ): List<Candidate> {
        return candidates.values
            .filter { candidate ->
                candidate.context.any { ctx -> ctx.type == "program" && ctx.id == programId }
            }
            .take(limit)
    }
    
    private fun makeKey(candidate: Candidate): String {
        val programContext = candidate.context.find { it.type == "program" }
        val marketplaceContext = candidate.context.find { it.type == "marketplace" }
        return "${candidate.customerId}:${programContext?.id}:${marketplaceContext?.id}:${candidate.subject.type}:${candidate.subject.id}"
    }
}
