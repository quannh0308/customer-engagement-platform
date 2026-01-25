#!/bin/bash

###############################################################################
# Deployment Script for Customer Engagement & Action Platform (CEAP)
#
# This script deploys the infrastructure and Lambda functions to AWS.
#
# Usage:
#   ./scripts/deploy.sh [options]
#
# Options:
#   -e, --environment ENV    Environment (dev, staging, prod) [default: dev]
#   -r, --region REGION      AWS region [default: us-east-1]
#   -p, --profile PROFILE    AWS CLI profile [default: default]
#   -s, --stack-only         Deploy only CloudFormation stacks (skip Lambda upload)
#   -l, --lambda-only        Deploy only Lambda functions (skip infrastructure)
#   -h, --help               Show this help message
#
# Prerequisites:
#   - AWS CLI configured with appropriate credentials
#   - S3 bucket for Lambda artifacts (created automatically if not exists)
#   - Built Lambda JARs (run ./scripts/build.sh first)
###############################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
ENVIRONMENT="dev"
AWS_REGION="us-east-1"
AWS_PROFILE="default"
PROJECT_NAME="ceap-platform"
STACK_ONLY=false
LAMBDA_ONLY=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        -r|--region)
            AWS_REGION="$2"
            shift 2
            ;;
        -p|--profile)
            AWS_PROFILE="$2"
            shift 2
            ;;
        -s|--stack-only)
            STACK_ONLY=true
            shift
            ;;
        -l|--lambda-only)
            LAMBDA_ONLY=true
            shift
            ;;
        -h|--help)
            grep "^#" "$0" | grep -v "#!/bin/bash" | sed 's/^# //'
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

# Validate environment
if [[ ! "$ENVIRONMENT" =~ ^(dev|staging|prod)$ ]]; then
    echo -e "${RED}Invalid environment: $ENVIRONMENT${NC}"
    echo "Valid environments: dev, staging, prod"
    exit 1
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Deploying CEAP Platform${NC}"
echo -e "${BLUE}Environment: $ENVIRONMENT${NC}"
echo -e "${BLUE}Region: $AWS_REGION${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"

if ! command -v aws &> /dev/null; then
    echo -e "${RED}AWS CLI is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ AWS CLI${NC}"

# Verify AWS credentials
if ! aws sts get-caller-identity --profile "$AWS_PROFILE" &> /dev/null; then
    echo -e "${RED}AWS credentials not configured for profile: $AWS_PROFILE${NC}"
    exit 1
fi

AWS_ACCOUNT_ID=$(aws sts get-caller-identity --profile "$AWS_PROFILE" --query Account --output text)
echo -e "${GREEN}✓ AWS Account: $AWS_ACCOUNT_ID${NC}"

echo ""

# S3 bucket for Lambda artifacts
S3_BUCKET="$PROJECT_NAME-lambda-artifacts-$ENVIRONMENT"
DEPLOY_DIR="target/deploy-$ENVIRONMENT"

# Create S3 bucket if it doesn't exist (unless lambda-only mode)
if [ "$LAMBDA_ONLY" = false ]; then
    echo -e "${YELLOW}Checking S3 bucket for Lambda artifacts...${NC}"
    
    if aws s3 ls "s3://$S3_BUCKET" --profile "$AWS_PROFILE" 2>&1 | grep -q 'NoSuchBucket'; then
        echo -e "${YELLOW}Creating S3 bucket: $S3_BUCKET${NC}"
        aws s3 mb "s3://$S3_BUCKET" --region "$AWS_REGION" --profile "$AWS_PROFILE"
        
        # Enable versioning
        aws s3api put-bucket-versioning \
            --bucket "$S3_BUCKET" \
            --versioning-configuration Status=Enabled \
            --profile "$AWS_PROFILE"
        
        echo -e "${GREEN}✓ S3 bucket created${NC}"
    else
        echo -e "${GREEN}✓ S3 bucket exists${NC}"
    fi
    echo ""
fi

# Upload Lambda JARs to S3 (unless stack-only mode)
if [ "$STACK_ONLY" = false ]; then
    echo -e "${YELLOW}Uploading Lambda JARs to S3...${NC}"
    
    if [ ! -d "$DEPLOY_DIR" ]; then
        echo -e "${RED}Deployment directory not found: $DEPLOY_DIR${NC}"
        echo -e "${YELLOW}Run ./scripts/build.sh first${NC}"
        exit 1
    fi
    
    LAMBDA_JARS=(
        "etl-lambda.jar"
        "filter-lambda.jar"
        "score-lambda.jar"
        "store-lambda.jar"
        "reactive-lambda.jar"
    )
    
    for jar in "${LAMBDA_JARS[@]}"; do
        if [ -f "$DEPLOY_DIR/$jar" ]; then
            aws s3 cp "$DEPLOY_DIR/$jar" "s3://$S3_BUCKET/$jar" \
                --profile "$AWS_PROFILE" \
                --region "$AWS_REGION"
            echo -e "${GREEN}✓ Uploaded $jar${NC}"
        else
            echo -e "${RED}✗ JAR not found: $DEPLOY_DIR/$jar${NC}"
            exit 1
        fi
    done
    
    echo ""
fi

# Deploy CloudFormation stacks (unless lambda-only mode)
if [ "$LAMBDA_ONLY" = false ]; then
    echo -e "${YELLOW}Deploying CloudFormation stacks...${NC}"
    echo ""
    
    # Deploy DynamoDB tables
    echo -e "${YELLOW}1. Deploying DynamoDB tables...${NC}"
    DYNAMODB_STACK_NAME="$PROJECT_NAME-dynamodb-$ENVIRONMENT"
    
    aws cloudformation deploy \
        --template-file infrastructure/dynamodb-tables.yaml \
        --stack-name "$DYNAMODB_STACK_NAME" \
        --parameter-overrides Environment="$ENVIRONMENT" \
        --capabilities CAPABILITY_NAMED_IAM \
        --region "$AWS_REGION" \
        --profile "$AWS_PROFILE"
    
    echo -e "${GREEN}✓ DynamoDB tables deployed${NC}"
    echo ""
    
    # Deploy Lambda functions
    echo -e "${YELLOW}2. Deploying Lambda functions...${NC}"
    LAMBDA_STACK_NAME="$PROJECT_NAME-lambda-$ENVIRONMENT"
    
    aws cloudformation deploy \
        --template-file infrastructure/lambda-functions.yaml \
        --stack-name "$LAMBDA_STACK_NAME" \
        --parameter-overrides \
            Environment="$ENVIRONMENT" \
            ProjectName="$PROJECT_NAME" \
        --capabilities CAPABILITY_NAMED_IAM \
        --region "$AWS_REGION" \
        --profile "$AWS_PROFILE"
    
    echo -e "${GREEN}✓ Lambda functions deployed${NC}"
    echo ""
    
    # Get Step Functions ARNs for EventBridge
    BATCH_INGESTION_ARN=$(aws cloudformation describe-stacks \
        --stack-name "$PROJECT_NAME-stepfunctions-$ENVIRONMENT" \
        --query "Stacks[0].Outputs[?OutputKey=='BatchIngestionStateMachineArn'].OutputValue" \
        --output text \
        --region "$AWS_REGION" \
        --profile "$AWS_PROFILE" 2>/dev/null || echo "")
    
    REACTIVE_CEAP_ARN=$(aws cloudformation describe-stacks \
        --stack-name "$PROJECT_NAME-stepfunctions-$ENVIRONMENT" \
        --query "Stacks[0].Outputs[?OutputKey=='ReactiveCeapStateMachineArn'].OutputValue" \
        --output text \
        --region "$AWS_REGION" \
        --profile "$AWS_PROFILE" 2>/dev/null || echo "")
    
    # Deploy Step Functions
    echo -e "${YELLOW}3. Deploying Step Functions...${NC}"
    STEPFUNCTIONS_STACK_NAME="$PROJECT_NAME-stepfunctions-$ENVIRONMENT"
    
    aws cloudformation deploy \
        --template-file infrastructure/step-functions.yaml \
        --stack-name "$STEPFUNCTIONS_STACK_NAME" \
        --parameter-overrides \
            Environment="$ENVIRONMENT" \
            ProjectName="$PROJECT_NAME" \
        --capabilities CAPABILITY_NAMED_IAM \
        --region "$AWS_REGION" \
        --profile "$AWS_PROFILE"
    
    echo -e "${GREEN}✓ Step Functions deployed${NC}"
    echo ""
    
    # Get updated Step Functions ARNs
    BATCH_INGESTION_ARN=$(aws cloudformation describe-stacks \
        --stack-name "$STEPFUNCTIONS_STACK_NAME" \
        --query "Stacks[0].Outputs[?OutputKey=='BatchIngestionStateMachineArn'].OutputValue" \
        --output text \
        --region "$AWS_REGION" \
        --profile "$AWS_PROFILE")
    
    REACTIVE_CEAP_ARN=$(aws cloudformation describe-stacks \
        --stack-name "$STEPFUNCTIONS_STACK_NAME" \
        --query "Stacks[0].Outputs[?OutputKey=='ReactiveCeapStateMachineArn'].OutputValue" \
        --output text \
        --region "$AWS_REGION" \
        --profile "$AWS_PROFILE")
    
    # Deploy EventBridge rules
    echo -e "${YELLOW}4. Deploying EventBridge rules...${NC}"
    EVENTBRIDGE_STACK_NAME="$PROJECT_NAME-eventbridge-$ENVIRONMENT"
    
    aws cloudformation deploy \
        --template-file infrastructure/eventbridge-rules.yaml \
        --stack-name "$EVENTBRIDGE_STACK_NAME" \
        --parameter-overrides \
            Environment="$ENVIRONMENT" \
            ProjectName="$PROJECT_NAME" \
            BatchIngestionStateMachineArn="$BATCH_INGESTION_ARN" \
            ReactiveCeapStateMachineArn="$REACTIVE_CEAP_ARN" \
        --capabilities CAPABILITY_NAMED_IAM \
        --region "$AWS_REGION" \
        --profile "$AWS_PROFILE"
    
    echo -e "${GREEN}✓ EventBridge rules deployed${NC}"
    echo ""
fi

# Update Lambda function code (if lambda-only or full deployment)
if [ "$STACK_ONLY" = false ]; then
    echo -e "${YELLOW}Updating Lambda function code...${NC}"
    
    LAMBDA_FUNCTIONS=(
        "$PROJECT_NAME-etl-$ENVIRONMENT:etl-lambda.jar"
        "$PROJECT_NAME-filter-$ENVIRONMENT:filter-lambda.jar"
        "$PROJECT_NAME-score-$ENVIRONMENT:score-lambda.jar"
        "$PROJECT_NAME-store-$ENVIRONMENT:store-lambda.jar"
        "$PROJECT_NAME-serve-$ENVIRONMENT:serve-lambda.jar"
    )
    
    for func in "${LAMBDA_FUNCTIONS[@]}"; do
        FUNCTION_NAME="${func%%:*}"
        JAR_FILE="${func##*:}"
        
        aws lambda update-function-code \
            --function-name "$FUNCTION_NAME" \
            --s3-bucket "$S3_BUCKET" \
            --s3-key "$JAR_FILE" \
            --region "$AWS_REGION" \
            --profile "$AWS_PROFILE" \
            > /dev/null
        
        echo -e "${GREEN}✓ Updated $FUNCTION_NAME${NC}"
    done
    
    echo ""
fi

# Display deployment summary
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Deployment Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "Environment:     ${GREEN}$ENVIRONMENT${NC}"
echo -e "Region:          ${GREEN}$AWS_REGION${NC}"
echo -e "Account:         ${GREEN}$AWS_ACCOUNT_ID${NC}"
echo -e "S3 Bucket:       ${GREEN}$S3_BUCKET${NC}"
echo ""

if [ "$LAMBDA_ONLY" = false ]; then
    echo -e "${YELLOW}Deployed Stacks:${NC}"
    echo -e "  - ${GREEN}$DYNAMODB_STACK_NAME${NC}"
    echo -e "  - ${GREEN}$LAMBDA_STACK_NAME${NC}"
    echo -e "  - ${GREEN}$STEPFUNCTIONS_STACK_NAME${NC}"
    echo -e "  - ${GREEN}$EVENTBRIDGE_STACK_NAME${NC}"
    echo ""
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Deployment completed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "Next steps:"
echo -e "  1. Verify deployment in AWS Console"
echo -e "  2. Test Lambda functions"
echo -e "  3. Monitor CloudWatch Logs"
echo ""
