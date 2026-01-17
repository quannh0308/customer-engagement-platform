# Implementation Tasks - Current Cycle

## Current Focus: Task 6 - Scoring Engine Layer

This task list shows the current 2-task implementation cycle. After completing these tasks, the next cycle will be loaded from FOUNDATION.

**Note**: Completed tasks are tracked in `completed-tasks.md` to keep this file focused on current work.

## Task Status Legend
- `[ ]` - Not started
- `[~]` - In progress  
- `[x]` - Complete
- `[*]` - Property-based test task

---

## Current Task Cycle

- [x] Task 6: Implement scoring engine layer
- [-] Complete cycle - Commit, push, and setup next tasks

---

## Task 6 Details: Implement scoring engine layer

Implement the framework for scoring candidates using ML models with caching, feature retrieval, and fallback support.

### Subtasks:

- [ ] 6.1 Create ScoringProvider interface
  - Define interface methods (getModelId, scoreCandidate, scoreBatch, healthCheck)
  - Add fallback score support
  - _Requirements: 3.1, 3.4_
  - _Files to create_:
    - `solicitation-scoring/src/main/kotlin/com/solicitation/scoring/ScoringProvider.kt`
    - `solicitation-scoring/src/main/kotlin/com/solicitation/scoring/BaseScoringProvider.kt`

- [ ] 6.2 Implement score caching in DynamoDB
  - Create score cache table schema
  - Implement cache read/write with TTL
  - Add cache invalidation logic
  - _Requirements: 3.5_
  - _Files to create_:
    - `solicitation-scoring/src/main/kotlin/com/solicitation/scoring/ScoreCache.kt`
    - `solicitation-scoring/src/main/kotlin/com/solicitation/scoring/ScoreCacheRepository.kt`

- [ ]* 6.3 Write property test for score caching consistency
  - **Property 6: Score caching consistency**
  - **Validates: Requirements 3.5**
  - Verify cached scores match computed scores
  - Verify cache TTL is respected
  - _Files to create_:
    - `solicitation-scoring/src/test/kotlin/com/solicitation/scoring/ScoreCachingPropertyTest.kt`

- [ ] 6.4 Implement feature store integration
  - Create feature retrieval client
  - Add feature validation against required features
  - _Requirements: 3.2_
  - _Files to create_:
    - `solicitation-scoring/src/main/kotlin/com/solicitation/scoring/FeatureStoreClient.kt`
    - `solicitation-scoring/src/main/kotlin/com/solicitation/scoring/FeatureValidator.kt`

- [ ]* 6.5 Write property test for feature retrieval completeness
  - **Property 8: Feature retrieval completeness**
  - **Validates: Requirements 3.2**
  - Verify all required features are retrieved
  - Verify feature validation catches missing features
  - _Files to create_:
    - `solicitation-scoring/src/test/kotlin/com/solicitation/scoring/FeatureRetrievalPropertyTest.kt`

- [ ] 6.6 Implement multi-model scoring support
  - Add logic to execute multiple scoring models per candidate
  - Store scores with modelId, value, confidence, timestamp
  - _Requirements: 3.3_
  - _Files to create_:
    - `solicitation-scoring/src/main/kotlin/com/solicitation/scoring/MultiModelScorer.kt`

- [ ]* 6.7 Write property test for multi-model scoring independence
  - **Property 5: Multi-model scoring independence**
  - **Validates: Requirements 3.3**
  - Verify each model scores independently
  - Verify one model failure doesn't affect others
  - _Files to create_:
    - `solicitation-scoring/src/test/kotlin/com/solicitation/scoring/MultiModelScoringPropertyTest.kt`

- [ ] 6.8 Add scoring fallback logic with circuit breaker
  - Implement circuit breaker pattern for model endpoints
  - Add fallback to cached scores or default values
  - Add failure logging
  - _Requirements: 3.4, 9.3_
  - _Files to create_:
    - `solicitation-scoring/src/main/kotlin/com/solicitation/scoring/CircuitBreaker.kt`
    - `solicitation-scoring/src/main/kotlin/com/solicitation/scoring/ScoringFallback.kt`

- [ ]* 6.9 Write property test for scoring fallback correctness
  - **Property 7: Scoring fallback correctness**
  - **Validates: Requirements 3.4, 9.3**
  - Verify fallback is used when model fails
  - Verify circuit breaker opens after threshold
  - _Files to create_:
    - `solicitation-scoring/src/test/kotlin/com/solicitation/scoring/ScoringFallbackPropertyTest.kt`

---

## Complete Cycle: Commit, Push, and Setup Next Tasks

After Task 6 completion, commit the changes, push to git, and prepare tasks.md for the next cycle.

### Subtasks:

- [x] Verify all scoring engine tests pass
  - Run `./gradlew :solicitation-scoring:test`
  - Ensure all tests pass with no errors
  - Verify build succeeds with no warnings

- [-] Commit and push changes
  - Stage all changes with `git add -A`
  - Create descriptive commit message for Task 6 completion
  - Push to origin/main

- [x] Setup next task cycle in tasks.md
  - Read FOUNDATION/tasks.md to identify next tasks (Task 7 from FOUNDATION)
  - Update tasks.md with Task 7 and new cycle completion task
  - Move completed Task 6 to completed-tasks.md
  - Commit and push the updated files

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

### Scoring Engine Testing
- Test scoring provider interface compliance
- Test score caching with various TTL scenarios
- Test feature retrieval with valid and invalid features
- Test multi-model scoring with independent failures
- Test circuit breaker and fallback behavior
- Test error handling and logging

---

## Success Criteria

Task 6 is complete when:
1. ✅ ScoringProvider interface and base class created
2. ✅ Score caching implemented with DynamoDB
3. ✅ Feature store integration working
4. ✅ Multi-model scoring support implemented
5. ✅ Circuit breaker and fallback logic working
6. ✅ All property tests pass with 100+ iterations
7. ✅ Unit tests cover edge cases and error conditions
8. ✅ Gradle build succeeds with no warnings

Cycle completion is complete when:
1. ✅ All scoring engine tests pass
2. ✅ No compilation errors or warnings
3. ✅ Changes committed and pushed to git
4. ✅ Next task cycle (Task 7 from FOUNDATION) loaded into tasks.md
5. ✅ Completed Task 6 moved to completed-tasks.md

---

## Next Cycle Preview

After Task 6 & cycle completion, the next cycle will focus on:
- **Task 7**: Implement filtering and eligibility pipeline (from FOUNDATION)
- **Complete cycle**: Commit, push, and setup next tasks

---

## Notes

- Property tests marked with `*` are required for correctness validation
- Each task references specific requirements for traceability
- Use the design document for detailed implementation guidance
- Refer to FOUNDATION/tasks.md for the complete task list
- Refer to completed-tasks.md for history of completed work
- DynamoDB local can be used for testing without AWS credentials
