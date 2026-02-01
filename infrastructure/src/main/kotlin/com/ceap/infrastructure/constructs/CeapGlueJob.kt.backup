package com.ceap.infrastructure.constructs

import software.amazon.awscdk.Duration
import software.amazon.awscdk.services.glue.CfnJob
import software.amazon.awscdk.services.iam.Effect
import software.amazon.awscdk.services.iam.PolicyStatement
import software.amazon.awscdk.services.iam.Role
import software.amazon.awscdk.services.iam.ServicePrincipal
import software.amazon.awscdk.services.s3.IBucket
import software.constructs.Construct

/**
 * Configuration for a CEAP Glue job.
 * 
 * @property jobName Name of the Glue job
 * @property scriptLocation S3 location of the PySpark script
 * @property workflowBucket S3 bucket for workflow intermediate storage
 * @property glueVersion Glue version (default: "4.0" - latest stable)
 * @property workerType Worker type (default: "G.1X" - standard worker)
 * @property numberOfWorkers Number of workers (default: 2 - minimum for distributed processing)
 * @property maxRetries Maximum number of retries (default: 0 - retries handled by Step Functions)
 * @property timeout Job timeout in minutes (default: 120 - 2 hours)
 * @property description Job description
 * 
 * Validates: Requirement 7.1
 */
data class GlueJobConfiguration(
    val jobName: String,
    val scriptLocation: String,
    val workflowBucket: IBucket,
    val glueVersion: String = "4.0",
    val workerType: String = "G.1X",
    val numberOfWorkers: Int = 2,
    val maxRetries: Int = 0,
    val timeout: Int = 120,
    val description: String = "CEAP workflow Glue job for ETL processing"
)

/**
 * CDK construct for creating a Glue job integrated with CEAP workflows.
 * 
 * This construct creates:
 * - Glue job with PySpark script
 * - IAM role with S3 permissions for workflow bucket
 * - CloudWatch Logs permissions for job logging
 * 
 * The Glue job follows the same S3 path convention as Lambda functions:
 * - Input: executions/{executionId}/{previousStage}/output.json
 * - Output: executions/{executionId}/{currentStage}/output.json
 * 
 * Key Features:
 * - Configurable DPUs and timeout for different workload sizes
 * - Least-privilege IAM permissions (only workflow bucket access)
 * - CloudWatch Logs integration for debugging
 * - Compatible with Standard workflows (not Express)
 * 
 * Example Usage:
 * ```kotlin
 * val glueJob = CeapGlueJob(
 *     scope = this,
 *     id = "HeavyETLJob",
 *     config = GlueJobConfiguration(
 *         jobName = "ceap-heavy-etl-job",
 *         scriptLocation = "s3://my-scripts-bucket/workflow_etl_template.py",
 *         workflowBucket = workflowBucket,
 *         numberOfWorkers = 10,  // Scale up for large datasets
 *         timeout = 240  // 4 hours for complex transformations
 *     )
 * )
 * 
 * // Use in Standard workflow
 * val workflow = WorkflowFactory.createStandardWorkflow(
 *     scope = this,
 *     config = WorkflowConfiguration(
 *         workflowType = WorkflowType.STANDARD,
 *         steps = listOf(
 *             WorkflowStepType.Lambda(LambdaStep("ETLStage", "etlLambda")),
 *             WorkflowStepType.Glue(GlueStep("HeavyETLStage", glueJob.jobName)),
 *             WorkflowStepType.Lambda(LambdaStep("FilterStage", "filterLambda"))
 *         ),
 *         // ...
 *     )
 * )
 * ```
 * 
 * Validates: Requirements 7.1, 11.2
 */
class CeapGlueJob(
    scope: Construct,
    id: String,
    private val config: GlueJobConfiguration
) : Construct(scope, id) {
    
    /**
     * The Glue job name (used to reference the job in Step Functions).
     */
    val jobName: String = config.jobName
    
    /**
     * The IAM role used by the Glue job.
     */
    val role: Role
    
    /**
     * The Glue job resource.
     */
    val job: CfnJob
    
    init {
        // Create IAM role for Glue job
        // This role allows the Glue job to:
        // - Read/write to the workflow S3 bucket
        // - Write logs to CloudWatch
        // - Access Glue service resources
        role = createGlueJobRole()
        
        // Create Glue job
        job = createGlueJob()
    }
    
    /**
     * Creates the IAM role for the Glue job with least-privilege permissions.
     * 
     * Permissions granted:
     * - S3: Read/write access to workflow bucket (executions/* prefix only)
     * - CloudWatch Logs: Write logs for debugging
     * - Glue: Access to Glue service resources (databases, tables, etc.)
     * 
     * Validates: Requirement 11.2, 11.9
     */
    private fun createGlueJobRole(): Role {
        val role = Role.Builder.create(this, "GlueJobRole")
            .roleName("${config.jobName}-role")
            .assumedBy(ServicePrincipal("glue.amazonaws.com"))
            .description("IAM role for ${config.jobName} Glue job")
            .build()
        
        // Grant S3 permissions for workflow bucket
        // Only allow access to executions/* prefix (least-privilege)
        config.workflowBucket.grantReadWrite(role, "executions/*")
        
        // Grant CloudWatch Logs permissions
        // Allows Glue job to write logs for debugging
        role.addToPolicy(
            PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(listOf(
                    "logs:CreateLogGroup",
                    "logs:CreateLogStream",
                    "logs:PutLogEvents"
                ))
                .resources(listOf(
                    "arn:aws:logs:*:*:/aws-glue/jobs/${config.jobName}",
                    "arn:aws:logs:*:*:/aws-glue/jobs/${config.jobName}/*"
                ))
                .build()
        )
        
        // Grant Glue service permissions
        // Allows access to Glue databases, tables, and other resources
        role.addToPolicy(
            PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(listOf(
                    "glue:GetDatabase",
                    "glue:GetTable",
                    "glue:GetPartition",
                    "glue:GetPartitions",
                    "glue:CreateTable",
                    "glue:UpdateTable",
                    "glue:DeleteTable"
                ))
                .resources(listOf("*"))  // Glue resources are account-scoped
                .build()
        )
        
        return role
    }
    
    /**
     * Creates the Glue job with PySpark script and configuration.
     * 
     * Configuration:
     * - Script location: S3 path to PySpark script
     * - Glue version: 4.0 (latest stable, supports Python 3.10 and Spark 3.3)
     * - Worker type: G.1X (standard worker with 4 vCPU and 16 GB memory)
     * - Number of workers: Configurable (default: 2)
     * - Max retries: 0 (retries handled by Step Functions)
     * - Timeout: Configurable (default: 120 minutes)
     * 
     * The job expects the following arguments from Step Functions:
     * - --execution-id: Step Functions execution ID
     * - --input-bucket: S3 bucket for input data
     * - --input-key: S3 key for input data
     * - --output-bucket: S3 bucket for output data
     * - --output-key: S3 key for output data
     * - --current-stage: Current stage name
     * - --previous-stage: Previous stage name
     * 
     * Validates: Requirement 7.1
     */
    private fun createGlueJob(): CfnJob {
        return CfnJob.Builder.create(this, "GlueJob")
            .name(config.jobName)
            .role(role.roleArn)
            .command(CfnJob.JobCommandProperty.builder()
                .name("glueetl")  // Standard Glue ETL job
                .scriptLocation(config.scriptLocation)
                .pythonVersion("3")  // Python 3.10 with Glue 4.0
                .build())
            .glueVersion(config.glueVersion)
            .workerType(config.workerType)
            .numberOfWorkers(config.numberOfWorkers)
            .maxRetries(config.maxRetries)
            .timeout(config.timeout)
            .description(config.description)
            // Default arguments (can be overridden by Step Functions)
            .defaultArguments(mapOf(
                "--enable-metrics" to "true",
                "--enable-spark-ui" to "true",
                "--enable-job-insights" to "true",
                "--enable-glue-datacatalog" to "true",
                "--job-language" to "python",
                "--TempDir" to "s3://${config.workflowBucket.bucketName}/glue-temp/",
                "--enable-continuous-cloudwatch-log" to "true"
            ))
            .build()
    }
}
