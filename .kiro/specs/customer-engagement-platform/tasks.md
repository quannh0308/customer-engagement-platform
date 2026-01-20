# Implementation Tasks - Current Cycle

> **Platform Rebranding Note**: This platform was formerly known as the "General Solicitation Platform". We've rebranded to "Customer Engagement & Action Platform (CEAP)" to better reflect its capabilities beyond solicitation. This is a documentation update only—package names and code remain unchanged.

## Current Focus: Task 28 - Rename code modules and packages to CEAP

This task list shows the current 2-task implementation cycle. After completing these tasks, the next cycle will be loaded from FOUNDATION.

**Note**: Completed tasks are tracked in `completed-tasks.md` to keep this file focused on current work.

## Task Status Legend
- `[ ]` - Not started
- `[~]` - In progress  
- `[x]` - Complete
- `[*]` - Property-based test task

---

## Current Task Cycle

- [ ] Task 28: Rename code modules and packages to CEAP
- [ ] Complete cycle - Commit, push, and setup next tasks

---

## Task 28 Details: Rename code modules and packages to CEAP

Rename all module directories from `solicitation-*` to `ceap-*` and all package names from `com.solicitation.*` to `com.ceap.*`. This aligns the codebase with the platform rebranding to "Customer Engagement & Action Platform (CEAP)".

**Requirements**: All (code consistency with branding)

### Subtasks:

- [ ] 28.1 Rename module directories
  - Rename: `solicitation-channels` → `ceap-channels`
  - Rename: `solicitation-common` → `ceap-common`
  - Rename: `solicitation-connectors` → `ceap-connectors`
  - Rename: `solicitation-filters` → `ceap-filters`
  - Rename: `solicitation-models` → `ceap-models`
  - Rename: `solicitation-scoring` → `ceap-scoring`
  - Rename: `solicitation-serving` → `ceap-serving`
  - Rename: `solicitation-storage` → `ceap-storage`
  - Rename: `solicitation-workflow-etl` → `ceap-workflow-etl`
  - Rename: `solicitation-workflow-filter` → `ceap-workflow-filter`
  - Rename: `solicitation-workflow-reactive` → `ceap-workflow-reactive`
  - Rename: `solicitation-workflow-score` → `ceap-workflow-score`
  - Rename: `solicitation-workflow-store` → `ceap-workflow-store`
  - Use `git mv` to preserve history

- [ ] 28.2 Update settings.gradle.kts
  - Update all module references from `solicitation-*` to `ceap-*`
  - Example: `include("solicitation-channels")` → `include("ceap-channels")`

- [ ] 28.3 Update all build.gradle.kts files
  - Update project dependencies from `:solicitation-*` to `:ceap-*`
  - Example: `implementation(project(":solicitation-models"))` → `implementation(project(":ceap-models"))`
  - Update in all 13 module build files

- [ ] 28.4 Rename package directories
  - Rename: `com/solicitation` → `com/ceap` in all modules
  - Use `git mv` to preserve history
  - Affects all `src/main/kotlin` and `src/main/java` directories

- [ ] 28.5 Update package declarations
  - Replace: `package com.solicitation.*` → `package com.ceap.*`
  - Affects all Kotlin and Java source files (~150+ files)
  - Use find and replace across all files

- [ ] 28.6 Update import statements
  - Replace: `import com.solicitation.*` → `import com.ceap.*`
  - Affects all Kotlin and Java source files (~150+ files)
  - Use find and replace across all files

- [ ] 28.7 Update infrastructure CDK code
  - Update package references in `infrastructure/src/main/kotlin`
  - Update Lambda handler references in CDK stacks
  - Example: `com.solicitation.workflow.etl.ETLHandler` → `com.ceap.workflow.etl.ETLHandler`

- [ ] 28.8 Update configuration files
  - Update `logback.xml` logger names
  - Update any configuration files referencing package names

- [ ] 28.9 Build and test after renaming
  - Run: `./gradlew clean build`
  - Verify all modules compile successfully
  - Verify all tests pass
  - Fix any issues found

- [ ] 28.10 Update documentation references
  - Update any documentation that references module names
  - Update any documentation that references package names
  - Keep migration notes explaining the history

- [ ] 28.11 Commit code renaming changes
  - Stage all changes: `git add .`
  - Create descriptive commit message
  - Commit changes

---

## Complete Cycle: Commit, Push, and Setup Next Tasks

After Task 28 completion, commit any fixes, push to git, and prepare tasks.md for the next cycle.

**IMPORTANT**: When setting up the next cycle, ALL tasks in the new tasks.md must be marked as `[ ]` not started. This is a fresh cycle start.

### Subtasks:

- [ ] Commit and push any fixes
  - Stage all changes with `git add -A`
  - Create descriptive commit message if fixes were needed
  - Push to origin/main

- [ ] Setup next task cycle in tasks.md
  - Read FOUNDATION/tasks.md to identify next tasks (Task 29 from FOUNDATION)
  - Move completed Task 28 to completed-tasks.md with full details
  - Update tasks.md with Task 29 as the new main task
  - **CRITICAL**: Ensure ALL tasks in tasks.md are marked as `[ ]` not started (including Task 29 AND "Complete cycle" task)
  - **CRITICAL**: Ensure tasks in FOUNDATION/tasks.md are updated correctly (mark only the current finished task as done)
  - Update the "Complete cycle" subtask to reference Task 30 for the next iteration
  - Commit and push the updated files

---

## Next Cycle Preview

After Task 28 & cycle completion, the next cycle will focus on:
- **Task 29**: Documentation audit and cleanup (from FOUNDATION)
- **Complete cycle**: Commit, push, and setup next tasks

---

## Notes

- Property tests marked with `*` are required for correctness validation
- Each task references specific requirements for traceability
- Use the design document for detailed implementation guidance
- Refer to FOUNDATION/tasks.md for the complete task list
- Refer to completed-tasks.md for history of completed work
- DynamoDB local can be used for testing without AWS credentials
