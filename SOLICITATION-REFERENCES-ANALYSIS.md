# "Solicitation" References Analysis

**Date**: January 20, 2026
**Status**: Analysis Complete

---

## Executive Summary

Analysis of all "solicitation" references in the codebase to determine what should be kept vs. refactored for CEAP branding.

**Key Finding**: Package names have already been migrated from `com.solicitation.*` to `com.ceap.*`. Remaining references are primarily:
1. **Documentation** - Explaining the platform's purpose (appropriate to keep)
2. **Config files** - Infrastructure naming (should be updated)
3. **Comments** - Code documentation (contextually appropriate)

---

## Current State

### ✅ Already Migrated (Complete)

1. **Module Directory Names**: `solicitation-*` → `ceap-*` ✅
2. **Package Names**: `com.solicitation.*` → `com.ceap.*` ✅
3. **Import Statements**: All updated to `com.ceap.*` ✅
4. **Infrastructure Code**: All using `com.ceap.*` ✅

### 📋 Remaining References by Category

#### 1. Documentation & Comments (Contextually Appropriate)

**Location**: Code comments, KDoc, class documentation

**Examples**:
```kotlin
/**
 * Represents a solicitation candidate - a potential opportunity to solicit
 * customer response for a specific subject.
 */
```

**Recommendation**: **KEEP** - These describe what the system does (soliciting customer responses). The word "solicitation" is accurate in this context.

**Rationale**: 
- "Solicitation" describes the action/purpose, not the platform name
- Removing it would make documentation less clear
- Similar to how an "email platform" still talks about "sending emails"

#### 2. Configuration Files (Should Update)

**Files Affected**:
- `infrastructure/lambda-functions.yaml`
- `infrastructure/dynamodb-tables.yaml`
- `infrastructure/step-functions.yaml`
- `infrastructure/eventbridge-rules.yaml`
- `.github/workflows/build.yml`
- `src/main/resources/logback.xml`

**Current References**:
- `ProjectName: solicitation-platform`
- `Description: 'Lambda Function Configurations for General Solicitation Platform'`
- Resource names: `solicitation-platform-*`
- Table names: `solicitation-score-cache`

**Recommendation**: **UPDATE** - These should use CEAP branding for consistency.

**Suggested Changes**:
```yaml
# Before
ProjectName: solicitation-platform
Description: 'Lambda Function Configurations for General Solicitation Platform'

# After
ProjectName: ceap-platform
Description: 'Lambda Function Configurations for Customer Engagement & Action Platform (CEAP)'
```

#### 3. Hardcoded Values in Code (Should Update)

**Location**: Default values in code

**Example**:
```kotlin
// ceap-scoring/src/main/kotlin/com/ceap/scoring/ScoreCacheRepository.kt
private val tableName: String = "solicitation-score-cache"

// ceap-channels/src/main/kotlin/com/ceap/channels/EmailChannelAdapter.kt
fromName = templateConfig["fromName"] as? String ?: "Solicitation Platform"
```

**Recommendation**: **UPDATE** - These are user-facing or infrastructure names.

**Suggested Changes**:
```kotlin
// Update table name
private val tableName: String = "ceap-score-cache"

// Update email from name
fromName = templateConfig["fromName"] as? String ?: "CEAP Platform"
```

#### 4. Test Data (Contextually Appropriate)

**Location**: Test files

**Example**:
```kotlin
Context(type = "program", id = "review-solicitation")
programName = "Product Review Solicitation"
programName = "Video Solicitation Program"
```

**Recommendation**: **KEEP** - These are test data representing program names. Programs can still be called "solicitation programs" even though the platform is CEAP.

**Rationale**: Individual programs/campaigns can have any name. The platform name is CEAP, but programs running on it can be called anything.

---

## Detailed Breakdown

### Files Requiring Updates (6 files)

#### High Priority (Infrastructure)

1. **infrastructure/lambda-functions.yaml**
   - Line 2: Description
   - Line 14: ProjectName default value
   - Multiple resource names

2. **infrastructure/dynamodb-tables.yaml**
   - Description
   - ProjectName default value
   - Table names

3. **infrastructure/step-functions.yaml**
   - Description
   - ProjectName default value
   - State machine names

4. **infrastructure/eventbridge-rules.yaml**
   - Description
   - ProjectName default value
   - Rule names

5. **.github/workflows/build.yml**
   - Workflow name
   - Job names
   - Any hardcoded references

6. **src/main/resources/logback.xml**
   - Line 5: Configuration comment

#### Medium Priority (Code Defaults)

7. **ceap-scoring/src/main/kotlin/com/ceap/scoring/ScoreCacheRepository.kt**
   - Line 24: Default table name

8. **ceap-channels/src/main/kotlin/com/ceap/channels/EmailChannelAdapter.kt**
   - Line 74: Default email from name

### Files to Keep As-Is (Documentation/Comments)

All code comments and documentation that use "solicitation" to describe the action/purpose should remain unchanged. Examples:

- "solicitation candidate" - describes what it is
- "solicitation opportunity" - describes the purpose
- "solicitation scenario" - describes the context
- "solicitation strategies" - describes the approach

---

## Recommendations

### Immediate Actions (High Priority)

1. **Update Infrastructure YAML Files**
   - Change `ProjectName` default from `solicitation-platform` to `ceap-platform`
   - Update descriptions to reference CEAP
   - Update resource naming patterns

2. **Update GitHub Workflow**
   - Change workflow name to reference CEAP
   - Update any hardcoded project names

3. **Update Logback Configuration**
   - Update configuration comment

### Medium Priority Actions

4. **Update Hardcoded Defaults in Code**
   - Table name in ScoreCacheRepository
   - Email from name in EmailChannelAdapter

### Do NOT Change

5. **Keep Documentation/Comments**
   - Code comments describing "solicitation" as an action
   - KDoc describing "solicitation candidates/opportunities"
   - Test data with "solicitation" in program names

---

## Implementation Plan

### Phase 1: Infrastructure Files (30 minutes)

```bash
# Update all infrastructure YAML files
# Files: lambda-functions.yaml, dynamodb-tables.yaml, 
#        step-functions.yaml, eventbridge-rules.yaml

# Changes:
# - ProjectName: solicitation-platform → ceap-platform
# - Description: General Solicitation Platform → CEAP
# - Resource names: solicitation-* → ceap-*
```

### Phase 2: GitHub Workflow (10 minutes)

```bash
# Update .github/workflows/build.yml
# - Workflow name
# - Job names
# - Any project references
```

### Phase 3: Code Defaults (15 minutes)

```kotlin
// Update ScoreCacheRepository.kt
private val tableName: String = "ceap-score-cache"

// Update EmailChannelAdapter.kt
fromName = templateConfig["fromName"] as? String ?: "CEAP Platform"
```

### Phase 4: Logback Config (5 minutes)

```xml
<!-- Update logback.xml comment -->
<!-- Logback Configuration for Customer Engagement & Action Platform (CEAP) -->
```

**Total Estimated Time**: ~60 minutes

---

## Risk Assessment

### Low Risk Changes
- Infrastructure YAML files (not yet deployed)
- GitHub workflow names
- Logback comments
- Email default from name

### Medium Risk Changes
- Table name defaults (if already deployed with old names)
  - **Mitigation**: Make table names configurable via environment variables
  - **Note**: Existing tables won't be affected, only new deployments

### No Risk
- Keeping documentation/comments as-is
- Keeping test data as-is

---

## Backward Compatibility

### Infrastructure
- If infrastructure is already deployed with `solicitation-*` names, we have two options:
  1. **Keep old names** - Set ProjectName parameter to `solicitation-platform` during deployment
  2. **Migrate** - Deploy new resources with CEAP names, migrate data, delete old resources

### Code
- Table name should be configurable via environment variable
- Email from name should be configurable via template config

**Recommendation**: Make all names configurable to support both old and new deployments.

---

## Proposed Changes Summary

### Files to Update: 8

1. ✅ infrastructure/lambda-functions.yaml
2. ✅ infrastructure/dynamodb-tables.yaml
3. ✅ infrastructure/step-functions.yaml
4. ✅ infrastructure/eventbridge-rules.yaml
5. ✅ .github/workflows/build.yml
6. ✅ src/main/resources/logback.xml
7. ✅ ceap-scoring/src/main/kotlin/com/ceap/scoring/ScoreCacheRepository.kt
8. ✅ ceap-channels/src/main/kotlin/com/ceap/channels/EmailChannelAdapter.kt

### Files to Keep: All Others

- Code comments and documentation
- Test data
- KDoc descriptions

---

## Decision Matrix

| Reference Type | Location | Action | Reason |
|---------------|----------|--------|--------|
| Package names | Code | ✅ Done | Already migrated |
| Module names | Directories | ✅ Done | Already migrated |
| Infrastructure names | YAML files | 🔄 Update | User-facing, should match branding |
| Table names | Code defaults | 🔄 Update | Infrastructure naming |
| Email from name | Code defaults | 🔄 Update | User-facing |
| Code comments | Documentation | ✅ Keep | Describes action, not platform |
| Test data | Test files | ✅ Keep | Test data can have any name |
| KDoc | Code docs | ✅ Keep | Describes purpose accurately |

---

## Conclusion

**Recommendation**: Proceed with updating the 8 identified files while keeping all documentation and comments that use "solicitation" to describe the platform's purpose.

**Rationale**: 
- The platform is CEAP (Customer Engagement & Action Platform)
- The platform's purpose is to solicit customer responses
- Infrastructure should use CEAP branding
- Documentation should accurately describe what the system does (solicitation)

**Next Steps**:
1. Review this analysis with the team
2. Get approval for changes
3. Implement Phase 1-4 updates
4. Test deployments
5. Update documentation to reflect changes

---

**Analysis Completed By**: Kiro AI Assistant
**Date**: January 20, 2026
**Files Analyzed**: 150+ source files, 6 config files
**Recommendation**: Update 8 files, keep documentation as-is

