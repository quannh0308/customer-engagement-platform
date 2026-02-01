# Workflow Orchestration Guide

## Overview

This guide documents the CEAP infrastructure enhancement that implements industry-standard AWS Step Functions orchestration patterns with S3-based intermediate storage. The enhancement provides two deployment options for workflow orchestration:

- **Express Workflow**: For fast processing (<5 minutes), synchronous invocation, cost-optimized
- **Standard Workflow**: For long-running jobs (up to 1 year), asynchronous invocation, supports Glue/EMR integration

## Workflow Type Selection Criteria

### When to Use Express Workflow

**Choose Express Workflow if:**
- All processing stages complete within 5 minutes
- All stages are Lambda functions (no Glue jobs)
- You need synchronous execution with immediate feedback
- Cost optimization is important ($1 per million transitions)
- You need low latency for real-time processing

**Example Use Cases:**
- Real-time data validation and filtering
- Fast API response enrichment
- Lightweight ETL for small datasets (<10MB)
- Event-driven reactive workflows
- Synchronous request-response patterns

**Limitations:**
- Maximum execution time: 5 minutes
- Cannot include Glue jobs or other long-running services
- Limited execution history retention (90 days)

### When to Use Standard Workflow

**Choose Standard Workflow if:**
- Any processing stage can exceed 5 minutes
- You need to integrate Glue jobs for complex ETL
- You need asynchronous execution (fire-and-forget)
- You need long execution history retention (1 year)
- You're processing large datasets requiring distributed computing

**Example Use Cases:**
- Batch data processing with Glue jobs
- Multi-hour ETL transformations
- Complex data enrichment requiring external API calls
- Machine learning model training pipelines
- Data warehouse loading workflows

**Limitations:**
- Higher cost ($25 per million transitions)
- Asynchronous execution (no immediate response)
- Requires EventBridge rules for failure detection

### Decision Matrix

| Criteria | Express | Standard |
|----------|---------|----------|
| Max execution time | 5 minutes | 1 year |
| Glue job support | ❌ No | ✅ Yes |
| Invocation type | Synchronous | Asynchronous |
| Cost per million | $1 | $25 |
| Failure detection | Built-in (sync) | EventBridge rule |
| Use case | Real-time | Batch processing |

## Glue Job Integration

### Overview

Standard workflows support integrating AWS Glue jobs for long-running ETL processes that exceed Lambda's 15-minute timeout. Glue jobs can be positioned at any point in the workflow sequence.

### Integration Steps

#### 1. Create Glue Job Script

Create a PySpark script following the S3 I/O convention:

```python
import sys
from awsglue.utils import getResolvedOptions
from pyspark.context import SparkContext
from awsglue.context import GlueContext
from awsglue.job import Job

# Get arguments from Step Functions
args = getResolvedOptions(sys.argv, [
    'JOB_NAME',
    'execution-id',
    'input-bucket',
    'input-key',
    'output-bucket',
    'output-key'
])

sc = SparkContext()
glueContext = GlueContext(sc)
spark = glueContext.spark_session
job = Job(glueContext)
job.init(args['JOB_NAME'], args)

# Read input from previous stage
input_path = f"s3://{args['input-bucket']}/{args['input-key']}"
print(f"Reading input from {input_path}")
df = spark.read.json(input_path)

# Perform ETL transformations
transformed_df = df.transform(your_transformation_logic)

# Write output for next stage
output_path = f"s3://{args['output-bucket']}/{args['output-key']}"
print(f"Writing output to {output_path}")
transformed_df.write.mode('overwrite').json(output_path)

job.commit()
```

#### 2. Upload Script to S3

```bash
aws s3 cp glue-etl-script.py s3://your-glue-scripts-bucket/scripts/
```

#### 3. Configure Workflow with Glue Step

```kotlin
val workflowConfig = WorkflowConfiguration(
    workflowName = "DataProcessingWorkflow",
    workflowType = WorkflowType.STANDARD,  // Required for Glue
    steps = listOf(
        WorkflowStepType.Lambda(LambdaStep("ETLStage", "etlLambda")),
        WorkflowStepType.Lambda(LambdaStep("FilterStage", "filterLambda")),
        WorkflowStepType.Glue(GlueStep("HeavyETLStage", "heavy-etl-job")),  // Glue job
        WorkflowStepType.Lambda(LambdaStep("ScoreStage", "scoreLambda")),
        WorkflowStepType.Lambda(LambdaStep("StoreStage", "storeLambda"))
    ),
    workflowBucket = "ceap-workflow-dev-123456789",
    sourceQueue = "workflow-trigger-queue",
    retryConfig = RetryConfiguration()
)
```

#### 4. Deploy Infrastructure

```bash
cd infrastructure
./gradlew build
cdk deploy WorkflowStack
```

### Glue Job Configuration

**Recommended Settings:**
- **DPUs**: Start with 2-5 DPUs, scale based on data volume
- **Timeout**: Set based on expected processing time + buffer (e.g., 2 hours)
- **Max Retries**: 2 (configured automatically by Step Functions)
- **Worker Type**: G.1X for general purpose, G.2X for memory-intensive

**IAM Permissions Required:**
- S3 read/write access to workflow bucket
- CloudWatch Logs write access
- Glue service role permissions

### Positioning Flexibility

Glue jobs can be inserted at any position:

```kotlin
// Start with Glue job
listOf(
    WorkflowStepType.Glue(GlueStep("InitialETL", "initial-etl")),
    WorkflowStepType.Lambda(LambdaStep("FilterStage", "filterLambda")),
    ...
)

// Multiple Glue jobs
listOf(
    WorkflowStepType.Lambda(LambdaStep("ETLStage", "etlLambda")),
    WorkflowStepType.Glue(GlueStep("EnrichmentETL", "enrichment-job")),
    WorkflowStepType.Lambda(LambdaStep("FilterStage", "filterLambda")),
    WorkflowStepType.Glue(GlueStep("AggregationETL", "aggregation-job")),
    WorkflowStepType.Lambda(LambdaStep("StoreStage", "storeLambda"))
)

// End with Glue job
listOf(
    WorkflowStepType.Lambda(LambdaStep("ETLStage", "etlLambda")),
    WorkflowStepType.Lambda(LambdaStep("FilterStage", "filterLambda")),
    WorkflowStepType.Glue(GlueStep("FinalExport", "export-job"))
)
```

## Migration Strategy

### Overview

The infrastructure enhancement is designed for incremental migration without disrupting existing functionality. This section provides a phased approach to adopting the new orchestration patterns.

### Migration Phases

#### Phase 1: Lambda Naming (Low Risk)

**Goal**: Update Lambda function names to remove auto-generated suffixes

**Steps:**
1. Update CDK constructs to use explicit `functionName` property
2. Deploy to development environment
3. Verify CloudWatch log groups use clean names
4. Test existing functionality
5. Deploy to production during maintenance window

**Rollback**: Revert CDK changes and redeploy

**Risk**: Low - naming change only, no functional changes

**Duration**: 1-2 hours

#### Phase 2: S3 Bucket and Base Handler (Medium Risk)

**Goal**: Create S3 workflow bucket and implement base Lambda handler

**Steps:**
1. Deploy S3 bucket with lifecycle policy
2. Implement `WorkflowLambdaHandler` base class
3. Deploy to development environment
4. Test S3 read/write operations
5. Verify IAM permissions

**Rollback**: Delete S3 bucket, revert code changes

**Risk**: Medium - new infrastructure, no impact on existing workflows

**Duration**: 2-4 hours

#### Phase 3: Migrate One Lambda Function (Medium Risk)

**Goal**: Migrate a single Lambda function to use base handler and S3

**Steps:**
1. Choose low-risk Lambda (e.g., ReactiveStage)
2. Refactor to extend `WorkflowLambdaHandler`
3. Deploy to development environment
4. Test with mock execution context
5. Verify S3 output is written correctly
6. Monitor for 24 hours

**Rollback**: Revert Lambda code to previous version

**Risk**: Medium - isolated to one Lambda function

**Duration**: 4-8 hours

#### Phase 4: Deploy Express Workflow (High Risk)

**Goal**: Deploy complete Express workflow for one use case

**Steps:**
1. Migrate all Lambda functions to use base handler
2. Create Express workflow CDK stack
3. Deploy to development environment
4. Run integration tests
5. Monitor execution metrics
6. Gradually shift traffic from old to new workflow
7. Deploy to production after 1 week of stable operation

**Rollback**: Shift traffic back to old workflow, delete Step Functions

**Risk**: High - complete workflow replacement

**Duration**: 1-2 weeks

#### Phase 5: Add Glue Job Integration (Optional)

**Goal**: Integrate Glue jobs for long-running ETL

**Steps:**
1. Create Glue job script following S3 convention
2. Deploy Glue job to AWS
3. Create Standard workflow with Glue step
4. Test end-to-end execution
5. Monitor Glue job performance and cost
6. Optimize DPU allocation

**Rollback**: Remove Glue step, use Lambda-only workflow

**Risk**: Medium - new service integration

**Duration**: 1-2 weeks

### Backward Compatibility Features

#### Dual Naming Pattern Support

During migration, the infrastructure supports both naming patterns:

```kotlin
// Old pattern (auto-generated)
val oldLambda = Function.Builder.create(this, "ReactiveLambdaFunction")
    .runtime(Runtime.JAVA_17)
    .handler("com.example.Handler")
    .code(Code.fromAsset("lambda.jar"))
    .build()
// Results in: CeapServingAPI-dev-ReactiveLambdaFunction89310B25-jLlIqkWt5O4x

// New pattern (explicit)
val newLambda = Function.Builder.create(this, "ReactiveLambdaFunctionNew")
    .functionName("${stackName}-${environment}-ReactiveLambdaFunction")
    .runtime(Runtime.JAVA_17)
    .handler("com.example.Handler")
    .code(Code.fromAsset("lambda.jar"))
    .build()
// Results in: CeapServingAPI-dev-ReactiveLambdaFunction
```

Both can coexist during migration.

#### Incremental Stage Migration

Migrate stages one at a time:

```kotlin
// Week 1: Migrate ReactiveStage only
val workflow1 = listOf(
    WorkflowStepType.Lambda(LambdaStep("ETLStage", "etlLambdaOld")),      // Old
    WorkflowStepType.Lambda(LambdaStep("FilterStage", "filterLambdaOld")), // Old
    WorkflowStepType.Lambda(LambdaStep("ScoreStage", "scoreLambdaOld")),   // Old
    WorkflowStepType.Lambda(LambdaStep("StoreStage", "storeLambdaOld")),   // Old
    WorkflowStepType.Lambda(LambdaStep("ReactiveStage", "reactiveLambdaNew")) // New
)

// Week 2: Migrate StoreStage
val workflow2 = listOf(
    WorkflowStepType.Lambda(LambdaStep("ETLStage", "etlLambdaOld")),
    WorkflowStepType.Lambda(LambdaStep("FilterStage", "filterLambdaOld")),
    WorkflowStepType.Lambda(LambdaStep("ScoreStage", "scoreLambdaOld")),
    WorkflowStepType.Lambda(LambdaStep("StoreStage", "storeLambdaNew")),    // New
    WorkflowStepType.Lambda(LambdaStep("ReactiveStage", "reactiveLambdaNew"))
)

// Continue until all stages migrated
```

### Migration Validation

After each phase, validate:

1. **Functional Correctness**: Run integration tests
2. **Performance**: Compare execution times
3. **Cost**: Monitor AWS costs (Lambda, Step Functions, S3)
4. **Observability**: Verify CloudWatch Logs and X-Ray traces
5. **Error Handling**: Test failure scenarios and DLQ processing

### Migration Checklist

- [ ] Phase 1: Lambda naming updated and deployed
- [ ] Phase 2: S3 bucket and base handler deployed
- [ ] Phase 3: One Lambda migrated and tested
- [ ] Phase 4: Express workflow deployed and stable
- [ ] Phase 5: Glue integration tested (if needed)
- [ ] Old infrastructure decommissioned
- [ ] Documentation updated
- [ ] Team trained on new patterns

## Configuration Reference

### Workflow Configuration

```kotlin
data class WorkflowConfiguration(
    val workflowName: String,           // Unique workflow identifier
    val workflowType: WorkflowType,     // EXPRESS or STANDARD
    val steps: List<WorkflowStepType>,  // Ordered list of Lambda/Glue steps
    val workflowBucket: String,         // S3 bucket for intermediate storage
    val sourceQueue: String,            // SQS queue ARN for triggers
    val retryConfig: RetryConfiguration // Retry settings
)

enum class WorkflowType {
    EXPRESS,    // <5 minutes, synchronous, $1 per million
    STANDARD    // Up to 1 year, asynchronous, $25 per million
}
```

### Retry Configuration

```kotlin
data class RetryConfiguration(
    val lambdaMaxAttempts: Int = 2,        // Lambda retry attempts
    val lambdaIntervalSeconds: Int = 20,   // Initial retry interval
    val lambdaBackoffRate: Double = 2.0,   // Exponential backoff
    val glueMaxAttempts: Int = 2,          // Glue retry attempts
    val glueIntervalMinutes: Int = 5,      // Initial retry interval
    val glueBackoffRate: Double = 2.0      // Exponential backoff
)
```

### S3 Path Convention

All intermediate outputs follow this convention:

```
s3://{workflowBucket}/executions/{executionId}/{stageName}/output.json
```

**Example:**
```
s3://ceap-workflow-dev-123456789/
  executions/abc-123-def-456/
    ├── ETLStage/output.json
    ├── FilterStage/output.json
    ├── ScoreStage/output.json
    ├── StoreStage/output.json
    └── ReactiveStage/output.json
```

## Best Practices

### Workflow Design

1. **Keep Stages Small**: Each stage should have a single responsibility
2. **Use Descriptive Names**: Stage names should clearly indicate their purpose
3. **Handle Errors Gracefully**: Implement proper error handling in each Lambda
4. **Log Execution Context**: Always log executionId and stage name
5. **Monitor Performance**: Track execution time and optimize slow stages

### Lambda Implementation

1. **Extend Base Handler**: Always extend `WorkflowLambdaHandler`
2. **Avoid Hardcoded Paths**: Use execution context for S3 paths
3. **Validate Input**: Check input data structure before processing
4. **Write Structured Output**: Use consistent JSON schema for outputs
5. **Test Independently**: Write unit tests with mock execution context

### Glue Job Development

1. **Follow S3 Convention**: Read from input-key, write to output-key
2. **Handle Large Data**: Use Spark partitioning for large datasets
3. **Optimize DPUs**: Start small and scale based on performance
4. **Log Progress**: Use print statements for debugging
5. **Test Locally**: Use Glue development endpoints for testing

### Cost Optimization

1. **Choose Right Workflow Type**: Use Express for fast workflows
2. **Optimize Lambda Memory**: Right-size memory allocation
3. **Use S3 Lifecycle**: Automatically delete old execution data
4. **Monitor Glue DPUs**: Scale down when possible
5. **Batch Processing**: Process multiple records per execution

## Troubleshooting

### Common Issues

**Issue**: Lambda function name conflicts
**Solution**: Check for existing functions with same name, use unique names

**Issue**: S3 access denied errors
**Solution**: Verify IAM roles have S3 read/write permissions

**Issue**: Step Functions execution timeout
**Solution**: Use Standard workflow for long-running processes

**Issue**: Glue job fails with OOM
**Solution**: Increase DPUs or optimize data processing logic

**Issue**: DLQ messages accumulating
**Solution**: Investigate root cause, fix issue, manually reprocess

For detailed troubleshooting, see [Operations Runbook](WORKFLOW-OPERATIONS-RUNBOOK.md).

## Additional Resources

- [AWS Step Functions Best Practices](https://docs.aws.amazon.com/step-functions/latest/dg/best-practices.html)
- [AWS Glue Developer Guide](https://docs.aws.amazon.com/glue/latest/dg/what-is-glue.html)
- [EventBridge Pipes Documentation](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-pipes.html)
- [Operations Runbook](WORKFLOW-OPERATIONS-RUNBOOK.md)
- [Deployment Scripts](../infrastructure/README.md)
