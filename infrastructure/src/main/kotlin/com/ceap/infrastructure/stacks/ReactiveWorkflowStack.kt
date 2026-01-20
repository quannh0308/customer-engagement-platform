package com.ceap.infrastructure.stacks

import software.amazon.awscdk.Duration
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.events.Rule
import software.amazon.awscdk.services.events.EventPattern
import software.amazon.awscdk.services.events.targets.LambdaFunction
import software.amazon.awscdk.services.dynamodb.Table
import software.amazon.awscdk.services.dynamodb.Attribute
import software.amazon.awscdk.services.dynamodb.AttributeType
import software.amazon.awscdk.services.dynamodb.BillingMode
import software.constructs.Construct
import com.ceap.infrastructure.constructs.SolicitationLambda

/**
 * Reactive Workflow Stack - Handles real-time events.
 * 
 * This is a plug-and-play workflow that can be deployed independently.
 * 
 * Components:
 * - Reactive Lambda: Processes customer events in real-time
 * - EventBridge Rule: Routes customer events to Lambda
 * - Deduplication Table: Tracks recent events to prevent duplicates
 * 
 * Validates: Requirements 9.1, 9.2, 9.5
 */
class ReactiveWorkflowStack(
    scope: Construct,
    id: String,
    props: StackProps,
    val envName: String,
    val databaseStack: DatabaseStack
) : Stack(scope, id, props) {
    
    // Create deduplication table for tracking recent events
    val deduplicationTable = Table.Builder.create(this, "DeduplicationTable")
        .tableName("solicitation-event-deduplication-$envName")
        .partitionKey(
            Attribute.builder()
                .name("deduplicationKey")
                .type(AttributeType.STRING)
                .build()
        )
        .billingMode(BillingMode.PAY_PER_REQUEST)
        .timeToLiveAttribute("ttl")
        .build()
    
    // Create reactive Lambda
    val reactiveLambda = SolicitationLambda(
        this,
        "ReactiveLambda",
        handler = "com.ceap.workflow.reactive.ReactiveHandler::handleRequest",
        jarPath = "../ceap-workflow-reactive/build/libs/ceap-workflow-reactive-1.0.0-SNAPSHOT.jar",
        environment = mapOf(
            "ENVIRONMENT" to envName,
            "CANDIDATES_TABLE" to databaseStack.candidatesTable.tableName,
            "PROGRAM_CONFIG_TABLE" to databaseStack.programConfigTable.tableName,
            "SCORE_CACHE_TABLE" to databaseStack.scoreCacheTable.tableName,
            "DEDUPLICATION_TABLE" to deduplicationTable.tableName,
            "LOG_LEVEL" to "INFO"
        ),
        tables = listOf(
            databaseStack.candidatesTable,
            databaseStack.programConfigTable,
            databaseStack.scoreCacheTable,
            deduplicationTable
        ),
        memorySize = 1024,
        timeout = Duration.minutes(1)
    )
    
    // Create EventBridge rule for customer events
    val customerEventRule = Rule.Builder.create(this, "CustomerEventRule")
        .ruleName("solicitation-customer-events-$envName")
        .description("Routes customer events to reactive solicitation workflow")
        .eventPattern(
            EventPattern.builder()
                .source(listOf("solicitation.customer-events"))
                .detailType(listOf(
                    "OrderDelivered",
                    "ProductPurchased",
                    "VideoWatched",
                    "TrackPlayed",
                    "ServiceCompleted",
                    "EventAttended"
                ))
                .build()
        )
        .targets(listOf(LambdaFunction(reactiveLambda.function)))
        .build()
}
