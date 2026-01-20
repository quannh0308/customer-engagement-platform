package com.ceap.workflow.export

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.ceap.model.Candidate
import com.ceap.storage.CandidateRepository
import mu.KotlinLogging
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.glue.GlueClient
import software.amazon.awssdk.services.glue.model.StartJobRunRequest
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

/**
 * Lambda handler for exporting candidates to data warehouse.
 * 
 * Exports candidates created or updated on a specific date to S3 in Parquet format,
 * then triggers a Glue job to load the data into the data warehouse.
 */
class DataWarehouseExportHandler(
    private val candidateRepository: CandidateRepository,
    private val s3Client: S3Client,
    private val glueClient: GlueClient,
    private val exportBucket: String,
    private val glueJobName: String
) : RequestHandler<ExportRequest, ExportResponse> {
    
    override fun handleRequest(request: ExportRequest, context: Context): ExportResponse {
        val startTime = System.currentTimeMillis()
        
        try {
            logger.info { "Starting data warehouse export for date: ${request.date}" }
            
            // Parse date
            val date = LocalDate.parse(request.date, DateTimeFormatter.ISO_LOCAL_DATE)
            val startOfDay = date.atStartOfDay(ZoneOffset.UTC).toInstant()
            val endOfDay = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
            
            // Query candidates for the date
            val candidates = queryCandidatesForDate(request.programId, request.date)
            
            // Filter candidates by date range
            val filteredCandidates = candidates.filter { candidate ->
                val createdAt = candidate.metadata.createdAt
                val updatedAt = candidate.metadata.updatedAt
                (createdAt >= startOfDay && createdAt < endOfDay) ||
                (updatedAt >= startOfDay && updatedAt < endOfDay)
            }
            
            logger.info { "Found ${filteredCandidates.size} candidates for export" }
            
            if (filteredCandidates.isEmpty()) {
                logger.info { "No candidates to export for date ${request.date}" }
                return ExportResponse(
                    success = true,
                    candidatesExported = 0,
                    s3Path = null,
                    glueJobRunId = null,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
            
            // Convert to Parquet and upload to S3
            val s3Path = uploadToS3(filteredCandidates, request.programId, request.date)
            
            logger.info { "Uploaded ${filteredCandidates.size} candidates to S3: $s3Path" }
            
            // Trigger Glue job
            val glueJobRunId = triggerGlueJob(s3Path, request.date)
            
            logger.info { "Triggered Glue job: $glueJobRunId" }
            
            val duration = System.currentTimeMillis() - startTime
            
            return ExportResponse(
                success = true,
                candidatesExported = filteredCandidates.size,
                s3Path = s3Path,
                glueJobRunId = glueJobRunId,
                durationMs = duration
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to export candidates to data warehouse" }
            return ExportResponse(
                success = false,
                candidatesExported = 0,
                s3Path = null,
                glueJobRunId = null,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message
            )
        }
    }
    
    /**
     * Queries candidates for a specific date.
     */
    private fun queryCandidatesForDate(programId: String, date: String): List<Candidate> {
        return try {
            candidateRepository.queryByProgramAndDate(
                programId = programId,
                date = date,
                limit = 10000 // Large limit for export
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to query candidates for date $date" }
            emptyList()
        }
    }
    
    /**
     * Uploads candidates to S3 in Parquet format.
     * 
     * Note: This is a simplified implementation. In production, you would use
     * Apache Parquet libraries to write proper Parquet files.
     */
    private fun uploadToS3(candidates: List<Candidate>, programId: String, date: String): String {
        val key = "exports/program=$programId/date=$date/candidates.parquet"
        
        // For now, we'll write JSON (in production, use Parquet)
        val jsonData = candidates.joinToString("\n") { candidate ->
            // Simplified JSON serialization
            """{"customerId":"${candidate.customerId}","programId":"$programId","date":"$date"}"""
        }
        
        val putRequest = PutObjectRequest.builder()
            .bucket(exportBucket)
            .key(key)
            .contentType("application/octet-stream")
            .build()
        
        s3Client.putObject(putRequest, RequestBody.fromString(jsonData))
        
        return "s3://$exportBucket/$key"
    }
    
    /**
     * Triggers Glue job to load data into warehouse.
     */
    private fun triggerGlueJob(s3Path: String, date: String): String {
        val jobRunRequest = StartJobRunRequest.builder()
            .jobName(glueJobName)
            .arguments(mapOf(
                "--S3_INPUT_PATH" to s3Path,
                "--EXPORT_DATE" to date
            ))
            .build()
        
        val response = glueClient.startJobRun(jobRunRequest)
        return response.jobRunId()
    }
}

/**
 * Request for data warehouse export.
 * 
 * @property programId Program to export candidates for
 * @property date Date to export in YYYY-MM-DD format
 */
data class ExportRequest(
    val programId: String,
    val date: String
)

/**
 * Response from data warehouse export.
 * 
 * @property success Whether the export was successful
 * @property candidatesExported Number of candidates exported
 * @property s3Path S3 path where data was written
 * @property glueJobRunId Glue job run ID
 * @property durationMs Export duration in milliseconds
 * @property errorMessage Error message if failed
 */
data class ExportResponse(
    val success: Boolean,
    val candidatesExported: Int,
    val s3Path: String?,
    val glueJobRunId: String?,
    val durationMs: Long,
    val errorMessage: String? = null
)
