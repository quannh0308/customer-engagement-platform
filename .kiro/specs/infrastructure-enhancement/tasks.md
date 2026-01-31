# Implementation Plan: Infrastructure Enhancement

## Overview

This implementation plan converts the infrastructure enhancement design into discrete coding tasks. The approach follows an incremental strategy:

1. Start with Lambda naming improvements (low risk, immediate value)
2. Implement S3-based orchestration foundation (bucket, base handler)
3. Build Express workflow support (simpler, faster to validate)
4. Add Standard workflow and Glue integration (more complex)
5. Implement observability and error handling
6. Comprehensive testing throughout

Each task builds on previous work, with checkpoints to validate progress. The implementation uses Kotlin for CDK infrastructure and Lambda handlers.

## Tasks

- [x] 1. Update Lambda function naming in CDK constructs
  - Modify CDK Lambda constructs to use explicit `functionName` property
  - Follow pattern: `{StackName}-{Environment}-{FunctionPurpose}`
  - Update all 5 Lambda functions (ETL, Filter, Score, Store, Reactive)
  - Verify CloudWatch log groups use clean names
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [ ]* 1.1 Write property test for Lambda naming convention
  - **Property 1: Lambda Function Name Format**
  - **Validates: Requirements 1.1, 1.2, 1.3, 1.4**

- [x] 1.2 Git commit and push Task 1 changes
  - Commit message: "feat: update Lambda function naming in CDK constructs"
  - Push to remote repository

- [x] 2. Create S3 workflow bucket infrastructure
  - [x] 2.1 Create S3 bucket with CDK
    - Define bucket with naming convention: `ceap-workflow-{environment}-{accountId}`
    - Configure S3-managed encryption
    - Block all public access
    - _Requirements: 2.5, 2.7_
  
  - [x] 2.2 Implement S3 lifecycle policy
    - Add lifecycle rule to delete objects under `executions/` prefix after 7 days
    - _Requirements: 2.7_
  
  - [ ]* 2.3 Write property test for S3 lifecycle policy
    - **Property 7: S3 Lifecycle Policy**
    - **Validates: Requirements 2.7**
  
  - [x] 2.4 Configure IAM permissions for S3 access
    - Grant read/write permissions to Lambda execution roles
    - Follow least-privilege principles
    - _Requirements: 11.2, 11.9_
  
  - [ ]* 2.5 Write property test for S3 IAM permissions
    - **Property 40: S3 IAM Permissions**
    - **Validates: Requirements 11.2**

- [x] 2.6 Git commit and push Task 2 changes
  - Commit message: "feat: create S3 workflow bucket infrastructure"
  - Push to remote repository

- [x] 3. Implement base Lambda handler with S3 integration
  - [x] 3.1 Create ExecutionContext data class
    - Define fields: executionId, currentStage, previousStage, workflowBucket, initialData
    - _Requirements: 2.1_
  
  - [x] 3.2 Create StageResult data class
    - Define fields: status, stage, recordsProcessed, errorMessage
    - _Requirements: 8.4_
  
  - [x] 3.3 Implement WorkflowLambdaHandler abstract base class
    - Implement S3 input reading logic (convention-based path)
    - Implement S3 output writing logic (convention-based path)
    - Handle first stage (use initialData instead of S3)
    - Add logging for execution context and S3 paths
    - _Requirements: 2.2, 2.3, 2.4, 3.1, 3.2, 9.5, 9.6_
  
  - [ ]* 3.4 Write property test for S3 output path convention
    - **Property 2: S3 Output Path Convention**
    - **Validates: Requirements 2.2, 3.2**
  
  - [ ]* 3.5 Write property test for S3 input path convention
    - **Property 3: S3 Input Path Convention**
    - **Validates: Requirements 2.3, 3.1**
  
  - [ ]* 3.6 Write property test for first stage input source
    - **Property 4: First Stage Input Source**
    - **Validates: Requirements 2.4**
  
  - [ ]* 3.7 Write property test for execution context payload structure
    - **Property 8: Execution Context Payload Structure**
    - **Validates: Requirements 2.1**
  
  - [ ]* 3.8 Write unit tests for base handler
    - Test S3 read/write operations
    - Test error handling (S3 access denied, object not found)
    - Test first stage vs non-first stage behavior
    - _Requirements: 12.1_

- [x] 3.9 Git commit and push Task 3 changes
  - Commit message: "feat: implement base Lambda handler with S3 integration"
  - Push to remote repository

- [x] 4. Checkpoint - Validate base infrastructure
  - Ensure all tests pass, ask the user if questions arise.

- [-] 5. Migrate existing Lambda functions to use base handler
  - [x] 5.1 Refactor ETL Lambda to extend WorkflowLambdaHandler
    - Implement processData method with existing ETL logic
    - Remove direct S3 client usage (handled by base class)
    - _Requirements: 3.3, 3.4, 3.5_
  
  - [x] 5.2 Refactor Filter Lambda to extend WorkflowLambdaHandler
    - Implement processData method with existing filter logic
    - _Requirements: 3.3, 3.4, 3.5_
  
  - [x] 5.3 Refactor Score Lambda to extend WorkflowLambdaHandler
    - Implement processData method with existing scoring logic
    - _Requirements: 3.3, 3.4, 3.5_
  
  - [x] 5.4 Refactor Store Lambda to extend WorkflowLambdaHandler
    - Implement processData method with existing storage logic
    - _Requirements: 3.3, 3.4, 3.5_
  
  - [x] 5.5 Refactor Reactive Lambda to extend WorkflowLambdaHandler
    - Implement processData method with existing reactive logic
    - _Requirements: 3.3, 3.4, 3.5_
  
  - [ ]* 5.6 Write property test for no hardcoded dependencies
    - **Property 9: No Hardcoded Dependencies**
    - **Validates: Requirements 3.3**
  
  - [ ]* 5.7 Write property test for stage reordering independence
    - **Property 10: Stage Reordering Independence**
    - **Validates: Requirements 3.4**
  
  - [ ]* 5.8 Write property test for independent testability
    - **Property 11: Independent Testability**
    - **Validates: Requirements 3.5**

- [-] 5.9 Git commit and push Task 5 changes
  - Commit message: "refactor: migrate Lambda functions to use base handler"
  - Push to remote repository

- [~] 6. Implement Express workflow CDK infrastructure
  - [ ] 6.1 Create WorkflowType enum and configuration data classes
    - Define WorkflowType (EXPRESS, STANDARD)
    - Define WorkflowConfiguration with all required fields
    - Define WorkflowStepType sealed class (Lambda, Glue)
    - _Requirements: 6.3_
  
  - [ ] 6.2 Implement createExpressWorkflow function
    - Create Lambda invoke tasks with execution context payload
    - Chain tasks sequentially
    - Configure retry logic (2 attempts, 20s interval, 2x backoff)
    - Set resultPath to DISCARD
    - Enable CloudWatch Logs and X-Ray tracing
    - _Requirements: 4.1, 4.4, 4.5, 4.6, 4.7_
  
  - [ ] 6.3 Create EventBridge Pipe for Express workflow
    - Connect SQS queue to Step Function
    - Use REQUEST_RESPONSE invocation type
    - Configure batch size = 1
    - _Requirements: 4.2_
  
  - [ ]* 6.4 Write property test for Express workflow configuration
    - **Property 12: Express Workflow Configuration**
    - **Validates: Requirements 4.1, 4.7**
  
  - [ ]* 6.5 Write property test for Express workflow invocation type
    - **Property 13: Express Workflow Invocation Type**
    - **Validates: Requirements 4.2**
  
  - [ ]* 6.6 Write property test for Lambda retry configuration
    - **Property 16: Lambda Retry Configuration**
    - **Validates: Requirements 4.5, 8.1**
  
  - [ ]* 6.7 Write property test for result path discard
    - **Property 17: Result Path Discard**
    - **Validates: Requirements 4.6**
  
  - [ ]* 6.8 Write unit tests for Express workflow CDK
    - Test Step Function resource properties
    - Test EventBridge Pipe configuration
    - Test IAM permissions
    - _Requirements: 12.4_

- [ ] 6.9 Git commit and push Task 6 changes
  - Commit message: "feat: implement Express workflow CDK infrastructure"
  - Push to remote repository

- [~] 7. Checkpoint - Validate Express workflow
  - Ensure all tests pass, ask the user if questions arise.

- [~] 8. Implement Standard workflow CDK infrastructure
  - [ ] 8.1 Implement createStandardWorkflow function
    - Support mixing Lambda and Glue steps
    - Create Lambda invoke tasks with execution context
    - Create Glue job tasks with RUN_JOB integration pattern
    - Chain all steps sequentially
    - Configure retry logic (Lambda: 20s, Glue: 5min)
    - Enable CloudWatch Logs and X-Ray tracing
    - _Requirements: 5.1, 5.3, 5.4, 5.5, 5.7_
  
  - [ ] 8.2 Create EventBridge Pipe for Standard workflow
    - Connect SQS queue to Step Function
    - Use FIRE_AND_FORGET invocation type
    - Configure batch size = 1
    - _Requirements: 5.2_
  
  - [ ] 8.3 Create EventBridge rule for failure detection
    - Monitor execution status changes (FAILED, TIMED_OUT, ABORTED)
    - Trigger SNS topic or Lambda for alerts
    - _Requirements: 8.7_
  
  - [ ]* 8.4 Write property test for Standard workflow configuration
    - **Property 18: Standard Workflow Configuration**
    - **Validates: Requirements 5.1**
  
  - [ ]* 8.5 Write property test for Standard workflow invocation type
    - **Property 19: Standard Workflow Invocation Type**
    - **Validates: Requirements 5.2**
  
  - [ ]* 8.6 Write property test for mixed step type support
    - **Property 20: Mixed Step Type Support**
    - **Validates: Requirements 5.3**
  
  - [ ]* 8.7 Write property test for Glue retry configuration
    - **Property 24: Glue Job Retry Configuration**
    - **Validates: Requirements 5.7, 8.2**
  
  - [ ]* 8.8 Write unit tests for Standard workflow CDK
    - Test Step Function resource properties
    - Test EventBridge Pipe configuration
    - Test EventBridge failure detection rule
    - _Requirements: 12.4_

- [ ] 8.9 Git commit and push Task 8 changes
  - Commit message: "feat: implement Standard workflow CDK infrastructure"
  - Push to remote repository

- [~] 9. Implement Glue job integration
  - [ ] 9.1 Create Glue job PySpark script template
    - Read arguments from Step Functions (execution-id, input/output paths)
    - Read input from S3 using input-bucket and input-key
    - Implement ETL transformation logic
    - Write output to S3 using output-bucket and output-key
    - _Requirements: 5.6, 7.3, 7.4_
  
  - [ ] 9.2 Configure Glue job in CDK
    - Define Glue job with script location
    - Configure DPUs and timeout
    - Grant S3 permissions to Glue job role
    - _Requirements: 7.1, 11.2_
  
  - [ ]* 9.3 Write property test for Glue job integration pattern
    - **Property 21: Glue Job Integration Pattern**
    - **Validates: Requirements 5.4, 7.5**
  
  - [ ]* 9.4 Write property test for Glue job arguments
    - **Property 22: Glue Job Arguments**
    - **Validates: Requirements 5.5, 7.2**
  
  - [ ]* 9.5 Write property test for Glue job I/O convention
    - **Property 23: Glue Job I/O Convention**
    - **Validates: Requirements 5.6, 7.3, 7.4**
  
  - [ ]* 9.6 Write property test for Glue job positioning flexibility
    - **Property 28: Glue Job Positioning Flexibility**
    - **Validates: Requirements 7.1**

- [ ] 9.7 Git commit and push Task 9 changes
  - Commit message: "feat: implement Glue job integration"
  - Push to remote repository

- [~] 10. Implement workflow type selection and validation
  - [ ] 10.1 Add workflow type validation logic
    - Validate Express workflows don't contain Glue steps
    - Provide clear error messages for invalid configurations
    - _Requirements: 6.1, 6.2, 6.4_
  
  - [ ] 10.2 Implement automatic invocation type configuration
    - Set REQUEST_RESPONSE for Express workflows
    - Set FIRE_AND_FORGET for Standard workflows
    - _Requirements: 6.5_
  
  - [ ]* 10.3 Write property test for workflow type selection validation
    - **Property 25: Workflow Type Selection Validation**
    - **Validates: Requirements 6.1, 6.2**
  
  - [ ]* 10.4 Write property test for workflow type configuration interface
    - **Property 26: Workflow Type Configuration Interface**
    - **Validates: Requirements 6.3**
  
  - [ ]* 10.5 Write property test for automatic invocation type configuration
    - **Property 27: Automatic Invocation Type Configuration**
    - **Validates: Requirements 6.5**

- [ ] 10.6 Git commit and push Task 10 changes
  - Commit message: "feat: implement workflow type selection and validation"
  - Push to remote repository

- [~] 11. Checkpoint - Validate Standard workflow and Glue integration
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 12. Implement error handling and retry logic
  - [ ] 12.1 Configure SQS Dead Letter Queue
    - Create DLQ for main workflow queue
    - Set maxReceiveCount = 3
    - _Requirements: 8.5_
  
  - [ ] 12.2 Implement error logging in Lambda handlers
    - Log error details in StageResult
    - Include error type, message, and stack trace
    - _Requirements: 8.4_
  
  - [ ] 12.3 Add error handling for S3 operations
    - Handle access denied (403)
    - Handle object not found (404)
    - Handle throttling (503)
    - Distinguish transient vs permanent errors
    - _Requirements: 8.1, 8.2, 8.3_
  
  - [ ]* 12.4 Write property test for step failure state transition
    - **Property 30: Step Failure State Transition**
    - **Validates: Requirements 8.3**
  
  - [ ]* 12.5 Write property test for error information recording
    - **Property 31: Error Information Recording**
    - **Validates: Requirements 8.4**
  
  - [ ]* 12.6 Write property test for SQS DLQ configuration
    - **Property 32: SQS Dead Letter Queue Configuration**
    - **Validates: Requirements 8.5**
  
  - [ ]* 12.7 Write property test for Express workflow SQS retry
    - **Property 33: Express Workflow SQS Retry**
    - **Validates: Requirements 8.6**
  
  - [ ]* 12.8 Write property test for Standard workflow failure detection
    - **Property 34: Standard Workflow Failure Detection**
    - **Validates: Requirements 8.7**
  
  - [ ]* 12.9 Write property test for Glue job failure handling
    - **Property 29: Glue Job Failure Handling**
    - **Validates: Requirements 7.6**
  
  - [ ]* 12.10 Write unit tests for error handling
    - Test S3 access denied scenario
    - Test S3 object not found scenario
    - Test Lambda timeout scenario
    - Test retry exhaustion scenario
    - _Requirements: 12.7_

- [ ] 12.11 Git commit and push Task 12 changes
  - Commit message: "feat: implement error handling and retry logic"
  - Push to remote repository

- [ ] 13. Implement observability features
  - [ ] 13.1 Configure CloudWatch Logs for Step Functions
    - Enable logs with includeExecutionData=true
    - Set log level to ALL
    - Configure 14-day retention
    - _Requirements: 9.2, 9.3_
  
  - [ ] 13.2 Enable X-Ray tracing for Step Functions
    - Set tracingEnabled=true in Step Function configuration
    - _Requirements: 9.1_
  
  - [ ] 13.3 Add execution context logging to Lambda handlers
    - Log executionId and currentStage at start
    - Log S3 input and output paths
    - _Requirements: 9.5, 9.6_
  
  - [ ]* 13.4 Write property test for observability configuration
    - **Property 35: Observability Configuration**
    - **Validates: Requirements 9.1, 9.2, 9.3**
  
  - [ ]* 13.5 Write property test for Lambda execution context logging
    - **Property 36: Lambda Execution Context Logging**
    - **Validates: Requirements 9.5**
  
  - [ ]* 13.6 Write property test for Lambda S3 path logging
    - **Property 37: Lambda S3 Path Logging**
    - **Validates: Requirements 9.6**

- [ ] 13.7 Git commit and push Task 13 changes
  - Commit message: "feat: implement observability features"
  - Push to remote repository

- [ ] 14. Implement backward compatibility features
  - [ ] 14.1 Add support for dual naming patterns
    - Allow both old (auto-generated) and new (explicit) naming
    - Provide configuration flag to control naming strategy
    - _Requirements: 10.2_
  
  - [ ] 14.2 Implement incremental migration support
    - Allow mixing old and new Lambda implementations
    - Support gradual stage-by-stage migration
    - _Requirements: 10.3_
  
  - [ ]* 14.3 Write property test for dual naming pattern support
    - **Property 38: Dual Naming Pattern Support**
    - **Validates: Requirements 10.2**
  
  - [ ]* 14.4 Write property test for incremental stage migration
    - **Property 39: Incremental Stage Migration**
    - **Validates: Requirements 10.3**

- [ ] 14.5 Git commit and push Task 14 changes
  - Commit message: "feat: implement backward compatibility features"
  - Push to remote repository

- [ ] 15. Implement remaining infrastructure components
  - [ ] 15.1 Configure EventBridge Pipe in CDK
    - Create Pipe resource connecting SQS to Step Function
    - Configure appropriate invocation type based on workflow type
    - _Requirements: 11.4_
  
  - [ ] 15.2 Implement least-privilege IAM roles
    - Create Lambda execution role with minimal S3 permissions
    - Create Glue job role with minimal S3 permissions
    - Create Step Functions role with minimal Lambda/Glue invoke permissions
    - _Requirements: 11.9_
  
  - [ ]* 15.3 Write property test for EventBridge Pipe configuration
    - **Property 41: EventBridge Pipe Configuration**
    - **Validates: Requirements 11.4**
  
  - [ ]* 15.4 Write property test for least-privilege IAM roles
    - **Property 42: Least-Privilege IAM Roles**
    - **Validates: Requirements 11.9**

- [ ] 15.5 Git commit and push Task 15 changes
  - Commit message: "feat: implement remaining infrastructure components"
  - Push to remote repository

- [ ] 16. Checkpoint - Validate complete infrastructure
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 17. Write integration tests
  - [ ] 17.1 Write end-to-end Express workflow test
    - Deploy test stack
    - Send test message to SQS
    - Verify workflow completes successfully
    - Verify S3 outputs exist for all stages
    - Verify final output correctness
    - _Requirements: 12.3_
  
  - [ ] 17.2 Write end-to-end Standard workflow test
    - Deploy test stack with Glue job
    - Send test message to SQS
    - Verify workflow completes successfully
    - Verify Glue job executes and produces output
    - Verify S3 outputs exist for all stages
    - _Requirements: 12.3_
  
  - [ ] 17.3 Write Lambda failure and retry test
    - Inject failure in Lambda (first attempt)
    - Verify workflow retries and succeeds
    - Verify retry count in execution history
    - _Requirements: 12.7_
  
  - [ ] 17.4 Write DLQ handling test
    - Inject permanent failure in Lambda
    - Verify message moves to DLQ after 3 attempts
    - Verify DLQ message contains original payload
    - _Requirements: 12.7_
  
  - [ ] 17.5 Write S3 lifecycle policy verification test
    - Create test execution data
    - Wait 7+ days (or use time travel in test)
    - Verify objects are deleted
    - _Requirements: 12.5_
  
  - [ ] 17.6 Write observability verification test
    - Execute workflow
    - Verify CloudWatch Logs contain execution data
    - Verify X-Ray traces are created
    - Verify Lambda logs contain execution context
    - _Requirements: 12.6_

- [ ] 17.7 Git commit and push Task 17 changes
  - Commit message: "test: add integration tests"
  - Push to remote repository

- [ ] 18. Write remaining property tests
  - [ ]* 18.1 Write property test for S3 organization by execution ID
    - **Property 5: S3 Organization by Execution ID**
    - **Validates: Requirements 2.5**
  
  - [ ]* 18.2 Write property test for S3 output persistence
    - **Property 6: S3 Output Persistence**
    - **Validates: Requirements 2.6**
  
  - [ ]* 18.3 Write property test for Express workflow structure
    - **Property 15: Express Workflow Structure**
    - **Validates: Requirements 4.4**
  
  - [ ]* 18.4 Write property test for Express workflow failure handling
    - **Property 14: Express Workflow Failure Handling**
    - **Validates: Requirements 4.3**

- [ ] 18.5 Git commit and push Task 18 changes
  - Commit message: "test: add remaining property tests"
  - Push to remote repository

- [ ] 19. Final checkpoint - Comprehensive testing
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 20. Documentation and deployment preparation
  - [ ] 20.1 Update deployment documentation
    - Document workflow type selection criteria
    - Document Glue job integration steps
    - Document migration strategy from old to new architecture
    - _Requirements: 10.1, 10.4, 10.5_
  
  - [ ] 20.2 Create runbook for operations
    - Document failure detection and recovery procedures
    - Document DLQ processing procedures
    - Document S3 lifecycle policy management
    - Document CloudWatch monitoring and alerting setup
  
  - [ ] 20.3 Prepare deployment scripts
    - Create CDK deployment commands
    - Create rollback procedures
    - Create smoke test scripts for post-deployment validation

- [ ] 20.4 Git commit and push Task 20 changes
  - Commit message: "docs: update documentation and deployment preparation"
  - Push to remote repository

## Notes

- Tasks marked with `*` are optional property-based tests that can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation and early detection of issues
- Property tests validate universal correctness properties across random inputs
- Unit tests validate specific examples, edge cases, and error conditions
- Integration tests validate end-to-end workflows in deployed environment
- The implementation follows an incremental approach: Lambda naming → S3 foundation → Express workflow → Standard workflow → Glue integration → Error handling → Observability
- Backward compatibility features allow gradual migration without disrupting existing functionality
