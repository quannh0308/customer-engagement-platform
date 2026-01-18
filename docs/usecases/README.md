# Use Case Diagrams

This directory contains detailed flow diagrams for each use case, showing:
- Actor interactions
- Data ingestion flows
- Data contribution flows
- Scheduled jobs
- Processing timelines
- Metrics and success criteria

---

## Use Cases by Processing Mode

### Batch Processing (Scheduled)
1. **[E-Commerce Product Reviews](E-COMMERCE-PRODUCT-REVIEWS.md)** ⭐ COMPLETE
   - Daily batch at 2 AM
   - 500K → 200K candidates
   - Email delivery at 10 AM
   - 5% conversion rate

2. **[Music Track Feedback](MUSIC-TRACK-FEEDBACK.md)** ⭐ COMPLETE
   - Daily batch at 2 AM
   - 2M → 200K candidates
   - Multi-channel delivery (email + voice)
   - 15% feedback rate

3. **[Event Participation Requests](EVENT-PARTICIPATION-REQUESTS.md)** ⭐ COMPLETE
   - Weekly batch on Monday
   - 2.5M → 100K candidates
   - Multi-channel campaign (email + in-app + push)
   - 8.5% registration rate, 87.5% attendance rate

### Reactive Processing (Real-Time)
4. **[Video Ratings](VIDEO-RATINGS-REACTIVE.md)** ⭐ COMPLETE
   - Event-driven (<200ms latency)
   - In-app card + push notification
   - 40% rating submission rate

### Hybrid Processing (Batch + Reactive)
5. **[Service Experience Surveys](SERVICE-EXPERIENCE-SURVEYS.md)** ⭐ COMPLETE
   - Reactive trigger after service completion
   - 1-hour cooling period
   - Multi-channel delivery (SMS + email)
   - 35% survey completion rate

---

## Diagram Types

Each use case includes:

### 1. Actor Interaction Diagram
Shows how different actors (customers, teams, services) interact with the system over time.

### 2. Data Ingestion Flow
Detailed step-by-step flow showing:
- Data source extraction
- ETL transformation
- Filter pipeline execution
- Scoring model invocation
- Storage operations

### 3. Data Contribution Flow
Shows how customers interact with solicitations:
- Delivery channel (email, in-app, push, etc.)
- Customer engagement (open, click, submit)
- Response collection
- Metrics tracking

### 4. Scheduled Jobs
Timeline of automated jobs:
- Daily batch processing
- Email campaign delivery
- Data warehouse exports
- Metrics aggregation
- Model retraining

### 5. Processing Timeline
Minute-by-minute breakdown showing:
- Lambda execution times
- Data volumes at each stage
- Filter pass rates
- Scoring throughput
- Storage operations

---

## Quick Reference

| Use Case | Mode | Latency | Volume | Conversion |
|----------|------|---------|--------|------------|
| Product Reviews | Batch | 1 hour | 500K → 200K | 5% |
| Music Feedback | Batch | 1h 20m | 2M → 200K | 15% |
| Event Requests | Batch | 2 hours | 2.5M → 100K | 8.5% (reg), 87.5% (attend) |
| Video Ratings | Reactive | <200ms | Real-time | 40% |
| Service Surveys | Reactive | <500ms + 1h cooling | Real-time | 35% |

---

## How to Read These Diagrams

### Symbols
- `┌─┐` = Component/Service
- `│` = Data flow
- `▼` = Direction of flow
- `├─>` = Branch/Fork
- `T+Xms` = Timeline marker (milliseconds)

### Data Volumes
- Numbers show candidates at each stage
- Percentages show filter pass rates
- Metrics show conversion rates

### Timing
- Batch: Shows clock time (2:00 AM, 10:00 AM)
- Reactive: Shows relative time (T+0ms, T+100ms)

---

## Architecture Components Referenced

### Data Sources
- Data Warehouse (Athena, Redshift, Snowflake)
- Event Streams (EventBridge, Kinesis)
- S3 Buckets

### Processing
- ETL Lambda
- Filter Lambda
- Score Lambda
- Store Lambda
- Reactive Lambda

### Storage
- DynamoDB (Candidates, ProgramConfig, ScoreCache)
- S3 (Data warehouse exports)

### Delivery
- Email Campaign Service
- In-App Notification Service
- Push Notification Service
- SMS Gateway
- Voice Assistant Integration

### ML/Scoring
- SageMaker Endpoints
- Feature Store
- Bedrock Models

---

## Related Documentation

- **Architecture**: See `docs/VISUAL-ARCHITECTURE.md`
- **Use Case Descriptions**: See `docs/USE-CASES.md`
- **Requirements**: See `.kiro/specs/solicitation-platform/requirements.md`
- **Design**: See `.kiro/specs/solicitation-platform/design.md`

---

## Contributing New Use Cases

To add a new use case diagram:

1. Create file: `docs/usecases/YOUR-USE-CASE.md`
2. Include all 5 diagram types:
   - Actor Interaction
   - Data Ingestion Flow
   - Data Contribution Flow
   - Scheduled Jobs
   - Processing Timeline
3. Add metrics and success criteria
4. Update this README with link and summary
5. Update quick reference table

---

## Examples of Real-World Scenarios

### E-Commerce Product Reviews
**Real Company**: Amazon, eBay, Shopify stores
**Scale**: 500K deliveries/day → 50K emails → 2.5K reviews
**ROI**: $0.02 per review, 30% volume increase

### Music Track Feedback
**Real Company**: Spotify, Apple Music, YouTube Music
**Scale**: 2M listens/day → 200K candidates → 30K feedback
**ROI**: Improved playlist curation, 15% engagement

### Video Ratings
**Real Company**: Netflix, YouTube, Prime Video
**Scale**: Real-time processing, <1s latency
**ROI**: Better recommendations, 40% rating rate

---

## Performance Benchmarks

### Batch Processing
- **Throughput**: 8,000-10,000 candidates/minute
- **Duration**: 30-60 minutes for 500K-2M candidates
- **Cost**: $2-5 per batch run
- **Latency**: N/A (scheduled)

### Reactive Processing
- **Latency**: 200-500ms end-to-end
- **Throughput**: 1,000-5,000 events/second
- **Cost**: $0.0001 per event
- **Availability**: 99.95%

### Serving API
- **Latency**: P99 < 30ms
- **Throughput**: 10,000 requests/second
- **Cache Hit Rate**: 85%
- **Availability**: 99.99%

---

## Next Steps

1. Review existing use case diagrams
2. Identify gaps or missing scenarios
3. Create new use case diagrams as needed
4. Update metrics based on production data
5. Share with stakeholders for feedback
