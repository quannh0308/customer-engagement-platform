package com.solicitation.workflow.reactive

import com.solicitation.filters.Filter
import com.solicitation.filters.FilterResult
import com.solicitation.model.Candidate
import com.solicitation.model.RejectionRecord
import com.solicitation.model.Score
import com.solicitation.model.config.FilterConfig
import com.solicitation.scoring.FeatureMap
import com.solicitation.scoring.HealthStatus
import com.solicitation.scoring.ScoringProvider
import com.solicitation.storage.BatchWriteResult
import com.solicitation.storage.CandidateRepository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock filter that always passes candidates
 */
class MockFilter : Filter {
    override fun getFilterId(): String = "mock-filter"
    override fun getFilterType(): String = "MOCK"
    override fun configure(config: FilterConfig) {}
    override fun filter(candidate: Candidate): FilterResult {
        return FilterResult.pass(candidate)
    }
}

/**
 * Mock scoring provider for testing
 */
class MockScoringProvider : ScoringProvider {
    override fun getModelId(): String = "mock-model"
    override fun getModelVersion(): String = "1.0"
    override fun getRequiredFeatures(): List<String> = emptyList()
    
    override suspend fun scoreCandidate(candidate: Candidate, features: FeatureMap): Score {
        return Score(
            modelId = getModelId(),
            value = 0.8,
            confidence = 0.9,
            timestamp = Instant.now(),
            metadata = null
        )
    }
    
    override suspend fun scoreBatch(candidates: List<Candidate>, features: List<FeatureMap>): List<Score> {
        return candidates.map { 
            Score(
                modelId = getModelId(),
                value = 0.8,
                confidence = 0.9,
                timestamp = Instant.now(),
                metadata = null
            )
        }
    }
    
    override suspend fun healthCheck(): HealthStatus {
        return HealthStatus(healthy = true, message = "Mock healthy")
    }
    
    override fun getFallbackScore(candidate: Candidate): Score {
        return Score(
            modelId = getModelId(),
            value = 0.5,
            confidence = 0.1,
            timestamp = Instant.now(),
            metadata = mapOf("fallback" to true)
        )
    }
}

/**
 * Mock candidate repository for testing
 */
class MockCandidateRepository : CandidateRepository {
    private val storage = ConcurrentHashMap<String, Candidate>()
    
    override fun create(candidate: Candidate): Candidate {
        val key = getKey(candidate)
        storage[key] = candidate
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
        return storage[key]
    }
    
    override fun update(candidate: Candidate): Candidate {
        val key = getKey(candidate)
        storage[key] = candidate.copy(
            metadata = candidate.metadata.copy(
                version = candidate.metadata.version + 1,
                updatedAt = Instant.now()
            )
        )
        return storage[key]!!
    }
    
    override fun delete(
        customerId: String,
        programId: String,
        marketplaceId: String,
        subjectType: String,
        subjectId: String
    ) {
        val key = "$customerId:$programId:$marketplaceId:$subjectType:$subjectId"
        storage.remove(key)
    }
    
    override fun batchWrite(candidates: List<Candidate>): BatchWriteResult {
        candidates.forEach { create(it) }
        return BatchWriteResult(successfulItems = candidates, failedItems = emptyList())
    }
    
    override fun queryByProgramAndChannel(
        programId: String,
        channelId: String,
        limit: Int,
        ascending: Boolean
    ): List<Candidate> {
        return storage.values.filter { candidate ->
            candidate.context.any { it.type == "program" && it.id == programId }
        }.take(limit)
    }
    
    override fun queryByProgramAndDate(
        programId: String,
        date: String,
        limit: Int
    ): List<Candidate> {
        return storage.values.filter { candidate ->
            candidate.context.any { it.type == "program" && it.id == programId }
        }.take(limit)
    }
    
    private fun getKey(candidate: Candidate): String {
        val programId = candidate.context.find { it.type == "program" }?.id ?: "unknown"
        val marketplaceId = candidate.context.find { it.type == "marketplace" }?.id ?: "unknown"
        return "${candidate.customerId}:$programId:$marketplaceId:${candidate.subject.type}:${candidate.subject.id}"
    }
    
    fun clear() {
        storage.clear()
    }
    
    fun size(): Int = storage.size
}

/**
 * Mock event deduplication tracker for testing
 */
class MockEventDeduplicationTracker : EventDeduplicationTracker(
    deduplicationWindowSeconds = 300,
    tableName = "mock-deduplication-table"
) {
    private val trackedEvents = ConcurrentHashMap<String, Long>()
    
    override fun isDuplicate(event: CustomerEvent): Boolean {
        val key = getDeduplicationKey(event)
        val now = System.currentTimeMillis()
        val lastEventTime = trackedEvents[key]
        
        return if (lastEventTime != null) {
            val windowMs = 300 * 1000 // 5 minutes
            (now - lastEventTime) < windowMs
        } else {
            false
        }
    }
    
    override fun track(event: CustomerEvent) {
        val key = getDeduplicationKey(event)
        trackedEvents[key] = System.currentTimeMillis()
    }
    
    private fun getDeduplicationKey(event: CustomerEvent): String {
        return "${event.customerId}:${event.subjectType}:${event.subjectId}:${event.programId}"
    }
    
    fun clear() {
        trackedEvents.clear()
    }
}

/**
 * Mock Lambda context for testing
 */
class MockLambdaContext : com.amazonaws.services.lambda.runtime.Context {
    override fun getAwsRequestId(): String = "test-request-id"
    override fun getLogGroupName(): String = "test-log-group"
    override fun getLogStreamName(): String = "test-log-stream"
    override fun getFunctionName(): String = "test-function"
    override fun getFunctionVersion(): String = "1.0"
    override fun getInvokedFunctionArn(): String = "arn:aws:lambda:us-east-1:123456789012:function:test"
    override fun getIdentity(): com.amazonaws.services.lambda.runtime.CognitoIdentity? = null
    override fun getClientContext(): com.amazonaws.services.lambda.runtime.ClientContext? = null
    override fun getRemainingTimeInMillis(): Int = 300000
    override fun getMemoryLimitInMB(): Int = 512
    override fun getLogger(): com.amazonaws.services.lambda.runtime.LambdaLogger {
        return object : com.amazonaws.services.lambda.runtime.LambdaLogger {
            override fun log(message: String) {
                println(message)
            }
            override fun log(message: ByteArray) {
                println(String(message))
            }
        }
    }
}
