# Implementation Plan: Customer Engagement & Action Platform (CEAP)

> **Platform Rebranding Note**: This platform was formerly known as the "General Solicitation Platform". We've rebranded to "Customer Engagement & Action Platform (CEAP)" to better reflect its capabilities beyond solicitation. This is a documentation update only—package names and code remain unchanged.

## Overview

This implementation plan breaks down the Customer Engagement & Action Platform (CEAP) into incremental, testable tasks. The platform will be built using Java with AWS services (Lambda, DynamoDB, Step Functions, EventBridge). We'll follow a layered approach, starting with core data models and storage, then adding scoring, filtering, serving, and finally channel delivery.

The implementation follows a phased approach:
1. Foundation: Core models, storage, and data ingestion
2. Intelligence: Scoring engine and filtering pipeline
3. Serving: Low-latency API for candidate retrieval
4. Delivery: Channel adapters and workflow orchestration
5. Operations: Configuration, observability, and migration support

## Tasks

- [x] 1. Set up project structure and core infrastructure
  - Create Maven/Gradle project with AWS SDK dependencies
  - Set up DynamoDB table definitions (CDK/CloudFormation)
  - Configure AWS Lambda runtime and deployment pipeline
  - Set up logging framework (SLF4J + CloudWatch)
  - _Requirements: All (foundational)_

- [x] 2. Implement core data models
  - [x] 2.1 Create Candidate model with all fields
    - Implement Context, Subject, Score, CandidateAttributes, CandidateMetadata classes
    - Add JSON serialization/deserialization annotations
    - Implement validation for required fields
    - _Requirements: 2.1, 2.2, 2.3, 2.4_
  
  - [x]* 2.2 Write property test for candidate model completeness
    - **Property 2: Candidate model completeness**
    - **Validates: Requirements 2.1, 2.2**
  
  - [x]* 2.3 Write property test for context extensibility
    - **Property 3: Context extensibility**
    - **Validates: Requirements 1.3, 2.3**
  
  - [x] 2.4 Create configuration models (ProgramConfig, FilterConfig, ChannelConfig)
    - Implement program registry data structures
    - Add validation logic for configuration fields
    - _Requirements: 10.1, 10.2_
  
  - [x]* 2.5 Write property test for program configuration validation
    - **Property 30: Program configuration validation**
    - **Validates: Requirements 10.1**



- [x] 3. Implement DynamoDB storage layer
  - [x] 3.1 Create DynamoDB repository interface and implementation
    - Implement CRUD operations (create, read, update, delete)
    - Add batch write support with DynamoDB batch limits
    - Implement query operations using primary key and GSIs
    - Add optimistic locking using version numbers
    - _Requirements: 5.1, 5.2, 5.3, 5.5_
  
  - [x]* 3.2 Write property test for storage round-trip consistency
    - **Property 12: Storage round-trip consistency**
    - **Validates: Requirements 5.2, 2.1**
  
  - [x]* 3.3 Write property test for query filtering correctness
    - **Property 13: Query filtering correctness**
    - **Validates: Requirements 5.3, 6.2**
  
  - [x]* 3.4 Write property test for optimistic locking
    - **Property 14: Optimistic locking conflict detection**
    - **Validates: Requirements 5.5**
  
  - [x]* 3.5 Write property test for batch write atomicity
    - **Property 15: Batch write atomicity**
    - **Validates: Requirements 5.2**
  
  - [x] 3.6 Implement TTL configuration logic
    - Add TTL calculation based on program configuration
    - Set TTL attribute on candidate creation
    - _Requirements: 17.1_
  
  - [x]* 3.7 Write property test for TTL configuration
    - **Property 51: TTL configuration**
    - **Validates: Requirements 17.1**

- [x] 4. Checkpoint - Ensure storage layer tests pass
  - Ensure all tests pass, ask the user if questions arise.



- [x] 5. Implement data connector framework
  - [x] 5.1 Create DataConnector interface
    - Define interface methods (getName, validateConfig, extractData, transformToCandidate)
    - Create base abstract class with common validation logic
    - _Requirements: 1.1, 1.2_
  
  - [x] 5.2 Implement data warehouse connector
    - Implement Athena/Glue integration for data warehouse queries
    - Add field mapping configuration support
    - Implement transformation to unified candidate model
    - _Requirements: 1.2, 1.3_
  
  - [x]* 5.3 Write property test for transformation preserves semantics
    - **Property 1: Data connector transformation preserves semantics**
    - **Validates: Requirements 1.2**
  
  - [x] 5.4 Add schema validation logic
    - Implement JSON Schema validation for source data
    - Add detailed error logging for validation failures
    - _Requirements: 1.4, 16.1, 16.2, 16.3_
  
  - [x]* 5.5 Write property test for required field validation
    - **Property 49: Required field validation**
    - **Validates: Requirements 16.1, 16.3**
  
  - [x]* 5.6 Write property test for date format validation
    - **Property 50: Date format validation**
    - **Validates: Requirements 16.2, 16.3**

- [x] 6. Implement scoring engine layer
  - [x] 6.1 Create ScoringProvider interface
    - Define interface methods (getModelId, scoreCandidate, scoreBatch, healthCheck)
    - Add fallback score support
    - _Requirements: 3.1, 3.4_
  
  - [x] 6.2 Implement score caching in DynamoDB
    - Create score cache table schema
    - Implement cache read/write with TTL
    - Add cache invalidation logic
    - _Requirements: 3.5_
  
  - [x]* 6.3 Write property test for score caching consistency
    - **Property 6: Score caching consistency**
    - **Validates: Requirements 3.5**
  
  - [x] 6.4 Implement feature store integration
    - Create feature retrieval client
    - Add feature validation against required features
    - _Requirements: 3.2_
  
  - [x]* 6.5 Write property test for feature retrieval completeness
    - **Property 8: Feature retrieval completeness**
    - **Validates: Requirements 3.2**
  
  - [x] 6.6 Implement multi-model scoring support
    - Add logic to execute multiple scoring models per candidate
    - Store scores with modelId, value, confidence, timestamp
    - _Requirements: 3.3_
  
  - [x]* 6.7 Write property test for multi-model scoring independence
    - **Property 5: Multi-model scoring independence**
    - **Validates: Requirements 3.3**
  
  - [x] 6.8 Add scoring fallback logic with circuit breaker
    - Implement circuit breaker pattern for model endpoints
    - Add fallback to cached scores or default values
    - Add failure logging
    - _Requirements: 3.4, 9.3_
  
  - [x]* 6.9 Write property test for scoring fallback correctness
    - **Property 7: Scoring fallback correctness**
    - **Validates: Requirements 3.4, 9.3**



- [x] 7. Implement filtering and eligibility pipeline
  - [x] 7.1 Create Filter interface
    - Define interface methods (getFilterId, getFilterType, filter, configure)
    - Create FilterResult and RejectedCandidate models
    - _Requirements: 4.1, 4.2_
  
  - [x] 7.2 Implement filter chain executor
    - Add logic to execute filters in configured order
    - Implement rejection tracking with reasons
    - Add parallel execution support where applicable
    - _Requirements: 4.1, 4.2, 4.4, 4.6_
  
  - [x]* 7.3 Write property test for filter chain ordering
    - **Property 9: Filter chain ordering**
    - **Validates: Requirements 4.1**
  
  - [x]* 7.4 Write property test for rejection tracking completeness
    - **Property 10: Rejection tracking completeness**
    - **Validates: Requirements 4.2, 4.6**
  
  - [x]* 7.5 Write property test for eligibility marking
    - **Property 11: Eligibility marking**
    - **Validates: Requirements 4.4**
  
  - [x] 7.6 Implement concrete filter types
    - Create trust filter implementation
    - Create eligibility filter implementation
    - Create business rule filter implementation
    - Create quality filter implementation
    - _Requirements: 4.3_

- [x] 8. Checkpoint - Ensure scoring and filtering tests pass
  - Ensure all tests pass, ask the user if questions arise.



- [x] 9. Implement serving API
  - [x] 9.1 Create ServingAPI interface and Lambda handler
    - Implement GetCandidatesForCustomer endpoint
    - Implement GetCandidatesForCustomers batch endpoint
    - Add request validation
    - _Requirements: 6.1, 6.2, 6.6_
  
  - [x] 9.2 Implement channel-specific ranking logic
    - Create ranking algorithm framework
    - Implement ranking strategies per channel
    - _Requirements: 6.3_
  
  - [x]* 9.3 Write property test for ranking consistency
    - **Property 17: Channel-specific ranking consistency**
    - **Validates: Requirements 6.3**
  
  - [x] 9.4 Add real-time eligibility refresh support
    - Implement eligibility check with staleness detection
    - Add refresh logic for stale candidates
    - _Requirements: 6.4_
  
  - [x]* 9.5 Write property test for eligibility refresh correctness
    - **Property 18: Eligibility refresh correctness**
    - **Validates: Requirements 6.4**
  
  - [x] 9.6 Implement fallback and graceful degradation
    - Add circuit breakers for dependencies
    - Implement fallback to cached results
    - Add degradation logging
    - _Requirements: 6.5_
  
  - [x]* 9.7 Write property test for serving API fallback behavior
    - **Property 19: Serving API fallback behavior**
    - **Validates: Requirements 6.5**
  
  - [x]* 9.8 Write property test for batch query correctness
    - **Property 20: Batch query correctness**
    - **Validates: Requirements 6.6**



- [x] 10. Implement channel adapter framework
  - [x] 10.1 Create ChannelAdapter interface
    - Define interface methods (getChannelId, deliver, configure, healthCheck, isShadowMode)
    - Create DeliveryResult and DeliveredCandidate models
    - _Requirements: 7.1, 7.2, 7.3_
  
  - [x]* 10.2 Write property test for channel adapter interface compliance
    - **Property 21: Channel adapter interface compliance**
    - **Validates: Requirements 7.1**
  
  - [x]* 10.3 Write property test for delivery status tracking
    - **Property 22: Delivery status tracking**
    - **Validates: Requirements 7.3**
  
  - [x] 10.4 Implement shadow mode support
    - Add shadow mode flag to adapter configuration
    - Implement logging without actual delivery
    - _Requirements: 7.5, 14.5_
  
  - [x]* 10.5 Write property test for shadow mode non-delivery
    - **Property 23: Shadow mode non-delivery**
    - **Validates: Requirements 7.5, 14.5**
  
  - [x] 10.6 Implement rate limiting and queueing
    - Add rate limit tracking per channel
    - Implement queue for rate-limited candidates
    - _Requirements: 7.6_
  
  - [x]* 10.7 Write property test for rate limiting queue behavior
    - **Property 24: Rate limiting queue behavior**
    - **Validates: Requirements 7.6**

- [x] 11. Implement email channel adapter
  - [x] 11.1 Create email channel adapter implementation
    - Integrate with email campaign service
    - Implement campaign creation automation
    - Add template management per program
    - _Requirements: 14.1, 14.2_
  
  - [x]* 11.2 Write property test for email campaign automation
    - **Property 42: Email campaign automation**
    - **Validates: Requirements 14.1**
  
  - [x]* 11.3 Write property test for program-specific email templates
    - **Property 43: Program-specific email templates**
    - **Validates: Requirements 14.2**
  
  - [x] 11.4 Implement opt-out enforcement
    - Add opt-out check before campaign creation
    - Exclude opted-out customers from recipient lists
    - _Requirements: 14.3, 18.5_
  
  - [x]* 11.5 Write property test for opt-out enforcement
    - **Property 44: Opt-out enforcement**
    - **Validates: Requirements 14.3**
  
  - [x] 11.6 Implement frequency capping
    - Track email sends per customer
    - Enforce frequency limits per program configuration
    - _Requirements: 14.4, 18.6_
  
  - [x]* 11.7 Write property test for email frequency capping
    - **Property 45: Email frequency capping**
    - **Validates: Requirements 14.4**
  
  - [x] 11.8 Add delivery tracking
    - Record delivery status and timestamps
    - Track open rates and engagement metrics
    - _Requirements: 14.6_
  
  - [x]* 11.9 Write property test for email delivery tracking
    - **Property 46: Email delivery tracking**
    - **Validates: Requirements 14.6**



- [x] 12. Checkpoint - Ensure serving and channel tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 13. Implement batch ingestion workflow
  - [x] 13.1 Create Step Functions workflow definition
    - Define workflow states (ETL, Filter, Score, Store)
    - Add error handling and retry logic
    - Configure parallel execution where applicable
    - _Requirements: 8.1, 8.3_
  
  - [x] 13.2 Implement ETL Lambda function
    - Use data connector to extract and transform data
    - Batch candidates for downstream processing
    - _Requirements: 1.2, 8.1_
  
  - [x] 13.3 Implement filter Lambda function
    - Execute filter chain on candidate batches
    - Track rejection reasons
    - _Requirements: 4.1, 4.2, 8.1_
  
  - [x] 13.4 Implement scoring Lambda function
    - Execute scoring for candidate batches
    - Handle scoring failures with fallbacks
    - _Requirements: 3.2, 3.3, 8.1_
  
  - [x] 13.5 Implement storage Lambda function
    - Batch write candidates to DynamoDB
    - Handle write failures and retries
    - _Requirements: 5.2, 8.1_
  
  - [x] 13.6 Add workflow metrics publishing
    - Publish metrics at each workflow stage
    - Track processed, passed, rejected counts
    - _Requirements: 8.4, 12.1_
  
  - [x]* 13.7 Write property test for workflow metrics publishing
    - **Property 26: Workflow metrics publishing**
    - **Validates: Requirements 8.4, 12.1**
  
  - [x] 13.8 Implement retry with exponential backoff
    - Configure Step Functions retry policy
    - Add exponential backoff delays
    - _Requirements: 8.3_
  
  - [x]* 13.9 Write property test for workflow retry with exponential backoff
    - **Property 25: Workflow retry with exponential backoff**
    - **Validates: Requirements 8.3**
  
  - [x] 13.10 Add workflow completion triggers
    - Publish completion metrics
    - Trigger downstream processes (data warehouse export)
    - _Requirements: 8.6_
  
  - [x]* 13.11 Write property test for workflow completion triggers
    - **Property 27: Workflow completion triggers**
    - **Validates: Requirements 8.6**



- [x] 14. Implement reactive solicitation workflow
  - [x] 14.1 Create EventBridge rule for customer events
    - Configure event pattern matching
    - Route events to reactive Lambda
    - _Requirements: 9.1, 9.2_
  
  - [x] 14.2 Implement reactive Lambda function
    - Execute filtering and scoring in real-time
    - Create and store eligible candidates immediately
    - _Requirements: 9.2, 9.3, 9.4_
  
  - [x]* 14.3 Write property test for reactive candidate creation
    - **Property 28: Reactive candidate creation**
    - **Validates: Requirements 9.4**
  
  - [x] 14.4 Implement event deduplication
    - Track recent events per customer-subject pair
    - Deduplicate within configured time window
    - _Requirements: 9.5_
  
  - [x]* 14.5 Write property test for event deduplication within window
    - **Property 29: Event deduplication within window**
    - **Validates: Requirements 9.5**

- [x] 15. Implement program configuration management
  - [x] 15.1 Create program registry DynamoDB table
    - Define table schema for program configurations
    - Add GSIs for querying by marketplace
    - _Requirements: 10.1, 10.2_
  
  - [x] 15.2 Implement program configuration API
    - Add CRUD operations for program configs
    - Implement configuration validation
    - _Requirements: 10.1, 10.2_
  
  - [x]* 15.3 Write property test for program configuration completeness
    - **Property 31: Program configuration completeness**
    - **Validates: Requirements 10.2**
  
  - [x] 15.4 Implement program enable/disable logic
    - Add disable flag to program configuration
    - Skip workflows for disabled programs
    - Prevent candidate creation for disabled programs
    - _Requirements: 10.3_
  
  - [x]* 15.5 Write property test for program disable enforcement
    - **Property 32: Program disable enforcement**
    - **Validates: Requirements 10.3**
  
  - [x] 15.6 Add marketplace configuration overrides
    - Support per-marketplace config overrides
    - Apply overrides in precedence order
    - _Requirements: 10.4_
  
  - [x]* 15.7 Write property test for marketplace configuration override
    - **Property 33: Marketplace configuration override**
    - **Validates: Requirements 10.4**



- [x] 16. Implement experimentation framework
  - [x] 16.1 Create experiment configuration model
    - Define experiment config structure
    - Add treatment group definitions
    - _Requirements: 11.1, 11.2_
  
  - [x] 16.2 Implement deterministic treatment assignment
    - Use consistent hashing for customer assignment
    - Ensure same customer always gets same treatment
    - _Requirements: 11.1_
  
  - [x]* 16.3 Write property test for deterministic treatment assignment
    - **Property 34: Deterministic treatment assignment**
    - **Validates: Requirements 11.1**
  
  - [x] 16.4 Add treatment recording to candidates
    - Record assigned treatment in candidate metadata
    - _Requirements: 11.3_
  
  - [x]* 16.5 Write property test for treatment recording
    - **Property 35: Treatment recording**
    - **Validates: Requirements 11.3**
  
  - [x] 16.6 Implement treatment-specific metrics collection
    - Collect metrics per treatment group
    - Enable comparison of treatment performance
    - _Requirements: 11.4_
  
  - [x]* 16.7 Write property test for treatment-specific metrics collection
    - **Property 36: Treatment-specific metrics collection**
    - **Validates: Requirements 11.4**

- [x] 17. Checkpoint - Ensure workflow and configuration tests pass
  - Ensure all tests pass, ask the user if questions arise.



- [x] 18. Implement observability and monitoring
  - [x] 18.1 Add structured logging with correlation IDs
    - Implement correlation ID generation and propagation
    - Add structured log format with context
    - Log failures with error details
    - _Requirements: 12.2_
  
  - [x]* 18.2 Write property test for structured logging with correlation
    - **Property 37: Structured logging with correlation**
    - **Validates: Requirements 12.2**
  
  - [x] 18.3 Implement rejection reason aggregation
    - Aggregate rejections by filter type and reason code
    - Publish aggregated metrics to CloudWatch
    - _Requirements: 12.3_
  
  - [x]* 18.4 Write property test for rejection reason aggregation
    - **Property 38: Rejection reason aggregation**
    - **Validates: Requirements 12.3**
  
  - [x] 18.5 Create CloudWatch dashboards
    - Per-program health dashboard
    - Per-channel performance dashboard
    - Cost and capacity dashboard
    - _Requirements: 12.5_
  
  - [x] 18.6 Configure CloudWatch alarms
    - API latency alarms
    - Workflow failure alarms
    - Data quality alarms
    - _Requirements: 12.4, 12.6_

- [x] 19. Implement multi-program isolation
  - [x] 19.1 Add program failure isolation
    - Ensure program workflows are independent
    - Prevent cascading failures across programs
    - _Requirements: 13.1_
  
  - [x]* 19.2 Write property test for program failure isolation
    - **Property 39: Program failure isolation**
    - **Validates: Requirements 13.1**
  
  - [x] 19.3 Implement program-specific throttling
    - Track rate limits per program
    - Throttle only the exceeding program
    - _Requirements: 13.3_
  
  - [x]* 19.4 Write property test for program-specific throttling
    - **Property 40: Program-specific throttling**
    - **Validates: Requirements 13.3**
  
  - [x] 19.5 Add program cost attribution
    - Tag resources with program ID
    - Track costs per program
    - Publish cost metrics
    - _Requirements: 13.4_
  
  - [x]* 19.6 Write property test for program cost attribution
    - **Property 41: Program cost attribution**
    - **Validates: Requirements 13.4**



- [x] 20. Implement security and compliance features
  - [x] 20.1 Add PII redaction in logs
    - Identify PII fields (email, phone, address)
    - Implement redaction/masking before logging
    - _Requirements: 18.4_
  
  - [x]* 20.2 Write property test for PII redaction in logs
    - **Property 55: PII redaction in logs**
    - **Validates: Requirements 18.4**
  
  - [x] 20.3 Implement opt-out candidate deletion
    - Create opt-out event handler
    - Delete all candidates for opted-out customer
    - Complete deletion within 24 hours
    - _Requirements: 18.5_
  
  - [x]* 20.4 Write property test for opt-out candidate deletion
    - **Property 56: Opt-out candidate deletion**
    - **Validates: Requirements 18.5**
  
  - [x] 20.5 Add email compliance features
    - Include unsubscribe link in all emails
    - Enforce frequency preferences
    - _Requirements: 18.6_
  
  - [x]* 20.6 Write property test for email compliance
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



- [x] 21. Implement candidate lifecycle management
  - [x] 21.1 Add manual candidate deletion API
    - Implement delete endpoint
    - Verify deletion removes candidate from storage
    - _Requirements: 17.3_
  
  - [x]* 21.2 Write property test for manual deletion
    - **Property 52: Manual deletion**
    - **Validates: Requirements 17.3**
  
  - [x] 21.3 Implement consumed marking
    - Mark candidates as consumed after delivery
    - Record delivery timestamp
    - _Requirements: 17.4_
  
  - [x]* 21.4 Write property test for consumed marking
    - **Property 53: Consumed marking**
    - **Validates: Requirements 17.4**
  
  - [x] 21.5 Add candidate refresh functionality
    - Implement re-scoring for active candidates
    - Implement eligibility refresh
    - Update candidate with current values
    - _Requirements: 17.5_
  
  - [x]* 21.6 Write property test for candidate refresh
    - **Property 54: Candidate refresh**
    - **Validates: Requirements 17.5**
  
  - [x] 21.7 Implement data warehouse export
    - Create daily export Lambda function
    - Export candidates to S3 in Parquet format
    - Trigger Glue job to load into data warehouse
    - _Requirements: 5.6_
  
  - [x]* 21.8 Write property test for data warehouse export completeness
    - **Property 16: Data warehouse export completeness**
    - **Validates: Requirements 5.6**

- [x] 22. Checkpoint - Ensure lifecycle and security tests pass
  - Ensure all tests pass, ask the user if questions arise.



- [x] 23. Implement real-time channel features
  - [x] 23.1 Create in-app channel adapter
    - Implement serving API integration
    - Add in-app specific formatting
    - _Requirements: 15.2_
  
  - [x] 23.2 Create push notification channel adapter
    - Integrate with push notification service
    - Add push-specific formatting
    - _Requirements: 15.2_
  
  - [x] 23.3 Create voice assistant channel adapter
    - Integrate with voice assistant service
    - Add voice-specific formatting
    - _Requirements: 15.2_
  
  - [x] 23.4 Implement personalized ranking with context
    - Use customer history and preferences for ranking
    - Apply context-aware ranking algorithms
    - _Requirements: 15.4_
  
  - [x]* 23.5 Write property test for personalized ranking with context
    - **Property 47: Personalized ranking with context**
    - **Validates: Requirements 15.4**
  
  - [x] 23.6 Add A/B test integration to serving API
    - Return treatment-specific candidates
    - Ensure treatment consistency
    - _Requirements: 15.5_
  
  - [x]* 23.7 Write property test for treatment-specific candidate serving
    - **Property 48: Treatment-specific candidate serving**
    - **Validates: Requirements 15.5**

- [x] 24. Implement backward compatibility and migration support
  - [x] 24.1 Create v1 API adapter layer
    - Implement v1 API endpoints
    - Translate v1 requests to v2 backend
    - Return v1 response format
    - _Requirements: 20.1, 20.2_
  
  - [x]* 24.2 Write property test for V1 API backward compatibility
    - **Property 58: V1 API backward compatibility**
    - **Validates: Requirements 20.1**
  
  - [x] 24.3 Add v1 usage tracking
    - Record v1 API usage metrics
    - Track endpoint, customer, timestamp
    - _Requirements: 20.3_
  
  - [x]* 24.4 Write property test for V1 usage tracking
    - **Property 59: V1 usage tracking**
    - **Validates: Requirements 20.3**
  
  - [x] 24.5 Implement shadow mode for v2
    - Execute v2 processing in parallel with v1
    - Ensure v2 doesn't affect v1 responses
    - _Requirements: 20.4_
  
  - [x]* 24.6 Write property test for shadow mode isolation
    - **Property 60: Shadow mode isolation**
    - **Validates: Requirements 20.4**



- [x] 25. Implement version monotonicity tracking
  - [x] 25.1 Add version increment logic to candidate updates
    - Ensure version number increases on each update
    - Ensure updatedAt timestamp is current
    - _Requirements: 2.5_
  
  - [x]* 25.2 Write property test for version monotonicity
    - **Property 4: Version monotonicity**
    - **Validates: Requirements 2.5**
    - **Status**: PASSED ✅

- [x] 26. Final integration and end-to-end testing
  - [x]* 26.1 Run end-to-end batch workflow test
    - Test complete flow from data warehouse to storage
    - Verify all stages execute correctly
    - Verify metrics are published
  
  - [x]* 26.2 Run end-to-end reactive workflow test
    - Test event-driven candidate creation
    - Verify sub-second latency
    - Verify candidate availability
  
  - [x]* 26.3 Run end-to-end serving API test
    - Test API with real DynamoDB backend
    - Test various query patterns
    - Verify latency targets
  
  - [x]* 26.4 Run end-to-end channel delivery test
    - Test email campaign creation
    - Test in-app serving
    - Test shadow mode
    - Verify delivery tracking

- [x] 27. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 28. Rename code modules and packages to CEAP
  - Rename all module directories from `solicitation-*` to `ceap-*`
  - Rename all package names from `com.solicitation.*` to `com.ceap.*`
  - Update all imports, references, and configuration files
  - Verify build and all tests pass after renaming
  - _Requirements: All (code consistency with branding)_
  
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

- [x] 29. Documentation audit and cleanup
  - Review all markdown files in the project for accuracy, consistency, and completeness
  - Update outdated information and fix any inconsistencies
  - Ensure all documentation reflects current architecture and branding
  - _Requirements: All (documentation quality)_
  
  - [ ] 29.1 Audit root-level documentation
    - Review and update `README.md`
    - Review and update `TECH-STACK.md`
    - Verify accuracy of project structure descriptions
    - Ensure all links work correctly
  
  - [ ] 29.2 Audit docs/ directory
    - Review `docs/VISUAL-ARCHITECTURE.md`
    - Review `docs/USE-CASES.md`
    - Review `docs/PLATFORM-EXPANSION-VISION.md`
    - Review `docs/EXPANSION-SUMMARY.md`
    - Review `docs/REBRANDING-STRATEGY.md`
    - Review `docs/BRANDING.md`
    - Verify consistency across all documents
  
  - [ ] 29.3 Audit use case documentation
    - Review all files in `docs/usecases/`
    - Review all files in `docs/usecases/expansion/`
    - Verify metrics and success criteria are accurate
    - Ensure all use cases reflect current capabilities
  
  - [ ] 29.4 Audit infrastructure documentation
    - Review `infrastructure/DYNAMODB_SCHEMA.md`
    - Review `infrastructure/LAMBDA_CONFIGURATION.md`
    - Review `infrastructure/LAMBDA_QUICK_REFERENCE.md`
    - Verify accuracy of infrastructure descriptions
  
  - [ ] 29.5 Audit archived documentation
    - Review files in `docs/archive/`
    - Determine if any should be updated or removed
    - Ensure archive is organized and relevant
  
  - [ ] 29.6 Check for documentation gaps
    - Identify missing documentation for implemented features
    - Create list of documentation that needs to be written
    - Prioritize documentation gaps by importance
  
  - [ ] 29.7 Verify cross-references and links
    - Check all internal links between documents
    - Verify all file paths are correct after rebranding
    - Fix any broken links or references
  
  - [ ] 29.8 Update version information
    - Ensure version numbers are consistent
    - Update "last updated" dates where applicable
    - Document current state of implementation
  
  - [ ] 29.9 Review code examples in documentation
    - Verify code examples compile and run
    - Update examples to use CEAP naming if Task 28 completed
    - Ensure examples follow current best practices
  
  - [ ] 29.10 Create documentation improvement plan
    - Document findings from audit
    - Create prioritized list of improvements
    - Estimate effort for each improvement
  
  - [ ] 29.11 Commit documentation updates
    - Stage all changes: `git add docs/ *.md`
    - Create descriptive commit message
    - Commit changes

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties (minimum 100 iterations each)
- Unit tests validate specific examples and edge cases
- Integration tests validate end-to-end flows
- Use JUnit 5 for unit tests and jqwik or QuickTheories for property-based testing in Java
