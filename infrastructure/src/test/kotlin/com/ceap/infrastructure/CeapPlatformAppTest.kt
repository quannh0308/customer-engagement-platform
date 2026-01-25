package com.ceap.infrastructure

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Unit tests for CeapPlatformApp main application file existence.
 * 
 * **Validates: Requirements 1.1**
 * 
 * These tests verify that:
 * 1. The CeapPlatformApp.kt file exists in the correct location
 * 2. The old SolicitationPlatformApp.kt file does not exist
 * 
 * Note: These tests use file-based verification to ensure the file rename
 * from task 9.1 was completed successfully.
 */
class CeapPlatformAppTest {
    
    private val projectRoot = File(System.getProperty("user.dir"))
    
    // When running tests from the infrastructure module, the working directory is the infrastructure directory
    // When running from the root, we need to include the infrastructure prefix
    private val ceapPlatformAppPath = if (File(projectRoot, "src/main/kotlin/com/ceap/infrastructure/CeapPlatformApp.kt").exists()) {
        "src/main/kotlin/com/ceap/infrastructure/CeapPlatformApp.kt"
    } else {
        "infrastructure/src/main/kotlin/com/ceap/infrastructure/CeapPlatformApp.kt"
    }
    
    private val oldSolicitationPlatformAppPath = if (File(projectRoot, "src/main/kotlin/com/ceap/infrastructure").exists()) {
        "src/main/kotlin/com/ceap/infrastructure/SolicitationPlatformApp.kt"
    } else {
        "infrastructure/src/main/kotlin/com/ceap/infrastructure/SolicitationPlatformApp.kt"
    }
    
    /**
     * Test that CeapPlatformApp.kt file exists in the correct location.
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    fun `CeapPlatformApp file should exist`() {
        val ceapPlatformAppFile = File(projectRoot, ceapPlatformAppPath)
        assertTrue(
            ceapPlatformAppFile.exists(),
            "CeapPlatformApp.kt file should exist at $ceapPlatformAppPath"
        )
        assertTrue(
            ceapPlatformAppFile.isFile,
            "CeapPlatformApp.kt should be a file, not a directory"
        )
    }
    
    /**
     * Test that old SolicitationPlatformApp.kt file does not exist.
     * 
     * **Validates: Requirements 1.1**
     * 
     * This verifies that the file rename from task 9.1 was successful and
     * no legacy files remain in the codebase.
     */
    @Test
    fun `old SolicitationPlatformApp file should not exist`() {
        val oldFile = File(projectRoot, oldSolicitationPlatformAppPath)
        assertFalse(
            oldFile.exists(),
            "SolicitationPlatformApp.kt file should not exist - it should have been renamed to CeapPlatformApp.kt"
        )
    }
    
    /**
     * Test that CeapPlatformApp has correct package declaration.
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    fun `CeapPlatformApp should have correct package declaration`() {
        val ceapPlatformAppFile = File(projectRoot, ceapPlatformAppPath)
        val content = ceapPlatformAppFile.readText()
        
        assertTrue(
            content.contains("package com.ceap.infrastructure"),
            "File should have correct package declaration"
        )
    }
    
    /**
     * Test that CeapPlatformApp contains the main function.
     * 
     * **Validates: Requirements 1.1**
     * 
     * The main application file should contain the main function that
     * serves as the entry point for the CDK application.
     */
    @Test
    fun `CeapPlatformApp should contain main function`() {
        val ceapPlatformAppFile = File(projectRoot, ceapPlatformAppPath)
        val content = ceapPlatformAppFile.readText()
        
        assertTrue(
            content.contains("fun main("),
            "File should contain 'fun main(' function definition"
        )
    }
    
    /**
     * Test that CeapPlatformApp uses CEAP branding in stack names.
     * 
     * **Validates: Requirements 1.1, 2.1-2.7**
     * 
     * Verifies that all CloudFormation stack names use the CEAP prefix
     * instead of the legacy Solicitation prefix.
     */
    @Test
    fun `CeapPlatformApp should use CEAP branding in stack names`() {
        val ceapPlatformAppFile = File(projectRoot, ceapPlatformAppPath)
        val content = ceapPlatformAppFile.readText()
        
        // Verify CEAP stack names are present
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
            assertTrue(
                content.contains(stackName),
                "File should contain stack name: $stackName"
            )
        }
    }
    
    /**
     * Test that CeapPlatformApp does not contain legacy Solicitation branding.
     * 
     * **Validates: Requirements 1.1, 1.2**
     * 
     * Verifies that no legacy "Solicitation" terminology remains in the
     * functional code (excluding comments and documentation).
     */
    @Test
    fun `CeapPlatformApp should not contain legacy Solicitation branding in code`() {
        val ceapPlatformAppFile = File(projectRoot, ceapPlatformAppPath)
        val content = ceapPlatformAppFile.readText()
        
        // Remove comments and documentation to focus on functional code
        val codeOnly = content
            .lines()
            .filterNot { it.trim().startsWith("//") }
            .filterNot { it.trim().startsWith("*") }
            .joinToString("\n")
        
        // Verify no "Solicitation" appears in functional code
        assertFalse(
            codeOnly.contains("Solicitation", ignoreCase = false),
            "File should not contain 'Solicitation' in functional code"
        )
        
        assertFalse(
            codeOnly.contains("solicitation", ignoreCase = false),
            "File should not contain 'solicitation' in functional code"
        )
    }
    
    /**
     * Test that CeapPlatformApp has required CDK imports.
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    fun `CeapPlatformApp should have required CDK imports`() {
        val ceapPlatformAppFile = File(projectRoot, ceapPlatformAppPath)
        val content = ceapPlatformAppFile.readText()
        
        val requiredImports = listOf(
            "import software.amazon.awscdk.App",
            "import software.amazon.awscdk.Environment",
            "import software.amazon.awscdk.StackProps"
        )
        
        requiredImports.forEach { import ->
            assertTrue(
                content.contains(import),
                "File should contain import: $import"
            )
        }
    }
    
    /**
     * Test that CeapPlatformApp imports stack classes.
     * 
     * **Validates: Requirements 1.1**
     */
    @Test
    fun `CeapPlatformApp should import stack classes`() {
        val ceapPlatformAppFile = File(projectRoot, ceapPlatformAppPath)
        val content = ceapPlatformAppFile.readText()
        
        // The file may use wildcard import or individual imports
        val hasWildcardImport = content.contains("import com.ceap.infrastructure.stacks.*")
        
        if (hasWildcardImport) {
            // If using wildcard import, just verify it exists
            assertTrue(
                hasWildcardImport,
                "File should contain wildcard import: import com.ceap.infrastructure.stacks.*"
            )
        } else {
            // If using individual imports, verify each one
            val stackImports = listOf(
                "import com.ceap.infrastructure.stacks.DatabaseStack",
                "import com.ceap.infrastructure.stacks.EtlWorkflowStack",
                "import com.ceap.infrastructure.stacks.FilterWorkflowStack",
                "import com.ceap.infrastructure.stacks.ScoreWorkflowStack",
                "import com.ceap.infrastructure.stacks.StoreWorkflowStack",
                "import com.ceap.infrastructure.stacks.ReactiveWorkflowStack",
                "import com.ceap.infrastructure.stacks.OrchestrationStack"
            )
            
            stackImports.forEach { import ->
                assertTrue(
                    content.contains(import),
                    "File should contain import: $import"
                )
            }
        }
    }
}
