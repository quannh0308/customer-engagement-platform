package com.ceap.common.observability

import java.util.UUID

/**
 * Generates correlation IDs for request tracing.
 * 
 * Correlation IDs are used to trace requests across multiple services
 * and workflow stages, enabling end-to-end observability.
 * 
 * Requirements:
 * - Req 12.2: Structured logging with correlation IDs
 */
object CorrelationIdGenerator {
    
    /**
     * Generates a new correlation ID using UUID v4.
     * 
     * @return A new correlation ID string
     */
    fun generate(): String {
        return UUID.randomUUID().toString()
    }
    
    /**
     * Generates a correlation ID with a prefix for easier identification.
     * 
     * @param prefix The prefix to add (e.g., "batch", "reactive", "api")
     * @return A new correlation ID string with prefix
     */
    fun generateWithPrefix(prefix: String): String {
        return "$prefix-${UUID.randomUUID()}"
    }
}
