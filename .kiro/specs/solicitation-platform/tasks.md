# Implementation Tasks - Current Cycle

## Current Focus: Task 2 - Implement Core Data Models

This task list shows the current 2-task implementation cycle. After completing these tasks, the next cycle will be loaded from FOUNDATION.

## Task Status Legend
- `[ ]` - Not started
- `[~]` - In progress  
- `[x]` - Complete
- `[*]` - Property-based test task

---

## Completed Tasks

### Task 1: Set up project structure and core infrastructure ✅
- ✅ Created Gradle multi-module project with Kotlin 1.9.21
- ✅ Migrated from Maven to Gradle 8.5 with Kotlin DSL
- ✅ Migrated from Java to Kotlin for all modules
- ✅ Set up AWS CDK (Kotlin) for infrastructure as code
- ✅ Created DynamoDB table definitions (CDK)
- ✅ Created Lambda function stacks (CDK)
- ✅ Created reusable SolicitationLambda construct
- ✅ Configured AWS Lambda runtime (Java 17)
- ✅ Set up deployment pipeline (deploy-cdk.sh)
- ✅ Set up logging framework (SLF4J + Logback + kotlin-logging)
- ✅ Configured 13 modules: 8 libraries + 5 Lambda workflows
- **Status**: COMPLETE
- **Technology Stack**: Kotlin 1.9.21, Gradle 8.5, AWS CDK 2.167.1
- **Architecture**: Multi-module with plug-and-play CDK infrastructure

---

## Current Task Cycle

### Task 2: Implement core data models

Create the unified candidate model and configuration models that serve as the canonical representation throughout the platform.

#### Subtasks:

- [x] 2.1 Create Candidate model with all fields
  - Implement Context, Subject, Score, CandidateAttributes, CandidateMetadata data classes
  - Add JSON serialization/deserialization annotations
  - Implement validation for required fields
  - _Requirements: 2.1, 2.2, 2.3, 2.4_
  - _Files to create_:
    - `solicitation-models/src/main/kotlin/com/solicitation/model/Candidate.kt`
    - `solicitation-models/src/main/kotlin/com/solicitation/model/Context.kt`
    - `solicitation-models/src/main/kotlin/com/solicitation/model/Subject.kt`
    - `solicitation-models/src/main/kotlin/com/solicitation/model/Score.kt`
    - `solicitation-models/src/main/kotlin/com/solicitation/model/CandidateAttributes.kt`
    - `solicitation-models/src/main/kotlin/com/solicitation/model/CandidateMetadata.kt`
    - `solicitation-models/src/main/kotlin/com/solicitation/model/RejectionRecord.kt`

- [x]* 2.2 Write property test for candidate model completeness
  - **Property 2: Candidate model completeness**
  - **Validates: Requirements 2.1, 2.2**
  - Verify all required fields are present in any candidate
  - Verify validation catches missing fields
  - _Files to create_:
    - `solicitation-models/src/test/kotlin/com/solicitation/model/CandidatePropertyTest.kt`
    - `solicitation-models/src/test/kotlin/com/solicitation/model/arbitraries/CandidateArbitraries.kt`

- [x]* 2.3 Write property test for context extensibility
  - **Property 3: Context extensibility**
  - **Validates: Requirements 1.3, 2.3**
  - Verify any valid context type/id can be stored without data loss
  - Verify JSON round-trip preserves context values
  - _Files to create_:
    - `solicitation-models/src/test/kotlin/com/solicitation/model/ContextPropertyTest.kt`

- [x] 2.4 Create configuration models (ProgramConfig, FilterConfig, ChannelConfig)
  - Implement program registry data structures
  - Add validation logic for configuration fields
  - _Requirements: 10.1, 10.2_
  - _Files to create_:
    - `solicitation-models/src/main/kotlin/com/solicitation/model/config/ProgramConfig.kt`
    - `solicitation-models/src/main/kotlin/com/solicitation/model/config/FilterConfig.kt`
    - `solicitation-models/src/main/kotlin/com/solicitation/model/config/ChannelConfig.kt`
    - `solicitation-models/src/main/kotlin/com/solicitation/model/config/DataConnectorConfig.kt`
    - `solicitation-models/src/main/kotlin/com/solicitation/model/config/ScoringModelConfig.kt`
    - `solicitation-models/src/main/kotlin/com/solicitation/model/config/FilterChainConfig.kt`

- [x]* 2.5 Write property test for program configuration validation
  - **Property 30: Program configuration validation**
  - **Validates: Requirements 10.1**
  - Verify all required fields must be present
  - Verify validation rejects invalid configurations
  - _Files to create_:
    - `solicitation-models/src/test/kotlin/com/solicitation/model/config/ProgramConfigPropertyTest.kt`
    - `solicitation-models/src/test/kotlin/com/solicitation/model/arbitraries/ConfigArbitraries.kt`

---

## Testing Requirements

### Property-Based Testing
- Use **jqwik** framework for Kotlin property-based tests
- Minimum **100 iterations** per property test
- Each property test must reference its design document property number
- Tag format: `@Property` annotation with comment `// Property {number}: {description}`

### Test Organization
- Unit tests in `src/test/kotlin` matching source package structure
- Property tests in same location with `PropertyTest` suffix
- Arbitrary generators in `arbitraries` subpackage for reuse

### Validation Testing
- Test valid object construction
- Test validation failures for missing/invalid fields
- Test JSON serialization/deserialization round-trip
- Test data class copy functionality

---

## Success Criteria

Task 2 is complete when:
1. ✅ All model classes are implemented with proper annotations
2. ✅ JSON serialization/deserialization works correctly
3. ✅ Field validation catches missing/invalid data
4. ✅ All property tests pass with 100+ iterations
5. ✅ Unit tests cover edge cases and error conditions
6. ✅ Code follows Java best practices and is well-documented
7. ✅ Gradle build succeeds with no warnings (`./gradlew build`)

---

## Next Cycle Preview

After Task 2 completion, the next cycle will focus on:
- **Task 3**: Implement DynamoDB storage layer using these models
- **Task 4**: Checkpoint - Ensure storage layer tests pass

---

## Notes

- Property tests marked with `*` are required for correctness validation
- Each task references specific requirements for traceability
- Use the design document for detailed implementation guidance
- Refer to FOUNDATION/tasks.md for the complete task list
