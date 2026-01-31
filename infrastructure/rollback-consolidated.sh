#!/bin/bash
set -e

# Rollback script for consolidated CEAP infrastructure
# Removes the new 3-stack architecture and optionally restores the old 7-stack configuration

echo "=========================================="
echo "CEAP Infrastructure Rollback"
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

# Confirmation prompt
echo "⚠️  WARNING: This will delete the consolidated infrastructure!"
echo ""
echo "Stacks to be deleted:"
echo "  - CeapDataPlatform-$ENV_NAME"
echo "  - CeapServingAPI-$ENV_NAME"
echo ""
echo "Note: CeapDatabase-$ENV_NAME will be retained (contains data)"
echo ""
read -p "Are you sure you want to proceed? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "Rollback cancelled"
    exit 0
fi

echo ""
echo "=========================================="
echo "Pre-Rollback Validation"
echo "=========================================="
echo ""

# Check if new stacks exist
NEW_STACKS_EXIST=false
for stack in "CeapDataPlatform-$ENV_NAME" "CeapServingAPI-$ENV_NAME"; do
    if aws cloudformation describe-stacks --stack-name "$stack" > /dev/null 2>&1; then
        echo "✓ Found: $stack"
        NEW_STACKS_EXIST=true
    else
        echo "ℹ️  Not found: $stack"
    fi
done

if [ "$NEW_STACKS_EXIST" = false ]; then
    echo ""
    echo "ℹ️  No consolidated stacks found to rollback"
    exit 0
fi

echo ""
echo "=========================================="
echo "Deleting Consolidated Stacks"
echo "=========================================="
echo ""

# Delete CeapServingAPI-dev first (no dependencies)
echo "Deleting CeapServingAPI-$ENV_NAME..."
if aws cloudformation describe-stacks --stack-name "CeapServingAPI-$ENV_NAME" > /dev/null 2>&1; then
    aws cloudformation delete-stack --stack-name "CeapServingAPI-$ENV_NAME"
    echo "Waiting for stack deletion..."
    aws cloudformation wait stack-delete-complete --stack-name "CeapServingAPI-$ENV_NAME"
    echo "✅ CeapServingAPI-$ENV_NAME deleted"
else
    echo "ℹ️  CeapServingAPI-$ENV_NAME does not exist"
fi
echo ""

# Delete CeapDataPlatform-dev
echo "Deleting CeapDataPlatform-$ENV_NAME..."
if aws cloudformation describe-stacks --stack-name "CeapDataPlatform-$ENV_NAME" > /dev/null 2>&1; then
    aws cloudformation delete-stack --stack-name "CeapDataPlatform-$ENV_NAME"
    echo "Waiting for stack deletion..."
    aws cloudformation wait stack-delete-complete --stack-name "CeapDataPlatform-$ENV_NAME"
    echo "✅ CeapDataPlatform-$ENV_NAME deleted"
else
    echo "ℹ️  CeapDataPlatform-$ENV_NAME does not exist"
fi
echo ""

# Optionally remove database exports (revert to original state)
echo "=========================================="
echo "Database Stack Cleanup"
echo "=========================================="
echo ""
read -p "Remove database exports (revert to original state)? (yes/no): " REVERT_DB

if [ "$REVERT_DB" = "yes" ]; then
    echo ""
    echo "⚠️  This requires redeploying the database stack without exports"
    echo "⚠️  This is only needed if you want to fully revert to the original 7-stack architecture"
    echo ""
    read -p "Proceed with database stack revert? (yes/no): " CONFIRM_DB
    
    if [ "$CONFIRM_DB" = "yes" ]; then
        echo "ℹ️  To revert database stack, redeploy using the original CeapPlatformApp:"
        echo ""
        echo "  cd infrastructure"
        echo "  npx cdk deploy CeapDatabase-$ENV_NAME --app 'gradle run' --context app=original"
        echo ""
    fi
fi

echo ""
echo "=========================================="
echo "Rollback Complete"
echo "=========================================="
echo ""
echo "Deleted stacks:"
echo "  ✅ CeapDataPlatform-$ENV_NAME"
echo "  ✅ CeapServingAPI-$ENV_NAME"
echo ""
echo "Retained stacks:"
echo "  ℹ️  CeapDatabase-$ENV_NAME (contains data)"
echo ""
echo "Next steps:"
echo "  1. Verify stacks are deleted in AWS Console"
echo "  2. If needed, redeploy old 7-stack architecture"
echo "  3. Run integration tests to verify system functionality"
echo ""

# Check rollback status
echo "=========================================="
echo "Rollback Status Monitoring"
echo "=========================================="
echo ""

# Verify stacks are deleted
ROLLBACK_SUCCESS=true
for stack in "CeapDataPlatform-$ENV_NAME" "CeapServingAPI-$ENV_NAME"; do
    if aws cloudformation describe-stacks --stack-name "$stack" > /dev/null 2>&1; then
        echo "❌ $stack still exists"
        ROLLBACK_SUCCESS=false
    else
        echo "✅ $stack successfully deleted"
    fi
done

echo ""
if [ "$ROLLBACK_SUCCESS" = true ]; then
    echo "✅ Rollback completed successfully"
    exit 0
else
    echo "❌ Rollback incomplete - some stacks still exist"
    exit 1
fi
