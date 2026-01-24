# Lambda Function Configurations

> **Platform Rebranding Note**: This platform was formerly known as the "General Solicitation Platform". We've rebranded to "Customer Engagement & Action Platform (CEAP)" to better reflect its capabilities beyond customer engagement. Package names now use `com.ceap.*` and handler paths use `com.ceap.workflow.*` following the CEAP branding.

This document describes the Lambda function configurations for the Customer Engagement & Action Platform (CEAP).

## Overview

The platform uses five core Lambda functions to implement the customer engagement workflow:

1. **ETL Lambda** - Extract, Transform, Load data from various sources
2. **Filter Lambda** - Apply eligibility and business rule filters
3. **Score Lambda** - Execute scoring models and cache results
4. **Store Lambda** - Batch write candidates to DynamoDB
5. **Serve Lambda** - Low-latency API for candidate retrieval

## Lambda Function Specifications

### 1. ETL Lambda Function

**Purpose**: Extract data from data warehouses, transform to unified candidate model

**Configuration**:
- **Memory**: 1024 MB
- **Timeout**: 300 seconds (5 minutes)
- **Runtime**: Java 17
- **Handler**: `com.ceap.workflow.ETLHandler::handleRequest`

**Environment Variables**:
- `ENVIRONMENT` - Deployment environment (dev/staging/prod)
- `DATA_SOURCE_CONFIG` - S3 bucket for data source configurations
- `BATCH_SIZE` - Number of records to process per batch (default: 1000)
- `LOG_LEVEL` - Logging level (INFO/DEBUG/WARN/ERROR)
- `CANDIDATES_TABLE` - DynamoDB table name for candidates
- `PROGRAM_CONFIG_TABLE` - DynamoDB table name for program configurations

**IAM Permissions**:
- DynamoDB: PutItem, UpdateItem, GetItem, Query, Scan, BatchWriteItem on Candidates table
- Athena: StartQueryExecution, GetQueryExecution, GetQueryResults, StopQueryExecution
- S3: GetObject, ListBucket on data source buckets
- S3: PutObject, GetObject on Athena results bucket
- Glue: GetDatabase, GetTable, GetPartitions for data catalog access

**Use Cases**:
- Batch ingestion from data warehouses
- Scheduled ETL jobs via EventBridge
- Data transformation and validation

---

### 2. Filter Lambda Function

**Purpose**: Apply eligibility filters and business rules to candidates

**Configuration**:
- **Memory**: 512 MB
- **Timeout**: 60 seconds
- **Runtime**: Java 17
- **Handler**: `com.ceap.workflow.FilterHandler::handleRequest`

**Environment Variables**:
- `ENVIRONMENT` - Deployment environment
- `FILTER_CONFIG` - S3 bucket for filter configurations
- `LOG_LEVEL` - Logging level
- `PROGRAM_CONFIG_TABLE` - DynamoDB table for program configurations

**IAM Permissions**:
- DynamoDB: GetItem, Query on ProgramConfig table
- API Gateway: Invoke for external eligibility service calls

**Use Cases**:
- Batch workflow filtering stage
- Reactive workflow filtering
- Real-time eligibility checks

---

### 3. Score Lambda Function

**Purpose**: Execute scoring models and manage score caching

**Configuration**:
- **Memory**: 1024 MB
- **Timeout**: 120 seconds (2 minutes)
- **Runtime**: Java 17
- **Handler**: `com.ceap.workflow.ScoreHandler::handleRequest`

**Environment Variables**:
- `ENVIRONMENT` - Deployment environment
- `MODEL_ENDPOINTS` - Configuration for SageMaker model endpoints
- `FEATURE_STORE_CONFIG` - Feature store configuration
- `LOG_LEVEL` - Logging level
- `SCORE_CACHE_TABLE` - DynamoDB table for score caching
- `CACHE_TTL_HOURS` - Cache TTL in hours (default: 24)

**IAM Permissions**:
- DynamoDB: PutItem, GetItem, Query, UpdateItem on ScoreCache table
- SageMaker: InvokeEndpoint for model inference
- SageMaker: GetRecord, BatchGetRecord for feature store access

**Use Cases**:
- Batch scoring in workflows
- Real-time scoring for reactive candidates
- Score caching and retrieval

---

### 4. Store Lambda Function

**Purpose**: Batch write candidates to DynamoDB with optimized throughput

**Configuration**:
- **Memory**: 512 MB
- **Timeout**: 60 seconds
- **Runtime**: Java 17
- **Handler**: `com.ceap.workflow.StoreHandler::handleRequest`

**Environment Variables**:
- `ENVIRONMENT` - Deployment environment
- `BATCH_SIZE` - DynamoDB batch write size (default: 25, max: 25)
- `LOG_LEVEL` - Logging level
- `CANDIDATES_TABLE` - DynamoDB table for candidates

**IAM Permissions**:
- DynamoDB: PutItem, BatchWriteItem, UpdateItem on Candidates table

**Use Cases**:
- Final stage of batch workflow
- Bulk candidate storage
- Optimized batch writes with retry logic

---

### 5. Serve Lambda Function

**Purpose**: Low-latency API for retrieving candidates by customer and channel

**Configuration**:
- **Memory**: 512 MB
- **Timeout**: 30 seconds
- **Runtime**: Java 17
- **Handler**: `com.ceap.serving.ServeHandler::handleRequest`

**Environment Variables**:
- `ENVIRONMENT` - Deployment environment
- `CACHE_CONFIG` - Caching configuration
- `LOG_LEVEL` - Logging level
- `CANDIDATES_TABLE` - DynamoDB table for candidates
- `MAX_RESULTS_PER_QUERY` - Maximum results per API call (default: 100)

**IAM Permissions**:
- DynamoDB: GetItem, Query, BatchGetItem on Candidates table and indexes
- API Gateway: Invoke for integration

**Use Cases**:
- Real-time candidate retrieval for channels
- Customer-specific candidate queries
- Channel-specific ranking and filtering

---

## IAM Roles

Each Lambda function has a dedicated IAM role following the principle of least privilege:

- **ETLLambdaRole** - Full data access for ETL operations
- **FilterLambdaRole** - Read access to configurations and external services
- **ScoreLambdaRole** - Model invocation and score caching
- **StoreLambdaRole** - Write access to candidate storage
- **ServeLambdaRole** - Read access to candidate storage

All roles include:
- Basic Lambda execution permissions (CloudWatch Logs)
- Environment-specific resource access
- Proper resource tagging for cost attribution

## CloudWatch Logging

Each Lambda function has a dedicated CloudWatch Log Group:
- Log Group Pattern: `/aws/lambda/${ProjectName}-{function}-${Environment}`
- Retention: 30 days
- Structured JSON logging with correlation IDs
- PII redaction enabled

**Note**: ProjectName is typically `ceap-platform` or `customer-engagement-platform`

## Deployment

### Prerequisites

1. AWS CLI configured with appropriate credentials
2. S3 bucket for Lambda artifacts: `${ProjectName}-lambda-artifacts-${Environment}`
3. DynamoDB tables deployed (see `dynamodb-tables.yaml`)
4. Lambda function JARs built and ready

### Deploy Lambda Configurations

```bash
# Set environment variables
export ENVIRONMENT=dev
export PROJECT_NAME=ceap-platform
export AWS_REGION=us-east-1

# Deploy the stack
cd infrastructure
./deploy-lambda.sh
```

### Update Lambda Function Code

After deploying the configurations, upload and update function code:

```bash
# Build the Lambda JARs using Gradle
./gradlew shadowJar

# Upload to S3
aws s3 cp ceap-workflow-etl/build/libs/ceap-workflow-etl-1.0.0-SNAPSHOT.jar \
  s3://${PROJECT_NAME}-lambda-artifacts-${ENVIRONMENT}/etl-lambda.jar
aws s3 cp ceap-workflow-filter/build/libs/ceap-workflow-filter-1.0.0-SNAPSHOT.jar \
  s3://${PROJECT_NAME}-lambda-artifacts-${ENVIRONMENT}/filter-lambda.jar
aws s3 cp ceap-workflow-score/build/libs/ceap-workflow-score-1.0.0-SNAPSHOT.jar \
  s3://${PROJECT_NAME}-lambda-artifacts-${ENVIRONMENT}/score-lambda.jar
aws s3 cp ceap-workflow-store/build/libs/ceap-workflow-store-1.0.0-SNAPSHOT.jar \
  s3://${PROJECT_NAME}-lambda-artifacts-${ENVIRONMENT}/store-lambda.jar
aws s3 cp ceap-serving/build/libs/ceap-serving-1.0.0-SNAPSHOT.jar \
  s3://${PROJECT_NAME}-lambda-artifacts-${ENVIRONMENT}/serve-lambda.jar

# Update Lambda functions
aws lambda update-function-code \
  --function-name ${PROJECT_NAME}-etl-${ENVIRONMENT} \
  --s3-bucket ${PROJECT_NAME}-lambda-artifacts-${ENVIRONMENT} \
  --s3-key etl-lambda.jar

# Repeat for other functions...
```

## Monitoring and Observability

### CloudWatch Metrics

Each Lambda function automatically publishes:
- Invocations
- Duration
- Errors
- Throttles
- Concurrent executions

### Custom Metrics

Functions publish custom metrics:
- Records processed
- Filter pass/fail rates
- Scoring latency
- Cache hit rates
- API response times

### Alarms

Configure CloudWatch Alarms for:
- Error rate > 5%
- Duration > 80% of timeout
- Throttles > 0
- Concurrent executions approaching limits

### Structured Logging

All logs follow structured JSON format:

```json
{
  "timestamp": "2026-01-20T10:30:00.000Z",
  "level": "INFO",
  "correlationId": "uuid-1234-5678",
  "service": "ceap-platform",
  "component": "ETLLambda",
  "message": "Processing batch",
  "context": {
    "programId": "retail",
    "marketplace": "US",
    "batchSize": 1000,
    "recordsProcessed": 850
  }
}
```

## Cost Optimization

### Memory Sizing

Functions are sized based on workload:
- **ETL & Score**: 1024 MB (compute-intensive)
- **Filter, Store, Serve**: 512 MB (I/O-intensive)

### Timeout Configuration

Timeouts are set to balance:
- Sufficient time for normal operations
- Fast failure for error cases
- Cost control

### Concurrency

- Use reserved concurrency for critical functions
- Set appropriate limits to prevent runaway costs
- Monitor concurrent execution metrics

## Security Best Practices

1. **Least Privilege IAM**: Each function has minimal required permissions
2. **Encryption**: All data encrypted in transit and at rest
3. **VPC Integration**: Consider VPC deployment for sensitive workloads
4. **Secrets Management**: Use AWS Secrets Manager for sensitive configuration
5. **PII Handling**: Automatic PII redaction in logs

## Troubleshooting

### Common Issues

**Issue**: Lambda timeout
- **Solution**: Increase timeout or optimize code, check for external service delays

**Issue**: Out of memory
- **Solution**: Increase memory allocation or optimize data processing

**Issue**: IAM permission denied
- **Solution**: Verify IAM role has required permissions, check resource ARNs

**Issue**: Cold start latency
- **Solution**: Use provisioned concurrency for latency-sensitive functions

### Debug Steps

1. Check CloudWatch Logs for error messages
2. Review X-Ray traces for performance bottlenecks
3. Verify environment variables are set correctly
4. Test IAM permissions using AWS CLI
5. Check DynamoDB table capacity and throttling

## Next Steps

After deploying Lambda functions:

1. Deploy Step Functions workflows (see `step-functions.yaml`)
2. Configure EventBridge rules (see `eventbridge-rules.yaml`)
3. Set up API Gateway for Serve Lambda
4. Configure CloudWatch dashboards and alarms
5. Implement CI/CD pipeline for automated deployments

## References

- [AWS Lambda Developer Guide](https://docs.aws.amazon.com/lambda/)
- [Lambda Best Practices](https://docs.aws.amazon.com/lambda/latest/dg/best-practices.html)
- [Java Lambda Functions](https://docs.aws.amazon.com/lambda/latest/dg/java-handler.html)
- [DynamoDB Best Practices](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/best-practices.html)
