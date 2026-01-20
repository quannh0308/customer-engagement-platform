package com.ceap.common.logging;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Utility class for managing logging context and correlation IDs.
 * 
 * This class provides methods to:
 * - Generate and manage correlation IDs for request tracing
 * - Set and clear MDC (Mapped Diagnostic Context) values
 * - Execute code blocks with specific logging context
 * 
 * Requirements:
 * - Req 12.2: Structured logging with correlation IDs
 */
public class LoggingUtil {
    
    // MDC keys
    public static final String CORRELATION_ID = "correlationId";
    public static final String PROGRAM_ID = "programId";
    public static final String MARKETPLACE = "marketplace";
    public static final String CUSTOMER_ID = "customerId";
    public static final String BATCH_ID = "batchId";
    public static final String EVENT_TYPE = "eventType";
    public static final String COMPONENT = "component";
    
    private LoggingUtil() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Generates a new correlation ID (UUID).
     * 
     * @return A new correlation ID
     */
    public static String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Sets the correlation ID in the MDC.
     * 
     * @param correlationId The correlation ID to set
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isEmpty()) {
            MDC.put(CORRELATION_ID, correlationId);
        }
    }
    
    /**
     * Gets the current correlation ID from the MDC.
     * 
     * @return The current correlation ID, or null if not set
     */
    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID);
    }
    
    /**
     * Sets the program ID in the MDC.
     * 
     * @param programId The program ID to set
     */
    public static void setProgramId(String programId) {
        if (programId != null && !programId.isEmpty()) {
            MDC.put(PROGRAM_ID, programId);
        }
    }
    
    /**
     * Sets the marketplace in the MDC.
     * 
     * @param marketplace The marketplace to set
     */
    public static void setMarketplace(String marketplace) {
        if (marketplace != null && !marketplace.isEmpty()) {
            MDC.put(MARKETPLACE, marketplace);
        }
    }
    
    /**
     * Sets the customer ID in the MDC (will be redacted in logs).
     * 
     * @param customerId The customer ID to set
     */
    public static void setCustomerId(String customerId) {
        if (customerId != null && !customerId.isEmpty()) {
            MDC.put(CUSTOMER_ID, PIIRedactor.redactCustomerId(customerId));
        }
    }
    
    /**
     * Sets the batch ID in the MDC.
     * 
     * @param batchId The batch ID to set
     */
    public static void setBatchId(String batchId) {
        if (batchId != null && !batchId.isEmpty()) {
            MDC.put(BATCH_ID, batchId);
        }
    }
    
    /**
     * Sets the event type in the MDC.
     * 
     * @param eventType The event type to set
     */
    public static void setEventType(String eventType) {
        if (eventType != null && !eventType.isEmpty()) {
            MDC.put(EVENT_TYPE, eventType);
        }
    }
    
    /**
     * Sets the component name in the MDC.
     * 
     * @param component The component name to set
     */
    public static void setComponent(String component) {
        if (component != null && !component.isEmpty()) {
            MDC.put(COMPONENT, component);
        }
    }
    
    /**
     * Clears all MDC values.
     */
    public static void clearAll() {
        MDC.clear();
    }
    
    /**
     * Clears a specific MDC key.
     * 
     * @param key The key to clear
     */
    public static void clear(String key) {
        MDC.remove(key);
    }
    
    /**
     * Executes a runnable with a specific correlation ID, then clears it.
     * 
     * @param correlationId The correlation ID to use
     * @param runnable The code to execute
     */
    public static void withCorrelationId(String correlationId, Runnable runnable) {
        String previousCorrelationId = getCorrelationId();
        try {
            setCorrelationId(correlationId);
            runnable.run();
        } finally {
            if (previousCorrelationId != null) {
                setCorrelationId(previousCorrelationId);
            } else {
                clear(CORRELATION_ID);
            }
        }
    }
    
    /**
     * Executes a supplier with a specific correlation ID, then clears it.
     * 
     * @param correlationId The correlation ID to use
     * @param supplier The code to execute
     * @param <T> The return type
     * @return The result of the supplier
     */
    public static <T> T withCorrelationId(String correlationId, Supplier<T> supplier) {
        String previousCorrelationId = getCorrelationId();
        try {
            setCorrelationId(correlationId);
            return supplier.get();
        } finally {
            if (previousCorrelationId != null) {
                setCorrelationId(previousCorrelationId);
            } else {
                clear(CORRELATION_ID);
            }
        }
    }
    
    /**
     * Executes a runnable with full logging context, then clears it.
     * 
     * @param context The logging context to use
     * @param runnable The code to execute
     */
    public static void withContext(LoggingContext context, Runnable runnable) {
        try {
            context.apply();
            runnable.run();
        } finally {
            clearAll();
        }
    }
    
    /**
     * Executes a supplier with full logging context, then clears it.
     * 
     * @param context The logging context to use
     * @param supplier The code to execute
     * @param <T> The return type
     * @return The result of the supplier
     */
    public static <T> T withContext(LoggingContext context, Supplier<T> supplier) {
        try {
            context.apply();
            return supplier.get();
        } finally {
            clearAll();
        }
    }
}
