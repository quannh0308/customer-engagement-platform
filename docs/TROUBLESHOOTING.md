# CEAP Platform - Troubleshooting Guide

**Last Updated**: January 20, 2026
**Audience**: Developers and operators

---

## Table of Contents

1. [AWS Credentials Issues](#aws-credentials-issues)
2. [Deployment Issues](#deployment-issues)
3. [Lambda Function Issues](#lambda-function-issues)
4. [DynamoDB Issues](#dynamodb-issues)
5. [Build Issues](#build-issues)
6. [Testing Issues](#testing-issues)
7. [Performance Issues](#performance-issues)
8. [Cost Issues](#cost-issues)

---

## AWS Credentials Issues

### Problem: "Unable to locate credentials"

**Symptoms**:
```
Unable to locate credentials. You can configure credentials by running "aws configure".
```

**Solutions**:

1. **Configure AWS CLI**:
```bash
aws configure
# Enter your Access Key ID and Secret Access Key
```

2. **Check credentials file**:
```bash
cat ~/.aws/credentials
# Should show [default] profile with credentials
```

3. **Verify credentials work**:
```bash
aws sts get-caller-identity
# Should show your account info
```

### Problem: "Access Denied" or "UnauthorizedOperation"

**Symptoms**:
```
An error occurred (AccessDenied) when calling the CreateTable operation
```

**Solutions**:

1. **Check IAM permissions**:
   - Go to IAM Console
   - Click on your user
   - Verify `AdministratorAccess` policy is attached

2. **Verify you're using correct profile**:
```bash
aws sts get-caller-identity --profile ceap-dev
```

3. **Check if MFA is required**:
   - Some accounts require MFA tokens
   - Ask your AWS administrator

### Problem: "The security token included in the request is expired"

**Symptoms**:
```
ExpiredToken: The security token included in the request is expired
```

**Solutions**:

1. **Refresh temporary credentials** (if using SSO/temporary credentials)
2. **Re-run aws configure** with permanent credentials
3. **Check system clock** (must be accurate for AWS API calls)

---

## Deployment Issues

### Problem: "Stack already exists"

**Symptoms**:
```
ceap-platform-database-dev already exists
```

**Solutions**:

1. **Update existing stack**:
```bash
cdk deploy DatabaseStack-dev --context environment=dev
```

2. **Or destroy and redeploy**:
```bash
cdk destroy DatabaseStack-dev --context environment=dev
cdk deploy DatabaseStack-dev --context environment=dev
```

### Problem: "Bootstrap required"

**Symptoms**:
```
This stack uses assets, so the toolkit stack must be deployed to the environment
```

**Solution**:
```bash
./deploy-cdk.sh --bootstrap --environment dev --region us-east-1
```

### Problem: "Deployment failed" with CloudFormation errors

**Symptoms**:
```
CREATE_FAILED | AWS::Lambda::Function | EtlLambdaFunction
Resource handler returned message: "Error occurred..."
```

**Solutions**:

1. **Check CloudFormation console**:
   - Go to https://console.aws.amazon.com/cloudformation
   - Click on failed stack
   - Check "Events" tab for detailed error

2. **Common causes**:
   - Lambda JAR not found in S3
   - IAM permission issues
   - Resource limits exceeded
   - Invalid configuration

3. **Fix and retry**:
```bash
# Fix the issue, then
cdk deploy --context environment=dev
```

### Problem: "Rate exceeded" or "Throttling"

**Symptoms**:
```
Rate exceeded (Service: AmazonDynamoDB; Status Code: 400; Error Code: ThrottlingException)
```

**Solutions**:

1. **Wait and retry** (AWS has rate limits)
2. **Deploy stacks one at a time**:
```bash
./deploy-cdk.sh --environment dev --stack Database
./deploy-cdk.sh --environment dev --stack EtlWorkflow
```

---

## Lambda Function Issues

### Problem: Lambda function timeout

**Symptoms**:
```
Task timed out after 30.00 seconds
```

**Solutions**:

1. **Check CloudWatch Logs**:
```bash
aws logs tail /aws/lambda/ceap-platform-etl-dev --since 1h
```

2. **Increase timeout** (in CDK stack):
```kotlin
timeout = Duration.seconds(300) // Increase from 30 to 300
```

3. **Optimize code**:
   - Reduce batch sizes
   - Add pagination
   - Optimize database queries

### Problem: Lambda out of memory

**Symptoms**:
```
Runtime exited with error: signal: killed
Runtime.ExitError
```

**Solutions**:

1. **Increase memory** (in CDK stack):
```kotlin
memorySize = 1024 // Increase from 512 to 1024 MB
```

2. **Optimize memory usage**:
   - Process data in smaller batches
   - Release objects after use
   - Avoid loading large datasets into memory

### Problem: Lambda cold start latency

**Symptoms**:
- First invocation takes 5-10 seconds
- Subsequent invocations are fast

**Solutions**:

1. **Use provisioned concurrency** (costs more):
```kotlin
reservedConcurrentExecutions = 1
```

2. **Optimize initialization**:
   - Move initialization outside handler
   - Use lazy initialization
   - Reduce dependencies

3. **Accept cold starts** (for dev environment)

---

## DynamoDB Issues

### Problem: "Table does not exist"

**Symptoms**:
```
ResourceNotFoundException: Requested resource not found: Table: ceap-candidates-dev not found
```

**Solutions**:

1. **Verify table exists**:
```bash
aws dynamodb describe-table --table-name ceap-candidates-dev
```

2. **Deploy database stack**:
```bash
cdk deploy DatabaseStack-dev --context environment=dev
```

3. **Check region**:
```bash
# Make sure you're in the correct region
aws configure get region
```

### Problem: "ConditionalCheckFailedException"

**Symptoms**:
```
The conditional request failed (ConditionalCheckFailedException)
```

**Cause**: Optimistic locking conflict (version mismatch)

**Solutions**:

1. **Retry the operation** (expected behavior for concurrent updates)
2. **Fetch latest version** before updating
3. **Implement retry logic** with exponential backoff

### Problem: "ProvisionedThroughputExceededException"

**Symptoms**:
```
The level of configured provisioned throughput for the table was exceeded
```

**Solutions**:

1. **Check if using on-demand mode**:
```bash
aws dynamodb describe-table --table-name ceap-candidates-dev \
  --query 'Table.BillingModeSummary.BillingMode'
# Should show: PAY_PER_REQUEST
```

2. **If using provisioned mode**, increase capacity or switch to on-demand

---

## Build Issues

### Problem: "Could not resolve dependency"

**Symptoms**:
```
Could not resolve com.amazonaws:aws-lambda-java-core:1.2.3
```

**Solutions**:

1. **Clean and rebuild**:
```bash
./gradlew clean build
```

2. **Check internet connection** (Gradle needs to download dependencies)

3. **Clear Gradle cache**:
```bash
rm -rf ~/.gradle/caches
./gradlew build
```

### Problem: "Compilation failed"

**Symptoms**:
```
Compilation error: Unresolved reference: ...
```

**Solutions**:

1. **Check Kotlin version**:
```bash
./gradlew --version
# Should show Kotlin 1.9.21
```

2. **Sync dependencies**:
```bash
./gradlew build --refresh-dependencies
```

3. **Check for syntax errors** in recent changes

---

## Testing Issues

### Problem: Tests fail with "Connection refused"

**Symptoms**:
```
java.net.ConnectException: Connection refused
```

**Cause**: Tests trying to connect to DynamoDB but no local instance running

**Solutions**:

1. **Use DynamoDB Local** (for integration tests):
```bash
# Install DynamoDB Local
docker run -p 8000:8000 amazon/dynamodb-local

# Run tests
./gradlew test
```

2. **Or mock DynamoDB** (for unit tests)

### Problem: Property-based tests fail intermittently

**Symptoms**:
```
Property test failed after 47 tries
```

**Solutions**:

1. **Check the failing example** in test output
2. **Reproduce with specific seed**:
```kotlin
@Property(seed = "1234567890")
fun testProperty() { ... }
```

3. **Fix the bug** or **adjust the property**

---

## Performance Issues

### Problem: API latency too high

**Symptoms**:
- Serving API takes >1 second to respond

**Solutions**:

1. **Check DynamoDB query patterns**:
   - Use GSIs for efficient queries
   - Avoid scans
   - Use batch operations

2. **Enable caching**:
   - Score caching (already implemented)
   - API Gateway caching
   - Application-level caching

3. **Optimize Lambda**:
   - Increase memory (improves CPU)
   - Use connection pooling
   - Minimize cold starts

### Problem: Workflow takes too long

**Symptoms**:
- Batch workflow takes hours instead of minutes

**Solutions**:

1. **Increase parallelism** in Step Functions
2. **Optimize Lambda functions**:
   - Increase memory
   - Reduce batch sizes
   - Add parallel processing

3. **Check for bottlenecks**:
```bash
# View Step Functions execution
aws stepfunctions describe-execution --execution-arn <arn>
```

---

## Cost Issues

### Problem: Unexpected high costs

**Symptoms**:
- AWS bill higher than expected

**Solutions**:

1. **Check Cost Explorer**:
   - Go to AWS Console → Billing → Cost Explorer
   - Group by Service to see what's expensive

2. **Common causes**:
   - DynamoDB scans (use queries instead)
   - Lambda running continuously (check for infinite loops)
   - CloudWatch Logs not expiring (set retention)
   - Unused resources (delete dev environments when not needed)

3. **Set up billing alerts**:
```bash
# Create billing alarm
aws cloudwatch put-metric-alarm \
  --alarm-name ceap-billing-alert \
  --alarm-description "Alert when costs exceed $50" \
  --metric-name EstimatedCharges \
  --namespace AWS/Billing \
  --statistic Maximum \
  --period 21600 \
  --evaluation-periods 1 \
  --threshold 50 \
  --comparison-operator GreaterThanThreshold
```

---

## Emergency Procedures

### Rollback Deployment

```bash
# Destroy current deployment
cdk destroy --all --context environment=dev

# Redeploy previous version
git checkout <previous-commit>
./deploy-cdk.sh --environment dev
```

### Delete All Resources

```bash
# Destroy all stacks
cdk destroy --all --context environment=dev

# Verify deletion
aws cloudformation list-stacks --stack-status-filter DELETE_COMPLETE
```

### Stop All Processing

```bash
# Disable all Lambda functions
for func in etl filter score store serve; do
  aws lambda update-function-configuration \
    --function-name ceap-platform-$func-dev \
    --environment Variables={ENABLED=false}
done

# Disable Step Functions
aws stepfunctions stop-execution --execution-arn <arn>
```

---

## Getting Support

### Internal Support
- Check documentation in `docs/` directory
- Review architecture in `docs/VISUAL-ARCHITECTURE.md`
- Check completed tasks in `.kiro/specs/customer-engagement-platform/completed-tasks.md`

### AWS Support
- AWS Support Center: https://console.aws.amazon.com/support
- AWS Forums: https://forums.aws.amazon.com
- AWS Documentation: https://docs.aws.amazon.com

### Community
- Stack Overflow: Tag questions with `aws-cdk`, `aws-lambda`, `dynamodb`
- GitHub Issues: (if open source)

---

## Diagnostic Commands

### Check Everything

```bash
# AWS account and region
aws sts get-caller-identity
aws configure get region

# List all resources
aws cloudformation list-stacks --stack-status-filter CREATE_COMPLETE UPDATE_COMPLETE
aws dynamodb list-tables
aws lambda list-functions
aws stepfunctions list-state-machines

# Check costs
aws ce get-cost-and-usage \
  --time-period Start=2026-01-01,End=2026-01-31 \
  --granularity MONTHLY \
  --metrics BlendedCost
```

### Health Check Script

```bash
#!/bin/bash
echo "=== CEAP Platform Health Check ==="
echo ""
echo "AWS Account:"
aws sts get-caller-identity --query Account --output text
echo ""
echo "DynamoDB Tables:"
aws dynamodb list-tables --query 'TableNames[?starts_with(@, `ceap`)]' --output table
echo ""
echo "Lambda Functions:"
aws lambda list-functions --query 'Functions[?starts_with(FunctionName, `ceap-platform`)].FunctionName' --output table
echo ""
echo "Recent Errors:"
aws logs filter-log-events \
  --log-group-name /aws/lambda/ceap-platform-serve-dev \
  --filter-pattern "ERROR" \
  --max-items 5
```

---

**Troubleshooting Guide Version**: 1.0
**Last Updated**: January 20, 2026

