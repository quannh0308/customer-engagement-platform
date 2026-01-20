package com.ceap.connectors

import mu.KotlinLogging
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Utility class for mapping fields from source data to target model fields.
 *
 * Supports:
 * - Direct field mapping (source field -> target field)
 * - Nested field extraction (e.g., "customer.id" -> "customerId")
 * - Type conversion (String to Instant, String to Boolean, etc.)
 * - Default values for missing fields
 */
class FieldMapper(
    private val mappings: Map<String, FieldMapping>
) {
    private val logger = KotlinLogging.logger {}
    
    /**
     * Maps a raw data record to a target map using configured field mappings.
     *
     * @param rawData Source data record
     * @return Mapped data as a map
     */
    fun mapFields(rawData: Map<String, Any>): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        
        for ((targetField, mapping) in mappings) {
            try {
                val value = extractValue(rawData, mapping)
                result[targetField] = value
            } catch (e: Exception) {
                logger.warn(e) { "Failed to map field '$targetField': ${e.message}" }
                if (mapping.required) {
                    throw FieldMappingException("Required field '$targetField' mapping failed", e)
                }
                result[targetField] = mapping.defaultValue
            }
        }
        
        return result
    }
    
    /**
     * Extracts a value from raw data using the field mapping configuration.
     */
    private fun extractValue(rawData: Map<String, Any>, mapping: FieldMapping): Any? {
        // Extract value from source field (supports nested paths)
        val sourceValue = extractNestedValue(rawData, mapping.sourceField)
        
        // If value is null and field is not required, return default
        if (sourceValue == null) {
            return if (mapping.required) {
                throw FieldMappingException("Required source field '${mapping.sourceField}' is missing")
            } else {
                mapping.defaultValue
            }
        }
        
        // Apply type conversion if specified
        return if (mapping.targetType != null) {
            convertType(sourceValue, mapping.targetType)
        } else {
            sourceValue
        }
    }
    
    /**
     * Extracts a value from a nested path (e.g., "customer.id").
     */
    private fun extractNestedValue(data: Map<String, Any>, path: String): Any? {
        val parts = path.split(".")
        var current: Any? = data
        
        for (part in parts) {
            current = when (current) {
                is Map<*, *> -> current[part]
                else -> return null
            }
            if (current == null) return null
        }
        
        return current
    }
    
    /**
     * Converts a value to the target type.
     */
    private fun convertType(value: Any, targetType: FieldType): Any? {
        return when (targetType) {
            FieldType.STRING -> value.toString()
            FieldType.INTEGER -> when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: throw FieldMappingException("Cannot convert '$value' to Integer")
                else -> throw FieldMappingException("Cannot convert ${value::class.simpleName} to Integer")
            }
            FieldType.LONG -> when (value) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull() ?: throw FieldMappingException("Cannot convert '$value' to Long")
                else -> throw FieldMappingException("Cannot convert ${value::class.simpleName} to Long")
            }
            FieldType.DOUBLE -> when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull() ?: throw FieldMappingException("Cannot convert '$value' to Double")
                else -> throw FieldMappingException("Cannot convert ${value::class.simpleName} to Double")
            }
            FieldType.BOOLEAN -> when (value) {
                is Boolean -> value
                is String -> value.toBoolean()
                is Number -> value.toInt() != 0
                else -> throw FieldMappingException("Cannot convert ${value::class.simpleName} to Boolean")
            }
            FieldType.INSTANT -> when (value) {
                is Instant -> value
                is String -> parseInstant(value)
                is Number -> Instant.ofEpochMilli(value.toLong())
                else -> throw FieldMappingException("Cannot convert ${value::class.simpleName} to Instant")
            }
            FieldType.LIST -> when (value) {
                is List<*> -> value
                is String -> value.split(",").map { it.trim() }
                else -> listOf(value)
            }
            FieldType.MAP -> when (value) {
                is Map<*, *> -> value
                else -> throw FieldMappingException("Cannot convert ${value::class.simpleName} to Map")
            }
        }
    }
    
    /**
     * Parses a string to an Instant, trying multiple date formats.
     */
    private fun parseInstant(value: String): Instant {
        val formats = listOf(
            DateTimeFormatter.ISO_INSTANT,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME
        )
        
        for (format in formats) {
            try {
                return Instant.from(format.parse(value))
            } catch (e: DateTimeParseException) {
                // Try next format
            }
        }
        
        // Try parsing as epoch milliseconds
        try {
            return Instant.ofEpochMilli(value.toLong())
        } catch (e: NumberFormatException) {
            throw FieldMappingException("Cannot parse '$value' as Instant")
        }
    }
    
    companion object {
        /**
         * Creates a FieldMapper from a configuration map.
         *
         * Configuration format:
         * ```
         * {
         *   "targetField": {
         *     "sourceField": "source.field.path",
         *     "type": "STRING",
         *     "required": true,
         *     "defaultValue": "default"
         *   }
         * }
         * ```
         */
        fun fromConfig(config: Map<String, Any>): FieldMapper {
            val mappings = config.mapValues { (targetField, mappingConfig) ->
                when (mappingConfig) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        val cfg = mappingConfig as Map<String, Any>
                        FieldMapping(
                            sourceField = cfg["sourceField"] as? String 
                                ?: throw IllegalArgumentException("Missing 'sourceField' for target '$targetField'"),
                            targetType = (cfg["type"] as? String)?.let { FieldType.valueOf(it) },
                            required = cfg["required"] as? Boolean ?: false,
                            defaultValue = cfg["defaultValue"]
                        )
                    }
                    is String -> {
                        // Simple mapping: targetField -> sourceField
                        FieldMapping(sourceField = mappingConfig)
                    }
                    else -> throw IllegalArgumentException("Invalid mapping config for target '$targetField'")
                }
            }
            return FieldMapper(mappings)
        }
    }
}

/**
 * Configuration for mapping a single field.
 *
 * @property sourceField Path to the source field (supports nested paths like "customer.id")
 * @property targetType Target type for conversion (optional)
 * @property required Whether this field is required
 * @property defaultValue Default value if source field is missing
 */
data class FieldMapping(
    val sourceField: String,
    val targetType: FieldType? = null,
    val required: Boolean = false,
    val defaultValue: Any? = null
)

/**
 * Supported field types for conversion.
 */
enum class FieldType {
    STRING,
    INTEGER,
    LONG,
    DOUBLE,
    BOOLEAN,
    INSTANT,
    LIST,
    MAP
}

/**
 * Exception thrown when field mapping fails.
 */
class FieldMappingException(message: String, cause: Throwable? = null) : Exception(message, cause)
