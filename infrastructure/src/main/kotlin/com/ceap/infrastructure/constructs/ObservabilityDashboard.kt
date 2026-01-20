package com.ceap.infrastructure.constructs

import software.amazon.awscdk.Duration
import software.amazon.awscdk.services.cloudwatch.*
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction
import software.amazon.awscdk.services.sns.Topic
import software.constructs.Construct

/**
 * Construct for creating CloudWatch dashboards and alarms.
 * 
 * Creates comprehensive observability including:
 * - Per-program health dashboards
 * - Per-channel performance dashboards
 * - Cost and capacity dashboards
 * - API latency alarms
 * - Workflow failure alarms
 * - Data quality alarms
 * 
 * Requirements:
 * - Req 12.4: API latency alarms
 * - Req 12.5: Per-program and per-channel dashboards
 * - Req 12.6: Data quality alarms
 */
class ObservabilityDashboard(
    scope: Construct,
    id: String,
    private val programId: String,
    private val alarmTopic: Topic
) : Construct(scope, id) {
    
    val dashboard: Dashboard
    
    init {
        dashboard = createDashboard()
        createAlarms()
    }
    
    /**
     * Creates the main observability dashboard.
     */
    private fun createDashboard(): Dashboard {
        val dashboard = Dashboard.Builder.create(this, "Dashboard")
            .dashboardName("SolicitationPlatform-$programId")
            .build()
        
        // Add program health widgets
        dashboard.addWidgets(
            createProgramHealthWidget(),
            createWorkflowMetricsWidget()
        )
        
        // Add channel performance widgets
        dashboard.addWidgets(
            createChannelPerformanceWidget(),
            createRejectionMetricsWidget()
        )
        
        // Add cost and capacity widgets
        dashboard.addWidgets(
            createCostMetricsWidget(),
            createCapacityMetricsWidget()
        )
        
        return dashboard
    }
    
    /**
     * Creates program health widget showing overall system health.
     */
    private fun createProgramHealthWidget(): GraphWidget {
        return GraphWidget.Builder.create()
            .title("Program Health - $programId")
            .left(listOf(
                createMetric("SolicitationPlatform/Workflow", "WorkflowSuccess", "ProgramId", programId),
                createMetric("SolicitationPlatform/Workflow", "TotalErrors", "ProgramId", programId)
            ))
            .width(12)
            .height(6)
            .build()
    }
    
    /**
     * Creates workflow metrics widget showing processing volumes.
     */
    private fun createWorkflowMetricsWidget(): GraphWidget {
        return GraphWidget.Builder.create()
            .title("Workflow Metrics - $programId")
            .left(listOf(
                createMetric("SolicitationPlatform/Workflow", "TotalProcessed", "ProgramId", programId),
                createMetric("SolicitationPlatform/Workflow", "TotalStored", "ProgramId", programId),
                createMetric("SolicitationPlatform/Workflow", "TotalRejected", "ProgramId", programId)
            ))
            .width(12)
            .height(6)
            .build()
    }
    
    /**
     * Creates channel performance widget showing delivery metrics.
     */
    private fun createChannelPerformanceWidget(): GraphWidget {
        return GraphWidget.Builder.create()
            .title("Channel Performance - $programId")
            .left(listOf(
                createMetric("SolicitationPlatform/Channels", "DeliveryAttempts", "ProgramId", programId),
                createMetric("SolicitationPlatform/Channels", "DeliverySuccess", "ProgramId", programId),
                createMetric("SolicitationPlatform/Channels", "DeliveryFailures", "ProgramId", programId)
            ))
            .width(12)
            .height(6)
            .build()
    }
    
    /**
     * Creates rejection metrics widget showing why candidates are rejected.
     */
    private fun createRejectionMetricsWidget(): GraphWidget {
        return GraphWidget.Builder.create()
            .title("Rejection Reasons - $programId")
            .left(listOf(
                createMetric("SolicitationPlatform/Rejections", "RejectionCount", "ProgramId", programId)
            ))
            .width(12)
            .height(6)
            .stacked(true)
            .build()
    }
    
    /**
     * Creates cost metrics widget showing operational costs.
     */
    private fun createCostMetricsWidget(): GraphWidget {
        return GraphWidget.Builder.create()
            .title("Cost Metrics - $programId")
            .left(listOf(
                createMetric("AWS/DynamoDB", "ConsumedReadCapacityUnits", "TableName", "SolicitationCandidates-$programId"),
                createMetric("AWS/DynamoDB", "ConsumedWriteCapacityUnits", "TableName", "SolicitationCandidates-$programId")
            ))
            .width(12)
            .height(6)
            .build()
    }
    
    /**
     * Creates capacity metrics widget showing resource utilization.
     */
    private fun createCapacityMetricsWidget(): GraphWidget {
        return GraphWidget.Builder.create()
            .title("Capacity Metrics - $programId")
            .left(listOf(
                createMetric("AWS/Lambda", "ConcurrentExecutions", "FunctionName", "SolicitationPlatform-ETL-$programId"),
                createMetric("AWS/Lambda", "Duration", "FunctionName", "SolicitationPlatform-Serve-$programId")
            ))
            .width(12)
            .height(6)
            .build()
    }
    
    /**
     * Creates CloudWatch alarms for monitoring.
     */
    private fun createAlarms() {
        createApiLatencyAlarm()
        createWorkflowFailureAlarm()
        createDataQualityAlarm()
    }
    
    /**
     * Creates API latency alarm.
     * Triggers when P99 latency exceeds 30ms threshold.
     */
    private fun createApiLatencyAlarm() {
        val metric = Metric.Builder.create()
            .namespace("AWS/Lambda")
            .metricName("Duration")
            .dimensionsMap(mapOf("FunctionName" to "SolicitationPlatform-Serve-$programId"))
            .statistic("p99")
            .period(Duration.minutes(5))
            .build()
        
        val alarm = Alarm.Builder.create(this, "ApiLatencyAlarm")
            .alarmName("SolicitationPlatform-ApiLatency-$programId")
            .alarmDescription("API latency exceeds 30ms at P99")
            .metric(metric)
            .threshold(30.0)
            .evaluationPeriods(2)
            .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
            .treatMissingData(TreatMissingData.NOT_BREACHING)
            .build()
        
        alarm.addAlarmAction(SnsAction(alarmTopic))
    }
    
    /**
     * Creates workflow failure alarm.
     * Triggers when workflow error count exceeds threshold.
     */
    private fun createWorkflowFailureAlarm() {
        val metric = Metric.Builder.create()
            .namespace("SolicitationPlatform/Workflow")
            .metricName("TotalErrors")
            .dimensionsMap(mapOf("ProgramId" to programId))
            .statistic("Sum")
            .period(Duration.minutes(5))
            .build()
        
        val alarm = Alarm.Builder.create(this, "WorkflowFailureAlarm")
            .alarmName("SolicitationPlatform-WorkflowFailure-$programId")
            .alarmDescription("Workflow errors exceed threshold")
            .metric(metric)
            .threshold(10.0)
            .evaluationPeriods(1)
            .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
            .treatMissingData(TreatMissingData.NOT_BREACHING)
            .build()
        
        alarm.addAlarmAction(SnsAction(alarmTopic))
    }
    
    /**
     * Creates data quality alarm.
     * Triggers when validation error rate is too high.
     */
    private fun createDataQualityAlarm() {
        val metric = Metric.Builder.create()
            .namespace("SolicitationPlatform/Workflow")
            .metricName("ErrorCount")
            .dimensionsMap(mapOf(
                "ProgramId" to programId,
                "Stage" to "ETL"
            ))
            .statistic("Sum")
            .period(Duration.minutes(5))
            .build()
        
        val alarm = Alarm.Builder.create(this, "DataQualityAlarm")
            .alarmName("SolicitationPlatform-DataQuality-$programId")
            .alarmDescription("Data validation errors exceed threshold")
            .metric(metric)
            .threshold(100.0)
            .evaluationPeriods(2)
            .comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD)
            .treatMissingData(TreatMissingData.NOT_BREACHING)
            .build()
        
        alarm.addAlarmAction(SnsAction(alarmTopic))
    }
    
    /**
     * Helper to create a metric.
     */
    private fun createMetric(
        namespace: String,
        metricName: String,
        dimensionName: String,
        dimensionValue: String
    ): IMetric {
        return Metric.Builder.create()
            .namespace(namespace)
            .metricName(metricName)
            .dimensionsMap(mapOf(dimensionName to dimensionValue))
            .statistic("Sum")
            .period(Duration.minutes(5))
            .build()
    }
}
