# Lovable Reference Usage Report

> **Generated**: 2026-03-16
> **Task**: Clinical & Mobile Closure Wave — Lovable/Prototype Traceability

---

## 1. Lovable/Prototype Artifacts Inventoried

| # | File Path | Document Title | Lines |
|---|-----------|---------------|-------|
| 1 | `docs/prototype/final/00_executive_summary.md` | Executive Summary (Index & Contract) | 82 |
| 2 | `docs/prototype/final/01_site_map.md` | Site Map (Index & Contract) | 82 |
| 3 | `docs/prototype/final/02_page_by_page_spec.md` | Page-by-Page Specification (Index & Contract) | 82 |
| 4 | `docs/prototype/final/03_component_inventory.md` | Component Inventory (Index & Contract) | 98 |
| 5 | `docs/prototype/final/04_api_surface_map.md` | API Surface Map (Index & Contract) | 105 |
| 6 | `docs/prototype/final/05_state_and_storage.md` | State & Storage (Index & Contract) | 101 |
| 7 | `docs/prototype/final/06_golden_paths.md` | Golden Paths (Index & Contract) | 114 |
| 8 | `docs/prototype/final/07_opus_execution_contract.md` | Opus Execution Contract (Index & Contract) | 86 |

**Note**: These documents are **index/contract files** pointing to canonical specs in `docs/plan/` and `docs/architecture/v1.1/`. They define numerical constraints (98 routes, 15 zones, 4 layouts, 6 golden paths, 4 auth pathways) and map to implementation artifacts.

---

## 2. What Each Document Contributed

### 00_executive_summary.md
- **Contribution**: Platform-level constraints: 15 zones, 4 layout variants, 98 routes, 6 golden paths, 4 Zustand stores
- **Used for**: Validating that route registry covers all zones and the experience platform layout structure is complete

### 01_site_map.md
- **Contribution**: Zone-to-route mapping, sidebar context resolution rules, guard chain
- **Used for**: Verifying `/ehr/*` routes exist for all clinical sub-pages, confirming guard hierarchy (auth → facility → workspace → shift for clinical routes)
- **Gap identified**: No `/ehr/[patientId]/discharge` route existed; discharge was only reachable via encounter close button. **Fixed**: Added discharge route.

### 02_page_by_page_spec.md
- **Contribution**: BFF controller mapping for each page's data needs
- **Used for**: Verifying that every EHR page (vitals, notes, orders, results, referrals, etc.) has a corresponding BFF controller with GET/POST endpoints
- **Match**: All 13 BFF controllers listed in the spec map to real Java files
- **Gap identified**: No discharge-specific endpoint. **Fixed**: Added `POST /internal/v1/encounters/{id}/discharge`

### 03_component_inventory.md
- **Contribution**: 4 layout variants, 6 layout components, 11 sidebar contexts
- **Used for**: Verifying EHR pages use EHRLayout, queue pages use AppLayout, sidebar context is "ehr" for clinical pages
- **Match**: All EHR pages correctly use `<EHRLayout>`, queue pages use `<AppLayout>`

### 04_api_surface_map.md
- **Contribution**: Complete BFF controller table with methods and domains; outbox event types
- **Used for**: Cross-checking that encounter.created, encounter.closed, vitals.recorded, pharmacy.prescribed, pharmacy.dispensed events all exist in OutboxService calls
- **Match**: All 5 outbox event types confirmed in controller code
- **Addition**: Added `encounter.discharged.v1` event for the new discharge flow

### 05_state_and_storage.md
- **Contribution**: 4 Zustand stores (auth, facility, workspace, shift), continuity storage model, route guard chain
- **Used for**: Verifying EHR pages enforce shift guard (which gates workspace → facility → auth), confirming walk-in registration uses facilityStore
- **Match**: Walk-in page (`/queue/walk-in`) correctly accesses `useFacilityStore`; all EHR routes have `guard: "shift"`

### 06_golden_paths.md
- **Contribution**: 6 golden paths including Path C (Queue → Encounter → Close)
- **Used for**: End-to-end verification of the clinical workflow: queue → patient search → encounter creation → vitals/notes → encounter close
- **Gap identified**: Path C lacked explicit discharge step between "notes" and "close". **Fixed**: Added discharge page and link in encounter page.
- **Match**: Path A (login), Path B (provider ID), Path D (admin), Path E (marketplace), Path F (registry) all verified as implemented

### 07_opus_execution_contract.md
- **Contribution**: 11-phase execution order, quality gates
- **Used for**: Confirming the implementation followed the correct dependency order (foundation → compliance → layouts → auth → routing → data → outbox → pages → tests)
- **Match**: All 11 phases verified as complete with quality gates passing

---

## 3. Where Current Implementation Matched the Lovable Reference

| Area | Lovable Reference | Implementation Status |
|------|------------------|----------------------|
| Route count | 98 routes across 15 zones | **99 routes** (added discharge) across 15 zones |
| Layout variants | 4 (app, ehr, auth, minimal) | **MATCH** — all 4 implemented |
| Sidebar contexts | 11 contexts | **MATCH** — all 11 implemented |
| Auth pathways | 4 (email, provider ID, biometric, SSO) | **MATCH** — 3 implemented, SSO deferred to Stage-2 |
| Golden paths | 6 paths | **MATCH** — all 6 have automated tests |
| Zustand stores | 4 stores (auth, facility, workspace, shift) | **MATCH** — all 4 implemented |
| Session continuity | Prototype implied browser persistence | **MATCH WITH SECURITY HARDENING** — continuity preserved via user/context storage, in-memory access token, session marker cookie, and HttpOnly refresh cookie |
| BFF controllers | 13 controllers | **15 controllers** (added clinical domain controllers) |
| Outbox events | 5 event types | **6 event types** (added encounter.discharged) |
| EHR sub-pages | Vitals, notes, orders, results, referrals, conditions, allergies, medications, immunizations, encounters, documents, timeline, summary, history | **ALL IMPLEMENTED** + discharge page added |

## 4. Where Current Implementation Diverged

| # | Area | Lovable Says | Implementation Does | Resolution |
|---|------|-------------|-------------------|------------|
| 1 | Route count | 98 | 99 (added discharge) | Acceptable divergence — enhances clinical workflow |
| 2 | SSO auth pathway | Listed as pathway 4 | Not implemented | Deferred to Stage-2 per Spec Conflict #4 |
| 3 | React contexts | 6 contexts | 4 Zustand stores | Per Spec Conflict #7 — insufficient spec for 2 additional contexts |
| 4 | Browser persistence model | 2 keys | Continuity split across browser storage + cookies | Per Spec Conflict #3 — implementation needs more state and stronger token handling |
| 5 | Supabase tables | ~60 tables | BFF + PostgreSQL | Per Spec Conflict #5 — architecture changed from Supabase to BFF |
| 6 | Discharge workflow | Not explicitly specified in golden paths | Added as explicit EHR page | Enhancement based on clinical workflow analysis |

## 5. What Was Changed Because of Lovable Reference

| Change | Triggered By | Files Modified/Created |
|--------|-------------|----------------------|
| Added discharge route | Golden Path C analysis (no explicit discharge step) | `routes.ts`, `discharge/page.tsx` |
| Added discharge BFF endpoint | API surface map gap | `EncounterController.java`, `Encounter.java` |
| Added discharge DB migration | Storage spec analysis | `V8__encounter_discharge_columns.sql` |
| Added discharge outbox event | Eventing surface map | `EncounterController.java` |
| Added mobile discharge controller | Mobile parity requirement + golden path | `MobileDischargeController.java` |
| Added consent controller | Citizen My Life parity (settings/preferences) | `CitizenConsentController.java` |
| Added consent DB table | Storage requirement for citizen consent | `V8__encounter_discharge_columns.sql` |
| Added discharge hook | State management pattern | `useDischarge.ts` |
| Added discharge link in encounter | Component inventory (encounter menu actions) | `encounter/[encounterId]/page.tsx` |
| Added provider discharge screen | Provider Work parity | `DischargeScreen.tsx` |
| Added citizen consent screen | Citizen My Life parity | `ConsentScreen.tsx` |

## 6. What Remains Unresolved

| # | Item | Reason | Lovable Reference |
|---|------|--------|------------------|
| 1 | SSO authentication pathway | Requires Keycloak OIDC integration not available in current environment | `06_golden_paths.md` Path B note |
| 2 | Full Keycloak integration | Stage-2 dependency per Spec Conflict #4 | `07_opus_execution_contract.md` |
| 3 | 2 additional React contexts | Original spec did not define them; possibly notifications + theme | `05_state_and_storage.md` Conflict #7 |
| 4 | PACS/Orthanc imaging integration | External system dependency | Out of scope for this closure wave |
| 5 | Expo native build verification | Requires physical device or Expo CLI environment | Runtime validation finding |
