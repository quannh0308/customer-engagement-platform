# Requirements Document - Task 1 Context

## Introduction

The General Solicitation Platform is a flexible, extensible system that decouples data sources, scoring systems, filtering mechanisms, and notification channels to solicit customer responses across multiple product verticals.

**Current Focus**: Task 1 - Set up project structure and core infrastructure

## Glossary

- **Candidate**: A potential solicitation opportunity (customer-subject pair with context)
- **Program**: An independent solicitation configuration with specific rules, channels, and data sources
- **DynamoDB**: AWS NoSQL database service used for candidate storage
- **Lambda**: AWS serverless compute service for running application code
- **Step Functions**: AWS workflow orchestration service
- **EventBridge**: AWS event bus service for event-driven architecture
- **CloudWatch**: AWS monitoring and logging service

## Relevant Requirements for Task 1

### Foundational Infrastructure Requirements

All requirements depend on proper infrastructure setup. Task 1 establishes the foundation for:

- **Data Storage**: DynamoDB tables for candidates, configurations, and caches
- **Compute**: Lambda functions for all processing logic
- **Orchestration**: Step Functions for batch and reactive workflows
- **Events**: EventBridge for scheduling and event-driven triggers
- **Observability**: CloudWatch for logging, metrics, and alarms
- **Deployment**: Infrastructure as Code (CDK/CloudFormation) for reproducible deployments
- **CI/CD**: Automated build and deployment pipelines

### Specific Requirements Addressed

**Requirement 5.1**: DynamoDB Schema Design
- Primary key: `CustomerId:Program:Marketplace`
- GSI for program-specific queries
- GSI for channel-specific queries
- TTL for automatic candidate expiration

**Requirement 5.3**: Query Support
- THE Candidate_Storage SHALL provide GSI for querying by program and by channel

**Requirement 8.1**: Batch Ingestion Workflow
- WHEN a batch workflow is scheduled, THE system SHALL execute ETL, filtering, scoring, and storage in sequence

**Requirement 9.1**: Reactive Solicitation Workflow
- WHEN a customer event occurs, THE system SHALL create a candidate within 1 second end-to-end

**Requirement 12.2**: Structured Logging
- WHEN workflows fail, THE system SHALL emit structured logs with correlation IDs

**Requirement 18.4**: PII Redaction
- WHEN PII is logged, THE system SHALL redact sensitive fields

## Success Criteria for Task 1

1. ✅ Maven project builds successfully with all dependencies
2. ✅ DynamoDB tables can be created via IaC
3. ✅ Lambda functions can be deployed
4. ✅ Step Functions workflows are defined
5. ✅ EventBridge rules are configured
6. ✅ Logging framework outputs structured logs with correlation IDs
7. ✅ CI/CD pipeline runs successfully
8. ✅ Project follows Java best practices and AWS Well-Architected Framework

## Next Steps

After Task 1 completion, Task 2 will implement core data models (Candidate, Context, Subject, etc.) building on this infrastructure foundation.
