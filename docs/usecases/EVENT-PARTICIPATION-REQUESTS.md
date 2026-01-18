# Use Case: Event Participation Requests

## Overview

**Business Goal**: Maximize attendance at virtual events (webinars, product launches, training sessions) by targeting likely participants with personalized invitations.

**Processing Mode**: Batch (Weekly scheduled job) + Multi-Channel Campaign

**Actors**:
- Potential attendee (customer)
- Events team
- Marketing team
- Data warehouse
- Multi-channel delivery services (Email, In-App, Push)

---

## Actor Interaction Diagram

```
┌──────────┐         ┌──────────────┐         ┌─────────────────┐
│ Customer │         │ Events Team  │         │ Marketing Team  │
└────┬─────┘         └──────┬───────┘         └────────┬────────┘
     │                      │                          │
     │                      │ 1. Creates Event         │
     │                      │    (Webinar)             │
     │                      ├─────────────────────────>│
     │                      │                          │
     │                      │ 2. Defines Target        │
     │                      │    Audience              │
     │                      │<─────────────────────────┤
     │                      │                          │
     │                      │                          │ 3. Schedules
     │                      │                          │    Weekly Batch
     │                      │                          ├────────────┐
     │                      │                          │            │
     │                      │                          │<───────────┘
     │                      │                          │
     │ 4. Receives Email    │                          │
     │    Invitation        │<─────────────────────────┤
     │<─────────────────────┤    (Primary Channel)     │
     │                      │                          │
     │ 5. Sees In-App       │                          │
     │    Banner            │<─────────────────────────┤
     │<─────────────────────┤    (Secondary Channel)   │
     │                      │                          │
     │ 6. Registers for     │                          │
     │    Event             │                          │
     ├─────────────────────>│                          │
     │                      │                          │
     │ 7. Receives Push     │                          │
     │    Reminder (1 day)  │<─────────────────────────┤
     │<─────────────────────┤    (Reminder Channel)    │
     │                      │                          │
     │ 8. Attends Event     │                          │
     ├─────────────────────>│                          │
     │                      │                          │
     │                      │ 9. Reviews Metrics       │
     │                      │<─────────────────────────┤
     │                      │                          │
     ▼                      ▼                          ▼
```

---

## Data Ingestion Flow (Batch Processing)

### Weekly Batch Job - Monday 2:00 AM

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         DATA INGESTION FLOW                              │
│                    (Batch - Weekly on Monday 2:00 AM)                    │
└─────────────────────────────────────────────────────────────────────────┘

Step 1: ETL Lambda (2:00 AM - 2:45 AM)
┌──────────────────┐
│  Data Warehouse  │
│  (Snowflake)     │
└────────┬─────────┘
         │ Query: SELECT customer_id, interest_tags, 
         │        event_attendance_history, engagement_score,
         │        geographic_region, job_role
         │        FROM customer_profiles
         │        WHERE interest_tags CONTAINS 'cloud-computing'
         │        OR interest_tags CONTAINS 'devops'
         │        OR event_attendance_history > 0
         ▼
┌─────────────────────────────────────┐
│    Snowflake Connector              │
│  - Extract 3M customer records      │
│  - Filter by event topic interests  │
│  - Map fields to candidate model    │
│  - Validate schema                  │
└────────┬────────────────────────────┘
         │ 3M raw candidates
         ▼
┌─────────────────────────────────────┐
│         ETL Lambda                  │
│  - Transform to unified model       │
│  - Add context (event, marketplace) │
│  - Set event metadata               │
│  - Deduplicate by customer          │
└────────┬────────────────────────────┘
         │ 2.5M unique candidates
         │ {customerId, eventId, interests, ...}
         ▼

Step 2: Filter Lambda (2:45 AM - 3:15 AM)
┌─────────────────────────────────────┐
│      Filter Chain Executor          │
└────────┬────────────────────────────┘
         │
         ├─> Trust Filter
         │   ├─ Verify customer authenticity
         │   ├─ Check account status
         │   └─ Result: Remove 200K (8%)
         │
         ├─> Eligibility Filter
         │   ├─ Customer hasn't registered for this event
         │   ├─ Event capacity not exceeded
         │   ├─ Customer hasn't opted out
         │   └─ Result: Remove 500K (20%)
         │
         ├─> Interest Match Filter
         │   ├─ Match customer interests with event topics
         │   ├─ Minimum match score: 0.7
         │   └─ Result: Remove 1M (40%)
         │
         ├─> Geographic Filter
         │   ├─ Check if customer in target regions
         │   ├─ Event timezone compatibility
         │   └─ Result: Remove 300K (12%)
         │
         ├─> Frequency Cap Filter
         │   ├─ Max 2 event invitations per month
         │   └─ Result: Remove 400K (16%)
         │
         ▼
    100,000 eligible candidates
         │
         ▼

Step 3: Score Lambda (3:15 AM - 3:45 AM)
┌─────────────────────────────────────┐
│     Feature Store Client            │
│  - Retrieve customer features       │
│    • engagement_score               │
│    • event_attendance_history       │
│    • interest_affinity              │
│    • job_role                       │
│  - Retrieve event features          │
│    • topic                          │
│    • speaker_popularity             │
│    • event_type                     │
│    • capacity                       │
└────────┬────────────────────────────┘
         │ Features for 100,000 candidates
         ▼
┌─────────────────────────────────────┐
│    SageMaker Scoring Provider       │
│  - Call event attendance model      │
│  - Call interest affinity model     │
│  - Batch scoring (5000 at a time)   │
│  - Combined score: 0.0 - 1.0        │
└────────┬────────────────────────────┘
         │ Scores computed
         ▼
┌─────────────────────────────────────┐
│       Score Cache Repository        │
│  - Cache scores in DynamoDB         │
│  - TTL: 14 days                     │
└────────┬────────────────────────────┘
         │ 100,000 scored candidates
         │ Score distribution:
         │   High (0.7-1.0): 30,000
         │   Medium (0.4-0.7): 50,000
         │   Low (0.0-0.4): 20,000
         ▼

Step 4: Store Lambda (3:45 AM - 4:00 AM)
┌─────────────────────────────────────┐
│   DynamoDB Candidate Repository     │
│  - Batch write (25 items per batch) │
│  - Primary key: CustomerId:Program  │
│  - GSI1: Program:Channel:Score      │
│  - GSI2: Program:EventId:Date       │
│  - TTL: 14 days from now            │
└────────┬────────────────────────────┘
         │ 100,000 candidates stored
         ▼
┌─────────────────────────────────────┐
│         DynamoDB Tables             │
│  ┌─────────────────────────────┐   │
│  │ Candidates Table            │   │
│  │ - 100,000 new items         │   │
│  │ - Partitioned by customerId │   │
│  │ - Indexed by program+score  │   │
│  │ - Indexed by eventId+date   │   │
│  └─────────────────────────────┘   │
└─────────────────────────────────────┘

Batch Job Complete: 4:00 AM
Total Time: 2 hours
Throughput: 13,889 candidates/minute
```

---

## Data Contribution Flow (Multi-Channel Campaign)

### Week 1: Primary Email Campaign - Monday 10:00 AM

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      DATA CONTRIBUTION FLOW                              │
│                   (Multi-Channel Event Campaign)                         │
└─────────────────────────────────────────────────────────────────────────┘

Monday 10:00 AM: Email Channel (Primary)
┌─────────────────────────────────────┐
│      Serving API Query              │
│  Query: GetCandidatesForChannel     │
│  Params:                            │
│    - channel: "email"               │
│    - program: "event-invitations"   │
│    - minScore: 0.5                  │
│    - limit: 50,000                  │
└────────┬────────────────────────────┘
         │ Query DynamoDB GSI1
         │ GSI1PK = "PROGRAM#events#CHANNEL#email"
         │ Filter: score >= 0.5
         ▼
┌─────────────────────────────────────┐
│    Top 50,000 Candidates            │
│  - Sorted by score (descending)     │
│  - All have score >= 0.5            │
│  - Capacity limit enforced          │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│   Email Channel Adapter             │
│  - Group by marketplace & timezone  │
│  - Create campaign per segment      │
│  - Set template: "event-invitation" │
│  - Personalize with event details   │
│  - Include calendar invite (.ics)   │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│   Email Campaign Service            │
│  - Create 5 campaigns (US, UK, etc) │
│  - Upload recipient lists           │
│  - Schedule delivery                │
│  - Enable tracking                  │
└────────┬────────────────────────────┘
         │ 50,000 emails sent
         ▼

Monday 10:00 AM - 6:00 PM: Customer Engagement
┌─────────────────────────────────────┐
│         Customer Inbox              │
│  Subject: "Join us for [Event Name] │
│           on [Date]"                │
│  Body: Personalized invitation      │
│  CTA: "Register Now" button         │
│  Attachment: Calendar invite        │
└────────┬────────────────────────────┘
         │
         │ 15,000 customers open email (30%)
         ▼
┌─────────────────────────────────────┐
│      Customer Opens Email           │
│  - Tracking pixel fires             │
│  - Open event recorded              │
└────────┬────────────────────────────┘
         │
         │ 7,500 customers click CTA (15% CTR)
         ▼
┌─────────────────────────────────────┐
│    Event Registration Page          │
│  - Pre-filled: Customer info        │
│  - Form: Confirm attendance         │
│  - Form: Add to calendar            │
│  - Form: Dietary preferences (opt)  │
└────────┬────────────────────────────┘
         │
         │ 5,000 customers register (10% conversion)
         ▼
┌─────────────────────────────────────┐
│      Event Service API              │
│  - Validate registration            │
│  - Store in event database          │
│  - Send confirmation email          │
│  - Add to calendar                  │
│  - Update capacity counter          │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│    Candidate Status Update          │
│  - Mark candidate as "registered"   │
│  - Record delivery timestamp        │
│  - Record registration timestamp    │
│  - Update metrics                   │
└─────────────────────────────────────┘

Email Campaign Results:
- Sent: 50,000
- Opened: 15,000 (30%)
- Clicked: 7,500 (15%)
- Registered: 5,000 (10%)
```

---

### Week 1: Secondary In-App Campaign - Monday 2:00 PM

```
Monday 2:00 PM: In-App Channel (Secondary)
┌─────────────────────────────────────┐
│      Serving API Query              │
│  Query: GetCandidatesForChannel     │
│  Params:                            │
│    - channel: "in-app"              │
│    - program: "event-invitations"   │
│    - minScore: 0.6                  │
│    - limit: 30,000                  │
│    - exclude: already registered    │
└────────┬────────────────────────────┘
         │ Query DynamoDB GSI1
         │ Filter: not registered via email
         ▼
┌─────────────────────────────────────┐
│    Top 30,000 Candidates            │
│  - Sorted by score (descending)     │
│  - Exclude email registrants        │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│   In-App Channel Adapter            │
│  - Create in-app banner campaign    │
│  - Set display rules                │
│  - Personalize with event details   │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│   In-App Notification Service       │
│  - Configure banner placement       │
│  - Set display frequency            │
│  - Enable click tracking            │
└────────┬────────────────────────────┘
         │ 30,000 in-app banners configured
         ▼

Monday 2:00 PM - Friday 6:00 PM: In-App Engagement
┌─────────────────────────────────────┐
│      Customer Opens App             │
│  - Banner displayed on home screen  │
│  - Message: "Join [Event Name]"     │
│  - CTA: "Learn More"                │
└────────┬────────────────────────────┘
         │
         │ 12,000 customers see banner (40% reach)
         ▼
┌─────────────────────────────────────┐
│      Customer Clicks Banner         │
│  - 3,600 clicks (30% CTR)           │
│  - Navigate to event details        │
└────────┬────────────────────────────┘
         │
         │ 1,800 customers register (5% conversion)
         ▼
┌─────────────────────────────────────┐
│      Event Service API              │
│  - Same registration flow as email  │
└─────────────────────────────────────┘

In-App Campaign Results:
- Configured: 30,000
- Displayed: 12,000 (40% reach)
- Clicked: 3,600 (30% CTR)
- Registered: 1,800 (5% conversion)
```

---

### Week 2: Reminder Push Notification - 1 Day Before Event

```
Sunday 10:00 AM (1 day before event): Push Notification (Reminder)
┌─────────────────────────────────────┐
│      Serving API Query              │
│  Query: GetRegisteredCandidates     │
│  Params:                            │
│    - program: "event-invitations"   │
│    - status: "registered"           │
│    - eventDate: tomorrow            │
└────────┬────────────────────────────┘
         │ Query DynamoDB
         │ Filter: registered customers
         ▼
┌─────────────────────────────────────┐
│    6,800 Registered Customers       │
│  - Email registrants: 5,000         │
│  - In-app registrants: 1,800        │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│   Push Notification Channel         │
│  - Create reminder campaign         │
│  - Personalize with event time      │
│  - Include join link                │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│   Push Notification Service         │
│  - Send push to 6,800 customers     │
│  - Title: "[Event Name] Tomorrow!"  │
│  - Body: "Join us at [Time]"        │
│  - Action: "Add to Calendar"        │
└────────┬────────────────────────────┘
         │ 6,800 push notifications sent
         ▼

Sunday 10:00 AM - Monday 10:00 AM: Reminder Engagement
┌─────────────────────────────────────┐
│      Customer Receives Push         │
│  - Notification displayed           │
│  - 6,120 customers open (90%)       │
└────────┬────────────────────────────┘
         │
         │ 5,440 customers click (80% of opens)
         ▼
┌─────────────────────────────────────┐
│      Event Reminder Confirmed       │
│  - Calendar updated                 │
│  - Join link saved                  │
└─────────────────────────────────────┘

Push Reminder Results:
- Sent: 6,800
- Opened: 6,120 (90%)
- Clicked: 5,440 (80%)
```

---

### Event Day: Attendance Tracking

```
Monday 10:00 AM (Event Day): Attendance
┌─────────────────────────────────────┐
│      Event Platform                 │
│  - 6,800 registered customers       │
│  - 5,950 customers attend (87.5%)   │
│  - Average watch time: 45 minutes   │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│    Attendance Tracking              │
│  - Record attendance                │
│  - Track engagement                 │
│  - Collect feedback                 │
└─────────────────────────────────────┘

Final Campaign Results:
- Total Invited: 50,000 (email) + 30,000 (in-app) = 80,000
- Total Registered: 6,800 (8.5%)
- Total Attended: 5,950 (87.5% of registered)
- Multi-Channel Attribution:
  • Email: 5,000 (73.5%)
  • In-App: 1,800 (26.5%)
```

---

## Scheduled Jobs

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           SCHEDULED JOBS                                 │
└─────────────────────────────────────────────────────────────────────────┘

Weekly Schedule:
┌──────────┬────────────────────────────────────────────────────────────┐
│   Time   │                        Job                                 │
├──────────┼────────────────────────────────────────────────────────────┤
│ Monday   │ Batch Ingestion Workflow (ETL → Filter → Score → Store)   │
│ 2:00 AM  │ - Process customer profiles for upcoming events            │
│          │ - Duration: 2 hours                                        │
│          │ - Output: 100,000 candidates                               │
├──────────┼────────────────────────────────────────────────────────────┤
│ Monday   │ Data Warehouse Export                                      │
│ 4:30 AM  │ - Export candidates to S3 (Parquet)                        │
│          │ - Update Glue catalog                                      │
│          │ - Duration: 30 minutes                                     │
├──────────┼────────────────────────────────────────────────────────────┤
│ Monday   │ Email Campaign Delivery (Primary)                          │
│ 10:00 AM │ - Retrieve top candidates (score >= 0.5)                   │
│          │ - Create email campaigns                                   │
│          │ - Send 50,000 emails                                       │
│          │ - Duration: 1 hour                                         │
├──────────┼────────────────────────────────────────────────────────────┤
│ Monday   │ In-App Campaign Delivery (Secondary)                       │
│ 2:00 PM  │ - Retrieve remaining candidates (score >= 0.6)             │
│          │ - Configure in-app banners                                 │
│          │ - Duration: 30 minutes                                     │
└──────────┴────────────────────────────────────────────────────────────┘

Event-Based Schedule:
┌──────────┬────────────────────────────────────────────────────────────┐
│   Time   │                        Job                                 │
├──────────┼────────────────────────────────────────────────────────────┤
│ 1 Day    │ Push Notification Reminder                                 │
│ Before   │ - Query registered customers                               │
│ Event    │ - Send push notifications                                  │
│          │ - Duration: 15 minutes                                     │
├──────────┼────────────────────────────────────────────────────────────┤
│ Event    │ Attendance Tracking                                        │
│ Day      │ - Track customer attendance                                │
│          │ - Record engagement metrics                                │
│          │ - Duration: Event duration                                 │
├──────────┼────────────────────────────────────────────────────────────┤
│ 1 Day    │ Post-Event Survey                                          │
│ After    │ - Send survey to attendees                                 │
│ Event    │ - Collect feedback                                         │
│          │ - Duration: 30 minutes                                     │
└──────────┴────────────────────────────────────────────────────────────┘

Daily Schedule:
┌──────────┬────────────────────────────────────────────────────────────┐
│   Time   │                        Job                                 │
├──────────┼────────────────────────────────────────────────────────────┤
│ 11:59 PM │ Metrics Aggregation                                        │
│          │ - Aggregate daily metrics                                  │
│          │ - Publish to CloudWatch                                    │
│          │ - Generate daily report                                    │
│          │ - Duration: 10 minutes                                     │
└──────────┴────────────────────────────────────────────────────────────┘
```

---

## Metrics & Success Criteria

### Processing Metrics
- **Batch Duration**: 2 hours (target: < 3 hours)
- **Throughput**: 13,889 candidates/minute
- **Filter Pass Rate**: 4% (100K / 2.5M)
- **High Score Rate**: 30% (30K / 100K with score >= 0.7)

### Delivery Metrics
- **Email Send Rate**: 50,000 per event
- **Email Open Rate**: 30% (15,000 opens)
- **Email Click-Through Rate**: 15% (7,500 clicks)
- **Email Conversion Rate**: 10% (5,000 registrations)
- **In-App Display Rate**: 40% (12,000 displays)
- **In-App Click-Through Rate**: 30% (3,600 clicks)
- **In-App Conversion Rate**: 5% (1,800 registrations)
- **Push Notification Open Rate**: 90% (6,120 opens)

### Business Metrics
- **Total Registrations**: 6,800 (8.5% of invited)
- **Attendance Rate**: 87.5% (5,950 / 6,800)
- **Cost per Registration**: $0.50 ($3,400 campaign cost / 6,800 registrations)
- **Cost per Attendee**: $0.57 ($3,400 / 5,950)
- **Event Satisfaction**: 4.7/5.0 average rating
- **Multi-Channel Lift**: 36% (vs email-only)

### Technical Metrics
- **Serving API Latency**: P99 = 20ms (target: < 30ms)
- **Cache Hit Rate**: 80%
- **Error Rate**: 0.015%
- **Availability**: 99.95%

---

## Component Configuration

### Program Configuration
```yaml
programId: "event-invitations"
programName: "Event Participation Requests"
enabled: true
marketplaces: ["US", "UK", "DE", "FR", "JP", "IN", "BR", "AU"]

dataConnectors:
  - connectorId: "snowflake-customer-profiles"
    type: "snowflake"
    config:
      account: "company.us-east-1"
      database: "customer_data"
      schema: "profiles"
      query: |
        SELECT customer_id, interest_tags, event_attendance_history,
               engagement_score, geographic_region, job_role
        FROM customer_profiles
        WHERE interest_tags CONTAINS ?
        OR event_attendance_history > 0

scoringModels:
  - modelId: "event-attendance-v3"
    endpoint: "sagemaker://event-attendance-v3"
    features: ["engagement_score", "event_attendance_history", "interest_affinity"]
  - modelId: "interest-affinity-v2"
    endpoint: "sagemaker://interest-affinity-v2"
    features: ["interest_tags", "job_role", "event_topic"]

filterChain:
  - filterId: "trust"
    type: "trust"
    order: 1
  - filterId: "eligibility"
    type: "eligibility"
    order: 2
  - filterId: "interest-match"
    type: "business-rule"
    order: 3
    parameters:
      minMatchScore: 0.7
  - filterId: "geographic"
    type: "business-rule"
    order: 4
    parameters:
      targetRegions: ["US", "UK", "DE", "FR"]
      timezoneCompatibility: true
  - filterId: "frequency-cap"
    type: "business-rule"
    order: 5
    parameters:
      maxInvitationsPerMonth: 2

channels:
  - channelId: "email"
    type: "email"
    enabled: true
    priority: 1
    config:
      templateId: "event-invitation-v3"
      sendTime: "10:00"
      timezone: "local"
      includeCalendarInvite: true
  - channelId: "in-app"
    type: "in-app"
    enabled: true
    priority: 2
    config:
      bannerType: "event-invitation"
      displayDuration: 7  # days
      placement: "home-screen"
  - channelId: "push"
    type: "push"
    enabled: true
    priority: 3
    config:
      reminderDaysBefore: 1
      title: "{eventName} Tomorrow!"
      body: "Join us at {eventTime}"

eventConfig:
  capacityLimit: 10000
  registrationDeadlineDays: 1
  reminderSchedule:
    - daysBefore: 1
      channel: "push"
    - daysBefore: 0
      channel: "email"
      time: "09:00"

batchSchedule: "cron(0 2 ? * MON *)"  # Weekly on Monday at 2 AM UTC
candidateTTLDays: 14
```

---

## Summary

This use case demonstrates:
- ✅ **Batch processing** at scale (2.5M → 100K candidates)
- ✅ **Multi-stage pipeline** (ETL → Filter → Score → Store)
- ✅ **Dual ML models** (event attendance + interest affinity)
- ✅ **Multi-channel campaign** (email + in-app + push)
- ✅ **High registration rate** (8.5% of invited)
- ✅ **High attendance rate** (87.5% of registered)
- ✅ **Cost efficiency** ($0.57 per attendee)
- ✅ **Scheduled automation** (weekly batch + multi-channel delivery)
- ✅ **Event-based reminders** (1 day before event)
- ✅ **Multi-channel attribution** (73.5% email, 26.5% in-app)
- ✅ **Capacity management** (10K limit enforced)
