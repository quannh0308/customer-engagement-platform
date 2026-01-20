package com.ceap.infrastructure.stacks

import software.amazon.awscdk.Duration
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.constructs.Construct
import com.ceap.infrastructure.constructs.SolicitationLambda

/**
 * Store Workflow Stack - Batch writes candidates to DynamoDB.
 * 
 * This is a plug-and-play workflow that can be deployed independently.
 */
class StoreWorkflowStack(
    scope: Construct,
    id: String,
    props: StackProps,
    val envName: String,
    val databaseStack: DatabaseStack
) : Stack(scope, id, props) {
    
    val storeLambda = SolicitationLambda(
        this,
        "StoreLambda",
        handler = "com.ceap.workflow.StoreHandler::handleRequest",
        jarPath = "../ceap-workflow-store/build/libs/ceap-workflow-store-1.0.0-SNAPSHOT.jar",
        environment = mapOf(
            "ENVIRONMENT" to envName,
            "CANDIDATES_TABLE" to databaseStack.candidatesTable.tableName,
            "BATCH_SIZE" to "25",
            "LOG_LEVEL" to "INFO"
        ),
        tables = listOf(databaseStack.candidatesTable),
        memorySize = 512,
        timeout = Duration.seconds(60)
    )
}
