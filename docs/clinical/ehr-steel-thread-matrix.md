# EHR Steel Thread Verification Matrix

**Date**: 2026-03-16
**Branch**: `claude/review-project-manifest-jb5O0`

## Summary

Each steel thread traces: **UI Route → Hook → BFF Controller → Domain Entity → DB Migration**

**Result: 13/13 steel threads COMPLETE**

## Matrix

| Steel Thread | UI Page | Hook | Controller | Domain | Migration |
|---|---|---|---|---|---|
| Patient Search → Queue | `queue/search/page.tsx` | `usePatients.ts` | `PatientController.java` | `Patient.java` | `CREATE TABLE patients` |
| Encounter Lifecycle | `ehr/[patientId]/encounter/[encounterId]/page.tsx` | `useEncounters.ts` | `EncounterController.java` | `Encounter.java` | `CREATE TABLE encounters` |
| Vitals Capture | `ehr/[patientId]/vitals/page.tsx` | `useVitals.ts` | `VitalsController.java` | `VitalsRecord.java` | `CREATE TABLE vitals_records` |
| Clinical Notes | `ehr/[patientId]/notes/page.tsx` | `useClinicalNotes.ts` | `ClinicalNotesController.java` | `ClinicalNote.java` | `CREATE TABLE clinical_notes` |
| Lab Orders | `ehr/[patientId]/orders/page.tsx` | `useLabOrders.ts` | `LabOrdersController.java` | `LabOrder.java` | `CREATE TABLE lab_orders` |
| Referrals | `ehr/[patientId]/referrals/page.tsx` | `useReferrals.ts` | `ReferralsController.java` | `Referral.java` | `CREATE TABLE referrals` |
| Allergies | `ehr/[patientId]/allergies/page.tsx` | `useAllergies.ts` | `AllergiesController.java` | `Allergy.java` | `CREATE TABLE allergies` |
| Conditions | `ehr/[patientId]/conditions/page.tsx` | `useConditions.ts` | `ConditionsController.java` | `Condition.java` | `CREATE TABLE conditions` |
| Immunizations | `ehr/[patientId]/immunizations/page.tsx` | `useImmunizations.ts` | `ImmunizationsController.java` | `Immunization.java` | `CREATE TABLE immunizations` |
| Pharmacy / Prescriptions | `ehr/[patientId]/medications/page.tsx` | `usePharmacy.ts` | `PharmacyController.java` | `Prescription.java` | `CREATE TABLE prescriptions` |
| Clinical Timeline | `ehr/[patientId]/timeline/page.tsx` | `useTimeline.ts` | `ClinicalTimelineController.java` | `ClinicalTimelineEntry.java` | `CREATE TABLE clinical_timeline` |
| Clinical Documents | `ehr/[patientId]/documents/page.tsx` | `useClinicalDocuments.ts` | `ClinicalDocumentsController.java` | `ClinicalDocument.java` | `CREATE TABLE clinical_documents` |
| Discharge Workflow | `ehr/[patientId]/discharge/page.tsx` | `useDischarge.ts` | `EncounterController.java` (discharge endpoint) | `Encounter.java` (discharge method) | `V8: ALTER TABLE encounters ADD discharge columns` |

## Layer Locations

- **UI**: `ui/experience/src/app/ehr/[patientId]/*/page.tsx`
- **Hooks**: `ui/experience/src/hooks/queries/use*.ts`
- **Controllers**: `services/experience-bff/src/main/java/.../controller/*.java`
- **Domain**: `services/experience-bff/src/main/java/.../domain/*.java`
- **Migrations**: `services/experience-bff/src/main/resources/db/migration/V6__clinical_domain_tables.sql`, `V8__encounter_discharge_columns.sql`

## Verification Script

```bash
scripts/clinical/run-ehr-steel-threads.sh
```
