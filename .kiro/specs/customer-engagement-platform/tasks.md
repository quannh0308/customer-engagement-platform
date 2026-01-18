# Implementation Tasks - Current Cycle

> **Platform Rebranding Note**: This platform was formerly known as the "General Solicitation Platform". We've rebranded to "Customer Engagement & Action Platform (CEAP)" to better reflect its capabilities beyond solicitation. This is a documentation update only—package names and code remain unchanged.

## Current Focus: Task 21 - Implement candidate lifecycle management

This task list shows the current 2-task implementation cycle. After completing these tasks, the next cycle will be loaded from FOUNDATION.

**Note**: Completed tasks are tracked in `completed-tasks.md` to keep this file focused on current work.

## Task Status Legend
- `[ ]` - Not started
- `[~]` - In progress  
- `[x]` - Complete
- `[*]` - Property-based test task

---

## Current Task Cycle

- [ ] Task 21: Implement candidate lifecycle management
- [ ] Complete cycle - Commit, push, and setup next tasks

---

## Task 21 Details: Implement candidate lifecycle management

Implement manual deletion, consumed marking, candidate refresh, and data warehouse export.

### Subtasks:

- [ ] 21.1 Add manual candidate deletion API
  - Implement delete endpoint
  - Verify deletion removes candidate from storage
  - _Requirements: 17.3_

- [ ]* 21.2 Write property test for manual deletion
  - **Property 52: Manual deletion**
  - **Validates: Requirements 17.3**

- [ ] 21.3 Implement consumed marking
  - Mark candidates as consumed after delivery
  - Record delivery timestamp
  - _Requirements: 17.4_

- [ ]* 21.4 Write property test for consumed marking
  - **Property 53: Consumed marking**
  - **Validates: Requirements 17.4**

- [ ] 21.5 Add candidate refresh functionality
  - Implement re-scoring for active candidates
  - Implement eligibility refresh
  - Update candidate with current values
  - _Requirements: 17.5_

- [ ]* 21.6 Write property test for candidate refresh
  - **Property 54: Candidate refresh**
  - **Validates: Requirements 17.5**

- [ ] 21.7 Implement data warehouse export
  - Create daily export Lambda function
  - Export candidates to S3 in Parquet format
  - Trigger Glue job to load into data warehouse
  - _Requirements: 5.6_

- [ ]* 21.8 Write property test for data warehouse export completeness
  - **Property 16: Data warehouse export completeness**
  - **Validates: Requirements 5.6**

---

## Complete Cycle: Commit, Push, and Setup Next Tasks

After Task 21 completion, commit any fixes, push to git, and prepare tasks.md for the next cycle.

**IMPORTANT**: When setting up the next cycle, ALL tasks in the new tasks.md must be marked as `[ ]` not started. This is a fresh cycle start.

### Subtasks:

- [ ] Commit and push any fixes
  - Stage all changes with `git add -A`
  - Create descriptive commit message if fixes were needed
  - Push to origin/main

- [ ] Setup next task cycle in tasks.md
  - Read FOUNDATION/tasks.md to identify next tasks (Task 22 from FOUNDATION)
  - Move completed Task 21 to completed-tasks.md with full details
  - Update tasks.md with Task 22 as the new main task
  - **CRITICAL**: Ensure ALL tasks in tasks.md are marked as `[ ]` not started (including Task 22 AND "Complete cycle" task)
  - **CRITICAL**: Ensure tasks in FOUNDATION/tasks.md are updated correctly (mark only the current finished task as done)
  - Update the "Complete cycle" subtask to reference Task 23 for the next iteration
  - Commit and push the updated files

---

## Next Cycle Preview

After Task 21 & cycle completion, the next cycle will focus on:
- **Task 22**: Checkpoint - Ensure lifecycle and security tests pass (from FOUNDATION)
- **Complete cycle**: Commit, push, and setup next tasks

---

## Notes

- Property tests marked with `*` are required for correctness validation
- Each task references specific requirements for traceability
- Use the design document for detailed implementation guidance
- Refer to FOUNDATION/tasks.md for the complete task list
- Refer to completed-tasks.md for history of completed work
- DynamoDB local can be used for testing without AWS credentials
