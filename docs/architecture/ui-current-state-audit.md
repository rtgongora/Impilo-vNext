# UI Current State Audit

## Scope Inspected

- `ui/one-ui-shell/src/app`
- `ui/one-ui-shell/src/components`
- `ui/one-ui-shell/src/features`
- `ui/one-ui-shell/src/data`
- `ui/one-ui-shell/src/engines`
- `ui/one-ui-shell/src/hooks`
- `ui/one-ui-shell/src/lib`
- `ui/one-ui-shell/src/providers`
- `ui/one-ui-shell/src/types`
- `ui/one-ui-shell/src/test`
- `contracts/core-transaction.ts`
- `ui/one-ui-shell/README.md`

## Existing Routes Grouped by Journey

### Person Journey

- Primary: `/client-journey`, `/citizen`, `/discover`, `/kiosk`, `/wallet`, `/wellness`, `/telemedicine`, `/marketplace`, `/share/claim`.
- Supporting: `/scheduling`, `/caregiving`, `/monitoring`, `/home/*`, `/citizen/*`, `/privacy`, `/consent`.
- Person-facing support is present in `/support`, `/support/tickets`, `/support/knowledge-base`.

### Provider Journey

- Primary: `/provider-workspace`, `/provider/activate`, `/clinical`, `/queue`, `/ehr/[patientId]`, `/shift`, `/workspace`.
- Clinical extensions: `/clinical-tools`, `/lab`, `/pharmacy`, `/telemedicine/session/[sessionId]`.
- Workflow support: `/learning`, `/guidance`, `/communication/secure-messaging`.

### Platform Journey

- Primary: `/platform-journey`, `/operations`, `/monitoring/provider-dashboard`, `/reports`.
- Governance and administration: `/admin/*`, `/registry-admin`, `/organization-admin/*`, `/ai-governance`, `/access`, `/id-services`.
- Enterprise and finance: `/finance/*`, `/enterprise/*`, `/erp/*`, `/coverage`.

### Cross-cutting / Nompilo / Search / Support

- `/ask`, `/search`, `/intelligence`, `/guidance`, `/support`.
- Shell-level command/discovery also exists in `ShellSearchPalette` and taskbar command affordances.
- Core transaction anchor route exists at `/core-transaction`.

### Domain-specific Modules

- Registry: `/registry/*`
- Public health: `/public-health/*`
- Commerce and supply: `/marketplace/*`, `/inventory/*`
- Finance: `/finance/*`
- Learning: `/learning/*`
- Shell utilities: `/shell/file-manager`, `/shell/task-manager`

## Existing Component Folders Grouped by Purpose

### Shell / Layout / Navigation

- `components/AppLayout.tsx`, `components/PageShell.tsx`, `components/EHRLayout.tsx`, `components/MinimalLayout.tsx`.
- `components/navigation/ExperienceSidebar.tsx`, `components/navigation/ModuleBreadcrumb.tsx`.
- `components/shell/*` includes Start, Search Palette, Taskbar, Task Manager, route sync, SOS dialog.

### Transaction / Timeline / Workflow

- `features/core-transaction/*` (types, fixtures, state-machine, doctrine UI components).
- `components/timeline/PatientTimeline.tsx`.
- `components/workflow/WorkflowHeader.tsx`.

### Citizen / Person

- `components/citizen/*`, `components/home/*`.
- Route-level pages in `app/citizen`, `app/wellness`, `app/monitoring`, `app/caregiving`.

### Provider / Clinical

- `components/clinical/*`, `components/ehr/*`, `components/queue/*`, `components/provider/*`, `components/lab/*`.
- Rich EHR-specific layout and contextual top bar already present.

### Payments / Billing / Enterprise

- `components/payment/*`, `components/billing/*`, `components/enterprise/*`, `components/workspace-ops/*`.
- Route-level finance pages strongly represented under `app/finance`.

### Learning / Fundo

- `components/learning/*`.
- Route structure under `app/learning/*` is comprehensive.

### Search / Intelligence / Nompilo-like

- `components/intelligent/*` includes `NompiloHint`, `ProactiveAssistant`, `UnifiedSearch`.
- `components/intelligence/*` includes structured result rendering.
- Shell command/search surfaces already exist in `components/shell/ShellSearchPalette.tsx`.

### Support / Help / Omnichannel

- `components/help/*`, `components/support/*`.
- Routes at `/support*` and `/omnichannel`.

### Accessibility Gaps

- Utilities exist (`lib/accessibility.ts`) but no persistent user-facing accessibility toolbar.
- Existing voice dictation exists (`components/ui/DictationButton.tsx`) but not globally discoverable.
- High-contrast/large-text toggles were not available in app-shell surfaces.

## Duplicate or Overlapping Patterns

- Two navigation paradigms coexist: `ExperienceSidebar` and legacy `ZoneNavigation` exports.
- Nompilo/intelligent experiences are split across `/ask`, `/search`, `/guidance`, `ProactiveAssistant`, and shell search palette.
- Multiple layout/context strips (`AppLayout` header + `OperationalContextStrip` + `UtilityStrip`) overlap in responsibility.
- Core transaction scaffolding exists but is not yet reused as the shared context frame across broader journey pages.

## Useful Components to Reuse

- Shell command infrastructure: `ShellSearchPalette`, `ShellTaskbar`, `ShellStartMenu`.
- Context and operational state providers: `ExperienceEntryProvider`, auth/facility/workspace/shift stores.
- Voice input primitive: `DictationButton`.
- Existing transaction feature set: `features/core-transaction/components.tsx` and fixtures.
- Route registry and breadcrumb backbone: `lib/routes.ts`.

## Gaps

- No explicit route-to-journey map artifact for docs and navigation consistency.
- No unified role-aware top-level journey navigation metadata.
- Nompilo command bar is available indirectly (taskbar/search/ask) but not consistently surfaced in app-shell header flow.
- No persistent, discoverable accessibility control strip.
- Existing README is minimal and does not explain route/journey/consolidation strategy.

## Safe Additive Implementation Plan

1. Add a canonical route-to-journey mapping metadata module without changing route paths.
2. Add role-aware journey navigation component that links to existing routes only.
3. Add a global Nompilo command bar in `AppLayout`, reusing shell search palette and dictation primitives.
4. Add accessibility toolbar with non-destructive, client-side toggles.
5. Refine core transaction feature with a reusable transaction context panel.
6. Keep domain pages untouched functionally; improve discoverability through shared shell layers.
7. Add focused component tests and update core-transaction tests.
8. Document architecture and usage patterns to prevent duplicate UI patterns in future work.

