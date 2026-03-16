#!/usr/bin/env bash
# audit-app-parity.sh — Checks provider/citizen app parity with web experience
set -euo pipefail

echo "=== App Parity Audit ==="
echo ""

PASS=0
FAIL=0

check_exists() {
  local path="$1"
  local desc="$2"
  if [ -f "$path" ] || [ -d "$path" ]; then
    echo "  [PASS] $desc — $path"
    PASS=$((PASS + 1))
  else
    echo "  [FAIL] $desc — $path"
    FAIL=$((FAIL + 1))
  fi
}

echo "--- Provider App ---"
check_exists "apps/mobile/provider-app/src/App.tsx" "Provider App entry"
check_exists "apps/mobile/provider-app/src/navigation/AppNavigator.tsx" "App navigator"
check_exists "apps/mobile/provider-app/src/navigation/ModeRouter.tsx" "Mode router"
check_exists "apps/mobile/provider-app/src/navigation/ProviderTabs.tsx" "Provider tabs"
check_exists "apps/mobile/provider-app/src/navigation/OutreachTabs.tsx" "Outreach tabs"
check_exists "apps/mobile/provider-app/src/navigation/SupervisorTabs.tsx" "Supervisor tabs"
check_exists "apps/mobile/provider-app/src/navigation/OfflineTabs.tsx" "Offline tabs"
check_exists "apps/mobile/provider-app/src/services/encounterService.ts" "Encounter service"
check_exists "apps/mobile/provider-app/src/services/vitalsService.ts" "Vitals service"
check_exists "apps/mobile/provider-app/src/services/prescriptionService.ts" "Prescription service"
check_exists "apps/mobile/provider-app/src/services/labService.ts" "Lab service"
check_exists "apps/mobile/provider-app/src/services/referralService.ts" "Referral service"
check_exists "apps/mobile/provider-app/src/services/dischargeService.ts" "Discharge service"

echo ""
echo "--- Citizen App ---"
check_exists "apps/mobile/citizen-app/src/App.tsx" "Citizen App entry"
check_exists "apps/mobile/citizen-app/src/navigation/AppNavigator.tsx" "App navigator"
check_exists "apps/mobile/citizen-app/src/navigation/CitizenTabs.tsx" "Citizen tabs"
check_exists "apps/mobile/citizen-app/src/services/appointmentService.ts" "Appointment service"
check_exists "apps/mobile/citizen-app/src/services/prescriptionService.ts" "Prescription service"
check_exists "apps/mobile/citizen-app/src/services/labResultService.ts" "Lab result service"
check_exists "apps/mobile/citizen-app/src/services/healthTimelineService.ts" "Timeline service"
check_exists "apps/mobile/citizen-app/src/services/messagingService.ts" "Messaging service"
check_exists "apps/mobile/citizen-app/src/services/telehealthService.ts" "Telehealth service"
check_exists "apps/mobile/citizen-app/src/services/marketplaceService.ts" "Marketplace service"

echo ""
echo "--- Shared Mobile Packages ---"
check_exists "apps/mobile/packages/mobile-auth" "mobile-auth package"
check_exists "apps/mobile/packages/mobile-api-client" "mobile-api-client package"
check_exists "apps/mobile/packages/mobile-trust" "mobile-trust package"
check_exists "apps/mobile/packages/mobile-offline" "mobile-offline package"
check_exists "apps/mobile/packages/mobile-messaging" "mobile-messaging package"
check_exists "apps/mobile/packages/mobile-timeline" "mobile-timeline package"
check_exists "apps/mobile/packages/mobile-design-system" "mobile-design-system package"

echo ""
echo "--- Auxiliary UIs ---"
check_exists "ui/support-console/src/app/(support)/layout.tsx" "Support Console"
check_exists "ui/developer-console/src/app/(developer)/layout.tsx" "Developer Console"
check_exists "ui/self-service/src/app/(citizen)/layout.tsx" "Self-Service Portal"

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
