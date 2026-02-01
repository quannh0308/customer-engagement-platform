package com.ceap.workflow.common

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

/**
 * Abstract base class for Lambda handlers in Step Functions workflows.
 * 
 * This class implements the S3-based orchestration pattern where:
 * - Each Lambda reads input from the previous stage's S3 output
 * - Each Lambda writes output to S3 for the next stage
 * - The first stage uses initialData from the SQS message instead of S3
 * - All stages follow convention-based S3 path resolution
 * 
 * Subclasses must implement the [processData] method to define stage-specific logic.
 * 
 * Key Features:
 * - Convention-based S3 path construction (no hardcoded paths)
 * - Automatic first-stage detection (uses initialData vs S3)
 * - Comprehensive logging for execution context and S3 paths
 * - Loose coupling between stages (enables reordering and independent testing)
 * 
 * Validates: Requirements 2.2, 2.3, 2.4, 3.1, 3.2, 9.5, 9.6
 */
abstract class WorkflowLambdaHandler : RequestHandler<ExecutionContext, StageResult> {
    
    protected val logger = LoggerFactory.getLogger(this::class.java)
    protected val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val s3Client: S3Client = S3Client.create()
    
    /**
     * Handles the Lambda request by orchestrating S3 I/O and data processing.
     * 
     * Execution flow:
     * 1. Log execution context (executionId, currentStage)
     * 2. Read input data (from S3 or initialData)
     * 3. Process data (implemented by subclass)
     * 4. Write output to S3
     * 5. Return stage result
     * 
     * @param context Execution context from Step Functions
     * @param lambdaContext AWS Lambda context
     * @return Stage result with status and metrics
     */
    override fun handleRequest(context: ExecutionContext, lambdaContext: Context): StageResult {
        // Log execution context for observability (Requirement 9.5)
        logger.info(
            "Starting stage: executionId={}, currentStage={}, previousStage={}",
            context.executionId,
            context.currentStage,
            context.previousStage ?: "none (first stage)"
        )
        
        return try {
            // Read input data
            val inputData = readInput(context)
            
            // Process data (implemented by subclass)
            val outputData = processData(inputData)
            
            // Write output to S3
            writeOutput(context, outputData)
            
            // Count records processed (if data is array)
            val recordsProcessed = if (outputData.isArray) {
                outputData.size()
            } else {
                1
            }
            
            logger.info(
                "Stage completed successfully: executionId={}, stage={}, recordsProcessed={}",
                context.executionId,
                context.currentStage,
                recordsProcessed
            )
            
            StageResult(
                status = "SUCCESS",
                stage = context.currentStage,
                recordsProcessed = recordsProcessed
            )
            
        } catch (e: Exception) {
            // Log comprehensive error details for debugging (Requirement 8.4)
            logger.error(
                "Stage failed: executionId={}, stage={}, errorType={}, errorMessage={}",
                context.executionId,
                context.currentStage,
                e.javaClass.simpleName,
                e.message,
                e // Include full stack trace in logs
            )
            
            // Build detailed error message with stack trace
            val stackTrace = e.stackTrace.take(5).joinToString("\n") { 
                "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})"
            }
            
            val errorMessage = buildString {
                append("${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
                if (stackTrace.isNotEmpty()) {
                    append("\nStack trace:\n")
                    append(stackTrace)
                }
                // Include cause if present
                e.cause?.let { cause ->
                    append("\nCaused by: ${cause.javaClass.simpleName}: ${cause.message}")
                }
            }
            
            StageResult(
                status = "FAILED",
                stage = context.currentStage,
                recordsProcessed = 0,
                errorMessage = errorMessage
            )
        }
    }
    
    /**
     * Reads input data from S3 or initialData based on stage position.
     * 
     * Convention-based path resolution (Requirement 3.1):
     * - First stage (previousStage == null): Use initialData from SQS message
     * - Non-first stage: Read from S3 at executions/{executionId}/{previousStage}/output.json
     * 
     * Error Handling (Requirements 8.1, 8.2, 8.3):
     * - Transient errors (503 SlowDown, 500 InternalError): Throw to trigger retry
     * - Permanent errors (403 Forbidden, 404 NoSuchKey): Throw with clear message
     * 
     * @param context Execution context
     * @return Input data as JsonNode
     * @throws S3Exception for S3-specific errors (access denied, not found, throttling)
     * @throws IllegalStateException for configuration errors
     */
    private fun readInput(context: ExecutionContext): JsonNode {
        return if (context.previousStage != null) {
            // Non-first stage: Read from previous stage S3 output (Requirement 2.3)
            val inputKey = "executions/${context.executionId}/${context.previousStage}/output.json"
            
            // Log S3 path for debugging (Requirement 9.6)
            logger.info(
                "Reading input from S3: bucket={}, key={}",
                context.workflowBucket,
                inputKey
            )
            
            try {
                val getObjectRequest = GetObjectRequest.builder()
                    .bucket(context.workflowBucket)
                    .key(inputKey)
                    .build()
                
                val response = s3Client.getObject(getObjectRequest)
                val jsonBytes = response.readAllBytes()
                
                objectMapper.readTree(jsonBytes)
                
            } catch (e: NoSuchKeyException) {
                // Permanent error: Object not found (404)
                // This indicates previous stage failed or path is incorrect
                logger.error(
                    "S3 object not found (404): bucket={}, key={}. " +
                    "Previous stage may have failed or path is incorrect.",
                    context.workflowBucket,
                    inputKey
                )
                throw IllegalStateException(
                    "Input data not found in S3. Previous stage '${context.previousStage}' " +
                    "may have failed or not written output. Path: s3://${context.workflowBucket}/$inputKey",
                    e
                )
                
            } catch (e: S3Exception) {
                when (e.statusCode()) {
                    403 -> {
                        // Permanent error: Access denied (403)
                        // This indicates IAM permission issue
                        logger.error(
                            "S3 access denied (403): bucket={}, key={}. " +
                            "Check IAM permissions for Lambda execution role.",
                            context.workflowBucket,
                            inputKey
                        )
                        throw IllegalStateException(
                            "Access denied to S3 bucket. Check IAM permissions for Lambda execution role. " +
                            "Path: s3://${context.workflowBucket}/$inputKey",
                            e
                        )
                    }
                    
                    503 -> {
                        // Transient error: Throttling (503 SlowDown)
                        // Throw to trigger Step Functions retry
                        logger.warn(
                            "S3 throttling (503): bucket={}, key={}. " +
                            "Will retry with exponential backoff.",
                            context.workflowBucket,
                            inputKey
                        )
                        throw S3Exception.builder()
                            .message("S3 throttling (503 SlowDown). Retrying with backoff.")
                            .statusCode(503)
                            .cause(e)
                            .build()
                    }
                    
                    500 -> {
                        // Transient error: Internal server error (500)
                        // Throw to trigger Step Functions retry
                        logger.warn(
                            "S3 internal error (500): bucket={}, key={}. " +
                            "Will retry with exponential backoff.",
                            context.workflowBucket,
                            inputKey
                        )
                        throw S3Exception.builder()
                            .message("S3 internal error (500). Retrying with backoff.")
                            .statusCode(500)
                            .cause(e)
                            .build()
                    }
                    
                    else -> {
                        // Unknown S3 error - log and rethrow
                        logger.error(
                            "S3 error ({}): bucket={}, key={}, message={}",
                            e.statusCode(),
                            context.workflowBucket,
                            inputKey,
                            e.message
                        )
                        throw e
                    }
                }
                
            } catch (e: SdkException) {
                // Network or SDK errors - likely transient
                logger.warn(
                    "SDK error reading from S3: bucket={}, key={}, message={}. " +
                    "Will retry with exponential backoff.",
                    context.workflowBucket,
                    inputKey,
                    e.message
                )
                throw e
            }
            
        } else {
            // First stage: Use initial SQS message data (Requirement 2.4)
            logger.info("Using initial data from SQS message (first stage)")
            
            context.initialData
                ?: throw IllegalStateException("initialData is null for first stage")
        }
    }
    
    /**
     * Writes output data to S3 following convention-based path.
     * 
     * Convention-based path resolution (Requirement 3.2):
     * - Output path: executions/{executionId}/{currentStage}/output.json
     * 
     * Error Handling (Requirements 8.1, 8.2, 8.3):
     * - Transient errors (503 SlowDown, 500 InternalError): Throw to trigger retry
     * - Permanent errors (403 Forbidden): Throw with clear message
     * 
     * @param context Execution context
     * @param data Output data to write
     * @throws S3Exception for S3-specific errors (access denied, throttling)
     */
    private fun writeOutput(context: ExecutionContext, data: JsonNode) {
        // Convention-based output path (Requirement 2.2)
        val outputKey = "executions/${context.executionId}/${context.currentStage}/output.json"
        
        // Log S3 path for debugging (Requirement 9.6)
        logger.info(
            "Writing output to S3: bucket={}, key={}",
            context.workflowBucket,
            outputKey
        )
        
        try {
            val jsonBytes = objectMapper.writeValueAsBytes(data)
            
            val putObjectRequest = PutObjectRequest.builder()
                .bucket(context.workflowBucket)
                .key(outputKey)
                .contentType("application/json")
                .build()
            
            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(jsonBytes))
            
            logger.info(
                "Output written successfully: bucket={}, key={}, sizeBytes={}",
                context.workflowBucket,
                outputKey,
                jsonBytes.size
            )
            
        } catch (e: S3Exception) {
            when (e.statusCode()) {
                403 -> {
                    // Permanent error: Access denied (403)
                    // This indicates IAM permission issue
                    logger.error(
                        "S3 access denied (403): bucket={}, key={}. " +
                        "Check IAM permissions for Lambda execution role.",
                        context.workflowBucket,
                        outputKey
                    )
                    throw IllegalStateException(
                        "Access denied to S3 bucket. Check IAM permissions for Lambda execution role. " +
                        "Path: s3://${context.workflowBucket}/$outputKey",
                        e
                    )
                }
                
                503 -> {
                    // Transient error: Throttling (503 SlowDown)
                    // Throw to trigger Step Functions retry
                    logger.warn(
                        "S3 throttling (503): bucket={}, key={}. " +
                        "Will retry with exponential backoff.",
                        context.workflowBucket,
                        outputKey
                    )
                    throw S3Exception.builder()
                        .message("S3 throttling (503 SlowDown). Retrying with backoff.")
                        .statusCode(503)
                        .cause(e)
                        .build()
                }
                
                500 -> {
                    // Transient error: Internal server error (500)
                    // Throw to trigger Step Functions retry
                    logger.warn(
                        "S3 internal error (500): bucket={}, key={}. " +
                        "Will retry with exponential backoff.",
                        context.workflowBucket,
                        outputKey
                    )
                    throw S3Exception.builder()
                        .message("S3 internal error (500). Retrying with backoff.")
                        .statusCode(500)
                        .cause(e)
                        .build()
                }
                
                else -> {
                    // Unknown S3 error - log and rethrow
                    logger.error(
                        "S3 error ({}): bucket={}, key={}, message={}",
                        e.statusCode(),
                        context.workflowBucket,
                        outputKey,
                        e.message
                    )
                    throw e
                }
            }
            
        } catch (e: SdkException) {
            // Network or SDK errors - likely transient
            logger.warn(
                "SDK error writing to S3: bucket={}, key={}, message={}. " +
                "Will retry with exponential backoff.",
                context.workflowBucket,
                outputKey,
                e.message
            )
            throw e
        }
    }
    
    /**
     * Processes input data and produces output data.
     * 
     * This method must be implemented by each stage to define stage-specific logic.
     * The implementation should:
     * - Parse input data structure
     * - Apply business logic transformations
     * - Return output data structure for next stage
     * 
     * @param input Input data from previous stage or initialData
     * @return Output data for next stage
     */
    abstract fun processData(input: JsonNode): JsonNode
}
