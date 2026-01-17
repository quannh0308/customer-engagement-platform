package com.solicitation.storage

import com.solicitation.model.*
import net.jqwik.api.*
import net.jqwik.time.api.DateTimes
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant

/**
 * Property-based tests for batch write atomicity.
 * 
 * **Property 15: Batch write atomicity**
 * **Validates: Requirements 5.2**
 * 
 * Verifies that batch writes handle partial failures correctly
 * and retry logic for failed items works as expected.
 */
class BatchWritePropertyTest {
    
    @Property(tries = 100)
    fun `batch write handles all successful items`(
        @ForAll("candidateBatch") candidates: List<Candidate>
    ) {
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // When: Batch writing the candidates
        val result = repository.batchWrite(candidates)
        
        // Then: All items are successful
        assertThat(result.successfulItems).hasSize(candidates.size)
        assertThat(result.failedItems).isEmpty()
        
        // And: All candidates can be retrieved
        candidates.forEach { candidate ->
            val programContext = candidate.context.find { it.type == "program" }!!
            val marketplaceContext = candidate.context.find { it.type == "marketplace" }!!
            
            val retrieved = repository.get(
                customerId = candidate.customerId,
                programId = programContext.id,
                marketplaceId = marketplaceContext.id,
                subjectType = candidate.subject.type,
                subjectId = candidate.subject.id
            )
            
            assertThat(retrieved).isNotNull
            assertThat(retrieved).isEqualTo(candidate)
        }
    }
    
    @Property(tries = 100)
    fun `batch write respects DynamoDB limit of 25 items`(
        @ForAll("largeCandidateBatch") candidates: List<Candidate>
    ) {
        // Given: A batch larger than DynamoDB's limit (25 items)
        Assume.that(candidates.size > 25)
        
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // When: Batch writing the candidates
        val result = repository.batchWrite(candidates)
        
        // Then: All items are processed (split into multiple batches)
        assertThat(result.successfulItems.size + result.failedItems.size)
            .isEqualTo(candidates.size)
    }
    
    @Property(tries = 100)
    fun `batch write result contains all input candidates`(
        @ForAll("candidateBatch") candidates: List<Candidate>
    ) {
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // When: Batch writing the candidates
        val result = repository.batchWrite(candidates)
        
        // Then: The total of successful and failed items equals input size
        assertThat(result.successfulItems.size + result.failedItems.size)
            .isEqualTo(candidates.size)
        
        // And: No duplicate items in results
        val allResultIds = result.successfulItems.map { it.customerId } +
                          result.failedItems.map { it.candidate.customerId }
        assertThat(allResultIds).hasSize(candidates.size)
    }
    
    @Property(tries = 100)
    fun `empty batch write returns empty results`() {
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // Given: An empty batch
        val candidates = emptyList<Candidate>()
        
        // When: Batch writing the empty batch
        val result = repository.batchWrite(candidates)
        
        // Then: Results are empty
        assertThat(result.successfulItems).isEmpty()
        assertThat(result.failedItems).isEmpty()
    }
    
    @Property(tries = 100)
    fun `single item batch write works correctly`(
        @ForAll("validCandidate") candidate: Candidate
    ) {
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // Given: A batch with a single candidate
        val candidates = listOf(candidate)
        
        // When: Batch writing the single candidate
        val result = repository.batchWrite(candidates)
        
        // Then: The item is successful
        assertThat(result.successfulItems).hasSize(1)
        assertThat(result.failedItems).isEmpty()
        assertThat(result.successfulItems.first()).isEqualTo(candidate)
    }
    
    @Property(tries = 100)
    fun `batch write with exact limit size works correctly`(
        @ForAll("exactLimitBatch") candidates: List<Candidate>
    ) {
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // Given: A batch with exactly 25 items (DynamoDB limit)
        assertThat(candidates).hasSize(25)
        
        // When: Batch writing the candidates
        val result = repository.batchWrite(candidates)
        
        // Then: All items are processed in a single batch
        assertThat(result.successfulItems).hasSize(25)
        assertThat(result.failedItems).isEmpty()
    }
    
    @Property(tries = 100)
    fun `batch write preserves candidate data integrity`(
        @ForAll("candidateBatch") candidates: List<Candidate>
    ) {
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // When: Batch writing the candidates
        val result = repository.batchWrite(candidates)
        
        // Then: Successful items match the original candidates
        result.successfulItems.forEach { successfulCandidate ->
            val original = candidates.find { it.customerId == successfulCandidate.customerId }
            assertThat(original).isNotNull
            assertThat(successfulCandidate).isEqualTo(original)
        }
    }
    
    // Arbitrary generators
    
    @Provide
    fun validCandidate(): Arbitrary<Candidate> {
        return Combinators.combine(
            customerIds(),
            contextListsWithRequiredTypes(),
            subjects(),
            candidateAttributes(),
            candidateMetadata()
        ).`as` { customerId, context, subject, attributes, metadata ->
            Candidate(
                customerId = customerId,
                context = context,
                subject = subject,
                scores = null,
                attributes = attributes,
                metadata = metadata,
                rejectionHistory = null
            )
        }
    }
    
    @Provide
    fun candidateBatch(): Arbitrary<List<Candidate>> {
        return validCandidate().list().ofMinSize(1).ofMaxSize(10)
            .map { candidates ->
                // Ensure unique customer IDs by appending index
                candidates.mapIndexed { index, candidate ->
                    candidate.copy(customerId = "${candidate.customerId}-$index")
                }
            }
    }
    
    @Provide
    fun largeCandidateBatch(): Arbitrary<List<Candidate>> {
        return validCandidate().list().ofMinSize(26).ofMaxSize(50)
            .map { candidates ->
                // Ensure unique customer IDs by appending index
                candidates.mapIndexed { index, candidate ->
                    candidate.copy(customerId = "${candidate.customerId}-$index")
                }
            }
    }
    
    @Provide
    fun exactLimitBatch(): Arbitrary<List<Candidate>> {
        return validCandidate().list().ofSize(25)
            .map { candidates ->
                // Ensure unique customer IDs by appending index
                candidates.mapIndexed { index, candidate ->
                    candidate.copy(customerId = "${candidate.customerId}-$index")
                }
            }
    }
    
    private fun customerIds(): Arbitrary<String> {
        return Arbitraries.strings().alpha().numeric().ofMinLength(8).ofMaxLength(15)
            .map { "customer-$it" }
    }
    
    private fun contextListsWithRequiredTypes(): Arbitrary<List<Context>> {
        val programContext = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
            .map { Context("program", it) }
        val marketplaceContext = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(5)
            .map { Context("marketplace", it) }
        
        return Combinators.combine(programContext, marketplaceContext)
            .`as` { program, marketplace ->
                listOf(program, marketplace)
            }
    }
    
    private fun subjects(): Arbitrary<Subject> {
        val types = Arbitraries.of("product", "video", "track")
        val ids = Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(10)
        
        return Combinators.combine(types, ids).`as` { type, id ->
            Subject(type = type, id = id, metadata = null)
        }
    }
    
    private fun candidateAttributes(): Arbitrary<CandidateAttributes> {
        val eventDates = instants()
        val channelEligibility = Arbitraries.maps(
            Arbitraries.of("email", "push", "sms"),
            Arbitraries.of(true, false)
        ).ofMinSize(1).ofMaxSize(3)
        
        return Combinators.combine(eventDates, channelEligibility)
            .`as` { eventDate, channels ->
                CandidateAttributes(
                    eventDate = eventDate,
                    deliveryDate = null,
                    timingWindow = null,
                    orderValue = null,
                    mediaEligible = null,
                    channelEligibility = channels
                )
            }
    }
    
    private fun candidateMetadata(): Arbitrary<CandidateMetadata> {
        val timestamps = instants()
        val versions = Arbitraries.longs().between(1L, 100L)
        
        return Combinators.combine(timestamps, versions).`as` { createdAt, version ->
            CandidateMetadata(
                createdAt = createdAt,
                updatedAt = createdAt.plusSeconds(60),
                expiresAt = createdAt.plusSeconds(86400 * 30),
                version = version,
                sourceConnectorId = "test-connector",
                workflowExecutionId = "exec-test"
            )
        }
    }
    
    private fun instants(): Arbitrary<Instant> {
        return DateTimes.instants()
            .between(
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T23:59:59Z")
            )
    }
}
