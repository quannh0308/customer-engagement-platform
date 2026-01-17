package com.solicitation.connectors

import com.solicitation.model.Candidate
import com.solicitation.model.config.DataConnectorConfig
import mu.KotlinLogging

/**
 * Base abstract class for data connectors providing common validation logic
 * and utility methods.
 *
 * Subclasses should implement:
 * - getName(): Return the connector's unique identifier
 * - extractData(): Implement source-specific data extraction
 * - transformToCandidate(): Implement source-specific transformation logic
 */
abstract class BaseDataConnector : DataConnector {
    
    protected val logger = KotlinLogging.logger {}
    
    /**
     * Validates the connector configuration with common checks.
     * Subclasses can override to add additional validation.
     *
     * @param config Configuration to validate
     * @return ValidationResult indicating success or failure
     */
    override fun validateConfig(config: DataConnectorConfig): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate connector ID
        if (config.connectorId.isBlank()) {
            errors.add("Connector ID cannot be blank")
        }
        
        // Validate connector type
        if (config.connectorType.isBlank()) {
            errors.add("Connector type cannot be blank")
        }
        
        // Validate that connector type matches this connector
        if (config.connectorType != getName()) {
            errors.add("Connector type '${config.connectorType}' does not match expected type '${getName()}'")
        }
        
        // Validate source config exists if required
        if (requiresSourceConfig() && config.sourceConfig.isNullOrEmpty()) {
            errors.add("Source configuration is required for connector type '${getName()}'")
        }
        
        // Allow subclasses to add additional validation
        errors.addAll(validateSourceConfig(config))
        
        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }
    
    /**
     * Indicates whether this connector requires source configuration.
     * Override to return false if source config is optional.
     *
     * @return true if source config is required, false otherwise
     */
    protected open fun requiresSourceConfig(): Boolean = true
    
    /**
     * Validates source-specific configuration.
     * Subclasses should override to add connector-specific validation.
     *
     * @param config Configuration to validate
     * @return List of validation error messages (empty if valid)
     */
    protected open fun validateSourceConfig(config: DataConnectorConfig): List<String> {
        return emptyList()
    }
    
    /**
     * Safely extracts a required field from raw data.
     *
     * @param rawData Raw data map
     * @param fieldName Field name to extract
     * @return Field value or null if not present
     */
    protected fun getRequiredField(rawData: Map<String, Any>, fieldName: String): Any? {
        return rawData[fieldName]
    }
    
    /**
     * Safely extracts an optional field from raw data.
     *
     * @param rawData Raw data map
     * @param fieldName Field name to extract
     * @param defaultValue Default value if field is not present
     * @return Field value or default value
     */
    protected fun <T> getOptionalField(
        rawData: Map<String, Any>,
        fieldName: String,
        defaultValue: T
    ): T {
        @Suppress("UNCHECKED_CAST")
        return rawData[fieldName] as? T ?: defaultValue
    }
    
    /**
     * Logs a transformation error and returns null.
     *
     * @param rawData Raw data that failed to transform
     * @param error Error message
     * @param cause Optional exception cause
     * @return null
     */
    protected fun logTransformationError(
        rawData: Map<String, Any>,
        error: String,
        cause: Throwable? = null
    ): Candidate? {
        if (cause != null) {
            logger.error(cause) { "Transformation failed for data: $error" }
        } else {
            logger.error { "Transformation failed for data: $error" }
        }
        logger.debug { "Failed raw data: $rawData" }
        return null
    }
    
    /**
     * Validates that all required fields are present in raw data.
     *
     * @param rawData Raw data map
     * @param requiredFields List of required field names
     * @return List of missing field names (empty if all present)
     */
    protected fun validateRequiredFields(
        rawData: Map<String, Any>,
        requiredFields: List<String>
    ): List<String> {
        return requiredFields.filter { field ->
            !rawData.containsKey(field) || rawData[field] == null
        }
    }
}
