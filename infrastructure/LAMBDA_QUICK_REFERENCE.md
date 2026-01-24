# Lambda Functions Quick Reference

> **Platform Rebranding Note**: This platform was formerly known as the "General Solicitation Platform". We've rebranded to "Customer Engagement & Action Platform (CEAP)" to better reflect its capabilities beyond customer engagement. Package names now use `com.ceap.*` following the CEAP branding.

## Function Overview

| Function | Memory | Timeout | Purpose |
|----------|--------|---------|---------|
| ETL | 1024 MB | 300s | Extract & transform data |
| Filter | 512 MB | 60s | Apply eligibility filters |
| Score | 1024 MB | 120s | Execute scoring models |
| Store | 512 MB | 60s | Batch write to DynamoDB |
| Serve | 512 MB | 30s | Low-latency API |

## Handler Classes

```
ETL:    com.ceap.workflow.ETLHandler::handleRequest
Filter: com.ceap.workflow.FilterHandler::handleRequest
Score:  com.ceap.workflow.ScoreHandler::handleRequest
Store:  com.ceap.workflow.StoreHandler::handleRequest
Serve:  com.ceap.serving.ServeHandler::handleRequest
```

## Environment Variables

### ETL Lambda
- `DATA_SOURCE_CONFIG` - S3 bucket for data sources
- `BATCH_SIZE` - Records per batch (default: 1000)
- `CANDIDATES_TABLE` - DynamoDB table name
- `PROGRAM_CONFIG_TABLE` - Program config table

### Filter Lambda
- `FILTER_CONFIG` - S3 bucket for filter configs
- `PROGRAM_CONFIG_TABLE` - Program config table

### Score Lambda
- `MODEL_ENDPOINTS` - SageMaker endpoints config
- `FEATURE_STORE_CONFIG` - Feature store config
- `SCORE_CACHE_TABLE` - Score cache table
- `CACHE_TTL_HOURS` - Cache TTL (default: 24)

### Store Lambda
- `BATCH_SIZE` - DynamoDB batch size (default: 25)
- `CANDIDATES_TABLE` - Candidates table

### Serve Lambda
- `CACHE_CONFIG` - Caching configuration
- `CANDIDATES_TABLE` - Candidates table
- `MAX_RESULTS_PER_QUERY` - Max results (default: 100)

### Common Variables (All Functions)
- `ENVIRONMENT` - Deployment environment
- `LOG_LEVEL` - Logging level (INFO/DEBUG/WARN/ERROR)

## IAM Permissions Summary

### ETL Lambda
- DynamoDB: Read/Write on Candidates
- Athena: Query execution
- S3: Read data sources, Write Athena results
- Glue: Data catalog access

### Filter Lambda
- DynamoDB: Read ProgramConfig
- API Gateway: External service calls

### Score Lambda
- DynamoDB: Read/Write ScoreCache
- SageMaker: Invoke endpoints, Feature store access

### Store Lambda
- DynamoDB: Write Candidates (batch)

### Serve Lambda
- DynamoDB: Read Candidates (with indexes)
- API Gateway: Integration

## Quick Commands

### Validate Template
```bash
cd infrastructure
./validate-lambda-template.sh
```

### Deploy Stack
```bash
export ENVIRONMENT=dev
export PROJECT_NAME=ceap-platform
export AWS_REGION=us-east-1
cd infrastructure
./deploy-lambda.sh
```

### Update Function Code
```bash
# Build JAR using Gradle
./gradlew :ceap-workflow-etl:shadowJar

# Upload to S3
aws s3 cp ceap-workflow-etl/build/libs/ceap-workflow-etl-1.0.0-SNAPSHOT.jar \
  s3://${PROJECT_NAME}-lambda-artifacts-${ENVIRONMENT}/etl-lambda.jar

# Update function
aws lambda update-function-code \
  --function-name ${PROJECT_NAME}-etl-${ENVIRONMENT} \
  --s3-bucket ${PROJECT_NAME}-lambda-artifacts-${ENVIRONMENT} \
  --s3-key etl-lambda.jar
```

### View Logs
```bash
# Tail logs
aws logs tail /aws/lambda/${PROJECT_NAME}-etl-${ENVIRONMENT} --follow

# Get recent logs
aws logs tail /aws/lambda/${PROJECT_NAME}-etl-${ENVIRONMENT} \
  --since 1h --format short
```

### Invoke Function (Test)
```bash
aws lambda invoke \
  --function-name ${PROJECT_NAME}-etl-${ENVIRONMENT} \
  --payload '{"test": "data"}' \
  response.json
```

## CloudWatch Log Groups

```
/aws/lambda/ceap-platform-etl-dev
/aws/lambda/ceap-platform-filter-dev
/aws/lambda/ceap-platform-score-dev
/aws/lambda/ceap-platform-store-dev
/aws/lambda/ceap-platform-serve-dev
```

**Note**: Log group names use `ceap-platform` or `customer-engagement-platform` as the project name.

## Stack Outputs

After deployment, the stack exports:
- Lambda function ARNs (for Step Functions)
- IAM role ARNs (for reference)

Access outputs:
```bash
aws cloudformation describe-stacks \
  --stack-name ceap-platform-lambda-functions \
  --query 'Stacks[0].Outputs'
```

## Monitoring Metrics

Key metrics to monitor:
- **Invocations**: Total function calls
- **Duration**: Execution time
- **Errors**: Failed invocations
- **Throttles**: Rate limit hits
- **ConcurrentExecutions**: Parallel executions

## Cost Optimization Tips

1. **Right-size memory**: Monitor actual usage, adjust if needed
2. **Optimize timeout**: Set just above P99 duration
3. **Use reserved concurrency**: For predictable workloads
4. **Enable X-Ray selectively**: Only for troubleshooting
5. **Batch operations**: Reduce invocation count

## Troubleshooting

### Function Timeout
- Increase timeout in template
- Optimize code performance
- Check external service latency

### Out of Memory
- Increase memory allocation
- Optimize data structures
- Process in smaller batches

### Permission Denied
- Check IAM role policies
- Verify resource ARNs
- Check resource-based policies

### Cold Start Issues
- Use provisioned concurrency
- Optimize initialization code
- Consider SnapStart (Java 11+)

## Related Files

- `lambda-functions.yaml` - CloudFormation template
- `deploy-lambda.sh` - Deployment script
- `validate-lambda-template.sh` - Validation script
- `LAMBDA_CONFIGURATION.md` - Full documentation
