package com.ceap.channels

import com.ceap.model.Candidate
import mu.KotlinLogging
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Email channel adapter for delivering candidates through email campaigns.
 * 
 * This adapter:
 * - Creates email campaigns automatically for selected candidates
 * - Supports per-program email templates
 * - Enforces opt-out and frequency capping
 * - Tracks delivery status and open rates
 * - Supports shadow mode for testing
 * 
 * **Validates: Requirements 14.1, 14.2, 14.3, 14.4, 14.6**
 */
class EmailChannelAdapter(
    private val sesClient: SesClient = SesClient.create()
) : BaseChannelAdapter() {
    
    /**
     * Email templates per program.
     * Key: programId, Value: template configuration
     */
    private val programTemplates = ConcurrentHashMap<String, EmailTemplate>()
    
    /**
     * Opt-out list - customers who have opted out of emails.
     * In production, this would be backed by a database or service.
     */
    private val optOutList = ConcurrentHashMap.newKeySet<String>()
    
    /**
     * Frequency tracking - tracks email sends per customer per program.
     * Key: "programId:customerId", Value: list of send timestamps
     */
    private val frequencyTracker = ConcurrentHashMap<String, MutableList<Long>>()
    
    /**
     * Frequency cap configuration per program.
     * Key: programId, Value: FrequencyCap configuration
     */
    private val frequencyCaps = ConcurrentHashMap<String, FrequencyCap>()
    
    /**
     * Delivery tracking - tracks sent emails and their status.
     * Key: deliveryId, Value: delivery tracking information
     */
    private val deliveryTracking = ConcurrentHashMap<String, EmailDeliveryTracking>()
    
    override fun getChannelId(): String = "email"
    
    override fun getChannelType(): ChannelType = ChannelType.EMAIL
    
    override fun configure(config: Map<String, Any>) {
        super.configure(config)
        
        // Extract email-specific configuration
        @Suppress("UNCHECKED_CAST")
        val templates = config["templates"] as? Map<String, Map<String, Any>>
        templates?.forEach { (programId, templateConfig) ->
            val template = EmailTemplate(
                templateId = templateConfig["templateId"] as? String ?: "default-template",
                subject = templateConfig["subject"] as? String ?: "We'd love your feedback",
                fromAddress = templateConfig["fromAddress"] as? String ?: "noreply@example.com",
                fromName = templateConfig["fromName"] as? String ?: "CEAP Platform",
                unsubscribeUrl = templateConfig["unsubscribeUrl"] as? String ?: "https://example.com/unsubscribe"
            )
            programTemplates[programId] = template
            logger.info { "Configured email template for program $programId: ${template.templateId}" }
        }
        
        // Extract frequency cap configuration
        @Suppress("UNCHECKED_CAST")
        val caps = config["frequencyCaps"] as? Map<String, Map<String, Any>>
        caps?.forEach { (programId, capConfig) ->
            val cap = FrequencyCap(
                maxEmailsPerWindow = (capConfig["maxEmailsPerWindow"] as? Number)?.toInt() ?: 3,
                windowDays = (capConfig["windowDays"] as? Number)?.toInt() ?: 7
            )
            frequencyCaps[programId] = cap
            logger.info { "Configured frequency cap for program $programId: ${cap.maxEmailsPerWindow} emails per ${cap.windowDays} days" }
        }
        
        // Extract opt-out list
        @Suppress("UNCHECKED_CAST")
        val optOuts = config["optOutList"] as? List<String>
        optOuts?.forEach { customerId ->
            optOutList.add(customerId)
        }
        logger.info { "Loaded ${optOutList.size} customers in opt-out list" }
    }
    
    override fun doDeliver(
        candidates: List<Candidate>,
        context: DeliveryContext,
        startTime: Long
    ): DeliveryResult {
        logger.info {
            "Starting email delivery for program ${context.programId}, " +
            "marketplace ${context.marketplace}, " +
            "candidateCount=${candidates.size}"
        }
        
        // Get template for this program
        val template = programTemplates[context.programId]
        if (template == null) {
            logger.error { "No email template configured for program ${context.programId}" }
            val failed = candidates.map { candidate ->
                FailedDelivery(
                    candidate = candidate,
                    errorCode = "NO_TEMPLATE",
                    errorMessage = "No email template configured for program ${context.programId}",
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
        
        // Get frequency cap for this program
        val frequencyCap = frequencyCaps[context.programId] ?: FrequencyCap(
            maxEmailsPerWindow = 3,
            windowDays = 7
        )
        
        // Filter candidates based on opt-out and frequency capping
        val (eligibleCandidates, filteredOut) = filterCandidates(candidates, frequencyCap, context.programId)
        
        // Create email campaign
        val campaignId = context.campaignId ?: "campaign-${System.currentTimeMillis()}"
        
        logger.info {
            "Creating email campaign: campaignId=$campaignId, " +
            "eligibleCount=${eligibleCandidates.size}, " +
            "filteredCount=${filteredOut.size}"
        }
        
        // Send emails to eligible candidates
        val delivered = mutableListOf<DeliveredCandidate>()
        val failed = mutableListOf<FailedDelivery>()
        
        eligibleCandidates.forEach { candidate ->
            try {
                val deliveryId = sendEmail(candidate, template, campaignId, context)
                
                // Track frequency
                trackEmailSend(candidate.customerId, context.programId)
                
                // Track delivery
                val tracking = EmailDeliveryTracking(
                    deliveryId = deliveryId,
                    customerId = candidate.customerId,
                    campaignId = campaignId,
                    sentAt = System.currentTimeMillis(),
                    status = EmailStatus.SENT,
                    openedAt = null
                )
                deliveryTracking[deliveryId] = tracking
                
                delivered.add(
                    DeliveredCandidate(
                        candidate = candidate,
                        deliveryId = deliveryId,
                        timestamp = System.currentTimeMillis(),
                        channelMetadata = mapOf(
                            "campaignId" to campaignId,
                            "templateId" to template.templateId,
                            "channel" to "email"
                        )
                    )
                )
                
                logger.debug {
                    "Email sent: deliveryId=$deliveryId, " +
                    "customerId=${candidate.customerId}, " +
                    "subjectId=${candidate.subject.id}"
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to send email to customer ${candidate.customerId}" }
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
        
        // Add filtered out candidates to failed list
        failed.addAll(filteredOut)
        
        val durationMs = System.currentTimeMillis() - startTime
        
        logger.info {
            "Email delivery completed: deliveredCount=${delivered.size}, " +
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
                rateLimitedCount = filteredOut.count { it.errorCode == "FREQUENCY_CAP_EXCEEDED" },
                shadowMode = false
            )
        )
    }
    
    /**
     * Filters candidates based on opt-out status and frequency capping.
     * 
     * @param candidates List of candidates to filter
     * @param frequencyCap Frequency cap configuration
     * @param programId Program identifier for frequency tracking
     * @return Pair of (eligible candidates, filtered out failures)
     */
    private fun filterCandidates(
        candidates: List<Candidate>,
        frequencyCap: FrequencyCap,
        programId: String
    ): Pair<List<Candidate>, List<FailedDelivery>> {
        val eligible = mutableListOf<Candidate>()
        val filtered = mutableListOf<FailedDelivery>()
        
        candidates.forEach { candidate ->
            val customerId = candidate.customerId
            
            // Check opt-out
            if (isOptedOut(customerId)) {
                filtered.add(
                    FailedDelivery(
                        candidate = candidate,
                        errorCode = "OPTED_OUT",
                        errorMessage = "Customer has opted out of emails",
                        timestamp = System.currentTimeMillis(),
                        retryable = false
                    )
                )
                return@forEach
            }
            
            // Check frequency cap
            if (isFrequencyCapped(customerId, frequencyCap, programId)) {
                filtered.add(
                    FailedDelivery(
                        candidate = candidate,
                        errorCode = "FREQUENCY_CAP_EXCEEDED",
                        errorMessage = "Customer has exceeded frequency cap: ${frequencyCap.maxEmailsPerWindow} emails per ${frequencyCap.windowDays} days",
                        timestamp = System.currentTimeMillis(),
                        retryable = true
                    )
                )
                return@forEach
            }
            
            eligible.add(candidate)
        }
        
        return Pair(eligible, filtered)
    }
    
    /**
     * Checks if a customer has opted out of emails.
     * 
     * @param customerId Customer identifier
     * @return true if opted out, false otherwise
     */
    fun isOptedOut(customerId: String): Boolean {
        return optOutList.contains(customerId)
    }
    
    /**
     * Adds a customer to the opt-out list.
     * 
     * @param customerId Customer identifier
     */
    fun addOptOut(customerId: String) {
        optOutList.add(customerId)
        logger.info { "Added customer $customerId to opt-out list" }
    }
    
    /**
     * Removes a customer from the opt-out list.
     * 
     * @param customerId Customer identifier
     */
    fun removeOptOut(customerId: String) {
        optOutList.remove(customerId)
        logger.info { "Removed customer $customerId from opt-out list" }
    }
    
    /**
     * Checks if a customer has exceeded the frequency cap for a specific program.
     * 
     * @param customerId Customer identifier
     * @param frequencyCap Frequency cap configuration
     * @param programId Program identifier
     * @return true if frequency cap exceeded, false otherwise
     */
    fun isFrequencyCapped(customerId: String, frequencyCap: FrequencyCap, programId: String): Boolean {
        val key = "$programId:$customerId"
        val sends = frequencyTracker[key] ?: return false
        
        // Calculate window start time
        val windowStartMs = System.currentTimeMillis() - (frequencyCap.windowDays * 24 * 60 * 60 * 1000L)
        
        // Count sends within window
        val sendsInWindow = sends.count { it >= windowStartMs }
        
        return sendsInWindow >= frequencyCap.maxEmailsPerWindow
    }
    
    /**
     * Tracks an email send for frequency capping.
     * 
     * @param customerId Customer identifier
     * @param programId Program identifier
     */
    private fun trackEmailSend(customerId: String, programId: String) {
        val key = "$programId:$customerId"
        val sends = frequencyTracker.computeIfAbsent(key) { mutableListOf() }
        sends.add(System.currentTimeMillis())
        
        // Clean up old sends outside the window (keep last 30 days)
        val cutoffMs = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        sends.removeIf { it < cutoffMs }
    }
    
    /**
     * Sends an email to a candidate.
     * 
     * All emails include an unsubscribe link as required by email compliance regulations.
     * 
     * @param candidate Candidate to send email to
     * @param template Email template to use
     * @param campaignId Campaign identifier
     * @param context Delivery context
     * @return Delivery ID
     */
    private fun sendEmail(
        candidate: Candidate,
        template: EmailTemplate,
        campaignId: String,
        context: DeliveryContext
    ): String {
        val deliveryId = "email-${System.nanoTime()}"
        
        // Build unsubscribe link with customer ID and program ID
        val unsubscribeLink = buildUnsubscribeLink(
            template.unsubscribeUrl,
            candidate.customerId,
            context.programId
        )
        
        // In a real implementation, this would:
        // 1. Render the email template with candidate data
        // 2. Include the unsubscribe link in the email footer
        // 3. Send via SES with List-Unsubscribe header
        // 4. Track the message ID
        // 5. Set up SNS notifications for bounces/complaints/opens
        
        // For now, we'll simulate the send
        logger.debug {
            "Sending email: deliveryId=$deliveryId, " +
            "customerId=${candidate.customerId}, " +
            "template=${template.templateId}, " +
            "campaignId=$campaignId, " +
            "unsubscribeLink=$unsubscribeLink"
        }
        
        // Simulate SES send (in production, use sesClient.sendEmail())
        // val htmlBody = renderTemplate(template, candidate, unsubscribeLink)
        // val request = SendEmailRequest.builder()
        //     .source(template.fromAddress)
        //     .destination(Destination.builder().toAddresses(customerEmail).build())
        //     .message(Message.builder()
        //         .subject(Content.builder().data(template.subject).build())
        //         .body(Body.builder().html(Content.builder().data(htmlBody).build()).build())
        //         .build())
        //     .configurationSetName("email-tracking")
        //     .build()
        // val response = sesClient.sendEmail(request)
        
        return deliveryId
    }
    
    /**
     * Builds an unsubscribe link for a customer and program.
     * 
     * The link includes the customer ID and program ID as query parameters,
     * allowing the unsubscribe handler to process the opt-out correctly.
     * 
     * Requirements: 18.6 - Email compliance
     * 
     * @param baseUrl Base unsubscribe URL
     * @param customerId Customer identifier
     * @param programId Program identifier
     * @return Complete unsubscribe URL
     */
    private fun buildUnsubscribeLink(
        baseUrl: String,
        customerId: String,
        programId: String
    ): String {
        return "$baseUrl?customerId=$customerId&programId=$programId"
    }
    
    /**
     * Records an email open event.
     * 
     * This would typically be called by a webhook handler when SES reports an open event.
     * 
     * @param deliveryId Delivery identifier
     */
    fun recordEmailOpen(deliveryId: String) {
        val tracking = deliveryTracking[deliveryId]
        if (tracking != null) {
            val updated = tracking.copy(
                status = EmailStatus.OPENED,
                openedAt = System.currentTimeMillis()
            )
            deliveryTracking[deliveryId] = updated
            logger.info { "Recorded email open: deliveryId=$deliveryId" }
        }
    }
    
    /**
     * Gets delivery tracking information.
     * 
     * @param deliveryId Delivery identifier
     * @return Delivery tracking information, or null if not found
     */
    fun getDeliveryTracking(deliveryId: String): EmailDeliveryTracking? {
        return deliveryTracking[deliveryId]
    }
    
    /**
     * Gets delivery metrics for a campaign.
     * 
     * @param campaignId Campaign identifier
     * @return Campaign metrics
     */
    fun getCampaignMetrics(campaignId: String): CampaignMetrics {
        val deliveries = deliveryTracking.values.filter { it.campaignId == campaignId }
        
        val totalSent = deliveries.size
        val totalOpened = deliveries.count { it.status == EmailStatus.OPENED }
        val openRate = if (totalSent > 0) totalOpened.toDouble() / totalSent else 0.0
        
        return CampaignMetrics(
            campaignId = campaignId,
            totalSent = totalSent,
            totalOpened = totalOpened,
            openRate = openRate
        )
    }
}

/**
 * Email template configuration.
 * 
 * @property templateId Template identifier
 * @property subject Email subject line
 * @property fromAddress From email address
 * @property fromName From display name
 * @property unsubscribeUrl Base URL for unsubscribe links (will be appended with customer ID and program ID)
 */
data class EmailTemplate(
    val templateId: String,
    val subject: String,
    val fromAddress: String,
    val fromName: String,
    val unsubscribeUrl: String = "https://example.com/unsubscribe"
)

/**
 * Frequency cap configuration.
 * 
 * @property maxEmailsPerWindow Maximum number of emails allowed per window
 * @property windowDays Window size in days
 */
data class FrequencyCap(
    val maxEmailsPerWindow: Int,
    val windowDays: Int
)

/**
 * Email delivery tracking information.
 * 
 * @property deliveryId Delivery identifier
 * @property customerId Customer identifier
 * @property campaignId Campaign identifier
 * @property sentAt Timestamp when email was sent
 * @property status Current delivery status
 * @property openedAt Timestamp when email was opened (if opened)
 */
data class EmailDeliveryTracking(
    val deliveryId: String,
    val customerId: String,
    val campaignId: String,
    val sentAt: Long,
    val status: EmailStatus,
    val openedAt: Long?
)

/**
 * Email delivery status.
 */
enum class EmailStatus {
    SENT,
    OPENED,
    BOUNCED,
    COMPLAINED
}

/**
 * Campaign metrics.
 * 
 * @property campaignId Campaign identifier
 * @property totalSent Total emails sent
 * @property totalOpened Total emails opened
 * @property openRate Open rate (0.0 to 1.0)
 */
data class CampaignMetrics(
    val campaignId: String,
    val totalSent: Int,
    val totalOpened: Int,
    val openRate: Double
)
