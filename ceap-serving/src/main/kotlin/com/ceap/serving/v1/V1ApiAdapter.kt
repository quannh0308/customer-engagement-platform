package com.ceap.serving.v1

import com.ceap.model.Candidate
import com.ceap.model.Context
import com.ceap.serving.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Adapter layer that translates V1 API requests to V2 backend calls.
 * 
 * Maintains backward compatibility by accepting V1 request formats,
 * translating them to V2 format, calling the V2 backend, and translating
 * the V2 response back to V1 format.
 * 
 * This allows existing clients to continue using the V1 API while the
 * backend has been migrated to V2 implementation.
 * 
 * @property v2ServingAPI The V2 backend serving API
 * @property usageTracker Optional tracker for V1 API usage metrics
 */
class V1ApiAdapter(
    private val v2ServingAPI: ServingAPI,
    private val usageTracker: V1UsageTracker? = null
) {
    
    /**
     * Handles V1 get candidates request.
     * 
     * Translates V1 request to V2, calls V2 backend, and translates response back to V1.
     */
    fun getCandidatesForCustomer(v1Request: V1GetCandidatesRequest): V1GetCandidatesResponse {
        val startTime = System.currentTimeMillis()
        
        try {
            logger.debug { "V1 API: Getting candidates for customer ${v1Request.customerId}" }
            
            // Track V1 API usage
            usageTracker?.trackRequest(
                endpoint = "getCandidatesForCustomer",
                customerId = v1Request.customerId,
                channel = v1Request.channel,
                program = v1Request.program
            )
            
            // Translate V1 request to V2
            val v2Request = translateToV2Request(v1Request)
            
            // Call V2 backend
            val v2Response = v2ServingAPI.getCandidatesForCustomer(v2Request)
            
            // Translate V2 response to V1
            val v1Response = translateToV1Response(v2Response)
            
            val latency = System.currentTimeMillis() - startTime
            logger.info { "V1 API: Retrieved ${v1Response.candidates.size} candidates in ${latency}ms" }
            
            return v1Response
            
        } catch (e: Exception) {
            logger.error(e) { "V1 API: Failed to get candidates for customer ${v1Request.customerId}" }
            throw V1ApiException("Failed to get candidates", e)
        }
    }
    
    /**
     * Handles V1 batch get candidates request.
     */
    fun getCandidatesForCustomers(v1Request: V1BatchGetCandidatesRequest): V1BatchGetCandidatesResponse {
        val startTime = System.currentTimeMillis()
        
        try {
            logger.debug { "V1 API: Getting candidates for ${v1Request.customerIds.size} customers" }
            
            // Track V1 API usage
            usageTracker?.trackBatchRequest(
                endpoint = "getCandidatesForCustomers",
                customerIds = v1Request.customerIds,
                channel = v1Request.channel,
                program = v1Request.program
            )
            
            // Translate V1 request to V2
            val v2Request = translateToV2BatchRequest(v1Request)
            
            // Call V2 backend
            val v2Response = v2ServingAPI.getCandidatesForCustomers(v2Request)
            
            // Translate V2 response to V1
            val v1Response = translateToV1BatchResponse(v2Response)
            
            val latency = System.currentTimeMillis() - startTime
            logger.info { "V1 API: Retrieved candidates for ${v1Response.results.size} customers in ${latency}ms" }
            
            return v1Response
            
        } catch (e: Exception) {
            logger.error(e) { "V1 API: Failed to get candidates for batch request" }
            throw V1ApiException("Failed to get candidates for batch", e)
        }
    }
    
    /**
     * Translates V1 single customer request to V2 format.
     */
    private fun translateToV2Request(v1Request: V1GetCandidatesRequest): GetCandidatesRequest {
        return GetCandidatesRequest(
            customerId = v1Request.customerId,
            channel = v1Request.channel,
            program = v1Request.program,
            marketplace = v1Request.marketplace,
            limit = v1Request.limit,
            includeScores = true, // V1 always includes scores if available
            refreshEligibility = false // V1 doesn't support real-time refresh
        )
    }
    
    /**
     * Translates V1 batch request to V2 format.
     */
    private fun translateToV2BatchRequest(v1Request: V1BatchGetCandidatesRequest): BatchGetCandidatesRequest {
        return BatchGetCandidatesRequest(
            customerIds = v1Request.customerIds,
            channel = v1Request.channel,
            program = v1Request.program,
            marketplace = v1Request.marketplace,
            limit = v1Request.limit,
            includeScores = true
        )
    }
    
    /**
     * Translates V2 response to V1 format.
     */
    private fun translateToV1Response(v2Response: GetCandidatesResponse): V1GetCandidatesResponse {
        val v1Candidates = v2Response.candidates.map { translateToV1Candidate(it) }
        
        return V1GetCandidatesResponse(
            candidates = v1Candidates,
            totalCount = v2Response.metadata.totalCount,
            latencyMs = v2Response.latencyMs
        )
    }
    
    /**
     * Translates V2 batch response to V1 format.
     */
    private fun translateToV1BatchResponse(v2Response: BatchGetCandidatesResponse): V1BatchGetCandidatesResponse {
        val v1Results = v2Response.results.mapValues { (_, candidates) ->
            candidates.map { translateToV1Candidate(it) }
        }
        
        return V1BatchGetCandidatesResponse(
            results = v1Results,
            totalCount = v2Response.metadata.totalCount,
            latencyMs = v2Response.latencyMs
        )
    }
    
    /**
     * Translates V2 candidate to V1 format.
     * 
     * Extracts the necessary fields from the V2 candidate model and
     * maps them to the simpler V1 structure.
     */
    private fun translateToV1Candidate(v2Candidate: Candidate): V1Candidate {
        // Extract program and marketplace from context
        val programContext = v2Candidate.context.find { it.type == "program" }
        val marketplaceContext = v2Candidate.context.find { it.type == "marketplace" }
        
        // Get the primary score (first score if multiple exist)
        val primaryScore = v2Candidate.scores?.values?.firstOrNull()?.value
        
        return V1Candidate(
            customerId = v2Candidate.customerId,
            subjectType = v2Candidate.subject.type,
            subjectId = v2Candidate.subject.id,
            programId = programContext?.id ?: "unknown",
            marketplaceId = marketplaceContext?.id ?: "unknown",
            score = primaryScore,
            eventDate = v2Candidate.attributes.eventDate.toString(),
            createdAt = v2Candidate.metadata.createdAt.toString()
        )
    }
}

/**
 * Exception thrown by V1 API adapter.
 */
class V1ApiException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
