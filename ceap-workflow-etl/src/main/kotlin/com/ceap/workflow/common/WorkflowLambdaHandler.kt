package com.ceap.workflow.common

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest

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
            logger.error(
                "Stage failed: executionId={}, stage={}, error={}",
                context.executionId,
                context.currentStage,
                e.message,
                e
            )
            
            StageResult(
                status = "FAILED",
                stage = context.currentStage,
                recordsProcessed = 0,
                errorMessage = "${e.javaClass.simpleName}: ${e.message}"
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
     * @param context Execution context
     * @return Input data as JsonNode
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
            
            val getObjectRequest = GetObjectRequest.builder()
                .bucket(context.workflowBucket)
                .key(inputKey)
                .build()
            
            val response = s3Client.getObject(getObjectRequest)
            val jsonBytes = response.readAllBytes()
            
            objectMapper.readTree(jsonBytes)
            
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
     * @param context Execution context
     * @param data Output data to write
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
