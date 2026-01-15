# Design Document - Task 1 Context

## Overview

The General Solicitation Platform is a cloud-native, event-driven system built on AWS using Java. This document focuses on the infrastructure and project setup required for Task 1.

**Current Focus**: Task 1 - Set up project structure and core infrastructure

## Technology Stack

### Core Technologies
- **Language**: Java 17
- **Build Tool**: Maven
- **AWS Services**: Lambda, DynamoDB, Step Functions, EventBridge, CloudWatch
- **Infrastructure as Code**: AWS CDK or CloudFormation
- **Logging**: SLF4J + Logback + CloudWatch Logs
- **Testing**: JUnit 5, jqwik (property-based testing)

### AWS Services Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     AWS Infrastructure                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐      ┌──────────────┐                    │
│  │  EventBridge │─────▶│    Lambda    │                    │
│  │   (Scheduler)│      │  (Functions) │                    │
│  └──────────────┘      └──────┬───────┘                    │
│                               │                             │
│  ┌──────────────┐            │        ┌──────────────┐    │
│  │     Step     │◀───────────┴───────▶│   DynamoDB   │    │
│  │  Functions   │                     │   (Tables)   │    │
│  └──────────────┘                     └──────────────┘    │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              CloudWatch Logs & Metrics                │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Project Structure

```
solicitation-platform/
├── pom.xml                          # Maven configuration
├── infrastructure/                   # IaC definitions
│   ├── dynamodb-tables.yaml         # DynamoDB table definitions
│   ├── lambda-functions.yaml        # Lambda configurations
│   ├── step-functions.yaml          # Workflow definitions
│   └── eventbridge-rules.yaml       # Event rules
├── src/
│   ├── main/
│   │   ├── java/com/solicitation/
│   │   │   ├── model/               # Data models
│   │   │   ├── storage/             # DynamoDB repositories
│   │   │   ├── connector/           # Data connectors
│   │   │   ├── scoring/             # Scoring engine
│   │   │   ├── filter/              # Filter pipeline
│   │   │   ├── serving/             # Serving API
│   │   │   ├── channel/             # Channel adapters
│   │   │   ├── workflow/            # Workflow handlers
│   │   │   ├── config/              # Configuration
│   │   │   └── util/                # Utilities
│   │   └── resources/
│   │       ├── logback.xml          # Logging configuration
│   │       └── application.properties
│   └── test/
│       └── java/com/solicitation/   # Test files
├── scripts/
│   ├── build.sh                     # Build script
│   └── deploy.sh                    # Deployment script
└── .github/
    └── workflows/
        └── build.yml                # CI/CD pipeline
```

## DynamoDB Schema Design

### Candidates Table

```
Table Name: Candidates
Primary Key: 
  PK: CUSTOMER#{customerId}#PROGRAM#{programId}#MARKETPLACE#{marketplaceId}
  SK: SUBJECT#{subjectType}#{subjectId}

Attributes:
  - context (List of Maps)
  - subject (Map)
  - scores (Map)
  - attributes (Map)
  - metadata (Map)
  - rejectionHistory (List)
  - ttl (Number, for automatic expiration)

GSI-1 (ProgramChannelIndex):
  PK: PROGRAM#{programId}#CHANNEL#{channelId}
  SK: SCORE#{scoreValue}#CUSTOMER#{customerId}
  
GSI-2 (ProgramDateIndex):
  PK: PROGRAM#{programId}#DATE#{YYYY-MM-DD}
  SK: CREATED#{timestamp}#CUSTOMER#{customerId}

Capacity Mode: On-Demand
TTL Attribute: ttl
```

### ProgramConfig Table

```
Table Name: ProgramConfig
Primary Key:
  PK: PROGRAM#{programId}
  SK: MARKETPLACE#{marketplaceId}

Attributes:
  - programName (String)
  - enabled (Boolean)
  - dataConnectors (List)
  - scoringModels (List)
  - filterChain (Map)
  - channels (List)
  - batchSchedule (String)
  - reactiveEnabled (Boolean)
  - candidateTTLDays (Number)

Capacity Mode: On-Demand
```

### ScoreCache Table

```
Table Name: ScoreCache
Primary Key:
  PK: CUSTOMER#{customerId}#SUBJECT#{subjectId}
  SK: MODEL#{modelId}

Attributes:
  - score (Map with value, confidence, timestamp)
  - ttl (Number)

Capacity Mode: On-Demand
TTL Attribute: ttl
```

## Lambda Function Configurations

### ETL Lambda
- **Memory**: 1024 MB
- **Timeout**: 300 seconds
- **Environment Variables**: DATA_SOURCE_CONFIG, BATCH_SIZE
- **IAM Permissions**: DynamoDB read/write, Athena query, S3 read

### Filter Lambda
- **Memory**: 512 MB
- **Timeout**: 60 seconds
- **Environment Variables**: FILTER_CONFIG
- **IAM Permissions**: DynamoDB read, external service calls

### Score Lambda
- **Memory**: 1024 MB
- **Timeout**: 120 seconds
- **Environment Variables**: MODEL_ENDPOINTS, FEATURE_STORE_CONFIG
- **IAM Permissions**: DynamoDB read/write, SageMaker invoke, feature store access

### Store Lambda
- **Memory**: 512 MB
- **Timeout**: 60 seconds
- **Environment Variables**: BATCH_SIZE
- **IAM Permissions**: DynamoDB batch write

### Serve Lambda
- **Memory**: 512 MB
- **Timeout**: 30 seconds
- **Environment Variables**: CACHE_CONFIG
- **IAM Permissions**: DynamoDB read, API Gateway integration

## Logging Configuration

### Structured Logging Format

```json
{
  "timestamp": "2026-01-16T10:30:00.000Z",
  "level": "INFO",
  "correlationId": "uuid-1234-5678",
  "service": "solicitation-platform",
  "component": "ETLLambda",
  "message": "Processing batch",
  "context": {
    "programId": "retail",
    "marketplace": "US",
    "batchSize": 1000
  }
}
```

### Log Levels
- **ERROR**: System errors, exceptions, failures
- **WARN**: Degraded performance, fallback usage, retries
- **INFO**: Normal operations, workflow progress, metrics
- **DEBUG**: Detailed debugging information (disabled in production)

### PII Redaction Rules
- Email addresses: `user@example.com` → `u***@e***.com`
- Phone numbers: `+1-555-1234` → `+1-***-****`
- Customer IDs: Keep first 4 chars, mask rest
- Addresses: Redact completely

## CI/CD Pipeline

### Build Stage
1. Checkout code
2. Run Maven build (`mvn clean package`)
3. Run unit tests
4. Run property-based tests
5. Generate code coverage report
6. Build Lambda deployment packages

### Deploy Stage (Dev)
1. Deploy infrastructure (CDK/CloudFormation)
2. Deploy Lambda functions
3. Run integration tests
4. Verify deployment health

### Deploy Stage (Staging/Prod)
1. Manual approval required
2. Deploy infrastructure
3. Deploy Lambda functions
4. Run smoke tests
5. Monitor metrics for 30 minutes
6. Rollback on errors

## Maven Dependencies

```xml
<dependencies>
  <!-- AWS SDK -->
  <dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>dynamodb</artifactId>
  </dependency>
  <dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>lambda</artifactId>
  </dependency>
  <dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>sfn</artifactId>
  </dependency>
  
  <!-- Lambda Runtime -->
  <dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-lambda-java-core</artifactId>
  </dependency>
  
  <!-- Logging -->
  <dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
  </dependency>
  <dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
  </dependency>
  
  <!-- JSON Processing -->
  <dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
  </dependency>
  
  <!-- Testing -->
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

## Next Steps

After Task 1 completion, Task 2 will implement the core data models using the infrastructure established here.
