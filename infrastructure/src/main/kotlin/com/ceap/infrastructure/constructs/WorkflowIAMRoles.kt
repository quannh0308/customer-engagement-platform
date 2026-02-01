package com.ceap.infrastructure.constructs

import software.amazon.awscdk.services.iam.*
import software.amazon.awscdk.services.lambda.IFunction
import software.amazon.awscdk.services.s3.IBucket
import software.amazon.awscdk.services.stepfunctions.StateMachine
import software.constructs.Construct

/**
 * Utility for creating least-privilege IAM roles for CEAP workflows.
 * 
 * This object provides methods to create IAM roles with minimal permissions
 * required for workflow operation, following AWS security best practices.
 * 
 * Key principles:
 * - Grant only the minimum permissions required for operation
 * - Scope permissions to specific resources (no wildcards)
 * - Use resource-based policies where possible
 * - Separate roles for different components (Lambda, Glue, Step Functions)
 * 
 * Validates: Requirement 11.9
 */
object WorkflowIAMRoles {
    
    /**
     * Creates a least-privilege IAM role for Lambda functions in workflows.
     * 
     * This role grants:
     * - S3 read/write permissions scoped to executions/* prefix only
     * - CloudWatch Logs permissions for function logging
     * - X-Ray permissions for distributed tracing
     * 
     * The role does NOT grant:
     * - Full S3 bucket access
     * - Access to other AWS services
     * - Administrative permissions
     * 
     * @param scope CDK construct scope
     * @param id Construct ID
     * @param roleName Name of the IAM role
     * @param workflowBucket S3 bucket for workflow intermediate storage
     * @return Created IAM role
     * 
     * Validates: Requirement 11.9
     */
    fun createLambdaExecutionRole(
        scope: Construct,
        id: String,
        roleName: String,
        workflowBucket: IBucket
    ): Role {
        val role = Role.Builder.create(scope, id)
            .roleName(roleName)
            .assumedBy(ServicePrincipal("lambda.amazonaws.com"))
            .description("Least-privilege execution role for CEAP workflow Lambda function")
            .build()
        
        // Grant S3 read/write permissions scoped to executions/* prefix only
        // This prevents Lambda from accessing other data in the bucket
        role.addToPolicy(
            PolicyStatement.Builder.create()
                .sid("S3WorkflowAccess")
                .effect(Effect.ALLOW)
                .actions(listOf(
                    "s3:GetObject",
                    "s3:PutObject"
                ))
                .resources(listOf(
                    "${workflowBucket.bucketArn}/executions/*"
                ))
                .build()
        )
        
        // Grant CloudWatch Logs permissions for function logging
        // Required for Lambda to write logs to CloudWatch
        role.addToPolicy(
            PolicyStatement.Builder.create()
                .sid("CloudWatchLogsAccess")
                .effect(Effect.ALLOW)
                .actions(listOf(
                    "logs:CreateLogGroup",
                    "logs:CreateLogStream",
                    "logs:PutLogEvents"
                ))
                .resources(listOf(
                    "arn:aws:logs:*:*:log-group:/aws/lambda/*"
                ))
                .build()
        )
        
        // Grant X-Ray permissions for distributed tracing
        // Required for Lambda to send trace data to X-Ray
        role.addToPolicy(
            PolicyStatement.Builder.create()
                .sid("XRayAccess")
                .effect(Effect.ALLOW)
                .actions(listOf(
                    "xray:PutTraceSegments",
                    "xray:PutTelemetryRecords"
                ))
                .resources(listOf("*"))  // X-Ray requires wildcard
                .build()
        )
        
        return role
    }
    
    /**
     * Creates a least-privilege IAM role for Glue jobs in workflows.
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
     * @param scope CDK construct scope
     * @param id Construct ID
     * @param roleName Name of the IAM role
     * @param workflowBucket S3 bucket for workflow intermediate storage
     * @param scriptBucket S3 bucket containing Glue scripts (optional, defaults to workflowBucket)
     * @return Created IAM role
     * 
     * Validates: Requirement 11.9
     */
    fun createGlueJobRole(
        scope: Construct,
        id: String,
        roleName: String,
        workflowBucket: IBucket,
        scriptBucket: IBucket? = null
    ): Role {
        val role = Role.Builder.create(scope, id)
            .roleName(roleName)
            .assumedBy(ServicePrincipal("glue.amazonaws.com"))
            .description("Least-privilege execution role for CEAP workflow Glue job")
            .build()
        
        // Grant S3 read/write permissions scoped to executions/* prefix only
        // This prevents Glue job from accessing other data in the bucket
        role.addToPolicy(
            PolicyStatement.Builder.create()
                .sid("S3WorkflowAccess")
                .effect(Effect.ALLOW)
                .actions(listOf(
                    "s3:GetObject",
                    "s3:PutObject"
                ))
                .resources(listOf(
                    "${workflowBucket.bucketArn}/executions/*"
                ))
                .build()
        )
        
        // Grant S3 read/write for Glue temporary directory
        // Required for Glue to store intermediate data during processing
        role.addToPolicy(
            PolicyStatement.Builder.create()
                .sid("S3GlueTempAccess")
                .effect(Effect.ALLOW)
                .actions(listOf(
                    "s3:GetObject",
                    "s3:PutObject",
                    "s3:DeleteObject"
                ))
                .resources(listOf(
                    "${workflowBucket.bucketArn}/glue-temp/*"
                ))
                .build()
        )
        
        // Grant S3 read permission for Glue script location
        // Required for Glue to read the PySpark script
        val scriptBucketArn = scriptBucket?.bucketArn ?: workflowBucket.bucketArn
        role.addToPolicy(
            PolicyStatement.Builder.create()
                .sid("S3ScriptAccess")
                .effect(Effect.ALLOW)
                .actions(listOf(
                    "s3:GetObject"
                ))
                .resources(listOf(
                    "$scriptBucketArn/glue-scripts/*"
                ))
                .build()
        )
        
        // Grant CloudWatch Logs permissions for job logging
        // Required for Glue to write logs to CloudWatch
        role.addToPolicy(
            PolicyStatement.Builder.create()
                .sid("CloudWatchLogsAccess")
                .effect(Effect.ALLOW)
                .actions(listOf(
                    "logs:CreateLogGroup",
                    "logs:CreateLogStream",
                    "logs:PutLogEvents"
                ))
                .resources(listOf(
                    "arn:aws:logs:*:*:log-group:/aws-glue/jobs/*"
                ))
                .build()
        )
        
        // Grant Glue Data Catalog permissions for table access
        // Required for Glue to read/write table metadata
        // Note: This is scoped to specific actions, not full admin access
        role.addToPolicy(
            PolicyStatement.Builder.create()
                .sid("GlueDataCatalogAccess")
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
                .resources(listOf(
                    "arn:aws:glue:*:*:catalog",
                    "arn:aws:glue:*:*:database/ceap*",
                    "arn:aws:glue:*:*:table/ceap*/*"
                ))
                .build()
        )
        
        return role
    }
    
    /**
     * Creates a least-privilege IAM role for Step Functions workflows.
     * 
     * This role grants:
     * - Lambda invoke permissions for specific functions only
     * - Glue job start/stop permissions for specific jobs only
     * - CloudWatch Logs permissions for workflow logging
     * - X-Ray permissions for distributed tracing
     * 
     * The role does NOT grant:
     * - Invoke permissions for all Lambda functions
     * - Access to other AWS services
     * - Administrative permissions
     * 
     * @param scope CDK construct scope
     * @param id Construct ID
     * @param roleName Name of the IAM role
     * @param lambdaFunctions List of Lambda functions that the workflow can invoke
     * @param glueJobNames List of Glue job names that the workflow can start
     * @return Created IAM role
     * 
     * Validates: Requirement 11.9
     */
    fun createStepFunctionsRole(
        scope: Construct,
        id: String,
        roleName: String,
        lambdaFunctions: List<IFunction> = emptyList(),
        glueJobNames: List<String> = emptyList()
    ): Role {
        val role = Role.Builder.create(scope, id)
            .roleName(roleName)
            .assumedBy(ServicePrincipal("states.amazonaws.com"))
            .description("Least-privilege execution role for CEAP Step Functions workflow")
            .build()
        
        // Grant Lambda invoke permissions for specific functions only
        // This prevents the workflow from invoking arbitrary Lambda functions
        if (lambdaFunctions.isNotEmpty()) {
            role.addToPolicy(
                PolicyStatement.Builder.create()
                    .sid("LambdaInvokeAccess")
                    .effect(Effect.ALLOW)
                    .actions(listOf(
                        "lambda:InvokeFunction"
                    ))
                    .resources(lambdaFunctions.map { it.functionArn })
                    .build()
            )
        }
        
        // Grant Glue job start/stop permissions for specific jobs only
        // This prevents the workflow from starting arbitrary Glue jobs
        if (glueJobNames.isNotEmpty()) {
            role.addToPolicy(
                PolicyStatement.Builder.create()
                    .sid("GlueJobAccess")
                    .effect(Effect.ALLOW)
                    .actions(listOf(
                        "glue:StartJobRun",
                        "glue:GetJobRun",
                        "glue:GetJobRuns",
                        "glue:BatchStopJobRun"
                    ))
                    .resources(glueJobNames.map { jobName ->
                        "arn:aws:glue:*:*:job/$jobName"
                    })
                    .build()
            )
        }
        
        // Grant CloudWatch Logs permissions for workflow logging
        // Required for Step Functions to write logs to CloudWatch
        role.addToPolicy(
            PolicyStatement.Builder.create()
                .sid("CloudWatchLogsAccess")
                .effect(Effect.ALLOW)
                .actions(listOf(
                    "logs:CreateLogDelivery",
                    "logs:GetLogDelivery",
                    "logs:UpdateLogDelivery",
                    "logs:DeleteLogDelivery",
                    "logs:ListLogDeliveries",
                    "logs:PutResourcePolicy",
                    "logs:DescribeResourcePolicies",
                    "logs:DescribeLogGroups"
                ))
                .resources(listOf("*"))  // CloudWatch Logs requires wildcard for these actions
                .build()
        )
        
        // Grant X-Ray permissions for distributed tracing
        // Required for Step Functions to send trace data to X-Ray
        role.addToPolicy(
            PolicyStatement.Builder.create()
                .sid("XRayAccess")
                .effect(Effect.ALLOW)
                .actions(listOf(
                    "xray:PutTraceSegments",
                    "xray:PutTelemetryRecords",
                    "xray:GetSamplingRules",
                    "xray:GetSamplingTargets"
                ))
                .resources(listOf("*"))  // X-Ray requires wildcard
                .build()
        )
        
        return role
    }
    
    /**
     * Grants a Lambda function least-privilege access to the workflow bucket.
     * 
     * This method adds S3 permissions to an existing Lambda function's execution role,
     * scoped to the executions/* prefix only.
     * 
     * Use this method when you have an existing Lambda function and want to grant
     * it workflow bucket access without creating a new role.
     * 
     * @param lambdaFunction Lambda function to grant access to
     * @param workflowBucket S3 bucket for workflow intermediate storage
     * 
     * Validates: Requirement 11.9
     */
    fun grantWorkflowBucketAccess(
        lambdaFunction: IFunction,
        workflowBucket: IBucket
    ) {
        lambdaFunction.addToRolePolicy(
            PolicyStatement.Builder.create()
                .sid("S3WorkflowAccess")
                .effect(Effect.ALLOW)
                .actions(listOf(
                    "s3:GetObject",
                    "s3:PutObject"
                ))
                .resources(listOf(
                    "${workflowBucket.bucketArn}/executions/*"
                ))
                .build()
        )
    }
    
    /**
     * Grants a Glue job least-privilege access to the workflow bucket.
     * 
     * This method adds S3 permissions to an existing Glue job's execution role,
     * scoped to the executions/* prefix only.
     * 
     * Use this method when you have an existing Glue job and want to grant
     * it workflow bucket access without creating a new role.
     * 
     * @param glueJobRole Glue job IAM role to grant access to
     * @param workflowBucket S3 bucket for workflow intermediate storage
     * 
     * Validates: Requirement 11.9
     */
    fun grantWorkflowBucketAccessToGlueRole(
        glueJobRole: IRole,
        workflowBucket: IBucket
    ) {
        glueJobRole.addToPrincipalPolicy(
            PolicyStatement.Builder.create()
                .sid("S3WorkflowAccess")
                .effect(Effect.ALLOW)
                .actions(listOf(
                    "s3:GetObject",
                    "s3:PutObject"
                ))
                .resources(listOf(
                    "${workflowBucket.bucketArn}/executions/*"
                ))
                .build()
        )
    }
}
