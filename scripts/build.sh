#!/bin/bash

###############################################################################
# Build Script for Customer Engagement & Action Platform (CEAP)
#
# This script builds the multi-module Maven project and creates deployment
# packages for all 5 Lambda functions.
#
# Usage:
#   ./scripts/build.sh [options]
#
# Options:
#   -e, --environment ENV    Environment (dev, staging, prod) [default: dev]
#   -s, --skip-tests         Skip running tests
#   -c, --clean              Clean build (remove target directories)
#   -h, --help               Show this help message
#
# Requirements:
#   - Java 17
#   - Maven 3.8+
#   - AWS CLI (for deployment)
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
SKIP_TESTS=false
CLEAN_BUILD=false

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -e|--environment)
            ENVIRONMENT="$2"
            shift 2
            ;;
        -s|--skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        -c|--clean)
            CLEAN_BUILD=true
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
echo -e "${BLUE}Building CEAP Platform${NC}"
echo -e "${BLUE}Environment: $ENVIRONMENT${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"

if ! command -v java &> /dev/null; then
    echo -e "${RED}Java is not installed${NC}"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${RED}Java 17 or higher is required (found: $JAVA_VERSION)${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Java $JAVA_VERSION${NC}"

if [ ! -f "./gradlew" ]; then
    echo -e "${RED}Gradle wrapper not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Gradle (wrapper)${NC}"

echo ""

# Clean build if requested
if [ "$CLEAN_BUILD" = true ]; then
    echo -e "${YELLOW}Cleaning previous build...${NC}"
    ./gradlew clean
    echo -e "${GREEN}✓ Clean complete${NC}"
    echo ""
fi

# Build Gradle project
echo -e "${YELLOW}Building Gradle project...${NC}"

GRADLE_ARGS="build"
if [ "$SKIP_TESTS" = true ]; then
    GRADLE_ARGS="$GRADLE_ARGS -x test"
    echo -e "${YELLOW}Skipping tests${NC}"
fi

# Build all modules
./gradlew $GRADLE_ARGS

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Build successful${NC}"
else
    echo -e "${RED}✗ Build failed${NC}"
    exit 1
fi

echo ""

# Verify Lambda JARs were created
echo -e "${YELLOW}Verifying Lambda deployment packages...${NC}"

LAMBDA_MODULES=(
    "ceap-workflow-etl"
    "ceap-workflow-filter"
    "ceap-workflow-score"
    "ceap-workflow-store"
    "ceap-workflow-reactive"
)

ALL_JARS_EXIST=true

for module in "${LAMBDA_MODULES[@]}"; do
    JAR_FILE="$module/build/libs/$module-1.0.0-SNAPSHOT.jar"
    if [ -f "$JAR_FILE" ]; then
        SIZE=$(du -h "$JAR_FILE" | cut -f1)
        echo -e "${GREEN}✓ $module ($SIZE)${NC}"
    else
        echo -e "${RED}✗ $module - JAR not found${NC}"
        ALL_JARS_EXIST=false
    fi
done

echo ""

if [ "$ALL_JARS_EXIST" = false ]; then
    echo -e "${RED}Some Lambda JARs were not created${NC}"
    exit 1
fi

# Create deployment directory
DEPLOY_DIR="target/deploy-$ENVIRONMENT"
echo -e "${YELLOW}Creating deployment directory: $DEPLOY_DIR${NC}"
mkdir -p "$DEPLOY_DIR"

# Copy Lambda JARs to deployment directory
for module in "${LAMBDA_MODULES[@]}"; do
    JAR_FILE="$module/build/libs/$module-1.0.0-SNAPSHOT.jar"
    LAMBDA_NAME=$(echo "$module" | sed 's/ceap-workflow-//')
    cp "$JAR_FILE" "$DEPLOY_DIR/${LAMBDA_NAME}-lambda.jar"
done

echo -e "${GREEN}✓ Lambda JARs copied to $DEPLOY_DIR${NC}"
echo ""

# Display build summary
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Build Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "Environment:     ${GREEN}$ENVIRONMENT${NC}"
echo -e "Tests:           ${GREEN}$([ "$SKIP_TESTS" = true ] && echo "Skipped" || echo "Passed")${NC}"
echo -e "Lambda JARs:     ${GREEN}5${NC}"
echo -e "Deploy Dir:      ${GREEN}$DEPLOY_DIR${NC}"
echo ""

# List deployment artifacts
echo -e "${YELLOW}Deployment artifacts:${NC}"
ls -lh "$DEPLOY_DIR"/*.jar | awk '{printf "  %-30s %s\n", $9, $5}'

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Build completed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "Next steps:"
echo -e "  1. Review the deployment artifacts in ${BLUE}$DEPLOY_DIR${NC}"
echo -e "  2. Deploy to AWS using: ${BLUE}./scripts/deploy.sh -e $ENVIRONMENT${NC}"
echo ""
