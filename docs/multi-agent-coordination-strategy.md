# Multi-Agent Coordination Strategy: Impilo vNext

## Agent Assignments

### Codex — EHR / Clinical / Patient Encounter Loop
**Owner of**: Everything inside the clinical encounter workflow

**Files**:
- `ui/experience/src/app/ehr/` (31 pages)
- `ui/experience/src/components/ehr/` (13 components — CadreHistoryForm, CadreExamForm, EncounterStepNav, EncounterDocumentsSheet, EncounterPatientHeader, CriticalEventButton, AIDiagnosticAssistant, SecureChartAccessFlow, ClinicalReviewHeader, clerking/)
- `ui/experience/src/components/clinical/` (7 components — ActiveCDSBanner, ClinicalToolbar, ClinicalToolsMenu, MedscapeTools, ClinicalReferences, SystemFeedbackStrip, VitalsRecorder)
- `ui/experience/src/components/ehr/sections/` (AssessmentSection, OutcomeSection)
- `ui/experience/src/components/lab/` (LabResultsSystem)
- `ui/experience/src/components/timeline/` (PatientTimeline)
- `ui/experience/src/engines/cadreEngine.ts`
- `ui/experience/src/hooks/useCadreFormConfig.ts`, `useEncounterWizard.ts`, `useSpeechToText.ts`, `useClinicalAlerts.ts`
- `ui/experience/src/data/clerkingTemplates.ts`
- `ui/experience/src/lib/consult-workflows.ts`

**Responsibilities**:
1. Clinical encounter workflow fidelity (Lovable parity)
2. Cadre-adaptive form system (35+ cadres × 13 visit types × 4 acuity levels)
3. Assessment → History → Exam → Outcome flow
4. CDS/AI integration
5. Drug database, interaction checker, clinical calculators
6. Clerking templates
7. Lab results, vitals recording, patient timeline
8. FHIR resource mapping for clinical data

**Interface contracts** (shared with Cursor):
- EncounterMenu.tsx — navigation between EHR sections (shared ownership)
- PatientBanner.tsx — patient context header (shared ownership)
- EHRLayout.tsx — clinical layout wrapper

---

### Cursor — Non-EHR UI + Mobile Apps + Backend BFF
**Owner of**: Everything outside the encounter loop

**UI Files**:
- `ui/experience/src/app/` (110 non-EHR pages — home, queue, admin, finance, pharmacy, registry, reports, scheduling, marketplace, settings, etc.)
- `ui/experience/src/components/` (non-clinical — AppLayout, ZoneNavigation, TopBar, PageShell, ExperienceSidebar, WorkplaceSelectionHub, NotificationsCommsHub, public-health/, portal/, help/, ward/, workspace-ops/, notifications/)
- `ui/experience/src/app/telemedicine/` (telemedicine pages + session)
- `ui/experience/src/lib/queue-workflows.ts`
- `ui/experience/src/providers/` (AuthGuardProvider, ExperienceEntryProvider, Providers)
- `ui/experience/src/hooks/` (stores — useAuthStore, useFacilityStore, useShiftStore, useWorkModeStore, useRoleGroup, useWardData)
- `ui/experience/src/hooks/queries/` (all API query hooks)
- `ui/experience/src/lib/routes.ts`, `api-client.ts`, `accessibility.ts`, `i18n/`

**Mobile Files**:
- `apps/mobile/citizen-app/` (all screens, services, navigation)
- `apps/mobile/provider-app/` (all screens, services, navigation)
- `apps/mobile/packages/` (shared mobile packages — api-client, auth, trust, messaging, timeline, offline, design-system)

**Backend BFF**:
- `services/experience-bff/` (75 controllers, migrations, configs)
- Backend integration tests

**Responsibilities**:
1. Non-clinical page UX (home dashboard, queue management, finance, pharmacy, admin)
2. Navigation architecture (sidebar, zone switching, facility/workspace selection)
3. Authentication/authorization flow
4. Mobile citizen and provider apps (React Native / Expo)
5. Experience BFF controllers and API wiring
6. State management (Zustand stores)
7. Public health, coverage, omnichannel, AI governance modules
8. Ward management, workspace operations

---

### Claude Code — Coordinator + Infrastructure + Quality Gate
**Role**: Orchestrator with largest context window

**Owns**:
- `helm/` (39 service Helm charts + helmfile)
- `infra/k8s/` (namespaces, RBAC, networking, storage, config, ingress)
- `.github/workflows/` (CI/CD pipelines)
- `docker-compose*.yml` files
- `scripts/` (runtime, validation, smoke tests)
- `e2e/` (Playwright test suite)
- `vitest.config.ts`, `.eslintrc.json`, `tsconfig.json`

**Coordination responsibilities**:
1. Maintain this strategy document
2. Run build validation (`type-check`, `lint`, `mvn package`) after each agent's work
3. Resolve merge conflicts between Codex and Cursor outputs
4. Ensure shared contracts stay in sync (EncounterMenu, PatientBanner, routes.ts, api-client)
5. Track Lovable parity status
6. Infrastructure and deployment
7. Cross-cutting concerns (security, a11y, i18n, observability)

---

## Shared Contracts (Boundary Files)

These files are touched by both Codex and Cursor. Changes must be coordinated through Claude:

| File | Codex writes | Cursor writes |
|------|-------------|---------------|
| `EncounterMenu.tsx` | Clinical section entries | Layout/navigation structure |
| `PatientBanner.tsx` | Clinical context fields | Layout/styling |
| `routes.ts` | EHR routes | Non-EHR routes |
| `api-client.ts` | Clinical API helpers | Auth/trust header injection |
| `EHRLayout.tsx` | Clinical toolbar integration | Overall layout shell |
| `TopBar.tsx` | CDS indicators | Navigation, notifications |

**Rule**: Neither agent modifies a shared contract without Claude reviewing the change first.

---

## Workflow

### For new Lovable features:
1. Claude reads Lovable source, determines domain (clinical vs non-clinical)
2. Claude writes the brief for the assigned agent (Codex or Cursor)
3. Agent executes
4. Claude validates build, reviews output, resolves conflicts
5. Claude pushes to branch

### For bug fixes:
1. Identify which domain the bug is in
2. Route to the owning agent
3. Agent fixes
4. Claude validates

### For cross-cutting changes:
1. Claude handles directly (infra, configs, shared contracts)
2. Or: Claude briefs both agents, sequences their work to avoid conflicts

---

## Branch Strategy

```
claude/staging-ux-orchestration-remediation-Yypyl  (main integration branch)
  ├── Work happens directly on this branch
  ├── Codex pushes EHR changes → Claude validates → merge
  ├── Cursor pushes non-EHR changes → Claude validates → merge
  └── Claude pushes infra/coordination changes directly
```

All agents push to the same branch. Claude resolves any conflicts.

---

## Current Status Handoff

### What Codex inherits (EHR/Clinical):
- 31 EHR pages implemented
- CadreHistoryForm (499L, verified at Lovable parity)
- CadreExamForm (447L)
- AssessmentSection (763L, wired with 9 tabs including VitalsRecorder, Clerking, Labs, Timeline)
- OutcomeSection (420L, 6 disposition forms)
- MedscapeTools (709L, DrugMonograph, 15 drugs, 6 calculators)
- ActiveCDSBanner, ClinicalToolbar, useSpeechToText, cadreEngine — all at 90%+ parity
- SecureChartAccessFlow, CriticalEventButton, AIDiagnosticAssistant — newly ported

### What Cursor inherits (Non-EHR):
- 110 non-EHR pages
- ExperienceSidebar (434L), WorkplaceSelectionHub (245L) — just added
- Queue pages enriched (triage, waiting, walk-in, search, scheduled)
- Finance, pharmacy pages enriched
- Consult-workflows, queue-workflows — just added
- Mobile apps with 28 provider screens, 15 citizen tabs
- Experience BFF with 75 controllers

### What Claude maintains:
- 39 Helm charts with templates
- K8s manifests (namespaces, RBAC, networking, storage, ingress)
- CI/CD (ci.yml + deploy.yml)
- Playwright E2E (8 spec files, 62 tests)
- Vitest unit tests (6 suites, 70 tests)
- Build validation gate
