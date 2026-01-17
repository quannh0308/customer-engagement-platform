package com.solicitation.storage

import com.solicitation.model.Candidate

/**
 * Repository interface for candidate persistence operations.
 * 
 * Provides CRUD operations, batch writes, queries, and optimistic locking
 * for candidate storage in DynamoDB.
 */
interface CandidateRepository {
    
    /**
     * Creates a new candidate in the repository.
     * 
     * @param candidate The candidate to create
     * @return The created candidate
     * @throws StorageException if the operation fails
     */
    fun create(candidate: Candidate): Candidate
    
    /**
     * Retrieves a candidate by its composite key.
     * 
     * @param customerId Customer identifier
     * @param programId Program identifier
     * @param marketplaceId Marketplace identifier
     * @param subjectType Subject type
     * @param subjectId Subject identifier
     * @return The candidate if found, null otherwise
     * @throws StorageException if the operation fails
     */
    fun get(
        customerId: String,
        programId: String,
        marketplaceId: String,
        subjectType: String,
        subjectId: String
    ): Candidate?
    
    /**
     * Updates an existing candidate with optimistic locking.
     * 
     * @param candidate The candidate to update (must have current version)
     * @return The updated candidate with incremented version
     * @throws OptimisticLockException if the version has changed
     * @throws StorageException if the operation fails
     */
    fun update(candidate: Candidate): Candidate
    
    /**
     * Deletes a candidate by its composite key.
     * 
     * @param customerId Customer identifier
     * @param programId Program identifier
     * @param marketplaceId Marketplace identifier
     * @param subjectType Subject type
     * @param subjectId Subject identifier
     * @throws StorageException if the operation fails
     */
    fun delete(
        customerId: String,
        programId: String,
        marketplaceId: String,
        subjectType: String,
        subjectId: String
    )
    
    /**
     * Batch writes multiple candidates.
     * Handles DynamoDB batch limits (25 items) and retries failed items.
     * 
     * @param candidates List of candidates to write
     * @return Result containing successful and failed items
     * @throws StorageException if the operation fails
     */
    fun batchWrite(candidates: List<Candidate>): BatchWriteResult
    
    /**
     * Queries candidates by program and channel, sorted by score.
     * Uses GSI-1 (ProgramChannelIndex).
     * 
     * @param programId Program identifier
     * @param channelId Channel identifier
     * @param limit Maximum number of results (default 100)
     * @param ascending Sort order (default false for descending scores)
     * @return List of candidates matching the query
     * @throws StorageException if the operation fails
     */
    fun queryByProgramAndChannel(
        programId: String,
        channelId: String,
        limit: Int = 100,
        ascending: Boolean = false
    ): List<Candidate>
    
    /**
     * Queries candidates by program and date.
     * Uses GSI-2 (ProgramDateIndex).
     * 
     * @param programId Program identifier
     * @param date Date in YYYY-MM-DD format
     * @param limit Maximum number of results (default 100)
     * @return List of candidates matching the query
     * @throws StorageException if the operation fails
     */
    fun queryByProgramAndDate(
        programId: String,
        date: String,
        limit: Int = 100
    ): List<Candidate>
}

/**
 * Result of a batch write operation.
 * 
 * @property successfulItems Candidates that were successfully written
 * @property failedItems Candidates that failed to write with error messages
 */
data class BatchWriteResult(
    val successfulItems: List<Candidate>,
    val failedItems: List<FailedItem>
)

/**
 * Represents a failed item in a batch write operation.
 * 
 * @property candidate The candidate that failed to write
 * @property errorMessage Error message describing the failure
 */
data class FailedItem(
    val candidate: Candidate,
    val errorMessage: String
)

/**
 * Exception thrown when storage operations fail.
 */
class StorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Exception thrown when optimistic locking detects a version conflict.
 */
class OptimisticLockException(message: String) : RuntimeException(message)
