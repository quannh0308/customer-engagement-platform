package com.ceap.infrastructure.stacks

import software.amazon.awscdk.Duration
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.constructs.Construct
import com.ceap.infrastructure.constructs.SolicitationLambda

/**
 * ETL Workflow Stack - Extracts and transforms candidate data from sources.
 * 
 * This is a plug-and-play workflow that can be deployed independently.
 * It creates:
 * - ETL Lambda function
 * - Automatic IAM permissions for DynamoDB
 * - CloudWatch Logs
 * - Environment configuration
 */
class EtlWorkflowStack(
    scope: Construct,
    id: String,
    props: StackProps,
    val envName: String,
    val databaseStack: DatabaseStack
) : Stack(scope, id, props) {
    
    val etlLambda = SolicitationLambda(
        this,
        "ETLLambda",
        handler = "com.ceap.workflow.ETLHandler::handleRequest",
        jarPath = "../ceap-workflow-etl/build/libs/ceap-workflow-etl-1.0.0-SNAPSHOT.jar",
        environment = mapOf(
            "ENVIRONMENT" to envName,
            "CANDIDATES_TABLE" to databaseStack.candidatesTable.tableName,
            "PROGRAM_CONFIG_TABLE" to databaseStack.programConfigTable.tableName,
            "BATCH_SIZE" to "1000",
            "LOG_LEVEL" to "INFO"
        ),
        tables = listOf(
            databaseStack.candidatesTable,
            databaseStack.programConfigTable
        ),
        memorySize = 1024,
        timeout = Duration.minutes(5)
    )
    
    // Additional permissions for Athena and S3 (data sources)
    // TODO: Add when implementing data connectors
}
