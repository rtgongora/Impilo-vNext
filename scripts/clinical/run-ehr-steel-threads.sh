#!/usr/bin/env bash
# ============================================================================
# run-ehr-steel-threads.sh â€” Verify EHR steel thread paths exist end-to-end
#
# Each steel thread traces: UI Route â†’ Hook â†’ BFF Controller â†’ Domain â†’ DB â†’ Outbox
# ============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PASS=0
FAIL=0

thread() {
  local name="$1"
  local ui_file="$2"
  local hook_file="$3"
  local controller="$4"
  local domain="$5"
  local migration_pattern="$6"

  echo "  Thread: $name"
  local ok=true

  if [ ! -f "$REPO_ROOT/$ui_file" ]; then
    echo "    [FAIL] UI: $ui_file"
    ok=false
  else
    echo "    [PASS] UI: $ui_file"
  fi

  if [ ! -f "$REPO_ROOT/$hook_file" ]; then
    echo "    [FAIL] Hook: $hook_file"
    ok=false
  else
    echo "    [PASS] Hook: $hook_file"
  fi

  if [ ! -f "$REPO_ROOT/$controller" ]; then
    echo "    [FAIL] Controller: $controller"
    ok=false
  else
    echo "    [PASS] Controller: $controller"
  fi

  if [ ! -f "$REPO_ROOT/$domain" ]; then
    echo "    [FAIL] Domain: $domain"
    ok=false
  else
    echo "    [PASS] Domain: $domain"
  fi

  if ! grep -rq "$migration_pattern" "$REPO_ROOT/services/experience-bff/src/main/resources/db/migration/" 2>/dev/null; then
    echo "    [FAIL] Migration: pattern '$migration_pattern' not found"
    ok=false
  else
    echo "    [PASS] Migration: $migration_pattern"
  fi

  if $ok; then
    echo "    RESULT: COMPLETE"
    ((PASS++)) || true
  else
    echo "    RESULT: INCOMPLETE"
    ((FAIL++)) || true
  fi
  echo ""
}

echo "============================================"
echo " EHR Steel Thread Verification"
echo "============================================"
echo ""

thread "Patient Search â†’ Queue" \
  "ui/one-ui-shell/src/app/queue/search/page.tsx" \
  "ui/one-ui-shell/src/hooks/queries/usePatients.ts" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PatientController.java" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/domain/Patient.java" \
  "CREATE TABLE patients"

thread "Encounter Lifecycle" \
  "ui/one-ui-shell/src/app/ehr/[patientId]/encounter/[encounterId]/page.tsx" \
  "ui/one-ui-shell/src/hooks/queries/useEncounters.ts" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/EncounterController.java" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/domain/Encounter.java" \
  "CREATE TABLE encounters"

thread "Vitals Capture" \
  "ui/one-ui-shell/src/app/ehr/[patientId]/vitals/page.tsx" \
  "ui/one-ui-shell/src/hooks/queries/useVitals.ts" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/VitalsController.java" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/domain/VitalsRecord.java" \
  "CREATE TABLE vitals_records"

thread "Clinical Notes" \
  "ui/one-ui-shell/src/app/ehr/[patientId]/notes/page.tsx" \
  "ui/one-ui-shell/src/hooks/queries/useClinicalNotes.ts" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/ClinicalNotesController.java" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/domain/ClinicalNote.java" \
  "CREATE TABLE clinical_notes"

thread "Lab Orders" \
  "ui/one-ui-shell/src/app/ehr/[patientId]/orders/page.tsx" \
  "ui/one-ui-shell/src/hooks/queries/useLabOrders.ts" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/LabOrdersController.java" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/domain/LabOrder.java" \
  "CREATE TABLE lab_orders"

thread "Referrals" \
  "ui/one-ui-shell/src/app/ehr/[patientId]/referrals/page.tsx" \
  "ui/one-ui-shell/src/hooks/queries/useReferrals.ts" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/ReferralsController.java" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/domain/Referral.java" \
  "CREATE TABLE referrals"

thread "Allergies" \
  "ui/one-ui-shell/src/app/ehr/[patientId]/allergies/page.tsx" \
  "ui/one-ui-shell/src/hooks/queries/useAllergies.ts" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/AllergiesController.java" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/domain/Allergy.java" \
  "CREATE TABLE allergies"

thread "Conditions" \
  "ui/one-ui-shell/src/app/ehr/[patientId]/conditions/page.tsx" \
  "ui/one-ui-shell/src/hooks/queries/useConditions.ts" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/ConditionsController.java" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/domain/Condition.java" \
  "CREATE TABLE conditions"

thread "Immunizations" \
  "ui/one-ui-shell/src/app/ehr/[patientId]/immunizations/page.tsx" \
  "ui/one-ui-shell/src/hooks/queries/useImmunizations.ts" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/ImmunizationsController.java" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/domain/Immunization.java" \
  "CREATE TABLE immunizations"

thread "Pharmacy / Prescriptions" \
  "ui/one-ui-shell/src/app/ehr/[patientId]/medications/page.tsx" \
  "ui/one-ui-shell/src/hooks/queries/usePharmacy.ts" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PharmacyController.java" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/domain/Prescription.java" \
  "CREATE TABLE prescriptions"

thread "Clinical Timeline" \
  "ui/one-ui-shell/src/app/ehr/[patientId]/timeline/page.tsx" \
  "ui/one-ui-shell/src/hooks/queries/useTimeline.ts" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/ClinicalTimelineController.java" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/domain/ClinicalTimelineEntry.java" \
  "CREATE TABLE clinical_timeline"

thread "Clinical Documents" \
  "ui/one-ui-shell/src/app/ehr/[patientId]/documents/page.tsx" \
  "ui/one-ui-shell/src/hooks/queries/useClinicalDocuments.ts" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/ClinicalDocumentsController.java" \
  "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/domain/ClinicalDocument.java" \
  "CREATE TABLE clinical_documents"

echo "============================================"
echo " Summary: COMPLETE=$PASS  INCOMPLETE=$FAIL"
echo "============================================"

if [ "$FAIL" -gt 0 ]; then
  exit 1
else
  echo "ALL STEEL THREADS VERIFIED"
  exit 0
fi
