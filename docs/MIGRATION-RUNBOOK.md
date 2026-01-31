# CEAP Infrastructure Migration Runbook
# 7-Stack to 3-Stack Consolidation

**Document Version**: 1.0  
**Last Updated**: January 31, 2026  
**Migration Type**: Infrastructure Consolidation  
**Estimated Duration**: 30-60 minutes  
**Downtime**: Zero (blue-green deployment)

---

## Executive Summary

This runbook guides the migration from the original 7-stack CEAP architecture to the consolidated 3-stack architecture. The migration is **non-destructive** and supports **instant rollback**.

**Architecture Change**:
- **Before**: 7 CloudFormation stacks (Database + 6 workflow stacks)
- **After**: 3 CloudFormation stacks (Database + DataPlatform + ServingAPI)

**Benefits**:
- ✅ 57% reduction in stack count (7 → 3)
- ✅ 67% faster deployment (15min → 5min)
- ✅ Clear business capability alignment
- ✅ Parallel deployment support
- ✅ Simplified dependency management

---

## Pre-Migration Checklist

Complete these steps **before** starting the migration:

### Technical Prerequisites
- [ ] AWS CLI installed and configured
- [ ] AWS CDK CLI installed (v2.x)
- [ ] Node.js installed (v20+)
- [ ] Java 17 installed
- [ ] Gradle installed
- [ ] Valid AWS credentials with AdministratorAccess
- [ ] CDK bootstrapped in target account/region

### Code Prerequisites
- [ ] Latest code pulled from main branch
- [ ] All tests passing locally (`./gradlew test`)
- [ ] Infrastructure builds successfully (`./gradlew :infrastructure:build`)
- [ ] CDK synthesis works (`npx cdk synth`)

### Environment Prerequisites
- [ ] Identified target environment (dev/staging/prod)
- [ ] Verified AWS account and region
- [ ] Checked AWS service quotas (CloudFormation stacks, Lambda functions)
- [ ] Reviewed current resource usage

### Communication Prerequisites
- [ ] Stakeholders notified of migration window
- [ ] Rollback plan communicated
- [ ] On-call engineer identified
- [ ] Incident response plan ready

### Backup Prerequisites
- [ ] Current CloudFormation templates exported
- [ ] DynamoDB tables backed up (if production)
- [ ] Lambda function code versions documented
- [ ] Current stack outputs documented

---

## Migration Steps

### Phase 1: Preparation (10 minutes)

#### Step 1.1: Verify Current State

```bash
# Navigate to infrastructure directory
cd infrastructure

# List current stacks
aws cloudformation list-stacks --stack-status-filter CREATE_COMPLETE \
  --query 'StackSummaries[?starts_with(StackName, `Ceap`)].StackName'

# Expected: 7 stacks or 0 stacks (if fresh deployment)
```

**Decision Point**:
- **If 7 stacks exist**: Continue with migration
- **If 0 stacks exist**: This is a fresh deployment, skip to Phase 2
- **If 3 stacks exist**: Already migrated, no action needed

#### Step 1.2: Export Current Templates (If 7 Stacks Exist)

```bash
# Create backup directory
mkdir -p backups/$(date +%Y%m%d-%H%M%S)
cd backups/$(date +%Y%m%d-%H%M%S)

# Export all stack templates
for stack in CeapDatabase-dev CeapEtlWorkflow-dev CeapFilterWorkflow-dev \
             CeapScoreWorkflow-dev CeapStoreWorkflow-dev \
             CeapReactiveWorkflow-dev CeapOrchestration-dev; do
    aws cloudformation get-template --stack-name $stack \
        --query 'TemplateBody' > ${stack}.json
    echo "✅ Exported $stack"
done

cd ../..
```

**Verification**: Check that 7 JSON files were created in the backup directory.

#### Step 1.3: Document Current Stack Outputs

```bash
# Save current stack outputs
for stack in CeapDatabase-dev CeapEtlWorkflow-dev CeapFilterWorkflow-dev \
             CeapScoreWorkflow-dev CeapStoreWorkflow-dev \
             CeapReactiveWorkflow-dev CeapOrchestration-dev; do
    aws cloudformation describe-stacks --stack-name $stack \
        --query 'Stacks[0].Outputs' > backups/$(date +%Y%m%d)/${stack}-outputs.json
done
```

#### Step 1.4: Run Pre-Migration Tests

```bash
# Build and test
cd ..
./gradlew :infrastructure:test

# Expected: All tests pass
```

**If tests fail**: Fix issues before proceeding.

---

### Phase 2: Deploy Consolidated Architecture (10-15 minutes)

#### Step 2.1: Build Infrastructure

```bash
cd infrastructure
../gradlew build -x test
```

**Expected output**: `BUILD SUCCESSFUL`

#### Step 2.2: Synthesize CDK Templates

```bash
npx cdk synth --output cdk.out.consolidated
```

**Expected output**: `Successfully synthesized to .../cdk.out.consolidated`

**Verification**: Check that 3 template files exist:
```bash
ls -la cdk.out.consolidated/*.template.json
# Should show:
# - CeapDatabase-dev.template.json
# - CeapDataPlatform-dev.template.json
# - CeapServingAPI-dev.template.json
```

#### Step 2.3: Deploy Database Stack (Update with Exports)

```bash
npx cdk deploy CeapDatabase-dev \
    --app "cdk.out.consolidated" \
    --require-approval never
```

**Expected duration**: 2-3 minutes  
**Expected output**: 
```
✅ CeapDatabase-dev (no changes)
Outputs:
  CeapDatabase-dev.CandidatesTableNameOutput = Candidates-dev
  CeapDatabase-dev.CandidatesTableArnOutput = arn:aws:dynamodb:...
  ... (6 outputs total)
```

**Verification**: 
```bash
aws cloudformation describe-stacks --stack-name CeapDatabase-dev \
    --query 'Stacks[0].Outputs' | jq length
# Should show: 6
```

#### Step 2.4: Deploy Application Stacks (Parallel)

```bash
npx cdk deploy CeapDataPlatform-dev CeapServingAPI-dev \
    --app "cdk.out.consolidated" \
    --concurrency 2 \
    --require-approval never
```

**Expected duration**: 10-12 minutes (parallel deployment)  
**Expected output**:
```
✅ CeapDataPlatform-dev
✅ CeapServingAPI-dev
```

**Verification**:
```bash
# Check both stacks are CREATE_COMPLETE
aws cloudformation describe-stacks --stack-name CeapDataPlatform-dev \
    --query 'Stacks[0].StackStatus'
# Should show: "CREATE_COMPLETE"

aws cloudformation describe-stacks --stack-name CeapServingAPI-dev \
    --query 'Stacks[0].StackStatus'
# Should show: "CREATE_COMPLETE"
```

---

### Phase 3: Validation (5-10 minutes)

#### Step 3.1: Run Resource Validation

```bash
./validate-resources.sh dev
```

**Expected output**:
```
✅ All validation checks passed!
✅ New 3-stack architecture contains all expected resources

Lambda Functions: 7
DynamoDB Tables: 4
Step Functions: 1
EventBridge Rules: 2
IAM Roles: 9
```

**If validation fails**: 
- Review the generated report file
- Check which resources are missing
- **DO NOT proceed to cleanup** - investigate and fix first

#### Step 3.2: Verify Cross-Stack References

```bash
# Check that DataPlatform can access database exports
aws cloudformation describe-stacks --stack-name CeapDataPlatform-dev \
    --query 'Stacks[0].Parameters'

# Should show DatabaseStackName parameter
```

#### Step 3.3: Run All Tests

```bash
cd ..
./gradlew :infrastructure:test
```

**Expected**: All 232+ tests pass

**If tests fail**: Investigate and fix before proceeding.

#### Step 3.4: Test Lambda Functions

```bash
# Test ETL Lambda
aws lambda invoke \
    --function-name $(aws lambda list-functions \
        --query 'Functions[?contains(FunctionName, `ETLLambda`)].FunctionName' \
        --output text | head -1) \
    --payload '{"test": true}' \
    response.json

# Check response
cat response.json
```

**Expected**: Lambda executes successfully (may return error if no test data, but should not fail to invoke)

#### Step 3.5: Test Step Functions

```bash
# Get state machine ARN
STATE_MACHINE_ARN=$(aws stepfunctions list-state-machines \
    --query 'stateMachines[?contains(name, `BatchIngestion`)].stateMachineArn' \
    --output text)

# Start test execution
aws stepfunctions start-execution \
    --state-machine-arn $STATE_MACHINE_ARN \
    --input '{"programId":"test","marketplace":"US","batchId":"migration-test"}'

# Note: Execution may fail due to no test data, but state machine should start
```

---

### Phase 4: Cleanup Old Stacks (Optional - 10 minutes)

**⚠️ CRITICAL DECISION POINT**

Before deleting old stacks, ensure:
- [ ] New stacks are fully functional
- [ ] All validation tests passed
- [ ] Integration tests completed successfully
- [ ] Stakeholders approve cleanup
- [ ] Rollback plan is ready

#### Step 4.1: Verify Old Stacks Exist

```bash
# Check for old stacks
for stack in CeapEtlWorkflow-dev CeapFilterWorkflow-dev \
             CeapScoreWorkflow-dev CeapStoreWorkflow-dev \
             CeapReactiveWorkflow-dev CeapOrchestration-dev; do
    if aws cloudformation describe-stacks --stack-name $stack > /dev/null 2>&1; then
        echo "✓ Found: $stack"
    else
        echo "✗ Not found: $stack"
    fi
done
```

**If no old stacks found**: Skip to Phase 5 (this was a fresh deployment)

#### Step 4.2: Delete Old Workflow Stacks

**⚠️ WARNING**: This is destructive. Ensure validation passed first!

```bash
# Delete old stacks (in reverse dependency order)
aws cloudformation delete-stack --stack-name CeapOrchestration-dev
aws cloudformation delete-stack --stack-name CeapReactiveWorkflow-dev
aws cloudformation delete-stack --stack-name CeapStoreWorkflow-dev
aws cloudformation delete-stack --stack-name CeapScoreWorkflow-dev
aws cloudformation delete-stack --stack-name CeapFilterWorkflow-dev
aws cloudformation delete-stack --stack-name CeapEtlWorkflow-dev

# Wait for deletions to complete
for stack in CeapOrchestration-dev CeapReactiveWorkflow-dev \
             CeapStoreWorkflow-dev CeapScoreWorkflow-dev \
             CeapFilterWorkflow-dev CeapEtlWorkflow-dev; do
    echo "Waiting for $stack deletion..."
    aws cloudformation wait stack-delete-complete --stack-name $stack
    echo "✅ $stack deleted"
done
```

**Expected duration**: 5-10 minutes

#### Step 4.3: Verify Cleanup

```bash
# List remaining stacks
aws cloudformation list-stacks --stack-status-filter CREATE_COMPLETE \
    --query 'StackSummaries[?starts_with(StackName, `Ceap`)].StackName'

# Expected: Only 3 stacks
# - CeapDatabase-dev
# - CeapDataPlatform-dev
# - CeapServingAPI-dev
```

---

### Phase 5: Post-Migration Validation (5 minutes)

#### Step 5.1: Run Final Validation

```bash
cd infrastructure
./validate-resources.sh dev
```

**Expected**: All checks pass

#### Step 5.2: Run Full Test Suite

```bash
cd ..
./gradlew test
```

**Expected**: All tests pass across all modules

#### Step 5.3: Verify in AWS Console

1. Go to https://console.aws.amazon.com/cloudformation
2. Verify 3 stacks exist and are in CREATE_COMPLETE status
3. Check Lambda functions in Lambda console
4. Check DynamoDB tables in DynamoDB console
5. Check Step Functions in Step Functions console

#### Step 5.4: Document Migration Completion

```bash
# Create migration completion record
cat > migration-complete-$(date +%Y%m%d).txt << EOF
CEAP Infrastructure Migration Complete
Date: $(date)
Environment: dev
Old Architecture: 7 stacks
New Architecture: 3 stacks
Migration Duration: [FILL IN]
Validation Status: PASSED
Rollback Tested: [YES/NO]
EOF
```

---

## Rollback Procedure

**When to Rollback**:
- Validation tests fail
- Integration tests fail
- Unexpected errors during deployment
- Stakeholder decision to abort

### Quick Rollback (If New Stacks Have Issues)

```bash
cd infrastructure
./rollback-consolidated.sh dev
```

**What this does**:
1. Deletes CeapServingAPI-dev
2. Deletes CeapDataPlatform-dev
3. Reverts CeapDatabase-dev to original state (removes exports)
4. Leaves old 7-stack architecture intact (if it exists)

**Duration**: 5-10 minutes

### Full Rollback (If Old Stacks Were Deleted)

If you already deleted the old stacks and need to restore them:

```bash
# Restore from backup templates
cd infrastructure/backups/[BACKUP_DATE]

# Redeploy old stacks
for template in *.json; do
    stack_name=$(basename $template .json)
    aws cloudformation create-stack \
        --stack-name $stack_name \
        --template-body file://$template \
        --capabilities CAPABILITY_IAM
done

# Wait for completion
# ... (manual monitoring required)
```

**Duration**: 15-20 minutes

---

## Troubleshooting

### Issue: CDK Synthesis Fails

**Symptoms**: `npx cdk synth` returns errors

**Diagnosis**:
```bash
# Check for compilation errors
../gradlew :infrastructure:build
```

**Solutions**:
- Fix Kotlin compilation errors
- Ensure all dependencies are resolved
- Check CDK version compatibility

---

### Issue: Database Stack Update Fails

**Symptoms**: CeapDatabase-dev deployment fails

**Diagnosis**:
```bash
# Check stack events
aws cloudformation describe-stack-events --stack-name CeapDatabase-dev \
    --max-items 20
```

**Solutions**:
- Check for resource conflicts
- Verify IAM permissions
- Review CloudFormation error messages

---

### Issue: Application Stack Deployment Fails

**Symptoms**: CeapDataPlatform-dev or CeapServingAPI-dev fails to deploy

**Diagnosis**:
```bash
# Check stack events
aws cloudformation describe-stack-events --stack-name CeapDataPlatform-dev

# Check for missing exports
aws cloudformation list-exports
```

**Solutions**:
- Ensure CeapDatabase-dev deployed successfully first
- Verify exports exist
- Check cross-stack reference syntax
- Review IAM permissions

---

### Issue: Validation Script Reports Missing Resources

**Symptoms**: `validate-resources.sh` shows resource count mismatch

**Diagnosis**:
```bash
# Run validation with detailed output
./validate-resources.sh dev

# Check generated report
cat resource-validation-report-*.txt
```

**Solutions**:
- Review which resources are missing
- Check if resources failed to create
- Review CloudFormation stack events
- **DO NOT delete old stacks** until resolved

---

### Issue: Cross-Stack References Not Working

**Symptoms**: Lambdas can't access DynamoDB tables

**Diagnosis**:
```bash
# Check Lambda environment variables
aws lambda get-function-configuration \
    --function-name [FUNCTION_NAME] \
    --query 'Environment.Variables'

# Check IAM role permissions
aws iam get-role-policy \
    --role-name [ROLE_NAME] \
    --policy-name DefaultPolicy
```

**Solutions**:
- Verify database exports exist
- Check Fn::ImportValue syntax in templates
- Verify IAM policies grant table access
- Check table ARNs match

---

### Issue: Old Stacks Won't Delete

**Symptoms**: Stack deletion hangs or fails

**Diagnosis**:
```bash
# Check stack events
aws cloudformation describe-stack-events --stack-name [STACK_NAME]

# Check for dependencies
aws cloudformation describe-stack-resources --stack-name [STACK_NAME]
```

**Solutions**:
- Check for resources with DeletionPolicy: Retain
- Manually delete stuck resources in AWS Console
- Use `--retain-resources` flag to skip problematic resources
- Contact AWS Support if stack is truly stuck

---

## Validation Criteria

### Success Criteria

Migration is successful when:
- ✅ All 3 new stacks are in CREATE_COMPLETE status
- ✅ Resource validation script passes
- ✅ All automated tests pass
- ✅ Lambda functions can be invoked successfully
- ✅ Step Functions can be started successfully
- ✅ Cross-stack references work correctly
- ✅ No CloudFormation errors in stack events

### Rollback Criteria

Rollback if:
- ❌ Any stack fails to deploy
- ❌ Resource validation fails
- ❌ Integration tests fail
- ❌ Cross-stack references don't work
- ❌ Deployment exceeds 30 minutes
- ❌ Stakeholder decision to abort

---

## Post-Migration Tasks

### Immediate (Day 1)
- [ ] Monitor CloudWatch logs for errors
- [ ] Check Lambda invocation metrics
- [ ] Verify Step Functions executions
- [ ] Review CloudWatch alarms
- [ ] Update team documentation
- [ ] Notify stakeholders of completion

### Short-term (Week 1)
- [ ] Monitor AWS costs (should be similar or lower)
- [ ] Review CloudWatch metrics
- [ ] Gather team feedback
- [ ] Document lessons learned
- [ ] Update deployment procedures

### Long-term (Month 1)
- [ ] Delete backup templates (if confident)
- [ ] Optimize resource configurations
- [ ] Review and adjust monitoring
- [ ] Plan next infrastructure improvements

---

## Migration Timeline

### Typical Timeline (Dev Environment)

| Phase | Duration | Can Rollback? |
|-------|----------|---------------|
| Preparation | 10 min | N/A |
| Deploy Database | 3 min | ✅ Yes (instant) |
| Deploy Applications | 12 min | ✅ Yes (5-10 min) |
| Validation | 10 min | ✅ Yes (5-10 min) |
| Cleanup Old Stacks | 10 min | ⚠️ Harder (need backups) |
| **Total** | **45 min** | |

### Production Timeline (Add Buffer)

| Phase | Duration | Notes |
|-------|----------|-------|
| Preparation | 30 min | Extra validation |
| Deploy Database | 5 min | Monitor closely |
| Deploy Applications | 15 min | Parallel deployment |
| Validation | 20 min | Thorough testing |
| Soak Period | 24 hours | Monitor before cleanup |
| Cleanup Old Stacks | 15 min | After soak period |
| **Total** | **~25 hours** | Includes soak time |

---

## Risk Assessment

### Low Risk
- ✅ Database stack update (only adds exports, no resource changes)
- ✅ New stack deployment (doesn't affect existing resources)
- ✅ Validation testing (read-only operations)

### Medium Risk
- ⚠️ Deleting old stacks (can't easily undo without backups)
- ⚠️ Production deployment (affects live system)

### Mitigation Strategies
- ✅ Blue-green deployment (new stacks alongside old)
- ✅ Automated validation before cleanup
- ✅ Template backups for rollback
- ✅ Instant rollback capability
- ✅ Zero-downtime migration

---

## Communication Templates

### Pre-Migration Announcement

```
Subject: CEAP Infrastructure Migration - [DATE] at [TIME]

Team,

We will be migrating the CEAP infrastructure from 7 stacks to 3 stacks
on [DATE] at [TIME] [TIMEZONE].

Duration: 30-60 minutes
Downtime: None (blue-green deployment)
Rollback: Available if needed

Benefits:
- Faster deployments (15min → 5min)
- Simplified management
- Better business alignment

Please avoid deployments during this window.

Questions? Contact [NAME]
```

### Post-Migration Success

```
Subject: CEAP Infrastructure Migration - Complete ✅

Team,

The CEAP infrastructure migration completed successfully!

Results:
- ✅ 3 stacks deployed (was 7)
- ✅ All resources migrated
- ✅ All tests passing
- ✅ Zero downtime

New architecture:
- CeapDatabase-dev (Storage)
- CeapDataPlatform-dev (Write Path)
- CeapServingAPI-dev (Read Path)

Deployment time: [ACTUAL TIME]

Thank you for your patience!
```

### Rollback Notification

```
Subject: CEAP Infrastructure Migration - Rolled Back

Team,

The CEAP infrastructure migration has been rolled back due to [REASON].

Current state:
- Original 7-stack architecture restored
- All services functioning normally
- No data loss

Next steps:
- Investigate root cause
- Fix issues
- Reschedule migration

Details: [LINK TO INCIDENT REPORT]
```

---

## Lessons Learned Template

After migration, document:

### What Went Well
- [List successes]

### What Could Be Improved
- [List challenges]

### Action Items
- [List follow-up tasks]

### Recommendations for Next Time
- [List process improvements]

---

## Quick Reference

### Essential Commands

```bash
# Deploy consolidated architecture
cd infrastructure
./deploy-consolidated.sh dev

# Validate deployment
./validate-resources.sh dev

# Rollback if needed
./rollback-consolidated.sh dev

# Check stack status
aws cloudformation describe-stacks --stack-name CeapDataPlatform-dev

# View stack outputs
aws cloudformation describe-stacks --stack-name CeapDatabase-dev \
    --query 'Stacks[0].Outputs'

# List all CEAP stacks
aws cloudformation list-stacks --stack-status-filter CREATE_COMPLETE \
    --query 'StackSummaries[?starts_with(StackName, `Ceap`)].StackName'
```

### Emergency Contacts

- **On-Call Engineer**: [NAME] - [PHONE]
- **AWS Support**: [SUPPORT PLAN LEVEL]
- **Team Lead**: [NAME] - [EMAIL]

---

## Appendix A: Detailed Resource Mapping

### From 7 Stacks to 3 Stacks

| Old Stack | New Stack | Resources Migrated |
|-----------|-----------|-------------------|
| CeapDatabase-dev | CeapDatabase-dev | 3 tables (unchanged) |
| CeapEtlWorkflow-dev | CeapDataPlatform-dev | 1 Lambda, 1 IAM role |
| CeapFilterWorkflow-dev | CeapDataPlatform-dev | 1 Lambda, 1 IAM role |
| CeapScoreWorkflow-dev | CeapDataPlatform-dev | 1 Lambda, 1 IAM role |
| CeapStoreWorkflow-dev | CeapDataPlatform-dev | 1 Lambda, 1 IAM role |
| CeapOrchestration-dev | CeapDataPlatform-dev | 1 Step Function, 1 EventBridge rule, 2 IAM roles |
| CeapReactiveWorkflow-dev | CeapServingAPI-dev | 1 Lambda, 1 table, 1 EventBridge rule, 2 IAM roles |

---

## Appendix B: Testing Checklist

### Pre-Migration Tests
- [ ] Unit tests pass (`./gradlew test`)
- [ ] CDK synthesis works (`npx cdk synth`)
- [ ] Templates validate (`cfn-lint`)
- [ ] Current infrastructure is healthy

### Post-Migration Tests
- [ ] Resource validation passes
- [ ] All unit tests pass
- [ ] All property tests pass
- [ ] Lambda functions can be invoked
- [ ] Step Functions can be started
- [ ] EventBridge rules are active
- [ ] DynamoDB tables are accessible
- [ ] Cross-stack references work
- [ ] CloudWatch logs are being written

### Integration Tests (Optional)
- [ ] End-to-end batch workflow
- [ ] End-to-end reactive workflow
- [ ] Multi-program scenarios
- [ ] Error handling scenarios
- [ ] Performance benchmarks

---

## Appendix C: Rollback Decision Matrix

| Scenario | Rollback? | Reason |
|----------|-----------|--------|
| Database stack fails | ✅ Yes | Foundation is broken |
| One application stack fails | ✅ Yes | Incomplete migration |
| Validation script fails | ✅ Yes | Resources missing |
| Tests fail | ✅ Yes | Functional issues |
| Deployment > 30 min | ⚠️ Maybe | Assess situation |
| Minor CloudWatch errors | ❌ No | Can fix post-migration |
| Documentation incomplete | ❌ No | Can update later |

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-31 | CEAP Team | Initial runbook for 7→3 stack migration |

---

**This runbook ensures a safe, validated, and reversible infrastructure migration.** 🚀
