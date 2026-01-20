package com.ceap.common.observability

import org.slf4j.MDC

/**
 * Manages structured logging context using SLF4J MDC.
 * 
 * This class provides a Kotlin-friendly API for managing logging context
 * with correlation IDs and other contextual information.
 * 
 * Requirements:
 * - Req 12.2: Structured logging with correlation IDs
 */
object StructuredLoggingContext {
    
    // MDC keys
    const val CORRELATION_ID = "correlationId"
    const val PROGRAM_ID = "programId"
    const val MARKETPLACE = "marketplace"
    const val CUSTOMER_ID = "customerId"
    const val BATCH_ID = "batchId"
    const val EVENT_TYPE = "eventType"
    const val COMPONENT = "component"
    const val WORKFLOW_EXECUTION_ID = "workflowExecutionId"
    const val FILTER_ID = "filterId"
    const val CHANNEL_ID = "channelId"
    
    /**
     * Sets the correlation ID in the MDC.
     */
    fun setCorrelationId(correlationId: String) {
        MDC.put(CORRELATION_ID, correlationId)
    }
    
    /**
     * Gets the current correlation ID from the MDC.
     */
    fun getCorrelationId(): String? {
        return MDC.get(CORRELATION_ID)
    }
    
    /**
     * Sets the program ID in the MDC.
     */
    fun setProgramId(programId: String) {
        MDC.put(PROGRAM_ID, programId)
    }
    
    /**
     * Sets the marketplace in the MDC.
     */
    fun setMarketplace(marketplace: String) {
        MDC.put(MARKETPLACE, marketplace)
    }
    
    /**
     * Sets the customer ID in the MDC (redacted).
     */
    fun setCustomerId(customerId: String) {
        MDC.put(CUSTOMER_ID, redactCustomerId(customerId))
    }
    
    /**
     * Sets the batch ID in the MDC.
     */
    fun setBatchId(batchId: String) {
        MDC.put(BATCH_ID, batchId)
    }
    
    /**
     * Sets the event type in the MDC.
     */
    fun setEventType(eventType: String) {
        MDC.put(EVENT_TYPE, eventType)
    }
    
    /**
     * Sets the component name in the MDC.
     */
    fun setComponent(component: String) {
        MDC.put(COMPONENT, component)
    }
    
    /**
     * Sets the workflow execution ID in the MDC.
     */
    fun setWorkflowExecutionId(executionId: String) {
        MDC.put(WORKFLOW_EXECUTION_ID, executionId)
    }
    
    /**
     * Sets the filter ID in the MDC.
     */
    fun setFilterId(filterId: String) {
        MDC.put(FILTER_ID, filterId)
    }
    
    /**
     * Sets the channel ID in the MDC.
     */
    fun setChannelId(channelId: String) {
        MDC.put(CHANNEL_ID, channelId)
    }
    
    /**
     * Clears all MDC values.
     */
    fun clearAll() {
        MDC.clear()
    }
    
    /**
     * Clears a specific MDC key.
     */
    fun clear(key: String) {
        MDC.remove(key)
    }
    
    /**
     * Executes a block with a correlation ID, then restores the previous value.
     */
    inline fun <T> withCorrelationId(correlationId: String, block: () -> T): T {
        val previousCorrelationId = getCorrelationId()
        return try {
            setCorrelationId(correlationId)
            block()
        } finally {
            if (previousCorrelationId != null) {
                setCorrelationId(previousCorrelationId)
            } else {
                clear(CORRELATION_ID)
            }
        }
    }
    
    /**
     * Executes a block with full logging context, then clears it.
     */
    inline fun <T> withContext(context: Map<String, String>, block: () -> T): T {
        return try {
            context.forEach { (key, value) ->
                MDC.put(key, value)
            }
            block()
        } finally {
            clearAll()
        }
    }
    
    /**
     * Redacts customer ID for PII protection.
     * Shows first 4 and last 4 characters, masks the middle.
     */
    private fun redactCustomerId(customerId: String): String {
        if (customerId.length <= 8) {
            return "****"
        }
        val prefix = customerId.substring(0, 4)
        val suffix = customerId.substring(customerId.length - 4)
        return "$prefix****$suffix"
    }
}
