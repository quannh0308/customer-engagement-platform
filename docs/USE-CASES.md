# Use Cases - Customer Engagement & Action Platform (CEAP)

> **Platform Rebranding Note**: This platform was formerly known as the "General Solicitation Platform". We've rebranded to "Customer Engagement & Action Platform (CEAP)" to better reflect its capabilities beyond solicitation. Package names (`com.solicitation.*`) remain unchanged for backward compatibility.

## Overview

The Customer Engagement & Action Platform (CEAP) enables businesses to deliver intelligent customer actions (reviews, ratings, surveys, participation requests, content contributions, recommendations, fraud alerts, and more) across multiple product verticals through flexible, pluggable components.

---

## Table of Contents

1. [Business Use Cases](#business-use-cases)
2. [Technical Use Cases](#technical-use-cases)
3. [Integration Use Cases](#integration-use-cases)
4. [End-to-End Scenarios](#end-to-end-scenarios)
5. [Extension Use Cases](#extension-use-cases)

---

## Business Use Cases

### Use Case 1: E-Commerce Product Reviews

**Business Goal**: Increase product review volume by 30% by soliciting reviews from verified purchasers at optimal times.

**Actors**: 
- Customer (purchaser)
- Product team
- Marketing team

**Flow**:
1. Customer completes a product purchase
2. System waits for delivery confirmation (reactive trigger)
3. System checks eligibility:
   - Customer has received the product
   - Customer hasn't been solicited for this product before
   - Customer hasn't opted out of review requests
   - Product is eligible for reviews (not restricted category)
4. ML model scores the likelihood of review submission
5. System creates candidate with high score
6. Email channel delivers review request 3 days after delivery
7. Customer submits review via email link

**Data Sources**: Order history, delivery tracking, customer profile
**Channels**: Email, in-app notification
**Scoring Models**: Review propensity model, engagement history model
**Filters**: Trust filter, eligibility filter, frequency cap filter

**Success Metrics**:
- 25% review submission rate
- P99 latency < 30ms for candidate retrieval
- 0% solicitations to opted-out customers

---

### Use Case 2: Video Content Ratings

**Business Goal**: Collect ratings for newly released videos to improve recommendation algorithms.

**Actors**:
- Video viewer
- Content team
- Recommendation team

**Flow**:
1. Customer watches video to completion
2. System immediately creates candidate (reactive)
3. System checks:
   - Customer watched >80% of video
   - Video is eligible for ratings
   - Customer hasn't rated this video
   - Customer engagement score is high
4. In-app card appears asking for rating
5. Customer provides 1-5 star rating
6. Rating stored and used for recommendations

**Data Sources**: Video watch history, customer engagement metrics
**Channels**: In-app card, push notification
**Scoring Models**: Engagement prediction model
**Filters**: Watch completion filter, duplicate prevention filter

**Success Metrics**:
- 40% rating submission rate
- <1 second end-to-end latency (reactive)
- 95% accuracy in targeting engaged viewers

---

### Use Case 3: Music Track Feedback

**Business Goal**: Gather feedback on new music releases to inform playlist curation.

**Actors**:
- Music listener
- Music curation team
- Artist relations team

**Flow**:
1. Batch job runs daily at 2 AM
2. System queries data warehouse for:
   - Customers who listened to new tracks (released in last 7 days)
   - Customers who completed full track playback
   - Customers with high engagement scores
3. System filters out:
   - Customers who already provided feedback
   - Customers with low trust scores
   - Customers who exceeded feedback frequency cap
4. ML model scores feedback likelihood
5. Top 10,000 candidates stored per track
6. Email campaign sent at 10 AM with feedback request
7. Customers provide thumbs up/down + optional comment

**Data Sources**: Music streaming data warehouse, customer profiles
**Channels**: Email, voice assistant prompt
**Scoring Models**: Feedback propensity model, music taste affinity model
**Filters**: Trust filter, frequency cap filter, engagement filter

**Success Metrics**:
- Process 10M+ candidates per day
- 15% feedback submission rate
- <5 minute batch processing time

---

### Use Case 4: Service Experience Surveys

**Business Goal**: Collect post-service feedback to improve customer satisfaction.

**Actors**:
- Service customer
- Customer service team
- Quality assurance team

**Flow**:
1. Customer completes service interaction (support call, installation, repair)
2. System waits 1 hour (cooling period)
3. System creates candidate (reactive)
4. System checks:
   - Service interaction completed successfully
   - Customer hasn't been surveyed for this service type in 30 days
   - Customer satisfaction score is not already very high
5. SMS sent with survey link
6. Customer completes 3-question survey
7. Results feed into service quality dashboard

**Data Sources**: Service interaction logs, customer satisfaction history
**Channels**: SMS, email, in-app
**Scoring Models**: Survey completion model, satisfaction prediction model
**Filters**: Frequency cap filter, service type filter, satisfaction threshold filter

**Success Metrics**:
- 35% survey completion rate
- <2 seconds reactive processing time
- 100% compliance with frequency caps

---

### Use Case 5: Event Participation Requests

**Business Goal**: Maximize attendance at virtual events by targeting likely participants.

**Actors**:
- Potential attendee
- Events team
- Marketing team

**Flow**:
1. Batch job runs weekly
2. System identifies customers who:
   - Match event topic interests
   - Have attended similar events
   - Are in target geographic region
   - Have high engagement scores
3. ML model scores attendance likelihood
4. System creates candidates for top 50,000 customers
5. Multi-channel campaign:
   - Email invitation (primary)
   - In-app banner (secondary)
   - Push notification (reminder 1 day before)
6. Customers register via email link
7. System tracks registration and attendance

**Data Sources**: Customer interest profiles, event history, geographic data
**Channels**: Email, in-app, push notification
**Scoring Models**: Event attendance model, interest affinity model
**Filters**: Geographic filter, interest match filter, capacity filter

**Success Metrics**:
- 20% registration rate
- 70% attendance rate (of registered)
- Process 1M+ candidates per event

---

## Technical Use Cases

### Use Case 6: Multi-Program Isolation

**Technical Goal**: Ensure one program's failure doesn't affect other programs.

**Scenario**:
1. Program A (product reviews) has a bug in its scoring model
2. Scoring model throws exceptions for all candidates
3. System isolates failure to Program A only
4. Program B (video ratings) continues processing normally
5. Program A falls back to cached scores
6. Alerts sent to Program A team
7. Other programs unaffected

**Architecture Features**:
- Independent Lambda functions per workflow
- Program-specific error handling
- Circuit breakers per program
- Isolated DynamoDB partitions

---

### Use Case 7: Real-Time Eligibility Refresh

**Technical Goal**: Ensure candidates are still eligible at delivery time.

**Scenario**:
1. Candidate created 5 days ago for product review
2. Serving API receives request for this customer
3. System detects candidate is >3 days old (stale)
4. System performs real-time eligibility check:
   - Customer hasn't opted out (check opt-out service)
   - Product still eligible (check product catalog)
   - Customer hasn't already reviewed (check review service)
5. If still eligible, return candidate
6. If not eligible, mark as expired and return empty

**Architecture Features**:
- Staleness detection in serving API
- Real-time service integrations
- Graceful degradation on service failures
- Caching of eligibility checks

---

### Use Case 8: Shadow Mode Testing

**Technical Goal**: Test new channels without affecting customers.

**Scenario**:
1. New WhatsApp channel adapter developed
2. Channel configured in shadow mode
3. System processes candidates normally
4. WhatsApp adapter executes all logic:
   - Formats messages
   - Validates phone numbers
   - Logs what would be sent
5. No actual messages sent to customers
6. Metrics collected for analysis
7. After validation, shadow mode disabled

**Architecture Features**:
- Shadow mode flag in channel config
- Full execution without delivery
- Comprehensive logging
- Metrics collection

---

### Use Case 9: Scoring Model A/B Testing

**Technical Goal**: Compare performance of two scoring models.

**Scenario**:
1. New scoring model (Model B) developed
2. Experiment configured: 90% Model A, 10% Model B
3. System uses consistent hashing to assign customers
4. Customer X always gets Model A
5. Customer Y always gets Model B
6. Both models execute, scores stored with model ID
7. Metrics collected per model:
   - Prediction accuracy
   - Response rates
   - Processing latency
8. After 2 weeks, Model B shows 15% improvement
9. Model B promoted to 100% traffic

**Architecture Features**:
- Deterministic treatment assignment
- Multi-model scoring support
- Treatment-specific metrics
- Gradual rollout capability

---

### Use Case 10: Data Source Migration

**Technical Goal**: Migrate from Athena to Snowflake without downtime.

**Scenario**:
1. New Snowflake connector developed (5 minutes)
2. Connector tested in dev environment
3. Program configured with both connectors:
   - Athena: 100% traffic (primary)
   - Snowflake: 0% traffic (shadow)
4. Snowflake runs in shadow mode for 1 week
5. Metrics show identical results
6. Traffic shifted: 50% Athena, 50% Snowflake
7. After validation, 100% Snowflake
8. Athena connector removed from config

**Architecture Features**:
- Pluggable connector interface
- Shadow mode for connectors
- Gradual traffic shifting
- Zero downtime migration

---

## Integration Use Cases

### Use Case 11: Feature Store Integration

**Technical Goal**: Retrieve customer and subject features for scoring.

**Scenario**:
1. Scoring Lambda receives candidate
2. System identifies required features:
   - Customer: engagement_score, review_history, trust_score
   - Subject: category, price, rating_count
3. System calls Feature Store API
4. Features retrieved in <10ms
5. Features validated (all required present)
6. Features passed to ML model
7. Score computed and cached

**Integration Points**:
- AWS SageMaker Feature Store
- Feature validation service
- Score cache (DynamoDB)

---

### Use Case 12: Email Campaign Service Integration

**Technical Goal**: Create email campaigns from candidates.

**Scenario**:
1. Batch workflow completes, 50,000 candidates ready
2. Email channel adapter groups by program
3. For each program:
   - Create campaign in email service
   - Upload recipient list (customer IDs)
   - Set template ID from program config
   - Set send time (10 AM local time)
   - Enable tracking (opens, clicks)
4. Email service schedules campaigns
5. Delivery status tracked in DynamoDB
6. Metrics published to CloudWatch

**Integration Points**:
- Email campaign service API
- Template management service
- Opt-out service
- Delivery tracking service

---

### Use Case 13: Data Warehouse Export

**Technical Goal**: Export candidates for analytics and reporting.

**Scenario**:
1. Daily export job runs at 3 AM
2. System queries DynamoDB for candidates created yesterday
3. Candidates transformed to Parquet format
4. Files written to S3 (partitioned by date and program)
5. Glue crawler updates data catalog
6. Athena tables available for querying
7. Analytics team runs reports

**Integration Points**:
- DynamoDB Streams
- S3 bucket
- AWS Glue
- Amazon Athena

---

## End-to-End Scenarios

### Scenario 1: Product Review - Batch Processing

**Timeline**: Daily batch job

```
Day 1, 2:00 AM - Batch Job Starts
├─ 2:00-2:15 AM: ETL Lambda
│  ├─ Query data warehouse for yesterday's deliveries
│  ├─ Extract 500,000 delivery records
│  ├─ Transform to candidate format
│  └─ Output: 500,000 raw candidates
│
├─ 2:15-2:25 AM: Filter Lambda
│  ├─ Apply trust filter (remove 50,000)
│  ├─ Apply eligibility filter (remove 100,000)
│  ├─ Apply frequency cap filter (remove 150,000)
│  └─ Output: 200,000 eligible candidates
│
├─ 2:25-2:45 AM: Score Lambda
│  ├─ Retrieve features from Feature Store
│  ├─ Call ML model (batch scoring)
│  ├─ Cache scores in DynamoDB
│  └─ Output: 200,000 scored candidates
│
├─ 2:45-3:00 AM: Store Lambda
│  ├─ Batch write to DynamoDB (25 items per batch)
│  ├─ Set TTL to 30 days
│  └─ Output: 200,000 candidates stored
│
└─ 10:00 AM: Email Channel
   ├─ Retrieve top 50,000 candidates (highest scores)
   ├─ Create email campaign
   ├─ Send review requests
   └─ Track delivery status

Day 1, 10:00 AM - 11:00 AM: Customer Engagement
├─ 12,500 emails opened (25% open rate)
├─ 5,000 customers click review link (10% CTR)
└─ 2,500 reviews submitted (5% conversion)

Day 2-30: Serving API
├─ In-app requests for review candidates
├─ P99 latency: 18ms
└─ Cache hit rate: 85%
```

**Metrics**:
- Total processing time: 1 hour
- Throughput: 8,333 candidates/minute
- Review submission rate: 5%
- Cost: $2.50 per batch run

---

### Scenario 2: Video Rating - Reactive Processing

**Timeline**: Real-time event processing

```
12:34:56.000 - Customer completes video
├─ EventBridge receives watch completion event
├─ Event payload: {customerId, videoId, watchPercentage: 95%, timestamp}
└─ Reactive Lambda triggered

12:34:56.100 - Reactive Lambda Processing
├─ Parse event (5ms)
├─ Check deduplication cache (10ms)
├─ Apply filters in parallel (50ms)
│  ├─ Trust filter: PASS
│  ├─ Eligibility filter: PASS
│  └─ Watch completion filter: PASS (95% > 80%)
├─ Retrieve features (15ms)
├─ Call scoring model (30ms)
├─ Create candidate (10ms)
└─ Write to DynamoDB (20ms)

12:34:56.240 - Candidate Available (240ms total)

12:34:56.500 - In-App Channel
├─ Customer navigates to home screen
├─ App calls Serving API
├─ API returns rating candidate (12ms)
└─ In-app card displayed

12:34:58.000 - Customer Interaction
├─ Customer sees rating card
├─ Customer provides 5-star rating
└─ Rating stored and used for recommendations
```

**Metrics**:
- End-to-end latency: 240ms
- Serving API latency: 12ms (P99)
- Rating submission rate: 40%
- Cost: $0.0001 per event

---

### Scenario 3: Multi-Channel Campaign

**Timeline**: Coordinated multi-channel delivery

```
Week 1, Monday 2:00 AM - Batch Processing
├─ ETL: Extract event attendees (100,000 customers)
├─ Filter: Apply interest matching (50,000 eligible)
├─ Score: Predict attendance likelihood (50,000 scored)
└─ Store: Save candidates with channel eligibility flags

Week 1, Monday 10:00 AM - Email Channel (Primary)
├─ Retrieve top 30,000 candidates (highest scores)
├─ Create email campaign with event invitation
├─ Send emails with registration link
└─ Track: 7,500 opens (25%), 3,000 clicks (10%)

Week 1, Monday 2:00 PM - In-App Channel (Secondary)
├─ Customers open app
├─ Serving API returns event candidates
├─ In-app banner displayed
└─ Track: 5,000 impressions, 500 clicks (10%)

Week 1, Friday 9:00 AM - Push Notification (Reminder)
├─ Event is in 2 days
├─ Retrieve registered customers (4,000)
├─ Send push notification reminder
└─ Track: 3,200 opens (80%)

Week 1, Sunday 10:00 AM - Event Day
├─ 3,500 customers attend (87.5% of registered)
├─ Post-event survey sent via email
└─ 1,400 surveys completed (40%)
```

**Metrics**:
- Total reach: 30,000 customers
- Registration rate: 13.3% (4,000/30,000)
- Attendance rate: 87.5% (3,500/4,000)
- Multi-channel attribution: Email 75%, In-app 12.5%, Push 12.5%

---

## Extension Use Cases

### Use Case 14: Adding Kinesis Connector (5 minutes)

**Goal**: Ingest real-time events from Kinesis stream.

**Steps**:
1. Create module: `solicitation-connectors-kinesis/`
2. Implement `DataConnector` interface:
   ```kotlin
   class KinesisConnector : DataConnector {
       override fun getName() = "kinesis"
       override fun extractData(config: Map<String, Any>): List<Map<String, Any>> {
           // Read from Kinesis stream
           // Deserialize events
           // Return as list
       }
       override fun transformToCandidate(data: Map<String, Any>): Candidate {
           // Map Kinesis event to Candidate
       }
   }
   ```
3. Add to `settings.gradle.kts`
4. Build: `./gradlew :solicitation-connectors-kinesis:build`
5. Update ETL Lambda dependency
6. Deploy: `./infrastructure/deploy-cdk.sh -e dev -s EtlWorkflow`

**Result**: Real-time event ingestion enabled in 5 minutes!

---

### Use Case 15: Adding Geographic Filter (3 minutes)

**Goal**: Filter candidates by customer location.

**Steps**:
1. Add class to `solicitation-filters` module:
   ```kotlin
   class GeographicFilter : Filter {
       override fun getFilterId() = "geographic"
       override fun filter(
           candidates: List<Candidate>,
           config: FilterConfig
       ): FilterResult {
           val allowedRegions = config.parameters["regions"] as List<String>
           val passed = candidates.filter { candidate ->
               val region = candidate.context.find { it.type == "region" }?.id
               region in allowedRegions
           }
           return FilterResult(passed, candidates - passed)
       }
   }
   ```
2. Build: `./gradlew :solicitation-filters:build`
3. Update program config to include geographic filter
4. Deploy: `./infrastructure/deploy-cdk.sh -e dev -s FilterWorkflow`

**Result**: Geographic filtering enabled in 3 minutes!

---

### Use Case 16: Adding Bedrock Scoring Model (5 minutes)

**Goal**: Use AWS Bedrock for candidate scoring.

**Steps**:
1. Add class to `solicitation-scoring` module:
   ```kotlin
   class BedrockScoringProvider : ScoringProvider {
       override fun getModelId() = "bedrock-claude"
       override fun scoreCandidate(
           candidate: Candidate,
           features: Map<String, Any>
       ): Score {
           // Call Bedrock API
           // Parse response
           // Return score
       }
   }
   ```
2. Build: `./gradlew :solicitation-scoring:build`
3. Update program config with new model
4. Deploy: `./infrastructure/deploy-cdk.sh -e dev -s ScoreWorkflow`

**Result**: Bedrock scoring enabled in 5 minutes!

---

### Use Case 17: Adding WhatsApp Channel (5 minutes)

**Goal**: Deliver solicitations via WhatsApp.

**Steps**:
1. Add class to `solicitation-channels` module:
   ```kotlin
   class WhatsAppChannelAdapter : ChannelAdapter {
       override fun getChannelId() = "whatsapp"
       override fun deliver(
           candidates: List<Candidate>,
           config: ChannelConfig
       ): DeliveryResult {
           // Format WhatsApp message
           // Call WhatsApp Business API
           // Track delivery status
       }
   }
   ```
2. Build: `./gradlew :solicitation-channels:build`
3. Update program config to include WhatsApp channel
4. Deploy: `./infrastructure/deploy-cdk.sh -e dev -s ServeWorkflow`

**Result**: WhatsApp delivery enabled in 5 minutes!

---

## Summary

The Customer Engagement & Action Platform (CEAP) supports diverse use cases across:

### Business Domains
- ✅ E-commerce (product reviews, ratings)
- ✅ Media (video ratings, music feedback)
- ✅ Services (surveys, feedback)
- ✅ Events (participation, registration)
- ✅ Content (contributions, polls)

### Processing Modes
- ✅ Batch (scheduled, high-volume)
- ✅ Reactive (real-time, event-driven)
- ✅ Hybrid (batch + reactive)

### Integration Patterns
- ✅ Data sources (Athena, Kinesis, S3, Snowflake)
- ✅ ML models (SageMaker, Bedrock, custom)
- ✅ Channels (email, in-app, push, SMS, voice, WhatsApp)
- ✅ Feature stores (SageMaker, custom)
- ✅ Analytics (data warehouse, Athena)

### Extension Capabilities
- ✅ New connectors: 5 minutes
- ✅ New filters: 3 minutes
- ✅ New scoring models: 5 minutes
- ✅ New channels: 5 minutes
- ✅ New workflows: 10 minutes

**The platform is truly plug-and-play!** 🚀
