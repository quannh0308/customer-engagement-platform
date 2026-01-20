package com.ceap.storage.arbitraries

import com.ceap.model.config.*
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators

/**
 * Arbitrary generators for configuration model classes.
 */
object ConfigArbitraries {
    
    fun dataConnectorConfigs(): Arbitrary<DataConnectorConfig> {
        val connectorIds = Arbitraries.of("order-connector", "review-connector", "event-connector")
        val connectorTypes = Arbitraries.of("kinesis", "s3", "api", "dynamodb-stream")
        val enabled = Arbitraries.of(true, false)
        val sourceConfig = Arbitraries.maps(
            Arbitraries.of("streamName", "bucketName", "endpoint"),
            Arbitraries.of("value1", "value2", "us-east-1")
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
    
    fun scoringModelConfigs(): Arbitrary<ScoringModelConfig> {
        val modelIds = Arbitraries.of("quality-model", "relevance-model", "engagement-model")
        val modelTypes = Arbitraries.of("ml-model", "rule-based", "heuristic")
        val enabled = Arbitraries.of(true, false)
        val modelConfig = Arbitraries.maps(
            Arbitraries.of("threshold", "weight", "version"),
            Arbitraries.of("0.5", "1.0", "v2")
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
    
    fun filterConfigs(): Arbitrary<FilterConfig> {
        val filterIds = Arbitraries.of("frequency-filter", "eligibility-filter", "quality-filter")
        val filterTypes = Arbitraries.of("eligibility", "quality", "timing")
        val enabled = Arbitraries.of(true, false)
        val parameters = Arbitraries.maps(
            Arbitraries.of("threshold", "windowDays", "maxCount"),
            Arbitraries.of("0.7", "30", "5")
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
    
    fun filterChainConfigs(): Arbitrary<FilterChainConfig> {
        return filterConfigs().list().ofMinSize(1).ofMaxSize(5).map { filters ->
            FilterChainConfig(filters = filters)
        }
    }
    
    fun channelConfigs(): Arbitrary<ChannelConfig> {
        val channelIds = Arbitraries.of("email-channel", "push-channel", "sms-channel")
        val channelTypes = Arbitraries.of("email", "push", "sms")
        val enabled = Arbitraries.of(true, false)
        val shadowMode = Arbitraries.of(true, false)
        val config = Arbitraries.maps(
            Arbitraries.of("templateId", "priority", "retryCount"),
            Arbitraries.of("template-123", "high", "3")
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
    
    fun programConfigs(): Arbitrary<ProgramConfig> {
        val programIds = Arbitraries.strings()
            .withCharRange('a', 'z')
            .withChars('-')
            .ofMinLength(5)
            .ofMaxLength(20)
            .map { "program-$it" }
        
        val programNames = Arbitraries.of(
            "Product Review Program",
            "Video Solicitation Program",
            "Music Review Program"
        )
        
        val enabled = Arbitraries.of(true, false)
        val marketplaces = Arbitraries.of("US", "UK", "DE", "FR", "JP").list().ofMinSize(1).ofMaxSize(5)
        val dataConnectorsList = dataConnectorConfigs().list().ofMinSize(1).ofMaxSize(3)
        val scoringModelsList = scoringModelConfigs().list().ofMinSize(1).ofMaxSize(3)
        val filterChain = filterChainConfigs()
        val channelsList = channelConfigs().list().ofMinSize(1).ofMaxSize(5)
        val batchSchedule = Arbitraries.of("0 0 * * *", "0 */6 * * *", "0 0 * * 0").injectNull(0.3)
        val reactiveEnabled = Arbitraries.of(true, false)
        val candidateTTLDays = Arbitraries.integers().between(1, 365)
        val timingWindowDays = Arbitraries.integers().between(1, 90).injectNull(0.3)
        
        data class FirstPart(
            val programId: String,
            val programName: String,
            val enabled: Boolean,
            val marketplaces: List<String>,
            val dataConnectors: List<DataConnectorConfig>,
            val scoringModels: List<ScoringModelConfig>,
            val filterChain: FilterChainConfig
        )
        
        val firstPartArb: Arbitrary<FirstPart> = Combinators.combine(
            programIds, programNames, enabled, marketplaces, dataConnectorsList, scoringModelsList, filterChain
        ).`as` { programId, programName, isEnabled, mkts, connectors, models, chain ->
            FirstPart(programId, programName, isEnabled, mkts, connectors, models, chain)
        }
        
        return firstPartArb.flatMap { first ->
            Combinators.combine(channelsList, batchSchedule, reactiveEnabled, candidateTTLDays, timingWindowDays)
                .`as` { channels, schedule, reactive, ttl, timingWindow ->
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
