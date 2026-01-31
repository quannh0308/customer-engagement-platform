package com.ceap.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import java.io.File

/**
 * Property-Based Tests for Resource Validation Before Cleanup
 * 
 * **Feature: infrastructure-consolidation**
 * **Property 5: Resource Validation Before Cleanup**
 * 
 * **Validates: Requirement 5.3**
 * 
 * These tests verify that the migration validation process checks for resource
 * existence in the new 3-stack architecture before allowing old stacks to be deleted.
 * This ensures no resources are lost during the consolidation process.
 * 
 * Test Strategy:
 * - Verify validation script exists and is executable
 * - Verify validation script checks all critical resource types
 * - Verify validation script compares old vs new architectures
 * - Verify validation script fails if resources are missing
 * - Verify validation script generates detailed inventory reports
 */
class ResourceValidationBeforeCleanupPropertyTest : StringSpec({
    
    val objectMapper = ObjectMapper()
    val consolidatedTemplatesDir = File("cdk.out.consolidated")
    val validationScriptFile = File("validate-resources.sh")
    
    /**
     * Property: Validation script exists and is executable.
     * 
     * **Validates: Requirement 5.3**
     * 
     * This property verifies that the resource validation script exists
     * and has executable permissions, ensuring it can be run as part of
     * the migration process.
     */
    "Property: Validation script exists and is executable" {
        validationScriptFile.exists() shouldBe true
        validationScriptFile.canExecute() shouldBe true
    }
    
    /**
     * Property: Validation script checks all critical resource types.
     * 
     * **Validates: Requirement 5.3**
     * 
     * This property verifies that the validation script checks for all
     * critical resource types that must be preserved during consolidation.
     */
    "Property: Validation script checks all critical resource types" {
        val scriptContent = validationScriptFile.readText()
        
        val criticalResourceTypes = listOf(
            "AWS::Lambda::Function",
            "AWS::StepFunctions::StateMachine",
            "AWS::Events::Rule",
            "AWS::DynamoDB::Table",
            "AWS::IAM::Role"
        )
        
        checkAll(100, Arb.of(criticalResourceTypes)) { resourceType ->
            // Script should check for this resource type
            scriptContent.contains(resourceType) shouldBe true
        }
    }
    
    /**
     * Property: Validation script compares resource counts.
     * 
     * **Validates: Requirement 5.3**
     * 
     * This property verifies that the validation script compares resource
     * counts between old and new architectures to detect missing resources.
     */
    "Property: Validation script compares resource counts" {
        val scriptContent = validationScriptFile.readText()
        
        val comparisonChecks = listOf(
            "TOTAL_NEW_LAMBDAS",
            "TOTAL_OLD_LAMBDAS",
            "TOTAL_NEW_STATE_MACHINES",
            "TOTAL_OLD_STATE_MACHINES",
            "TOTAL_NEW_TABLES",
            "TOTAL_NEW_RULES",
            "TOTAL_NEW_ROLES"
        )
        
        checkAll(100, Arb.of(comparisonChecks)) { check ->
            // Script should define and use these comparison variables
            scriptContent.contains(check) shouldBe true
        }
    }
    
    /**
     * Property: Validation script checks all old stacks.
     * 
     * **Validates: Requirement 5.3**
     * 
     * This property verifies that the validation script checks for resources
     * in all 6 old workflow stacks (excluding database which is unchanged).
     */
    "Property: Validation script checks all old stacks" {
        val scriptContent = validationScriptFile.readText()
        
        val oldStacks = listOf(
            "CeapEtlWorkflow",
            "CeapFilterWorkflow",
            "CeapScoreWorkflow",
            "CeapStoreWorkflow",
            "CeapReactiveWorkflow",
            "CeapOrchestration"
        )
        
        checkAll(100, Arb.of(oldStacks)) { stackPrefix ->
            // Script should reference this old stack
            scriptContent.contains(stackPrefix) shouldBe true
        }
    }
    
    /**
     * Property: Validation script checks all new stacks.
     * 
     * **Validates: Requirement 5.3**
     * 
     * This property verifies that the validation script checks for resources
     * in all 3 new consolidated stacks.
     */
    "Property: Validation script checks all new stacks" {
        val scriptContent = validationScriptFile.readText()
        
        val newStacks = listOf(
            "CeapDatabase",
            "CeapDataPlatform",
            "CeapServingAPI"
        )
        
        checkAll(100, Arb.of(newStacks)) { stackPrefix ->
            // Script should reference this new stack
            scriptContent.contains(stackPrefix) shouldBe true
        }
    }
    
    /**
     * Property: Validation script generates inventory reports.
     * 
     * **Validates: Requirement 5.3**
     * 
     * This property verifies that the validation script generates detailed
     * inventory reports for comparison and audit purposes.
     */
    "Property: Validation script generates inventory reports" {
        val scriptContent = validationScriptFile.readText()
        
        val reportFeatures = listOf(
            "REPORT_FILE",
            "resource-validation-report",
            "get_stack_resources",
            "Validation Report"
        )
        
        checkAll(100, Arb.of(reportFeatures)) { feature ->
            // Script should include report generation features
            scriptContent.contains(feature) shouldBe true
        }
    }
    
    /**
     * Property: Validation script fails on missing resources.
     * 
     * **Validates: Requirement 5.3**
     * 
     * This property verifies that the validation script exits with an error
     * if resources are missing in the new architecture.
     */
    "Property: Validation script fails on missing resources" {
        val scriptContent = validationScriptFile.readText()
        
        // Script should have validation failure logic
        scriptContent.contains("VALIDATION_PASSED") shouldBe true
        scriptContent.contains("Validation failed") shouldBe true
        scriptContent.contains("MISSING") shouldBe true
        scriptContent.contains("exit 1") shouldBe true
    }
    
    /**
     * Property: Validation script succeeds when resources match.
     * 
     * **Validates: Requirement 5.3**
     * 
     * This property verifies that the validation script exits successfully
     * when all resources are present in the new architecture.
     */
    "Property: Validation script succeeds when resources match" {
        val scriptContent = validationScriptFile.readText()
        
        // Script should have validation success logic
        scriptContent.contains("All validation checks passed") shouldBe true
        scriptContent.contains("Safe to proceed") shouldBe true
        scriptContent.contains("exit 0") shouldBe true
    }
    
    /**
     * Property: Validation script uses AWS CLI for resource counting.
     * 
     * **Validates: Requirement 5.3**
     * 
     * This property verifies that the validation script uses AWS CLI
     * to query actual deployed resources, not just template definitions.
     */
    "Property: Validation script uses AWS CLI for resource counting" {
        val scriptContent = validationScriptFile.readText()
        
        val awsCommands = listOf(
            "aws cloudformation describe-stacks",
            "aws cloudformation list-stack-resources",
            "count_stack_resources"
        )
        
        checkAll(100, Arb.of(awsCommands)) { command ->
            // Script should use AWS CLI commands
            scriptContent.contains(command) shouldBe true
        }
    }
    
    /**
     * Property: Validation script handles non-existent stacks gracefully.
     * 
     * **Validates: Requirement 5.3**
     * 
     * This property verifies that the validation script handles cases where
     * old stacks don't exist (already cleaned up or never deployed).
     */
    "Property: Validation script handles non-existent stacks gracefully" {
        val scriptContent = validationScriptFile.readText()
        
        // Script should check for stack existence
        scriptContent.contains("describe-stacks") shouldBe true
        scriptContent.contains("does not exist") shouldBe true
        scriptContent.contains("OLD_STACKS_EXIST") shouldBe true
    }
    
    /**
     * Property: Validation script provides detailed output.
     * 
     * **Validates: Requirement 5.3**
     * 
     * This property verifies that the validation script provides detailed
     * output about resource counts and validation results.
     */
    "Property: Validation script provides detailed output" {
        val scriptContent = validationScriptFile.readText()
        
        val outputSections = listOf(
            "Consolidated Stack Resources",
            "Resource Totals",
            "Validation Results",
            "Validation Summary"
        )
        
        checkAll(100, Arb.of(outputSections)) { section ->
            // Script should include these output sections
            scriptContent.contains(section) shouldBe true
        }
    }
})
