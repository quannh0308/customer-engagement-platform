# Implementation Tasks - Current Cycle

## Current Focus: Task 3 & 4 - DynamoDB Storage Layer

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

### Task 2: Implement core data models ✅
- ✅ Created 7 Kotlin data classes for Candidate model (Task 2.1)
  - Candidate, Context, Subject, Score, CandidateAttributes, CandidateMetadata, RejectionRecord
  - Added Jackson annotations for JSON serialization
  - Implemented Bean Validation (JSR 380) with @field: prefix
  - Created unit tests (4 tests passing)
- ✅ Created 6 configuration model classes (Task 2.4)
  - ProgramConfig, FilterConfig, FilterChainConfig, ChannelConfig, DataConnectorConfig, ScoringModelConfig
  - Added validation logic for all required fields
  - Created unit tests (8 tests passing)
- ✅ Implemented property-based tests using jqwik (Tasks 2.2, 2.3, 2.5)
  - CandidatePropertyTest: 7 properties (700 test cases)
  - ContextPropertyTest: 6 properties (600 test cases)
  - ProgramConfigPropertyTest: 12 properties (1,200 test cases)
- **Status**: COMPLETE
- **Test Results**: All 37 tests passing (12 unit + 25 property tests)
- **Validates**: Requirements 1.3, 2.1, 2.2, 2.3, 2.4, 2.5, 10.1, 10.2

---

## Current Task Cycle

- [x] Task 3: Implement DynamoDB storage layer
- [-] Task 4: Complete cycle - Commit, push, and setup next tasks

---

## Task 3 Details: Implement DynamoDB storage layer

Implement the persistence layer for candidates using DynamoDB with support for CRUD operations, batch writes, queries, and optimistic locking.

### Subtasks:

- [ ] 3.1 Create DynamoDB repository interface and implementation
  - Implement CRUD operations (create, read, update, delete)
  - Add batch write support with DynamoDB batch limits (25 items)
  - Implement query operations using primary key and GSIs
  - Add optimistic locking using version numbers
  - _Requirements: 5.1, 5.2, 5.3, 5.5_
  - _Files to create_:
    - `solicitation-storage/src/main/kotlin/com/solicitation/storage/CandidateRepository.kt`
    - `solicitation-storage/src/main/kotlin/com/solicitation/storage/DynamoDBCandidateRepository.kt`
    - `solicitation-storage/src/main/kotlin/com/solicitation/storage/DynamoDBConfig.kt`

- [ ]* 3.2 Write property test for storage round-trip consistency
  - **Property 12: Storage round-trip consistency**
  - **Validates: Requirements 5.2, 2.1**
  - Verify any candidate can be stored and retrieved without data loss
  - Verify all fields are preserved through storage round-trip
  - _Files to create_:
    - `solicitation-storage/src/test/kotlin/com/solicitation/storage/StorageRoundTripPropertyTest.kt`

- [ ]* 3.3 Write property test for query filtering correctness
  - **Property 13: Query filtering correctness**
  - **Validates: Requirements 5.3, 6.2**
  - Verify query results match filter criteria
  - Verify no false positives or false negatives
  - _Files to create_:
    - `solicitation-storage/src/test/kotlin/com/solicitation/storage/QueryFilteringPropertyTest.kt`

- [ ]* 3.4 Write property test for optimistic locking
  - **Property 14: Optimistic locking conflict detection**
  - **Validates: Requirements 5.5**
  - Verify concurrent updates are detected and rejected
  - Verify version numbers prevent lost updates
  - _Files to create_:
    - `solicitation-storage/src/test/kotlin/com/solicitation/storage/OptimisticLockingPropertyTest.kt`

- [ ]* 3.5 Write property test for batch write atomicity
  - **Property 15: Batch write atomicity**
  - **Validates: Requirements 5.2**
  - Verify batch writes handle partial failures correctly
  - Verify retry logic for failed items
  - _Files to create_:
    - `solicitation-storage/src/test/kotlin/com/solicitation/storage/BatchWritePropertyTest.kt`

- [ ] 3.6 Implement TTL configuration logic
  - Add TTL calculation based on program configuration
  - Set TTL attribute on candidate creation
  - _Requirements: 17.1_
  - _Files to create_:
    - `solicitation-storage/src/main/kotlin/com/solicitation/storage/TTLCalculator.kt`

- [ ]* 3.7 Write property test for TTL configuration
  - **Property 51: TTL configuration**
  - **Validates: Requirements 17.1**
  - Verify TTL is calculated correctly based on program config
  - Verify TTL attribute is set on all candidates
  - _Files to create_:
    - `solicitation-storage/src/test/kotlin/com/solicitation/storage/TTLConfigurationPropertyTest.kt`

---

## Task 4 Details: Complete cycle - Commit, push, and setup next tasks

After Task 3 is complete, commit the changes, push to git, and prepare tasks.md for the next cycle.

### Subtasks:

- [ ] 4.1 Verify all storage layer tests pass
  - Run `./gradlew :solicitation-storage:test`
  - Ensure all tests pass with no errors
  - Verify build succeeds with no warnings

- [ ] 4.2 Commit and push changes
  - Stage all changes with `git add -A`
  - Create descriptive commit message for Task 3 completion
  - Push to origin/main

- [ ] 4.3 Setup next task cycle in tasks.md
  - Read FOUNDATION/tasks.md to identify next tasks (Tasks 5 & 6)
  - Update tasks.md with Task 5 and new Task 6 (recurring cycle)
  - Move completed Task 3 to completed tasks section
  - Commit and push the updated tasks.md

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

### Storage Testing
- Test CRUD operations with valid and invalid data
- Test batch operations with various batch sizes
- Test query operations with different filter criteria
- Test optimistic locking with concurrent updates
- Test TTL calculation with various program configurations

---

## Success Criteria

Task 3 is complete when:
1. ✅ Repository interface and implementation created
2. ✅ CRUD operations working correctly
3. ✅ Batch write operations handle DynamoDB limits
4. ✅ Query operations return correct results
5. ✅ Optimistic locking prevents lost updates
6. ✅ TTL configuration works correctly
7. ✅ All property tests pass with 100+ iterations
8. ✅ Unit tests cover edge cases and error conditions
9. ✅ Gradle build succeeds with no warnings

Task 4 is complete when:
1. ✅ All storage layer tests pass
2. ✅ No compilation errors or warnings
3. ✅ Implementation ready for integration with other layers

---

## Next Cycle Preview

After Task 3 & 4 completion, the next cycle will focus on:
- **Task 5**: Implement data connector framework
- **Task 6**: Implement scoring engine layer

---

## Notes

- Property tests marked with `*` are required for correctness validation
- Each task references specific requirements for traceability
- Use the design document for detailed implementation guidance
- Refer to FOUNDATION/tasks.md for the complete task list
- DynamoDB local can be used for testing without AWS credentials
