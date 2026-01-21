# CEAP Platform - Complete Deployment Guide

**Last Updated**: January 20, 2026
**Audience**: Developers with no prior AWS experience
**Time Required**: 2-3 hours for first deployment

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [AWS Account Setup](#aws-account-setup)
3. [Getting AWS Credentials](#getting-aws-credentials)
4. [Installing Required Tools](#installing-required-tools)
5. [Configuring AWS CLI](#configuring-aws-cli)
6. [Understanding Environments](#understanding-environments)
7. [Deploying to Dev](#deploying-to-dev)
8. [Verifying Deployment](#verifying-deployment)
9. [Troubleshooting](#troubleshooting)
10. [Cost Management](#cost-management)

---

## Prerequisites

Before you begin, you need:

- [ ] A computer with macOS, Linux, or Windows
- [ ] Internet connection
- [ ] Terminal/command line access
- [ ] Text editor (VS Code, IntelliJ, etc.)
- [ ] This CEAP platform codebase

**No AWS experience required!** This guide will walk you through everything.

---

## AWS Account Setup

### What is AWS?

AWS (Amazon Web Services) is a cloud computing platform that provides servers, databases, and other services. The CEAP platform runs on AWS.

### Do You Have an AWS Account?

**Option A: You Already Have an AWS Account**
- Skip to [Getting AWS Credentials](#getting-aws-credentials)

**Option B: You Need to Create an AWS Account**


#### Step 1: Create AWS Account

1. Go to https://aws.amazon.com
2. Click "Create an AWS Account"
3. Enter your email address
4. Choose a password
5. Enter your name and contact information
6. Provide payment information (credit card required)
   - **Note**: AWS Free Tier covers most dev usage
   - You won't be charged unless you exceed free tier limits
7. Verify your phone number
8. Choose "Basic Support - Free"
9. Complete sign-up

**Time**: 10-15 minutes

#### Step 2: Sign In to AWS Console

1. Go to https://console.aws.amazon.com
2. Sign in with your email and password
3. You should see the AWS Management Console

**You now have an AWS account!** ✅

---

## Getting AWS Credentials

### What Are AWS Credentials?

AWS credentials are like a username and password for programmatic access to AWS. They consist of:
- **Access Key ID**: Like a username (e.g., `AKIAIOSFODNN7EXAMPLE`)
- **Secret Access Key**: Like a password (e.g., `wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY`)

**⚠️ IMPORTANT**: Never share your Secret Access Key or commit it to git!

### Creating Your Credentials

#### Step 1: Open IAM Console

1. Sign in to AWS Console: https://console.aws.amazon.com
2. In the search bar at the top, type "IAM"
3. Click "IAM" (Identity and Access Management)

#### Step 2: Create an IAM User (Recommended for Security)

**Why?** It's safer to create a separate user for deployments rather than using root account credentials.

1. In IAM Console, click "Users" in the left sidebar
2. Click "Create user" button
3. Enter username: `ceap-deployer`
4. Click "Next"

#### Step 3: Set Permissions

1. Select "Attach policies directly"
2. Search for and select these policies:
   - ✅ `AdministratorAccess` (for dev/testing - full access)
   - Or for production, use more restrictive policies
3. Click "Next"
4. Click "Create user"

#### Step 4: Create Access Keys

1. Click on the user you just created (`ceap-deployer`)
2. Click "Security credentials" tab
3. Scroll down to "Access keys" section
4. Click "Create access key"
5. Select "Command Line Interface (CLI)"
6. Check the confirmation box
7. Click "Next"
8. Add description: "CEAP Platform Deployment"
9. Click "Create access key"

#### Step 5: Save Your Credentials

**⚠️ CRITICAL**: You can only see the Secret Access Key once!

1. You'll see:
   - **Access key ID**: `AKIAIOSFODNN7EXAMPLE`
   - **Secret access key**: `wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY`

2. **Save these somewhere safe**:
   - Option A: Click "Download .csv file" (recommended)
   - Option B: Copy to a password manager
   - Option C: Write them down temporarily

3. Click "Done"

**You now have AWS credentials!** ✅

---

## Installing Required Tools

### 1. Install AWS CLI

The AWS CLI is a command-line tool for interacting with AWS.

#### macOS
```bash
# Using Homebrew (recommended)
brew install awscli

# Verify installation
aws --version
# Should show: aws-cli/2.x.x
```

#### Linux
```bash
# Download installer
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Verify installation
aws --version
```

#### Windows
1. Download installer: https://awscli.amazonaws.com/AWSCLIV2.msi
2. Run the installer
3. Open Command Prompt and verify: `aws --version`

### 2. Install Node.js (for CDK)

#### macOS
```bash
# Using Homebrew
brew install node

# Verify installation
node --version  # Should be 20.x or higher
npm --version
```

#### Linux
```bash
# Using NodeSource
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs

# Verify
node --version
npm --version
```

#### Windows
1. Download installer: https://nodejs.org/en/download/
2. Run the installer
3. Verify in Command Prompt: `node --version`

### 3. Install AWS CDK CLI

```bash
# Install globally using npm
npm install -g aws-cdk

# Verify installation
cdk --version
# Should show: 2.x.x
```

### 4. Verify Java and Gradle (Already Installed)

```bash
# Check Java
java -version
# Should show: openjdk version "17.x.x"

# Check Gradle
./gradlew --version
# Should show: Gradle 8.5
```

**All tools installed!** ✅

---

## Configuring AWS CLI

Now let's configure the AWS CLI with your credentials.

### Step 1: Run AWS Configure

```bash
aws configure
```

### Step 2: Enter Your Credentials

You'll be prompted for 4 pieces of information:

```
AWS Access Key ID [None]: AKIAIOSFODNN7EXAMPLE
```
👉 **Paste your Access Key ID** (from the IAM step earlier)

```
AWS Secret Access Key [None]: wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
```
👉 **Paste your Secret Access Key** (from the IAM step earlier)

```
Default region name [None]: us-east-1
```
👉 **Enter a region**. Common choices:
- `us-east-1` (US East - Virginia) - Most services, cheapest
- `us-west-2` (US West - Oregon) - West coast
- `eu-west-1` (Europe - Ireland) - Europe
- `ap-southeast-1` (Asia Pacific - Singapore) - Asia

```
Default output format [None]: json
```
👉 **Enter**: `json`

### Step 3: Verify Configuration

```bash
# Test your credentials
aws sts get-caller-identity
```

**Expected output**:
```json
{
    "UserId": "AIDAI...",
    "Account": "123456789012",
    "Arn": "arn:aws:iam::123456789012:user/ceap-deployer"
}
```

If you see this, **your AWS CLI is configured!** ✅

### Where Are Credentials Stored?

Your credentials are stored in:
- **macOS/Linux**: `~/.aws/credentials`
- **Windows**: `C:\Users\USERNAME\.aws\credentials`

**⚠️ Security**: Never commit this file to git! (Already in .gitignore)

---

## Understanding Environments

### What is an Environment?

An environment is a separate deployment of your application. Think of it like:
- **dev** = Your personal sandbox for testing
- **staging** = Shared testing environment before production
- **prod** = Live environment serving real customers

### Environment Isolation

Each environment has:
- Separate AWS resources (different DynamoDB tables, Lambda functions)
- Separate data (dev data doesn't affect prod)
- Separate costs (you can delete dev without affecting prod)

### Naming Convention

Resources are named: `ceap-platform-{component}-{environment}`

Examples:
- `ceap-platform-etl-dev` (dev environment)
- `ceap-platform-etl-staging` (staging environment)
- `ceap-platform-etl-prod` (production environment)

---

## Deploying to Dev

Now let's deploy the CEAP platform to your AWS account!

### Step 1: Navigate to Infrastructure Directory

```bash
cd infrastructure
```

### Step 2: Bootstrap CDK (First-Time Only)

This sets up AWS resources needed for CDK deployments.

```bash
./deploy-cdk.sh --bootstrap --environment dev --region us-east-1
```

**What this does**:
- Creates an S3 bucket for CDK assets
- Creates IAM roles for CloudFormation
- Sets up CDK toolkit stack

**Time**: 2-3 minutes

**Expected output**:
```
✅ Environment aws://123456789012/us-east-1 bootstrapped.
```

**You only need to do this once per account/region!**

### Step 3: Build Lambda JARs

The deployment script will do this automatically, but you can verify:

```bash
cd ..
./gradlew shadowJar
```

**What this does**:
- Compiles all Kotlin code
- Runs tests
- Creates 5 Lambda JAR files

**Time**: 2-5 minutes

**Expected output**:
```
BUILD SUCCESSFUL in 3m 45s
```

### Step 4: Deploy Infrastructure

```bash
cd infrastructure
./deploy-cdk.sh --environment dev --region us-east-1
```

**What this does**:
1. Builds Lambda JARs
2. Synthesizes CloudFormation templates
3. Deploys DynamoDB tables
4. Deploys Lambda functions
5. Deploys Step Functions workflows
6. Deploys EventBridge rules
7. Sets up CloudWatch logs

**Time**: 10-15 minutes

**Expected output**:
```
✅ DatabaseStack-dev
✅ EtlWorkflowStack-dev
✅ FilterWorkflowStack-dev
✅ ScoreWorkflowStack-dev
✅ StoreWorkflowStack-dev
✅ ReactiveWorkflowStack-dev
✅ OrchestrationStack-dev

========================================
Deployment Complete!
========================================
```

### Step 5: Verify Deployment

```bash
# List all stacks
cdk list --context environment=dev

# Check stack status
aws cloudformation describe-stacks --stack-name DatabaseStack-dev
```

**You've deployed CEAP to AWS!** 🎉

---

## Verifying Deployment

### Check DynamoDB Tables

```bash
# List tables
aws dynamodb list-tables

# Expected output:
# - ceap-candidates-dev
# - ceap-program-config-dev
# - ceap-score-cache-dev
```

### Check Lambda Functions

```bash
# List functions
aws lambda list-functions --query 'Functions[?starts_with(FunctionName, `ceap-platform`)].FunctionName'

# Expected output:
# - ceap-platform-etl-dev
# - ceap-platform-filter-dev
# - ceap-platform-score-dev
# - ceap-platform-store-dev
# - ceap-platform-serve-dev
```

### Check in AWS Console

1. Go to https://console.aws.amazon.com
2. Make sure you're in the correct region (top-right corner)
3. Search for "DynamoDB" - you should see 3 tables
4. Search for "Lambda" - you should see 5 functions
5. Search for "Step Functions" - you should see workflows

**Everything deployed successfully!** ✅

---

## Testing Your Deployment

### Test Lambda Function

```bash
# Invoke the serve Lambda (should return empty results)
aws lambda invoke \
  --function-name ceap-platform-serve-dev \
  --payload '{"customerId":"test-123","channel":"email"}' \
  response.json

# Check response
cat response.json
```

### Check CloudWatch Logs

```bash
# View recent logs
aws logs tail /aws/lambda/ceap-platform-serve-dev --follow
```

---

## Troubleshooting

### Issue: "Unable to locate credentials"

**Problem**: AWS CLI can't find your credentials

**Solution**:
```bash
# Re-run configure
aws configure

# Or check if credentials file exists
cat ~/.aws/credentials
```

### Issue: "Access Denied"

**Problem**: Your IAM user doesn't have required permissions

**Solution**:
1. Go to IAM Console
2. Click on your user
3. Add `AdministratorAccess` policy
4. Try deployment again

### Issue: "Stack already exists"

**Problem**: You're trying to deploy a stack that already exists

**Solution**:
```bash
# Update existing stack
cdk deploy --context environment=dev

# Or delete and redeploy
cdk destroy --context environment=dev
cdk deploy --context environment=dev
```

### Issue: "Bootstrap required"

**Problem**: CDK hasn't been bootstrapped in this account/region

**Solution**:
```bash
./deploy-cdk.sh --bootstrap --environment dev
```



---

## Cost Management

### Understanding AWS Costs

AWS charges for:
- **DynamoDB**: Storage + read/write requests
- **Lambda**: Number of invocations + execution time
- **CloudWatch**: Log storage + metrics
- **S3**: Storage for Lambda artifacts

### Estimated Costs for Dev Environment

**With minimal usage** (testing only):
- DynamoDB: $5-10/month (on-demand pricing)
- Lambda: $0.20/month (1M requests free tier)
- CloudWatch: $5/month (5GB logs free tier)
- S3: $0.50/month (minimal storage)

**Total**: ~$10-20/month

### Free Tier Benefits

AWS Free Tier includes (first 12 months):
- 25 GB DynamoDB storage
- 1M Lambda requests/month
- 5 GB CloudWatch Logs

**Most dev usage stays within free tier!**

### Monitoring Costs

```bash
# Check current month costs
aws ce get-cost-and-usage \
  --time-period Start=2026-01-01,End=2026-01-31 \
  --granularity MONTHLY \
  --metrics BlendedCost
```

Or check in AWS Console:
1. Go to https://console.aws.amazon.com
2. Search for "Billing"
3. Click "Bills" to see current charges

### Reducing Costs

**When not using dev environment**:
```bash
# Delete all resources
cdk destroy --all --context environment=dev

# Redeploy when needed
./deploy-cdk.sh --environment dev
```

**Cost optimization features already configured**:
- ✅ On-demand DynamoDB (pay only for what you use)
- ✅ TTL enabled (automatic data cleanup)
- ✅ Serverless architecture (no idle costs)
- ✅ Efficient Lambda memory sizing

---

## Advanced: Multiple Environments

### Deploying to Staging

```bash
./deploy-cdk.sh --environment staging --region us-east-1
```

This creates a completely separate set of resources:
- `ceap-platform-*-staging` (instead of `-dev`)

### Deploying to Production

```bash
./deploy-cdk.sh --environment prod --region us-east-1
```

**⚠️ Production Checklist**:
- [ ] Tested thoroughly in dev and staging
- [ ] Reviewed all configurations
- [ ] Set up monitoring and alarms
- [ ] Documented rollback procedures
- [ ] Notified team members

---

## Advanced: Using AWS Profiles

### What Are Profiles?

Profiles let you manage multiple AWS accounts or users.

### Creating a Profile

```bash
# Configure a named profile
aws configure --profile ceap-dev
```

Enter credentials for this specific profile.

### Using a Profile

```bash
# Deploy with specific profile
./deploy-cdk.sh --environment dev --profile ceap-dev

# Or set as default
export AWS_PROFILE=ceap-dev
./deploy-cdk.sh --environment dev
```

### Listing Profiles

```bash
# View all configured profiles
cat ~/.aws/credentials
```

---

## Quick Reference

### Essential Commands

```bash
# Check AWS configuration
aws sts get-caller-identity

# Bootstrap CDK (first-time only)
./deploy-cdk.sh --bootstrap --environment dev

# Deploy to dev
./deploy-cdk.sh --environment dev

# Show what will change (before deploying)
./deploy-cdk.sh --environment dev --diff

# Deploy specific stack
./deploy-cdk.sh --environment dev --stack Database

# Destroy environment
cdk destroy --all --context environment=dev

# View logs
aws logs tail /aws/lambda/ceap-platform-serve-dev --follow
```

### Useful AWS Console Links

- **DynamoDB**: https://console.aws.amazon.com/dynamodb
- **Lambda**: https://console.aws.amazon.com/lambda
- **CloudWatch**: https://console.aws.amazon.com/cloudwatch
- **IAM**: https://console.aws.amazon.com/iam
- **Billing**: https://console.aws.amazon.com/billing

---

## Getting Help

### AWS Documentation
- AWS Getting Started: https://aws.amazon.com/getting-started/
- AWS CLI Guide: https://docs.aws.amazon.com/cli/
- AWS CDK Guide: https://docs.aws.amazon.com/cdk/

### CEAP Platform Documentation
- Architecture: `docs/VISUAL-ARCHITECTURE.md`
- Infrastructure: `infrastructure/LAMBDA_CONFIGURATION.md`
- Use Cases: `docs/usecases/`

### Common Questions

**Q: How do I know which region to use?**
A: Use `us-east-1` (cheapest, most services). Or choose closest to your users.

**Q: Can I use my company's AWS account?**
A: Yes! Ask your AWS administrator for credentials or an IAM role.

**Q: What if I don't have a credit card?**
A: AWS requires a credit card, but you can use a prepaid card or virtual card.

**Q: How do I delete everything?**
A: Run `cdk destroy --all --context environment=dev`

**Q: Is this safe to deploy?**
A: Yes! The dev environment is isolated and uses minimal resources.

---

## Next Steps After Deployment

1. **Configure Your First Program**
   - Create program configuration in DynamoDB
   - Define data sources and filters
   - Set up scoring models

2. **Test End-to-End Flow**
   - Trigger ETL workflow
   - Verify candidates are created
   - Test serving API

3. **Set Up Monitoring**
   - Review CloudWatch dashboards
   - Configure alarms
   - Set up cost alerts

4. **Deploy to Staging**
   - Test with realistic data
   - Run integration tests
   - Validate performance

5. **Deploy to Production**
   - Follow production checklist
   - Monitor closely
   - Iterate based on feedback

---

## Summary

**You've learned**:
- ✅ How to create an AWS account
- ✅ How to get AWS credentials
- ✅ How to install required tools
- ✅ How to configure AWS CLI
- ✅ How to deploy CEAP platform
- ✅ How to verify deployment
- ✅ How to manage costs

**Time to deploy**: ~30 minutes (after setup)

**You're ready to deploy CEAP to AWS!** 🚀

---

**Guide Version**: 1.0
**Last Updated**: January 20, 2026
**Maintained By**: CEAP Platform Team

