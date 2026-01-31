# Multi-Tenancy Guide - CEAP Platform

**Last Updated**: January 27, 2026  
**Audience**: Platform architects and DevOps engineers

---

## Overview

This guide explains how to deploy the CEAP platform for multiple clients/tenants and the trade-offs between different isolation strategies.

---

## Current Architecture Analysis

The CEAP platform currently supports **program-level isolation** within a single AWS account:

```
Single AWS Account
├── Shared Infrastructure (Lambdas, Step Functions)
├── Shared DynamoDB Tables
│   ├── Program A data (isolated by programId)
│   ├── Program B data (isolated by programId)
│   └── Program C data (isolated by programId)
└── Shared EventBridge rules
```

**Isolation Level**: Logical (data partitioned by `programId` and `marketplace`)

---

## Multi-Tenancy Options

### Option 1: Single Account, Program-Level Isolation (Current)

**Architecture**:
```
Your AWS Account (794039325997)
├── CEAP Infrastructure (shared)
│   ├── Lambdas (shared code)
│   ├── DynamoDB tables (shared, partitioned by programId)
│   └── Step Functions (shared)
├── Client A: programId="client-a-reviews"
├── Client B: programId="client-b-surveys"
└── Client C: programId="client-c-ratings"
```

**Pros**:
- ✅ **Lowest cost** - Shared infrastructure, pay only for usage
- ✅ **Easiest to manage** - Single deployment, single codebase
- ✅ **Fast onboarding** - Just add program config to DynamoDB
- ✅ **Shared improvements** - All clients benefit from updates
- ✅ **Efficient resource utilization** - Lambda concurrency shared

**Cons**:
- ❌ **Limited isolation** - Clients share same infrastructure
- ❌ **Noisy neighbor risk** - One client's high load affects others
- ❌ **Data residency concerns** - All data in same tables
- ❌ **Compliance challenges** - Harder to meet strict isolation requirements
- ❌ **Blast radius** - Bug affects all clients

**Best For**:
- SaaS platform with many small clients
- Internal use (different business units)
- Development/testing environments
- Cost-sensitive deployments

**Cost**: ~$50-100/month for 10 clients

---

### Option 2: Single Account, Environment-Level Isolation

**Architecture**:
```
Your AWS Account
├── Client A Environment
│   ├── CeapDatabase-client-a
│   ├── CeapDataPlatform-client-a
│   ├── CeapServingAPI-client-a
│   └── 3 stacks with "-client-a" suffix
├── Client B Environment
│   ├── CeapDatabase-client-b
│   ├── CeapDataPlatform-client-b
│   ├── CeapServingAPI-client-b
│   └── 3 stacks with "-client-b" suffix
└── Client C Environment
    ├── CeapDatabase-client-c
    ├── CeapDataPlatform-client-c
    ├── CeapServingAPI-client-c
    └── 3 stacks with "-client-c" suffix
```

**Pros**:
- ✅ **Better isolation** - Separate infrastructure per client
- ✅ **Independent scaling** - Each client has dedicated resources
- ✅ **Blast radius contained** - Issues isolated to one client
- ✅ **Easier compliance** - Clear data boundaries
- ✅ **Same AWS account** - Centralized billing and management

**Cons**:
- ❌ **Higher cost** - Duplicate infrastructure per client
- ❌ **More complex management** - Multiple deployments
- ❌ **Slower updates** - Must deploy to each environment
- ❌ **Resource limits** - AWS account limits apply (e.g., 600 CloudFormation stacks = 200 clients with 3 stacks each)

**Best For**:
- Medium-sized clients (5-20 clients)
- Clients with different SLAs
- Regulated industries requiring isolation
- Clients with varying load patterns

**Cost**: ~$100-200/month per client

**Deployment**:
```bash
# Deploy for Client A
cdk deploy --all --context environment=client-a

# Deploy for Client B
cdk deploy --all --context environment=client-b
```

---

### Option 3: Multi-Account, Full Isolation (Recommended for Enterprise)

**Architecture**:
```
Your Organization
├── Management Account (794039325997)
│   └── Billing, IAM, Governance
├── Client A Account (111111111111)
│   └── Complete CEAP deployment
├── Client B Account (222222222222)
│   └── Complete CEAP deployment
└── Client C Account (333333333333)
    └── Complete CEAP deployment
```

**Pros**:
- ✅ **Maximum isolation** - Complete separation
- ✅ **Security** - No cross-client access possible
- ✅ **Compliance** - Meets strictest requirements
- ✅ **Independent billing** - Each client pays directly
- ✅ **No resource limits** - Each account has full limits
- ✅ **Data residency** - Can deploy in different regions
- ✅ **Custom configurations** - Each client can customize

**Cons**:
- ❌ **Highest cost** - Full infrastructure per client
- ❌ **Complex management** - Multiple AWS accounts
- ❌ **Slower onboarding** - Account setup required
- ❌ **Update overhead** - Deploy to each account separately

**Best For**:
- Enterprise clients
- Regulated industries (healthcare, finance)
- Clients requiring data residency
- Clients with dedicated AWS accounts
- High-value contracts

**Cost**: ~$200-500/month per client (but client pays directly)

**Deployment**:
```bash
# Deploy to Client A's account
export AWS_PROFILE=client-a
cdk deploy --all --context environment=prod

# Deploy to Client B's account
export AWS_PROFILE=client-b
cdk deploy --all --context environment=prod
```

---

## Recommended Strategy by Client Type

### Small Clients (< $10K/year)
**Use**: Option 1 (Program-level isolation)
- Deploy once in your account
- Each client gets a `programId`
- Charge based on usage (candidates processed, messages sent)

### Medium Clients ($10K-$100K/year)
**Use**: Option 2 (Environment-level isolation)
- Each client gets 3 dedicated stacks in your account
- Better isolation and performance guarantees
- Charge flat fee + usage

### Enterprise Clients (> $100K/year)
**Use**: Option 3 (Multi-account)
- Each client gets their own AWS account
- Full control and customization
- Client pays AWS costs directly, you charge for platform license/support

---

## Implementation Recommendations

### For SaaS Model (Recommended)

**Use Option 1 with enhancements**:

1. **Add Client Isolation Layer**:
```kotlin
// Add clientId to all data models
data class Candidate(
    val clientId: String,  // NEW: Client identifier
    val customerId: String,
    val programId: String,
    // ... rest of fields
)

// DynamoDB key structure
PK: CLIENT#{clientId}#CUST#{customerId}
SK: PROG#{programId}#SUBJ#{subjectId}
```

2. **Add Resource Quotas**:
```json
{
  "clientQuotas": {
    "client-a": {
      "maxCandidatesPerDay": 100000,
      "maxApiRequestsPerSecond": 100,
      "maxStorageGB": 10
    },
    "client-b": {
      "maxCandidatesPerDay": 50000,
      "maxApiRequestsPerSecond": 50,
      "maxStorageGB": 5
    }
  }
}
```

3. **Add Client-Specific Configuration**:
```json
{
  "clientId": "client-a",
  "clientName": "Acme Corp",
  "tier": "enterprise",
  "programs": ["product-reviews", "video-ratings"],
  "customConnectors": ["client-a-custom-connector"],
  "customChannels": ["client-a-whatsapp"],
  "billingConfig": {
    "model": "usage-based",
    "pricePerCandidate": 0.001,
    "pricePerMessage": 0.01
  }
}
```

4. **Add IAM Policies for Client Access**:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["dynamodb:GetItem", "dynamodb:Query"],
      "Resource": "arn:aws:dynamodb:*:*:table/Candidates-*",
      "Condition": {
        "ForAllValues:StringEquals": {
          "dynamodb:LeadingKeys": ["CLIENT#client-a#*"]
        }
      }
    }
  ]
}
```

---

### For Enterprise/White-Label Model

**Use Option 3 (Multi-account)**:

1. **Create AWS Organization**:
```bash
# Create organization (one-time)
aws organizations create-organization

# Create account for each client
aws organizations create-account \
  --email client-a@example.com \
  --account-name "CEAP-ClientA"
```

2. **Automate Deployment**:
```bash
#!/bin/bash
# deploy-to-client.sh

CLIENT_ACCOUNT=$1
CLIENT_ENV=$2

# Assume role in client account
aws sts assume-role \
  --role-arn "arn:aws:iam::$CLIENT_ACCOUNT:role/CEAPDeploymentRole" \
  --role-session-name ceap-deployment

# Deploy infrastructure
cdk deploy --all --context environment=$CLIENT_ENV
```

3. **Centralized Monitoring** (Optional):
```bash
# Set up cross-account CloudWatch
# Clients send metrics to your central monitoring account
```

---

## Migration Path

### Phase 1: Start with Option 1 (Current)
- Deploy in your account
- Onboard first 5-10 clients as programs
- Validate architecture and pricing

### Phase 2: Add Client Isolation (Option 1 Enhanced)
- Add `clientId` to data models
- Implement quotas and rate limiting
- Add client-specific IAM policies

### Phase 3: Offer Environment Isolation (Option 2)
- For clients willing to pay premium
- Deploy 3 dedicated stacks per client
- Charge higher tier pricing

### Phase 4: Enterprise Accounts (Option 3)
- For enterprise clients
- Deploy in client's AWS account
- Charge license + support fees

---

## Cost Comparison

### Option 1: Program-Level (10 clients)
```
Shared Infrastructure:
- DynamoDB: $20/month (on-demand, shared)
- Lambda: $30/month (1M invocations total)
- Step Functions: $10/month
- CloudWatch: $10/month
Total: ~$70/month for all clients
Per Client: ~$7/month
```

### Option 2: Environment-Level (10 clients)
```
Per Client:
- DynamoDB: $10/month
- Lambda: $15/month
- Step Functions: $5/month
- CloudWatch: $5/month
Total per client: ~$35/month
Total for 10 clients: ~$350/month
```

### Option 3: Multi-Account (10 clients)
```
Per Client (in their account):
- DynamoDB: $10-50/month
- Lambda: $15-100/month
- Step Functions: $5-20/month
- CloudWatch: $5-20/month
- Data transfer: $10-50/month
Total per client: ~$50-250/month (client pays)

Your costs:
- Management overhead: $0
- Support/maintenance: Charge separately
```

---

## Recommendation

### For Your Use Case:

**Start with Option 1 (Current Architecture)** because:

1. ✅ **Already implemented** - No changes needed
2. ✅ **Cost-effective** - Lowest operational costs
3. ✅ **Fast onboarding** - Add clients in minutes
4. ✅ **Proven pattern** - Used by Stripe, Twilio, SendGrid
5. ✅ **Scalable** - Can handle 100+ clients easily

**Add these enhancements**:

1. **Client ID field** - Add to all data models for clear attribution
2. **Resource quotas** - Prevent one client from consuming all resources
3. **Client-specific configs** - Allow custom connectors/channels per client
4. **Usage tracking** - Track per-client usage for billing
5. **IAM policies** - Restrict client API access to their data only

**Offer Option 2 or 3 as premium tiers**:
- Standard tier: Program-level isolation ($100/month)
- Premium tier: Environment-level isolation ($500/month)
- Enterprise tier: Multi-account deployment ($2,000/month + AWS costs)

---

## Implementation Steps

### Step 1: Add Client ID Support

```kotlin
// Update Candidate model
data class Candidate(
    val clientId: String,  // NEW
    val customerId: String,
    val programId: String,
    // ... rest
)

// Update DynamoDB key structure
PK: CLIENT#{clientId}#CUST#{customerId}#PROG#{programId}#MKT#{marketplace}
SK: SUBJ#{subjectType}#{subjectId}
```

### Step 2: Add Client Configuration

```json
{
  "clientId": "acme-corp",
  "clientName": "Acme Corporation",
  "tier": "standard",
  "enabled": true,
  "quotas": {
    "maxCandidatesPerDay": 100000,
    "maxApiRequestsPerSecond": 100,
    "maxStorageGB": 10
  },
  "programs": ["product-reviews", "video-ratings"],
  "customizations": {
    "allowCustomConnectors": true,
    "allowCustomChannels": true,
    "allowCustomModels": false
  },
  "billing": {
    "model": "usage-based",
    "pricePerCandidate": 0.001,
    "pricePerMessage": 0.01,
    "monthlyMinimum": 100
  }
}
```

### Step 3: Add Client API Gateway

```bash
# Create API Gateway for client access
aws apigateway create-rest-api \
  --name ceap-client-api \
  --description "CEAP Platform Client API"

# Add API key per client
aws apigateway create-api-key \
  --name client-a-api-key \
  --enabled

# Associate with usage plan
aws apigateway create-usage-plan \
  --name client-a-usage-plan \
  --throttle burstLimit=100,rateLimit=50 \
  --quota limit=1000000,period=MONTH
```

### Step 4: Add Usage Tracking

```kotlin
// Track usage per client
class UsageTracker(
    private val cloudWatchClient: CloudWatchClient
) {
    fun trackCandidateProcessed(clientId: String, programId: String) {
        cloudWatchClient.putMetricData {
            namespace = "CEAP/Usage"
            metricData = listOf(
                MetricDatum.builder()
                    .metricName("CandidatesProcessed")
                    .value(1.0)
                    .unit(StandardUnit.COUNT)
                    .dimensions(
                        Dimension.builder().name("ClientId").value(clientId).build(),
                        Dimension.builder().name("ProgramId").value(programId).build()
                    )
                    .timestamp(Instant.now())
                    .build()
            )
        }
    }
}
```

---

## Decision Matrix

| Criteria | Option 1 (Program) | Option 2 (Environment) | Option 3 (Multi-Account) |
|----------|-------------------|----------------------|------------------------|
| **Cost** | $ | $$ | $$$ |
| **Isolation** | Logical | Infrastructure | Complete |
| **Onboarding Time** | Minutes | Hours | Days |
| **Management Complexity** | Low | Medium | High |
| **Scalability** | 100+ clients | 20-50 clients | Unlimited |
| **Compliance** | Basic | Good | Excellent |
| **Customization** | Limited | Moderate | Full |
| **Blast Radius** | All clients | One client | One client |
| **Best For** | SaaS | Premium SaaS | Enterprise |

---

## Hybrid Approach (Recommended)

**Offer all three options based on client needs**:

```
Tier 1: Standard ($100/month)
└── Option 1: Program-level isolation
    └── Shared infrastructure, logical isolation

Tier 2: Premium ($500/month)
└── Option 2: Environment-level isolation
    └── 3 dedicated stacks in your account

Tier 3: Enterprise ($2,000/month + AWS costs)
└── Option 3: Multi-account deployment
    └── Deploy in client's AWS account
```

---

## Security Considerations

### For Option 1 (Program-Level):

**Must implement**:
1. ✅ Client ID in all data models
2. ✅ IAM policies restricting cross-client access
3. ✅ API Gateway with client-specific API keys
4. ✅ Rate limiting per client
5. ✅ Audit logging of all client actions
6. ✅ Encryption at rest and in transit

### For Option 2 (Environment-Level):

**Must implement**:
1. ✅ Separate VPCs per client (optional)
2. ✅ Separate KMS keys per client
3. ✅ Client-specific IAM roles
4. ✅ Network isolation
5. ✅ Separate CloudWatch log groups

### For Option 3 (Multi-Account):

**Must implement**:
1. ✅ Cross-account deployment automation
2. ✅ Centralized monitoring (optional)
3. ✅ AWS Organizations for management
4. ✅ Service Control Policies (SCPs)
5. ✅ Consolidated billing (optional)

---

## Current State & Recommendation

### Your Current Deployment:

**Status**: Option 1 (Program-level isolation) in single account  
**Account**: 794039325997  
**Environment**: dev  

### Recommended Next Steps:

**For SaaS Business Model**:

1. **Keep current architecture** (Option 1)
2. **Add client ID support** to data models
3. **Implement quotas** to prevent abuse
4. **Add API Gateway** for client access
5. **Track usage** for billing
6. **Offer Option 2/3 as premium tiers**

**Implementation Priority**:
1. ✅ Add `clientId` field to Candidate model (1 day)
2. ✅ Update DynamoDB keys to include clientId (1 day)
3. ✅ Add client configuration table (1 day)
4. ✅ Implement quota enforcement (2 days)
5. ✅ Add usage tracking (1 day)
6. ✅ Create API Gateway (2 days)
7. ✅ Add client onboarding automation (2 days)

**Total**: ~2 weeks to production-ready multi-tenant SaaS

---

## Example: Onboarding a New Client

### Option 1 (Current - 5 minutes):

```bash
# 1. Create client configuration
aws dynamodb put-item \
  --table-name ClientConfig-prod \
  --item '{
    "clientId": {"S": "acme-corp"},
    "clientName": {"S": "Acme Corporation"},
    "tier": {"S": "standard"},
    "enabled": {"BOOL": true}
  }'

# 2. Create program configuration
aws dynamodb put-item \
  --table-name ProgramConfig-prod \
  --item '{
    "PK": {"S": "CLIENT#acme-corp#PROGRAM#product-reviews"},
    "SK": {"S": "MARKETPLACE#US"},
    "programId": {"S": "product-reviews"},
    "clientId": {"S": "acme-corp"},
    "enabled": {"BOOL": true}
  }'

# 3. Generate API key
aws apigateway create-api-key \
  --name acme-corp-api-key \
  --enabled

# Done! Client can start using the platform
```

### Option 2 (Environment per client - 2 hours):

```bash
# Deploy dedicated environment
./infrastructure/deploy-cdk.sh --environment acme-corp --region us-east-1 --all

# Configure client-specific settings
# Test deployment
# Provide client with endpoints
```

### Option 3 (Multi-account - 1 day):

```bash
# Create AWS account
aws organizations create-account \
  --email acme-corp@example.com \
  --account-name "CEAP-AcmeCorp"

# Set up cross-account role
# Deploy infrastructure to client account
# Configure monitoring
# Hand over access to client
```

---

## Conclusion

**Answer to your question**: 

**For most clients**: **Single AWS account** (Option 1) is the best choice because:
- ✅ Lowest cost and complexity
- ✅ Fast onboarding
- ✅ Easy to manage
- ✅ Proven SaaS pattern

**For enterprise clients**: **Multiple AWS accounts** (Option 3) when:
- Client requires complete isolation
- Compliance mandates separate accounts
- Client has existing AWS account
- Client wants full control

**Your current deployment supports Option 1 out of the box!** You can onboard clients immediately by creating program configurations with unique `programId` values.

---

**Recommendation**: Start with Option 1, add client ID support, and offer Option 3 as an enterprise tier.
