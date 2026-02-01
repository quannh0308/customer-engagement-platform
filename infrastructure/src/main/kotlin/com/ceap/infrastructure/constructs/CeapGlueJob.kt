package com.ceap.infrastructure.constructs

import software.amazon.awscdk.Duration
import software.amazon.awscdk.services.glue.CfnJob
import software.amazon.awscdk.services.iam.IRole
import software.amazon.awscdk.services.iam.Role
import software.amazon.awscdk.services.s3.IBucket
import software.constructs.Construct

/**
 * Configuration for a CEAP Glue job.
 * 
 * @property jobName Name of the Glue job
 * @property scriptLocation S3 location of the PySpark script
 * @property workflowBucket S3 bucket for workflow intermediate storage
 * @property scriptBucket S3 bucket containing Glue scripts (optional, defaults to workflowBucket)
 * @property glueVersion Glue version (default: "4.0" - latest stable)
 * @property workerType Worker type (default: "G.1X" - standard worker)
 * @property numberOfWorkers Number of workers (default: 2 - minimum for distributed processing)
 * @property maxRetries Maximum number of retries (default: 0 - retries handled by Step Functions)
 * @property timeout Job timeout in minutes (default: 120 - 2 hours)
 * @property description Job description
 * @property customRole Custom IAM role for the Glue job (optional, creates least-privilege role if not provided)
 * 
 * Validates: Requirements 7.1, 11.9
 */
data class GlueJobConfiguration(
    val jobName: String,
    val scriptLocation: String,
    val workflowBucket: IBucket,
    val scriptBucket: IBucket? = null,
    val glueVersion: String = "4.0",
    val workerType: String = "G.1X",
    val numberOfWorkers: Int = 2,
    val maxRetries: Int = 0,
    val timeout: Int = 120,
    val description: String = "CEAP workflow Glue job for ETL processing",
    val customRole: IRole? = null
)

/**
 * CDK construct for creating a Glue job integrated with CEAP workflows.
 * 
 * This construct creates:
 * - Glue job with PySpark script
 * - Least-privilege IAM role with S3 permissions scoped to executions/* prefix
 * - CloudWatch Logs permissions for job logging
 * - Glue Data Catalog permissions for table access
 * 
 * The Glue job follows the same S3 path convention as Lambda functions:
 * - Input: executions/{executionId}/{previousStage}/output.json
 * - Output: executions/{executionId}/{currentStage}/output.json
 * 
 * Example usage with default least-privilege role:
 * ```kotlin
 * val glueJob = CeapGlueJob(
 *     this, "HeavyETLJob",
 *     config = GlueJobConfiguration(
 *         jobName = "ceap-heavy-etl-job",
 *         scriptLocation = "s3://my-scripts-bucket/glue-scripts/workflow_etl_template.py",
 *         workflowBucket = workflowBucket,
 *         numberOfWorkers = 10,
 *         timeout = 240
 *     )
 * )
 * ```
 * 
 * Example usage with custom IAM role:
 * ```kotlin
 * val customRole = WorkflowIAMRoles.createGlueJobRole(
 *     this, "CustomGlueRole",
 *     roleName = "ceap-custom-glue-role",
 *     workflowBucket = workflowBucket
 * )
 * 
 * val glueJob = CeapGlueJob(
 *     this, "HeavyETLJob",
 *     config = GlueJobConfiguration(
 *         jobName = "ceap-heavy-etl-job",
 *         scriptLocation = "s3://my-scripts-bucket/glue-scripts/workflow_etl_template.py",
 *         workflowBucket = workflowBucket,
 *         customRole = customRole
 *     )
 * )
 * ```
 * 
 * Validates: Requirements 7.1, 11.9
 */
class CeapGlueJob(
    scope: Construct,
    id: String,
    private val config: GlueJobConfiguration
) : Construct(scope, id) {
    
    val jobName: String = config.jobName
    val role: IRole
    val job: CfnJob
    
    init {
        // Use custom role if provided, otherwise create least-privilege role
        role = config.customRole ?: createLeastPrivilegeGlueJobRole()
        job = createGlueJob()
    }
    
    /**
     * Creates a least-privilege IAM role for the Glue job.
     * 
     * This role grants:
     * - S3 read/write permissions scoped to executions/* prefix only
     * - S3 read permission for Glue script location
     * - S3 read/write for Glue temporary directory
     * - CloudWatch Logs permissions for job logging
     * - Glue Data Catalog permissions for table access
     * 
     * The role does NOT grant:
     * - Full S3 bucket access
     * - Access to other AWS services
     * - Administrative permissions
     * 
     * @return Created IAM role with least-privilege permissions
     * 
     * Validates: Requirement 11.9
     */
    private fun createLeastPrivilegeGlueJobRole(): Role {
        return WorkflowIAMRoles.createGlueJobRole(
            scope = this,
            id = "GlueJobRole",
            roleName = "${config.jobName}-role",
            workflowBucket = config.workflowBucket,
            scriptBucket = config.scriptBucket
        )
    }
    
    private fun createGlueJob(): CfnJob {
        return CfnJob.Builder.create(this, "GlueJob")
            .name(config.jobName)
            .role(role.roleArn)
            .command(CfnJob.JobCommandProperty.builder()
                .name("glueetl")
                .scriptLocation(config.scriptLocation)
                .pythonVersion("3")
                .build())
            .glueVersion(config.glueVersion)
            .workerType(config.workerType)
            .numberOfWorkers(config.numberOfWorkers)
            .maxRetries(config.maxRetries)
            .timeout(config.timeout)
            .description(config.description)
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
