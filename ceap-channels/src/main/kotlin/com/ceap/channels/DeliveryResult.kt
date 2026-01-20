package com.ceap.channels

import com.ceap.model.Candidate

/**
 * Result of a delivery operation through a channel adapter.
 * 
 * @property delivered List of successfully delivered candidates
 * @property failed List of failed deliveries with error information
 * @property metrics Metrics about the delivery operation
 */
data class DeliveryResult(
    val delivered: List<DeliveredCandidate>,
    val failed: List<FailedDelivery>,
    val metrics: DeliveryMetrics
)

/**
 * Represents a successfully delivered candidate.
 * 
 * @property candidate The candidate that was delivered
 * @property deliveryId Unique identifier for this delivery
 * @property timestamp Timestamp when the delivery occurred (epoch millis)
 * @property channelMetadata Optional channel-specific metadata about the delivery
 */
data class DeliveredCandidate(
    val candidate: Candidate,
    val deliveryId: String,
    val timestamp: Long,
    val channelMetadata: Map<String, Any>? = null
)

/**
 * Represents a failed delivery attempt.
 * 
 * @property candidate The candidate that failed to deliver
 * @property errorCode Error code indicating the type of failure
 * @property errorMessage Human-readable error message
 * @property timestamp Timestamp when the failure occurred (epoch millis)
 * @property retryable Whether this failure is retryable
 */
data class FailedDelivery(
    val candidate: Candidate,
    val errorCode: String,
    val errorMessage: String,
    val timestamp: Long,
    val retryable: Boolean = false
)

/**
 * Metrics about a delivery operation.
 * 
 * @property totalCandidates Total number of candidates in the delivery request
 * @property deliveredCount Number of successfully delivered candidates
 * @property failedCount Number of failed deliveries
 * @property durationMs Duration of the delivery operation in milliseconds
 * @property rateLimitedCount Number of candidates that were rate limited
 * @property shadowMode Whether this delivery was in shadow mode
 */
data class DeliveryMetrics(
    val totalCandidates: Int,
    val deliveredCount: Int,
    val failedCount: Int,
    val durationMs: Long,
    val rateLimitedCount: Int = 0,
    val shadowMode: Boolean = false
)
