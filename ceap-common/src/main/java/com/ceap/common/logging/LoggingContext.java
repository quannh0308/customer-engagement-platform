package com.ceap.common.logging;

/**
 * Builder class for creating logging context with multiple MDC values.
 * 
 * Example usage:
 * <pre>
 * LoggingContext context = LoggingContext.builder()
 *     .correlationId("abc-123")
 *     .programId("retail")
 *     .marketplace("US")
 *     .build();
 * 
 * LoggingUtil.withContext(context, () -> {
 *     // All logs here will include the context
 *     logger.info("Processing batch");
 * });
 * </pre>
 */
public class LoggingContext {
    
    private final String correlationId;
    private final String programId;
    private final String marketplace;
    private final String customerId;
    private final String batchId;
    private final String eventType;
    private final String component;
    
    private LoggingContext(Builder builder) {
        this.correlationId = builder.correlationId;
        this.programId = builder.programId;
        this.marketplace = builder.marketplace;
        this.customerId = builder.customerId;
        this.batchId = builder.batchId;
        this.eventType = builder.eventType;
        this.component = builder.component;
    }
    
    /**
     * Applies this context to the MDC.
     */
    public void apply() {
        if (correlationId != null) {
            LoggingUtil.setCorrelationId(correlationId);
        }
        if (programId != null) {
            LoggingUtil.setProgramId(programId);
        }
        if (marketplace != null) {
            LoggingUtil.setMarketplace(marketplace);
        }
        if (customerId != null) {
            LoggingUtil.setCustomerId(customerId);
        }
        if (batchId != null) {
            LoggingUtil.setBatchId(batchId);
        }
        if (eventType != null) {
            LoggingUtil.setEventType(eventType);
        }
        if (component != null) {
            LoggingUtil.setComponent(component);
        }
    }
    
    /**
     * Creates a new builder.
     * 
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for LoggingContext.
     */
    public static class Builder {
        private String correlationId;
        private String programId;
        private String marketplace;
        private String customerId;
        private String batchId;
        private String eventType;
        private String component;
        
        private Builder() {
        }
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public Builder programId(String programId) {
            this.programId = programId;
            return this;
        }
        
        public Builder marketplace(String marketplace) {
            this.marketplace = marketplace;
            return this;
        }
        
        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }
        
        public Builder batchId(String batchId) {
            this.batchId = batchId;
            return this;
        }
        
        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }
        
        public Builder component(String component) {
            this.component = component;
            return this;
        }
        
        public LoggingContext build() {
            return new LoggingContext(this);
        }
    }
}
