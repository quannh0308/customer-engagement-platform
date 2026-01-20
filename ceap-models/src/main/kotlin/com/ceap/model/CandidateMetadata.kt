package com.ceap.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Positive
import java.time.Instant

/**
 * System-level metadata for candidate tracking.
 *
 * @property createdAt When the candidate was created
 * @property updatedAt When the candidate was last updated
 * @property expiresAt When the candidate expires (for TTL)
 * @property version Version number for optimistic locking
 * @property sourceConnectorId ID of the data connector that created this candidate
 * @property workflowExecutionId Workflow execution ID for traceability
 * @property experimentTreatment Assigned experiment treatment (optional)
 */
data class CandidateMetadata(
    @field:NotNull
    @JsonProperty("createdAt")
    val createdAt: Instant,
    
    @field:NotNull
    @JsonProperty("updatedAt")
    val updatedAt: Instant,
    
    @field:NotNull
    @JsonProperty("expiresAt")
    val expiresAt: Instant,
    
    @field:Positive
    @JsonProperty("version")
    val version: Long,
    
    @field:NotBlank
    @JsonProperty("sourceConnectorId")
    val sourceConnectorId: String,
    
    @field:NotBlank
    @JsonProperty("workflowExecutionId")
    val workflowExecutionId: String,
    
    @JsonProperty("experimentTreatment")
    val experimentTreatment: ExperimentTreatment? = null
)

/**
 * Records the experiment treatment assigned to a candidate.
 *
 * @property experimentId ID of the experiment
 * @property treatmentId ID of the assigned treatment
 */
data class ExperimentTreatment(
    @field:NotBlank
    @JsonProperty("experimentId")
    val experimentId: String,
    
    @field:NotBlank
    @JsonProperty("treatmentId")
    val treatmentId: String
)
