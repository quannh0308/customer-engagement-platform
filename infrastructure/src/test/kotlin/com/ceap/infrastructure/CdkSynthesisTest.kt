package com.ceap.infrastructure

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * CDK Synthesis verification tests.
 * 
 * **Validates: Requirements 14.1**
 * 
 * These tests verify that:
 * 1. CDK synthesis completes successfully without compilation errors
 * 2. CloudFormation templates are generated with correct CEAP naming
 * 3. No legacy "solicitation" terminology remains in generated templates
 * 
 * Note: These tests assume that `cdk synth` has been run successfully
 * and the cdk.out directory contains the generated templates.
 */
class CdkSynthesisTest {
    
    private val projectRoot = File(System.getProperty("user.dir"))
    
    // CDK output directory - adjust path based on where tests are run from
    private val cdkOutDir = when {
        File(projectRoot, "cdk.out.consolidated").exists() -> File(projectRoot, "cdk.out.consolidated")
        File(projectRoot, "cdk.out").exists() -> File(projectRoot, "cdk.out")
        else -> File(projectRoot, "infrastructure/cdk.out")
    }
    
    /**
     * Test that CDK synthesis output directory exists.
     * 
     * **Validates: Requirements 14.1**
     * 
     * This verifies that `cdk synth` has been run and generated output.
     */
    @Test
    fun `CDK output directory should exist`() {
        assertTrue(
            cdkOutDir.exists(),
            "CDK output directory should exist at ${cdkOutDir.absolutePath}. " +
            "Run 'cdk synth' from the infrastructure directory first."
        )
        assertTrue(
            cdkOutDir.isDirectory,
            "CDK output path should be a directory"
        )
    }
    
    /**
     * Test that all expected CloudFormation templates are generated with CEAP naming.
     * 
     * **Validates: Requirements 14.1, 2.1-2.7**
     * 
     * Verifies that all stack templates use the CEAP prefix instead of
     * the legacy Solicitation prefix.
     */
    @Test
    fun `all CloudFormation templates should use CEAP naming`() {
        val expectedTemplates = listOf(
            "CeapDatabase-dev.template.json",
            "CeapEtlWorkflow-dev.template.json",
            "CeapFilterWorkflow-dev.template.json",
            "CeapScoreWorkflow-dev.template.json",
            "CeapStoreWorkflow-dev.template.json",
            "CeapReactiveWorkflow-dev.template.json",
            "CeapOrchestration-dev.template.json"
        )
        
        expectedTemplates.forEach { templateName ->
            val templateFile = File(cdkOutDir, templateName)
            assertTrue(
                templateFile.exists(),
                "CloudFormation template should exist: $templateName"
            )
            assertTrue(
                templateFile.isFile,
                "CloudFormation template should be a file: $templateName"
            )
        }
    }
    
    /**
     * Test that no legacy Solicitation templates exist.
     * 
     * **Validates: Requirements 14.1, 1.2**
     * 
     * Verifies that no templates with the old "Solicitation" naming remain.
     */
    @Test
    fun `no legacy Solicitation templates should exist`() {
        val legacyTemplates = listOf(
            "SolicitationDatabase-dev.template.json",
            "SolicitationEtlWorkflow-dev.template.json",
            "SolicitationFilterWorkflow-dev.template.json",
            "SolicitationScoreWorkflow-dev.template.json",
            "SolicitationStoreWorkflow-dev.template.json",
            "SolicitationReactiveWorkflow-dev.template.json",
            "SolicitationOrchestration-dev.template.json"
        )
        
        legacyTemplates.forEach { templateName ->
            val templateFile = File(cdkOutDir, templateName)
            assertFalse(
                templateFile.exists(),
                "Legacy Solicitation template should not exist: $templateName"
            )
        }
    }
    
    /**
     * Test that generated templates do not contain legacy "solicitation" terminology.
     * 
     * **Validates: Requirements 14.1, 1.2, 3.2, 3.3, 15.1, 15.2, 15.3**
     * 
     * Scans all generated CloudFormation templates to ensure no legacy
     * terminology remains in the infrastructure definitions.
     */
    @Test
    fun `generated templates should not contain legacy solicitation terminology`() {
        val templateFiles = cdkOutDir.listFiles { file ->
            file.name.endsWith(".template.json")
        } ?: emptyArray()
        
        assertTrue(
            templateFiles.isNotEmpty(),
            "Should find at least one CloudFormation template in ${cdkOutDir.absolutePath}"
        )
        
        templateFiles.forEach { templateFile ->
            val content = templateFile.readText()
            
            // Check for "solicitation" (case-insensitive)
            val solicitationMatches = Regex("solicitation", RegexOption.IGNORE_CASE)
                .findAll(content)
                .toList()
            
            assertTrue(
                solicitationMatches.isEmpty(),
                "Template ${templateFile.name} should not contain 'solicitation' terminology. " +
                "Found ${solicitationMatches.size} occurrences."
            )
        }
    }
    
    /**
     * Test that reactive workflow template uses correct EventBridge configuration.
     * 
     * **Validates: Requirements 14.1, 5.2, 6.1, 7.1**
     * 
     * Verifies that the reactive workflow template contains:
     * - EventBridge rule name: ceap-customer-events-dev
     * - EventBridge source: ceap.customer-events
     * - DynamoDB table name: ceap-event-deduplication-dev
     */
    @Test
    fun `reactive workflow template should use CEAP EventBridge configuration`() {
        val reactiveTemplate = File(cdkOutDir, "CeapReactiveWorkflow-dev.template.json")
        assertTrue(
            reactiveTemplate.exists(),
            "Reactive workflow template should exist"
        )
        
        val content = reactiveTemplate.readText()
        
        // Verify EventBridge rule name
        assertTrue(
            content.contains("ceap-customer-events-dev"),
            "Template should contain EventBridge rule name: ceap-customer-events-dev"
        )
        
        // Verify EventBridge source
        assertTrue(
            content.contains("ceap.customer-events"),
            "Template should contain EventBridge source: ceap.customer-events"
        )
        
        // Verify DynamoDB table name
        assertTrue(
            content.contains("ceap-event-deduplication-dev"),
            "Template should contain DynamoDB table name: ceap-event-deduplication-dev"
        )
    }
    
    /**
     * Test that orchestration template uses correct state machine name.
     * 
     * **Validates: Requirements 14.1, 4.1, 5.1**
     * 
     * Verifies that the orchestration template contains:
     * - State machine name: CeapBatchIngestion-dev
     * - EventBridge schedule rule name: CeapBatchIngestion-dev
     */
    @Test
    fun `orchestration template should use CEAP state machine naming`() {
        val orchestrationTemplate = File(cdkOutDir, "CeapOrchestration-dev.template.json")
        assertTrue(
            orchestrationTemplate.exists(),
            "Orchestration template should exist"
        )
        
        val content = orchestrationTemplate.readText()
        
        // Verify state machine name
        assertTrue(
            content.contains("CeapBatchIngestion-dev"),
            "Template should contain state machine name: CeapBatchIngestion-dev"
        )
    }
    
    /**
     * Test that database template uses CEAP naming.
     * 
     * **Validates: Requirements 14.1, 2.1**
     * 
     * Verifies that the database template uses CEAP branding in exports
     * and resource references.
     */
    @Test
    fun `database template should use CEAP naming in exports`() {
        val databaseTemplate = File(cdkOutDir, "CeapDatabase-dev.template.json")
        assertTrue(
            databaseTemplate.exists(),
            "Database template should exist"
        )
        
        val content = databaseTemplate.readText()
        
        // Verify CEAP naming in exports
        assertTrue(
            content.contains("CeapDatabase-dev-CandidatesTableName") ||
            content.contains("CeapDatabase-dev-"),
            "Template should contain CEAP-prefixed exports"
        )
        
        // Verify no legacy Solicitation naming
        assertFalse(
            content.contains("SolicitationDatabase"),
            "Template should not contain legacy SolicitationDatabase naming"
        )
    }
    
    /**
     * Test that all workflow templates reference CEAP database exports.
     * 
     * **Validates: Requirements 14.1, 2.1-2.6**
     * 
     * Verifies that workflow stacks correctly import from the renamed
     * CEAP database stack.
     */
    @Test
    fun `workflow templates should reference CEAP database exports`() {
        val workflowTemplates = listOf(
            "CeapEtlWorkflow-dev.template.json",
            "CeapFilterWorkflow-dev.template.json",
            "CeapScoreWorkflow-dev.template.json",
            "CeapStoreWorkflow-dev.template.json",
            "CeapReactiveWorkflow-dev.template.json"
        )
        
        workflowTemplates.forEach { templateName ->
            val templateFile = File(cdkOutDir, templateName)
            assertTrue(
                templateFile.exists(),
                "Workflow template should exist: $templateName"
            )
            
            val content = templateFile.readText()
            
            // Verify imports from CEAP database stack
            assertTrue(
                content.contains("CeapDatabase-dev:ExportsOutput"),
                "Template $templateName should import from CeapDatabase-dev stack"
            )
            
            // Verify no imports from legacy Solicitation database stack
            assertFalse(
                content.contains("SolicitationDatabase-dev:ExportsOutput"),
                "Template $templateName should not import from legacy SolicitationDatabase-dev stack"
            )
        }
    }
}
