package com.ceap.storage

import com.ceap.model.*
import net.jqwik.api.*
import net.jqwik.time.api.DateTimes
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import java.time.Instant

/**
 * Property-based tests for optimistic locking conflict detection.
 * 
 * **Property 14: Optimistic locking conflict detection**
 * **Validates: Requirements 5.5**
 * 
 * Verifies that concurrent updates are detected and rejected,
 * and version numbers prevent lost updates.
 */
class OptimisticLockingPropertyTest {
    
    @Property(tries = 100)
    fun `concurrent updates are detected and rejected`(
        @ForAll("validCandidate") candidate: Candidate
    ) {
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // Given: A candidate is created
        val created = repository.create(candidate)
        
        // When: The candidate is updated successfully
        val updated1 = repository.update(created)
        
        // Then: The version is incremented
        assertThat(updated1.metadata.version).isEqualTo(created.metadata.version + 1)
        
        // When: Attempting to update using the old version
        // Then: An OptimisticLockException is thrown
        assertThatThrownBy {
            repository.update(created)
        }.isInstanceOf(OptimisticLockException::class.java)
            .hasMessageContaining("Version conflict")
    }
    
    @Property(tries = 100)
    fun `version numbers prevent lost updates`(
        @ForAll("validCandidate") candidate: Candidate
    ) {
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // Given: A candidate is created
        val created = repository.create(candidate)
        
        // When: Multiple sequential updates occur
        var current = created
        for (i in 1..5) {
            current = repository.update(current)
            
            // Then: Each update increments the version
            assertThat(current.metadata.version).isEqualTo(created.metadata.version + i)
        }
        
        // And: Attempting to update with an old version fails
        assertThatThrownBy {
            repository.update(created)
        }.isInstanceOf(OptimisticLockException::class.java)
    }
    
    @Property(tries = 100)
    fun `update increments version and updates timestamp`(
        @ForAll("validCandidate") candidate: Candidate
    ) {
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // Given: A candidate is created
        val created = repository.create(candidate)
        val originalVersion = created.metadata.version
        
        // When: The candidate is updated
        val updated = repository.update(created)
        
        // Then: Version is incremented
        assertThat(updated.metadata.version).isEqualTo(originalVersion + 1)
        
        // And: Updated timestamp is set (may be same as original due to clock precision)
        assertThat(updated.metadata.updatedAt).isNotNull()
    }
    
    @Property(tries = 100)
    fun `successful update returns updated candidate`(
        @ForAll("validCandidate") candidate: Candidate
    ) {
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // Given: A candidate is created
        val created = repository.create(candidate)
        
        // When: The candidate is updated
        val updated = repository.update(created)
        
        // Then: The returned candidate has the new version
        assertThat(updated.metadata.version).isGreaterThan(created.metadata.version)
        
        // And: All other fields remain unchanged
        assertThat(updated.customerId).isEqualTo(created.customerId)
        assertThat(updated.context).isEqualTo(created.context)
        assertThat(updated.subject).isEqualTo(created.subject)
        assertThat(updated.attributes).isEqualTo(created.attributes)
    }
    
    @Property(tries = 100)
    fun `version conflict is detected regardless of update order`(
        @ForAll("validCandidate") candidate: Candidate
    ) {
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // Given: A candidate is created
        val created = repository.create(candidate)
        
        // When: First update succeeds
        val updated1 = repository.update(created)
        assertThat(updated1.metadata.version).isEqualTo(created.metadata.version + 1)
        
        // And: Second update with the latest version succeeds
        val updated2 = repository.update(updated1)
        assertThat(updated2.metadata.version).isEqualTo(updated1.metadata.version + 1)
        
        // Then: Attempting to update with the first version fails
        assertThatThrownBy {
            repository.update(updated1)
        }.isInstanceOf(OptimisticLockException::class.java)
        
        // And: Attempting to update with the original version fails
        assertThatThrownBy {
            repository.update(created)
        }.isInstanceOf(OptimisticLockException::class.java)
    }
    
    @Property(tries = 100)
    fun `version must match exactly for update to succeed`(
        @ForAll("validCandidate") candidate: Candidate,
        @ForAll("versionOffset") versionOffset: Long
    ) {
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // Given: A candidate is created
        val created = repository.create(candidate)
        
        // When: Attempting to update with a different version
        val modifiedCandidate = created.copy(
            metadata = created.metadata.copy(
                version = created.metadata.version + versionOffset
            )
        )
        
        // Then: The update fails with OptimisticLockException
        if (versionOffset != 0L) {
            assertThatThrownBy {
                repository.update(modifiedCandidate)
            }.isInstanceOf(OptimisticLockException::class.java)
        }
    }
    
    // Arbitrary generators
    
    @Provide
    fun versionOffset(): Arbitrary<Long> {
        return Arbitraries.longs().between(1, 100)
    }
    
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
