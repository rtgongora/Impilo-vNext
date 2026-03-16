#!/bin/bash
# =============================================================================
# Impilo vNext — Verify No Stubs Remain
# Strict check that no stub implementations exist in production code
# =============================================================================
set -euo pipefail

echo "============================================"
echo " Impilo vNext — No-Stubs Verification"
echo "============================================"
echo ""

EXIT_CODE=0

# Production Java code
echo "--- Java production code ---"
STUBS=$(grep -rn "stub\|TODO\|UnsupportedOperationException" services/*/src/main/ libs/*/src/main/ --include="*.java" 2>/dev/null \
    | grep -v "grpc-stub\|StreamObserver\|import.*stub\|proto.*stub\|@SuppressWarnings" \
    | grep -v "test\|Test" || true)

if [ -n "$STUBS" ]; then
    echo "FAIL: Found potential stubs in production Java code:"
    echo "$STUBS" | head -20
    EXIT_CODE=1
else
    echo "PASS: No stubs in production Java code"
fi

echo ""

# TypeScript/React code
echo "--- TypeScript/React production code ---"
TS_STUBS=$(grep -rn "TODO\|FIXME\|HACK\|XXX" apps/*/src/ ui/*/src/ --include="*.ts" --include="*.tsx" 2>/dev/null \
    | grep -v "node_modules\|.test.\|__test__" || true)

if [ -n "$TS_STUBS" ]; then
    echo "INFO: Found annotations in TypeScript code:"
    echo "$TS_STUBS" | head -10
else
    echo "PASS: No TODOs/FIXMEs in TypeScript code"
fi

echo ""

# Migration files
echo "--- Migration SQL files ---"
SQL_STUBS=$(grep -rn "stub" services/*/src/main/resources/db/migration/ --include="*.sql" 2>/dev/null || true)
if [ -n "$SQL_STUBS" ]; then
    echo "FAIL: Found stub references in migrations:"
    echo "$SQL_STUBS" | head -10
    EXIT_CODE=1
else
    echo "PASS: No stub references in migrations"
fi

echo ""
echo "============================================"
if [ "$EXIT_CODE" -eq 0 ]; then
    echo "RESULT: ALL CLEAR — no stubs detected"
else
    echo "RESULT: STUBS FOUND — review and fix above"
fi
exit $EXIT_CODE
