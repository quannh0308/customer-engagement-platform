package com.solicitation.serving.v1

import com.solicitation.serving.ServingAPI
import mu.KotlinLogging
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Shadow mode adapter that runs V2 processing in parallel with V1.
 * 
 * In shadow mode:
 * - V1 request is processed normally and returned to the client
 * - V2 request is executed in parallel in the background
 * - V2 results are logged for comparison but don't affect the V1 response
 * - V2 errors are logged but don't cause V1 request to fail
 * 
 * This allows testing V2 implementation in production without affecting
 * existing V1 clients.
 * 
 * @property v1Adapter The V1 adapter for normal processing
 * @property v2ServingAPI The V2 backend for shadow mode testing
 * @property shadowModeEnabled Whether shadow mode is currently enabled
 * @property shadowExecutor Executor for running V2 requests in background
 */
class V1ShadowModeAdapter(
    private val v1Adapter: V1ApiAdapter,
    private val v2ServingAPI: ServingAPI,
    private var shadowModeEnabled: Boolean = false,
    private val shadowExecutor: ExecutorService = Executors.newFixedThreadPool(4)
) {
    
    /**
     * Handles get candidates request with optional shadow mode.
     * 
     * If shadow mode is enabled, executes V2 request in parallel but
     * always returns V1 response.
     */
    fun getCandidatesForCustomer(v1Request: V1GetCandidatesRequest): V1GetCandidatesResponse {
        // Always execute V1 request
        val v1Response = v1Adapter.getCandidatesForCustomer(v1Request)
        
        // If shadow mode is enabled, execute V2 in parallel
        if (shadowModeEnabled) {
            executeShadowRequest(v1Request, v1Response)
        }
        
        // Always return V1 response
        return v1Response
    }
    
    /**
     * Handles batch get candidates request with optional shadow mode.
     */
    fun getCandidatesForCustomers(v1Request: V1BatchGetCandidatesRequest): V1BatchGetCandidatesResponse {
        // Always execute V1 request
        val v1Response = v1Adapter.getCandidatesForCustomers(v1Request)
        
        // If shadow mode is enabled, execute V2 in parallel
        if (shadowModeEnabled) {
            executeShadowBatchRequest(v1Request, v1Response)
        }
        
        // Always return V1 response
        return v1Response
    }
    
    /**
     * Enables or disables shadow mode.
     */
    fun setShadowModeEnabled(enabled: Boolean) {
        shadowModeEnabled = enabled
        logger.info { "Shadow mode ${if (enabled) "enabled" else "disabled"}" }
    }
    
    /**
     * Checks if shadow mode is enabled.
     */
    fun isShadowModeEnabled(): Boolean = shadowModeEnabled
    
    /**
     * Executes V2 request in background for shadow mode comparison.
     */
    private fun executeShadowRequest(
        v1Request: V1GetCandidatesRequest,
        v1Response: V1GetCandidatesResponse
    ) {
        CompletableFuture.runAsync({
            try {
                val startTime = System.currentTimeMillis()
                
                // Translate V1 request to V2
                val v2Request = com.solicitation.serving.GetCandidatesRequest(
                    customerId = v1Request.customerId,
                    channel = v1Request.channel,
                    program = v1Request.program,
                    marketplace = v1Request.marketplace,
                    limit = v1Request.limit,
                    includeScores = true,
                    refreshEligibility = false
                )
                
                // Execute V2 request
                val v2Response = v2ServingAPI.getCandidatesForCustomer(v2Request)
                
                val latency = System.currentTimeMillis() - startTime
                
                // Compare results
                val comparison = compareResults(v1Response, v2Response)
                
                // Log comparison
                logger.info { 
                    "Shadow mode comparison: customer=${v1Request.customerId}, " +
                    "v1Count=${v1Response.candidates.size}, " +
                    "v2Count=${v2Response.candidates.size}, " +
                    "match=${comparison.candidatesMatch}, " +
                    "v2Latency=${latency}ms"
                }
                
                if (!comparison.candidatesMatch) {
                    logger.warn { 
                        "Shadow mode mismatch: ${comparison.differences}" 
                    }
                }
                
            } catch (e: Exception) {
                // Log error but don't propagate - shadow mode failures should not affect V1
                logger.error(e) { "Shadow mode V2 request failed for customer ${v1Request.customerId}" }
            }
        }, shadowExecutor)
    }
    
    /**
     * Executes V2 batch request in background for shadow mode comparison.
     */
    private fun executeShadowBatchRequest(
        v1Request: V1BatchGetCandidatesRequest,
        v1Response: V1BatchGetCandidatesResponse
    ) {
        CompletableFuture.runAsync({
            try {
                val startTime = System.currentTimeMillis()
                
                // Translate V1 request to V2
                val v2Request = com.solicitation.serving.BatchGetCandidatesRequest(
                    customerIds = v1Request.customerIds,
                    channel = v1Request.channel,
                    program = v1Request.program,
                    marketplace = v1Request.marketplace,
                    limit = v1Request.limit,
                    includeScores = true
                )
                
                // Execute V2 request
                val v2Response = v2ServingAPI.getCandidatesForCustomers(v2Request)
                
                val latency = System.currentTimeMillis() - startTime
                
                // Compare results
                val comparison = compareBatchResults(v1Response, v2Response)
                
                // Log comparison
                logger.info { 
                    "Shadow mode batch comparison: customers=${v1Request.customerIds.size}, " +
                    "v1TotalCount=${v1Response.totalCount}, " +
                    "v2TotalCount=${v2Response.metadata.totalCount}, " +
                    "match=${comparison.resultsMatch}, " +
                    "v2Latency=${latency}ms"
                }
                
                if (!comparison.resultsMatch) {
                    logger.warn { 
                        "Shadow mode batch mismatch: ${comparison.differences}" 
                    }
                }
                
            } catch (e: Exception) {
                // Log error but don't propagate
                logger.error(e) { "Shadow mode V2 batch request failed" }
            }
        }, shadowExecutor)
    }
    
    /**
     * Compares V1 and V2 responses.
     */
    private fun compareResults(
        v1Response: V1GetCandidatesResponse,
        v2Response: com.solicitation.serving.GetCandidatesResponse
    ): ComparisonResult {
        val differences = mutableListOf<String>()
        
        // Compare candidate counts
        if (v1Response.candidates.size != v2Response.candidates.size) {
            differences.add("Candidate count mismatch: v1=${v1Response.candidates.size}, v2=${v2Response.candidates.size}")
        }
        
        // Compare candidate IDs
        val v1SubjectIds = v1Response.candidates.map { it.subjectId }.toSet()
        val v2SubjectIds = v2Response.candidates.map { it.subject.id }.toSet()
        
        val onlyInV1 = v1SubjectIds - v2SubjectIds
        val onlyInV2 = v2SubjectIds - v1SubjectIds
        
        if (onlyInV1.isNotEmpty()) {
            differences.add("Subjects only in V1: $onlyInV1")
        }
        
        if (onlyInV2.isNotEmpty()) {
            differences.add("Subjects only in V2: $onlyInV2")
        }
        
        return ComparisonResult(
            candidatesMatch = differences.isEmpty(),
            differences = differences
        )
    }
    
    /**
     * Compares V1 and V2 batch responses.
     */
    private fun compareBatchResults(
        v1Response: V1BatchGetCandidatesResponse,
        v2Response: com.solicitation.serving.BatchGetCandidatesResponse
    ): ComparisonResult {
        val differences = mutableListOf<String>()
        
        // Compare total counts
        if (v1Response.totalCount != v2Response.metadata.totalCount) {
            differences.add("Total count mismatch: v1=${v1Response.totalCount}, v2=${v2Response.metadata.totalCount}")
        }
        
        // Compare customer results
        val v1Customers = v1Response.results.keys
        val v2Customers = v2Response.results.keys
        
        if (v1Customers != v2Customers) {
            differences.add("Customer set mismatch")
        }
        
        // Compare per-customer counts
        for (customerId in v1Customers.intersect(v2Customers)) {
            val v1Count = v1Response.results[customerId]?.size ?: 0
            val v2Count = v2Response.results[customerId]?.size ?: 0
            
            if (v1Count != v2Count) {
                differences.add("Customer $customerId: v1=$v1Count, v2=$v2Count")
            }
        }
        
        return ComparisonResult(
            resultsMatch = differences.isEmpty(),
            differences = differences
        )
    }
    
    /**
     * Shuts down the shadow executor.
     */
    fun shutdown() {
        shadowExecutor.shutdown()
        try {
            if (!shadowExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                shadowExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            shadowExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}

/**
 * Result of comparing V1 and V2 responses.
 */
private data class ComparisonResult(
    val candidatesMatch: Boolean = false,
    val resultsMatch: Boolean = false,
    val differences: List<String>
)
