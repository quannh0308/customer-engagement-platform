# Workflow Operations Runbook

## Overview

This runbook provides operational procedures for monitoring, troubleshooting, and maintaining the CEAP workflow orchestration infrastructure. It covers failure detection, recovery procedures, DLQ processing, S3 lifecycle management, and CloudWatch monitoring.

## Table of Contents

1. [Failure Detection and Recovery](#failure-detection-and-recovery)
2. [Dead Letter Queue (DLQ) Processing](#dead-letter-queue-dlq-processing)
3. [S3 Lifecycle Policy Management](#s3-lifecycle-policy-management)
4. [CloudWatch Monitoring and Alerting](#cloudwatch-monitoring-and-alerting)
5. [Common Operational Tasks](#common-operational-tasks)
6. [Emergency Procedures](#emergency-procedures)

## Failure Detection and Recovery

### Express Workflow Failures

#### Detection

Express workflows fail synchronously, returning errors to the EventBridge Pipe, which makes the SQS message visible again for retry.

**Indicators:**
- SQS message receive count increases
- CloudWatch metric: `ExecutionsFailed` increases
- EventBridge Pipe shows failed invocations

#### Investigation Steps

1. **Check Step Functions Execution History**
   ```bash
   aws stepfunctions describe-execution \
     --execution-arn arn:aws:states:us-east-1:123456789:execution:WorkflowName:execution-id
   ```

2. **Identify Failed Stage**
   Look for the last successful state and the first failed state in the execution history.

3. **Check CloudWatch Logs**
   ```bash
   aws logs filter-log-events \
     --log-group-name /aws/stepfunction/WorkflowName \
     --filter-pattern "execution-id" \
     --start-time $(date -d '1 hour ago' +%s)000
   ```

4. **Download S3 Intermediate Outputs**
   ```bash
   aws s3 cp s3://workflow-bucket/executions/execution-id/ ./debug/ --recursive
   ```

5. **Analyze Error**
   - Check error type (transient vs permanent)
   - Review error message and stack trace
   - Inspect input data that caused failure

#### Recovery Procedures

**For Transient Errors (S3 throttling, network timeout):**
1. Wait for automatic retry (SQS visibility timeout)
2. Monitor retry attempts
3. If retries succeed, no action needed
4. If retries fail, escalate to permanent error handling

**For Permanent Errors (access denied, invalid data):**
1. Fix root cause:
   - IAM permissions: Update IAM policy
   - Invalid data: Fix data quality at source
   - Code bug: Deploy Lambda fix
2. Manually reprocess failed message:
   ```bash
   # Get message from DLQ
   aws sqs receive-message --queue-url https://sqs.us-east-1.amazonaws.com/123456789/workflow-dlq
   
   # Fix issue, then send to main queue
   aws sqs send-message \
     --queue-url https://sqs.us-east-1.amazonaws.com/123456789/workflow-queue \
     --message-body '{"fixed": "data"}'
   ```

### Standard Workflow Failures

#### Detection

Standard workflows fail asynchronously. Failures are detected via EventBridge rules monitoring execution status changes.

**Indicators:**
- EventBridge rule triggers SNS notification
- CloudWatch metric: `ExecutionsFailed` increases
- Step Functions console shows FAILED executions

#### Investigation Steps

1. **List Recent Failed Executions**
   ```bash
   aws stepfunctions list-executions \
     --state-machine-arn arn:aws:states:us-east-1:123456789:stateMachine:WorkflowName \
     --status-filter FAILED \
     --max-results 10
   ```

2. **Get Execution Details**
   ```bash
   aws stepfunctions describe-execution \
     --execution-arn arn:aws:states:us-east-1:123456789:execution:WorkflowName:execution-id
   ```

3. **Check Execution History**
   ```bash
   aws stepfunctions get-execution-history \
     --execution-arn arn:aws:states:us-east-1:123456789:execution:WorkflowName:execution-id \
     --max-results 100
   ```

4. **For Glue Job Failures**
   ```bash
   # Get Glue job run details
   aws glue get-job-run \
     --job-name heavy-etl-job \
     --run-id jr_abc123
   
   # Check Glue job logs
   aws logs tail /aws-glue/jobs/output --follow
   ```

#### Recovery Procedures

**For Lambda Failures:**
Same as Express workflow recovery procedures above.

**For Glue Job Failures:**

1. **Check Glue Job Logs**
   - Navigate to AWS Glue Console → Jobs → Job Runs
   - Click on failed run → View logs
   - Check error logs and output logs

2. **Common Glue Issues:**
   - **Out of Memory**: Increase DPUs or optimize Spark code
   - **S3 Access Denied**: Update Glue job IAM role
   - **Script Error**: Fix PySpark script and redeploy
   - **Data Schema Mismatch**: Update schema or fix input data

3. **Restart Execution**
   ```bash
   # Option A: Restart entire workflow
   aws stepfunctions start-execution \
     --state-machine-arn arn:aws:states:us-east-1:123456789:stateMachine:WorkflowName \
     --input '{"original": "input"}'
   
   # Option B: Resume from failed stage (manual)
   # 1. Fix the issue
   # 2. Manually run Glue job with correct S3 paths
   # 3. Continue workflow from next stage
   ```

### Failure Patterns and Solutions

| Error Pattern | Root Cause | Solution |
|---------------|------------|----------|
| `AccessDeniedException` | IAM permissions missing | Update IAM policy with S3/Lambda/Glue permissions |
| `NoSuchKey` | Previous stage failed | Check previous stage execution, fix and rerun |
| `ThrottlingException` | S3 rate limit exceeded | Implement exponential backoff, reduce request rate |
| `TimeoutException` | Lambda/Glue timeout | Increase timeout or optimize processing logic |
| `OutOfMemoryError` | Insufficient memory | Increase Lambda memory or Glue DPUs |
| `InvalidJSON` | Data corruption | Fix data quality at source, validate input |

## Dead Letter Queue (DLQ) Processing

### Overview

Messages that fail after 3 receive attempts are automatically moved to the Dead Letter Queue for manual investigation and reprocessing.

### Monitoring DLQ

**Check DLQ Message Count:**
```bash
aws sqs get-queue-attributes \
  --queue-url https://sqs.us-east-1.amazonaws.com/123456789/workflow-dlq \
  --attribute-names ApproximateNumberOfMessages
```

**Set Up CloudWatch Alarm:**
```bash
aws cloudwatch put-metric-alarm \
  --alarm-name workflow-dlq-messages \
  --alarm-description "Alert when DLQ has messages" \
  --metric-name ApproximateNumberOfMessagesVisible \
  --namespace AWS/SQS \
  --statistic Average \
  --period 300 \
  --threshold 1 \
  --comparison-operator GreaterThanThreshold \
  --dimensions Name=QueueName,Value=workflow-dlq \
  --evaluation-periods 1 \
  --alarm-actions arn:aws:sns:us-east-1:123456789:ops-alerts
```

### Processing DLQ Messages

#### Step 1: Retrieve Messages

```bash
# Receive up to 10 messages
aws sqs receive-message \
  --queue-url https://sqs.us-east-1.amazonaws.com/123456789/workflow-dlq \
  --max-number-of-messages 10 \
  --visibility-timeout 300 \
  --attribute-names All \
  --message-attribute-names All > dlq-messages.json
```

#### Step 2: Analyze Failure Patterns

```bash
# Extract message bodies
jq -r '.Messages[].Body' dlq-messages.json > message-bodies.txt

# Check for common patterns
grep -E "error|exception|failed" message-bodies.txt
```

#### Step 3: Categorize Messages

**Category A: Data Quality Issues**
- Invalid JSON format
- Missing required fields
- Out-of-range values

**Action**: Fix data at source, discard messages

**Category B: Transient Failures (Now Resolved)**
- S3 throttling (now resolved)
- Lambda cold start timeout (now resolved)
- Network issues (now resolved)

**Action**: Reprocess messages

**Category C: Code Bugs (Now Fixed)**
- Lambda code bug (now fixed and deployed)
- Glue script error (now fixed)

**Action**: Reprocess messages

#### Step 4: Reprocess Messages

**For Valid Messages:**
```bash
# Send back to main queue
while read -r message; do
  aws sqs send-message \
    --queue-url https://sqs.us-east-1.amazonaws.com/123456789/workflow-queue \
    --message-body "$message"
done < valid-messages.txt
```

**For Invalid Messages:**
```bash
# Archive to S3 for audit
aws s3 cp invalid-messages.txt s3://workflow-audit/dlq/$(date +%Y-%m-%d)/

# Delete from DLQ
while read -r receipt_handle; do
  aws sqs delete-message \
    --queue-url https://sqs.us-east-1.amazonaws.com/123456789/workflow-dlq \
    --receipt-handle "$receipt_handle"
done < receipt-handles.txt
```

### DLQ Processing Checklist

- [ ] Retrieve messages from DLQ
- [ ] Analyze failure patterns
- [ ] Categorize messages (data quality, transient, code bug)
- [ ] Fix root cause if applicable
- [ ] Reprocess valid messages
- [ ] Archive or delete invalid messages
- [ ] Update monitoring to prevent recurrence
- [ ] Document lessons learned

## S3 Lifecycle Policy Management

### Overview

The S3 workflow bucket implements lifecycle policies to automatically delete execution data after 7 days, preventing unbounded storage growth.

### Verify Lifecycle Policy

```bash
aws s3api get-bucket-lifecycle-configuration \
  --bucket ceap-workflow-dev-123456789
```

**Expected Output:**
```json
{
  "Rules": [
    {
      "Id": "DeleteOldExecutions",
      "Status": "Enabled",
      "Prefix": "executions/",
      "Expiration": {
        "Days": 7
      }
    }
  ]
}
```

### Monitor Lifecycle Policy

**Check Objects Scheduled for Deletion:**
```bash
# List objects older than 7 days
aws s3 ls s3://ceap-workflow-dev-123456789/executions/ --recursive | \
  awk -v date="$(date -d '7 days ago' +%Y-%m-%d)" '$1 < date {print}'
```

**Verify Deletion is Occurring:**
```bash
# Check bucket size over time
aws cloudwatch get-metric-statistics \
  --namespace AWS/S3 \
  --metric-name BucketSizeBytes \
  --dimensions Name=BucketName,Value=ceap-workflow-dev-123456789 Name=StorageType,Value=StandardStorage \
  --start-time $(date -d '30 days ago' --iso-8601) \
  --end-time $(date --iso-8601) \
  --period 86400 \
  --statistics Average
```

### Adjust Lifecycle Policy

**Increase Retention (e.g., 14 days):**
```bash
aws s3api put-bucket-lifecycle-configuration \
  --bucket ceap-workflow-dev-123456789 \
  --lifecycle-configuration '{
    "Rules": [
      {
        "Id": "DeleteOldExecutions",
        "Status": "Enabled",
        "Prefix": "executions/",
        "Expiration": {
          "Days": 14
        }
      }
    ]
  }'
```

**Disable Lifecycle Policy (Temporarily):**
```bash
aws s3api put-bucket-lifecycle-configuration \
  --bucket ceap-workflow-dev-123456789 \
  --lifecycle-configuration '{
    "Rules": [
      {
        "Id": "DeleteOldExecutions",
        "Status": "Disabled",
        "Prefix": "executions/",
        "Expiration": {
          "Days": 7
        }
      }
    ]
  }'
```

### Archive Important Executions

**Before Automatic Deletion:**
```bash
# Copy execution data to archive bucket
aws s3 cp s3://ceap-workflow-dev-123456789/executions/important-execution-id/ \
  s3://ceap-workflow-archive/executions/important-execution-id/ \
  --recursive

# Tag for long-term retention
aws s3api put-object-tagging \
  --bucket ceap-workflow-archive \
  --key executions/important-execution-id/ETLStage/output.json \
  --tagging 'TagSet=[{Key=Retention,Value=LongTerm}]'
```

## CloudWatch Monitoring and Alerting

### Key Metrics to Monitor

#### Step Functions Metrics

| Metric | Description | Threshold | Action |
|--------|-------------|-----------|--------|
| `ExecutionsFailed` | Failed executions | > 5 in 5 min | Investigate failures |
| `ExecutionsTimedOut` | Timed out executions | > 0 | Increase timeout or optimize |
| `ExecutionTime` | Execution duration | > 4 min (Express) | Consider Standard workflow |
| `ExecutionsStarted` | Started executions | < expected rate | Check upstream triggers |

#### Lambda Metrics

| Metric | Description | Threshold | Action |
|--------|-------------|-----------|--------|
| `Errors` | Lambda errors | > 5% error rate | Check logs, fix code |
| `Throttles` | Lambda throttles | > 0 | Increase concurrency |
| `Duration` | Execution time | > 80% of timeout | Optimize or increase timeout |
| `ConcurrentExecutions` | Concurrent invocations | > 80% of limit | Request limit increase |

#### SQS Metrics

| Metric | Description | Threshold | Action |
|--------|-------------|-----------|--------|
| `ApproximateAgeOfOldestMessage` | Message age | > 1 hour | Check processing rate |
| `ApproximateNumberOfMessagesVisible` | Queue depth | > 1000 | Scale processing |
| `NumberOfMessagesDeleted` | Processed messages | < expected rate | Investigate slowdown |

#### S3 Metrics

| Metric | Description | Threshold | Action |
|--------|-------------|-----------|--------|
| `BucketSizeBytes` | Bucket size | > 100 GB | Check lifecycle policy |
| `NumberOfObjects` | Object count | > 100,000 | Check lifecycle policy |
| `4xxErrors` | Client errors | > 1% | Check IAM permissions |
| `5xxErrors` | Server errors | > 0 | Contact AWS support |

### Setting Up CloudWatch Alarms

#### High Failure Rate Alarm

```bash
aws cloudwatch put-metric-alarm \
  --alarm-name workflow-high-failure-rate \
  --alarm-description "Alert when workflow failure rate exceeds 10%" \
  --metric-name ExecutionsFailed \
  --namespace AWS/States \
  --statistic Sum \
  --period 300 \
  --threshold 5 \
  --comparison-operator GreaterThanThreshold \
  --dimensions Name=StateMachineArn,Value=arn:aws:states:us-east-1:123456789:stateMachine:WorkflowName \
  --evaluation-periods 1 \
  --alarm-actions arn:aws:sns:us-east-1:123456789:ops-alerts
```

#### Long Execution Time Alarm

```bash
aws cloudwatch put-metric-alarm \
  --alarm-name workflow-long-execution \
  --alarm-description "Alert when execution time exceeds 4 minutes" \
  --metric-name ExecutionTime \
  --namespace AWS/States \
  --statistic Average \
  --period 60 \
  --threshold 240000 \
  --comparison-operator GreaterThanThreshold \
  --dimensions Name=StateMachineArn,Value=arn:aws:states:us-east-1:123456789:stateMachine:WorkflowName \
  --evaluation-periods 1 \
  --alarm-actions arn:aws:sns:us-east-1:123456789:ops-alerts
```

#### DLQ Messages Alarm

```bash
aws cloudwatch put-metric-alarm \
  --alarm-name workflow-dlq-messages \
  --alarm-description "Alert when DLQ has messages" \
  --metric-name ApproximateNumberOfMessagesVisible \
  --namespace AWS/SQS \
  --statistic Average \
  --period 300 \
  --threshold 1 \
  --comparison-operator GreaterThanThreshold \
  --dimensions Name=QueueName,Value=workflow-dlq \
  --evaluation-periods 1 \
  --alarm-actions arn:aws:sns:us-east-1:123456789:ops-alerts
```

### CloudWatch Dashboards

**Create Workflow Dashboard:**
```bash
aws cloudwatch put-dashboard \
  --dashboard-name WorkflowMonitoring \
  --dashboard-body file://workflow-dashboard.json
```

**Dashboard Configuration (workflow-dashboard.json):**
```json
{
  "widgets": [
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/States", "ExecutionsStarted", {"stat": "Sum"}],
          [".", "ExecutionsSucceeded", {"stat": "Sum"}],
          [".", "ExecutionsFailed", {"stat": "Sum"}]
        ],
        "period": 300,
        "stat": "Sum",
        "region": "us-east-1",
        "title": "Workflow Executions"
      }
    },
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/States", "ExecutionTime", {"stat": "Average"}]
        ],
        "period": 300,
        "stat": "Average",
        "region": "us-east-1",
        "title": "Execution Duration"
      }
    },
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/SQS", "ApproximateNumberOfMessagesVisible", {"dimensions": {"QueueName": "workflow-queue"}}],
          ["...", {"dimensions": {"QueueName": "workflow-dlq"}}]
        ],
        "period": 300,
        "stat": "Average",
        "region": "us-east-1",
        "title": "Queue Depth"
      }
    },
    {
      "type": "log",
      "properties": {
        "query": "SOURCE '/aws/stepfunction/WorkflowName'\n| fields @timestamp, @message\n| filter @message like /ERROR/\n| sort @timestamp desc\n| limit 20",
        "region": "us-east-1",
        "title": "Recent Errors"
      }
    }
  ]
}
```

### Log Insights Queries

**Find Failed Executions:**
```
fields @timestamp, executionId, stage, errorMessage
| filter status = "FAILED"
| sort @timestamp desc
| limit 50
```

**Analyze Execution Times by Stage:**
```
fields stage, duration
| stats avg(duration), max(duration), min(duration) by stage
| sort avg(duration) desc
```

**Find S3 Access Errors:**
```
fields @timestamp, @message
| filter @message like /AccessDenied|NoSuchKey/
| sort @timestamp desc
| limit 20
```

## Common Operational Tasks

### Restart Failed Workflow

```bash
# Get original input from failed execution
aws stepfunctions describe-execution \
  --execution-arn arn:aws:states:us-east-1:123456789:execution:WorkflowName:failed-execution-id \
  --query 'input' --output text > original-input.json

# Start new execution with same input
aws stepfunctions start-execution \
  --state-machine-arn arn:aws:states:us-east-1:123456789:stateMachine:WorkflowName \
  --input file://original-input.json
```

### Pause Workflow Processing

```bash
# Disable EventBridge Pipe
aws pipes update-pipe \
  --name workflow-event-pipe \
  --desired-state STOPPED
```

### Resume Workflow Processing

```bash
# Enable EventBridge Pipe
aws pipes update-pipe \
  --name workflow-event-pipe \
  --desired-state RUNNING
```

### Drain Queue Before Maintenance

```bash
# Stop new messages
aws pipes update-pipe --name workflow-event-pipe --desired-state STOPPED

# Wait for queue to drain
while true; do
  count=$(aws sqs get-queue-attributes \
    --queue-url https://sqs.us-east-1.amazonaws.com/123456789/workflow-queue \
    --attribute-names ApproximateNumberOfMessages \
    --query 'Attributes.ApproximateNumberOfMessages' --output text)
  
  if [ "$count" -eq 0 ]; then
    echo "Queue drained"
    break
  fi
  
  echo "Waiting for queue to drain... ($count messages remaining)"
  sleep 30
done

# Perform maintenance
# ...

# Resume processing
aws pipes update-pipe --name workflow-event-pipe --desired-state RUNNING
```

### Scale Lambda Concurrency

```bash
# Increase reserved concurrency
aws lambda put-function-concurrency \
  --function-name CeapServingAPI-dev-FilterLambdaFunction \
  --reserved-concurrent-executions 100

# Remove concurrency limit
aws lambda delete-function-concurrency \
  --function-name CeapServingAPI-dev-FilterLambdaFunction
```

## Emergency Procedures

### Complete System Outage

**Symptoms:**
- All workflows failing
- High error rates across all stages
- CloudWatch alarms firing

**Response:**

1. **Stop Processing**
   ```bash
   aws pipes update-pipe --name workflow-event-pipe --desired-state STOPPED
   ```

2. **Assess Impact**
   - Check AWS Service Health Dashboard
   - Review CloudWatch metrics and logs
   - Identify affected components

3. **Engage Support**
   - Open AWS Support case (if AWS issue)
   - Escalate to engineering team (if code issue)
   - Notify stakeholders

4. **Implement Workaround**
   - Route traffic to backup system (if available)
   - Process critical messages manually
   - Document workaround steps

5. **Restore Service**
   - Fix root cause
   - Test with single message
   - Gradually resume processing
   - Monitor closely

### Data Corruption Detected

**Symptoms:**
- Invalid data in S3 outputs
- Downstream systems reporting errors
- Data validation failures

**Response:**

1. **Stop Processing**
   ```bash
   aws pipes update-pipe --name workflow-event-pipe --desired-state STOPPED
   ```

2. **Identify Scope**
   - Find first corrupted execution
   - Determine affected time range
   - List all affected executions

3. **Quarantine Data**
   ```bash
   # Move corrupted data to quarantine
   aws s3 mv s3://workflow-bucket/executions/corrupted-execution-id/ \
     s3://workflow-quarantine/executions/corrupted-execution-id/ \
     --recursive
   ```

4. **Fix Root Cause**
   - Identify corruption source (code bug, data quality)
   - Deploy fix
   - Test thoroughly

5. **Reprocess Data**
   - Retrieve original messages from DLQ or archive
   - Reprocess with fixed code
   - Validate outputs

6. **Resume Processing**
   ```bash
   aws pipes update-pipe --name workflow-event-pipe --desired-state RUNNING
   ```

### Runaway Costs

**Symptoms:**
- AWS bill significantly higher than expected
- High Glue DPU usage
- Excessive S3 storage

**Response:**

1. **Identify Cost Driver**
   ```bash
   # Check Glue job runs
   aws glue get-job-runs --job-name heavy-etl-job --max-results 100
   
   # Check S3 storage
   aws s3 ls s3://workflow-bucket/executions/ --recursive --summarize
   
   # Check Lambda invocations
   aws cloudwatch get-metric-statistics \
     --namespace AWS/Lambda \
     --metric-name Invocations \
     --start-time $(date -d '7 days ago' --iso-8601) \
     --end-time $(date --iso-8601) \
     --period 86400 \
     --statistics Sum
   ```

2. **Immediate Actions**
   - Stop non-critical workflows
   - Reduce Glue DPUs
   - Enable S3 lifecycle policy
   - Set Lambda reserved concurrency limits

3. **Long-term Optimization**
   - Optimize Glue scripts
   - Right-size Lambda memory
   - Implement cost monitoring alarms
   - Review workflow design for efficiency

## Contact Information

**On-Call Engineer**: [Pager Duty / Phone Number]
**Team Slack Channel**: #ceap-ops
**AWS Support**: [Support Case Portal]
**Escalation Path**: Engineer → Team Lead → Engineering Manager

## Revision History

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2024-01-15 | 1.0 | Initial runbook | Operations Team |
