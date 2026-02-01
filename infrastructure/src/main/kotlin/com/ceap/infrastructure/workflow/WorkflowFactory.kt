package com.ceap.infrastructure.workflow

import com.ceap.infrastructure.constructs.CeapGlueJob
import com.ceap.infrastructure.constructs.GlueJobConfiguration
import software.amazon.awscdk.Duration
import software.amazon.awscdk.services.events.Rule
import software.amazon.awscdk.services.events.EventPattern
import software.amazon.awscdk.services.glue.CfnJob
import software.amazon.awscdk.services.logs.LogGroup
import software.amazon.awscdk.services.logs.RetentionDays
import software.amazon.awscdk.services.pipes.CfnPipe
import software.amazon.awscdk.services.stepfunctions.*
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvoke
import software.amazon.awscdk.services.stepfunctions.tasks.GlueStartJobRun
import software.constructs.Construct

/**
 * Factory for creating Step Functions workflows with S3-based orchestration.
 * 
 * This factory provides methods to create Express and Standard workflows
 * following AWS best practices for large-scale data processing.
 * 
 * Key features:
 * - Convention-based S3 path resolution
 * - Automatic retry configuration
 * - CloudWatch Logs and X-Ray tracing
 * - Support for Lambda and Glue steps
 */
object WorkflowFactory {
    
    /**
     * Determines the appropriate EventBridge Pipe invocation type based on workflow type.
     * 
     * This function implements automatic invocation type configuration:
     * - EXPRESS workflows use REQUEST_RESPONSE (synchronous)
     * - STANDARD workflows use FIRE_AND_FORGET (asynchronous)
     * 
     * Rationale:
     * - Express workflows complete quickly (<5 minutes) and benefit from synchronous
     *   invocation to enable immediate failure detection and SQS message retry
     * - Standard workflows can run for extended periods and use asynchronous invocation
     *   to avoid blocking the EventBridge Pipe
     * 
     * @param workflowType The workflow type (EXPRESS or STANDARD)
     * @return Invocation type string: "REQUEST_RESPONSE" or "FIRE_AND_FORGET"
     * 
     * Validates: Requirement 6.5
     */
    private fun getInvocationType(workflowType: WorkflowType): String {
        return when (workflowType) {
            WorkflowType.EXPRESS -> "REQUEST_RESPONSE"  // Synchronous for Express
            WorkflowType.STANDARD -> "FIRE_AND_FORGET"  // Asynchronous for Standard
        }
    }
    
    /**
     * Creates an Express workflow for fast processing pipelines.
     * 
     * Express workflows are designed for:
     * - All stages complete within 5 minutes
     * - Lambda-only processing (no Glue jobs)
     * - Synchronous invocation (REQUEST_RESPONSE)
     * - Cost-optimized ($1 per million transitions)
     * 
     * Features:
     * - Sequential Lambda chaining with S3 intermediate storage
     * - Automatic retry logic (2 attempts, 20s interval, 2x backoff)
     * - Result path set to DISCARD to prevent large responses
     * - CloudWatch Logs with full execution data
     * - X-Ray tracing enabled
     * - Support for incremental migration (HYBRID mode)
     * 
     * @param scope CDK construct scope
     * @param config Workflow configuration
     * @return Created StateMachine
     * 
     * @throws IllegalArgumentException if config contains Glue steps
     * 
     * Validates: Requirements 4.1, 4.4, 4.5, 4.6, 4.7, 10.3
     */
    fun createExpressWorkflow(
        scope: Construct,
        config: WorkflowConfiguration
    ): StateMachine {
        // Validate: Express workflows only support Lambda steps
        require(config.workflowType == WorkflowType.EXPRESS) {
            "createExpressWorkflow requires WorkflowType.EXPRESS. " +
            "Received: ${config.workflowType}"
        }
        
        val hasGlueSteps = config.steps.any { it is WorkflowStepType.Glue }
        require(!hasGlueSteps) {
            "Express workflows only support Lambda steps. Use createStandardWorkflow for Glue jobs. " +
            "Found ${config.steps.count { it is WorkflowStepType.Glue }} Glue step(s)."
        }
        
        // Extract Lambda steps
        val lambdaSteps = config.steps.map { 
            (it as WorkflowStepType.Lambda).step 
        }
        
        // Create Lambda invoke tasks with execution context payload
        val states = lambdaSteps.mapIndexed { index, step ->
            val lambdaFunction = config.lambdaFunctions[step.lambdaFunctionKey]
                ?: error("Lambda function not found for key: ${step.lambdaFunctionKey}")
            
            // Determine previous stage name (null for first stage)
            val previousStage = if (index > 0) {
                lambdaSteps[index - 1].stateName
            } else {
                null
            }
            
            // Create Lambda invoke task with execution context payload
            // Payload structure varies based on migration mode and step configuration
            // 
            // For S3 orchestration (usesS3Orchestration=true):
            // {
            //   "executionId": "$.Execution.Name",
            //   "currentStage": "ETLStage",
            //   "previousStage": null | "PreviousStage",
            //   "workflowBucket": "ceap-workflow-dev-123456789",
            //   "initialData": "$.body" (parsed from SQS message)
            // }
            //
            // For legacy implementation (usesS3Orchestration=false):
            // {
            //   "data": "$.body" (parsed from SQS message or previous stage output)
            // }
            val payload = if (step.usesS3Orchestration) {
                // New S3-based orchestration payload (Requirement 10.3)
                TaskInput.fromObject(mapOf(
                    "executionId" to JsonPath.stringAt("\$.Execution.Name"),
                    "currentStage" to step.stateName,
                    "previousStage" to previousStage,
                    "workflowBucket" to config.workflowBucket.bucketName,
                    "initialData" to JsonPath.stringToJson(JsonPath.stringAt("\$[0].body"))
                ))
            } else {
                // Legacy payload for backward compatibility (Requirement 10.3)
                // Pass data directly in payload (old behavior)
                if (index == 0) {
                    // First stage: use SQS message body
                    TaskInput.fromObject(mapOf(
                        "data" to JsonPath.stringToJson(JsonPath.stringAt("\$[0].body"))
                    ))
                } else {
                    // Subsequent stages: use previous stage output
                    // Note: This assumes previous stage returns data in response
                    TaskInput.fromJsonPathAt("\$.Payload")
                }
            }
            
            val lambdaTask = LambdaInvoke.Builder.create(scope, step.stateName)
                .lambdaFunction(lambdaFunction)
                .payload(payload)
                .resultPath(if (step.usesS3Orchestration) JsonPath.DISCARD else JsonPath.stringAt("\$.Payload"))
                .retryOnServiceExceptions(false)  // We configure retries manually
                .build()
            
            // Add retry configuration (Requirement 4.5)
            // - Maximum 2 attempts
            // - Initial interval: 20 seconds
            // - Backoff rate: 2x (20s, 40s)
            // - No jitter (prevents rapid consecutive retries)
            lambdaTask.addRetry(
                RetryProps.builder()
                    .errors(listOf(Errors.ALL))
                    .interval(Duration.seconds(config.retryConfig.lambdaIntervalSeconds.toLong()))
                    .maxAttempts(config.retryConfig.lambdaMaxAttempts)
                    .backoffRate(config.retryConfig.lambdaBackoffRate)
                    .jitterStrategy(JitterType.NONE)
                    .build()
            )
            
            lambdaTask
        }
        
        // Chain states sequentially (Requirement 4.4)
        // ETL → Filter → Score → Store → Reactive
        states.forEachIndexed { index, state ->
            if (index > 0) {
                states[index - 1].next(state)
            }
        }
        
        // Create CloudWatch Logs log group for Step Functions
        // Retention: 14 days (Requirement 9.3)
        val logGroup = LogGroup.Builder.create(scope, "${config.workflowName}Logs")
            .logGroupName("/aws/stepfunctions/${config.workflowName}")
            .retention(RetentionDays.TWO_WEEKS)
            .build()
        
        // Create Express Step Function (Requirement 4.1)
        val stateMachine = StateMachine.Builder.create(scope, "${config.workflowName}StateMachine")
            .stateMachineName("${config.workflowName}-Express")
            .definitionBody(DefinitionBody.fromChainable(states[0]))
            .stateMachineType(StateMachineType.EXPRESS)  // Express workflow type
            .timeout(Duration.minutes(5))  // 5-minute maximum duration
            .tracingEnabled(true)  // Enable X-Ray tracing (Requirement 4.7, 9.1)
            .logs(LogOptions.builder()
                .destination(logGroup)
                .includeExecutionData(true)  // Include full execution data (Requirement 4.7, 9.2)
                .level(LogLevel.ALL)  // Log all events (Requirement 9.2)
                .build())
            .build()
        
        // Grant S3 permissions to Step Functions execution role
        // Allows Lambda functions to read/write to workflow bucket
        config.workflowBucket.grantReadWrite(stateMachine)
        
        // Create EventBridge Pipe to connect SQS queue to Step Function
        // Uses REQUEST_RESPONSE (synchronous) invocation type (Requirement 4.2)
        // Automatically configured based on workflow type (Requirement 6.5)
        createEventBridgePipe(
            scope = scope,
            pipeName = "${config.workflowName}-pipe",
            sourceQueue = config.sourceQueue,
            targetStateMachine = stateMachine,
            invocationType = getInvocationType(config.workflowType)
        )
        
        return stateMachine
    }
    
    /**
     * Creates a Standard workflow for long-running pipelines.
     * 
     * Standard workflows are designed for:
     * - Any stage can exceed 5 minutes
     * - Support for Glue jobs and Lambda functions
     * - Asynchronous invocation (FIRE_AND_FORGET)
     * - Long-running processes (up to 1 year)
     * 
     * Features:
     * - Flexible step composition (mix Lambda and Glue steps)
     * - Glue job integration with RUN_JOB pattern (waits for completion)
     * - Automatic retry logic (Lambda: 20s, Glue: 5min)
     * - CloudWatch Logs with full execution data
     * - X-Ray tracing enabled
     * - EventBridge rule for failure detection
     * - Support for incremental migration (HYBRID mode)
     * 
     * @param scope CDK construct scope
     * @param config Workflow configuration
     * @return Created StateMachine
     * 
     * Validates: Requirements 5.1, 5.3, 5.4, 5.5, 5.7, 10.3
     */
    fun createStandardWorkflow(
        scope: Construct,
        config: WorkflowConfiguration
    ): StateMachine {
        // Validate: Standard workflow type
        require(config.workflowType == WorkflowType.STANDARD) {
            "createStandardWorkflow requires WorkflowType.STANDARD. " +
            "Received: ${config.workflowType}"
        }
        
        // Build chainable steps (mix of Lambda and Glue)
        val chainableSteps = mutableListOf<IChainable>()
        
        config.steps.forEachIndexed { index, stepType ->
            // Determine previous stage name (null for first stage)
            val previousStage = if (index > 0) {
                when (val prev = config.steps[index - 1]) {
                    is WorkflowStepType.Lambda -> prev.step.stateName
                    is WorkflowStepType.Glue -> prev.step.stateName
                }
            } else {
                null
            }
            
            when (stepType) {
                is WorkflowStepType.Lambda -> {
                    val step = stepType.step
                    val lambdaFunction = config.lambdaFunctions[step.lambdaFunctionKey]
                        ?: error("Lambda function not found for key: ${step.lambdaFunctionKey}")
                    
                    // Create Lambda invoke task with execution context payload
                    // Payload structure varies based on migration mode and step configuration
                    // 
                    // For S3 orchestration (usesS3Orchestration=true):
                    // {
                    //   "executionId": "$.Execution.Name",
                    //   "currentStage": "ETLStage",
                    //   "previousStage": null | "PreviousStage",
                    //   "workflowBucket": "ceap-workflow-dev-123456789",
                    //   "initialData": "$.body" (parsed from SQS message)
                    // }
                    //
                    // For legacy implementation (usesS3Orchestration=false):
                    // {
                    //   "data": "$.body" (parsed from SQS message or previous stage output)
                    // }
                    val payload = if (step.usesS3Orchestration) {
                        // New S3-based orchestration payload (Requirement 10.3)
                        TaskInput.fromObject(mapOf(
                            "executionId" to JsonPath.stringAt("\$.Execution.Name"),
                            "currentStage" to step.stateName,
                            "previousStage" to previousStage,
                            "workflowBucket" to config.workflowBucket.bucketName,
                            "initialData" to JsonPath.stringToJson(JsonPath.stringAt("\$[0].body"))
                        ))
                    } else {
                        // Legacy payload for backward compatibility (Requirement 10.3)
                        // Pass data directly in payload (old behavior)
                        if (index == 0) {
                            // First stage: use SQS message body
                            TaskInput.fromObject(mapOf(
                                "data" to JsonPath.stringToJson(JsonPath.stringAt("\$[0].body"))
                            ))
                        } else {
                            // Subsequent stages: use previous stage output
                            // Note: This assumes previous stage returns data in response
                            TaskInput.fromJsonPathAt("\$.Payload")
                        }
                    }
                    
                    val lambdaTask = LambdaInvoke.Builder.create(scope, step.stateName)
                        .lambdaFunction(lambdaFunction)
                        .payload(payload)
                        .resultPath(if (step.usesS3Orchestration) JsonPath.DISCARD else JsonPath.stringAt("\$.Payload"))
                        .retryOnServiceExceptions(false)
                        .build()
                    
                    // Add Lambda retry configuration (Requirement 5.7)
                    // - Maximum 2 attempts
                    // - Initial interval: 20 seconds
                    // - Backoff rate: 2x (20s, 40s)
                    lambdaTask.addRetry(
                        RetryProps.builder()
                            .errors(listOf(Errors.ALL))
                            .interval(Duration.seconds(config.retryConfig.lambdaIntervalSeconds.toLong()))
                            .maxAttempts(config.retryConfig.lambdaMaxAttempts)
                            .backoffRate(config.retryConfig.lambdaBackoffRate)
                            .jitterStrategy(JitterType.NONE)
                            .build()
                    )
                    
                    chainableSteps.add(lambdaTask)
                }
                
                is WorkflowStepType.Glue -> {
                    val step = stepType.step
                    
                    // Create Glue job task with RUN_JOB integration pattern (Requirement 5.4)
                    // This waits for the Glue job to complete before proceeding
                    val glueTask = GlueStartJobRun.Builder.create(scope, step.stateName)
                        .glueJobName(step.glueJobName)
                        .integrationPattern(IntegrationPattern.RUN_JOB)  // Wait for completion
                        .arguments(TaskInput.fromObject(mapOf(
                            // Pass execution context to Glue job (Requirement 5.5)
                            "--execution-id" to JsonPath.stringAt("\$.Execution.Name"),
                            "--input-bucket" to config.workflowBucket.bucketName,
                            "--input-key" to if (previousStage != null) {
                                "executions/\${JsonPath.stringAt(\"\$.Execution.Name\")}/$previousStage/output.json"
                            } else {
                                "executions/\${JsonPath.stringAt(\"\$.Execution.Name\")}/initial/output.json"
                            },
                            "--output-bucket" to config.workflowBucket.bucketName,
                            "--output-key" to "executions/\${JsonPath.stringAt(\"\$.Execution.Name\")}/${step.stateName}/output.json",
                            "--current-stage" to step.stateName,
                            "--previous-stage" to (previousStage ?: "initial")
                        )))
                        .resultPath(JsonPath.stringAt("\$.glueJobResult"))
                        .build()
                    
                    // Add Glue retry configuration (Requirement 5.7)
                    // - Maximum 2 attempts
                    // - Initial interval: 5 minutes
                    // - Backoff rate: 2x (5min, 10min)
                    glueTask.addRetry(
                        RetryProps.builder()
                            .errors(listOf("States.ALL"))
                            .interval(Duration.minutes(config.retryConfig.glueIntervalMinutes.toLong()))
                            .maxAttempts(config.retryConfig.glueMaxAttempts)
                            .backoffRate(config.retryConfig.glueBackoffRate)
                            .jitterStrategy(JitterType.NONE)
                            .build()
                    )
                    
                    chainableSteps.add(glueTask)
                }
            }
        }
        
        // Chain all steps sequentially (Requirement 5.3)
        // Supports mixing Lambda and Glue steps in any order
        chainableSteps.forEachIndexed { index, step ->
            if (index > 0) {
                (chainableSteps[index - 1] as INextable).next(step)
            }
        }
        
        // Create CloudWatch Logs log group for Step Functions
        // Retention: 14 days (Requirement 9.3)
        val logGroup = LogGroup.Builder.create(scope, "${config.workflowName}Logs")
            .logGroupName("/aws/stepfunctions/${config.workflowName}")
            .retention(RetentionDays.TWO_WEEKS)
            .build()
        
        // Create Standard Step Function (Requirement 5.1)
        val stateMachine = StateMachine.Builder.create(scope, "${config.workflowName}StateMachine")
            .stateMachineName("${config.workflowName}-Standard")
            .definitionBody(DefinitionBody.fromChainable(chainableSteps[0]))
            .stateMachineType(StateMachineType.STANDARD)  // Standard workflow type
            .tracingEnabled(true)  // Enable X-Ray tracing (Requirement 9.1)
            .logs(LogOptions.builder()
                .destination(logGroup)
                .includeExecutionData(true)  // Include full execution data (Requirement 9.2)
                .level(LogLevel.ALL)  // Log all events (Requirement 9.2)
                .build())
            .build()
        
        // Grant S3 permissions to Step Functions execution role
        // Allows Lambda functions and Glue jobs to read/write to workflow bucket
        config.workflowBucket.grantReadWrite(stateMachine)
        
        // Create EventBridge Pipe to connect SQS queue to Step Function
        // Uses FIRE_AND_FORGET (asynchronous) invocation type (Requirement 5.2)
        // Automatically configured based on workflow type (Requirement 6.5)
        createEventBridgePipe(
            scope = scope,
            pipeName = "${config.workflowName}-pipe",
            sourceQueue = config.sourceQueue,
            targetStateMachine = stateMachine,
            invocationType = getInvocationType(config.workflowType)
        )
        
        // Create EventBridge rule for failure detection (Requirement 8.7)
        // Monitors execution status changes and triggers alerts
        createFailureDetectionRule(
            scope = scope,
            ruleName = "${config.workflowName}-failure-rule",
            stateMachine = stateMachine
        )
        
        return stateMachine
    }
    
    /**
     * Creates an EventBridge rule to detect Standard workflow failures.
     * 
     * This rule monitors Step Functions execution status changes and triggers
     * alerts when executions fail, time out, or are aborted.
     * 
     * Monitored statuses:
     * - FAILED: Execution failed due to an error
     * - TIMED_OUT: Execution exceeded maximum duration
     * - ABORTED: Execution was manually aborted
     * 
     * @param scope CDK construct scope
     * @param ruleName Name of the EventBridge rule
     * @param stateMachine Step Functions state machine to monitor
     * 
     * Validates: Requirement 8.7
     */
    private fun createFailureDetectionRule(
        scope: Construct,
        ruleName: String,
        stateMachine: StateMachine
    ) {
        Rule.Builder.create(scope, ruleName)
            .ruleName(ruleName)
            .eventPattern(EventPattern.builder()
                .source(listOf("aws.states"))
                .detailType(listOf("Step Functions Execution Status Change"))
                .detail(mapOf(
                    "status" to listOf("FAILED", "TIMED_OUT", "ABORTED"),
                    "stateMachineArn" to listOf(stateMachine.stateMachineArn)
                ))
                .build())
            // Note: Targets (SNS topic or Lambda) should be added by the caller
            // based on their specific alerting requirements
            .build()
    }
    
    /**
     * Creates an EventBridge Pipe to connect SQS queue to Step Functions.
     * 
     * EventBridge Pipes provide a managed integration between SQS and Step Functions:
     * - Automatic polling of SQS messages
     * - Batch size configuration
     * - Invocation type selection (REQUEST_RESPONSE or FIRE_AND_FORGET)
     * - Built-in error handling and retry logic
     * 
     * For Express workflows:
     * - Uses REQUEST_RESPONSE (synchronous) invocation
     * - Returns failure to SQS on execution failure (enables message retry)
     * 
     * For Standard workflows:
     * - Uses FIRE_AND_FORGET (asynchronous) invocation
     * - Does not wait for execution completion
     * 
     * @param scope CDK construct scope
     * @param pipeName Name of the EventBridge Pipe
     * @param sourceQueue SQS queue to poll messages from
     * @param targetStateMachine Step Functions state machine to invoke
     * @param invocationType Invocation type: "REQUEST_RESPONSE" or "FIRE_AND_FORGET"
     * 
     * Validates: Requirements 4.2, 11.4
     */
    private fun createEventBridgePipe(
        scope: Construct,
        pipeName: String,
        sourceQueue: software.amazon.awscdk.services.sqs.IQueue,
        targetStateMachine: StateMachine,
        invocationType: String
    ) {
        // Create IAM role for EventBridge Pipe
        val pipeRole = software.amazon.awscdk.services.iam.Role.Builder.create(scope, "${pipeName}Role")
            .assumedBy(software.amazon.awscdk.services.iam.ServicePrincipal("pipes.amazonaws.com"))
            .build()
        
        // Grant SQS permissions to Pipe role
        sourceQueue.grantConsumeMessages(pipeRole)
        
        // Grant Step Functions permissions to Pipe role
        targetStateMachine.grantStartExecution(pipeRole)
        
        // Create EventBridge Pipe
        CfnPipe.Builder.create(scope, pipeName)
            .name(pipeName)
            .roleArn(pipeRole.roleArn)
            .source(sourceQueue.queueArn)
            .sourceParameters(CfnPipe.PipeSourceParametersProperty.builder()
                .sqsQueueParameters(CfnPipe.PipeSourceSqsQueueParametersProperty.builder()
                    .batchSize(1)  // Process one message at a time (Requirement 4.2)
                    .build())
                .build())
            .target(targetStateMachine.stateMachineArn)
            .targetParameters(CfnPipe.PipeTargetParametersProperty.builder()
                .stepFunctionStateMachineParameters(
                    CfnPipe.PipeTargetStateMachineParametersProperty.builder()
                        .invocationType(invocationType)  // REQUEST_RESPONSE or FIRE_AND_FORGET
                        .build()
                )
                .build())
            .desiredState("RUNNING")  // Start the pipe immediately
            .build()
    }
    
    /**
     * Creates a Glue job for use in Standard workflows.
     * 
     * This is a convenience method that wraps the CeapGlueJob construct
     * with sensible defaults for workflow integration.
     * 
     * The created Glue job:
     * - Follows the same S3 path convention as Lambda functions
     * - Receives execution context from Step Functions
     * - Reads input from previous stage's S3 output
     * - Writes output to its own S3 path
     * 
     * Example Usage:
     * ```kotlin
     * val glueJob = WorkflowFactory.createGlueJob(
     *     scope = this,
     *     id = "HeavyETLJob",
     *     jobName = "ceap-heavy-etl-job",
     *     scriptLocation = "s3://my-scripts-bucket/workflow_etl_template.py",
     *     workflowBucket = workflowBucket,
     *     numberOfWorkers = 10,
     *     timeout = 240
     * )
     * ```
     * 
     * @param scope CDK construct scope
     * @param id Construct ID
     * @param jobName Name of the Glue job
     * @param scriptLocation S3 location of the PySpark script
     * @param workflowBucket S3 bucket for workflow intermediate storage
     * @param glueVersion Glue version (default: "4.0")
     * @param workerType Worker type (default: "G.1X")
     * @param numberOfWorkers Number of workers (default: 2)
     * @param timeout Job timeout in minutes (default: 120)
     * @param description Job description
     * @return Created CeapGlueJob construct
     * 
     * Validates: Requirement 7.1, 11.2
     */
    fun createGlueJob(
        scope: Construct,
        id: String,
        jobName: String,
        scriptLocation: String,
        workflowBucket: software.amazon.awscdk.services.s3.IBucket,
        glueVersion: String = "4.0",
        workerType: String = "G.1X",
        numberOfWorkers: Int = 2,
        timeout: Int = 120,
        description: String = "CEAP workflow Glue job for ETL processing"
    ): CeapGlueJob {
        return CeapGlueJob(
            scope = scope,
            id = id,
            config = GlueJobConfiguration(
                jobName = jobName,
                scriptLocation = scriptLocation,
                workflowBucket = workflowBucket,
                glueVersion = glueVersion,
                workerType = workerType,
                numberOfWorkers = numberOfWorkers,
                maxRetries = 0,  // Retries handled by Step Functions
                timeout = timeout,
                description = description
            )
        )
    }
}
