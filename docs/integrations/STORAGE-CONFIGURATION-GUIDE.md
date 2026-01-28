# Storage Configuration Guide - CEAP Platform

**Last Updated**: January 27, 2026  
**Audience**: Developers configuring storage for the CEAP platform

---

## Overview

The CEAP platform uses **Amazon DynamoDB** as its primary data store for candidates, program configurations, and engagement tracking. This guide covers how to configure DynamoDB storage, TTL settings, indexing strategies, batch writes, optimistic locking, and query patterns.

**Key Components**:
- **Candidates Table**: Stores engagement candidates
- **ProgramConfig Table**: Stores program configurations
- **EngagementHistory Table**: Tracks engagement events
- **Global Secondary Indexes (GSIs)**: Enable efficient queries
- **TTL**: Automatic data expiration

---

## Storage Architecture

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│   Candidates     │     │  ProgramConfig   │     │ EngagementHistory│
│   (Main Table)   │     │   (Main Table)   │     │   (Main Table)   │
└──────────────────┘     └──────────────────┘     └──────────────────┘
         │                        │                         │
         ▼                        ▼                         ▼
  ┌─────────────┐         ┌─────────────┐         ┌─────────────┐
  │ GSI: Status │         │ GSI: Program│         │ GSI: Customer│
  │ GSI: Score  │         │             │         │ GSI: Date    │
  └─────────────┘         └─────────────┘         └─────────────┘
```

---

## Table Schemas

### Candidates Table

**Purpose**: Store engagement candidates with scores and metadata

**Schema**:
```
PK: CUST#{customerId}
SK: PROG#{programId}#SUBJ#{subjectId}#MKT#{marketplace}

Attributes:
- customerId (String)
- programId (String)
- subjectId (String)
- subjectType (String)
- marketplace (String)
- score (Number)
- status (String): PENDING, SENT, ENGAGED, EXPIRED
- createdAt (String): ISO8601 timestamp
- updatedAt (String): ISO8601 timestamp
- expiresAt (Number): Unix timestamp for TTL
- metadata (Map): Additional data
```

### ProgramConfig Table

**Purpose**: Store program configurations

**Schema**:
```
PK: PROGRAM#{programId}
SK: MARKETPLACE#{marketplace}

Attributes:
- programId (String)
- programName (String)
- marketplace (String)
- enabled (Boolean)
- dataConnectors (String): JSON
- scoringModels (String): JSON
- channels (String): JSON
- createdAt (String)
- updatedAt (String)
```

### EngagementHistory Table

**Purpose**: Track engagement events and responses

**Schema**:
```
PK: CUST#{customerId}
SK: EVENT#{timestamp}#{eventType}

Attributes:
- customerId (String)
- programId (String)
- subjectId (String)
- eventType (String): SENT, OPENED, CLICKED, RESPONDED
- eventTimestamp (String): ISO8601
- channel (String): email, sms, push
- metadata (Map)
- expiresAt (Number): TTL
```

---

## Configuration Steps

### Step 1: Create DynamoDB Tables

#### 1.1 Create Candidates Table

```bash
# Create table with on-demand billing
aws dynamodb create-table \
  --table-name Candidates-prod \
  --attribute-definitions \
    AttributeName=PK,AttributeType=S \
    AttributeName=SK,AttributeType=S \
    AttributeName=status,AttributeType=S \
    AttributeName=score,AttributeType=N \
    AttributeName=createdAt,AttributeType=S \
  --key-schema \
    AttributeName=PK,KeyType=HASH \
    AttributeName=SK,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --tags Key=Environment,Value=prod Key=Application,Value=CEAP \
  --global-secondary-indexes '[
    {
      "IndexName": "StatusIndex",
      "KeySchema": [
        {"AttributeName": "status", "KeyType": "HASH"},
        {"AttributeName": "createdAt", "KeyType": "RANGE"}
      ],
      "Projection": {"ProjectionType": "ALL"}
    },
    {
      "IndexName": "ScoreIndex",
      "KeySchema": [
        {"AttributeName": "status", "KeyType": "HASH"},
        {"AttributeName": "score", "KeyType": "RANGE"}
      ],
      "Projection": {"ProjectionType": "ALL"}
    }
  ]'

# Enable TTL
aws dynamodb update-time-to-live \
  --table-name Candidates-prod \
  --time-to-live-specification \
    Enabled=true,AttributeName=expiresAt

# Enable Point-in-Time Recovery
aws dynamodb update-continuous-backups \
  --table-name Candidates-prod \
  --point-in-time-recovery-specification \
    PointInTimeRecoveryEnabled=true
```

#### 1.2 Create ProgramConfig Table

```bash
aws dynamodb create-table \
  --table-name ProgramConfig-prod \
  --attribute-definitions \
    AttributeName=PK,AttributeType=S \
    AttributeName=SK,AttributeType=S \
  --key-schema \
    AttributeName=PK,KeyType=HASH \
    AttributeName=SK,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --tags Key=Environment,Value=prod Key=Application,Value=CEAP
```

#### 1.3 Create EngagementHistory Table

```bash
aws dynamodb create-table \
  --table-name EngagementHistory-prod \
  --attribute-definitions \
    AttributeName=PK,AttributeType=S \
    AttributeName=SK,AttributeType=S \
    AttributeName=programId,AttributeType=S \
    AttributeName=eventTimestamp,AttributeType=S \
  --key-schema \
    AttributeName=PK,KeyType=HASH \
    AttributeName=SK,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST \
  --global-secondary-indexes '[
    {
      "IndexName": "ProgramIndex",
      "KeySchema": [
        {"AttributeName": "programId", "KeyType": "HASH"},
        {"AttributeName": "eventTimestamp", "KeyType": "RANGE"}
      ],
      "Projection": {"ProjectionType": "ALL"}
    }
  ]' \
  --tags Key=Environment,Value=prod Key=Application,Value=CEAP

# Enable TTL for automatic cleanup
aws dynamodb update-time-to-live \
  --table-name EngagementHistory-prod \
  --time-to-live-specification \
    Enabled=true,AttributeName=expiresAt
```

---

### Step 2: Configure TTL Settings

#### 2.1 TTL Configuration in Code

```json
{
  "ttlConfig": {
    "enabled": true,
    "defaultTTLDays": 30,
    "tableTTLs": {
      "Candidates": {
        "ttlDays": 30,
        "ttlAttribute": "expiresAt"
      },
      "EngagementHistory": {
        "ttlDays": 90,
        "ttlAttribute": "expiresAt"
      }
    }
  }
}
```

#### 2.2 Calculate TTL in Application

```kotlin
// Calculate expiration timestamp
fun calculateExpiresAt(ttlDays: Int): Long {
    return Instant.now()
        .plus(ttlDays.toLong(), ChronoUnit.DAYS)
        .epochSecond
}

// Set TTL when creating item
val item = mapOf(
    "PK" to AttributeValue.builder().s("CUST#12345").build(),
    "SK" to AttributeValue.builder().s("PROG#reviews#SUBJ#prod-123").build(),
    "expiresAt" to AttributeValue.builder().n(calculateExpiresAt(30).toString()).build()
)
```

---

### Step 3: Configure Indexing Strategies

#### 3.1 Query by Status and Creation Date

**Use Case**: Get all pending candidates ordered by creation time

**GSI Configuration**:
```json
{
  "IndexName": "StatusIndex",
  "KeySchema": [
    {"AttributeName": "status", "KeyType": "HASH"},
    {"AttributeName": "createdAt", "KeyType": "RANGE"}
  ],
  "Projection": {"ProjectionType": "ALL"}
}
```

**Query Example**:
```bash
aws dynamodb query \
  --table-name Candidates-prod \
  --index-name StatusIndex \
  --key-condition-expression "status = :status AND createdAt > :date" \
  --expression-attribute-values '{
    ":status": {"S": "PENDING"},
    ":date": {"S": "2026-01-20T00:00:00Z"}
  }'
```

#### 3.2 Query by Score (Top Candidates)

**Use Case**: Get highest-scoring pending candidates

**GSI Configuration**:
```json
{
  "IndexName": "ScoreIndex",
  "KeySchema": [
    {"AttributeName": "status", "KeyType": "HASH"},
    {"AttributeName": "score", "KeyType": "RANGE"}
  ],
  "Projection": {"ProjectionType": "ALL"}
}
```

**Query Example**:
```bash
aws dynamodb query \
  --table-name Candidates-prod \
  --index-name ScoreIndex \
  --key-condition-expression "status = :status AND score > :minScore" \
  --expression-attribute-values '{
    ":status": {"S": "PENDING"},
    ":minScore": {"N": "80"}
  }' \
  --scan-index-forward false \
  --limit 100
```

#### 3.3 Sparse Index for Active Items

**Use Case**: Only index items with specific status

**Configuration**:
```json
{
  "IndexName": "ActiveCandidatesIndex",
  "KeySchema": [
    {"AttributeName": "activeFlag", "KeyType": "HASH"},
    {"AttributeName": "score", "KeyType": "RANGE"}
  ],
  "Projection": {"ProjectionType": "KEYS_ONLY"}
}
```

**Note**: Only set `activeFlag` attribute for active candidates to create sparse index

---

### Step 4: Configure Batch Writes

#### 4.1 Batch Write Configuration

```json
{
  "batchWriteConfig": {
    "batchSize": 25,
    "maxRetries": 3,
    "retryDelayMs": 100,
    "exponentialBackoff": true,
    "unprocessedItemsHandling": "retry",
    "parallelBatches": 5
  }
}
```

#### 4.2 Batch Write Implementation

```kotlin
// ceap-storage/src/main/kotlin/com/ceap/storage/BatchWriter.kt
class BatchWriter(
    private val dynamoDbClient: DynamoDbClient,
    private val config: BatchWriteConfig
) {
    
    suspend fun writeBatch(
        tableName: String,
        items: List<Map<String, AttributeValue>>
    ): BatchWriteResult = coroutineScope {
        val batches = items.chunked(config.batchSize)
        val results = mutableListOf<WriteResult>()
        
        batches.chunked(config.parallelBatches).forEach { batchGroup ->
            val groupResults = batchGroup.map { batch ->
                async {
                    writeSingleBatch(tableName, batch)
                }
            }.awaitAll()
            
            results.addAll(groupResults)
        }
        
        BatchWriteResult(
            totalItems = items.size,
            successful = results.sumOf { it.successful },
            failed = results.sumOf { it.failed }
        )
    }
    
    private suspend fun writeSingleBatch(
        tableName: String,
        items: List<Map<String, AttributeValue>>
    ): WriteResult {
        val writeRequests = items.map { item ->
            WriteRequest.builder()
                .putRequest(PutRequest.builder().item(item).build())
                .build()
        }
        
        var unprocessedItems = writeRequests
        var retries = 0
        
        while (unprocessedItems.isNotEmpty() && retries < config.maxRetries) {
            val response = dynamoDbClient.batchWriteItem {
                requestItems = mapOf(tableName to unprocessedItems)
            }
            
            unprocessedItems = response.unprocessedItems()[tableName] ?: emptyList()
            
            if (unprocessedItems.isNotEmpty()) {
                delay(config.retryDelayMs * (1 shl retries).toLong())
                retries++
            }
        }
        
        return WriteResult(
            successful = items.size - unprocessedItems.size,
            failed = unprocessedItems.size
        )
    }
}

data class BatchWriteResult(
    val totalItems: Int,
    val successful: Int,
    val failed: Int
)

data class WriteResult(
    val successful: Int,
    val failed: Int
)
```

---

### Step 5: Configure Optimistic Locking

#### 5.1 Version-Based Locking

```kotlin
// Add version attribute to items
data class Candidate(
    val customerId: String,
    val programId: String,
    val subjectId: String,
    val score: Double,
    val status: String,
    val version: Int = 1,
    val updatedAt: String
)

// Update with version check
suspend fun updateWithOptimisticLock(
    candidate: Candidate
): UpdateResult {
    return try {
        dynamoDbClient.updateItem {
            tableName = "Candidates-prod"
            key = mapOf(
                "PK" to AttributeValue.builder().s("CUST#${candidate.customerId}").build(),
                "SK" to AttributeValue.builder().s("PROG#${candidate.programId}#SUBJ#${candidate.subjectId}").build()
            )
            updateExpression = "SET #status = :status, #version = :newVersion, #updatedAt = :updatedAt"
            conditionExpression = "#version = :currentVersion"
            expressionAttributeNames = mapOf(
                "#status" to "status",
                "#version" to "version",
                "#updatedAt" to "updatedAt"
            )
            expressionAttributeValues = mapOf(
                ":status" to AttributeValue.builder().s(candidate.status).build(),
                ":currentVersion" to AttributeValue.builder().n(candidate.version.toString()).build(),
                ":newVersion" to AttributeValue.builder().n((candidate.version + 1).toString()).build(),
                ":updatedAt" to AttributeValue.builder().s(Instant.now().toString()).build()
            )
        }
        UpdateResult.success()
    } catch (e: ConditionalCheckFailedException) {
        UpdateResult.conflict("Version mismatch - item was modified")
    }
}
```

#### 5.2 Timestamp-Based Locking

```kotlin
// Update only if not modified since last read
suspend fun updateIfNotModified(
    candidate: Candidate,
    lastReadTimestamp: String
): UpdateResult {
    return try {
        dynamoDbClient.updateItem {
            tableName = "Candidates-prod"
            key = buildKey(candidate)
            updateExpression = "SET #status = :status, #updatedAt = :updatedAt"
            conditionExpression = "#updatedAt = :lastRead"
            expressionAttributeNames = mapOf(
                "#status" to "status",
                "#updatedAt" to "updatedAt"
            )
            expressionAttributeValues = mapOf(
                ":status" to AttributeValue.builder().s(candidate.status).build(),
                ":updatedAt" to AttributeValue.builder().s(Instant.now().toString()).build(),
                ":lastRead" to AttributeValue.builder().s(lastReadTimestamp).build()
            )
        }
        UpdateResult.success()
    } catch (e: ConditionalCheckFailedException) {
        UpdateResult.conflict("Item was modified since last read")
    }
}
```

---

### Step 6: Configure Query Patterns

#### 6.1 Single Item Retrieval

```kotlin
// Get specific candidate
suspend fun getCandidate(
    customerId: String,
    programId: String,
    subjectId: String
): Candidate? {
    val response = dynamoDbClient.getItem {
        tableName = "Candidates-prod"
        key = mapOf(
            "PK" to AttributeValue.builder().s("CUST#$customerId").build(),
            "SK" to AttributeValue.builder().s("PROG#$programId#SUBJ#$subjectId").build()
        )
        consistentRead = false
    }
    
    return response.item()?.let { parseCandidate(it) }
}
```

#### 6.2 Query by Customer

```kotlin
// Get all candidates for a customer
suspend fun getCandidatesByCustomer(
    customerId: String,
    limit: Int = 100
): List<Candidate> {
    val response = dynamoDbClient.query {
        tableName = "Candidates-prod"
        keyConditionExpression = "PK = :pk"
        expressionAttributeValues = mapOf(
            ":pk" to AttributeValue.builder().s("CUST#$customerId").build()
        )
        limit = limit
    }
    
    return response.items().map { parseCandidate(it) }
}
```

#### 6.3 Query with Pagination

```kotlin
// Paginated query
suspend fun getPendingCandidates(
    pageSize: Int = 100,
    lastEvaluatedKey: Map<String, AttributeValue>? = null
): PaginatedResult<Candidate> {
    val response = dynamoDbClient.query {
        tableName = "Candidates-prod"
        indexName = "StatusIndex"
        keyConditionExpression = "status = :status"
        expressionAttributeValues = mapOf(
            ":status" to AttributeValue.builder().s("PENDING").build()
        )
        limit = pageSize
        lastEvaluatedKey?.let { exclusiveStartKey = it }
    }
    
    return PaginatedResult(
        items = response.items().map { parseCandidate(it) },
        lastEvaluatedKey = response.lastEvaluatedKey(),
        hasMore = response.lastEvaluatedKey()?.isNotEmpty() == true
    )
}

data class PaginatedResult<T>(
    val items: List<T>,
    val lastEvaluatedKey: Map<String, AttributeValue>?,
    val hasMore: Boolean
)
```

#### 6.4 Query with Filters

```kotlin
// Query with filter expression
suspend fun getHighScoreCandidates(
    status: String,
    minScore: Double,
    marketplace: String
): List<Candidate> {
    val response = dynamoDbClient.query {
        tableName = "Candidates-prod"
        indexName = "ScoreIndex"
        keyConditionExpression = "status = :status AND score > :minScore"
        filterExpression = "marketplace = :marketplace"
        expressionAttributeValues = mapOf(
            ":status" to AttributeValue.builder().s(status).build(),
            ":minScore" to AttributeValue.builder().n(minScore.toString()).build(),
            ":marketplace" to AttributeValue.builder().s(marketplace).build()
        )
        scanIndexForward = false
    }
    
    return response.items().map { parseCandidate(it) }
}
```

#### 6.5 Batch Get Items

```kotlin
// Get multiple items by key
suspend fun getCandidatesBatch(
    keys: List<Pair<String, String>>
): List<Candidate> {
    val keysAndAttributes = KeysAndAttributes.builder()
        .keys(keys.map { (customerId, sortKey) ->
            mapOf(
                "PK" to AttributeValue.builder().s("CUST#$customerId").build(),
                "SK" to AttributeValue.builder().s(sortKey).build()
            )
        })
        .build()
    
    val response = dynamoDbClient.batchGetItem {
        requestItems = mapOf("Candidates-prod" to keysAndAttributes)
    }
    
    return response.responses()["Candidates-prod"]
        ?.map { parseCandidate(it) }
        ?: emptyList()
}
```

---

## AWS CLI Commands

### Query Examples

```bash
# Get item by key
aws dynamodb get-item \
  --table-name Candidates-prod \
  --key '{
    "PK": {"S": "CUST#12345"},
    "SK": {"S": "PROG#reviews#SUBJ#prod-123#MKT#US"}
  }'

# Query by partition key
aws dynamodb query \
  --table-name Candidates-prod \
  --key-condition-expression "PK = :pk" \
  --expression-attribute-values '{
    ":pk": {"S": "CUST#12345"}
  }'

# Query GSI with filter
aws dynamodb query \
  --table-name Candidates-prod \
  --index-name StatusIndex \
  --key-condition-expression "status = :status" \
  --filter-expression "score > :minScore" \
  --expression-attribute-values '{
    ":status": {"S": "PENDING"},
    ":minScore": {"N": "75"}
  }'

# Scan with filter (use sparingly)
aws dynamodb scan \
  --table-name Candidates-prod \
  --filter-expression "marketplace = :mkt AND createdAt > :date" \
  --expression-attribute-values '{
    ":mkt": {"S": "US"},
    ":date": {"S": "2026-01-20T00:00:00Z"}
  }' \
  --max-items 100
```

### Update Examples

```bash
# Update item
aws dynamodb update-item \
  --table-name Candidates-prod \
  --key '{
    "PK": {"S": "CUST#12345"},
    "SK": {"S": "PROG#reviews#SUBJ#prod-123#MKT#US"}
  }' \
  --update-expression "SET #status = :status, #updatedAt = :time" \
  --expression-attribute-names '{
    "#status": "status",
    "#updatedAt": "updatedAt"
  }' \
  --expression-attribute-values '{
    ":status": {"S": "SENT"},
    ":time": {"S": "2026-01-27T12:00:00Z"}
  }'

# Conditional update
aws dynamodb update-item \
  --table-name Candidates-prod \
  --key '{
    "PK": {"S": "CUST#12345"},
    "SK": {"S": "PROG#reviews#SUBJ#prod-123#MKT#US"}
  }' \
  --update-expression "SET #status = :newStatus" \
  --condition-expression "#status = :currentStatus" \
  --expression-attribute-names '{"#status": "status"}' \
  --expression-attribute-values '{
    ":newStatus": {"S": "SENT"},
    ":currentStatus": {"S": "PENDING"}
  }'

# Increment counter
aws dynamodb update-item \
  --table-name Candidates-prod \
  --key '{
    "PK": {"S": "CUST#12345"},
    "SK": {"S": "PROG#reviews#SUBJ#prod-123#MKT#US"}
  }' \
  --update-expression "SET attemptCount = attemptCount + :inc" \
  --expression-attribute-values '{":inc": {"N": "1"}}'
```

### Batch Operations

```bash
# Batch write
aws dynamodb batch-write-item \
  --request-items file://batch-write.json

# batch-write.json:
{
  "Candidates-prod": [
    {
      "PutRequest": {
        "Item": {
          "PK": {"S": "CUST#12345"},
          "SK": {"S": "PROG#reviews#SUBJ#prod-123#MKT#US"},
          "score": {"N": "85"},
          "status": {"S": "PENDING"}
        }
      }
    },
    {
      "PutRequest": {
        "Item": {
          "PK": {"S": "CUST#67890"},
          "SK": {"S": "PROG#reviews#SUBJ#prod-456#MKT#US"},
          "score": {"N": "92"},
          "status": {"S": "PENDING"}
        }
      }
    }
  ]
}

# Batch get
aws dynamodb batch-get-item \
  --request-items '{
    "Candidates-prod": {
      "Keys": [
        {
          "PK": {"S": "CUST#12345"},
          "SK": {"S": "PROG#reviews#SUBJ#prod-123#MKT#US"}
        },
        {
          "PK": {"S": "CUST#67890"},
          "SK": {"S": "PROG#reviews#SUBJ#prod-456#MKT#US"}
        }
      ]
    }
  }'
```

---

## Testing Procedures

### Test 1: Write and Read Item

```bash
# Write item
aws dynamodb put-item \
  --table-name Candidates-prod \
  --item '{
    "PK": {"S": "CUST#TEST-001"},
    "SK": {"S": "PROG#test#SUBJ#test-123#MKT#US"},
    "customerId": {"S": "TEST-001"},
    "programId": {"S": "test"},
    "subjectId": {"S": "test-123"},
    "score": {"N": "75"},
    "status": {"S": "PENDING"},
    "createdAt": {"S": "2026-01-27T12:00:00Z"},
    "expiresAt": {"N": "1738022400"}
  }'

# Read item
aws dynamodb get-item \
  --table-name Candidates-prod \
  --key '{
    "PK": {"S": "CUST#TEST-001"},
    "SK": {"S": "PROG#test#SUBJ#test-123#MKT#US"}
  }'
```

### Test 2: Test GSI Query

```bash
# Query by status
aws dynamodb query \
  --table-name Candidates-prod \
  --index-name StatusIndex \
  --key-condition-expression "status = :status" \
  --expression-attribute-values '{":status": {"S": "PENDING"}}' \
  --limit 10
```

### Test 3: Test TTL

```bash
# Create item with short TTL (expires in 1 hour)
EXPIRES_AT=$(($(date +%s) + 3600))

aws dynamodb put-item \
  --table-name Candidates-prod \
  --item "{
    \"PK\": {\"S\": \"CUST#TTL-TEST\"},
    \"SK\": {\"S\": \"PROG#test#SUBJ#ttl-test#MKT#US\"},
    \"status\": {\"S\": \"PENDING\"},
    \"expiresAt\": {\"N\": \"$EXPIRES_AT\"}
  }"

# Check TTL status
aws dynamodb describe-time-to-live \
  --table-name Candidates-prod
```

### Test 4: Test Batch Write

```bash
# Create test batch file
cat > test-batch.json << 'EOF'
{
  "Candidates-prod": [
    {
      "PutRequest": {
        "Item": {
          "PK": {"S": "CUST#BATCH-001"},
          "SK": {"S": "PROG#test#SUBJ#batch-1#MKT#US"},
          "status": {"S": "PENDING"},
          "score": {"N": "80"}
        }
      }
    },
    {
      "PutRequest": {
        "Item": {
          "PK": {"S": "CUST#BATCH-002"},
          "SK": {"S": "PROG#test#SUBJ#batch-2#MKT#US"},
          "status": {"S": "PENDING"},
          "score": {"N": "85"}
        }
      }
    }
  ]
}
EOF

# Execute batch write
aws dynamodb batch-write-item --request-items file://test-batch.json
```

### Test 5: Test Optimistic Locking

```bash
# Get current version
ITEM=$(aws dynamodb get-item \
  --table-name Candidates-prod \
  --key '{
    "PK": {"S": "CUST#TEST-001"},
    "SK": {"S": "PROG#test#SUBJ#test-123#MKT#US"}
  }' \
  --output json)

VERSION=$(echo $ITEM | jq -r '.Item.version.N // "1"')

# Update with version check
aws dynamodb update-item \
  --table-name Candidates-prod \
  --key '{
    "PK": {"S": "CUST#TEST-001"},
    "SK": {"S": "PROG#test#SUBJ#test-123#MKT#US"}
  }' \
  --update-expression "SET #status = :status, #version = :newVersion" \
  --condition-expression "#version = :currentVersion" \
  --expression-attribute-names '{
    "#status": "status",
    "#version": "version"
  }' \
  --expression-attribute-values "{
    \":status\": {\"S\": \"SENT\"},
    \":currentVersion\": {\"N\": \"$VERSION\"},
    \":newVersion\": {\"N\": \"$((VERSION + 1))\"}
  }"
```

---

## Troubleshooting

### Issue: "ProvisionedThroughputExceededException"

**Symptoms**: Throttling errors during high traffic

**Solutions**:
1. Switch to on-demand billing mode
2. Increase provisioned capacity
3. Implement exponential backoff
4. Use batch operations

```bash
# Switch to on-demand
aws dynamodb update-table \
  --table-name Candidates-prod \
  --billing-mode PAY_PER_REQUEST

# Or increase provisioned capacity
aws dynamodb update-table \
  --table-name Candidates-prod \
  --provisioned-throughput ReadCapacityUnits=100,WriteCapacityUnits=100
```

### Issue: "ConditionalCheckFailedException"

**Symptoms**: Optimistic locking failures

**Solutions**:
1. Implement retry logic with exponential backoff
2. Reduce concurrent updates to same item
3. Use transactions for atomic operations

```kotlin
suspend fun updateWithRetry(
    candidate: Candidate,
    maxRetries: Int = 3
): UpdateResult {
    repeat(maxRetries) { attempt ->
        val result = updateWithOptimisticLock(candidate)
        if (result.isSuccess) return result
        
        // Exponential backoff
        delay(100L * (1 shl attempt))
        
        // Refresh candidate data
        val refreshed = getCandidate(candidate.customerId, candidate.programId, candidate.subjectId)
        if (refreshed != null) {
            return updateWithOptimisticLock(refreshed.copy(status = candidate.status))
        }
    }
    return UpdateResult.failure("Max retries exceeded")
}
```

### Issue: "Item size exceeds 400KB limit"

**Symptoms**: Items too large to store

**Solutions**:
1. Store large attributes in S3, reference in DynamoDB
2. Compress large text fields
3. Split data across multiple items
4. Use document compression

```kotlin
// Store large data in S3
suspend fun storeLargeCandidate(
    candidate: Candidate,
    largeMetadata: String
): String {
    // Upload to S3
    val s3Key = "candidates/${candidate.customerId}/${candidate.programId}/${UUID.randomUUID()}.json"
    s3Client.putObject {
        bucket = "ceap-large-data-prod"
        key = s3Key
        body = largeMetadata.toByteArray()
    }
    
    // Store reference in DynamoDB
    dynamoDbClient.putItem {
        tableName = "Candidates-prod"
        item = candidate.toDynamoDbItem().plus(
            "metadataS3Key" to AttributeValue.builder().s(s3Key).build()
        )
    }
    
    return s3Key
}
```

### Issue: "Hot partition"

**Symptoms**: Uneven distribution of requests

**Solutions**:
1. Add randomness to partition key
2. Use composite keys
3. Implement write sharding
4. Review access patterns

```kotlin
// Add shard suffix to distribute writes
fun buildShardedKey(customerId: String, shardCount: Int = 10): String {
    val shard = customerId.hashCode().absoluteValue % shardCount
    return "CUST#$customerId#SHARD#$shard"
}
```

### Issue: "GSI throttling"

**Symptoms**: GSI queries being throttled

**Solutions**:
1. Ensure GSI has adequate capacity
2. Use sparse indexes to reduce item count
3. Implement query result caching
4. Review projection type (KEYS_ONLY vs ALL)

```bash
# Check GSI metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/DynamoDB \
  --metric-name ConsumedReadCapacityUnits \
  --dimensions Name=TableName,Value=Candidates-prod Name=GlobalSecondaryIndexName,Value=StatusIndex \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 300 \
  --statistics Sum
```

---

## Real-World Use Cases

### Use Case 1: High-Volume Candidate Storage

**Scenario**: Store millions of candidates daily

**Configuration**:
```json
{
  "tableConfig": {
    "billingMode": "PAY_PER_REQUEST",
    "ttlEnabled": true,
    "ttlDays": 30,
    "pointInTimeRecovery": true
  },
  "batchWriteConfig": {
    "batchSize": 25,
    "parallelBatches": 10,
    "maxRetries": 3
  }
}
```

**Implementation**:
```kotlin
suspend fun storeHighVolumeCandidates(
    candidates: List<Candidate>
): BatchWriteResult {
    return batchWriter.writeBatch(
        tableName = "Candidates-prod",
        items = candidates.map { it.toDynamoDbItem() }
    )
}
```

### Use Case 2: Real-Time Status Updates

**Scenario**: Update candidate status as engagement events occur

**Configuration**:
```kotlin
suspend fun updateCandidateStatus(
    customerId: String,
    programId: String,
    subjectId: String,
    newStatus: String,
    eventMetadata: Map<String, Any>
): UpdateResult {
    return dynamoDbClient.updateItem {
        tableName = "Candidates-prod"
        key = buildKey(customerId, programId, subjectId)
        updateExpression = "SET #status = :status, #updatedAt = :time, #metadata = :metadata"
        expressionAttributeNames = mapOf(
            "#status" to "status",
            "#updatedAt" to "updatedAt",
            "#metadata" to "metadata"
        )
        expressionAttributeValues = mapOf(
            ":status" to AttributeValue.builder().s(newStatus).build(),
            ":time" to AttributeValue.builder().s(Instant.now().toString()).build(),
            ":metadata" to AttributeValue.builder().m(
                eventMetadata.mapValues { (_, v) ->
                    AttributeValue.builder().s(v.toString()).build()
                }
            ).build()
        )
    }
    UpdateResult.success()
}
```

### Use Case 3: Engagement History Tracking

**Scenario**: Track all engagement events for analytics

**Configuration**:
```kotlin
suspend fun recordEngagementEvent(
    customerId: String,
    programId: String,
    subjectId: String,
    eventType: String,
    channel: String,
    metadata: Map<String, Any>
) {
    val timestamp = Instant.now()
    val expiresAt = timestamp.plus(90, ChronoUnit.DAYS).epochSecond
    
    dynamoDbClient.putItem {
        tableName = "EngagementHistory-prod"
        item = mapOf(
            "PK" to AttributeValue.builder().s("CUST#$customerId").build(),
            "SK" to AttributeValue.builder().s("EVENT#${timestamp.toEpochMilli()}#$eventType").build(),
            "customerId" to AttributeValue.builder().s(customerId).build(),
            "programId" to AttributeValue.builder().s(programId).build(),
            "subjectId" to AttributeValue.builder().s(subjectId).build(),
            "eventType" to AttributeValue.builder().s(eventType).build(),
            "eventTimestamp" to AttributeValue.builder().s(timestamp.toString()).build(),
            "channel" to AttributeValue.builder().s(channel).build(),
            "metadata" to AttributeValue.builder().m(
                metadata.mapValues { (_, v) ->
                    AttributeValue.builder().s(v.toString()).build()
                }
            ).build(),
            "expiresAt" to AttributeValue.builder().n(expiresAt.toString()).build()
        )
    }
}
```

### Use Case 4: Top Candidates Query

**Scenario**: Get highest-scoring candidates for prioritization

**Configuration**:
```kotlin
suspend fun getTopCandidates(
    status: String = "PENDING",
    limit: Int = 100,
    minScore: Double = 80.0
): List<Candidate> {
    val response = dynamoDbClient.query {
        tableName = "Candidates-prod"
        indexName = "ScoreIndex"
        keyConditionExpression = "status = :status AND score >= :minScore"
        expressionAttributeValues = mapOf(
            ":status" to AttributeValue.builder().s(status).build(),
            ":minScore" to AttributeValue.builder().n(minScore.toString()).build()
        )
        scanIndexForward = false  // Descending order
        limit = limit
    }
    
    return response.items().map { parseCandidate(it) }
}
```

---

## Performance Optimization Tips

### 1. Use Batch Operations

```kotlin
// Instead of individual writes
candidates.forEach { candidate ->
    dynamoDbClient.putItem { /* ... */ }  // ❌ Slow
}

// Use batch writes
batchWriter.writeBatch("Candidates-prod", candidates.map { it.toDynamoDbItem() })  // ✅ Fast
```

### 2. Implement Caching

```kotlin
class CachedDynamoDbClient(
    private val dynamoDbClient: DynamoDbClient,
    private val cacheClient: CacheClient
) {
    suspend fun getItem(key: Map<String, AttributeValue>): Map<String, AttributeValue>? {
        val cacheKey = key.toString()
        
        // Check cache first
        cacheClient.get(cacheKey)?.let { cached ->
            return Json.decodeFromString(cached)
        }
        
        // Fetch from DynamoDB
        val response = dynamoDbClient.getItem {
            tableName = "Candidates-prod"
            this.key = key
        }
        
        // Cache result
        response.item()?.let { item ->
            cacheClient.set(cacheKey, Json.encodeToString(item), ttl = 300)
        }
        
        return response.item()
    }
}
```

### 3. Use Projection Expressions

```bash
# Only fetch needed attributes
aws dynamodb query \
  --table-name Candidates-prod \
  --key-condition-expression "PK = :pk" \
  --projection-expression "customerId, score, status" \
  --expression-attribute-values '{":pk": {"S": "CUST#12345"}}'
```

### 4. Optimize GSI Design

```json
{
  "IndexName": "StatusScoreIndex",
  "KeySchema": [
    {"AttributeName": "status", "KeyType": "HASH"},
    {"AttributeName": "score", "KeyType": "RANGE"}
  ],
  "Projection": {
    "ProjectionType": "INCLUDE",
    "NonKeyAttributes": ["customerId", "programId", "subjectId"]
  }
}
```

---

## Monitoring and Metrics

### Key Metrics to Track

```bash
# Read/Write capacity
aws cloudwatch get-metric-statistics \
  --namespace AWS/DynamoDB \
  --metric-name ConsumedReadCapacityUnits \
  --dimensions Name=TableName,Value=Candidates-prod \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 300 \
  --statistics Sum,Average

# Throttled requests
aws cloudwatch get-metric-statistics \
  --namespace AWS/DynamoDB \
  --metric-name ThrottledRequests \
  --dimensions Name=TableName,Value=Candidates-prod \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 300 \
  --statistics Sum

# Item count
aws dynamodb describe-table \
  --table-name Candidates-prod \
  --query 'Table.ItemCount'

# Table size
aws dynamodb describe-table \
  --table-name Candidates-prod \
  --query 'Table.TableSizeBytes'
```

---

## Summary

**Storage Configuration Checklist**:

1. ✅ Create DynamoDB tables with appropriate schemas
2. ✅ Configure TTL for automatic data expiration
3. ✅ Set up GSIs for efficient queries
4. ✅ Implement batch write operations
5. ✅ Configure optimistic locking for concurrent updates
6. ✅ Design efficient query patterns
7. ✅ Enable Point-in-Time Recovery
8. ✅ Monitor capacity and performance metrics

**Your storage layer is now configured!**

---

## Next Steps

- **Configure Notifications**: See `NOTIFICATION-CONFIGURATION-GUIDE.md`
- **Optimize Queries**: Review access patterns and GSI usage
- **Set Up Backups**: Configure automated backups
- **Monitor Costs**: Track capacity usage and optimize billing

---

**Need help?** Check `docs/TROUBLESHOOTING.md` or review `infrastructure/DYNAMODB_SCHEMA.md` for schema details.
