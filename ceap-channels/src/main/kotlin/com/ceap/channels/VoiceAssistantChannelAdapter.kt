package com.ceap.channels

import com.ceap.model.Candidate
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Voice assistant channel adapter for delivering candidates through voice interfaces.
 * 
 * This adapter:
 * - Integrates with voice assistant services (e.g., Alexa, Google Assistant)
 * - Formats candidates for voice interaction
 * - Supports shadow mode for testing
 * - Tracks delivery status and engagement
 * 
 * **Validates: Requirements 15.2**
 */
class VoiceAssistantChannelAdapter : BaseChannelAdapter() {
    
    /**
     * Voice prompt templates per program.
     * Key: programId, Value: template configuration
     */
    private val voiceTemplates = mutableMapOf<String, VoicePromptTemplate>()
    
    /**
     * Voice assistant user registry - maps customer IDs to voice assistant user IDs.
     * In production, this would be backed by a database or service.
     */
    private val voiceUserIds = mutableMapOf<String, String>()
    
    override fun getChannelId(): String = "voice"
    
    override fun getChannelType(): ChannelType = ChannelType.VOICE
    
    override fun configure(config: Map<String, Any>) {
        super.configure(config)
        
        // Extract voice assistant specific configuration
        @Suppress("UNCHECKED_CAST")
        val templates = config["templates"] as? Map<String, Map<String, Any>>
        templates?.forEach { (programId, templateConfig) ->
            val template = VoicePromptTemplate(
                templateId = templateConfig["templateId"] as? String ?: "default-voice",
                prompt = templateConfig["prompt"] as? String ?: "We'd love to hear your feedback",
                reprompt = templateConfig["reprompt"] as? String ?: "Would you like to share your experience?",
                confirmationPrompt = templateConfig["confirmationPrompt"] as? String ?: "Thank you for your feedback",
                ssmlEnabled = templateConfig["ssmlEnabled"] as? Boolean ?: false,
                cardTitle = templateConfig["cardTitle"] as? String,
                cardContent = templateConfig["cardContent"] as? String
            )
            voiceTemplates[programId] = template
            logger.info { "Configured voice prompt template for program $programId: ${template.templateId}" }
        }
        
        // Extract voice user ID mappings
        @Suppress("UNCHECKED_CAST")
        val userIds = config["voiceUserIds"] as? Map<String, String>
        userIds?.forEach { (customerId, voiceUserId) ->
            voiceUserIds[customerId] = voiceUserId
        }
        logger.info { "Loaded voice user IDs for ${voiceUserIds.size} customers" }
    }
    
    override fun doDeliver(
        candidates: List<Candidate>,
        context: DeliveryContext,
        startTime: Long
    ): DeliveryResult {
        logger.info {
            "Starting voice assistant delivery for program ${context.programId}, " +
            "marketplace ${context.marketplace}, " +
            "candidateCount=${candidates.size}"
        }
        
        // Get template for this program
        val template = voiceTemplates[context.programId]
        if (template == null) {
            logger.error { "No voice prompt template configured for program ${context.programId}" }
            val failed = candidates.map { candidate ->
                FailedDelivery(
                    candidate = candidate,
                    errorCode = "NO_TEMPLATE",
                    errorMessage = "No voice prompt template configured for program ${context.programId}",
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
        
        // Format voice prompts
        val delivered = mutableListOf<DeliveredCandidate>()
        val failed = mutableListOf<FailedDelivery>()
        
        candidates.forEach { candidate ->
            try {
                // Check if customer has voice assistant linked
                val voiceUserId = voiceUserIds[candidate.customerId]
                if (voiceUserId == null) {
                    failed.add(
                        FailedDelivery(
                            candidate = candidate,
                            errorCode = "NO_VOICE_USER_ID",
                            errorMessage = "No voice assistant user ID found for customer ${candidate.customerId}",
                            timestamp = System.currentTimeMillis(),
                            retryable = false
                        )
                    )
                    return@forEach
                }
                
                val deliveryId = formatVoicePrompt(candidate, template, voiceUserId, context)
                
                delivered.add(
                    DeliveredCandidate(
                        candidate = candidate,
                        deliveryId = deliveryId,
                        timestamp = System.currentTimeMillis(),
                        channelMetadata = mapOf(
                            "templateId" to template.templateId,
                            "channel" to "voice",
                            "voiceUserId" to voiceUserId,
                            "ssmlEnabled" to template.ssmlEnabled
                        )
                    )
                )
                
                logger.debug {
                    "Voice prompt formatted: deliveryId=$deliveryId, " +
                    "customerId=${candidate.customerId}, " +
                    "voiceUserId=$voiceUserId"
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to format voice prompt for customer ${candidate.customerId}" }
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
            "Voice assistant delivery completed: deliveredCount=${delivered.size}, " +
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
     * Formats a candidate as a voice prompt.
     * 
     * In a real implementation, this would:
     * 1. Render the voice template with candidate data
     * 2. Generate SSML markup if enabled
     * 3. Create voice skill response with cards
     * 4. Store the prompt for voice assistant retrieval
     * 5. Handle multi-turn conversations
     * 
     * @param candidate Candidate to format
     * @param template Voice template to use
     * @param voiceUserId Voice assistant user ID
     * @param context Delivery context
     * @return Delivery ID
     */
    private fun formatVoicePrompt(
        candidate: Candidate,
        template: VoicePromptTemplate,
        voiceUserId: String,
        context: DeliveryContext
    ): String {
        val deliveryId = "voice-${System.nanoTime()}"
        
        // Build voice prompt data structure
        val promptData = mapOf(
            "deliveryId" to deliveryId,
            "customerId" to candidate.customerId,
            "voiceUserId" to voiceUserId,
            "programId" to context.programId,
            "marketplace" to context.marketplace,
            "templateId" to template.templateId,
            "prompt" to template.prompt,
            "reprompt" to template.reprompt,
            "confirmationPrompt" to template.confirmationPrompt,
            "ssmlEnabled" to template.ssmlEnabled,
            "card" to if (template.cardTitle != null) {
                mapOf(
                    "title" to template.cardTitle,
                    "content" to template.cardContent
                )
            } else null,
            "subject" to mapOf(
                "type" to candidate.subject.type,
                "id" to candidate.subject.id,
                "metadata" to candidate.subject.metadata
            ),
            "eventDate" to candidate.attributes.eventDate.toString(),
            "createdAt" to System.currentTimeMillis()
        )
        
        logger.debug {
            "Formatted voice prompt: deliveryId=$deliveryId, " +
            "customerId=${candidate.customerId}, " +
            "voiceUserId=$voiceUserId, " +
            "template=${template.templateId}, " +
            "promptData=$promptData"
        }
        
        // In production, this would:
        // 1. Store the prompt in a cache or database
        // 2. Register with voice assistant service for proactive notifications
        // 3. Handle skill invocation and response generation
        
        return deliveryId
    }
    
    /**
     * Links a voice assistant user ID to a customer.
     * 
     * @param customerId Customer identifier
     * @param voiceUserId Voice assistant user ID
     */
    fun linkVoiceUser(customerId: String, voiceUserId: String) {
        voiceUserIds[customerId] = voiceUserId
        logger.info { "Linked voice user $voiceUserId to customer $customerId" }
    }
    
    /**
     * Unlinks a voice assistant user ID from a customer.
     * 
     * @param customerId Customer identifier
     */
    fun unlinkVoiceUser(customerId: String) {
        voiceUserIds.remove(customerId)
        logger.info { "Unlinked voice user for customer $customerId" }
    }
}

/**
 * Voice prompt template configuration.
 * 
 * @property templateId Template identifier
 * @property prompt Main voice prompt text
 * @property reprompt Reprompt text if user doesn't respond
 * @property confirmationPrompt Confirmation message after user provides feedback
 * @property ssmlEnabled Whether to use SSML (Speech Synthesis Markup Language)
 * @property cardTitle Optional card title for visual display
 * @property cardContent Optional card content for visual display
 */
data class VoicePromptTemplate(
    val templateId: String,
    val prompt: String,
    val reprompt: String,
    val confirmationPrompt: String,
    val ssmlEnabled: Boolean = false,
    val cardTitle: String? = null,
    val cardContent: String? = null
)
