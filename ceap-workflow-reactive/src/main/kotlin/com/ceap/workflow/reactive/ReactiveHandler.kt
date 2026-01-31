package com.ceap.workflow.reactive

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.ceap.filters.FilterChainExecutor
import com.ceap.filters.Filter
import com.ceap.filters.EligibilityFilter
import com.ceap.filters.TrustFilter
import com.ceap.filters.QualityFilter
import com.ceap.model.Candidate
import com.ceap.model.CandidateAttributes
import com.ceap.model.CandidateMetadata
import com.ceap.model.Context as CandidateContext
import com.ceap.model.Subject
import com.ceap.model.Score
import com.ceap.model.config.FilterChainConfig
import com.ceap.model.config.FilterConfig
import com.ceap.scoring.MultiModelScorer
import com.ceap.scoring.ScoringProvider
import com.ceap.scoring.BaseScoringProvider
import com.ceap.scoring.FeatureStoreClient
import com.ceap.scoring.FeatureValidator
import com.ceap.scoring.ScoreCacheRepository
import com.ceap.scoring.FeatureMap
import com.ceap.storage.CandidateRepository
import com.ceap.storage.DynamoDBCandidateRepository
import com.ceap.workflow.common.WorkflowLambdaHandler
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.time.Instant
import kotlinx.coroutines.runBlocking

/**
 * Simple scoring provider for reactive workflow
 */
class DefaultScoringProvider : BaseScoringProvider() {
    override fun getModelId(): String = "default-model"
    override fun getModelVersion(): String = "1.0"
    override fun getRequiredFeatures(): List<String> = emptyList()
    
    override suspend fun scoreCandidate(candidate: Candidate, features: FeatureMap): Score {
        return Score(
            modelId = getModelId(),
            value = 0.7,
            confidence = 0.8,
            timestamp = Instant.now(),
            metadata = null
        )
    }
}

/**
 * Lambda handler for reactive customer engagement workflow.
 * 
 * Processes customer events in real-time to create customer engagement candidates.
 * 
 * Responsibilities:
 * - Parse customer events from EventBridge
 * - Create candidate from event data
 * - Execute filtering in real-time
 * - Execute scoring with fallback
 * - Store eligible candidates immediately
 * - Track deduplication
 * 
 * Extends WorkflowLambdaHandler to leverage S3-based orchestration pattern.
 * S3 I/O is handled by base class - this class focuses on reactive processing logic.
 * 
 * Validates: Requirements 3.3, 3.4, 3.5, 9.1, 9.2, 9.3, 9.4, 9.5
 */
class ReactiveHandler(
    private val candidateRepository: CandidateRepository? = null,
    private val deduplicationTracker: IEventDeduplicationTracker? = null,
    private val filterChainExecutor: FilterChainExecutor? = null,
    private val multiModelScorer: MultiModelScorer? = null
) : WorkflowLambdaHandler() {
    
    private val dynamoDbClient = DynamoDbClient.builder().build()
    
    // Initialize filter chain with default filters
    private val defaultFilterChainExecutor: FilterChainExecutor by lazy {
        val filters = listOf<Filter>(
            EligibilityFilter(),
            TrustFilter(),
            QualityFilter()
        )
        val config = FilterChainConfig(
            filters = listOf(
                FilterConfig(
                    filterId = "eligibility",
                    filterType = "eligibility",
                    enabled = true,
                    parameters = mapOf("checkTimingWindow" to false),
                    order = 1
                ),
                FilterConfig(
                    filterId = "trust",
                    filterType = "trust",
                    enabled = false,  // Disable trust filter in reactive mode (no scores yet)
                    parameters = emptyMap(),
                    order = 2
                ),
                FilterConfig(
                    filterId = "quality",
                    filterType = "quality",
                    enabled = false,  // Disable quality filter in reactive mode (no scores yet)
                    parameters = emptyMap(),
                    order = 3
                )
            )
        )
        FilterChainExecutor(filters, config)
    }
    
    // Initialize multi-model scorer with default providers
    private val defaultMultiModelScorer: MultiModelScorer by lazy {
        val scoringProviders = listOf<ScoringProvider>(
            DefaultScoringProvider()
        )
        val featureStoreClient = FeatureStoreClient()
        val featureValidator = FeatureValidator()
        val scoreCacheRepository = ScoreCacheRepository(dynamoDbClient)
        
        MultiModelScorer(
            scoringProviders,
            featureStoreClient,
            featureValidator,
            scoreCacheRepository
        )
    }
    
    private val defaultCandidateRepository: CandidateRepository by lazy {
        DynamoDBCandidateRepository(dynamoDbClient)
    }
    
    private val defaultDeduplicationTracker = EventDeduplicationTracker()
    
    /**
     * Process input data by handling reactive customer event.
     * 
     * Input structure (from EventBridge):
     * {
     *   "detail": {
     *     "customerId": "string",
     *     "eventType": "string",
     *     "subjectType": "string",
     *     "subjectId": "string",
     *     "programId": "string",
     *     "marketplace": "string",
     *     "eventDate": "string",
     *     "metadata": {...}
     *   }
     * }
     * 
     * Output structure:
     * {
     *   "success": boolean,
     *   "candidateCreated": boolean,
     *   "candidateId": "string" (optional),
     *   "reason": "string" (optional)
     * }
     */
    override fun processData(input: JsonNode): JsonNode {
        val startTime = System.currentTimeMillis()
        
        logger.info("Processing reactive workflow: input keys={}", input.fieldNames().asSequence().toList())
        
        try {
            // Parse event from EventBridge
            val event = parseCustomerEvent(input)
            
            logger.info("Processing customer event: customerId={}, eventType={}, subjectId={}", 
                event.customerId, event.eventType, event.subjectId)
            
            // Check for deduplication
            val tracker = deduplicationTracker ?: defaultDeduplicationTracker
            if (tracker.isDuplicate(event)) {
                logger.info("Duplicate event detected, skipping: customerId={}, subjectId={}", 
                    event.customerId, event.subjectId)
                return buildResponse(true, false, null, "Duplicate event within deduplication window")
            }
            
            // Create candidate from event
            val candidate = createCandidateFromEvent(event)
            
            logger.info("Created candidate: customerId={}, subjectId={}", 
                candidate.customerId, candidate.subject.id)
            
            // Execute filter chain
            val executor = filterChainExecutor ?: defaultFilterChainExecutor
            val filterResult = executor.execute(candidate)
            
            if (!filterResult.passed) {
                val rejection = filterResult.rejectionHistory.firstOrNull()
                logger.info("Candidate rejected by filter: filterId={}, reason={}", 
                    rejection?.filterId, rejection?.reason)
                
                // Track deduplication even for rejected candidates
                tracker.track(event)
                
                return buildResponse(true, false, null, "Rejected by filter: ${rejection?.reason}")
            }
            
            val eligibleCandidate = filterResult.candidate
            
            logger.info("Candidate passed filters, scoring...")
            
            // Execute scoring with fallback
            val scorer = multiModelScorer ?: defaultMultiModelScorer
            val scores = runBlocking {
                try {
                    scorer.scoreCandidate(eligibleCandidate)
                } catch (e: Exception) {
                    logger.warn("Scoring failed, using empty scores: error={}", e.message)
                    emptyMap()
                }
            }
            
            // Create scored candidate
            val scoredCandidate = eligibleCandidate.copy(scores = scores)
            
            logger.info("Candidate scored, storing...")
            
            // Store candidate immediately
            val repository = candidateRepository ?: defaultCandidateRepository
            repository.create(scoredCandidate)
            
            // Track deduplication
            tracker.track(event)
            
            val candidateId = "${scoredCandidate.customerId}:${scoredCandidate.subject.id}"
            
            logger.info("Reactive workflow completed: customerId={}, subjectId={}", 
                scoredCandidate.customerId, scoredCandidate.subject.id)
            
            return buildResponse(true, true, candidateId, null)
            
        } catch (e: Exception) {
            logger.error("Reactive workflow failed", e)
            return buildResponse(false, false, null, "Error: ${e.message}")
        }
    }
    
    /**
     * Parse customer event from EventBridge input
     */
    private fun parseCustomerEvent(input: JsonNode): CustomerEvent {
        // EventBridge wraps the event in a detail field
        val detail = input.get("detail")
            ?: throw IllegalArgumentException("Missing 'detail' field in event")
        
        return CustomerEvent(
            customerId = detail.get("customerId")?.asText()
                ?: throw IllegalArgumentException("Missing customerId"),
            eventType = detail.get("eventType")?.asText()
                ?: throw IllegalArgumentException("Missing eventType"),
            subjectType = detail.get("subjectType")?.asText()
                ?: throw IllegalArgumentException("Missing subjectType"),
            subjectId = detail.get("subjectId")?.asText()
                ?: throw IllegalArgumentException("Missing subjectId"),
            programId = detail.get("programId")?.asText()
                ?: throw IllegalArgumentException("Missing programId"),
            marketplace = detail.get("marketplace")?.asText()
                ?: throw IllegalArgumentException("Missing marketplace"),
            eventDate = detail.get("eventDate")?.asText()
                ?: Instant.now().toString(),
            metadata = if (detail.has("metadata")) {
                objectMapper.convertValue(detail.get("metadata"), Map::class.java) as Map<String, Any>
            } else {
                emptyMap()
            }
        )
    }
    
    /**
     * Create candidate from customer event
     */
    private fun createCandidateFromEvent(event: CustomerEvent): Candidate {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(30 * 24 * 60 * 60) // 30 days TTL
        
        return Candidate(
            customerId = event.customerId,
            context = listOf(
                CandidateContext(type = "marketplace", id = event.marketplace),
                CandidateContext(type = "program", id = event.programId)
            ),
            subject = Subject(
                type = event.subjectType,
                id = event.subjectId,
                metadata = event.metadata
            ),
            scores = null, // Will be populated after scoring
            attributes = CandidateAttributes(
                eventDate = Instant.parse(event.eventDate),
                deliveryDate = null,
                timingWindow = null,
                orderValue = event.metadata["orderValue"] as? Double,
                mediaEligible = null,
                channelEligibility = mapOf(
                    "email" to true,
                    "in-app" to true,
                    "push" to true
                )
            ),
            metadata = CandidateMetadata(
                createdAt = now,
                updatedAt = now,
                expiresAt = expiresAt,
                version = 1L,
                sourceConnectorId = "reactive-workflow",
                workflowExecutionId = null
            ),
            rejectionHistory = null
        )
    }
    
    private fun buildResponse(
        success: Boolean,
        candidateCreated: Boolean,
        candidateId: String?,
        reason: String?
    ): JsonNode {
        val output = objectMapper.createObjectNode()
        output.put("success", success)
        output.put("candidateCreated", candidateCreated)
        if (candidateId != null) {
            output.put("candidateId", candidateId)
        }
        if (reason != null) {
            output.put("reason", reason)
        }
        return output
    }
}

/**
 * Customer event from EventBridge
 */
data class CustomerEvent(
    val customerId: String,
    val eventType: String,
    val subjectType: String,
    val subjectId: String,
    val programId: String,
    val marketplace: String,
    val eventDate: String,
    val metadata: Map<String, Any>
)

