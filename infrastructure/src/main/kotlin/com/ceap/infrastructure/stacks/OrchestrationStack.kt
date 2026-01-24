package com.ceap.infrastructure.stacks

import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.events.Rule
import software.amazon.awscdk.services.events.Schedule
import software.amazon.awscdk.services.events.targets.SfnStateMachine
import software.amazon.awscdk.services.stepfunctions.*
import software.amazon.awscdk.services.stepfunctions.tasks.LambdaInvoke
import software.amazon.awscdk.services.cloudwatch.Metric
import software.amazon.awscdk.services.cloudwatch.Unit
import software.constructs.Construct
import java.util.Arrays

/**
 * Orchestration stack containing Step Functions workflows and EventBridge rules.
 * 
 * Workflows:
 * - Batch Ingestion: ETL → Filter → Score → Store with error handling and retry logic
 * - Reactive Customer Engagement: Real-time event processing
 */
class OrchestrationStack(
    scope: Construct,
    id: String,
    props: StackProps,
    val envName: String,
    val etlStack: EtlWorkflowStack,
    val filterStack: FilterWorkflowStack,
    val scoreStack: ScoreWorkflowStack,
    val storeStack: StoreWorkflowStack,
    val reactiveStack: ReactiveWorkflowStack
) : Stack(scope, id, props) {
    
    /**
     * Batch Ingestion Workflow: ETL → Filter → Score → Store
     */
    val batchIngestionWorkflow: StateMachine
    
    init {
        // Create Step Functions tasks with error handling and retry logic
        
        // ETL Task with retry configuration
        val etlTask = LambdaInvoke.Builder.create(this, "ETLTask")
            .lambdaFunction(etlStack.etlLambda.function)
            .outputPath("$.Payload")
            .retryOnServiceExceptions(true)
            .build()
            .addRetry(
                RetryProps.builder()
                    .errors(Arrays.asList("States.TaskFailed", "States.Timeout", "Lambda.ServiceException"))
                    .interval(software.amazon.awscdk.Duration.seconds(1))
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
        val filterTask = LambdaInvoke.Builder.create(this, "FilterTask")
            .lambdaFunction(filterStack.filterLambda.function)
            .outputPath("$.Payload")
            .retryOnServiceExceptions(true)
            .build()
            .addRetry(
                RetryProps.builder()
                    .errors(Arrays.asList("States.TaskFailed", "States.Timeout", "Lambda.ServiceException"))
                    .interval(software.amazon.awscdk.Duration.seconds(1))
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
        val scoreTask = LambdaInvoke.Builder.create(this, "ScoreTask")
            .lambdaFunction(scoreStack.scoreLambda.function)
            .outputPath("$.Payload")
            .retryOnServiceExceptions(true)
            .build()
            .addRetry(
                RetryProps.builder()
                    .errors(Arrays.asList("States.TaskFailed", "States.Timeout", "Lambda.ServiceException"))
                    .interval(software.amazon.awscdk.Duration.seconds(1))
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
        val storeTask = LambdaInvoke.Builder.create(this, "StoreTask")
            .lambdaFunction(storeStack.storeLambda.function)
            .outputPath("$.Payload")
            .retryOnServiceExceptions(true)
            .build()
            .addRetry(
                RetryProps.builder()
                    .errors(Arrays.asList("States.TaskFailed", "States.Timeout", "Lambda.ServiceException"))
                    .interval(software.amazon.awscdk.Duration.seconds(1))
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
        val workflowSuccess = Succeed.Builder.create(this, "WorkflowSuccess")
            .comment("Batch ingestion workflow completed successfully")
            .build()
        
        // Chain tasks: ETL → Filter → Score → Store → Success
        val definition = etlTask
            .next(filterTask)
            .next(scoreTask)
            .next(storeTask)
            .next(workflowSuccess)
        
        // Create State Machine
        batchIngestionWorkflow = StateMachine.Builder.create(this, "BatchIngestionWorkflow")
            .stateMachineName("SolicitationBatchIngestion-$envName")
            .definitionBody(DefinitionBody.fromChainable(definition))
            .timeout(software.amazon.awscdk.Duration.hours(4)) // Allow up to 4 hours for large batches
            .build()
        
        // Schedule batch ingestion (daily at 2 AM UTC)
        Rule.Builder.create(this, "BatchIngestionSchedule")
            .ruleName("SolicitationBatchIngestion-$envName")
            .schedule(Schedule.cron(
                software.amazon.awscdk.services.events.CronOptions.builder()
                    .minute("0")
                    .hour("2")
                    .build()
            ))
            .targets(listOf(SfnStateMachine(batchIngestionWorkflow)))
            .build()
    }
}
