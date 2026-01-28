# Scoring Configuration Guide - CEAP Platform

**Last Updated**: January 27, 2026  
**Audience**: Developers configuring ML scoring models for the CEAP platform

---

## Overview

The CEAP platform's **Scoring System** uses machine learning models to predict customer engagement likelihood and prioritize candidates. This guide covers how to configure ML scoring models (SageMaker, Bedrock, custom models), feature stores, score caching, fallback strategies, and A/B testing.

**Key Components**:
- **Scoring Lambda**: Orchestrates model inference
- **Model Adapters**: Interface with different ML platforms
- **Feature Store**: Provides model features
- **Score Cache**: Reduces inference costs
- **A/B Testing**: Compare model performance

---

## Scoring Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Candidate  │────▶│   Feature    │────▶│    Model     │────▶│    Score     │
│              │     │  Extraction  │     │  Inference   │     │   Storage    │
└──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
       │                    │                     │                    │
       ▼                    ▼                     ▼                    ▼
  Load Data          Get Features         Call Model           Cache Result
  Check Cache        Transform Data       Apply Fallback       Update Metrics
  Batch Process      Validate Schema      Handle Errors        Track Performance
```

---

## Supported Model Types

### 1. Amazon SageMaker Models
- Real-time endpoints
- Batch transform jobs
- Multi-model endpoints
- Serverless inference

### 2. Amazon Bedrock Models
- Foundation models (Claude, Titan, etc.)
- Custom fine-tuned models
- Prompt-based scoring

### 3. Custom Models
- Lambda-hosted models
- External API endpoints
- Rule-based scoring

---

## Configuration Steps

### Step 1: Configure SageMaker Model

#### 1.1 Basic SageMaker Configuration

Create a scoring configuration file `scoring-config.json`:

```json
{
  "scoringModels": [
    {
      "modelId": "review-propensity-v1",
      "modelType": "sagemaker",
      "enabled": true,
      "weight": 1.0,
      "config": {
        "endpointName": "ceap-review-propensity-prod",
        "region": "us-east-1",
        "contentType": "application/json",
        "acceptType": "application/json",
        "timeout": 30000,
        "maxRetries": 3
      }
    }
  ]
}
```

#### 1.2 Advanced SageMaker Configuration

```json
{
  "scoringModels": [
    {
      "modelId": "engagement-score-v2",
      "modelType": "sagemaker",
      "enabled": true,
      "weight": 0.7,
      "config": {
        "endpointName": "ceap-engagement-model-prod",
        "region": "us-east-1",
        "contentType": "application/json",
        "acceptType": "application/json",
        "timeout": 30000,
        "maxRetries": 3,
        "batchSize": 100,
        "inferenceComponentName": "engagement-component",
        "customAttributes": {
          "model_version": "2.1",
          "feature_set": "standard"
        }
      },
      "featureConfig": {
        "featureStoreEnabled": true,
        "featureGroupName": "ceap-customer-features",
        "features": [
          "customer_lifetime_value",
          "purchase_frequency",
          "avg_order_value",
          "days_since_last_purchase",
          "product_category_affinity"
        ]
      },
      "caching": {
        "enabled": true,
        "ttl": 3600,
        "cacheKey": ["customerId", "subjectId", "modelVersion"]
      }
    }
  ]
}
```

---

### Step 2: Configure Bedrock Model

#### 2.1 Basic Bedrock Configuration

```json
{
  "scoringModels": [
    {
      "modelId": "sentiment-analyzer",
      "modelType": "bedrock",
      "enabled": true,
      "weight": 0.5,
      "config": {
        "modelArn": "arn:aws:bedrock:us-east-1::foundation-model/anthropic.claude-v2",
        "region": "us-east-1",
        "temperature": 0.3,
        "maxTokens": 100,
        "topP": 0.9
      }
    }
  ]
}
```

#### 2.2 Bedrock with Prompt Template

```json
{
  "scoringModels": [
    {
      "modelId": "engagement-predictor",
      "modelType": "bedrock",
      "enabled": true,
      "weight": 0.6,
      "config": {
        "modelArn": "arn:aws:bedrock:us-east-1::foundation-model/anthropic.claude-v2",
        "region": "us-east-1",
        "temperature": 0.5,
        "maxTokens": 200,
        "promptTemplate": "Analyze customer engagement likelihood:\nCustomer ID: {customerId}\nProduct: {productName}\nOrder Value: ${orderValue}\nPrevious Purchases: {purchaseCount}\n\nPredict engagement score (0-100) and provide reasoning.",
        "responseParser": {
          "type": "json",
          "scoreField": "engagement_score",
          "reasoningField": "reasoning"
        }
      },
      "caching": {
        "enabled": true,
        "ttl": 7200
      }
    }
  ]
}
```

---

### Step 3: Configure Custom Model

#### 3.1 Lambda-Hosted Model

```json
{
  "scoringModels": [
    {
      "modelId": "custom-rule-based",
      "modelType": "custom",
      "enabled": true,
      "weight": 0.3,
      "config": {
        "type": "lambda",
        "functionName": "CeapCustomScoringModel-prod",
        "region": "us-east-1",
        "timeout": 10000,
        "payload": {
          "modelVersion": "1.0",
          "scoringType": "rule-based"
        }
      }
    }
  ]
}
```

#### 3.2 External API Model

```json
{
  "scoringModels": [
    {
      "modelId": "external-ml-service",
      "modelType": "custom",
      "enabled": true,
      "weight": 0.8,
      "config": {
        "type": "api",
        "endpoint": "https://ml-api.example.com/score",
        "method": "POST",
        "headers": {
          "Authorization": "Bearer ${API_KEY}",
          "Content-Type": "application/json"
        },
        "timeout": 5000,
        "retryPolicy": {
          "maxRetries": 3,
          "backoffMultiplier": 2
        }
      }
    }
  ]
}
```

---

### Step 4: Configure Feature Store

#### 4.1 SageMaker Feature Store Integration

```json
{
  "featureStoreConfig": {
    "enabled": true,
    "type": "sagemaker",
    "featureGroupName": "ceap-customer-features-prod",
    "region": "us-east-1",
    "recordIdentifierFeatureName": "customer_id",
    "eventTimeFeatureName": "event_time",
    "features": [
      {
        "name": "customer_lifetime_value",
        "type": "Fractional",
        "required": true
      },
      {
        "name": "purchase_frequency",
        "type": "Integral",
        "required": true
      },
      {
        "name": "avg_order_value",
        "type": "Fractional",
        "required": true
      },
      {
        "name": "days_since_last_purchase",
        "type": "Integral",
        "required": false
      }
    ],
    "caching": {
      "enabled": true,
      "ttl": 1800
    }
  }
}
```

#### 4.2 DynamoDB Feature Store

```json
{
  "featureStoreConfig": {
    "enabled": true,
    "type": "dynamodb",
    "tableName": "CustomerFeatures-prod",
    "region": "us-east-1",
    "keySchema": {
      "partitionKey": "customerId",
      "sortKey": "featureTimestamp"
    },
    "features": [
      "lifetimeValue",
      "purchaseFrequency",
      "avgOrderValue",
      "categoryPreferences",
      "engagementHistory"
    ],
    "caching": {
      "enabled": true,
      "ttl": 3600
    }
  }
}
```

---

### Step 5: Configure Score Caching

#### 5.1 ElastiCache Redis Configuration

```json
{
  "scoreCaching": {
    "enabled": true,
    "provider": "elasticache",
    "config": {
      "clusterEndpoint": "ceap-score-cache.abc123.0001.use1.cache.amazonaws.com:6379",
      "ttl": 3600,
      "keyPrefix": "score:",
      "keyPattern": "{programId}:{customerId}:{subjectId}:{modelId}",
      "compression": true,
      "serialization": "json"
    },
    "invalidation": {
      "enabled": true,
      "triggers": ["customer_profile_update", "model_version_change"]
    }
  }
}
```

#### 5.2 DynamoDB Caching

```json
{
  "scoreCaching": {
    "enabled": true,
    "provider": "dynamodb",
    "config": {
      "tableName": "ScoreCache-prod",
      "ttl": 7200,
      "ttlAttribute": "expiresAt",
      "keySchema": {
        "partitionKey": "cacheKey",
        "sortKey": "modelId"
      }
    }
  }
}
```

---

### Step 6: Configure Fallback Strategies

#### 6.1 Multi-Model Fallback

```json
{
  "fallbackConfig": {
    "enabled": true,
    "strategy": "cascade",
    "models": [
      {
        "modelId": "primary-sagemaker-model",
        "priority": 1,
        "timeout": 5000
      },
      {
        "modelId": "backup-bedrock-model",
        "priority": 2,
        "timeout": 10000
      },
      {
        "modelId": "rule-based-fallback",
        "priority": 3,
        "timeout": 1000
      }
    ],
    "defaultScore": 50,
    "useDefaultOnAllFailures": true
  }
}
```

#### 6.2 Weighted Ensemble Fallback

```json
{
  "fallbackConfig": {
    "enabled": true,
    "strategy": "ensemble",
    "models": [
      {
        "modelId": "sagemaker-model-v1",
        "weight": 0.5,
        "required": false
      },
      {
        "modelId": "bedrock-model",
        "weight": 0.3,
        "required": false
      },
      {
        "modelId": "rule-based-model",
        "weight": 0.2,
        "required": true
      }
    ],
    "minimumModelsRequired": 2,
    "aggregationMethod": "weighted_average"
  }
}
```

---

### Step 7: Configure A/B Testing

#### 7.1 Basic A/B Test Configuration

```json
{
  "abTestConfig": {
    "enabled": true,
    "testId": "model-comparison-v1-v2",
    "variants": [
      {
        "variantId": "control",
        "modelId": "review-propensity-v1",
        "trafficPercentage": 50,
        "description": "Current production model"
      },
      {
        "variantId": "treatment",
        "modelId": "review-propensity-v2",
        "trafficPercentage": 50,
        "description": "New model with additional features"
      }
    ],
    "assignmentStrategy": "hash",
    "hashKey": "customerId",
    "metrics": [
      "engagement_rate",
      "conversion_rate",
      "response_time"
    ]
  }
}
```

#### 7.2 Multi-Variant Test

```json
{
  "abTestConfig": {
    "enabled": true,
    "testId": "model-optimization-test",
    "variants": [
      {
        "variantId": "baseline",
        "modelId": "current-model",
        "trafficPercentage": 40
      },
      {
        "variantId": "variant-a",
        "modelId": "optimized-model-a",
        "trafficPercentage": 30
      },
      {
        "variantId": "variant-b",
        "modelId": "optimized-model-b",
        "trafficPercentage": 30
      }
    ],
    "assignmentStrategy": "random",
    "duration": "7d",
    "autoPromote": {
      "enabled": true,
      "metric": "engagement_rate",
      "threshold": 0.05,
      "confidenceLevel": 0.95
    }
  }
}
```

---

## Complete Scoring Configuration Example

```json
{
  "programId": "product-reviews",
  "marketplace": "US",
  "scoringConfig": {
    "enabled": true,
    "scoringModels": [
      {
        "modelId": "review-propensity-v2",
        "modelType": "sagemaker",
        "enabled": true,
        "weight": 0.7,
        "config": {
          "endpointName": "ceap-review-propensity-prod",
          "region": "us-east-1",
          "contentType": "application/json",
          "timeout": 30000
        },
        "featureConfig": {
          "featureStoreEnabled": true,
          "featureGroupName": "ceap-customer-features",
          "features": [
            "customer_lifetime_value",
            "purchase_frequency",
            "days_since_last_purchase"
          ]
        }
      },
      {
        "modelId": "sentiment-analyzer",
        "modelType": "bedrock",
        "enabled": true,
        "weight": 0.3,
        "config": {
          "modelArn": "arn:aws:bedrock:us-east-1::foundation-model/anthropic.claude-v2",
          "temperature": 0.3,
          "maxTokens": 100
        }
      }
    ],
    "featureStoreConfig": {
      "enabled": true,
      "type": "sagemaker",
      "featureGroupName": "ceap-customer-features-prod",
      "caching": {
        "enabled": true,
        "ttl": 1800
      }
    },
    "scoreCaching": {
      "enabled": true,
      "provider": "elasticache",
      "config": {
        "clusterEndpoint": "ceap-score-cache.abc123.0001.use1.cache.amazonaws.com:6379",
        "ttl": 3600
      }
    },
    "fallbackConfig": {
      "enabled": true,
      "strategy": "cascade",
      "defaultScore": 50
    },
    "abTestConfig": {
      "enabled": false
    }
  }
}
```

---

## AWS CLI Setup Commands

### Create SageMaker Endpoint

```bash
# Create model
aws sagemaker create-model \
  --model-name ceap-review-propensity-v2 \
  --primary-container '{
    "Image": "123456789.dkr.ecr.us-east-1.amazonaws.com/ceap-ml-model:latest",
    "ModelDataUrl": "s3://ceap-ml-models/review-propensity-v2/model.tar.gz"
  }' \
  --execution-role-arn arn:aws:iam::123456789:role/SageMakerExecutionRole

# Create endpoint configuration
aws sagemaker create-endpoint-config \
  --endpoint-config-name ceap-review-propensity-config \
  --production-variants '[{
    "VariantName": "AllTraffic",
    "ModelName": "ceap-review-propensity-v2",
    "InitialInstanceCount": 2,
    "InstanceType": "ml.m5.xlarge",
    "InitialVariantWeight": 1.0
  }]'

# Create endpoint
aws sagemaker create-endpoint \
  --endpoint-name ceap-review-propensity-prod \
  --endpoint-config-name ceap-review-propensity-config

# Wait for endpoint to be in service
aws sagemaker wait endpoint-in-service \
  --endpoint-name ceap-review-propensity-prod
```

### Create Feature Store

```bash
# Create feature group
aws sagemaker create-feature-group \
  --feature-group-name ceap-customer-features-prod \
  --record-identifier-feature-name customer_id \
  --event-time-feature-name event_time \
  --feature-definitions '[
    {"FeatureName": "customer_id", "FeatureType": "String"},
    {"FeatureName": "event_time", "FeatureType": "String"},
    {"FeatureName": "customer_lifetime_value", "FeatureType": "Fractional"},
    {"FeatureName": "purchase_frequency", "FeatureType": "Integral"},
    {"FeatureName": "avg_order_value", "FeatureType": "Fractional"}
  ]' \
  --online-store-config '{"EnableOnlineStore": true}' \
  --offline-store-config '{
    "S3StorageConfig": {
      "S3Uri": "s3://ceap-feature-store/offline"
    }
  }'
```

### Create ElastiCache Cluster

```bash
# Create Redis cache cluster
aws elasticache create-cache-cluster \
  --cache-cluster-id ceap-score-cache-prod \
  --cache-node-type cache.t3.medium \
  --engine redis \
  --num-cache-nodes 1 \
  --cache-parameter-group default.redis7 \
  --engine-version 7.0

# Get cluster endpoint
aws elasticache describe-cache-clusters \
  --cache-cluster-id ceap-score-cache-prod \
  --show-cache-node-info \
  --query 'CacheClusters[0].CacheNodes[0].Endpoint'
```

### Grant Lambda Permissions

```bash
# Allow Lambda to invoke SageMaker endpoint
aws iam attach-role-policy \
  --role-name CeapScoringLambdaRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonSageMakerFullAccess

# Allow Lambda to access Feature Store
aws iam attach-role-policy \
  --role-name CeapScoringLambdaRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonSageMakerFeatureStoreAccess

# Allow Lambda to access Bedrock
aws iam attach-role-policy \
  --role-name CeapScoringLambdaRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonBedrockFullAccess
```

---

## Code Examples

### SageMaker Model Adapter (Kotlin)

```kotlin
// ceap-scoring/src/main/kotlin/com/ceap/scoring/adapters/SageMakerAdapter.kt
class SageMakerAdapter(
    private val sageMakerClient: SageMakerRuntimeClient,
    private val config: SageMakerConfig
) : ModelAdapter {
    
    override suspend fun score(
        candidate: Candidate,
        features: Map<String, Any>
    ): ScoringResult {
        val payload = buildPayload(candidate, features)
        
        return try {
            val response = sageMakerClient.invokeEndpoint {
                endpointName = config.endpointName
                contentType = config.contentType
                accept = config.acceptType
                body = payload.toByteArray()
            }
            
            parseResponse(response.body().asUtf8String())
        } catch (e: Exception) {
            logger.error("SageMaker inference failed", e)
            ScoringResult.failure(e.message)
        }
    }
    
    private fun buildPayload(
        candidate: Candidate,
        features: Map<String, Any>
    ): String {
        return Json.encodeToString(
            mapOf(
                "instances" to listOf(
                    mapOf(
                        "customer_id" to candidate.customerId,
                        "subject_id" to candidate.subjectId,
                        "features" to features
                    )
                )
            )
        )
    }
    
    private fun parseResponse(response: String): ScoringResult {
        val json = Json.parseToJsonElement(response).jsonObject
        val predictions = json["predictions"]?.jsonArray?.firstOrNull()
        val score = predictions?.jsonPrimitive?.double ?: 0.0
        
        return ScoringResult.success(
            score = score,
            modelId = config.modelId,
            metadata = mapOf("raw_response" to response)
        )
    }
}
```

### Bedrock Model Adapter (Kotlin)

```kotlin
// ceap-scoring/src/main/kotlin/com/ceap/scoring/adapters/BedrockAdapter.kt
class BedrockAdapter(
    private val bedrockClient: BedrockRuntimeClient,
    private val config: BedrockConfig
) : ModelAdapter {
    
    override suspend fun score(
        candidate: Candidate,
        features: Map<String, Any>
    ): ScoringResult {
        val prompt = buildPrompt(candidate, features)
        
        return try {
            val response = bedrockClient.invokeModel {
                modelId = config.modelArn
                contentType = "application/json"
                accept = "application/json"
                body = buildRequestBody(prompt).toByteArray()
            }
            
            parseResponse(response.body().asUtf8String())
        } catch (e: Exception) {
            logger.error("Bedrock inference failed", e)
            ScoringResult.failure(e.message)
        }
    }
    
    private fun buildPrompt(
        candidate: Candidate,
        features: Map<String, Any>
    ): String {
        return config.promptTemplate
            .replace("{customerId}", candidate.customerId)
            .replace("{subjectId}", candidate.subjectId)
            .replace("{orderValue}", features["orderValue"].toString())
            .replace("{purchaseCount}", features["purchaseCount"].toString())
    }
    
    private fun buildRequestBody(prompt: String): String {
        return Json.encodeToString(
            mapOf(
                "prompt" to prompt,
                "temperature" to config.temperature,
                "max_tokens" to config.maxTokens,
                "top_p" to config.topP
            )
        )
    }
    
    private fun parseResponse(response: String): ScoringResult {
        val json = Json.parseToJsonElement(response).jsonObject
        val completion = json["completion"]?.jsonPrimitive?.content ?: ""
        
        // Extract score from response
        val scoreRegex = """engagement_score[\"']?\s*:\s*(\d+)""".toRegex()
        val score = scoreRegex.find(completion)?.groupValues?.get(1)?.toDouble() ?: 50.0
        
        return ScoringResult.success(
            score = score,
            modelId = config.modelId,
            metadata = mapOf("reasoning" to completion)
        )
    }
}
```

### Feature Store Client (Kotlin)

```kotlin
// ceap-scoring/src/main/kotlin/com/ceap/scoring/features/FeatureStoreClient.kt
class FeatureStoreClient(
    private val sageMakerClient: SageMakerFeatureStoreRuntimeClient,
    private val cacheClient: CacheClient,
    private val config: FeatureStoreConfig
) {
    
    suspend fun getFeatures(
        customerId: String,
        featureNames: List<String>
    ): Map<String, Any> {
        // Check cache first
        if (config.caching.enabled) {
            val cached = cacheClient.get("features:$customerId")
            if (cached != null) {
                return Json.decodeFromString(cached)
            }
        }
        
        // Fetch from Feature Store
        val response = sageMakerClient.getRecord {
            featureGroupName = config.featureGroupName
            recordIdentifierValueAsString = customerId
            featureNames = featureNames
        }
        
        val features = response.record()
            .associate { it.featureName() to parseFeatureValue(it) }
        
        // Cache result
        if (config.caching.enabled) {
            cacheClient.set(
                "features:$customerId",
                Json.encodeToString(features),
                config.caching.ttl
            )
        }
        
        return features
    }
    
    private fun parseFeatureValue(feature: FeatureValue): Any {
        return when {
            feature.valueAsString() != null -> feature.valueAsString()
            else -> 0.0
        }
    }
}
```

### Score Cache Manager (Kotlin)

```kotlin
// ceap-scoring/src/main/kotlin/com/ceap/scoring/cache/ScoreCacheManager.kt
class ScoreCacheManager(
    private val redisClient: RedisClient,
    private val config: ScoreCachingConfig
) {
    
    suspend fun getScore(
        programId: String,
        customerId: String,
        subjectId: String,
        modelId: String
    ): Double? {
        if (!config.enabled) return null
        
        val key = buildCacheKey(programId, customerId, subjectId, modelId)
        val cached = redisClient.get(key)
        
        return cached?.toDoubleOrNull()
    }
    
    suspend fun setScore(
        programId: String,
        customerId: String,
        subjectId: String,
        modelId: String,
        score: Double
    ) {
        if (!config.enabled) return
        
        val key = buildCacheKey(programId, customerId, subjectId, modelId)
        redisClient.setex(key, config.ttl, score.toString())
    }
    
    suspend fun invalidate(
        programId: String,
        customerId: String
    ) {
        val pattern = buildCacheKey(programId, customerId, "*", "*")
        val keys = redisClient.keys(pattern)
        
        if (keys.isNotEmpty()) {
            redisClient.del(*keys.toTypedArray())
        }
    }
    
    private fun buildCacheKey(
        programId: String,
        customerId: String,
        subjectId: String,
        modelId: String
    ): String {
        return "${config.keyPrefix}$programId:$customerId:$subjectId:$modelId"
    }
}
```

### A/B Test Manager (Kotlin)

```kotlin
// ceap-scoring/src/main/kotlin/com/ceap/scoring/ab/ABTestManager.kt
class ABTestManager(
    private val config: ABTestConfig
) {
    
    fun assignVariant(customerId: String): String {
        if (!config.enabled) {
            return config.variants.first().variantId
        }
        
        return when (config.assignmentStrategy) {
            "hash" -> assignByHash(customerId)
            "random" -> assignRandomly()
            else -> config.variants.first().variantId
        }
    }
    
    private fun assignByHash(customerId: String): String {
        val hash = customerId.hashCode().absoluteValue
        val bucket = hash % 100
        
        var cumulative = 0
        for (variant in config.variants) {
            cumulative += variant.trafficPercentage
            if (bucket < cumulative) {
                return variant.variantId
            }
        }
        
        return config.variants.last().variantId
    }
    
    private fun assignRandomly(): String {
        val random = (0..99).random()
        var cumulative = 0
        
        for (variant in config.variants) {
            cumulative += variant.trafficPercentage
            if (random < cumulative) {
                return variant.variantId
            }
        }
        
        return config.variants.last().variantId
    }
    
    fun getModelForVariant(variantId: String): String? {
        return config.variants
            .find { it.variantId == variantId }
            ?.modelId
    }
}
```

---

## Testing Procedures

### Test 1: Test SageMaker Endpoint

```bash
# Test endpoint directly
aws sagemaker-runtime invoke-endpoint \
  --endpoint-name ceap-review-propensity-prod \
  --content-type application/json \
  --accept application/json \
  --body '{"instances": [{"customer_id": "CUST-12345", "features": {"ltv": 500, "frequency": 10}}]}' \
  response.json

cat response.json
```

### Test 2: Test Bedrock Model

```bash
# Test Bedrock model
aws bedrock-runtime invoke-model \
  --model-id anthropic.claude-v2 \
  --content-type application/json \
  --accept application/json \
  --body '{"prompt": "Predict engagement score for customer CUST-12345", "temperature": 0.3, "max_tokens": 100}' \
  response.json

cat response.json
```

### Test 3: Test Feature Store

```bash
# Get features from Feature Store
aws sagemaker-featurestore-runtime get-record \
  --feature-group-name ceap-customer-features-prod \
  --record-identifier-value-as-string "CUST-12345" \
  --feature-names customer_lifetime_value purchase_frequency avg_order_value
```

### Test 4: Test Scoring Lambda

```bash
# Invoke scoring Lambda
aws lambda invoke \
  --function-name CeapScoringLambda-prod \
  --payload '{
    "candidates": [
      {
        "customerId": "CUST-12345",
        "subjectId": "PROD-67890",
        "programId": "product-reviews",
        "marketplace": "US"
      }
    ]
  }' \
  --cli-binary-format raw-in-base64-out \
  response.json

cat response.json | jq '.scores'
```

### Test 5: Test Cache

```bash
# Connect to Redis and check cache
redis-cli -h ceap-score-cache.abc123.0001.use1.cache.amazonaws.com

# In Redis CLI:
GET score:product-reviews:CUST-12345:PROD-67890:review-propensity-v2
TTL score:product-reviews:CUST-12345:PROD-67890:review-propensity-v2
```

### Test 6: Test A/B Assignment

```bash
# Test A/B variant assignment
aws lambda invoke \
  --function-name CeapScoringLambda-prod \
  --payload '{
    "action": "test-ab-assignment",
    "customerIds": ["CUST-001", "CUST-002", "CUST-003", "CUST-004", "CUST-005"]
  }' \
  --cli-binary-format raw-in-base64-out \
  response.json

cat response.json | jq '.assignments'
```

---

## Troubleshooting

### Issue: "SageMaker endpoint timeout"

**Symptoms**: Scoring requests timing out

**Solutions**:
1. Increase timeout in configuration
2. Scale up endpoint instances
3. Use batch inference for large volumes
4. Enable caching to reduce calls

```bash
# Check endpoint metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/SageMaker \
  --metric-name ModelLatency \
  --dimensions Name=EndpointName,Value=ceap-review-propensity-prod \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 300 \
  --statistics Average,Maximum

# Scale endpoint
aws sagemaker update-endpoint-weights-and-capacities \
  --endpoint-name ceap-review-propensity-prod \
  --desired-weights-and-capacities '[{
    "VariantName": "AllTraffic",
    "DesiredInstanceCount": 4
  }]'
```

### Issue: "Feature Store latency"

**Symptoms**: Slow feature retrieval

**Solutions**:
1. Enable feature caching
2. Use batch feature retrieval
3. Pre-compute features offline
4. Optimize feature group design

```bash
# Check Feature Store metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/SageMaker/FeatureStore \
  --metric-name GetRecordLatency \
  --dimensions Name=FeatureGroupName,Value=ceap-customer-features-prod \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 300 \
  --statistics Average,Maximum
```

### Issue: "Cache misses"

**Symptoms**: High cache miss rate, increased costs

**Solutions**:
1. Increase cache TTL
2. Pre-warm cache for common customers
3. Review cache key strategy
4. Monitor cache hit rate

```bash
# Check cache metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/ElastiCache \
  --metric-name CacheHitRate \
  --dimensions Name=CacheClusterId,Value=ceap-score-cache-prod \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 300 \
  --statistics Average
```

### Issue: "Model inference errors"

**Symptoms**: Scoring failures, fallback to default scores

**Solutions**:
1. Check model endpoint status
2. Verify input data format
3. Review model logs
4. Test with sample data

```bash
# Check endpoint status
aws sagemaker describe-endpoint \
  --endpoint-name ceap-review-propensity-prod \
  --query 'EndpointStatus'

# Check CloudWatch logs
aws logs tail /aws/sagemaker/Endpoints/ceap-review-propensity-prod --follow
```

### Issue: "A/B test imbalance"

**Symptoms**: Uneven traffic distribution

**Solutions**:
1. Verify traffic percentages sum to 100
2. Check hash function for bias
3. Review assignment logs
4. Use random assignment for testing

```bash
# Check variant distribution
aws cloudwatch get-metric-statistics \
  --namespace CEAP/Scoring \
  --metric-name VariantAssignments \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Sum \
  --dimensions Name=VariantId,Value=control

aws cloudwatch get-metric-statistics \
  --namespace CEAP/Scoring \
  --metric-name VariantAssignments \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Sum \
  --dimensions Name=VariantId,Value=treatment
```

---

## Real-World Use Cases

### Use Case 1: Product Review Propensity

**Scenario**: Predict likelihood of customer leaving a product review

**Configuration**:
```json
{
  "scoringModels": [
    {
      "modelId": "review-propensity-v2",
      "modelType": "sagemaker",
      "config": {
        "endpointName": "ceap-review-propensity-prod"
      },
      "featureConfig": {
        "features": [
          "customer_lifetime_value",
          "purchase_frequency",
          "previous_review_count",
          "days_since_last_review",
          "product_category_affinity",
          "order_value"
        ]
      }
    }
  ],
  "scoreCaching": {
    "enabled": true,
    "ttl": 3600
  }
}
```

**Expected Output**: Score 0-100 indicating review likelihood

### Use Case 2: Video Rating with Sentiment Analysis

**Scenario**: Combine ML propensity with sentiment analysis

**Configuration**:
```json
{
  "scoringModels": [
    {
      "modelId": "video-engagement-model",
      "modelType": "sagemaker",
      "weight": 0.6,
      "config": {
        "endpointName": "ceap-video-engagement-prod"
      },
      "featureConfig": {
        "features": [
          "watch_percentage",
          "watch_count",
          "genre_preference",
          "time_of_day"
        ]
      }
    },
    {
      "modelId": "sentiment-analyzer",
      "modelType": "bedrock",
      "weight": 0.4,
      "config": {
        "modelArn": "arn:aws:bedrock:us-east-1::foundation-model/anthropic.claude-v2",
        "promptTemplate": "Analyze viewer sentiment for video {videoTitle}. Watch percentage: {watchPercentage}%. Previous ratings: {previousRatings}. Predict engagement score 0-100."
      }
    }
  ],
  "fallbackConfig": {
    "strategy": "ensemble",
    "aggregationMethod": "weighted_average"
  }
}
```

### Use Case 3: High-Value Customer Prioritization

**Scenario**: Prioritize surveys for high-value customers

**Configuration**:
```json
{
  "scoringModels": [
    {
      "modelId": "customer-value-scorer",
      "modelType": "custom",
      "config": {
        "type": "lambda",
        "functionName": "CeapCustomerValueScorer-prod"
      }
    }
  ],
  "featureConfig": {
    "features": [
      "customer_lifetime_value",
      "purchase_frequency",
      "avg_order_value",
      "customer_tenure_days",
      "vip_status"
    ]
  },
  "scoreCaching": {
    "enabled": true,
    "ttl": 7200
  }
}
```

### Use Case 4: Multi-Model A/B Test

**Scenario**: Compare new model against baseline

**Configuration**:
```json
{
  "scoringModels": [
    {
      "modelId": "baseline-model-v1",
      "modelType": "sagemaker",
      "config": {
        "endpointName": "ceap-baseline-v1-prod"
      }
    },
    {
      "modelId": "optimized-model-v2",
      "modelType": "sagemaker",
      "config": {
        "endpointName": "ceap-optimized-v2-prod"
      }
    }
  ],
  "abTestConfig": {
    "enabled": true,
    "testId": "model-optimization-2026-01",
    "variants": [
      {
        "variantId": "baseline",
        "modelId": "baseline-model-v1",
        "trafficPercentage": 50
      },
      {
        "variantId": "optimized",
        "modelId": "optimized-model-v2",
        "trafficPercentage": 50
      }
    ],
    "metrics": ["engagement_rate", "conversion_rate", "model_latency"],
    "duration": "14d"
  }
}
```

---

## Performance Optimization Tips

### 1. Batch Scoring

```kotlin
// Score multiple candidates in one request
suspend fun scoreBatch(candidates: List<Candidate>): List<ScoringResult> {
    val batchSize = 100
    return candidates.chunked(batchSize).flatMap { batch ->
        sageMakerClient.invokeEndpoint {
            endpointName = config.endpointName
            body = Json.encodeToString(mapOf("instances" to batch)).toByteArray()
        }
    }
}
```

### 2. Parallel Model Execution

```kotlin
// Execute multiple models in parallel
suspend fun scoreWithMultipleModels(
    candidate: Candidate
): Map<String, Double> = coroutineScope {
    val results = models.map { model ->
        async {
            model.modelId to model.score(candidate)
        }
    }.awaitAll()
    
    results.toMap()
}
```

### 3. Feature Pre-computation

```sql
-- Pre-compute features in data warehouse
CREATE TABLE customer_features_materialized AS
SELECT 
    customer_id,
    SUM(order_value) as customer_lifetime_value,
    COUNT(*) as purchase_frequency,
    AVG(order_value) as avg_order_value,
    MAX(order_date) as last_purchase_date,
    CURRENT_DATE - MAX(order_date) as days_since_last_purchase
FROM orders
GROUP BY customer_id;
```

### 4. Smart Caching Strategy

```json
{
  "scoreCaching": {
    "enabled": true,
    "strategy": "tiered",
    "tiers": [
      {
        "name": "hot",
        "ttl": 300,
        "criteria": "customerTier == 'vip'"
      },
      {
        "name": "warm",
        "ttl": 1800,
        "criteria": "purchaseFrequency > 5"
      },
      {
        "name": "cold",
        "ttl": 7200,
        "criteria": "default"
      }
    ]
  }
}
```

---

## Monitoring and Metrics

### Key Metrics to Track

```bash
# Model latency
aws cloudwatch get-metric-statistics \
  --namespace CEAP/Scoring \
  --metric-name ModelLatency \
  --dimensions Name=ModelId,Value=review-propensity-v2 \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 300 \
  --statistics Average,p99

# Scoring throughput
aws cloudwatch get-metric-statistics \
  --namespace CEAP/Scoring \
  --metric-name ScoringRequests \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 300 \
  --statistics Sum

# Cache hit rate
aws cloudwatch get-metric-statistics \
  --namespace CEAP/Scoring \
  --metric-name CacheHitRate \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 300 \
  --statistics Average

# Model errors
aws cloudwatch get-metric-statistics \
  --namespace CEAP/Scoring \
  --metric-name ModelErrors \
  --dimensions Name=ModelId,Value=review-propensity-v2 \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 300 \
  --statistics Sum

# A/B test metrics
aws cloudwatch get-metric-statistics \
  --namespace CEAP/Scoring \
  --metric-name EngagementRate \
  --dimensions Name=VariantId,Value=treatment \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Average
```

---

## Summary

**Scoring Configuration Checklist**:

1. ✅ Configure ML models (SageMaker, Bedrock, or custom)
2. ✅ Set up Feature Store for model features
3. ✅ Enable score caching (ElastiCache or DynamoDB)
4. ✅ Configure fallback strategies
5. ✅ Set up A/B testing (optional)
6. ✅ Test model endpoints and feature retrieval
7. ✅ Monitor performance metrics
8. ✅ Optimize for latency and cost

**Your scoring system is now configured!**

---

## Next Steps

- **Configure Storage**: See `STORAGE-CONFIGURATION-GUIDE.md`
- **Configure Notifications**: See `NOTIFICATION-CONFIGURATION-GUIDE.md`
- **Monitor Model Performance**: Set up CloudWatch dashboards
- **Optimize Costs**: Review caching and batch strategies

---

**Need help?** Check `docs/TROUBLESHOOTING.md` or review `docs/USE-CASES.md` for complete examples.
