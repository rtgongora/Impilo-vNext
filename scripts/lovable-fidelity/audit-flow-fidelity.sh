#!/usr/bin/env bash
# audit-flow-fidelity.sh â€” Checks golden path flow completeness
set -euo pipefail

echo "=== Flow Fidelity Audit ==="
echo ""

PASS=0
FAIL=0

check_file() {
  local path="$1"
  local desc="$2"
  if [ -f "$path" ]; then
    lines=$(wc -l < "$path" 2>/dev/null || echo "0")
    if [ "$lines" -gt 15 ]; then
      echo "  [PASS] $desc â€” $path ($lines lines)"
      PASS=$((PASS + 1))
    else
      echo "  [STUB] $desc â€” $path ($lines lines)"
      FAIL=$((FAIL + 1))
    fi
  else
    echo "  [MISS] $desc â€” $path"
    FAIL=$((FAIL + 1))
  fi
}

echo "--- Golden Path A: Email Login ---"
check_file "ui/one-ui-shell/src/app/auth/login/page.tsx" "Login page"
check_file "ui/one-ui-shell/src/hooks/queries/useAuth.ts" "Auth hooks"
check_file "ui/one-ui-shell/src/app/home/page.tsx" "Home dashboard"

echo ""
echo "--- Golden Path B: Provider ID Login ---"
check_file "ui/one-ui-shell/src/app/auth/login/provider-id/page.tsx" "Provider ID login"
check_file "ui/one-ui-shell/src/app/auth/login/biometric/page.tsx" "Biometric page"

echo ""
echo "--- Golden Path C: Queue â†’ Encounter â†’ Close ---"
check_file "ui/one-ui-shell/src/app/queue/page.tsx" "Queue page"
check_file "ui/one-ui-shell/src/app/ehr/[patientId]/page.tsx" "Patient chart"
check_file "ui/one-ui-shell/src/app/ehr/[patientId]/encounter/[encounterId]/page.tsx" "Encounter page"
check_file "ui/one-ui-shell/src/app/ehr/[patientId]/vitals/page.tsx" "Vitals page"
check_file "ui/one-ui-shell/src/app/ehr/[patientId]/notes/page.tsx" "Clinical notes"
check_file "ui/one-ui-shell/src/app/ehr/[patientId]/discharge/page.tsx" "Discharge page"
check_file "ui/one-ui-shell/src/hooks/queries/useEncounters.ts" "Encounter hooks"
check_file "ui/one-ui-shell/src/hooks/queries/useVitals.ts" "Vitals hooks"
check_file "ui/one-ui-shell/src/hooks/queries/useQueue.ts" "Queue hooks"

echo ""
echo "--- Golden Path D: Admin/TSHEPO ---"
check_file "ui/one-ui-shell/src/app/admin/page.tsx" "Admin dashboard"
check_file "ui/one-ui-shell/src/app/admin/users/page.tsx" "Users page"
check_file "ui/one-ui-shell/src/app/admin/audit/page.tsx" "Audit page"
check_file "ui/one-ui-shell/src/hooks/queries/useAdminUsers.ts" "Admin hooks"
check_file "ui/one-ui-shell/src/hooks/queries/useAudit.ts" "Audit hooks"

echo ""
echo "--- Golden Path E: Marketplace ---"
check_file "ui/one-ui-shell/src/app/marketplace/page.tsx" "Marketplace dashboard"
check_file "ui/one-ui-shell/src/app/marketplace/orders/page.tsx" "Orders page"
check_file "ui/one-ui-shell/src/hooks/queries/useMarketplace.ts" "Marketplace hooks"

echo ""
echo "--- Golden Path F: Registry ---"
check_file "ui/one-ui-shell/src/app/registry/page.tsx" "Registry dashboard"
check_file "ui/one-ui-shell/src/app/registry/providers/page.tsx" "Providers page"
check_file "ui/one-ui-shell/src/hooks/queries/useRegistry.ts" "Registry hooks"

echo ""
echo "--- Layout Components ---"
check_file "ui/one-ui-shell/src/components/AppLayout.tsx" "AppLayout"
check_file "ui/one-ui-shell/src/components/EHRLayout.tsx" "EHRLayout"
check_file "ui/one-ui-shell/src/components/TopBar.tsx" "TopBar"
check_file "ui/one-ui-shell/src/components/EncounterMenu.tsx" "EncounterMenu"
check_file "ui/one-ui-shell/src/components/ZoneNavigation.tsx" "ZoneNavigation"
check_file "ui/one-ui-shell/src/components/PageShell.tsx" "PageShell"
check_file "ui/one-ui-shell/src/components/AuthLayout.tsx" "AuthLayout"
check_file "ui/one-ui-shell/src/components/MinimalLayout.tsx" "MinimalLayout"

echo ""
echo "=== Summary ==="
echo "  Passed: $PASS"
echo "  Failed: $FAIL"
TOTAL=$((PASS + FAIL))
echo "  Score:  $PASS/$TOTAL ($(( PASS * 100 / TOTAL ))%)"

if [ "$FAIL" -gt 0 ]; then
  echo "  STATUS: INCOMPLETE"
  exit 1
else
  echo "  STATUS: COMPLETE"
  exit 0
fi
