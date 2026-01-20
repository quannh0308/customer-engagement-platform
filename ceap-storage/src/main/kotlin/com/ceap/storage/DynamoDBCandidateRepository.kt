package com.ceap.storage

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.ceap.model.Candidate
import mu.KotlinLogging
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

/**
 * DynamoDB implementation of CandidateRepository.
 * 
 * Implements CRUD operations, batch writes, queries, and optimistic locking
 * using DynamoDB SDK v2.
 * 
 * @property dynamoDbClient DynamoDB client
 * @property tableName Name of the candidates table
 */
class DynamoDBCandidateRepository(
    private val dynamoDbClient: DynamoDbClient,
    private val tableName: String = DynamoDBConfig.TableNames.CANDIDATES
) : CandidateRepository {
    
    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
    
    companion object {
        private const val BATCH_WRITE_MAX_SIZE = 25
        private const val MAX_RETRY_ATTEMPTS = 3
    }
    
    override fun create(candidate: Candidate): Candidate {
        try {
            val item = candidateToItem(candidate)
            
            val request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(PK)")
                .build()
            
            dynamoDbClient.putItem(request)
            logger.debug { "Created candidate: customerId=${candidate.customerId}, subject=${candidate.subject.id}" }
            
            return candidate
        } catch (e: ConditionalCheckFailedException) {
            throw StorageException("Candidate already exists", e)
        } catch (e: Exception) {
            logger.error(e) { "Failed to create candidate" }
            throw StorageException("Failed to create candidate", e)
        }
    }
    
    override fun get(
        customerId: String,
        programId: String,
        marketplaceId: String,
        subjectType: String,
        subjectId: String
    ): Candidate? {
        try {
            val pk = buildPK(customerId, programId, marketplaceId)
            val sk = buildSK(subjectType, subjectId)
            
            val request = GetItemRequest.builder()
                .tableName(tableName)
                .key(mapOf(
                    "PK" to AttributeValue.builder().s(pk).build(),
                    "SK" to AttributeValue.builder().s(sk).build()
                ))
                .build()
            
            val response = dynamoDbClient.getItem(request)
            
            return if (response.hasItem()) {
                itemToCandidate(response.item())
            } else {
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get candidate" }
            throw StorageException("Failed to get candidate", e)
        }
    }
    
    override fun update(candidate: Candidate): Candidate {
        try {
            // Extract context for key building
            val programContext = candidate.context.find { it.type == "program" }
                ?: throw StorageException("Candidate must have program context")
            val marketplaceContext = candidate.context.find { it.type == "marketplace" }
                ?: throw StorageException("Candidate must have marketplace context")
            
            val pk = buildPK(candidate.customerId, programContext.id, marketplaceContext.id)
            val sk = buildSK(candidate.subject.type, candidate.subject.id)
            
            // Increment version and update timestamp
            val updatedCandidate = candidate.copy(
                metadata = candidate.metadata.copy(
                    version = candidate.metadata.version + 1,
                    updatedAt = Instant.now()
                )
            )
            
            val item = candidateToItem(updatedCandidate)
            
            val request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_exists(PK) AND version = :expectedVersion")
                .expressionAttributeValues(mapOf(
                    ":expectedVersion" to AttributeValue.builder().n(candidate.metadata.version.toString()).build()
                ))
                .build()
            
            dynamoDbClient.putItem(request)
            logger.debug { "Updated candidate: customerId=${candidate.customerId}, version=${updatedCandidate.metadata.version}" }
            
            return updatedCandidate
        } catch (e: ConditionalCheckFailedException) {
            throw OptimisticLockException("Version conflict: candidate has been modified by another process")
        } catch (e: Exception) {
            logger.error(e) { "Failed to update candidate" }
            throw StorageException("Failed to update candidate", e)
        }
    }
    
    override fun delete(
        customerId: String,
        programId: String,
        marketplaceId: String,
        subjectType: String,
        subjectId: String
    ) {
        try {
            val pk = buildPK(customerId, programId, marketplaceId)
            val sk = buildSK(subjectType, subjectId)
            
            val request = DeleteItemRequest.builder()
                .tableName(tableName)
                .key(mapOf(
                    "PK" to AttributeValue.builder().s(pk).build(),
                    "SK" to AttributeValue.builder().s(sk).build()
                ))
                .build()
            
            dynamoDbClient.deleteItem(request)
            logger.debug { "Deleted candidate: customerId=$customerId, subject=$subjectId" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete candidate" }
            throw StorageException("Failed to delete candidate", e)
        }
    }
    
    override fun batchWrite(candidates: List<Candidate>): BatchWriteResult {
        val successfulItems = mutableListOf<Candidate>()
        val failedItems = mutableListOf<FailedItem>()
        
        try {
            // Split into batches of 25 (DynamoDB limit)
            val batches = candidates.chunked(BATCH_WRITE_MAX_SIZE)
            
            for (batch in batches) {
                var unprocessedItems = batch
                var retryCount = 0
                
                while (unprocessedItems.isNotEmpty() && retryCount < MAX_RETRY_ATTEMPTS) {
                    val writeRequests = unprocessedItems.map { candidate ->
                        WriteRequest.builder()
                            .putRequest(
                                PutRequest.builder()
                                    .item(candidateToItem(candidate))
                                    .build()
                            )
                            .build()
                    }
                    
                    val request = BatchWriteItemRequest.builder()
                        .requestItems(mapOf(tableName to writeRequests))
                        .build()
                    
                    val response = dynamoDbClient.batchWriteItem(request)
                    
                    // Track successful items
                    val processedCount = unprocessedItems.size - (response.unprocessedItems()[tableName]?.size ?: 0)
                    successfulItems.addAll(unprocessedItems.take(processedCount))
                    
                    // Handle unprocessed items
                    if (response.hasUnprocessedItems() && response.unprocessedItems().containsKey(tableName)) {
                        val unprocessedCount = response.unprocessedItems()[tableName]?.size ?: 0
                        unprocessedItems = unprocessedItems.takeLast(unprocessedCount)
                        retryCount++
                        
                        if (retryCount < MAX_RETRY_ATTEMPTS) {
                            // Exponential backoff
                            Thread.sleep((100L * (1 shl retryCount)))
                        }
                    } else {
                        unprocessedItems = emptyList()
                    }
                }
                
                // Add remaining unprocessed items as failures
                unprocessedItems.forEach { candidate ->
                    failedItems.add(FailedItem(candidate, "Failed after $MAX_RETRY_ATTEMPTS retry attempts"))
                }
            }
            
            logger.info { "Batch write completed: ${successfulItems.size} successful, ${failedItems.size} failed" }
            
            return BatchWriteResult(successfulItems, failedItems)
        } catch (e: Exception) {
            logger.error(e) { "Batch write operation failed" }
            throw StorageException("Batch write operation failed", e)
        }
    }
    
    override fun queryByProgramAndChannel(
        programId: String,
        channelId: String,
        limit: Int,
        ascending: Boolean
    ): List<Candidate> {
        try {
            val gsi1pk = "PROGRAM#$programId#CHANNEL#$channelId"
            
            val request = QueryRequest.builder()
                .tableName(tableName)
                .indexName(DynamoDBConfig.IndexNames.PROGRAM_CHANNEL_INDEX)
                .keyConditionExpression("GSI1PK = :gsi1pk")
                .expressionAttributeValues(mapOf(
                    ":gsi1pk" to AttributeValue.builder().s(gsi1pk).build()
                ))
                .scanIndexForward(ascending)
                .limit(limit)
                .build()
            
            val response = dynamoDbClient.query(request)
            
            return response.items().map { itemToCandidate(it) }
        } catch (e: Exception) {
            logger.error(e) { "Failed to query by program and channel" }
            throw StorageException("Failed to query by program and channel", e)
        }
    }
    
    override fun queryByProgramAndDate(
        programId: String,
        date: String,
        limit: Int
    ): List<Candidate> {
        try {
            val gsi2pk = "PROGRAM#$programId#DATE#$date"
            
            val request = QueryRequest.builder()
                .tableName(tableName)
                .indexName(DynamoDBConfig.IndexNames.PROGRAM_DATE_INDEX)
                .keyConditionExpression("GSI2PK = :gsi2pk")
                .expressionAttributeValues(mapOf(
                    ":gsi2pk" to AttributeValue.builder().s(gsi2pk).build()
                ))
                .limit(limit)
                .build()
            
            val response = dynamoDbClient.query(request)
            
            return response.items().map { itemToCandidate(it) }
        } catch (e: Exception) {
            logger.error(e) { "Failed to query by program and date" }
            throw StorageException("Failed to query by program and date", e)
        }
    }
    
    /**
     * Builds the partition key (PK) for a candidate.
     */
    private fun buildPK(customerId: String, programId: String, marketplaceId: String): String {
        return "CUSTOMER#$customerId#PROGRAM#$programId#MARKETPLACE#$marketplaceId"
    }
    
    /**
     * Builds the sort key (SK) for a candidate.
     */
    private fun buildSK(subjectType: String, subjectId: String): String {
        return "SUBJECT#$subjectType#$subjectId"
    }
    
    /**
     * Converts a Candidate to a DynamoDB item.
     */
    private fun candidateToItem(candidate: Candidate): Map<String, AttributeValue> {
        // Extract context for key building
        val programContext = candidate.context.find { it.type == "program" }
            ?: throw StorageException("Candidate must have program context")
        val marketplaceContext = candidate.context.find { it.type == "marketplace" }
            ?: throw StorageException("Candidate must have marketplace context")
        
        val pk = buildPK(candidate.customerId, programContext.id, marketplaceContext.id)
        val sk = buildSK(candidate.subject.type, candidate.subject.id)
        
        // Build GSI keys
        val channelEligibility = candidate.attributes.channelEligibility
        val primaryChannel = channelEligibility.entries.firstOrNull { it.value }?.key ?: "none"
        val primaryScore = candidate.scores?.values?.firstOrNull()?.value ?: 0.0
        
        val gsi1pk = "PROGRAM#${programContext.id}#CHANNEL#$primaryChannel"
        val gsi1sk = "SCORE#${String.format("%.6f", primaryScore)}#CUSTOMER#${candidate.customerId}"
        
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val createdDate = LocalDate.ofInstant(candidate.metadata.createdAt, ZoneOffset.UTC)
        val gsi2pk = "PROGRAM#${programContext.id}#DATE#${createdDate.format(dateFormatter)}"
        val gsi2sk = "CREATED#${candidate.metadata.createdAt.epochSecond}#CUSTOMER#${candidate.customerId}"
        
        // Convert candidate to JSON for storage
        val candidateJson = objectMapper.writeValueAsString(candidate)
        
        return mapOf(
            "PK" to AttributeValue.builder().s(pk).build(),
            "SK" to AttributeValue.builder().s(sk).build(),
            "GSI1PK" to AttributeValue.builder().s(gsi1pk).build(),
            "GSI1SK" to AttributeValue.builder().s(gsi1sk).build(),
            "GSI2PK" to AttributeValue.builder().s(gsi2pk).build(),
            "GSI2SK" to AttributeValue.builder().s(gsi2sk).build(),
            "customerId" to AttributeValue.builder().s(candidate.customerId).build(),
            "data" to AttributeValue.builder().s(candidateJson).build(),
            "version" to AttributeValue.builder().n(candidate.metadata.version.toString()).build(),
            "ttl" to AttributeValue.builder().n(candidate.metadata.expiresAt.epochSecond.toString()).build()
        )
    }
    
    /**
     * Converts a DynamoDB item to a Candidate.
     */
    private fun itemToCandidate(item: Map<String, AttributeValue>): Candidate {
        val candidateJson = item["data"]?.s() 
            ?: throw StorageException("Missing data field in DynamoDB item")
        
        return objectMapper.readValue(candidateJson, Candidate::class.java)
    }
}
