# Implementation Plan: General Solicitation Platform

## Overview

This implementation plan breaks down the General Solicitation Platform into incremental, testable tasks. The platform will be built using Java with AWS services (Lambda, DynamoDB, Step Functions, EventBridge). We'll follow a layered approach, starting with core data models and storage, then adding scoring, filtering, serving, and finally channel delivery.

The implementation follows a phased approach:
1. Foundation: Core models, storage, and data ingestion
2. Intelligence: Scoring engine and filtering pipeline
3. Serving: Low-latency API for candidate retrieval
4. Delivery: Channel adapters and workflow orchestration
5. Operations: Configuration, observability, and migration support

## Tasks

- [ ] 1. Set up project structure and core infrastructure
  - Create Maven/Gradle project with AWS SDK dependencies
  - Set up DynamoDB table definitions (CDK/CloudFormation)
  - Configure AWS Lambda runtime and deployment pipeline
  - Set up logging framework (SLF4J + CloudWatch)
  - _Requirements: All (foundational)_

- [ ] 2. Implement core data models
  - [ ] 2.1 Create Candidate model with all fields
    - Implement Context, Subject, Score, CandidateAttributes, CandidateMetadata classes
    - Add JSON serialization/deserialization annotations
    - Implement validation for required fields
    - _Requirements: 2.1, 2.2, 2.3, 2.4_
  
  - [ ]* 2.2 Write property test for candidate model completeness
    - **Property 2: Candidate model completeness**
    - **Validates: Requirements 2.1, 2.2**
  
  - [ ]* 2.3 Write property test for context extensibility
    - **Property 3: Context extensibility**
    - **Validates: Requirements 1.3, 2.3**
  
  - [ ] 2.4 Create configuration models (ProgramConfig, FilterConfig, ChannelConfig)
    - Implement program registry data structures
    - Add validation logic for configuration fields
    - _Requirements: 10.1, 10.2_
  
  - [ ]* 2.5 Write property test for program configuration validation
    - **Property 30: Program configuration validation**
    - **Validates: Requirements 10.1**



- [ ] 3. Implement DynamoDB storage layer
  - [ ] 3.1 Create DynamoDB repository interface and implementation
    - Implement CRUD operations (create, read, update, delete)
    - Add batch write support with DynamoDB batch limits
    - Implement query operations using primary key and GSIs
    - Add optimistic locking using version numbers
    - _Requirements: 5.1, 5.2, 5.3, 5.5_
  
  - [ ]* 3.2 Write property test for storage round-trip consistency
    - **Property 12: Storage round-trip consistency**
    - **Validates: Requirements 5.2, 2.1**
  
  - [ ]* 3.3 Write property test for query filtering correctness
    - **Property 13: Query filtering correctness**
    - **Validates: Requirements 5.3, 6.2**
  
  - [ ]* 3.4 Write property test for optimistic locking
    - **Property 14: Optimistic locking conflict detection**
    - **Validates: Requirements 5.5**
  
  - [ ]* 3.5 Write property test for batch write atomicity
    - **Property 15: Batch write atomicity**
    - **Validates: Requirements 5.2**
  
  - [ ] 3.6 Implement TTL configuration logic
    - Add TTL calculation based on program configuration
    - Set TTL attribute on candidate creation
    - _Requirements: 17.1_
  
  - [ ]* 3.7 Write property test for TTL configuration
    - **Property 51: TTL configuration**
    - **Validates: Requirements 17.1**

- [ ] 4. Checkpoint - Ensure storage layer tests pass
  - Ensure all tests pass, ask the user if questions arise.



- [ ] 5. Implement data connector framework
  - [ ] 5.1 Create DataConnector interface
    - Define interface methods (getName, validateConfig, extractData, transformToCandidate)
    - Create base abstract class with common validation logic
    - _Requirements: 1.1, 1.2_
  
  - [ ] 5.2 Implement data warehouse connector
    - Implement Athena/Glue integration for data warehouse queries
    - Add field mapping configuration support
    - Implement transformation to unified candidate model
    - _Requirements: 1.2, 1.3_
  
  - [ ]* 5.3 Write property test for transformation preserves semantics
    - **Property 1: Data connector transformation preserves semantics**
    - **Validates: Requirements 1.2**
  
  - [ ] 5.4 Add schema validation logic
    - Implement JSON Schema validation for source data
    - Add detailed error logging for validation failures
    - _Requirements: 1.4, 16.1, 16.2, 16.3_
  
  - [ ]* 5.5 Write property test for required field validation
    - **Property 49: Required field validation**
    - **Validates: Requirements 16.1, 16.3**
  
  - [ ]* 5.6 Write property test for date format validation
    - **Property 50: Date format validation**
    - **Validates: Requirements 16.2, 16.3**

- [ ] 6. Implement scoring engine layer
  - [ ] 6.1 Create ScoringProvider interface
    - Define interface methods (getModelId, scoreCandidate, scoreBatch, healthCheck)
    - Add fallback score support
    - _Requirements: 3.1, 3.4_
  
  - [ ] 6.2 Implement score caching in DynamoDB
    - Create score cache table schema
    - Implement cache read/write with TTL
    - Add cache invalidation logic
    - _Requirements: 3.5_
  
  - [ ]* 6.3 Write property test for score caching consistency
    - **Property 6: Score caching consistency**
    - **Validates: Requirements 3.5**
  
  - [ ] 6.4 Implement feature store integration
    - Create feature retrieval client
    - Add feature validation against required features
    - _Requirements: 3.2_
  
  - [ ]* 6.5 Write property test for feature retrieval completeness
    - **Property 8: Feature retrieval completeness**
    - **Validates: Requirements 3.2**
  
  - [ ] 6.6 Implement multi-model scoring support
    - Add logic to execute multiple scoring models per candidate
    - Store scores with modelId, value, confidence, timestamp
    - _Requirements: 3.3_
  
  - [ ]* 6.7 Write property test for multi-model scoring independence
    - **Property 5: Multi-model scoring independence**
    - **Validates: Requirements 3.3**
  
  - [ ] 6.8 Add scoring fallback logic with circuit breaker
    - Implement circuit breaker pattern for model endpoints
    - Add fallback to cached scores or default values
    - Add failure logging
    - _Requirements: 3.4, 9.3_
  
  - [ ]* 6.9 Write property test for scoring fallback correctness
    - **Property 7: Scoring fallback correctness**
    - **Validates: Requirements 3.4, 9.3**



- [ ] 7. Implement filtering and eligibility pipeline
  - [ ] 7.1 Create Filter interface
    - Define interface methods (getFilterId, getFilterType, filter, configure)
    - Create FilterResult and RejectedCandidate models
    - _Requirements: 4.1, 4.2_
  
  - [ ] 7.2 Implement filter chain executor
    - Add logic to execute filters in configured order
    - Implement rejection tracking with reasons
    - Add parallel execution support where applicable
    - _Requirements: 4.1, 4.2, 4.4, 4.6_
  
  - [ ]* 7.3 Write property test for filter chain ordering
    - **Property 9: Filter chain ordering**
    - **Validates: Requirements 4.1**
  
  - [ ]* 7.4 Write property test for rejection tracking completeness
    - **Property 10: Rejection tracking completeness**
    - **Validates: Requirements 4.2, 4.6**
  
  - [ ]* 7.5 Write property test for eligibility marking
    - **Property 11: Eligibility marking**
    - **Validates: Requirements 4.4**
  
  - [ ] 7.6 Implement concrete filter types
    - Create trust filter implementation
    - Create eligibility filter implementation
    - Create business rule filter implementation
    - Create quality filter implementation
    - _Requirements: 4.3_

- [ ] 8. Checkpoint - Ensure scoring and filtering tests pass
  - Ensure all tests pass, ask the user if questions arise.



- [ ] 9. Implement serving API
  - [ ] 9.1 Create ServingAPI interface and Lambda handler
    - Implement GetCandidatesForCustomer endpoint
    - Implement GetCandidatesForCustomers batch endpoint
    - Add request validation
    - _Requirements: 6.1, 6.2, 6.6_
  
  - [ ] 9.2 Implement channel-specific ranking logic
    - Create ranking algorithm framework
    - Implement ranking strategies per channel
    - _Requirements: 6.3_
  
  - [ ]* 9.3 Write property test for ranking consistency
    - **Property 17: Channel-specific ranking consistency**
    - **Validates: Requirements 6.3**
  
  - [ ] 9.4 Add real-time eligibility refresh support
    - Implement eligibility check with staleness detection
    - Add refresh logic for stale candidates
    - _Requirements: 6.4_
  
  - [ ]* 9.5 Write property test for eligibility refresh correctness
    - **Property 18: Eligibility refresh correctness**
    - **Validates: Requirements 6.4**
  
  - [ ] 9.6 Implement fallback and graceful degradation
    - Add circuit breakers for dependencies
    - Implement fallback to cached results
    - Add degradation logging
    - _Requirements: 6.5_
  
  - [ ]* 9.7 Write property test for serving API fallback behavior
    - **Property 19: Serving API fallback behavior**
    - **Validates: Requirements 6.5**
  
  - [ ]* 9.8 Write property test for batch query correctness
    - **Property 20: Batch query correctness**
    - **Validates: Requirements 6.6**



- [ ] 10. Implement channel adapter framework
  - [ ] 10.1 Create ChannelAdapter interface
    - Define interface methods (getChannelId, deliver, configure, healthCheck, isShadowMode)
    - Create DeliveryResult and DeliveredCandidate models
    - _Requirements: 7.1, 7.2, 7.3_
  
  - [ ]* 10.2 Write property test for channel adapter interface compliance
    - **Property 21: Channel adapter interface compliance**
    - **Validates: Requirements 7.1**
  
  - [ ]* 10.3 Write property test for delivery status tracking
    - **Property 22: Delivery status tracking**
    - **Validates: Requirements 7.3**
  
  - [ ] 10.4 Implement shadow mode support
    - Add shadow mode flag to adapter configuration
    - Implement logging without actual delivery
    - _Requirements: 7.5, 14.5_
  
  - [ ]* 10.5 Write property test for shadow mode non-delivery
    - **Property 23: Shadow mode non-delivery**
    - **Validates: Requirements 7.5, 14.5**
  
  - [ ] 10.6 Implement rate limiting and queueing
    - Add rate limit tracking per channel
    - Implement queue for rate-limited candidates
    - _Requirements: 7.6_
  
  - [ ]* 10.7 Write property test for rate limiting queue behavior
    - **Property 24: Rate limiting queue behavior**
    - **Validates: Requirements 7.6**

- [ ] 11. Implement email channel adapter
  - [ ] 11.1 Create email channel adapter implementation
    - Integrate with email campaign service
    - Implement campaign creation automation
    - Add template management per program
    - _Requirements: 14.1, 14.2_
  
  - [ ]* 11.2 Write property test for email campaign automation
    - **Property 42: Email campaign automation**
    - **Validates: Requirements 14.1**
  
  - [ ]* 11.3 Write property test for program-specific email templates
    - **Property 43: Program-specific email templates**
    - **Validates: Requirements 14.2**
  
  - [ ] 11.4 Implement opt-out enforcement
    - Add opt-out check before campaign creation
    - Exclude opted-out customers from recipient lists
    - _Requirements: 14.3, 18.5_
  
  - [ ]* 11.5 Write property test for opt-out enforcement
    - **Property 44: Opt-out enforcement**
    - **Validates: Requirements 14.3**
  
  - [ ] 11.6 Implement frequency capping
    - Track email sends per customer
    - Enforce frequency limits per program configuration
    - _Requirements: 14.4, 18.6_
  
  - [ ]* 11.7 Write property test for email frequency capping
    - **Property 45: Email frequency capping**
    - **Validates: Requirements 14.4**
  
  - [ ] 11.8 Add delivery tracking
    - Record delivery status and timestamps
    - Track open rates and engagement metrics
    - _Requirements: 14.6_
  
  - [ ]* 11.9 Write property test for email delivery tracking
    - **Property 46: Email delivery tracking**
    - **Validates: Requirements 14.6**



- [ ] 12. Checkpoint - Ensure serving and channel tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 13. Implement batch ingestion workflow
  - [ ] 13.1 Create Step Functions workflow definition
    - Define workflow states (ETL, Filter, Score, Store)
    - Add error handling and retry logic
    - Configure parallel execution where applicable
    - _Requirements: 8.1, 8.3_
  
  - [ ] 13.2 Implement ETL Lambda function
    - Use data connector to extract and transform data
    - Batch candidates for downstream processing
    - _Requirements: 1.2, 8.1_
  
  - [ ] 13.3 Implement filter Lambda function
    - Execute filter chain on candidate batches
    - Track rejection reasons
    - _Requirements: 4.1, 4.2, 8.1_
  
  - [ ] 13.4 Implement scoring Lambda function
    - Execute scoring for candidate batches
    - Handle scoring failures with fallbacks
    - _Requirements: 3.2, 3.3, 8.1_
  
  - [ ] 13.5 Implement storage Lambda function
    - Batch write candidates to DynamoDB
    - Handle write failures and retries
    - _Requirements: 5.2, 8.1_
  
  - [ ] 13.6 Add workflow metrics publishing
    - Publish metrics at each workflow stage
    - Track processed, passed, rejected counts
    - _Requirements: 8.4, 12.1_
  
  - [ ]* 13.7 Write property test for workflow metrics publishing
    - **Property 26: Workflow metrics publishing**
    - **Validates: Requirements 8.4, 12.1**
  
  - [ ] 13.8 Implement retry with exponential backoff
    - Configure Step Functions retry policy
    - Add exponential backoff delays
    - _Requirements: 8.3_
  
  - [ ]* 13.9 Write property test for workflow retry with exponential backoff
    - **Property 25: Workflow retry with exponential backoff**
    - **Validates: Requirements 8.3**
  
  - [ ] 13.10 Add workflow completion triggers
    - Publish completion metrics
    - Trigger downstream processes (data warehouse export)
    - _Requirements: 8.6_
  
  - [ ]* 13.11 Write property test for workflow completion triggers
    - **Property 27: Workflow completion triggers**
    - **Validates: Requirements 8.6**



- [ ] 14. Implement reactive solicitation workflow
  - [ ] 14.1 Create EventBridge rule for customer events
    - Configure event pattern matching
    - Route events to reactive Lambda
    - _Requirements: 9.1, 9.2_
  
  - [ ] 14.2 Implement reactive Lambda function
    - Execute filtering and scoring in real-time
    - Create and store eligible candidates immediately
    - _Requirements: 9.2, 9.3, 9.4_
  
  - [ ]* 14.3 Write property test for reactive candidate creation
    - **Property 28: Reactive candidate creation**
    - **Validates: Requirements 9.4**
  
  - [ ] 14.4 Implement event deduplication
    - Track recent events per customer-subject pair
    - Deduplicate within configured time window
    - _Requirements: 9.5_
  
  - [ ]* 14.5 Write property test for event deduplication within window
    - **Property 29: Event deduplication within window**
    - **Validates: Requirements 9.5**

- [ ] 15. Implement program configuration management
  - [ ] 15.1 Create program registry DynamoDB table
    - Define table schema for program configurations
    - Add GSIs for querying by marketplace
    - _Requirements: 10.1, 10.2_
  
  - [ ] 15.2 Implement program configuration API
    - Add CRUD operations for program configs
    - Implement configuration validation
    - _Requirements: 10.1, 10.2_
  
  - [ ]* 15.3 Write property test for program configuration completeness
    - **Property 31: Program configuration completeness**
    - **Validates: Requirements 10.2**
  
  - [ ] 15.4 Implement program enable/disable logic
    - Add disable flag to program configuration
    - Skip workflows for disabled programs
    - Prevent candidate creation for disabled programs
    - _Requirements: 10.3_
  
  - [ ]* 15.5 Write property test for program disable enforcement
    - **Property 32: Program disable enforcement**
    - **Validates: Requirements 10.3**
  
  - [ ] 15.6 Add marketplace configuration overrides
    - Support per-marketplace config overrides
    - Apply overrides in precedence order
    - _Requirements: 10.4_
  
  - [ ]* 15.7 Write property test for marketplace configuration override
    - **Property 33: Marketplace configuration override**
    - **Validates: Requirements 10.4**



- [ ] 16. Implement experimentation framework
  - [ ] 16.1 Create experiment configuration model
    - Define experiment config structure
    - Add treatment group definitions
    - _Requirements: 11.1, 11.2_
  
  - [ ] 16.2 Implement deterministic treatment assignment
    - Use consistent hashing for customer assignment
    - Ensure same customer always gets same treatment
    - _Requirements: 11.1_
  
  - [ ]* 16.3 Write property test for deterministic treatment assignment
    - **Property 34: Deterministic treatment assignment**
    - **Validates: Requirements 11.1**
  
  - [ ] 16.4 Add treatment recording to candidates
    - Record assigned treatment in candidate metadata
    - _Requirements: 11.3_
  
  - [ ]* 16.5 Write property test for treatment recording
    - **Property 35: Treatment recording**
    - **Validates: Requirements 11.3**
  
  - [ ] 16.6 Implement treatment-specific metrics collection
    - Collect metrics per treatment group
    - Enable comparison of treatment performance
    - _Requirements: 11.4_
  
  - [ ]* 16.7 Write property test for treatment-specific metrics collection
    - **Property 36: Treatment-specific metrics collection**
    - **Validates: Requirements 11.4**

- [ ] 17. Checkpoint - Ensure workflow and configuration tests pass
  - Ensure all tests pass, ask the user if questions arise.



- [ ] 18. Implement observability and monitoring
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

- [ ] 19. Implement multi-program isolation
  - [ ] 19.1 Add program failure isolation
    - Ensure program workflows are independent
    - Prevent cascading failures across programs
    - _Requirements: 13.1_
  
  - [ ]* 19.2 Write property test for program failure isolation
    - **Property 39: Program failure isolation**
    - **Validates: Requirements 13.1**
  
  - [ ] 19.3 Implement program-specific throttling
    - Track rate limits per program
    - Throttle only the exceeding program
    - _Requirements: 13.3_
  
  - [ ]* 19.4 Write property test for program-specific throttling
    - **Property 40: Program-specific throttling**
    - **Validates: Requirements 13.3**
  
  - [ ] 19.5 Add program cost attribution
    - Tag resources with program ID
    - Track costs per program
    - Publish cost metrics
    - _Requirements: 13.4_
  
  - [ ]* 19.6 Write property test for program cost attribution
    - **Property 41: Program cost attribution**
    - **Validates: Requirements 13.4**



- [ ] 20. Implement security and compliance features
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



- [ ] 21. Implement candidate lifecycle management
  - [ ] 21.1 Add manual candidate deletion API
    - Implement delete endpoint
    - Verify deletion removes candidate from storage
    - _Requirements: 17.3_
  
  - [ ]* 21.2 Write property test for manual deletion
    - **Property 52: Manual deletion**
    - **Validates: Requirements 17.3**
  
  - [ ] 21.3 Implement consumed marking
    - Mark candidates as consumed after delivery
    - Record delivery timestamp
    - _Requirements: 17.4_
  
  - [ ]* 21.4 Write property test for consumed marking
    - **Property 53: Consumed marking**
    - **Validates: Requirements 17.4**
  
  - [ ] 21.5 Add candidate refresh functionality
    - Implement re-scoring for active candidates
    - Implement eligibility refresh
    - Update candidate with current values
    - _Requirements: 17.5_
  
  - [ ]* 21.6 Write property test for candidate refresh
    - **Property 54: Candidate refresh**
    - **Validates: Requirements 17.5**
  
  - [ ] 21.7 Implement data warehouse export
    - Create daily export Lambda function
    - Export candidates to S3 in Parquet format
    - Trigger Glue job to load into data warehouse
    - _Requirements: 5.6_
  
  - [ ]* 21.8 Write property test for data warehouse export completeness
    - **Property 16: Data warehouse export completeness**
    - **Validates: Requirements 5.6**

- [ ] 22. Checkpoint - Ensure lifecycle and security tests pass
  - Ensure all tests pass, ask the user if questions arise.



- [ ] 23. Implement real-time channel features
  - [ ] 23.1 Create in-app channel adapter
    - Implement serving API integration
    - Add in-app specific formatting
    - _Requirements: 15.2_
  
  - [ ] 23.2 Create push notification channel adapter
    - Integrate with push notification service
    - Add push-specific formatting
    - _Requirements: 15.2_
  
  - [ ] 23.3 Create voice assistant channel adapter
    - Integrate with voice assistant service
    - Add voice-specific formatting
    - _Requirements: 15.2_
  
  - [ ] 23.4 Implement personalized ranking with context
    - Use customer history and preferences for ranking
    - Apply context-aware ranking algorithms
    - _Requirements: 15.4_
  
  - [ ]* 23.5 Write property test for personalized ranking with context
    - **Property 47: Personalized ranking with context**
    - **Validates: Requirements 15.4**
  
  - [ ] 23.6 Add A/B test integration to serving API
    - Return treatment-specific candidates
    - Ensure treatment consistency
    - _Requirements: 15.5_
  
  - [ ]* 23.7 Write property test for treatment-specific candidate serving
    - **Property 48: Treatment-specific candidate serving**
    - **Validates: Requirements 15.5**

- [ ] 24. Implement backward compatibility and migration support
  - [ ] 24.1 Create v1 API adapter layer
    - Implement v1 API endpoints
    - Translate v1 requests to v2 backend
    - Return v1 response format
    - _Requirements: 20.1, 20.2_
  
  - [ ]* 24.2 Write property test for V1 API backward compatibility
    - **Property 58: V1 API backward compatibility**
    - **Validates: Requirements 20.1**
  
  - [ ] 24.3 Add v1 usage tracking
    - Record v1 API usage metrics
    - Track endpoint, customer, timestamp
    - _Requirements: 20.3_
  
  - [ ]* 24.4 Write property test for V1 usage tracking
    - **Property 59: V1 usage tracking**
    - **Validates: Requirements 20.3**
  
  - [ ] 24.5 Implement shadow mode for v2
    - Execute v2 processing in parallel with v1
    - Ensure v2 doesn't affect v1 responses
    - _Requirements: 20.4_
  
  - [ ]* 24.6 Write property test for shadow mode isolation
    - **Property 60: Shadow mode isolation**
    - **Validates: Requirements 20.4**



- [ ] 25. Implement version monotonicity tracking
  - [ ] 25.1 Add version increment logic to candidate updates
    - Ensure version number increases on each update
    - Ensure updatedAt timestamp is current
    - _Requirements: 2.5_
  
  - [ ]* 25.2 Write property test for version monotonicity
    - **Property 4: Version monotonicity**
    - **Validates: Requirements 2.5**

- [ ] 26. Final integration and end-to-end testing
  - [ ]* 26.1 Run end-to-end batch workflow test
    - Test complete flow from data warehouse to storage
    - Verify all stages execute correctly
    - Verify metrics are published
  
  - [ ]* 26.2 Run end-to-end reactive workflow test
    - Test event-driven candidate creation
    - Verify sub-second latency
    - Verify candidate availability
  
  - [ ]* 26.3 Run end-to-end serving API test
    - Test API with real DynamoDB backend
    - Test various query patterns
    - Verify latency targets
  
  - [ ]* 26.4 Run end-to-end channel delivery test
    - Test email campaign creation
    - Test in-app serving
    - Test shadow mode
    - Verify delivery tracking

- [ ] 27. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties (minimum 100 iterations each)
- Unit tests validate specific examples and edge cases
- Integration tests validate end-to-end flows
- Use JUnit 5 for unit tests and jqwik or QuickTheories for property-based testing in Java
