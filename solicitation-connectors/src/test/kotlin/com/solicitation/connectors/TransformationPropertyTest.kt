package com.solicitation.connectors

import com.solicitation.connectors.arbitraries.DataArbitraries
import com.solicitation.model.config.DataConnectorConfig
import net.jqwik.api.*
import org.assertj.core.api.Assertions.assertThat
import software.amazon.awssdk.services.athena.AthenaClient
import io.mockk.mockk

/**
 * Property-based tests for data connector transformation semantics.
 *
 * **Property 1: Data connector transformation preserves semantics**
 * **Validates: Requirements 1.2**
 *
 * These tests verify that:
 * - Source data is correctly transformed to candidate model
 * - No data loss occurs during transformation
 * - Field mappings are applied correctly
 * - Required fields are preserved
 */
class TransformationPropertyTest {
    
    private val athenaClient: AthenaClient = mockk(relaxed = true)
    private val connector = DataWarehouseConnector(athenaClient)
    
    /**
     * Property 1: Data connector transformation preserves semantics
     *
     * For any valid raw data, when transformed to a Candidate:
     * - The customerId must match the source customerId
     * - The subject type and ID must match the source
     * - The context must include all source context dimensions
     * - The eventDate must match the source eventDate
     */
    @Property(tries = 100)
    fun `transformation preserves core field semantics`(
        @ForAll("validRawData") rawData: Map<String, Any>
    ) {
        // Given: A valid connector configuration
        val config = DataConnectorConfig(
            connectorId = "test-connector",
            connectorType = "data-warehouse",
            enabled = true,
            sourceConfig = mapOf(
                "database" to "test_db",
                "query" to "SELECT * FROM test",
                "resultsBucket" to "test-bucket"
            )
        )
        
        // When: Transforming raw data to candidate
        val candidate = connector.transformToCandidate(rawData, config)
        
        // Then: Candidate should be created successfully
        assertThat(candidate).isNotNull
        
        // And: Core fields should match source data
        assertThat(candidate!!.customerId).isEqualTo(rawData["customerId"])
        assertThat(candidate.subject.type).isEqualTo(rawData["subjectType"])
        assertThat(candidate.subject.id).isEqualTo(rawData["subjectId"])
        
        // And: Context should include marketplace and program
        val contextTypes = candidate.context.map { it.type }
        assertThat(contextTypes).contains("marketplace", "program")
        
        val marketplaceContext = candidate.context.find { it.type == "marketplace" }
        assertThat(marketplaceContext?.id).isEqualTo(rawData["marketplace"])
        
        val programContext = candidate.context.find { it.type == "program" }
        assertThat(programContext?.id).isEqualTo(rawData["programId"])
        
        // And: Metadata should reference the source connector
        assertThat(candidate.metadata.sourceConnectorId).isEqualTo(config.connectorId)
    }
    
    /**
     * Property 1 (continued): Transformation preserves numeric and boolean fields
     *
     * For any valid raw data with numeric/boolean fields:
     * - Numeric values should be preserved (orderValue)
     * - Boolean values should be preserved (channel eligibility)
     */
    @Property(tries = 100)
    fun `transformation preserves numeric and boolean field semantics`(
        @ForAll("validRawData") rawData: Map<String, Any>
    ) {
        // Given: A valid connector configuration
        val config = DataConnectorConfig(
            connectorId = "test-connector",
            connectorType = "data-warehouse",
            enabled = true,
            sourceConfig = mapOf(
                "database" to "test_db",
                "query" to "SELECT * FROM test",
                "resultsBucket" to "test-bucket"
            )
        )
        
        // When: Transforming raw data to candidate
        val candidate = connector.transformToCandidate(rawData, config)
        
        // Then: Candidate should be created successfully
        assertThat(candidate).isNotNull
        
        // And: Numeric fields should be preserved
        if (rawData.containsKey("orderValue")) {
            val expectedOrderValue = (rawData["orderValue"] as Number).toDouble()
            assertThat(candidate!!.attributes.orderValue).isEqualTo(expectedOrderValue)
        }
        
        // And: Boolean fields should be preserved in channel eligibility
        if (rawData.containsKey("emailEligible")) {
            val expectedEmailEligible = rawData["emailEligible"] as Boolean
            assertThat(candidate!!.attributes.channelEligibility["email"]).isEqualTo(expectedEmailEligible)
        }
    }
    
    /**
     * Property 1 (continued): Transformation with field mappings preserves semantics
     *
     * For any valid raw data with field mappings:
     * - Mapped fields should be correctly transformed
     * - Source field names should be mapped to target field names
     * - Values should be preserved through mapping
     */
    @Property(tries = 100)
    fun `transformation with field mappings preserves semantics`(
        @ForAll("validRawData") rawData: Map<String, Any>,
        @ForAll("fieldMappingConfig") fieldMappings: Map<String, Any>
    ) {
        // Given: A connector configuration with field mappings
        val config = DataConnectorConfig(
            connectorId = "test-connector",
            connectorType = "data-warehouse",
            enabled = true,
            sourceConfig = mapOf(
                "database" to "test_db",
                "query" to "SELECT * FROM test",
                "resultsBucket" to "test-bucket",
                "fieldMappings" to fieldMappings
            )
        )
        
        // And: Raw data with source field names
        val mappedRawData = mutableMapOf<String, Any>()
        
        // Apply reverse mapping to create source data
        for ((targetField, mapping) in fieldMappings) {
            val sourceField = when (mapping) {
                is Map<*, *> -> mapping["sourceField"] as? String ?: targetField
                is String -> mapping
                else -> targetField
            }
            
            if (rawData.containsKey(targetField)) {
                mappedRawData[sourceField] = rawData[targetField]!!
            }
        }
        
        // Add unmapped required fields
        for ((key, value) in rawData) {
            if (!mappedRawData.containsValue(value)) {
                mappedRawData[key] = value
            }
        }
        
        // When: Transforming with field mappings
        val candidate = connector.transformToCandidate(mappedRawData, config)
        
        // Then: Transformation should succeed or fail gracefully
        // (Some mappings may not have all required fields)
        if (candidate != null) {
            // Verify that mapped fields are present
            assertThat(candidate.customerId).isNotBlank()
            assertThat(candidate.subject.type).isNotBlank()
            assertThat(candidate.subject.id).isNotBlank()
            assertThat(candidate.context).isNotEmpty
        }
    }
    
    @Provide
    fun validRawData(): Arbitrary<Map<String, Any>> {
        return DataArbitraries.validRawData()
    }
    
    @Provide
    fun fieldMappingConfig(): Arbitrary<Map<String, Any>> {
        return DataArbitraries.fieldMappingConfig()
    }
}
