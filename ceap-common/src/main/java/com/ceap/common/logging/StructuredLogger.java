package com.ceap.common.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Structured logger that provides consistent JSON-formatted logging with automatic PII redaction.
 * 
 * This class wraps SLF4J Logger and adds structured data support for
 * better log analysis and monitoring. All log messages and structured data
 * are automatically scanned and redacted for PII (emails, phone numbers, addresses, etc.)
 * before being written to logs.
 * 
 * Requirements:
 * - Req 12.2: Structured logging with correlation IDs
 * - Req 18.4: PII redaction in logs
 */
public class StructuredLogger {
    
    private final Logger logger;
    
    private StructuredLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }
    
    private StructuredLogger(String name) {
        this.logger = LoggerFactory.getLogger(name);
    }
    
    /**
     * Creates a structured logger for the given class.
     * 
     * @param clazz The class to create a logger for
     * @return A new structured logger
     */
    public static StructuredLogger getLogger(Class<?> clazz) {
        return new StructuredLogger(clazz);
    }
    
    /**
     * Creates a structured logger with the given name.
     * 
     * @param name The logger name
     * @return A new structured logger
     */
    public static StructuredLogger getLogger(String name) {
        return new StructuredLogger(name);
    }
    
    /**
     * Logs an info message with structured data.
     * PII is automatically redacted from both message and data.
     * 
     * @param message The log message
     * @param data Additional structured data
     */
    public void info(String message, Map<String, Object> data) {
        if (logger.isInfoEnabled()) {
            String redactedMessage = PIIRedactor.redactAll(message);
            Map<String, Object> redactedData = redactData(data);
            logger.info(formatMessage(redactedMessage, redactedData));
        }
    }
    
    /**
     * Logs an info message.
     * PII is automatically redacted.
     * 
     * @param message The log message
     */
    public void info(String message) {
        logger.info(PIIRedactor.redactAll(message));
    }
    
    /**
     * Logs a warning message with structured data.
     * PII is automatically redacted from both message and data.
     * 
     * @param message The log message
     * @param data Additional structured data
     */
    public void warn(String message, Map<String, Object> data) {
        if (logger.isWarnEnabled()) {
            String redactedMessage = PIIRedactor.redactAll(message);
            Map<String, Object> redactedData = redactData(data);
            logger.warn(formatMessage(redactedMessage, redactedData));
        }
    }
    
    /**
     * Logs a warning message.
     * PII is automatically redacted.
     * 
     * @param message The log message
     */
    public void warn(String message) {
        logger.warn(PIIRedactor.redactAll(message));
    }
    
    /**
     * Logs a warning message with exception.
     * PII is automatically redacted from message.
     * 
     * @param message The log message
     * @param throwable The exception
     */
    public void warn(String message, Throwable throwable) {
        logger.warn(PIIRedactor.redactAll(message), throwable);
    }
    
    /**
     * Logs an error message with structured data.
     * PII is automatically redacted from both message and data.
     * 
     * @param message The log message
     * @param data Additional structured data
     */
    public void error(String message, Map<String, Object> data) {
        if (logger.isErrorEnabled()) {
            String redactedMessage = PIIRedactor.redactAll(message);
            Map<String, Object> redactedData = redactData(data);
            logger.error(formatMessage(redactedMessage, redactedData));
        }
    }
    
    /**
     * Logs an error message.
     * PII is automatically redacted.
     * 
     * @param message The log message
     */
    public void error(String message) {
        logger.error(PIIRedactor.redactAll(message));
    }
    
    /**
     * Logs an error message with exception.
     * PII is automatically redacted from message.
     * 
     * @param message The log message
     * @param throwable The exception
     */
    public void error(String message, Throwable throwable) {
        logger.error(PIIRedactor.redactAll(message), throwable);
    }
    
    /**
     * Logs an error message with exception and structured data.
     * PII is automatically redacted from both message and data.
     * 
     * @param message The log message
     * @param throwable The exception
     * @param data Additional structured data
     */
    public void error(String message, Throwable throwable, Map<String, Object> data) {
        if (logger.isErrorEnabled()) {
            String redactedMessage = PIIRedactor.redactAll(message);
            Map<String, Object> redactedData = redactData(data);
            logger.error(formatMessage(redactedMessage, redactedData), throwable);
        }
    }
    
    /**
     * Logs a debug message with structured data.
     * PII is automatically redacted from both message and data.
     * 
     * @param message The log message
     * @param data Additional structured data
     */
    public void debug(String message, Map<String, Object> data) {
        if (logger.isDebugEnabled()) {
            String redactedMessage = PIIRedactor.redactAll(message);
            Map<String, Object> redactedData = redactData(data);
            logger.debug(formatMessage(redactedMessage, redactedData));
        }
    }
    
    /**
     * Logs a debug message.
     * PII is automatically redacted.
     * 
     * @param message The log message
     */
    public void debug(String message) {
        logger.debug(PIIRedactor.redactAll(message));
    }
    
    /**
     * Checks if info logging is enabled.
     * 
     * @return true if info logging is enabled
     */
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }
    
    /**
     * Checks if debug logging is enabled.
     * 
     * @return true if debug logging is enabled
     */
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }
    
    /**
     * Creates a builder for structured log data.
     * 
     * @return A new data builder
     */
    public static DataBuilder data() {
        return new DataBuilder();
    }
    
    /**
     * Formats a message with structured data.
     * 
     * @param message The base message
     * @param data The structured data
     * @return The formatted message
     */
    private String formatMessage(String message, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return message;
        }
        
        StringBuilder sb = new StringBuilder(message);
        sb.append(" | ");
        
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        
        return sb.toString();
    }
    
    /**
     * Redacts PII from structured data map.
     * 
     * @param data The data map to redact
     * @return A new map with redacted values
     */
    private Map<String, Object> redactData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        
        Map<String, Object> redacted = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey().toLowerCase();
            Object value = entry.getValue();
            
            // Redact known PII fields
            if (key.contains("email") || key.contains("phone") || 
                key.contains("address") || key.contains("ssn") ||
                key.contains("creditcard") || key.contains("card")) {
                
                if (value instanceof String) {
                    redacted.put(entry.getKey(), PIIRedactor.redactAll((String) value));
                } else {
                    redacted.put(entry.getKey(), "[REDACTED]");
                }
            } else if (key.contains("customerid") || key.contains("customer_id")) {
                if (value instanceof String) {
                    redacted.put(entry.getKey(), PIIRedactor.redactCustomerId((String) value));
                } else {
                    redacted.put(entry.getKey(), value);
                }
            } else if (key.contains("name") && !key.contains("filename") && !key.contains("username")) {
                if (value instanceof String) {
                    redacted.put(entry.getKey(), PIIRedactor.redactName((String) value));
                } else {
                    redacted.put(entry.getKey(), value);
                }
            } else {
                // For other fields, still scan for PII patterns in string values
                if (value instanceof String) {
                    redacted.put(entry.getKey(), PIIRedactor.redactAll((String) value));
                } else {
                    redacted.put(entry.getKey(), value);
                }
            }
        }
        
        return redacted;
    }
    
    /**
     * Builder for structured log data.
     */
    public static class DataBuilder {
        private final Map<String, Object> data = new HashMap<>();
        
        public DataBuilder add(String key, Object value) {
            if (value != null) {
                data.put(key, value);
            }
            return this;
        }
        
        public DataBuilder count(String key, int value) {
            data.put(key, value);
            return this;
        }
        
        public DataBuilder duration(String key, long milliseconds) {
            data.put(key + "Ms", milliseconds);
            return this;
        }
        
        public DataBuilder success(boolean success) {
            data.put("success", success);
            return this;
        }
        
        public Map<String, Object> build() {
            return new HashMap<>(data);
        }
    }
}
