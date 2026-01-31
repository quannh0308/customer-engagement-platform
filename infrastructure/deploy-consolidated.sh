#!/bin/bash
set -e

# Deployment script for consolidated 3-stack CEAP architecture
# This script deploys the consolidated infrastructure in the correct order

echo "=========================================="
echo "CEAP Consolidated Infrastructure Deployment"
echo "=========================================="
echo ""

# Get environment name (default: dev)
ENV_NAME="${1:-dev}"
echo "Environment: $ENV_NAME"
echo ""

# Check AWS credentials
echo "Checking AWS credentials..."
aws sts get-caller-identity > /dev/null 2>&1
if [ $? -ne 0 ]; then
    echo "❌ Error: AWS credentials not configured or expired"
    echo "Please run 'aws configure' or set AWS environment variables"
    exit 1
fi
echo "✅ AWS credentials valid"
echo ""

# Get AWS account and region
AWS_ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
AWS_REGION=$(aws configure get region || echo "us-east-1")
echo "AWS Account: $AWS_ACCOUNT"
echo "AWS Region: $AWS_REGION"
echo ""

# Navigate to infrastructure directory
cd "$(dirname "$0")"

# Build the project
echo "Building infrastructure project..."
../gradlew build -x test
if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi
echo "✅ Build successful"
echo ""

# Synthesize CDK app
echo "Synthesizing CDK templates..."
npx cdk synth --output cdk.out.consolidated --context environment=$ENV_NAME
if [ $? -ne 0 ]; then
    echo "❌ CDK synthesis failed"
    exit 1
fi
echo "✅ CDK synthesis successful"
echo ""

# Phase 1: Deploy Database Stack (with new exports)
echo "=========================================="
echo "Phase 1: Deploying Database Stack"
echo "=========================================="
echo "Stack: CeapDatabase-$ENV_NAME"
echo "Purpose: Add exports for cross-stack references"
echo ""

npx cdk deploy CeapDatabase-$ENV_NAME \
    --app "cdk.out.consolidated" \
    --require-approval never \
    --context environment=$ENV_NAME

if [ $? -ne 0 ]; then
    echo "❌ Database stack deployment failed"
    exit 1
fi
echo "✅ Database stack deployed successfully"
echo ""

# Phase 2: Deploy Application Stacks in parallel
echo "=========================================="
echo "Phase 2: Deploying Application Stacks"
echo "=========================================="
echo "Stacks: CeapDataPlatform-$ENV_NAME, CeapServingAPI-$ENV_NAME"
echo "Purpose: Deploy consolidated write and read paths"
echo ""

npx cdk deploy CeapDataPlatform-$ENV_NAME CeapServingAPI-$ENV_NAME \
    --app "cdk.out.consolidated" \
    --concurrency 2 \
    --require-approval never \
    --context environment=$ENV_NAME

if [ $? -ne 0 ]; then
    echo "❌ Application stacks deployment failed"
    exit 1
fi
echo "✅ Application stacks deployed successfully"
echo ""

# Deployment complete
echo "=========================================="
echo "Deployment Complete!"
echo "=========================================="
echo ""
echo "Deployed stacks:"
echo "  1. CeapDatabase-$ENV_NAME (Storage Layer)"
echo "  2. CeapDataPlatform-$ENV_NAME (Write Path)"
echo "  3. CeapServingAPI-$ENV_NAME (Read Path)"
echo ""
echo "Next steps:"
echo "  1. Verify resources in AWS Console"
echo "  2. Run integration tests"
echo "  3. Monitor CloudWatch logs"
echo ""
echo "To view stack outputs:"
echo "  aws cloudformation describe-stacks --stack-name CeapDatabase-$ENV_NAME"
echo "  aws cloudformation describe-stacks --stack-name CeapDataPlatform-$ENV_NAME"
echo "  aws cloudformation describe-stacks --stack-name CeapServingAPI-$ENV_NAME"
echo ""
