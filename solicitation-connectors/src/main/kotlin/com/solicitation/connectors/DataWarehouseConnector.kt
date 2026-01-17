package com.solicitation.connectors

import com.solicitation.model.*
import com.solicitation.model.config.DataConnectorConfig
import software.amazon.awssdk.services.athena.AthenaClient
import software.amazon.awssdk.services.athena.model.*
import java.time.Instant
import java.time.Duration

/**
 * Data connector for extracting data from data warehouses via AWS Athena.
 *
 * Configuration (in sourceConfig):
 * - database: Athena database name (required)
 * - query: SQL query to execute (required)
 * - resultsBucket: S3 bucket for query results (required)
 * - workGroup: Athena workgroup (optional, defaults to "primary")
 * - fieldMappings: Map of target field -> source field mappings (optional)
 * - maxWaitSeconds: Maximum time to wait for query completion (optional, defaults to 300)
 */
class DataWarehouseConnector(
    private val athenaClient: AthenaClient
) : BaseDataConnector() {
    
    companion object {
        const val CONNECTOR_TYPE = "data-warehouse"
        private const val DEFAULT_MAX_WAIT_SECONDS = 300L
        private const val POLL_INTERVAL_MILLIS = 1000L
    }
    
    override fun getName(): String = CONNECTOR_TYPE
    
    override fun validateSourceConfig(config: DataConnectorConfig): List<String> {
        val errors = mutableListOf<String>()
        val sourceConfig = config.sourceConfig ?: return listOf("Source configuration is required")
        
        // Validate required fields
        if (!sourceConfig.containsKey("database") || (sourceConfig["database"] as? String).isNullOrBlank()) {
            errors.add("Source config must contain 'database' field")
        }
        
        if (!sourceConfig.containsKey("query") || (sourceConfig["query"] as? String).isNullOrBlank()) {
            errors.add("Source config must contain 'query' field")
        }
        
        if (!sourceConfig.containsKey("resultsBucket") || (sourceConfig["resultsBucket"] as? String).isNullOrBlank()) {
            errors.add("Source config must contain 'resultsBucket' field")
        }
        
        return errors
    }
    
    override fun extractData(
        config: DataConnectorConfig,
        parameters: Map<String, Any>
    ): List<Map<String, Any>> {
        val sourceConfig = config.sourceConfig 
            ?: throw DataExtractionException("Source configuration is required")
        
        val database = sourceConfig["database"] as String
        val query = sourceConfig["query"] as String
        val resultsBucket = sourceConfig["resultsBucket"] as String
        val workGroup = sourceConfig["workGroup"] as? String ?: "primary"
        val maxWaitSeconds = (sourceConfig["maxWaitSeconds"] as? Number)?.toLong() ?: DEFAULT_MAX_WAIT_SECONDS
        
        logger.info { "Executing Athena query in database '$database', workgroup '$workGroup'" }
        logger.debug { "Query: $query" }
        
        try {
            // Start query execution
            val queryExecutionId = startQueryExecution(database, query, resultsBucket, workGroup)
            
            // Wait for query to complete
            waitForQueryCompletion(queryExecutionId, maxWaitSeconds)
            
            // Retrieve results
            val results = getQueryResults(queryExecutionId)
            
            logger.info { "Successfully extracted ${results.size} records from data warehouse" }
            return results
            
        } catch (e: AthenaException) {
            throw DataExtractionException("Athena query failed: ${e.message}", e)
        } catch (e: Exception) {
            throw DataExtractionException("Data extraction failed: ${e.message}", e)
        }
    }
    
    override fun transformToCandidate(
        rawData: Map<String, Any>,
        config: DataConnectorConfig
    ): Candidate? {
        try {
            // Apply field mappings if configured
            val mappedData = applyFieldMappings(rawData, config)
            
            // Validate required fields
            val requiredFields = listOf("customerId", "subjectType", "subjectId", "eventDate")
            val missingFields = validateRequiredFields(mappedData, requiredFields)
            if (missingFields.isNotEmpty()) {
                return logTransformationError(
                    rawData,
                    "Missing required fields: ${missingFields.joinToString(", ")}"
                )
            }
            
            // Extract and build candidate components
            val customerId = mappedData["customerId"] as String
            val context = extractContext(mappedData)
            val subject = extractSubject(mappedData)
            val attributes = extractAttributes(mappedData)
            val metadata = extractMetadata(mappedData, config)
            
            return Candidate(
                customerId = customerId,
                context = context,
                subject = subject,
                scores = null, // Scores are added by scoring engine
                attributes = attributes,
                metadata = metadata,
                rejectionHistory = null
            )
            
        } catch (e: Exception) {
            return logTransformationError(rawData, "Transformation failed: ${e.message}", e)
        }
    }
    
    /**
     * Applies field mappings from source config if present.
     */
    private fun applyFieldMappings(
        rawData: Map<String, Any>,
        config: DataConnectorConfig
    ): Map<String, Any> {
        val fieldMappings = config.sourceConfig?.get("fieldMappings") as? Map<*, *>
        
        return if (fieldMappings != null) {
            @Suppress("UNCHECKED_CAST")
            val mapper = FieldMapper.fromConfig(fieldMappings as Map<String, Any>)
            mapper.mapFields(rawData).filterValues { it != null } as Map<String, Any>
        } else {
            rawData
        }
    }
    
    /**
     * Extracts context list from mapped data.
     */
    private fun extractContext(data: Map<String, Any>): List<Context> {
        val contexts = mutableListOf<Context>()
        
        // Extract marketplace context
        data["marketplace"]?.let { marketplace ->
            contexts.add(Context(type = "marketplace", id = marketplace.toString()))
        }
        
        // Extract program context
        data["programId"]?.let { programId ->
            contexts.add(Context(type = "program", id = programId.toString()))
        }
        
        // Extract vertical context
        data["vertical"]?.let { vertical ->
            contexts.add(Context(type = "vertical", id = vertical.toString()))
        }
        
        // Extract additional contexts from contextList if present
        (data["contextList"] as? List<*>)?.forEach { ctx ->
            if (ctx is Map<*, *>) {
                val type = ctx["type"] as? String
                val id = ctx["id"] as? String
                if (type != null && id != null) {
                    contexts.add(Context(type = type, id = id))
                }
            }
        }
        
        if (contexts.isEmpty()) {
            throw TransformationException("At least one context is required")
        }
        
        return contexts
    }
    
    /**
     * Extracts subject from mapped data.
     */
    private fun extractSubject(data: Map<String, Any>): Subject {
        val subjectType = data["subjectType"] as String
        val subjectId = data["subjectId"] as String
        val subjectMetadata = data["subjectMetadata"] as? Map<String, Any>
        
        return Subject(
            type = subjectType,
            id = subjectId,
            metadata = subjectMetadata
        )
    }
    
    /**
     * Extracts candidate attributes from mapped data.
     */
    private fun extractAttributes(data: Map<String, Any>): CandidateAttributes {
        val eventDate = parseInstant(data["eventDate"] 
            ?: throw TransformationException("eventDate is required"))
        val deliveryDate = data["deliveryDate"]?.let { parseInstant(it) }
        val timingWindow = data["timingWindow"] as? String
        val orderValue = (data["orderValue"] as? Number)?.toDouble()
        val mediaEligible = data["mediaEligible"] as? Boolean
        
        // Extract channel eligibility
        val channelEligibility = extractChannelEligibility(data)
        
        return CandidateAttributes(
            eventDate = eventDate,
            deliveryDate = deliveryDate,
            timingWindow = timingWindow,
            orderValue = orderValue,
            mediaEligible = mediaEligible,
            channelEligibility = channelEligibility
        )
    }
    
    /**
     * Extracts channel eligibility map from data.
     */
    private fun extractChannelEligibility(data: Map<String, Any>): Map<String, Boolean> {
        // Check if channelEligibility is provided as a map
        (data["channelEligibility"] as? Map<*, *>)?.let { eligibility ->
            @Suppress("UNCHECKED_CAST")
            return eligibility as Map<String, Boolean>
        }
        
        // Otherwise, build from individual channel flags
        val eligibility = mutableMapOf<String, Boolean>()
        data["emailEligible"]?.let { eligibility["email"] = it as Boolean }
        data["smsEligible"]?.let { eligibility["sms"] = it as Boolean }
        data["pushEligible"]?.let { eligibility["push"] = it as Boolean }
        
        // Default to email eligible if no channels specified
        if (eligibility.isEmpty()) {
            eligibility["email"] = true
        }
        
        return eligibility
    }
    
    /**
     * Extracts candidate metadata from mapped data.
     */
    private fun extractMetadata(
        data: Map<String, Any>,
        config: DataConnectorConfig
    ): CandidateMetadata {
        val now = Instant.now()
        val ttlDays = (data["ttlDays"] as? Number)?.toLong() ?: 30L
        val expiresAt = now.plus(Duration.ofDays(ttlDays))
        
        return CandidateMetadata(
            createdAt = now,
            updatedAt = now,
            expiresAt = expiresAt,
            version = 1L,
            sourceConnectorId = config.connectorId,
            workflowExecutionId = data["workflowExecutionId"] as? String ?: "unknown"
        )
    }
    
    /**
     * Parses an object to Instant.
     */
    private fun parseInstant(value: Any): Instant {
        return when (value) {
            is Instant -> value
            is String -> Instant.parse(value)
            is Number -> Instant.ofEpochMilli(value.toLong())
            else -> throw TransformationException("Cannot parse $value as Instant")
        }
    }
    
    /**
     * Starts an Athena query execution.
     */
    private fun startQueryExecution(
        database: String,
        query: String,
        resultsBucket: String,
        workGroup: String
    ): String {
        val queryExecutionContext = QueryExecutionContext.builder()
            .database(database)
            .build()
        
        val resultConfiguration = ResultConfiguration.builder()
            .outputLocation("s3://$resultsBucket/")
            .build()
        
        val request = StartQueryExecutionRequest.builder()
            .queryString(query)
            .queryExecutionContext(queryExecutionContext)
            .resultConfiguration(resultConfiguration)
            .workGroup(workGroup)
            .build()
        
        val response = athenaClient.startQueryExecution(request)
        return response.queryExecutionId()
    }
    
    /**
     * Waits for query execution to complete.
     */
    private fun waitForQueryCompletion(queryExecutionId: String, maxWaitSeconds: Long) {
        val startTime = System.currentTimeMillis()
        val maxWaitMillis = maxWaitSeconds * 1000
        
        while (true) {
            val request = GetQueryExecutionRequest.builder()
                .queryExecutionId(queryExecutionId)
                .build()
            
            val response = athenaClient.getQueryExecution(request)
            val state = response.queryExecution().status().state()
            
            when (state) {
                QueryExecutionState.SUCCEEDED -> {
                    logger.info { "Query execution succeeded: $queryExecutionId" }
                    return
                }
                QueryExecutionState.FAILED -> {
                    val reason = response.queryExecution().status().stateChangeReason()
                    throw DataExtractionException("Query execution failed: $reason")
                }
                QueryExecutionState.CANCELLED -> {
                    throw DataExtractionException("Query execution was cancelled")
                }
                else -> {
                    // Still running, check timeout
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed > maxWaitMillis) {
                        throw DataExtractionException("Query execution timed out after $maxWaitSeconds seconds")
                    }
                    Thread.sleep(POLL_INTERVAL_MILLIS)
                }
            }
        }
    }
    
    /**
     * Retrieves query results.
     */
    private fun getQueryResults(queryExecutionId: String): List<Map<String, Any>> {
        val results = mutableListOf<Map<String, Any>>()
        var nextToken: String? = null
        
        do {
            val request = GetQueryResultsRequest.builder()
                .queryExecutionId(queryExecutionId)
                .apply { nextToken?.let { this.nextToken(it) } }
                .build()
            
            val response = athenaClient.getQueryResults(request)
            val resultSet = response.resultSet()
            
            // Extract column names from first row (header)
            val columnNames = resultSet.rows().firstOrNull()?.data()?.map { it.varCharValue() } ?: emptyList()
            
            // Process data rows (skip header)
            resultSet.rows().drop(1).forEach { row ->
                val record = mutableMapOf<String, Any>()
                row.data().forEachIndexed { index, datum ->
                    if (index < columnNames.size) {
                        val columnName = columnNames[index]
                        val value = datum.varCharValue()
                        if (value != null) {
                            record[columnName] = value
                        }
                    }
                }
                results.add(record)
            }
            
            nextToken = response.nextToken()
        } while (nextToken != null)
        
        return results
    }
}
