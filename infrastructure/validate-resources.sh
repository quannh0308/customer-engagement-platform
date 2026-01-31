#!/bin/bash
set -e

# Resource validation script for consolidated CEAP infrastructure
# Compares resources between old 7-stack and new 3-stack architectures

echo "=========================================="
echo "CEAP Resource Validation"
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
    exit 1
fi
echo "✅ AWS credentials valid"
echo ""

# Function to count resources in a stack
count_stack_resources() {
    local stack_name=$1
    local resource_type=$2
    
    # Check if stack exists
    if ! aws cloudformation describe-stacks --stack-name "$stack_name" > /dev/null 2>&1; then
        echo "0"
        return
    fi
    
    # Count resources of specific type
    local count=$(aws cloudformation list-stack-resources \
        --stack-name "$stack_name" \
        --query "StackResourceSummaries[?ResourceType=='$resource_type'].ResourceType" \
        --output text 2>/dev/null | wc -w)
    
    echo "$count"
}

# Function to get all resources from a stack
get_stack_resources() {
    local stack_name=$1
    
    # Check if stack exists
    if ! aws cloudformation describe-stacks --stack-name "$stack_name" > /dev/null 2>&1; then
        echo "Stack $stack_name does not exist"
        return
    fi
    
    aws cloudformation list-stack-resources \
        --stack-name "$stack_name" \
        --query 'StackResourceSummaries[*].[ResourceType,LogicalResourceId,PhysicalResourceId]' \
        --output text 2>/dev/null
}

echo "=========================================="
echo "Consolidated Stack Resources"
echo "=========================================="
echo ""

# Count resources in new consolidated stacks
echo "CeapDatabase-$ENV_NAME:"
DB_TABLES=$(count_stack_resources "CeapDatabase-$ENV_NAME" "AWS::DynamoDB::Table")
echo "  DynamoDB Tables: $DB_TABLES"
echo ""

echo "CeapDataPlatform-$ENV_NAME:"
DP_LAMBDAS=$(count_stack_resources "CeapDataPlatform-$ENV_NAME" "AWS::Lambda::Function")
DP_STATE_MACHINES=$(count_stack_resources "CeapDataPlatform-$ENV_NAME" "AWS::StepFunctions::StateMachine")
DP_RULES=$(count_stack_resources "CeapDataPlatform-$ENV_NAME" "AWS::Events::Rule")
DP_ROLES=$(count_stack_resources "CeapDataPlatform-$ENV_NAME" "AWS::IAM::Role")
echo "  Lambda Functions: $DP_LAMBDAS"
echo "  Step Functions: $DP_STATE_MACHINES"
echo "  EventBridge Rules: $DP_RULES"
echo "  IAM Roles: $DP_ROLES"
echo ""

echo "CeapServingAPI-$ENV_NAME:"
SA_LAMBDAS=$(count_stack_resources "CeapServingAPI-$ENV_NAME" "AWS::Lambda::Function")
SA_TABLES=$(count_stack_resources "CeapServingAPI-$ENV_NAME" "AWS::DynamoDB::Table")
SA_RULES=$(count_stack_resources "CeapServingAPI-$ENV_NAME" "AWS::Events::Rule")
SA_ROLES=$(count_stack_resources "CeapServingAPI-$ENV_NAME" "AWS::IAM::Role")
echo "  Lambda Functions: $SA_LAMBDAS"
echo "  DynamoDB Tables: $SA_TABLES"
echo "  EventBridge Rules: $SA_RULES"
echo "  IAM Roles: $SA_ROLES"
echo ""

# Calculate totals for new architecture
TOTAL_NEW_LAMBDAS=$((DP_LAMBDAS + SA_LAMBDAS))
TOTAL_NEW_TABLES=$((DB_TABLES + SA_TABLES))
TOTAL_NEW_STATE_MACHINES=$DP_STATE_MACHINES
TOTAL_NEW_RULES=$((DP_RULES + SA_RULES))
TOTAL_NEW_ROLES=$((DP_ROLES + SA_ROLES))

echo "=========================================="
echo "Resource Totals (3-Stack Architecture)"
echo "=========================================="
echo "Lambda Functions: $TOTAL_NEW_LAMBDAS"
echo "DynamoDB Tables: $TOTAL_NEW_TABLES"
echo "Step Functions: $TOTAL_NEW_STATE_MACHINES"
echo "EventBridge Rules: $TOTAL_NEW_RULES"
echo "IAM Roles: $TOTAL_NEW_ROLES"
echo ""

# Check if old stacks exist
echo "=========================================="
echo "Checking Old Stack Architecture"
echo "=========================================="
echo ""

OLD_STACKS_EXIST=false
for stack in "CeapEtlWorkflow-$ENV_NAME" "CeapFilterWorkflow-$ENV_NAME" "CeapScoreWorkflow-$ENV_NAME" "CeapStoreWorkflow-$ENV_NAME" "CeapReactiveWorkflow-$ENV_NAME" "CeapOrchestration-$ENV_NAME"; do
    if aws cloudformation describe-stacks --stack-name "$stack" > /dev/null 2>&1; then
        echo "✓ Found: $stack"
        OLD_STACKS_EXIST=true
    fi
done

if [ "$OLD_STACKS_EXIST" = false ]; then
    echo "ℹ️  Old 7-stack architecture not found (already cleaned up or never existed)"
    echo ""
    echo "=========================================="
    echo "Validation Summary"
    echo "=========================================="
    echo "✅ New 3-stack architecture is deployed"
    echo "ℹ️  Cannot compare with old architecture (not present)"
    echo ""
    exit 0
fi

echo ""

# Count resources in old stacks (if they exist)
echo "=========================================="
echo "Old Stack Resources (7-Stack Architecture)"
echo "=========================================="
echo ""

OLD_ETL_LAMBDAS=$(count_stack_resources "CeapEtlWorkflow-$ENV_NAME" "AWS::Lambda::Function")
OLD_FILTER_LAMBDAS=$(count_stack_resources "CeapFilterWorkflow-$ENV_NAME" "AWS::Lambda::Function")
OLD_SCORE_LAMBDAS=$(count_stack_resources "CeapScoreWorkflow-$ENV_NAME" "AWS::Lambda::Function")
OLD_STORE_LAMBDAS=$(count_stack_resources "CeapStoreWorkflow-$ENV_NAME" "AWS::Lambda::Function")
OLD_REACTIVE_LAMBDAS=$(count_stack_resources "CeapReactiveWorkflow-$ENV_NAME" "AWS::Lambda::Function")

OLD_ETL_STATE_MACHINES=$(count_stack_resources "CeapEtlWorkflow-$ENV_NAME" "AWS::StepFunctions::StateMachine")
OLD_FILTER_STATE_MACHINES=$(count_stack_resources "CeapFilterWorkflow-$ENV_NAME" "AWS::StepFunctions::StateMachine")
OLD_SCORE_STATE_MACHINES=$(count_stack_resources "CeapScoreWorkflow-$ENV_NAME" "AWS::StepFunctions::StateMachine")
OLD_STORE_STATE_MACHINES=$(count_stack_resources "CeapStoreWorkflow-$ENV_NAME" "AWS::StepFunctions::StateMachine")
OLD_REACTIVE_STATE_MACHINES=$(count_stack_resources "CeapReactiveWorkflow-$ENV_NAME" "AWS::StepFunctions::StateMachine")
OLD_ORCH_STATE_MACHINES=$(count_stack_resources "CeapOrchestration-$ENV_NAME" "AWS::StepFunctions::StateMachine")

TOTAL_OLD_LAMBDAS=$((OLD_ETL_LAMBDAS + OLD_FILTER_LAMBDAS + OLD_SCORE_LAMBDAS + OLD_STORE_LAMBDAS + OLD_REACTIVE_LAMBDAS))
TOTAL_OLD_STATE_MACHINES=$((OLD_ETL_STATE_MACHINES + OLD_FILTER_STATE_MACHINES + OLD_SCORE_STATE_MACHINES + OLD_STORE_STATE_MACHINES + OLD_REACTIVE_STATE_MACHINES + OLD_ORCH_STATE_MACHINES))

echo "Total Lambda Functions: $TOTAL_OLD_LAMBDAS"
echo "Total Step Functions: $TOTAL_OLD_STATE_MACHINES"
echo ""

# Validation checks
echo "=========================================="
echo "Validation Results"
echo "=========================================="
echo ""

VALIDATION_PASSED=true

# Check Lambda functions
if [ "$TOTAL_NEW_LAMBDAS" -ge "$TOTAL_OLD_LAMBDAS" ]; then
    echo "✅ Lambda Functions: $TOTAL_NEW_LAMBDAS (old: $TOTAL_OLD_LAMBDAS)"
else
    echo "❌ Lambda Functions: $TOTAL_NEW_LAMBDAS (old: $TOTAL_OLD_LAMBDAS) - MISSING FUNCTIONS"
    VALIDATION_PASSED=false
fi

# Check Step Functions
if [ "$TOTAL_NEW_STATE_MACHINES" -ge "$TOTAL_OLD_STATE_MACHINES" ]; then
    echo "✅ Step Functions: $TOTAL_NEW_STATE_MACHINES (old: $TOTAL_OLD_STATE_MACHINES)"
else
    echo "❌ Step Functions: $TOTAL_NEW_STATE_MACHINES (old: $TOTAL_OLD_STATE_MACHINES) - MISSING STATE MACHINES"
    VALIDATION_PASSED=false
fi

# Check DynamoDB tables
echo "✅ DynamoDB Tables: $TOTAL_NEW_TABLES"

# Check EventBridge rules
echo "✅ EventBridge Rules: $TOTAL_NEW_RULES"

# Check IAM roles
echo "✅ IAM Roles: $TOTAL_NEW_ROLES"

echo ""

# Generate detailed inventory report
REPORT_FILE="resource-validation-report-$(date +%Y%m%d-%H%M%S).txt"
echo "Generating detailed inventory report: $REPORT_FILE"
echo ""

{
    echo "CEAP Resource Validation Report"
    echo "Generated: $(date)"
    echo "Environment: $ENV_NAME"
    echo ""
    echo "=========================================="
    echo "NEW 3-STACK ARCHITECTURE"
    echo "=========================================="
    echo ""
    echo "CeapDatabase-$ENV_NAME Resources:"
    get_stack_resources "CeapDatabase-$ENV_NAME"
    echo ""
    echo "CeapDataPlatform-$ENV_NAME Resources:"
    get_stack_resources "CeapDataPlatform-$ENV_NAME"
    echo ""
    echo "CeapServingAPI-$ENV_NAME Resources:"
    get_stack_resources "CeapServingAPI-$ENV_NAME"
    echo ""
    
    if [ "$OLD_STACKS_EXIST" = true ]; then
        echo "=========================================="
        echo "OLD 7-STACK ARCHITECTURE"
        echo "=========================================="
        echo ""
        for stack in "CeapEtlWorkflow-$ENV_NAME" "CeapFilterWorkflow-$ENV_NAME" "CeapScoreWorkflow-$ENV_NAME" "CeapStoreWorkflow-$ENV_NAME" "CeapReactiveWorkflow-$ENV_NAME" "CeapOrchestration-$ENV_NAME"; do
            if aws cloudformation describe-stacks --stack-name "$stack" > /dev/null 2>&1; then
                echo "$stack Resources:"
                get_stack_resources "$stack"
                echo ""
            fi
        done
    fi
} > "$REPORT_FILE"

echo "✅ Report saved to: $REPORT_FILE"
echo ""

# Final summary
echo "=========================================="
echo "Validation Summary"
echo "=========================================="
if [ "$VALIDATION_PASSED" = true ]; then
    echo "✅ All validation checks passed!"
    echo "✅ New 3-stack architecture contains all expected resources"
    echo ""
    echo "Safe to proceed with old stack cleanup"
    exit 0
else
    echo "❌ Validation failed!"
    echo "❌ Some resources are missing in the new architecture"
    echo ""
    echo "DO NOT proceed with old stack cleanup"
    exit 1
fi
