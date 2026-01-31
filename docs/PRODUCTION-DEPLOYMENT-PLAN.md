# CEAP Infrastructure - Production Deployment Plan
# 3-Stack Consolidated Architecture

**Document Version**: 1.0  
**Created**: January 31, 2026  
**Target Environment**: Production  
**Deployment Type**: Infrastructure Consolidation (7→3 stacks)

---

## Deployment Window

### Proposed Schedule

**Date**: [TO BE DETERMINED]  
**Start Time**: [TO BE DETERMINED] (e.g., 2:00 AM EST)  
**End Time**: [TO BE DETERMINED] (e.g., 3:00 AM EST)  
**Duration**: 60 minutes (with 30-minute buffer)  
**Timezone**: [TO BE DETERMINED]

### Rationale for Timing
- Low traffic period (minimize impact)
- Engineering team available
- AWS Support available (if needed)
- Buffer time for unexpected issues

---

## Stakeholders

### Notification List

**Engineering Team**:
- [ ] Platform Lead: [NAME] - [EMAIL] - [PHONE]
- [ ] DevOps Engineer: [NAME] - [EMAIL] - [PHONE]
- [ ] Backend Engineer: [NAME] - [EMAIL] - [PHONE]
- [ ] QA Engineer: [NAME] - [EMAIL] - [PHONE]

**Business Team**:
- [ ] Product Manager: [NAME] - [EMAIL]
- [ ] Customer Success: [NAME] - [EMAIL]
- [ ] Operations Manager: [NAME] - [EMAIL]

**On-Call**:
- [ ] Primary On-Call: [NAME] - [PHONE]
- [ ] Secondary On-Call: [NAME] - [PHONE]
- [ ] Escalation Contact: [NAME] - [PHONE]

### Communication Plan

**T-7 days**: Initial announcement
- Email to all stakeholders
- Deployment window proposed
- Request for conflicts

**T-3 days**: Confirmation
- Confirm deployment window
- Share detailed timeline
- Provide rollback plan

**T-1 day**: Final reminder
- Reminder email
- Pre-deployment checklist review
- Confirm on-call assignments

**T-0 (Deployment Day)**:
- **T-30min**: Pre-deployment checks
- **T-0**: Start deployment
- **T+15min**: Status update (Phase 1 complete)
- **T+30min**: Status update (Phase 2 complete)
- **T+45min**: Validation complete
- **T+60min**: Deployment complete announcement

**T+1 day**: Post-deployment review
- Metrics review
- Lessons learned
- Documentation updates

---

## Success Criteria

### Deployment Success

Deployment is successful when:
- ✅ All 3 stacks deploy successfully (CREATE_COMPLETE status)
- ✅ Resource validation script passes
- ✅ All automated tests pass
- ✅ Lambda functions respond to invocations
- ✅ Step Functions can be started
- ✅ EventBridge rules are active
- ✅ DynamoDB tables are accessible
- ✅ Cross-stack references work
- ✅ CloudWatch logs are being written
- ✅ No errors in CloudFormation events
- ✅ Deployment completes within 60 minutes

### Functional Success

System is functional when:
- ✅ Batch workflows execute successfully
- ✅ Reactive workflows process events
- ✅ API responses are correct
- ✅ Data is being written to DynamoDB
- ✅ Monitoring and alarms are working
- ✅ No increase in error rates
- ✅ Performance metrics are normal

---

## Rollback Triggers

### Automatic Rollback Triggers

Rollback immediately if:
- ❌ Any CloudFormation stack fails to deploy
- ❌ Resource validation script fails
- ❌ Critical automated tests fail
- ❌ Deployment exceeds 90 minutes
- ❌ Database stack update fails

### Manual Rollback Triggers

Consider rollback if:
- ⚠️ Lambda error rate > 5%
- ⚠️ Step Functions failure rate > 10%
- ⚠️ DynamoDB throttling detected
- ⚠️ Unexpected AWS costs
- ⚠️ Stakeholder concerns
- ⚠️ Customer-facing issues

### Rollback Decision Authority

**Can authorize rollback**:
- Platform Lead
- On-Call Engineer (for critical issues)
- Product Manager (for business impact)

**Rollback procedure**: See `docs/MIGRATION-RUNBOOK.md` Section "Rollback Procedure"

---

## Deployment Procedure

### Phase 0: Pre-Deployment (T-30 minutes)

#### Checklist
- [ ] All stakeholders notified
- [ ] On-call engineers confirmed
- [ ] AWS credentials verified
- [ ] Latest code pulled from main branch
- [ ] All tests passing locally
- [ ] CDK synthesis successful
- [ ] Backup templates exported
- [ ] Rollback plan reviewed
- [ ] Communication channels open (Slack/Teams)

#### Commands
```bash
# Verify AWS access
aws sts get-caller-identity

# Pull latest code
git pull origin main

# Run tests
./gradlew :infrastructure:test

# Synthesize templates
cd infrastructure
npx cdk synth --output cdk.out.consolidated
```

---

### Phase 1: Deploy Database Stack (T+0 to T+5)

#### Actions
```bash
# Deploy database stack with exports
npx cdk deploy CeapDatabase-prod \
    --app "cdk.out.consolidated" \
    --require-approval never
```

#### Expected Duration
3-5 minutes

#### Success Criteria
- Stack status: CREATE_COMPLETE or UPDATE_COMPLETE
- 6 outputs created (table names and ARNs)
- No errors in stack events

#### Monitoring
```bash
# Watch deployment progress
watch -n 5 'aws cloudformation describe-stacks --stack-name CeapDatabase-prod --query "Stacks[0].StackStatus"'
```

#### Rollback (If Needed)
```bash
# Database stack update is low-risk, but if it fails:
aws cloudformation cancel-update-stack --stack-name CeapDatabase-prod
```

---

### Phase 2: Deploy Application Stacks (T+5 to T+20)

#### Actions
```bash
# Deploy both application stacks in parallel
npx cdk deploy CeapDataPlatform-prod CeapServingAPI-prod \
    --app "cdk.out.consolidated" \
    --concurrency 2 \
    --require-approval never
```

#### Expected Duration
10-15 minutes (parallel deployment)

#### Success Criteria
- Both stacks status: CREATE_COMPLETE
- All Lambda functions created
- Step Functions state machine created
- EventBridge rules created
- IAM roles and policies created
- No errors in stack events

#### Monitoring
```bash
# Monitor both stacks
watch -n 5 'aws cloudformation describe-stacks \
    --stack-name CeapDataPlatform-prod \
    --query "Stacks[0].StackStatus" && \
    aws cloudformation describe-stacks \
    --stack-name CeapServingAPI-prod \
    --query "Stacks[0].StackStatus"'
```

#### Rollback (If Needed)
```bash
cd infrastructure
./rollback-consolidated.sh prod
```

---

### Phase 3: Validation (T+20 to T+35)

#### Actions
```bash
# Run resource validation
./validate-resources.sh prod

# Run automated tests
cd ..
./gradlew :infrastructure:test

# Test Lambda invocation
aws lambda invoke \
    --function-name [FUNCTION_NAME] \
    --payload '{"test": true}' \
    response.json
```

#### Expected Duration
10-15 minutes

#### Success Criteria
- Validation script passes
- All tests pass
- Lambda functions respond
- Step Functions can be started
- No errors in CloudWatch logs

#### Rollback (If Needed)
If validation fails, rollback immediately:
```bash
cd infrastructure
./rollback-consolidated.sh prod
```

---

### Phase 4: Soak Period (T+35 to T+24h)

#### Actions
- Monitor CloudWatch metrics
- Watch for errors in logs
- Check Lambda invocation counts
- Verify Step Functions executions
- Monitor DynamoDB metrics
- Review AWS costs

#### Duration
24 hours (recommended before cleanup)

#### Monitoring Checklist
- [ ] Lambda error rate < 1%
- [ ] Step Functions success rate > 95%
- [ ] DynamoDB throttling = 0
- [ ] API latency normal
- [ ] No customer complaints
- [ ] AWS costs as expected

---

### Phase 5: Cleanup Old Stacks (T+24h to T+24h+15m)

**⚠️ ONLY proceed if soak period is successful!**

#### Actions
```bash
# Delete old workflow stacks
aws cloudformation delete-stack --stack-name CeapOrchestration-prod
aws cloudformation delete-stack --stack-name CeapReactiveWorkflow-prod
aws cloudformation delete-stack --stack-name CeapStoreWorkflow-prod
aws cloudformation delete-stack --stack-name CeapScoreWorkflow-prod
aws cloudformation delete-stack --stack-name CeapFilterWorkflow-prod
aws cloudformation delete-stack --stack-name CeapEtlWorkflow-prod

# Wait for deletions
for stack in CeapOrchestration-prod CeapReactiveWorkflow-prod \
             CeapStoreWorkflow-prod CeapScoreWorkflow-prod \
             CeapFilterWorkflow-prod CeapEtlWorkflow-prod; do
    aws cloudformation wait stack-delete-complete --stack-name $stack
    echo "✅ $stack deleted"
done
```

#### Expected Duration
10-15 minutes

#### Success Criteria
- All old stacks deleted
- Only 3 stacks remain
- No errors during deletion
- New stacks still functional

---

## Monitoring Plan

### Real-Time Monitoring (During Deployment)

**CloudFormation Events**:
```bash
# Watch stack events
aws cloudformation describe-stack-events \
    --stack-name CeapDataPlatform-prod \
    --max-items 20
```

**Lambda Metrics**:
- Invocations
- Errors
- Duration
- Throttles

**DynamoDB Metrics**:
- Read/Write capacity
- Throttled requests
- System errors

**Step Functions Metrics**:
- Executions started
- Executions succeeded
- Executions failed
- Execution time

### Post-Deployment Monitoring (24 hours)

**CloudWatch Dashboards**:
- Create temporary dashboard for migration monitoring
- Include all key metrics
- Set up alerts for anomalies

**Log Monitoring**:
```bash
# Tail logs for errors
aws logs tail /aws/lambda/CeapDataPlatform-prod-ETLLambdaFunction* \
    --follow --filter-pattern "ERROR"
```

**Cost Monitoring**:
```bash
# Check daily costs
aws ce get-cost-and-usage \
    --time-period Start=$(date +%Y-%m-%d),End=$(date -d "+1 day" +%Y-%m-%d) \
    --granularity DAILY \
    --metrics BlendedCost
```

---

## Risk Mitigation

### Risk 1: Stack Deployment Failure

**Probability**: Low  
**Impact**: Medium  
**Mitigation**:
- Tested in dev and staging
- Automated validation
- Instant rollback available

**Response**:
1. Review CloudFormation error messages
2. Check IAM permissions
3. Verify resource quotas
4. Rollback if can't resolve quickly

### Risk 2: Cross-Stack Reference Issues

**Probability**: Low  
**Impact**: High  
**Mitigation**:
- Property tests validate references
- Tested in dev environment
- Database exports verified

**Response**:
1. Check database stack outputs
2. Verify Fn::ImportValue syntax
3. Check parameter values
4. Rollback if references broken

### Risk 3: Performance Degradation

**Probability**: Very Low  
**Impact**: Medium  
**Mitigation**:
- No functional changes
- Same Lambda configurations
- Same DynamoDB settings

**Response**:
1. Check CloudWatch metrics
2. Compare with baseline
3. Investigate specific bottlenecks
4. Rollback if significant degradation

### Risk 4: Unexpected Costs

**Probability**: Very Low  
**Impact**: Low  
**Mitigation**:
- Same resources, same costs
- Monitoring enabled
- Budget alerts configured

**Response**:
1. Review AWS Cost Explorer
2. Identify cost drivers
3. Optimize if needed
4. Costs should be identical or lower

---

## Success Metrics

### Deployment Metrics

| Metric | Target | Actual |
|--------|--------|--------|
| Total deployment time | < 60 min | [FILL IN] |
| Database stack time | < 5 min | [FILL IN] |
| Application stacks time | < 15 min | [FILL IN] |
| Validation time | < 15 min | [FILL IN] |
| Rollback time (if needed) | < 10 min | N/A |

### Quality Metrics

| Metric | Target | Actual |
|--------|--------|--------|
| Tests passing | 100% | [FILL IN] |
| Resource validation | PASS | [FILL IN] |
| Lambda error rate | < 1% | [FILL IN] |
| Step Functions success rate | > 95% | [FILL IN] |
| API latency | < 500ms | [FILL IN] |

### Business Metrics

| Metric | Target | Actual |
|--------|--------|--------|
| Downtime | 0 minutes | [FILL IN] |
| Customer impact | None | [FILL IN] |
| Support tickets | 0 | [FILL IN] |
| Cost change | ±0% | [FILL IN] |

---

## Go/No-Go Decision

### Go Criteria (All Must Be True)

- [ ] All pre-deployment tests pass
- [ ] Stakeholders approve
- [ ] On-call engineers available
- [ ] Rollback plan tested
- [ ] Communication sent
- [ ] AWS account healthy
- [ ] No ongoing incidents
- [ ] Weather is clear (no AWS outages)

### No-Go Criteria (Any Triggers Postponement)

- [ ] Tests failing
- [ ] Stakeholder concerns
- [ ] On-call unavailable
- [ ] Ongoing incidents
- [ ] AWS service issues
- [ ] Major holidays/events
- [ ] Recent production issues

---

## Deployment Team Roles

### Deployment Lead
**Responsibilities**:
- Execute deployment commands
- Monitor progress
- Make go/no-go decisions
- Coordinate team

**Person**: [NAME]

### Validation Engineer
**Responsibilities**:
- Run validation scripts
- Execute tests
- Verify resources
- Report status

**Person**: [NAME]

### Monitoring Engineer
**Responsibilities**:
- Watch CloudWatch metrics
- Monitor logs
- Check for errors
- Alert team to issues

**Person**: [NAME]

### Communication Lead
**Responsibilities**:
- Send status updates
- Notify stakeholders
- Document progress
- Handle escalations

**Person**: [NAME]

---

## Communication Templates

### T-7 Days: Initial Announcement

```
Subject: CEAP Production Infrastructure Migration - [DATE]

Team,

We will be migrating the CEAP production infrastructure to a consolidated
3-stack architecture on [DATE] at [TIME] [TIMEZONE].

What's Changing:
- Infrastructure consolidation (7 stacks → 3 stacks)
- No functional changes
- No API changes
- No data migration

Benefits:
- 67% faster deployments
- Simplified management
- Better business alignment
- Improved reliability

Impact:
- Expected downtime: 0 minutes (blue-green deployment)
- Duration: 60 minutes
- Rollback available if needed

Timeline:
- [DATE] [TIME]: Deployment starts
- [DATE] [TIME+1h]: Deployment completes
- [DATE+1d]: Post-deployment review

Please avoid production deployments during this window.

Questions? Reply to this email or contact [NAME].

Thank you!
```

### T-0: Deployment Started

```
Subject: CEAP Migration - Started

Team,

CEAP production infrastructure migration has started.

Status: IN PROGRESS
Started: [TIME]
Expected completion: [TIME+1h]

Live updates: [SLACK CHANNEL / TEAMS CHANNEL]

Monitoring: [CLOUDWATCH DASHBOARD LINK]
```

### T+60: Deployment Complete

```
Subject: CEAP Migration - Complete ✅

Team,

CEAP production infrastructure migration completed successfully!

Results:
- ✅ 3 stacks deployed (was 7)
- ✅ All resources migrated
- ✅ All tests passing
- ✅ Zero downtime
- ✅ Completed in [ACTUAL TIME]

New architecture:
- CeapDatabase-prod (Storage Layer)
- CeapDataPlatform-prod (Write Path)
- CeapServingAPI-prod (Read Path)

Next steps:
- 24-hour monitoring period
- Old stacks cleanup (after soak period)
- Post-deployment review

Thank you for your patience!
```

### Rollback Notification

```
Subject: CEAP Migration - Rolled Back ⚠️

Team,

The CEAP production infrastructure migration has been rolled back.

Reason: [SPECIFIC REASON]
Rollback time: [TIME]
Current state: Original 7-stack architecture restored

Impact:
- All services functioning normally
- No data loss
- No customer impact

Next steps:
- Root cause analysis
- Fix identified issues
- Reschedule migration

Incident report: [LINK]

Questions? Contact [NAME]
```

---

## Detailed Deployment Steps

### Step 1: Pre-Deployment Verification (15 minutes)

```bash
# 1. Verify AWS credentials
aws sts get-caller-identity

# 2. Check current stack status
aws cloudformation list-stacks --stack-status-filter CREATE_COMPLETE \
    --query 'StackSummaries[?starts_with(StackName, `Ceap`)].StackName'

# 3. Export current templates (backup)
mkdir -p backups/prod-$(date +%Y%m%d-%H%M%S)
for stack in CeapDatabase-prod CeapEtlWorkflow-prod CeapFilterWorkflow-prod \
             CeapScoreWorkflow-prod CeapStoreWorkflow-prod \
             CeapReactiveWorkflow-prod CeapOrchestration-prod; do
    aws cloudformation get-template --stack-name $stack \
        --query 'TemplateBody' > backups/prod-$(date +%Y%m%d)/${stack}.json 2>/dev/null || true
done

# 4. Build and test
cd infrastructure
../gradlew build
../gradlew test

# 5. Synthesize CDK
npx cdk synth --output cdk.out.consolidated --context environment=prod
```

**Checkpoint**: All commands succeed, no errors

---

### Step 2: Deploy Database Stack (5 minutes)

```bash
# Deploy with monitoring
npx cdk deploy CeapDatabase-prod \
    --app "cdk.out.consolidated" \
    --require-approval never \
    --context environment=prod

# Verify outputs
aws cloudformation describe-stacks --stack-name CeapDatabase-prod \
    --query 'Stacks[0].Outputs'
```

**Checkpoint**: 6 outputs exist, stack is CREATE_COMPLETE

---

### Step 3: Deploy Application Stacks (15 minutes)

```bash
# Deploy in parallel
npx cdk deploy CeapDataPlatform-prod CeapServingAPI-prod \
    --app "cdk.out.consolidated" \
    --concurrency 2 \
    --require-approval never \
    --context environment=prod
```

**Checkpoint**: Both stacks are CREATE_COMPLETE

---

### Step 4: Validation (15 minutes)

```bash
# Run validation script
./validate-resources.sh prod

# Run tests
cd ..
./gradlew :infrastructure:test

# Test Lambda functions
aws lambda invoke \
    --function-name [FUNCTION_NAME] \
    --payload '{"test": true}' \
    response.json

# Test Step Functions
aws stepfunctions start-execution \
    --state-machine-arn [STATE_MACHINE_ARN] \
    --input '{"programId":"test","marketplace":"US"}'
```

**Checkpoint**: All validations pass

---

### Step 5: Monitoring (24 hours)

```bash
# Monitor Lambda errors
aws cloudwatch get-metric-statistics \
    --namespace AWS/Lambda \
    --metric-name Errors \
    --dimensions Name=FunctionName,Value=[FUNCTION_NAME] \
    --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
    --period 300 \
    --statistics Sum

# Monitor Step Functions
aws cloudwatch get-metric-statistics \
    --namespace AWS/States \
    --metric-name ExecutionsFailed \
    --dimensions Name=StateMachineArn,Value=[STATE_MACHINE_ARN] \
    --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
    --period 300 \
    --statistics Sum
```

**Checkpoint**: No anomalies detected

---

### Step 6: Cleanup Old Stacks (15 minutes)

**⚠️ ONLY after successful 24-hour soak period!**

```bash
# Delete old stacks
for stack in CeapOrchestration-prod CeapReactiveWorkflow-prod \
             CeapStoreWorkflow-prod CeapScoreWorkflow-prod \
             CeapFilterWorkflow-prod CeapEtlWorkflow-prod; do
    aws cloudformation delete-stack --stack-name $stack
done

# Wait for deletions
for stack in CeapOrchestration-prod CeapReactiveWorkflow-prod \
             CeapStoreWorkflow-prod CeapScoreWorkflow-prod \
             CeapFilterWorkflow-prod CeapEtlWorkflow-prod; do
    aws cloudformation wait stack-delete-complete --stack-name $stack
    echo "✅ $stack deleted"
done
```

**Checkpoint**: Only 3 stacks remain

---

## Post-Deployment Review

### Metrics to Collect

**Deployment Metrics**:
- Actual deployment time
- Any issues encountered
- Rollback needed? (Yes/No)
- Downtime (should be 0)

**Quality Metrics**:
- Test pass rate
- Validation results
- Error rates (first 24h)
- Performance metrics

**Business Metrics**:
- Customer impact
- Support tickets
- Cost change
- Team feedback

### Review Meeting Agenda

1. **What went well**
   - Successes
   - Smooth processes
   - Effective preparations

2. **What could be improved**
   - Challenges
   - Unexpected issues
   - Process gaps

3. **Action items**
   - Follow-up tasks
   - Documentation updates
   - Process improvements

4. **Lessons learned**
   - Key takeaways
   - Recommendations for future migrations

---

## Appendix: Emergency Procedures

### If Deployment Hangs

1. Check CloudFormation events for stuck resources
2. Wait 15 minutes (some resources take time)
3. If still stuck after 30 minutes, cancel update:
   ```bash
   aws cloudformation cancel-update-stack --stack-name [STACK_NAME]
   ```
4. Investigate root cause
5. Rollback if necessary

### If Rollback Fails

1. Check CloudFormation events
2. Manually delete stuck resources in AWS Console
3. Retry rollback script
4. If still failing, contact AWS Support
5. Document state for post-mortem

### If Data Loss Suspected

1. **STOP ALL OPERATIONS**
2. Check DynamoDB point-in-time recovery
3. Compare table item counts before/after
4. Review CloudWatch logs for delete operations
5. Restore from backup if needed
6. Contact AWS Support immediately

### If Customer Impact Detected

1. Assess severity and scope
2. Notify customer success team
3. Check error rates and logs
4. Decide: Fix forward or rollback
5. Communicate with affected customers
6. Document incident

---

## Approval Signatures

**Deployment Approved By**:

- [ ] Platform Lead: _________________ Date: _______
- [ ] Product Manager: _________________ Date: _______
- [ ] Engineering Manager: _________________ Date: _______

**Deployment Executed By**:

- [ ] Engineer: _________________ Date: _______ Time: _______

**Deployment Verified By**:

- [ ] QA Engineer: _________________ Date: _______ Time: _______

---

## Document Control

**Version History**:

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-31 | CEAP Team | Initial production deployment plan |

**Next Review Date**: After production deployment

---

**This plan ensures a safe, coordinated, and successful production deployment.** 🚀
