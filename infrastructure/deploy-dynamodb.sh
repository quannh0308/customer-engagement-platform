#!/bin/bash

# Deploy DynamoDB tables for Customer Engagement & Action Platform (CEAP)
# Usage: ./deploy-dynamodb.sh [environment]
# Example: ./deploy-dynamodb.sh dev

set -e

# Default to dev environment if not specified
ENVIRONMENT=${1:-dev}

# Validate environment
if [[ ! "$ENVIRONMENT" =~ ^(dev|staging|prod)$ ]]; then
    echo "Error: Invalid environment. Must be one of: dev, staging, prod"
    exit 1
fi

STACK_NAME="ceap-platform-dynamodb-${ENVIRONMENT}"
TEMPLATE_FILE="dynamodb-tables.yaml"

echo "=========================================="
echo "Deploying DynamoDB Tables"
echo "=========================================="
echo "Environment: $ENVIRONMENT"
echo "Stack Name: $STACK_NAME"
echo "Template: $TEMPLATE_FILE"
echo "=========================================="

# Check if AWS CLI is installed
if ! command -v aws &> /dev/null; then
    echo "Error: AWS CLI is not installed"
    exit 1
fi

# Check if template file exists
if [ ! -f "$TEMPLATE_FILE" ]; then
    echo "Error: Template file $TEMPLATE_FILE not found"
    exit 1
fi

# Deploy the stack
echo "Deploying CloudFormation stack..."
aws cloudformation deploy \
    --template-file "$TEMPLATE_FILE" \
    --stack-name "$STACK_NAME" \
    --parameter-overrides Environment="$ENVIRONMENT" \
    --tags \
        Environment="$ENVIRONMENT" \
        Application=CeapPlatform \
        ManagedBy=CloudFormation \
    --no-fail-on-empty-changeset

# Check deployment status
if [ $? -eq 0 ]; then
    echo "=========================================="
    echo "Deployment successful!"
    echo "=========================================="
    
    # Display stack outputs
    echo ""
    echo "Stack Outputs:"
    aws cloudformation describe-stacks \
        --stack-name "$STACK_NAME" \
        --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' \
        --output table
    
    echo ""
    echo "Table Names:"
    aws cloudformation describe-stacks \
        --stack-name "$STACK_NAME" \
        --query 'Stacks[0].Outputs[?contains(OutputKey, `TableName`)].OutputValue' \
        --output table
else
    echo "=========================================="
    echo "Deployment failed!"
    echo "=========================================="
    exit 1
fi
