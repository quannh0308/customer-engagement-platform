package com.ceap.infrastructure.stacks

import software.amazon.awscdk.CfnOutput
import software.amazon.awscdk.CfnParameter
import software.amazon.awscdk.Duration
import software.amazon.awscdk.Fn
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.events.Rule
import software.amazon.awscdk.services.events.Schedule
import software.amazon.awscdk.services.events.targets.SfnStateMachine
import software.amazon.awscdk.services.iam.PolicyStatement
import software.amazon.awscdk.services.lambda.Code
import software.amazon.awscdk.services.lambda.Function
import software.amazon.awscdk.services.lambda.Runtime
import software.amazon.awscdk.services.logs.RetentionDays
import software.amazon.awscdk.services.s3.BlockPublicAccess
import software.amazon.awscdk.services.s3.Bucket
import software.amazon.awscdk.services.s3.BucketEncryption
import software.amazon.awscdk.services.s3.LifecycleRule
import software.amazon.awscdk.services.sqs.DeadLetterQueue
import software.amazon.awscdk.services.sqs.Queue
import software.amazon.awscdk.services.stepfunctions.*
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvoke
import software.constructs.Construct
import java.util.Arrays

/**
 * Data Platform Stack - Consolidates the Write Path for CEAP.
 * 
 * This stack consolidates resources from 5 original stacks:
 * - CeapEtlWorkflow-dev (ETL Lambda and resources) ✓ MIGRATED
 * - CeapFilterWorkflow-dev (Filter Lambda and resources) ✓ MIGRATED
 * - CeapScoreWorkflow-dev (Score Lambda and resources) ✓ MIGRATED
 * - CeapStoreWorkflow-dev (Store Lambda and resources) ✓ MIGRATED
 * - CeapOrchestration-dev (Step Functions workflow and EventBridge rules) ✓ MIGRATED
 * 
 * The Data Platform focuses on the B-2 capability (building datasets):
 * - Data ingestion and transformation (ETL)
 * - Filtering operations
 * - Scoring operations
 * - Storage operations
 * - Orchestration of the batch ingestion workflow
 * 
 * Resources:
 * - 4 Lambda Functions (ETL, Filter, Score, Store)
 * - 1 Step Functions State Machine (BatchIngestionWorkflow)
 * - 1 EventBridge Rule (BatchIngestionSchedule - daily at 2 AM UTC)
 * - 9 Step Functions States (ETLTask, FilterTask, ScoreTask, StoreTask, ETLFailed, FilterFailed, ScoreFailed, StoreFailed, WorkflowSuccess)
 * 
 * Dependencies:
 * - CeapDatabase-dev stack (for DynamoDB table references)
 * 
 * Cross-Stack References:
 * - Uses CloudFormation imports (Fn::ImportValue) to reference database tables
 * - Exports orchestration workflow ARN for potential use by other stacks
 * - Exports all Lambda function ARNs for potential direct invocation
 * 
 * Requirements Validated:
 * - Requirement 1.3: Write Path consolidated into CeapDataPlatform-dev
 * - Requirement 1.5: Contains all resources from ETL, Filter, Score, Store, and Orchestration stacks
 * - Requirement 2.1: All Lambda functions preserved
 * - Requirement 2.2: All Step Functions workflows preserved
 * - Requirement 2.3: All EventBridge rules preserved
 * - Requirement 7.1: Depends on CeapDatabase-dev stack
 * - Requirement 8.1: All logical IDs preserved
 * - Requirement 10.1: Uses CloudFormation exports for cross-stack references
 */
class DataPlatformStack(
    scope: Construct,
    id: String,
    props: StackProps,
    val envName: String
) : Stack(scope, id, props) {
    
    /**
     * Parameter for database stack name to enable cross-stack references.
     * This allows the Data Platform stack to import database table names and ARNs
     * from the CeapDatabase-dev stack using CloudFormation exports.
     * 
     * Default: CeapDatabase-{envName}
     * 
     * Validates: Requirement 7.1 (stack dependencies)
     */
    val databaseStackName = CfnParameter.Builder.create(this, "DatabaseStackName")
        .type("String")
        .defaultValue("CeapDatabase-$envName")
        .description("Name of the database stack for cross-stack references. " +
                "Used to import DynamoDB table names and ARNs via CloudFormation exports.")
        .build()
    
    // ETL Lambda function - migrated from CeapEtlWorkflow-dev
    // Preserves original logical ID "ETLLambda" (Requirement 8.1)
    val etlLambda: Function
    
    // Filter Lambda function - migrated from CeapFilterWorkflow-dev
    // Preserves original logical ID "FilterLambda" (Requirement 8.1)
    val filterLambda: Function
    
    // Score Lambda function - migrated from CeapScoreWorkflow-dev
    // Preserves original logical ID "ScoreLambda" (Requirement 8.1)
    val scoreLambda: Function
    
    // Store Lambda function - migrated from CeapStoreWorkflow-dev
    // Preserves original logical ID "StoreLambda" (Requirement 8.1)
    val storeLambda: Function
    
    // Batch Ingestion Workflow - migrated from CeapOrchestration-dev
    // Preserves original logical ID "BatchIngestionWorkflow" (Requirement 8.1)
    val batchIngestionWorkflow: StateMachine
    
    // S3 Workflow Bucket - for Step Functions intermediate storage
    // Stores execution data between workflow stages
    // Validates: Requirements 2.5, 2.7
    val workflowBucket: Bucket
    
    // SQS Dead Letter Queue - for failed workflow messages
    // Stores messages that fail after maxReceiveCount attempts
    // Validates: Requirement 8.5
    val workflowDLQ: Queue
    
    // SQS Workflow Queue - triggers Step Functions workflows
    // Connected to DLQ for failure handling
    // Validates: Requirement 8.5
    val workflowQueue: Queue
    
    init {
        // Set stack description
        this.templateOptions.description = 
            "CEAP Data Platform Stack - Write Path consolidation containing ETL, Filter, " +
            "Score, Store, and Orchestration workflows. This stack handles data ingestion, " +
            "transformation, scoring, and storage operations for the Customer Engagement & " +
            "Action Platform. Depends on CeapDatabase-$envName stack for DynamoDB tables."
        
        // Set stack metadata
        this.templateOptions.metadata = mapOf(
            "Purpose" to "Data Platform - Write Path",
            "BusinessCapability" to "B-2: Building Datasets",
            "ConsolidatedFrom" to listOf(
                "CeapEtlWorkflow-$envName",
                "CeapFilterWorkflow-$envName",
                "CeapScoreWorkflow-$envName",
                "CeapStoreWorkflow-$envName",
                "CeapOrchestration-$envName"
            ),
            "Dependencies" to listOf(
                "CeapDatabase-$envName"
            ),
            "Version" to "1.0.0",
            "LastUpdated" to "2025-01-27",
            "Requirements" to listOf(
                "1.3: Write Path consolidated into CeapDataPlatform-dev",
                "1.5: Contains all resources from 5 original stacks",
                "7.1: Depends on CeapDatabase-dev stack",
                "10.1: Uses CloudFormation exports for cross-stack references"
            )
        )
        
        // ========================================
        // S3 Workflow Bucket
        // ========================================
        
        // Create S3 bucket for workflow intermediate storage
        // Naming convention: ceap-workflow-{environment}-{accountId}
        // Validates: Requirements 2.5, 2.7
        workflowBucket = Bucket.Builder.create(this, "WorkflowBucket")
            .bucketName("ceap-workflow-$envName-${this.account}")
            .versioned(false)
            .encryption(BucketEncryption.S3_MANAGED)
            .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
            .lifecycleRules(listOf(
                LifecycleRule.builder()
                    .id("DeleteOldExecutions")
                    .prefix("executions/")
                    .expiration(Duration.days(7))
                    .enabled(true)
                    .build()
            ))
            .build()
        
        // ========================================
        // SQS Queues for Workflow Orchestration
        // ========================================
        
        // Create Dead Letter Queue for failed workflow messages
        // Messages move here after 3 failed receive attempts
        // Validates: Requirement 8.5
        workflowDLQ = Queue.Builder.create(this, "WorkflowDLQ")
            .queueName("ceap-workflow-dlq-$envName")
            .retentionPeriod(Duration.days(14)) // Retain failed messages for 14 days
            .build()
        
        // Create main workflow queue with DLQ configuration
        // Visibility timeout: 10 minutes (allows workflow to complete)
        // Max receive count: 3 attempts before moving to DLQ
        // Validates: Requirement 8.5
        workflowQueue = Queue.Builder.create(this, "WorkflowQueue")
            .queueName("ceap-workflow-queue-$envName")
            .visibilityTimeout(Duration.minutes(10)) // Allow time for workflow execution
            .deadLetterQueue(DeadLetterQueue.builder()
                .queue(workflowDLQ)
                .maxReceiveCount(3) // Move to DLQ after 3 failed attempts
                .build())
            .build()
        
        // ========================================
        // ETL Resources (from CeapEtlWorkflow-dev)
        // ========================================
        
        // Import database table names and ARNs from CeapDatabase-dev stack
        // Uses Fn::ImportValue for cross-stack references (Requirement 10.1)
        val candidatesTableName = Fn.importValue("${databaseStackName.valueAsString}-CandidatesTableName")
        val candidatesTableArn = Fn.importValue("${databaseStackName.valueAsString}-CandidatesTableArn")
        val programConfigTableName = Fn.importValue("${databaseStackName.valueAsString}-ProgramConfigTableName")
        val programConfigTableArn = Fn.importValue("${databaseStackName.valueAsString}-ProgramConfigTableArn")
        
        // ETL Lambda function
        // Preserves original logical ID "ETLLambdaFunction49DD508A" (Requirement 8.1)
        // Configuration matches original EtlWorkflowStack (Requirement 2.1)
        // Uses explicit functionName to prevent CloudFormation auto-generated suffixes (Requirement 1.1, 1.2)
        etlLambda = Function.Builder.create(this, "ETLLambda")
            .functionName("$stackName-$envName-ETLLambda")
            .runtime(Runtime.JAVA_17)
            .handler("com.ceap.workflow.etl.ETLHandler::handleRequest")
            .code(Code.fromAsset("../ceap-workflow-etl/build/libs/ceap-workflow-etl-1.0.0-SNAPSHOT.jar"))
            .memorySize(1024)
            .timeout(Duration.minutes(5))
            .environment(mapOf(
                "ENVIRONMENT" to envName,
                "CANDIDATES_TABLE" to candidatesTableName,
                "PROGRAM_CONFIG_TABLE" to programConfigTableName,
                "BATCH_SIZE" to "1000",
                "LOG_LEVEL" to "INFO"
            ))
            .logRetention(RetentionDays.ONE_MONTH)
            .build()
        
        // Override logical ID to match original stack (Requirement 8.1)
        val etlLambdaCfn = etlLambda.node.defaultChild as software.amazon.awscdk.services.lambda.CfnFunction
        etlLambdaCfn.overrideLogicalId("ETLLambdaFunction49DD508A")
        
        // Grant DynamoDB permissions to ETL Lambda
        // Uses imported ARNs for IAM policy (Requirement 10.1)
        etlLambda.addToRolePolicy(
            PolicyStatement.Builder.create()
                .actions(listOf(
                    "dynamodb:GetItem",
                    "dynamodb:PutItem",
                    "dynamodb:Query",
                    "dynamodb:Scan"
                ))
                .resources(listOf(candidatesTableArn))
                .build()
        )
        
        etlLambda.addToRolePolicy(
            PolicyStatement.Builder.create()
                .actions(listOf(
                    "dynamodb:GetItem",
                    "dynamodb:Query"
                ))
                .resources(listOf(programConfigTableArn))
                .build()
        )
        
        // TODO: Add Athena and S3 permissions when implementing data connectors
        
        // ========================================
        // Filter Resources (from CeapFilterWorkflow-dev)
        // ========================================
        
        // Filter Lambda function
        // Preserves original logical ID "FilterLambdaFunction29040EBB" (Requirement 8.1)
        // Configuration matches original FilterWorkflowStack (Requirement 2.1)
        // Uses explicit functionName to prevent CloudFormation auto-generated suffixes (Requirement 1.1, 1.2)
        filterLambda = Function.Builder.create(this, "FilterLambda")
            .functionName("$stackName-$envName-FilterLambda")
            .runtime(Runtime.JAVA_17)
            .handler("com.ceap.workflow.filter.FilterHandler::handleRequest")
            .code(Code.fromAsset("../ceap-workflow-filter/build/libs/ceap-workflow-filter-1.0.0-SNAPSHOT.jar"))
            .memorySize(512)
            .timeout(Duration.seconds(60))
            .environment(mapOf(
                "ENVIRONMENT" to envName,
                "PROGRAM_CONFIG_TABLE" to programConfigTableName,
                "LOG_LEVEL" to "INFO"
            ))
            .logRetention(RetentionDays.ONE_MONTH)
            .build()
        
        // Override logical ID to match original stack (Requirement 8.1)
        val filterLambdaCfn = filterLambda.node.defaultChild as software.amazon.awscdk.services.lambda.CfnFunction
        filterLambdaCfn.overrideLogicalId("FilterLambdaFunction29040EBB")
        
        // Grant DynamoDB permissions to Filter Lambda
        // Uses imported ARNs for IAM policy (Requirement 10.1)
        filterLambda.addToRolePolicy(
            PolicyStatement.Builder.create()
                .actions(listOf(
                    "dynamodb:GetItem",
                    "dynamodb:Query"
                ))
                .resources(listOf(programConfigTableArn))
                .build()
        )
        
        // ========================================
        // Score Resources (from CeapScoreWorkflow-dev)
        // ========================================
        
        // Import ScoreCache table name and ARN from CeapDatabase-dev stack
        // Uses Fn::ImportValue for cross-stack references (Requirement 10.1)
        val scoreCacheTableName = Fn.importValue("${databaseStackName.valueAsString}-ScoreCacheTableName")
        val scoreCacheTableArn = Fn.importValue("${databaseStackName.valueAsString}-ScoreCacheTableArn")
        
        // Score Lambda function
        // Preserves original logical ID "ScoreLambdaFunction04AD330A" (Requirement 8.1)
        // Configuration matches original ScoreWorkflowStack (Requirement 2.1)
        // Uses explicit functionName to prevent CloudFormation auto-generated suffixes (Requirement 1.1, 1.2)
        scoreLambda = Function.Builder.create(this, "ScoreLambda")
            .functionName("$stackName-$envName-ScoreLambda")
            .runtime(Runtime.JAVA_17)
            .handler("com.ceap.workflow.score.ScoreHandler::handleRequest")
            .code(Code.fromAsset("../ceap-workflow-score/build/libs/ceap-workflow-score-1.0.0-SNAPSHOT.jar"))
            .memorySize(1024)
            .timeout(Duration.minutes(2))
            .environment(mapOf(
                "ENVIRONMENT" to envName,
                "SCORE_CACHE_TABLE" to scoreCacheTableName,
                "CACHE_TTL_HOURS" to "24",
                "LOG_LEVEL" to "INFO"
            ))
            .logRetention(RetentionDays.ONE_MONTH)
            .build()
        
        // Override logical ID to match original stack (Requirement 8.1)
        val scoreLambdaCfn = scoreLambda.node.defaultChild as software.amazon.awscdk.services.lambda.CfnFunction
        scoreLambdaCfn.overrideLogicalId("ScoreLambdaFunction04AD330A")
        
        // Grant DynamoDB permissions to Score Lambda
        // Uses imported ARNs for IAM policy (Requirement 10.1)
        scoreLambda.addToRolePolicy(
            PolicyStatement.Builder.create()
                .actions(listOf(
                    "dynamodb:GetItem",
                    "dynamodb:PutItem",
                    "dynamodb:Query"
                ))
                .resources(listOf(scoreCacheTableArn))
                .build()
        )
        
        // TODO: Add SageMaker endpoint permissions when implementing scoring engine
        
        // TODO: Task 2.4 - COMPLETED
        // ✓ Copied Lambda function definition from CeapScoreWorkflow-dev
        // ✓ Updated cross-stack references to use Fn::ImportValue
        // ✓ Preserved original logical ID "ScoreLambda"
        // ✓ Preserved all configuration (memory, timeout, environment variables)
        // ✓ Preserved IAM permissions for ScoreCacheTable
        
        // Note: No Step Function definitions in CeapScoreWorkflow-dev to migrate
        // The Score Lambda is invoked by the BatchIngestionWorkflow in the Orchestration stack
        
        // ========================================
        // Store Resources (from CeapStoreWorkflow-dev)
        // ========================================
        
        // Store Lambda function
        // Preserves original logical ID "StoreLambdaFunction7FC1576D" (Requirement 8.1)
        // Configuration matches original StoreWorkflowStack (Requirement 2.1)
        // Uses explicit functionName to prevent CloudFormation auto-generated suffixes (Requirement 1.1, 1.2)
        storeLambda = Function.Builder.create(this, "StoreLambda")
            .functionName("$stackName-$envName-StoreLambda")
            .runtime(Runtime.JAVA_17)
            .handler("com.ceap.workflow.store.StoreHandler::handleRequest")
            .code(Code.fromAsset("../ceap-workflow-store/build/libs/ceap-workflow-store-1.0.0-SNAPSHOT.jar"))
            .memorySize(512)
            .timeout(Duration.seconds(60))
            .environment(mapOf(
                "ENVIRONMENT" to envName,
                "CANDIDATES_TABLE" to candidatesTableName,
                "BATCH_SIZE" to "25",
                "LOG_LEVEL" to "INFO"
            ))
            .logRetention(RetentionDays.ONE_MONTH)
            .build()
        
        // Override logical ID to match original stack (Requirement 8.1)
        val storeLambdaCfn = storeLambda.node.defaultChild as software.amazon.awscdk.services.lambda.CfnFunction
        storeLambdaCfn.overrideLogicalId("StoreLambdaFunction7FC1576D")
        
        // Grant DynamoDB permissions to Store Lambda
        // Uses imported ARNs for IAM policy (Requirement 10.1)
        storeLambda.addToRolePolicy(
            PolicyStatement.Builder.create()
                .actions(listOf(
                    "dynamodb:PutItem",
                    "dynamodb:BatchWriteItem"
                ))
                .resources(listOf(candidatesTableArn))
                .build()
        )
        
        // ========================================
        // S3 Workflow Bucket IAM Permissions
        // ========================================
        
        // Grant S3 read/write permissions to all Lambda functions
        // Follows least-privilege principles: only access to executions/* prefix
        // Validates: Requirements 11.2, 11.9
        workflowBucket.grantReadWrite(etlLambda, "executions/*")
        workflowBucket.grantReadWrite(filterLambda, "executions/*")
        workflowBucket.grantReadWrite(scoreLambda, "executions/*")
        workflowBucket.grantReadWrite(storeLambda, "executions/*")
        
        // TODO: Task 2.5 - COMPLETED
        // ✓ Copied Lambda function definition from CeapStoreWorkflow-dev
        // ✓ Updated cross-stack references to use Fn::ImportValue
        // ✓ Preserved original logical ID "StoreLambda"
        // ✓ Preserved all configuration (memory, timeout, environment variables)
        // ✓ Preserved IAM permissions for CandidatesTable (write operations)
        
        // Note: No Step Function definitions in CeapStoreWorkflow-dev to migrate
        // The Store Lambda is invoked by the BatchIngestionWorkflow in the Orchestration stack
        
        // ========================================
        // Orchestration Resources (from CeapOrchestration-dev)
        // ========================================
        
        // Create Step Functions tasks with error handling and retry logic
        // All tasks reference Lambda functions within the same stack (direct references)
        // Preserves original logical IDs for all tasks and states (Requirement 8.1)
        
        // ETL Task with retry configuration
        // Preserves original retry configuration: 5 attempts, exponential backoff (1s, 2s, 4s, 8s, 16s)
        val etlTask = LambdaInvoke.Builder.create(this, "ETLTask")
            .lambdaFunction(etlLambda)
            .outputPath("$.Payload")
            .retryOnServiceExceptions(true)
            .build()
            .addRetry(
                RetryProps.builder()
                    .errors(Arrays.asList("States.TaskFailed", "States.Timeout", "Lambda.ServiceException"))
                    .interval(Duration.seconds(1))
                    .maxAttempts(5)
                    .backoffRate(2.0) // Exponential backoff: 1s, 2s, 4s, 8s, 16s
                    .build()
            )
            .addCatch(
                Fail.Builder.create(this, "ETLFailed")
                    .cause("ETL stage failed after retries")
                    .error("ETLError")
                    .build(),
                CatchProps.builder()
                    .errors(Arrays.asList("States.ALL"))
                    .resultPath("$.error")
                    .build()
            )
        
        // Filter Task with retry configuration
        // Preserves original retry configuration: 5 attempts, exponential backoff
        val filterTask = LambdaInvoke.Builder.create(this, "FilterTask")
            .lambdaFunction(filterLambda)
            .outputPath("$.Payload")
            .retryOnServiceExceptions(true)
            .build()
            .addRetry(
                RetryProps.builder()
                    .errors(Arrays.asList("States.TaskFailed", "States.Timeout", "Lambda.ServiceException"))
                    .interval(Duration.seconds(1))
                    .maxAttempts(5)
                    .backoffRate(2.0)
                    .build()
            )
            .addCatch(
                Fail.Builder.create(this, "FilterFailed")
                    .cause("Filter stage failed after retries")
                    .error("FilterError")
                    .build(),
                CatchProps.builder()
                    .errors(Arrays.asList("States.ALL"))
                    .resultPath("$.error")
                    .build()
            )
        
        // Score Task with retry configuration
        // Preserves original retry configuration: 5 attempts, exponential backoff
        val scoreTask = LambdaInvoke.Builder.create(this, "ScoreTask")
            .lambdaFunction(scoreLambda)
            .outputPath("$.Payload")
            .retryOnServiceExceptions(true)
            .build()
            .addRetry(
                RetryProps.builder()
                    .errors(Arrays.asList("States.TaskFailed", "States.Timeout", "Lambda.ServiceException"))
                    .interval(Duration.seconds(1))
                    .maxAttempts(5)
                    .backoffRate(2.0)
                    .build()
            )
            .addCatch(
                Fail.Builder.create(this, "ScoreFailed")
                    .cause("Score stage failed after retries")
                    .error("ScoreError")
                    .build(),
                CatchProps.builder()
                    .errors(Arrays.asList("States.ALL"))
                    .resultPath("$.error")
                    .build()
            )
        
        // Store Task with retry configuration
        // Preserves original retry configuration: 5 attempts, exponential backoff
        val storeTask = LambdaInvoke.Builder.create(this, "StoreTask")
            .lambdaFunction(storeLambda)
            .outputPath("$.Payload")
            .retryOnServiceExceptions(true)
            .build()
            .addRetry(
                RetryProps.builder()
                    .errors(Arrays.asList("States.TaskFailed", "States.Timeout", "Lambda.ServiceException"))
                    .interval(Duration.seconds(1))
                    .maxAttempts(5)
                    .backoffRate(2.0)
                    .build()
            )
            .addCatch(
                Fail.Builder.create(this, "StoreFailed")
                    .cause("Store stage failed after retries")
                    .error("StoreError")
                    .build(),
                CatchProps.builder()
                    .errors(Arrays.asList("States.ALL"))
                    .resultPath("$.error")
                    .build()
            )
        
        // Success state for workflow completion
        // Preserves original logical ID "WorkflowSuccess" (Requirement 8.1)
        val workflowSuccess = Succeed.Builder.create(this, "WorkflowSuccess")
            .comment("Batch ingestion workflow completed successfully")
            .build()
        
        // Chain tasks: ETL → Filter → Score → Store → Success
        // Preserves original workflow structure (Requirement 2.2, 3.6)
        val definition = etlTask
            .next(filterTask)
            .next(scoreTask)
            .next(storeTask)
            .next(workflowSuccess)
        
        // Create State Machine
        // Preserves original logical ID "BatchIngestionWorkflow10186577" (Requirement 8.1)
        // Preserves original configuration: 4-hour timeout (Requirement 2.2)
        batchIngestionWorkflow = StateMachine.Builder.create(this, "BatchIngestionWorkflow")
            .stateMachineName("CeapBatchIngestion-$envName")
            .definitionBody(DefinitionBody.fromChainable(definition))
            .timeout(Duration.hours(4)) // Allow up to 4 hours for large batches
            .build()
        
        // Override logical ID to match original stack (Requirement 8.1)
        val batchIngestionWorkflowCfn = batchIngestionWorkflow.node.defaultChild as software.amazon.awscdk.services.stepfunctions.CfnStateMachine
        batchIngestionWorkflowCfn.overrideLogicalId("BatchIngestionWorkflow10186577")
        
        // Schedule batch ingestion (daily at 2 AM UTC)
        // Preserves original logical ID "BatchIngestionSchedule3D278ACF" (Requirement 8.1)
        // Preserves original schedule: cron(0 2 * * ? *) (Requirement 2.3)
        val batchIngestionSchedule = Rule.Builder.create(this, "BatchIngestionSchedule")
            .ruleName("CeapBatchIngestion-$envName")
            .schedule(Schedule.cron(
                software.amazon.awscdk.services.events.CronOptions.builder()
                    .minute("0")
                    .hour("2")
                    .build()
            ))
            .targets(listOf(SfnStateMachine(batchIngestionWorkflow)))
            .build()
        
        // Override logical ID to match original stack (Requirement 8.1)
        val batchIngestionScheduleCfn = batchIngestionSchedule.node.defaultChild as software.amazon.awscdk.services.events.CfnRule
        batchIngestionScheduleCfn.overrideLogicalId("BatchIngestionSchedule3D278ACF")
        
        // ========================================
        // Stack Outputs
        // ========================================
        
        // Export BatchIngestionWorkflow ARN for potential cross-stack references
        // Enables CeapServingAPI-dev or other stacks to trigger the workflow if needed
        // Validates: Requirement 10.1 (cross-stack reference mechanisms)
        CfnOutput.Builder.create(this, "BatchIngestionWorkflowArnOutput")
            .value(batchIngestionWorkflow.stateMachineArn)
            .exportName("$stackName-BatchIngestionWorkflowArn")
            .description("ARN of the batch ingestion Step Functions workflow. " +
                    "Can be imported by other stacks to trigger batch processing.")
            .build()
        
        // Export BatchIngestionWorkflow name for convenience
        CfnOutput.Builder.create(this, "BatchIngestionWorkflowNameOutput")
            .value(batchIngestionWorkflow.stateMachineName)
            .exportName("$stackName-BatchIngestionWorkflowName")
            .description("Name of the batch ingestion Step Functions workflow.")
            .build()
        
        // Export ETL Lambda ARN for potential direct invocation
        CfnOutput.Builder.create(this, "ETLLambdaArnOutput")
            .value(etlLambda.functionArn)
            .exportName("$stackName-ETLLambdaArn")
            .description("ARN of the ETL Lambda function.")
            .build()
        
        // Export Filter Lambda ARN for potential direct invocation
        CfnOutput.Builder.create(this, "FilterLambdaArnOutput")
            .value(filterLambda.functionArn)
            .exportName("$stackName-FilterLambdaArn")
            .description("ARN of the Filter Lambda function.")
            .build()
        
        // Export Score Lambda ARN for potential direct invocation
        CfnOutput.Builder.create(this, "ScoreLambdaArnOutput")
            .value(scoreLambda.functionArn)
            .exportName("$stackName-ScoreLambdaArn")
            .description("ARN of the Score Lambda function.")
            .build()
        
        // Export Store Lambda ARN for potential direct invocation
        CfnOutput.Builder.create(this, "StoreLambdaArnOutput")
            .value(storeLambda.functionArn)
            .exportName("$stackName-StoreLambdaArn")
            .description("ARN of the Store Lambda function.")
            .build()
        
        // Export Workflow Bucket name for cross-stack references
        CfnOutput.Builder.create(this, "WorkflowBucketNameOutput")
            .value(workflowBucket.bucketName)
            .exportName("$stackName-WorkflowBucketName")
            .description("Name of the S3 bucket for workflow intermediate storage.")
            .build()
        
        // Export Workflow Bucket ARN for IAM policy references
        CfnOutput.Builder.create(this, "WorkflowBucketArnOutput")
            .value(workflowBucket.bucketArn)
            .exportName("$stackName-WorkflowBucketArn")
            .description("ARN of the S3 bucket for workflow intermediate storage.")
            .build()
        
        // Export Workflow Queue URL for message publishing
        CfnOutput.Builder.create(this, "WorkflowQueueUrlOutput")
            .value(workflowQueue.queueUrl)
            .exportName("$stackName-WorkflowQueueUrl")
            .description("URL of the SQS queue for triggering workflows.")
            .build()
        
        // Export Workflow Queue ARN for IAM policy references
        CfnOutput.Builder.create(this, "WorkflowQueueArnOutput")
            .value(workflowQueue.queueArn)
            .exportName("$stackName-WorkflowQueueArn")
            .description("ARN of the SQS queue for triggering workflows.")
            .build()
        
        // Export Workflow DLQ URL for monitoring and reprocessing
        CfnOutput.Builder.create(this, "WorkflowDLQUrlOutput")
            .value(workflowDLQ.queueUrl)
            .exportName("$stackName-WorkflowDLQUrl")
            .description("URL of the Dead Letter Queue for failed workflow messages.")
            .build()
        
        // Export Workflow DLQ ARN for IAM policy references
        CfnOutput.Builder.create(this, "WorkflowDLQArnOutput")
            .value(workflowDLQ.queueArn)
            .exportName("$stackName-WorkflowDLQArn")
            .description("ARN of the Dead Letter Queue for failed workflow messages.")
            .build()
        
        // Task 2.6 - COMPLETED
        // ✓ Copied Step Function definitions from CeapOrchestration-dev
        // ✓ Copied EventBridge rules from CeapOrchestration-dev
        // ✓ Updated cross-stack references to use direct references (same stack)
        // ✓ Preserved original logical IDs (BatchIngestionWorkflow, BatchIngestionSchedule, ETLTask, FilterTask, ScoreTask, StoreTask, ETLFailed, FilterFailed, ScoreFailed, StoreFailed, WorkflowSuccess)
        // ✓ Added stack outputs for orchestration resources
        // ✓ Preserved all retry configurations (5 attempts, exponential backoff)
        // ✓ Preserved all error handling (Catch blocks)
        // ✓ Preserved workflow timeout (4 hours)
        // ✓ Preserved EventBridge schedule (daily at 2 AM UTC)
    }
}
