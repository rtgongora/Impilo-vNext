#!/bin/bash
# =============================================================================
# Impilo vNext — Inspect Incomplete Components
# Scans for TODO, stub, placeholder, and scaffold references in production code
# =============================================================================
set -euo pipefail

echo "============================================"
echo " Impilo vNext — Incomplete Component Scan"
echo "============================================"
echo ""

FAILURES=0

# 1. Check for TODOs in production Java code
echo "--- Checking for TODO in production Java code ---"
TODO_COUNT=$(grep -rn "TODO" services/*/src/main/ libs/*/src/main/ --include="*.java" 2>/dev/null | wc -l || true)
if [ "$TODO_COUNT" -gt 0 ]; then
    echo "WARN: Found $TODO_COUNT TODO references in production Java code:"
    grep -rn "TODO" services/*/src/main/ libs/*/src/main/ --include="*.java" 2>/dev/null | head -20
    FAILURES=$((FAILURES + 1))
else
    echo "PASS: No TODOs in production Java code"
fi

echo ""

# 2. Check for stub references in production Java code (excluding test helpers)
echo "--- Checking for 'stub' in production Java code ---"
STUB_COUNT=$(grep -rn "stub" services/*/src/main/ libs/*/src/main/ --include="*.java" 2>/dev/null \
    | grep -v "grpc-stub\|StreamObserver\|import.*stub" | wc -l || true)
if [ "$STUB_COUNT" -gt 0 ]; then
    echo "WARN: Found $STUB_COUNT stub references in production Java code:"
    grep -rn "stub" services/*/src/main/ libs/*/src/main/ --include="*.java" 2>/dev/null \
        | grep -v "grpc-stub\|StreamObserver\|import.*stub" | head -20
    FAILURES=$((FAILURES + 1))
else
    echo "PASS: No stubs in production Java code"
fi

echo ""

# 3. Check for placeholder references in production Java code (excluding UI placeholder attrs)
echo "--- Checking for 'placeholder' in production Java code ---"
PLACEHOLDER_COUNT=$(grep -rn "placeholder" services/*/src/main/ libs/*/src/main/ --include="*.java" 2>/dev/null \
    | grep -iv "photo placeholder\|QR placeholder" | wc -l || true)
if [ "$PLACEHOLDER_COUNT" -gt 0 ]; then
    echo "INFO: Found $PLACEHOLDER_COUNT placeholder references (review manually):"
    grep -rn "placeholder" services/*/src/main/ libs/*/src/main/ --include="*.java" 2>/dev/null \
        | grep -iv "photo placeholder\|QR placeholder" | head -10
else
    echo "PASS: No suspicious placeholder references"
fi

echo ""

# 4. Check for scaffold references
echo "--- Checking for 'scaffold' in code ---"
SCAFFOLD_COUNT=$(grep -rn "scaffold" services/ libs/ apps/ ui/ --include="*.java" --include="*.ts" --include="*.tsx" 2>/dev/null | wc -l || true)
if [ "$SCAFFOLD_COUNT" -gt 0 ]; then
    echo "WARN: Found $SCAFFOLD_COUNT scaffold references"
    grep -rn "scaffold" services/ libs/ apps/ ui/ --include="*.java" --include="*.ts" --include="*.tsx" 2>/dev/null | head -10
    FAILURES=$((FAILURES + 1))
else
    echo "PASS: No scaffold references"
fi

echo ""

# 5. Check pom.xml and README for stub labels
echo "--- Checking for 'stub' in pom.xml and README files ---"
META_STUB=$(grep -rn "stub" services/*/pom.xml services/*/README.md 2>/dev/null \
    | grep -iv "grpc-stub\|proto stub" | wc -l || true)
if [ "$META_STUB" -gt 0 ]; then
    echo "WARN: Found $META_STUB stub references in metadata:"
    grep -rn "stub" services/*/pom.xml services/*/README.md 2>/dev/null \
        | grep -iv "grpc-stub\|proto stub" | head -10
    FAILURES=$((FAILURES + 1))
else
    echo "PASS: No stub labels in service metadata"
fi

echo ""
echo "============================================"
if [ "$FAILURES" -gt 0 ]; then
    echo "RESULT: $FAILURES categories have warnings"
    exit 1
else
    echo "RESULT: ALL CLEAR — no incomplete components found"
    exit 0
fi
