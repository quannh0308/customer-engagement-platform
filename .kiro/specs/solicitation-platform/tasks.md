# Implementation Tasks - Current Cycle

## Current Focus: Task 18 - Implement observability and monitoring

This task list shows the current 2-task implementation cycle. After completing these tasks, the next cycle will be loaded from FOUNDATION.

**Note**: Completed tasks are tracked in `completed-tasks.md` to keep this file focused on current work.

## Task Status Legend
- `[ ]` - Not started
- `[~]` - In progress  
- `[x]` - Complete
- `[*]` - Property-based test task

---

## Current Task Cycle

- [ ] Task 18: Implement observability and monitoring
- [ ] Complete cycle - Commit, push, and setup next tasks

---

## Task 18 Details: Implement observability and monitoring

Add comprehensive observability features including structured logging, metrics aggregation, dashboards, and alarms.

### Subtasks:

- [ ] 18.1 Add structured logging with correlation IDs
  - Implement correlation ID generation and propagation
  - Add structured log format with context
  - Log failures with error details
  - _Requirements: 12.2_

- [ ]* 18.2 Write property test for structured logging with correlation
  - **Property 37: Structured logging with correlation**
  - **Validates: Requirements 12.2**

- [ ] 18.3 Implement rejection reason aggregation
  - Aggregate rejections by filter type and reason code
  - Publish aggregated metrics to CloudWatch
  - _Requirements: 12.3_

- [ ]* 18.4 Write property test for rejection reason aggregation
  - **Property 38: Rejection reason aggregation**
  - **Validates: Requirements 12.3**

- [ ] 18.5 Create CloudWatch dashboards
  - Per-program health dashboard
  - Per-channel performance dashboard
  - Cost and capacity dashboard
  - _Requirements: 12.5_

- [ ] 18.6 Configure CloudWatch alarms
  - API latency alarms
  - Workflow failure alarms
  - Data quality alarms
  - _Requirements: 12.4, 12.6_

---

## Complete Cycle: Commit, Push, and Setup Next Tasks

After Task 18 completion, commit any fixes, push to git, and prepare tasks.md for the next cycle.

**IMPORTANT**: When setting up the next cycle, ALL tasks in the new tasks.md must be marked as `[ ]` not started. This is a fresh cycle start.

### Subtasks:

- [ ] Commit and push any fixes
  - Stage all changes with `git add -A`
  - Create descriptive commit message if fixes were needed
  - Push to origin/main

- [x] Setup next task cycle in tasks.md
  - Read FOUNDATION/tasks.md to identify next tasks (Task 19 from FOUNDATION)
  - Move completed Task 18 to completed-tasks.md with full details
  - Update tasks.md with Task 19 as the new main task
  - **CRITICAL**: Ensure ALL tasks in tasks.md are marked as `[ ]` not started (including Task 19 AND "Complete cycle" task)
  - Update the "Complete cycle" subtask to reference Task 20 for the next iteration
  - Commit and push the updated files

---

## Next Cycle Preview

After Task 18 & cycle completion, the next cycle will focus on:
- **Task 19**: Implement multi-program isolation (from FOUNDATION)
- **Complete cycle**: Commit, push, and setup next tasks

---

## Notes

- Property tests marked with `*` are required for correctness validation
- Each task references specific requirements for traceability
- Use the design document for detailed implementation guidance
- Refer to FOUNDATION/tasks.md for the complete task list
- Refer to completed-tasks.md for history of completed work
- DynamoDB local can be used for testing without AWS credentials
