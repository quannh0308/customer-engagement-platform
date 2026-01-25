package com.ceap.infrastructure.stacks

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Unit tests for resource name updates in Phase 3.
 * 
 * **Validates: Requirements 2.1-2.7, 4.1, 5.1, 5.2, 6.1, 7.1**
 * 
 * These tests verify that all infrastructure resources have been updated to use
 * CEAP branding instead of Solicitation branding. This includes:
 * - CloudFormation stack names
 * - DynamoDB table names
 * - EventBridge rule names
 * - EventBridge source names
 * - Step Functions state machine names
 * 
 * The tests check that the correct naming patterns are used:
 * - CloudFormation stacks: Ceap*-$envName
 * - DynamoDB tables: ceap-*-$envName
 * - EventBridge rules: ceap-*-$envName or Ceap*-$envName
 * - EventBridge sources: ceap.*
 * - State machines: Ceap*-$envName
 */
class ResourceNameUpdatesTest {
    
    private val projectRoot = File(System.getProperty("user.dir"))
    
    // When running tests from the infrastructure module, the working directory is the infrastructure directory
    // When running from the root, we need to include the infrastructure prefix
    private val mainAppPath = if (File(projectRoot, "src/main/kotlin/com/ceap/infrastructure/SolicitationPlatformApp.kt").exists()) {
        "src/main/kotlin/com/ceap/infrastructure/SolicitationPlatformApp.kt"
    } else {
        "infrastructure/src/main/kotlin/com/ceap/infrastructure/SolicitationPlatformApp.kt"
    }
    
    private val reactiveStackPath = if (File(projectRoot, "src/main/kotlin/com/ceap/infrastructure/stacks/ReactiveWorkflowStack.kt").exists()) {
        "src/main/kotlin/com/ceap/infrastructure/stacks/ReactiveWorkflowStack.kt"
    } else {
        "infrastructure/src/main/kotlin/com/ceap/infrastructure/stacks/ReactiveWorkflowStack.kt"
    }
    
    private val orchestrationStackPath = if (File(projectRoot, "src/main/kotlin/com/ceap/infrastructure/stacks/OrchestrationStack.kt").exists()) {
        "src/main/kotlin/com/ceap/infrastructure/stacks/OrchestrationStack.kt"
    } else {
        "infrastructure/src/main/kotlin/com/ceap/infrastructure/stacks/OrchestrationStack.kt"
    }
    
    // ========== CloudFormation Stack Name Tests ==========
    
    /**
     * Test that database stack uses CeapDatabase-$envName pattern.
     * 
     * **Validates: Requirements 2.1**
     */
    @Test
    fun `database stack should use CeapDatabase-envName pattern`() {
        val mainAppFile = File(projectRoot, mainAppPath)
        assertTrue(mainAppFile.exists(), "Main application file should exist")
        
        val content = mainAppFile.readText()
        assertTrue(
            content.contains("\"CeapDatabase-\$envName\""),
            "Database stack should use CeapDatabase-\$envName pattern"
        )
        
        // Verify old pattern is not present
        assertFalse(
            content.contains("\"SolicitationDatabase-\$envName\""),
            "Database stack should not use old SolicitationDatabase-\$envName pattern"
        )
    }
    
    /**
     * Test that ETL stack uses CeapEtlWorkflow-$envName pattern.
     * 
     * **Validates: Requirements 2.2**
     */
    @Test
    fun `ETL stack should use CeapEtlWorkflow-envName pattern`() {
        val mainAppFile = File(projectRoot, mainAppPath)
        val content = mainAppFile.readText()
        
        assertTrue(
            content.contains("\"CeapEtlWorkflow-\$envName\""),
            "ETL stack should use CeapEtlWorkflow-\$envName pattern"
        )
        
        // Verify old pattern is not present
        assertFalse(
            content.contains("\"SolicitationEtlWorkflow-\$envName\""),
            "ETL stack should not use old SolicitationEtlWorkflow-\$envName pattern"
        )
    }
    
    /**
     * Test that filter stack uses CeapFilterWorkflow-$envName pattern.
     * 
     * **Validates: Requirements 2.3**
     */
    @Test
    fun `filter stack should use CeapFilterWorkflow-envName pattern`() {
        val mainAppFile = File(projectRoot, mainAppPath)
        val content = mainAppFile.readText()
        
        assertTrue(
            content.contains("\"CeapFilterWorkflow-\$envName\""),
            "Filter stack should use CeapFilterWorkflow-\$envName pattern"
        )
        
        // Verify old pattern is not present
        assertFalse(
            content.contains("\"SolicitationFilterWorkflow-\$envName\""),
            "Filter stack should not use old SolicitationFilterWorkflow-\$envName pattern"
        )
    }
    
    /**
     * Test that score stack uses CeapScoreWorkflow-$envName pattern.
     * 
     * **Validates: Requirements 2.4**
     */
    @Test
    fun `score stack should use CeapScoreWorkflow-envName pattern`() {
        val mainAppFile = File(projectRoot, mainAppPath)
        val content = mainAppFile.readText()
        
        assertTrue(
            content.contains("\"CeapScoreWorkflow-\$envName\""),
            "Score stack should use CeapScoreWorkflow-\$envName pattern"
        )
        
        // Verify old pattern is not present
        assertFalse(
            content.contains("\"SolicitationScoreWorkflow-\$envName\""),
            "Score stack should not use old SolicitationScoreWorkflow-\$envName pattern"
        )
    }
    
    /**
     * Test that store stack uses CeapStoreWorkflow-$envName pattern.
     * 
     * **Validates: Requirements 2.5**
     */
    @Test
    fun `store stack should use CeapStoreWorkflow-envName pattern`() {
        val mainAppFile = File(projectRoot, mainAppPath)
        val content = mainAppFile.readText()
        
        assertTrue(
            content.contains("\"CeapStoreWorkflow-\$envName\""),
            "Store stack should use CeapStoreWorkflow-\$envName pattern"
        )
        
        // Verify old pattern is not present
        assertFalse(
            content.contains("\"SolicitationStoreWorkflow-\$envName\""),
            "Store stack should not use old SolicitationStoreWorkflow-\$envName pattern"
        )
    }
    
    /**
     * Test that reactive stack uses CeapReactiveWorkflow-$envName pattern.
     * 
     * **Validates: Requirements 2.6**
     */
    @Test
    fun `reactive stack should use CeapReactiveWorkflow-envName pattern`() {
        val mainAppFile = File(projectRoot, mainAppPath)
        val content = mainAppFile.readText()
        
        assertTrue(
            content.contains("\"CeapReactiveWorkflow-\$envName\""),
            "Reactive stack should use CeapReactiveWorkflow-\$envName pattern"
        )
        
        // Verify old pattern is not present
        assertFalse(
            content.contains("\"SolicitationReactiveWorkflow-\$envName\""),
            "Reactive stack should not use old SolicitationReactiveWorkflow-\$envName pattern"
        )
    }
    
    /**
     * Test that orchestration stack uses CeapOrchestration-$envName pattern.
     * 
     * **Validates: Requirements 2.7**
     */
    @Test
    fun `orchestration stack should use CeapOrchestration-envName pattern`() {
        val mainAppFile = File(projectRoot, mainAppPath)
        val content = mainAppFile.readText()
        
        assertTrue(
            content.contains("\"CeapOrchestration-\$envName\""),
            "Orchestration stack should use CeapOrchestration-\$envName pattern"
        )
        
        // Verify old pattern is not present
        assertFalse(
            content.contains("\"SolicitationOrchestration-\$envName\""),
            "Orchestration stack should not use old SolicitationOrchestration-\$envName pattern"
        )
    }
    
    /**
     * Test that all CloudFormation stacks use CEAP naming pattern.
     * 
     * **Validates: Requirements 2.1-2.7**
     * 
     * This comprehensive test verifies all 7 CloudFormation stacks in a single assertion.
     */
    @Test
    fun `all CloudFormation stacks should use CEAP naming pattern`() {
        val mainAppFile = File(projectRoot, mainAppPath)
        val content = mainAppFile.readText()
        
        val expectedStackNames = listOf(
            "CeapDatabase-\$envName",
            "CeapEtlWorkflow-\$envName",
            "CeapFilterWorkflow-\$envName",
            "CeapScoreWorkflow-\$envName",
            "CeapStoreWorkflow-\$envName",
            "CeapReactiveWorkflow-\$envName",
            "CeapOrchestration-\$envName"
        )
        
        expectedStackNames.forEach { stackName ->
            assertTrue(
                content.contains("\"$stackName\""),
                "Main application should contain stack name: $stackName"
            )
        }
    }
    
    /**
     * Test that no CloudFormation stacks use old Solicitation naming pattern.
     * 
     * **Validates: Requirements 2.1-2.7**
     * 
     * This test ensures that all legacy stack names have been removed.
     */
    @Test
    fun `no CloudFormation stacks should use old Solicitation naming pattern`() {
        val mainAppFile = File(projectRoot, mainAppPath)
        val content = mainAppFile.readText()
        
        val oldStackNames = listOf(
            "SolicitationDatabase-",
            "SolicitationEtlWorkflow-",
            "SolicitationFilterWorkflow-",
            "SolicitationScoreWorkflow-",
            "SolicitationStoreWorkflow-",
            "SolicitationReactiveWorkflow-",
            "SolicitationOrchestration-"
        )
        
        oldStackNames.forEach { stackName ->
            assertFalse(
                content.contains(stackName),
                "Main application should not contain old stack name pattern: $stackName"
            )
        }
    }
    
    // ========== DynamoDB Table Name Tests ==========
    
    /**
     * Test that deduplication table uses ceap-event-deduplication-$envName pattern.
     * 
     * **Validates: Requirements 7.1**
     */
    @Test
    fun `deduplication table should use ceap-event-deduplication-envName pattern`() {
        val reactiveStackFile = File(projectRoot, reactiveStackPath)
        assertTrue(reactiveStackFile.exists(), "ReactiveWorkflowStack file should exist")
        
        val content = reactiveStackFile.readText()
        assertTrue(
            content.contains("\"ceap-event-deduplication-\$envName\""),
            "Deduplication table should use ceap-event-deduplication-\$envName pattern"
        )
        
        // Verify old pattern is not present
        assertFalse(
            content.contains("\"solicitation-event-deduplication-\$envName\""),
            "Deduplication table should not use old solicitation-event-deduplication-\$envName pattern"
        )
    }
    
    // ========== EventBridge Rule Name Tests ==========
    
    /**
     * Test that customer event rule uses ceap-customer-events-$envName pattern.
     * 
     * **Validates: Requirements 5.2**
     */
    @Test
    fun `customer event rule should use ceap-customer-events-envName pattern`() {
        val reactiveStackFile = File(projectRoot, reactiveStackPath)
        val content = reactiveStackFile.readText()
        
        assertTrue(
            content.contains("\"ceap-customer-events-\$envName\""),
            "Customer event rule should use ceap-customer-events-\$envName pattern"
        )
        
        // Verify old pattern is not present
        assertFalse(
            content.contains("\"solicitation-customer-events-\$envName\""),
            "Customer event rule should not use old solicitation-customer-events-\$envName pattern"
        )
    }
    
    /**
     * Test that schedule rule uses CeapBatchIngestion-$envName pattern.
     * 
     * **Validates: Requirements 5.1**
     */
    @Test
    fun `schedule rule should use CeapBatchIngestion-envName pattern`() {
        val orchestrationStackFile = File(projectRoot, orchestrationStackPath)
        assertTrue(orchestrationStackFile.exists(), "OrchestrationStack file should exist")
        
        val content = orchestrationStackFile.readText()
        assertTrue(
            content.contains(".ruleName(\"CeapBatchIngestion-\$envName\")"),
            "Schedule rule should use CeapBatchIngestion-\$envName pattern"
        )
        
        // Verify old pattern is not present
        assertFalse(
            content.contains(".ruleName(\"SolicitationBatchIngestion-\$envName\")"),
            "Schedule rule should not use old SolicitationBatchIngestion-\$envName pattern"
        )
    }
    
    // ========== EventBridge Source Tests ==========
    
    /**
     * Test that EventBridge source uses ceap.customer-events.
     * 
     * **Validates: Requirements 6.1**
     */
    @Test
    fun `EventBridge source should use ceap-customer-events`() {
        val reactiveStackFile = File(projectRoot, reactiveStackPath)
        val content = reactiveStackFile.readText()
        
        assertTrue(
            content.contains(".source(listOf(\"ceap.customer-events\"))"),
            "EventBridge source should use ceap.customer-events"
        )
        
        // Verify old pattern is not present
        assertFalse(
            content.contains(".source(listOf(\"solicitation.customer-events\"))"),
            "EventBridge source should not use old solicitation.customer-events"
        )
    }
    
    // ========== Step Functions State Machine Tests ==========
    
    /**
     * Test that state machine uses CeapBatchIngestion-$envName pattern.
     * 
     * **Validates: Requirements 4.1**
     */
    @Test
    fun `state machine should use CeapBatchIngestion-envName pattern`() {
        val orchestrationStackFile = File(projectRoot, orchestrationStackPath)
        val content = orchestrationStackFile.readText()
        
        assertTrue(
            content.contains(".stateMachineName(\"CeapBatchIngestion-\$envName\")"),
            "State machine should use CeapBatchIngestion-\$envName pattern"
        )
        
        // Verify old pattern is not present
        assertFalse(
            content.contains(".stateMachineName(\"SolicitationBatchIngestion-\$envName\")"),
            "State machine should not use old SolicitationBatchIngestion-\$envName pattern"
        )
    }
    
    // ========== Comprehensive Tests ==========
    
    /**
     * Test that ReactiveWorkflowStack has all required CEAP resource names.
     * 
     * **Validates: Requirements 5.2, 6.1, 7.1**
     * 
     * This comprehensive test verifies all resource names in ReactiveWorkflowStack.
     */
    @Test
    fun `ReactiveWorkflowStack should have all CEAP resource names`() {
        val reactiveStackFile = File(projectRoot, reactiveStackPath)
        val content = reactiveStackFile.readText()
        
        val expectedResources = listOf(
            "ceap-event-deduplication-\$envName",
            "ceap-customer-events-\$envName",
            "ceap.customer-events"
        )
        
        expectedResources.forEach { resource ->
            assertTrue(
                content.contains(resource),
                "ReactiveWorkflowStack should contain resource: $resource"
            )
        }
    }
    
    /**
     * Test that ReactiveWorkflowStack has no old Solicitation resource names.
     * 
     * **Validates: Requirements 5.2, 6.1, 7.1**
     * 
     * This test ensures that all legacy resource names have been removed from ReactiveWorkflowStack.
     */
    @Test
    fun `ReactiveWorkflowStack should have no old Solicitation resource names`() {
        val reactiveStackFile = File(projectRoot, reactiveStackPath)
        val content = reactiveStackFile.readText()
        
        val oldResources = listOf(
            "solicitation-event-deduplication-",
            "solicitation-customer-events-",
            "solicitation.customer-events"
        )
        
        oldResources.forEach { resource ->
            assertFalse(
                content.contains(resource),
                "ReactiveWorkflowStack should not contain old resource: $resource"
            )
        }
    }
    
    /**
     * Test that OrchestrationStack has all required CEAP resource names.
     * 
     * **Validates: Requirements 4.1, 5.1**
     * 
     * This comprehensive test verifies all resource names in OrchestrationStack.
     */
    @Test
    fun `OrchestrationStack should have all CEAP resource names`() {
        val orchestrationStackFile = File(projectRoot, orchestrationStackPath)
        val content = orchestrationStackFile.readText()
        
        val expectedResources = listOf(
            "CeapBatchIngestion-\$envName"
        )
        
        expectedResources.forEach { resource ->
            assertTrue(
                content.contains(resource),
                "OrchestrationStack should contain resource: $resource"
            )
        }
    }
    
    /**
     * Test that OrchestrationStack has no old Solicitation resource names.
     * 
     * **Validates: Requirements 4.1, 5.1**
     * 
     * This test ensures that all legacy resource names have been removed from OrchestrationStack.
     */
    @Test
    fun `OrchestrationStack should have no old Solicitation resource names`() {
        val orchestrationStackFile = File(projectRoot, orchestrationStackPath)
        val content = orchestrationStackFile.readText()
        
        val oldResources = listOf(
            "SolicitationBatchIngestion-"
        )
        
        oldResources.forEach { resource ->
            assertFalse(
                content.contains(resource),
                "OrchestrationStack should not contain old resource: $resource"
            )
        }
    }
    
    /**
     * Test that all resource names follow CEAP naming conventions.
     * 
     * **Validates: Requirements 2.1-2.7, 4.1, 5.1, 5.2, 6.1, 7.1**
     * 
     * This comprehensive test verifies that all infrastructure resources follow
     * the correct CEAP naming patterns as defined in the design document.
     */
    @Test
    fun `all resources should follow CEAP naming conventions`() {
        // Check main application file
        val mainAppFile = File(projectRoot, mainAppPath)
        val mainAppContent = mainAppFile.readText()
        
        // Check reactive stack file
        val reactiveStackFile = File(projectRoot, reactiveStackPath)
        val reactiveStackContent = reactiveStackFile.readText()
        
        // Check orchestration stack file
        val orchestrationStackFile = File(projectRoot, orchestrationStackPath)
        val orchestrationStackContent = orchestrationStackFile.readText()
        
        // Verify all CEAP patterns are present
        val ceapPatterns = listOf(
            "CeapDatabase-",
            "CeapEtlWorkflow-",
            "CeapFilterWorkflow-",
            "CeapScoreWorkflow-",
            "CeapStoreWorkflow-",
            "CeapReactiveWorkflow-",
            "CeapOrchestration-",
            "ceap-event-deduplication-",
            "ceap-customer-events-",
            "ceap.customer-events",
            "CeapBatchIngestion-"
        )
        
        val allContent = mainAppContent + reactiveStackContent + orchestrationStackContent
        
        ceapPatterns.forEach { pattern ->
            assertTrue(
                allContent.contains(pattern),
                "Infrastructure code should contain CEAP pattern: $pattern"
            )
        }
        
        // Verify no Solicitation patterns remain
        val solicitationPatterns = listOf(
            "SolicitationDatabase-",
            "SolicitationEtlWorkflow-",
            "SolicitationFilterWorkflow-",
            "SolicitationScoreWorkflow-",
            "SolicitationStoreWorkflow-",
            "SolicitationReactiveWorkflow-",
            "SolicitationOrchestration-",
            "solicitation-event-deduplication-",
            "solicitation-customer-events-",
            "solicitation.customer-events",
            "SolicitationBatchIngestion-"
        )
        
        solicitationPatterns.forEach { pattern ->
            assertFalse(
                allContent.contains(pattern),
                "Infrastructure code should not contain old Solicitation pattern: $pattern"
            )
        }
    }
}
