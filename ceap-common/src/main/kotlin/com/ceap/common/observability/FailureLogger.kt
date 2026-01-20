package com.ceap.common.observability

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Utility for logging failures with structured error details.
 * 
 * This class provides consistent failure logging across the platform,
 * ensuring all errors include correlation IDs and detailed context.
 * 
 * Requirements:
 * - Req 12.2: Structured logging with correlation IDs
 */
class FailureLogger(private val logger: Logger) {
    
    companion object {
        fun getLogger(clazz: Class<*>): FailureLogger {
            return FailureLogger(LoggerFactory.getLogger(clazz))
        }
        
        inline fun <reified T> getLogger(): FailureLogger {
            return FailureLogger(LoggerFactory.getLogger(T::class.java))
        }
    }
    
    /**
     * Logs a failure with structured error details.
     * 
     * @param message The error message
     * @param throwable The exception that occurred
     * @param context Additional context about the failure
     */
    fun logFailure(
        message: String,
        throwable: Throwable,
        context: Map<String, Any> = emptyMap()
    ) {
        val correlationId = StructuredLoggingContext.getCorrelationId() ?: "unknown"
        
        val errorDetails = buildMap {
            put("correlationId", correlationId)
            put("errorType", throwable.javaClass.simpleName)
            put("errorMessage", throwable.message ?: "No message")
            putAll(context)
        }
        
        logger.error(formatMessage(message, errorDetails), throwable)
    }
    
    /**
     * Logs a failure without an exception.
     * 
     * @param message The error message
     * @param context Additional context about the failure
     */
    fun logFailure(
        message: String,
        context: Map<String, Any> = emptyMap()
    ) {
        val correlationId = StructuredLoggingContext.getCorrelationId() ?: "unknown"
        
        val errorDetails = buildMap {
            put("correlationId", correlationId)
            putAll(context)
        }
        
        logger.error(formatMessage(message, errorDetails))
    }
    
    /**
     * Logs a warning with structured details.
     * 
     * @param message The warning message
     * @param context Additional context
     */
    fun logWarning(
        message: String,
        context: Map<String, Any> = emptyMap()
    ) {
        val correlationId = StructuredLoggingContext.getCorrelationId() ?: "unknown"
        
        val details = buildMap {
            put("correlationId", correlationId)
            putAll(context)
        }
        
        logger.warn(formatMessage(message, details))
    }
    
    /**
     * Logs an info message with structured details.
     * 
     * @param message The info message
     * @param context Additional context
     */
    fun logInfo(
        message: String,
        context: Map<String, Any> = emptyMap()
    ) {
        val correlationId = StructuredLoggingContext.getCorrelationId() ?: "unknown"
        
        val details = buildMap {
            put("correlationId", correlationId)
            putAll(context)
        }
        
        logger.info(formatMessage(message, details))
    }
    
    /**
     * Formats a message with structured data.
     */
    private fun formatMessage(message: String, data: Map<String, Any>): String {
        if (data.isEmpty()) {
            return message
        }
        
        val contextString = data.entries.joinToString(", ") { (key, value) ->
            "$key=$value"
        }
        
        return "$message | $contextString"
    }
}
