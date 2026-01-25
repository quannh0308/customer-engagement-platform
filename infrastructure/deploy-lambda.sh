#!/bin/bash

# Deploy Lambda Functions CloudFormation Stack
# This script deploys the Lambda function configurations to AWS

set -e

# Configuration
STACK_NAME="ceap-platform-lambda-functions"
TEMPLATE_FILE="lambda-functions.yaml"
ENVIRONMENT="${ENVIRONMENT:-dev}"
PROJECT_NAME="${PROJECT_NAME:-ceap-platform}"
AWS_REGION="${AWS_REGION:-us-east-1}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Lambda Functions Deployment Script${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Stack Name: $STACK_NAME"
echo "Environment: $ENVIRONMENT"
echo "Project Name: $PROJECT_NAME"
echo "Region: $AWS_REGION"
echo ""

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo -e "${RED}Error: AWS CLI is not installed${NC}"
    exit 1
fi

# Check if template file exists
if [ ! -f "$TEMPLATE_FILE" ]; then
    echo -e "${RED}Error: Template file $TEMPLATE_FILE not found${NC}"
    exit 1
fi

# Validate the CloudFormation template
echo -e "${YELLOW}Validating CloudFormation template...${NC}"
aws cloudformation validate-template \
    --template-body file://$TEMPLATE_FILE \
    --region $AWS_REGION > /dev/null

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Template validation successful${NC}"
else
    echo -e "${RED}✗ Template validation failed${NC}"
    exit 1
fi

# Check if stack exists
STACK_EXISTS=$(aws cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --region $AWS_REGION 2>&1 || true)

if echo "$STACK_EXISTS" | grep -q "does not exist"; then
    echo -e "${YELLOW}Stack does not exist. Creating new stack...${NC}"
    OPERATION="create-stack"
    WAIT_CONDITION="stack-create-complete"
else
    echo -e "${YELLOW}Stack exists. Updating stack...${NC}"
    OPERATION="update-stack"
    WAIT_CONDITION="stack-update-complete"
fi

# Deploy the stack
echo -e "${YELLOW}Deploying Lambda functions stack...${NC}"

if [ "$OPERATION" = "create-stack" ]; then
    aws cloudformation create-stack \
        --stack-name $STACK_NAME \
        --template-body file://$TEMPLATE_FILE \
        --parameters \
            ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
            ParameterKey=ProjectName,ParameterValue=$PROJECT_NAME \
        --capabilities CAPABILITY_NAMED_IAM \
        --region $AWS_REGION \
        --tags \
            Key=Environment,Value=$ENVIRONMENT \
            Key=Project,Value=$PROJECT_NAME \
            Key=ManagedBy,Value=CloudFormation
else
    # Try to update, but handle "no updates" case
    UPDATE_OUTPUT=$(aws cloudformation update-stack \
        --stack-name $STACK_NAME \
        --template-body file://$TEMPLATE_FILE \
        --parameters \
            ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
            ParameterKey=ProjectName,ParameterValue=$PROJECT_NAME \
        --capabilities CAPABILITY_NAMED_IAM \
        --region $AWS_REGION \
        --tags \
            Key=Environment,Value=$ENVIRONMENT \
            Key=Project,Value=$PROJECT_NAME \
            Key=ManagedBy,Value=CloudFormation 2>&1 || true)
    
    if echo "$UPDATE_OUTPUT" | grep -q "No updates are to be performed"; then
        echo -e "${GREEN}✓ No updates needed - stack is already up to date${NC}"
        exit 0
    elif echo "$UPDATE_OUTPUT" | grep -q "ValidationError"; then
        echo -e "${RED}✗ Update failed: $UPDATE_OUTPUT${NC}"
        exit 1
    fi
fi

# Wait for stack operation to complete
echo -e "${YELLOW}Waiting for stack operation to complete...${NC}"
aws cloudformation wait $WAIT_CONDITION \
    --stack-name $STACK_NAME \
    --region $AWS_REGION

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Stack operation completed successfully${NC}"
else
    echo -e "${RED}✗ Stack operation failed${NC}"
    
    # Get stack events to show what went wrong
    echo -e "${YELLOW}Recent stack events:${NC}"
    aws cloudformation describe-stack-events \
        --stack-name $STACK_NAME \
        --region $AWS_REGION \
        --max-items 10 \
        --query 'StackEvents[*].[Timestamp,ResourceStatus,ResourceType,LogicalResourceId,ResourceStatusReason]' \
        --output table
    
    exit 1
fi

# Display stack outputs
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Stack Outputs${NC}"
echo -e "${GREEN}========================================${NC}"
aws cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --region $AWS_REGION \
    --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' \
    --output table

echo ""
echo -e "${GREEN}✓ Lambda functions deployment completed successfully!${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Build and package Lambda function JARs"
echo "2. Upload JARs to S3 bucket: ${PROJECT_NAME}-lambda-artifacts-${ENVIRONMENT}"
echo "3. Update Lambda function code using AWS CLI or console"
echo ""
