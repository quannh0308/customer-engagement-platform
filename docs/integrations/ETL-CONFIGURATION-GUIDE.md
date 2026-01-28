# ETL Configuration Guide - CEAP Platform

**Last Updated**: January 27, 2026  
**Audience**: Developers configuring ETL workflows for the CEAP platform

---

## Overview

The CEAP platform's **ETL (Extract, Transform, Load)** system processes raw data from various sources, transforms it into engagement candidates, and loads them into DynamoDB. This guide covers how to configure ETL workflows, data transformations, field mappings, validation rules, and batch processing settings.

**Key Components**:
- **ETL Lambda**: Orchestrates extraction, transformation, and loading
- **Data Connectors**: Extract data from sources (Athena, S3, Kinesis)
- **Transformers**: Apply business logic and data transformations
- **Validators**: Ensure data quality and completeness
- **Batch Processor**: Handle large-scale data processing

---

## ETL Workflow Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐     ┌──────────────┐
│   Extract   │────▶│  Transform   │────▶│  Validate   │────▶│     Load     │
│ (Connector) │     │ (Mappings)   │     │  (Rules)    │     │ (DynamoDB)   │
└─────────────┘     └──────────────┘     └─────────────┘     └──────────────┘
       │                    │                     │                    │
       ▼                    ▼                     ▼                    ▼
  Query Data          Apply Logic          Check Quality        Batch Write
  Field Mapping       Enrich Data          Reject Invalid       Handle Errors
  Pagination          Calculate Fields     Log Issues           Track Metrics
```

---

## Configuration Steps

### Step 1: Configure Data Extraction

#### 1.1 Basic Connector Configuration

Create an ETL configuration file `etl-config.json`:

```json
{
  "etlConfig": {
    "connectorId": "athena-product-data",
    "connectorType": "data-warehouse",
    "enabled": true,
    "extractionConfig": {
      "database": "ceap_data_prod",
      "query": "SELECT * FROM product_deliveries WHERE delivery_date BETWEEN '{startDate}' AND '{endDate}'",
      "resultsBucket": "ceap-athena-results-prod",
      "workGroup": "primary",
      "maxWaitSeconds": 300,
      "batchSize": 1000,
      "maxRetries": 3
    }
  }
}
```

#### 1.2 Advanced Extraction with Pagination

```json
{
  "extractionConfig": {
    "database": "ceap_data_prod",
    "query": "SELECT * FROM large_dataset WHERE date = '{date}' LIMIT {limit} OFFSET {offset}",
    "resultsBucket": "ceap-athena-results-prod",
    "pagination": {
      "enabled": true,
      "pageSize": 5000,
      "maxPages": 100,
      "offsetField": "offset",
      "limitField": "limit"
    },
    "parallelExecution": {
      "enabled": true,
      "maxConcurrency": 5
    }
  }
}
```

---

### Step 2: Configure Data Transformations

#### 2.1 Field Mappings

Map source fields to CEAP candidate fields:

```json
{
  "transformationConfig": {
    "fieldMappings": {
      "customerId": "cust_id",
      "subjectId": "product_id",
      "subjectType": "product",
      "eventDate": "delivery_timestamp",
      "marketplace": "market_code",
      "orderValue": "total_amount",
      "emailEligible": "email_opt_in",
      "smsEligible": "sms_opt_in"
    }
  }
}
```

#### 2.2 Computed Fields

Add calculated fields during transformation:

```json
{
  "transformationConfig": {
    "computedFields": [
      {
        "fieldName": "eligibilityScore",
        "expression": "orderValue * 0.1 + (emailEligible ? 10 : 0)",
        "type": "number"
      },
      {
        "fieldName": "timingWindow",
        "expression": "eventDate.hour >= 9 && eventDate.hour <= 17 ? 'business' : 'evening'",
        "type": "string"
      },
      {
        "fieldName": "customerSegment",
        "expression": "orderValue > 100 ? 'premium' : orderValue > 50 ? 'standard' : 'basic'",
        "type": "string"
      }
    ]
  }
}
```

#### 2.3 Data Enrichment

Enrich data with additional information:

```json
{
  "transformationConfig": {
    "enrichment": {
      "enabled": true,
      "sources": [
        {
          "sourceType": "dynamodb",
          "tableName": "CustomerProfiles-prod",
          "lookupKey": "customerId",
          "fields": ["preferredLanguage", "timezone", "vipStatus"]
        },
        {
          "sourceType": "api",
          "endpoint": "https://api.example.com/product-details",
          "method": "GET",
          "lookupKey": "subjectId",
          "fields": ["productName", "category", "brand"],
          "cacheEnabled": true,
          "cacheTTL": 3600
        }
      ]
    }
  }
}
```

#### 2.4 Data Normalization

Standardize data formats:

```json
{
  "transformationConfig": {
    "normalization": {
      "dateFormat": "ISO8601",
      "timezone": "UTC",
      "currencyFormat": "USD",
      "phoneFormat": "E164",
      "emailFormat": "lowercase",
      "rules": [
        {
          "field": "marketplace",
          "transform": "uppercase"
        },
        {
          "field": "customerId",
          "transform": "trim"
        },
        {
          "field": "eventDate",
          "transform": "parseDate",
          "format": "yyyy-MM-dd HH:mm:ss"
        }
      ]
    }
  }
}
```

---

### Step 3: Configure Validation Rules

#### 3.1 Required Field Validation

```json
{
  "validationConfig": {
    "requiredFields": [
      "customerId",
      "subjectId",
      "subjectType",
      "eventDate",
      "marketplace",
      "programId"
    ],
    "onMissingField": "reject",
    "logMissingFields": true
  }
}
```

#### 3.2 Data Type Validation

```json
{
  "validationConfig": {
    "typeValidation": {
      "enabled": true,
      "rules": [
        {
          "field": "customerId",
          "type": "string",
          "pattern": "^CUST-[0-9]{5,10}$"
        },
        {
          "field": "orderValue",
          "type": "number",
          "min": 0,
          "max": 100000
        },
        {
          "field": "eventDate",
          "type": "date",
          "minDate": "2020-01-01",
          "maxDate": "now+7d"
        },
        {
          "field": "marketplace",
          "type": "enum",
          "allowedValues": ["US", "UK", "DE", "FR", "JP"]
        }
      ]
    }
  }
}
```

#### 3.3 Business Logic Validation

```json
{
  "validationConfig": {
    "businessRules": [
      {
        "ruleId": "delivery-date-check",
        "description": "Event date must be within last 30 days",
        "condition": "eventDate >= now-30d && eventDate <= now",
        "onFailure": "reject",
        "errorMessage": "Event date outside acceptable range"
      },
      {
        "ruleId": "channel-eligibility",
        "description": "At least one channel must be eligible",
        "condition": "emailEligible || smsEligible || pushEligible",
        "onFailure": "reject",
        "errorMessage": "No eligible communication channels"
      },
      {
        "ruleId": "order-value-check",
        "description": "Order value required for premium programs",
        "condition": "programId != 'premium-reviews' || orderValue > 0",
        "onFailure": "warn",
        "errorMessage": "Missing order value for premium program"
      }
    ]
  }
}
```

#### 3.4 Duplicate Detection

```json
{
  "validationConfig": {
    "duplicateDetection": {
      "enabled": true,
      "keys": ["customerId", "subjectId", "programId"],
      "window": "24h",
      "action": "skip",
      "logDuplicates": true
    }
  }
}
```

---

### Step 4: Configure Batch Processing

#### 4.1 Batch Size and Throttling

```json
{
  "batchConfig": {
    "batchSize": 25,
    "maxBatchesPerRun": 100,
    "throttling": {
      "enabled": true,
      "requestsPerSecond": 10,
      "burstCapacity": 50
    },
    "retryPolicy": {
      "maxRetries": 3,
      "backoffMultiplier": 2,
      "initialBackoffMs": 100,
      "maxBackoffMs": 5000
    }
  }
}
```

#### 4.2 Error Handling

```json
{
  "batchConfig": {
    "errorHandling": {
      "onTransformError": "skip",
      "onValidationError": "skip",
      "onLoadError": "retry",
      "maxErrorsPerBatch": 10,
      "maxErrorRate": 0.05,
      "deadLetterQueue": {
        "enabled": true,
        "queueUrl": "https://sqs.us-east-1.amazonaws.com/123456789/ceap-etl-dlq"
      }
    }
  }
}
```

#### 4.3 Parallel Processing

```json
{
  "batchConfig": {
    "parallelProcessing": {
      "enabled": true,
      "maxConcurrency": 10,
      "partitionKey": "marketplace",
      "preserveOrder": false
    }
  }
}
```

---

## Complete ETL Configuration Example

Here's a complete configuration combining all components:

```json
{
  "programId": "product-reviews",
  "marketplace": "US",
  "etlConfig": {
    "connectorId": "athena-product-deliveries",
    "connectorType": "data-warehouse",
    "enabled": true,
    "extractionConfig": {
      "database": "ceap_data_prod",
      "query": "SELECT customer_id, product_id, delivery_date, order_value, marketplace, email_opt_in, sms_opt_in FROM product_deliveries WHERE delivery_date BETWEEN '{startDate}' AND '{endDate}' AND marketplace = '{marketplace}'",
      "resultsBucket": "ceap-athena-results-prod",
      "workGroup": "primary",
      "maxWaitSeconds": 300,
      "batchSize": 1000
    },
    "transformationConfig": {
      "fieldMappings": {
        "customerId": "customer_id",
        "subjectId": "product_id",
        "subjectType": "product",
        "eventDate": "delivery_date",
        "orderValue": "order_value",
        "marketplace": "marketplace",
        "emailEligible": "email_opt_in",
        "smsEligible": "sms_opt_in"
      },
      "computedFields": [
        {
          "fieldName": "eligibilityScore",
          "expression": "orderValue * 0.1",
          "type": "number"
        }
      ],
      "normalization": {
        "dateFormat": "ISO8601",
        "timezone": "UTC"
      }
    },
    "validationConfig": {
      "requiredFields": ["customerId", "subjectId", "eventDate"],
      "typeValidation": {
        "enabled": true,
        "rules": [
          {
            "field": "orderValue",
            "type": "number",
            "min": 0
          }
        ]
      },
      "businessRules": [
        {
          "ruleId": "recent-delivery",
          "condition": "eventDate >= now-30d",
          "onFailure": "reject"
        }
      ]
    },
    "batchConfig": {
      "batchSize": 25,
      "maxBatchesPerRun": 100,
      "errorHandling": {
        "onTransformError": "skip",
        "onValidationError": "skip",
        "maxErrorRate": 0.05
      }
    }
  }
}
```

---

## AWS CLI Setup Commands

### Create ETL Configuration in DynamoDB

```bash
# Upload ETL configuration
aws dynamodb put-item \
  --table-name ProgramConfig-prod \
  --item file://etl-config-item.json
```

Where `etl-config-item.json` contains:

```json
{
  "PK": {"S": "PROGRAM#product-reviews"},
  "SK": {"S": "MARKETPLACE#US"},
  "programId": {"S": "product-reviews"},
  "marketplace": {"S": "US"},
  "etlConfig": {"S": "{\"connectorId\":\"athena-product-deliveries\",\"enabled\":true,...}"},
  "createdAt": {"S": "2026-01-27T00:00:00Z"},
  "updatedAt": {"S": "2026-01-27T00:00:00Z"}
}
```

### Create Dead Letter Queue

```bash
# Create DLQ for failed records
aws sqs create-queue \
  --queue-name ceap-etl-dlq-prod \
  --attributes '{
    "MessageRetentionPeriod": "1209600",
    "VisibilityTimeout": "300"
  }'

# Get queue URL
aws sqs get-queue-url --queue-name ceap-etl-dlq-prod
```

### Grant Lambda Permissions

```bash
# Allow Lambda to write to DLQ
aws sqs add-permission \
  --queue-url https://sqs.us-east-1.amazonaws.com/123456789/ceap-etl-dlq-prod \
  --label ETLLambdaAccess \
  --aws-account-ids 123456789 \
  --actions SendMessage
```

---

## Code Examples

### Custom Transformer (Kotlin)

```kotlin
// ceap-etl/src/main/kotlin/com/ceap/etl/transformers/CustomTransformer.kt
class CustomTransformer : DataTransformer {
    
    override fun transform(
        rawData: Map<String, Any>,
        config: TransformationConfig
    ): Map<String, Any> {
        val transformed = mutableMapOf<String, Any>()
        
        // Apply field mappings
        config.fieldMappings.forEach { (target, source) ->
            rawData[source]?.let { transformed[target] = it }
        }
        
        // Apply computed fields
        config.computedFields?.forEach { computed ->
            val value = evaluateExpression(computed.expression, transformed)
            transformed[computed.fieldName] = value
        }
        
        // Apply normalization
        config.normalization?.let { norm ->
            transformed["eventDate"] = normalizeDate(
                transformed["eventDate"] as String,
                norm.dateFormat,
                norm.timezone
            )
        }
        
        return transformed
    }
    
    private fun evaluateExpression(
        expression: String,
        context: Map<String, Any>
    ): Any {
        // Simple expression evaluator
        // In production, use a proper expression engine
        return when {
            expression.contains("*") -> {
                val parts = expression.split("*")
                (context[parts[0].trim()] as Double) * parts[1].trim().toDouble()
            }
            else -> expression
        }
    }
}
```

### Custom Validator (Kotlin)

```kotlin
// ceap-etl/src/main/kotlin/com/ceap/etl/validators/BusinessRuleValidator.kt
class BusinessRuleValidator(
    private val config: ValidationConfig
) : DataValidator {
    
    override fun validate(data: Map<String, Any>): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // Check required fields
        config.requiredFields.forEach { field ->
            if (!data.containsKey(field) || data[field] == null) {
                errors.add("Missing required field: $field")
            }
        }
        
        // Apply business rules
        config.businessRules?.forEach { rule ->
            if (!evaluateCondition(rule.condition, data)) {
                when (rule.onFailure) {
                    "reject" -> errors.add(rule.errorMessage)
                    "warn" -> warnings.add(rule.errorMessage)
                }
            }
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    private fun evaluateCondition(
        condition: String,
        data: Map<String, Any>
    ): Boolean {
        // Evaluate business rule condition
        // In production, use a proper rules engine
        return true // Simplified
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)
```

### Batch Processor (Kotlin)

```kotlin
// ceap-etl/src/main/kotlin/com/ceap/etl/BatchProcessor.kt
class BatchProcessor(
    private val dynamoDbClient: DynamoDbClient,
    private val config: BatchConfig
) {
    
    suspend fun processBatch(
        candidates: List<Candidate>
    ): BatchResult = coroutineScope {
        val batches = candidates.chunked(config.batchSize)
        val results = mutableListOf<WriteResult>()
        
        batches.forEachIndexed { index, batch ->
            // Apply throttling
            if (index > 0) {
                delay(1000L / config.throttling.requestsPerSecond)
            }
            
            // Process batch with retry
            val result = retryWithBackoff(config.retryPolicy) {
                writeBatch(batch)
            }
            
            results.add(result)
        }
        
        BatchResult(
            totalProcessed = candidates.size,
            successful = results.sumOf { it.successful },
            failed = results.sumOf { it.failed }
        )
    }
    
    private suspend fun writeBatch(
        candidates: List<Candidate>
    ): WriteResult {
        val writeRequests = candidates.map { candidate ->
            WriteRequest.builder()
                .putRequest(
                    PutRequest.builder()
                        .item(candidate.toDynamoDbItem())
                        .build()
                )
                .build()
        }
        
        val response = dynamoDbClient.batchWriteItem {
            requestItems = mapOf("Candidates-prod" to writeRequests)
        }
        
        return WriteResult(
            successful = candidates.size - (response.unprocessedItems()?.size ?: 0),
            failed = response.unprocessedItems()?.size ?: 0
        )
    }
}

data class BatchResult(
    val totalProcessed: Int,
    val successful: Int,
    val failed: Int
)

data class WriteResult(
    val successful: Int,
    val failed: Int
)
```

---

## Testing Procedures

### Test 1: Validate Configuration

```bash
# Test configuration syntax
cat etl-config.json | jq '.'

# Validate against schema
ajv validate -s etl-config-schema.json -d etl-config.json
```

### Test 2: Test Extraction

```bash
# Invoke ETL Lambda with test event
aws lambda invoke \
  --function-name CeapETLLambda-prod \
  --payload '{
    "action": "extract",
    "programId": "product-reviews",
    "marketplace": "US",
    "dateRange": {
      "start": "2026-01-26",
      "end": "2026-01-27"
    }
  }' \
  --cli-binary-format raw-in-base64-out \
  response.json

# Check results
cat response.json | jq '.extractedCount'
```

### Test 3: Test Transformation

```bash
# Test transformation with sample data
aws lambda invoke \
  --function-name CeapETLLambda-prod \
  --payload '{
    "action": "transform",
    "data": [{
      "customer_id": "CUST-12345",
      "product_id": "PROD-67890",
      "delivery_date": "2026-01-27",
      "order_value": 49.99
    }]
  }' \
  --cli-binary-format raw-in-base64-out \
  response.json

cat response.json | jq '.transformed'
```

### Test 4: Test Validation

```bash
# Test validation rules
aws lambda invoke \
  --function-name CeapETLLambda-prod \
  --payload '{
    "action": "validate",
    "data": [{
      "customerId": "CUST-12345",
      "subjectId": "PROD-67890",
      "eventDate": "2026-01-27",
      "orderValue": -10
    }]
  }' \
  --cli-binary-format raw-in-base64-out \
  response.json

# Should show validation errors
cat response.json | jq '.validationErrors'
```

### Test 5: End-to-End Test

```bash
# Run complete ETL workflow
aws stepfunctions start-execution \
  --state-machine-arn arn:aws:states:us-east-1:123456789:stateMachine:CeapBatchIngestion-prod \
  --name "etl-test-$(date +%s)" \
  --input '{
    "programId": "product-reviews",
    "marketplace": "US",
    "batchId": "test-batch-001",
    "dateRange": {
      "start": "2026-01-26",
      "end": "2026-01-27"
    }
  }'

# Monitor execution
aws stepfunctions describe-execution \
  --execution-arn "YOUR_EXECUTION_ARN" \
  --query '{status:status,output:output}' \
  --output json
```

---

## Troubleshooting

### Issue: "Extraction timeout"

**Symptoms**: Query takes longer than maxWaitSeconds

**Solutions**:
1. Increase `maxWaitSeconds` in extraction config
2. Optimize Athena query (add partitions, reduce data scanned)
3. Enable pagination for large datasets
4. Use smaller date ranges

```bash
# Check query execution time
aws athena get-query-execution \
  --query-execution-id YOUR_QUERY_ID \
  --query 'QueryExecution.Statistics.TotalExecutionTimeInMillis'
```

### Issue: "Transformation errors"

**Symptoms**: Records failing during transformation

**Solutions**:
1. Check field mappings match source data
2. Verify computed field expressions are valid
3. Review logs for specific errors
4. Test transformation with sample data

```bash
# Check CloudWatch logs
aws logs tail /aws/lambda/CeapETLLambda-prod --follow --filter-pattern "ERROR"
```

### Issue: "High validation failure rate"

**Symptoms**: Many records rejected by validation

**Solutions**:
1. Review validation rules - may be too strict
2. Check data quality at source
3. Add data cleansing in transformation
4. Use "warn" instead of "reject" for non-critical rules

```bash
# Check validation metrics
aws cloudwatch get-metric-statistics \
  --namespace CEAP/ETL \
  --metric-name ValidationFailures \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Sum
```

### Issue: "Batch write failures"

**Symptoms**: Records not appearing in DynamoDB

**Solutions**:
1. Check DynamoDB capacity (provisioned or on-demand)
2. Review error handling configuration
3. Check dead letter queue for failed records
4. Reduce batch size if throttling occurs

```bash
# Check DLQ for failed records
aws sqs receive-message \
  --queue-url https://sqs.us-east-1.amazonaws.com/123456789/ceap-etl-dlq-prod \
  --max-number-of-messages 10

# Check DynamoDB throttling
aws cloudwatch get-metric-statistics \
  --namespace AWS/DynamoDB \
  --metric-name ThrottledRequests \
  --dimensions Name=TableName,Value=Candidates-prod \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 300 \
  --statistics Sum
```

---

## Real-World Use Cases

### Use Case 1: Product Review Collection

**Scenario**: Extract recent product deliveries and create review candidates

**Configuration**:
```json
{
  "programId": "product-reviews",
  "extractionConfig": {
    "query": "SELECT customer_id, product_id, delivery_date, order_value FROM deliveries WHERE delivery_date >= CURRENT_DATE - INTERVAL '7' DAY AND review_submitted = false"
  },
  "transformationConfig": {
    "computedFields": [
      {
        "fieldName": "reviewWindow",
        "expression": "eventDate + 7d",
        "type": "date"
      }
    ]
  },
  "validationConfig": {
    "businessRules": [
      {
        "ruleId": "delivery-confirmed",
        "condition": "deliveryStatus == 'DELIVERED'",
        "onFailure": "reject"
      }
    ]
  }
}
```

### Use Case 2: Video Rating Campaign

**Scenario**: Extract completed video watches for rating requests

**Configuration**:
```json
{
  "programId": "video-ratings",
  "extractionConfig": {
    "query": "SELECT customer_id, video_id, watch_completed_at, watch_percentage FROM video_watches WHERE watch_percentage >= 80 AND watch_date = '{date}'"
  },
  "transformationConfig": {
    "fieldMappings": {
      "customerId": "customer_id",
      "subjectId": "video_id",
      "subjectType": "video",
      "eventDate": "watch_completed_at"
    },
    "enrichment": {
      "enabled": true,
      "sources": [
        {
          "sourceType": "api",
          "endpoint": "https://api.example.com/videos/{subjectId}",
          "fields": ["title", "genre", "duration"]
        }
      ]
    }
  },
  "validationConfig": {
    "businessRules": [
      {
        "ruleId": "minimum-watch-percentage",
        "condition": "watchPercentage >= 80",
        "onFailure": "reject"
      }
    ]
  }
}
```

### Use Case 3: High-Value Customer Surveys

**Scenario**: Extract high-value orders for satisfaction surveys

**Configuration**:
```json
{
  "programId": "vip-surveys",
  "extractionConfig": {
    "query": "SELECT customer_id, order_id, order_value, order_date FROM orders WHERE order_value >= 500 AND order_date BETWEEN '{startDate}' AND '{endDate}'"
  },
  "transformationConfig": {
    "computedFields": [
      {
        "fieldName": "customerTier",
        "expression": "orderValue >= 1000 ? 'platinum' : 'gold'",
        "type": "string"
      },
      {
        "fieldName": "surveyPriority",
        "expression": "orderValue / 100",
        "type": "number"
      }
    ]
  },
  "validationConfig": {
    "businessRules": [
      {
        "ruleId": "minimum-order-value",
        "condition": "orderValue >= 500",
        "onFailure": "reject"
      }
    ]
  },
  "batchConfig": {
    "batchSize": 10,
    "throttling": {
      "requestsPerSecond": 5
    }
  }
}
```

---

## Performance Optimization Tips

### 1. Optimize Extraction Queries

```sql
-- Bad: Full table scan
SELECT * FROM large_table WHERE date >= '2026-01-01'

-- Good: Use partitions
SELECT * FROM large_table 
WHERE date_partition >= '2026-01-01' 
  AND date_partition <= '2026-01-31'
```

### 2. Enable Caching for Enrichment

```json
{
  "enrichment": {
    "sources": [
      {
        "sourceType": "api",
        "endpoint": "https://api.example.com/products",
        "cacheEnabled": true,
        "cacheTTL": 3600,
        "cacheSize": 10000
      }
    ]
  }
}
```

### 3. Use Parallel Processing

```json
{
  "batchConfig": {
    "parallelProcessing": {
      "enabled": true,
      "maxConcurrency": 10,
      "partitionKey": "marketplace"
    }
  }
}
```

### 4. Optimize Batch Sizes

```json
{
  "batchConfig": {
    "batchSize": 25,
    "maxBatchesPerRun": 100
  }
}
```

---

## Monitoring and Metrics

### Key Metrics to Track

```bash
# Extraction metrics
aws cloudwatch get-metric-statistics \
  --namespace CEAP/ETL \
  --metric-name RecordsExtracted \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Sum,Average

# Transformation metrics
aws cloudwatch get-metric-statistics \
  --namespace CEAP/ETL \
  --metric-name TransformationErrors \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Sum

# Validation metrics
aws cloudwatch get-metric-statistics \
  --namespace CEAP/ETL \
  --metric-name ValidationFailureRate \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Average

# Load metrics
aws cloudwatch get-metric-statistics \
  --namespace CEAP/ETL \
  --metric-name RecordsLoaded \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Sum
```

---

## Summary

**ETL Configuration Checklist**:

1. ✅ Configure data extraction (connector, query, pagination)
2. ✅ Set up field mappings and transformations
3. ✅ Define computed fields and enrichment sources
4. ✅ Configure validation rules (required fields, types, business logic)
5. ✅ Set up batch processing (size, throttling, error handling)
6. ✅ Create dead letter queue for failed records
7. ✅ Test extraction, transformation, and validation
8. ✅ Monitor metrics and optimize performance

**Your ETL pipeline is now configured!**

---

## Next Steps

- **Configure Scoring**: See `SCORING-CONFIGURATION-GUIDE.md`
- **Configure Storage**: See `STORAGE-CONFIGURATION-GUIDE.md`
- **Configure Notifications**: See `NOTIFICATION-CONFIGURATION-GUIDE.md`
- **Monitor Performance**: Set up CloudWatch dashboards

---

**Need help?** Check `docs/TROUBLESHOOTING.md` or review `docs/USE-CASES.md` for complete examples.
