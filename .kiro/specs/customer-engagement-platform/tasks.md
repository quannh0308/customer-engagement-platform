# Implementation Tasks - Current Cycle

> **Platform Rebranding Note**: This platform was formerly known as the "General Solicitation Platform". We've rebranded to "Customer Engagement & Action Platform (CEAP)" to better reflect its capabilities beyond solicitation. This is a documentation update only—package names and code remain unchanged.

## Current Focus: Task 20 - Implement security and compliance features

This task list shows the current 2-task implementation cycle. After completing these tasks, the next cycle will be loaded from FOUNDATION.

**Note**: Completed tasks are tracked in `completed-tasks.md` to keep this file focused on current work.

## Task Status Legend
- `[ ]` - Not started
- `[~]` - In progress  
- `[x]` - Complete
- `[*]` - Property-based test task

---

## Current Task Cycle

- [x] Task 20: Implement security and compliance features
- [-] Complete cycle - Commit, push, and setup next tasks

---

## Task 20 Details: Implement security and compliance features

Implement PII redaction, opt-out handling, email compliance, IAM roles, and encryption.

### Subtasks:

- [ ] 20.1 Add PII redaction in logs
  - Identify PII fields (email, phone, address)
  - Implement redaction/masking before logging
  - _Requirements: 18.4_

- [ ]* 20.2 Write property test for PII redaction in logs
  - **Property 55: PII redaction in logs**
  - **Validates: Requirements 18.4**

- [ ] 20.3 Implement opt-out candidate deletion
  - Create opt-out event handler
  - Delete all candidates for opted-out customer
  - Complete deletion within 24 hours
  - _Requirements: 18.5_

- [ ]* 20.4 Write property test for opt-out candidate deletion
  - **Property 56: Opt-out candidate deletion**
  - **Validates: Requirements 18.5**

- [ ] 20.5 Add email compliance features
  - Include unsubscribe link in all emails
  - Enforce frequency preferences
  - _Requirements: 18.6_

- [ ]* 20.6 Write property test for email compliance
  - **Property 57: Email compliance**
  - **Validates: Requirements 18.6**

- [ ] 20.7 Configure IAM roles and policies
  - Set up service-to-service authentication
  - Apply least privilege principle
  - _Requirements: 18.1_

- [ ] 20.8 Enable encryption at rest and in transit
  - Configure KMS for DynamoDB encryption
  - Enable TLS for all API endpoints
  - _Requirements: 18.2, 18.3_

---

## Complete Cycle: Commit, Push, and Setup Next Tasks

After Task 20 completion, commit any fixes, push to git, and prepare tasks.md for the next cycle.

**IMPORTANT**: When setting up the next cycle, ALL tasks in the new tasks.md must be marked as `[ ]` not started. This is a fresh cycle start.

### Subtasks:

- [-] Commit and push any fixes
  - Stage all changes with `git add -A`
  - Create descriptive commit message if fixes were needed
  - Push to origin/main

- [ ] Setup next task cycle in tasks.md
  - Read FOUNDATION/tasks.md to identify next tasks (Task 21 from FOUNDATION)
  - Move completed Task 20 to completed-tasks.md with full details
  - Update tasks.md with Task 21 as the new main task
  - **CRITICAL**: Ensure ALL tasks in tasks.md are marked as `[ ]` not started (including Task 21 AND "Complete cycle" task)
  - **CRITICAL**: Ensure tasks in FOUNDATION/tasks.md are updated correctly (mark only the current finished task as done)
  - Update the "Complete cycle" subtask to reference Task 22 for the next iteration
  - Commit and push the updated files

---

## Next Cycle Preview

After Task 20 & cycle completion, the next cycle will focus on:
- **Task 21**: Implement candidate lifecycle management (from FOUNDATION)
- **Complete cycle**: Commit, push, and setup next tasks

---

## Notes

- Property tests marked with `*` are required for correctness validation
- Each task references specific requirements for traceability
- Use the design document for detailed implementation guidance
- Refer to FOUNDATION/tasks.md for the complete task list
- Refer to completed-tasks.md for history of completed work
- DynamoDB local can be used for testing without AWS credentials
