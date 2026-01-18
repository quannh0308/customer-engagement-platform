# Requirements Document - Task 2: Implement Core Data Models

> **Platform Rebranding Note**: This platform was formerly known as the "General Solicitation Platform". We've rebranded to "Customer Engagement & Action Platform (CEAP)" to better reflect its capabilities beyond solicitation. This is a documentation update only—package names and code remain unchanged.

## Task Context

**Current Task**: Task 2 - Implement core data models
**Focus**: Create the unified candidate model and configuration models with proper validation

## Glossary (Task 2 Relevant Terms)

- **Candidate**: A potential solicitation opportunity (customer-subject pair with context)
- **Subject**: The item being solicited for response (product, video, music track, service, event, experience, content piece)
- **Context**: Multi-dimensional metadata describing the solicitation scenario (marketplace, program, vertical)
- **Program**: An independent solicitation configuration with specific rules, channels, and data sources
- **Score**: Evaluation of candidate quality with value, confidence, and timestamp
- **CandidateAttributes**: Properties describing the solicitation opportunity (event date, delivery date, channel eligibility)
- **CandidateMetadata**: System-level tracking information (timestamps, version, source)

## Requirements for Task 2

### Requirement 2: Unified Candidate Model

**User Story:** As a system architect, I want a single canonical representation for all solicitation candidates, so that downstream components work consistently across programs.

#### Acceptance Criteria

1. THE Candidate_Storage SHALL store candidates with context array, subject, customer, event metadata, scores, and attributes
2. WHEN a candidate is created, THE system SHALL validate all required fields are present
3. THE candidate model SHALL support arbitrary score types with value, confidence, and timestamp
4. THE candidate model SHALL include channel eligibility flags per supported channel
5. WHEN a candidate is updated, THE system SHALL increment the version number and update the timestamp

### Requirement 1.3: Context Extensibility

**From Requirement 1: Data Ingestion Framework**

#### Acceptance Criteria

3. THE unified candidate model SHALL support extensible context dimensions including marketplace, program, and vertical

### Requirement 10: Program Configuration Management

**User Story:** As a program administrator, I want to configure solicitation programs independently, so that I can customize behavior per business vertical.

#### Acceptance Criteria

1. WHEN a program is created, THE system SHALL validate all required configuration fields
2. THE program configuration SHALL specify data sources, filter chains, scoring models, channels, and schedules
3. WHEN a program is disabled, THE system SHALL stop all workflows and prevent new candidate creation
4. THE system SHALL support per-marketplace program configuration overrides
5. WHEN configuration changes, THE system SHALL apply them without requiring system restart

## Correctness Properties for Task 2

### Property 2: Candidate model completeness

*For any* candidate stored in the system, it must contain all required fields: customerId, context array (with at least one context), subject, metadata with timestamps, and version number.

**Validates: Requirements 2.1, 2.2**

### Property 3: Context extensibility

*For any* valid context type and id combination, the candidate model must be able to store it in the context array without data loss.

**Validates: Requirements 1.3, 2.3**

### Property 4: Version monotonicity

*For any* candidate, if it is updated, the new version number must be strictly greater than the previous version number, and the updatedAt timestamp must be greater than or equal to the previous updatedAt timestamp.

**Validates: Requirements 2.5**

### Property 30: Program configuration validation

*For any* program configuration, all required fields (programId, dataConnectors, filterChain, channels) must be present and valid, or the configuration must be rejected with detailed error messages.

**Validates: Requirements 10.1**

## Success Criteria for Task 2

1. ✅ Candidate model class created with all required fields
2. ✅ Context, Subject, Score classes implemented
3. ✅ CandidateAttributes and CandidateMetadata classes implemented
4. ✅ JSON serialization/deserialization working correctly
5. ✅ Field validation logic implemented
6. ✅ ProgramConfig, FilterConfig, ChannelConfig classes created
7. ✅ Configuration validation logic implemented
8. ✅ Property tests pass for candidate completeness, context extensibility, and version monotonicity
9. ✅ Property test passes for program configuration validation

## Implementation Notes

- Use Kotlin data classes for immutable models
- Add Jackson annotations for JSON serialization (jackson-module-kotlin)
- Use Bean Validation (JSR 380) for field validation with @field: prefix
- Ensure immutability with val properties
- Add comprehensive KDoc documentation
- Project uses Gradle build system with Kotlin DSL (build.gradle.kts files)
- Kotlin 1.9.21 with JVM target 17

## Next Steps

After Task 2 completion, Task 3 will implement the DynamoDB storage layer using these data models.
