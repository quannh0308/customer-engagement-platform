package com.solicitation.storage

import com.solicitation.model.*
import net.jqwik.api.*
import net.jqwik.time.api.DateTimes
import org.assertj.core.api.Assertions.assertThat
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.time.Instant

/**
 * Property-based tests for storage round-trip consistency.
 * 
 * **Property 12: Storage round-trip consistency**
 * **Validates: Requirements 5.2, 2.1**
 * 
 * Verifies that any candidate can be stored and retrieved without data loss,
 * and all fields are preserved through the storage round-trip.
 */
class StorageRoundTripPropertyTest {
    
    @Property(tries = 100)
    fun `any candidate can be stored and retrieved without data loss`(
        @ForAll("validCandidates") candidate: Candidate
    ) {
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // When: The candidate is stored
        val stored = repository.create(candidate)
        
        // Then: The stored candidate equals the original
        assertThat(stored).isEqualTo(candidate)
        
        // When: The candidate is retrieved
        val programContext = candidate.context.find { it.type == "program" }!!
        val marketplaceContext = candidate.context.find { it.type == "marketplace" }!!
        
        val retrieved = repository.get(
            customerId = candidate.customerId,
            programId = programContext.id,
            marketplaceId = marketplaceContext.id,
            subjectType = candidate.subject.type,
            subjectId = candidate.subject.id
        )
        
        // Then: The retrieved candidate equals the original
        assertThat(retrieved).isNotNull
        assertThat(retrieved).isEqualTo(candidate)
        
        // And: All fields are preserved
        assertThat(retrieved!!.customerId).isEqualTo(candidate.customerId)
        assertThat(retrieved.context).isEqualTo(candidate.context)
        assertThat(retrieved.subject).isEqualTo(candidate.subject)
        assertThat(retrieved.scores).isEqualTo(candidate.scores)
        assertThat(retrieved.attributes).isEqualTo(candidate.attributes)
        assertThat(retrieved.metadata).isEqualTo(candidate.metadata)
        assertThat(retrieved.rejectionHistory).isEqualTo(candidate.rejectionHistory)
    }
    
    @Property(tries = 100)
    fun `all required fields are preserved through storage`(
        @ForAll("validCandidates") candidate: Candidate
    ) {
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // When: The candidate is stored and retrieved
        repository.create(candidate)
        
        val programContext = candidate.context.find { it.type == "program" }!!
        val marketplaceContext = candidate.context.find { it.type == "marketplace" }!!
        
        val retrieved = repository.get(
            customerId = candidate.customerId,
            programId = programContext.id,
            marketplaceId = marketplaceContext.id,
            subjectType = candidate.subject.type,
            subjectId = candidate.subject.id
        )
        
        // Then: All required fields are present and correct
        assertThat(retrieved).isNotNull
        assertThat(retrieved!!.customerId).isNotBlank()
        assertThat(retrieved.context).isNotEmpty()
        assertThat(retrieved.subject).isNotNull
        assertThat(retrieved.attributes).isNotNull
        assertThat(retrieved.metadata).isNotNull
        assertThat(retrieved.metadata.version).isPositive()
    }
    
    @Property(tries = 100)
    fun `optional fields are preserved when present`(
        @ForAll("candidatesWithOptionalFields") candidate: Candidate
    ) {
        // Given: A fresh mock and repository for each test
        val mockDynamoDb = MockDynamoDbClient()
        val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
        
        // When: The candidate is stored and retrieved
        repository.create(candidate)
        
        val programContext = candidate.context.find { it.type == "program" }!!
        val marketplaceContext = candidate.context.find { it.type == "marketplace" }!!
        
        val retrieved = repository.get(
            customerId = candidate.customerId,
            programId = programContext.id,
            marketplaceId = marketplaceContext.id,
            subjectType = candidate.subject.type,
            subjectId = candidate.subject.id
        )
        
        // Then: Optional fields are preserved
        assertThat(retrieved).isNotNull
        if (candidate.scores != null) {
            assertThat(retrieved!!.scores).isEqualTo(candidate.scores)
        }
        if (candidate.rejectionHistory != null) {
            assertThat(retrieved!!.rejectionHistory).isEqualTo(candidate.rejectionHistory)
        }
    }
    
    @Provide
    fun validCandidates(): Arbitrary<Candidate> {
        return Combinators.combine(
            customerIds(),
            contextListsWithRequiredTypes(),
            subjects(),
            scoresMap(),
            candidateAttributes(),
            candidateMetadata(),
            rejectionHistoryList()
        ).`as` { customerId, context, subject, scores, attributes, metadata, rejectionHistory ->
            Candidate(
                customerId = customerId,
                context = context,
                subject = subject,
                scores = if (scores.isEmpty()) null else scores,
                attributes = attributes,
                metadata = metadata,
                rejectionHistory = if (rejectionHistory.isEmpty()) null else rejectionHistory
            )
        }
    }
    
    @Provide
    fun candidatesWithOptionalFields(): Arbitrary<Candidate> {
        return Combinators.combine(
            customerIds(),
            contextListsWithRequiredTypes(),
            subjects(),
            scoresMap().filter { it.isNotEmpty() },
            candidateAttributes(),
            candidateMetadata(),
            rejectionRecords().list().ofMinSize(1).ofMaxSize(3)
        ).`as` { customerId, context, subject, scores, attributes, metadata, rejectionHistory ->
            Candidate(
                customerId = customerId,
                context = context,
                subject = subject,
                scores = scores,
                attributes = attributes,
                metadata = metadata,
                rejectionHistory = rejectionHistory
            )
        }
    }
    
    // Arbitrary generators
    
    private fun customerIds(): Arbitrary<String> {
        return Arbitraries.strings().withCharRange('a', 'z').numeric()
            .ofMinLength(8).ofMaxLength(20).map { "customer-$it" }
    }
    
    private fun contextListsWithRequiredTypes(): Arbitrary<List<Context>> {
        // Always include program and marketplace contexts
        val programContext = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
            .map { Context("program", it) }
        val marketplaceContext = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(5)
            .map { Context("marketplace", it) }
        val otherContexts = contexts().list().ofMaxSize(3)
        
        return Combinators.combine(programContext, marketplaceContext, otherContexts)
            .`as` { program, marketplace, others ->
                listOf(program, marketplace) + others
            }
    }
    
    private fun contexts(): Arbitrary<Context> {
        val types = Arbitraries.of("vertical", "category", "region", "channel")
        val ids = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(20)
        
        return Combinators.combine(types, ids).`as` { type, id ->
            Context(type = type, id = id)
        }
    }
    
    private fun subjects(): Arbitrary<Subject> {
        val types = Arbitraries.of("product", "video", "track", "service", "event")
        val ids = Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(15)
        
        return Combinators.combine(types, ids).`as` { type, id ->
            Subject(type = type, id = id, metadata = null)
        }
    }
    
    private fun scores(): Arbitrary<Score> {
        val modelIds = Arbitraries.of("quality-model", "relevance-model", "engagement-model")
        val values = Arbitraries.doubles().between(0.0, 1.0)
        val confidences = Arbitraries.doubles().between(0.0, 1.0)
        val timestamps = instants()
        
        return Combinators.combine(modelIds, values, confidences, timestamps)
            .`as` { modelId, value, confidence, timestamp ->
                Score(
                    modelId = modelId,
                    value = value,
                    confidence = confidence,
                    timestamp = timestamp,
                    metadata = null
                )
            }
    }
    
    private fun scoresMap(): Arbitrary<Map<String, Score>> {
        return Arbitraries.maps(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15),
            scores()
        ).ofMaxSize(3)
    }
    
    private fun candidateAttributes(): Arbitrary<CandidateAttributes> {
        val eventDates = instants()
        val channelEligibility = Arbitraries.maps(
            Arbitraries.of("email", "push", "sms", "in-app"),
            Arbitraries.of(true, false)
        ).ofMinSize(1).ofMaxSize(4)
        
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
        val connectorIds = Arbitraries.of("order-connector", "review-connector", "event-connector")
        val executionIds = Arbitraries.strings().alpha().numeric().ofMinLength(10).ofMaxLength(20)
        
        return Combinators.combine(timestamps, versions, connectorIds, executionIds)
            .`as` { createdAt, version, connectorId, executionId ->
                CandidateMetadata(
                    createdAt = createdAt,
                    updatedAt = createdAt.plusSeconds(60),
                    expiresAt = createdAt.plusSeconds(86400 * 30),
                    version = version,
                    sourceConnectorId = connectorId,
                    workflowExecutionId = "exec-$executionId"
                )
            }
    }
    
    private fun rejectionRecords(): Arbitrary<RejectionRecord> {
        val filterIds = Arbitraries.of("frequency-filter", "eligibility-filter", "quality-filter")
        val reasons = Arbitraries.of("Too frequent", "Not eligible", "Low quality")
        val reasonCodes = Arbitraries.of("FREQUENCY_CAP", "NOT_ELIGIBLE", "LOW_QUALITY")
        val timestamps = instants()
        
        return Combinators.combine(filterIds, reasons, reasonCodes, timestamps)
            .`as` { filterId, reason, reasonCode, timestamp ->
                RejectionRecord(
                    filterId = filterId,
                    reason = reason,
                    reasonCode = reasonCode,
                    timestamp = timestamp
                )
            }
    }
    
    private fun rejectionHistoryList(): Arbitrary<List<RejectionRecord>> {
        return rejectionRecords().list().ofMaxSize(3)
    }
    
    private fun instants(): Arbitrary<Instant> {
        return DateTimes.instants()
            .between(
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T23:59:59Z")
            )
    }
}

/**
 * Mock DynamoDB client for testing without AWS infrastructure.
 */
class MockDynamoDbClient : DynamoDbClient {
    private val storage = mutableMapOf<String, Map<String, AttributeValue>>()
    
    override fun serviceName(): String = "dynamodb"
    
    override fun close() {}
    
    override fun putItem(request: PutItemRequest): PutItemResponse {
        val pk = request.item()["PK"]?.s() ?: throw RuntimeException("Missing PK")
        val sk = request.item()["SK"]?.s() ?: throw RuntimeException("Missing SK")
        val key = "$pk#$sk"
        
        // Check condition expression for optimistic locking
        if (request.conditionExpression() != null) {
            if (request.conditionExpression().contains("attribute_not_exists")) {
                // For create operations, allow overwrite in tests (simpler mock behavior)
                // In real DynamoDB, this would fail if item exists
            } else if (request.conditionExpression().contains("version")) {
                val existingItem = storage[key]
                if (existingItem != null) {
                    val existingVersion = existingItem["version"]?.n()?.toLong() ?: 0L
                    val expectedVersion = request.expressionAttributeValues()[":expectedVersion"]?.n()?.toLong() ?: 0L
                    if (existingVersion != expectedVersion) {
                        throw ConditionalCheckFailedException.builder()
                            .message("Version mismatch")
                            .build()
                    }
                }
            }
        }
        
        storage[key] = request.item()
        return PutItemResponse.builder().build()
    }
    
    override fun getItem(request: GetItemRequest): GetItemResponse {
        val pk = request.key()["PK"]?.s() ?: throw RuntimeException("Missing PK")
        val sk = request.key()["SK"]?.s() ?: throw RuntimeException("Missing SK")
        val key = "$pk#$sk"
        
        val item = storage[key]
        return GetItemResponse.builder()
            .item(item ?: emptyMap())
            .build()
    }
    
    override fun deleteItem(request: DeleteItemRequest): DeleteItemResponse {
        val pk = request.key()["PK"]?.s() ?: throw RuntimeException("Missing PK")
        val sk = request.key()["SK"]?.s() ?: throw RuntimeException("Missing SK")
        val key = "$pk#$sk"
        
        storage.remove(key)
        return DeleteItemResponse.builder().build()
    }
    
    override fun batchWriteItem(request: BatchWriteItemRequest): BatchWriteItemResponse {
        request.requestItems().values.flatten().forEach { writeRequest ->
            if (writeRequest.putRequest() != null) {
                val item = writeRequest.putRequest().item()
                val pk = item["PK"]?.s() ?: throw RuntimeException("Missing PK")
                val sk = item["SK"]?.s() ?: throw RuntimeException("Missing SK")
                val key = "$pk#$sk"
                storage[key] = item
            }
        }
        
        return BatchWriteItemResponse.builder()
            .unprocessedItems(emptyMap())
            .build()
    }
    
    override fun query(request: QueryRequest): QueryResponse {
        // Simple mock implementation for queries
        val items = storage.values.toList()
        
        return QueryResponse.builder()
            .items(items)
            .build()
    }
    
    /**
     * Clear all stored items (for test cleanup).
     */
    fun clear() {
        storage.clear()
    }
}
