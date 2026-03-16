# Lovable Fidelity Audit: EHR Encounter Shell & Telemedicine Workflows

> **Date**: 2026-03-16
> **Auditor**: Claude Code (Opus 4.6)
> **Branch**: `claude/review-project-manifest-jb5O0`
> **Scope**: Encounter shell structure, Consult/Referral/Teleconsult workflows, Telemedicine Hub

---

## Methodology

This audit compares the current implementation against the Lovable-derived reference documents in `docs/prototype/final/` (00–07) and their canonical sources. Each question is answered with:

1. Lovable reference evidence
2. Current implementation evidence (exact file paths, line numbers, route/component names)
3. Verdict: PRESENT AND COMPLETE / PRESENT BUT PARTIAL / ABSENT / UNCLEAR / SPEC CONFLICT
4. Gap description
5. Exact fix required

---

## 1. Encounter Shell Fidelity

### 1A. Landing Page When User Selects a Patient from Queue and Opens File Under "In Service"

**Lovable Reference Evidence**:
- `docs/prototype/final/06_golden_paths.md` — Path C (Queue → Encounter → Close): Step 1 is `/queue`, Step 2 is "Select patient from queue", Step 3 is "Start encounter"
- `docs/prototype/final/01_site_map.md` — Zone 7: EHR routes under `/ehr/*` with layout `ehr`
- `docs/prototype/final/03_component_inventory.md` — EHRLayout: "Narrow nav | TopBar + EncounterMenu" for clinical encounter pages
- Route registry: `/ehr/[patientId]` — pageTitle: "Patient Chart"

**Current Implementation Evidence**:
- `ui/experience/src/app/ehr/[patientId]/page.tsx` — Patient Chart page with:
  - Patient summary card (name, DOB, gender, CPID) (lines 80–104)
  - Grid of chart section links: Vitals, Conditions, Medications, Allergies, Orders, Results, Notes, Immunizations, Encounters, Timeline (lines 107–127)
  - Recent encounters list (lines 130–161)
  - "Active Encounter" button linking to `/ehr/{patientId}/encounter/{encounterId}` (lines 95–101)
- `ui/experience/src/app/queue/page.tsx` exists for queue management
- `ui/experience/src/lib/routes.ts:80` — Route `/ehr/[patientId]` defined with layout `ehr`, sidebar `ehr`, guard `shift`

**Verdict**: **PRESENT BUT PARTIAL**

**Gap**: The patient chart page does exist as a landing page when navigating from queue to patient file. It shows a patient summary and navigation grid. However:
1. No concept of "In Service" status is visually differentiated — the queue page does not distinguish between waiting/triaged/in-service patients in its current UI
2. The landing page is a flat grid of links, not a structured encounter shell with operational context
3. The patient chart page does not automatically open or highlight the active encounter — the "Active Encounter" button is available but not prominent as a primary workflow entry point

**Fix Required**:
1. Add "In Service" visual state on queue page to indicate patient is being seen
2. When navigating from queue to patient with an active encounter, consider auto-routing to the encounter page or presenting the encounter shell prominently
3. Add encounter-aware landing page behavior: if active encounter exists, show encounter-first view rather than chart-section grid

---

### 1B. Top Bar Menu with Lovable-style Operational Items

**Lovable Reference Evidence**:
- `docs/prototype/final/03_component_inventory.md` (line 56): EHR layout uses **TopBar + EncounterMenu**
- Layout architecture table: `ehr` layout = `EHRLayout | Narrow nav | TopBar + EncounterMenu | Clinical encounter pages`
- The component inventory lists **6 layout components**: AppLayout, AppSidebar, AppHeader, **EHRLayout**, **TopBar**, **EncounterMenu**
- The Lovable reference implies TopBar contains operational/contextual actions available during an encounter

**Expected items per Lovable pattern** (inferred from component inventory, clinical workflow context, and route registry):
- Pharmacy → `/pharmacy` zone exists (routes.ts:141–144)
- Theatre Booking → Not in route registry
- Payments → `/finance/payments` exists (routes.ts:137)
- Shift Handoff → `/shift/handover` exists (routes.ts:69)
- Critical Events → Not in route registry
- Care Pathways → Not in route registry

**Current Implementation Evidence**:
- `ui/experience/src/components/EHRLayout.tsx` — **27 lines total**. The entire component is:
  ```tsx
  <div className="flex h-screen bg-gray-50">
    <nav className="w-16 bg-gray-900 text-white flex flex-col items-center py-4 gap-3 shrink-0">
      <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center text-xs font-bold">EHR</div>
    </nav>
    <div className="flex-1 flex flex-col min-w-0">
      <header className="h-12 border-b bg-white px-4 flex items-center gap-4 shrink-0">
        <span className="text-sm font-medium text-gray-700">Patient Chart</span>
        <div className="flex-1" />
        <span className="text-xs text-gray-500">EHR View</span>
      </header>
      <main className="flex-1 overflow-auto p-4">{children}</main>
    </div>
  </div>
  ```
- **There is NO TopBar component.** The `<header>` element on line 19 is a static label ("Patient Chart" / "EHR View") with zero operational menu items.
- No component file named `TopBar.tsx` exists anywhere in the codebase.
- The encounter page (`ui/experience/src/app/ehr/[patientId]/encounter/[encounterId]/page.tsx`) has quick action links (Orders, Rx, Referral, Discharge) on lines 210–236, but these are **inside the page content**, not in a persistent TopBar.

**Verdict**: **ABSENT**

**Gap**: The Lovable component inventory explicitly specifies a **TopBar** component as part of the EHR layout. The current EHRLayout has only a static header with no interactive operational items. The 6-component layout architecture (`AppLayout, AppSidebar, AppHeader, EHRLayout, TopBar, EncounterMenu`) is missing 2 of its 6 components: TopBar and EncounterMenu.

**Fix Required**:
1. Create `ui/experience/src/components/TopBar.tsx` with operational action items
2. Minimum items based on route registry and clinical workflow:
   - **Pharmacy** → link to `/pharmacy` (shift-gated)
   - **Payments** → link to `/finance/payments` (role-gated)
   - **Shift Handoff** → link to `/shift/handover` (shift-gated)
   - **Orders** → link to `/ehr/{patientId}/orders` (context-aware)
   - **Referrals** → link to `/ehr/{patientId}/referrals` (context-aware)
3. Consider adding: Theatre Booking (if route added), Critical Events, Care Pathways — these need route/backend work first
4. Integrate TopBar into EHRLayout between the header and main content area
5. TopBar should be patient-context-aware (show patient name, CPID, encounter status)

---

### 1C. Right Encounter Menu with Clinical Navigation Items

**Lovable Reference Evidence**:
- `docs/prototype/final/03_component_inventory.md` (line 56): EHR layout includes **EncounterMenu**
- Sidebar context table (line 71): `ehr` context → "Patient chart, encounter tools, clinical forms"
- Component inventory lists EncounterMenu as one of the 6 layout components
- Expected items per clinical workflow context: Overview, Assessment, Problems & Diagnoses, Care & Management, Consults & Referrals

**Actual EHR sub-pages in route registry** (`routes.ts:80–96`):
- `/ehr/[patientId]` — Patient Chart (landing)
- `/ehr/[patientId]/summary` — Patient Summary
- `/ehr/[patientId]/vitals` — Vitals
- `/ehr/[patientId]/history` — Medical History
- `/ehr/[patientId]/conditions` — Conditions
- `/ehr/[patientId]/medications` — Medications
- `/ehr/[patientId]/allergies` — Allergies
- `/ehr/[patientId]/orders` — Orders
- `/ehr/[patientId]/results` — Results
- `/ehr/[patientId]/notes` — Clinical Notes
- `/ehr/[patientId]/documents` — Documents
- `/ehr/[patientId]/encounters` — Encounters
- `/ehr/[patientId]/encounter/[encounterId]` — Active Encounter
- `/ehr/[patientId]/immunizations` — Immunizations
- `/ehr/[patientId]/referrals` — Referrals
- `/ehr/[patientId]/timeline` — Timeline
- `/ehr/[patientId]/discharge` — Discharge

**Current Implementation Evidence**:
- `ui/experience/src/components/EHRLayout.tsx` — **No EncounterMenu component.** The narrow nav (`w-16`) on lines 13–16 contains only a static "EHR" badge. There is no sidebar/right panel with clinical navigation items.
- No component file named `EncounterMenu.tsx` exists anywhere in the codebase.
- The patient chart page (`/ehr/[patientId]/page.tsx`) has a grid of section links (lines 107–127) that functions as a **flat navigation grid**, not a persistent sidebar menu.
- Each EHR sub-page has a "Back to patient chart" breadcrumb link, but no persistent right-side navigation.

**Verdict**: **ABSENT**

**Gap**: The Lovable component inventory explicitly lists **EncounterMenu** as a layout component. The current EHR layout has no encounter menu — only a minimal narrow nav with a static badge. Navigation between EHR sub-pages requires returning to the patient chart landing page first. There is no persistent sidebar showing clinical sections like Overview, Assessment, Problems & Diagnoses, Care & Management, Consults & Referrals.

**Fix Required**:
1. Create `ui/experience/src/components/EncounterMenu.tsx` as a right-side or left-side sidebar
2. Group EHR sub-pages into logical clinical categories:
   - **Overview**: Summary, Timeline
   - **Assessment**: Vitals, Notes, History
   - **Problems & Diagnoses**: Conditions, Allergies
   - **Care & Management**: Medications, Orders, Results, Immunizations, Documents
   - **Consults & Referrals**: Referrals, Encounters
   - **Discharge**: Discharge
3. EncounterMenu must be persistent across all `/ehr/*` pages — not just on the landing page
4. Integrate into EHRLayout as a sidebar alongside the main content area
5. Show active encounter status, patient context, and highlight current section

---

## 2. Consult / Referral / Teleconsult Fidelity

### 2A. Tabs for Consultations, Referrals, Teleconsults

**Lovable Reference Evidence**:
- Route registry has `/ehr/[patientId]/referrals` — a single referrals page
- No separate routes for consultations or teleconsults under `/ehr/*`
- `docs/prototype/final/03_component_inventory.md` — EHR sidebar context: "Patient chart, encounter tools, clinical forms"
- The Lovable reference does not explicitly define tabs for Consultations/Referrals/Teleconsults as separate sub-routes, but the clinical workflow expectation is that "Consults & Referrals" should encompass these modes

**Current Implementation Evidence**:
- `ui/experience/src/app/ehr/[patientId]/referrals/page.tsx` — Single page titled "Referrals" with:
  - Referral list with status/urgency badges (lines 294–358)
  - Create Referral form with types: SPECIALIST, FACILITY, EMERGENCY (line 141–149)
  - Fields: specialty, referred to (provider), referred to (facility), urgency, reason, clinical summary (lines 135–254)
  - **No tabs structure** — the page is a flat list + create form
  - **No "Consultations" tab** — no consultation-specific workflow
  - **No "Teleconsults" tab** — no teleconsult initiation from within the EHR
- Encounter types dropdown in encounters page includes "TELEHEALTH" option (`encounters/page.tsx:139`) but this just creates a TELEHEALTH-typed encounter, not a teleconsult workflow
- No file at `/ehr/[patientId]/consultations/` or `/ehr/[patientId]/teleconsults/` exists

**Verdict**: **ABSENT** (Consultations tab), **ABSENT** (Teleconsults tab), **PRESENT BUT PARTIAL** (Referrals — exists as a page but not as a tab within a multi-tab view)

**Gap**: The referrals page exists as a standalone page but lacks:
1. A tabbed interface separating Consultations, Referrals, and Teleconsults
2. Any consultation-specific workflow (request consultation from another provider/specialty)
3. Any teleconsult workflow from within the EHR encounter context

**Fix Required**:
1. Refactor `/ehr/[patientId]/referrals/page.tsx` into a tabbed layout:
   - Tab 1: **Consultations** — request consultation, view consultation status, receive consultation notes
   - Tab 2: **Referrals** — current referral functionality (already working)
   - Tab 3: **Teleconsults** — initiate teleconsult, view teleconsult history
2. Alternatively, create separate sub-routes: `/ehr/[patientId]/consultations`, `/ehr/[patientId]/teleconsults`
3. Wire up backend endpoints for consultation requests (currently no `ConsultationController` exists in the web BFF layer)

---

### 2B. Teleconsult Functionality Within "Consults & Referrals"

#### Quick Connect Option

**Lovable Reference Evidence**: No explicit Lovable specification for "Quick Connect" found in `docs/prototype/final/`. The concept implies an ad-hoc teleconsult without pre-scheduling.

**Current Implementation Evidence**:
- No Quick Connect UI component exists anywhere in `ui/experience/`
- No Quick Connect route exists in `routes.ts`
- The mobile provider app has `TelemedicineScreen.tsx` (`apps/mobile/provider-app/src/screens/provider/TelemedicineScreen.tsx`) — mobile only
- Backend: `MobileTelemedicineController.java` has session join/end but no "quick connect" flow

**Verdict**: **ABSENT**

**Gap**: No Quick Connect functionality exists in the web EHR. The mobile app has telemedicine session management but no quick connect flow either.

**Fix Required**:
1. Add Quick Connect component within the Teleconsults tab: ability to create an ad-hoc telemedicine session with an available specialist
2. Backend: Add web-facing telemedicine session creation endpoint (currently only mobile endpoints exist at `/internal/v1/mobile/provider/telemedicine/*`)
3. Create web-facing route: e.g., `POST /internal/v1/telemedicine/sessions` for quick connect

---

#### Schedule First Teleconsult Option

**Lovable Reference Evidence**: No explicit specification found. Inferred from the telemedicine workflow expectation.

**Current Implementation Evidence**:
- `telemedicine_sessions` table exists (created in `V5__citizen_app_tables.sql`) with columns: `scheduled_at`, `session_type`, `status`
- `MobileTelemedicineController.java` (lines 46–48): queries `telemedicine_sessions` with `scheduled_at` field
- `CitizenTelehealthController.java` exists for citizen-side session listing and requests
- **No web UI for scheduling a teleconsult** exists in `ui/experience/`

**Verdict**: **ABSENT** (web UI), **PRESENT BUT PARTIAL** (backend — session table and mobile API exist)

**Gap**: Backend supports scheduled telemedicine sessions, but there is no web UI within the EHR to schedule a first teleconsult.

**Fix Required**:
1. Add Schedule Teleconsult form within the Teleconsults tab
2. Create web BFF endpoint: `POST /internal/v1/telemedicine/sessions` (separate from mobile endpoint)
3. Form fields: patient, specialist/provider, session type, scheduled date/time, reason
4. Hook up to existing `telemedicine_sessions` table

---

#### Acceptable Teleconsultation Modes Shown

**Lovable Reference Evidence**: No explicit mode specification found. Backend has `session_type` column.

**Current Implementation Evidence**:
- `telemedicine_sessions.session_type` column exists in DB
- `MobileTelemedicineController.java` returns `session_type` in responses (line 233)
- Citizen app has session type support (`apps/mobile/citizen-app/src/services/telehealthService.ts`)
- **No web UI displays or allows selection of teleconsultation modes**

**Verdict**: **ABSENT** (web UI)

**Gap**: Session types exist in the data model but are not exposed in any web UI.

**Fix Required**: Display available teleconsultation modes (e.g., Video, Audio, Chat) in the Schedule/Quick Connect teleconsult forms.

---

#### Build a Referral Package End-to-End

**Lovable Reference Evidence**: Referral creation exists in the Lovable reference as part of the clinical workflow.

**Current Implementation Evidence**:
- `ui/experience/src/app/ehr/[patientId]/referrals/page.tsx` — Create Referral form with:
  - Referral type (SPECIALIST, FACILITY, EMERGENCY)
  - Specialty, provider, facility, urgency, reason, clinical summary
  - Submit button calls `useCreateReferral` mutation
- `ui/experience/src/hooks/queries/useReferrals.ts` — mutation hook
- Backend: Referral controller exists (implied by the hook calling an endpoint)
- **Missing**: No ability to attach clinical documents, vitals, lab results, or encounter history to the referral package
- **Missing**: No referral package preview/summary before submission
- **Missing**: No status tracking after submission (beyond the list view)

**Verdict**: **PRESENT BUT PARTIAL**

**Gap**: Basic referral creation works (type, specialty, provider, facility, urgency, reason, summary). However:
1. No structured referral package with attached clinical data (vitals, labs, notes, conditions, medications)
2. No package preview/review step before submission
3. No status tracking workflow (accept/decline/complete by receiving facility)

**Fix Required**:
1. Add clinical data attachment to referral form: select vitals, lab results, conditions, medications to include
2. Add referral package preview/summary step before final submission
3. Add referral status tracking page showing the full lifecycle (Pending → Accepted → In Progress → Completed)

---

#### Submit Teleconsult Successfully

**Lovable Reference Evidence**: Teleconsult submission is an expected end-to-end flow.

**Current Implementation Evidence**:
- **No web UI for teleconsult submission exists**
- Backend `MobileTelemedicineController.java` supports session lifecycle: list → join → end
- `CitizenTelehealthController.java` supports citizen-side requests
- No web-facing teleconsult creation, scheduling, or submission endpoint exists

**Verdict**: **ABSENT** (web)

**Gap**: Teleconsult can only be initiated/managed from mobile apps. No web EHR path exists.

**Fix Required**:
1. Create web-facing teleconsult submission flow within EHR
2. Wire to backend telemedicine session creation and management endpoints
3. Include: patient context, reason, requested specialist, preferred mode, attached clinical data

---

## 3. Telemedicine Hub Fidelity

### 3A. Fully Functional Telemedicine Hub at Platform Level

**Lovable Reference Evidence**:
- `docs/prototype/final/01_site_map.md` — No explicit "Telemedicine Hub" zone or route
- Route registry (`routes.ts`) — No `/telemedicine/*` routes exist
- The Lovable reference does not specify a standalone Telemedicine Hub page/zone

**Current Implementation Evidence**:
- No `ui/experience/src/app/telemedicine/` directory exists
- No `/telemedicine` route in `routes.ts`
- No TelemedicineHub component exists in `ui/experience/src/components/`
- Mobile apps have telemedicine screens:
  - `apps/mobile/citizen-app/src/screens/telehealth/TelehealthSessionScreen.tsx`
  - `apps/mobile/citizen-app/src/screens/telehealth/TelehealthListScreen.tsx`
  - `apps/mobile/provider-app/src/screens/provider/TelemedicineScreen.tsx`
- Backend:
  - `MobileTelemedicineController.java` — mobile provider sessions (list/join/end)
  - `CitizenTelehealthController.java` — citizen session requests
  - `telemedicine_sessions` table — SCHEDULED, IN_PROGRESS, COMPLETED statuses
  - Outbox events: `impilo.experience.telemedicine.joined.v1`, `impilo.experience.telemedicine.ended.v1`

**Verdict**: **ABSENT** (web Telemedicine Hub)

**Gap**: No web-based Telemedicine Hub exists. Telemedicine functionality is mobile-only. The backend supports the session lifecycle, but there is no platform-level web page for managing, viewing, or triaging telemedicine sessions.

**Fix Required**:
1. Create zone/route: `/telemedicine` with layout `app`, sidebar context `workspace` or new `telemedicine`
2. Create Telemedicine Hub page at `ui/experience/src/app/telemedicine/page.tsx`
3. Hub should display:
   - Incoming teleconsult requests
   - Scheduled sessions
   - Active/in-progress sessions
   - Completed sessions with outcomes
   - Quick Connect initiation
4. Create web BFF controller: `TelemedicineController.java` (non-mobile) or expose existing mobile endpoints via web-compatible routes
5. Add to route registry and ZoneNavigation

---

### 3B. Receiving Facility Can See Incoming Teleconsults

**Lovable Reference Evidence**: Implied by bidirectional telemedicine workflow.

**Current Implementation Evidence**:
- `MobileTelemedicineController.java:33–96` — `GET /internal/v1/mobile/provider/telemedicine/sessions` supports filtering by `provider_id` and `status`, scoped by `tenant_id`
- `telemedicine_sessions` table has `facility_id` column
- **No facility-scoped query**: The existing API filters by `provider_id` and `status` but not by `facility_id` for the receiving facility
- **No web UI** for incoming teleconsult view
- No notification/event-driven alert for incoming teleconsults to receiving facility

**Verdict**: **ABSENT** (web), **PRESENT BUT PARTIAL** (backend — table structure supports it, API does not expose facility-scoped queries)

**Gap**: The data model supports facility-scoped teleconsult viewing (`facility_id` column), but:
1. No web UI for receiving facility to view incoming teleconsults
2. API does not support filtering by receiving facility
3. No real-time notification when a new teleconsult is requested

**Fix Required**:
1. Add `receiving_facility_id` to `telemedicine_sessions` table (or use existing `facility_id` with clear semantics)
2. Add facility-scoped endpoint: `GET /internal/v1/telemedicine/sessions?receiving_facility_id=xxx`
3. Create incoming teleconsults view in the Telemedicine Hub
4. Add Kafka event for `telemedicine.requested.v1` to drive notifications

---

### 3C. Receiving Facility Can Action Incoming Teleconsults

**Lovable Reference Evidence**: Implied — accept/reject/schedule actions on incoming teleconsults.

**Current Implementation Evidence**:
- `MobileTelemedicineController.java` has `/sessions/{id}/join` (line 98) and `/sessions/{id}/end` (line 167)
- No accept/reject/reschedule endpoints
- Status transitions: SCHEDULED → IN_PROGRESS → COMPLETED (join transitions to IN_PROGRESS)
- **No web UI for actioning** teleconsults
- **No reject/decline flow**

**Verdict**: **ABSENT** (web), **PRESENT BUT PARTIAL** (backend — join/end exist, but no accept/reject)

**Gap**:
1. No web UI for actioning teleconsults
2. No accept/reject workflow — only join (start) and end
3. No ability to reassign to a different provider or reschedule

**Fix Required**:
1. Add action endpoints: `POST /sessions/{id}/accept`, `POST /sessions/{id}/reject`, `POST /sessions/{id}/reschedule`
2. Create web UI action buttons in Telemedicine Hub
3. Add status transitions: REQUESTED → ACCEPTED/REJECTED → SCHEDULED → IN_PROGRESS → COMPLETED

---

### 3D. Consulting/Referring Facility Can Receive Response/Outcome

**Lovable Reference Evidence**: Bidirectional communication expected.

**Current Implementation Evidence**:
- `telemedicine_sessions.notes` column exists for session notes
- `telemedicine_sessions.status` tracks lifecycle
- Outbox events exist for `telemedicine.joined.v1` and `telemedicine.ended.v1` — these could drive notifications
- **No explicit outcome/response object** attached to a teleconsult
- **No web UI** for viewing teleconsult outcomes from the referring facility's perspective
- **No consultation response/notes sharing mechanism**

**Verdict**: **ABSENT** (web), **PRESENT BUT PARTIAL** (backend — events and notes field exist, but no structured outcome)

**Gap**:
1. No structured teleconsult outcome (diagnosis, recommendations, follow-up actions)
2. No UI for referring facility to view outcomes
3. No notification to referring facility when teleconsult is completed
4. Notes field is a single text column — not structured clinical communication

**Fix Required**:
1. Add teleconsult outcome fields: `outcome_diagnosis`, `recommendations`, `follow_up_actions`, `outcome_status`
2. Add outcome creation endpoint: `POST /sessions/{id}/outcome`
3. Create web UI for viewing teleconsult outcomes in both EHR referrals tab and Telemedicine Hub
4. Leverage existing outbox events (`telemedicine.ended.v1`) to notify referring facility

---

## Summary Matrix

| # | Area | Verdict | Severity |
|---|------|---------|----------|
| 1A | Landing page when opening patient file | PRESENT BUT PARTIAL | Medium |
| 1B | Top Bar Menu with operational items | ABSENT | **Critical** |
| 1C | Right Encounter Menu | ABSENT | **Critical** |
| 2A-Consultations | Consultations tab | ABSENT | High |
| 2A-Referrals | Referrals tab | PRESENT BUT PARTIAL | Medium |
| 2A-Teleconsults | Teleconsults tab | ABSENT | High |
| 2B-QuickConnect | Quick Connect option | ABSENT | High |
| 2B-Schedule | Schedule First Teleconsult | ABSENT | High |
| 2B-Modes | Teleconsultation modes shown | ABSENT | Medium |
| 2B-Package | Build referral package E2E | PRESENT BUT PARTIAL | Medium |
| 2B-Submit | Submit teleconsult | ABSENT | High |
| 3A | Telemedicine Hub | ABSENT | **Critical** |
| 3B | Receiving facility sees teleconsults | ABSENT | **Critical** |
| 3C | Receiving facility actions teleconsults | ABSENT | **Critical** |
| 3D | Referring facility receives outcome | ABSENT | **Critical** |

**Overall Lovable Fidelity Score**: **LOW**

- Encounter shell structure: **Materially misaligned** — 2 of 6 layout components missing (TopBar, EncounterMenu)
- Consult/referral/teleconsult flows: **Not executable end-to-end** — only basic referral creation works
- Telemedicine hub: **Not functional** — no web UI, mobile-only backend with partial lifecycle support
