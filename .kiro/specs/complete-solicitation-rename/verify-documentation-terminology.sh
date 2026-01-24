#!/bin/bash
# Property 1: Documentation Terminology Completeness
# Validates: Requirements 3.1
# 
# This script verifies that all "solicitation" references have been removed
# from documentation files (code comments and markdown files).
# Acceptable contexts (excluded): .kiro/specs directory (this spec itself)

set -e

echo "=== Property 1: Documentation Terminology Completeness ==="
echo "Searching for 'solicitation' references in documentation..."
echo ""

# Search in Kotlin and Java files (code comments)
echo "1. Searching .kt and .java files..."
KT_JAVA_MATCHES=$(grep -ri "solicitation" --include="*.kt" --include="*.java" --exclude-dir=".kiro" . | wc -l)
echo "   Found $KT_JAVA_MATCHES matches in code files"

# Search in Markdown files (excluding spec directory)
echo "2. Searching .md files (excluding .kiro/specs)..."
MD_MATCHES=$(grep -ri "solicitation" --include="*.md" --exclude-dir=".kiro" . | wc -l)
echo "   Found $MD_MATCHES matches in markdown files"

echo ""
TOTAL_MATCHES=$((KT_JAVA_MATCHES + MD_MATCHES))
echo "Total matches: $TOTAL_MATCHES"
echo ""

if [ $TOTAL_MATCHES -eq 0 ]; then
    echo "✅ PASS: No 'solicitation' references found in documentation"
    exit 0
else
    echo "❌ FAIL: Found $TOTAL_MATCHES 'solicitation' references in documentation"
    echo ""
    echo "Details:"
    echo "--------"
    grep -ri "solicitation" --include="*.kt" --include="*.java" --include="*.md" --exclude-dir=".kiro" . | head -20
    exit 1
fi
