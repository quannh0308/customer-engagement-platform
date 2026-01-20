package com.ceap.workflow.reactive

import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.*
import java.time.Instant

/**
 * Interface for event deduplication tracking
 */
interface IEventDeduplicationTracker {
    fun isDuplicate(event: CustomerEvent): Boolean
    fun track(event: CustomerEvent)
}

/**
 * Tracks recent events for deduplication.
 * 
 * Uses DynamoDB to track events within a configurable time window.
 * Events are deduplicated based on customer-subject pair.
 * 
 * Validates: Requirements 9.5
 */
open class EventDeduplicationTracker(
    private val deduplicationWindowSeconds: Long = 300, // 5 minutes default
    private val tableName: String = System.getenv("DEDUPLICATION_TABLE") ?: "solicitation-event-deduplication"
) : IEventDeduplicationTracker {
    
    private val logger = LoggerFactory.getLogger(EventDeduplicationTracker::class.java)
    private val dynamoDb: DynamoDbClient = DynamoDbClient.builder().build()
    
    /**
     * Check if an event is a duplicate within the deduplication window
     */
    override fun isDuplicate(event: CustomerEvent): Boolean {
        val key = getDeduplicationKey(event)
        val now = Instant.now()
        val windowStart = now.minusSeconds(deduplicationWindowSeconds)
        
        try {
            // Query for recent events with this key
            val response = dynamoDb.getItem(
                GetItemRequest.builder()
                    .tableName(tableName)
                    .key(mapOf(
                        "deduplicationKey" to AttributeValue.builder().s(key).build()
                    ))
                    .build()
            )
            
            if (response.hasItem()) {
                val item = response.item()
                val lastEventTime = item["lastEventTime"]?.n()?.toLongOrNull()
                
                if (lastEventTime != null) {
                    val lastEvent = Instant.ofEpochSecond(lastEventTime)
                    
                    // Check if last event was within deduplication window
                    if (lastEvent.isAfter(windowStart)) {
                        logger.info("Duplicate event detected: key={}, lastEventTime={}, windowStart={}", 
                            key, lastEvent, windowStart)
                        return true
                    }
                }
            }
            
            return false
            
        } catch (e: Exception) {
            logger.error("Failed to check deduplication: key={}, error={}", key, e.message)
            // On error, allow the event through (fail open)
            return false
        }
    }
    
    /**
     * Track an event for deduplication
     */
    override fun track(event: CustomerEvent) {
        val key = getDeduplicationKey(event)
        val now = Instant.now()
        val ttl = now.plusSeconds(deduplicationWindowSeconds * 2).epochSecond // TTL = 2x window
        
        try {
            dynamoDb.putItem(
                PutItemRequest.builder()
                    .tableName(tableName)
                    .item(mapOf(
                        "deduplicationKey" to AttributeValue.builder().s(key).build(),
                        "lastEventTime" to AttributeValue.builder().n(now.epochSecond.toString()).build(),
                        "customerId" to AttributeValue.builder().s(event.customerId).build(),
                        "subjectId" to AttributeValue.builder().s(event.subjectId).build(),
                        "eventType" to AttributeValue.builder().s(event.eventType).build(),
                        "ttl" to AttributeValue.builder().n(ttl.toString()).build()
                    ))
                    .build()
            )
            
            logger.debug("Tracked event for deduplication: key={}, eventTime={}", key, now)
            
        } catch (e: Exception) {
            logger.error("Failed to track event for deduplication: key={}, error={}", key, e.message)
            // Non-fatal error, continue processing
        }
    }
    
    /**
     * Generate deduplication key from event
     * Format: {customerId}:{subjectType}:{subjectId}:{programId}
     */
    private fun getDeduplicationKey(event: CustomerEvent): String {
        return "${event.customerId}:${event.subjectType}:${event.subjectId}:${event.programId}"
    }
}
