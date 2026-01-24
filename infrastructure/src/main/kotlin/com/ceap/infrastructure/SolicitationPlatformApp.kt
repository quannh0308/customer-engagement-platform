package com.ceap.infrastructure

import software.amazon.awscdk.App
import software.amazon.awscdk.Environment
import software.amazon.awscdk.StackProps
import com.ceap.infrastructure.stacks.*

/**
 * Main CDK application for the Customer Engagement & Action Platform (CEAP).
 * 
 * This app creates all infrastructure stacks for the platform including:
 * - Database stack (DynamoDB tables)
 * - Lambda workflow stacks (ETL, Filter, Score, Store, Reactive)
 * - Step Functions workflows
 * - EventBridge rules
 */
fun main() {
    val app = App()
    
    // Get environment from context or use defaults
    val envName = app.node.tryGetContext("environment")?.toString() ?: "dev"
    val account = System.getenv("CDK_DEFAULT_ACCOUNT") ?: System.getenv("AWS_ACCOUNT_ID")
    val region = System.getenv("CDK_DEFAULT_REGION") ?: "us-east-1"
    
    val env = Environment.builder()
        .account(account)
        .region(region)
        .build()
    
    val stackProps = StackProps.builder()
        .env(env)
        .build()
    
    // Create database stack (shared across all workflows)
    val databaseStack = DatabaseStack(app, "SolicitationDatabase-$envName", stackProps, envName)
    
    // Create workflow stacks (each is independent and plug-and-play!)
    val etlStack = EtlWorkflowStack(app, "SolicitationEtlWorkflow-$envName", stackProps, envName, databaseStack)
    val filterStack = FilterWorkflowStack(app, "SolicitationFilterWorkflow-$envName", stackProps, envName, databaseStack)
    val scoreStack = ScoreWorkflowStack(app, "SolicitationScoreWorkflow-$envName", stackProps, envName, databaseStack)
    val storeStack = StoreWorkflowStack(app, "SolicitationStoreWorkflow-$envName", stackProps, envName, databaseStack)
    val reactiveStack = ReactiveWorkflowStack(app, "SolicitationReactiveWorkflow-$envName", stackProps, envName, databaseStack)
    
    // Create orchestration stack (Step Functions + EventBridge)
    OrchestrationStack(
        app, 
        "SolicitationOrchestration-$envName", 
        stackProps, 
        envName,
        etlStack,
        filterStack,
        scoreStack,
        storeStack,
        reactiveStack
    )
    
    app.synth()
}
