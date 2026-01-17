package com.solicitation.connectors

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import mu.KotlinLogging
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Validates source data against JSON schemas and performs field-level validation.
 *
 * Supports:
 * - JSON Schema validation (draft 2020-12)
 * - Required field validation
 * - Date format validation
 * - Type validation
 * - Custom validation rules
 */
class SchemaValidator(
    private val schema: JsonSchema? = null,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {
    private val logger = KotlinLogging.logger {}
    
    /**
     * Validates data against the configured JSON schema and custom rules.
     *
     * @param data Data to validate
     * @return ValidationResult with detailed error messages
     */
    fun validate(data: Map<String, Any>): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate against JSON schema if provided
        if (schema != null) {
            val schemaErrors = validateAgainstSchema(data)
            errors.addAll(schemaErrors)
        }
        
        // Perform custom validations
        errors.addAll(validateRequiredFields(data))
        errors.addAll(validateDateFormats(data))
        errors.addAll(validateTypes(data))
        
        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            logger.warn { "Validation failed with ${errors.size} errors" }
            errors.forEach { error -> logger.debug { "  - $error" } }
            ValidationResult.failure(errors)
        }
    }
    
    /**
     * Validates data against the JSON schema.
     */
    private fun validateAgainstSchema(data: Map<String, Any>): List<String> {
        if (schema == null) return emptyList()
        
        try {
            val jsonNode: JsonNode = objectMapper.valueToTree(data)
            val validationMessages: Set<ValidationMessage> = schema.validate(jsonNode)
            
            return validationMessages.map { msg ->
                "${msg.path}: ${msg.message}"
            }
        } catch (e: Exception) {
            logger.error(e) { "Schema validation failed with exception" }
            return listOf("Schema validation error: ${e.message}")
        }
    }
    
    /**
     * Validates that required fields are present and non-null.
     */
    private fun validateRequiredFields(data: Map<String, Any>): List<String> {
        val errors = mutableListOf<String>()
        
        // Core required fields for candidate transformation
        val requiredFields = listOf(
            "customerId",
            "subjectType",
            "subjectId",
            "eventDate"
        )
        
        for (field in requiredFields) {
            if (!data.containsKey(field)) {
                errors.add("Required field '$field' is missing")
            } else if (data[field] == null) {
                errors.add("Required field '$field' is null")
            } else if (data[field] is String && (data[field] as String).isBlank()) {
                errors.add("Required field '$field' is blank")
            }
        }
        
        return errors
    }
    
    /**
     * Validates date format fields.
     */
    private fun validateDateFormats(data: Map<String, Any>): List<String> {
        val errors = mutableListOf<String>()
        
        // Fields that should be valid dates
        val dateFields = listOf("eventDate", "deliveryDate", "createdAt", "updatedAt", "expiresAt")
        
        for (field in dateFields) {
            val value = data[field]
            if (value != null) {
                if (!isValidDate(value)) {
                    errors.add("Field '$field' has invalid date format: $value")
                }
            }
        }
        
        return errors
    }
    
    /**
     * Validates field types.
     */
    private fun validateTypes(data: Map<String, Any>): List<String> {
        val errors = mutableListOf<String>()
        
        // Validate string fields
        val stringFields = listOf("customerId", "subjectType", "subjectId")
        for (field in stringFields) {
            val value = data[field]
            if (value != null && value !is String) {
                errors.add("Field '$field' must be a string, got ${value::class.simpleName}")
            }
        }
        
        // Validate numeric fields
        val numericFields = listOf("orderValue", "ttlDays")
        for (field in numericFields) {
            val value = data[field]
            if (value != null && value !is Number) {
                errors.add("Field '$field' must be numeric, got ${value::class.simpleName}")
            }
        }
        
        // Validate boolean fields
        val booleanFields = listOf("mediaEligible", "emailEligible", "smsEligible", "pushEligible")
        for (field in booleanFields) {
            val value = data[field]
            if (value != null && value !is Boolean) {
                errors.add("Field '$field' must be boolean, got ${value::class.simpleName}")
            }
        }
        
        return errors
    }
    
    /**
     * Checks if a value is a valid date.
     */
    private fun isValidDate(value: Any): Boolean {
        return when (value) {
            is Instant -> true
            is Number -> {
                // Check if it's a reasonable epoch timestamp
                val millis = value.toLong()
                millis > 0 && millis < Instant.now().plusSeconds(365L * 24 * 60 * 60 * 10).toEpochMilli()
            }
            is String -> {
                // Try parsing with various formats
                val formats = listOf(
                    DateTimeFormatter.ISO_INSTANT,
                    DateTimeFormatter.ISO_DATE_TIME,
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                    DateTimeFormatter.ISO_ZONED_DATE_TIME,
                    DateTimeFormatter.ISO_DATE
                )
                
                formats.any { format ->
                    try {
                        format.parse(value)
                        true
                    } catch (e: DateTimeParseException) {
                        false
                    }
                }
            }
            else -> false
        }
    }
    
    companion object {
        /**
         * Creates a SchemaValidator from a JSON schema string.
         *
         * @param schemaJson JSON schema as a string
         * @return SchemaValidator instance
         */
        fun fromSchemaString(schemaJson: String): SchemaValidator {
            val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
            val schema = factory.getSchema(schemaJson)
            return SchemaValidator(schema)
        }
        
        /**
         * Creates a SchemaValidator from a JSON schema file.
         *
         * @param schemaPath Path to JSON schema file
         * @return SchemaValidator instance
         */
        fun fromSchemaFile(schemaPath: String): SchemaValidator {
            val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
            val schemaStream = SchemaValidator::class.java.getResourceAsStream(schemaPath)
                ?: throw IllegalArgumentException("Schema file not found: $schemaPath")
            val schema = factory.getSchema(schemaStream)
            return SchemaValidator(schema)
        }
        
        /**
         * Creates a SchemaValidator without a JSON schema (custom validation only).
         *
         * @return SchemaValidator instance
         */
        fun withoutSchema(): SchemaValidator {
            return SchemaValidator(schema = null)
        }
    }
}

/**
 * Exception thrown when schema validation fails.
 */
class SchemaValidationException(message: String, cause: Throwable? = null) : Exception(message, cause)
