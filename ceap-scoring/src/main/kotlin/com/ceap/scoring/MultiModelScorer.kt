package com.ceap.scoring

import com.ceap.model.Candidate
import com.ceap.model.Score
import kotlinx.coroutines.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Orchestrates scoring across multiple ML models.
 * 
 * Executes multiple scoring providers in parallel and aggregates results.
 * Each model scores independently, and failures in one model don't affect others.
 * 
 * **Requirements**: 3.3
 */
class MultiModelScorer(
    private val scoringProviders: List<ScoringProvider>,
    private val featureStoreClient: FeatureStoreClient,
    private val featureValidator: FeatureValidator,
    private val scoreCacheRepository: ScoreCacheRepository,
    private val config: MultiModelScoringConfig = MultiModelScoringConfig()
) {
    
    /**
     * Scores a single candidate using all configured models.
     * 
     * Models execute in parallel up to the configured concurrency limit.
     * Each model's score is independent - one failure doesn't affect others.
     * 
     * @param candidate Candidate to score
     * @return Map of modelId to Score (only successful scores included)
     */
    suspend fun scoreCandidate(candidate: Candidate): Map<String, Score> = coroutineScope {
        logger.debug { "Scoring candidate ${candidate.customerId} with ${scoringProviders.size} models" }
        
        val results = mutableMapOf<String, Score>()
        
        // Score with each provider independently
        val jobs = scoringProviders.map { provider ->
            async {
                try {
                    scoreSingleModel(candidate, provider)
                } catch (e: Exception) {
                    logger.warn(e) { 
                        "Failed to score candidate ${candidate.customerId} with model ${provider.getModelId()}" 
                    }
                    null
                }
            }
        }
        
        // Collect results
        jobs.awaitAll().filterNotNull().forEach { score ->
            results[score.modelId] = score
        }
        
        logger.info { 
            "Scored candidate ${candidate.customerId}: ${results.size}/${scoringProviders.size} models succeeded" 
        }
        
        return@coroutineScope results
    }
    
    /**
     * Scores multiple candidates using all configured models.
     * 
     * Processes candidates in batches for efficiency.
     * 
     * @param candidates List of candidates to score
     * @return List of maps (modelId to Score) in the same order as input
     */
    suspend fun scoreCandidates(candidates: List<Candidate>): List<Map<String, Score>> = coroutineScope {
        logger.debug { "Batch scoring ${candidates.size} candidates with ${scoringProviders.size} models" }
        
        // Process in batches to avoid overwhelming the system
        val batchSize = config.batchSize
        val results = mutableListOf<Map<String, Score>>()
        
        for (batch in candidates.chunked(batchSize)) {
            val batchResults = batch.map { candidate ->
                async {
                    scoreCandidate(candidate)
                }
            }.awaitAll()
            
            results.addAll(batchResults)
        }
        
        logger.info { 
            "Batch scored ${candidates.size} candidates with ${scoringProviders.size} models" 
        }
        
        return@coroutineScope results
    }
    
    /**
     * Scores a candidate with a single model.
     * 
     * Checks cache first, then retrieves features and scores if needed.
     * 
     * @param candidate Candidate to score
     * @param provider Scoring provider to use
     * @return Score from the model
     */
    private suspend fun scoreSingleModel(
        candidate: Candidate,
        provider: ScoringProvider
    ): Score {
        val modelId = provider.getModelId()
        val subjectId = candidate.subject.id
        
        // Check cache first
        val cachedScore = scoreCacheRepository.get(candidate.customerId, subjectId, modelId)
        if (cachedScore != null) {
            logger.debug { "Using cached score for customer ${candidate.customerId}, model $modelId" }
            return cachedScore.toScore()
        }
        
        // Retrieve features
        val requiredFeatures = provider.getRequiredFeatures()
        val features = featureStoreClient.getFeatures(
            candidate.customerId,
            subjectId,
            requiredFeatures
        )
        
        // Validate features
        val validationResult = featureValidator.validate(requiredFeatures, features)
        if (!validationResult.valid) {
            val errorMsg = featureValidator.createErrorMessage(validationResult)
            logger.warn { "Feature validation failed for model $modelId: $errorMsg" }
            throw ScoringException("Feature validation failed: $errorMsg")
        }
        
        // Score the candidate
        val score = provider.scoreCandidate(candidate, features)
        
        // Cache the score
        scoreCacheRepository.put(
            candidate.customerId,
            subjectId,
            provider.getModelVersion(),
            score
        )
        
        logger.debug { 
            "Scored candidate ${candidate.customerId} with model $modelId: ${score.value}" 
        }
        
        return score
    }
    
    /**
     * Gets the health status of all scoring providers.
     * 
     * @return Map of modelId to health status
     */
    suspend fun getHealthStatus(): Map<String, HealthStatus> = coroutineScope {
        val healthChecks = scoringProviders.map { provider ->
            async {
                try {
                    provider.getModelId() to provider.healthCheck()
                } catch (e: Exception) {
                    logger.warn(e) { "Health check failed for model ${provider.getModelId()}" }
                    provider.getModelId() to HealthStatus(
                        healthy = false,
                        message = "Health check failed: ${e.message}"
                    )
                }
            }
        }
        
        return@coroutineScope healthChecks.awaitAll().toMap()
    }
    
    /**
     * Gets the list of all configured model IDs.
     * 
     * @return List of model identifiers
     */
    fun getModelIds(): List<String> {
        return scoringProviders.map { it.getModelId() }
    }
    
    /**
     * Gets a specific scoring provider by model ID.
     * 
     * @param modelId Model identifier
     * @return Scoring provider if found, null otherwise
     */
    fun getProvider(modelId: String): ScoringProvider? {
        return scoringProviders.find { it.getModelId() == modelId }
    }
}

/**
 * Configuration for multi-model scoring.
 * 
 * @property batchSize Number of candidates to process in each batch
 * @property maxConcurrency Maximum number of concurrent scoring operations
 * @property timeoutMs Timeout for scoring operations in milliseconds
 */
data class MultiModelScoringConfig(
    val batchSize: Int = 10,
    val maxConcurrency: Int = 5,
    val timeoutMs: Long = 30000 // 30 seconds
)

