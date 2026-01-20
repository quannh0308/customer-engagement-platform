package com.ceap.channels

import com.ceap.model.Candidate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Queue for managing rate-limited candidates.
 * 
 * When rate limits are exceeded, candidates are queued for later delivery.
 * The queue is organized per channel to allow independent processing.
 * 
 * This is a simple in-memory queue. For production use, consider:
 * - Persistent queue (SQS, Kinesis, etc.)
 * - Queue size limits
 * - TTL for queued items
 * - Priority queuing
 */
class DeliveryQueue {
    
    /**
     * Queues per channel.
     * Key: channelId, Value: Queue of QueuedDelivery items
     */
    private val queues = ConcurrentHashMap<String, ConcurrentLinkedQueue<QueuedDelivery>>()
    
    /**
     * Enqueues candidates for later delivery.
     * 
     * @param channelId Channel identifier
     * @param candidates List of candidates to enqueue
     * @param context Delivery context
     */
    fun enqueue(channelId: String, candidates: List<Candidate>, context: DeliveryContext) {
        val queue = queues.computeIfAbsent(channelId) {
            ConcurrentLinkedQueue()
        }
        
        val queuedDelivery = QueuedDelivery(
            candidates = candidates,
            context = context,
            enqueuedAt = System.currentTimeMillis()
        )
        
        queue.offer(queuedDelivery)
        
        logger.info {
            "Enqueued ${candidates.size} candidates for channel $channelId. " +
            "Queue size: ${queue.size}"
        }
    }
    
    /**
     * Dequeues candidates for delivery.
     * 
     * @param channelId Channel identifier
     * @param maxCount Maximum number of items to dequeue (default: 1)
     * @return List of queued deliveries (may be empty)
     */
    fun dequeue(channelId: String, maxCount: Int = 1): List<QueuedDelivery> {
        val queue = queues[channelId] ?: return emptyList()
        
        val items = mutableListOf<QueuedDelivery>()
        repeat(maxCount) {
            val item = queue.poll()
            if (item != null) {
                items.add(item)
            }
        }
        
        if (items.isNotEmpty()) {
            logger.info {
                "Dequeued ${items.size} items for channel $channelId. " +
                "Remaining queue size: ${queue.size}"
            }
        }
        
        return items
    }
    
    /**
     * Peeks at the next queued delivery without removing it.
     * 
     * @param channelId Channel identifier
     * @return Next queued delivery, or null if queue is empty
     */
    fun peek(channelId: String): QueuedDelivery? {
        val queue = queues[channelId] ?: return null
        return queue.peek()
    }
    
    /**
     * Gets the current queue size for the given channel.
     * 
     * @param channelId Channel identifier
     * @return Queue size
     */
    fun getQueueSize(channelId: String): Int {
        val queue = queues[channelId] ?: return 0
        return queue.size
    }
    
    /**
     * Checks if the queue is empty for the given channel.
     * 
     * @param channelId Channel identifier
     * @return true if queue is empty, false otherwise
     */
    fun isEmpty(channelId: String): Boolean {
        val queue = queues[channelId] ?: return true
        return queue.isEmpty()
    }
    
    /**
     * Clears the queue for the given channel.
     * 
     * @param channelId Channel identifier
     * @return Number of items cleared
     */
    fun clear(channelId: String): Int {
        val queue = queues[channelId] ?: return 0
        val size = queue.size
        queue.clear()
        
        logger.info { "Cleared queue for channel $channelId. Removed $size items." }
        
        return size
    }
    
    /**
     * Clears all queues.
     * 
     * @return Total number of items cleared
     */
    fun clearAll(): Int {
        var totalCleared = 0
        queues.forEach { (channelId, queue) ->
            val size = queue.size
            queue.clear()
            totalCleared += size
            
            logger.info { "Cleared queue for channel $channelId. Removed $size items." }
        }
        
        queues.clear()
        
        return totalCleared
    }
    
    /**
     * Gets statistics for all queues.
     * 
     * @return Map of channelId to queue size
     */
    fun getQueueStats(): Map<String, Int> {
        return queues.mapValues { (_, queue) -> queue.size }
    }
}

/**
 * Represents a queued delivery.
 * 
 * @property candidates List of candidates to deliver
 * @property context Delivery context
 * @property enqueuedAt Timestamp when the delivery was enqueued (epoch millis)
 */
data class QueuedDelivery(
    val candidates: List<Candidate>,
    val context: DeliveryContext,
    val enqueuedAt: Long
)
