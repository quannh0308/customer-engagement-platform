package com.ceap.scoring

import kotlinx.coroutines.runBlocking
import net.jqwik.api.*
import net.jqwik.api.constraints.NotEmpty
import net.jqwik.api.constraints.Size
import net.jqwik.api.constraints.StringLength
import org.assertj.core.api.Assertions.assertThat

/**
 * Property-based tests for feature retrieval completeness.
 * 
 * **Property 8: Feature retrieval completeness**
 * 
 * *For any* scoring request, all features listed in the scoring provider's
 * required features must be retrieved from the feature store before scoring
 * is attempted.
 * 
 * **Validates: Requirements 3.2**
 */
class FeatureRetrievalPropertyTest {
    
    /**
     * Property 8: All required features must be retrieved.
     * 
     * Tests that:
     * 1. Feature store client retrieves all requested features
     * 2. Returned feature map contains all required features
     * 3. No features are missing from the response
     */
    @Property(tries = 100)
    fun allRequiredFeaturesAreRetrieved(
        @ForAll @StringLength(min = 5, max = 20) customerId: String,
        @ForAll @StringLength(min = 5, max = 20) subjectId: String,
        @ForAll @NotEmpty @Size(min = 1, max = 10) requiredFeatures: List<@StringLength(min = 3, max = 20) String>
    ) {
        runBlocking {
            // Arrange
            val featureStoreClient = FeatureStoreClient()
            
            // Act: Retrieve features
            val featureMap = featureStoreClient.getFeatures(customerId, subjectId, requiredFeatures)
            
            // Assert: All required features are present
            assertThat(featureMap.customerId).isEqualTo(customerId)
            assertThat(featureMap.subjectId).isEqualTo(subjectId)
            assertThat(featureMap.features.keys).containsAll(requiredFeatures)
            
            // Verify each feature has a value
            requiredFeatures.forEach { featureName ->
                assertThat(featureMap.features).containsKey(featureName)
                val featureValue = featureMap.features[featureName]
                assertThat(featureValue).isNotNull
            }
        }
    }
    
    /**
     * Property 8b: Feature validation catches missing features.
     * 
     * Tests that the validator correctly identifies missing features.
     */
    @Property(tries = 100)
    fun validationCatchesMissingFeatures(
        @ForAll @StringLength(min = 5, max = 20) customerId: String,
        @ForAll @StringLength(min = 5, max = 20) subjectId: String,
        @ForAll @NotEmpty @Size(min = 2, max = 10) requiredFeatures: List<@StringLength(min = 3, max = 20) String>,
        @ForAll @NotEmpty @Size(min = 1, max = 5) providedFeatures: List<@StringLength(min = 3, max = 20) String>
    ) {
        // Arrange
        val validator = FeatureValidator()
        
        // Create a feature map with only some of the required features
        val features = FeatureMap(
            customerId = customerId,
            subjectId = subjectId,
            features = providedFeatures.associateWith { 
                FeatureValue(value = 1.0, type = FeatureType.NUMERIC)
            }
        )
        
        // Act: Validate features
        val result = validator.validate(requiredFeatures, features)
        
        // Assert: Validation detects missing features
        val expectedMissing = requiredFeatures.filter { it !in providedFeatures }
        
        if (expectedMissing.isEmpty()) {
            assertThat(result.valid).isTrue()
            assertThat(result.missingFeatures).isEmpty()
        } else {
            assertThat(result.valid).isFalse()
            assertThat(result.missingFeatures).containsAll(expectedMissing)
        }
    }
    
    /**
     * Property 8c: Feature validation catches invalid values.
     * 
     * Tests that the validator correctly identifies invalid feature values.
     */
    @Property(tries = 100)
    fun validationCatchesInvalidValues(
        @ForAll @StringLength(min = 5, max = 20) customerId: String,
        @ForAll @StringLength(min = 5, max = 20) subjectId: String,
        @ForAll @NotEmpty @Size(min = 1, max = 5) featureNames: List<@StringLength(min = 3, max = 20) String>
    ) {
        // Arrange
        val validator = FeatureValidator()
        
        // Create a feature map with invalid numeric values (NaN)
        val features = FeatureMap(
            customerId = customerId,
            subjectId = subjectId,
            features = featureNames.associateWith { 
                FeatureValue(value = Double.NaN, type = FeatureType.NUMERIC)
            }
        )
        
        // Act: Validate features
        val result = validator.validate(featureNames, features)
        
        // Assert: Validation detects invalid values
        assertThat(result.valid).isFalse()
        assertThat(result.invalidFeatures).containsAll(featureNames)
    }
    
    /**
     * Property 8d: Batch feature retrieval completeness.
     * 
     * Tests that batch feature retrieval returns features for all requests.
     */
    @Property(tries = 100)
    fun batchFeatureRetrievalIsComplete(
        @ForAll @NotEmpty @Size(min = 1, max = 5) customerIds: List<@StringLength(min = 5, max = 20) String>,
        @ForAll @NotEmpty @Size(min = 1, max = 5) subjectIds: List<@StringLength(min = 5, max = 20) String>,
        @ForAll @NotEmpty @Size(min = 1, max = 5) featureNames: List<@StringLength(min = 3, max = 20) String>
    ) {
        runBlocking {
            // Arrange
            val featureStoreClient = FeatureStoreClient()
            
            val requests = customerIds.zip(subjectIds).map { (customerId, subjectId) ->
                FeatureRequest(
                    customerId = customerId,
                    subjectId = subjectId,
                    featureNames = featureNames
                )
            }
            
            // Act: Batch retrieve features
            val featureMaps = featureStoreClient.batchGetFeatures(requests)
            
            // Assert: Same number of results as requests
            assertThat(featureMaps).hasSize(requests.size)
            
            // Verify each feature map has all required features
            featureMaps.forEachIndexed { index, featureMap ->
                val request = requests[index]
                assertThat(featureMap.customerId).isEqualTo(request.customerId)
                assertThat(featureMap.subjectId).isEqualTo(request.subjectId)
                assertThat(featureMap.features.keys).containsAll(request.featureNames)
            }
        }
    }
    
    /**
     * Property 8e: Feature type consistency.
     * 
     * Tests that feature values have consistent types.
     */
    @Property(tries = 100)
    fun featureTypesAreConsistent(
        @ForAll @StringLength(min = 5, max = 20) featureName: String,
        @ForAll featureType: FeatureType
    ) {
        // Arrange & Act: Create feature values of different types
        val featureValue = when (featureType) {
            FeatureType.NUMERIC -> FeatureValue(value = 42.0, type = FeatureType.NUMERIC)
            FeatureType.STRING -> FeatureValue(value = "test", type = FeatureType.STRING)
            FeatureType.BOOLEAN -> FeatureValue(value = true, type = FeatureType.BOOLEAN)
            FeatureType.LIST -> FeatureValue(value = listOf(1, 2, 3), type = FeatureType.LIST)
        }
        
        // Assert: Type matches the declared type
        assertThat(featureValue.type).isEqualTo(featureType)
        
        // Verify value type matches declared type
        when (featureType) {
            FeatureType.NUMERIC -> {
                val value = featureValue.value
                assertThat(value is Number || value is Double || value is Int || value is Long || value is Float).isTrue
            }
            FeatureType.STRING -> assertThat(featureValue.value).isInstanceOf(String::class.java)
            FeatureType.BOOLEAN -> assertThat(featureValue.value is Boolean).isTrue
            FeatureType.LIST -> assertThat(featureValue.value).isInstanceOf(List::class.java)
        }
    }
    
    /**
     * Property 8f: Error message clarity.
     * 
     * Tests that validation error messages are clear and informative.
     */
    @Property(tries = 100)
    fun validationErrorMessagesAreClear(
        @ForAll @NotEmpty @Size(min = 1, max = 5) missingFeatures: List<@StringLength(min = 3, max = 20) String>,
        @ForAll @NotEmpty @Size(min = 1, max = 5) invalidFeatures: List<@StringLength(min = 3, max = 20) String>
    ) {
        // Arrange
        val validator = FeatureValidator()
        
        val result = FeatureValidationResult(
            valid = false,
            missingFeatures = missingFeatures,
            invalidFeatures = invalidFeatures
        )
        
        // Act: Create error message
        val errorMessage = validator.createErrorMessage(result)
        
        // Assert: Error message contains feature names
        assertThat(errorMessage).isNotBlank()
        missingFeatures.forEach { feature ->
            assertThat(errorMessage).contains(feature)
        }
        invalidFeatures.forEach { feature ->
            assertThat(errorMessage).contains(feature)
        }
    }
}

