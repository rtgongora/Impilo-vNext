#!/usr/bin/env bash
# verify-remediations.sh — Verify that all remediated pages are now real
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXIT_CODE=0

echo "=== Verifying Remediated Pages ==="
echo ""

# List of pages that were remediated in the adversarial wave
declare -A REMEDIATED_PAGES
REMEDIATED_PAGES["ui/msika-flow-ops/src/app/(ops)/orders/page.tsx"]="opsApi.searchOrders"
REMEDIATED_PAGES["ui/msika-flow-ops/src/app/(ops)/vendors/page.tsx"]="opsApi.listVendors"
REMEDIATED_PAGES["ui/msika-flow-ops/src/app/(ops)/audit/page.tsx"]="opsApi.listAuditEvents"
REMEDIATED_PAGES["ui/msika-flow-portal/src/app/(portal)/browse/page.tsx"]="msikaFlowApi.searchCatalog"
REMEDIATED_PAGES["ui/msika-flow-portal/src/app/(portal)/cart/page.tsx"]="msikaFlowApi.getOrder"
REMEDIATED_PAGES["ui/msika-flow-portal/src/app/(portal)/substitutions/page.tsx"]="msikaFlowApi.listSubstitutions"
REMEDIATED_PAGES["ui/ops-console/src/app/(ops)/vito/page.tsx"]="vitoApi.listClients"
REMEDIATED_PAGES["ui/butano-web/src/app/(ops)/reconciliation/page.tsx"]="butanoApi.listReconciliationJobs"

for page in "${!REMEDIATED_PAGES[@]}"; do
  expected_api="${REMEDIATED_PAGES[$page]}"
  full_path="$REPO_ROOT/$page"

  if [ ! -f "$full_path" ]; then
    echo "FAIL: $page — file not found"
    EXIT_CODE=1
    continue
  fi

  # Check the page has the expected API call
  if grep -q "$expected_api" "$full_path" 2>/dev/null; then
    echo "PASS: $page — uses $expected_api"
  else
    echo "FAIL: $page — expected $expected_api but not found"
    EXIT_CODE=1
  fi

  # Check the page has no hardcoded sample data
  if grep -qE "(SAMPLE_|MOCK_|DEMO_|FAKE_)" "$full_path" 2>/dev/null; then
    echo "  WARNING: Still contains hardcoded sample data"
    EXIT_CODE=1
  fi

  # Check the page has no sessionStorage usage (except where appropriate)
  if grep -q "sessionStorage" "$full_path" 2>/dev/null; then
    echo "  WARNING: Still uses sessionStorage"
    # Only fail for reconciliation page
    if echo "$page" | grep -q "reconciliation"; then
      EXIT_CODE=1
    fi
  fi
done

# Verify API client enhancements
echo ""
echo "=== Verifying API Client Enhancements ==="

check_api_method() {
  local file="$1"
  local method="$2"
  local full="$REPO_ROOT/$file"
  if [ -f "$full" ] && grep -q "$method" "$full" 2>/dev/null; then
    echo "PASS: $file contains $method"
  else
    echo "FAIL: $file missing $method"
    EXIT_CODE=1
  fi
}

check_api_method "ui/msika-flow-ops/src/lib/opsApi.ts" "searchOrders"
check_api_method "ui/msika-flow-ops/src/lib/opsApi.ts" "listVendors"
check_api_method "ui/msika-flow-ops/src/lib/opsApi.ts" "listAuditEvents"
check_api_method "ui/msika-flow-ops/src/lib/opsApi.ts" "reinstateVendor"
check_api_method "ui/msika-flow-portal/src/lib/msikaFlowApi.ts" "searchCatalog"
check_api_method "ui/msika-flow-portal/src/lib/msikaFlowApi.ts" "listSubstitutions"
check_api_method "ui/msika-flow-portal/src/lib/msikaFlowApi.ts" "rejectSubstitution"
check_api_method "ui/butano-web/src/lib/butanoApi.ts" "listReconciliationJobs"

echo ""
if [ $EXIT_CODE -eq 0 ]; then
  echo "PASS: All remediations verified successfully"
else
  echo "FAIL: Some remediations are incomplete"
fi

exit $EXIT_CODE
