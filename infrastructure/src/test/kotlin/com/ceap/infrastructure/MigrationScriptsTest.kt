package com.ceap.infrastructure

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Unit Tests for Migration Scripts
 * 
 * These tests verify the deployment, validation, and rollback scripts
 * for the infrastructure consolidation process.
 * 
 * **Validates: Requirements 5.1, 5.3, 5.5**
 */
class MigrationScriptsTest {
    
    private val deploymentScript = File("deploy-consolidated.sh")
    private val validationScript = File("validate-resources.sh")
    private val rollbackScript = File("rollback-consolidated.sh")
    
    // ========================================
    // Deployment Script Tests
    // ========================================
    
    @Test
    fun `deployment script should exist and be executable`() {
        deploymentScript.exists() shouldBe true
        deploymentScript.canExecute() shouldBe true
    }
    
    @Test
    fun `deployment script should have correct shebang`() {
        val content = deploymentScript.readText()
        content.startsWith("#!/bin/bash") shouldBe true
    }
    
    @Test
    fun `deployment script should check AWS credentials`() {
        val content = deploymentScript.readText()
        content shouldContain "aws sts get-caller-identity"
        content shouldContain "AWS credentials"
    }
    
    @Test
    fun `deployment script should deploy database stack first`() {
        val content = deploymentScript.readText()
        
        // Should deploy database before application stacks
        val dbDeployIndex = content.indexOf("CeapDatabase-")
        val dataPlatformIndex = content.indexOf("CeapDataPlatform-")
        val servingAPIIndex = content.indexOf("CeapServingAPI-")
        
        dbDeployIndex shouldNotBe -1
        dataPlatformIndex shouldNotBe -1
        servingAPIIndex shouldNotBe -1
        
        // Database should come before application stacks
        (dbDeployIndex < dataPlatformIndex) shouldBe true
        (dbDeployIndex < servingAPIIndex) shouldBe true
    }
    
    @Test
    fun `deployment script should deploy application stacks in parallel`() {
        val content = deploymentScript.readText()
        
        // Should use concurrency flag for parallel deployment
        content shouldContain "--concurrency"
        content shouldContain "CeapDataPlatform-"
        content shouldContain "CeapServingAPI-"
    }
    
    @Test
    fun `deployment script should build project before deployment`() {
        val content = deploymentScript.readText()
        content shouldContain "gradlew build"
    }
    
    @Test
    fun `deployment script should synthesize CDK templates`() {
        val content = deploymentScript.readText()
        content shouldContain "cdk synth"
        content shouldContain "cdk.out.consolidated"
    }
    
    @Test
    fun `deployment script should handle errors with set -e`() {
        val content = deploymentScript.readText()
        content shouldContain "set -e"
    }
    
    @Test
    fun `deployment script should accept environment parameter`() {
        val content = deploymentScript.readText()
        content shouldContain "ENV_NAME"
        content shouldContain "\${1:-dev}"
    }
    
    @Test
    fun `deployment script should provide deployment status output`() {
        val content = deploymentScript.readText()
        content shouldContain "Deployment Complete"
        content shouldContain "Deployed stacks"
    }
    
    @Test
    fun `deployment script should not contain hardcoded AWS account numbers`() {
        val content = deploymentScript.readText()
        
        // Should not contain any 12-digit AWS account numbers
        val accountPattern = Regex("\\b\\d{12}\\b")
        accountPattern.containsMatchIn(content) shouldBe false
    }
    
    // ========================================
    // Validation Script Tests
    // ========================================
    
    @Test
    fun `validation script should exist and be executable`() {
        validationScript.exists() shouldBe true
        validationScript.canExecute() shouldBe true
    }
    
    @Test
    fun `validation script should have correct shebang`() {
        val content = validationScript.readText()
        content.startsWith("#!/bin/bash") shouldBe true
    }
    
    @Test
    fun `validation script should check AWS credentials`() {
        val content = validationScript.readText()
        content shouldContain "aws sts get-caller-identity"
    }
    
    @Test
    fun `validation script should count Lambda functions`() {
        val content = validationScript.readText()
        content shouldContain "AWS::Lambda::Function"
        content shouldContain "TOTAL_NEW_LAMBDAS"
    }
    
    @Test
    fun `validation script should count Step Functions`() {
        val content = validationScript.readText()
        content shouldContain "AWS::StepFunctions::StateMachine"
        content shouldContain "TOTAL_NEW_STATE_MACHINES"
    }
    
    @Test
    fun `validation script should count DynamoDB tables`() {
        val content = validationScript.readText()
        content shouldContain "AWS::DynamoDB::Table"
        content shouldContain "TOTAL_NEW_TABLES"
    }
    
    @Test
    fun `validation script should count EventBridge rules`() {
        val content = validationScript.readText()
        content shouldContain "AWS::Events::Rule"
        content shouldContain "TOTAL_NEW_RULES"
    }
    
    @Test
    fun `validation script should count IAM roles`() {
        val content = validationScript.readText()
        content shouldContain "AWS::IAM::Role"
        content shouldContain "TOTAL_NEW_ROLES"
    }
    
    @Test
    fun `validation script should compare old and new resource counts`() {
        val content = validationScript.readText()
        content shouldContain "TOTAL_OLD_LAMBDAS"
        content shouldContain "TOTAL_OLD_STATE_MACHINES"
        content shouldContain "VALIDATION_PASSED"
    }
    
    @Test
    fun `validation script should generate inventory report`() {
        val content = validationScript.readText()
        content shouldContain "REPORT_FILE"
        content shouldContain "resource-validation-report"
        content shouldContain "get_stack_resources"
    }
    
    @Test
    fun `validation script should fail if resources are missing`() {
        val content = validationScript.readText()
        content shouldContain "MISSING"
        content shouldContain "Validation failed"
        content shouldContain "exit 1"
    }
    
    @Test
    fun `validation script should succeed if resources match`() {
        val content = validationScript.readText()
        content shouldContain "All validation checks passed"
        content shouldContain "Safe to proceed"
        content shouldContain "exit 0"
    }
    
    @Test
    fun `validation script should handle non-existent old stacks`() {
        val content = validationScript.readText()
        content shouldContain "OLD_STACKS_EXIST"
        content shouldContain "already cleaned up or never existed"
    }
    
    @Test
    fun `validation script should check all 3 new stacks`() {
        val content = validationScript.readText()
        content shouldContain "CeapDatabase-"
        content shouldContain "CeapDataPlatform-"
        content shouldContain "CeapServingAPI-"
    }
    
    @Test
    fun `validation script should not contain hardcoded AWS account numbers`() {
        val content = validationScript.readText()
        
        // Should not contain any 12-digit AWS account numbers
        val accountPattern = Regex("\\b\\d{12}\\b")
        accountPattern.containsMatchIn(content) shouldBe false
    }
    
    // ========================================
    // Rollback Script Tests
    // ========================================
    
    @Test
    fun `rollback script should exist and be executable`() {
        rollbackScript.exists() shouldBe true
        rollbackScript.canExecute() shouldBe true
    }
    
    @Test
    fun `rollback script should have correct shebang`() {
        val content = rollbackScript.readText()
        content.startsWith("#!/bin/bash") shouldBe true
    }
    
    @Test
    fun `rollback script should check AWS credentials`() {
        val content = rollbackScript.readText()
        content shouldContain "aws sts get-caller-identity"
    }
    
    @Test
    fun `rollback script should require user confirmation`() {
        val content = rollbackScript.readText()
        content shouldContain "Are you sure"
        content shouldContain "read -p"
        content shouldContain "CONFIRM"
    }
    
    @Test
    fun `rollback script should delete CeapServingAPI-dev first`() {
        val content = rollbackScript.readText()
        
        // Should delete ServingAPI before DataPlatform (no dependencies)
        val servingAPIDeleteIndex = content.indexOf("Deleting CeapServingAPI-")
        val dataPlatformDeleteIndex = content.indexOf("Deleting CeapDataPlatform-")
        
        servingAPIDeleteIndex shouldNotBe -1
        dataPlatformDeleteIndex shouldNotBe -1
        (servingAPIDeleteIndex < dataPlatformDeleteIndex) shouldBe true
    }
    
    @Test
    fun `rollback script should delete both application stacks`() {
        val content = rollbackScript.readText()
        content shouldContain "delete-stack --stack-name \"CeapDataPlatform-"
        content shouldContain "delete-stack --stack-name \"CeapServingAPI-"
    }
    
    @Test
    fun `rollback script should wait for stack deletion`() {
        val content = rollbackScript.readText()
        content shouldContain "wait stack-delete-complete"
    }
    
    @Test
    fun `rollback script should retain database stack`() {
        val content = rollbackScript.readText()
        content shouldContain "will be retained"
    }
    
    @Test
    fun `rollback script should monitor rollback status`() {
        val content = rollbackScript.readText()
        content shouldContain "Rollback Status Monitoring"
        content shouldContain "ROLLBACK_SUCCESS"
    }
    
    @Test
    fun `rollback script should verify stacks are deleted`() {
        val content = rollbackScript.readText()
        content shouldContain "describe-stacks"
        content shouldContain "successfully deleted"
    }
    
    @Test
    fun `rollback script should handle errors gracefully`() {
        val content = rollbackScript.readText()
        content shouldContain "set -e"
        content shouldContain "exit 1"
    }
    
    @Test
    fun `rollback script should provide next steps guidance`() {
        val content = rollbackScript.readText()
        content shouldContain "Next steps"
        content shouldContain "Verify"
    }
    
    @Test
    fun `rollback script should not contain hardcoded AWS account numbers`() {
        val content = rollbackScript.readText()
        
        // Should not contain any 12-digit AWS account numbers
        val accountPattern = Regex("\\b\\d{12}\\b")
        accountPattern.containsMatchIn(content) shouldBe false
    }
    
    // ========================================
    // Script Integration Tests
    // ========================================
    
    @Test
    fun `all scripts should use consistent environment variable naming`() {
        val deployContent = deploymentScript.readText()
        val validateContent = validationScript.readText()
        val rollbackContent = rollbackScript.readText()
        
        // All should use ENV_NAME
        deployContent shouldContain "ENV_NAME"
        validateContent shouldContain "ENV_NAME"
        rollbackContent shouldContain "ENV_NAME"
    }
    
    @Test
    fun `all scripts should have error handling`() {
        val deployContent = deploymentScript.readText()
        val validateContent = validationScript.readText()
        val rollbackContent = rollbackScript.readText()
        
        // All should use set -e for error handling
        deployContent shouldContain "set -e"
        validateContent shouldContain "set -e"
        rollbackContent shouldContain "set -e"
    }
    
    @Test
    fun `all scripts should check AWS credentials before proceeding`() {
        val deployContent = deploymentScript.readText()
        val validateContent = validationScript.readText()
        val rollbackContent = rollbackScript.readText()
        
        // All should check credentials
        deployContent shouldContain "aws sts get-caller-identity"
        validateContent shouldContain "aws sts get-caller-identity"
        rollbackContent shouldContain "aws sts get-caller-identity"
    }
    
    @Test
    fun `scripts should reference correct stack names`() {
        val deployContent = deploymentScript.readText()
        val validateContent = validationScript.readText()
        val rollbackContent = rollbackScript.readText()
        
        // All should reference the 3 consolidated stacks
        listOf(deployContent, validateContent, rollbackContent).forEach { content ->
            content shouldContain "CeapDatabase-"
            content shouldContain "CeapDataPlatform-"
            content shouldContain "CeapServingAPI-"
        }
    }
    
    @Test
    fun `scripts should not reference old stack names in deployment logic`() {
        val deployContent = deploymentScript.readText()
        
        // Deployment script should not try to deploy old stacks
        deployContent shouldNotContain "CeapEtlWorkflow-"
        deployContent shouldNotContain "CeapFilterWorkflow-"
        deployContent shouldNotContain "CeapScoreWorkflow-"
        deployContent shouldNotContain "CeapStoreWorkflow-"
        deployContent shouldNotContain "CeapOrchestration-"
    }
}
