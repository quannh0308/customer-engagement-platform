# Gradle Multi-Module Structure - Visual Guide

## Current Project Structure

```
solicitation-platform/                           ← ROOT PROJECT
│
├── build.gradle.kts                             ← SHARED CONFIG (applies to all 13 modules)
│   ├── Kotlin 1.9.21 plugin
│   ├── Common dependencies (Kotlin stdlib, SLF4J, JUnit, etc.)
│   ├── JVM target 17
│   └── Test configuration
│
├── settings.gradle.kts                          ← MODULE REGISTRY
│   └── Lists all 13 modules
│
├── gradlew / gradlew.bat                        ← GRADLE WRAPPER
│   └── Ensures everyone uses Gradle 8.5
│
├── solicitation-common/                         ← MODULE 1
│   ├── build.gradle.kts                         ← Module-specific config
│   │   └── (inherits from root + adds AWS CloudWatch)
│   └── src/
│       ├── main/kotlin/
│       └── test/kotlin/
│
├── solicitation-models/                         ← MODULE 2
│   ├── build.gradle.kts                         ← Module-specific config
│   │   ├── (inherits from root)
│   │   ├── + depends on :solicitation-common
│   │   ├── + adds Jackson
│   │   └── + adds Bean Validation
│   └── src/
│       ├── main/kotlin/
│       └── test/kotlin/
│
├── solicitation-storage/                        ← MODULE 3
│   ├── build.gradle.kts                         ← Module-specific config
│   │   ├── (inherits from root)
│   │   ├── + depends on :solicitation-common
│   │   ├── + depends on :solicitation-models
│   │   └── + adds DynamoDB SDK
│   └── src/
│
├── solicitation-workflow-etl/                   ← MODULE 4 (Lambda)
│   ├── build.gradle.kts                         ← Module-specific config
│   │   ├── (inherits from root)
│   │   ├── + depends on multiple modules
│   │   ├── + adds Lambda runtime
│   │   └── + Shadow plugin for fat JAR
│   └── src/
│
└── ... (9 more modules)
```

## How Configuration Flows

```
┌─────────────────────────────────────────────────────────────┐
│  ROOT build.gradle.kts                                      │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ subprojects {                                         │  │
│  │   - Kotlin 1.9.21                                     │  │
│  │   - JVM target 17                                     │  │
│  │   - kotlin-stdlib, kotlin-reflect                     │  │
│  │   - SLF4J, Logback                                    │  │
│  │   - JUnit 5, jqwik, MockK                            │  │
│  │ }                                                      │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ APPLIES TO ALL ↓
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│ Module 1      │   │ Module 2      │   │ Module 3      │
│ ┌───────────┐ │   │ ┌───────────┐ │   │ ┌───────────┐ │
│ │ Inherits: │ │   │ │ Inherits: │ │   │ │ Inherits: │ │
│ │ ✓ Kotlin  │ │   │ │ ✓ Kotlin  │ │   │ │ ✓ Kotlin  │ │
│ │ ✓ JVM 17  │ │   │ │ ✓ JVM 17  │ │   │ │ ✓ JVM 17  │ │
│ │ ✓ Logging │ │   │ │ ✓ Logging │ │   │ │ ✓ Logging │ │
│ │ ✓ Testing │ │   │ │ ✓ Testing │ │   │ │ ✓ Testing │ │
│ └───────────┘ │   │ └───────────┘ │   │ └───────────┘ │
│               │   │               │   │               │
│ ┌───────────┐ │   │ ┌───────────┐ │   │ ┌───────────┐ │
│ │ Adds:     │ │   │ │ Adds:     │ │   │ │ Adds:     │ │
│ │ + AWS SDK │ │   │ │ + Jackson │ │   │ │ + DynamoDB│ │
│ └───────────┘ │   │ │ + project │ │   │ │ + project │ │
└───────────────┘   │ │   (:mod1) │ │   │ │   (:mod2) │ │
                    │ └───────────┘ │   │ └───────────┘ │
                    └───────────────┘   └───────────────┘
```

## Dependency Graph Example

```
┌──────────────────────┐
│ solicitation-common  │  ← No dependencies
└──────────────────────┘
           ▲
           │ depends on
           │
┌──────────────────────┐
│ solicitation-models  │  ← Depends on common
└──────────────────────┘
           ▲
           │ depends on
           │
┌──────────────────────┐
│ solicitation-storage │  ← Depends on models + common
└──────────────────────┘
           ▲
           │ depends on
           │
┌──────────────────────┐
│ workflow-etl (Lambda)│  ← Depends on storage + models + common
└──────────────────────┘
```

## Build Process Flow

### When you run: `./gradlew build`

```
Step 1: Read settings.gradle.kts
   ↓
   Discovers 13 modules

Step 2: Read root build.gradle.kts
   ↓
   Loads shared configuration

Step 3: Read each module's build.gradle.kts
   ↓
   Merges with shared config

Step 4: Resolve dependencies
   ↓
   Creates unified dependency graph

Step 5: Build in dependency order
   ↓
   ┌─────────────────────────────────┐
   │ 1. solicitation-common          │ (no deps)
   ├─────────────────────────────────┤
   │ 2. solicitation-models          │ (needs common)
   ├─────────────────────────────────┤
   │ 3. solicitation-storage         │ (needs models)
   ├─────────────────────────────────┤
   │ 4. solicitation-connectors      │ (needs models)
   ├─────────────────────────────────┤
   │ 5. solicitation-scoring         │ (needs models)
   ├─────────────────────────────────┤
   │ 6. solicitation-filters         │ (needs models)
   ├─────────────────────────────────┤
   │ 7. solicitation-serving         │ (needs storage)
   ├─────────────────────────────────┤
   │ 8. solicitation-channels        │ (needs models)
   ├─────────────────────────────────┤
   │ 9. solicitation-workflow-etl    │ (needs storage)
   ├─────────────────────────────────┤
   │ 10. solicitation-workflow-filter│ (needs filters)
   ├─────────────────────────────────┤
   │ 11. solicitation-workflow-score │ (needs scoring)
   ├─────────────────────────────────┤
   │ 12. solicitation-workflow-store │ (needs storage)
   ├─────────────────────────────────┤
   │ 13. solicitation-workflow-reactive│ (needs all)
   └─────────────────────────────────┘

Step 6: Run tests for all modules
   ↓
   BUILD SUCCESSFUL ✅
```

## What Each File Contains

### Root `build.gradle.kts` (Shared)
```kotlin
plugins {
    kotlin("jvm") version "1.9.21" apply false
}

subprojects {
    // This applies to ALL 13 modules
    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-stdlib")
        testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
        // ... etc
    }
}
```

### Module `build.gradle.kts` (Specific)
```kotlin
// solicitation-models/build.gradle.kts

dependencies {
    // Module-specific dependencies
    implementation(project(":solicitation-common"))  // ← Inter-module
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    
    // Inherits from root:
    // - kotlin-stdlib (automatic)
    // - junit-jupiter (automatic)
    // - etc.
}
```

## Key Concepts

### 1. Inheritance
Every module **automatically inherits** from root config:
- ✅ Kotlin plugin
- ✅ Common dependencies
- ✅ JVM target
- ✅ Test configuration

### 2. Module-Specific Config
Each module **adds** its own needs:
- ✅ Module-specific dependencies
- ✅ Inter-module dependencies (`project(":other")`)
- ✅ Plugins (like Shadow for Lambda JARs)

### 3. Single Source of Truth
Want to upgrade Kotlin? Change **one line** in root build.gradle.kts:
```kotlin
kotlin("jvm") version "1.9.21"  // ← Change here
                                 // ↓ Applies to all 13 modules
```

### 4. Dependency Resolution
Gradle resolves dependencies **once** for entire project:
- ✅ Prevents version conflicts
- ✅ Faster builds (shared cache)
- ✅ Consistent versions across modules

## Commands

```bash
# Build everything (all 13 modules)
./gradlew build

# Build specific module (still uses root config)
./gradlew :solicitation-models:build

# Test everything
./gradlew test

# Test specific module
./gradlew :solicitation-models:test

# Build Lambda JARs only
./gradlew shadowJar

# Clean everything
./gradlew clean

# See all modules
./gradlew projects

# See dependencies for a module
./gradlew :solicitation-models:dependencies
```

## Why This Structure?

### ✅ Advantages
1. **DRY**: Shared config in one place
2. **Consistency**: All modules use same versions
3. **Easy updates**: Change once, applies everywhere
4. **Inter-module deps**: Easy `project(":other")` references
5. **Single build**: One command builds everything
6. **Dependency management**: Unified resolution

### ❌ Alternative (Standalone) Would Have
1. 13 separate `build.gradle.kts` files with duplicated config
2. 13 separate `settings.gradle.kts` files
3. 13 separate `gradlew` wrappers
4. No easy inter-module dependencies
5. Version conflicts between modules
6. 13 separate builds to run

## Summary

**The top-level Gradle files exist to:**
1. **Share configuration** across all 13 modules (DRY)
2. **Manage modules** as one cohesive project
3. **Resolve dependencies** consistently
4. **Enable inter-module dependencies** easily
5. **Simplify builds** with single commands

**The modules are NOT standalone** - they're subprojects of one multi-module Gradle project. This is the recommended approach for related modules! 🎯
