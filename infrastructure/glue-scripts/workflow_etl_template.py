"""
Glue Job PySpark Script Template for CEAP Workflow Integration

This template demonstrates how to integrate a Glue job into a CEAP workflow
using S3-based orchestration with convention-based path resolution.

Key Features:
- Reads arguments from Step Functions (execution-id, input/output paths)
- Reads input from S3 using input-bucket and input-key
- Implements ETL transformation logic
- Writes output to S3 using output-bucket and output-key
- Follows the same S3 path convention as Lambda functions

Validates: Requirements 5.6, 7.3, 7.4
"""

import sys
import json
from datetime import datetime
from awsglue.utils import getResolvedOptions
from pyspark.context import SparkContext
from awsglue.context import GlueContext
from awsglue.job import Job
from pyspark.sql import DataFrame
from pyspark.sql.functions import col, lit, current_timestamp

# ============================================================================
# Argument Parsing
# ============================================================================

# Get arguments from Step Functions
# These arguments are passed by the Step Functions workflow and follow
# the convention-based path resolution pattern
#
# Required arguments:
# - JOB_NAME: Glue job name (automatically provided by Glue)
# - execution-id: Step Functions execution ID (unique per workflow run)
# - input-bucket: S3 bucket name for reading input data
# - input-key: S3 key for reading input data (previous stage output)
# - output-bucket: S3 bucket name for writing output data
# - output-key: S3 key for writing output data (current stage output)
# - current-stage: Name of the current Glue job stage
# - previous-stage: Name of the previous stage (for logging/debugging)
#
# Validates: Requirement 5.5, 7.2
args = getResolvedOptions(sys.argv, [
    'JOB_NAME',
    'execution-id',
    'input-bucket',
    'input-key',
    'output-bucket',
    'output-key',
    'current-stage',
    'previous-stage'
])

# ============================================================================
# Spark and Glue Context Initialization
# ============================================================================

# Initialize Spark context
sc = SparkContext()
glueContext = GlueContext(sc)
spark = glueContext.spark_session
job = Job(glueContext)
job.init(args['JOB_NAME'], args)

# Log job start with execution context
print(f"=== Glue Job Started ===")
print(f"Job Name: {args['JOB_NAME']}")
print(f"Execution ID: {args['execution-id']}")
print(f"Current Stage: {args['current-stage']}")
print(f"Previous Stage: {args['previous-stage']}")
print(f"Timestamp: {datetime.now().isoformat()}")

# ============================================================================
# Input Data Reading
# ============================================================================

# Construct S3 input path from arguments
# This follows the convention: s3://{bucket}/{key}
# The key follows the pattern: executions/{executionId}/{previousStage}/output.json
#
# Validates: Requirement 5.6, 7.3
input_path = f"s3://{args['input-bucket']}/{args['input-key']}"
print(f"Reading input from: {input_path}")

try:
    # Read input data from S3 (previous stage output)
    # The data format is JSON, matching the Lambda function output format
    input_df = spark.read.json(input_path)
    
    # Log input data statistics
    input_record_count = input_df.count()
    print(f"Input records: {input_record_count}")
    print(f"Input schema:")
    input_df.printSchema()
    
except Exception as e:
    print(f"ERROR: Failed to read input from {input_path}")
    print(f"Error details: {str(e)}")
    raise

# ============================================================================
# ETL Transformation Logic
# ============================================================================

# This section contains the actual ETL transformation logic
# Replace this with your specific business logic
#
# Example transformations:
# - Data cleaning and validation
# - Complex joins with external data sources
# - Aggregations and analytics
# - Feature engineering for ML models
# - Data enrichment from data warehouses

print("=== Starting ETL Transformations ===")

try:
    # Example transformation 1: Add processing metadata
    # This adds metadata fields to track processing information
    transformed_df = input_df.withColumn(
        "glue_processing_timestamp",
        current_timestamp()
    ).withColumn(
        "glue_job_name",
        lit(args['JOB_NAME'])
    ).withColumn(
        "execution_id",
        lit(args['execution-id'])
    ).withColumn(
        "stage",
        lit(args['current-stage'])
    )
    
    # Example transformation 2: Data filtering
    # Filter records based on business rules
    # Replace this with your actual filtering logic
    if "candidates" in input_df.columns:
        print("Applying candidate filtering logic...")
        # Example: Filter candidates with score > 50
        # transformed_df = transformed_df.filter(col("score") > 50)
    
    # Example transformation 3: Data enrichment
    # Join with external data sources, perform lookups, etc.
    # Replace this with your actual enrichment logic
    print("Applying data enrichment logic...")
    # Example: Add additional fields, perform joins, etc.
    
    # Example transformation 4: Aggregations
    # Perform complex aggregations that would be expensive in Lambda
    # Replace this with your actual aggregation logic
    print("Applying aggregation logic...")
    # Example: Group by, aggregate, pivot, etc.
    
    # Log transformation statistics
    output_record_count = transformed_df.count()
    print(f"Output records: {output_record_count}")
    print(f"Records processed: {input_record_count}")
    print(f"Records filtered: {input_record_count - output_record_count}")
    print(f"Output schema:")
    transformed_df.printSchema()
    
except Exception as e:
    print(f"ERROR: ETL transformation failed")
    print(f"Error details: {str(e)}")
    raise

# ============================================================================
# Output Data Writing
# ============================================================================

# Construct S3 output path from arguments
# This follows the convention: s3://{bucket}/{key}
# The key follows the pattern: executions/{executionId}/{currentStage}/output.json
#
# Validates: Requirement 5.6, 7.4
output_path = f"s3://{args['output-bucket']}/{args['output-key']}"
print(f"Writing output to: {output_path}")

try:
    # Write output data to S3 for next stage
    # Use 'overwrite' mode to replace any existing data
    # Use 'json' format to match Lambda function output format
    #
    # Note: For large datasets, consider using Parquet format instead:
    # transformed_df.write.mode('overwrite').parquet(output_path)
    transformed_df.write.mode('overwrite').json(output_path)
    
    print(f"Successfully wrote {output_record_count} records to {output_path}")
    
except Exception as e:
    print(f"ERROR: Failed to write output to {output_path}")
    print(f"Error details: {str(e)}")
    raise

# ============================================================================
# Job Completion
# ============================================================================

# Log job completion
print(f"=== Glue Job Completed Successfully ===")
print(f"Execution ID: {args['execution-id']}")
print(f"Stage: {args['current-stage']}")
print(f"Input records: {input_record_count}")
print(f"Output records: {output_record_count}")
print(f"Processing time: {datetime.now().isoformat()}")

# Commit the job (marks it as successful)
job.commit()
