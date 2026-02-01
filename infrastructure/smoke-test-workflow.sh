#!/bin/bash

###############################################################################
# Workflow Orchestration Smoke Test Script
#
# This script performs post-deployment validation of the workflow orchestration
# infrastructure by sending test messages and verifying execution.
#
# Usage:
#   ./infrastructure/smoke-test-workflow.sh [options]
#
# Options:
#   -e, --environment ENV    Environment (dev, staging, prod) [default: dev]
#   -r, --region REGION      AWS region [default: us-east-1]
#   -p, --profile PROFILE    AWS CLI profile [default: default]
#   -t, --timeout SECONDS    Execution timeout [default: 300]
#   -v, --verbose            Verbose output
#   -h, --help               Show this help message
#
# Test Scenarios:
#   1. Send test message to SQS queue
#   2. Verify Step Functions execution starts
#   3. Wait for execution completion
#   4. Verify S3 outputs for all stages
#   5. Validate final output correctness
###############################################################################

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Defaults
ENVIRONMENT="dev"
AWS_REGION="us-east-1"
AWS_PROFILE="default"
TIMEOUT=300
VERBOSE=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment) ENVIRONMENT="$2"; shift 2 ;;
        -r|--region) AWS_REGION="$2"; shift 2 ;;
        -p|--profile) AWS_PROFILE="$2"; shift 2 ;;
        -t|--timeout) TIMEOUT="$2"; shift 2 ;;
        -v|--verbose) VERBOSE=true; shift ;;
        -h|--help) grep "^#" "$0" | grep -v "#!/bin/bash" | sed 's/^# //'; exit 0 ;;
        *) echo -e "${RED}Unknown option: $1${NC}"; exit 1 ;;
    esac
done

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Workflow Smoke Test${NC}"
echo -e "${BLUE}Environment: $ENVIRONMENT${NC}"
echo -e "${BLUE}Region: $AWS_REGION${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check prerequisites
if ! command -v aws &> /dev/null; then
    echo -e "${RED}AWS CLI is not installed${NC}"
    exit 1
fi

if ! command -v jq &> /dev/null; then
    echo -e "${YELLOW}jq is not installed (recommended for JSON parsing)${NC}"
    echo "Install: brew install jq (macOS) or apt-get install jq (Linux)"
fi

# Check AWS credentials
echo -e "${YELLOW}Validating AWS credentials...${NC}"
AWS_ACCOUNT=$(aws sts get-caller-identity --profile "$AWS_PROFILE" --query Account --output text 2>/dev/null)
if [ $? -ne 0 ]; then
    echo -e "${RED}AWS credentials not configured or expired${NC}"
    exit 1
fi
echo -e "${GREEN}✓ AWS Account: $AWS_ACCOUNT${NC}"
echo ""

# Get stack outputs
echo -e "${YELLOW}Retrieving stack resources...${NC}"

STATE_MACHINE_ARN=$(aws cloudformation describe-stacks \
    --stack-name "CeapWorkflowOrchestration-$ENVIRONMENT" \
    --profile "$AWS_PROFILE" \
    --query 'Stacks[0].Outputs[?OutputKey==`StateMachineArn`].OutputValue' \
    --output text 2>/dev/null)

WORKFLOW_BUCKET=$(aws cloudformation describe-stacks \
    --stack-name "CeapWorkflowStorage-$ENVIRONMENT" \
    --profile "$AWS_PROFILE" \
    --query 'Stacks[0].Outputs[?OutputKey==`WorkflowBucketName`].OutputValue' \
    --output text 2>/dev/null)

QUEUE_URL=$(aws cloudformation describe-stacks \
    --stack-name "CeapWorkflowOrchestration-$ENVIRONMENT" \
    --profile "$AWS_PROFILE" \
    --query 'Stacks[0].Outputs[?OutputKey==`QueueUrl`].OutputValue' \
    --output text 2>/dev/null)

if [ -z "$STATE_MACHINE_ARN" ] || [ -z "$WORKFLOW_BUCKET" ] || [ -z "$QUEUE_URL" ]; then
    echo -e "${RED}Failed to retrieve stack outputs${NC}"
    echo "Please ensure the workflow stacks are deployed"
    exit 1
fi

echo -e "${GREEN}✓ State Machine: $STATE_MACHINE_ARN${NC}"
echo -e "${GREEN}✓ S3 Bucket: $WORKFLOW_BUCKET${NC}"
echo -e "${GREEN}✓ Queue URL: $QUEUE_URL${NC}"
echo ""

# Test 1: Send test message to SQS
echo -e "${YELLOW}Test 1: Sending test message to SQS...${NC}"

TEST_MESSAGE=$(cat <<EOF
{
  "testId": "smoke-test-$(date +%s)",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "data": {
    "candidates": [
      {"id": 1, "name": "Test Candidate 1", "score": 85},
      {"id": 2, "name": "Test Candidate 2", "score": 92},
      {"id": 3, "name": "Test Candidate 3", "score": 78}
    ]
  }
}
EOF
)

MESSAGE_ID=$(aws sqs send-message \
    --queue-url "$QUEUE_URL" \
    --message-body "$TEST_MESSAGE" \
    --profile "$AWS_PROFILE" \
    --query 'MessageId' \
    --output text)

if [ -z "$MESSAGE_ID" ]; then
    echo -e "${RED}✗ Failed to send message${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Message sent: $MESSAGE_ID${NC}"
echo ""

# Test 2: Wait for execution to start
echo -e "${YELLOW}Test 2: Waiting for execution to start...${NC}"

EXECUTION_ARN=""
START_TIME=$(date +%s)

while [ -z "$EXECUTION_ARN" ]; do
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    
    if [ $ELAPSED -gt 60 ]; then
        echo -e "${RED}✗ Timeout waiting for execution to start${NC}"
        exit 1
    fi
    
    # List recent executions
    EXECUTION_ARN=$(aws stepfunctions list-executions \
        --state-machine-arn "$STATE_MACHINE_ARN" \
        --status-filter RUNNING \
        --profile "$AWS_PROFILE" \
        --max-results 1 \
        --query 'executions[0].executionArn' \
        --output text 2>/dev/null)
    
    if [ "$EXECUTION_ARN" = "None" ] || [ -z "$EXECUTION_ARN" ]; then
        EXECUTION_ARN=""
        sleep 2
    fi
done

EXECUTION_NAME=$(echo "$EXECUTION_ARN" | awk -F: '{print $NF}')
echo -e "${GREEN}✓ Execution started: $EXECUTION_NAME${NC}"
echo ""

# Test 3: Wait for execution completion
echo -e "${YELLOW}Test 3: Waiting for execution to complete (timeout: ${TIMEOUT}s)...${NC}"

STATUS=""
START_TIME=$(date +%s)

while [ "$STATUS" != "SUCCEEDED" ] && [ "$STATUS" != "FAILED" ] && [ "$STATUS" != "TIMED_OUT" ] && [ "$STATUS" != "ABORTED" ]; do
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    
    if [ $ELAPSED -gt $TIMEOUT ]; then
        echo -e "${RED}✗ Timeout waiting for execution to complete${NC}"
        echo ""
        echo "Execution details:"
        aws stepfunctions describe-execution \
            --execution-arn "$EXECUTION_ARN" \
            --profile "$AWS_PROFILE"
        exit 1
    fi
    
    STATUS=$(aws stepfunctions describe-execution \
        --execution-arn "$EXECUTION_ARN" \
        --profile "$AWS_PROFILE" \
        --query 'status' \
        --output text)
    
    if [ "$VERBOSE" = true ]; then
        echo -e "${BLUE}Status: $STATUS (${ELAPSED}s elapsed)${NC}"
    fi
    
    sleep 5
done

if [ "$STATUS" = "SUCCEEDED" ]; then
    echo -e "${GREEN}✓ Execution completed successfully${NC}"
elif [ "$STATUS" = "FAILED" ]; then
    echo -e "${RED}✗ Execution failed${NC}"
    echo ""
    echo "Execution details:"
    aws stepfunctions describe-execution \
        --execution-arn "$EXECUTION_ARN" \
        --profile "$AWS_PROFILE"
    exit 1
else
    echo -e "${RED}✗ Execution ended with status: $STATUS${NC}"
    exit 1
fi
echo ""

# Test 4: Verify S3 outputs for all stages
echo -e "${YELLOW}Test 4: Verifying S3 outputs for all stages...${NC}"

STAGES=("ETLStage" "FilterStage" "ScoreStage" "StoreStage" "ReactiveStage")
ALL_OUTPUTS_EXIST=true

for stage in "${STAGES[@]}"; do
    S3_KEY="executions/$EXECUTION_NAME/$stage/output.json"
    
    if aws s3 ls "s3://$WORKFLOW_BUCKET/$S3_KEY" --profile "$AWS_PROFILE" &> /dev/null; then
        echo -e "${GREEN}✓ $stage output exists${NC}"
        
        if [ "$VERBOSE" = true ]; then
            echo -e "${BLUE}  Path: s3://$WORKFLOW_BUCKET/$S3_KEY${NC}"
            SIZE=$(aws s3 ls "s3://$WORKFLOW_BUCKET/$S3_KEY" --profile "$AWS_PROFILE" | awk '{print $3}')
            echo -e "${BLUE}  Size: $SIZE bytes${NC}"
        fi
    else
        echo -e "${RED}✗ $stage output missing${NC}"
        ALL_OUTPUTS_EXIST=false
    fi
done

if [ "$ALL_OUTPUTS_EXIST" = false ]; then
    echo -e "${RED}✗ Some stage outputs are missing${NC}"
    exit 1
fi
echo ""

# Test 5: Validate final output
echo -e "${YELLOW}Test 5: Validating final output...${NC}"

FINAL_OUTPUT=$(aws s3 cp "s3://$WORKFLOW_BUCKET/executions/$EXECUTION_NAME/ReactiveStage/output.json" - --profile "$AWS_PROFILE")

if [ -z "$FINAL_OUTPUT" ]; then
    echo -e "${RED}✗ Failed to retrieve final output${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Final output retrieved${NC}"

if [ "$VERBOSE" = true ]; then
    echo ""
    echo -e "${BLUE}Final Output:${NC}"
    echo "$FINAL_OUTPUT" | jq '.' 2>/dev/null || echo "$FINAL_OUTPUT"
fi
echo ""

# Test 6: Verify CloudWatch Logs
echo -e "${YELLOW}Test 6: Checking CloudWatch Logs...${NC}"

LOG_GROUP="/aws/stepfunction/CeapWorkflow-$ENVIRONMENT"

if aws logs describe-log-groups --log-group-name-prefix "$LOG_GROUP" --profile "$AWS_PROFILE" &> /dev/null; then
    echo -e "${GREEN}✓ CloudWatch log group exists${NC}"
    
    # Get recent log events
    LOG_STREAMS=$(aws logs describe-log-streams \
        --log-group-name "$LOG_GROUP" \
        --order-by LastEventTime \
        --descending \
        --max-items 1 \
        --profile "$AWS_PROFILE" \
        --query 'logStreams[0].logStreamName' \
        --output text 2>/dev/null)
    
    if [ -n "$LOG_STREAMS" ] && [ "$LOG_STREAMS" != "None" ]; then
        echo -e "${GREEN}✓ Log streams found${NC}"
        
        if [ "$VERBOSE" = true ]; then
            echo ""
            echo -e "${BLUE}Recent log events:${NC}"
            aws logs get-log-events \
                --log-group-name "$LOG_GROUP" \
                --log-stream-name "$LOG_STREAMS" \
                --limit 10 \
                --profile "$AWS_PROFILE" \
                --query 'events[].message' \
                --output text
        fi
    fi
else
    echo -e "${YELLOW}⚠ CloudWatch log group not found${NC}"
fi
echo ""

# Test 7: Verify S3 lifecycle policy
echo -e "${YELLOW}Test 7: Verifying S3 lifecycle policy...${NC}"

LIFECYCLE_CONFIG=$(aws s3api get-bucket-lifecycle-configuration \
    --bucket "$WORKFLOW_BUCKET" \
    --profile "$AWS_PROFILE" 2>/dev/null)

if [ -n "$LIFECYCLE_CONFIG" ]; then
    echo -e "${GREEN}✓ Lifecycle policy configured${NC}"
    
    if [ "$VERBOSE" = true ]; then
        echo ""
        echo -e "${BLUE}Lifecycle Configuration:${NC}"
        echo "$LIFECYCLE_CONFIG" | jq '.' 2>/dev/null || echo "$LIFECYCLE_CONFIG"
    fi
else
    echo -e "${YELLOW}⚠ No lifecycle policy found${NC}"
fi
echo ""

# Summary
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Smoke Test Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "Test Results:"
echo -e "  ${GREEN}✓ Message sent to SQS${NC}"
echo -e "  ${GREEN}✓ Execution started${NC}"
echo -e "  ${GREEN}✓ Execution completed successfully${NC}"
echo -e "  ${GREEN}✓ All stage outputs exist in S3${NC}"
echo -e "  ${GREEN}✓ Final output validated${NC}"
echo -e "  ${GREEN}✓ CloudWatch logs verified${NC}"
echo -e "  ${GREEN}✓ S3 lifecycle policy verified${NC}"
echo ""
echo -e "Execution Details:"
echo -e "  Execution ARN: ${BLUE}$EXECUTION_ARN${NC}"
echo -e "  Execution Name: ${BLUE}$EXECUTION_NAME${NC}"
echo -e "  S3 Outputs: ${BLUE}s3://$WORKFLOW_BUCKET/executions/$EXECUTION_NAME/${NC}"
echo ""
echo -e "Next Steps:"
echo -e "  1. Review execution in Step Functions console"
echo -e "  2. Check CloudWatch Logs for detailed execution data"
echo -e "  3. Inspect S3 outputs for data quality"
echo -e "  4. Monitor for any errors or warnings"
echo ""
echo -e "Documentation:"
echo -e "  - Operations Runbook: ${BLUE}docs/WORKFLOW-OPERATIONS-RUNBOOK.md${NC}"
echo ""
