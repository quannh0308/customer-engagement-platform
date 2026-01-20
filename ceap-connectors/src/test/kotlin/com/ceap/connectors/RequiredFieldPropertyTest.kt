package com.ceap.connectors

import com.ceap.connectors.arbitraries.DataArbitraries
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat

/**
 * Property-based tests for required field validation.
 *
 * **Property 49: Required field validation**
 * **Validates: Requirements 16.1, 16.3**
 *
 * These tests verify that:
 * - Missing required fields are detected
 * - Validation errors are descriptive
 * - All required fields are checked
 */
class RequiredFieldPropertyTest {
    
    private val validator = SchemaValidator.withoutSchema()
    
    /**
     * Property 49: Required field validation
     *
     * For any data with missing required fields:
     * - Validation must fail
     * - Error messages must identify the missing fields
     * - Error messages must be descriptive
     */
    @Property(tries = 100)
    fun `validation detects missing required fields`(
        @ForAll("rawDataWithMissingFields") dataWithMissing: Pair<Map<String, Any>, List<String>>
    ) {
        // Given: Data with missing required fields
        val (data, expectedMissingFields) = dataWithMissing
        
        // When: Validating the data
        val result = validator.validate(data)
        
        // Then: Validation should fail
        assertThat(result.isValid).isFalse()
        
        // And: Error messages should identify all missing fields
        assertThat(result.errors).isNotEmpty
        
        for (missingField in expectedMissingFields) {
            val hasErrorForField = result.errors.any { error ->
                error.contains(missingField) && 
                (error.contains("missing") || error.contains("null") || error.contains("blank"))
            }
            assertThat(hasErrorForField)
                .withFailMessage("Expected error for missing field '$missingField', but got: ${result.errors}")
                .isTrue()
        }
    }
    
    /**
     * Property 49 (continued): Validation errors are descriptive
     *
     * For any validation error:
     * - Error message must include the field name
     * - Error message must describe the problem
     * - Error message must be human-readable
     */
    @Property(tries = 100)
    fun `validation errors are descriptive`(
        @ForAll("rawDataWithMissingFields") dataWithMissing: Pair<Map<String, Any>, List<String>>
    ) {
        // Given: Data with missing required fields
        val (data, expectedMissingFields) = dataWithMissing
        
        // When: Validating the data
        val result = validator.validate(data)
        
        // Then: Each error should be descriptive
        for (error in result.errors) {
            // Error should contain a field name
            val containsFieldName = expectedMissingFields.any { field -> error.contains(field) }
            assertThat(containsFieldName)
                .withFailMessage("Error message should contain field name: $error")
                .isTrue()
            
            // Error should describe the problem
            val describesProblems = error.contains("missing") || 
                                   error.contains("null") || 
                                   error.contains("blank") ||
                                   error.contains("required")
            assertThat(describesProblems)
                .withFailMessage("Error message should describe the problem: $error")
                .isTrue()
            
            // Error should be reasonably short (human-readable)
            assertThat(error.length)
                .withFailMessage("Error message should be concise: $error")
                .isLessThan(200)
        }
    }
    
    /**
     * Property 49 (continued): Valid data passes required field validation
     *
     * For any data with all required fields present:
     * - Validation should not fail due to missing fields
     * - No errors about missing required fields should be present
     */
    @Property(tries = 100)
    fun `valid data passes required field validation`(
        @ForAll("validRawData") data: Map<String, Any>
    ) {
        // Given: Data with all required fields
        // (from validRawData generator)
        
        // When: Validating the data
        val result = validator.validate(data)
        
        // Then: Should not have errors about missing required fields
        val requiredFieldErrors = result.errors.filter { error ->
            error.contains("missing") || error.contains("Required field")
        }
        
        assertThat(requiredFieldErrors)
            .withFailMessage("Valid data should not have missing field errors: $requiredFieldErrors")
            .isEmpty()
    }
    
    /**
     * Property 49 (continued): Null values are detected as missing
     *
     * For any required field with null value:
     * - Validation must fail
     * - Error message must indicate the field is null or missing
     */
    @Property(tries = 50)
    fun `null values in required fields are detected`(
        @ForAll("validRawData") baseData: Map<String, Any>
    ) {
        // Given: Data with a required field removed (simulating null)
        val requiredFields = listOf("customerId", "subjectType", "subjectId", "eventDate")
        
        for (field in requiredFields) {
            val dataWithMissing = baseData.toMutableMap()
            dataWithMissing.remove(field)
            
            // When: Validating the data
            val result = validator.validate(dataWithMissing)
            
            // Then: Validation should fail
            assertThat(result.isValid).isFalse()
            
            // And: Error should mention the missing/null field
            val hasError = result.errors.any { error ->
                error.contains(field) && (error.contains("missing") || error.contains("null"))
            }
            assertThat(hasError)
                .withFailMessage("Expected error for missing field '$field', but got: ${result.errors}")
                .isTrue()
        }
    }
    
    /**
     * Property 49 (continued): Blank strings are detected as invalid
     *
     * For any required string field with blank value:
     * - Validation must fail
     * - Error message must indicate the field is blank
     */
    @Property(tries = 50)
    fun `blank strings in required fields are detected`(
        @ForAll("validRawData") baseData: Map<String, Any>
    ) {
        // Given: Data with a required string field set to blank
        val stringFields = listOf("customerId", "subjectType", "subjectId")
        
        for (field in stringFields) {
            val dataWithBlank = baseData.toMutableMap()
            dataWithBlank[field] = "   " // Blank string
            
            // When: Validating the data
            val result = validator.validate(dataWithBlank)
            
            // Then: Validation should fail
            assertThat(result.isValid).isFalse()
            
            // And: Error should mention the blank field
            val hasBlankError = result.errors.any { error ->
                error.contains(field) && error.contains("blank")
            }
            assertThat(hasBlankError)
                .withFailMessage("Expected error for blank field '$field', but got: ${result.errors}")
                .isTrue()
        }
    }
    
    @Provide
    fun rawDataWithMissingFields(): Arbitrary<Pair<Map<String, Any>, List<String>>> {
        return DataArbitraries.rawDataWithMissingFields()
    }
    
    @Provide
    fun validRawData(): Arbitrary<Map<String, Any>> {
        return DataArbitraries.validRawData()
    }
}
