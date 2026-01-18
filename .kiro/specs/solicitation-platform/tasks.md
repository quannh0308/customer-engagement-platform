# Implementation Tasks - Current Cycle

## Current Focus: Task 14 - Implement reactive solicitation workflow

This task list shows the current 2-task implementation cycle. After completing these tasks, the next cycle will be loaded from FOUNDATION.

**Note**: Completed tasks are tracked in `completed-tasks.md` to keep this file focused on current work.

## Task Status Legend
- `[ ]` - Not started
- `[~]` - In progress  
- `[x]` - Complete
- `[*]` - Property-based test task

---

## Current Task Cycle

- [x] Task 14: Implement reactive solicitation workflow
- [-] Complete cycle - Commit, push, and setup next tasks

---

## Task 14 Details: Implement reactive solicitation workflow

Create event-driven workflow for real-time candidate creation in response to customer events.

### Subtasks:

- [ ] 14.1 Create EventBridge rule for customer events
  - Configure event pattern matching
  - Route events to reactive Lambda
  - _Validates: Requirements 9.1, 9.2_

- [ ] 14.2 Implement reactive Lambda function
  - Execute filtering and scoring in real-time
  - Create and store eligible candidates immediately
  - _Validates: Requirements 9.2, 9.3, 9.4_

- [ ]* 14.3 Write property test for reactive candidate creation
  - **Property 28: Reactive candidate creation**
  - **Validates: Requirements 9.4**

- [ ] 14.4 Implement event deduplication
  - Track recent events per customer-subject pair
  - Deduplicate within configured time window
  - _Validates: Requirements 9.5_

- [ ]* 14.5 Write property test for event deduplication within window
  - **Property 29: Event deduplication within window**
  - **Validates: Requirements 9.5**

---

## Complete Cycle: Commit, Push, and Setup Next Tasks

After Task 14 completion, commit any fixes, push to git, and prepare tasks.md for the next cycle.

**IMPORTANT**: When setting up the next cycle, ALL tasks in the new tasks.md must be marked as `[ ]` not started. This is a fresh cycle start.

### Subtasks:

- [-] Commit and push any fixes
  - Stage all changes with `git add -A`
  - Create descriptive commit message if fixes were needed
  - Push to origin/main

- [ ] Setup next task cycle in tasks.md
  - Read FOUNDATION/tasks.md to identify next tasks (Task 15 from FOUNDATION)
  - Move completed Task 14 to completed-tasks.md with full details
  - Update tasks.md with Task 15 as the new main task
  - **CRITICAL**: Ensure ALL tasks in tasks.md are marked as `[ ]` not started (including Task 15 AND "Complete cycle" task)
  - Update the "Complete cycle" subtask to reference Task 16 for the next iteration
  - Commit and push the updated files

---

## Next Cycle Preview

After Task 14 & cycle completion, the next cycle will focus on:
- **Task 15**: Implement program configuration management (from FOUNDATION)
- **Complete cycle**: Commit, push, and setup next tasks

---

## Notes

- Property tests marked with `*` are required for correctness validation
- Each task references specific requirements for traceability
- Use the design document for detailed implementation guidance
- Refer to FOUNDATION/tasks.md for the complete task list
- Refer to completed-tasks.md for history of completed work
- DynamoDB local can be used for testing without AWS credentials
