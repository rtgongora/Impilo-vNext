#!/usr/bin/env bash
# ============================================================================
# verify-provider-parity.sh — Verify Provider App has parity with Work + Professional
# ============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APP="$REPO_ROOT/apps/mobile/provider-app"
BFF="$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/mobile"
PASS=0
FAIL=0

check() {
  local label="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    echo "  [PASS] $label"
    ((PASS++)) || true
  else
    echo "  [FAIL] $label"
    ((FAIL++)) || true
  fi
}

echo "============================================"
echo " Provider App — Work + Professional Parity"
echo "============================================"
echo ""

echo "=== My Work — Worklists / Task Queues ==="
check "Dashboard screen" test -f "$APP/src/screens/provider/ProviderDashboardScreen.tsx"
check "Task service" test -f "$APP/src/services/taskService.ts"
check "BFF: MobileTaskController" test -f "$BFF/MobileTaskController.java"
echo ""

echo "=== My Work — Patient & Encounter Actions ==="
check "Patient lookup screen" test -f "$APP/src/screens/provider/PatientLookupScreen.tsx"
check "Encounter screen" test -f "$APP/src/screens/provider/EncounterScreen.tsx"
check "Encounter service" test -f "$APP/src/services/encounterService.ts"
check "Encounter store" test -f "$APP/src/stores/encounterStore.ts"
echo ""

echo "=== My Work — Vitals & Note Capture ==="
check "Vitals panel" test -f "$APP/src/screens/provider/VitalsPanel.tsx"
check "Notes panel" test -f "$APP/src/screens/provider/NotesPanel.tsx"
check "Vitals service" test -f "$APP/src/services/vitalsService.ts"
check "BFF: MobileVitalsController" test -f "$BFF/MobileVitalsController.java"
echo ""

echo "=== My Work — Results / Orders Visibility ==="
check "Lab order panel" test -f "$APP/src/screens/provider/LabOrderPanel.tsx"
check "Results view screen" test -f "$APP/src/screens/provider/ResultsViewScreen.tsx"
check "Lab service" test -f "$APP/src/services/labService.ts"
check "BFF: MobileLabController" test -f "$BFF/MobileLabController.java"
check "BFF: MobileResultsController" test -f "$BFF/MobileResultsController.java"
echo ""

echo "=== My Work — Messaging / Handoffs ==="
check "Messaging screen" test -f "$APP/src/screens/provider/MessagingScreen.tsx"
check "BFF: MobileMessagingController" test -f "$BFF/MobileMessagingController.java"
check "Shared messaging package" test -d "$REPO_ROOT/apps/mobile/packages/mobile-messaging"
echo ""

echo "=== My Work — Telemedicine ==="
check "Telemedicine screen" test -f "$APP/src/screens/provider/TelemedicineScreen.tsx"
check "BFF: MobileTelemedicineController" test -f "$BFF/MobileTelemedicineController.java"
echo ""

echo "=== My Work — Prescriptions ==="
check "Prescription panel" test -f "$APP/src/screens/provider/PrescriptionPanel.tsx"
check "Prescription service" test -f "$APP/src/services/prescriptionService.ts"
check "BFF: MobilePrescriptionController" test -f "$BFF/MobilePrescriptionController.java"
echo ""

echo "=== My Work — Referrals ==="
check "Referral panel" test -f "$APP/src/screens/provider/ReferralPanel.tsx"
check "Referral service" test -f "$APP/src/services/referralService.ts"
check "BFF: MobileReferralController" test -f "$BFF/MobileReferralController.java"
echo ""

echo "=== My Work — Diagnosis ==="
check "Diagnosis panel" test -f "$APP/src/screens/provider/DiagnosisPanel.tsx"
check "Diagnosis service" test -f "$APP/src/services/diagnosisService.ts"
check "BFF: MobileDiagnosisController" test -f "$BFF/MobileDiagnosisController.java"
echo ""

echo "=== My Professional — Profile & Notices ==="
check "Professional profile screen" test -f "$APP/src/screens/provider/ProfessionalProfileScreen.tsx"
check "Schedule screen" test -f "$APP/src/screens/provider/ScheduleScreen.tsx"
check "Profile service" test -f "$APP/src/services/profileService.ts"
check "BFF: MobileProfileController" test -f "$BFF/MobileProfileController.java"
check "BFF: MobileScheduleController" test -f "$BFF/MobileScheduleController.java"
check "BFF: MobileNoticesController" test -f "$BFF/MobileNoticesController.java"
echo ""

echo "=== My Work — Outreach & Supervisor ==="
check "Outreach dashboard" test -f "$APP/src/screens/outreach/OutreachDashboardScreen.tsx"
check "Supervisor dashboard" test -f "$APP/src/screens/supervisor/SupervisorDashboardScreen.tsx"
check "Team overview screen" test -f "$APP/src/screens/supervisor/TeamOverviewScreen.tsx"
check "BFF: MobileSupervisorController" test -f "$BFF/MobileSupervisorController.java"
echo ""

echo "=== Shared Foundations ==="
check "Auth package" test -d "$REPO_ROOT/apps/mobile/packages/mobile-auth"
check "Trust headers" test -d "$REPO_ROOT/apps/mobile/packages/mobile-trust"
check "Offline sync" test -d "$REPO_ROOT/apps/mobile/packages/mobile-offline"
check "API client" test -d "$REPO_ROOT/apps/mobile/packages/mobile-api-client"
check "Mode router" test -f "$APP/src/navigation/ModeRouter.tsx"
check "Break-glass screen" test -f "$APP/src/screens/offline/BreakGlassScreen.tsx"
echo ""

echo "============================================"
echo " Summary: PASS=$PASS  FAIL=$FAIL"
echo "============================================"
[ "$FAIL" -eq 0 ] && echo "STATUS: PROVIDER PARITY ACHIEVED" || echo "STATUS: GAPS DETECTED"
exit "$FAIL"
