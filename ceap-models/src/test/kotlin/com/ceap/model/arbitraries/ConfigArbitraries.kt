package com.ceap.model.arbitraries

import com.ceap.model.config.*
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators

/**
 * Arbitrary generators for configuration model classes.
 * These generators create valid random instances for property-based testing.
 */
object ConfigArbitraries {
    
    /**
     * Generates arbitrary DataConnectorConfig instances.
     */
    fun dataConnectorConfigs(): Arbitrary<DataConnectorConfig> {
        val connectorIds = Arbitraries.of(
            "order-connector",
            "review-connector",
            "event-connector",
            "subscription-connector",
            "kinesis-connector"
        )
        val connectorTypes = Arbitraries.of("kinesis", "s3", "api", "dynamodb-stream", "sqs")
        val enabled = Arbitraries.of(true, false)
        val sourceConfig = Arbitraries.maps(
            Arbitraries.of("streamName", "bucketName", "endpoint", "region", "batchSize"),
            Arbitraries.of("value1", "value2", "us-east-1", "100", "my-stream")
        ).ofMaxSize(3)
        
        return Combinators.combine(connectorIds, connectorTypes, enabled, sourceConfig)
            .`as` { connectorId, connectorType, isEnabled, config ->
                DataConnectorConfig(
                    connectorId = connectorId,
                    connectorType = connectorType,
                    enabled = isEnabled,
                    sourceConfig = if (config.isEmpty()) null else config
                )
            }
    }
    
    /**
     * Generates arbitrary ScoringModelConfig instances.
     */
    fun scoringModelConfigs(): Arbitrary<ScoringModelConfig> {
        val modelIds = Arbitraries.of(
            "quality-model",
            "relevance-model",
            "engagement-model",
            "sentiment-model",
            "propensity-model"
        )
        val modelTypes = Arbitraries.of("ml-model", "rule-based", "heuristic", "ensemble")
        val enabled = Arbitraries.of(true, false)
        val modelConfig = Arbitraries.maps(
            Arbitraries.of("threshold", "weight", "version", "endpoint"),
            Arbitraries.of("0.5", "1.0", "v2", "https://api.example.com")
        ).ofMaxSize(3)
        
        return Combinators.combine(modelIds, modelTypes, enabled, modelConfig)
            .`as` { modelId, modelType, isEnabled, config ->
                ScoringModelConfig(
                    modelId = modelId,
                    modelType = modelType,
                    enabled = isEnabled,
                    modelConfig = if (config.isEmpty()) null else config
                )
            }
    }
    
    /**
     * Generates arbitrary FilterConfig instances.
     */
    fun filterConfigs(): Arbitrary<FilterConfig> {
        val filterIds = Arbitraries.of(
            "frequency-filter",
            "eligibility-filter",
            "quality-filter",
            "timing-filter",
            "deduplication-filter"
        )
        val filterTypes = Arbitraries.of("eligibility", "quality", "timing", "frequency", "deduplication")
        val enabled = Arbitraries.of(true, false)
        val parameters = Arbitraries.maps(
            Arbitraries.of("threshold", "windowDays", "maxCount", "minScore"),
            Arbitraries.of("0.7", "30", "5", "0.5")
        ).ofMaxSize(3)
        val order = Arbitraries.integers().between(0, 10)
        
        return Combinators.combine(filterIds, filterTypes, enabled, parameters, order)
            .`as` { filterId, filterType, isEnabled, params, filterOrder ->
                FilterConfig(
                    filterId = filterId,
                    filterType = filterType,
                    enabled = isEnabled,
                    parameters = if (params.isEmpty()) null else params,
                    order = filterOrder
                )
            }
    }
    
    /**
     * Generates arbitrary FilterChainConfig instances.
     */
    fun filterChainConfigs(): Arbitrary<FilterChainConfig> {
        val filtersList = filterConfigs().list().ofMinSize(1).ofMaxSize(5)
        
        return filtersList.map { filters ->
            FilterChainConfig(filters = filters)
        }
    }
    
    /**
     * Generates arbitrary ChannelConfig instances.
     */
    fun channelConfigs(): Arbitrary<ChannelConfig> {
        val channelIds = Arbitraries.of(
            "email-channel",
            "push-channel",
            "sms-channel",
            "in-app-channel",
            "web-channel"
        )
        val channelTypes = Arbitraries.of("email", "push", "sms", "in-app", "web")
        val enabled = Arbitraries.of(true, false)
        val shadowMode = Arbitraries.of(true, false)
        val config = Arbitraries.maps(
            Arbitraries.of("templateId", "priority", "retryCount", "timeout"),
            Arbitraries.of("template-123", "high", "3", "5000")
        ).ofMaxSize(3)
        
        return Combinators.combine(channelIds, channelTypes, enabled, shadowMode, config)
            .`as` { channelId, channelType, isEnabled, isShadowMode, channelConfig ->
                ChannelConfig(
                    channelId = channelId,
                    channelType = channelType,
                    enabled = isEnabled,
                    shadowMode = isShadowMode,
                    config = if (channelConfig.isEmpty()) null else channelConfig
                )
            }
    }
    
    /**
     * Generates arbitrary ProgramConfig instances with all required fields.
     */
    fun programConfigs(): Arbitrary<ProgramConfig> {
        val programIds = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withChars('-')
            .ofMinLength(5)
            .ofMaxLength(20)
            .map { "program-$it" }
        
        val programNames = Arbitraries.of(
            "Product Review Program",
            "Video Engagement Program",
            "Music Review Program",
            "Service Feedback Program",
            "Event Review Program"
        )
        
        val enabled = Arbitraries.of(true, false)
        
        val marketplaces = Arbitraries.of("US", "UK", "DE", "FR", "JP", "CA", "AU")
            .list()
            .ofMinSize(1)
            .ofMaxSize(5)
        
        val dataConnectorsList = dataConnectorConfigs().list().ofMinSize(1).ofMaxSize(3)
        val scoringModelsList = scoringModelConfigs().list().ofMinSize(1).ofMaxSize(3)
        val filterChain = filterChainConfigs()
        val channelsList = channelConfigs().list().ofMinSize(1).ofMaxSize(5)
        
        val batchSchedule = Arbitraries.of(
            "0 0 * * *",      // Daily at midnight
            "0 */6 * * *",    // Every 6 hours
            "0 0 * * 0"       // Weekly on Sunday
        ).injectNull(0.3)
        
        val reactiveEnabled = Arbitraries.of(true, false)
        val candidateTTLDays = Arbitraries.integers().between(1, 365)
        val timingWindowDays = Arbitraries.integers().between(1, 90).injectNull(0.3)
        
        // Create a data class to hold the first 7 parameters
        data class FirstPart(
            val programId: String,
            val programName: String,
            val enabled: Boolean,
            val marketplaces: List<String>,
            val dataConnectors: List<DataConnectorConfig>,
            val scoringModels: List<ScoringModelConfig>,
            val filterChain: FilterChainConfig
        )
        
        // Combine first 7 parameters into a data class
        val firstPartArb: Arbitrary<FirstPart> = Combinators.combine(
            programIds,
            programNames,
            enabled,
            marketplaces,
            dataConnectorsList,
            scoringModelsList,
            filterChain
        ).`as` { programId, programName, isEnabled, mkts, connectors, models, chain ->
            FirstPart(programId, programName, isEnabled, mkts, connectors, models, chain)
        }
        
        // Use flatMap to combine with remaining parameters
        return firstPartArb.flatMap { first ->
            Combinators.combine(
                channelsList,
                batchSchedule,
                reactiveEnabled,
                candidateTTLDays,
                timingWindowDays
            ).`as` { channels, schedule, reactive, ttl, timingWindow ->
                ProgramConfig(
                    programId = first.programId,
                    programName = first.programName,
                    enabled = first.enabled,
                    marketplaces = first.marketplaces,
                    dataConnectors = first.dataConnectors,
                    scoringModels = first.scoringModels,
                    filterChain = first.filterChain,
                    channels = channels,
                    batchSchedule = schedule,
                    reactiveEnabled = reactive,
                    candidateTTLDays = ttl,
                    timingWindowDays = timingWindow
                )
            }
        }
    }
}
