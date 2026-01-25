package com.ceap.infrastructure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.constant
import io.kotest.property.checkAll
import java.io.File

/**
 * Property-based tests for file rename completeness.
 * 
 * **Feature: complete-ceap-infrastructure-rebrand, Property 5: File Rename Completeness**
 * **Validates: Requirements 1.1, 3.1**
 * 
 * These property tests verify that all file renames in the rebrand are complete:
 * 1. SolicitationPlatformApp.kt → CeapPlatformApp.kt
 * 2. SolicitationLambda.kt → CeapLambda.kt
 * 
 * The tests ensure that:
 * - Old filenames don't exist in the repository
 * - New filenames exist with expected content
 * - Renamed files maintain their expected structure and functionality
 * 
 * The tests use property-based testing to verify these invariants hold across
 * multiple iterations with different test scenarios.
 */
class FileRenameCompletenessPropertyTest : StringSpec({
    
    val projectRoot = File(System.getProperty("user.dir"))
    
    // Determine correct paths based on working directory
    val ceapPlatformAppPath = if (File(projectRoot, "src/main/kotlin/com/ceap/infrastructure/CeapPlatformApp.kt").exists()) {
        "src/main/kotlin/com/ceap/infrastructure/CeapPlatformApp.kt"
    } else {
        "infrastructure/src/main/kotlin/com/ceap/infrastructure/CeapPlatformApp.kt"
    }
    
    val oldSolicitationPlatformAppPath = if (File(projectRoot, "src/main/kotlin/com/ceap/infrastructure").exists()) {
        "src/main/kotlin/com/ceap/infrastructure/SolicitationPlatformApp.kt"
    } else {
        "infrastructure/src/main/kotlin/com/ceap/infrastructure/SolicitationPlatformApp.kt"
    }
    
    val ceapLambdaPath = if (File(projectRoot, "src/main/kotlin/com/ceap/infrastructure/constructs/CeapLambda.kt").exists()) {
        "src/main/kotlin/com/ceap/infrastructure/constructs/CeapLambda.kt"
    } else {
        "infrastructure/src/main/kotlin/com/ceap/infrastructure/constructs/CeapLambda.kt"
    }
    
    val oldSolicitationLambdaPath = if (File(projectRoot, "src/main/kotlin/com/ceap/infrastructure/constructs").exists()) {
        "src/main/kotlin/com/ceap/infrastructure/constructs/SolicitationLambda.kt"
    } else {
        "infrastructure/src/main/kotlin/com/ceap/infrastructure/constructs/SolicitationLambda.kt"
    }
    
    /**
     * Property: Old SolicitationPlatformApp.kt file should not exist.
     * 
     * **Validates: Requirements 1.1**
     * 
     * This property verifies that the old SolicitationPlatformApp.kt file
     * has been successfully renamed and no longer exists in the repository.
     * This ensures the file rename from task 9.1 was completed.
     */
    "Property: Old SolicitationPlatformApp.kt file should not exist" {
        checkAll(100, Arb.constant(Unit)) {
            val oldFile = File(projectRoot, oldSolicitationPlatformAppPath)
            oldFile.exists() shouldBe false
        }
    }
    
    /**
     * Property: New CeapPlatformApp.kt file should exist.
     * 
     * **Validates: Requirements 1.1**
     * 
     * This property verifies that the new CeapPlatformApp.kt file exists
     * in the correct location after the rename from SolicitationPlatformApp.kt.
     */
    "Property: New CeapPlatformApp.kt file should exist" {
        checkAll(100, Arb.constant(Unit)) {
            val newFile = File(projectRoot, ceapPlatformAppPath)
            newFile.exists() shouldBe true
            newFile.isFile shouldBe true
        }
    }
    
    /**
     * Property: CeapPlatformApp.kt should have expected content.
     * 
     * **Validates: Requirements 1.1**
     * 
     * This property verifies that the renamed CeapPlatformApp.kt file
     * contains the expected content including:
     * - Correct package declaration
     * - Main function definition
     * - CEAP-branded stack names
     * - No legacy Solicitation terminology in functional code
     */
    "Property: CeapPlatformApp.kt should have expected content" {
        checkAll(100, Arb.constant(Unit)) {
            val newFile = File(projectRoot, ceapPlatformAppPath)
            val content = newFile.readText()
            
            // Verify package declaration
            content shouldContain "package com.ceap.infrastructure"
            
            // Verify main function exists
            content shouldContain "fun main("
            
            // Verify CEAP-branded stack names are present
            val ceapStackNames = listOf(
                "CeapDatabase",
                "CeapEtlWorkflow",
                "CeapFilterWorkflow",
                "CeapScoreWorkflow",
                "CeapStoreWorkflow",
                "CeapReactiveWorkflow",
                "CeapOrchestration"
            )
            
            ceapStackNames.forEach { stackName ->
                content shouldContain stackName
            }
            
            // Verify no legacy Solicitation terminology in functional code
            // (excluding comments and documentation)
            val codeOnly = content
                .lines()
                .filterNot { it.trim().startsWith("//") }
                .filterNot { it.trim().startsWith("*") }
                .joinToString("\n")
            
            codeOnly shouldNotContain "Solicitation"
            codeOnly shouldNotContain "solicitation"
        }
    }
    
    /**
     * Property: Old SolicitationLambda.kt file should not exist.
     * 
     * **Validates: Requirements 3.1**
     * 
     * This property verifies that the old SolicitationLambda.kt file
     * has been successfully renamed and no longer exists in the repository.
     * This ensures the file rename from task 1.1 was completed.
     */
    "Property: Old SolicitationLambda.kt file should not exist" {
        checkAll(100, Arb.constant(Unit)) {
            val oldFile = File(projectRoot, oldSolicitationLambdaPath)
            oldFile.exists() shouldBe false
        }
    }
    
    /**
     * Property: New CeapLambda.kt file should exist.
     * 
     * **Validates: Requirements 3.1**
     * 
     * This property verifies that the new CeapLambda.kt file exists
     * in the correct location after the rename from SolicitationLambda.kt.
     */
    "Property: New CeapLambda.kt file should exist" {
        checkAll(100, Arb.constant(Unit)) {
            val newFile = File(projectRoot, ceapLambdaPath)
            newFile.exists() shouldBe true
            newFile.isFile shouldBe true
        }
    }
    
    /**
     * Property: CeapLambda.kt should have expected content.
     * 
     * **Validates: Requirements 3.1**
     * 
     * This property verifies that the renamed CeapLambda.kt file
     * contains the expected content including:
     * - Correct package declaration
     * - CeapLambda class definition
     * - Correct constructor signature
     * - No legacy SolicitationLambda references
     */
    "Property: CeapLambda.kt should have expected content" {
        checkAll(100, Arb.constant(Unit)) {
            val newFile = File(projectRoot, ceapLambdaPath)
            val content = newFile.readText()
            
            // Verify package declaration
            content shouldContain "package com.ceap.infrastructure.constructs"
            
            // Verify class definition
            content shouldContain "class CeapLambda"
            
            // Verify class extends Construct
            content shouldContain ": Construct(scope, id)"
            
            // Verify required constructor parameters
            val requiredParams = listOf(
                "scope: Construct",
                "id: String",
                "handler: String",
                "jarPath: String"
            )
            
            requiredParams.forEach { param ->
                content shouldContain param
            }
            
            // Verify no legacy SolicitationLambda references
            content shouldNotContain "SolicitationLambda"
            content shouldNotContain "solicitation"
        }
    }
    
    /**
     * Property: All renamed files should be in correct package structure.
     * 
     * **Validates: Requirements 1.1, 3.1**
     * 
     * This property verifies that all renamed files are located in the
     * correct package structure and have matching package declarations.
     */
    "Property: All renamed files should be in correct package structure" {
        checkAll(100, Arb.constant(Unit)) {
            // Verify CeapPlatformApp.kt is in correct location
            val ceapPlatformAppFile = File(projectRoot, ceapPlatformAppPath)
            ceapPlatformAppFile.exists() shouldBe true
            
            val ceapPlatformAppContent = ceapPlatformAppFile.readText()
            ceapPlatformAppContent shouldContain "package com.ceap.infrastructure"
            
            // Verify CeapLambda.kt is in correct location
            val ceapLambdaFile = File(projectRoot, ceapLambdaPath)
            ceapLambdaFile.exists() shouldBe true
            
            val ceapLambdaContent = ceapLambdaFile.readText()
            ceapLambdaContent shouldContain "package com.ceap.infrastructure.constructs"
        }
    }
    
    /**
     * Property: Renamed files should maintain expected file structure.
     * 
     * **Validates: Requirements 1.1, 3.1**
     * 
     * This property verifies that renamed files maintain their expected
     * structure including imports, class definitions, and functionality.
     */
    "Property: Renamed files should maintain expected file structure" {
        checkAll(100, Arb.constant(Unit)) {
            // Verify CeapPlatformApp.kt has required imports
            val ceapPlatformAppFile = File(projectRoot, ceapPlatformAppPath)
            val ceapPlatformAppContent = ceapPlatformAppFile.readText()
            
            val platformAppImports = listOf(
                "import software.amazon.awscdk.App",
                "import software.amazon.awscdk.Environment",
                "import software.amazon.awscdk.StackProps"
            )
            
            platformAppImports.forEach { import ->
                ceapPlatformAppContent shouldContain import
            }
            
            // Verify CeapLambda.kt has required imports
            val ceapLambdaFile = File(projectRoot, ceapLambdaPath)
            val ceapLambdaContent = ceapLambdaFile.readText()
            
            val lambdaImports = listOf(
                "import software.amazon.awscdk.Duration",
                "import software.amazon.awscdk.services.dynamodb.ITable",
                "import software.amazon.awscdk.services.lambda.Function",
                "import software.constructs.Construct"
            )
            
            lambdaImports.forEach { import ->
                ceapLambdaContent shouldContain import
            }
        }
    }
    
    /**
     * Property: No orphaned old files should exist in repository.
     * 
     * **Validates: Requirements 1.1, 3.1**
     * 
     * This comprehensive property verifies that no old files with legacy
     * names remain in the repository after the rename operations.
     */
    "Property: No orphaned old files should exist in repository" {
        checkAll(100, Arb.constant(Unit)) {
            val oldFiles = listOf(
                File(projectRoot, oldSolicitationPlatformAppPath),
                File(projectRoot, oldSolicitationLambdaPath)
            )
            
            oldFiles.forEach { oldFile ->
                oldFile.exists() shouldBe false
            }
        }
    }
    
    /**
     * Property: All new files should exist with correct names.
     * 
     * **Validates: Requirements 1.1, 3.1**
     * 
     * This comprehensive property verifies that all new files with CEAP
     * branding exist in the correct locations after the rename operations.
     */
    "Property: All new files should exist with correct names" {
        checkAll(100, Arb.constant(Unit)) {
            val newFiles = listOf(
                File(projectRoot, ceapPlatformAppPath),
                File(projectRoot, ceapLambdaPath)
            )
            
            newFiles.forEach { newFile ->
                newFile.exists() shouldBe true
                newFile.isFile shouldBe true
            }
        }
    }
    
    /**
     * Property: Renamed files should not contain legacy class names.
     * 
     * **Validates: Requirements 1.1, 3.1**
     * 
     * This property verifies that renamed files do not contain any references
     * to the old class names (SolicitationPlatformApp, SolicitationLambda).
     */
    "Property: Renamed files should not contain legacy class names" {
        checkAll(100, Arb.constant(Unit)) {
            val ceapPlatformAppFile = File(projectRoot, ceapPlatformAppPath)
            val ceapPlatformAppContent = ceapPlatformAppFile.readText()
            
            // CeapPlatformApp.kt should not reference SolicitationPlatformApp
            // (excluding comments and documentation)
            val platformAppCodeOnly = ceapPlatformAppContent
                .lines()
                .filterNot { it.trim().startsWith("//") }
                .filterNot { it.trim().startsWith("*") }
                .joinToString("\n")
            
            platformAppCodeOnly shouldNotContain "SolicitationPlatformApp"
            
            val ceapLambdaFile = File(projectRoot, ceapLambdaPath)
            val ceapLambdaContent = ceapLambdaFile.readText()
            
            // CeapLambda.kt should not reference SolicitationLambda
            // (excluding comments and documentation)
            val lambdaCodeOnly = ceapLambdaContent
                .lines()
                .filterNot { it.trim().startsWith("//") }
                .filterNot { it.trim().startsWith("*") }
                .joinToString("\n")
            
            lambdaCodeOnly shouldNotContain "SolicitationLambda"
        }
    }
    
    /**
     * Property: File rename completeness across all iterations.
     * 
     * **Validates: Requirements 1.1, 3.1**
     * 
     * This comprehensive property verifies the complete file rename operation:
     * 1. Old files don't exist
     * 2. New files exist
     * 3. New files have expected content
     * 4. No legacy terminology remains in functional code
     */
    "Property: File rename completeness across all iterations" {
        checkAll(100, Arb.constant(Unit)) {
            // Define all file pairs (old -> new)
            val filePairs = listOf(
                Pair(
                    File(projectRoot, oldSolicitationPlatformAppPath),
                    File(projectRoot, ceapPlatformAppPath)
                ),
                Pair(
                    File(projectRoot, oldSolicitationLambdaPath),
                    File(projectRoot, ceapLambdaPath)
                )
            )
            
            filePairs.forEach { (oldFile, newFile) ->
                // Verify old file doesn't exist
                oldFile.exists() shouldBe false
                
                // Verify new file exists
                newFile.exists() shouldBe true
                newFile.isFile shouldBe true
                
                // Verify new file has content
                val content = newFile.readText()
                content.isNotEmpty() shouldBe true
                
                // Verify new file has correct package declaration
                content shouldContain "package com.ceap.infrastructure"
                
                // Verify no legacy terminology in functional code
                val codeOnly = content
                    .lines()
                    .filterNot { it.trim().startsWith("//") }
                    .filterNot { it.trim().startsWith("*") }
                    .joinToString("\n")
                
                codeOnly shouldNotContain "Solicitation"
            }
        }
    }
})
