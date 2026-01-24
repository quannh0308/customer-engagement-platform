# DynamoDB Schema Documentation

> **Platform Rebranding Note**: This platform was formerly known as the "General Solicitation Platform". We've rebranded to "Customer Engagement & Action Platform (CEAP)" to better reflect its capabilities beyond customer engagement. Package names now use `com.ceap.*` following the CEAP branding, and table structures remain unchanged for backward compatibility.

## Overview

This document describes the DynamoDB table schemas for the Customer Engagement & Action Platform (CEAP). All tables use on-demand capacity mode for automatic scaling.

## Table Schemas

### 1. Candidates Table

**Purpose**: Stores customer engagement candidates with customer, program, subject, and scoring information.

**Primary Key Pattern**:
- **PK**: `CUSTOMER#{customerId}#PROGRAM#{programId}#MARKETPLACE#{marketplaceId}`
- **SK**: `SUBJECT#{subjectType}#{subjectId}`

**Attributes**:
- `context` (List): List of context maps with key-value pairs
- `subject` (Map): Subject information (type, id, attributes)
- `scores` (Map): Scoring results from various models
- `attributes` (Map): Additional candidate attributes
- `metadata` (Map): System metadata (created, updated timestamps)
- `rejectionHistory` (List): History of rejections with reasons
- `ttl` (Number): Unix timestamp for automatic expiration

**Global Secondary Indexes**:

#### GSI-1: ProgramChannelIndex
Query candidates by program and channel, sorted by score.

- **GSI1PK**: `PROGRAM#{programId}#CHANNEL#{channelId}`
- **GSI1SK**: `SCORE#{scoreValue}#CUSTOMER#{customerId}`
- **Projection**: ALL

**Use Case**: Retrieve top-scoring candidates for a specific program and channel.

**Example Query**:
```
GSI1PK = "PROGRAM#retail#CHANNEL#email"
GSI1SK begins_with "SCORE#"
ScanIndexForward = false (descending by score)
```

#### GSI-2: ProgramDateIndex
Query candidates by program and date, sorted by creation time.

- **GSI2PK**: `PROGRAM#{programId}#DATE#{YYYY-MM-DD}`
- **GSI2SK**: `CREATED#{timestamp}#CUSTOMER#{customerId}`
- **Projection**: ALL

**Use Case**: Retrieve candidates created on a specific date for a program.

**Example Query**:
```
GSI2PK = "PROGRAM#retail#DATE#2026-01-16"
GSI2SK begins_with "CREATED#"
```

**TTL Configuration**:
- Enabled on `ttl` attribute
- Automatically deletes expired candidates
- TTL value set based on program configuration (candidateTTLDays)

---

### 2. ProgramConfig Table

**Purpose**: Stores program configuration including data connectors, scoring models, filters, and notification channels.

**Primary Key Pattern**:
- **PK**: `PROGRAM#{programId}`
- **SK**: `MARKETPLACE#{marketplaceId}`

**Attributes**:
- `programName` (String): Human-readable program name
- `enabled` (Boolean): Whether the program is active
- `dataConnectors` (List): List of data connector configurations
- `scoringModels` (List): List of scoring model configurations
- `filterChain` (Map): Filter pipeline configuration
- `channels` (List): List of notification channel configurations
- `batchSchedule` (String): Cron expression for batch processing
- `reactiveEnabled` (Boolean): Whether reactive processing is enabled
- `candidateTTLDays` (Number): Days until candidate expiration

**Example Item**:
```json
{
  "PK": "PROGRAM#retail",
  "SK": "MARKETPLACE#US",
  "programName": "Retail Customer Engagement",
  "enabled": true,
  "dataConnectors": [
    {
      "type": "athena",
      "query": "SELECT * FROM customers WHERE ...",
      "schedule": "0 2 * * *"
    }
  ],
  "scoringModels": [
    {
      "modelId": "propensity-v1",
      "endpoint": "https://...",
      "weight": 0.7
    }
  ],
  "filterChain": {
    "filters": ["frequency", "eligibility", "preference"]
  },
  "channels": [
    {
      "channelId": "email",
      "priority": 1,
      "config": {}
    }
  ],
  "batchSchedule": "0 2 * * *",
  "reactiveEnabled": true,
  "candidateTTLDays": 30
}
```

---

### 3. ScoreCache Table

**Purpose**: Caches scoring results for customer-subject-model combinations to avoid redundant scoring calls.

**Primary Key Pattern**:
- **PK**: `CUSTOMER#{customerId}#SUBJECT#{subjectId}`
- **SK**: `MODEL#{modelId}`

**Attributes**:
- `score` (Map): Score information
  - `value` (Number): Score value
  - `confidence` (Number): Confidence level
  - `timestamp` (Number): When score was computed
- `ttl` (Number): Unix timestamp for cache expiration

**Example Item**:
```json
{
  "PK": "CUSTOMER#12345#SUBJECT#PRODUCT-ABC",
  "SK": "MODEL#propensity-v1",
  "score": {
    "value": 0.85,
    "confidence": 0.92,
    "timestamp": 1705401600
  },
  "ttl": 1705488000
}
```

**TTL Configuration**:
- Enabled on `ttl` attribute
- Automatically deletes expired cache entries
- TTL typically set to 24-48 hours after score computation

---

## Key Design Patterns

### Single Table Design Considerations

While this implementation uses three separate tables, the design follows DynamoDB best practices:

1. **Composite Keys**: Use compound keys with delimiters for hierarchical data
2. **GSIs for Access Patterns**: Each GSI supports a specific query pattern
3. **Attribute Projection**: GSIs use ALL projection for flexibility
4. **TTL for Cleanup**: Automatic deletion of expired data

### Key Naming Conventions

- Use `#` as delimiter between key components
- Use uppercase for entity types (CUSTOMER, PROGRAM, SUBJECT, etc.)
- Use descriptive prefixes (PK, SK, GSI1PK, GSI1SK, etc.)
- Include relevant identifiers in sort keys for efficient queries

### Capacity Mode

All tables use **PAY_PER_REQUEST** (on-demand) billing mode:
- Automatically scales with traffic
- No capacity planning required
- Cost-effective for variable workloads
- Suitable for development and production

### Data Protection

All tables include:
- **Point-in-Time Recovery**: Enabled for data protection
- **Server-Side Encryption**: Enabled using AWS managed keys
- **Tags**: Environment, Application, and ManagedBy tags for governance

---

## Deployment

### Using CloudFormation

```bash
# Deploy to dev environment
aws cloudformation deploy \
  --template-file dynamodb-tables.yaml \
  --stack-name ceap-platform-dynamodb-dev \
  --parameter-overrides Environment=dev

# Deploy to production
aws cloudformation deploy \
  --template-file dynamodb-tables.yaml \
  --stack-name ceap-platform-dynamodb-prod \
  --parameter-overrides Environment=prod
```

### Stack Outputs

The CloudFormation stack exports the following outputs:
- Table names for each table
- Table ARNs for IAM policy configuration

These outputs can be referenced by other stacks for Lambda function configuration.

---

## Access Patterns

### Candidates Table

1. **Get candidate by customer, program, marketplace, and subject**
   - Query: PK = `CUSTOMER#...#PROGRAM#...#MARKETPLACE#...`, SK = `SUBJECT#...`

2. **Get top candidates for program and channel**
   - Query: GSI1 where GSI1PK = `PROGRAM#...#CHANNEL#...`, sort by GSI1SK descending

3. **Get candidates created on specific date**
   - Query: GSI2 where GSI2PK = `PROGRAM#...#DATE#...`

### ProgramConfig Table

1. **Get program configuration**
   - Query: PK = `PROGRAM#...`, SK = `MARKETPLACE#...`

2. **List all programs for marketplace**
   - Query: SK = `MARKETPLACE#...` (requires scan or additional GSI)

### ScoreCache Table

1. **Get cached score**
   - Query: PK = `CUSTOMER#...#SUBJECT#...`, SK = `MODEL#...`

2. **Get all scores for customer-subject pair**
   - Query: PK = `CUSTOMER#...#SUBJECT#...`

---

## Maintenance

### Monitoring

Monitor the following CloudWatch metrics:
- `ConsumedReadCapacityUnits` / `ConsumedWriteCapacityUnits`
- `UserErrors` (throttling, validation errors)
- `SystemErrors`
- `ConditionalCheckFailedRequests`

### Cost Optimization

- TTL automatically removes expired data, reducing storage costs
- On-demand billing eliminates over-provisioning
- Consider switching to provisioned capacity for predictable workloads

### Backup Strategy

- Point-in-Time Recovery enabled for all tables
- Consider AWS Backup for additional backup policies
- Test restore procedures regularly

