package com.ceap.serving

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.ceap.storage.DynamoDBCandidateRepository
import mu.KotlinLogging
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

private val logger = KotlinLogging.logger {}

/**
 * AWS Lambda handler for serving API endpoints.
 * 
 * Handles API Gateway requests for candidate retrieval with low latency.
 * Supports both single and batch customer queries.
 */
class ServingLambdaHandler : RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
    
    private val dynamoDbClient = DynamoDbClient.builder().build()
    private val candidateRepository = DynamoDBCandidateRepository(dynamoDbClient)
    private val rankingStrategy = DefaultRankingStrategy()
    private val eligibilityChecker = DefaultEligibilityChecker()
    private val fallbackHandler = DefaultFallbackHandler(candidateRepository)
    
    private val servingAPI = ServingAPIImpl(
        candidateRepository = candidateRepository,
        rankingStrategy = rankingStrategy,
        eligibilityChecker = eligibilityChecker,
        fallbackHandler = fallbackHandler
    )
    
    override fun handleRequest(
        input: APIGatewayProxyRequestEvent,
        context: Context
    ): APIGatewayProxyResponseEvent {
        
        logger.info { "Received request: ${input.httpMethod} ${input.path}" }
        
        return try {
            when {
                input.path == "/candidates" && input.httpMethod == "GET" -> {
                    handleGetCandidates(input)
                }
                input.path == "/candidates/batch" && input.httpMethod == "POST" -> {
                    handleBatchGetCandidates(input)
                }
                else -> {
                    createResponse(404, mapOf("error" to "Not found"))
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Request failed" }
            createResponse(500, mapOf("error" to "Internal server error", "message" to (e.message ?: "Unknown error")))
        }
    }
    
    /**
     * Handles GET /candidates request for a single customer.
     */
    private fun handleGetCandidates(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val queryParams = input.queryStringParameters ?: emptyMap()
        
        val customerId = queryParams["customerId"]
            ?: return createResponse(400, mapOf("error" to "Missing customerId parameter"))
        
        val marketplace = queryParams["marketplace"]
            ?: return createResponse(400, mapOf("error" to "Missing marketplace parameter"))
        
        val request = GetCandidatesRequest(
            customerId = customerId,
            channel = queryParams["channel"],
            program = queryParams["program"],
            marketplace = marketplace,
            limit = queryParams["limit"]?.toIntOrNull() ?: 10,
            includeScores = queryParams["includeScores"]?.toBoolean() ?: false,
            refreshEligibility = queryParams["refreshEligibility"]?.toBoolean() ?: false
        )
        
        val response = servingAPI.getCandidatesForCustomer(request)
        
        return createResponse(200, response)
    }
    
    /**
     * Handles POST /candidates/batch request for multiple customers.
     */
    private fun handleBatchGetCandidates(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val body = input.body ?: return createResponse(400, mapOf("error" to "Missing request body"))
        
        val request = try {
            objectMapper.readValue(body, BatchGetCandidatesRequest::class.java)
        } catch (e: Exception) {
            return createResponse(400, mapOf("error" to "Invalid request body", "message" to (e.message ?: "Parse error")))
        }
        
        val response = servingAPI.getCandidatesForCustomers(request)
        
        return createResponse(200, response)
    }
    
    /**
     * Creates an API Gateway response.
     */
    private fun createResponse(statusCode: Int, body: Any): APIGatewayProxyResponseEvent {
        return APIGatewayProxyResponseEvent().apply {
            this.statusCode = statusCode
            this.body = objectMapper.writeValueAsString(body)
            this.headers = mapOf(
                "Content-Type" to "application/json",
                "Access-Control-Allow-Origin" to "*"
            )
        }
    }
}
