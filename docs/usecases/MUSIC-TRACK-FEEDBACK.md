# Use Case: Music Track Feedback

## Overview

**Business Goal**: Gather feedback on new music releases to inform playlist curation and artist recommendations.

**Processing Mode**: Batch (Daily scheduled job)

**Actors**:
- Music listener (customer)
- Music curation team
- Artist relations team
- Data warehouse
- Email campaign service

---

## Actor Interaction Diagram

```
┌──────────┐         ┌──────────────┐         ┌─────────────────┐
│ Listener │         │ Curation Team│         │ Artist Relations│
└────┬─────┘         └──────┬───────┘         └────────┬────────┘
     │                      │                          │
     │ 1. Listens to        │                          │
     │    New Track         │                          │
     ├──────────────────────┼──────────────────────────┤
     │                      │                          │
     │ 2. Completes         │                          │
     │    Full Playback     │                          │
     ├──────────────────────┼──────────────────────────┤
     │                      │                          │
     │                      │ 3. Configures            │
     │                      │    Feedback Program      │
     │                      ├─────────────────────────>│
     │                      │                          │
     │                      │                          │ 4. Schedules
     │                      │                          │    Daily Batch
     │                      │                          ├────────────┐
     │                      │                          │            │
     │                      │                          │<───────────┘
     │                      │                          │
     │ 5. Receives Feedback │                          │
     │    Request Email     │<─────────────────────────┤
     │<─────────────────────┤                          │
     │                      │                          │
     │ 6. Provides Feedback │                          │
     │    (👍/👎 + comment) │                          │
     ├─────────────────────>│                          │
     │                      │                          │
     │                      │ 7. Updates Playlists     │
     │                      │<─────────────────────────┤
     │                      │                          │
     ▼                      ▼                          ▼
```

---

## Data Ingestion Flow (Batch Processing)

### Daily Batch Job - 2:00 AM

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         DATA INGESTION FLOW                              │
│                      (Batch - Daily at 2:00 AM)                          │
└─────────────────────────────────────────────────────────────────────────┘

Step 1: ETL Lambda (2:00 AM - 2:30 AM)
┌──────────────────┐
│  Data Warehouse  │
│  (Redshift)      │
└────────┬─────────┘
         │ Query: SELECT listener_id, track_id, play_count,
         │        completion_rate, engagement_score
         │        FROM music_streams
         │        WHERE track_release_date >= CURRENT_DATE - 7
         │        AND completion_rate >= 0.95
         │        AND play_count >= 1
         ▼
┌─────────────────────────────────────┐
│    Data Warehouse Connector         │
│  - Extract 2M listening records     │
│  - Filter: New tracks (last 7 days) │
│  - Filter: Full playback (95%+)     │
│  - Map fields to candidate model    │
└────────┬────────────────────────────┘
         │ 2M raw candidates
         ▼
┌─────────────────────────────────────┐
│         ETL Lambda                  │
│  - Transform to unified model       │
│  - Add context (marketplace, genre) │
│  - Set event metadata               │
│  - Deduplicate by listener+track    │
└────────┬────────────────────────────┘
         │ 1.5M unique candidates
         │ {listenerId, trackId, playbackDate, ...}
         ▼

Step 2: Filter Lambda (2:30 AM - 2:45 AM)
┌─────────────────────────────────────┐
│      Filter Chain Executor          │
└────────┬────────────────────────────┘
         │
         ├─> Trust Filter
         │   ├─ Verify listener authenticity
         │   ├─ Check for bot activity
         │   └─ Result: Remove 100K (6.7%)
         │
         ├─> Eligibility Filter
         │   ├─ Listener hasn't given feedback for this track
         │   ├─ Track is eligible for feedback
         │   ├─ Listener hasn't opted out
         │   └─ Result: Remove 400K (26.7%)
         │
         ├─> Engagement Filter
         │   ├─ Check listener engagement score
         │   ├─ Minimum score: 0.6
         │   └─ Result: Remove 500K (33.3%)
         │
         ├─> Frequency Cap Filter
         │   ├─ Max 2 feedback requests per week
         │   └─ Result: Remove 300K (20%)
         │
         ▼
    200,000 eligible candidates
         │
         ▼

Step 3: Score Lambda (2:45 AM - 3:05 AM)
┌─────────────────────────────────────┐
│     Feature Store Client            │
│  - Retrieve listener features       │
│    • engagement_score               │
│    • feedback_history               │
│    • music_taste_profile            │
│    • listening_frequency            │
│  - Retrieve track features          │
│    • genre                          │
│    • artist_popularity              │
│    • release_date                   │
│    • similar_track_ratings          │
└────────┬────────────────────────────┘
         │ Features for 200,000 candidates
         ▼
┌─────────────────────────────────────┐
│    SageMaker Scoring Provider       │
│  - Call feedback propensity model   │
│  - Call music affinity model        │
│  - Batch scoring (2000 at a time)   │
│  - Combined score: 0.0 - 1.0        │
└────────┬────────────────────────────┘
         │ Scores computed
         ▼
┌─────────────────────────────────────┐
│       Score Cache Repository        │
│  - Cache scores in DynamoDB         │
│  - TTL: 14 days                     │
└────────┬────────────────────────────┘
         │ 200,000 scored candidates
         │ Score distribution:
         │   High (0.7-1.0): 60,000
         │   Medium (0.4-0.7): 100,000
         │   Low (0.0-0.4): 40,000
         ▼

Step 4: Store Lambda (3:05 AM - 3:20 AM)
┌─────────────────────────────────────┐
│   DynamoDB Candidate Repository     │
│  - Batch write (25 items per batch) │
│  - Primary key: ListenerId:Program  │
│  - GSI1: Program:Channel:Score      │
│  - GSI2: Program:TrackId:Date       │
│  - TTL: 14 days from now            │
└────────┬────────────────────────────┘
         │ 200,000 candidates stored
         ▼
┌─────────────────────────────────────┐
│         DynamoDB Tables             │
│  ┌─────────────────────────────┐   │
│  │ Candidates Table            │   │
│  │ - 200,000 new items         │   │
│  │ - Partitioned by listenerId │   │
│  │ - Indexed by program+score  │   │
│  │ - Indexed by trackId+date   │   │
│  └─────────────────────────────┘   │
└─────────────────────────────────────┘

Batch Job Complete: 3:20 AM
Total Time: 1 hour 20 minutes
Throughput: 10,000 candidates/minute
```

---

## Data Contribution Flow (Listener Interaction)

### Multi-Channel Delivery - 10:00 AM

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      DATA CONTRIBUTION FLOW                              │
│                   (Listener Feedback Submission)                         │
└─────────────────────────────────────────────────────────────────────────┘

Step 1: Email Channel Adapter (10:00 AM)
┌─────────────────────────────────────┐
│      Serving API Query              │
│  Query: GetCandidatesForChannel     │
│  Params:                            │
│    - channel: "email"               │
│    - program: "music-feedback"      │
│    - minScore: 0.6                  │
│    - limit: 100,000                 │
└────────┬────────────────────────────┘
         │ Query DynamoDB GSI1
         │ GSI1PK = "PROGRAM#music#CHANNEL#email"
         │ Filter: score >= 0.6
         ▼
┌─────────────────────────────────────┐
│    Top 100,000 Candidates           │
│  - Sorted by score (descending)     │
│  - All have score >= 0.6            │
│  - Grouped by track (max 10K/track) │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│   Email Channel Adapter             │
│  - Group by marketplace & genre     │
│  - Create campaign per segment      │
│  - Set template: "track-feedback"   │
│  - Set send time: 10 AM local       │
│  - Personalize with track info      │
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
         │ 100,000 emails sent
         ▼

Step 2: Listener Receives Email (10:00 AM - 12:00 PM)
┌─────────────────────────────────────┐
│         Listener Inbox              │
│  Subject: "What did you think of    │
│           [Track Name]?"            │
│  Body: Personalized feedback request│
│  CTA: "Share Your Thoughts" button  │
└────────┬────────────────────────────┘
         │
         │ 30,000 listeners open email (30%)
         ▼
┌─────────────────────────────────────┐
│      Listener Opens Email           │
│  - Tracking pixel fires             │
│  - Open event recorded              │
└────────┬────────────────────────────┘
         │
         │ 18,000 listeners click CTA (18% CTR)
         ▼
┌─────────────────────────────────────┐
│    Feedback Submission Page         │
│  - Pre-filled: Track, Listener      │
│  - Form: Thumbs up/down             │
│  - Form: Optional comment (200 char)│
│  - Form: Genre tags (multi-select)  │
└────────┬────────────────────────────┘
         │
         │ 15,000 listeners submit (15% conversion)
         ▼
┌─────────────────────────────────────┐
│      Music Feedback Service API     │
│  - Validate feedback                │
│  - Store in feedback database       │
│  - Publish feedback event           │
│  - Update track metrics             │
│  - Trigger playlist recalculation   │
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

Step 3: Voice Assistant Channel (Throughout Day)
┌─────────────────────────────────────┐
│   Voice Assistant Integration       │
│  - Listener asks: "Play music"      │
│  - After track ends, prompt:        │
│    "Did you enjoy [Track Name]?"    │
│  - Listener responds: "Yes" or "No" │
│  - Feedback recorded immediately    │
└────────┬────────────────────────────┘
         │ 5,000 voice feedbacks collected
         ▼

Contribution Complete
Feedback Collected: 20,000 total
  - Email: 15,000 (75%)
  - Voice: 5,000 (25%)
Email Conversion Rate: 15% (15,000 / 100,000)
```

---

## Scheduled Jobs

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           SCHEDULED JOBS                                 │
└─────────────────────────────────────────────────────────────────────────┘

Daily Schedule:
┌──────────┬────────────────────────────────────────────────────────────┐
│   Time   │                        Job                                 │
├──────────┼────────────────────────────────────────────────────────────┤
│ 2:00 AM  │ Batch Ingestion Workflow (ETL → Filter → Score → Store)   │
│          │ - Process yesterday's listening data                       │
│          │ - Duration: 1 hour 20 minutes                              │
│          │ - Output: 200,000 candidates                               │
├──────────┼────────────────────────────────────────────────────────────┤
│ 3:30 AM  │ Data Warehouse Export                                      │
│          │ - Export candidates to S3 (Parquet)                        │
│          │ - Update Glue catalog                                      │
│          │ - Duration: 20 minutes                                     │
├──────────┼────────────────────────────────────────────────────────────┤
│ 10:00 AM │ Email Campaign Delivery                                    │
│          │ - Retrieve top candidates (score >= 0.6)                   │
│          │ - Create email campaigns                                   │
│          │ - Send 100,000 emails                                      │
│          │ - Duration: 45 minutes                                     │
├──────────┼────────────────────────────────────────────────────────────┤
│ 6:00 PM  │ Playlist Update Job                                        │
│          │ - Aggregate feedback from today                            │
│          │ - Recalculate track scores                                 │
│          │ - Update curated playlists                                 │
│          │ - Duration: 30 minutes                                     │
├──────────┼────────────────────────────────────────────────────────────┤
│ 11:59 PM │ Metrics Aggregation                                        │
│          │ - Aggregate daily metrics                                  │
│          │ - Publish to CloudWatch                                    │
│          │ - Generate daily report                                    │
│          │ - Duration: 10 minutes                                     │
└──────────┴────────────────────────────────────────────────────────────┘

Weekly Schedule:
┌──────────┬────────────────────────────────────────────────────────────┐
│   Time   │                        Job                                 │
├──────────┼────────────────────────────────────────────────────────────┤
│ Sunday   │ Cleanup Expired Candidates                                 │
│ 1:00 AM  │ - DynamoDB TTL handles automatic deletion                  │
│          │ - Verify cleanup completed                                 │
│          │ - Duration: 15 minutes                                     │
├──────────┼────────────────────────────────────────────────────────────┤
│ Sunday   │ Model Retraining                                           │
│ 4:00 AM  │ - Retrain feedback propensity model                        │
│          │ - Retrain music affinity model                             │
│          │ - Evaluate model performance                               │
│          │ - Deploy if improved                                       │
│          │ - Duration: 3 hours                                        │
└──────────┴────────────────────────────────────────────────────────────┘
```

---

## Metrics & Success Criteria

### Processing Metrics
- **Batch Duration**: 1 hour 20 minutes (target: < 2 hours)
- **Throughput**: 10,000 candidates/minute
- **Filter Pass Rate**: 13.3% (200K / 1.5M)
- **High Score Rate**: 30% (60K / 200K with score >= 0.7)

### Delivery Metrics
- **Email Send Rate**: 100,000 per day
- **Open Rate**: 30% (30,000 opens)
- **Click-Through Rate**: 18% (18,000 clicks)
- **Conversion Rate**: 15% (15,000 feedback submissions)
- **Voice Feedback**: 5,000 per day

### Business Metrics
- **Feedback Volume**: 20,000 per day
- **Cost per Feedback**: $0.015 ($300 batch cost / 20,000 feedback)
- **Playlist Improvement**: 12% increase in listener engagement
- **Artist Satisfaction**: 4.5/5.0 average rating

### Technical Metrics
- **Serving API Latency**: P99 = 22ms (target: < 30ms)
- **Cache Hit Rate**: 82%
- **Error Rate**: 0.02%
- **Availability**: 99.93%

---

## Component Configuration

### Program Configuration
```yaml
programId: "music-feedback"
programName: "Music Track Feedback"
enabled: true
marketplaces: ["US", "UK", "DE", "FR", "JP", "IN"]

dataConnectors:
  - connectorId: "redshift-music-streams"
    type: "redshift"
    config:
      cluster: "music-analytics"
      database: "streaming"
      table: "track_plays"
      query: |
        SELECT listener_id, track_id, play_count, completion_rate, 
               engagement_score, genre, artist_id
        FROM track_plays
        WHERE track_release_date >= CURRENT_DATE - 7
        AND completion_rate >= 0.95

scoringModels:
  - modelId: "feedback-propensity-v3"
    endpoint: "sagemaker://feedback-propensity-v3"
    features: ["engagement_score", "feedback_history", "music_taste_profile"]
  - modelId: "music-affinity-v2"
    endpoint: "sagemaker://music-affinity-v2"
    features: ["genre", "artist_popularity", "listening_frequency"]

filterChain:
  - filterId: "trust"
    type: "trust"
    order: 1
  - filterId: "eligibility"
    type: "eligibility"
    order: 2
  - filterId: "engagement"
    type: "business-rule"
    order: 3
    parameters:
      minEngagementScore: 0.6
  - filterId: "frequency-cap"
    type: "business-rule"
    order: 4
    parameters:
      maxRequestsPerWeek: 2

channels:
  - channelId: "email"
    type: "email"
    enabled: true
    config:
      templateId: "track-feedback-v2"
      sendTime: "10:00"
      timezone: "local"
  - channelId: "voice-assistant"
    type: "voice"
    enabled: true
    config:
      prompt: "Did you enjoy {trackName}?"
      responseOptions: ["yes", "no"]

batchSchedule: "cron(0 2 * * ? *)"  # Daily at 2 AM UTC
candidateTTLDays: 14
```

---

## Summary

This use case demonstrates:
- ✅ **Batch processing** at scale (1.5M → 200K candidates)
- ✅ **Multi-stage pipeline** (ETL → Filter → Score → Store)
- ✅ **Dual ML models** (feedback propensity + music affinity)
- ✅ **Multi-channel delivery** (email + voice assistant)
- ✅ **High conversion** (15% feedback submission rate)
- ✅ **Cost efficiency** ($0.015 per feedback)
- ✅ **Scheduled automation** (daily batch + email + playlist updates)
- ✅ **Real-time voice integration** (5K voice feedbacks/day)
