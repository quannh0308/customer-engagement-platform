# Implementation Plan: Complete CEAP Infrastructure Rebrand

## Overview

This implementation plan breaks down the CEAP infrastructure rebrand into discrete, incremental steps following a bottom-up refactoring approach. Each task builds on previous tasks to minimize compilation errors and ensure the codebase remains functional throughout the refactoring process.

The implementation follows five phases:
1. Rename leaf constructs (no dependencies)
2. Update stack imports and instantiations
3. Update resource names in stacks
4. Update observability identifiers
5. Rename main application class

## Tasks

- [x] 1. Phase 1: Rename Lambda Construct
  - [x] 1.1 Rename `SolicitationLambda.kt` to `CeapLambda.kt` and update class name
    - Rename file from `infrastructure/src/main/kotlin/com/ceap/infrastructure/constructs/SolicitationLambda.kt` to `CeapLambda.kt`
    - Update class name from `class SolicitationLambda` to `class CeapLambda`
    - Update class documentation to reference CEAP instead of solicitation
    - Verify constructor signature and public API remain unchanged
    - _Requirements: 3.1, 14.2_
  
  - [x] 1.2 Write unit test for CeapLambda class existence
    - Test that `CeapLambda.kt` file exists
    - Test that `CeapLambda` class is defined with correct constructor signature
    - Test that old `SolicitationLambda.kt` file does not exist
    - _Requirements: 3.1_
  
  - [x] 1.3 Write property test for class interface preservation
    - **Property 2: Class Interface Preservation**
    - **Validates: Requirements 1.3, 3.1, 14.2**
    - Verify constructor parameters and public methods remain unchanged
    - _Requirements: 14.2_

- [x] 2. Phase 2: Update Workflow Stack Imports
  - [x] 2.1 Update import statements in all workflow stacks
    - Update `EtlWorkflowStack.kt`: Change import from `SolicitationLambda` to `CeapLambda`
    - Update `FilterWorkflowStack.kt`: Change import from `SolicitationLambda` to `CeapLambda`
    - Update `ScoreWorkflowStack.kt`: Change import from `SolicitationLambda` to `CeapLambda`
    - Update `StoreWorkflowStack.kt`: Change import from `SolicitationLambda` to `CeapLambda`
    - Update `ReactiveWorkflowStack.kt`: Change import from `SolicitationLambda` to `CeapLambda`
    - _Requirements: 3.2, 15.1_
  
  - [x] 2.2 Update Lambda instantiations in all workflow stacks
    - Update `EtlWorkflowStack.kt`: Change `SolicitationLambda(...)` to `CeapLambda(...)`
    - Update `FilterWorkflowStack.kt`: Change `SolicitationLambda(...)` to `CeapLambda(...)`
    - Update `ScoreWorkflowStack.kt`: Change `SolicitationLambda(...)` to `CeapLambda(...)`
    - Update `StoreWorkflowStack.kt`: Change `SolicitationLambda(...)` to `CeapLambda(...)`
    - Update `ReactiveWorkflowStack.kt`: Change `SolicitationLambda(...)` to `CeapLambda(...)`
    - _Requirements: 3.3, 15.2_
  
  - [x] 2.3 Write unit tests for import statement updates
    - Test that each workflow stack imports `CeapLambda`
    - Test that no workflow stack imports `SolicitationLambda`
    - _Requirements: 3.2_

- [x] 3. Checkpoint - Verify compilation after construct rename
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Phase 3: Update Resource Names in Stacks
  - [x] 4.1 Update CloudFormation stack names in main application
    - Update `SolicitationPlatformApp.kt` line 35: Change `SolicitationDatabase-$envName` to `CeapDatabase-$envName`
    - Update `SolicitationPlatformApp.kt` line 38: Change `SolicitationEtlWorkflow-$envName` to `CeapEtlWorkflow-$envName`
    - Update `SolicitationPlatformApp.kt` line 39: Change `SolicitationFilterWorkflow-$envName` to `CeapFilterWorkflow-$envName`
    - Update `SolicitationPlatformApp.kt` line 40: Change `SolicitationScoreWorkflow-$envName` to `CeapScoreWorkflow-$envName`
    - Update `SolicitationPlatformApp.kt` line 41: Change `SolicitationStoreWorkflow-$envName` to `CeapStoreWorkflow-$envName`
    - Update `SolicitationPlatformApp.kt` line 42: Change `SolicitationReactiveWorkflow-$envName` to `CeapReactiveWorkflow-$envName`
    - Update `SolicitationPlatformApp.kt` line 47: Change `SolicitationOrchestration-$envName` to `CeapOrchestration-$envName`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_
  
  - [x] 4.2 Update DynamoDB table name in ReactiveWorkflowStack
    - Update `ReactiveWorkflowStack.kt` line 38: Change `solicitation-event-deduplication-$envName` to `ceap-event-deduplication-$envName`
    - _Requirements: 7.1_
  
  - [x] 4.3 Update EventBridge rule names in ReactiveWorkflowStack
    - Update `ReactiveWorkflowStack.kt` line 75: Change `solicitation-customer-events-$envName` to `ceap-customer-events-$envName`
    - _Requirements: 5.2_
  
  - [x] 4.4 Update EventBridge source in ReactiveWorkflowStack
    - Update `ReactiveWorkflowStack.kt` line 79: Change `solicitation.customer-events` to `ceap.customer-events`
    - _Requirements: 6.1_
  
  - [x] 4.5 Update Step Functions state machine name in OrchestrationStack
    - Update `OrchestrationStack.kt` line 156: Change `SolicitationBatchIngestion-$envName` to `CeapBatchIngestion-$envName`
    - _Requirements: 4.1_
  
  - [x] 4.6 Update EventBridge schedule rule name in OrchestrationStack
    - Update `OrchestrationStack.kt` line 163: Change `SolicitationBatchIngestion-$envName` to `CeapBatchIngestion-$envName`
    - _Requirements: 5.1_
  
  - [x] 4.7 Write unit tests for resource name updates
    - Test that database stack uses `CeapDatabase-$envName` pattern
    - Test that ETL stack uses `CeapEtlWorkflow-$envName` pattern
    - Test that filter stack uses `CeapFilterWorkflow-$envName` pattern
    - Test that score stack uses `CeapScoreWorkflow-$envName` pattern
    - Test that store stack uses `CeapStoreWorkflow-$envName` pattern
    - Test that reactive stack uses `CeapReactiveWorkflow-$envName` pattern
    - Test that orchestration stack uses `CeapOrchestration-$envName` pattern
    - Test that deduplication table uses `ceap-event-deduplication-$envName` pattern
    - Test that customer event rule uses `ceap-customer-events-$envName` pattern
    - Test that EventBridge source uses `ceap.customer-events`
    - Test that state machine uses `CeapBatchIngestion-$envName` pattern
    - Test that schedule rule uses `CeapBatchIngestion-$envName` pattern
    - _Requirements: 2.1-2.7, 4.1, 5.1, 5.2, 6.1, 7.1_
  
  - [x] 4.8 Write property test for naming convention consistency
    - **Property 4: Naming Convention Consistency**
    - **Validates: Requirements 2.1-2.7, 4.1, 5.1-5.2, 6.1-6.2, 7.1, 8.1-8.4, 9.1, 10.1-10.3, 11.1-11.2, 12.1**
    - Verify all resources follow CEAP naming patterns from the naming conventions table
    - _Requirements: 2.1-2.7, 4.1, 5.1, 5.2, 6.1, 7.1_

- [x] 5. Checkpoint - Verify compilation after resource name updates
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Phase 4: Update Observability Identifiers
  - [x] 6.1 Update CloudWatch dashboard name in ObservabilityDashboard
    - Update `ObservabilityDashboard.kt` line 44: Change `SolicitationPlatform-$programId` to `CeapPlatform-$programId`
    - _Requirements: 9.1_
  
  - [x] 6.2 Update CloudWatch namespace references in ObservabilityDashboard
    - Update `ObservabilityDashboard.kt` lines 75, 76, 90-92, 201, 227: Change `SolicitationPlatform/Workflow` to `CeapPlatform/Workflow`
    - Update `ObservabilityDashboard.kt` lines 106-108: Change `SolicitationPlatform/Channels` to `CeapPlatform/Channels`
    - Update `ObservabilityDashboard.kt` line 122: Change `SolicitationPlatform/Rejections` to `CeapPlatform/Rejections`
    - _Requirements: 8.1, 8.3, 8.4_
  
  - [x] 6.3 Update CloudWatch alarm names in ObservabilityDashboard
    - Update `ObservabilityDashboard.kt` line 183: Change `SolicitationPlatform-ApiLatency-$programId` to `CeapPlatform-ApiLatency-$programId`
    - Update `ObservabilityDashboard.kt` line 209: Change `SolicitationPlatform-WorkflowFailure-$programId` to `CeapPlatform-WorkflowFailure-$programId`
    - Update `ObservabilityDashboard.kt` line 238: Change `SolicitationPlatform-DataQuality-$programId` to `CeapPlatform-DataQuality-$programId`
    - _Requirements: 10.1, 10.2, 10.3_
  
  - [x] 6.4 Update Lambda function name references in ObservabilityDashboard
    - Update `ObservabilityDashboard.kt` line 152: Change `SolicitationPlatform-ETL-$programId` to `CeapPlatform-ETL-$programId`
    - Update `ObservabilityDashboard.kt` lines 153, 177: Change `SolicitationPlatform-Serve-$programId` to `CeapPlatform-Serve-$programId`
    - _Requirements: 11.1, 11.2_
  
  - [x] 6.5 Update DynamoDB table name references in ObservabilityDashboard
    - Update `ObservabilityDashboard.kt` lines 137, 138: Change `SolicitationCandidates-$programId` to `CeapCandidates-$programId`
    - _Requirements: 12.1_
  
  - [x] 6.6 Write unit tests for observability identifier updates
    - Test that dashboard uses `CeapPlatform-$programId` pattern
    - Test that workflow namespace uses `CeapPlatform/Workflow`
    - Test that channels namespace uses `CeapPlatform/Channels`
    - Test that rejections namespace uses `CeapPlatform/Rejections`
    - Test that API latency alarm uses `CeapPlatform-ApiLatency-$programId` pattern
    - Test that workflow failure alarm uses `CeapPlatform-WorkflowFailure-$programId` pattern
    - Test that data quality alarm uses `CeapPlatform-DataQuality-$programId` pattern
    - Test that ETL function reference uses `CeapPlatform-ETL-$programId` pattern
    - Test that Serve function reference uses `CeapPlatform-Serve-$programId` pattern
    - Test that candidates table reference uses `CeapCandidates-$programId` pattern
    - _Requirements: 8.1, 8.3, 8.4, 9.1, 10.1, 10.2, 10.3, 11.1, 11.2, 12.1_

- [x] 7. Phase 5: Update EventBridge Configuration
  - [x] 7.1 Update CloudFormation parameter in eventbridge-rules.yaml
    - Update `eventbridge-rules.yaml` line 23: Change `ReactiveSolicitationStateMachineArn` to `ReactiveCeapStateMachineArn`
    - Update parameter description to replace "Solicitation" with "CEAP"
    - _Requirements: 13.2_
  
  - [x] 7.2 Update EventBridge rule descriptions in eventbridge-rules.yaml
    - Update rule descriptions to replace "solicitation" with "ceap"
    - Update lines 69, 92 to replace "solicitation program" with "ceap program"
    - _Requirements: 13.1_
  
  - [x] 7.3 Write property test for description field consistency
    - **Property 3: Description Field Consistency**
    - **Validates: Requirements 13.1, 13.2**
    - Verify no description fields contain "solicitation"
    - Verify appropriate fields contain "ceap"
    - _Requirements: 13.1, 13.2_

- [x] 8. Checkpoint - Verify compilation after observability updates
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Phase 6: Rename Main Application Class
  - [x] 9.1 Rename `SolicitationPlatformApp.kt` to `CeapPlatformApp.kt`
    - Rename file from `infrastructure/src/main/kotlin/com/ceap/infrastructure/SolicitationPlatformApp.kt` to `CeapPlatformApp.kt`
    - Note: File contains only the main function, no class to rename
    - Update file documentation to reference CEAP instead of solicitation
    - _Requirements: 1.1_
  
  - [x] 9.2 Write unit test for main application file rename
    - Test that `CeapPlatformApp.kt` file exists
    - Test that old `SolicitationPlatformApp.kt` file does not exist
    - _Requirements: 1.1_
  
  - [x] 9.3 Write property test for file rename completeness
    - **Property 5: File Rename Completeness**
    - **Validates: Requirements 1.1, 3.1**
    - Verify old filenames don't exist
    - Verify new filenames exist with expected content
    - _Requirements: 1.1, 3.1_

- [x] 10. Final Verification: Complete Legacy Terminology Removal
  - [x] 10.1 Write property test for complete legacy terminology removal
    - **Property 1: Complete Legacy Terminology Removal**
    - **Validates: Requirements 1.2, 3.2, 3.3, 15.1, 15.2, 15.3**
    - Search all source files for "solicitation" in functional code
    - Verify zero matches (excluding comments/docs)
    - _Requirements: 1.2, 3.2, 3.3, 15.1, 15.2, 15.3_
  
  - [x] 10.2 Run CDK synthesis test
    - Run `cdk synth` to verify CloudFormation templates are generated correctly
    - Verify no compilation errors
    - _Requirements: 14.1_

- [x] 11. Final Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- All tasks are required for comprehensive testing and validation
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation after each major phase
- Property tests validate universal correctness properties across all files
- Unit tests validate specific examples and resource name patterns
- The bottom-up approach ensures the codebase remains compilable at each step
- All file paths are relative to the `infrastructure/` directory
- Line numbers are approximate and may shift as edits are made


## Phase 7: Update Project-Wide Configuration

- [ ] 12. Update Build Configuration
  - [ ] 12.1 Update Gradle group in root build.gradle.kts
    - Update `build.gradle.kts` line 7: Change `group = "com.solicitation"` to `group = "com.ceap"`
    - _Requirements: 16.1_
  
  - [ ] 12.2 Write unit test for Gradle group update
    - Test that root build.gradle.kts uses `group = "com.ceap"`
    - Test that root build.gradle.kts does not use `group = "com.solicitation"`
    - _Requirements: 16.1_

- [ ] 13. Update Deployment Scripts
  - [ ] 13.1 Update deploy-lambda.sh configuration
    - Update line 9: Change `STACK_NAME="solicitation-platform-lambda-functions"` to `STACK_NAME="ceap-platform-lambda-functions"`
    - Update line 12: Change `PROJECT_NAME="${PROJECT_NAME:-solicitation-platform}"` to `PROJECT_NAME="${PROJECT_NAME:-ceap-platform}"`
    - _Requirements: 17.1, 17.3_
  
  - [ ] 13.2 Update deploy-dynamodb.sh configuration
    - Update line 18: Change `STACK_NAME="solicitation-platform-dynamodb-${ENVIRONMENT}"` to `STACK_NAME="ceap-platform-dynamodb-${ENVIRONMENT}"`
    - Update line 49: Change `Application=SolicitationPlatform` to `Application=CeapPlatform`
    - _Requirements: 17.2, 20.1_
  
  - [ ] 13.3 Update deploy.sh configuration and CloudFormation references
    - Update line 38: Change `PROJECT_NAME="solicitation-platform"` to `PROJECT_NAME="ceap-platform"`
    - Update line 213: Change `REACTIVE_SOLICITATION_ARN` variable name to `REACTIVE_CEAP_ARN`
    - Update line 215: Change output key query from `ReactiveSolicitationStateMachineArn` to `ReactiveCeapStateMachineArn`
    - Update line 245: Change `REACTIVE_SOLICITATION_ARN` variable name to `REACTIVE_CEAP_ARN`
    - Update line 247: Change output key query from `ReactiveSolicitationStateMachineArn` to `ReactiveCeapStateMachineArn`
    - Update line 263: Change parameter name from `ReactiveSolicitationStateMachineArn` to `ReactiveCeapStateMachineArn`
    - _Requirements: 17.3, 19.1, 19.2_
  
  - [ ] 13.4 Update build.sh configuration
    - Update lines 133-137: Change module names from `solicitation-workflow-*` to `ceap-workflow-*`
    - Update line 168: Change sed pattern from `'s/solicitation-workflow-//'` to `'s/ceap-workflow-//'`
    - _Requirements: 17.4_

- [ ] 14. Update Script Documentation
  - [ ] 14.1 Update deploy-cdk.sh header
    - Update line 4: Change "CDK Deployment Script for Solicitation Platform" to "CDK Deployment Script for CEAP Platform"
    - _Requirements: 18.1_
  
  - [ ] 14.2 Update build.sh header and output
    - Update line 4: Change "Build Script for General Solicitation Platform" to "Build Script for Customer Engagement & Action Platform (CEAP)"
    - Update line 72: Change "Building Solicitation Platform" to "Building CEAP Platform"
    - _Requirements: 18.2_
  
  - [ ] 14.3 Update deploy.sh header and output
    - Update line 4: Change "Deployment Script for General Solicitation Platform" to "Deployment Script for Customer Engagement & Action Platform (CEAP)"
    - Update line 84: Change "Deploying Solicitation Platform" to "Deploying CEAP Platform"
    - _Requirements: 18.1_

- [ ] 15. Write Comprehensive Tests for Project-Wide Changes
  - [ ] 15.1 Write unit tests for build configuration updates
    - Test that root build.gradle.kts uses correct group
    - Test that deployment scripts use correct stack names
    - Test that deployment scripts use correct project names
    - _Requirements: 16.1, 17.1, 17.2, 17.3_
  
  - [ ] 15.2 Write property test for project-wide terminology removal
    - **Property 6: Project-Wide Legacy Terminology Removal**
    - **Validates: Requirements 16.1, 17.1-17.4, 18.1-18.2, 19.1-19.2, 20.1**
    - Search all configuration and script files for "solicitation"
    - Verify zero matches in functional code (excluding test documentation)
    - _Requirements: 16.1, 17.1, 17.2, 17.3, 17.4, 18.1, 18.2, 19.1, 19.2, 20.1_

- [ ] 16. Final Project-Wide Verification
  - [ ] 16.1 Run full project build
    - Run `./gradlew build` to verify all modules compile
    - Verify no compilation errors
    - _Requirements: 14.1, 16.1_
  
  - [ ] 16.2 Verify deployment scripts are functional
    - Verify all deployment scripts reference correct resource names
    - Verify no broken references to old "solicitation" names
    - _Requirements: 17.1, 17.2, 17.3, 17.4, 19.1, 19.2_

- [ ] 17. Final Project-Wide Checkpoint
  - Ensure all tests pass, verify no "solicitation" references remain in functional code
