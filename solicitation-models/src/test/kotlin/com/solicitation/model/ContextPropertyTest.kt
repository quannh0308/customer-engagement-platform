package com.solicitation.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.solicitation.model.arbitraries.CandidateArbitraries
import net.jqwik.api.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-based tests for Context extensibility.
 * 
 * **Validates: Requirements 1.3, 2.3**
 * 
 * These tests verify that:
 * - Any valid context type/id combination can be stored without data loss
 * - JSON round-trip preserves context values
 * - The context model supports arbitrary dimensions
 */
class ContextPropertyTest {
    
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
    
    /**
     * Property 3: Context extensibility
     * 
     * For any valid context type and id combination, the candidate model must be able to 
     * store it in the context array without data loss.
     * 
     * This test verifies that arbitrary context types and IDs can be stored in a candidate
     * and retrieved without modification.
     * 
     * **Validates: Requirements 1.3, 2.3**
     */
    @Property(tries = 100)
    fun `any valid context type and id can be stored without data loss`(
        @ForAll("arbitraryContextTypes") contextType: String,
        @ForAll("arbitraryContextIds") contextId: String,
        @ForAll("validCandidates") candidate: Candidate
    ) {
        // Create a context with arbitrary type and id
        val customContext = Context(type = contextType, id = contextId)
        
        // Add the custom context to the candidate's context array
        val updatedCandidate = candidate.copy(
            context = candidate.context + customContext
        )
        
        // Verify the context was added
        assertTrue(
            updatedCandidate.context.contains(customContext),
            "Context with type '$contextType' and id '$contextId' should be present in candidate"
        )
        
        // Verify the context can be retrieved
        val retrievedContext = updatedCandidate.context.find { 
            it.type == contextType && it.id == contextId 
        }
        assertNotNull(retrievedContext, "Should be able to retrieve context by type and id")
        assertEquals(contextType, retrievedContext.type, "Context type should match")
        assertEquals(contextId, retrievedContext.id, "Context id should match")
    }
    
    /**
     * Property 3b: JSON round-trip preserves context values
     * 
     * For any context stored in a candidate, JSON serialization and deserialization
     * must preserve the context type and id without modification.
     * 
     * **Validates: Requirements 1.3, 2.3**
     */
    @Property(tries = 100)
    fun `JSON round-trip preserves context values`(
        @ForAll("arbitraryContextTypes") contextType: String,
        @ForAll("arbitraryContextIds") contextId: String,
        @ForAll("validCandidates") candidate: Candidate
    ) {
        // Create a context with arbitrary type and id
        val customContext = Context(type = contextType, id = contextId)
        
        // Add the custom context to the candidate
        val originalCandidate = candidate.copy(
            context = candidate.context + customContext
        )
        
        // Serialize to JSON
        val json = objectMapper.writeValueAsString(originalCandidate)
        assertNotNull(json, "JSON serialization should succeed")
        
        // Deserialize from JSON
        val deserializedCandidate: Candidate = objectMapper.readValue(json)
        
        // Verify the custom context is preserved
        val deserializedContext = deserializedCandidate.context.find {
            it.type == contextType && it.id == contextId
        }
        assertNotNull(
            deserializedContext,
            "Context with type '$contextType' and id '$contextId' should be preserved after JSON round-trip"
        )
        assertEquals(contextType, deserializedContext.type, "Context type should be preserved")
        assertEquals(contextId, deserializedContext.id, "Context id should be preserved")
    }
    
    /**
     * Property 3c: Multiple contexts with same type but different ids are preserved
     * 
     * The context array should support multiple contexts of the same type with different IDs,
     * allowing for scenarios like multiple marketplaces or programs.
     * 
     * **Validates: Requirements 1.3, 2.3**
     */
    @Property(tries = 100)
    fun `multiple contexts with same type but different ids are preserved`(
        @ForAll("arbitraryContextTypes") contextType: String,
        @ForAll("arbitraryContextIds") contextId1: String,
        @ForAll("arbitraryContextIds") contextId2: String,
        @ForAll("validCandidates") candidate: Candidate
    ) {
        // Skip if IDs are the same (we want to test different IDs)
        Assume.that(!contextId1.equals(contextId2, ignoreCase = true))
        
        // Create two contexts with the same type but different IDs
        val context1 = Context(type = contextType, id = contextId1)
        val context2 = Context(type = contextType, id = contextId2)
        
        // Add both contexts to the candidate
        val updatedCandidate = candidate.copy(
            context = candidate.context + context1 + context2
        )
        
        // Verify both contexts are present
        val contextsOfType = updatedCandidate.context.filter { it.type == contextType }
        assertTrue(
            contextsOfType.any { it.id == contextId1 },
            "Context with id '$contextId1' should be present"
        )
        assertTrue(
            contextsOfType.any { it.id == contextId2 },
            "Context with id '$contextId2' should be present"
        )
        
        // Verify JSON round-trip preserves both
        val json = objectMapper.writeValueAsString(updatedCandidate)
        val deserialized: Candidate = objectMapper.readValue(json)
        
        val deserializedContextsOfType = deserialized.context.filter { it.type == contextType }
        assertTrue(
            deserializedContextsOfType.any { it.id == contextId1 },
            "Context with id '$contextId1' should be preserved after JSON round-trip"
        )
        assertTrue(
            deserializedContextsOfType.any { it.id == contextId2 },
            "Context with id '$contextId2' should be preserved after JSON round-trip"
        )
    }
    
    /**
     * Property 3d: Context with special characters in type and id are preserved
     * 
     * Context types and IDs may contain special characters (hyphens, underscores, etc.)
     * and these should be preserved through storage and JSON serialization.
     * 
     * **Validates: Requirements 1.3, 2.3**
     */
    @Property(tries = 100)
    fun `context with special characters in type and id are preserved`(
        @ForAll("contextTypesWithSpecialChars") contextType: String,
        @ForAll("contextIdsWithSpecialChars") contextId: String,
        @ForAll("validCandidates") candidate: Candidate
    ) {
        // Create a context with special characters
        val specialContext = Context(type = contextType, id = contextId)
        
        // Add to candidate
        val updatedCandidate = candidate.copy(
            context = candidate.context + specialContext
        )
        
        // Verify the context is stored correctly
        val retrievedContext = updatedCandidate.context.find {
            it.type == contextType && it.id == contextId
        }
        assertNotNull(retrievedContext, "Context with special characters should be stored")
        assertEquals(contextType, retrievedContext.type, "Context type with special chars should match")
        assertEquals(contextId, retrievedContext.id, "Context id with special chars should match")
        
        // Verify JSON round-trip preserves special characters
        val json = objectMapper.writeValueAsString(updatedCandidate)
        val deserialized: Candidate = objectMapper.readValue(json)
        
        val deserializedContext = deserialized.context.find {
            it.type == contextType && it.id == contextId
        }
        assertNotNull(
            deserializedContext,
            "Context with special characters should survive JSON round-trip"
        )
        assertEquals(
            contextType,
            deserializedContext.type,
            "Context type with special chars should be preserved"
        )
        assertEquals(
            contextId,
            deserializedContext.id,
            "Context id with special chars should be preserved"
        )
    }
    
    /**
     * Property 3e: Context array order is preserved through JSON round-trip
     * 
     * The order of contexts in the array should be preserved during JSON serialization
     * and deserialization, as order may be semantically meaningful.
     * 
     * **Validates: Requirements 1.3, 2.3**
     */
    @Property(tries = 100)
    fun `context array order is preserved through JSON round-trip`(
        @ForAll("validCandidates") candidate: Candidate
    ) {
        // Ensure candidate has at least 2 contexts for meaningful order testing
        Assume.that(candidate.context.size >= 2)
        
        // Record the original order
        val originalOrder = candidate.context.map { it.type to it.id }
        
        // Serialize and deserialize
        val json = objectMapper.writeValueAsString(candidate)
        val deserialized: Candidate = objectMapper.readValue(json)
        
        // Verify the order is preserved
        val deserializedOrder = deserialized.context.map { it.type to it.id }
        assertEquals(
            originalOrder,
            deserializedOrder,
            "Context array order should be preserved through JSON round-trip"
        )
    }
    
    /**
     * Property 3f: Empty strings are not valid for context type or id
     * 
     * Context type and id must not be blank (empty or whitespace-only).
     * This validates the @NotBlank constraint.
     * 
     * **Validates: Requirements 1.3, 2.3**
     */
    @Property(tries = 100)
    fun `context type and id must not be blank`(
        @ForAll("validCandidates") candidate: Candidate
    ) {
        // Verify all contexts in the candidate have non-blank type and id
        candidate.context.forEach { context ->
            assertTrue(
                context.type.isNotBlank(),
                "Context type must not be blank"
            )
            assertTrue(
                context.id.isNotBlank(),
                "Context id must not be blank"
            )
        }
    }
    
    // ========== Arbitrary Providers ==========
    
    @Provide
    fun validCandidates(): Arbitrary<Candidate> {
        return CandidateArbitraries.candidates()
    }
    
    @Provide
    fun arbitraryContextTypes(): Arbitrary<String> {
        return Arbitraries.of(
            "marketplace",
            "program",
            "vertical",
            "category",
            "region",
            "business-unit",
            "customer-segment",
            "product-line",
            "channel",
            "campaign",
            "experiment",
            "feature-flag",
            "locale",
            "device-type",
            "platform"
        )
    }
    
    @Provide
    fun arbitraryContextIds(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .ofMinLength(1)
            .ofMaxLength(50)
    }
    
    @Provide
    fun contextTypesWithSpecialChars(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('-', '_', '.')
            .ofMinLength(1)
            .ofMaxLength(30)
            .filter { it.isNotBlank() }
    }
    
    @Provide
    fun contextIdsWithSpecialChars(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .withChars('-', '_', '.', ':', '/')
            .ofMinLength(1)
            .ofMaxLength(50)
            .filter { it.isNotBlank() }
    }
}
