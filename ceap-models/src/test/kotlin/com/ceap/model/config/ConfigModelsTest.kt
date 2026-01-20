package com.ceap.model.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for configuration model classes.
 * Tests JSON serialization and data class functionality.
 */
class ConfigModelsTest {

    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setup() {
        objectMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
    }

    @Test
    fun `should create valid DataConnectorConfig`() {
        val config = DataConnectorConfig(
            connectorId = "kinesis-orders",
            connectorType = "kinesis",
            enabled = true,
            sourceConfig = mapOf("streamName" to "order-events")
        )

        assertEquals("kinesis-orders", config.connectorId)
        assertEquals("kinesis", config.connectorType)
        assertTrue(config.enabled)
        assertEquals("order-events", config.sourceConfig?.get("streamName"))
    }

    @Test
    fun `should create valid ScoringModelConfig`() {
        val config = ScoringModelConfig(
            modelId = "quality-score-v1",
            modelType = "ml",
            enabled = true,
            modelConfig = mapOf("threshold" to 0.7)
        )

        assertEquals("quality-score-v1", config.modelId)
        assertEquals("ml", config.modelType)
        assertTrue(config.enabled)
    }

    @Test
    fun `should create valid FilterConfig`() {
        val config = FilterConfig(
            filterId = "eligibility-filter",
            filterType = "eligibility",
            enabled = true,
            parameters = mapOf("minScore" to 0.5),
            order = 0
        )

        assertEquals("eligibility-filter", config.filterId)
        assertEquals("eligibility", config.filterType)
        assertTrue(config.enabled)
        assertEquals(0, config.order)
    }

    @Test
    fun `should create valid FilterChainConfig`() {
        val filter1 = FilterConfig(
            filterId = "filter-1",
            filterType = "eligibility",
            enabled = true,
            order = 0
        )
        val filter2 = FilterConfig(
            filterId = "filter-2",
            filterType = "quality",
            enabled = true,
            order = 1
        )

        val config = FilterChainConfig(
            filters = listOf(filter1, filter2)
        )

        assertEquals(2, config.filters.size)
    }

    @Test
    fun `should create valid ChannelConfig`() {
        val config = ChannelConfig(
            channelId = "email-channel",
            channelType = "email",
            enabled = true,
            shadowMode = false,
            config = mapOf("templateId" to "review-request")
        )

        assertEquals("email-channel", config.channelId)
        assertEquals("email", config.channelType)
        assertTrue(config.enabled)
        assertFalse(config.shadowMode)
    }

    @Test
    fun `should create valid ProgramConfig`() {
        val dataConnector = DataConnectorConfig(
            connectorId = "kinesis-orders",
            connectorType = "kinesis",
            enabled = true
        )

        val scoringModel = ScoringModelConfig(
            modelId = "quality-score",
            modelType = "ml",
            enabled = true
        )

        val filter = FilterConfig(
            filterId = "eligibility",
            filterType = "eligibility",
            enabled = true,
            order = 0
        )

        val filterChain = FilterChainConfig(
            filters = listOf(filter)
        )

        val channel = ChannelConfig(
            channelId = "email",
            channelType = "email",
            enabled = true,
            shadowMode = false
        )

        val config = ProgramConfig(
            programId = "product-reviews",
            programName = "Product Review Solicitation",
            enabled = true,
            marketplaces = listOf("US", "UK", "DE"),
            dataConnectors = listOf(dataConnector),
            scoringModels = listOf(scoringModel),
            filterChain = filterChain,
            channels = listOf(channel),
            batchSchedule = "0 0 * * *",
            reactiveEnabled = true,
            candidateTTLDays = 30,
            timingWindowDays = 7
        )

        assertEquals("product-reviews", config.programId)
        assertEquals("Product Review Solicitation", config.programName)
        assertTrue(config.enabled)
        assertEquals(3, config.marketplaces.size)
        assertEquals(1, config.dataConnectors.size)
        assertEquals(1, config.scoringModels.size)
        assertEquals(1, config.channels.size)
        assertEquals(30, config.candidateTTLDays)
    }

    @Test
    fun `should serialize and deserialize ProgramConfig to JSON`() {
        val dataConnector = DataConnectorConfig(
            connectorId = "kinesis-orders",
            connectorType = "kinesis",
            enabled = true,
            sourceConfig = mapOf("streamName" to "orders")
        )

        val scoringModel = ScoringModelConfig(
            modelId = "quality-score",
            modelType = "ml",
            enabled = true
        )

        val filter = FilterConfig(
            filterId = "eligibility",
            filterType = "eligibility",
            enabled = true,
            order = 0
        )

        val filterChain = FilterChainConfig(
            filters = listOf(filter)
        )

        val channel = ChannelConfig(
            channelId = "email",
            channelType = "email",
            enabled = true,
            shadowMode = false
        )

        val original = ProgramConfig(
            programId = "product-reviews",
            programName = "Product Review Solicitation",
            enabled = true,
            marketplaces = listOf("US", "UK"),
            dataConnectors = listOf(dataConnector),
            scoringModels = listOf(scoringModel),
            filterChain = filterChain,
            channels = listOf(channel),
            batchSchedule = "0 0 * * *",
            reactiveEnabled = true,
            candidateTTLDays = 30,
            timingWindowDays = 7
        )

        // Serialize to JSON
        val json = objectMapper.writeValueAsString(original)
        assertNotNull(json)
        assertTrue(json.contains("product-reviews"))

        // Deserialize from JSON
        val deserialized: ProgramConfig = objectMapper.readValue(json)
        assertEquals(original.programId, deserialized.programId)
        assertEquals(original.programName, deserialized.programName)
        assertEquals(original.enabled, deserialized.enabled)
        assertEquals(original.marketplaces, deserialized.marketplaces)
        assertEquals(original.candidateTTLDays, deserialized.candidateTTLDays)
        assertEquals(original.timingWindowDays, deserialized.timingWindowDays)
    }

    @Test
    fun `should support data class copy for immutable updates`() {
        val original = ChannelConfig(
            channelId = "email",
            channelType = "email",
            enabled = true,
            shadowMode = false
        )

        val updated = original.copy(enabled = false)

        assertFalse(updated.enabled)
        assertTrue(original.enabled) // Original unchanged
        assertEquals(original.channelId, updated.channelId)
    }
}
