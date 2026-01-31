package com.ceap.infrastructure.stacks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import software.amazon.awscdk.App
import software.amazon.awscdk.Environment
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.assertions.Template

/**
 * Unit tests for consolidated CloudFormation template structure.
 * 
 * **Validates: Requirements 1.3, 1.4, 7.1, 7.2**
 * 
 * These tests verify that:
 * 1. CeapDataPlatform-dev contains all expected resource types from 5 consolidated stacks
 * 2. CeapServingAPI-dev contains all expected resource types from 1 consolidated stack
 * 3. Stack parameters are correctly defined for cross-stack references
 * 4. Stack outputs are correctly defined for resource exports
 * 
 * This test suite focuses on template structure validation, ensuring that the
 * consolidated templates maintain all resources from the original 7-stack architecture.
 */
class ConsolidatedTemplateStructureTest {
    
    companion object {
        private lateinit var dataPlatformTemplate: Template
        private lateinit var servingAPITemplate: Template
        
        @JvmStatic
        @BeforeAll
        fun setup() {
            val app = App()
            val env = Environment.builder()
                .account("123456789012")
                .region("us-east-1")
                .build()
            
            val stackProps = StackProps.builder()
                .env(env)
                .build()
            
            // Create stacks for testing
            val dataPlatformStack = DataPlatformStack(app, "TestDataPlatform", stackProps, "test")
            val servingAPIStack = ServingAPIStack(app, "TestServingAPI", stackProps, "test")
            
            // Generate CloudFormation templates
            dataPlatformTemplate = Template.fromStack(dataPlatformStack)
            servingAPITemplate = Template.fromStack(servingAPIStack)
        }
    }
    
    // ========================================
    // CeapDataPlatform-dev Template Tests
    // ========================================
    
    /**
     * Test that CeapDataPlatform-dev contains all expected Lambda functions.
     * 
     * Expected Lambda functions (from 5 consolidated stacks):
     * - ETLLambda (from CeapEtlWorkflow-dev)
     * - FilterLambda (from CeapFilterWorkflow-dev)
     * - ScoreLambda (from CeapScoreWorkflow-dev)
     * - StoreLambda (from CeapStoreWorkflow-dev)
     * 
     * Note: CDK may create additional Lambda functions for log retention custom resources.
     * This test verifies that the core Lambda functions exist with preserved logical IDs.
     * 
     * **Validates: Requirements 1.3, 1.5**
     */
    @Test
    fun `DataPlatform stack should contain all expected Lambda functions`() {
        // Verify Lambda functions exist
        val lambdaFunctions = dataPlatformTemplate.findResources("AWS::Lambda::Function")
        assertTrue(lambdaFunctions.size >= 4, 
            "DataPlatform stack should contain at least 4 Lambda functions (ETL, Filter, Score, Store)")
        
        // Verify each Lambda function exists with correct logical ID
        assertTrue(lambdaFunctions.containsKey("ETLLambdaFunction49DD508A"),
            "DataPlatform stack should contain ETLLambda with preserved logical ID")
        assertTrue(lambdaFunctions.containsKey("FilterLambdaFunction29040EBB"),
            "DataPlatform stack should contain FilterLambda with preserved logical ID")
        assertTrue(lambdaFunctions.containsKey("ScoreLambdaFunction04AD330A"),
            "DataPlatform stack should contain ScoreLambda with preserved logical ID")
        assertTrue(lambdaFunctions.containsKey("StoreLambdaFunction7FC1576D"),
            "DataPlatform stack should contain StoreLambda with preserved logical ID")
    }
    
    /**
     * Test that CeapDataPlatform-dev contains all expected Step Functions state machines.
     * 
     * Expected state machines (from CeapOrchestration-dev):
     * - BatchIngestionWorkflow
     * 
     * **Validates: Requirements 1.3, 1.5**
     */
    @Test
    fun `DataPlatform stack should contain all expected Step Functions state machines`() {
        // Verify Step Functions state machine count
        val stateMachines = dataPlatformTemplate.findResources("AWS::StepFunctions::StateMachine")
        assertEquals(1, stateMachines.size,
            "DataPlatform stack should contain exactly 1 Step Functions state machine (BatchIngestionWorkflow)")
        
        // Verify BatchIngestionWorkflow exists with correct logical ID
        assertTrue(stateMachines.containsKey("BatchIngestionWorkflow10186577"),
            "DataPlatform stack should contain BatchIngestionWorkflow with preserved logical ID")
    }
    
    /**
     * Test that CeapDataPlatform-dev contains all expected EventBridge rules.
     * 
     * Expected EventBridge rules (from CeapOrchestration-dev):
     * - BatchIngestionSchedule (daily at 2 AM UTC)
     * 
     * **Validates: Requirements 1.3, 1.5**
     */
    @Test
    fun `DataPlatform stack should contain all expected EventBridge rules`() {
        // Verify EventBridge rule count
        val eventRules = dataPlatformTemplate.findResources("AWS::Events::Rule")
        assertEquals(1, eventRules.size,
            "DataPlatform stack should contain exactly 1 EventBridge rule (BatchIngestionSchedule)")
        
        // Verify BatchIngestionSchedule exists with correct logical ID
        assertTrue(eventRules.containsKey("BatchIngestionSchedule3D278ACF"),
            "DataPlatform stack should contain BatchIngestionSchedule with preserved logical ID")
    }
    
    /**
     * Test that CeapDataPlatform-dev contains all expected IAM roles.
     * 
     * Expected IAM roles:
     * - ETLLambda execution role
     * - FilterLambda execution role
     * - ScoreLambda execution role
     * - StoreLambda execution role
     * - BatchIngestionWorkflow execution role
     * 
     * Note: CDK may create additional IAM roles for log retention custom resources.
     * This test verifies that at least the core IAM roles exist.
     * 
     * **Validates: Requirements 1.3, 1.5**
     */
    @Test
    fun `DataPlatform stack should contain all expected IAM roles`() {
        // Verify IAM role count (at least 4 Lambda roles + 1 Step Functions role)
        val iamRoles = dataPlatformTemplate.findResources("AWS::IAM::Role")
        assertTrue(iamRoles.size >= 5,
            "DataPlatform stack should contain at least 5 IAM roles (4 Lambda + 1 Step Functions)")
    }
    
    /**
     * Test that CeapDataPlatform-dev has correct stack parameters defined.
     * 
     * Expected parameters:
     * - DatabaseStackName: For cross-stack references to CeapDatabase-dev
     * 
     * **Validates: Requirements 7.1**
     */
    @Test
    fun `DataPlatform stack should have correct parameters defined`() {
        // Verify DatabaseStackName parameter exists
        dataPlatformTemplate.hasParameter("DatabaseStackName", mapOf(
            "Type" to "String"
        ))
    }
    
    /**
     * Test that CeapDataPlatform-dev has correct stack outputs defined.
     * 
     * Expected outputs:
     * - BatchIngestionWorkflowArnOutput: ARN of the batch ingestion workflow
     * - BatchIngestionWorkflowNameOutput: Name of the batch ingestion workflow
     * - ETLLambdaArnOutput: ARN of the ETL Lambda function
     * - FilterLambdaArnOutput: ARN of the Filter Lambda function
     * - ScoreLambdaArnOutput: ARN of the Score Lambda function
     * - StoreLambdaArnOutput: ARN of the Store Lambda function
     * 
     * **Validates: Requirements 7.1**
     */
    @Test
    fun `DataPlatform stack should have correct outputs defined`() {
        // Get all outputs
        val template = dataPlatformTemplate.toJSON()
        val outputs = template["Outputs"] as? Map<*, *>
        assertNotNull(outputs, "DataPlatform stack should have Outputs section")
        
        // Verify expected outputs exist
        val expectedOutputs = listOf(
            "BatchIngestionWorkflowArnOutput",
            "BatchIngestionWorkflowNameOutput",
            "ETLLambdaArnOutput",
            "FilterLambdaArnOutput",
            "ScoreLambdaArnOutput",
            "StoreLambdaArnOutput",
            "WorkflowBucketNameOutput",
            "WorkflowBucketArnOutput"
        )
        
        expectedOutputs.forEach { outputName ->
            assertTrue(outputs!!.containsKey(outputName),
                "DataPlatform stack should contain output: $outputName")
        }
        
        // Verify output count
        assertEquals(expectedOutputs.size, outputs!!.size,
            "DataPlatform stack should contain exactly ${expectedOutputs.size} outputs")
    }
    
    // ========================================
    // CeapServingAPI-dev Template Tests
    // ========================================
    
    /**
     * Test that CeapServingAPI-dev contains all expected Lambda functions.
     * 
     * Expected Lambda functions (from CeapReactiveWorkflow-dev):
     * - ReactiveLambda
     * 
     * Note: CDK may create additional Lambda functions for log retention custom resources.
     * This test verifies that the core Lambda function exists with preserved logical ID.
     * 
     * **Validates: Requirements 1.4**
     */
    @Test
    fun `ServingAPI stack should contain all expected Lambda functions`() {
        // Verify Lambda functions exist
        val lambdaFunctions = servingAPITemplate.findResources("AWS::Lambda::Function")
        assertTrue(lambdaFunctions.size >= 1,
            "ServingAPI stack should contain at least 1 Lambda function (ReactiveLambda)")
        
        // Verify ReactiveLambda exists with correct logical ID
        assertTrue(lambdaFunctions.containsKey("ReactiveLambdaFunction89310B25"),
            "ServingAPI stack should contain ReactiveLambda with preserved logical ID")
    }
    
    /**
     * Test that CeapServingAPI-dev contains all expected EventBridge rules.
     * 
     * Expected EventBridge rules (from CeapReactiveWorkflow-dev):
     * - CustomerEventRule
     * 
     * **Validates: Requirements 1.4**
     */
    @Test
    fun `ServingAPI stack should contain all expected EventBridge rules`() {
        // Verify EventBridge rule count
        val eventRules = servingAPITemplate.findResources("AWS::Events::Rule")
        assertEquals(1, eventRules.size,
            "ServingAPI stack should contain exactly 1 EventBridge rule (CustomerEventRule)")
        
        // Verify CustomerEventRule exists with correct logical ID
        assertTrue(eventRules.containsKey("CustomerEventRuleC528982C"),
            "ServingAPI stack should contain CustomerEventRule with preserved logical ID")
    }
    
    /**
     * Test that CeapServingAPI-dev contains all expected DynamoDB tables.
     * 
     * Expected DynamoDB tables (from CeapReactiveWorkflow-dev):
     * - DeduplicationTable
     * 
     * **Validates: Requirements 1.4**
     */
    @Test
    fun `ServingAPI stack should contain all expected DynamoDB tables`() {
        // Verify DynamoDB table count
        val dynamoTables = servingAPITemplate.findResources("AWS::DynamoDB::Table")
        assertEquals(1, dynamoTables.size,
            "ServingAPI stack should contain exactly 1 DynamoDB table (DeduplicationTable)")
        
        // Verify DeduplicationTable exists with correct logical ID
        assertTrue(dynamoTables.containsKey("DeduplicationTableAD30DFB7"),
            "ServingAPI stack should contain DeduplicationTable with preserved logical ID")
    }
    
    /**
     * Test that CeapServingAPI-dev contains all expected IAM roles.
     * 
     * Expected IAM roles:
     * - ReactiveLambda execution role
     * 
     * Note: CDK may create additional IAM roles for log retention custom resources.
     * This test verifies that at least the core IAM role exists.
     * 
     * **Validates: Requirements 1.4**
     */
    @Test
    fun `ServingAPI stack should contain all expected IAM roles`() {
        // Verify IAM role count (at least 1 Lambda role)
        val iamRoles = servingAPITemplate.findResources("AWS::IAM::Role")
        assertTrue(iamRoles.size >= 1,
            "ServingAPI stack should contain at least 1 IAM role (ReactiveLambda)")
    }
    
    /**
     * Test that CeapServingAPI-dev has correct stack parameters defined.
     * 
     * Expected parameters:
     * - DatabaseStackName: For cross-stack references to CeapDatabase-dev
     * - DataPlatformStackName: For cross-stack references to CeapDataPlatform-dev
     * 
     * **Validates: Requirements 7.2**
     */
    @Test
    fun `ServingAPI stack should have correct parameters defined`() {
        // Verify DatabaseStackName parameter exists
        servingAPITemplate.hasParameter("DatabaseStackName", mapOf(
            "Type" to "String"
        ))
        
        // Verify DataPlatformStackName parameter exists
        servingAPITemplate.hasParameter("DataPlatformStackName", mapOf(
            "Type" to "String"
        ))
    }
    
    /**
     * Test that CeapServingAPI-dev has correct stack outputs defined.
     * 
     * Expected outputs:
     * - ReactiveLambdaArnOutput: ARN of the Reactive Lambda function
     * - ReactiveLambdaNameOutput: Name of the Reactive Lambda function
     * - CustomerEventRuleArnOutput: ARN of the Customer Event Rule
     * - DeduplicationTableNameOutput: Name of the Deduplication table
     * - DeduplicationTableArnOutput: ARN of the Deduplication table
     * 
     * **Validates: Requirements 7.2**
     */
    @Test
    fun `ServingAPI stack should have correct outputs defined`() {
        // Get all outputs
        val template = servingAPITemplate.toJSON()
        val outputs = template["Outputs"] as? Map<*, *>
        assertNotNull(outputs, "ServingAPI stack should have Outputs section")
        
        // Verify expected outputs exist
        val expectedOutputs = listOf(
            "ReactiveLambdaArnOutput",
            "ReactiveLambdaNameOutput",
            "CustomerEventRuleArnOutput",
            "DeduplicationTableNameOutput",
            "DeduplicationTableArnOutput"
        )
        
        expectedOutputs.forEach { outputName ->
            assertTrue(outputs!!.containsKey(outputName),
                "ServingAPI stack should contain output: $outputName")
        }
        
        // Verify output count
        assertEquals(expectedOutputs.size, outputs!!.size,
            "ServingAPI stack should contain exactly ${expectedOutputs.size} outputs")
    }
    
    // ========================================
    // Resource Type Distribution Tests
    // ========================================
    
    /**
     * Test that CeapDataPlatform-dev contains the correct total number of resources.
     * 
     * Expected resource breakdown:
     * - 4 Lambda Functions (ETL, Filter, Score, Store)
     * - 1 Step Functions State Machine (BatchIngestionWorkflow)
     * - 1 EventBridge Rule (BatchIngestionSchedule)
     * - 5 IAM Roles (4 Lambda + 1 Step Functions)
     * - Additional IAM policies and log groups (auto-generated)
     * 
     * Note: CDK may create additional resources for log retention custom resources.
     * This test verifies that at least the core resources exist.
     * 
     * **Validates: Requirements 1.3, 1.5**
     */
    @Test
    fun `DataPlatform stack should have correct resource type distribution`() {
        val template = dataPlatformTemplate.toJSON()
        val resources = template["Resources"] as? Map<*, *>
        assertNotNull(resources, "DataPlatform stack should have Resources section")
        
        // Count resources by type
        val resourcesByType = resources!!.values
            .filterIsInstance<Map<*, *>>()
            .groupBy { it["Type"] as String }
            .mapValues { it.value.size }
        
        // Verify key resource types (at least the expected count)
        assertTrue((resourcesByType["AWS::Lambda::Function"] ?: 0) >= 4,
            "DataPlatform stack should have at least 4 Lambda functions")
        assertEquals(1, resourcesByType["AWS::StepFunctions::StateMachine"],
            "DataPlatform stack should have 1 Step Functions state machine")
        assertEquals(1, resourcesByType["AWS::Events::Rule"],
            "DataPlatform stack should have 1 EventBridge rule")
        assertTrue((resourcesByType["AWS::IAM::Role"] ?: 0) >= 5,
            "DataPlatform stack should have at least 5 IAM roles")
    }
    
    /**
     * Test that CeapServingAPI-dev contains the correct total number of resources.
     * 
     * Expected resource breakdown:
     * - 1 Lambda Function (ReactiveLambda)
     * - 1 EventBridge Rule (CustomerEventRule)
     * - 1 DynamoDB Table (DeduplicationTable)
     * - 1 IAM Role (ReactiveLambda)
     * - Additional IAM policies and log groups (auto-generated)
     * 
     * Note: CDK may create additional resources for log retention custom resources.
     * This test verifies that at least the core resources exist.
     * 
     * **Validates: Requirements 1.4**
     */
    @Test
    fun `ServingAPI stack should have correct resource type distribution`() {
        val template = servingAPITemplate.toJSON()
        val resources = template["Resources"] as? Map<*, *>
        assertNotNull(resources, "ServingAPI stack should have Resources section")
        
        // Count resources by type
        val resourcesByType = resources!!.values
            .filterIsInstance<Map<*, *>>()
            .groupBy { it["Type"] as String }
            .mapValues { it.value.size }
        
        // Verify key resource types (at least the expected count)
        assertTrue((resourcesByType["AWS::Lambda::Function"] ?: 0) >= 1,
            "ServingAPI stack should have at least 1 Lambda function")
        assertEquals(1, resourcesByType["AWS::Events::Rule"],
            "ServingAPI stack should have 1 EventBridge rule")
        assertEquals(1, resourcesByType["AWS::DynamoDB::Table"],
            "ServingAPI stack should have 1 DynamoDB table")
        assertTrue((resourcesByType["AWS::IAM::Role"] ?: 0) >= 1,
            "ServingAPI stack should have at least 1 IAM role")
    }
}
