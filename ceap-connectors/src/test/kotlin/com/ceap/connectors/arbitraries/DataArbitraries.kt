package com.ceap.connectors.arbitraries

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Arbitrary generators for test data used in connector property tests.
 */
object DataArbitraries {
    
    /**
     * Generates valid raw data maps for candidate transformation.
     */
    fun validRawData(): Arbitrary<Map<String, Any>> {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),  // customerId
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),  // subjectType
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),  // subjectId
            Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(5),   // marketplace
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15),  // programId
            instantArbitrary(),                                              // eventDate
            Arbitraries.doubles().between(0.0, 10000.0),                   // orderValue
            Arbitraries.of(true, false)                                     // emailEligible
        ).`as` { customerId: String, subjectType: String, subjectId: String, 
                 marketplace: String, programId: String, eventDate: Instant, 
                 orderValue: Double, emailEligible: Boolean ->
            mapOf(
                "customerId" to customerId,
                "subjectType" to subjectType,
                "subjectId" to subjectId,
                "marketplace" to marketplace,
                "programId" to programId,
                "eventDate" to eventDate.toString(),
                "orderValue" to orderValue,
                "emailEligible" to emailEligible,
                "workflowExecutionId" to "test-workflow-${System.currentTimeMillis()}"
            )
        }
    }
    
    /**
     * Generates raw data with missing required fields.
     */
    fun rawDataWithMissingFields(): Arbitrary<Pair<Map<String, Any>, List<String>>> {
        return Arbitraries.of(
            Pair(
                mapOf(
                    "subjectType" to "product",
                    "subjectId" to "ASIN123",
                    "marketplace" to "US"
                ),
                listOf("customerId", "eventDate")
            ),
            Pair(
                mapOf(
                    "customerId" to "CUST123",
                    "subjectId" to "ASIN123",
                    "eventDate" to Instant.now().toString()
                ),
                listOf("subjectType")
            ),
            Pair(
                mapOf(
                    "customerId" to "CUST123",
                    "subjectType" to "product",
                    "eventDate" to Instant.now().toString()
                ),
                listOf("subjectId")
            ),
            Pair(
                mapOf(
                    "customerId" to "CUST123",
                    "subjectType" to "product",
                    "subjectId" to "ASIN123"
                ),
                listOf("eventDate")
            )
        )
    }
    
    /**
     * Generates raw data with invalid date formats.
     */
    fun rawDataWithInvalidDates(): Arbitrary<Map<String, Any>> {
        return Arbitraries.of(
            mapOf(
                "customerId" to "CUST123",
                "subjectType" to "product",
                "subjectId" to "ASIN123",
                "marketplace" to "US",
                "eventDate" to "not-a-date"
            ),
            mapOf(
                "customerId" to "CUST123",
                "subjectType" to "product",
                "subjectId" to "ASIN123",
                "marketplace" to "US",
                "eventDate" to "2024-13-45"  // Invalid month and day
            ),
            mapOf(
                "customerId" to "CUST123",
                "subjectType" to "product",
                "subjectId" to "ASIN123",
                "marketplace" to "US",
                "eventDate" to "12345abc"
            )
        )
    }
    
    /**
     * Generates raw data with valid date formats.
     */
    fun rawDataWithValidDates(): Arbitrary<Map<String, Any>> {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
            instantArbitrary()
        ).`as` { customerId: String, eventDate: Instant ->
            mapOf(
                "customerId" to customerId,
                "subjectType" to "product",
                "subjectId" to "ASIN123",
                "marketplace" to "US",
                "eventDate" to eventDate.toString(),
                "deliveryDate" to eventDate.plus(7, ChronoUnit.DAYS).toString()
            )
        }
    }
    
    /**
     * Generates field mapping configurations.
     */
    fun fieldMappingConfig(): Arbitrary<Map<String, Any>> {
        return Arbitraries.of(
            mapOf(
                "customerId" to mapOf(
                    "sourceField" to "customer_id",
                    "type" to "STRING",
                    "required" to true
                ),
                "subjectId" to mapOf(
                    "sourceField" to "asin",
                    "type" to "STRING",
                    "required" to true
                )
            ),
            mapOf(
                "customerId" to "customer_id",
                "subjectType" to "product_type",
                "subjectId" to "product_id"
            )
        )
    }
    
    /**
     * Generates Instant values for testing.
     */
    private fun instantArbitrary(): Arbitrary<Instant> {
        val now = Instant.now()
        val past = now.minus(365, ChronoUnit.DAYS)
        val future = now.plus(365, ChronoUnit.DAYS)
        
        return Arbitraries.longs()
            .between(past.toEpochMilli(), future.toEpochMilli())
            .map { Instant.ofEpochMilli(it) }
    }
}
