#!/bin/bash

###############################################################################
# CDK Deployment Script for CEAP Platform
#
# This script deploys the infrastructure using AWS CDK (Kotlin).
#
# Usage:
#   ./infrastructure/deploy-cdk.sh [options]
#
# Options:
#   -e, --environment ENV    Environment (dev, staging, prod) [default: dev]
#   -r, --region REGION      AWS region [default: us-east-1]
#   -p, --profile PROFILE    AWS CLI profile [default: default]
#   -s, --stack STACK        Deploy specific stack only
#   -a, --all                Deploy all stacks
#   -b, --bootstrap          Bootstrap CDK (first-time setup)
#   -d, --diff               Show diff before deploying
#   -h, --help               Show this help message
#
# Prerequisites:
#   - AWS CLI configured
#   - Node.js 20+ (for CDK CLI)
#   - Java 17+ (for building Lambdas)
#   - Gradle (for building Lambdas)
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
SPECIFIC_STACK=""
DEPLOY_ALL=false
BOOTSTRAP=false
SHOW_DIFF=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment) ENVIRONMENT="$2"; shift 2 ;;
        -r|--region) AWS_REGION="$2"; shift 2 ;;
        -p|--profile) AWS_PROFILE="$2"; shift 2 ;;
        -s|--stack) SPECIFIC_STACK="$2"; shift 2 ;;
        -a|--all) DEPLOY_ALL=true; shift ;;
        -b|--bootstrap) BOOTSTRAP=true; shift ;;
        -d|--diff) SHOW_DIFF=true; shift ;;
        -h|--help) grep "^#" "$0" | grep -v "#!/bin/bash" | sed 's/^# //'; exit 0 ;;
        *) echo -e "${RED}Unknown option: $1${NC}"; exit 1 ;;
    esac
done

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}AWS CDK Deployment${NC}"
echo -e "${BLUE}Environment: $ENVIRONMENT${NC}"
echo -e "${BLUE}Region: $AWS_REGION${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"

if ! command -v node &> /dev/null; then
    echo -e "${RED}Node.js is not installed${NC}"
    echo "Install: brew install node (macOS) or visit https://nodejs.org"
    exit 1
fi
echo -e "${GREEN}✓ Node.js $(node --version)${NC}"

if ! command -v npm &> /dev/null; then
    echo -e "${RED}npm is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ npm $(npm --version)${NC}"

if ! command -v cdk &> /dev/null; then
    echo -e "${YELLOW}CDK CLI not found. Installing...${NC}"
    npm install -g aws-cdk
fi
echo -e "${GREEN}✓ CDK $(cdk --version)${NC}"

if ! command -v java &> /dev/null; then
    echo -e "${RED}Java is not installed${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Java $(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}')${NC}"

echo ""

# Bootstrap if requested
if [ "$BOOTSTRAP" = true ]; then
    echo -e "${YELLOW}Bootstrapping CDK...${NC}"
    cd infrastructure
    cdk bootstrap aws://$(aws sts get-caller-identity --profile "$AWS_PROFILE" --query Account --output text)/$AWS_REGION \
        --profile "$AWS_PROFILE"
    echo -e "${GREEN}✓ Bootstrap complete${NC}"
    echo ""
fi

# Build Lambda JARs
echo -e "${YELLOW}Building Lambda JARs...${NC}"
cd "$(dirname "$0")/.."
./gradlew shadowJar
echo -e "${GREEN}✓ Lambda JARs built${NC}"
echo ""

# Deploy with CDK
cd infrastructure

export CDK_DEFAULT_ACCOUNT=$(aws sts get-caller-identity --profile "$AWS_PROFILE" --query Account --output text)
export CDK_DEFAULT_REGION=$AWS_REGION
export AWS_PROFILE=$AWS_PROFILE

if [ "$SHOW_DIFF" = true ]; then
    echo -e "${YELLOW}Showing diff...${NC}"
    cdk diff --context environment="$ENVIRONMENT"
    echo ""
    read -p "Continue with deployment? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Deployment cancelled"
        exit 0
    fi
fi

echo -e "${YELLOW}Deploying stacks...${NC}"

if [ -n "$SPECIFIC_STACK" ]; then
    echo -e "${BLUE}Deploying stack: $SPECIFIC_STACK${NC}"
    cdk deploy "$SPECIFIC_STACK-$ENVIRONMENT" \
        --context environment="$ENVIRONMENT" \
        --require-approval never \
        --profile "$AWS_PROFILE"
elif [ "$DEPLOY_ALL" = true ]; then
    echo -e "${BLUE}Deploying all stacks${NC}"
    cdk deploy --all \
        --context environment="$ENVIRONMENT" \
        --require-approval never \
        --profile "$AWS_PROFILE"
else
    echo -e "${BLUE}Deploying all stacks (use -s to deploy specific stack)${NC}"
    cdk deploy --all \
        --context environment="$ENVIRONMENT" \
        --require-approval never \
        --profile "$AWS_PROFILE"
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Deployment Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "Environment: ${GREEN}$ENVIRONMENT${NC}"
echo -e "Region: ${GREEN}$AWS_REGION${NC}"
echo ""
echo -e "Next steps:"
echo -e "  1. Verify deployment in AWS Console"
echo -e "  2. Test Lambda functions"
echo -e "  3. Monitor CloudWatch Logs"
echo ""
