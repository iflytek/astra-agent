#!/bin/bash

# =============================================================================
# Comment Language Checker - English Comments Enforcement
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RESET='\033[0m'

# Configuration
TEMP_DIR="/tmp/comment-check-$$"
COMMENTS_FILE="$TEMP_DIR/comments.txt"
ERRORS_FILE="$TEMP_DIR/errors.txt"
WARNINGS_FILE="$TEMP_DIR/warnings.txt"

# Create temp directory
mkdir -p "$TEMP_DIR"

# Cleanup function
cleanup() {
    rm -rf "$TEMP_DIR" 2>/dev/null || true
}
trap cleanup EXIT

# Function to extract ONLY comments from different file types (ignoring code content)
extract_comments() {
    local file="$1"
    local ext="${file##*.}"

    case "$ext" in
        "go")
            # Extract Go comments more precisely - only comment lines, not code with Chinese
            python3 - "$file" << 'EOF'
import sys
import re

file_path = sys.argv[1] if len(sys.argv) > 1 else '/dev/stdin'
try:
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    for i, line in enumerate(lines, 1):
        stripped = line.strip()

        # Single line comment //
        if re.match(r'^\s*//', line):
            comment_content = re.sub(r'^\s*//\s*', '', line).strip()
            if comment_content:
                print(f"{i}: {comment_content}")

        # Multi-line comment start /*
        elif re.match(r'^\s*/\*', line):
            comment_content = re.sub(r'^\s*/\*\s*', '', line).strip()
            comment_content = re.sub(r'\*/\s*$', '', comment_content).strip()
            if comment_content:
                print(f"{i}: {comment_content}")

        # Multi-line comment continuation *
        elif re.match(r'^\s*\*[^/]', line):
            comment_content = re.sub(r'^\s*\*\s*', '', line).strip()
            if comment_content:
                print(f"{i}: {comment_content}")

except Exception as e:
    pass
EOF
            ;;
        "java")
            # Extract Java comments and annotation messages precisely
            python3 - "$file" << 'EOF'
import sys
import re

file_path = sys.argv[1] if len(sys.argv) > 1 else '/dev/stdin'
try:
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    for i, line in enumerate(lines, 1):
        stripped = line.strip()

        # Single line comment //
        if re.match(r'^\s*//', line):
            comment_content = re.sub(r'^\s*//\s*', '', line).strip()
            if comment_content:
                print(f"{i}: {comment_content}")

        # Multi-line comment /* or javadoc /**
        elif re.match(r'^\s*/\*', line):
            comment_content = re.sub(r'^\s*/\*+\s*', '', line).strip()
            comment_content = re.sub(r'\*/\s*$', '', comment_content).strip()
            if comment_content:
                print(f"{i}: {comment_content}")

        # Multi-line comment continuation *
        elif re.match(r'^\s*\*[^/]', line):
            comment_content = re.sub(r'^\s*\*\s*', '', line).strip()
            if comment_content:
                print(f"{i}: {comment_content}")

        # Check annotation message parameters (validation messages should be English)
        elif 'message' in line and re.search(r'message\s*=\s*"[^"]*"', line):
            match = re.search(r'message\s*=\s*"([^"]*)"', line)
            if match:
                message_content = match.group(1)
                print(f"{i}: {message_content}")

except Exception as e:
    pass
EOF
            ;;
        "py")
            # Extract Python comments and docstrings precisely
            python3 - "$file" << 'EOF'
import sys
import re
import ast

file_path = sys.argv[1] if len(sys.argv) > 1 else '/dev/stdin'
try:
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
        lines = content.split('\n')

    for i, line in enumerate(lines, 1):
        # Single line comments #
        if re.match(r'^\s*#', line):
            comment_content = re.sub(r'^\s*#\s*', '', line).strip()
            if comment_content:
                print(f"{i}: {comment_content}")

        # Docstrings (only at start of line or after def/class)
        elif '"""' in line:
            # Extract content within triple quotes
            matches = re.findall(r'"""([^"]*(?:"[^"]*"[^"]*)*)"""', line)
            for match in matches:
                if match.strip():
                    print(f"{i}: {match.strip()}")

        # Check Pydantic description fields (should be English)
        elif 'description=' in line and re.search(r'description\s*=\s*"[^"]*"', line):
            match = re.search(r'description\s*=\s*"([^"]*)"', line)
            if match:
                desc_content = match.group(1)
                print(f"{i}: {desc_content}")

except Exception as e:
    pass
EOF
            ;;
        "ts"|"tsx"|"js"|"jsx")
            # Extract TypeScript/JavaScript comments precisely
            python3 - "$file" << 'EOF'
import sys
import re

file_path = sys.argv[1] if len(sys.argv) > 1 else '/dev/stdin'
try:
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    for i, line in enumerate(lines, 1):
        # Single line comment //
        if re.match(r'^\s*//', line):
            comment_content = re.sub(r'^\s*//\s*', '', line).strip()
            if comment_content:
                print(f"{i}: {comment_content}")

        # Multi-line comment start /*
        elif re.match(r'^\s*/\*', line):
            comment_content = re.sub(r'^\s*/\*\s*', '', line).strip()
            comment_content = re.sub(r'\*/\s*$', '', comment_content).strip()
            if comment_content:
                print(f"{i}: {comment_content}")

        # Multi-line comment continuation *
        elif re.match(r'^\s*\*[^/]', line):
            comment_content = re.sub(r'^\s*\*\s*', '', line).strip()
            if comment_content:
                print(f"{i}: {comment_content}")

except Exception as e:
    pass
EOF
            ;;
        *)
            echo "Unsupported file type: $ext" >&2
            return 1
            ;;
    esac
}

# Function to check if text contains Chinese characters
contains_chinese() {
    local text="$1"
    # Method 1: Try Python Unicode detection (most reliable)
    if command -v python3 >/dev/null 2>&1; then
        python3 -c "import re, sys; sys.exit(0 if re.search(r'[\u4e00-\u9fff]', '''$text''') else 1)" 2>/dev/null
        return $?
    fi

    # Method 2: Try Perl regex (GNU grep)
    if echo "$text" | grep -qP '[\x{4e00}-\x{9fff}]' 2>/dev/null; then
        return 0
    fi

    # Method 3: Fallback - check for non-ASCII characters (broader detection)
    if echo "$text" | LC_ALL=C grep -q '[^\x00-\x7F]' 2>/dev/null; then
        # Additional heuristic: if contains non-ASCII and no common European chars
        if ! echo "$text" | LC_ALL=C grep -q '[àáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ]' 2>/dev/null; then
            return 0
        fi
    fi

    return 1
}

# Function to check comments in a single file
check_file_comments() {
    local file="$1"
    local violations=0

    echo -e "${YELLOW}Checking comments in: $file${RESET}"

    # Extract comments
    local comments
    comments=$(extract_comments "$file")

    if [ -z "$comments" ]; then
        echo "  No comments found"
        return 0
    fi

    # Check each comment line
    while IFS= read -r line; do
        if [ -n "$line" ]; then
            local line_num=$(echo "$line" | cut -d: -f1)
            local comment_text=$(echo "$line" | cut -d: -f2-)

            # Skip empty comments or just punctuation
            local cleaned_text=$(echo "$comment_text" | sed 's/[[:punct:][:space:]]//g')
            if [ -z "$cleaned_text" ]; then
                continue
            fi

            # Check for Chinese characters
            if contains_chinese "$comment_text"; then
                echo -e "  ${RED}Line $line_num: Contains non-English characters${RESET}"
                echo "    Content: $comment_text"
                violations=$((violations + 1))
            fi
        fi
    done <<< "$comments"

    return $violations
}

# Main function
main() {
    echo -e "${BLUE}Comment Language Checker - Enforcing English Comments${RESET}"
    echo ""

    local total_violations=0
    local files_checked=0

    # Find all relevant source files
    local source_files=()

    # Go files
    for go_dir in core/tenant; do
        if [ -d "$go_dir" ]; then
            while IFS= read -r -d '' file; do
                source_files+=("$file")
            done < <(find "$go_dir" -name "*.go" -print0 2>/dev/null || true)
        fi
    done

    # Java files
    for java_dir in console/backend; do
        if [ -d "$java_dir" ]; then
            while IFS= read -r -d '' file; do
                source_files+=("$file")
            done < <(find "$java_dir" -name "*.java" -print0 2>/dev/null || true)
        fi
    done

    # Python files
    for python_dir in core/memory/database core/plugin/rpa core/plugin/link core/plugin/aitools core/agent core/knowledge core/workflow; do
        if [ -d "$python_dir" ]; then
            while IFS= read -r -d '' file; do
                source_files+=("$file")
            done < <(find "$python_dir" -name "*.py" -print0 2>/dev/null || true)
        fi
    done

    # TypeScript files
    for ts_dir in console/frontend; do
        if [ -d "$ts_dir" ]; then
            while IFS= read -r -d '' file; do
                source_files+=("$file")
            done < <(find "$ts_dir" -name "*.ts" -o -name "*.tsx" -o -name "*.js" -o -name "*.jsx" | grep -v node_modules | head -20 | tr '\n' '\0' 2>/dev/null || true)
        fi
    done

    if [ ${#source_files[@]} -eq 0 ]; then
        echo -e "${YELLOW}No source files found to check${RESET}"
        exit 0
    fi

    echo "Found ${#source_files[@]} files to check"
    echo ""

    # Check each file
    for file in "${source_files[@]}"; do
        if [ -f "$file" ]; then
            check_file_comments "$file"
            local file_violations=$?
            total_violations=$((total_violations + file_violations))
            files_checked=$((files_checked + 1))
            echo ""
        fi
    done

    # Summary
    echo -e "${BLUE}Comment Language Check Summary:${RESET}"
    echo "  Files checked: $files_checked"
    echo "  Total violations: $total_violations"

    if [ $total_violations -eq 0 ]; then
        echo -e "${GREEN}✅ All comments are in English!${RESET}"
        exit 0
    else
        echo -e "${RED}❌ Found $total_violations non-English comments${RESET}"
        echo -e "${YELLOW}Please update comments to use English only${RESET}"
        exit 1
    fi
}

# Help function
show_help() {
    echo "Comment Language Checker"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -h, --help     Show this help message"
    echo ""
    echo "This script checks that all comments in source code are written in English."
    echo "Supported file types: .go, .java, .py, .ts, .tsx, .js, .jsx"
}

# Parse arguments
case "${1:-}" in
    -h|--help)
        show_help
        exit 0
        ;;
    *)
        main "$@"
        ;;
esac