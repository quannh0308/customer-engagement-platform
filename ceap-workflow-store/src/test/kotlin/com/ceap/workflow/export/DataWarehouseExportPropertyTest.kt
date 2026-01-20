package com.ceap.workflow.export

import com.amazonaws.services.lambda.runtime.Context
import com.ceap.model.*
import com.ceap.storage.CandidateRepository
import com.ceap.storage.StorageException
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import software.amazon.awssdk.services.glue.GlueClient
import software.amazon.awssdk.services.glue.model.StartJobRunRequest
import software.amazon.awssdk.services.glue.model.StartJobRunResponse
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Property-based test for data warehouse export completeness.
 * 
 * **Property 16: Data warehouse export completeness**
 * 
 * *For any* daily export, all candidates created or updated during that day
 * must appear in the export, and no candidates from other days should be included.
 * 
 * **Validates: Requirements 5.6**
 */
class DataWarehouseExportPropertyTest {
    
    @Property
    fun `export includes all candidates from target date`(
        @ForAll("dates") date: LocalDate,
        @ForAll("candidateCounts") count: Int
    ) {
        // Arrange
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val startOfDay = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        
        val repository = InMemoryCandidateRepository()
        val s3Client = MockS3Client()
        val glueClient = MockGlueClient()
        
        val handler = DataWarehouseExportHandler(
            candidateRepository = repository,
            s3Client = s3Client,
            glueClient = glueClient,
            exportBucket = "test-bucket",
            glueJobName = "test-job"
        )
        
        // Create candidates for the target date
        val targetDateCandidates = (1..count).map { i ->
            createCandidate(
                customerId = "customer-$i",
                programId = "program-1",
                createdAt = startOfDay.plusSeconds(i * 60L),
                updatedAt = startOfDay.plusSeconds(i * 60L)
            )
        }
        
        // Create candidates for other dates (should not be included)
        val otherDateCandidates = listOf(
            createCandidate(
                customerId = "customer-before",
                programId = "program-1",
                createdAt = startOfDay.minusSeconds(3600),
                updatedAt = startOfDay.minusSeconds(3600)
            ),
            createCandidate(
                customerId = "customer-after",
                programId = "program-1",
                createdAt = endOfDay.plusSeconds(3600),
                updatedAt = endOfDay.plusSeconds(3600)
            )
        )
        
        // Store all candidates
        (targetDateCandidates + otherDateCandidates).forEach { repository.create(it) }
        
        // Act - Export candidates for target date
        val request = ExportRequest(
            programId = "program-1",
            date = dateStr
        )
        
        val response = handler.handleRequest(request, MockContext())
        
        // Assert - Export was successful
        assertThat(response.success).isTrue()
        
        // Assert - Correct number of candidates exported
        assertThat(response.candidatesExported).isEqualTo(count)
        
        // Assert - S3 upload was called
        assertThat(s3Client.uploadCount).isEqualTo(1)
        
        // Assert - Glue job was triggered
        assertThat(glueClient.jobRunCount).isEqualTo(1)
    }
    
    @Property
    fun `export includes candidates updated on target date`(
        @ForAll("dates") date: LocalDate
    ) {
        // Arrange
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val startOfDay = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        
        val repository = InMemoryCandidateRepository()
        val s3Client = MockS3Client()
        val glueClient = MockGlueClient()
        
        val handler = DataWarehouseExportHandler(
            candidateRepository = repository,
            s3Client = s3Client,
            glueClient = glueClient,
            exportBucket = "test-bucket",
            glueJobName = "test-job"
        )
        
        // Create candidate created before target date but updated on target date
        val candidate = createCandidate(
            customerId = "customer-1",
            programId = "program-1",
            createdAt = startOfDay.minusSeconds(86400), // Created yesterday
            updatedAt = startOfDay.plusSeconds(3600)    // Updated today
        )
        
        repository.create(candidate)
        
        // Act - Export candidates for target date
        val request = ExportRequest(
            programId = "program-1",
            date = dateStr
        )
        
        val response = handler.handleRequest(request, MockContext())
        
        // Assert - Export was successful
        assertThat(response.success).isTrue()
        
        // Assert - Candidate was included (updated on target date)
        assertThat(response.candidatesExported).isEqualTo(1)
    }
    
    @Property
    fun `export handles empty result set`(
        @ForAll("dates") date: LocalDate
    ) {
        // Arrange
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        val repository = InMemoryCandidateRepository()
        val s3Client = MockS3Client()
        val glueClient = MockGlueClient()
        
        val handler = DataWarehouseExportHandler(
            candidateRepository = repository,
            s3Client = s3Client,
            glueClient = glueClient,
            exportBucket = "test-bucket",
            glueJobName = "test-job"
        )
        
        // Act - Export with no candidates
        val request = ExportRequest(
            programId = "program-1",
            date = dateStr
        )
        
        val response = handler.handleRequest(request, MockContext())
        
        // Assert - Export was successful
        assertThat(response.success).isTrue()
        
        // Assert - No candidates exported
        assertThat(response.candidatesExported).isEqualTo(0)
        
        // Assert - No S3 upload
        assertThat(s3Client.uploadCount).isEqualTo(0)
        
        // Assert - No Glue job triggered
        assertThat(glueClient.jobRunCount).isEqualTo(0)
    }
    
    @Provide
    fun dates(): Arbitrary<LocalDate> {
        return Arbitraries.create {
            LocalDate.of(2024, 1, 1).plusDays(
                Arbitraries.integers().between(0, 365).sample().toLong()
            )
        }
    }
    
    @Provide
    fun candidateCounts(): Arbitrary<Int> {
        return Arbitraries.integers().between(1, 10)
    }
    
    private fun createCandidate(
        customerId: String,
        programId: String,
        createdAt: Instant,
        updatedAt: Instant
    ): Candidate {
        return Candidate(
            customerId = customerId,
            context = listOf(
                Context(type = "program", id = programId),
                Context(type = "marketplace", id = "US")
            ),
            subject = Subject(
                type = "product",
                id = "product-123"
            ),
            scores = null,
            attributes = CandidateAttributes(
                eventDate = createdAt,
                channelEligibility = mapOf("email" to true)
            ),
            metadata = CandidateMetadata(
                createdAt = createdAt,
                updatedAt = updatedAt,
                expiresAt = createdAt.plusSeconds(86400),
                version = 1,
                sourceConnectorId = "test-connector",
                workflowExecutionId = "test-execution"
            )
        )
    }
}

/**
 * In-memory implementation of CandidateRepository for testing.
 */
private class InMemoryCandidateRepository : CandidateRepository {
    private val storage = mutableMapOf<String, Candidate>()
    private val byProgramAndDate = mutableMapOf<String, MutableList<Candidate>>()
    
    override fun create(candidate: Candidate): Candidate {
        val programContext = candidate.context.find { it.type == "program" }
        val marketplaceContext = candidate.context.find { it.type == "marketplace" }
        
        if (programContext == null || marketplaceContext == null) {
            throw StorageException("Candidate must have program and marketplace context")
        }
        
        val key = "${candidate.customerId}#${programContext.id}#${marketplaceContext.id}#${candidate.subject.type}#${candidate.subject.id}"
        storage[key] = candidate
        
        // Index by program and created date
        val createdDateKey = "${programContext.id}#${candidate.metadata.createdAt.toString().substring(0, 10)}"
        byProgramAndDate.getOrPut(createdDateKey) { mutableListOf() }.add(candidate)
        
        // Also index by program and updated date if different
        val updatedDateKey = "${programContext.id}#${candidate.metadata.updatedAt.toString().substring(0, 10)}"
        if (createdDateKey != updatedDateKey) {
            byProgramAndDate.getOrPut(updatedDateKey) { mutableListOf() }.add(candidate)
        }
        
        return candidate
    }
    
    override fun queryByProgramAndDate(programId: String, date: String, limit: Int): List<Candidate> {
        val dateKey = "$programId#$date"
        return byProgramAndDate[dateKey] ?: emptyList()
    }
    
    override fun get(customerId: String, programId: String, marketplaceId: String, subjectType: String, subjectId: String) = throw NotImplementedError()
    override fun update(candidate: Candidate) = throw NotImplementedError()
    override fun delete(customerId: String, programId: String, marketplaceId: String, subjectType: String, subjectId: String) = throw NotImplementedError()
    override fun batchWrite(candidates: List<Candidate>) = throw NotImplementedError()
    override fun queryByProgramAndChannel(programId: String, channelId: String, limit: Int, ascending: Boolean) = throw NotImplementedError()
}

/**
 * Mock S3 client for testing.
 */
private class MockS3Client : S3Client {
    var uploadCount = 0
    
    override fun putObject(request: PutObjectRequest, body: software.amazon.awssdk.core.sync.RequestBody): PutObjectResponse {
        uploadCount++
        return PutObjectResponse.builder().build()
    }
    
    override fun serviceName() = "s3"
    override fun close() {}
}

/**
 * Mock Glue client for testing.
 */
private class MockGlueClient : GlueClient {
    var jobRunCount = 0
    
    override fun startJobRun(request: StartJobRunRequest): StartJobRunResponse {
        jobRunCount++
        return StartJobRunResponse.builder()
            .jobRunId("test-run-${jobRunCount}")
            .build()
    }
    
    override fun serviceName() = "glue"
    override fun close() {}
}

/**
 * Mock Lambda context for testing.
 */
private class MockContext : Context {
    override fun getAwsRequestId() = "test-request-id"
    override fun getLogGroupName() = "test-log-group"
    override fun getLogStreamName() = "test-log-stream"
    override fun getFunctionName() = "test-function"
    override fun getFunctionVersion() = "1"
    override fun getInvokedFunctionArn() = "arn:aws:lambda:us-east-1:123456789012:function:test"
    override fun getIdentity() = null
    override fun getClientContext() = null
    override fun getRemainingTimeInMillis() = 300000
    override fun getMemoryLimitInMB() = 512
    override fun getLogger() = object : com.amazonaws.services.lambda.runtime.LambdaLogger {
        override fun log(message: String) {}
        override fun log(message: ByteArray) {}
    }
}
