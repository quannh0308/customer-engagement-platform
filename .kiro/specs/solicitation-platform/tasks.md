# Implementation Tasks - Current Cycle

## Current Focus: Task 13 - Implement batch ingestion workflow

This task list shows the current 2-task implementation cycle. After completing these tasks, the next cycle will be loaded from FOUNDATION.

**Note**: Completed tasks are tracked in `completed-tasks.md` to keep this file focused on current work.

## Task Status Legend
- `[ ]` - Not started
- `[~]` - In progress  
- `[x]` - Complete
- `[*]` - Property-based test task

---

## Current Task Cycle

- [ ] Task 13: Implement batch ingestion workflow
- [ ] Complete cycle - Commit, push, and setup next tasks

---

## Task 13 Details: Implement batch ingestion workflow

Create Step Functions workflow for batch processing of candidates through ETL, filtering, scoring, and storage stages.

### Subtasks:

- [ ] 13.1 Create Step Functions workflow definition
  - Define workflow states (ETL, Filter, Score, Store)
  - Add error handling and retry logic
  - Configure parallel execution where applicable
  - _Validates: Requirements 8.1, 8.3_

- [ ] 13.2 Implement ETL Lambda function
  - Use data connector to extract and transform data
  - Batch candidates for downstream processing
  - _Validates: Requirements 1.2, 8.1_

- [ ] 13.3 Implement filter Lambda function
  - Execute filter chain on candidate batches
  - Track rejection reasons
  - _Validates: Requirements 4.1, 4.2, 8.1_

- [ ] 13.4 Implement scoring Lambda function
  - Execute scoring for candidate batches
  - Handle scoring failures with fallbacks
  - _Validates: Requirements 3.2, 3.3, 8.1_

- [ ] 13.5 Implement storage Lambda function
  - Batch write candidates to DynamoDB
  - Handle write failures and retries
  - _Validates: Requirements 5.2, 8.1_

- [ ] 13.6 Add workflow metrics publishing
  - Publish metrics at each workflow stage
  - Track processed, passed, rejected counts
  - _Validates: Requirements 8.4, 12.1_

- [ ]* 13.7 Write property test for workflow metrics publishing
  - **Property 26: Workflow metrics publishing**
  - **Validates: Requirements 8.4, 12.1**

- [ ] 13.8 Implement retry with exponential backoff
  - Configure Step Functions retry policy
  - Add exponential backoff delays
  - _Validates: Requirements 8.3_

- [ ]* 13.9 Write property test for workflow retry with exponential backoff
  - **Property 25: Workflow retry with exponential backoff**
  - **Validates: Requirements 8.3**

- [ ] 13.10 Add workflow completion triggers
  - Publish completion metrics
  - Trigger downstream processes (data warehouse export)
  - _Validates: Requirements 8.6_

- [ ]* 13.11 Write property test for workflow completion triggers
  - **Property 27: Workflow completion triggers**
  - **Validates: Requirements 8.6**

---

## Complete Cycle: Commit, Push, and Setup Next Tasks

After Task 13 completion, commit any fixes, push to git, and prepare tasks.md for the next cycle.

**IMPORTANT**: When setting up the next cycle, ALL tasks in the new tasks.md must be marked as `[ ]` not started. This is a fresh cycle start.

### Subtasks:

- [ ] Commit and push any fixes
  - Stage all changes with `git add -A`
  - Create descriptive commit message if fixes were needed
  - Push to origin/main

- [x] Setup next task cycle in tasks.md
  - Read FOUNDATION/tasks.md to identify next tasks (Task 14 from FOUNDATION)
  - Move completed Task 13 to completed-tasks.md with full details
  - Update tasks.md with Task 14 as the new main task
  - **CRITICAL**: Ensure ALL tasks in tasks.md are marked as `[ ]` not started (including Task 14 AND "Complete cycle" task)
  - Update the "Complete cycle" subtask to reference Task 15 for the next iteration
  - Commit and push the updated files

---

## Next Cycle Preview

After Task 13 & cycle completion, the next cycle will focus on:
- **Task 14**: Implement reactive solicitation workflow (from FOUNDATION)
- **Complete cycle**: Commit, push, and setup next tasks

---

## Notes

- Property tests marked with `*` are required for correctness validation
- Each task references specific requirements for traceability
- Use the design document for detailed implementation guidance
- Refer to FOUNDATION/tasks.md for the complete task list
- Refer to completed-tasks.md for history of completed work
- DynamoDB local can be used for testing without AWS credentials

