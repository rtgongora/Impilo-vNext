# Lovable Fidelity Gap List: EHR & Telemedicine

> **Date**: 2026-03-16
> **Source**: `docs/clinical/lovable-fidelity-audit-ehr-and-telemedicine.md`
> **Branch**: `claude/review-project-manifest-jb5O0`

---

## Critical Gaps (Must Fix for Lovable Fidelity)

### GAP-01: TopBar Component Missing from EHR Layout

| Field | Detail |
|-------|--------|
| **Lovable Ref** | `docs/prototype/final/03_component_inventory.md` line 56 — "TopBar" listed as 1 of 6 layout components |
| **Current State** | `ui/experience/src/components/EHRLayout.tsx` — 27 lines, static header with "Patient Chart" / "EHR View" labels only |
| **Impact** | No operational context bar during clinical encounters; no quick access to Pharmacy, Payments, Shift Handoff |
| **Fix** | Create `TopBar.tsx` component with contextual operational items; integrate into EHRLayout |
| **Files to Create** | `ui/experience/src/components/TopBar.tsx` |
| **Files to Modify** | `ui/experience/src/components/EHRLayout.tsx` |
| **Severity** | CRITICAL |

---

### GAP-02: EncounterMenu Component Missing from EHR Layout

| Field | Detail |
|-------|--------|
| **Lovable Ref** | `docs/prototype/final/03_component_inventory.md` line 56 — "EncounterMenu" listed as 1 of 6 layout components |
| **Current State** | No EncounterMenu component exists. EHR narrow nav (w-16) has only a static "EHR" badge. |
| **Impact** | No persistent clinical navigation between EHR sub-pages; users must return to patient chart to navigate |
| **Fix** | Create `EncounterMenu.tsx` with grouped clinical sections; integrate into EHRLayout as sidebar |
| **Files to Create** | `ui/experience/src/components/EncounterMenu.tsx` |
| **Files to Modify** | `ui/experience/src/components/EHRLayout.tsx` |
| **Severity** | CRITICAL |

---

### GAP-03: Telemedicine Hub Absent (Web)

| Field | Detail |
|-------|--------|
| **Lovable Ref** | Implied by telemedicine workflow; mobile apps have full telemedicine screens |
| **Current State** | No `/telemedicine` route, no TelemedicineHub page, no web UI for telemedicine |
| **Impact** | Telemedicine is mobile-only; web clinicians cannot manage teleconsults |
| **Fix** | Create `/telemedicine` zone with hub page, session list, actions, and outcome views |
| **Files to Create** | `ui/experience/src/app/telemedicine/page.tsx`, `ui/experience/src/app/telemedicine/sessions/page.tsx` |
| **Files to Modify** | `ui/experience/src/lib/routes.ts`, `ui/experience/src/components/ZoneNavigation.tsx` |
| **Backend** | Create `TelemedicineController.java` (web BFF) or adapt mobile controller |
| **Severity** | CRITICAL |

---

### GAP-04: No Bidirectional Teleconsult Workflow (Receive/Action/Respond)

| Field | Detail |
|-------|--------|
| **Lovable Ref** | Bidirectional telemedicine implied by cross-facility workflow |
| **Current State** | Backend: `MobileTelemedicineController.java` has join/end only. No accept/reject/outcome endpoints. No facility-scoped queries. |
| **Impact** | Receiving facility cannot see, accept, reject, or respond to incoming teleconsults |
| **Fix** | Add accept/reject/reschedule/outcome endpoints; add facility-scoped queries; add notification events |
| **Files to Modify** | `MobileTelemedicineController.java` or new `TelemedicineController.java` |
| **DB Migration** | Add `receiving_facility_id`, `outcome_*` columns to `telemedicine_sessions` |
| **Events** | Add `telemedicine.requested.v1`, `telemedicine.accepted.v1`, `telemedicine.outcome.v1` |
| **Severity** | CRITICAL |

---

## High Gaps (Required for Feature Completeness)

### GAP-05: Consultations Tab/Workflow Missing

| Field | Detail |
|-------|--------|
| **Lovable Ref** | Clinical workflow expectation: "Consults & Referrals" section in EncounterMenu |
| **Current State** | Only `/ehr/[patientId]/referrals` page exists; no consultation-specific workflow |
| **Impact** | Cannot request a consultation from another provider/specialty within the EHR |
| **Fix** | Add Consultations tab or sub-route with consultation request form, status tracking, and notes |
| **Files to Create** | Consultation components within referrals page or new route |
| **Backend** | New consultation-specific endpoints or extend referral controller |
| **Severity** | HIGH |

---

### GAP-06: Teleconsults Tab/Workflow Missing from EHR

| Field | Detail |
|-------|--------|
| **Lovable Ref** | Clinical encounter should support teleconsult initiation |
| **Current State** | No teleconsults tab or section within `/ehr/[patientId]/referrals`; TELEHEALTH encounter type exists but no teleconsult workflow |
| **Impact** | Cannot initiate, schedule, or manage teleconsults from within a patient's EHR |
| **Fix** | Add Teleconsults tab within referrals page with Quick Connect, Schedule, and history |
| **Files to Modify** | `ui/experience/src/app/ehr/[patientId]/referrals/page.tsx` |
| **Severity** | HIGH |

---

### GAP-07: Quick Connect for Teleconsult Missing

| Field | Detail |
|-------|--------|
| **Current State** | No Quick Connect UI or backend endpoint for ad-hoc teleconsult creation on web |
| **Fix** | Add Quick Connect component + backend session creation endpoint |
| **Severity** | HIGH |

---

### GAP-08: Schedule Teleconsult Missing (Web)

| Field | Detail |
|-------|--------|
| **Current State** | `telemedicine_sessions` table supports `scheduled_at` but no web UI for scheduling |
| **Fix** | Add scheduling form in Teleconsults tab; create web BFF endpoint |
| **Severity** | HIGH |

---

### GAP-09: Teleconsult Submission Flow Missing (Web)

| Field | Detail |
|-------|--------|
| **Current State** | No end-to-end teleconsult submission flow exists in the web EHR |
| **Fix** | Create teleconsult submission flow: select patient → choose provider/facility → pick mode → attach clinical data → submit |
| **Severity** | HIGH |

---

## Medium Gaps (Enhancement for Full Fidelity)

### GAP-10: Referral Package Does Not Include Clinical Attachments

| Field | Detail |
|-------|--------|
| **Current State** | Referral form has text fields only; no ability to attach vitals, labs, documents |
| **Fix** | Add clinical data selection to referral form; store attachments with referral |
| **Files to Modify** | `ui/experience/src/app/ehr/[patientId]/referrals/page.tsx` |
| **Severity** | MEDIUM |

---

### GAP-11: Teleconsultation Modes Not Displayed

| Field | Detail |
|-------|--------|
| **Current State** | `session_type` column exists but not exposed in any web UI |
| **Fix** | Show available modes (Video, Audio, Chat) in teleconsult forms |
| **Severity** | MEDIUM |

---

### GAP-12: Patient Landing Page Lacks Encounter-Aware Behavior

| Field | Detail |
|-------|--------|
| **Current State** | `/ehr/[patientId]` always shows chart section grid; no special handling for active encounters |
| **Fix** | When active encounter exists, show encounter-first view or auto-route to encounter |
| **Severity** | MEDIUM |

---

### GAP-13: Queue "In Service" Status Not Visually Differentiated

| Field | Detail |
|-------|--------|
| **Current State** | Queue page exists but "In Service" state not visually distinct |
| **Fix** | Add visual indicator for patients currently in service |
| **Severity** | MEDIUM |

---

## Summary Counts

| Severity | Count |
|----------|-------|
| CRITICAL | 4 |
| HIGH | 5 |
| MEDIUM | 4 |
| **Total** | **13** |

---

## Prioritized Fix Order

1. **GAP-01 + GAP-02** (TopBar + EncounterMenu) — Restores EHR layout to Lovable spec
2. **GAP-03** (Telemedicine Hub) — Creates the platform-level telemedicine page
3. **GAP-04** (Bidirectional Teleconsult Backend) — Enables cross-facility teleconsult workflow
4. **GAP-05 + GAP-06** (Consultations + Teleconsults tabs) — Completes clinical workflow tabs
5. **GAP-07 + GAP-08 + GAP-09** (Quick Connect + Schedule + Submit) — Teleconsult E2E flow
6. **GAP-10 + GAP-11 + GAP-12 + GAP-13** (Medium enhancements) — Polish and completeness
