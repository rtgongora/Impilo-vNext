#!/usr/bin/env bash
# ============================================================================
# verify-citizen-parity.sh — Verify Citizen App has parity with My Life
# ============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APP="$REPO_ROOT/apps/mobile/citizen-app"
BFF="$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/mobile/citizen"
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
echo " Citizen App — My Life Parity"
echo "============================================"
echo ""

echo "=== Profile / Personal Context ==="
check "Profile section" test -f "$APP/src/screens/personal/ProfileSection.tsx"
check "Settings section" test -f "$APP/src/screens/personal/SettingsSection.tsx"
check "Profile service" test -f "$APP/src/services/profileService.ts"
check "BFF: CitizenProfileController" test -f "$BFF/CitizenProfileController.java"
echo ""

echo "=== Appointments ==="
check "Appointments section" test -f "$APP/src/screens/personal/AppointmentsSection.tsx"
check "Appointment service" test -f "$APP/src/services/appointmentService.ts"
check "BFF: CitizenAppointmentController" test -f "$BFF/CitizenAppointmentController.java"
echo ""

echo "=== Prescriptions / Refills ==="
check "Prescriptions section" test -f "$APP/src/screens/personal/PrescriptionsSection.tsx"
check "Prescription service" test -f "$APP/src/services/prescriptionService.ts"
check "BFF: CitizenPrescriptionController" test -f "$BFF/CitizenPrescriptionController.java"
echo ""

echo "=== Results ==="
check "Results section" test -f "$APP/src/screens/personal/ResultsSection.tsx"
check "Lab result service" test -f "$APP/src/services/labResultService.ts"
check "BFF: CitizenResultsController" test -f "$BFF/CitizenResultsController.java"
echo ""

echo "=== Records Access ==="
check "Records screen" test -f "$APP/src/screens/personal/RecordsScreen.tsx"
check "Records service" test -f "$APP/src/services/recordsService.ts"
check "BFF: CitizenRecordsController" test -f "$BFF/CitizenRecordsController.java"
echo ""

echo "=== Messaging ==="
check "Messaging inbox" test -f "$APP/src/screens/messaging/MessagingInboxScreen.tsx"
check "Thread view" test -f "$APP/src/screens/messaging/ThreadViewScreen.tsx"
check "Messaging service" test -f "$APP/src/services/messagingService.ts"
check "BFF: CitizenMessagingController" test -f "$BFF/CitizenMessagingController.java"
echo ""

echo "=== Telehealth ==="
check "Telehealth list" test -f "$APP/src/screens/telehealth/TelehealthListScreen.tsx"
check "Telehealth session" test -f "$APP/src/screens/telehealth/TelehealthSessionScreen.tsx"
check "Telehealth service" test -f "$APP/src/services/telehealthService.ts"
check "BFF: CitizenTelehealthController" test -f "$BFF/CitizenTelehealthController.java"
echo ""

echo "=== Reminders / Timeline ==="
check "Reminders screen" test -f "$APP/src/screens/personal/RemindersScreen.tsx"
check "Reminders service" test -f "$APP/src/services/remindersService.ts"
check "Health timeline screen" test -f "$APP/src/screens/personal/HealthTimelineScreen.tsx"
check "Health timeline service" test -f "$APP/src/services/healthTimelineService.ts"
check "BFF: CitizenRemindersController" test -f "$BFF/CitizenRemindersController.java"
check "BFF: CitizenTimelineController" test -f "$BFF/CitizenTimelineController.java"
echo ""

echo "=== Self-Service Requests ==="
check "Marketplace screen" test -f "$APP/src/screens/marketplace/MarketplaceScreen.tsx"
check "Marketplace service" test -f "$APP/src/services/marketplaceService.ts"
check "BFF: CitizenMarketplaceController" test -f "$BFF/CitizenMarketplaceController.java"
echo ""

echo "=== Coverage / Insurance ==="
check "Coverage section" test -f "$APP/src/screens/personal/CoverageSection.tsx"
check "Coverage service" test -f "$APP/src/services/coverageService.ts"
check "BFF: CitizenCoverageController" test -f "$BFF/CitizenCoverageController.java"
echo ""

echo "=== Support ==="
check "Support screen" test -f "$APP/src/screens/support/SupportScreen.tsx"
check "Support service" test -f "$APP/src/services/supportService.ts"
check "BFF: CitizenSupportController" test -f "$BFF/CitizenSupportController.java"
echo ""

echo "=== Social / Feed ==="
check "Social feed" test -f "$APP/src/screens/social/SocialFeedScreen.tsx"
check "Feed service" test -f "$APP/src/services/feedService.ts"
check "BFF: CitizenFeedController" test -f "$BFF/CitizenFeedController.java"
echo ""

echo "=== Notifications ==="
check "Notifications screen" test -f "$APP/src/screens/NotificationsScreen.tsx"
check "Messaging package" test -d "$REPO_ROOT/apps/mobile/packages/mobile-messaging"
echo ""

echo "=== Consent Management ==="
check "Consent screen" test -f "$APP/src/screens/personal/ConsentScreen.tsx"
check "Consent service" test -f "$APP/src/services/consentService.ts"
check "BFF: CitizenConsentController" test -f "$BFF/CitizenConsentController.java"
echo ""

echo "=== Shared Foundations ==="
check "Auth package" test -d "$REPO_ROOT/apps/mobile/packages/mobile-auth"
check "Trust headers" test -d "$REPO_ROOT/apps/mobile/packages/mobile-trust"
check "API client" test -d "$REPO_ROOT/apps/mobile/packages/mobile-api-client"
check "Timeline package" test -d "$REPO_ROOT/apps/mobile/packages/mobile-timeline"
echo ""

echo "============================================"
echo " Summary: PASS=$PASS  FAIL=$FAIL"
echo "============================================"
[ "$FAIL" -eq 0 ] && echo "STATUS: CITIZEN PARITY ACHIEVED" || echo "STATUS: GAPS DETECTED"
exit "$FAIL"
