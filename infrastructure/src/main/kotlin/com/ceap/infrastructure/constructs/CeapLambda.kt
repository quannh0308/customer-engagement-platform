package com.ceap.infrastructure.constructs

import software.amazon.awscdk.Duration
import software.amazon.awscdk.services.dynamodb.ITable
import software.amazon.awscdk.services.lambda.Code
import software.amazon.awscdk.services.lambda.Function
import software.amazon.awscdk.services.lambda.Runtime
import software.amazon.awscdk.services.logs.RetentionDays
import software.constructs.Construct

/**
 * Lambda naming strategy for backward compatibility during migration.
 * 
 * This enum controls how Lambda function names are generated:
 * - AUTO_GENERATED: Uses CloudFormation auto-generated names (old behavior)
 * - EXPLICIT: Uses explicit function names following pattern {StackName}-{Environment}-{FunctionPurpose}
 * - DUAL: Supports both naming patterns during migration (allows incremental adoption)
 * 
 * Validates: Requirement 10.2
 */
enum class LambdaNamingStrategy {
    /**
     * Auto-generated naming (old behavior).
     * CloudFormation generates names with random suffixes.
     * Example: CeapServingAPI-dev-ReactiveLambdaFunction89310B25-jLlIqkWt5O4x
     */
    AUTO_GENERATED,
    
    /**
     * Explicit naming (new behavior).
     * Uses explicit function names without random suffixes.
     * Example: CeapServingAPI-dev-ReactiveLambdaFunction
     */
    EXPLICIT,
    
    /**
     * Dual naming support (migration mode).
     * Allows deploying both old and new naming patterns simultaneously.
     * Used during incremental migration to new naming convention.
     */
    DUAL
}

/**
 * Reusable construct for creating CEAP Lambda functions.
 * 
 * This construct encapsulates common Lambda configuration including:
 * - Java 17 runtime
 * - Automatic IAM permissions for DynamoDB tables
 * - CloudWatch Logs with retention
 * - Environment variables
 * - Memory and timeout configuration
 * - Backward compatible naming strategies
 * 
 * Example usage with explicit naming:
 * ```kotlin
 * val etlLambda = CeapLambda(
 *     this, "ETLLambda",
 *     handler = "com.ceap.workflow.ETLHandler::handleRequest",
 *     jarPath = "../ceap-workflow-etl/build/libs/etl-lambda.jar",
 *     tables = listOf(candidatesTable, programConfigTable),
 *     memorySize = 1024,
 *     timeout = Duration.minutes(5),
 *     namingStrategy = LambdaNamingStrategy.EXPLICIT,
 *     functionName = "CeapServingAPI-dev-ETLLambda"
 * )
 * ```
 * 
 * Example usage with auto-generated naming (backward compatible):
 * ```kotlin
 * val etlLambda = CeapLambda(
 *     this, "ETLLambda",
 *     handler = "com.ceap.workflow.ETLHandler::handleRequest",
 *     jarPath = "../ceap-workflow-etl/build/libs/etl-lambda.jar",
 *     namingStrategy = LambdaNamingStrategy.AUTO_GENERATED
 * )
 * ```
 * 
 * Validates: Requirement 10.2
 */
class CeapLambda(
    scope: Construct,
    id: String,
    handler: String,
    jarPath: String,
    environment: Map<String, String> = emptyMap(),
    tables: List<ITable> = emptyList(),
    memorySize: Int = 512,
    timeout: Duration = Duration.minutes(1),
    logRetention: RetentionDays = RetentionDays.ONE_MONTH,
    namingStrategy: LambdaNamingStrategy = LambdaNamingStrategy.EXPLICIT,
    functionName: String? = null
) : Construct(scope, id) {
    
    val function: Function
    
    init {
        // Validate naming strategy configuration
        when (namingStrategy) {
            LambdaNamingStrategy.EXPLICIT -> {
                require(functionName != null) {
                    "functionName must be provided when using LambdaNamingStrategy.EXPLICIT. " +
                    "Example: \"CeapServingAPI-dev-ETLLambda\""
                }
            }
            LambdaNamingStrategy.AUTO_GENERATED -> {
                if (functionName != null) {
                    println("WARNING: functionName provided but will be ignored with LambdaNamingStrategy.AUTO_GENERATED")
                }
            }
            LambdaNamingStrategy.DUAL -> {
                // DUAL mode allows both patterns - no validation needed
                // This enables incremental migration where some functions use explicit names
                // and others use auto-generated names
            }
        }
        
        // Create Lambda function with appropriate naming strategy
        function = when (namingStrategy) {
            LambdaNamingStrategy.EXPLICIT -> {
                // Use explicit function name (new behavior)
                Function.Builder.create(this, "Function")
                    .functionName(functionName)  // Explicit name prevents auto-generation
                    .runtime(Runtime.JAVA_17)
                    .handler(handler)
                    .code(Code.fromAsset(jarPath))
                    .memorySize(memorySize)
                    .timeout(timeout)
                    .environment(environment)
                    .logRetention(logRetention)
                    .build()
            }
            LambdaNamingStrategy.AUTO_GENERATED -> {
                // Use auto-generated function name (old behavior)
                Function.Builder.create(this, "Function")
                    // No functionName property - CloudFormation generates name
                    .runtime(Runtime.JAVA_17)
                    .handler(handler)
                    .code(Code.fromAsset(jarPath))
                    .memorySize(memorySize)
                    .timeout(timeout)
                    .environment(environment)
                    .logRetention(logRetention)
                    .build()
            }
            LambdaNamingStrategy.DUAL -> {
                // DUAL mode: Use explicit name if provided, otherwise auto-generate
                val builder = Function.Builder.create(this, "Function")
                    .runtime(Runtime.JAVA_17)
                    .handler(handler)
                    .code(Code.fromAsset(jarPath))
                    .memorySize(memorySize)
                    .timeout(timeout)
                    .environment(environment)
                    .logRetention(logRetention)
                
                // Apply explicit name only if provided
                if (functionName != null) {
                    builder.functionName(functionName)
                }
                
                builder.build()
            }
        }
        
        // Automatically grant DynamoDB permissions
        tables.forEach { table ->
            table.grantReadWriteData(function)
        }
    }
}
