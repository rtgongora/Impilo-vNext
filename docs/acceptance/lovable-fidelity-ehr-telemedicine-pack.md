# Lovable Fidelity Acceptance Pack: EHR Encounter Shell & Telemedicine

> **Date**: 2026-03-16
> **Version**: 1.0
> **Branch**: `claude/review-project-manifest-jb5O0`
> **Pre-requisite**: `docs/clinical/lovable-fidelity-audit-ehr-and-telemedicine.md`

---

## Purpose

This acceptance pack defines the criteria that must be met before the EHR encounter shell and telemedicine workflows can be declared as having **strong Lovable fidelity**. Each criterion maps to a gap from the audit.

---

## Acceptance Criteria

### AC-1: Encounter Shell Structure

| # | Criterion | Maps To | Pass Condition |
|---|-----------|---------|----------------|
| 1.1 | TopBar component exists | GAP-01 | `ui/experience/src/components/TopBar.tsx` exists and renders operational items |
| 1.2 | TopBar is integrated into EHRLayout | GAP-01 | `EHRLayout.tsx` imports and renders TopBar between header and main content |
| 1.3 | TopBar shows patient-context-aware links | GAP-01 | At minimum: Pharmacy, Payments, Shift Handoff, Orders, Referrals links rendered |
| 1.4 | EncounterMenu component exists | GAP-02 | `ui/experience/src/components/EncounterMenu.tsx` exists with grouped clinical sections |
| 1.5 | EncounterMenu is persistent across all /ehr/* pages | GAP-02 | Navigating between /ehr/[id]/vitals, /ehr/[id]/notes, etc. keeps EncounterMenu visible |
| 1.6 | EncounterMenu groups sections logically | GAP-02 | Sections grouped as: Overview, Assessment, Problems & Diagnoses, Care & Management, Consults & Referrals |
| 1.7 | EncounterMenu highlights active section | GAP-02 | Current page is visually highlighted in the menu |
| 1.8 | Patient landing page is encounter-aware | GAP-12 | When active encounter exists, landing page shows encounter-first view or prominent call-to-action |
| 1.9 | Queue shows "In Service" status | GAP-13 | Patients actively in an encounter are visually differentiated on the queue page |

**Layout component count check**: After fixes, EHRLayout must compose all 6 layout components per Lovable spec:
- [x] AppLayout (exists)
- [x] AppSidebar / ZoneNavigation (exists)
- [x] AppHeader (exists in AppLayout)
- [x] EHRLayout (exists, needs expansion)
- [ ] TopBar (GAP-01)
- [ ] EncounterMenu (GAP-02)

---

### AC-2: Consult / Referral / Teleconsult Workflows

| # | Criterion | Maps To | Pass Condition |
|---|-----------|---------|----------------|
| 2.1 | Tabbed interface in referrals section | GAP-05, GAP-06 | `/ehr/[patientId]/referrals` page shows tabs: Consultations, Referrals, Teleconsults |
| 2.2 | Consultations tab functional | GAP-05 | Can create a consultation request with specialty, provider, reason; view consultation status |
| 2.3 | Referrals tab functional | Existing | Current referral create/list functionality preserved (already works) |
| 2.4 | Referral package includes clinical data | GAP-10 | Can attach vitals, lab results, conditions to a referral before submission |
| 2.5 | Referral package preview | GAP-10 | Preview/review step shown before final referral submission |
| 2.6 | Teleconsults tab functional | GAP-06 | Tab shows teleconsult history for the patient and options to initiate |
| 2.7 | Quick Connect option | GAP-07 | Button to start an ad-hoc teleconsult with an available specialist |
| 2.8 | Schedule Teleconsult option | GAP-08 | Form to schedule a future teleconsult with date, time, provider, mode |
| 2.9 | Teleconsultation modes displayed | GAP-11 | Available modes (Video, Audio, Chat) shown during teleconsult creation |
| 2.10 | Teleconsult submission succeeds E2E | GAP-09 | User can fill form → select mode → attach data → submit → see confirmation |

---

### AC-3: Telemedicine Hub (Platform Level)

| # | Criterion | Maps To | Pass Condition |
|---|-----------|---------|----------------|
| 3.1 | Telemedicine Hub route exists | GAP-03 | `/telemedicine` route in `routes.ts` with layout `app` |
| 3.2 | Hub page renders | GAP-03 | `ui/experience/src/app/telemedicine/page.tsx` renders dashboard with session lists |
| 3.3 | Hub in navigation | GAP-03 | Telemedicine link appears in ZoneNavigation sidebar (Work zone) |
| 3.4 | Incoming teleconsults visible | GAP-03, GAP-04 | Hub shows incoming/pending teleconsult requests for the current facility |
| 3.5 | Receiving facility sees teleconsults | GAP-04 | Sessions filtered by `receiving_facility_id` display correctly |
| 3.6 | Accept action works | GAP-04 | Receiving facility can accept an incoming teleconsult; status changes to ACCEPTED |
| 3.7 | Reject action works | GAP-04 | Receiving facility can reject; status changes to REJECTED with reason |
| 3.8 | Reschedule action works | GAP-04 | Receiving facility can propose new time; status changes to RESCHEDULED |
| 3.9 | Join session works | Existing backend | Provider can join an ACCEPTED/SCHEDULED session; status changes to IN_PROGRESS |
| 3.10 | End session with outcome works | GAP-04 | Session ends with structured outcome (diagnosis, recommendations, follow-up) |
| 3.11 | Referring facility sees outcome | GAP-04 | Referring facility can view completed teleconsult outcomes in EHR and Hub |
| 3.12 | Real-time notification | GAP-04 | Kafka events trigger notifications for teleconsult lifecycle changes |

---

## Backend Acceptance Criteria

| # | Criterion | Pass Condition |
|---|-----------|----------------|
| B.1 | Web telemedicine controller | `TelemedicineController.java` exists with web-facing endpoints (non-mobile path) |
| B.2 | Session creation endpoint | `POST /internal/v1/telemedicine/sessions` creates a session |
| B.3 | Facility-scoped queries | `GET /internal/v1/telemedicine/sessions?facility_id=xxx` returns facility-specific sessions |
| B.4 | Accept/Reject endpoints | `POST /sessions/{id}/accept`, `POST /sessions/{id}/reject` work |
| B.5 | Outcome endpoint | `POST /sessions/{id}/outcome` stores structured outcome |
| B.6 | Consultation endpoints | Consultation request CRUD endpoints exist |
| B.7 | DB migration | New columns/tables for outcome, receiving facility, consultation requests |
| B.8 | Outbox events | Events: `telemedicine.requested.v1`, `telemedicine.accepted.v1`, `telemedicine.rejected.v1`, `telemedicine.outcome.v1` |

---

## Current State vs. Required State

### What EXISTS Today

| Component | Location | Status |
|-----------|----------|--------|
| EHRLayout (minimal) | `ui/experience/src/components/EHRLayout.tsx` | 27-line shell, no TopBar/EncounterMenu |
| Patient Chart landing | `ui/experience/src/app/ehr/[patientId]/page.tsx` | Grid of section links, patient summary |
| Referrals page | `ui/experience/src/app/ehr/[patientId]/referrals/page.tsx` | Create/list referrals (no tabs) |
| Encounter page | `ui/experience/src/app/ehr/[patientId]/encounter/[encounterId]/page.tsx` | Vitals, notes, close encounter |
| 17 EHR sub-pages | `ui/experience/src/app/ehr/[patientId]/*` | All implemented with data fetching |
| Mobile telemedicine | `apps/mobile/*/screens/*/Telemedicine*.tsx` | Provider + citizen screens |
| Backend telemedicine | `MobileTelemedicineController.java` | List/join/end sessions (mobile only) |
| Telemedicine DB | `V5__citizen_app_tables.sql` | `telemedicine_sessions` table |
| Outbox events | `OutboxService.java` | `telemedicine.joined.v1`, `telemedicine.ended.v1` |
| EHR store (ui/ehr) | `ui/ehr/src/stores/ehrStore.ts` | Separate EHR app with Zustand store |

### What is MISSING

| Component | Required For | Priority |
|-----------|-------------|----------|
| TopBar.tsx | AC-1.1–1.3 | P0 |
| EncounterMenu.tsx | AC-1.4–1.7 | P0 |
| Telemedicine Hub page | AC-3.1–3.4 | P0 |
| Consultations tab/workflow | AC-2.1–2.2 | P1 |
| Teleconsults tab/workflow | AC-2.6–2.10 | P1 |
| Web TelemedicineController.java | B.1–B.5 | P1 |
| Bidirectional teleconsult lifecycle | AC-3.5–3.12 | P1 |
| Clinical attachments on referrals | AC-2.4–2.5 | P2 |
| Teleconsultation mode selection | AC-2.9 | P2 |
| Encounter-aware landing page | AC-1.8 | P2 |
| Queue "In Service" visual | AC-1.9 | P2 |

---

## Smoke Test Checklist (Post-Implementation)

### Shell Structure

- [ ] Open `/queue`, see patient list with "In Service" indicators
- [ ] Click patient → navigate to `/ehr/[patientId]`
- [ ] See TopBar with operational links (Pharmacy, Payments, Shift Handoff)
- [ ] See EncounterMenu on right/left side with clinical sections
- [ ] Click "Vitals" in EncounterMenu → navigate to `/ehr/[patientId]/vitals`; menu stays visible
- [ ] Click "Referrals" in EncounterMenu → navigate to `/ehr/[patientId]/referrals`; menu stays visible
- [ ] Current section is highlighted in EncounterMenu

### Consults & Referrals

- [ ] On referrals page, see tabs: Consultations, Referrals, Teleconsults
- [ ] Create a consultation request → submit → see in list
- [ ] Create a referral with clinical attachments → preview → submit
- [ ] Switch to Teleconsults tab → see teleconsult history
- [ ] Click Quick Connect → create ad-hoc teleconsult → submit
- [ ] Click Schedule Teleconsult → fill form with mode selection → submit
- [ ] Verify teleconsult appears in Telemedicine Hub for receiving facility

### Telemedicine Hub

- [ ] Navigate to `/telemedicine` from sidebar
- [ ] See dashboard with incoming, scheduled, active, completed sessions
- [ ] Receiving facility: see incoming teleconsult request
- [ ] Accept teleconsult → status changes to ACCEPTED
- [ ] Join session → status changes to IN_PROGRESS
- [ ] End session with outcome → status changes to COMPLETED
- [ ] Referring facility: see completed outcome in EHR referrals and Hub

---

## Sign-Off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Clinical Lead | | | |
| Technical Lead | | | |
| Product Owner | | | |

---

## References

- `docs/clinical/lovable-fidelity-audit-ehr-and-telemedicine.md` — Full audit with code evidence
- `docs/clinical/lovable-fidelity-gap-list.md` — Prioritized gap list (13 gaps)
- `docs/prototype/final/03_component_inventory.md` — Lovable component inventory (6 layout components)
- `docs/prototype/final/01_site_map.md` — Zone and route mapping
- `docs/prototype/final/06_golden_paths.md` — Golden path C (Queue → Encounter → Close)
- `docs/clinical/lovable-reference-usage-report.md` — Prior Lovable traceability report
