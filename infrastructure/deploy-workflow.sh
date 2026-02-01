#!/bin/bash

###############################################################################
# Workflow Orchestration Deployment Script
#
# This script deploys the Step Functions workflow orchestration infrastructure
# with S3-based intermediate storage.
#
# Usage:
#   ./infrastructure/deploy-workflow.sh [options]
#
# Options:
#   -e, --environment ENV    Environment (dev, staging, prod) [default: dev]
#   -r, --region REGION      AWS region [default: us-east-1]
#   -p, --profile PROFILE    AWS CLI profile [default: default]
#   -t, --type TYPE          Workflow type (express, standard) [default: express]
#   -d, --diff               Show diff before deploying
#   -v, --validate           Validate only, don't deploy
#   -h, --help               Show this help message
#
# Prerequisites:
#   - AWS CLI configured
#   - CDK CLI installed (npm install -g aws-cdk)
#   - Java 17+ and Gradle (for building Lambdas)
#   - Existing CEAP infrastructure deployed
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
WORKFLOW_TYPE="express"
SHOW_DIFF=false
VALIDATE_ONLY=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment) ENVIRONMENT="$2"; shift 2 ;;
        -r|--region) AWS_REGION="$2"; shift 2 ;;
        -p|--profile) AWS_PROFILE="$2"; shift 2 ;;
        -t|--type) WORKFLOW_TYPE="$2"; shift 2 ;;
        -d|--diff) SHOW_DIFF=true; shift ;;
        -v|--validate) VALIDATE_ONLY=true; shift ;;
        -h|--help) grep "^#" "$0" | grep -v "#!/bin/bash" | sed 's/^# //'; exit 0 ;;
        *) echo -e "${RED}Unknown option: $1${NC}"; exit 1 ;;
    esac
done

# Validate workflow type
if [[ ! "$WORKFLOW_TYPE" =~ ^(express|standard)$ ]]; then
    echo -e "${RED}Invalid workflow type: $WORKFLOW_TYPE${NC}"
    echo "Must be 'express' or 'standard'"
    exit 1
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Workflow Orchestration Deployment${NC}"
echo -e "${BLUE}Environment: $ENVIRONMENT${NC}"
echo -e "${BLUE}Region: $AWS_REGION${NC}"
echo -e "${BLUE}Workflow Type: $WORKFLOW_TYPE${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"

if ! command -v aws &> /dev/null; then
    echo -e "${RED}AWS CLI is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ AWS CLI $(aws --version | awk '{print $1}')${NC}"

if ! command -v cdk &> /dev/null; then
    echo -e "${RED}CDK CLI is not installed${NC}"
    echo "Install: npm install -g aws-cdk"
    exit 1
fi
echo -e "${GREEN}✓ CDK $(cdk --version)${NC}"

if ! command -v java &> /dev/null; then
    echo -e "${RED}Java is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Java $(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}')${NC}"

# Check AWS credentials
echo -e "${YELLOW}Validating AWS credentials...${NC}"
AWS_ACCOUNT=$(aws sts get-caller-identity --profile "$AWS_PROFILE" --query Account --output text 2>/dev/null)
if [ $? -ne 0 ]; then
    echo -e "${RED}AWS credentials not configured or expired${NC}"
    exit 1
fi
echo -e "${GREEN}✓ AWS Account: $AWS_ACCOUNT${NC}"
echo ""

# Check existing infrastructure
echo -e "${YELLOW}Checking existing infrastructure...${NC}"

# Check if database stack exists
if ! aws cloudformation describe-stacks --stack-name "CeapDatabase-$ENVIRONMENT" --profile "$AWS_PROFILE" &> /dev/null; then
    echo -e "${RED}CeapDatabase-$ENVIRONMENT stack not found${NC}"
    echo "Please deploy the base CEAP infrastructure first"
    exit 1
fi
echo -e "${GREEN}✓ CeapDatabase-$ENVIRONMENT exists${NC}"

# Check if Lambda functions exist
LAMBDA_FUNCTIONS=(
    "CeapServingAPI-$ENVIRONMENT-ETLLambdaFunction"
    "CeapServingAPI-$ENVIRONMENT-FilterLambdaFunction"
    "CeapServingAPI-$ENVIRONMENT-ScoreLambdaFunction"
    "CeapServingAPI-$ENVIRONMENT-StoreLambdaFunction"
    "CeapServingAPI-$ENVIRONMENT-ReactiveLambdaFunction"
)

for lambda in "${LAMBDA_FUNCTIONS[@]}"; do
    if aws lambda get-function --function-name "$lambda" --profile "$AWS_PROFILE" &> /dev/null; then
        echo -e "${GREEN}✓ $lambda exists${NC}"
    else
        echo -e "${YELLOW}⚠ $lambda not found (will be created)${NC}"
    fi
done
echo ""

# Build Lambda JARs
if [ "$VALIDATE_ONLY" = false ]; then
    echo -e "${YELLOW}Building Lambda JARs...${NC}"
    cd "$(dirname "$0")/.."
    ./gradlew shadowJar
    if [ $? -ne 0 ]; then
        echo -e "${RED}Build failed${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ Lambda JARs built${NC}"
    echo ""
fi

# Navigate to infrastructure directory
cd "$(dirname "$0")"

# Set CDK environment variables
export CDK_DEFAULT_ACCOUNT=$AWS_ACCOUNT
export CDK_DEFAULT_REGION=$AWS_REGION
export AWS_PROFILE=$AWS_PROFILE

# Synthesize CDK templates
echo -e "${YELLOW}Synthesizing CDK templates...${NC}"
cdk synth \
    --context environment="$ENVIRONMENT" \
    --context workflowType="$WORKFLOW_TYPE" \
    --profile "$AWS_PROFILE"

if [ $? -ne 0 ]; then
    echo -e "${RED}CDK synthesis failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ CDK synthesis successful${NC}"
echo ""

# Validate templates
echo -e "${YELLOW}Validating CloudFormation templates...${NC}"
./validate-template.sh
if [ $? -ne 0 ]; then
    echo -e "${RED}Template validation failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Templates validated${NC}"
echo ""

if [ "$VALIDATE_ONLY" = true ]; then
    echo -e "${GREEN}Validation complete. Exiting without deployment.${NC}"
    exit 0
fi

# Show diff if requested
if [ "$SHOW_DIFF" = true ]; then
    echo -e "${YELLOW}Showing deployment diff...${NC}"
    cdk diff \
        --context environment="$ENVIRONMENT" \
        --context workflowType="$WORKFLOW_TYPE" \
        --profile "$AWS_PROFILE"
    echo ""
    read -p "Continue with deployment? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Deployment cancelled"
        exit 0
    fi
fi

# Deploy workflow infrastructure
echo -e "${YELLOW}Deploying workflow infrastructure...${NC}"
echo ""

# Phase 1: Deploy S3 workflow bucket
echo -e "${BLUE}Phase 1: Deploying S3 Workflow Bucket${NC}"
cdk deploy "CeapWorkflowStorage-$ENVIRONMENT" \
    --context environment="$ENVIRONMENT" \
    --context workflowType="$WORKFLOW_TYPE" \
    --require-approval never \
    --profile "$AWS_PROFILE"

if [ $? -ne 0 ]; then
    echo -e "${RED}S3 bucket deployment failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ S3 workflow bucket deployed${NC}"
echo ""

# Phase 2: Deploy Lambda functions with base handler
echo -e "${BLUE}Phase 2: Deploying Lambda Functions${NC}"
cdk deploy "CeapWorkflowLambdas-$ENVIRONMENT" \
    --context environment="$ENVIRONMENT" \
    --context workflowType="$WORKFLOW_TYPE" \
    --require-approval never \
    --profile "$AWS_PROFILE"

if [ $? -ne 0 ]; then
    echo -e "${RED}Lambda deployment failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Lambda functions deployed${NC}"
echo ""

# Phase 3: Deploy Step Functions workflow
echo -e "${BLUE}Phase 3: Deploying Step Functions Workflow${NC}"
cdk deploy "CeapWorkflowOrchestration-$ENVIRONMENT" \
    --context environment="$ENVIRONMENT" \
    --context workflowType="$WORKFLOW_TYPE" \
    --require-approval never \
    --profile "$AWS_PROFILE"

if [ $? -ne 0 ]; then
    echo -e "${RED}Step Functions deployment failed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Step Functions workflow deployed${NC}"
echo ""

# Get stack outputs
echo -e "${YELLOW}Retrieving stack outputs...${NC}"
WORKFLOW_BUCKET=$(aws cloudformation describe-stacks \
    --stack-name "CeapWorkflowStorage-$ENVIRONMENT" \
    --profile "$AWS_PROFILE" \
    --query 'Stacks[0].Outputs[?OutputKey==`WorkflowBucketName`].OutputValue' \
    --output text)

STATE_MACHINE_ARN=$(aws cloudformation describe-stacks \
    --stack-name "CeapWorkflowOrchestration-$ENVIRONMENT" \
    --profile "$AWS_PROFILE" \
    --query 'Stacks[0].Outputs[?OutputKey==`StateMachineArn`].OutputValue' \
    --output text)

QUEUE_URL=$(aws cloudformation describe-stacks \
    --stack-name "CeapWorkflowOrchestration-$ENVIRONMENT" \
    --profile "$AWS_PROFILE" \
    --query 'Stacks[0].Outputs[?OutputKey==`QueueUrl`].OutputValue' \
    --output text)

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Deployment Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "Environment: ${GREEN}$ENVIRONMENT${NC}"
echo -e "Region: ${GREEN}$AWS_REGION${NC}"
echo -e "Workflow Type: ${GREEN}$WORKFLOW_TYPE${NC}"
echo ""
echo -e "Deployed Resources:"
echo -e "  S3 Bucket: ${BLUE}$WORKFLOW_BUCKET${NC}"
echo -e "  State Machine: ${BLUE}$STATE_MACHINE_ARN${NC}"
echo -e "  Queue URL: ${BLUE}$QUEUE_URL${NC}"
echo ""
echo -e "Next steps:"
echo -e "  1. Run smoke tests: ${YELLOW}./smoke-test-workflow.sh -e $ENVIRONMENT${NC}"
echo -e "  2. Monitor CloudWatch Logs: ${YELLOW}/aws/stepfunction/CeapWorkflow-$ENVIRONMENT${NC}"
echo -e "  3. Check S3 bucket: ${YELLOW}aws s3 ls s3://$WORKFLOW_BUCKET/executions/${NC}"
echo ""
echo -e "Documentation:"
echo -e "  - Workflow Guide: ${BLUE}docs/WORKFLOW-ORCHESTRATION-GUIDE.md${NC}"
echo -e "  - Operations Runbook: ${BLUE}docs/WORKFLOW-OPERATIONS-RUNBOOK.md${NC}"
echo ""
