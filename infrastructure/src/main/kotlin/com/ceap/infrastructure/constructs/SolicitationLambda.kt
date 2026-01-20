package com.ceap.infrastructure.constructs

import software.amazon.awscdk.Duration
import software.amazon.awscdk.services.dynamodb.ITable
import software.amazon.awscdk.services.lambda.Code
import software.amazon.awscdk.services.lambda.Function
import software.amazon.awscdk.services.lambda.Runtime
import software.amazon.awscdk.services.logs.RetentionDays
import software.constructs.Construct

/**
 * Reusable construct for creating Solicitation Platform Lambda functions.
 * 
 * This construct encapsulates common Lambda configuration including:
 * - Java 17 runtime
 * - Automatic IAM permissions for DynamoDB tables
 * - CloudWatch Logs with retention
 * - Environment variables
 * - Memory and timeout configuration
 * 
 * Example usage:
 * ```kotlin
 * val etlLambda = SolicitationLambda(
 *     this, "ETLLambda",
 *     handler = "com.ceap.workflow.ETLHandler::handleRequest",
 *     jarPath = "../ceap-workflow-etl/build/libs/etl-lambda.jar",
 *     tables = listOf(candidatesTable, programConfigTable),
 *     memorySize = 1024,
 *     timeout = Duration.minutes(5)
 * )
 * ```
 */
class SolicitationLambda(
    scope: Construct,
    id: String,
    handler: String,
    jarPath: String,
    environment: Map<String, String> = emptyMap(),
    tables: List<ITable> = emptyList(),
    memorySize: Int = 512,
    timeout: Duration = Duration.minutes(1),
    logRetention: RetentionDays = RetentionDays.ONE_MONTH
) : Construct(scope, id) {
    
    val function: Function = Function.Builder.create(this, "Function")
        .runtime(Runtime.JAVA_17)
        .handler(handler)
        .code(Code.fromAsset(jarPath))
        .memorySize(memorySize)
        .timeout(timeout)
        .environment(environment)
        .logRetention(logRetention)
        .build()
    
    // Automatically grant DynamoDB permissions
    tables.forEach { table ->
        table.grantReadWriteData(function)
    }
}
