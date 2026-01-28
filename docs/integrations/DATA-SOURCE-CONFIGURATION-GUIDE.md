# Data Source Configuration Guide - CEAP Platform

**Last Updated**: January 27, 2026  
**Audience**: Developers configuring data sources for the CEAP platform

---

## Overview

The CEAP platform uses **data connectors** to extract customer engagement candidates from various data sources. Currently, the platform includes a **DataWarehouseConnector** that queries data via AWS Athena.

---

## Available Data Connectors

### 1. DataWarehouseConnector (AWS Athena)

**Purpose**: Query data warehouses using SQL via AWS Athena

**Supported Sources**:
- Amazon S3 data lakes
- AWS Glue Data Catalog tables
- External data sources accessible via Athena

**Use Cases**:
- Batch processing of historical data
- Querying customer purchase history
- Extracting video watch data
- Analyzing service interactions

---

## Configuration Steps

### Step 1: Set Up AWS Athena (If Not Already Done)

#### 1.1 Create S3 Bucket for Query Results

```bash
# Create bucket for Athena query results
aws s3 mb s3://ceap-athena-results-dev --region us-east-1

# Enable versioning (optional)
aws s3api put-bucket-versioning \
  --bucket ceap-athena-results-dev \
  --versioning-configuration Status=Enabled
```

#### 1.2 Create Athena Database (Example)

```sql
-- Create database in Athena
CREATE DATABASE IF NOT EXISTS ceap_data_dev
COMMENT 'CEAP platform data for dev environment'
LOCATION 's3://your-data-bucket/ceap-data/';
```

#### 1.3 Create Sample Table (Example: Product Deliveries)

```sql
CREATE EXTERNAL TABLE IF NOT EXISTS ceap_data_dev.product_deliveries (
    customer_id STRING,
    product_id STRING,
    order_id STRING,
    delivery_date STRING,
    order_value DOUBLE,
    product_category STRING,
    marketplace STRING,
    email_eligible BOOLEAN,
    sms_eligible BOOLEAN
)
PARTITIONED BY (delivery_date_partition STRING)
STORED AS PARQUET
LOCATION 's3://your-data-bucket/product-deliveries/';
```

---

### Step 2: Configure Program in DynamoDB

The program configuration tells the ETL Lambda which data source to use and how to query it.

#### 2.1 Create Program Configuration JSON

Create a file `program-config.json`:

```json
{
  "programId": "product-reviews",
  "programName": "Product Review Collection",
  "marketplace": "US",
  "enabled": true,
  "candidateTTLDays": 30,
  "dataConnectors": [
    {
      "connectorId": "athena-product-deliveries",
      "connectorType": "data-warehouse",
      "enabled": true,
      "sourceConfig": {
        "database": "ceap_data_dev",
        "query": "SELECT customer_id, product_id, order_id, delivery_date, order_value, product_category, marketplace, email_eligible, sms_eligible FROM product_deliveries WHERE delivery_date_partition BETWEEN '{startDate}' AND '{endDate}' AND marketplace = 'US' LIMIT 1000",
        "resultsBucket": "ceap-athena-results-dev",
        "workGroup": "primary",
        "maxWaitSeconds": 300,
        "fieldMappings": {
          "customerId": "customer_id",
          "subjectId": "product_id",
          "subjectType": "product",
          "eventDate": "delivery_date",
          "orderValue": "order_value",
          "marketplace": "marketplace",
          "emailEligible": "email_eligible",
          "smsEligible": "sms_eligible"
        }
      }
    }
  ],
  "filterChain": {
    "filters": [
      {
        "filterId": "eligibility",
        "filterType": "eligibility",
        "enabled": true,
        "order": 1,
        "parameters": {
          "requiredChannels": ["email"],
          "checkTimingWindow": false
        }
      }
    ]
  },
  "scoringModels": [
    {
      "modelId": "review-propensity",
      "modelType": "sagemaker",
      "enabled": true,
      "weight": 1.0
    }
  ],
  "channels": [
    {
      "channelType": "email",
      "enabled": true,
      "config": {
        "templateId": "product-review-request",
        "fromName": "CEAP Platform",
        "fromEmail": "reviews@example.com"
      }
    }
  ]
}
```

#### 2.2 Upload to DynamoDB

```bash
# Convert to DynamoDB format and upload
aws dynamodb put-item \
  --table-name ProgramConfig-dev \
  --item '{
    "PK": {"S": "PROGRAM#product-reviews"},
    "SK": {"S": "MARKETPLACE#US"},
    "programId": {"S": "product-reviews"},
    "programName": {"S": "Product Review Collection"},
    "marketplace": {"S": "US"},
    "enabled": {"BOOL": true},
    "candidateTTLDays": {"N": "30"},
    "dataConnectors": {"S": "[{\"connectorId\":\"athena-product-deliveries\",\"connectorType\":\"data-warehouse\",\"enabled\":true,\"sourceConfig\":{\"database\":\"ceap_data_dev\",\"query\":\"SELECT customer_id, product_id, delivery_date FROM product_deliveries WHERE delivery_date_partition BETWEEN '"'"'{startDate}'"'"' AND '"'"'{endDate}'"'"' LIMIT 1000\",\"resultsBucket\":\"ceap-athena-results-dev\",\"workGroup\":\"primary\",\"fieldMappings\":{\"customerId\":\"customer_id\",\"subjectId\":\"product_id\",\"subjectType\":\"product\",\"eventDate\":\"delivery_date\",\"marketplace\":\"US\"}}}]"},
    "createdAt": {"S": "2026-01-27T00:00:00Z"},
    "updatedAt": {"S": "2026-01-27T00:00:00Z"}
  }'
```

---

### Step 3: Test the Configuration

#### 3.1 Trigger Step Functions Workflow

```bash
aws stepfunctions start-execution \
  --state-machine-arn arn:aws:states:us-east-1:794039325997:stateMachine:CeapBatchIngestion-dev \
  --name "test-with-data-$(date +%s)" \
  --input '{
    "programId": "product-reviews",
    "marketplace": "US",
    "batchId": "test-batch-001",
    "correlationId": "test-001",
    "dateRange": {
      "start": "2026-01-26",
      "end": "2026-01-27"
    }
  }'
```

#### 3.2 Monitor Execution

```bash
# Get execution ARN from previous command, then:
aws stepfunctions describe-execution \
  --execution-arn "YOUR_EXECUTION_ARN" \
  --query '{Status:status,Output:output}'
```

#### 3.3 Check Results in DynamoDB

```bash
# Check if candidates were created
aws dynamodb scan \
  --table-name Candidates-dev \
  --filter-expression "begins_with(PK, :prog)" \
  --expression-attribute-values '{":prog":{"S":"CUST#"}}' \
  --max-items 10
```

---

## Data Source Configuration Reference

### Required Fields in Source Data

Your Athena query MUST return these fields (or map them via fieldMappings):

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `customerId` | String | Unique customer identifier | "CUST-12345" |
| `subjectType` | String | Type of subject | "product", "video", "service" |
| `subjectId` | String | Unique subject identifier | "PROD-67890" |
| `eventDate` | String/Timestamp | When event occurred | "2026-01-27T00:00:00Z" |
| `marketplace` | String | Marketplace code | "US", "UK", "DE" |
| `programId` | String | Program identifier | "product-reviews" |

### Optional Fields

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `orderValue` | Number | Order value in dollars | 49.99 |
| `deliveryDate` | String/Timestamp | Delivery date | "2026-01-27" |
| `timingWindow` | String | Preferred contact time | "morning", "evening" |
| `emailEligible` | Boolean | Email channel eligible | true |
| `smsEligible` | Boolean | SMS channel eligible | false |
| `pushEligible` | Boolean | Push notification eligible | true |
| `mediaEligible` | Boolean | Media content eligible | true |

---

## Example Athena Queries

### Example 1: Product Reviews (Recent Deliveries)

```sql
SELECT 
    customer_id,
    product_id as subject_id,
    'product' as subject_type,
    delivery_date as event_date,
    order_value,
    marketplace,
    CASE WHEN email_opt_in = true THEN true ELSE false END as email_eligible,
    'product-reviews' as program_id
FROM product_deliveries
WHERE delivery_date_partition BETWEEN '{startDate}' AND '{endDate}'
  AND marketplace = 'US'
  AND delivery_status = 'DELIVERED'
  AND customer_id NOT IN (
      SELECT customer_id FROM review_submissions 
      WHERE product_id = product_deliveries.product_id
  )
LIMIT 10000;
```

### Example 2: Video Ratings (Completed Watches)

```sql
SELECT 
    customer_id,
    video_id as subject_id,
    'video' as subject_type,
    watch_completed_at as event_date,
    watch_percentage,
    marketplace,
    true as email_eligible,
    true as push_eligible,
    'video-ratings' as program_id
FROM video_watch_history
WHERE watch_date BETWEEN '{startDate}' AND '{endDate}'
  AND watch_percentage >= 80
  AND marketplace = 'US'
LIMIT 10000;
```

### Example 3: Service Surveys (Completed Interactions)

```sql
SELECT 
    customer_id,
    service_interaction_id as subject_id,
    'service' as subject_type,
    interaction_completed_at as event_date,
    service_type,
    marketplace,
    CASE WHEN sms_opt_in = true THEN true ELSE false END as sms_eligible,
    'service-surveys' as program_id
FROM service_interactions
WHERE interaction_date BETWEEN '{startDate}' AND '{endDate}'
  AND interaction_status = 'COMPLETED'
  AND marketplace = 'US'
LIMIT 5000;
```

---

## Field Mappings

If your source data has different column names, use `fieldMappings` to map them:

```json
{
  "fieldMappings": {
    "customerId": "cust_id",           // Target: Source
    "subjectId": "prod_id",
    "subjectType": "product",          // Can be a constant
    "eventDate": "delivered_at",
    "orderValue": "total_amount",
    "marketplace": "market",
    "emailEligible": "email_ok",
    "programId": "product-reviews"     // Can be a constant
  }
}
```

---

## Testing Your Data Source

### Test 1: Verify Athena Query Works

```bash
# Start query execution
QUERY_ID=$(aws athena start-query-execution \
  --query-string "SELECT * FROM ceap_data_dev.product_deliveries LIMIT 10" \
  --query-execution-context Database=ceap_data_dev \
  --result-configuration OutputLocation=s3://ceap-athena-results-dev/ \
  --query 'QueryExecutionId' \
  --output text)

# Wait for completion
aws athena get-query-execution --query-execution-id $QUERY_ID

# Get results
aws athena get-query-results --query-execution-id $QUERY_ID
```

### Test 2: Test ETL Lambda Directly

```bash
# Invoke ETL Lambda with your program config
aws lambda invoke \
  --function-name $(aws lambda list-functions --query 'Functions[?contains(FunctionName, `ETLLambda`)].FunctionName' --output text) \
  --cli-binary-format raw-in-base64-out \
  --payload '{
    "programId": "product-reviews",
    "marketplace": "US",
    "batchId": "test-001",
    "dateRange": {
      "start": "2026-01-26",
      "end": "2026-01-27"
    }
  }' \
  response.json && cat response.json
```

### Test 3: Run Full Workflow

```bash
aws stepfunctions start-execution \
  --state-machine-arn arn:aws:states:us-east-1:794039325997:stateMachine:CeapBatchIngestion-dev \
  --name "test-real-data-$(date +%s)" \
  --input '{
    "programId": "product-reviews",
    "marketplace": "US",
    "batchId": "batch-001",
    "correlationId": "corr-001",
    "dateRange": {
      "start": "2026-01-26",
      "end": "2026-01-27"
    }
  }'
```

---

## Quick Start: Mock Data Source

If you don't have Athena set up yet, you can create a simple mock data source for testing:

### Option 1: Use S3 with Sample Data

1. **Create sample CSV file** (`sample-deliveries.csv`):
```csv
customer_id,product_id,delivery_date,order_value,marketplace
CUST-001,PROD-123,2026-01-26,29.99,US
CUST-002,PROD-456,2026-01-26,49.99,US
CUST-003,PROD-789,2026-01-27,19.99,US
```

2. **Upload to S3**:
```bash
aws s3 cp sample-deliveries.csv s3://your-data-bucket/deliveries/
```

3. **Create Athena table**:
```sql
CREATE EXTERNAL TABLE ceap_data_dev.product_deliveries (
    customer_id STRING,
    product_id STRING,
    delivery_date STRING,
    order_value DOUBLE,
    marketplace STRING
)
ROW FORMAT DELIMITED
FIELDS TERMINATED BY ','
LOCATION 's3://your-data-bucket/deliveries/'
TBLPROPERTIES ('skip.header.line.count'='1');
```

4. **Configure program** (as shown in Step 2 above)

5. **Run workflow** - Should process 3 candidates!

---

## Alternative: Extend with Custom Connectors

You can create custom connectors for other data sources:

### Example: Kinesis Connector (5 minutes)

```kotlin
// ceap-connectors/src/main/kotlin/com/ceap/connectors/KinesisConnector.kt
class KinesisConnector(
    private val kinesisClient: KinesisClient
) : BaseDataConnector() {
    
    override fun getName() = "kinesis"
    
    override fun extractData(
        config: DataConnectorConfig,
        parameters: Map<String, Any>
    ): List<Map<String, Any>> {
        val streamName = config.sourceConfig?.get("streamName") as String
        val shardIterator = getShardIterator(streamName)
        val records = getRecords(shardIterator)
        return records.map { deserializeRecord(it) }
    }
    
    override fun transformToCandidate(
        rawData: Map<String, Any>,
        config: DataConnectorConfig
    ): Candidate? {
        // Same transformation logic as DataWarehouseConnector
        return super.transformToCandidate(rawData, config)
    }
}
```

### Example: S3 Connector (Direct File Reading)

```kotlin
class S3Connector(
    private val s3Client: S3Client
) : BaseDataConnector() {
    
    override fun getName() = "s3"
    
    override fun extractData(
        config: DataConnectorConfig,
        parameters: Map<String, Any>
    ): List<Map<String, Any>> {
        val bucket = config.sourceConfig?.get("bucket") as String
        val key = config.sourceConfig?.get("key") as String
        val content = s3Client.getObject(GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build())
        return parseCSV(content) // or parseJSON, parseParquet, etc.
    }
}
```

---

## Troubleshooting

### Issue: "Query execution failed"

**Check**:
- Athena database exists
- Table exists and has data
- S3 results bucket exists and Lambda has write permissions
- Query syntax is valid

**Solution**:
```bash
# Test query in Athena console first
# Or run query manually:
aws athena start-query-execution \
  --query-string "SELECT * FROM your_table LIMIT 10" \
  --query-execution-context Database=your_database \
  --result-configuration OutputLocation=s3://your-results-bucket/
```

### Issue: "Missing required fields"

**Check**: Your query returns all required fields (customerId, subjectId, subjectType, eventDate)

**Solution**: Add field mappings or update your query to include missing fields

### Issue: "No candidates extracted"

**Check**:
- Date range includes data
- Query filters aren't too restrictive
- Table has data for the specified marketplace

**Solution**: Run query directly in Athena to verify data exists

---

## Summary

**To configure a data source:**

1. ✅ Set up AWS Athena (database + table)
2. ✅ Create S3 bucket for query results
3. ✅ Write SQL query that returns required fields
4. ✅ Create program configuration JSON
5. ✅ Upload program config to DynamoDB
6. ✅ Test with Step Functions workflow

**Your data source is now connected!** The ETL Lambda will query it on each batch run.

---

## Next Steps

- **Add more programs**: Create configs for video-ratings, music-feedback, etc.
- **Set up scheduling**: EventBridge rules trigger workflows automatically
- **Monitor metrics**: CloudWatch dashboards show processing volumes
- **Configure channels**: Set up email/SMS providers for delivery

---

**Need help?** Check `docs/USE-CASES.md` for complete examples or `infrastructure/DYNAMODB_SCHEMA.md` for schema details.
