# CEAP Infrastructure Deployment

This directory contains the AWS CDK infrastructure code and deployment scripts for the CEAP (Candidate Evaluation and Processing) platform.

## Overview

The CEAP infrastructure consists of:

1. **Base Infrastructure** (Consolidated 3-stack architecture)
   - CeapDatabase: DynamoDB tables and storage layer
   - CeapDataPlatform: Write path (ETL, filtering, scoring, storage)
   - CeapServingAPI: Read path (serving API and reactive workflows)

2. **Workflow Orchestration** (Step Functions with S3-based storage)
   - CeapWorkflowStorage: S3 bucket for intermediate outputs
   - CeapWorkflowLambdas: Lambda functions with base handler
   - CeapWorkflowOrchestration: Step Functions workflows and EventBridge Pipes

## Prerequisites

- **AWS CLI**: Configured with appropriate credentials
- **Node.js 20+**: For CDK CLI
- **Java 17+**: For building Lambda functions
- **Gradle**: For building the project
- **CDK CLI**: Install with `npm install -g aws-cdk`

## Deployment Scripts

### Base Infrastructure

#### deploy-cdk.sh
Deploy the base CEAP infrastructure using CDK.

```bash
# Deploy to dev environment
./infrastructure/deploy-cdk.sh -e dev

# Deploy specific stack
./infrastructure/deploy-cdk.sh -e dev -s CeapDatabase

# Show diff before deploying
./infrastructure/deploy-cdk.sh -e dev -d

# Bootstrap CDK (first-time setup)
./infrastructure/deploy-cdk.sh -e dev -b
```

**Options:**
- `-e, --environment ENV`: Environment (dev, staging, prod) [default: dev]
- `-r, --region REGION`: AWS region [default: us-east-1]
- `-p, --profile PROFILE`: AWS CLI profile [default: default]
- `-s, --stack STACK`: Deploy specific stack only
- `-a, --all`: Deploy all stacks
- `-b, --bootstrap`: Bootstrap CDK (first-time setup)
- `-d, --diff`: Show diff before deploying
- `-h, --help`: Show help message

#### deploy-consolidated.sh
Deploy the consolidated 3-stack architecture in the correct order.

```bash
# Deploy to dev environment
./infrastructure/deploy-consolidated.sh dev

# Deploy to production
./infrastructure/deploy-consolidated.sh prod
```

**Deployment Order:**
1. CeapDatabase (storage layer with exports)
2. CeapDataPlatform and CeapServingAPI (parallel deployment)

#### rollback-consolidated.sh
Rollback the consolidated infrastructure.

```bash
# Rollback dev environment
./infrastructure/rollback-consolidated.sh dev

# Rollback with database revert
./infrastructure/rollback-consolidated.sh dev
# (Follow prompts to revert database exports)
```

**Safety Features:**
- Confirmation prompts before deletion
- Retains database stack by default
- Optional database export removal

### Workflow Orchestration

#### deploy-workflow.sh
Deploy the Step Functions workflow orchestration infrastructure.

```bash
# Deploy Express workflow (fast, <5 minutes)
./infrastructure/deploy-workflow.sh -e dev -t express

# Deploy Standard workflow (long-running, supports Glue)
./infrastructure/deploy-workflow.sh -e dev -t standard

# Show diff before deploying
./infrastructure/deploy-workflow.sh -e dev -d

# Validate only (no deployment)
./infrastructure/deploy-workflow.sh -e dev -v
```

**Options:**
- `-e, --environment ENV`: Environment (dev, staging, prod) [default: dev]
- `-r, --region REGION`: AWS region [default: us-east-1]
- `-p, --profile PROFILE`: AWS CLI profile [default: default]
- `-t, --type TYPE`: Workflow type (express, standard) [default: express]
- `-d, --diff`: Show diff before deploying
- `-v, --validate`: Validate only, don't deploy
- `-h, --help`: Show help message

**Deployment Phases:**
1. S3 workflow bucket with lifecycle policy
2. Lambda functions with base handler
3. Step Functions workflow and EventBridge Pipe

#### rollback-workflow.sh
Rollback the workflow orchestration infrastructure.

```bash
# Rollback with confirmation
./infrastructure/rollback-workflow.sh -e dev

# Force rollback (skip confirmations)
./infrastructure/rollback-workflow.sh -e dev -f

# Keep S3 execution data
./infrastructure/rollback-workflow.sh -e dev -k
```

**Options:**
- `-e, --environment ENV`: Environment (dev, staging, prod) [default: dev]
- `-r, --region REGION`: AWS region [default: us-east-1]
- `-p, --profile PROFILE`: AWS CLI profile [default: default]
- `-f, --force`: Skip confirmation prompts
- `-k, --keep-data`: Keep S3 execution data (don't delete bucket)
- `-h, --help`: Show help message

**Safety Features:**
- Drains SQS queue before deletion
- Archives S3 execution data before deletion
- Validates no active executions
- Provides detailed rollback status

#### smoke-test-workflow.sh
Post-deployment validation of workflow orchestration.

```bash
# Run smoke test
./infrastructure/smoke-test-workflow.sh -e dev

# Verbose output
./infrastructure/smoke-test-workflow.sh -e dev -v

# Custom timeout
./infrastructure/smoke-test-workflow.sh -e dev -t 600
```

**Options:**
- `-e, --environment ENV`: Environment (dev, staging, prod) [default: dev]
- `-r, --region REGION`: AWS region [default: us-east-1]
- `-p, --profile PROFILE`: AWS CLI profile [default: default]
- `-t, --timeout SECONDS`: Execution timeout [default: 300]
- `-v, --verbose`: Verbose output
- `-h, --help`: Show help message

**Test Scenarios:**
1. Send test message to SQS queue
2. Verify Step Functions execution starts
3. Wait for execution completion
4. Verify S3 outputs for all stages
5. Validate final output correctness
6. Check CloudWatch Logs
7. Verify S3 lifecycle policy

### Validation Scripts

#### validate-template.sh
Validate CloudFormation templates.

```bash
./infrastructure/validate-template.sh
```

#### validate-resources.sh
Validate deployed resources.

```bash
./infrastructure/validate-resources.sh
```

#### validate-lambda-template.sh
Validate Lambda function templates.

```bash
./infrastructure/validate-lambda-template.sh
```

## Deployment Workflows

### Initial Deployment

1. **Bootstrap CDK** (first-time only):
   ```bash
   ./infrastructure/deploy-cdk.sh -e dev -b
   ```

2. **Deploy Base Infrastructure**:
   ```bash
   ./infrastructure/deploy-consolidated.sh dev
   ```

3. **Deploy Workflow Orchestration**:
   ```bash
   ./infrastructure/deploy-workflow.sh -e dev -t express
   ```

4. **Run Smoke Tests**:
   ```bash
   ./infrastructure/smoke-test-workflow.sh -e dev
   ```

### Update Deployment

1. **Build Lambda JARs**:
   ```bash
   ./gradlew shadowJar
   ```

2. **Deploy Changes**:
   ```bash
   ./infrastructure/deploy-workflow.sh -e dev -d
   ```

3. **Verify Deployment**:
   ```bash
   ./infrastructure/smoke-test-workflow.sh -e dev
   ```

### Rollback

1. **Rollback Workflow Orchestration**:
   ```bash
   ./infrastructure/rollback-workflow.sh -e dev
   ```

2. **Rollback Base Infrastructure** (if needed):
   ```bash
   ./infrastructure/rollback-consolidated.sh dev
   ```

## Environment Configuration

### Development (dev)
- Purpose: Development and testing
- Workflow Type: Express (fast iteration)
- Retention: 7 days
- Cost: Optimized for development

### Staging (staging)
- Purpose: Pre-production testing
- Workflow Type: Express or Standard
- Retention: 14 days
- Cost: Production-like

### Production (prod)
- Purpose: Production workloads
- Workflow Type: Standard (long-running support)
- Retention: 30 days
- Cost: Optimized for reliability

## Monitoring and Operations

### CloudWatch Dashboards

Access CloudWatch dashboards:
```bash
# Open AWS Console
aws cloudwatch get-dashboard \
  --dashboard-name WorkflowMonitoring \
  --profile default
```

### CloudWatch Logs

View Step Functions logs:
```bash
aws logs tail /aws/stepfunction/CeapWorkflow-dev --follow
```

View Lambda logs:
```bash
aws logs tail /aws/lambda/CeapServingAPI-dev-FilterLambdaFunction --follow
```

### S3 Execution Data

List execution data:
```bash
aws s3 ls s3://ceap-workflow-dev-123456789/executions/ --recursive
```

Download execution data:
```bash
aws s3 cp s3://ceap-workflow-dev-123456789/executions/abc-123/ ./debug/ --recursive
```

### Step Functions Executions

List recent executions:
```bash
aws stepfunctions list-executions \
  --state-machine-arn arn:aws:states:us-east-1:123456789:stateMachine:CeapWorkflow-dev \
  --max-results 10
```

Describe execution:
```bash
aws stepfunctions describe-execution \
  --execution-arn arn:aws:states:us-east-1:123456789:execution:CeapWorkflow-dev:abc-123
```

## Troubleshooting

### Common Issues

**Issue**: CDK synthesis fails
**Solution**: Run `./gradlew build` to ensure project compiles

**Issue**: Lambda deployment fails
**Solution**: Check Lambda function size (<250MB unzipped)

**Issue**: Step Functions execution fails
**Solution**: Check CloudWatch Logs for error details

**Issue**: S3 access denied
**Solution**: Verify IAM roles have S3 read/write permissions

### Debug Commands

Check stack status:
```bash
aws cloudformation describe-stacks \
  --stack-name CeapWorkflowOrchestration-dev
```

Get stack outputs:
```bash
aws cloudformation describe-stacks \
  --stack-name CeapWorkflowOrchestration-dev \
  --query 'Stacks[0].Outputs'
```

List stack resources:
```bash
aws cloudformation list-stack-resources \
  --stack-name CeapWorkflowOrchestration-dev
```

## Documentation

- [Workflow Orchestration Guide](../docs/WORKFLOW-ORCHESTRATION-GUIDE.md)
- [Operations Runbook](../docs/WORKFLOW-OPERATIONS-RUNBOOK.md)
- [Deployment Guide](../docs/DEPLOYMENT-GUIDE.md)
- [Troubleshooting Guide](../docs/TROUBLESHOOTING.md)

## Support

For issues or questions:
- Team Slack: #ceap-ops
- On-Call: [Pager Duty]
- AWS Support: [Support Portal]

## License

Internal use only - Amazon Confidential
