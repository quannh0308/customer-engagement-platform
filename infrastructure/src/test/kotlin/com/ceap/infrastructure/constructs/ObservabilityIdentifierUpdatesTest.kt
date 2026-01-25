package com.ceap.infrastructure.constructs

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Unit tests for observability identifier updates in Phase 4.
 * 
 * Validates: Requirements 8.1, 8.3, 8.4, 9.1, 10.1, 10.2, 10.3, 11.1, 11.2, 12.1
 * 
 * These tests verify that all observability resources have been updated to use
 * CEAP branding instead of Solicitation branding. This includes:
 * - CloudWatch dashboard names
 * - CloudWatch namespace references
 * - CloudWatch alarm names
 * - Lambda function name references in metrics
 * - DynamoDB table name references in metrics
 * 
 * The tests check that the correct naming patterns are used:
 * - Dashboards: CeapPlatform-$programId
 * - Namespaces: CeapPlatform slash *
 * - Alarms: CeapPlatform-*-$programId
 * - Lambda functions: CeapPlatform-*-$programId
 * - DynamoDB tables: CeapCandidates-$programId
 */
class ObservabilityIdentifierUpdatesTest {
    
    private val projectRoot = File(System.getProperty("user.dir"))
    
    private val observabilityDashboardPath = if (File(projectRoot, "src/main/kotlin/com/ceap/infrastructure/constructs/ObservabilityDashboard.kt").exists()) {
        "src/main/kotlin/com/ceap/infrastructure/constructs/ObservabilityDashboard.kt"
    } else {
        "infrastructure/src/main/kotlin/com/ceap/infrastructure/constructs/ObservabilityDashboard.kt"
    }
    
    @Test
    fun `dashboard should use CeapPlatform-programId pattern`() {
        val dashboardFile = File(projectRoot, observabilityDashboardPath)
        assertTrue(dashboardFile.exists(), "ObservabilityDashboard file should exist")
        
        val content = dashboardFile.readText()
        assertTrue(
            content.contains(".dashboardName(\"CeapPlatform-\$programId\")"),
            "Dashboard should use CeapPlatform-\$programId pattern"
        )
        
        assertFalse(
            content.contains(".dashboardName(\"SolicitationPlatform-\$programId\")"),
            "Dashboard should not use old SolicitationPlatform-\$programId pattern"
        )
    }
    
    @Test
    fun `workflow namespace should use CeapPlatform-Workflow`() {
        val dashboardFile = File(projectRoot, observabilityDashboardPath)
        val content = dashboardFile.readText()
        
        assertTrue(
            content.contains("\"CeapPlatform/Workflow\""),
            "Workflow namespace should use CeapPlatform/Workflow"
        )
        
        assertFalse(
            content.contains("\"SolicitationPlatform/Workflow\""),
            "Workflow namespace should not use old SolicitationPlatform/Workflow"
        )
    }
    
    @Test
    fun `channels namespace should use CeapPlatform-Channels`() {
        val dashboardFile = File(projectRoot, observabilityDashboardPath)
        val content = dashboardFile.readText()
        
        assertTrue(
            content.contains("\"CeapPlatform/Channels\""),
            "Channels namespace should use CeapPlatform/Channels"
        )
        
        assertFalse(
            content.contains("\"SolicitationPlatform/Channels\""),
            "Channels namespace should not use old SolicitationPlatform/Channels"
        )
    }
    
    @Test
    fun `rejections namespace should use CeapPlatform-Rejections`() {
        val dashboardFile = File(projectRoot, observabilityDashboardPath)
        val content = dashboardFile.readText()
        
        assertTrue(
            content.contains("\"CeapPlatform/Rejections\""),
            "Rejections namespace should use CeapPlatform/Rejections"
        )
        
        assertFalse(
            content.contains("\"SolicitationPlatform/Rejections\""),
            "Rejections namespace should not use old SolicitationPlatform/Rejections"
        )
    }
    
    @Test
    fun `API latency alarm should use CeapPlatform-ApiLatency-programId pattern`() {
        val dashboardFile = File(projectRoot, observabilityDashboardPath)
        val content = dashboardFile.readText()
        
        assertTrue(
            content.contains(".alarmName(\"CeapPlatform-ApiLatency-\$programId\")"),
            "API latency alarm should use CeapPlatform-ApiLatency-\$programId pattern"
        )
        
        assertFalse(
            content.contains(".alarmName(\"SolicitationPlatform-ApiLatency-\$programId\")"),
            "API latency alarm should not use old SolicitationPlatform-ApiLatency-\$programId pattern"
        )
    }
    
    @Test
    fun `workflow failure alarm should use CeapPlatform-WorkflowFailure-programId pattern`() {
        val dashboardFile = File(projectRoot, observabilityDashboardPath)
        val content = dashboardFile.readText()
        
        assertTrue(
            content.contains(".alarmName(\"CeapPlatform-WorkflowFailure-\$programId\")"),
            "Workflow failure alarm should use CeapPlatform-WorkflowFailure-\$programId pattern"
        )
        
        assertFalse(
            content.contains(".alarmName(\"SolicitationPlatform-WorkflowFailure-\$programId\")"),
            "Workflow failure alarm should not use old SolicitationPlatform-WorkflowFailure-\$programId pattern"
        )
    }
    
    @Test
    fun `data quality alarm should use CeapPlatform-DataQuality-programId pattern`() {
        val dashboardFile = File(projectRoot, observabilityDashboardPath)
        val content = dashboardFile.readText()
        
        assertTrue(
            content.contains(".alarmName(\"CeapPlatform-DataQuality-\$programId\")"),
            "Data quality alarm should use CeapPlatform-DataQuality-\$programId pattern"
        )
        
        assertFalse(
            content.contains(".alarmName(\"SolicitationPlatform-DataQuality-\$programId\")"),
            "Data quality alarm should not use old SolicitationPlatform-DataQuality-\$programId pattern"
        )
    }
    
    @Test
    fun `ETL function reference should use CeapPlatform-ETL-programId pattern`() {
        val dashboardFile = File(projectRoot, observabilityDashboardPath)
        val content = dashboardFile.readText()
        
        assertTrue(
            content.contains("\"CeapPlatform-ETL-\$programId\""),
            "ETL function reference should use CeapPlatform-ETL-\$programId pattern"
        )
        
        assertFalse(
            content.contains("\"SolicitationPlatform-ETL-\$programId\""),
            "ETL function reference should not use old SolicitationPlatform-ETL-\$programId pattern"
        )
    }
    
    @Test
    fun `Serve function reference should use CeapPlatform-Serve-programId pattern`() {
        val dashboardFile = File(projectRoot, observabilityDashboardPath)
        val content = dashboardFile.readText()
        
        assertTrue(
            content.contains("\"CeapPlatform-Serve-\$programId\""),
            "Serve function reference should use CeapPlatform-Serve-\$programId pattern"
        )
        
        assertFalse(
            content.contains("\"SolicitationPlatform-Serve-\$programId\""),
            "Serve function reference should not use old SolicitationPlatform-Serve-\$programId pattern"
        )
    }
    
    @Test
    fun `candidates table reference should use CeapCandidates-programId pattern`() {
        val dashboardFile = File(projectRoot, observabilityDashboardPath)
        val content = dashboardFile.readText()
        
        assertTrue(
            content.contains("\"CeapCandidates-\$programId\""),
            "Candidates table reference should use CeapCandidates-\$programId pattern"
        )
        
        assertFalse(
            content.contains("\"SolicitationCandidates-\$programId\""),
            "Candidates table reference should not use old SolicitationCandidates-\$programId pattern"
        )
    }
}
