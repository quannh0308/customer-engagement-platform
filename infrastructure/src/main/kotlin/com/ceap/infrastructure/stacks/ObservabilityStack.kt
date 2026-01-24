package com.ceap.infrastructure.stacks

import com.ceap.infrastructure.constructs.ObservabilityDashboard
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.sns.Topic
import software.amazon.awscdk.services.sns.subscriptions.EmailSubscription
import software.constructs.Construct

/**
 * Stack for observability infrastructure including dashboards and alarms.
 * 
 * Creates:
 * - CloudWatch dashboards for each program
 * - CloudWatch alarms for latency, failures, and data quality
 * - SNS topics for alarm notifications
 * 
 * Requirements:
 * - Req 12.4: API latency alarms
 * - Req 12.5: Per-program and per-channel dashboards
 * - Req 12.6: Data quality alarms
 */
class ObservabilityStack(
    scope: Construct,
    id: String,
    props: StackProps? = null,
    private val programIds: List<String>,
    private val alarmEmail: String
) : Stack(scope, id, props) {
    
    val alarmTopic: Topic
    val dashboards: Map<String, ObservabilityDashboard>
    
    init {
        // Create SNS topic for alarm notifications
        alarmTopic = createAlarmTopic()
        
        // Create dashboards for each program
        dashboards = programIds.associateWith { programId ->
            ObservabilityDashboard(
                this,
                "Dashboard-$programId",
                programId,
                alarmTopic
            )
        }
    }
    
    /**
     * Creates SNS topic for alarm notifications.
     */
    private fun createAlarmTopic(): Topic {
        val topic = Topic.Builder.create(this, "AlarmTopic")
            .topicName("CEAPlatform-Alarms")
            .displayName("CEAP Alarms")
            .build()
        
        // Subscribe email to alarm notifications
        topic.addSubscription(EmailSubscription.Builder.create(alarmEmail).build())
        
        return topic
    }
}
