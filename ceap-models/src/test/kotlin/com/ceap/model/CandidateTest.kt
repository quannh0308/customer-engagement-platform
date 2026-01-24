package com.ceap.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CandidateTest {
    
    private lateinit var objectMapper: ObjectMapper
    
    @BeforeEach
    fun setup() {
        objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
    
    @Test
    fun `should create valid candidate with all required fields`() {
        val now = Instant.now()
        
        val context = Context(
            type = "marketplace",
            id = "US"
        )
        
        val subject = Subject(
            type = "product",
            id = "B08N5WRWNW"
        )
        
        val attributes = CandidateAttributes(
            eventDate = now,
            channelEligibility = mapOf("email" to true, "push" to false)
        )
        
        val metadata = CandidateMetadata(
            createdAt = now,
            updatedAt = now,
            expiresAt = now.plusSeconds(86400),
            version = 1L,
            sourceConnectorId = "order-connector",
            workflowExecutionId = "exec-123"
        )
        
        val candidate = Candidate(
            customerId = "customer-123",
            context = listOf(context),
            subject = subject,
            attributes = attributes,
            metadata = metadata
        )
        
        // Verify fields
        assertEquals("customer-123", candidate.customerId)
        assertEquals(1, candidate.context.size)
        assertEquals("marketplace", candidate.context[0].type)
        assertEquals("US", candidate.context[0].id)
        assertEquals("product", candidate.subject.type)
        assertEquals("B08N5WRWNW", candidate.subject.id)
        assertNotNull(candidate.attributes)
        assertNotNull(candidate.metadata)
    }
    
    @Test
    fun `should serialize and deserialize candidate to JSON`() {
        val now = Instant.parse("2024-01-15T10:00:00Z")
        
        val candidate = Candidate(
            customerId = "customer-456",
            context = listOf(
                Context(type = "marketplace", id = "US"),
                Context(type = "program", id = "review-engagement")
            ),
            subject = Subject(
                type = "product",
                id = "B08N5WRWNW",
                metadata = mapOf("category" to "Electronics")
            ),
            scores = mapOf(
                "quality-model" to Score(
                    modelId = "quality-model",
                    value = 0.85,
                    confidence = 0.9,
                    timestamp = now
                )
            ),
            attributes = CandidateAttributes(
                eventDate = now,
                deliveryDate = now.plusSeconds(172800),
                timingWindow = "7-14 days",
                orderValue = 99.99,
                mediaEligible = true,
                channelEligibility = mapOf("email" to true, "push" to true, "sms" to false)
            ),
            metadata = CandidateMetadata(
                createdAt = now,
                updatedAt = now,
                expiresAt = now.plusSeconds(2592000),
                version = 1L,
                sourceConnectorId = "order-connector",
                workflowExecutionId = "exec-456"
            ),
            rejectionHistory = listOf(
                RejectionRecord(
                    filterId = "frequency-filter",
                    reason = "Customer contacted too recently",
                    reasonCode = "FREQUENCY_CAP",
                    timestamp = now.minusSeconds(3600)
                )
            )
        )
        
        // Serialize to JSON
        val json = objectMapper.writeValueAsString(candidate)
        assertNotNull(json)
        assertTrue(json.contains("customer-456"))
        assertTrue(json.contains("marketplace"))
        assertTrue(json.contains("quality-model"))
        
        // Deserialize from JSON
        val deserialized: Candidate = objectMapper.readValue(json)
        
        // Verify deserialized object
        assertEquals(candidate.customerId, deserialized.customerId)
        assertEquals(candidate.context.size, deserialized.context.size)
        assertEquals(candidate.context[0].type, deserialized.context[0].type)
        assertEquals(candidate.subject.type, deserialized.subject.type)
        assertEquals(candidate.scores?.size, deserialized.scores?.size)
        assertEquals(candidate.attributes.eventDate, deserialized.attributes.eventDate)
        assertEquals(candidate.metadata.version, deserialized.metadata.version)
        assertEquals(candidate.rejectionHistory?.size, deserialized.rejectionHistory?.size)
    }
    
    @Test
    fun `should support multiple context dimensions`() {
        val now = Instant.now()
        
        val candidate = Candidate(
            customerId = "customer-789",
            context = listOf(
                Context(type = "marketplace", id = "US"),
                Context(type = "program", id = "review-engagement"),
                Context(type = "vertical", id = "electronics")
            ),
            subject = Subject(type = "product", id = "B08N5WRWNW"),
            attributes = CandidateAttributes(
                eventDate = now,
                channelEligibility = mapOf("email" to true)
            ),
            metadata = CandidateMetadata(
                createdAt = now,
                updatedAt = now,
                expiresAt = now.plusSeconds(86400),
                version = 1L,
                sourceConnectorId = "connector-1",
                workflowExecutionId = "exec-789"
            )
        )
        
        assertEquals(3, candidate.context.size)
        assertEquals("marketplace", candidate.context[0].type)
        assertEquals("program", candidate.context[1].type)
        assertEquals("vertical", candidate.context[2].type)
    }
    
    @Test
    fun `should support data class copy for immutable updates`() {
        val now = Instant.now()
        
        val original = Candidate(
            customerId = "customer-001",
            context = listOf(Context(type = "marketplace", id = "US")),
            subject = Subject(type = "product", id = "B08N5WRWNW"),
            attributes = CandidateAttributes(
                eventDate = now,
                channelEligibility = mapOf("email" to true)
            ),
            metadata = CandidateMetadata(
                createdAt = now,
                updatedAt = now,
                expiresAt = now.plusSeconds(86400),
                version = 1L,
                sourceConnectorId = "connector-1",
                workflowExecutionId = "exec-001"
            )
        )
        
        // Update using copy
        val updated = original.copy(
            metadata = original.metadata.copy(
                updatedAt = now.plusSeconds(60),
                version = 2L
            )
        )
        
        // Verify original is unchanged
        assertEquals(1L, original.metadata.version)
        
        // Verify updated has new values
        assertEquals(2L, updated.metadata.version)
        assertEquals(original.customerId, updated.customerId)
    }
}
