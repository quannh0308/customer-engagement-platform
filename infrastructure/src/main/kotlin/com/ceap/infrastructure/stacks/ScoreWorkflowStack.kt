package com.ceap.infrastructure.stacks

import software.amazon.awscdk.Duration
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.constructs.Construct
import com.ceap.infrastructure.constructs.SolicitationLambda

/**
 * Score Workflow Stack - Executes scoring models on candidates.
 * 
 * This is a plug-and-play workflow that can be deployed independently.
 */
class ScoreWorkflowStack(
    scope: Construct,
    id: String,
    props: StackProps,
    val envName: String,
    val databaseStack: DatabaseStack
) : Stack(scope, id, props) {
    
    val scoreLambda = SolicitationLambda(
        this,
        "ScoreLambda",
        handler = "com.ceap.workflow.ScoreHandler::handleRequest",
        jarPath = "../ceap-workflow-score/build/libs/ceap-workflow-score-1.0.0-SNAPSHOT.jar",
        environment = mapOf(
            "ENVIRONMENT" to envName,
            "SCORE_CACHE_TABLE" to databaseStack.scoreCacheTable.tableName,
            "CACHE_TTL_HOURS" to "24",
            "LOG_LEVEL" to "INFO"
        ),
        tables = listOf(databaseStack.scoreCacheTable),
        memorySize = 1024,
        timeout = Duration.minutes(2)
    )
    
    // Additional permissions for SageMaker endpoints
    // TODO: Add when implementing scoring engine
}
