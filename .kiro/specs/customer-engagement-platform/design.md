# Design Document - Task 2: Implement Core Data Models

> **Platform Rebranding Note**: This platform was formerly known as the "General Solicitation Platform". We've rebranded to "Customer Engagement & Action Platform (CEAP)" to better reflect its capabilities beyond solicitation. This is a documentation update only—package names and code remain unchanged.

## Task Context

**Current Task**: Task 2 - Implement core data models
**Focus**: Create Kotlin data classes for Candidate, Context, Subject, Score, and configuration models

## Overview

Task 2 implements the foundational data models that represent solicitation candidates and program configurations. These models serve as the canonical representation used throughout the platform.

### Design Principles for Task 2

1. **Immutability**: Use Kotlin data classes for immutable objects by default
2. **Validation**: Validate all required fields at construction time
3. **Extensibility**: Support arbitrary context dimensions and score types
4. **Serialization**: Ensure proper JSON serialization for storage and API responses
5. **Type Safety**: Leverage Kotlin's null safety and strong typing
6. **Conciseness**: Use Kotlin's concise syntax to reduce boilerplate

## Data Models

### Candidate Model

The Candidate model is the core data structure representing a solicitation opportunity.

```kotlin
package com.solicitation.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.Valid
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull
import java.time.Instant

/**
 * Represents a solicitation candidate - a potential opportunity to solicit
 * customer response for a specific subject.
 *
 * @property customerId Unique identifier for the customer
 * @property context Multi-dimensional context describing the solicitation scenario
 * @property subject The subject being solicited for response
 * @property scores Scores from various ML models (key: modelId, value: Score)
 * @property attributes Attributes describing the solicitation opportunity
 * @property metadata System-level metadata for tracking
 * @property rejectionHistory History of rejections (if any)
 */
data class Candidate(
    @field:NotNull
    @JsonProperty("customerId")
    val customerId: String,
    
    @field:NotEmpty
    @field:Valid
    @JsonProperty("context")
    val context: List<Context>,
    
    @field:NotNull
    @field:Valid
    @JsonProperty("subject")
    val subject: Subject,
    
    @JsonProperty("scores")
    val scores: Map<String, Score>? = null,
    
    @field:NotNull
    @field:Valid
    @JsonProperty("attributes")
    val attributes: CandidateAttributes,
    
    @field:NotNull
    @field:Valid
    @JsonProperty("metadata")
    val metadata: CandidateMetadata,
    
    @JsonProperty("rejectionHistory")
    val rejectionHistory: List<RejectionRecord>? = null
)
    /**
     * Unique identifier for the customer
     */
    @NotNull
    @JsonProperty("customerId")
    String customerId;
    
    /**
     * Multi-dimensional context describing the solicitation scenario
     * Must contain at least one context (e.g., marketplace, program, vertical)
     */
    @NotEmpty
    @Valid
    @JsonProperty("context")
    List<Context> context;
    
    /**
     * The subject being solicited for response
     */
    @NotNull
    @Valid
    @JsonProperty("subject")
    Subject subject;
    
    /**
     * Scores from various ML models
     * Key: modelId, Value: Score object
     */
    @JsonProperty("scores")
    Map<String, Score> scores;
    
    /**
     * Attributes describing the solicitation opportunity
     */
    @NotNull
    @Valid
    @JsonProperty("attributes")
    CandidateAttributes attributes;
    
    /**
     * System-level metadata for tracking
     */
    @NotNull
    @Valid
    @JsonProperty("metadata")
    CandidateMetadata metadata;
    
    /**
     * History of rejections (if any)
     */
    @JsonProperty("rejectionHistory")
    List<RejectionRecord> rejectionHistory;
    
    @JsonPOJOBuilder(withPrefix = "")
    public static class CandidateBuilder {
    }
}
```

### Context Model

```kotlin
package com.solicitation.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank

/**
 * Represents a dimension of context for a candidate.
 * Examples: marketplace, program, vertical
 *
 * @property type Type of context (e.g., "marketplace", "program", "vertical")
 * @property id Identifier for this context dimension
 */
data class Context(
    @field:NotBlank
    @JsonProperty("type")
    val type: String,
    
    @field:NotBlank
    @JsonProperty("id")
    val id: String
)
```

### Subject Model

```kotlin
package com.solicitation.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank

/**
 * Represents the subject being solicited for response.
 * Examples: product, video, music track, service, event
 *
 * @property type Type of subject (e.g., "product", "video", "track", "service")
 * @property id Unique identifier for the subject
 * @property metadata Optional metadata about the subject
 */
data class Subject(
    @field:NotBlank
    @JsonProperty("type")
    val type: String,
    
    @field:NotBlank
    @JsonProperty("id")
    val id: String,
    
    @JsonProperty("metadata")
    val metadata: Map<String, Any>? = null
)
```

### Score Model

```kotlin
package com.solicitation.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import java.time.Instant

/**
 * Represents a score from an ML model.
 *
 * @property modelId Identifier of the model that produced this score
 * @property value Score value (typically 0.0 to 1.0)
 * @property confidence Confidence in the score (0.0 to 1.0)
 * @property timestamp When the score was computed
 * @property metadata Optional metadata about the scoring
 */
data class Score(
    @field:NotBlank
    @JsonProperty("modelId")
    val modelId: String,
    
    @field:NotNull
    @JsonProperty("value")
    val value: Double,
    
    @field:Min(0)
    @field:Max(1)
    @JsonProperty("confidence")
    val confidence: Double? = null,
    
    @field:NotNull
    @JsonProperty("timestamp")
    val timestamp: Instant,
    
    @JsonProperty("metadata")
    val metadata: Map<String, Any>? = null
)
```

### CandidateAttributes Model

```kotlin
package com.solicitation.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotNull
import java.time.Instant

/**
 * Attributes describing the solicitation opportunity.
 *
 * @property eventDate When the triggering event occurred
 * @property deliveryDate Preferred delivery date (optional)
 * @property timingWindow Timing window for solicitation (optional)
 * @property orderValue Order value if applicable (optional)
 * @property mediaEligible Whether media (images/video) is eligible
 * @property channelEligibility Channel eligibility flags
 */
data class CandidateAttributes(
    @field:NotNull
    @JsonProperty("eventDate")
    val eventDate: Instant,
    
    @JsonProperty("deliveryDate")
    val deliveryDate: Instant? = null,
    
    @JsonProperty("timingWindow")
    val timingWindow: String? = null,
    
    @JsonProperty("orderValue")
    val orderValue: Double? = null,
    
    @JsonProperty("mediaEligible")
    val mediaEligible: Boolean? = null,
    
    @field:NotNull
    @JsonProperty("channelEligibility")
    val channelEligibility: Map<String, Boolean>
)
```

### CandidateMetadata Model

```kotlin
package com.solicitation.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive
import java.time.Instant

/**
 * System-level metadata for candidate tracking.
 *
 * @property createdAt When the candidate was created
 * @property updatedAt When the candidate was last updated
 * @property expiresAt When the candidate expires (for TTL)
 * @property version Version number for optimistic locking
 * @property sourceConnectorId ID of the data connector that created this candidate
 * @property workflowExecutionId Workflow execution ID for traceability
 */
data class CandidateMetadata(
    @field:NotNull
    @JsonProperty("createdAt")
    val createdAt: Instant,
    
    @field:NotNull
    @JsonProperty("updatedAt")
    val updatedAt: Instant,
    
    @field:NotNull
    @JsonProperty("expiresAt")
    val expiresAt: Instant,
    
    @field:Positive
    @JsonProperty("version")
    val version: Long,
    
    @field:NotBlank
    @JsonProperty("sourceConnectorId")
    val sourceConnectorId: String,
    
    @field:NotBlank
    @JsonProperty("workflowExecutionId")
    val workflowExecutionId: String
)
```

### RejectionRecord Model

```kotlin
package com.solicitation.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import java.time.Instant

/**
 * Records why a candidate was rejected by a filter.
 *
 * @property filterId ID of the filter that rejected the candidate
 * @property reason Human-readable rejection reason
 * @property reasonCode Machine-readable reason code
 * @property timestamp When the rejection occurred
 */
data class RejectionRecord(
    @field:NotBlank
    @JsonProperty("filterId")
    val filterId: String,
    
    @field:NotBlank
    @JsonProperty("reason")
    val reason: String,
    
    @field:NotBlank
    @JsonProperty("reasonCode")
    val reasonCode: String,
    
    @field:NotNull
    @JsonProperty("timestamp")
    val timestamp: Instant
)
```

## Configuration Models

### ProgramConfig Model

```kotlin
package com.solicitation.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive

/**
 * Configuration for a solicitation program.
 */
data class ProgramConfig(
    @field:NotBlank
    @JsonProperty("programId")
    val programId: String,
    
    @field:NotBlank
    @JsonProperty("programName")
    val programName: String,
    
    @field:NotNull
    @JsonProperty("enabled")
    val enabled: Boolean,
    
    @field:NotEmpty
    @JsonProperty("marketplaces")
    val marketplaces: List<String>,
    
    @field:NotEmpty
    @field:Valid
    @JsonProperty("dataConnectors")
    val dataConnectors: List<DataConnectorConfig>,
    
    @field:NotEmpty
    @field:Valid
    @JsonProperty("scoringModels")
    val scoringModels: List<ScoringModelConfig>,
    
    @field:NotNull
    @field:Valid
    @JsonProperty("filterChain")
    val filterChain: FilterChainConfig,
    
    @field:NotEmpty
    @field:Valid
    @JsonProperty("channels")
    val channels: List<ChannelConfig>,
    
    @JsonProperty("batchSchedule")
    val batchSchedule: String? = null,
    
    @field:NotNull
    @JsonProperty("reactiveEnabled")
    val reactiveEnabled: Boolean,
    
    @field:Positive
    @JsonProperty("candidateTTLDays")
    val candidateTTLDays: Int,
    
    @JsonProperty("timingWindowDays")
    val timingWindowDays: Int? = null
)
```

### FilterConfig Model

```kotlin
package com.solicitation.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.PositiveOrZero

/**
 * Configuration for a single filter.
 */
data class FilterConfig(
    @field:NotBlank
    @JsonProperty("filterId")
    val filterId: String,
    
    @field:NotBlank
    @JsonProperty("filterType")
    val filterType: String,
    
    @field:NotNull
    @JsonProperty("enabled")
    val enabled: Boolean,
    
    @JsonProperty("parameters")
    val parameters: Map<String, Any>? = null,
    
    @field:PositiveOrZero
    @JsonProperty("order")
    val order: Int
)
```

### ChannelConfig Model

```kotlin
package com.solicitation.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

/**
 * Configuration for a delivery channel.
 */
data class ChannelConfig(
    @field:NotBlank
    @JsonProperty("channelId")
    val channelId: String,
    
    @field:NotBlank
    @JsonProperty("channelType")
    val channelType: String,
    
    @field:NotNull
    @JsonProperty("enabled")
    val enabled: Boolean,
    
    @field:NotNull
    @JsonProperty("shadowMode")
    val shadowMode: Boolean,
    
    @JsonProperty("config")
    val config: Map<String, Any>? = null
)
```

## Validation Strategy

### Field Validation

Use Bean Validation (JSR 380) annotations with Kotlin:
- `@field:NotNull`: Field must not be null
- `@field:NotBlank`: String must not be null or empty
- `@field:NotEmpty`: Collection must not be null or empty
- `@field:Valid`: Cascade validation to nested objects
- `@field:Positive`: Number must be positive
- `@field:Min/@Max`: Number range validation

**Note**: In Kotlin, use `@field:` prefix for validation annotations to apply them to the backing field rather than the property getter.
- `@Min/@Max`: Number range validation

### Custom Validation

For complex validation logic, implement custom validators in Kotlin:

```kotlin
package com.solicitation.model.validation

import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext

class ContextValidator : ConstraintValidator<ValidContext, List<Context>> {
    
    override fun isValid(
        contexts: List<Context>?,
        context: ConstraintValidatorContext
    ): Boolean {
        if (contexts.isNullOrEmpty()) {
            return false
        }
        
        // Ensure at least one context is present
        // Validate context types are known
        // Check for duplicate context types
        
        return true
    }
}
```

## Testing Strategy

### Unit Tests

Test each model class:
- Valid construction with all required fields
- Validation failures for missing/invalid fields
- JSON serialization/deserialization
- Builder pattern functionality
- Immutability (where applicable)

### Property-Based Tests

Implement property tests for:

**Property 2: Candidate model completeness**
- Generate random candidates
- Verify all required fields are present
- Verify validation catches missing fields

**Property 3: Context extensibility**
- Generate random context types and IDs
- Verify they can be stored without data loss
- Verify JSON round-trip preserves values

**Property 4: Version monotonicity**
- Generate candidate with version N
- Update to version N+1
- Verify version increases and timestamp updates

**Property 30: Program configuration validation**
- Generate random program configs
- Verify validation catches missing required fields
- Verify valid configs pass validation

## Implementation Checklist

### Task 2.1: Create Candidate model with all fields
- [ ] Create Candidate data class with all fields
- [ ] Create Context data class
- [ ] Create Subject data class
- [ ] Create Score data class
- [ ] Create CandidateAttributes data class
- [ ] Create CandidateMetadata data class
- [ ] Create RejectionRecord data class
- [ ] Add Jackson annotations for JSON serialization
- [ ] Add validation annotations (with @field: prefix)
- [ ] Add KDoc documentation

### Task 2.2: Write property test for candidate model completeness
- [ ] Set up property testing framework (jqwik)
- [ ] Create arbitrary generators for all model classes
- [ ] Implement Property 2 test
- [ ] Verify test catches validation failures
- [ ] Run test with 100+ iterations

### Task 2.3: Write property test for context extensibility
- [ ] Create arbitrary generators for Context
- [ ] Implement Property 3 test
- [ ] Verify JSON round-trip preserves data
- [ ] Run test with 100+ iterations

### Task 2.4: Create configuration models
- [ ] Create ProgramConfig data class
- [ ] Create FilterConfig data class
- [ ] Create ChannelConfig data class
- [ ] Create DataConnectorConfig data class
- [ ] Create ScoringModelConfig data class
- [ ] Create FilterChainConfig data class
- [ ] Add validation logic
- [ ] Add KDoc documentation

### Task 2.5: Write property test for program configuration validation
- [ ] Create arbitrary generators for config classes
- [ ] Implement Property 30 test
- [ ] Verify validation catches missing fields
- [ ] Run test with 100+ iterations

## Dependencies

```kotlin
// solicitation-models/build.gradle.kts additions for Task 2
dependencies {
    // Internal dependencies
    implementation(project(":solicitation-common"))

    // JSON Processing - Jackson with Kotlin support
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")

    // Kotlin Serialization (alternative to Jackson)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // Bean Validation
    implementation("javax.validation:validation-api:2.0.1.Final")
    implementation("org.hibernate.validator:hibernate-validator:7.0.5.Final")
    implementation("org.glassfish:jakarta.el:4.0.2") // Required for Hibernate Validator
    
    // Kotlin standard library and reflection (included via root build.gradle.kts)
    // Property-based testing (jqwik) is included in root build.gradle.kts
}
```

## Next Steps

After Task 2 completion:
- Task 3 will implement DynamoDB storage layer using these models
- Task 5 will implement data connectors that create these models
- Task 6 will implement scoring engine that populates score fields
