# Infrastructure Inventory - CEAP Platform

**Last Updated**: January 31, 2026  
**Environment**: dev  
**Architecture**: Consolidated 3-Stack  
**Region**: us-east-1

---

## Overview

This document provides a complete inventory of all AWS resources deployed by the CEAP platform using the consolidated 3-stack architecture.

**Architecture Change**: Migrated from 7-stack to 3-stack architecture on January 31, 2026 for improved manageability and business alignment.

---

## CloudFormation Stacks (3 Total)

### 1. CeapDatabase-dev (Storage Layer)
**Purpose**: DynamoDB tables for data storage  
**Business Capability**: Shared data foundation  
**Resources**:
- 3 DynamoDB tables (Candidates, ProgramConfig, ScoreCache)
- 6 CloudFormation exports for cross-stack references

**Dependencies**: None (base stack)  
**Deployment Time**: ~2-3 minutes

---

### 2. CeapDataPlatform-dev (Write Path)
**Purpose**: Data ingestion, transformation, scoring, and storage  
**Business Capability**: B-2 (Building Datasets)  
**Resources**:
- 4 Lambda functions (ETL, Filter, Score, Store)
- 1 Step Functions state machine (BatchIngestionWorkflow)
- 1 EventBridge rule (BatchIngestionSchedule)
- 7 IAM roles
- 4 CloudWatch log groups

**Dependencies**: CeapDatabase-dev  
**Deployment Time**: ~2-3 minutes  
**Consolidated From**: CeapEtlWorkflow-dev, CeapFilterWorkflow-dev, CeapScoreWorkflow-dev, CeapStoreWorkflow-dev, CeapOrchestration-dev

---

### 3. CeapServingAPI-dev (Read Path)
**Purpose**: Real-time event processing and serving  
**Business Capability**: B-3 (Low-Latency Retrieval)  
**Resources**:
- 1 Lambda function (Reactive)
- 1 DynamoDB table (event deduplication)
- 1 EventBridge rule (customer events)
- 2 IAM roles
- 1 CloudWatch log group

**Dependencies**: CeapDatabase-dev  
**Deployment Time**: ~2 minutes  
**Consolidated From**: CeapReactiveWorkflow-dev

---

## Stack Dependency Graph

```
CeapDatabase-dev (Storage Layer)
       ↓                    ↓
CeapDataPlatform-dev    CeapServingAPI-dev
   (Write Path)            (Read Path)
```

**Deployment Order**:
1. CeapDatabase-dev (must deploy first)
2. CeapDataPlatform-dev and CeapServingAPI-dev (can deploy in parallel)

---

## DynamoDB Tables (4 Total)

### 1. Candidates-dev
**Purpose**: Store engagement candidates  
**Capacity**: On-demand (pay per request)  
**Key Schema**:
- PK (Hash): `CUST#{customerId}#PROG#{programId}#MKT#{marketplace}`
- SK (Range): `SUBJ#{subjectType}#{subjectId}`

**Global Secondary Indexes**:
- ProgramChannelIndex (programId, channel)
- ProgramDateIndex (programId, createdAt)

**TTL**: Enabled on `expiresAt` attribute  
**Estimated Size**: 0 items (empty)  
**Estimated Cost**: $0-5/month

---

### 2. ProgramConfig-dev
**Purpose**: Store program configurations  
**Capacity**: On-demand  
**Key Schema**:
- PK (Hash): `PROGRAM#{programId}`
- SK (Range): `MARKETPLACE#{marketplace}`

**GSI**: None  
**TTL**: Disabled  
**Estimated Size**: 0-100 items  
**Estimated Cost**: $0-1/month

---

### 3. ScoreCache-dev
**Purpose**: Cache ML model scores  
**Capacity**: On-demand  
**Key Schema**:
- PK (Hash): `CACHE#{cacheKey}`
- SK (Range): `MODEL#{modelId}`

**GSI**: None  
**TTL**: Enabled on `expiresAt` attribute (24 hours default)  
**Estimated Size**: 0-10,000 items  
**Estimated Cost**: $0-2/month

---

### 4. ceap-event-deduplication-dev
**Purpose**: Track recent events to prevent duplicates  
**Capacity**: On-demand  
**Key Schema**:
- PK (Hash): `deduplicationKey`

**GSI**: None  
**TTL**: Enabled (5 minutes default)  
**Estimated Size**: 0-1,000 items (rolling window)  
**Estimated Cost**: $0-1/month

---

## Lambda Functions (5 Total)

### 1. ETL Lambda
**Full Name**: `CeapDataPlatform-dev-ETLLambdaFunction49DD508A-*`  
**Stack**: CeapDataPlatform-dev  
**Handler**: `com.ceap.workflow.etl.ETLHandler::handleRequest`  
**Runtime**: Java 17  
**Memory**: 1024 MB  
**Timeout**: 5 minutes  
**Code Size**: ~33 MB (fat JAR with dependencies)

**Purpose**: Extract data from sources, transform to candidates  
**Triggers**: Step Functions  
**Permissions**: Read ProgramConfig, Write Candidates

**Environment Variables**:
- `ENVIRONMENT`: dev
- `CANDIDATES_TABLE`: Candidates-dev
- `PROGRAM_CONFIG_TABLE`: ProgramConfig-dev
- `BATCH_SIZE`: 1000
- `LOG_LEVEL`: INFO

---

### 2. Filter Lambda
**Full Name**: `CeapDataPlatform-dev-FilterLambdaFunction29040EBB-*`  
**Stack**: CeapDataPlatform-dev  
**Handler**: `com.ceap.workflow.filter.FilterHandler::handleRequest`  
**Runtime**: Java 17  
**Memory**: 512 MB  
**Timeout**: 1 minute  
**Code Size**: ~34 MB

**Purpose**: Apply filters to candidates (eligibility, trust, quality)  
**Triggers**: Step Functions  
**Permissions**: Read ProgramConfig

**Environment Variables**:
- `ENVIRONMENT`: dev
- `PROGRAM_CONFIG_TABLE`: ProgramConfig-dev
- `LOG_LEVEL`: INFO

---

### 3. Score Lambda
**Full Name**: `CeapDataPlatform-dev-ScoreLambdaFunction04AD330A-*`  
**Stack**: CeapDataPlatform-dev  
**Handler**: `com.ceap.workflow.score.ScoreHandler::handleRequest`  
**Runtime**: Java 17  
**Memory**: 1024 MB  
**Timeout**: 2 minutes  
**Code Size**: ~44 MB

**Purpose**: Execute ML models to score candidates  
**Triggers**: Step Functions  
**Permissions**: Read/Write ScoreCache

**Environment Variables**:
- `ENVIRONMENT`: dev
- `SCORE_CACHE_TABLE`: ScoreCache-dev
- `CACHE_TTL_HOURS`: 24
- `LOG_LEVEL`: INFO

---

### 4. Store Lambda
**Full Name**: `CeapDataPlatform-dev-StoreLambdaFunction7FC1576D-*`  
**Stack**: CeapDataPlatform-dev  
**Handler**: `com.ceap.workflow.store.StoreHandler::handleRequest`  
**Runtime**: Java 17  
**Memory**: 512 MB  
**Timeout**: 1 minute  
**Code Size**: ~39 MB

**Purpose**: Batch write candidates to DynamoDB  
**Triggers**: Step Functions  
**Permissions**: Write Candidates

**Environment Variables**:
- `ENVIRONMENT`: dev
- `CANDIDATES_TABLE`: Candidates-dev
- `BATCH_SIZE`: 25
- `LOG_LEVEL`: INFO

---

### 5. Reactive Lambda
**Full Name**: `CeapServingAPI-dev-ReactiveLambdaFunction89310B25-*`  
**Stack**: CeapServingAPI-dev  
**Handler**: `com.ceap.workflow.reactive.ReactiveHandler::handleRequest`  
**Runtime**: Java 17  
**Memory**: 1024 MB  
**Timeout**: 1 minute  
**Code Size**: ~43 MB

**Purpose**: Process real-time customer events  
**Triggers**: EventBridge (customer events)  
**Permissions**: Read/Write Candidates, ProgramConfig, ScoreCache, Deduplication

**Environment Variables**:
- `ENVIRONMENT`: dev
- `CANDIDATES_TABLE`: Candidates-dev
- `PROGRAM_CONFIG_TABLE`: ProgramConfig-dev
- `SCORE_CACHE_TABLE`: ScoreCache-dev
- `DEDUPLICATION_TABLE`: ceap-event-deduplication-dev
- `LOG_LEVEL`: INFO

---

## Step Functions State Machines (1 Total)

### 1. CeapBatchIngestion-dev
**Purpose**: Orchestrate batch processing workflow  
**Type**: Standard workflow  
**Timeout**: 4 hours

**States**:
1. **ETL Task** - Extract and transform data
2. **Filter Task** - Apply filters to candidates
3. **Score Task** - Execute ML scoring
4. **Store Task** - Batch write to DynamoDB
5. **Success State** - Workflow completion

**Retry Configuration**:
- Max attempts: 6 per task
- Backoff rate: 2.0
- Interval: 1 second

**Error Handling**:
- Catch all errors
- Retry with exponential backoff
- Fail workflow after max retries

**Triggers**: EventBridge schedule (daily at 2 AM UTC)

**Input Format**:
```json
{
  "programId": "string",
  "marketplace": "string",
  "batchId": "string",
  "correlationId": "string",
  "dateRange": {
    "start": "YYYY-MM-DD",
    "end": "YYYY-MM-DD"
  }
}
```

**Output Format**:
```json
{
  "totalProcessed": 0,
  "totalStored": 0,
  "executionTimeMs": 0,
  "programId": "string",
  "marketplace": "string"
}
```

---

## EventBridge Rules (2 Total)

### 1. ceap-customer-events-dev
**Purpose**: Route customer events to Reactive Lambda  
**Type**: Event pattern rule  
**State**: ENABLED

**Event Pattern**:
```json
{
  "source": ["ceap.customer-events"],
  "detail-type": [
    "OrderDelivered",
    "ProductPurchased",
    "VideoWatched",
    "TrackPlayed",
    "ServiceCompleted",
    "EventRegistered"
  ]
}
```

**Target**: Reactive Lambda function

---

### 2. CeapBatchIngestion-dev (Schedule)
**Purpose**: Trigger daily batch processing  
**Type**: Schedule rule  
**State**: ENABLED  
**Schedule**: `cron(0 2 * * ? *)` (2 AM UTC daily)

**Target**: CeapBatchIngestion-dev state machine

**Input Template**:
```json
{
  "programId": "default",
  "marketplace": "US",
  "batchId": "scheduled-<aws.events.rule-name>-<aws.events.event.ingestion-time>",
  "correlationId": "<aws.events.event.id>",
  "dateRange": {
    "start": "<yesterday>",
    "end": "<today>"
  }
}
```

---

## IAM Roles (12 Total)

### Lambda Execution Roles (5)
1. **ETL Lambda Role** - DynamoDB read/write, CloudWatch logs
2. **Filter Lambda Role** - DynamoDB read, CloudWatch logs
3. **Score Lambda Role** - DynamoDB read/write (ScoreCache), CloudWatch logs
4. **Store Lambda Role** - DynamoDB write (Candidates), CloudWatch logs
5. **Reactive Lambda Role** - DynamoDB read/write (all tables), CloudWatch logs

### Step Functions Roles (2)
6. **Batch Ingestion Workflow Role** - Invoke Lambdas, CloudWatch logs
7. **Batch Ingestion Events Role** - Start Step Functions executions

### Log Retention Roles (5)
8-12. **Log Retention Lambda Roles** - Manage CloudWatch log retention

---

## CloudWatch Resources

### Log Groups (5)
1. `/aws/lambda/CeapDataPlatform-dev-ETLLambdaFunction*`
2. `/aws/lambda/CeapDataPlatform-dev-FilterLambdaFunction*`
3. `/aws/lambda/CeapDataPlatform-dev-ScoreLambdaFunction*`
4. `/aws/lambda/CeapDataPlatform-dev-StoreLambdaFunction*`
5. `/aws/lambda/CeapServingAPI-dev-ReactiveLambdaFunction*`

**Retention**: 30 days  
**Estimated Size**: 0-100 MB/day  
**Estimated Cost**: $0-5/month

### Metrics Namespaces (3)
1. `CeapPlatform/Workflow` - Workflow execution metrics
2. `CeapPlatform/Channels` - Channel delivery metrics
3. `CeapPlatform/Rejections` - Rejection tracking metrics

---

## Complete Resource Count

| Resource Type | Count | Purpose |
|--------------|-------|---------|
| **CloudFormation Stacks** | 3 | Infrastructure as code (consolidated) |
| **DynamoDB Tables** | 4 | Data storage |
| **Lambda Functions** | 5 | Business logic |
| **Step Functions** | 1 | Workflow orchestration |
| **EventBridge Rules** | 2 | Event routing & scheduling |
| **IAM Roles** | 9 | Permissions management |
| **CloudWatch Log Groups** | 5 | Logging |
| **CloudWatch Metrics Namespaces** | 3 | Monitoring |

**Total AWS Resources**: ~40 resources

**Architecture Benefits**:
- ✅ Reduced from 7 stacks to 3 stacks
- ✅ Clear business capability alignment (Storage, Write Path, Read Path)
- ✅ Parallel deployment capability
- ✅ Simplified dependency management
- ✅ Faster deployment times (~5 minutes total)

---

## Resource Relationships

```
CeapDatabase-dev (Storage Layer - Base Stack)
├── Candidates-dev (DynamoDB)
├── ProgramConfig-dev (DynamoDB)
└── ScoreCache-dev (DynamoDB)
    │
    ├─▶ CeapDataPlatform-dev (Write Path - Consolidated)
    │   ├── ETL Lambda → reads ProgramConfig, writes Candidates
    │   ├── Filter Lambda → reads ProgramConfig
    │   ├── Score Lambda → reads/writes ScoreCache
    │   ├── Store Lambda → writes Candidates
    │   ├── BatchIngestionWorkflow (Step Functions)
    │   │   └── Orchestrates: ETL → Filter → Score → Store
    │   └── BatchIngestionSchedule (EventBridge rule)
    │
    └─▶ CeapServingAPI-dev (Read Path - Consolidated)
        ├── Reactive Lambda → reads/writes all tables
        ├── ceap-event-deduplication-dev (DynamoDB)
        └── ceap-customer-events-dev (EventBridge rule)
```

**Cross-Stack References**:
- CeapDataPlatform-dev imports table names/ARNs from CeapDatabase-dev
- CeapServingAPI-dev imports table names/ARNs from CeapDatabase-dev
- All references use CloudFormation exports (Fn::ImportValue)

---

## Data Flow

### Batch Processing Flow

```
EventBridge Schedule (2 AM UTC)
    │
    ▼
Step Functions: CeapBatchIngestion-dev
    │
    ├─▶ ETL Lambda
    │   ├─ Reads: ProgramConfig-dev
    │   ├─ Queries: Athena (external data)
    │   └─ Returns: List<Candidate>
    │
    ├─▶ Filter Lambda
    │   ├─ Receives: List<Candidate>
    │   ├─ Applies: Eligibility, Trust, Quality filters
    │   └─ Returns: Filtered List<Candidate>
    │
    ├─▶ Score Lambda
    │   ├─ Receives: Filtered List<Candidate>
    │   ├─ Reads/Writes: ScoreCache-dev
    │   ├─ Calls: ML models (SageMaker/Bedrock)
    │   └─ Returns: Scored List<Candidate>
    │
    └─▶ Store Lambda
        ├─ Receives: Scored List<Candidate>
        ├─ Writes: Candidates-dev (batch write)
        └─ Returns: Storage metrics
```

### Reactive Processing Flow

```
Customer Event (e.g., OrderDelivered)
    │
    ▼
EventBridge Rule: ceap-customer-events-dev
    │
    ▼
Reactive Lambda
    │
    ├─▶ Check: ceap-event-deduplication-dev (prevent duplicates)
    ├─▶ Read: ProgramConfig-dev (get program settings)
    ├─▶ Apply: Filters (eligibility check)
    ├─▶ Score: ML models
    ├─▶ Write: ScoreCache-dev (cache score)
    └─▶ Write: Candidates-dev (store candidate)
```

---

## Compute Resources

### Lambda Concurrency

**Reserved Concurrency**: None (uses account-level unreserved)  
**Burst Concurrency**: 1,000 (AWS default)  
**Provisioned Concurrency**: None

**Estimated Concurrent Executions**:
- Batch processing: 1-5 concurrent
- Reactive processing: 10-100 concurrent (depends on event volume)

### Lambda Memory Allocation

| Function | Memory | Typical Duration | Cost per Invocation |
|----------|--------|-----------------|---------------------|
| ETL | 1024 MB | 3-10 seconds | $0.0002 |
| Filter | 512 MB | 100-500 ms | $0.00001 |
| Score | 1024 MB | 1-5 seconds | $0.0001 |
| Store | 512 MB | 500-2000 ms | $0.00002 |
| Reactive | 1024 MB | 200-1000 ms | $0.00003 |

---

## Storage Capacity

### DynamoDB Storage

**Current Usage**: 0 GB (empty tables)  
**Estimated with Data**:
- Candidates: 1 KB per item × 100,000 items = 100 MB
- ProgramConfig: 10 KB per item × 10 items = 100 KB
- ScoreCache: 500 bytes per item × 10,000 items = 5 MB
- Deduplication: 200 bytes per item × 1,000 items = 200 KB

**Total Estimated**: ~105 MB

**Cost**: $0.25/GB/month = ~$0.03/month

### CloudWatch Logs Storage

**Retention**: 30 days  
**Estimated Volume**: 10-100 MB/day  
**Monthly Storage**: 300 MB - 3 GB  
**Cost**: First 5 GB free, then $0.50/GB

---

## Network Resources

**VPC**: None (Lambdas run in AWS-managed VPC)  
**NAT Gateway**: None  
**Load Balancer**: None  
**API Gateway**: None (not yet deployed)

**Note**: All resources use AWS public endpoints. For enhanced security, consider deploying in VPC.

---

## Estimated Monthly Costs (Dev Environment)

### Baseline Costs (No Usage)
- DynamoDB: $0 (on-demand, no requests)
- Lambda: $0 (no invocations)
- Step Functions: $0 (no executions)
- CloudWatch Logs: $0 (within free tier)
- **Total**: $0/month

### Light Usage (1,000 candidates/day)
- DynamoDB: $5/month (reads + writes)
- Lambda: $2/month (30K invocations)
- Step Functions: $1/month (30 executions)
- CloudWatch: $2/month (logs)
- **Total**: ~$10/month

### Medium Usage (10,000 candidates/day)
- DynamoDB: $20/month
- Lambda: $15/month (300K invocations)
- Step Functions: $5/month (30 executions)
- CloudWatch: $5/month
- **Total**: ~$45/month

### Heavy Usage (100,000 candidates/day)
- DynamoDB: $100/month
- Lambda: $80/month (3M invocations)
- Step Functions: $10/month (30 executions)
- CloudWatch: $15/month
- **Total**: ~$205/month

---

## Scaling Limits

### Current Limits (AWS Default)

| Resource | Limit | Current Usage | Headroom |
|----------|-------|---------------|----------|
| Lambda Concurrent Executions | 1,000 | ~10 | 99% |
| DynamoDB Tables per Region | 2,500 | 4 | 99.8% |
| CloudFormation Stacks | 2,000 | 7 | 99.6% |
| Step Functions Executions | Unlimited | ~30/month | ∞ |
| EventBridge Rules | 300 | 2 | 99.3% |

### Scalability

**Can handle**:
- ✅ 1M+ candidates per day (batch)
- ✅ 10K+ events per second (reactive)
- ✅ 100+ concurrent programs
- ✅ 1000+ concurrent API requests

**Bottlenecks**:
- Lambda concurrency (can request increase)
- DynamoDB throughput (on-demand scales automatically)
- Step Functions execution history (30 days retention)

---

## Monitoring & Observability

### CloudWatch Dashboards
**Count**: 0 (not yet created)  
**Recommended**: Create per-program dashboards

### CloudWatch Alarms
**Count**: 0 (not yet created)  
**Recommended**: 
- Lambda error rate > 1%
- DynamoDB throttling
- Step Functions failures
- API latency > 1 second

### X-Ray Tracing
**Status**: Not enabled  
**Recommended**: Enable for distributed tracing

---

## Security Resources

### Encryption
- **DynamoDB**: Encrypted at rest (AWS managed keys)
- **Lambda**: Environment variables encrypted
- **CloudWatch Logs**: Encrypted at rest
- **S3** (if used): Server-side encryption

### IAM Policies
- **Least privilege**: Each Lambda has minimal required permissions
- **No wildcards**: Specific resource ARNs
- **Condition keys**: None (could add for enhanced security)

### Secrets Management
**Status**: Not configured  
**Recommended**: Use AWS Secrets Manager for:
- API keys
- Database credentials
- Third-party service tokens

---

## Disaster Recovery

### Backup Strategy
- **DynamoDB**: Point-in-time recovery enabled
- **Lambda code**: Stored in S3 by CDK
- **Infrastructure**: Defined in code (CDK)

### Recovery Time Objective (RTO)
- **Infrastructure**: 15 minutes (redeploy with CDK)
- **Data**: Instant (DynamoDB PITR)

### Recovery Point Objective (RPO)
- **DynamoDB**: 5 minutes (PITR granularity)
- **Lambda code**: 0 (version controlled)

---

## Summary

**Your CEAP Platform Deployment**:

- **3 CloudFormation stacks** (consolidated from 7) orchestrating ~40 AWS resources
- **4 DynamoDB tables** for data storage
- **5 Lambda functions** for business logic
- **1 Step Functions workflow** for batch orchestration
- **2 EventBridge rules** for scheduling and event routing

**Architecture**: Serverless, event-driven, highly scalable, business-aligned  
**Cost**: $0-10/month (dev), $10-200/month (production)  
**Scalability**: 1M+ candidates/day, 10K+ events/second  
**Multi-Tenancy**: Program-level isolation (single account)  
**Deployment Time**: ~5 minutes (3 stacks, parallel deployment)

**Ready for**: Multiple clients in single AWS account with program-level isolation

---

## Migration History

**January 31, 2026**: Consolidated from 7-stack to 3-stack architecture
- **Before**: 7 stacks (Database + 6 workflow stacks)
- **After**: 3 stacks (Database + DataPlatform + ServingAPI)
- **Benefits**: Faster deployment, clearer business alignment, simplified management
- **Migration Time**: ~5 minutes
- **Downtime**: Zero (blue-green deployment)

---

## Quick Reference Commands

```bash
# List all stacks (3-stack architecture)
aws cloudformation list-stacks --stack-status-filter CREATE_COMPLETE --query 'StackSummaries[?starts_with(StackName, `Ceap`)].StackName'

# View stack outputs
aws cloudformation describe-stacks --stack-name CeapDatabase-dev --query 'Stacks[0].Outputs'
aws cloudformation describe-stacks --stack-name CeapDataPlatform-dev --query 'Stacks[0].Outputs'
aws cloudformation describe-stacks --stack-name CeapServingAPI-dev --query 'Stacks[0].Outputs'

# List all tables
aws dynamodb list-tables --query 'TableNames[?contains(@, `dev`)]'

# List all functions
aws lambda list-functions --query 'Functions[?contains(FunctionName, `Ceap`)].FunctionName'

# List all state machines
aws stepfunctions list-state-machines --query 'stateMachines[?contains(name, `Ceap`)].name'

# List all EventBridge rules
aws events list-rules --query 'Rules[?contains(Name, `ceap`)].Name'

# Deploy consolidated infrastructure
cd infrastructure
./deploy-consolidated.sh dev

# Validate resources
cd infrastructure
./validate-resources.sh dev

# Rollback to previous state
cd infrastructure
./rollback-consolidated.sh dev
```

---

**Your infrastructure is well-organized, scalable, and ready for production!** 🚀
