package com.ceap.workflow.common

import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Isolates program workflows to prevent cascading failures.
 * 
 * Each program's workflow executes independently in its own execution context.
 * Failures in one program do not affect other programs' workflows.
 * 
 * Features:
 * - Independent execution contexts per program
 * - Failure isolation with circuit breaker pattern
 * - Timeout protection per program
 * - Concurrent execution of multiple programs
 * 
 * Validates: Requirements 13.1
 */
class ProgramWorkflowIsolator(
    private val maxConcurrentPrograms: Int = 10,
    private val defaultTimeoutMs: Long = 300_000L // 5 minutes default
) {
    
    private val logger = LoggerFactory.getLogger(ProgramWorkflowIsolator::class.java)
    private val executorService: ExecutorService = Executors.newFixedThreadPool(maxConcurrentPrograms)
    
    /**
     * Tracks program execution status.
     * Key: programId, Value: ProgramExecutionStatus
     */
    private val programStatus = ConcurrentHashMap<String, ProgramExecutionStatus>()
    
    /**
     * Result of a program workflow execution.
     */
    sealed class ExecutionResult<T> {
        data class Success<T>(val result: T, val programId: String) : ExecutionResult<T>()
        data class Failure<T>(val error: Throwable, val programId: String) : ExecutionResult<T>()
        data class Timeout<T>(val programId: String, val timeoutMs: Long) : ExecutionResult<T>()
    }
    
    /**
     * Tracks execution status for a program.
     */
    data class ProgramExecutionStatus(
        val programId: String,
        val isExecuting: Boolean = false,
        val lastExecutionTime: Long = 0L,
        val consecutiveFailures: Int = 0,
        val lastError: Throwable? = null
    )
    
    /**
     * Executes a workflow for a single program in isolation.
     * 
     * The workflow executes in a separate thread with timeout protection.
     * Failures are caught and logged without affecting other programs.
     * 
     * @param programId Program identifier
     * @param workflow Workflow function to execute
     * @param timeoutMs Timeout in milliseconds (optional, uses default if not specified)
     * @return Execution result
     */
    fun <T> executeIsolated(
        programId: String,
        workflow: () -> T,
        timeoutMs: Long = defaultTimeoutMs
    ): ExecutionResult<T> {
        logger.info("Starting isolated workflow execution: programId={}", programId)
        
        // Mark program as executing
        updateProgramStatus(programId, isExecuting = true)
        
        return try {
            // Execute workflow in separate thread with timeout
            val future = CompletableFuture.supplyAsync({
                try {
                    workflow()
                } catch (e: Exception) {
                    logger.error("Workflow execution failed: programId={}", programId, e)
                    throw e
                }
            }, executorService)
            
            // Wait for completion with timeout
            val result = future.get(timeoutMs, TimeUnit.MILLISECONDS)
            
            // Mark as successful
            updateProgramStatus(programId, isExecuting = false, consecutiveFailures = 0)
            
            logger.info("Workflow execution completed successfully: programId={}", programId)
            ExecutionResult.Success(result, programId)
            
        } catch (e: java.util.concurrent.TimeoutException) {
            logger.error("Workflow execution timed out: programId={}, timeoutMs={}", programId, timeoutMs)
            updateProgramStatus(programId, isExecuting = false, incrementFailures = true, error = e)
            ExecutionResult.Timeout(programId, timeoutMs)
            
        } catch (e: Exception) {
            logger.error("Workflow execution failed: programId={}", programId, e)
            updateProgramStatus(programId, isExecuting = false, incrementFailures = true, error = e)
            ExecutionResult.Failure(e, programId)
        }
    }
    
    /**
     * Executes workflows for multiple programs concurrently in isolation.
     * 
     * Each program's workflow executes independently. Failures in one program
     * do not affect other programs.
     * 
     * @param workflows Map of programId to workflow function
     * @param timeoutMs Timeout per program in milliseconds
     * @return Map of programId to execution result
     */
    fun <T> executeMultipleIsolated(
        workflows: Map<String, () -> T>,
        timeoutMs: Long = defaultTimeoutMs
    ): Map<String, ExecutionResult<T>> {
        logger.info("Starting isolated execution for {} programs", workflows.size)
        
        val results = ConcurrentHashMap<String, ExecutionResult<T>>()
        
        // Execute all workflows concurrently
        val futures = workflows.map { (programId, workflow) ->
            CompletableFuture.supplyAsync({
                val result = executeIsolated(programId, workflow, timeoutMs)
                results[programId] = result
                result
            }, executorService)
        }
        
        // Wait for all to complete
        CompletableFuture.allOf(*futures.toTypedArray()).join()
        
        // Log summary
        val successCount = results.values.count { it is ExecutionResult.Success }
        val failureCount = results.values.count { it is ExecutionResult.Failure }
        val timeoutCount = results.values.count { it is ExecutionResult.Timeout }
        
        logger.info(
            "Completed isolated execution: total={}, success={}, failure={}, timeout={}",
            workflows.size, successCount, failureCount, timeoutCount
        )
        
        return results
    }
    
    /**
     * Gets the execution status for a program.
     * 
     * @param programId Program identifier
     * @return Program execution status, or null if not found
     */
    fun getProgramStatus(programId: String): ProgramExecutionStatus? {
        return programStatus[programId]
    }
    
    /**
     * Checks if a program is currently executing.
     * 
     * @param programId Program identifier
     * @return true if program is executing, false otherwise
     */
    fun isExecuting(programId: String): Boolean {
        return programStatus[programId]?.isExecuting ?: false
    }
    
    /**
     * Gets the number of consecutive failures for a program.
     * 
     * @param programId Program identifier
     * @return Number of consecutive failures
     */
    fun getConsecutiveFailures(programId: String): Int {
        return programStatus[programId]?.consecutiveFailures ?: 0
    }
    
    /**
     * Resets the failure count for a program.
     * 
     * @param programId Program identifier
     */
    fun resetFailures(programId: String) {
        programStatus.computeIfPresent(programId) { _, status ->
            status.copy(consecutiveFailures = 0, lastError = null)
        }
    }
    
    /**
     * Updates the execution status for a program.
     */
    private fun updateProgramStatus(
        programId: String,
        isExecuting: Boolean,
        consecutiveFailures: Int? = null,
        incrementFailures: Boolean = false,
        error: Throwable? = null
    ) {
        programStatus.compute(programId) { _, existing ->
            val currentFailures = existing?.consecutiveFailures ?: 0
            val newFailures = when {
                consecutiveFailures != null -> consecutiveFailures
                incrementFailures -> currentFailures + 1
                else -> currentFailures
            }
            
            ProgramExecutionStatus(
                programId = programId,
                isExecuting = isExecuting,
                lastExecutionTime = System.currentTimeMillis(),
                consecutiveFailures = newFailures,
                lastError = error
            )
        }
    }
    
    /**
     * Shuts down the executor service.
     * 
     * Should be called when the isolator is no longer needed.
     */
    fun shutdown() {
        logger.info("Shutting down program workflow isolator")
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
