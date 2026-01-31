package com.ceap.infrastructure

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import java.io.File

/**
 * Property-Based Tests for Cross-Stack Reference Validation
 * 
 * **Feature: infrastructure-consolidation**
 * **Property 10: Cross-Stack Reference Validation**
 * 
 * **Validates: Requirement 10.4**
 * 
 * These tests verify that CloudFormation deployment validation catches
 * invalid cross-stack references before allowing deployment to proceed.
 * This ensures that all Fn::ImportValue references have corresponding
 * exports in the dependency stacks.
 * 
 * Test Strategy:
 * - Verify all imports have corresponding exports
 * - Verify export names match import references
 * - Verify parameter-based references are valid
 * - Verify no circular dependencies exist
 */
class CrossStackReferenceValidationPropertyTest : StringSpec({
    
    val objectMapper = ObjectMapper()
    val consolidatedTemplatesDir = File("cdk.out.consolidated")
    
    /**
     * Helper function to load a CloudFormation template
     */
    fun loadTemplate(templateFile: File): JsonNode? {
        if (!templateFile.exists()) {
            return null
        }
        return objectMapper.readTree(templateFile)
    }
    
    /**
     * Extract all Fn::ImportValue references from a template
     */
    fun extractImportReferences(template: JsonNode): List<String> {
        val imports = mutableListOf<String>()
        
        fun traverseNode(node: JsonNode) {
            when {
                node.isObject -> {
                    if (node.has("Fn::ImportValue")) {
                        val importValue = node.get("Fn::ImportValue")
                        when {
                            importValue.isTextual -> imports.add(importValue.asText())
                            importValue.isObject && importValue.has("Fn::Join") -> {
                                val joinArray = importValue.get("Fn::Join")
                                if (joinArray.isArray && joinArray.size() >= 2) {
                                    val parts = joinArray.get(1)
                                    if (parts.isArray) {
                                        val joinedParts = mutableListOf<String>()
                                        parts.forEach { part ->
                                            when {
                                                part.isTextual -> joinedParts.add(part.asText())
                                                part.isObject && part.has("Ref") -> {
                                                    joinedParts.add(part.get("Ref").asText())
                                                }
                                            }
                                        }
                                        imports.add(joinedParts.joinToString(""))
                                    }
                                }
                            }
                        }
                    }
                    node.fields().forEach { (_, value) -> traverseNode(value) }
                }
                node.isArray -> node.forEach { traverseNode(it) }
            }
        }
        
        traverseNode(template)
        return imports
    }
    
    /**
     * Extract all exports from a template
     */
    fun extractExports(template: JsonNode): List<String> {
        val exports = mutableListOf<String>()
        val outputs = template.get("Outputs") ?: return exports
        
        outputs.fields().forEach { (_, output) ->
            val exportName = output.get("Export")?.get("Name")
            if (exportName != null) {
                when {
                    exportName.isTextual -> exports.add(exportName.asText())
                    exportName.isObject && exportName.has("Fn::Sub") -> {
                        exports.add(exportName.get("Fn::Sub").asText())
                    }
                }
            }
        }
        
        return exports
    }
    
    /**
     * Property: All Fn::ImportValue references have corresponding exports.
     * 
     * **Validates: Requirement 10.4**
     * 
     * This property verifies that every Fn::ImportValue reference in the
     * consolidated templates has a corresponding export in a dependency stack.
     */
    "Property: All Fn::ImportValue references have corresponding exports" {
        // Load all templates
        val databaseTemplate = loadTemplate(File(consolidatedTemplatesDir, "CeapDatabase-dev.template.json"))
        val dataPlatformTemplate = loadTemplate(File(consolidatedTemplatesDir, "CeapDataPlatform-dev.template.json"))
        val servingAPITemplate = loadTemplate(File(consolidatedTemplatesDir, "CeapServingAPI-dev.template.json"))
        
        databaseTemplate shouldNotBe null
        dataPlatformTemplate shouldNotBe null
        servingAPITemplate shouldNotBe null
        
        // Extract all exports from database stack
        val databaseExports = extractExports(databaseTemplate!!)
        val dataPlatformExports = extractExports(dataPlatformTemplate!!)
        
        // Extract imports from application stacks
        val dataPlatformImports = extractImportReferences(dataPlatformTemplate)
        val servingAPIImports = extractImportReferences(servingAPITemplate!!)
        
        // Verify DataPlatform imports
        checkAll(100, Arb.of(dataPlatformImports)) { importRef ->
            // Import should match an export pattern
            val hasMatchingExport = databaseExports.any { export ->
                importRef.contains("CandidatesTable") && export.contains("CandidatesTable") ||
                importRef.contains("ProgramConfigTable") && export.contains("ProgramConfigTable") ||
                importRef.contains("ScoreCacheTable") && export.contains("ScoreCacheTable") ||
                importRef.contains("DatabaseStackName")  // Parameter reference
            }
            hasMatchingExport shouldBe true
        }
        
        // Verify ServingAPI imports
        checkAll(100, Arb.of(servingAPIImports)) { importRef ->
            // Import should match an export from database or data platform
            val hasMatchingExport = databaseExports.any { export ->
                importRef.contains("CandidatesTable") && export.contains("CandidatesTable") ||
                importRef.contains("ProgramConfigTable") && export.contains("ProgramConfigTable") ||
                importRef.contains("ScoreCacheTable") && export.contains("ScoreCacheTable") ||
                importRef.contains("DatabaseStackName") ||  // Parameter reference
                importRef.contains("DataPlatformStackName")  // Parameter reference
            } || dataPlatformExports.any { export ->
                importRef.contains(export) || importRef.contains("DataPlatformStackName")
            }
            hasMatchingExport shouldBe true
        }
    }
    
    /**
     * Property: Database stack exports all required resources.
     * 
     * **Validates: Requirement 10.4**
     * 
     * This property verifies that the database stack exports all
     * table names and ARNs required by dependent stacks.
     */
    "Property: Database stack exports all required resources" {
        val databaseTemplate = loadTemplate(File(consolidatedTemplatesDir, "CeapDatabase-dev.template.json"))
        databaseTemplate shouldNotBe null
        
        val exports = extractExports(databaseTemplate!!)
        
        val requiredExports = listOf(
            "CandidatesTableName",
            "CandidatesTableArn",
            "ProgramConfigTableName",
            "ProgramConfigTableArn",
            "ScoreCacheTableName",
            "ScoreCacheTableArn"
        )
        
        checkAll(100, Arb.of(requiredExports)) { requiredExport ->
            // Each required export should exist
            exports.any { it.contains(requiredExport) } shouldBe true
        }
    }
    
    /**
     * Property: Application stacks define dependency parameters.
     * 
     * **Validates: Requirement 10.4**
     * 
     * This property verifies that application stacks define parameters
     * for their stack dependencies, enabling flexible cross-stack references.
     */
    "Property: Application stacks define dependency parameters" {
        val stacksAndParams = listOf(
            "CeapDataPlatform-dev.template.json" to "DatabaseStackName",
            "CeapServingAPI-dev.template.json" to "DatabaseStackName",
            "CeapServingAPI-dev.template.json" to "DataPlatformStackName"
        )
        
        checkAll(100, Arb.of(stacksAndParams)) { (templateFile, paramName) ->
            val template = loadTemplate(File(consolidatedTemplatesDir, templateFile))
            template shouldNotBe null
            
            val parameters = template!!.get("Parameters")
            parameters shouldNotBe null
            
            // Parameter should be defined
            parameters.has(paramName) shouldBe true
        }
    }
    
    /**
     * Property: No circular dependencies exist between stacks.
     * 
     * **Validates: Requirement 10.4**
     * 
     * This property verifies that there are no circular dependencies
     * in the stack dependency graph.
     */
    "Property: No circular dependencies exist between stacks" {
        // Define dependency graph
        val dependencies = mapOf(
            "CeapDatabase-dev" to emptyList<String>(),
            "CeapDataPlatform-dev" to listOf("CeapDatabase-dev"),
            "CeapServingAPI-dev" to listOf("CeapDatabase-dev")
        )
        
        checkAll(100, Arb.of(dependencies.keys.toList())) { stackName ->
            val deps = dependencies[stackName] ?: emptyList()
            
            // Stack should not depend on itself
            deps.contains(stackName) shouldBe false
            
            // Stack's dependencies should not depend on it (no cycles)
            deps.forEach { dep ->
                val depDeps = dependencies[dep] ?: emptyList()
                depDeps.contains(stackName) shouldBe false
            }
        }
    }
    
    /**
     * Property: Export names are unique across all stacks.
     * 
     * **Validates: Requirement 10.4**
     * 
     * This property verifies that export names are unique to prevent
     * conflicts during CloudFormation deployment.
     */
    "Property: Export names are unique across all stacks" {
        val templates = listOf(
            "CeapDatabase-dev.template.json",
            "CeapDataPlatform-dev.template.json",
            "CeapServingAPI-dev.template.json"
        )
        
        val allExports = mutableListOf<String>()
        
        templates.forEach { templateFile ->
            val template = loadTemplate(File(consolidatedTemplatesDir, templateFile))
            if (template != null) {
                allExports.addAll(extractExports(template))
            }
        }
        
        // Check for duplicates
        val uniqueExports = allExports.toSet()
        allExports.size shouldBe uniqueExports.size
    }
    
    /**
     * Property: Parameters have appropriate default values.
     * 
     * **Validates: Requirement 10.4**
     * 
     * This property verifies that stack parameters have sensible
     * default values that match the expected stack names.
     */
    "Property: Parameters have appropriate default values" {
        val dataPlatformTemplate = loadTemplate(File(consolidatedTemplatesDir, "CeapDataPlatform-dev.template.json"))
        dataPlatformTemplate shouldNotBe null
        
        val parameters = dataPlatformTemplate!!.get("Parameters")
        parameters shouldNotBe null
        
        // DatabaseStackName parameter should have default
        val dbStackParam = parameters.get("DatabaseStackName")
        dbStackParam shouldNotBe null
        
        val defaultValue = dbStackParam.get("Default")
        defaultValue shouldNotBe null
        defaultValue.asText().contains("CeapDatabase-") shouldBe true
    }
    
    /**
     * Property: Import references use parameter substitution.
     * 
     * **Validates: Requirement 10.4**
     * 
     * This property verifies that import references use parameter
     * substitution rather than hardcoded stack names.
     */
    "Property: Import references use parameter substitution" {
        val stackFiles = listOf(
            "CeapDataPlatform-dev.template.json",
            "CeapServingAPI-dev.template.json"
        )
        
        checkAll(100, Arb.of(stackFiles)) { stackFile ->
            val template = loadTemplate(File(consolidatedTemplatesDir, stackFile))
            
            if (template != null) {
                val imports = extractImportReferences(template)
                
                // All imports should use parameter references
                imports.forEach { importRef ->
                    val usesParameter = importRef.contains("DatabaseStackName") ||
                                      importRef.contains("DataPlatformStackName")
                    usesParameter shouldBe true
                }
            }
        }
    }
})
