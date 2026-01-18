# Implementation Tasks - Current Cycle

## Current Focus: Task 16 - Implement experimentation framework

This task list shows the current 2-task implementation cycle. After completing these tasks, the next cycle will be loaded from FOUNDATION.

**Note**: Completed tasks are tracked in `completed-tasks.md` to keep this file focused on current work.

## Task Status Legend
- `[ ]` - Not started
- `[~]` - In progress  
- `[x]` - Complete
- `[*]` - Property-based test task

---

## Current Task Cycle

- [x] Task 16: Implement experimentation framework
- [-] Complete cycle - Commit, push, and setup next tasks

---

## Task 16 Details: Implement experimentation framework

Create experimentation framework for A/B testing with deterministic treatment assignment and metrics collection.

### Subtasks:

- [ ] 16.1 Create experiment configuration model
  - Define experiment config structure
  - Add treatment group definitions
  - _Validates: Requirements 11.1, 11.2_

- [ ] 16.2 Implement deterministic treatment assignment
  - Use consistent hashing for customer assignment
  - Ensure same customer always gets same treatment
  - _Validates: Requirements 11.1_

- [ ]* 16.3 Write property test for deterministic treatment assignment
  - **Property 34: Deterministic treatment assignment**
  - **Validates: Requirements 11.1**

- [ ] 16.4 Add treatment recording to candidates
  - Record assigned treatment in candidate metadata
  - _Validates: Requirements 11.3_

- [ ]* 16.5 Write property test for treatment recording
  - **Property 35: Treatment recording**
  - **Validates: Requirements 11.3**

- [ ] 16.6 Implement treatment-specific metrics collection
  - Collect metrics per treatment group
  - Enable comparison of treatment performance
  - _Validates: Requirements 11.4_

- [ ]* 16.7 Write property test for treatment-specific metrics collection
  - **Property 36: Treatment-specific metrics collection**
  - **Validates: Requirements 11.4**

---

## Complete Cycle: Commit, Push, and Setup Next Tasks

After Task 16 completion, commit any fixes, push to git, and prepare tasks.md for the next cycle.

**IMPORTANT**: When setting up the next cycle, ALL tasks in the new tasks.md must be marked as `[ ]` not started. This is a fresh cycle start.

### Subtasks:

- [-] Commit and push any fixes
  - Stage all changes with `git add -A`
  - Create descriptive commit message if fixes were needed
  - Push to origin/main

- [ ] Setup next task cycle in tasks.md
  - Read FOUNDATION/tasks.md to identify next tasks (Task 17 from FOUNDATION)
  - Move completed Task 16 to completed-tasks.md with full details
  - Update tasks.md with Task 17 as the new main task
  - **CRITICAL**: Ensure ALL tasks in tasks.md are marked as `[ ]` not started (including Task 17 AND "Complete cycle" task)
  - Update the "Complete cycle" subtask to reference Task 18 for the next iteration
  - Commit and push the updated files

---

## Next Cycle Preview

After Task 16 & cycle completion, the next cycle will focus on:
- **Task 17**: Checkpoint - Ensure workflow and configuration tests pass (from FOUNDATION)
- **Complete cycle**: Commit, push, and setup next tasks

---

## Notes

- Property tests marked with `*` are required for correctness validation
- Each task references specific requirements for traceability
- Use the design document for detailed implementation guidance
- Refer to FOUNDATION/tasks.md for the complete task list
- Refer to completed-tasks.md for history of completed work
- DynamoDB local can be used for testing without AWS credentials
