# Next Steps: Multi-Module Migration

## Current Status ✅

The multi-module Maven architecture has been designed and configured:

- ✅ Parent POM created with 13 modules
- ✅ All module POMs created with correct dependencies
- ✅ Architecture documentation complete
- ✅ README updated
- ✅ Deployment comparison documented

## What's Left to Do

### Phase 1: Code Migration (NEXT)

The existing code in `src/main/java/com/solicitation/` needs to be moved to the appropriate modules.

#### Step 1.1: Create Module Directory Structures

For each module, create the standard Maven directory structure:

```bash
# Library modules
for module in common models storage connectors scoring filters serving channels; do
    mkdir -p solicitation-$module/src/main/java/com/solicitation/$module
    mkdir -p solicitation-$module/src/main/resources
    mkdir -p solicitation-$module/src/test/java/com/solicitation/$module
done

# Workflow modules
for module in etl filter score store reactive; do
    mkdir -p solicitation-workflow-$module/src/main/java/com/solicitation/workflow
    mkdir -p solicitation-workflow-$module/src/main/resources
    mkdir -p solicitation-workflow-$module/src/test/java/com/solicitation/workflow
done
```

#### Step 1.2: Move Existing Code

**Current structure**:
```
src/main/java/com/solicitation/
├── model/       → solicitation-models/src/main/java/com/solicitation/model/
├── storage/     → solicitation-storage/src/main/java/com/solicitation/storage/
├── connector/   → solicitation-connectors/src/main/java/com/solicitation/connector/
├── scoring/     → solicitation-scoring/src/main/java/com/solicitation/scoring/
├── filter/      → solicitation-filters/src/main/java/com/solicitation/filter/
├── serving/     → solicitation-serving/src/main/java/com/solicitation/serving/
├── channel/     → solicitation-channels/src/main/java/com/solicitation/channel/
├── workflow/    → Split across workflow modules
├── config/      → solicitation-common/src/main/java/com/solicitation/config/
└── util/        → solicitation-common/src/main/java/com/solicitation/util/
```

**Commands**:
```bash
# Move model code
mv src/main/java/com/solicitation/model/* solicitation-models/src/main/java/com/solicitation/model/

# Move storage code
mv src/main/java/com/solicitation/storage/* solicitation-storage/src/main/java/com/solicitation/storage/

# Move connector code
mv src/main/java/com/solicitation/connector/* solicitation-connectors/src/main/java/com/solicitation/connector/

# Move scoring code
mv src/main/java/com/solicitation/scoring/* solicitation-scoring/src/main/java/com/solicitation/scoring/

# Move filter code
mv src/main/java/com/solicitation/filter/* solicitation-filters/src/main/java/com/solicitation/filter/

# Move serving code
mv src/main/java/com/solicitation/serving/* solicitation-serving/src/main/java/com/solicitation/serving/

# Move channel code
mv src/main/java/com/solicitation/channel/* solicitation-channels/src/main/java/com/solicitation/channel/

# Move util and config to common
mv src/main/java/com/solicitation/util/* solicitation-common/src/main/java/com/solicitation/util/
mv src/main/java/com/solicitation/config/* solicitation-common/src/main/java/com/solicitation/config/
```

#### Step 1.3: Split Workflow Code

The `workflow/` package needs to be split across the 5 workflow modules:

```bash
# Create workflow handlers in each module
# ETL Handler
# - Move ETL-related code to solicitation-workflow-etl/src/main/java/com/solicitation/workflow/

# Filter Handler
# - Move Filter-related code to solicitation-workflow-filter/src/main/java/com/solicitation/workflow/

# Score Handler
# - Move Score-related code to solicitation-workflow-score/src/main/java/com/solicitation/workflow/

# Store Handler
# - Move Store-related code to solicitation-workflow-store/src/main/java/com/solicitation/workflow/

# Reactive Handler
# - Move Reactive-related code to solicitation-workflow-reactive/src/main/java/com/solicitation/workflow/
```

#### Step 1.4: Move Resources

```bash
# Move logback.xml to common
mv src/main/resources/logback.xml solicitation-common/src/main/resources/

# Move application.properties to common
mv src/main/resources/application.properties solicitation-common/src/main/resources/
```

#### Step 1.5: Move Tests

```bash
# Move tests to corresponding modules
mv src/test/java/com/solicitation/model/* solicitation-models/src/test/java/com/solicitation/model/
mv src/test/java/com/solicitation/storage/* solicitation-storage/src/test/java/com/solicitation/storage/
# ... repeat for all modules
```

### Phase 2: Build Verification

#### Step 2.1: Build Each Module Individually

```bash
# Build common first (no dependencies)
cd solicitation-common
mvn clean install

# Build models (depends on common)
cd ../solicitation-models
mvn clean install

# Build storage (depends on models, common)
cd ../solicitation-storage
mvn clean install

# Continue for all modules...
```

#### Step 2.2: Build All Modules

```bash
# From root directory
mvn clean install
```

#### Step 2.3: Fix Compilation Errors

Common issues:
- Missing imports (update package references)
- Circular dependencies (refactor if needed)
- Missing dependencies in module POMs

### Phase 3: Update Infrastructure

#### Step 3.1: Update Lambda CloudFormation Template

Update `infrastructure/lambda-functions.yaml`:

```yaml
# Before
Code:
  S3Bucket: !Sub '${ProjectName}-lambda-artifacts-${Environment}'
  S3Key: etl-lambda.jar

# After
Code:
  S3Bucket: !Sub '${ProjectName}-lambda-artifacts-${Environment}'
  S3Key: solicitation-workflow-etl-1.0.0-SNAPSHOT.jar
```

Repeat for all 5 Lambda functions.

#### Step 3.2: Update Deployment Scripts

Update `scripts/deploy.sh` to:
1. Build all modules
2. Upload 5 separate JARs to S3
3. Deploy CloudFormation stack

```bash
#!/bin/bash
set -e

ENVIRONMENT=${1:-dev}
S3_BUCKET="solicitation-platform-lambda-artifacts-${ENVIRONMENT}"

echo "Building all modules..."
mvn clean package

echo "Uploading Lambda JARs to S3..."
aws s3 cp solicitation-workflow-etl/target/solicitation-workflow-etl-1.0.0-SNAPSHOT.jar \
    s3://${S3_BUCKET}/solicitation-workflow-etl-1.0.0-SNAPSHOT.jar

aws s3 cp solicitation-workflow-filter/target/solicitation-workflow-filter-1.0.0-SNAPSHOT.jar \
    s3://${S3_BUCKET}/solicitation-workflow-filter-1.0.0-SNAPSHOT.jar

aws s3 cp solicitation-workflow-score/target/solicitation-workflow-score-1.0.0-SNAPSHOT.jar \
    s3://${S3_BUCKET}/solicitation-workflow-score-1.0.0-SNAPSHOT.jar

aws s3 cp solicitation-workflow-store/target/solicitation-workflow-store-1.0.0-SNAPSHOT.jar \
    s3://${S3_BUCKET}/solicitation-workflow-store-1.0.0-SNAPSHOT.jar

aws s3 cp solicitation-workflow-reactive/target/solicitation-workflow-reactive-1.0.0-SNAPSHOT.jar \
    s3://${S3_BUCKET}/solicitation-workflow-reactive-1.0.0-SNAPSHOT.jar

echo "Deploying CloudFormation stack..."
aws cloudformation deploy \
    --template-file infrastructure/lambda-functions.yaml \
    --stack-name solicitation-platform-lambdas-${ENVIRONMENT} \
    --parameter-overrides Environment=${ENVIRONMENT} \
    --capabilities CAPABILITY_NAMED_IAM

echo "Deployment complete!"
```

### Phase 4: Testing

#### Step 4.1: Unit Tests

```bash
# Run all tests
mvn test

# Run tests for specific module
cd solicitation-storage
mvn test
```

#### Step 4.2: Integration Tests

```bash
# Deploy to dev environment
./scripts/deploy.sh dev

# Run integration tests
mvn verify -P integration-tests
```

#### Step 4.3: Performance Testing

- Measure Lambda cold start times
- Compare with baseline (monolithic)
- Verify 50-70% improvement

### Phase 5: CI/CD Updates

#### Step 5.1: Update GitHub Actions Workflow

Update `.github/workflows/build.yml`:

```yaml
name: Build and Test

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Build with Maven
        run: mvn clean install
      
      - name: Run tests
        run: mvn test
      
      - name: Generate coverage report
        run: mvn jacoco:report
      
      - name: Upload Lambda artifacts
        if: github.ref == 'refs/heads/main'
        run: |
          aws s3 cp solicitation-workflow-etl/target/*.jar s3://...
          aws s3 cp solicitation-workflow-filter/target/*.jar s3://...
          aws s3 cp solicitation-workflow-score/target/*.jar s3://...
          aws s3 cp solicitation-workflow-store/target/*.jar s3://...
          aws s3 cp solicitation-workflow-reactive/target/*.jar s3://...
```

### Phase 6: Documentation Updates

#### Step 6.1: Update FOUNDATION Files

Update `.kiro/specs/solicitation-platform/FOUNDATION/design.md` to reflect multi-module architecture.

#### Step 6.2: Update Task 1 in FOUNDATION

Update Task 1 in `.kiro/specs/solicitation-platform/FOUNDATION/tasks.md` to reflect the new structure.

#### Step 6.3: Create Migration Guide

Document the migration process for the team.

## Quick Start Commands

### For Immediate Testing

```bash
# 1. Build everything
mvn clean install

# 2. If build fails, check for:
#    - Missing code in modules
#    - Import errors
#    - Dependency issues

# 3. Run tests
mvn test

# 4. Package Lambdas
mvn clean package -pl solicitation-workflow-etl,solicitation-workflow-filter,solicitation-workflow-score,solicitation-workflow-store,solicitation-workflow-reactive -am

# 5. Check JAR sizes
ls -lh solicitation-workflow-*/target/*.jar
```

## Rollback Plan

If issues arise, the monolithic structure can be restored:

1. Keep the old `src/` directory as backup
2. Revert `pom.xml` to monolithic version
3. Delete module directories
4. Restore original CloudFormation templates

## Success Criteria

- ✅ All modules build successfully
- ✅ All tests pass
- ✅ Lambda JARs are 50-80% smaller
- ✅ Cold starts are 50-70% faster
- ✅ Deployment time reduced by 60%
- ✅ Clear module boundaries
- ✅ Easy to add new connectors/filters/channels

## Timeline Estimate

- **Phase 1 (Code Migration)**: 2-3 hours
- **Phase 2 (Build Verification)**: 1-2 hours
- **Phase 3 (Infrastructure Updates)**: 1 hour
- **Phase 4 (Testing)**: 2-3 hours
- **Phase 5 (CI/CD Updates)**: 1 hour
- **Phase 6 (Documentation)**: 1 hour

**Total**: 8-11 hours

## Questions?

Refer to:
- `MULTI-MODULE-ARCHITECTURE.md` - Architecture details
- `ARCHITECTURE-REDESIGN-SUMMARY.md` - What changed and why
- `DEPLOYMENT-COMPARISON.md` - Before/after comparison
- `README.md` - Build and deployment instructions

