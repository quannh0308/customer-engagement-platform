package com.ceap.channels

import com.ceap.model.Candidate
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * In-app channel adapter for delivering candidates through in-app cards.
 * 
 * This adapter:
 * - Integrates with the serving API for real-time candidate retrieval
 * - Formats candidates for in-app display
 * - Supports shadow mode for testing
 * - Tracks delivery status
 * 
 * **Validates: Requirements 15.1, 15.2**
 */
class InAppChannelAdapter : BaseChannelAdapter() {
    
    /**
     * In-app card templates per program.
     * Key: programId, Value: template configuration
     */
    private val cardTemplates = mutableMapOf<String, InAppCardTemplate>()
    
    override fun getChannelId(): String = "in-app"
    
    override fun getChannelType(): ChannelType = ChannelType.IN_APP
    
    override fun configure(config: Map<String, Any>) {
        super.configure(config)
        
        // Extract in-app specific configuration
        @Suppress("UNCHECKED_CAST")
        val templates = config["templates"] as? Map<String, Map<String, Any>>
        templates?.forEach { (programId, templateConfig) ->
            val template = InAppCardTemplate(
                templateId = templateConfig["templateId"] as? String ?: "default-card",
                title = templateConfig["title"] as? String ?: "We'd love your feedback",
                description = templateConfig["description"] as? String ?: "Share your experience",
                actionText = templateConfig["actionText"] as? String ?: "Provide Feedback",
                imageUrl = templateConfig["imageUrl"] as? String?,
                priority = (templateConfig["priority"] as? Number)?.toInt() ?: 1
            )
            cardTemplates[programId] = template
            logger.info { "Configured in-app card template for program $programId: ${template.templateId}" }
        }
    }
    
    override fun doDeliver(
        candidates: List<Candidate>,
        context: DeliveryContext,
        startTime: Long
    ): DeliveryResult {
        logger.info {
            "Starting in-app delivery for program ${context.programId}, " +
            "marketplace ${context.marketplace}, " +
            "candidateCount=${candidates.size}"
        }
        
        // Get template for this program
        val template = cardTemplates[context.programId]
        if (template == null) {
            logger.error { "No in-app card template configured for program ${context.programId}" }
            val failed = candidates.map { candidate ->
                FailedDelivery(
                    candidate = candidate,
                    errorCode = "NO_TEMPLATE",
                    errorMessage = "No in-app card template configured for program ${context.programId}",
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
        
        // Format candidates as in-app cards
        val delivered = mutableListOf<DeliveredCandidate>()
        val failed = mutableListOf<FailedDelivery>()
        
        candidates.forEach { candidate ->
            try {
                val deliveryId = formatInAppCard(candidate, template, context)
                
                delivered.add(
                    DeliveredCandidate(
                        candidate = candidate,
                        deliveryId = deliveryId,
                        timestamp = System.currentTimeMillis(),
                        channelMetadata = mapOf(
                            "templateId" to template.templateId,
                            "channel" to "in-app",
                            "cardFormat" to "standard"
                        )
                    )
                )
                
                logger.debug {
                    "In-app card formatted: deliveryId=$deliveryId, " +
                    "customerId=${candidate.customerId}, " +
                    "subjectId=${candidate.subject.id}"
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to format in-app card for customer ${candidate.customerId}" }
                failed.add(
                    FailedDelivery(
                        candidate = candidate,
                        errorCode = "FORMAT_FAILED",
                        errorMessage = e.message ?: "Unknown error",
                        timestamp = System.currentTimeMillis(),
                        retryable = true
                    )
                )
            }
        }
        
        val durationMs = System.currentTimeMillis() - startTime
        
        logger.info {
            "In-app delivery completed: deliveredCount=${delivered.size}, " +
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
     * Formats a candidate as an in-app card.
     * 
     * In a real implementation, this would:
     * 1. Render the card template with candidate data
     * 2. Include subject metadata (images, descriptions)
     * 3. Generate deep links for actions
     * 4. Store the formatted card for serving API retrieval
     * 
     * @param candidate Candidate to format
     * @param template Card template to use
     * @param context Delivery context
     * @return Delivery ID
     */
    private fun formatInAppCard(
        candidate: Candidate,
        template: InAppCardTemplate,
        context: DeliveryContext
    ): String {
        val deliveryId = "in-app-${System.nanoTime()}"
        
        // Build card data structure
        val cardData = mapOf(
            "deliveryId" to deliveryId,
            "customerId" to candidate.customerId,
            "programId" to context.programId,
            "marketplace" to context.marketplace,
            "templateId" to template.templateId,
            "title" to template.title,
            "description" to template.description,
            "actionText" to template.actionText,
            "imageUrl" to template.imageUrl,
            "priority" to template.priority,
            "subject" to mapOf(
                "type" to candidate.subject.type,
                "id" to candidate.subject.id,
                "metadata" to candidate.subject.metadata
            ),
            "eventDate" to candidate.attributes.eventDate.toString(),
            "createdAt" to System.currentTimeMillis()
        )
        
        logger.debug {
            "Formatted in-app card: deliveryId=$deliveryId, " +
            "customerId=${candidate.customerId}, " +
            "template=${template.templateId}, " +
            "cardData=$cardData"
        }
        
        // In production, this would store the card data in a cache or database
        // for retrieval by the serving API
        
        return deliveryId
    }
}

/**
 * In-app card template configuration.
 * 
 * @property templateId Template identifier
 * @property title Card title
 * @property description Card description
 * @property actionText Text for the action button
 * @property imageUrl Optional image URL for the card
 * @property priority Card priority (higher = more prominent)
 */
data class InAppCardTemplate(
    val templateId: String,
    val title: String,
    val description: String,
    val actionText: String,
    val imageUrl: String? = null,
    val priority: Int = 1
)
