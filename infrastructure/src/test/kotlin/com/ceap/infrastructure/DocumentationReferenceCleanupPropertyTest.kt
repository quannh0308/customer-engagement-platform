package com.ceap.infrastructure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import java.io.File

/**
 * Property-Based Tests for Documentation Reference Cleanup
 * 
 * **Feature: infrastructure-consolidation**
 * **Property 6: Documentation Reference Cleanup**
 * 
 * **Validates: Requirement 6.4**
 * 
 * These tests verify that all documentation has been updated to reference
 * the new 3-stack architecture instead of the old 7-stack architecture.
 * 
 * Test Strategy:
 * - Verify main documentation files don't reference old stack names
 * - Verify deployment guides reference new stack names
 * - Verify configuration guides are updated
 * - Allow historical references in spec files (they document the migration)
 */
class DocumentationReferenceCleanupPropertyTest : StringSpec({
    
    val docsDir = File("../docs")
    val infrastructureDocsDir = File("../infrastructure")
    
    /**
     * Property: Main documentation files don't reference old 7-stack names.
     * 
     * **Validates: Requirement 6.4**
     * 
     * This property verifies that primary documentation files have been
     * updated to use the new 3-stack architecture naming.
     */
    "Property: Main documentation files don't reference old 7-stack names" {
        val mainDocFiles = listOf(
            File(docsDir, "INFRASTRUCTURE-INVENTORY.md"),
            File(docsDir, "DEPLOYMENT-GUIDE.md"),
            File(docsDir, "MULTI-TENANCY-GUIDE.md")
        )
        
        val oldStackNames = listOf(
            "CeapEtlWorkflow-",
            "CeapFilterWorkflow-",
            "CeapScoreWorkflow-",
            "CeapStoreWorkflow-",
            "CeapOrchestration-"
            // Note: CeapReactiveWorkflow is allowed in historical context
        )
        
        checkAll(100, Arb.of(mainDocFiles)) { docFile ->
            if (docFile.exists()) {
                val content = docFile.readText()
                
                // Check for old stack references (excluding historical mentions)
                oldStackNames.forEach { oldStackName ->
                    // Count occurrences
                    val count = content.split(oldStackName).size - 1
                    
                    // Should have minimal or no references (some historical context is OK)
                    // We're checking that it's not the primary architecture being documented
                    val hasMinimalReferences = count <= 2  // Allow up to 2 historical mentions
                    hasMinimalReferences shouldBe true
                }
            }
        }
    }
    
    /**
     * Property: Documentation references new 3-stack architecture.
     * 
     * **Validates: Requirement 6.4**
     * 
     * This property verifies that documentation actively references
     * the new consolidated stack names.
     */
    "Property: Documentation references new 3-stack architecture" {
        val mainDocFiles = listOf(
            File(docsDir, "INFRASTRUCTURE-INVENTORY.md"),
            File(docsDir, "DEPLOYMENT-GUIDE.md")
        )
        
        val newStackNames = listOf(
            "CeapDatabase-",
            "CeapDataPlatform-",
            "CeapServingAPI-"
        )
        
        checkAll(100, Arb.of(mainDocFiles)) { docFile ->
            if (docFile.exists()) {
                val content = docFile.readText()
                
                // Should reference all 3 new stacks
                newStackNames.forEach { newStackName ->
                    content.contains(newStackName) shouldBe true
                }
            }
        }
    }
    
    /**
     * Property: Documentation mentions 3-stack architecture explicitly.
     * 
     * **Validates: Requirement 6.4**
     * 
     * This property verifies that documentation explicitly mentions
     * the consolidated 3-stack architecture.
     */
    "Property: Documentation mentions 3-stack architecture explicitly" {
        val inventoryFile = File(docsDir, "INFRASTRUCTURE-INVENTORY.md")
        
        if (inventoryFile.exists()) {
            val content = inventoryFile.readText()
            
            // Should mention "3" or "three" stacks
            val mentions3Stacks = content.contains("3 stack") || 
                                 content.contains("3-stack") ||
                                 content.contains("three stack")
            
            mentions3Stacks shouldBe true
            
            // Should mention consolidation
            val mentionsConsolidation = content.contains("consolidat") ||
                                       content.contains("Consolidat")
            
            mentionsConsolidation shouldBe true
        }
    }
    
    /**
     * Property: Deployment guide references new deployment scripts.
     * 
     * **Validates: Requirement 6.4**
     * 
     * This property verifies that the deployment guide references
     * the new consolidated deployment scripts.
     */
    "Property: Deployment guide references new deployment scripts" {
        val deploymentGuide = File(docsDir, "DEPLOYMENT-GUIDE.md")
        
        if (deploymentGuide.exists()) {
            val content = deploymentGuide.readText()
            
            // Should reference new deployment script
            content.contains("deploy-consolidated.sh") shouldBe true
            
            // Should reference validation script
            content.contains("validate-resources.sh") shouldBe true
            
            // Should reference rollback script
            content.contains("rollback-consolidated.sh") shouldBe true
        }
    }
    
    /**
     * Property: Configuration guides are consistent with 3-stack architecture.
     * 
     * **Validates: Requirement 6.4**
     * 
     * This property verifies that configuration guides don't have
     * outdated references to the old 7-stack architecture.
     */
    "Property: Configuration guides are consistent with 3-stack architecture" {
        val configGuides = listOf(
            File(docsDir, "integrations/SCORING-CONFIGURATION-GUIDE.md"),
            File(docsDir, "integrations/NOTIFICATION-CONFIGURATION-GUIDE.md"),
            File(docsDir, "integrations/STORAGE-CONFIGURATION-GUIDE.md"),
            File(docsDir, "MULTI-TENANCY-GUIDE.md")
        )
        
        checkAll(100, Arb.of(configGuides)) { guideFile ->
            if (guideFile.exists()) {
                val content = guideFile.readText()
                
                // If it mentions stacks, should reference 3-stack architecture
                if (content.contains("stack") || content.contains("Stack")) {
                    // Should not heavily reference old individual workflow stacks
                    val oldReferences = listOf(
                        "CeapEtlWorkflow-",
                        "CeapFilterWorkflow-",
                        "CeapScoreWorkflow-",
                        "CeapStoreWorkflow-"
                    )
                    
                    oldReferences.forEach { oldRef ->
                        val count = content.split(oldRef).size - 1
                        // Allow minimal historical mentions
                        (count <= 1) shouldBe true
                    }
                }
            }
        }
    }
    
    /**
     * Property: Infrastructure documentation updated date is recent.
     * 
     * **Validates: Requirement 6.4**
     * 
     * This property verifies that infrastructure documentation
     * has been updated recently (indicating it reflects current state).
     */
    "Property: Infrastructure documentation updated date is recent" {
        val inventoryFile = File(docsDir, "INFRASTRUCTURE-INVENTORY.md")
        
        if (inventoryFile.exists()) {
            val content = inventoryFile.readText()
            
            // Should have "Last Updated" field
            content.contains("Last Updated") shouldBe true
            
            // Should mention 2026 (current year)
            content.contains("2026") shouldBe true
        }
    }
    
    /**
     * Property: Deployment scripts exist and are documented.
     * 
     * **Validates: Requirement 6.4**
     * 
     * This property verifies that the new deployment scripts exist
     * and are referenced in documentation.
     */
    "Property: Deployment scripts exist and are documented" {
        val scripts = listOf(
            "deploy-consolidated.sh",
            "validate-resources.sh",
            "rollback-consolidated.sh"
        )
        
        checkAll(100, Arb.of(scripts)) { scriptName ->
            // Script should exist
            val scriptFile = File(scriptName)
            scriptFile.exists() shouldBe true
            
            // Script should be mentioned in deployment guide
            val deploymentGuide = File(docsDir, "DEPLOYMENT-GUIDE.md")
            if (deploymentGuide.exists()) {
                val guideContent = deploymentGuide.readText()
                guideContent.contains(scriptName) shouldBe true
            }
        }
    }
    
    /**
     * Property: Documentation describes read/write separation.
     * 
     * **Validates: Requirement 6.4**
     * 
     * This property verifies that documentation explains the
     * read/write separation rationale for the 3-stack architecture.
     */
    "Property: Documentation describes read/write separation" {
        val inventoryFile = File(docsDir, "INFRASTRUCTURE-INVENTORY.md")
        
        if (inventoryFile.exists()) {
            val content = inventoryFile.readText()
            
            // Should mention write path
            val mentionsWritePath = content.contains("Write Path") ||
                                   content.contains("write path")
            mentionsWritePath shouldBe true
            
            // Should mention read path
            val mentionsReadPath = content.contains("Read Path") ||
                                  content.contains("read path")
            mentionsReadPath shouldBe true
            
            // Should mention storage layer
            val mentionsStorage = content.contains("Storage Layer") ||
                                 content.contains("storage layer")
            mentionsStorage shouldBe true
        }
    }
    
    /**
     * Property: Documentation includes deployment timing information.
     * 
     * **Validates: Requirement 6.4**
     * 
     * This property verifies that documentation includes deployment
     * timing information for the new architecture.
     */
    "Property: Documentation includes deployment timing information" {
        val docFiles = listOf(
            File(docsDir, "INFRASTRUCTURE-INVENTORY.md"),
            File(docsDir, "DEPLOYMENT-GUIDE.md")
        )
        
        checkAll(100, Arb.of(docFiles)) { docFile ->
            if (docFile.exists()) {
                val content = docFile.readText()
                
                // Should mention deployment time or duration
                val mentionsTiming = content.contains("minute") ||
                                    content.contains("Deployment Time") ||
                                    content.contains("Time:")
                
                mentionsTiming shouldBe true
            }
        }
    }
})
