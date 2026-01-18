# Implementation Tasks - Current Cycle

## Current Focus: Task 11 - Implement email channel adapter

This task list shows the current 2-task implementation cycle. After completing these tasks, the next cycle will be loaded from FOUNDATION.

**Note**: Completed tasks are tracked in `completed-tasks.md` to keep this file focused on current work.

## Task Status Legend
- `[ ]` - Not started
- `[~]` - In progress  
- `[x]` - Complete
- `[*]` - Property-based test task

---

## Current Task Cycle

- [x] Task 11: Implement email channel adapter
- [-] Complete cycle - Commit, push, and setup next tasks

---

## Task 11 Details: Implement email channel adapter

Create the email channel adapter for delivering candidates through email campaigns.

### Subtasks:

- [ ] 11.1 Create email channel adapter implementation
  - Integrate with email campaign service
  - Implement campaign creation automation
  - Add template management per program
  - _Validates: Requirements 14.1, 14.2_

- [ ]* 11.2 Write property test for email campaign automation
  - **Property 42: Email campaign automation**
  - **Validates: Requirements 14.1**

- [ ]* 11.3 Write property test for program-specific email templates
  - **Property 43: Program-specific email templates**
  - **Validates: Requirements 14.2**

- [ ] 11.4 Implement opt-out enforcement
  - Add opt-out check before campaign creation
  - Exclude opted-out customers from recipient lists
  - _Validates: Requirements 14.3, 18.5_

- [ ]* 11.5 Write property test for opt-out enforcement
  - **Property 44: Opt-out enforcement**
  - **Validates: Requirements 14.3**

- [ ] 11.6 Implement frequency capping
  - Track email sends per customer
  - Enforce frequency limits per program configuration
  - _Validates: Requirements 14.4, 18.6_

- [ ]* 11.7 Write property test for email frequency capping
  - **Property 45: Email frequency capping**
  - **Validates: Requirements 14.4**

- [ ] 11.8 Add delivery tracking
  - Record delivery status and timestamps
  - Track open rates and engagement metrics
  - _Validates: Requirements 14.6_

- [ ]* 11.9 Write property test for email delivery tracking
  - **Property 46: Email delivery tracking**
  - **Validates: Requirements 14.6**

---

## Complete Cycle: Commit, Push, and Setup Next Tasks

After Task 11 completion, commit any fixes, push to git, and prepare tasks.md for the next cycle.

**IMPORTANT**: When setting up the next cycle, ALL tasks in the new tasks.md must be marked as `[ ]` not started. This is a fresh cycle start.

### Subtasks:

- [-] Commit and push any fixes
  - Stage all changes with `git add -A`
  - Create descriptive commit message if fixes were needed
  - Push to origin/main

- [ ] Setup next task cycle in tasks.md
  - Read FOUNDATION/tasks.md to identify next tasks (Task 12 from FOUNDATION)
  - Move completed Task 11 to completed-tasks.md with full details
  - Update tasks.md with Task 12 as the new main task
  - **CRITICAL**: Ensure ALL tasks in tasks.md are marked as `[ ]` not started (including Task 12 AND "Complete cycle" task)
  - Update the "Complete cycle" subtask to reference Task 13 for the next iteration
  - Commit and push the updated files

---

## Next Cycle Preview

After Task 11 & cycle completion, the next cycle will focus on:
- **Task 12**: Checkpoint - Ensure serving and channel tests pass (from FOUNDATION)
- **Complete cycle**: Commit, push, and setup next tasks

---

## Notes

- Property tests marked with `*` are required for correctness validation
- Each task references specific requirements for traceability
- Use the design document for detailed implementation guidance
- Refer to FOUNDATION/tasks.md for the complete task list
- Refer to completed-tasks.md for history of completed work
- DynamoDB local can be used for testing without AWS credentials
