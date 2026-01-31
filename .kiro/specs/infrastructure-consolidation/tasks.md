# Implementation Plan: Infrastructure Consolidation

## Overview

This plan consolidates the CEAP platform from 7 CloudFormation stacks to 3 stacks with clear read/write separation. The implementation focuses on template consolidation, resource migration, validation, and testing to ensure a safe transition with no functional changes.

## Tasks

- [x] 1. Audit and document current infrastructure
  - Create inventory of all resources in each of the 7 existing stacks
  - Document all cross-stack references and dependencies
  - Export current CloudFormation templates as backup
  - Create resource mapping from 7-stack to 3-stack architecture
  - _Requirements: 5.1, 5.3_

- [x] 2. Create consolidated CloudFormation templates
  - [x] 2.1 Create CeapDataPlatform-dev template structure
    - Set up template parameters for cross-stack references
    - Add DatabaseStackName parameter
    - Define template metadata and description
    - _Requirements: 1.3, 7.1_
  
  - [x] 2.2 Migrate ETL resources to CeapDataPlatform-dev
    - Copy Lambda function definitions from CeapEtlWorkflow-dev
    - Copy Step Function definitions from CeapEtlWorkflow-dev
    - Copy EventBridge rules from CeapEtlWorkflow-dev
    - Update cross-stack references to use Fn::ImportValue
    - Preserve original logical IDs
    - _Requirements: 1.5, 2.1, 2.2, 2.3, 8.1, 10.1_
  
  - [x] 2.3 Migrate Filter resources to CeapDataPlatform-dev
    - Copy Lambda function definitions from CeapFilterWorkflow-dev
    - Copy Step Function definitions from CeapFilterWorkflow-dev
    - Update cross-stack references to use Fn::ImportValue
    - Preserve original logical IDs
    - _Requirements: 1.5, 2.1, 2.2, 8.1, 10.1_
  
  - [x] 2.4 Migrate Score resources to CeapDataPlatform-dev
    - Copy Lambda function definitions from CeapScoreWorkflow-dev
    - Copy Step Function definitions from CeapScoreWorkflow-dev
    - Update cross-stack references to use Fn::ImportValue
    - Preserve original logical IDs
    - _Requirements: 1.5, 2.1, 2.2, 8.1, 10.1_
  
  - [x] 2.5 Migrate Store resources to CeapDataPlatform-dev
    - Copy Lambda function definitions from CeapStoreWorkflow-dev
    - Copy Step Function definitions from CeapStoreWorkflow-dev
    - Update cross-stack references to use Fn::ImportValue
    - Preserve original logical IDs
    - _Requirements: 1.5, 2.1, 2.2, 8.1, 10.1_
  
  - [x] 2.6 Migrate Orchestration resources to CeapDataPlatform-dev
    - Copy Step Function definitions from CeapOrchestration-dev
    - Copy EventBridge rules from CeapOrchestration-dev
    - Update cross-stack references to use Fn::ImportValue
    - Preserve original logical IDs
    - Add stack outputs for orchestration resources
    - _Requirements: 1.5, 2.2, 2.3, 8.1, 10.1_
  
  - [x] 2.7 Create CeapServingAPI-dev template structure
    - Set up template parameters for cross-stack references
    - Add DatabaseStackName parameter
    - Add DataPlatformStackName parameter
    - Define template metadata and description
    - _Requirements: 1.4, 7.2_
  
  - [x] 2.8 Migrate Reactive resources to CeapServingAPI-dev
    - Copy Lambda function definitions from CeapReactiveWorkflow-dev
    - Copy Step Function definitions from CeapReactiveWorkflow-dev
    - Update cross-stack references to use Fn::ImportValue
    - Preserve original logical IDs
    - Add stack outputs for reactive resources
    - _Requirements: 1.4, 2.1, 2.2, 8.2, 10.2, 10.3_

- [x] 3. Validate consolidated templates
  - [x] 3.1 Run CloudFormation template validation
    - Validate CeapDataPlatform-dev template syntax with cfn-lint
    - Validate CeapServingAPI-dev template syntax with cfn-lint
    - Check for template size limits
    - _Requirements: 5.3_
  
  - [x] 3.2 Write property test for resource consolidation completeness
    - **Property 2: Resource Consolidation Completeness**
    - **Validates: Requirements 1.3, 1.4, 1.5**
  
  - [x] 3.3 Write property test for resource preservation
    - **Property 3: Resource Preservation**
    - **Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.5**
  
  - [x] 3.4 Write property test for logical name preservation
    - **Property 7: Logical Name Preservation**
    - **Validates: Requirements 8.1, 8.2**
  
  - [x] 3.5 Write property test for cross-stack reference mechanisms
    - **Property 9: Cross-Stack Reference Mechanisms**
    - **Validates: Requirements 10.1, 10.2, 10.3**
  
  - [x] 3.6 Write unit tests for template structure
    - Test that CeapDataPlatform-dev contains all expected resource types
    - Test that CeapServingAPI-dev contains all expected resource types
    - Test that stack parameters are correctly defined
    - Test that stack outputs are correctly defined
    - _Requirements: 1.3, 1.4, 7.1, 7.2_

- [x] 4. Checkpoint - Ensure all validation tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Create migration and deployment scripts
  - [x] 5.1 Create deployment script for 3-stack architecture
    - Script to deploy CeapDatabase-dev first
    - Script to deploy CeapDataPlatform-dev and CeapServingAPI-dev in parallel
    - Add parameter passing for cross-stack references
    - Add deployment status monitoring
    - _Requirements: 7.3, 7.4_
  
  - [x] 5.2 Create resource validation script
    - Script to compare resources between old and new stacks
    - Generate detailed resource inventory reports
    - Validate resource counts match
    - Validate resource configurations match
    - _Requirements: 5.3_
  
  - [x] 5.3 Create rollback script
    - Script to redeploy original 7-stack configuration
    - Add validation checks before rollback
    - Add rollback status monitoring
    - Document rollback procedure
    - _Requirements: 5.1, 5.5_
  
  - [x] 5.4 Write property test for resource validation before cleanup
    - **Property 5: Resource Validation Before Cleanup**
    - **Validates: Requirements 5.3**
  
  - [x] 5.5 Write unit tests for migration scripts
    - Test deployment script logic
    - Test validation script logic
    - Test rollback script logic
    - Test error handling in scripts
    - _Requirements: 5.1, 5.3, 5.5_

- [x] 6. Deploy to test environment
  - [x] 6.1 Deploy 3-stack architecture to test environment
    - Run deployment script in test AWS account
    - Monitor deployment progress
    - Verify all stacks deploy successfully
    - Capture deployment timing metrics
    - _Requirements: 1.1, 1.2, 1.3, 1.4_
  
  - [x] 6.2 Run resource validation
    - Execute validation script to compare old vs new stacks
    - Review resource inventory reports
    - Verify all resources migrated correctly
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_
  
  - [x] 6.3 Write property test for functional equivalence
    - **Property 4: Functional Equivalence**
    - **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**
  
  - [x] 6.4 Run integration tests
    - Execute ETL workflows and verify outputs
    - Execute Filter workflows and verify outputs
    - Execute Score workflows and verify outputs
    - Execute Store workflows and verify outputs
    - Execute Reactive workflows and verify outputs
    - Execute Orchestration workflows and verify outputs
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_
  
  - [x] 6.5 Test rollback procedure
    - Execute rollback script in test environment
    - Verify original 7-stack configuration restored
    - Run integration tests on restored stacks
    - _Requirements: 5.1_

- [x] 7. Checkpoint - Verify test environment success
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Update documentation
  - [x] 8.1 Update infrastructure documentation
    - Update INFRASTRUCTURE-INVENTORY.md with 3-stack architecture
    - Update stepfunction-architecture.md with new stack references
    - Update deployment guides with 3-stack procedures
    - _Requirements: 6.1, 6.3_
  
  - [x] 8.2 Update configuration guides
    - Update SCORING-CONFIGURATION-GUIDE.md with new stack names
    - Update NOTIFICATION-CONFIGURATION-GUIDE.md with new stack names
    - Update STORAGE-CONFIGURATION-GUIDE.md with new stack names
    - Update MULTI-TENANCY-GUIDE.md with new stack names
    - _Requirements: 6.4_
  
  - [x] 8.3 Write property test for documentation reference cleanup
    - **Property 6: Documentation Reference Cleanup**
    - **Validates: Requirements 6.4**
  
  - [x] 8.4 Create migration runbook
    - Document step-by-step migration procedure
    - Include pre-migration checklist
    - Include post-migration validation steps
    - Include rollback procedure
    - _Requirements: 5.4, 6.5_

- [x] 9. Prepare production deployment
  - [x] 9.1 Create production deployment plan
    - Define deployment window
    - Identify stakeholders to notify
    - Create communication plan
    - Define success criteria
    - Define rollback triggers
    - _Requirements: 5.1, 5.2_
  
  - [x] 9.2 Review deployment checklist
    - Verify all tests pass in test environment
    - Verify documentation is updated
    - Verify rollback procedure is tested
    - Verify stakeholders are notified
    - Verify deployment runbook is complete
    - _Requirements: 5.3, 5.4_
  
  - [x] 9.3 Write property test for cross-stack reference validation
    - **Property 10: Cross-Stack Reference Validation**
    - **Validates: Requirements 10.4**

- [x] 10. Final checkpoint - Production readiness
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at key milestones
- Property tests validate universal correctness properties across all resources
- Unit tests validate specific template structures and migration logic
- Integration tests verify functional equivalence between old and new architectures
- The actual production deployment is not included as it requires manual approval and coordination
