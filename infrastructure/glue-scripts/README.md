# CEAP Glue Job Scripts

This directory contains PySpark scripts for AWS Glue jobs integrated with CEAP workflows.

## Overview

Glue jobs enable long-running ETL processes that exceed Lambda's 15-minute timeout. They integrate seamlessly with CEAP workflows using the same S3-based orchestration pattern as Lambda functions.

## Script Template

The `workflow_etl_template.py` script provides a complete template for creating Glue jobs that integrate with CEAP workflows.

### Key Features

- **Convention-Based Paths**: Reads input from previous stage's S3 output, writes to its own S3 path
- **Execution Context**: Receives execution ID and stage information from Step Functions
- **Error Handling**: Comprehensive error handling with detailed logging
- **Scalability**: Configurable workers and DPUs for different workload sizes

### Arguments

The script expects the following arguments from Step Functions:

| Argument | Description | Example |
|----------|-------------|---------|
| `--execution-id` | Step Functions execution ID | `abc-123-def-456` |
| `--input-bucket` | S3 bucket for input data | `ceap-workflow-dev-123456789` |
| `--input-key` | S3 key for input data | `executions/abc-123/ETLStage/output.json` |
| `--output-bucket` | S3 bucket for output data | `ceap-workflow-dev-123456789` |
| `--output-key` | S3 key for output data | `executions/abc-123/HeavyETLStage/output.json` |
| `--current-stage` | Current stage name | `HeavyETLStage` |
| `--previous-stage` | Previous stage name | `ETLStage` |

These arguments are automatically provided by the Step Functions workflow when using the `WorkflowFactory.createStandardWorkflow()` method.

## Usage

### 1. Upload Script to S3

First, upload your PySpark script to an S3 bucket:

```bash
aws s3 cp workflow_etl_template.py s3://my-scripts-bucket/glue-scripts/
```

### 2. Create Glue Job in CDK

Use the `WorkflowFactory.createGlueJob()` helper method:

```kotlin
import com.ceap.infrastructure.workflow.WorkflowFactory

// Create Glue job
val heavyETLJob = WorkflowFactory.createGlueJob(
    scope = this,
    id = "HeavyETLJob",
    jobName = "ceap-heavy-etl-job",
    scriptLocation = "s3://my-scripts-bucket/glue-scripts/workflow_etl_template.py",
    workflowBucket = workflowBucket,
    numberOfWorkers = 10,  // Scale up for large datasets
    timeout = 240  // 4 hours for complex transformations
)
```

### 3. Add to Standard Workflow

Include the Glue job in a Standard workflow:

```kotlin
val workflow = WorkflowFactory.createStandardWorkflow(
    scope = this,
    config = WorkflowConfiguration(
        workflowName = "CeapWorkflow",
        workflowType = WorkflowType.STANDARD,  // Required for Glue jobs
        steps = listOf(
            WorkflowStepType.Lambda(LambdaStep("ETLStage", "etlLambda")),
            WorkflowStepType.Glue(GlueStep("HeavyETLStage", heavyETLJob.jobName)),
            WorkflowStepType.Lambda(LambdaStep("FilterStage", "filterLambda")),
            WorkflowStepType.Lambda(LambdaStep("ScoreStage", "scoreLambda")),
            WorkflowStepType.Lambda(LambdaStep("StoreStage", "storeLambda"))
        ),
        lambdaFunctions = mapOf(
            "etlLambda" to etlLambda,
            "filterLambda" to filterLambda,
            "scoreLambda" to scoreLambda,
            "storeLambda" to storeLambda
        ),
        workflowBucket = workflowBucket,
        sourceQueue = queue,
        retryConfig = RetryConfiguration()
    )
)
```

## Customizing the Script

To create your own Glue job:

1. Copy `workflow_etl_template.py` to a new file
2. Modify the ETL transformation logic section (lines 120-180)
3. Keep the argument parsing, input reading, and output writing sections unchanged
4. Upload to S3 and reference in CDK

### Example Transformations

**Data Filtering:**
```python
# Filter records based on business rules
filtered_df = input_df.filter(col("score") > 50)
```

**Data Enrichment:**
```python
# Join with external data source
enriched_df = input_df.join(
    external_df,
    input_df["customer_id"] == external_df["id"],
    "left"
)
```

**Aggregations:**
```python
# Perform complex aggregations
aggregated_df = input_df.groupBy("category").agg(
    count("*").alias("count"),
    avg("score").alias("avg_score"),
    max("timestamp").alias("latest_timestamp")
)
```

## Configuration Options

### Worker Types

| Worker Type | vCPU | Memory | Use Case |
|-------------|------|--------|----------|
| G.1X | 4 | 16 GB | Standard workloads |
| G.2X | 8 | 32 GB | Memory-intensive workloads |
| G.4X | 16 | 64 GB | Very large datasets |
| G.8X | 32 | 128 GB | Extreme workloads |

### Number of Workers

- **Minimum**: 2 (required for distributed processing)
- **Recommended**: 2-10 for most workloads
- **Large datasets**: 10-50 workers
- **Cost consideration**: Each worker incurs cost, scale appropriately

### Timeout

- **Default**: 120 minutes (2 hours)
- **Maximum**: 2880 minutes (48 hours)
- **Recommendation**: Set based on expected processing time + 50% buffer

## Monitoring and Debugging

### CloudWatch Logs

Glue job logs are available in CloudWatch Logs:

```
/aws-glue/jobs/{job-name}
```

### Execution Tracking

The script logs execution context at start:

```
=== Glue Job Started ===
Job Name: ceap-heavy-etl-job
Execution ID: abc-123-def-456
Current Stage: HeavyETLStage
Previous Stage: ETLStage
Timestamp: 2024-01-15T10:30:00Z
```

### S3 Intermediate Outputs

Input and output data are stored in S3 for debugging:

```
s3://ceap-workflow-dev-123456789/executions/abc-123/
├── ETLStage/output.json          (input to Glue job)
├── HeavyETLStage/output.json     (output from Glue job)
└── FilterStage/output.json       (next stage)
```

## Best Practices

1. **Use Glue for Long-Running Jobs**: If processing takes >5 minutes, use Glue instead of Lambda
2. **Scale Workers Appropriately**: Start with 2 workers, scale up based on data volume
3. **Monitor Costs**: Glue jobs are billed per DPU-hour, monitor usage
4. **Test Locally**: Use Glue Docker images for local testing before deployment
5. **Optimize Spark**: Use appropriate partitioning, caching, and broadcast joins
6. **Handle Errors**: Add try-catch blocks around transformations for better error messages
7. **Log Metrics**: Log input/output record counts for monitoring

## Troubleshooting

### Job Fails with "Access Denied"

- Check IAM role has S3 permissions for workflow bucket
- Verify script location is accessible
- Ensure Glue service role is correctly configured

### Job Times Out

- Increase timeout in CDK configuration
- Scale up number of workers
- Optimize Spark transformations (reduce shuffles, use broadcast joins)

### Output Not Found by Next Stage

- Verify output path matches convention: `executions/{executionId}/{currentStage}/output.json`
- Check CloudWatch Logs for write errors
- Ensure output format is JSON (or update next stage to read Parquet)

### High Costs

- Reduce number of workers if possible
- Optimize job to complete faster
- Consider using Lambda for smaller datasets
- Use Spot instances for non-critical workloads (configure in CDK)

## References

- [AWS Glue Documentation](https://docs.aws.amazon.com/glue/)
- [PySpark API Reference](https://spark.apache.org/docs/latest/api/python/)
- [CEAP Infrastructure Enhancement Design](../docs/INFRASTRUCTURE-ENHANCEMENT.md)
- [Step Functions Integration Guide](../docs/STEP-FUNCTIONS-INTEGRATION.md)
