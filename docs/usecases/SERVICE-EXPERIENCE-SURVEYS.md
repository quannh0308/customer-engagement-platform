# Use Case: Service Experience Surveys

## Overview

**Business Goal**: Collect post-service feedback to improve customer satisfaction and identify service quality issues in real-time.

**Processing Mode**: Reactive (Event-driven with cooling period)

**Actors**:
- Service customer
- Customer service team
- Quality assurance team
- Service operations team
- EventBridge
- SMS gateway

---

## Actor Interaction Diagram

```
┌──────────┐         ┌──────────────┐         ┌─────────────────┐
│ Customer │         │ Service Team │         │ QA Team         │
└────┬─────┘         └──────┬───────┘         └────────┬────────┘
     │                      │                          │
     │ 1. Completes         │                          │
     │    Service Call      │                          │
     ├──────────────────────┼──────────────────────────┤
     │                      │                          │
     │ 2. Service Closed    │                          │
     │    Event Published   │                          │
     ├──────────────────────┼──────────────────────────┤
     │                      │                          │
     │                      │ 3. Configures            │
     │                      │    Survey Program        │
     │                      ├─────────────────────────>│
     │                      │                          │
     │ 4. Receives SMS      │                          │
     │    Survey (1hr later)│<─────────────────────────┤
     │<─────────────────────┤    (After cooling)       │
     │                      │                          │
     │ 5. Completes Survey  │                          │
     │    (3 questions)     │                          │
     ├─────────────────────>│                          │
     │                      │                          │
     │                      │ 6. Reviews Feedback      │
     │                      │<─────────────────────────┤
     │                      │                          │
     │                      │ 7. Takes Action          │
     │                      │    (if negative)         │
     │                      ├─────────────────────────>│
     │                      │                          │
     ▼                      ▼                          ▼
```

---

## Reactive Data Flow (Real-Time Processing with Cooling Period)

### Event-Driven Processing - <2 Seconds + 1 Hour Cooling

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      REACTIVE DATA FLOW                                  │
│              (Real-Time Event Processing + Cooling Period)               │
└─────────────────────────────────────────────────────────────────────────┘

T+0ms: Service Interaction Completed
┌─────────────────────────────────────┐
│   Service Management System         │
│  - Service call completed           │
│  - Resolution: "Issue Resolved"     │
│  - Duration: 45 minutes             │
│  - Agent: "John Smith"              │
│  - Publishes event to EventBridge   │
└────────┬────────────────────────────┘
         │ Event: {
         │   customerId: "C789",
         │   serviceId: "S123",
         │   serviceType: "technical-support",
         │   resolution: "resolved",
         │   duration: 45,
         │   agentId: "A456",
         │   timestamp: "2026-01-18T14:30:00Z"
         │ }
         ▼

T+10ms: EventBridge Routes Event
┌─────────────────────────────────────┐
│         EventBridge Rule            │
│  Pattern: {                         │
│    source: "service.management",    │
│    detail-type: "ServiceCompleted", │
│    detail: {                        │
│      resolution: ["resolved"]       │
│    }                                │
│  }                                  │
│  Target: Reactive Lambda            │
└────────┬────────────────────────────┘
         │ Event matched, trigger Lambda
         ▼

T+20ms: Reactive Lambda Invoked
┌─────────────────────────────────────┐
│      Reactive Lambda Handler        │
│  1. Parse event (5ms)               │
│  2. Check deduplication (10ms)      │
│  3. Create candidate object (5ms)   │
└────────┬────────────────────────────┘
         │ Candidate created
         ▼

T+30ms: Filter Lambda Processing
┌─────────────────────────────────────┐
│      Filter Chain Executor          │
│  Parallel filter execution (80ms)   │
└────────┬────────────────────────────┘
         │
         ├─> Trust Filter (20ms)
         │   ├─ Verify customer authenticity
         │   ├─ Check account status
         │   └─ Result: PASS
         │
         ├─> Eligibility Filter (30ms)
         │   ├─ Customer hasn't been surveyed for this service type in 30 days
         │   ├─ Service type is eligible for surveys
         │   ├─ Customer hasn't opted out
         │   └─ Result: PASS
         │
         ├─> Service Quality Filter (20ms)
         │   ├─ Check if service was successful
         │   ├─ Resolution status: "resolved" ✓
         │   └─ Result: PASS
         │
         ├─> Satisfaction Threshold Filter (10ms)
         │   ├─ Check customer satisfaction history
         │   ├─ If already very high (>4.8), skip survey
         │   ├─ Current: 4.2 (needs feedback)
         │   └─ Result: PASS
         │
         ▼
    Candidate eligible for scoring
         │
         ▼

T+110ms: Score Lambda Processing
┌─────────────────────────────────────┐
│     Feature Store Client (20ms)     │
│  - Retrieve customer features       │
│    • survey_completion_rate: 0.75   │
│    • satisfaction_history: 4.2      │
│    • service_frequency: 3/month     │
│  - Retrieve service features        │
│    • service_type: "tech-support"   │
│    • duration: 45 minutes           │
│    • resolution: "resolved"         │
└────────┬────────────────────────────┘
         │ Features retrieved
         ▼
┌─────────────────────────────────────┐
│    SageMaker Scoring Provider (40ms)│
│  - Call survey completion model     │
│  - Call satisfaction prediction     │
│  - Input: customer + service        │
│  - Output: score = 0.88             │
│  - Confidence: HIGH                 │
└────────┬────────────────────────────┘
         │ Score computed
         ▼
┌─────────────────────────────────────┐
│       Score Cache Repository (15ms) │
│  - Cache score in DynamoDB          │
│  - TTL: 30 days                     │
│  - Key: CustomerId:ServiceId        │
└────────┬────────────────────────────┘
         │ Score cached
         ▼

T+185ms: Store Lambda Processing
┌─────────────────────────────────────┐
│   DynamoDB Candidate Repository     │
│  - Write candidate (25ms)           │
│  - Primary key: CustomerId:Program  │
│  - GSI1: Program:Channel:Score      │
│  - GSI2: Program:ServiceType:Date   │
│  - TTL: 30 days from now            │
│  - Attributes:                      │
│    • score: 0.88                    │
│    • serviceId: "S123"              │
│    • serviceType: "tech-support"    │
│    • coolingPeriodEnd: T+3600000ms  │
│    • createdAt: T+0                 │
└────────┬────────────────────────────┘
         │ Candidate stored with cooling period
         ▼
┌─────────────────────────────────────┐
│    Cooling Period Scheduler         │
│  - Schedule SMS delivery for 1 hour │
│  - Use EventBridge Scheduler        │
│  - Target: SMS Channel Lambda       │
│  - Payload: candidateId             │
└─────────────────────────────────────┘

T+210ms: Reactive Processing Complete
Total Latency: 210ms
Candidate ready, SMS scheduled for T+3600000ms (1 hour)

---

T+3600000ms (1 hour later): Cooling Period Ends
┌─────────────────────────────────────┐
│   EventBridge Scheduler Trigger     │
│  - Cooling period complete          │
│  - Invoke SMS Channel Lambda        │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│      SMS Channel Lambda             │
│  - Retrieve candidate from DynamoDB │
│  - Verify still eligible            │
│  - Format SMS message               │
│  - Send via SMS gateway             │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│         SMS Gateway                 │
│  - Send SMS to customer             │
│  - Track delivery status            │
└─────────────────────────────────────┘

End-to-End: Service completion → SMS delivery = 1 hour + 210ms
```

---

## Data Contribution Flow (Customer Interaction)

### SMS Survey Delivery - 1 Hour After Service

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      DATA CONTRIBUTION FLOW                              │
│                   (Customer Survey Submission)                           │
└─────────────────────────────────────────────────────────────────────────┘

T+3600000ms: Customer Receives SMS
┌─────────────────────────────────────┐
│         Customer Phone              │
│  Message: "Hi [Name], how was your  │
│           recent service experience │
│           with [Company]? Please    │
│           rate 1-5 and share        │
│           feedback: [Survey Link]"  │
└────────┬────────────────────────────┘
         │
         │ Customer clicks link (within 10 minutes)
         ▼
┌─────────────────────────────────────┐
│      Mobile Survey Page             │
│  - Pre-filled: Service details      │
│  - Question 1: Overall satisfaction │
│    (1-5 stars)                      │
│  - Question 2: Agent helpfulness    │
│    (1-5 stars)                      │
│  - Question 3: Issue resolution     │
│    (Yes/No)                         │
│  - Optional: Comments (500 char)    │
└────────┬────────────────────────────┘
         │
         │ Customer completes survey (2 minutes)
         ▼
┌─────────────────────────────────────┐
│      Survey Service API             │
│  - Validate responses               │
│  - Store in survey database         │
│  - Publish survey event             │
│  - Calculate satisfaction score     │
│  - Trigger alerts if negative       │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│    Candidate Status Update          │
│  - Mark candidate as "consumed"     │
│  - Record delivery timestamp        │
│  - Record submission timestamp      │
│  - Update metrics                   │
└─────────────────────────────────────┘

Survey Response Analysis:
┌─────────────────────────────────────┐
│   Real-Time Alert System            │
│  IF satisfaction < 3:               │
│    - Alert service manager          │
│    - Create follow-up ticket        │
│    - Escalate to QA team            │
│  IF satisfaction >= 4:              │
│    - Update agent performance       │
│    - Add to positive feedback pool  │
└─────────────────────────────────────┘

Contribution Complete
Survey Collected: 3 questions + optional comment
Response Time: 12 minutes (SMS sent → survey completed)
```

---

## Alternative Delivery Channel: Email

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      EMAIL SURVEY FLOW                                   │
│                  (Alternative to SMS)                                    │
└─────────────────────────────────────────────────────────────────────────┘

T+3600000ms (1 hour later): Email Sent
┌─────────────────────────────────────┐
│   Email Channel Adapter             │
│  - Retrieve candidate               │
│  - Format email with survey link    │
│  - Personalize with service details │
│  - Send via email service           │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│         Customer Inbox              │
│  Subject: "How was your service     │
│           experience?"              │
│  Body: Personalized survey request  │
│  CTA: "Take 2-Minute Survey" button │
└────────┬────────────────────────────┘
         │
         │ Customer opens email (within 2 hours)
         ▼
┌─────────────────────────────────────┐
│      Customer Opens Email           │
│  - Tracking pixel fires             │
│  - Open event recorded              │
└────────┬────────────────────────────┘
         │
         │ Customer clicks CTA
         ▼
┌─────────────────────────────────────┐
│      Survey Submission Page         │
│  - Same 3 questions as SMS          │
│  - Customer completes survey        │
└─────────────────────────────────────┘

Email Survey Metrics:
- Open Rate: 45%
- Click-Through Rate: 30%
- Completion Rate: 25%
```

---

## Scheduled Jobs

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           SCHEDULED JOBS                                 │
└─────────────────────────────────────────────────────────────────────────┘

Hourly Schedule:
┌──────────┬────────────────────────────────────────────────────────────┐
│   Time   │                        Job                                 │
├──────────┼────────────────────────────────────────────────────────────┤
│ Every    │ Negative Feedback Alert Check                              │
│ Hour     │ - Query surveys with satisfaction < 3                      │
│ :00      │ - Create follow-up tickets                                 │
│          │ - Alert service managers                                   │
│          │ - Duration: 5 minutes                                      │
└──────────┴────────────────────────────────────────────────────────────┘

Daily Schedule:
┌──────────┬────────────────────────────────────────────────────────────┐
│   Time   │                        Job                                 │
├──────────┼────────────────────────────────────────────────────────────┤
│ 3:00 AM  │ Data Warehouse Export                                      │
│          │ - Export surveys to S3 (Parquet)                           │
│          │ - Update Glue catalog                                      │
│          │ - Duration: 20 minutes                                     │
├──────────┼────────────────────────────────────────────────────────────┤
│ 6:00 AM  │ Daily Satisfaction Report                                  │
│          │ - Aggregate yesterday's surveys                            │
│          │ - Calculate team satisfaction scores                       │
│          │ - Generate report for managers                             │
│          │ - Duration: 15 minutes                                     │
├──────────┼────────────────────────────────────────────────────────────┤
│ 11:59 PM │ Metrics Aggregation                                        │
│          │ - Aggregate daily metrics                                  │
│          │ - Publish to CloudWatch                                    │
│          │ - Generate daily report                                    │
│          │ - Duration: 5 minutes                                      │
└──────────┴────────────────────────────────────────────────────────────┘

Weekly Schedule:
┌──────────┬────────────────────────────────────────────────────────────┐
│   Time   │                        Job                                 │
├──────────┼────────────────────────────────────────────────────────────┤
│ Sunday   │ Cleanup Expired Candidates                                 │
│ 1:00 AM  │ - DynamoDB TTL handles automatic deletion                  │
│          │ - Verify cleanup completed                                 │
│          │ - Duration: 10 minutes                                     │
├──────────┼────────────────────────────────────────────────────────────┤
│ Sunday   │ Model Retraining                                           │
│ 3:00 AM  │ - Retrain survey completion model                          │
│          │ - Retrain satisfaction prediction model                    │
│          │ - Evaluate model performance                               │
│          │ - Deploy if improved                                       │
│          │ - Duration: 2 hours                                        │
├──────────┼────────────────────────────────────────────────────────────┤
│ Monday   │ Weekly Service Quality Review                              │
│ 9:00 AM  │ - Aggregate last week's surveys                            │
│          │ - Identify trends and issues                               │
│          │ - Generate executive report                                │
│          │ - Duration: 30 minutes                                     │
└──────────┴────────────────────────────────────────────────────────────┘
```

---

## Metrics & Success Criteria

### Processing Metrics
- **Reactive Latency**: 210ms average (target: < 500ms)
- **P99 Latency**: 350ms (target: < 1000ms)
- **Throughput**: 2,000 events/second
- **Filter Pass Rate**: 70% (eligible services)
- **Cooling Period**: 1 hour (configurable)

### Delivery Metrics
- **SMS Delivery Rate**: 98%
- **Email Delivery Rate**: 95%
- **SMS Open Rate**: 85% (link clicks)
- **Email Open Rate**: 45%

### Engagement Metrics
- **SMS Survey Completion Rate**: 35%
- **Email Survey Completion Rate**: 25%
- **Overall Survey Rate**: 32% (combined channels)
- **Average Response Time**: 12 minutes (SMS), 2 hours (email)

### Business Metrics
- **Surveys Collected**: 10,000 per day
- **Cost per Survey**: $0.05 ($500 daily cost / 10,000 surveys)
- **Satisfaction Score**: 4.3/5.0 average
- **Issue Resolution Rate**: 92%
- **Follow-up Ticket Creation**: 8% (negative feedback)

### Technical Metrics
- **Serving API Latency**: P99 = 15ms (target: < 30ms)
- **Cache Hit Rate**: 85%
- **Error Rate**: 0.01%
- **Availability**: 99.97%
- **Deduplication Rate**: 99.8%

---

## Component Configuration

### Program Configuration
```yaml
programId: "service-surveys"
programName: "Service Experience Surveys"
enabled: true
marketplaces: ["US", "UK", "DE", "FR", "JP", "IN"]

eventSources:
  - sourceId: "service-management-events"
    type: "eventbridge"
    config:
      eventBusName: "service-platform"
      eventPattern:
        source: ["service.management"]
        detail-type: ["ServiceCompleted"]
        detail:
          resolution: ["resolved", "partially-resolved"]

scoringModels:
  - modelId: "survey-completion-v2"
    endpoint: "sagemaker://survey-completion-v2"
    features: ["survey_completion_rate", "satisfaction_history", "service_frequency"]
  - modelId: "satisfaction-prediction-v1"
    endpoint: "sagemaker://satisfaction-prediction-v1"
    features: ["service_type", "duration", "resolution"]

filterChain:
  - filterId: "trust"
    type: "trust"
    order: 1
  - filterId: "eligibility"
    type: "eligibility"
    order: 2
  - filterId: "service-quality"
    type: "business-rule"
    order: 3
    parameters:
      requiredResolution: ["resolved", "partially-resolved"]
  - filterId: "satisfaction-threshold"
    type: "business-rule"
    order: 4
    parameters:
      maxSatisfactionScore: 4.8  # Skip if already very satisfied
  - filterId: "frequency-cap"
    type: "business-rule"
    order: 5
    parameters:
      maxSurveysPerServiceType: 1
      periodDays: 30

channels:
  - channelId: "sms"
    type: "sms"
    enabled: true
    priority: 1
    config:
      coolingPeriodMinutes: 60
      message: "Hi {customerName}, how was your recent service experience with {companyName}? Please rate 1-5 and share feedback: {surveyLink}"
      maxLength: 160
  - channelId: "email"
    type: "email"
    enabled: true
    priority: 2
    config:
      coolingPeriodMinutes: 60
      templateId: "service-survey-v1"
      subject: "How was your service experience?"

deduplication:
  enabled: true
  ttlDays: 30
  keyFormat: "{customerId}:{serviceId}:{programId}"

alerting:
  enabled: true
  negativeFeedbackThreshold: 3
  alertChannels: ["email", "slack"]
  escalationRules:
    - condition: "satisfaction < 2"
      action: "create-ticket"
      assignTo: "service-manager"
    - condition: "satisfaction < 3"
      action: "alert"
      recipients: ["qa-team@company.com"]

candidateTTLDays: 30
```

---

## Summary

This use case demonstrates:
- ✅ **Reactive processing** with cooling period (210ms + 1 hour)
- ✅ **Event-driven architecture** (EventBridge → Lambda)
- ✅ **Intelligent cooling period** (1 hour delay for better response)
- ✅ **Multi-channel delivery** (SMS + email)
- ✅ **Real-time alerting** (negative feedback triggers immediate action)
- ✅ **High engagement** (35% SMS completion rate)
- ✅ **Cost efficiency** ($0.05 per survey)
- ✅ **Deduplication** (99.8% duplicate prevention)
- ✅ **Service quality tracking** (4.3/5.0 average satisfaction)
- ✅ **Automated follow-up** (8% negative feedback → tickets)
