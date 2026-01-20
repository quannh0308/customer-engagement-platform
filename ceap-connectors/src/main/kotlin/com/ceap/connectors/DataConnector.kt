package com.ceap.connectors

import com.ceap.model.Candidate
import com.ceap.model.config.DataConnectorConfig

/**
 * Interface for data connectors that extract data from various sources
 * and transform it into unified candidate models.
 *
 * Data connectors are responsible for:
 * - Validating their configuration
 * - Extracting raw data from source systems
 * - Transforming source data into the unified Candidate model
 * - Handling errors and logging appropriately
 */
interface DataConnector {
    
    /**
     * Returns the unique name/identifier of this connector.
     *
     * @return Connector name (e.g., "data-warehouse", "kinesis-stream")
     */
    fun getName(): String
    
    /**
     * Validates the connector configuration.
     *
     * @param config Configuration to validate
     * @return ValidationResult indicating success or failure with error messages
     */
    fun validateConfig(config: DataConnectorConfig): ValidationResult
    
    /**
     * Extracts raw data from the source system.
     *
     * @param config Connector configuration
     * @param parameters Optional parameters for the extraction (e.g., date range, filters)
     * @return List of raw data records as maps
     * @throws DataExtractionException if extraction fails
     */
    fun extractData(
        config: DataConnectorConfig,
        parameters: Map<String, Any> = emptyMap()
    ): List<Map<String, Any>>
    
    /**
     * Transforms a raw data record into a Candidate model.
     *
     * @param rawData Raw data record from the source
     * @param config Connector configuration (may contain field mappings)
     * @return Candidate model or null if transformation fails
     * @throws TransformationException if transformation fails critically
     */
    fun transformToCandidate(
        rawData: Map<String, Any>,
        config: DataConnectorConfig
    ): Candidate?
}

/**
 * Result of configuration validation.
 *
 * @property isValid Whether the configuration is valid
 * @property errors List of validation error messages (empty if valid)
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
) {
    companion object {
        fun success() = ValidationResult(isValid = true)
        fun failure(vararg errors: String) = ValidationResult(isValid = false, errors = errors.toList())
        fun failure(errors: List<String>) = ValidationResult(isValid = false, errors = errors)
    }
}

/**
 * Exception thrown when data extraction fails.
 */
class DataExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when data transformation fails.
 */
class TransformationException(message: String, cause: Throwable? = null) : Exception(message, cause)
