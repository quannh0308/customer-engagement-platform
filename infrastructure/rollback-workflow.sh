#!/bin/bash

###############################################################################
# Workflow Orchestration Rollback Script
#
# This script rolls back the Step Functions workflow orchestration infrastructure.
# It safely removes workflow resources while preserving data and allowing recovery.
#
# Usage:
#   ./infrastructure/rollback-workflow.sh [options]
#
# Options:
#   -e, --environment ENV    Environment (dev, staging, prod) [default: dev]
#   -r, --region REGION      AWS region [default: us-east-1]
#   -p, --profile PROFILE    AWS CLI profile [default: default]
#   -f, --force              Skip confirmation prompts
#   -k, --keep-data          Keep S3 execution data (don't delete bucket)
#   -h, --help               Show this help message
#
# Safety Features:
#   - Drains SQS queue before deletion
#   - Archives S3 execution data before deletion
#   - Validates no active executions before rollback
#   - Provides detailed rollback status
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
FORCE=false
KEEP_DATA=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment) ENVIRONMENT="$2"; shift 2 ;;
        -r|--region) AWS_REGION="$2"; shift 2 ;;
        -p|--profile) AWS_PROFILE="$2"; shift 2 ;;
        -f|--force) FORCE=true; shift ;;
        -k|--keep-data) KEEP_DATA=true; shift ;;
        -h|--help) grep "^#" "$0" | grep -v "#!/bin/bash" | sed 's/^# //'; exit 0 ;;
        *) echo -e "${RED}Unknown option: $1${NC}"; exit 1 ;;
    esac
done

echo -e "${RED}========================================${NC}"
echo -e "${RED}Workflow Orchestration Rollback${NC}"
echo -e "${RED}Environment: $ENVIRONMENT${NC}"
echo -e "${RED}Region: $AWS_REGION${NC}"
echo -e "${RED}========================================${NC}"
echo ""

# Check AWS credentials
echo -e "${YELLOW}Validating AWS credentials...${NC}"
AWS_ACCOUNT=$(aws sts get-caller-identity --profile "$AWS_PROFILE" --query Account --output text 2>/dev/null)
if [ $? -ne 0 ]; then
    echo -e "${RED}AWS credentials not configured or expired${NC}"
    exit 1
fi
echo -e "${GREEN}✓ AWS Account: $AWS_ACCOUNT${NC}"
echo ""

# Check if stacks exist
echo -e "${YELLOW}Checking existing stacks...${NC}"
STACKS_TO_DELETE=(
    "CeapWorkflowOrchestration-$ENVIRONMENT"
    "CeapWorkflowLambdas-$ENVIRONMENT"
    "CeapWorkflowStorage-$ENVIRONMENT"
)

STACKS_EXIST=false
for stack in "${STACKS_TO_DELETE[@]}"; do
    if aws cloudformation describe-stacks --stack-name "$stack" --profile "$AWS_PROFILE" &> /dev/null; then
        echo -e "${GREEN}✓ Found: $stack${NC}"
        STACKS_EXIST=true
    else
        echo -e "${YELLOW}⚠ Not found: $stack${NC}"
    fi
done

if [ "$STACKS_EXIST" = false ]; then
    echo ""
    echo -e "${YELLOW}No workflow stacks found to rollback${NC}"
    exit 0
fi
echo ""

# Get stack resources
echo -e "${YELLOW}Retrieving stack resources...${NC}"

STATE_MACHINE_ARN=$(aws cloudformation describe-stacks \
    --stack-name "CeapWorkflowOrchestration-$ENVIRONMENT" \
    --profile "$AWS_PROFILE" \
    --query 'Stacks[0].Outputs[?OutputKey==`StateMachineArn`].OutputValue' \
    --output text 2>/dev/null || echo "")

WORKFLOW_BUCKET=$(aws cloudformation describe-stacks \
    --stack-name "CeapWorkflowStorage-$ENVIRONMENT" \
    --profile "$AWS_PROFILE" \
    --query 'Stacks[0].Outputs[?OutputKey==`WorkflowBucketName`].OutputValue' \
    --output text 2>/dev/null || echo "")

QUEUE_URL=$(aws cloudformation describe-stacks \
    --stack-name "CeapWorkflowOrchestration-$ENVIRONMENT" \
    --profile "$AWS_PROFILE" \
    --query 'Stacks[0].Outputs[?OutputKey==`QueueUrl`].OutputValue' \
    --output text 2>/dev/null || echo "")

echo -e "State Machine: ${BLUE}$STATE_MACHINE_ARN${NC}"
echo -e "S3 Bucket: ${BLUE}$WORKFLOW_BUCKET${NC}"
echo -e "Queue URL: ${BLUE}$QUEUE_URL${NC}"
echo ""

# Confirmation prompt
if [ "$FORCE" = false ]; then
    echo -e "${RED}⚠️  WARNING: This will delete the workflow orchestration infrastructure!${NC}"
    echo ""
    echo "Stacks to be deleted:"
    for stack in "${STACKS_TO_DELETE[@]}"; do
        echo "  - $stack"
    done
    echo ""
    if [ "$KEEP_DATA" = false ]; then
        echo -e "${RED}⚠️  S3 execution data will be DELETED${NC}"
    else
        echo -e "${YELLOW}ℹ️  S3 execution data will be PRESERVED${NC}"
    fi
    echo ""
    read -p "Are you sure you want to proceed? (yes/no): " CONFIRM
    
    if [ "$CONFIRM" != "yes" ]; then
        echo "Rollback cancelled"
        exit 0
    fi
fi

echo ""
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}Pre-Rollback Validation${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

# Check for active executions
if [ -n "$STATE_MACHINE_ARN" ]; then
    echo -e "${YELLOW}Checking for active executions...${NC}"
    RUNNING_EXECUTIONS=$(aws stepfunctions list-executions \
        --state-machine-arn "$STATE_MACHINE_ARN" \
        --status-filter RUNNING \
        --profile "$AWS_PROFILE" \
        --query 'length(executions)' \
        --output text)
    
    if [ "$RUNNING_EXECUTIONS" -gt 0 ]; then
        echo -e "${RED}⚠️  $RUNNING_EXECUTIONS active executions found${NC}"
        echo ""
        if [ "$FORCE" = false ]; then
            read -p "Stop active executions and continue? (yes/no): " STOP_EXECUTIONS
            if [ "$STOP_EXECUTIONS" != "yes" ]; then
                echo "Rollback cancelled"
                exit 0
            fi
            
            # Stop all running executions
            echo -e "${YELLOW}Stopping active executions...${NC}"
            aws stepfunctions list-executions \
                --state-machine-arn "$STATE_MACHINE_ARN" \
                --status-filter RUNNING \
                --profile "$AWS_PROFILE" \
                --query 'executions[].executionArn' \
                --output text | xargs -I {} aws stepfunctions stop-execution \
                --execution-arn {} \
                --profile "$AWS_PROFILE"
            
            echo -e "${GREEN}✓ Active executions stopped${NC}"
        fi
    else
        echo -e "${GREEN}✓ No active executions${NC}"
    fi
fi
echo ""

# Drain SQS queue
if [ -n "$QUEUE_URL" ]; then
    echo -e "${YELLOW}Checking SQS queue...${NC}"
    QUEUE_DEPTH=$(aws sqs get-queue-attributes \
        --queue-url "$QUEUE_URL" \
        --attribute-names ApproximateNumberOfMessages \
        --profile "$AWS_PROFILE" \
        --query 'Attributes.ApproximateNumberOfMessages' \
        --output text)
    
    if [ "$QUEUE_DEPTH" -gt 0 ]; then
        echo -e "${YELLOW}⚠️  $QUEUE_DEPTH messages in queue${NC}"
        echo ""
        if [ "$FORCE" = false ]; then
            read -p "Purge queue and continue? (yes/no): " PURGE_QUEUE
            if [ "$PURGE_QUEUE" != "yes" ]; then
                echo "Rollback cancelled"
                exit 0
            fi
            
            # Purge queue
            echo -e "${YELLOW}Purging queue...${NC}"
            aws sqs purge-queue --queue-url "$QUEUE_URL" --profile "$AWS_PROFILE"
            echo -e "${GREEN}✓ Queue purged${NC}"
        fi
    else
        echo -e "${GREEN}✓ Queue is empty${NC}"
    fi
fi
echo ""

# Archive S3 data if requested
if [ "$KEEP_DATA" = false ] && [ -n "$WORKFLOW_BUCKET" ]; then
    echo -e "${YELLOW}Archiving S3 execution data...${NC}"
    ARCHIVE_BUCKET="ceap-workflow-archive-$ENVIRONMENT-$AWS_ACCOUNT"
    
    # Check if archive bucket exists
    if ! aws s3 ls "s3://$ARCHIVE_BUCKET" --profile "$AWS_PROFILE" &> /dev/null; then
        echo -e "${YELLOW}Creating archive bucket: $ARCHIVE_BUCKET${NC}"
        aws s3 mb "s3://$ARCHIVE_BUCKET" --profile "$AWS_PROFILE" --region "$AWS_REGION"
    fi
    
    # Copy execution data to archive
    TIMESTAMP=$(date +%Y%m%d-%H%M%S)
    echo -e "${YELLOW}Copying to s3://$ARCHIVE_BUCKET/rollback-$TIMESTAMP/${NC}"
    aws s3 sync "s3://$WORKFLOW_BUCKET/executions/" \
        "s3://$ARCHIVE_BUCKET/rollback-$TIMESTAMP/executions/" \
        --profile "$AWS_PROFILE"
    
    echo -e "${GREEN}✓ Data archived to s3://$ARCHIVE_BUCKET/rollback-$TIMESTAMP/${NC}"
fi
echo ""

# Delete stacks in reverse order
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}Deleting Stacks${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

# Phase 1: Delete Step Functions workflow
echo -e "${BLUE}Phase 1: Deleting Step Functions Workflow${NC}"
if aws cloudformation describe-stacks --stack-name "CeapWorkflowOrchestration-$ENVIRONMENT" --profile "$AWS_PROFILE" &> /dev/null; then
    aws cloudformation delete-stack \
        --stack-name "CeapWorkflowOrchestration-$ENVIRONMENT" \
        --profile "$AWS_PROFILE"
    
    echo -e "${YELLOW}Waiting for stack deletion...${NC}"
    aws cloudformation wait stack-delete-complete \
        --stack-name "CeapWorkflowOrchestration-$ENVIRONMENT" \
        --profile "$AWS_PROFILE"
    
    echo -e "${GREEN}✓ CeapWorkflowOrchestration-$ENVIRONMENT deleted${NC}"
else
    echo -e "${YELLOW}⚠ Stack does not exist${NC}"
fi
echo ""

# Phase 2: Delete Lambda functions
echo -e "${BLUE}Phase 2: Deleting Lambda Functions${NC}"
if aws cloudformation describe-stacks --stack-name "CeapWorkflowLambdas-$ENVIRONMENT" --profile "$AWS_PROFILE" &> /dev/null; then
    aws cloudformation delete-stack \
        --stack-name "CeapWorkflowLambdas-$ENVIRONMENT" \
        --profile "$AWS_PROFILE"
    
    echo -e "${YELLOW}Waiting for stack deletion...${NC}"
    aws cloudformation wait stack-delete-complete \
        --stack-name "CeapWorkflowLambdas-$ENVIRONMENT" \
        --profile "$AWS_PROFILE"
    
    echo -e "${GREEN}✓ CeapWorkflowLambdas-$ENVIRONMENT deleted${NC}"
else
    echo -e "${YELLOW}⚠ Stack does not exist${NC}"
fi
echo ""

# Phase 3: Delete S3 bucket (if not keeping data)
if [ "$KEEP_DATA" = false ]; then
    echo -e "${BLUE}Phase 3: Deleting S3 Workflow Bucket${NC}"
    if aws cloudformation describe-stacks --stack-name "CeapWorkflowStorage-$ENVIRONMENT" --profile "$AWS_PROFILE" &> /dev/null; then
        # Empty bucket first
        if [ -n "$WORKFLOW_BUCKET" ]; then
            echo -e "${YELLOW}Emptying S3 bucket...${NC}"
            aws s3 rm "s3://$WORKFLOW_BUCKET" --recursive --profile "$AWS_PROFILE"
        fi
        
        aws cloudformation delete-stack \
            --stack-name "CeapWorkflowStorage-$ENVIRONMENT" \
            --profile "$AWS_PROFILE"
        
        echo -e "${YELLOW}Waiting for stack deletion...${NC}"
        aws cloudformation wait stack-delete-complete \
            --stack-name "CeapWorkflowStorage-$ENVIRONMENT" \
            --profile "$AWS_PROFILE"
        
        echo -e "${GREEN}✓ CeapWorkflowStorage-$ENVIRONMENT deleted${NC}"
    else
        echo -e "${YELLOW}⚠ Stack does not exist${NC}"
    fi
else
    echo -e "${YELLOW}ℹ️  Skipping S3 bucket deletion (--keep-data flag)${NC}"
fi
echo ""

# Verify rollback
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}Rollback Verification${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

ROLLBACK_SUCCESS=true
for stack in "${STACKS_TO_DELETE[@]}"; do
    if [ "$KEEP_DATA" = true ] && [ "$stack" = "CeapWorkflowStorage-$ENVIRONMENT" ]; then
        echo -e "${YELLOW}⚠ $stack preserved (--keep-data)${NC}"
        continue
    fi
    
    if aws cloudformation describe-stacks --stack-name "$stack" --profile "$AWS_PROFILE" &> /dev/null; then
        echo -e "${RED}❌ $stack still exists${NC}"
        ROLLBACK_SUCCESS=false
    else
        echo -e "${GREEN}✓ $stack successfully deleted${NC}"
    fi
done

echo ""
if [ "$ROLLBACK_SUCCESS" = true ]; then
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}Rollback Complete!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo "Deleted stacks:"
    for stack in "${STACKS_TO_DELETE[@]}"; do
        if [ "$KEEP_DATA" = true ] && [ "$stack" = "CeapWorkflowStorage-$ENVIRONMENT" ]; then
            echo -e "  ${YELLOW}⚠ $stack (preserved)${NC}"
        else
            echo -e "  ${GREEN}✓ $stack${NC}"
        fi
    done
    echo ""
    if [ "$KEEP_DATA" = false ] && [ -n "$WORKFLOW_BUCKET" ]; then
        echo -e "Archived data: ${BLUE}s3://$ARCHIVE_BUCKET/rollback-$TIMESTAMP/${NC}"
        echo ""
    fi
    echo "Next steps:"
    echo "  1. Verify resources deleted in AWS Console"
    echo "  2. Redeploy if needed: ./deploy-workflow.sh -e $ENVIRONMENT"
    echo "  3. Check for any orphaned resources"
    echo ""
    exit 0
else
    echo -e "${RED}========================================${NC}"
    echo -e "${RED}Rollback Incomplete${NC}"
    echo -e "${RED}========================================${NC}"
    echo ""
    echo "Some stacks still exist. Please investigate and retry."
    echo ""
    exit 1
fi
