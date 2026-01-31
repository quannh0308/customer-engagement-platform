package com.ceap.workflow.common

/**
 * Result returned by each Lambda stage after processing.
 * 
 * This data class provides execution status and metrics for observability
 * and error tracking in Step Functions execution history.
 * 
 * @property status Execution status: "SUCCESS" or "FAILED"
 * @property stage Stage name that produced this result
 * @property recordsProcessed Number of records processed by this stage
 * @property errorMessage Error details if status is "FAILED", null otherwise
 * 
 * Validates: Requirements 8.4
 */
data class StageResult(
    val status: String,
    val stage: String,
    val recordsProcessed: Int,
    val errorMessage: String? = null
)
