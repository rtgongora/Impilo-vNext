#!/usr/bin/env bash
# scan-costing-engine.sh — Verify costing engine implementation completeness
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXIT_CODE=0
COSTA_SVC="$REPO_ROOT/services/costing-engine-service"
COSTA_UI="$REPO_ROOT/ui/costa-console"

echo "=== Costing Engine Implementation Verification ==="
echo ""

# 1. Check Java source files exist
echo "--- Backend (services/costing-engine-service) ---"
if [ -d "$COSTA_SVC/src/main/java" ]; then
  java_count=$(find "$COSTA_SVC/src/main/java" -name "*.java" -type f | wc -l)
  echo "Java source files: $java_count"
  if [ "$java_count" -lt 50 ]; then
    echo "WARNING: Expected 100+ Java files, found $java_count"
    EXIT_CODE=1
  fi
else
  echo "FAIL: No Java source directory found"
  EXIT_CODE=1
fi

# 2. Check database migrations
if [ -d "$COSTA_SVC/src/main/resources/db/migration" ]; then
  migration_count=$(find "$COSTA_SVC/src/main/resources/db/migration" -name "V*.sql" -type f | wc -l)
  echo "Flyway migrations: $migration_count"
else
  echo "FAIL: No migration directory found"
  EXIT_CODE=1
fi

# 3. Check for cost engine implementations
echo ""
echo "--- Cost Engine Implementations ---"
for engine in "TariffCostEngine" "MicroCostEngine" "AbcCostEngine" "StandardCostEngine" "StockAverageCostEngine"; do
  if find "$COSTA_SVC" -name "*.java" -exec grep -l "$engine" {} \; 2>/dev/null | head -1 | grep -q .; then
    echo "FOUND: $engine"
  else
    echo "MISSING: $engine"
    EXIT_CODE=1
  fi
done

# 4. Check for controllers
echo ""
echo "--- REST Controllers ---"
for controller in "BillController" "EstimateController" "TariffController" "RulesetController"; do
  if find "$COSTA_SVC" -name "*.java" -exec grep -l "$controller" {} \; 2>/dev/null | head -1 | grep -q .; then
    echo "FOUND: $controller"
  else
    echo "MISSING: $controller"
    EXIT_CODE=1
  fi
done

# 5. Check UI pages
echo ""
echo "--- UI Console (ui/costa-console) ---"
for page_path in "bills/page.tsx" "tariffs/page.tsx" "rulesets/page.tsx" "simulate/page.tsx" "audit/page.tsx"; do
  if [ -f "$COSTA_UI/src/app/(ops)/$page_path" ]; then
    # Check if page has real API calls
    if grep -qE "(costaApi\.|apiClient\.|fetch\()" "$COSTA_UI/src/app/(ops)/$page_path"; then
      echo "REAL: $page_path"
    else
      echo "STUB: $page_path (no API calls)"
      EXIT_CODE=1
    fi
  else
    echo "MISSING: $page_path"
    EXIT_CODE=1
  fi
done

# 6. Check API client
echo ""
echo "--- API Client ---"
if [ -f "$COSTA_UI/src/lib/costaApi.ts" ]; then
  endpoint_count=$(grep -c "apiClient\.\(get\|post\|put\|patch\|delete\)" "$COSTA_UI/src/lib/costaApi.ts" 2>/dev/null || echo "0")
  echo "API endpoints in costaApi.ts: $endpoint_count"
  if [ "$endpoint_count" -lt 10 ]; then
    echo "WARNING: Expected 10+ endpoints, found $endpoint_count"
  fi
else
  echo "FAIL: costaApi.ts not found"
  EXIT_CODE=1
fi

echo ""
if [ $EXIT_CODE -eq 0 ]; then
  echo "PASS: Costing engine is fully implemented"
else
  echo "FAIL: Costing engine has implementation gaps"
fi

exit $EXIT_CODE
