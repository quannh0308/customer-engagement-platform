package com.ceap.model.arbitraries

import com.ceap.model.*
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.time.api.DateTimes
import java.time.Instant

/**
 * Arbitrary generators for Candidate model and related classes.
 * These generators create valid random instances for property-based testing.
 */
object CandidateArbitraries {
    
    /**
     * Generates arbitrary Context instances.
     */
    fun contexts(): Arbitrary<Context> {
        val types = Arbitraries.of("marketplace", "program", "vertical", "category", "region")
        val ids = Arbitraries.strings().alpha().ofMinLength(2).ofMaxLength(20)
        
        return Combinators.combine(types, ids).`as` { type, id ->
            Context(type = type, id = id)
        }
    }
    
    /**
     * Generates arbitrary Subject instances.
     */
    fun subjects(): Arbitrary<Subject> {
        val types = Arbitraries.of("product", "video", "track", "service", "event", "experience")
        val ids = Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(15)
        val metadata = Arbitraries.maps(
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),
            Arbitraries.of("value1", "value2", "value3")
        ).ofMaxSize(3)
        
        return Combinators.combine(types, ids, metadata).`as` { type, id, meta ->
            Subject(
                type = type,
                id = id,
                metadata = if (meta.isEmpty()) null else meta
            )
        }
    }
    
    /**
     * Generates arbitrary Score instances.
     */
    fun scores(): Arbitrary<Score> {
        val modelIds = Arbitraries.of("quality-model", "relevance-model", "engagement-model", "sentiment-model")
        val values = Arbitraries.doubles().between(0.0, 1.0)
        val confidences = Arbitraries.doubles().between(0.0, 1.0)
        val timestamps = instants()
        val metadata = Arbitraries.maps(
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10),
            Arbitraries.of("meta1", "meta2", "meta3")
        ).ofMaxSize(2)
        
        return Combinators.combine(modelIds, values, confidences, timestamps, metadata)
            .`as` { modelId, value, confidence, timestamp, meta ->
                Score(
                    modelId = modelId,
                    value = value,
                    confidence = confidence,
                    timestamp = timestamp,
                    metadata = if (meta.isEmpty()) null else meta
                )
            }
    }
    
    /**
     * Generates arbitrary CandidateAttributes instances.
     */
    fun candidateAttributes(): Arbitrary<CandidateAttributes> {
        val eventDates = instants()
        val deliveryDates = instants()
        val timingWindows = Arbitraries.of("1-3 days", "7-14 days", "14-30 days", "30-60 days")
        val orderValues = Arbitraries.doubles().between(0.01, 9999.99)
        val mediaEligible = Arbitraries.of(true, false)
        val channelEligibility = Arbitraries.maps(
            Arbitraries.of("email", "push", "sms", "in-app", "web"),
            Arbitraries.of(true, false)
        ).ofMinSize(1).ofMaxSize(5)
        
        return Combinators.combine(
            eventDates,
            deliveryDates,
            timingWindows,
            orderValues,
            mediaEligible,
            channelEligibility
        ).`as` { eventDate, deliveryDate, timingWindow, orderValue, media, channels ->
            CandidateAttributes(
                eventDate = eventDate,
                deliveryDate = deliveryDate,
                timingWindow = timingWindow,
                orderValue = orderValue,
                mediaEligible = media,
                channelEligibility = channels
            )
        }
    }
    
    /**
     * Generates arbitrary CandidateMetadata instances.
     */
    fun candidateMetadata(): Arbitrary<CandidateMetadata> {
        val timestamps = instants()
        val versions = Arbitraries.longs().between(1L, 1000L)
        val connectorIds = Arbitraries.of(
            "order-connector",
            "review-connector",
            "event-connector",
            "subscription-connector"
        )
        val executionIds = Arbitraries.strings().withCharRange('a', 'z').numeric()
            .withChars('-').ofMinLength(10).ofMaxLength(30)
        
        return Combinators.combine(
            timestamps,
            timestamps,
            timestamps,
            versions,
            connectorIds,
            executionIds
        ).`as` { createdAt, updatedAt, expiresAt, version, connectorId, executionId ->
            CandidateMetadata(
                createdAt = createdAt,
                updatedAt = updatedAt.plusSeconds(60), // Ensure updatedAt >= createdAt
                expiresAt = expiresAt.plusSeconds(86400), // Ensure expiresAt > updatedAt
                version = version,
                sourceConnectorId = connectorId,
                workflowExecutionId = "exec-$executionId"
            )
        }
    }
    
    /**
     * Generates arbitrary RejectionRecord instances.
     */
    fun rejectionRecords(): Arbitrary<RejectionRecord> {
        val filterIds = Arbitraries.of(
            "frequency-filter",
            "eligibility-filter",
            "quality-filter",
            "timing-filter"
        )
        val reasons = Arbitraries.of(
            "Customer contacted too recently",
            "Customer not eligible for program",
            "Quality score below threshold",
            "Outside timing window"
        )
        val reasonCodes = Arbitraries.of(
            "FREQUENCY_CAP",
            "NOT_ELIGIBLE",
            "LOW_QUALITY",
            "TIMING_VIOLATION"
        )
        val timestamps = instants()
        
        return Combinators.combine(filterIds, reasons, reasonCodes, timestamps)
            .`as` { filterId, reason, reasonCode, timestamp ->
                RejectionRecord(
                    filterId = filterId,
                    reason = reason,
                    reasonCode = reasonCode,
                    timestamp = timestamp
                )
            }
    }
    
    /**
     * Generates arbitrary Candidate instances with all required fields.
     */
    fun candidates(): Arbitrary<Candidate> {
        val customerIds = Arbitraries.strings().withCharRange('a', 'z').numeric()
            .ofMinLength(8).ofMaxLength(20).map { "customer-$it" }
        val contextLists = contexts().list().ofMinSize(1).ofMaxSize(5)
        val subjectArb = subjects()
        val scoresMap = Arbitraries.maps(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(15),
            scores()
        ).ofMaxSize(3)
        val attributesArb = candidateAttributes()
        val metadataArb = candidateMetadata()
        val rejectionHistoryList = rejectionRecords().list().ofMaxSize(3)
        
        return Combinators.combine(
            customerIds,
            contextLists,
            subjectArb,
            scoresMap,
            attributesArb,
            metadataArb,
            rejectionHistoryList
        ).`as` { customerId, context, subject, scores, attributes, metadata, rejectionHistory ->
            Candidate(
                customerId = customerId,
                context = context,
                subject = subject,
                scores = if (scores.isEmpty()) null else scores,
                attributes = attributes,
                metadata = metadata,
                rejectionHistory = if (rejectionHistory.isEmpty()) null else rejectionHistory
            )
        }
    }
    
    /**
     * Generates arbitrary Instant values for timestamps.
     */
    private fun instants(): Arbitrary<Instant> {
        return DateTimes.instants()
            .between(
                Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2030-12-31T23:59:59Z")
            )
    }
}
