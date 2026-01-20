package com.ceap.channels

/**
 * Context information for a delivery operation.
 * 
 * Provides metadata about the delivery request including program, marketplace,
 * campaign information, and operational flags like shadow mode.
 * 
 * @property programId Identifier of the solicitation program
 * @property marketplace Marketplace identifier (e.g., "US", "UK", "DE")
 * @property campaignId Optional campaign identifier for tracking
 * @property shadowMode Whether this delivery should be in shadow mode (log only, no actual delivery)
 */
data class DeliveryContext(
    val programId: String,
    val marketplace: String,
    val campaignId: String? = null,
    val shadowMode: Boolean = false
)
