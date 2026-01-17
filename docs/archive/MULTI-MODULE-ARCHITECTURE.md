# Multi-Module Maven Architecture

## Overview

The General Solicitation Platform uses a multi-module Maven architecture for better modularity, independent deployment, and clear separation of concerns.

## Module Structure

```
solicitation-platform-parent/          (Parent POM - aggregator)
├── solicitation-common/               (Shared utilities & logging)
├── solicitation-models/               (Data models - POJOs)
├── solicitation-storage/              (DynamoDB repository layer)
├── solicitation-connectors/           (Data source connectors)
├── solicitation-scoring/              (Scoring engine)
├── solicitation-filters/              (Filter pipeline)
├── solicitation-serving/              (Serving API logic)
├── solicitation-channels/             (Channel adapters)
├── solicitation-workflow-etl/         (ETL Lambda - DEPLOYABLE)
├── solicitation-workflow-filter/      (Filter Lambda - DEPLOYABLE)
├── solicitation-workflow-score/       (Score Lambda - DEPLOYABLE)
├── solicitation-workflow-store/       (Store Lambda - DEPLOYABLE)
└── solicitation-workflow-reactive/    (Reactive Lambda - DEPLOYABLE)
```

## Module Descriptions

### Library Modules (Reusable Components)

#### solicitation-common
**Purpose**: Shared utilities, logging configuration, and common infrastructure

**Contains**:
- Logging utilities (correlation IDs, structured logging)
- PII redaction utilities
- Common exception classes
- Configuration utilities
- CloudWatch integration

**Dependencies**: Jackson, Commons Lang3, Guava, Logback

---

#### solicitation-models
**Purpose**: Data models and POJOs

**Contains**:
- `Candidate` - Main candidate model
- `Context` - Multi-dimensional context
- `Subject` - Subject information
- `Score` - Scoring results
- `CandidateAttributes` - Candidate attributes
- `CandidateMetadata` - System metadata
- `ProgramConfig` - Program configuration
- `FilterConfig`, `ChannelConfig` - Configuration models

**Dependencies**: solicitation-common, Jackson

---

#### solicitation-storage
**Purpose**: DynamoDB repository layer

**Contains**:
- `CandidateRepository` - CRUD operations for candidates
- `ProgramConfigRepository` - Program configuration storage
- `ScoreCacheRepository` - Score caching
- DynamoDB table mappers
- Query builders for GSIs
- Optimistic locking implementation

**Dependencies**: solicitation-models, solicitation-common, AWS DynamoDB SDK

---

#### solicitation-connectors
**Purpose**: Data source connectors

**Contains**:
- `DataConnector` interface
- `AthenaConnector` - Data warehouse integration
- `S3Connector` - S3 file processing
- Schema validation
- Field mapping logic
- Transformation pipeline

**Dependencies**: solicitation-models, solicitation-common, AWS Athena, AWS S3

---

#### solicitation-scoring
**Purpose**: Scoring engine with ML model integration

**Contains**:
- `ScoringProvider` interface
- `SageMakerScoringProvider` - SageMaker endpoint integration
- `FeatureStoreClient` - Feature retrieval
- Score caching logic
- Circuit breaker for model endpoints
- Fallback strategies

**Dependencies**: solicitation-models, solicitation-storage, solicitation-common, AWS SageMaker

---

#### solicitation-filters
**Purpose**: Filter pipeline for eligibility and business rules

**Contains**:
- `Filter` interface
- `FilterChainExecutor` - Orchestrates filter execution
- `TrustFilter` - Trust validation
- `EligibilityFilter` - Eligibility checks
- `BusinessRuleFilter` - Business logic
- `QualityFilter` - Quality checks
- Rejection tracking

**Dependencies**: solicitation-models, solicitation-common

---

#### solicitation-serving
**Purpose**: Low-latency serving API logic

**Contains**:
- `ServingAPI` interface
- Query optimization logic
- Channel-specific ranking algorithms
- Real-time eligibility refresh
- Fallback and graceful degradation
- Response caching

**Dependencies**: solicitation-models, solicitation-storage, solicitation-common

---

#### solicitation-channels
**Purpose**: Channel adapters for notification delivery

**Contains**:
- `ChannelAdapter` interface
- `EmailChannelAdapter` - Email campaign integration
- `InAppChannelAdapter` - In-app card delivery
- `PushChannelAdapter` - Push notifications
- `VoiceChannelAdapter` - Voice assistant integration
- Shadow mode support
- Rate limiting and queueing

**Dependencies**: solicitation-models, solicitation-common

---

### Deployable Modules (Lambda Functions)

#### solicitation-workflow-etl
**Purpose**: ETL Lambda function for data extraction and transformation

**Handler**: `com.solicitation.workflow.ETLHandler::handleRequest`

**Contains**:
- Lambda handler implementation
- Data connector orchestration
- Batch processing logic

**Dependencies**: solicitation-connectors, solicitation-models, solicitation-common, AWS Lambda Runtime

**Deployment**: Shaded JAR → `solicitation-workflow-etl-1.0.0-SNAPSHOT.jar`

---

#### solicitation-workflow-filter
**Purpose**: Filter Lambda function for eligibility and business rules

**Handler**: `com.solicitation.workflow.FilterHandler::handleRequest`

**Contains**:
- Lambda handler implementation
- Filter chain execution
- Rejection tracking

**Dependencies**: solicitation-filters, solicitation-models, solicitation-common, AWS Lambda Runtime

**Deployment**: Shaded JAR → `solicitation-workflow-filter-1.0.0-SNAPSHOT.jar`

---

#### solicitation-workflow-score
**Purpose**: Score Lambda function for ML model execution

**Handler**: `com.solicitation.workflow.ScoreHandler::handleRequest`

**Contains**:
- Lambda handler implementation
- Scoring orchestration
- Feature retrieval
- Score caching

**Dependencies**: solicitation-scoring, solicitation-models, solicitation-common, AWS Lambda Runtime

**Deployment**: Shaded JAR → `solicitation-workflow-score-1.0.0-SNAPSHOT.jar`

---

#### solicitation-workflow-store
**Purpose**: Store Lambda function for batch writing to DynamoDB

**Handler**: `com.solicitation.workflow.StoreHandler::handleRequest`

**Contains**:
- Lambda handler implementation
- Batch write orchestration
- Error handling and retries

**Dependencies**: solicitation-storage, solicitation-models, solicitation-common, AWS Lambda Runtime

**Deployment**: Shaded JAR → `solicitation-workflow-store-1.0.0-SNAPSHOT.jar`

---

#### solicitation-workflow-reactive
**Purpose**: Reactive Lambda function for real-time event processing

**Handler**: `com.solicitation.workflow.ReactiveHandler::handleRequest`

**Contains**:
- Lambda handler implementation
- Real-time filtering and scoring
- Event deduplication
- Immediate candidate creation

**Dependencies**: solicitation-filters, solicitation-scoring, solicitation-storage, solicitation-models, solicitation-common, AWS Lambda Runtime

**Deployment**: Shaded JAR → `solicitation-workflow-reactive-1.0.0-SNAPSHOT.jar`

---

## Dependency Graph

```
┌─────────────────────────────────────────────────────────────┐
│                    Deployable Lambdas                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  workflow-etl ──────────┐                                   │
│  workflow-filter ───────┤                                   │
│  workflow-score ────────┼──> Library Modules                │
│  workflow-store ────────┤                                   │
│  workflow-reactive ─────┘                                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    Library Modules                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  connectors ──┐                                             │
│  filters ─────┤                                             │
│  scoring ─────┼──> storage ──> models ──> common           │
│  serving ─────┤                                             │
│  channels ────┘                                             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## Build Commands

### Build All Modules
```bash
mvn clean install
```

### Build Specific Module
```bash
cd solicitation-workflow-etl
mvn clean package
```

### Build Only Deployable Lambdas
```bash
mvn clean package -pl solicitation-workflow-etl,solicitation-workflow-filter,solicitation-workflow-score,solicitation-workflow-store,solicitation-workflow-reactive -am
```

### Run Tests for All Modules
```bash
mvn test
```

### Run Tests for Specific Module
```bash
cd solicitation-storage
mvn test
```

### Generate Coverage Report
```bash
mvn clean test jacoco:report
```

## Deployment Artifacts

After building, the following shaded JARs will be created in their respective `target/` directories:

1. `solicitation-workflow-etl/target/solicitation-workflow-etl-1.0.0-SNAPSHOT.jar`
2. `solicitation-workflow-filter/target/solicitation-workflow-filter-1.0.0-SNAPSHOT.jar`
3. `solicitation-workflow-score/target/solicitation-workflow-score-1.0.0-SNAPSHOT.jar`
4. `solicitation-workflow-store/target/solicitation-workflow-store-1.0.0-SNAPSHOT.jar`
5. `solicitation-workflow-reactive/target/solicitation-workflow-reactive-1.0.0-SNAPSHOT.jar`

These JARs are uploaded to S3 and referenced by Lambda functions in the CloudFormation template.

## Benefits of Multi-Module Architecture

### 1. Independent Deployment
- Each Lambda function has its own JAR
- Smaller deployment packages (faster cold starts)
- Deploy only what changed

### 2. Clear Dependencies
- Explicit module dependencies in POM files
- Prevents circular dependencies
- Easy to understand component relationships

### 3. Better Testing
- Test each module independently
- Mock dependencies easily
- Faster test execution (parallel)

### 4. Team Ownership
- Different teams can own different modules
- Clear boundaries and interfaces
- Reduced merge conflicts

### 5. Selective Builds
- Build only changed modules
- Faster CI/CD pipelines
- Reduced build times

### 6. Easy Extension
- Add new connectors without touching core
- Add new filters without redeploying everything
- Add new channels independently

### 7. Code Reuse
- Library modules shared across Lambdas
- No code duplication
- Consistent behavior

### 8. Smaller JARs
- Each Lambda only includes what it needs
- Reduced cold start times
- Lower memory footprint

## Adding New Modules

### Adding a New Connector

1. Create module directory: `solicitation-connectors-kinesis/`
2. Create `pom.xml` with parent reference
3. Add dependency on `solicitation-models` and `solicitation-common`
4. Implement `DataConnector` interface
5. Add module to parent POM `<modules>` section
6. Build: `mvn clean install`

### Adding a New Lambda Function

1. Create module directory: `solicitation-workflow-serve/`
2. Create `pom.xml` with parent reference
3. Add dependencies on required library modules
4. Add AWS Lambda Runtime dependencies
5. Add Shade plugin configuration
6. Implement Lambda handler
7. Add module to parent POM `<modules>` section
8. Update CloudFormation template with new Lambda function
9. Build: `mvn clean package`

## Version Management

All modules share the same version defined in the parent POM:
- Version: `1.0.0-SNAPSHOT`
- Property: `${solicitation.version}`

To release a new version:
```bash
mvn versions:set -DnewVersion=1.1.0
mvn versions:commit
```

## IDE Support

### IntelliJ IDEA
1. Open parent `pom.xml` as project
2. IntelliJ will automatically detect all modules
3. Each module appears as a separate module in the project structure

### Eclipse
1. Import → Existing Maven Projects
2. Select parent directory
3. Eclipse will import all modules

### VS Code
1. Open parent directory
2. Java extension will detect Maven multi-module project
3. Use Maven sidebar to build/test modules

## Troubleshooting

### Module Not Found
```
[ERROR] Failed to execute goal on project solicitation-workflow-etl: 
Could not resolve dependencies for project com.solicitation:solicitation-workflow-etl:jar:1.0.0-SNAPSHOT: 
Could not find artifact com.solicitation:solicitation-connectors:jar:1.0.0-SNAPSHOT
```

**Solution**: Build the parent project first to install all modules to local Maven repository:
```bash
mvn clean install
```

### Circular Dependency
```
[ERROR] The projects in the reactor contain a cyclic reference
```

**Solution**: Review module dependencies and remove circular references. Use dependency tree:
```bash
mvn dependency:tree
```

### Shade Plugin Issues
```
[WARNING] Discovered module-info.class. Shading will break its strong encapsulation.
```

**Solution**: This is a warning, not an error. The shade plugin handles Java 9+ modules correctly.

