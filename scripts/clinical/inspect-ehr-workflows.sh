#!/usr/bin/env bash
# ============================================================================
# inspect-ehr-workflows.sh — Inventory all EHR workflow surfaces
#
# Checks for the presence and completeness of:
#   1. Patient search/identity resolution
#   2. Registration/demographic update
#   3. Encounter start
#   4. Vitals capture
#   5. Clinical notes/assessment
#   6. Orders placement
#   7. Results viewing/reconciliation
#   8. Medication/dispense flow
#   9. Referrals/follow-up
#  10. Discharge/encounter close
#  11. Role/capability restrictions
#  12. Eventing/outbox side-effects
#  13. Offline/replay path
# ============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PASS=0
FAIL=0
WARN=0

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

warn() {
  local label="$1"
  echo "  [WARN] $label"
  ((WARN++)) || true
}

echo "============================================"
echo " EHR Workflow Inspection"
echo "============================================"
echo ""

# 1. Patient Search / Identity Resolution
echo "1. Patient Search / Identity Resolution"
check "UI: queue/search page exists" test -f "$REPO_ROOT/ui/experience/src/app/queue/search/page.tsx"
check "UI: queue/walk-in page exists" test -f "$REPO_ROOT/ui/experience/src/app/queue/walk-in/page.tsx"
check "Hook: usePatients exists" test -f "$REPO_ROOT/ui/experience/src/hooks/queries/usePatients.ts"
check "BFF: PatientController exists" test -f "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PatientController.java"
check "Mobile: PatientLookupScreen exists" test -f "$REPO_ROOT/apps/mobile/provider-app/src/screens/provider/PatientLookupScreen.tsx"
echo ""

# 2. Registration / Demographic Update
echo "2. Registration / Demographic Update"
check "UI: walk-in registration with new patient form" grep -q "Register New Patient" "$REPO_ROOT/ui/experience/src/app/queue/walk-in/page.tsx"
check "BFF: Patient creation endpoint" grep -q "POST" "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PatientController.java"
check "DB: patients table" grep -q "CREATE TABLE patients" "$REPO_ROOT/services/experience-bff/src/main/resources/db/migration/V3__golden_path_tables.sql"
echo ""

# 3. Encounter Start
echo "3. Encounter Start"
check "UI: encounter page exists" test -f "$REPO_ROOT/ui/experience/src/app/ehr/[patientId]/encounter/[encounterId]/page.tsx"
check "UI: encounters list exists" test -f "$REPO_ROOT/ui/experience/src/app/ehr/[patientId]/encounters/page.tsx"
check "Hook: useCreateEncounter exists" grep -q "useCreateEncounter" "$REPO_ROOT/ui/experience/src/hooks/queries/useEncounters.ts"
check "BFF: EncounterController exists" test -f "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/EncounterController.java"
check "BFF: encounter.created event" grep -q "encounter.created" "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/EncounterController.java"
echo ""

# 4. Vitals Capture
echo "4. Vitals Capture"
check "UI: vitals page exists" test -f "$REPO_ROOT/ui/experience/src/app/ehr/[patientId]/vitals/page.tsx"
check "UI: vitals save in encounter page" grep -q "handleSaveVitals" "$REPO_ROOT/ui/experience/src/app/ehr/[patientId]/encounter/[encounterId]/page.tsx"
check "Hook: useVitals exists" test -f "$REPO_ROOT/ui/experience/src/hooks/queries/useVitals.ts"
check "BFF: VitalsController exists" test -f "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/VitalsController.java"
check "DB: vitals_records table" grep -q "vitals_records" "$REPO_ROOT/services/experience-bff/src/main/resources/db/migration/V6__clinical_domain_tables.sql"
check "Mobile: VitalsPanel exists" test -f "$REPO_ROOT/apps/mobile/provider-app/src/screens/provider/VitalsPanel.tsx"
echo ""

# 5. Clinical Notes / Assessment
echo "5. Clinical Notes / Assessment"
check "UI: notes page exists" test -f "$REPO_ROOT/ui/experience/src/app/ehr/[patientId]/notes/page.tsx"
check "UI: notes save in encounter page" grep -q "handleSaveNote" "$REPO_ROOT/ui/experience/src/app/ehr/[patientId]/encounter/[encounterId]/page.tsx"
check "Hook: useClinicalNotes exists" test -f "$REPO_ROOT/ui/experience/src/hooks/queries/useClinicalNotes.ts"
check "BFF: ClinicalNotesController exists" test -f "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/ClinicalNotesController.java"
check "DB: clinical_notes table" grep -q "clinical_notes" "$REPO_ROOT/services/experience-bff/src/main/resources/db/migration/V6__clinical_domain_tables.sql"
check "Mobile: NotesPanel exists" test -f "$REPO_ROOT/apps/mobile/provider-app/src/screens/provider/NotesPanel.tsx"
echo ""

# 6. Orders Placement
echo "6. Orders Placement"
check "UI: orders page exists" test -f "$REPO_ROOT/ui/experience/src/app/ehr/[patientId]/orders/page.tsx"
check "Hook: useLabOrders exists" test -f "$REPO_ROOT/ui/experience/src/hooks/queries/useLabOrders.ts"
check "BFF: LabOrdersController exists" test -f "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/LabOrdersController.java"
check "DB: lab_orders table" grep -q "lab_orders" "$REPO_ROOT/services/experience-bff/src/main/resources/db/migration/V6__clinical_domain_tables.sql"
check "Mobile: LabOrderPanel exists" test -f "$REPO_ROOT/apps/mobile/provider-app/src/screens/provider/LabOrderPanel.tsx"
echo ""

# 7. Results Viewing / Reconciliation
echo "7. Results Viewing / Reconciliation"
check "UI: results page exists" test -f "$REPO_ROOT/ui/experience/src/app/ehr/[patientId]/results/page.tsx"
check "Mobile: ResultsViewScreen exists" test -f "$REPO_ROOT/apps/mobile/provider-app/src/screens/provider/ResultsViewScreen.tsx"
check "BFF: MobileResultsController exists" test -f "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/mobile/MobileResultsController.java"
check "Citizen: ResultsSection exists" test -f "$REPO_ROOT/apps/mobile/citizen-app/src/screens/personal/ResultsSection.tsx"
echo ""

# 8. Medication / Dispense Flow
echo "8. Medication / Dispense Flow"
check "UI: medications page exists" test -f "$REPO_ROOT/ui/experience/src/app/ehr/[patientId]/medications/page.tsx"
check "BFF: PharmacyController exists" test -f "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PharmacyController.java"
check "BFF: prescription creation" grep -q "createPrescription\|CreatePrescriptionRequest" "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PharmacyController.java"
check "BFF: dispense endpoint" grep -q "dispense" "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PharmacyController.java"
check "Mobile: PrescriptionPanel exists" test -f "$REPO_ROOT/apps/mobile/provider-app/src/screens/provider/PrescriptionPanel.tsx"
check "Citizen: PrescriptionsSection exists" test -f "$REPO_ROOT/apps/mobile/citizen-app/src/screens/personal/PrescriptionsSection.tsx"
echo ""

# 9. Referrals / Follow-up
echo "9. Referrals / Follow-up"
check "UI: referrals page exists" test -f "$REPO_ROOT/ui/experience/src/app/ehr/[patientId]/referrals/page.tsx"
check "Hook: useReferrals exists" test -f "$REPO_ROOT/ui/experience/src/hooks/queries/useReferrals.ts"
check "BFF: ReferralsController exists" test -f "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/ReferralsController.java"
check "Mobile: ReferralPanel exists" test -f "$REPO_ROOT/apps/mobile/provider-app/src/screens/provider/ReferralPanel.tsx"
echo ""

# 10. Discharge / Encounter Close
echo "10. Discharge / Encounter Close"
check "UI: close encounter button" grep -q "Close Encounter" "$REPO_ROOT/ui/experience/src/app/ehr/[patientId]/encounter/[encounterId]/page.tsx"
check "Hook: useCloseEncounter exists" grep -q "useCloseEncounter" "$REPO_ROOT/ui/experience/src/hooks/queries/useEncounters.ts"
check "BFF: close endpoint" grep -q "close" "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/EncounterController.java"
check "BFF: encounter.closed event" grep -q "encounter.closed" "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/EncounterController.java"
echo ""

# 11. Role / Capability Restrictions
echo "11. Role / Capability Restrictions"
check "BFF: SecurityConfig exists" test -f "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/SecurityConfig.java"
check "Trust: TSHEPO ext_authz" test -d "$REPO_ROOT/services/tshepo-service"
check "Mobile: AuthGuard exists" test -f "$REPO_ROOT/apps/mobile/provider-app/src/navigation/AuthGuard.tsx"
check "UI: AuthGuardProvider exists" test -f "$REPO_ROOT/ui/experience/src/providers/AuthGuardProvider.tsx"
echo ""

# 12. Eventing / Outbox Side-Effects
echo "12. Eventing / Outbox Side-Effects"
check "DB: event_outbox table" grep -q "event_outbox" "$REPO_ROOT/services/experience-bff/src/main/resources/db/migration/V1__create_core_tables.sql"
check "BFF: OutboxService used" grep -rq "OutboxService" "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/"
check "Events: encounter events" grep -q "encounter.created\|encounter.closed" "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/EncounterController.java"
check "Events: vitals events" grep -q "vitals.recorded" "$REPO_ROOT/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/VitalsController.java"
echo ""

# 13. Offline / Replay Path
echo "13. Offline / Replay Path"
check "Mobile: offline sync engine" test -f "$REPO_ROOT/apps/mobile/packages/mobile-offline/src/syncEngine.ts"
check "Mobile: offline store" test -f "$REPO_ROOT/apps/mobile/packages/mobile-offline/src/offlineStore.ts"
check "Mobile: break-glass screen" test -f "$REPO_ROOT/apps/mobile/provider-app/src/screens/offline/BreakGlassScreen.tsx"
check "Mobile: conflict review" test -f "$REPO_ROOT/apps/mobile/provider-app/src/screens/offline/ConflictReviewScreen.tsx"
check "BFF: idempotency table" grep -q "idempotency" "$REPO_ROOT/services/experience-bff/src/main/resources/db/migration/V1__create_core_tables.sql"
echo ""

echo "============================================"
echo " Summary: PASS=$PASS  FAIL=$FAIL  WARN=$WARN"
echo "============================================"

if [ "$FAIL" -gt 0 ]; then
  echo "STATUS: GAPS DETECTED"
  exit 1
else
  echo "STATUS: ALL WORKFLOWS PRESENT"
  exit 0
fi
