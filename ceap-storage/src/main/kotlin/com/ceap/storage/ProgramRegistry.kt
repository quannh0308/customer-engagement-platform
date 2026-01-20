package com.ceap.storage

import com.ceap.model.config.ProgramConfig
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

/**
 * Central registry for managing program configurations.
 * 
 * Provides high-level operations for program configuration management including:
 * - CRUD operations with validation
 * - Enable/disable program functionality
 * - Marketplace-specific configuration overrides
 * - Querying programs by various criteria
 */
class ProgramRegistry(
    private val repository: ProgramConfigRepository,
    private val validator: ProgramConfigValidator = ProgramConfigValidator()
) {
    
    constructor(dynamoDbClient: DynamoDbClient, tableName: String = DynamoDBConfig.TableNames.PROGRAM_CONFIG) :
        this(ProgramConfigRepository(dynamoDbClient, tableName))
    
    /**
     * Result of a program configuration operation.
     */
    sealed class OperationResult {
        data class Success(val config: ProgramConfig) : OperationResult()
        data class ValidationError(val errors: List<String>) : OperationResult()
        data class NotFound(val programId: String) : OperationResult()
        data class Error(val message: String, val cause: Throwable? = null) : OperationResult()
    }
    
    /**
     * Creates or updates a program configuration.
     * 
     * @param config Program configuration to save
     * @return Operation result
     */
    fun saveProgram(config: ProgramConfig): OperationResult {
        // Validate configuration
        val validationResult = validator.validate(config)
        if (!validationResult.isValid) {
            return OperationResult.ValidationError(validationResult.errors)
        }
        
        return try {
            repository.save(config)
            OperationResult.Success(config)
        } catch (e: Exception) {
            OperationResult.Error("Failed to save program configuration", e)
        }
    }
    
    /**
     * Retrieves a program configuration by ID.
     * 
     * @param programId Program identifier
     * @return Program configuration if found
     */
    fun getProgram(programId: String): OperationResult {
        return try {
            val config = repository.findByProgramId(programId)
            if (config != null) {
                OperationResult.Success(config)
            } else {
                OperationResult.NotFound(programId)
            }
        } catch (e: Exception) {
            OperationResult.Error("Failed to retrieve program configuration", e)
        }
    }
    
    /**
     * Retrieves a program configuration with marketplace-specific overrides applied.
     * 
     * @param programId Program identifier
     * @param marketplace Marketplace identifier
     * @return Program configuration with overrides applied if found
     */
    fun getProgramForMarketplace(programId: String, marketplace: String): OperationResult {
        return try {
            val config = repository.findByProgramIdAndMarketplace(programId, marketplace)
            if (config != null) {
                OperationResult.Success(config)
            } else {
                OperationResult.NotFound(programId)
            }
        } catch (e: Exception) {
            OperationResult.Error("Failed to retrieve program configuration for marketplace", e)
        }
    }
    
    /**
     * Retrieves all programs for a specific marketplace.
     * 
     * @param marketplace Marketplace identifier
     * @return List of program configurations
     */
    fun getProgramsForMarketplace(marketplace: String): List<ProgramConfig> {
        return try {
            repository.findByMarketplace(marketplace)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Retrieves all program configurations.
     * 
     * @return List of all program configurations
     */
    fun getAllPrograms(): List<ProgramConfig> {
        return try {
            repository.findAll()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Retrieves all enabled program configurations.
     * 
     * @return List of enabled program configurations
     */
    fun getEnabledPrograms(): List<ProgramConfig> {
        return getAllPrograms().filter { it.enabled }
    }
    
    /**
     * Enables a program.
     * 
     * @param programId Program identifier
     * @return Operation result
     */
    fun enableProgram(programId: String): OperationResult {
        return updateProgramStatus(programId, true)
    }
    
    /**
     * Disables a program.
     * 
     * When a program is disabled:
     * - No new candidates will be created
     * - Scheduled workflows will be skipped
     * - Existing candidates remain in storage
     * 
     * @param programId Program identifier
     * @return Operation result
     */
    fun disableProgram(programId: String): OperationResult {
        return updateProgramStatus(programId, false)
    }
    
    /**
     * Updates the enabled status of a program.
     */
    private fun updateProgramStatus(programId: String, enabled: Boolean): OperationResult {
        return try {
            // Verify program exists
            val existing = repository.findByProgramId(programId)
            if (existing == null) {
                return OperationResult.NotFound(programId)
            }
            
            // Update status
            repository.updateEnabled(programId, enabled)
            
            // Return updated config
            val updated = existing.copy(enabled = enabled)
            OperationResult.Success(updated)
        } catch (e: Exception) {
            OperationResult.Error("Failed to update program status", e)
        }
    }
    
    /**
     * Deletes a program configuration.
     * 
     * @param programId Program identifier
     * @return Operation result
     */
    fun deleteProgram(programId: String): OperationResult {
        return try {
            // Verify program exists
            val existing = repository.findByProgramId(programId)
            if (existing == null) {
                return OperationResult.NotFound(programId)
            }
            
            repository.delete(programId)
            OperationResult.Success(existing)
        } catch (e: Exception) {
            OperationResult.Error("Failed to delete program configuration", e)
        }
    }
    
    /**
     * Saves a marketplace-specific configuration override.
     * 
     * @param override Marketplace configuration override
     * @return Operation result
     */
    fun saveMarketplaceOverride(override: MarketplaceConfigOverride): OperationResult {
        return try {
            // Verify program exists
            val existing = repository.findByProgramId(override.programId)
            if (existing == null) {
                return OperationResult.NotFound(override.programId)
            }
            
            repository.saveMarketplaceOverride(override)
            
            // Return config with override applied
            val updated = applyMarketplaceOverrides(existing, override.marketplace, override.overrides)
            OperationResult.Success(updated)
        } catch (e: Exception) {
            OperationResult.Error("Failed to save marketplace override", e)
        }
    }
    
    /**
     * Checks if a program is enabled.
     * 
     * @param programId Program identifier
     * @return True if program is enabled, false otherwise
     */
    fun isProgramEnabled(programId: String): Boolean {
        return when (val result = getProgram(programId)) {
            is OperationResult.Success -> result.config.enabled
            else -> false
        }
    }
    
    /**
     * Checks if a program is enabled for a specific marketplace.
     * 
     * @param programId Program identifier
     * @param marketplace Marketplace identifier
     * @return True if program is enabled for the marketplace, false otherwise
     */
    fun isProgramEnabledForMarketplace(programId: String, marketplace: String): Boolean {
        return when (val result = getProgramForMarketplace(programId, marketplace)) {
            is OperationResult.Success -> result.config.enabled
            else -> false
        }
    }
}
