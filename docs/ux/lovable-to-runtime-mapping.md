# Lovable-to-Runtime Mapping

Reference: `/home/user/impilo-structure` (Lovable prototype)
Runtime: `/home/user/Impilo-vNext/ui/experience` (production implementation)

Generated: 2026-04-04
Branch: `claude/staging-ux-orchestration-remediation-Yypyl`

---

## 1. Application Shell & Navigation

### 1.1 Sidebar Navigation

| Aspect | Lovable (`impilo-structure`) | Runtime (`Impilo-vNext`) | Status | Action |
|--------|------------------------------|--------------------------|--------|--------|
| **Architecture** | Context-aware sidebar that switches nav groups based on current page context (clinical, operations, scheduling, registry, admin, portal, etc.) | 3-zone static sidebar (Life / Work / Professional) | **divergent** | adapt — the Lovable context-switching model is more coherent for a multi-role platform; evaluate adoption |
| **Clinical Priority Items** | Dashboard, My Worklist, Communication | Home, Queue | **partial** | adapt — add Communication link |
| **Clinical Nav** | Clinical EHR, Bed Management, Appointments, Patients | Queue, Telemedicine, Pharmacy, Inventory, Marketplace, Finance | **divergent** | adapt — Lovable groups clinical tools differently; runtime mixes clinical + operational |
| **Orders Nav** | Order Entry, Pharmacy, Laboratory, PACS, Shift Handoff | (distributed across EHR encounter menu) | **divergent** | preserve — runtime puts orders inside encounter context, which is stronger |
| **Operations** | Stock, Consumables, Charges, Payments, Theatre | Inventory, Finance | **partial** | adapt where needed |
| **Scheduling** | Appointments, Theatre Booking, Noticeboard, Resources | (not in sidebar) | **missing** | implement if in scope |
| **Registry** | Client Registry, Provider Registry, Facility Registry + tools | Registry (facilities, providers, products, terminology) | **partial** | preserve — runtime registry is backed by real sovereign services |
| **Admin** | System Settings, User Management, Security, Audit, Integrations + 25 service-specific admin pages | Admin section with 10 sub-pages | **partial** | preserve — runtime admin is real; Lovable admin is more comprehensive in breadth |
| **Portal** | My Health, Social Hub, Marketplace, Communication | (separate portal app) | **divergent** | preserve — runtime has separate citizen portal app |
| **Workspace Selector** | FacilitySelector + WorkspaceSelector in sidebar | Facility, Workspace, Shift in sidebar footer | **exact** | preserve |

### 1.2 Layout System

| Aspect | Lovable | Runtime | Status | Action |
|--------|---------|---------|--------|--------|
| **App Layout** | AppLayout with AppSidebar + AppHeader + MainWorkArea | AppLayout with ZoneNavigation sidebar + header bar | **partial** | preserve — same structural pattern |
| **EHR Layout** | EHRLayout with TopBar + EncounterMenu (right sidebar) + MainWorkArea | EHRLayout with TopBar + EncounterMenu (left sidebar) + main area | **partial** | adapt — Lovable puts encounter menu on the RIGHT; runtime puts it LEFT. Both work. |
| **Patient Banner** | Dedicated PatientBanner component above workspace | Inline in encounter page header | **divergent** | adapt — Lovable's persistent patient banner is better UX |
| **Active Workspace Indicator** | ActiveWorkspaceIndicator with mode/phase display | Shift Active badge in header | **partial** | adapt |

---

## 2. EHR Encounter Workspace

### 2.1 Encounter Menu Sections

| Lovable Section | Lovable Items | Runtime Section | Runtime Items | Status | Action |
|-----------------|---------------|-----------------|---------------|--------|--------|
| **Overview** | Patient summary and status | **Overview** | Summary, Timeline | **exact** | preserve |
| **Assessment** | Clinical assessments | **Assessment** | Vitals, Conditions, History | **exact** | preserve — runtime has more granular items |
| **Problems & Diagnoses** | Active problems and diagnoses | **Problems & Diagnoses** | Allergies, Immunizations | **exact** | preserve |
| **Orders & Results** | Lab orders and results | **Care & Management** | Medications, Orders, Results | **partial** | adapt — Lovable combines orders+results; runtime splits them across Care section |
| **Care & Management** | Care plans and management | (distributed) | Medications in Care section | **partial** | adapt |
| **Consults & Referrals** | Specialist consultations (3-tab: Consultations / Referrals / Teleconsults) | **Consults & Referrals** | Referrals, Teleconsults, Documents | **partial** | adapt — Lovable has a unified 3-tab Consults section with integrated telemedicine hub; runtime splits into separate route pages |
| **Notes & Attachments** | Clinical notes and documents | **Discharge** section | Notes, Discharge | **partial** | adapt — Lovable groups notes+documents; runtime groups notes+discharge |
| **Visit Outcome** | Encounter disposition | **Discharge** | Discharge page | **partial** | adapt — Lovable calls it "Visit Outcome" (broader than discharge) |
| *(Patient File)* | Standalone workspace button | (patient chart is the root `/ehr/[patientId]`) | **divergent** | preserve — runtime's route-based approach is more natural for Next.js |

### 2.2 Encounter Menu UX

| Aspect | Lovable | Runtime | Status | Action |
|--------|---------|---------|--------|--------|
| **Item format** | Icon + Label + Description, with active indicator chevron | Icon + Label only | **divergent** | adapt — add descriptions to menu items |
| **Patient File button** | Dedicated button above menu with open/close toggle | Link to patient chart root | **partial** | preserve |
| **Footer** | "Last saved: X min ago" + Active status indicator | (none) | **missing** | implement |
| **Critical event dimming** | Menu dims during critical events or active workspaces | (not implemented) | **missing** | out of scope for now |
| **Animation** | framer-motion slide-in for items | (none) | **missing** | optional |

### 2.3 Top Bar (EHR)

| Lovable Action | Runtime Equivalent | Status | Action |
|----------------|-------------------|--------|--------|
| **Queue** | Back to Queue (breadcrumb) | **partial** | adapt — Lovable has explicit Queue action button |
| **Beds** | (not implemented) | **missing** | out of scope |
| **Pharmacy** | Pharmacy link | **exact** | preserve |
| **Theatre Booking** | (not implemented) | **missing** | out of scope |
| **Payments** | Payments link | **exact** | preserve |
| **Shift Handoff** | Shift Handoff link | **exact** | preserve |
| **Workspaces** | (not implemented) | **missing** | out of scope |
| **Care Pathways** | (not implemented) | **missing** | out of scope |
| **Consumables** | (not implemented) | **missing** | out of scope |
| **Charges** | (not implemented) | **missing** | out of scope |
| *(added in runtime)* **Orders** | Orders (dynamic) | n/a | preserve |
| *(added in runtime)* **Referrals** | Referrals (dynamic) | n/a | preserve |
| *(added in runtime)* **Teleconsult** | Telemedicine link | n/a | preserve |

---

## 3. Consults & Referrals (Key Divergence Area)

### 3.1 Lovable Consults Architecture

The Lovable reference has a **unified Consults section** with rich sub-workflows:

```
ConsultsSection
├── Dashboard (ConsultsDashboard)
├── 3-Tab Layout
│   ├── Consultations Tab (ConsultationsTab)
│   ├── Referrals Tab (ReferralsTab)
│   └── Teleconsults Tab (TeleconsultsTab)
├── Sub-workflows (rendered in-place)
│   ├── New Referral → ReferralBuilder
│   ├── New Teleconsult → TelemedicineWorkflow (7-stage)
│   ├── Active Session → TeleconsultSession (video/audio/chat)
│   ├── Completion Note → CompletionNoteForm
│   ├── Telehealth Dashboard → TelehealthDashboard
│   ├── Async Review → AsynchronousReviewPane
│   └── Telemedicine Hub → FullCircleTelemedicineHub
└── Context: patient-specific, encounter-linked
```

**Key components:**
- `ReferralBuilder` — structured referral creation with clinical context
- `TelemedicineWorkflow` — 7-stage workflow (select mode → build package → submit → etc.)
- `TeleconsultSession` — live session with video/audio/chat
- `CompletionNote` — post-session clinical documentation
- `ConsultsDashboard` — worklist-style overview
- `ReferralPackageBuilderDialog` — structured referral package creation
- `ReferralPackageViewer` — view received referral packages
- `IncomingConsultWorkflow` — receiving facility workflow
- `OutgoingReferralWorkflow` — sending facility workflow

### 3.2 Runtime Consults Architecture

```
Encounter Menu → Consults & Referrals
├── /ehr/[patientId]/referrals (separate page)
│   ├── Referral list with status badges
│   ├── Create Referral form
│   └── Response cards (return loop)
├── /ehr/[patientId]/teleconsults (separate page)
│   ├── Session list
│   ├── Schedule Teleconsult form with referral linkage
│   └── Join session navigation
└── /telemedicine (hub page, outside EHR context)
    ├── Session list with tabs
    └── /telemedicine/session/[sessionId] (session detail)
```

### 3.3 Consults Comparison

| Lovable Component | Runtime Equivalent | Status | Action |
|-------------------|-------------------|--------|--------|
| **ConsultsDashboard** | (no unified dashboard) | **missing** | implement — unified consults overview within encounter |
| **ConsultationsTab** | (no consultations concept separate from referrals) | **missing** | implement — consultations are facility-internal; referrals are cross-facility |
| **ReferralsTab** | `/ehr/[patientId]/referrals` page | **partial** | adapt — Lovable renders as in-encounter tab, runtime as separate route |
| **TeleconsultsTab** | `/ehr/[patientId]/teleconsults` page | **partial** | adapt — same pattern |
| **ReferralBuilder** | Create Referral form on referrals page | **partial** | adapt — Lovable has structured multi-step builder |
| **TelemedicineWorkflow** (7-stage) | Schedule form on teleconsults page | **divergent** | adapt — Lovable's 7-stage workflow is richer; runtime has simpler create |
| **TeleconsultSession** | `/telemedicine/session/[sessionId]` | **partial** | preserve — runtime has vitals+notes capture |
| **CompletionNote** | (not implemented) | **missing** | implement — post-session clinical documentation |
| **IncomingConsultWorkflow** | `/queue/incoming-referrals` page | **partial** | adapt — Lovable shows this in encounter context |
| **OutgoingReferralWorkflow** | Response cards on referrals page | **partial** | preserve |
| **ReferralPackageBuilderDialog** | (not implemented) | **missing** | implement if in scope |
| **ReferralPackageViewer** | (not implemented) | **missing** | implement if in scope |
| **AsynchronousReviewPane** | (not implemented) | **missing** | out of scope for now |
| **FullCircleTelemedicineHub** | `/telemedicine` hub page | **partial** | preserve |
| **TelemedicineModeSelection** | session_type select (VIDEO/AUDIO/CHAT) | **partial** | preserve |
| **VideoCallPanel** / **AudioCallSession** / **ChatSession** | (no real media) | **missing** | BLOCKED_EXTERNAL — requires video service |

---

## 4. Queue & Patient Flow

| Lovable Component | File | Runtime Equivalent | Status | Action |
|-------------------|------|-------------------|--------|--------|
| **QueueManagement** | `ehr/queue/QueueManagement.tsx` | `/queue` page | **partial** | adapt — Lovable queue is more integrated with EHR |
| **QueueManagementLive** | `ehr/queue/QueueManagementLive.tsx` | (no real-time) | **missing** | BLOCKED_EXTERNAL |
| **QueuePatientCard** | `ehr/queue/QueuePatientCard.tsx` | Table rows on queue page | **partial** | adapt — Lovable uses cards; runtime uses table |
| **QueueStats** | `ehr/queue/QueueStats.tsx` | Patient count display | **partial** | adapt — add queue stats |
| **AddPatientDialog** | `ehr/queue/AddPatientDialog.tsx` | `/queue/walk-in` page | **partial** | preserve — runtime has fuller walk-in flow |
| **PatientSorting** | `pages/PatientSorting.tsx` | `/queue/triage` page | **partial** | adapt |
| **Registration** | `pages/Registration.tsx` | `/queue/walk-in` (combined) | **partial** | preserve |

---

## 5. EHR Encounter Sections (Detailed)

### 5.1 Overview Section

| Lovable | Runtime | Status | Action |
|---------|---------|--------|--------|
| `OverviewSection.tsx` — Patient demographics, active problems, recent vitals, current medications, allergies alert | `/ehr/[patientId]/summary` — Patient info, encounters, allergies, conditions, medications counts | **partial** | adapt — Lovable shows more clinical data inline |

### 5.2 Assessment Section

| Lovable | Runtime | Status | Action |
|---------|---------|--------|--------|
| `AssessmentSection.tsx` — Vitals monitoring, clinical assessments | `/ehr/[patientId]/vitals` + `/conditions` + `/history` | **partial** | preserve — runtime splits into granular pages which is appropriate |

### 5.3 Problems Section

| Lovable | Runtime | Status | Action |
|---------|---------|--------|--------|
| `ProblemsSection.tsx` with `LiveProblemsSection.tsx` — Active problem list with severity, onset, ICD codes | `/ehr/[patientId]/allergies` + `/immunizations` | **partial** | adapt — Lovable has a unified problem list; runtime splits into allergy/immunization pages |

### 5.4 Orders Section

| Lovable | Runtime | Status | Action |
|---------|---------|--------|--------|
| `OrdersSection.tsx` + `OrderSetsSystem.tsx` + `OrderSetCard.tsx` + `OrderSetDialog.tsx` | `/ehr/[patientId]/orders` (lab orders) + `/results` | **partial** | adapt — Lovable has order sets (templated order groups); runtime has individual order creation |

### 5.5 Care Section

| Lovable | Runtime | Status | Action |
|---------|---------|--------|--------|
| `CareSection.tsx` + `NursingCarePlan.tsx` + `NursingInterventions.tsx` + `TreatmentGoals.tsx` | `/ehr/[patientId]/medications` | **divergent** | adapt — Lovable has structured care plans; runtime has medication list only |

### 5.6 Notes Section

| Lovable | Runtime | Status | Action |
|---------|---------|--------|--------|
| `NotesSection.tsx` — Clinical notes + document attachments | `/ehr/[patientId]/notes` + `/documents` | **exact** | preserve — runtime has separate pages for notes and documents |

### 5.7 Outcome Section

| Lovable | Runtime | Status | Action |
|---------|---------|--------|--------|
| `OutcomeSection.tsx` — Visit disposition, discharge summary, follow-up plan | `/ehr/[patientId]/discharge` | **partial** | adapt — Lovable calls it "Visit Outcome" which covers more than just discharge |

---

## 6. Specialized Workspaces (Lovable Only)

These exist in Lovable but have no runtime equivalent. All are **out of scope** for current remediation.

| Workspace | Purpose |
|-----------|---------|
| PatientFileWorkspace | Longitudinal patient record viewer |
| TeleconsultationWorkspace | Virtual care workspace |
| TraumaWorkspace | Emergency trauma protocol |
| ResuscitationWorkspace | Code blue / rapid response |
| BurnsWorkspace | Burns treatment protocol |
| LabourDeliveryWorkspace | Maternity care |
| TheatreWorkspace | Operating theatre workflow |
| DialysisWorkspace | Renal dialysis protocol |
| ChemotherapyWorkspace | Oncology treatment |
| MinorProcedureWorkspace | Minor procedures |
| PhysiotherapyWorkspace | Rehabilitation |
| PsychotherapyWorkspace | Mental health |
| RadiotherapyWorkspace | Radiation therapy |
| SexualAssaultWorkspace | Forensic examination |
| NeonatalResusWorkspace | Neonatal resuscitation |
| PoisoningWorkspace | Toxicology |
| AnaesthesiaPreOpWorkspace | Pre-operative assessment |
| VirtualCareWorkspace | Telemedicine variant |
| CriticalEventWorkspace | Critical event activation |

---

## 7. Pages Mapping (Full)

### 7.1 Clinical Pages

| Lovable Page | Route | Runtime Equivalent | Status | Action |
|-------------|-------|-------------------|--------|--------|
| `Encounter.tsx` | `/encounter/:encounterId` | `/ehr/[patientId]/encounter/[encounterId]` | **partial** | adapt — Lovable uses EHRContext for state; runtime uses URL params |
| `Dashboard.tsx` | `/dashboard` | `/home` | **partial** | preserve |
| `Patients.tsx` | `/patients` | `/queue/search` | **partial** | adapt |
| `Beds.tsx` | `/beds` | (not implemented) | **missing** | out of scope |
| `Appointments.tsx` | `/appointments` | `/queue/scheduled` | **partial** | adapt |
| `Discharge.tsx` | `/discharge` | `/ehr/[patientId]/discharge` | **exact** | preserve |
| `Handoff.tsx` | `/handoff` | `/shift/handover` | **exact** | preserve |

### 7.2 Orders & Diagnostics

| Lovable Page | Route | Runtime Equivalent | Status | Action |
|-------------|-------|-------------------|--------|--------|
| `Orders.tsx` | `/orders` | `/ehr/[patientId]/orders` (in encounter) | **partial** | preserve — runtime scopes to encounter |
| `Pharmacy.tsx` | `/pharmacy` | `/pharmacy` | **exact** | preserve |
| `LIMS.tsx` | `/lims` | `/ehr/[patientId]/results` (in encounter) | **partial** | adapt |
| `PACS.tsx` | `/pacs` | (not implemented) | **missing** | out of scope |

### 7.3 Operations

| Lovable Page | Route | Runtime Equivalent | Status | Action |
|-------------|-------|-------------------|--------|--------|
| `Consumables.tsx` | `/consumables` | `/inventory` | **partial** | preserve |
| `Charges.tsx` | `/charges` | `/finance/billing` | **partial** | preserve |
| `Payments.tsx` | `/payments` | `/finance/payments` | **exact** | preserve |
| `Operations.tsx` | `/operations` | (not implemented as standalone) | **missing** | out of scope |

### 7.4 Registry

| Lovable Page | Route | Runtime Equivalent | Status | Action |
|-------------|-------|-------------------|--------|--------|
| `FacilityRegistry.tsx` | `/facility-registry` | `/registry/facilities` | **exact** | preserve |
| `HealthProviderRegistry.tsx` | `/hpr` | `/registry/providers` | **exact** | preserve |
| `ClientRegistry.tsx` | `/client-registry` | `/queue/search` (patient search) | **partial** | adapt |
| `ProductCatalogue.tsx` | `/catalogue` | `/registry/products` | **exact** | preserve |

### 7.5 Marketplace & Fulfillment

| Lovable Page | Route | Runtime Equivalent | Status | Action |
|-------------|-------|-------------------|--------|--------|
| `HealthMarketplace.tsx` | `/marketplace` | `/marketplace` | **exact** | preserve |
| `PrescriptionFulfillment.tsx` | `/fulfillment` | `/pharmacy/dispense` | **partial** | preserve |

### 7.6 Identity & Portal

| Lovable Page | Route | Runtime Equivalent | Status | Action |
|-------------|-------|-------------------|--------|--------|
| `IdServices.tsx` | `/id-services` | (separate portal app) | **divergent** | preserve — runtime uses dedicated portal |
| `Portal.tsx` | `/portal` | `/ui/portal` (separate app) | **divergent** | preserve |

### 7.7 Admin & Service-Specific Pages

| Lovable Page | Route | Runtime Equivalent | Status | Action |
|-------------|-------|-------------------|--------|--------|
| `AdminDashboard.tsx` | `/admin` | `/admin` | **exact** | preserve |
| `admin/TshepoAuditSearch.tsx` | `/admin/tshepo/audit` | `/admin/audit` | **partial** | preserve |
| `admin/TshepoBreakGlass.tsx` | `/admin/tshepo/break-glass` | `/admin/break-glass` | **exact** | preserve |
| `admin/TusoFacilities.tsx` | `/admin/tuso/facilities` | `/registry/facilities` | **partial** | preserve |
| `admin/TusoWorkspaces.tsx` | `/admin/tuso/workspaces` | `/workspace` | **partial** | preserve |
| `admin/VarapiProviders.tsx` | `/admin/varapi/providers` | `/registry/providers` | **partial** | preserve |
| `admin/VitoPatients.tsx` | `/admin/vito/patients` | `/queue/search` | **partial** | preserve |
| `admin/ZiboAdmin.tsx` | `/admin/zibo` | `/admin` (general) | **partial** | preserve |
| *(25 more admin pages)* | various | partial coverage | **partial** | preserve |

### 7.8 Scheduling

| Lovable Page | Route | Runtime Equivalent | Status | Action |
|-------------|-------|-------------------|--------|--------|
| `scheduling/AppointmentScheduling.tsx` | `/scheduling` | `/queue/scheduled` | **partial** | adapt |
| `scheduling/TheatreScheduling.tsx` | `/scheduling/theatre` | (not implemented) | **missing** | out of scope |
| `scheduling/ProviderNoticeboard.tsx` | `/scheduling/noticeboard` | (not implemented) | **missing** | out of scope |
| `scheduling/ResourceCalendar.tsx` | `/scheduling/resources` | (not implemented) | **missing** | out of scope |

### 7.9 Advanced/Specialized

| Lovable Page | Route | Runtime Equivalent | Status | Action |
|-------------|-------|-------------------|--------|--------|
| `Communication.tsx` | `/communication` | (not implemented) | **missing** | out of scope |
| `CoverageOperations.tsx` | `/coverage` | (not implemented) | **missing** | out of scope |
| `AIGovernance.tsx` | `/ai-governance` | (not implemented) | **missing** | out of scope |
| `OmnichannelHub.tsx` | `/omnichannel` | (not implemented) | **missing** | out of scope |
| `PublicHealthOps.tsx` | `/public-health` | (not implemented) | **missing** | out of scope |
| `Landela.tsx` | `/landela` | (not implemented) | **missing** | out of scope |
| `Kiosk.tsx` | `/kiosk` | (not implemented) | **missing** | out of scope |

---

## 8. Summary Statistics

| Category | Lovable | Runtime | Match | Partial | Missing | Divergent |
|----------|---------|---------|-------|---------|---------|-----------|
| **Top-level routes** | ~65 | ~100 | 12 | 28 | 18 | 7 |
| **EHR encounter sections** | 8 | 6 (+ sub-pages) | 2 | 5 | 1 | 0 |
| **Consults components** | 17 | 5 | 0 | 5 | 10 | 2 |
| **Layout components** | 10 | 5 | 2 | 3 | 0 | 0 |
| **Admin pages** | 25+ | 10 | 3 | 7 | 15 | 0 |
| **Workspaces** | 19 | 0 | 0 | 0 | 19 | 0 |

### Key Findings

1. **Runtime has MORE pages** (100 vs ~65) but Lovable has richer component depth within each page
2. **Encounter workspace** is structurally aligned but Lovable's Consults section is significantly richer
3. **Backend strength** is overwhelmingly in the runtime (PCT, OROS, VITO, etc.) — preserve this
4. **Lovable's specialized workspaces** (19 clinical workspace types) are aspirational — out of scope
5. **The biggest UX gap** is the Consults & Referrals section: Lovable has a unified 3-tab layout with in-encounter sub-workflows; runtime splits into separate route pages

### Recommended Next Implementation Priority

1. **Consults section unification** — Adapt the 3-tab Consults pattern (Consultations / Referrals / Teleconsults) as a coherent in-encounter experience
2. **Encounter menu descriptions** — Add item descriptions matching Lovable's richer menu items
3. **Patient banner** — Add persistent patient context banner in EHR layout
4. **Visit Outcome** — Rename/expand Discharge to Visit Outcome with broader disposition options
5. **Completion Note** — Implement post-teleconsult clinical documentation
