#!/usr/bin/env bash
# audit-component-fidelity.sh â€” Checks component inventory against Lovable spec
set -euo pipefail

echo "=== Component Fidelity Audit ==="
echo ""

PASS=0
FAIL=0

check_component() {
  local path="$1"
  local name="$2"
  local required="$3"

  if [ -f "$path" ]; then
    lines=$(wc -l < "$path" 2>/dev/null || echo "0")
    exports=$(grep -c "export" "$path" 2>/dev/null || echo "0")
    echo "  [PASS] $name â€” $path ($lines lines, $exports exports)"
    PASS=$((PASS + 1))
  elif [ "$required" = "required" ]; then
    echo "  [FAIL] $name â€” $path (MISSING - required)"
    FAIL=$((FAIL + 1))
  else
    echo "  [SKIP] $name â€” $path (optional)"
  fi
}

echo "--- Layout Components (6 required by Lovable spec) ---"
check_component "ui/one-ui-shell/src/components/AppLayout.tsx" "AppLayout" "required"
check_component "ui/one-ui-shell/src/components/EHRLayout.tsx" "EHRLayout" "required"
check_component "ui/one-ui-shell/src/components/AuthLayout.tsx" "AuthLayout" "required"
check_component "ui/one-ui-shell/src/components/MinimalLayout.tsx" "MinimalLayout" "required"
check_component "ui/one-ui-shell/src/components/TopBar.tsx" "TopBar" "required"
check_component "ui/one-ui-shell/src/components/EncounterMenu.tsx" "EncounterMenu" "required"

echo ""
echo "--- Navigation Components ---"
check_component "ui/one-ui-shell/src/components/ZoneNavigation.tsx" "ZoneNavigation" "required"
check_component "ui/one-ui-shell/src/components/PageShell.tsx" "PageShell" "required"

echo ""
echo "--- Providers ---"
check_component "ui/one-ui-shell/src/providers/Providers.tsx" "Providers" "required"
check_component "ui/one-ui-shell/src/providers/AuthGuardProvider.tsx" "AuthGuardProvider" "required"

echo ""
echo "--- Zustand Stores (4 required by 05_state_and_storage spec) ---"
check_component "ui/one-ui-shell/src/hooks/useAuthStore.ts" "authStore" "required"
check_component "ui/one-ui-shell/src/hooks/useFacilityStore.ts" "facilityStore" "required"
check_component "ui/one-ui-shell/src/hooks/useWorkspaceStore.ts" "workspaceStore" "required"
check_component "ui/one-ui-shell/src/hooks/useShiftStore.ts" "shiftStore" "required"

echo ""
echo "--- Query Hooks ---"
for hook in ui/one-ui-shell/src/hooks/queries/*.ts; do
  if [ -f "$hook" ]; then
    name=$(basename "$hook" .ts)
    check_component "$hook" "$name" "optional"
  fi
done

echo ""
echo "=== Summary ==="
echo "  Passed: $PASS"
echo "  Failed: $FAIL"
TOTAL=$((PASS + FAIL))
if [ "$TOTAL" -gt 0 ]; then
  echo "  Score:  $PASS/$TOTAL ($(( PASS * 100 / TOTAL ))%)"
fi

if [ "$FAIL" -gt 0 ]; then
  echo "  STATUS: INCOMPLETE"
  exit 1
else
  echo "  STATUS: COMPLETE"
  exit 0
fi
