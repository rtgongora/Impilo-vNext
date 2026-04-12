# BFF Decomposition — Part 4: Wave Execution Plan

**Version**: 1.0.0
**Date**: 2026-04-12
**Status**: PROPOSED

---

## 1. Migration Strategy: Strangler Fig Pattern

The BFF monolith is decomposed incrementally. For each table/controller being migrated:

1. **Add API to sovereign service** (PCT, TUSO, VITO, etc.) if not already present
2. **Add/update BFF client** to call the sovereign service
3. **Refactor BFF controller** to proxy through the client instead of local JdbcTemplate
4. **Keep BFF table as read cache** initially (populated from sovereign service response)
5. **Verify** end-to-end through UI
6. **Later wave**: drop the BFF table once reads also go through the sovereign service

No big-bang migration. Each controller is migrated independently. The UI never knows the difference.

---

## 2. Wave 0 — PCT Database Foundation (No BFF Changes Yet)

**Goal**: Create the pct_db schemas and Flyway migrations so PCT can accept writes.

**Tasks**:
- [ ] Create Flyway migration V005__pct_clinical_schema.sql (pct_clinical schema + tables from Part 3)
- [ ] Create Flyway migration V006__pct_orders_schema.sql (pct_orders schema + tables)
- [ ] Create Flyway migration V007__pct_queue_expansion.sql (expand existing queue tables if needed)
- [ ] Create Flyway migration V008__pct_inpatient_schema.sql (pct_inpatient schema + tables)
- [ ] Create Flyway migration V009__pct_finance_schema.sql (pct_finance schema)
- [ ] Enable RLS on all new tables
- [ ] Add PCT API controllers for the new clinical endpoints
- [ ] Add PctOutboxPublisher extending CompanionOutboxPublisher (if not already present)
- [ ] Verify PCT starts cleanly with new migrations

**Exit criteria**: PCT can accept allergy/condition/vital/immunization writes via its own API.

---

## 3. Wave 1 — CRITICAL Clinical Data (Safety-First)

**Goal**: Move safety-critical clinical writes from BFF → PCT.

### 3.1 Allergies (Class A — drug interaction safety)

| Step | Action |
|------|--------|
| 1 | Add `POST /internal/v1/clinical/allergies` to PCT |
| 2 | Add `GET /internal/v1/clinical/allergies?patient_cpid=` to PCT |
| 3 | Add `DELETE /internal/v1/clinical/allergies/{id}` to PCT |
| 4 | Update `PctServiceClient` in BFF with `createAllergy()`, `listAllergies()`, `deactivateAllergy()` |
| 5 | Refactor `AllergiesController` in BFF to proxy to PctServiceClient |
| 6 | Verify UI allergy recording works end-to-end |
| 7 | PCT emits `clinical.pct.allergy.recorded.v1` → BUTANO creates AllergyIntolerance |

### 3.2 Conditions (Class A/B — diagnosis management)

Same pattern: add PCT endpoints → update BFF client → refactor BFF controller.

### 3.3 Vitals (Class B — observation recording)

Same pattern. High-frequency writes during encounters.

### 3.4 Immunizations (Class A — vaccine safety)

Same pattern. Includes dose tracking, series management, lot/expiry.

### 3.5 Clinical Notes (Class B — SOAP documentation)

Same pattern. SOAP structure preserved in PCT.

### 3.6 Early Warning Scores (Class A — escalation trigger)

Same pattern. NEWS2 scoring with escalation_required flag.

**Wave 1 exit criteria**:
- All 6 clinical content types write to PCT, not BFF
- BFF tables still exist as read cache (populated from PCT response)
- BUTANO receives events and creates FHIR resources
- UI works identically from clinician perspective

---

## 4. Wave 2 — Encounter/Queue/Triage Consolidation

**Goal**: Ensure the BFF's encounter and queue management goes through PCT.

PCT already has journey/encounter/queue entities. The BFF has its own parallel tables with
bridge columns (`pct_journey_id`, `pct_encounter_ref`). This wave ensures writes go to PCT
and the BFF reads from PCT.

| Table | Action |
|-------|--------|
| encounters | Refactor BFF encounter creation to call PCT → PCT returns encounter_id → BFF caches |
| queue_entries | Refactor BFF queue operations to call PCT → PCT manages queue state |
| triage_records | Refactor BFF triage recording to call PCT |
| referrals | Add referral API to PCT → refactor BFF referral logic |

**Wave 2 exit criteria**:
- All encounter/queue/triage writes go through PCT
- BFF encounter bridge columns (pct_journey_id etc.) are now the primary references

---

## 5. Wave 3 — Orders/Prescriptions (OROS/Pharmacy Coordination)

**Goal**: PCT becomes the coordinator for orders and prescriptions.

| Table | Action |
|-------|--------|
| lab_orders | BFF → PCT → OROS (PCT tracks order lifecycle) |
| lab_order_results | OROS → PCT (event) → BFF reads from PCT |
| prescriptions | BFF → PCT → Pharmacy (PCT tracks dispensing lifecycle) |

**Wave 3 exit criteria**:
- Order placement flows through PCT → OROS
- Result notifications flow from OROS → PCT → BFF
- Prescription flows through PCT → Pharmacy
- BUTANO receives completed results as DiagnosticReport, dispensed meds as MedicationDispense

---

## 6. Wave 4 — Inpatient/Emergency/Maternity

**Goal**: Move inpatient, emergency, and maternity workflow state to PCT.

| Table Group | Action |
|-------------|--------|
| admissions, beds, wards, transfers | Add pct_inpatient API → refactor BFF |
| emergency_activations, emergency_actions, resuscitation_*, cpr_cycles | Add emergency protocol API to PCT |
| maternity_*, ctg_*, labour_monitoring_entries | Add maternity monitoring API to PCT |
| ward_charts, ward_chart_entries, fluid_balance_records | Add charting API to PCT |
| discharge_clearances | Add discharge workflow API to PCT |

**Wave 4 exit criteria**:
- All inpatient/emergency/maternity writes go through PCT
- BUTANO receives summary FHIR resources when workflows close (Procedure for resuscitation, Observation for maternity outcomes)

---

## 7. Wave 5 — Operational Data (TUSO, VITO, Inventory)

**Goal**: Move operational data to its sovereign owner.

| Table Group | Target | Action |
|-------------|--------|--------|
| facilities, workspaces | TUSO | BFF → TUSO API; BFF retains read cache |
| shifts, on_call_* | TUSO | BFF → TUSO shift/resource API |
| appointments | TUSO | BFF → TUSO booking API (bridge exists) |
| patients | VITO | BFF → VITO API; BFF retains read cache |
| inventory_*, stock_* | Inventory Service | BFF → Inventory API |
| coverage_plans | Coverage Service | BFF → Coverage API |
| marketplace_*, service_requests | MSIKA-Flow | BFF → MSIKA-Flow API |

**Wave 5 exit criteria**:
- Operational reads/writes proxy to sovereign services
- BFF retains read caches that are populated from API responses

---

## 8. Wave 6 — Communications & Omnichannel

**Goal**: Move messaging and omnichannel data to Channels/Notification services.

| Table Group | Target | Action |
|-------------|--------|--------|
| conversations, messages, message_channels | Channels | BFF → Channels API |
| clinical_messages, clinical_pages | Channels | BFF → Channels API (high priority) |
| omni_* (6 tables) | Channels | BFF → Channels API |
| telemedicine_sessions, citizen_telehealth_sessions | Channels | BFF → Channels virtual session API |
| announcements, announcement_acknowledgments | Notification | BFF → Notification API |
| reminders | Notification | BFF → Notification API |

**Wave 6 exit criteria**:
- All messaging/omnichannel writes go to Channels or Notification service
- BFF retains no messaging tables

---

## 9. Wave 7 — Cleanup & Table Removal

**Goal**: Remove BFF tables that are now fully served by sovereign services.

For each table:
1. Verify no BFF code reads from the local table (all reads go through service client)
2. Add deprecation marker (rename table to `_deprecated_{table}`)
3. After verification period (1 sprint), drop the table via Flyway migration

**Wave 7 exit criteria**:
- BFF has ~26 tables (wellness, social, infrastructure)
- All clinical, operational, and communication data lives in sovereign services
- BFF is a thin proxy + experience-native wellness/social store

---

## 10. Timeline Estimate

| Wave | Scope | Dependency | Relative Size |
|------|-------|------------|---------------|
| 0 | PCT schema foundation | None | Medium |
| 1 | Clinical content (6 types) | Wave 0 | Large |
| 2 | Encounter/queue/triage | Wave 0 | Medium |
| 3 | Orders/prescriptions | Waves 1-2 | Medium |
| 4 | Inpatient/emergency/maternity | Wave 0 | Large |
| 5 | Operational (TUSO, VITO, etc.) | None (parallel with 1-4) | Medium |
| 6 | Communications/omnichannel | None (parallel with 1-4) | Medium |
| 7 | Cleanup/table removal | All above | Small |

Waves 1-4 are sequential (clinical foundation must precede orchestration).
Waves 5-6 can run in parallel with waves 1-4 (different services, different tables).
Wave 7 runs last after verification.

---

## 11. Risk Mitigations

| Risk | Mitigation |
|------|-----------|
| PCT API not ready for BFF volume | Load test PCT with expected BFF traffic before cutover |
| Data inconsistency during dual-write | Idempotency keys ensure no duplicates; outbox pattern ensures no lost events |
| UI regression | Each wave includes UI verification; no table dropped until reads also migrate |
| BUTANO event lag | Bounded staleness monitoring (Law 5); Class A checks go to PCT directly |
| Offline mode breaks | Class C entitlements continue to work against PCT's local store |
| Performance regression | Redis cache for hot paths; PCT connection pooling sized for BFF load |

---

## 12. Success Metrics

| Metric | Target |
|--------|--------|
| BFF business tables | ≤ 30 (from 123) |
| Clinical writes to PCT | 100% |
| BUTANO FHIR resources created from PCT events | All clinical content types |
| UI regressions | Zero |
| p99 latency for clinical writes | ≤ 200ms (PCT) vs current ≤ 150ms (BFF local) |
| Event publication lag (PCT → BUTANO) | ≤ 5 seconds |
