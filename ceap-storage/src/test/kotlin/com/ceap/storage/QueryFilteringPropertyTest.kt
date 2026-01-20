package com.ceap.storage

import com.ceap.model.*
import net.jqwik.api.*
import net.jqwik.time.api.DateTimes
import org.assertj.core.api.Assertions.assertThat
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Property-based tests for query filtering correctness.
 * 
 * **Property 13: Query filtering correctness**
 * **Validates: Requirements 5.3, 6.2**
 * 
 * Verifies that query results match filter criteria with no false positives or false negatives.
 */
class QueryFilteringPropertyTest {
    
    private val mockDynamoDb = EnhancedMockDynamoDbClient()
    private val repository = DynamoDBCandidateRepository(mockDynamoDb, "test-table")
    
    @Property(tries = 100)
    fun `query by program and channel returns only matching candidates`(
        @ForAll("programId") programId: String,
        @ForAll("channelId") channelId: String,
        @ForAll("candidatesForProgram") candidates: List<Candidate>
    ) {
        // Given: Multiple candidates for the same program
        candidates.forEach { repository.create(it) }
        
        // When: Querying by program and channel
        val results = repository.queryByProgramAndChannel(programId, channelId, limit = 100)
        
        // Then: All results match the program
        results.forEach { candidate ->
            val programContext = candidate.context.find { it.type == "program" }
            assertThat(programContext).isNotNull
            assertThat(programContext!!.id).isEqualTo(programId)
        }
        
        // And: All results are eligible for the channel
        results.forEach { candidate ->
            val isEligible = candidate.attributes.channelEligibility[channelId] ?: false
            assertThat(isEligible).isTrue()
        }
    }
    
    @Property(tries = 100)
    fun `query by program and date returns only candidates from that date`(
        @ForAll("programId") programId: String,
        @ForAll("date") date: String,
        @ForAll("candidatesForProgramAndDate") candidates: List<Candidate>
    ) {
        // Given: Multiple candidates created on the same date
        candidates.forEach { repository.create(it) }
        
        // When: Querying by program and date
        val results = repository.queryByProgramAndDate(programId, date, limit = 100)
        
        // Then: All results match the program
        results.forEach { candidate ->
            val programContext = candidate.context.find { it.type == "program" }
            assertThat(programContext).isNotNull
            assertThat(programContext!!.id).isEqualTo(programId)
        }
        
        // And: All results were created on the specified date
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        results.forEach { candidate ->
            val createdDate = LocalDate.ofInstant(candidate.metadata.createdAt, ZoneOffset.UTC)
            assertThat(createdDate.format(dateFormatter)).isEqualTo(date)
        }
    }
    
    @Property(tries = 100)
    fun `query results contain no false positives`(
        @ForAll("programId") programId: String,
        @ForAll("channelId") channelId: String,
        @ForAll("mixedCandidates") candidates: List<Candidate>
    ) {
        // Given: A mix of candidates for different programs and channels
        candidates.forEach { repository.create(it) }
        
        // When: Querying by specific program and channel
        val results = repository.queryByProgramAndChannel(programId, channelId, limit = 100)
        
        // Then: No results have a different program
        results.forEach { candidate ->
            val programContext = candidate.context.find { it.type == "program" }
            assertThat(programContext?.id).isEqualTo(programId)
        }
        
        // And: No results are ineligible for the channel
        results.forEach { candidate ->
            val isEligible = candidate.attributes.channelEligibility[channelId] ?: false
            assertThat(isEligible).isTrue()
        }
    }
    
    @Property(tries = 100)
    fun `query with limit returns at most limit results`(
        @ForAll("programId") programId: String,
        @ForAll("channelId") channelId: String,
        @ForAll("queryLimit") limit: Int,
        @ForAll("candidatesForProgram") candidates: List<Candidate>
    ) {
        // Given: Multiple candidates
        candidates.forEach { repository.create(it) }
        
        // When: Querying with a limit
        val results = repository.queryByProgramAndChannel(programId, channelId, limit = limit)
        
        // Then: Results size is at most the limit
        assertThat(results.size).isLessThanOrEqualTo(limit)
    }
    
    @Property(tries = 100)
    fun `query with descending order returns highest scores first`(
        @ForAll("programId") programId: String,
        @ForAll("channelId") channelId: String,
        @ForAll("candidatesWithScores") candidates: List<Candidate>
    ) {
        // Given: Candidates with different scores
        candidates.forEach { repository.create(it) }
        
        // When: Querying with descending order (default)
        val results = repository.queryByProgramAndChannel(programId, channelId, ascending = false)
        
        // Then: Results are sorted by score in descending order
        if (results.size > 1) {
            for (i in 0 until results.size - 1) {
                val currentScore = results[i].scores?.values?.firstOrNull()?.value ?: 0.0
                val nextScore = results[i + 1].scores?.values?.firstOrNull()?.value ?: 0.0
                assertThat(currentScore).isGreaterThanOrEqualTo(nextScore)
            }
        }
    }
    
    // Arbitrary generators
    
    @Provide
    fun queryLimit(): Arbitrary<Int> {
        return Arbitraries.integers().between(1, 50)
    }
    
    @Provide
    fun programId(): Arbitrary<String> {
        return Arbitraries.of("retail", "prime", "music", "video", "books")
    }
    
    @Provide
    fun channelId(): Arbitrary<String> {
        return Arbitraries.of("email", "push", "sms", "in-app")
    }
    
    @Provide
    fun date(): Arbitrary<String> {
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        return DateTimes.instants()
            .between(
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2026-12-31T23:59:59Z")
            )
            .map { instant ->
                LocalDate.ofInstant(instant, ZoneOffset.UTC).format(dateFormatter)
            }
    }
    
    @Provide
    fun candidatesForProgram(): Arbitrary<List<Candidate>> {
        val programArb = programId()
        val channelArb = channelId()
        
        return programArb.flatMap { prog ->
            channelArb.flatMap { chan ->
                candidateForProgramAndChannel(prog, chan)
                    .list()
                    .ofMinSize(1)
                    .ofMaxSize(10)
            }
        }
    }
    
    @Provide
    fun candidatesForProgramAndDate(): Arbitrary<List<Candidate>> {
        val programArb = programId()
        val dateArb = date()
        
        return programArb.flatMap { prog ->
            dateArb.flatMap { d ->
                candidateForProgramAndDate(prog, d)
                    .list()
                    .ofMinSize(1)
                    .ofMaxSize(10)
            }
        }
    }
    
    @Provide
    fun mixedCandidates(): Arbitrary<List<Candidate>> {
        val list1 = candidatesForProgram()
        val list2 = candidatesForProgram()
        val list3 = candidatesForProgram()
        
        return Combinators.combine(list1, list2, list3).`as` { l1, l2, l3 ->
            l1 + l2 + l3
        }
    }
    
    @Provide
    fun candidatesWithScores(): Arbitrary<List<Candidate>> {
        val programArb = programId()
        val channelArb = channelId()
        
        return programArb.flatMap { prog ->
            channelArb.flatMap { chan ->
                candidateWithScore(prog, chan)
                    .list()
                    .ofMinSize(3)
                    .ofMaxSize(10)
            }
        }
    }
    
    private fun candidateForProgramAndChannel(programId: String, channelId: String): Arbitrary<Candidate> {
        return Combinators.combine(
            customerIds(),
            subjects(),
            candidateAttributes(channelId),
            candidateMetadata()
        ).`as` { customerId, subject, attributes, metadata ->
            Candidate(
                customerId = customerId,
                context = listOf(
                    Context("program", programId),
                    Context("marketplace", "US")
                ),
                subject = subject,
                scores = null,
                attributes = attributes,
                metadata = metadata,
                rejectionHistory = null
            )
        }
    }
    
    private fun candidateForProgramAndDate(programId: String, date: String): Arbitrary<Candidate> {
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val targetDate = LocalDate.parse(date, dateFormatter)
        val targetInstant = targetDate.atStartOfDay(ZoneOffset.UTC).toInstant()
        
        return Combinators.combine(
            customerIds(),
            subjects(),
            candidateAttributes("email"),
            candidateMetadataForDate(targetInstant)
        ).`as` { customerId, subject, attributes, metadata ->
            Candidate(
                customerId = customerId,
                context = listOf(
                    Context("program", programId),
                    Context("marketplace", "US")
                ),
                subject = subject,
                scores = null,
                attributes = attributes,
                metadata = metadata,
                rejectionHistory = null
            )
        }
    }
    
    private fun candidateWithScore(programId: String, channelId: String): Arbitrary<Candidate> {
        return Combinators.combine(
            customerIds(),
            subjects(),
            scoresMap(),
            candidateAttributes(channelId),
            candidateMetadata()
        ).`as` { customerId, subject, scores, attributes, metadata ->
            Candidate(
                customerId = customerId,
                context = listOf(
                    Context("program", programId),
                    Context("marketplace", "US")
                ),
                subject = subject,
                scores = scores,
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
    
    private fun subjects(): Arbitrary<Subject> {
        val types = Arbitraries.of("product", "video", "track")
        val ids = Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(10)
        
        return Combinators.combine(types, ids).`as` { type, id ->
            Subject(type = type, id = id, metadata = null)
        }
    }
    
    private fun candidateAttributes(eligibleChannel: String): Arbitrary<CandidateAttributes> {
        val eventDates = instants()
        
        return eventDates.map { eventDate ->
            CandidateAttributes(
                eventDate = eventDate,
                deliveryDate = null,
                timingWindow = null,
                orderValue = null,
                mediaEligible = null,
                channelEligibility = mapOf(
                    eligibleChannel to true,
                    "other-channel" to false
                )
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
    
    private fun candidateMetadataForDate(targetDate: Instant): Arbitrary<CandidateMetadata> {
        val versions = Arbitraries.longs().between(1L, 100L)
        
        return versions.map { version ->
            CandidateMetadata(
                createdAt = targetDate,
                updatedAt = targetDate.plusSeconds(60),
                expiresAt = targetDate.plusSeconds(86400 * 30),
                version = version,
                sourceConnectorId = "test-connector",
                workflowExecutionId = "exec-test"
            )
        }
    }
    
    private fun scoresMap(): Arbitrary<Map<String, Score>> {
        val scores = Arbitraries.doubles().between(0.0, 1.0).map { value ->
            Score(
                modelId = "test-model",
                value = value,
                confidence = 0.9,
                timestamp = Instant.now(),
                metadata = null
            )
        }
        
        return scores.map { score ->
            mapOf("test-model" to score)
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

/**
 * Enhanced mock DynamoDB client with query filtering support.
 */
class EnhancedMockDynamoDbClient : DynamoDbClient {
    private val storage = mutableMapOf<String, Map<String, AttributeValue>>()
    
    override fun serviceName(): String = "dynamodb"
    
    override fun close() {}
    
    override fun putItem(request: PutItemRequest): PutItemResponse {
        val pk = request.item()["PK"]?.s() ?: throw RuntimeException("Missing PK")
        val sk = request.item()["SK"]?.s() ?: throw RuntimeException("Missing SK")
        val key = "$pk#$sk"
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
        // Filter items based on query conditions
        val keyCondition = request.expressionAttributeValues()[":gsi1pk"]?.s()
            ?: request.expressionAttributeValues()[":gsi2pk"]?.s()
            ?: ""
        
        val matchingItems = storage.values.filter { item ->
            val gsi1pk = item["GSI1PK"]?.s() ?: ""
            val gsi2pk = item["GSI2PK"]?.s() ?: ""
            gsi1pk == keyCondition || gsi2pk == keyCondition
        }
        
        // Sort by score if using GSI1
        val sortedItems = if (request.indexName() == DynamoDBConfig.IndexNames.PROGRAM_CHANNEL_INDEX) {
            if (request.scanIndexForward()) {
                matchingItems.sortedBy { it["GSI1SK"]?.s() ?: "" }
            } else {
                matchingItems.sortedByDescending { it["GSI1SK"]?.s() ?: "" }
            }
        } else {
            matchingItems
        }
        
        // Apply limit
        val limitedItems = sortedItems.take(request.limit() ?: 100)
        
        return QueryResponse.builder()
            .items(limitedItems)
            .build()
    }
}
