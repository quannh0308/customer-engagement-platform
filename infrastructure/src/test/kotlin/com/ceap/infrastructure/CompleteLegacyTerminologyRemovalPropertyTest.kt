package com.ceap.infrastructure

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.constant
import io.kotest.property.checkAll
import java.io.File

/**
 * Property-based tests for complete legacy terminology removal.
 * 
 * **Feature: complete-ceap-infrastructure-rebrand, Property 1: Complete Legacy Terminology Removal**
 * **Validates: Requirements 1.2, 3.2, 3.3, 15.1, 15.2, 15.3**
 * 
 * These property tests verify that all legacy "solicitation" terminology has been
 * completely removed from functional code across the entire infrastructure codebase.
 * 
 * The tests ensure that:
 * 1. No functional code contains "solicitation" or "Solicitation" (case-insensitive)
 * 2. Comments and documentation are excluded from the search
 * 3. All source files (.kt, .yaml, .json) are scanned comprehensively
 * 4. The rebrand is complete and consistent across all file types
 * 
 * The tests use property-based testing to verify these invariants hold across
 * multiple iterations with different test scenarios.
 */
class CompleteLegacyTerminologyRemovalPropertyTest : StringSpec({
    
    val projectRoot = File(System.getProperty("user.dir"))
    
    // Determine correct infrastructure root based on working directory
    val infrastructureRoot = if (File(projectRoot, "src/main/kotlin").exists()) {
        projectRoot
    } else {
        File(projectRoot, "infrastructure")
    }
    
    /**
     * Helper function to extract functional code from Kotlin files.
     * Removes comments and documentation strings to focus on actual code.
     */
    fun extractFunctionalCode(content: String): String {
        val lines = content.lines()
        val functionalLines = mutableListOf<String>()
        var inBlockComment = false
        var inDocComment = false
        
        for (line in lines) {
            val trimmed = line.trim()
            
            // Handle block comment start
            if (trimmed.startsWith("/*") && !trimmed.startsWith("/**")) {
                inBlockComment = true
                continue
            }
            
            // Handle doc comment start
            if (trimmed.startsWith("/**")) {
                inDocComment = true
                continue
            }
            
            // Handle block comment end
            if (trimmed.endsWith("*/") && inBlockComment) {
                inBlockComment = false
                continue
            }
            
            // Handle doc comment end
            if (trimmed.endsWith("*/") && inDocComment) {
                inDocComment = false
                continue
            }
            
            // Skip lines inside comments
            if (inBlockComment || inDocComment) {
                continue
            }
            
            // Skip single-line comments
            if (trimmed.startsWith("//")) {
                continue
            }
            
            // Skip doc comment lines
            if (trimmed.startsWith("*")) {
                continue
            }
            
            // Remove inline comments from the line
            val codeOnly = if (line.contains("//")) {
                line.substringBefore("//")
            } else {
                line
            }
            
            functionalLines.add(codeOnly)
        }
        
        return functionalLines.joinToString("\n")
    }
    
    /**
     * Helper function to extract functional code from YAML files.
     * Removes comments to focus on actual configuration.
     */
    fun extractFunctionalYaml(content: String): String {
        return content.lines()
            .filterNot { it.trim().startsWith("#") }
            .map { line ->
                // Remove inline comments
                if (line.contains("#")) {
                    line.substringBefore("#")
                } else {
                    line
                }
            }
            .joinToString("\n")
    }
    
    /**
     * Helper function to find all source files in the infrastructure directory.
     * Excludes test directories to avoid false positives from test code.
     */
    fun findSourceFiles(root: File, extensions: List<String>): List<File> {
        val sourceFiles = mutableListOf<File>()
        
        fun traverse(dir: File) {
            if (!dir.exists() || !dir.isDirectory) return
            
            // Skip test directories
            if (dir.name == "test" || dir.path.contains("/test/")) return
            
            dir.listFiles()?.forEach { file ->
                when {
                    file.isDirectory && !file.name.startsWith(".") && file.name != "build" -> {
                        traverse(file)
                    }
                    file.isFile && extensions.any { file.name.endsWith(it) } -> {
                        sourceFiles.add(file)
                    }
                }
            }
        }
        
        traverse(root)
        return sourceFiles
    }
    
    /**
     * Helper function to search for legacy terminology in a file.
     * Returns a list of matches with line numbers and context.
     */
    data class TerminologyMatch(
        val file: String,
        val lineNumber: Int,
        val line: String,
        val term: String
    )
    
    fun searchForLegacyTerminology(file: File, functionalCode: String): List<TerminologyMatch> {
        val matches = mutableListOf<TerminologyMatch>()
        val terms = listOf("solicitation", "Solicitation", "SOLICITATION")
        
        functionalCode.lines().forEachIndexed { index, line ->
            terms.forEach { term ->
                if (line.contains(term, ignoreCase = false)) {
                    matches.add(
                        TerminologyMatch(
                            file = file.relativeTo(infrastructureRoot).path,
                            lineNumber = index + 1,
                            line = line.trim(),
                            term = term
                        )
                    )
                }
            }
        }
        
        return matches
    }
    
    /**
     * Property: No Kotlin source files should contain "solicitation" in functional code.
     * 
     * **Validates: Requirements 1.2, 3.2, 3.3, 15.1, 15.2, 15.3**
     * 
     * This property verifies that all Kotlin source files (.kt) in the infrastructure
     * codebase do not contain the legacy "solicitation" terminology in functional code.
     * Comments and documentation strings are excluded from the search.
     */
    "Property: No Kotlin source files should contain 'solicitation' in functional code" {
        checkAll(100, Arb.constant(Unit)) {
            val kotlinFiles = findSourceFiles(infrastructureRoot, listOf(".kt"))
            val allMatches = mutableListOf<TerminologyMatch>()
            
            kotlinFiles.forEach { file ->
                val content = file.readText()
                val functionalCode = extractFunctionalCode(content)
                val matches = searchForLegacyTerminology(file, functionalCode)
                allMatches.addAll(matches)
            }
            
            // Report any matches found
            if (allMatches.isNotEmpty()) {
                val report = buildString {
                    appendLine("Found ${allMatches.size} instances of legacy 'solicitation' terminology in Kotlin files:")
                    allMatches.forEach { match ->
                        appendLine("  ${match.file}:${match.lineNumber} - '${match.term}' in: ${match.line}")
                    }
                }
                println(report)
            }
            
            allMatches.shouldBeEmpty()
        }
    }
    
    /**
     * Property: No YAML configuration files should contain "solicitation" in functional config.
     * 
     * **Validates: Requirements 1.2, 15.1, 15.2, 15.3**
     * 
     * This property verifies that all YAML configuration files (.yaml, .yml) in the
     * infrastructure codebase do not contain the legacy "solicitation" terminology
     * in functional configuration. Comments are excluded from the search.
     */
    "Property: No YAML configuration files should contain 'solicitation' in functional config" {
        checkAll(100, Arb.constant(Unit)) {
            val yamlFiles = findSourceFiles(infrastructureRoot, listOf(".yaml", ".yml"))
            val allMatches = mutableListOf<TerminologyMatch>()
            
            yamlFiles.forEach { file ->
                val content = file.readText()
                val functionalYaml = extractFunctionalYaml(content)
                val matches = searchForLegacyTerminology(file, functionalYaml)
                allMatches.addAll(matches)
            }
            
            // Report any matches found
            if (allMatches.isNotEmpty()) {
                val report = buildString {
                    appendLine("Found ${allMatches.size} instances of legacy 'solicitation' terminology in YAML files:")
                    allMatches.forEach { match ->
                        appendLine("  ${match.file}:${match.lineNumber} - '${match.term}' in: ${match.line}")
                    }
                }
                println(report)
            }
            
            allMatches.shouldBeEmpty()
        }
    }
    
    /**
     * Property: No JSON configuration files should contain "solicitation" in functional config.
     * 
     * **Validates: Requirements 1.2, 15.1, 15.2, 15.3**
     * 
     * This property verifies that all JSON configuration files (.json) in the
     * infrastructure codebase do not contain the legacy "solicitation" terminology
     * in functional configuration.
     */
    "Property: No JSON configuration files should contain 'solicitation' in functional config" {
        checkAll(100, Arb.constant(Unit)) {
            val jsonFiles = findSourceFiles(infrastructureRoot, listOf(".json"))
            val allMatches = mutableListOf<TerminologyMatch>()
            
            jsonFiles.forEach { file ->
                val content = file.readText()
                // JSON doesn't have standard comments, so we search the entire content
                val matches = searchForLegacyTerminology(file, content)
                allMatches.addAll(matches)
            }
            
            // Report any matches found
            if (allMatches.isNotEmpty()) {
                val report = buildString {
                    appendLine("Found ${allMatches.size} instances of legacy 'solicitation' terminology in JSON files:")
                    allMatches.forEach { match ->
                        appendLine("  ${match.file}:${match.lineNumber} - '${match.term}' in: ${match.line}")
                    }
                }
                println(report)
            }
            
            allMatches.shouldBeEmpty()
        }
    }
    
    /**
     * Property: All source files combined should have zero "solicitation" matches.
     * 
     * **Validates: Requirements 1.2, 3.2, 3.3, 15.1, 15.2, 15.3**
     * 
     * This comprehensive property verifies that across all source files in the
     * infrastructure codebase (.kt, .yaml, .yml, .json), there are zero instances
     * of the legacy "solicitation" terminology in functional code.
     * 
     * This is the primary property test that ensures complete legacy terminology removal.
     */
    "Property: All source files combined should have zero 'solicitation' matches" {
        checkAll(100, Arb.constant(Unit)) {
            val allSourceFiles = findSourceFiles(
                infrastructureRoot,
                listOf(".kt", ".yaml", ".yml", ".json")
            )
            
            val allMatches = mutableListOf<TerminologyMatch>()
            
            allSourceFiles.forEach { file ->
                val content = file.readText()
                val functionalCode = when {
                    file.name.endsWith(".kt") -> extractFunctionalCode(content)
                    file.name.endsWith(".yaml") || file.name.endsWith(".yml") -> extractFunctionalYaml(content)
                    file.name.endsWith(".json") -> content
                    else -> content
                }
                
                val matches = searchForLegacyTerminology(file, functionalCode)
                allMatches.addAll(matches)
            }
            
            // Report comprehensive results
            if (allMatches.isNotEmpty()) {
                val report = buildString {
                    appendLine("\n" + "=".repeat(80))
                    appendLine("LEGACY TERMINOLOGY REMOVAL - COMPREHENSIVE REPORT")
                    appendLine("=".repeat(80))
                    appendLine("Found ${allMatches.size} instances of legacy 'solicitation' terminology:")
                    appendLine()
                    
                    // Group by file
                    val matchesByFile = allMatches.groupBy { it.file }
                    matchesByFile.forEach { (file, matches) ->
                        appendLine("File: $file (${matches.size} matches)")
                        matches.forEach { match ->
                            appendLine("  Line ${match.lineNumber}: '${match.term}' in: ${match.line}")
                        }
                        appendLine()
                    }
                    
                    appendLine("=".repeat(80))
                    appendLine("Total files scanned: ${allSourceFiles.size}")
                    appendLine("Files with matches: ${matchesByFile.size}")
                    appendLine("Total matches: ${allMatches.size}")
                    appendLine("=".repeat(80))
                }
                println(report)
            } else {
                println("\n" + "=".repeat(80))
                println("LEGACY TERMINOLOGY REMOVAL - SUCCESS")
                println("=".repeat(80))
                println("Scanned ${allSourceFiles.size} source files")
                println("Found 0 instances of legacy 'solicitation' terminology")
                println("The rebrand is complete!")
                println("=".repeat(80))
            }
            
            allMatches.shouldBeEmpty()
        }
    }
    
    /**
     * Property: Specific infrastructure files should not contain "solicitation".
     * 
     * **Validates: Requirements 1.2, 3.2, 3.3, 15.1, 15.2, 15.3**
     * 
     * This property specifically checks key infrastructure files that were
     * updated during the rebrand to ensure they don't contain legacy terminology.
     */
    "Property: Specific infrastructure files should not contain 'solicitation'" {
        checkAll(100, Arb.constant(Unit)) {
            val keyFiles = listOf(
                "src/main/kotlin/com/ceap/infrastructure/CeapPlatformApp.kt",
                "src/main/kotlin/com/ceap/infrastructure/constructs/CeapLambda.kt",
                "src/main/kotlin/com/ceap/infrastructure/stacks/ReactiveWorkflowStack.kt",
                "src/main/kotlin/com/ceap/infrastructure/stacks/OrchestrationStack.kt",
                "src/main/kotlin/com/ceap/infrastructure/constructs/ObservabilityDashboard.kt",
                "eventbridge-rules.yaml",
                "step-functions.yaml",
                "lambda-functions.yaml",
                "dynamodb-tables.yaml"
            )
            
            val allMatches = mutableListOf<TerminologyMatch>()
            
            keyFiles.forEach { relativePath ->
                val file = File(infrastructureRoot, relativePath)
                if (file.exists()) {
                    val content = file.readText()
                    val functionalCode = when {
                        file.name.endsWith(".kt") -> extractFunctionalCode(content)
                        file.name.endsWith(".yaml") || file.name.endsWith(".yml") -> extractFunctionalYaml(content)
                        else -> content
                    }
                    
                    val matches = searchForLegacyTerminology(file, functionalCode)
                    allMatches.addAll(matches)
                }
            }
            
            // Report any matches found in key files
            if (allMatches.isNotEmpty()) {
                val report = buildString {
                    appendLine("Found ${allMatches.size} instances of legacy 'solicitation' terminology in key infrastructure files:")
                    allMatches.forEach { match ->
                        appendLine("  ${match.file}:${match.lineNumber} - '${match.term}' in: ${match.line}")
                    }
                }
                println(report)
            }
            
            allMatches.shouldBeEmpty()
        }
    }
    
    /**
     * Property: Case-insensitive search should find zero "solicitation" matches.
     * 
     * **Validates: Requirements 1.2, 3.2, 3.3, 15.1, 15.2, 15.3**
     * 
     * This property performs a case-insensitive search for "solicitation" to catch
     * any variations like "SOLICITATION", "Solicitation", "solicitation", etc.
     */
    "Property: Case-insensitive search should find zero 'solicitation' matches" {
        checkAll(100, Arb.constant(Unit)) {
            val allSourceFiles = findSourceFiles(
                infrastructureRoot,
                listOf(".kt", ".yaml", ".yml", ".json")
            )
            
            val allMatches = mutableListOf<TerminologyMatch>()
            
            allSourceFiles.forEach { file ->
                val content = file.readText()
                val functionalCode = when {
                    file.name.endsWith(".kt") -> extractFunctionalCode(content)
                    file.name.endsWith(".yaml") || file.name.endsWith(".yml") -> extractFunctionalYaml(content)
                    file.name.endsWith(".json") -> content
                    else -> content
                }
                
                // Case-insensitive search
                functionalCode.lines().forEachIndexed { index, line ->
                    if (line.contains("solicitation", ignoreCase = true)) {
                        // Find the actual term that matched
                        val matchedTerm = Regex("solicitation", RegexOption.IGNORE_CASE)
                            .find(line)?.value ?: "solicitation"
                        
                        allMatches.add(
                            TerminologyMatch(
                                file = file.relativeTo(infrastructureRoot).path,
                                lineNumber = index + 1,
                                line = line.trim(),
                                term = matchedTerm
                            )
                        )
                    }
                }
            }
            
            // Report any matches found
            if (allMatches.isNotEmpty()) {
                val report = buildString {
                    appendLine("Found ${allMatches.size} instances of 'solicitation' (case-insensitive):")
                    allMatches.forEach { match ->
                        appendLine("  ${match.file}:${match.lineNumber} - '${match.term}' in: ${match.line}")
                    }
                }
                println(report)
            }
            
            allMatches.shouldBeEmpty()
        }
    }
    
    /**
     * Property: File count should be reasonable for infrastructure project.
     * 
     * **Validates: Requirements 15.1, 15.2, 15.3**
     * 
     * This property verifies that the test is scanning a reasonable number of files,
     * ensuring that the file discovery mechanism is working correctly.
     */
    "Property: File count should be reasonable for infrastructure project" {
        checkAll(100, Arb.constant(Unit)) {
            val allSourceFiles = findSourceFiles(
                infrastructureRoot,
                listOf(".kt", ".yaml", ".yml", ".json")
            )
            
            // Should have at least some files
            (allSourceFiles.size > 0) shouldBe true
            
            // Report file count
            val filesByType = allSourceFiles.groupBy { file ->
                when {
                    file.name.endsWith(".kt") -> "Kotlin"
                    file.name.endsWith(".yaml") || file.name.endsWith(".yml") -> "YAML"
                    file.name.endsWith(".json") -> "JSON"
                    else -> "Other"
                }
            }
            
            println("\nFile scan summary:")
            filesByType.forEach { (type, files) ->
                println("  $type files: ${files.size}")
            }
            println("  Total files: ${allSourceFiles.size}")
        }
    }
    
    /**
     * Property: All workflow stack files should not contain "solicitation".
     * 
     * **Validates: Requirements 3.2, 3.3, 15.2**
     * 
     * This property specifically checks all workflow stack files to ensure
     * they don't contain legacy terminology after the import and instantiation updates.
     */
    "Property: All workflow stack files should not contain 'solicitation'" {
        checkAll(100, Arb.constant(Unit)) {
            val workflowStacks = listOf(
                "src/main/kotlin/com/ceap/infrastructure/stacks/EtlWorkflowStack.kt",
                "src/main/kotlin/com/ceap/infrastructure/stacks/FilterWorkflowStack.kt",
                "src/main/kotlin/com/ceap/infrastructure/stacks/ScoreWorkflowStack.kt",
                "src/main/kotlin/com/ceap/infrastructure/stacks/StoreWorkflowStack.kt",
                "src/main/kotlin/com/ceap/infrastructure/stacks/ReactiveWorkflowStack.kt"
            )
            
            val allMatches = mutableListOf<TerminologyMatch>()
            
            workflowStacks.forEach { relativePath ->
                val file = File(infrastructureRoot, relativePath)
                if (file.exists()) {
                    val content = file.readText()
                    val functionalCode = extractFunctionalCode(content)
                    val matches = searchForLegacyTerminology(file, functionalCode)
                    allMatches.addAll(matches)
                }
            }
            
            // Report any matches found in workflow stacks
            if (allMatches.isNotEmpty()) {
                val report = buildString {
                    appendLine("Found ${allMatches.size} instances of legacy 'solicitation' terminology in workflow stacks:")
                    allMatches.forEach { match ->
                        appendLine("  ${match.file}:${match.lineNumber} - '${match.term}' in: ${match.line}")
                    }
                }
                println(report)
            }
            
            allMatches.shouldBeEmpty()
        }
    }
})
