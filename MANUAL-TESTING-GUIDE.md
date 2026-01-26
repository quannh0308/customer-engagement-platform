# Manual Testing Guide - CEAP Platform

**Last Updated**: January 27, 2026  
**Environment**: dev  
**AWS Account**: {AWS_ACCOUNT_ID}

---

## Quick Test Commands

### 1. Test Step Functions - Batch Ingestion Workflow

**Start an execution:**
```bash
aws stepfunctions start-execution \
  --state-machine-arn arn:aws:states:us-east-1:{AWS_ACCOUNT_ID}:stateMachine:CeapBatchIngestion-dev \
  --name "test-$(date +%s)" \
  --input '{
    "programId": "product-reviews",
    "marketplace": "US",
    "batchId": "test-batch-001",
    "correlationId": "test-corr-001"
  }'
```

**Check execution status:**
```bash
# List recent executions
aws stepfunctions list-executions \
  --state-machine-arn arn:aws:states:us-east-1:{AWS_ACCOUNT_ID}:stateMachine:CeapBatchIngestion-dev \
  --max-results 5 \
  --query 'executions[*].{Name:name,Status:status,Start:startDate}' \
  --output table

# Get specific execution details (replace EXECUTION_ARN)
aws stepfunctions describe-execution \
  --execution-arn "arn:aws:states:us-east-1:{AWS_ACCOUNT_ID}:execution:CeapBatchIngestion-dev:test-1234567890"
```

**View execution history:**
```bash
aws stepfunctions get-execution-history \
  --execution-arn "arn:aws:states:us-east-1:{AWS_ACCOUNT_ID}:execution:CeapBatchIngestion-dev:test-1234567890" \
  --query 'events[*].{Type:type,Timestamp:timestamp}' \
  --output table
```

**Expected Result**: Workflow completes with status SUCCEEDED (or FAILED if no data sources configured)

---

### 2. Test Reactive Lambda - Event Processing

**Invoke reactive Lambda directly:**
```bash
aws lambda invoke \
  --function-name $(aws lambda list-functions --query 'Functions[?contains(FunctionName, `ReactiveLambda`)].FunctionName' --output text) \
  --cli-binary-format raw-in-base64-out \
  --payload '{"detail":{"customerId":"CUST-TEST-001","eventType":"OrderDelivered","subjectType":"product","subjectId":"PROD-12345","programId":"product-reviews","marketplace":"US","eventDate":"2026-01-27T00:00:00Z","metadata":{}}}' \
  response.json && cat response.json && rm response.json
```

**Expected Response:**
```json
{
  "success": true,
  "candidateCreated": true,
  "candidateId": "CUST-TEST-001:PROD-12345",
  "executionTimeMs": 245
}
```

---

### 3. Test ETL Lambda

**Invoke ETL Lambda:**
```bash
aws lambda invoke \
  --function-name $(aws lambda list-functions --query 'Functions[?contains(FunctionName, `ETLLambda`)].FunctionName' --output text) \
  --payload '{
    "programId": "product-reviews",
    "marketplace": "US",
    "batchId": "test-batch-001"
  }' \
  response.json && cat response.json && rm response.json
```

**Expected Response:**
```json
{
  "success": true,
  "candidatesExtracted": 0,
  "executionTimeMs": 150,
  "message": "No data source configured"
}
```

---

### 4. Test Filter Lambda

**Invoke Filter Lambda:**
```bash
aws lambda invoke \
  --function-name $(aws lambda list-functions --query 'Functions[?contains(FunctionName, `FilterLambda`)].FunctionName' --output text) \
  --payload '{
    "candidates": [
      {
        "customerId": "CUST-001",
        "subject": {"type": "product", "id": "PROD-123"},
        "context": [],
        "attributes": {
          "channelEligibility": {"email": true},
          "timingWindow": "morning",
          "mediaEligible": true
        },
        "metadata": {
          "programId": "product-reviews",
          "marketplace": "US",
          "createdAt": "2026-01-27T00:00:00Z",
          "version": 1
        }
      }
    ],
    "programId": "product-reviews"
  }' \
  response.json && cat response.json && rm response.json
```

---

### 5. Test Score Lambda

**Invoke Score Lambda:**
```bash
aws lambda invoke \
  --function-name $(aws lambda list-functions --query 'Functions[?contains(FunctionName, `ScoreLambda`)].FunctionName' --output text) \
  --payload '{
    "candidates": [
      {
        "customerId": "CUST-001",
        "subject": {"type": "product", "id": "PROD-123"},
        "context": [],
        "attributes": {
          "channelEligibility": {"email": true}
        },
        "metadata": {
          "programId": "product-reviews",
          "marketplace": "US"
        }
      }
    ]
  }' \
  response.json && cat response.json && rm response.json
```

---

### 6. Test Store Lambda

**Invoke Store Lambda:**
```bash
aws lambda invoke \
  --function-name $(aws lambda list-functions --query 'Functions[?contains(FunctionName, `StoreLambda`)].FunctionName' --output text) \
  --payload '{
    "candidates": [
      {
        "customerId": "CUST-TEST-002",
        "subject": {"type": "product", "id": "PROD-456"},
        "context": [{"type": "program", "id": "product-reviews"}],
        "scores": {"default-model": {"modelId": "default-model", "value": 0.85, "confidence": 0.9, "timestamp": "2026-01-27T00:00:00Z"}},
        "attributes": {
          "channelEligibility": {"email": true},
          "timingWindow": "morning",
          "mediaEligible": true
        },
        "metadata": {
          "programId": "product-reviews",
          "marketplace": "US",
          "createdAt": "2026-01-27T00:00:00Z",
          "version": 1
        }
      }
    ]
  }' \
  response.json && cat response.json && rm response.json
```

**Then verify in DynamoDB:**
```bash
aws dynamodb get-item \
  --table-name Candidates-dev \
  --key '{"PK": {"S": "CUST#CUST-TEST-002#PROG#product-reviews#MKT#US"}, "SK": {"S": "SUBJ#product#PROD-456"}}'
```

---

### 7. Query DynamoDB Tables

**Check Candidates table:**
```bash
# Scan table (limit 10)
aws dynamodb scan \
  --table-name Candidates-dev \
  --max-items 10 \
  --query 'Items[*].{Customer:PK.S,Subject:SK.S}' \
  --output table

# Count total items
aws dynamodb scan \
  --table-name Candidates-dev \
  --select COUNT \
  --query 'Count'
```

**Check ProgramConfig table:**
```bash
aws dynamodb scan \
  --table-name ProgramConfig-dev \
  --query 'Items[*].{ProgramId:programId.S,Marketplace:marketplace.S}' \
  --output table
```

**Check ScoreCache table:**
```bash
aws dynamodb scan \
  --table-name ScoreCache-dev \
  --max-items 10 \
  --query 'Items[*].{Key:cacheKey.S,Score:score.N}' \
  --output table
```

---

### 8. View CloudWatch Logs

**Reactive Lambda logs:**
```bash
# Get log group name
REACTIVE_LOG_GROUP=$(aws lambda get-function --function-name $(aws lambda list-functions --query 'Functions[?contains(FunctionName, `ReactiveLambda`)].FunctionName' --output text) --query 'Configuration.LoggingConfig.LogGroup' --output text)

# Tail logs (live)
aws logs tail $REACTIVE_LOG_GROUP --follow

# Get recent logs
aws logs tail $REACTIVE_LOG_GROUP --since 10m
```

**ETL Lambda logs:**
```bash
ETL_LOG_GROUP=$(aws lambda get-function --function-name $(aws lambda list-functions --query 'Functions[?contains(FunctionName, `ETLLambda`)].FunctionName' --output text) --query 'Configuration.LoggingConfig.LogGroup' --output text)

aws logs tail $ETL_LOG_GROUP --since 1h
```

---

### 9. Test EventBridge Rules

**List EventBridge rules:**
```bash
aws events list-rules --query 'Rules[?contains(Name, `ceap`)].{Name:Name,State:State,Schedule:ScheduleExpression}' --output table
```

**Check rule targets:**
```bash
aws events list-targets-by-rule --rule ceap-customer-events-dev
```

**Send test event:**
```bash
aws events put-events --entries '[
  {
    "Source": "ceap.customer-events",
    "DetailType": "OrderDelivered",
    "Detail": "{\"customerId\":\"CUST-TEST-003\",\"orderId\":\"ORDER-789\",\"marketplace\":\"US\"}",
    "EventBusName": "default"
  }
]'
```

---

### 10. End-to-End Test Scenario

**Complete workflow test:**

```bash
# Step 1: Create a test candidate via Reactive Lambda
echo "Step 1: Creating candidate..."
aws lambda invoke \
  --function-name $(aws lambda list-functions --query 'Functions[?contains(FunctionName, `ReactiveLambda`)].FunctionName' --output text) \
  --payload '{
    "detail": {
      "customerId": "CUST-E2E-TEST",
      "eventType": "OrderDelivered",
      "subjectType": "product",
      "subjectId": "PROD-E2E-001",
      "programId": "product-reviews",
      "marketplace": "US",
      "eventDate": "2026-01-27T00:00:00Z",
      "metadata": {"orderValue": 49.99}
    }
  }' \
  response.json && cat response.json && echo ""

# Step 2: Verify candidate was stored in DynamoDB
echo "Step 2: Checking DynamoDB..."
aws dynamodb get-item \
  --table-name Candidates-dev \
  --key '{"PK": {"S": "CUST#CUST-E2E-TEST#PROG#product-reviews#MKT#US"}, "SK": {"S": "SUBJ#product#PROD-E2E-001"}}' \
  --query 'Item.{Customer:PK.S,Subject:SK.S,CreatedAt:createdAt.S}' \
  --output table

# Step 3: Check CloudWatch logs
echo "Step 3: Checking logs..."
REACTIVE_LOG_GROUP=$(aws lambda get-function --function-name $(aws lambda list-functions --query 'Functions[?contains(FunctionName, `ReactiveLambda`)].FunctionName' --output text) --query 'Configuration.LoggingConfig.LogGroup' --output text)
aws logs tail $REACTIVE_LOG_GROUP --since 5m | grep "CUST-E2E-TEST"

# Cleanup
rm -f response.json
```

---

### 11. Check Deployment Health

**List all resources:**
```bash
# CloudFormation stacks
aws cloudformation list-stacks --stack-status-filter CREATE_COMPLETE --query 'StackSummaries[?starts_with(StackName, `Ceap`)].StackName' --output table

# DynamoDB tables
aws dynamodb list-tables --query 'TableNames[?contains(@, `dev`)]' --output table

# Lambda functions
aws lambda list-functions --query 'Functions[?contains(FunctionName, `Ceap`)].{Name:FunctionName,Runtime:Runtime,Memory:MemorySize}' --output table

# Step Functions
aws stepfunctions list-state-machines --query 'stateMachines[?contains(name, `Ceap`)].{Name:name,Status:status,Created:creationDate}' --output table
```

---

### 12. Monitor Costs

**Check current month costs:**
```bash
aws ce get-cost-and-usage \
  --time-period Start=2026-01-01,End=2026-01-31 \
  --granularity MONTHLY \
  --metrics BlendedCost \
  --query 'ResultsByTime[0].Total.BlendedCost.{Amount:Amount,Unit:Unit}' \
  --output table
```

---

### 13. Cleanup (When Done Testing)

**Delete all resources:**
```bash
cd infrastructure
cdk destroy --all --context environment=dev --force
```

**Or delete specific stack:**
```bash
cdk destroy CeapDatabase-dev --context environment=dev --force
```

---

## Test Scenarios by Use Case

### Scenario 1: Product Review Collection (Reactive)

**Trigger**: Customer receives product delivery

```bash
aws lambda invoke \
  --function-name $(aws lambda list-functions --query 'Functions[?contains(FunctionName, `ReactiveLambda`)].FunctionName' --output text) \
  --payload '{
    "detail": {
      "customerId": "CUST-12345",
      "eventType": "OrderDelivered",
      "subjectType": "product",
      "subjectId": "B08N5WRWNW",
      "programId": "product-reviews",
      "marketplace": "US",
      "eventDate": "2026-01-27T00:00:00Z",
      "metadata": {
        "orderValue": 29.99,
        "deliveryDate": "2026-01-27",
        "productCategory": "Electronics"
      }
    }
  }' \
  response.json && cat response.json
```

### Scenario 2: Video Rating Collection (Reactive)

**Trigger**: Customer completes video watch

```bash
aws lambda invoke \
  --function-name $(aws lambda list-functions --query 'Functions[?contains(FunctionName, `ReactiveLambda`)].FunctionName' --output text) \
  --payload '{
    "detail": {
      "customerId": "CUST-67890",
      "eventType": "VideoWatched",
      "subjectType": "video",
      "subjectId": "VID-ABC123",
      "programId": "video-ratings",
      "marketplace": "US",
      "eventDate": "2026-01-27T00:00:00Z",
      "metadata": {
        "watchPercentage": 95,
        "videoDuration": 3600,
        "genre": "Documentary"
      }
    }
  }' \
  response.json && cat response.json
```

### Scenario 3: Batch Processing Test

**Start batch workflow:**
```bash
aws stepfunctions start-execution \
  --state-machine-arn arn:aws:states:us-east-1:{AWS_ACCOUNT_ID}:stateMachine:CeapBatchIngestion-dev \
  --name "batch-test-$(date +%s)" \
  --input '{
    "programId": "music-feedback",
    "marketplace": "US",
    "batchId": "batch-$(date +%Y%m%d)",
    "correlationId": "corr-$(date +%s)"
  }'
```

---

## Useful AWS Console Links

**Your Account ({AWS_ACCOUNT_ID}):**

- **CloudFormation**: https://console.aws.amazon.com/cloudformation/home?region=us-east-1
- **DynamoDB**: https://console.aws.amazon.com/dynamodbv2/home?region=us-east-1#tables
- **Lambda**: https://console.aws.amazon.com/lambda/home?region=us-east-1#/functions
- **Step Functions**: https://console.aws.amazon.com/states/home?region=us-east-1#/statemachines
- **CloudWatch Logs**: https://console.aws.amazon.com/cloudwatch/home?region=us-east-1#logsV2:log-groups
- **EventBridge**: https://console.aws.amazon.com/events/home?region=us-east-1#/rules

---

## Troubleshooting

### Lambda Returns Error

**Check logs:**
```bash
# Get function name
FUNCTION_NAME=$(aws lambda list-functions --query 'Functions[?contains(FunctionName, `ReactiveLambda`)].FunctionName' --output text)

# Get log group
LOG_GROUP="/aws/lambda/$FUNCTION_NAME"

# View recent errors
aws logs filter-log-events \
  --log-group-name $LOG_GROUP \
  --filter-pattern "ERROR" \
  --start-time $(($(date +%s) - 3600))000 \
  --query 'events[*].message' \
  --output text
```

### Step Functions Fails

**Get failure reason:**
```bash
aws stepfunctions describe-execution \
  --execution-arn "YOUR_EXECUTION_ARN" \
  --query '{Status:status,Error:error,Cause:cause}'
```

### DynamoDB Access Denied

**Check IAM permissions:**
```bash
# Lambda should have DynamoDB permissions
aws lambda get-function \
  --function-name YOUR_FUNCTION_NAME \
  --query 'Configuration.Role'
```

---

## Next Steps

1. ✅ **Deployment complete** - All infrastructure deployed
2. 🧪 **Manual testing** - Use commands above to test
3. 📊 **Configure program** - Add program config to DynamoDB
4. 🔗 **Connect data sources** - Configure Athena/Kinesis connectors
5. 📧 **Set up channels** - Configure email/SMS providers
6. 📈 **Monitor** - Set up CloudWatch dashboards

---

**Happy Testing!** 🚀
