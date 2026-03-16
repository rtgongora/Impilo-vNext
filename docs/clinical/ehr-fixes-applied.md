# EHR Fixes Applied

**Date**: 2026-03-16
**Branch**: `claude/review-project-manifest-jb5O0`

## Summary

54 files changed, ~8,300 lines added to close all 13 EHR clinical workflows end-to-end.

## Changes by Category

### 1. Database Migrations

| File | Description |
|---|---|
| `V6__clinical_domain_tables.sql` | 10 new tables: vitals_records, clinical_notes, lab_orders, lab_order_results, referrals, allergies, conditions, immunizations, clinical_documents, clinical_timeline |

### 2. Domain Entities (9 new)

| Entity | Key Methods |
|---|---|
| `VitalsRecord.java` | Standard vitals: BP, HR, temp, SpO2, RR, weight, height, pain |
| `ClinicalNote.java` | `sign()` — transitions DRAFT→SIGNED |
| `LabOrder.java` | `collect()`, `result()` — lifecycle management |
| `Referral.java` | `complete(outcome)` — referral closure |
| `Allergy.java` | Severity, reaction, onset tracking |
| `Condition.java` | `resolve()` — problem list management |
| `Immunization.java` | Vaccine, lot, site, route recording |
| `ClinicalDocument.java` | Document metadata with storage_ref |
| `ClinicalTimelineEntry.java` | Polymorphic timeline with source_type/source_id |

### 3. Repositories (9 new)

All extend `JpaRepository` with tenant-scoped and patient-scoped queries.

### 4. BFF Controllers (9 new + 1 enhanced)

| Controller | Endpoints | Events |
|---|---|---|
| `VitalsController` | GET, POST `/internal/v1/vitals` | `vitals.recorded` |
| `ClinicalNotesController` | GET, POST, PATCH `/internal/v1/clinical-notes` | `note.created`, `note.signed` |
| `LabOrdersController` | GET, POST `/internal/v1/lab-orders` | `lab_order.created` |
| `ReferralsController` | GET, POST `/internal/v1/referrals` | `referral.created` |
| `AllergiesController` | GET, POST `/internal/v1/allergies` | `allergy.recorded` |
| `ConditionsController` | GET, POST, PATCH `/internal/v1/conditions` | `condition.recorded` |
| `ImmunizationsController` | GET, POST `/internal/v1/immunizations` | `immunization.recorded` |
| `ClinicalDocumentsController` | GET, POST `/internal/v1/clinical-documents` | `document.uploaded` |
| `ClinicalTimelineController` | GET `/internal/v1/timeline` | — |
| `PatientController` (enhanced) | Added POST `/internal/v1/patients` | — |

### 5. TanStack Query Hooks (9 new)

| Hook | Queries | Mutations |
|---|---|---|
| `useVitals.ts` | `useVitals(patientId)` | `useRecordVitals()` |
| `useClinicalNotes.ts` | `useClinicalNotes(patientId)` | `useCreateNote()`, `useSignNote()` |
| `useLabOrders.ts` | `useLabOrders(patientId)` | `useCreateLabOrder()` |
| `useReferrals.ts` | `useReferrals(patientId)` | `useCreateReferral()` |
| `useAllergies.ts` | `useAllergies(patientId)` | `useRecordAllergy()` |
| `useConditions.ts` | `useConditions(patientId)` | `useRecordCondition()`, `useResolveCondition()` |
| `useImmunizations.ts` | `useImmunizations(patientId)` | `useRecordImmunization()` |
| `useClinicalDocuments.ts` | `useClinicalDocuments(patientId)` | `useUploadDocument()` |
| `useTimeline.ts` | `useTimeline(patientId)` | — |

### 6. EHR UI Pages (14 pages implemented/enhanced)

| Page | Key Functionality |
|---|---|
| `vitals/page.tsx` | Vitals list + recording form (BP, HR, temp, SpO2, weight, height, pain) |
| `notes/page.tsx` | Clinical notes list + SOAP entry form + sign action |
| `orders/page.tsx` | Lab orders table + create order form with priority/category |
| `results/page.tsx` | Results viewing filtered to RESULTED orders |
| `medications/page.tsx` | Prescriptions list + add prescription form |
| `referrals/page.tsx` | Referrals list + create referral form |
| `allergies/page.tsx` | Allergies management + recording |
| `conditions/page.tsx` | Problem list + resolve action |
| `immunizations/page.tsx` | Immunization records + recording |
| `documents/page.tsx` | Document listing + upload |
| `timeline/page.tsx` | Vertical timeline view |
| `encounters/page.tsx` | Encounters list + start encounter |
| `encounter/[encounterId]/page.tsx` | Enhanced with vitals save + notes save actions |
| `queue/search/page.tsx` | Full patient search with results table |

### 7. Additional Pages

| Page | Description |
|---|---|
| `summary/page.tsx` | Patient summary aggregating conditions, allergies, meds, encounters |
| `history/page.tsx` | Medical history with past encounters, resolved conditions, immunizations |
