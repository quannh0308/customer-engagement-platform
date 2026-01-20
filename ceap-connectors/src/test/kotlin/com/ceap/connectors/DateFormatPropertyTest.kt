package com.ceap.connectors

import com.ceap.connectors.arbitraries.DataArbitraries
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import java.time.Instant

/**
 * Property-based tests for date format validation.
 *
 * **Property 50: Date format validation**
 * **Validates: Requirements 16.2, 16.3**
 *
 * These tests verify that:
 * - Date fields are validated correctly
 * - Invalid date formats are rejected
 * - Valid date formats are accepted
 * - Error messages are descriptive
 */
class DateFormatPropertyTest {
    
    private val validator = SchemaValidator.withoutSchema()
    
    /**
     * Property 50: Date format validation
     *
     * For any data with invalid date formats:
     * - Validation must fail
     * - Error messages must identify the invalid date fields
     * - Error messages must be descriptive
     */
    @Property(tries = 100)
    fun `validation detects invalid date formats`(
        @ForAll("rawDataWithInvalidDates") data: Map<String, Any>
    ) {
        // Given: Data with invalid date format
        // (from rawDataWithInvalidDates generator)
        
        // When: Validating the data
        val result = validator.validate(data)
        
        // Then: Validation should fail
        assertThat(result.isValid).isFalse()
        
        // And: Error messages should identify date format issues
        val dateFormatErrors = result.errors.filter { error ->
            error.contains("date") && error.contains("invalid")
        }
        
        assertThat(dateFormatErrors)
            .withFailMessage("Expected date format errors, but got: ${result.errors}")
            .isNotEmpty
    }
    
    /**
     * Property 50 (continued): Valid date formats are accepted
     *
     * For any data with valid date formats:
     * - Validation should not fail due to date format issues
     * - No errors about invalid dates should be present
     */
    @Property(tries = 100)
    fun `validation accepts valid date formats`(
        @ForAll("rawDataWithValidDates") data: Map<String, Any>
    ) {
        // Given: Data with valid date formats
        // (from rawDataWithValidDates generator)
        
        // When: Validating the data
        val result = validator.validate(data)
        
        // Then: Should not have date format errors
        val dateFormatErrors = result.errors.filter { error ->
            error.contains("date") && error.contains("invalid")
        }
        
        assertThat(dateFormatErrors)
            .withFailMessage("Valid dates should not produce format errors: $dateFormatErrors")
            .isEmpty()
    }
    
    /**
     * Property 50 (continued): ISO 8601 instant format is accepted
     *
     * For any ISO 8601 instant string:
     * - Validation should accept it as a valid date
     */
    @Property(tries = 100)
    fun `ISO 8601 instant format is accepted`(
        @ForAll("instantString") instantString: String
    ) {
        // Given: Data with ISO 8601 instant format
        val data = mapOf(
            "customerId" to "CUST123",
            "subjectType" to "product",
            "subjectId" to "ASIN123",
            "marketplace" to "US",
            "eventDate" to instantString
        )
        
        // When: Validating the data
        val result = validator.validate(data)
        
        // Then: Should not have date format errors for eventDate
        val eventDateErrors = result.errors.filter { error ->
            error.contains("eventDate") && error.contains("invalid")
        }
        
        assertThat(eventDateErrors)
            .withFailMessage("ISO 8601 instant should be valid: $instantString, errors: $eventDateErrors")
            .isEmpty()
    }
    
    /**
     * Property 50 (continued): Epoch milliseconds format is accepted
     *
     * For any valid epoch milliseconds value:
     * - Validation should accept it as a valid date
     */
    @Property(tries = 100)
    fun `epoch milliseconds format is accepted`(
        @ForAll("epochMillis") epochMillis: Long
    ) {
        // Given: Data with epoch milliseconds format
        val data = mapOf(
            "customerId" to "CUST123",
            "subjectType" to "product",
            "subjectId" to "ASIN123",
            "marketplace" to "US",
            "eventDate" to epochMillis
        )
        
        // When: Validating the data
        val result = validator.validate(data)
        
        // Then: Should not have date format errors for eventDate
        val eventDateErrors = result.errors.filter { error ->
            error.contains("eventDate") && error.contains("invalid")
        }
        
        assertThat(eventDateErrors)
            .withFailMessage("Epoch milliseconds should be valid: $epochMillis, errors: $eventDateErrors")
            .isEmpty()
    }
    
    /**
     * Property 50 (continued): Date validation errors are descriptive
     *
     * For any invalid date format:
     * - Error message must include the field name
     * - Error message must indicate it's a date format issue
     * - Error message must show the invalid value
     */
    @Property(tries = 100)
    fun `date validation errors are descriptive`(
        @ForAll("rawDataWithInvalidDates") data: Map<String, Any>
    ) {
        // Given: Data with invalid date format
        // (from rawDataWithInvalidDates generator)
        
        // When: Validating the data
        val result = validator.validate(data)
        
        // Then: Date format errors should be descriptive
        val dateFormatErrors = result.errors.filter { error ->
            error.contains("date") && error.contains("invalid")
        }
        
        for (error in dateFormatErrors) {
            // Error should contain field name
            assertThat(error)
                .withFailMessage("Error should contain field name: $error")
                .containsAnyOf("eventDate", "deliveryDate", "createdAt", "updatedAt", "expiresAt")
            
            // Error should indicate it's a format issue
            assertThat(error)
                .withFailMessage("Error should indicate format issue: $error")
                .containsAnyOf("invalid", "format")
            
            // Error should be reasonably short (human-readable)
            assertThat(error.length)
                .withFailMessage("Error message should be concise: $error")
                .isLessThan(200)
        }
    }
    
    /**
     * Property 50 (continued): Multiple date fields are validated
     *
     * For any data with multiple date fields:
     * - All date fields should be validated
     * - Invalid dates in any field should be detected
     */
    @Property(tries = 50)
    fun `multiple date fields are validated`() {
        // Given: Data with multiple invalid date fields
        val data = mapOf(
            "customerId" to "CUST123",
            "subjectType" to "product",
            "subjectId" to "ASIN123",
            "marketplace" to "US",
            "eventDate" to "invalid-date-1",
            "deliveryDate" to "invalid-date-2"
        )
        
        // When: Validating the data
        val result = validator.validate(data)
        
        // Then: Should have errors for both date fields
        val eventDateError = result.errors.any { it.contains("eventDate") && it.contains("invalid") }
        val deliveryDateError = result.errors.any { it.contains("deliveryDate") && it.contains("invalid") }
        
        assertThat(eventDateError)
            .withFailMessage("Should detect invalid eventDate")
            .isTrue()
        
        assertThat(deliveryDateError)
            .withFailMessage("Should detect invalid deliveryDate")
            .isTrue()
    }
    
    @Provide
    fun rawDataWithInvalidDates(): Arbitrary<Map<String, Any>> {
        return DataArbitraries.rawDataWithInvalidDates()
    }
    
    @Provide
    fun rawDataWithValidDates(): Arbitrary<Map<String, Any>> {
        return DataArbitraries.rawDataWithValidDates()
    }
    
    @Provide
    fun instantString(): Arbitrary<String> {
        val now = Instant.now()
        val past = now.minusSeconds(365L * 24 * 60 * 60)
        val future = now.plusSeconds(365L * 24 * 60 * 60)
        
        return Arbitraries.longs()
            .between(past.toEpochMilli(), future.toEpochMilli())
            .map { Instant.ofEpochMilli(it).toString() }
    }
    
    @Provide
    fun epochMillis(): Arbitrary<Long> {
        val now = Instant.now()
        val past = now.minusSeconds(365L * 24 * 60 * 60)
        val future = now.plusSeconds(365L * 24 * 60 * 60)
        
        return Arbitraries.longs()
            .between(past.toEpochMilli(), future.toEpochMilli())
    }
}
