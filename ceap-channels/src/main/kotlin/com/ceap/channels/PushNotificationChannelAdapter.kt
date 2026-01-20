package com.ceap.channels

import com.ceap.model.Candidate
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Push notification channel adapter for delivering candidates through mobile push notifications.
 * 
 * This adapter:
 * - Integrates with push notification service (e.g., SNS, FCM, APNS)
 * - Formats candidates for push notification display
 * - Supports shadow mode for testing
 * - Tracks delivery status and engagement
 * 
 * **Validates: Requirements 15.2**
 */
class PushNotificationChannelAdapter : BaseChannelAdapter() {
    
    /**
     * Push notification templates per program.
     * Key: programId, Value: template configuration
     */
    private val pushTemplates = mutableMapOf<String, PushNotificationTemplate>()
    
    /**
     * Device token registry - maps customer IDs to device tokens.
     * In production, this would be backed by a database or service.
     */
    private val deviceTokens = mutableMapOf<String, List<String>>()
    
    override fun getChannelId(): String = "push"
    
    override fun getChannelType(): ChannelType = ChannelType.PUSH
    
    override fun configure(config: Map<String, Any>) {
        super.configure(config)
        
        // Extract push notification specific configuration
        @Suppress("UNCHECKED_CAST")
        val templates = config["templates"] as? Map<String, Map<String, Any>>
        templates?.forEach { (programId, templateConfig) ->
            val template = PushNotificationTemplate(
                templateId = templateConfig["templateId"] as? String ?: "default-push",
                title = templateConfig["title"] as? String ?: "We'd love your feedback",
                body = templateConfig["body"] as? String ?: "Share your experience with us",
                sound = templateConfig["sound"] as? String ?: "default",
                badge = (templateConfig["badge"] as? Number)?.toInt(),
                category = templateConfig["category"] as? String,
                deepLink = templateConfig["deepLink"] as? String
            )
            pushTemplates[programId] = template
            logger.info { "Configured push notification template for program $programId: ${template.templateId}" }
        }
        
        // Extract device token mappings
        @Suppress("UNCHECKED_CAST")
        val tokens = config["deviceTokens"] as? Map<String, List<String>>
        tokens?.forEach { (customerId, tokens) ->
            deviceTokens[customerId] = tokens
        }
        logger.info { "Loaded device tokens for ${deviceTokens.size} customers" }
    }
    
    override fun doDeliver(
        candidates: List<Candidate>,
        context: DeliveryContext,
        startTime: Long
    ): DeliveryResult {
        logger.info {
            "Starting push notification delivery for program ${context.programId}, " +
            "marketplace ${context.marketplace}, " +
            "candidateCount=${candidates.size}"
        }
        
        // Get template for this program
        val template = pushTemplates[context.programId]
        if (template == null) {
            logger.error { "No push notification template configured for program ${context.programId}" }
            val failed = candidates.map { candidate ->
                FailedDelivery(
                    candidate = candidate,
                    errorCode = "NO_TEMPLATE",
                    errorMessage = "No push notification template configured for program ${context.programId}",
                    timestamp = System.currentTimeMillis(),
                    retryable = false
                )
            }
            return DeliveryResult(
                delivered = emptyList(),
                failed = failed,
                metrics = DeliveryMetrics(
                    totalCandidates = candidates.size,
                    deliveredCount = 0,
                    failedCount = failed.size,
                    durationMs = System.currentTimeMillis() - startTime,
                    rateLimitedCount = 0,
                    shadowMode = false
                )
            )
        }
        
        // Send push notifications
        val delivered = mutableListOf<DeliveredCandidate>()
        val failed = mutableListOf<FailedDelivery>()
        
        candidates.forEach { candidate ->
            try {
                // Check if customer has device tokens
                val tokens = deviceTokens[candidate.customerId]
                if (tokens.isNullOrEmpty()) {
                    failed.add(
                        FailedDelivery(
                            candidate = candidate,
                            errorCode = "NO_DEVICE_TOKEN",
                            errorMessage = "No device tokens found for customer ${candidate.customerId}",
                            timestamp = System.currentTimeMillis(),
                            retryable = false
                        )
                    )
                    return@forEach
                }
                
                val deliveryId = sendPushNotification(candidate, template, tokens, context)
                
                delivered.add(
                    DeliveredCandidate(
                        candidate = candidate,
                        deliveryId = deliveryId,
                        timestamp = System.currentTimeMillis(),
                        channelMetadata = mapOf(
                            "templateId" to template.templateId,
                            "channel" to "push",
                            "deviceCount" to tokens.size
                        )
                    )
                )
                
                logger.debug {
                    "Push notification sent: deliveryId=$deliveryId, " +
                    "customerId=${candidate.customerId}, " +
                    "deviceCount=${tokens.size}"
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to send push notification to customer ${candidate.customerId}" }
                failed.add(
                    FailedDelivery(
                        candidate = candidate,
                        errorCode = "SEND_FAILED",
                        errorMessage = e.message ?: "Unknown error",
                        timestamp = System.currentTimeMillis(),
                        retryable = true
                    )
                )
            }
        }
        
        val durationMs = System.currentTimeMillis() - startTime
        
        logger.info {
            "Push notification delivery completed: deliveredCount=${delivered.size}, " +
            "failedCount=${failed.size}, " +
            "durationMs=$durationMs"
        }
        
        return DeliveryResult(
            delivered = delivered,
            failed = failed,
            metrics = DeliveryMetrics(
                totalCandidates = candidates.size,
                deliveredCount = delivered.size,
                failedCount = failed.size,
                durationMs = durationMs,
                rateLimitedCount = 0,
                shadowMode = false
            )
        )
    }
    
    /**
     * Sends a push notification to device tokens.
     * 
     * In a real implementation, this would:
     * 1. Render the notification template with candidate data
     * 2. Send via SNS/FCM/APNS to all device tokens
     * 3. Handle platform-specific formatting (iOS vs Android)
     * 4. Track delivery status and engagement
     * 5. Handle token expiration and cleanup
     * 
     * @param candidate Candidate to send notification for
     * @param template Notification template to use
     * @param deviceTokens List of device tokens for the customer
     * @param context Delivery context
     * @return Delivery ID
     */
    private fun sendPushNotification(
        candidate: Candidate,
        template: PushNotificationTemplate,
        deviceTokens: List<String>,
        context: DeliveryContext
    ): String {
        val deliveryId = "push-${System.nanoTime()}"
        
        // Build notification payload
        val payload = mapOf(
            "deliveryId" to deliveryId,
            "customerId" to candidate.customerId,
            "programId" to context.programId,
            "marketplace" to context.marketplace,
            "notification" to mapOf(
                "title" to template.title,
                "body" to template.body,
                "sound" to template.sound,
                "badge" to template.badge,
                "category" to template.category
            ),
            "data" to mapOf(
                "subjectType" to candidate.subject.type,
                "subjectId" to candidate.subject.id,
                "deepLink" to template.deepLink,
                "eventDate" to candidate.attributes.eventDate.toString()
            )
        )
        
        logger.debug {
            "Sending push notification: deliveryId=$deliveryId, " +
            "customerId=${candidate.customerId}, " +
            "template=${template.templateId}, " +
            "deviceCount=${deviceTokens.size}, " +
            "payload=$payload"
        }
        
        // In production, this would:
        // 1. Use SNS to send to multiple platforms
        // 2. Or use FCM/APNS directly
        // 3. Handle platform-specific payload formatting
        // 4. Track message IDs for delivery confirmation
        
        return deliveryId
    }
    
    /**
     * Registers device tokens for a customer.
     * 
     * @param customerId Customer identifier
     * @param tokens List of device tokens
     */
    fun registerDeviceTokens(customerId: String, tokens: List<String>) {
        deviceTokens[customerId] = tokens
        logger.info { "Registered ${tokens.size} device tokens for customer $customerId" }
    }
    
    /**
     * Removes device tokens for a customer.
     * 
     * @param customerId Customer identifier
     */
    fun removeDeviceTokens(customerId: String) {
        deviceTokens.remove(customerId)
        logger.info { "Removed device tokens for customer $customerId" }
    }
}

/**
 * Push notification template configuration.
 * 
 * @property templateId Template identifier
 * @property title Notification title
 * @property body Notification body text
 * @property sound Sound to play (default, custom sound name, or null for silent)
 * @property badge Badge count to display on app icon
 * @property category Notification category for iOS
 * @property deepLink Deep link URL to open when notification is tapped
 */
data class PushNotificationTemplate(
    val templateId: String,
    val title: String,
    val body: String,
    val sound: String = "default",
    val badge: Int? = null,
    val category: String? = null,
    val deepLink: String? = null
)
