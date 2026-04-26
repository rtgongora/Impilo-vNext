# Lovable parity — clinical experience shell restoration audit

Date: 2026-04-12  
Scope: `ui/experience` (EHR shell, encounter workspace, wizard, comms/help/support strip, floating assist, app shell parity). Cross-package notes for `ui/one-ui-shell`, `ui/ehr`, `ui/shared-ui`.

## Files inspected

- `ui/experience/src/app/ehr/[patientId]/layout.tsx` (new — single shell wrapper)
- `ui/experience/src/app/ehr/[patientId]/**/page.tsx` (per-route inventory; `EHRLayout` wrappers removed)
- `ui/experience/src/components/EHRLayout.tsx`
- `ui/experience/src/components/AppLayout.tsx`
- `ui/experience/src/components/TopBar.tsx`
- `ui/experience/src/components/PatientBanner.tsx`
- `ui/experience/src/components/EncounterMenu.tsx`
- `ui/experience/src/components/experience/OperationalContextStrip.tsx`
- `ui/experience/src/components/clinical/ClinicalToolbar.tsx`
- `ui/experience/src/components/clinical/ClinicalKnowledgeDock.tsx`
- `ui/experience/src/components/clinical/ClinicalWizardHeader.tsx`
- `ui/experience/src/components/clinical/ClinicalWorkflowContext.tsx`
- `ui/experience/src/components/clinical/ClinicalSupportStrip.tsx`
- `ui/experience/src/components/clinical/FloatingClinicalAssist.tsx`
- `ui/experience/src/lib/clinical/encounter-workspace-nav.ts`
- `ui/experience/src/components/notifications/NotificationsCommsHub.tsx`
- `ui/experience/src/providers/Providers.tsx`, `components/shell/ShellChrome.tsx`
- `ui/one-ui-shell/src` (grep for clinical shell components — none duplicated)
- `ui/ehr` (standalone legacy EHR sample app — separate from experience `/ehr`)
- `ui/shared-ui` (shared primitives; no encounter shell exports in this pass)

## Components found

| Component | Role |
|-----------|------|
| `EHRLayout` | TopBar, ClinicalSupportStrip, OperationalContextStrip, PatientBanner, ClinicalToolbar, ClinicalWizardHeader, EncounterMenu + main + ClinicalKnowledgeDock, FloatingClinicalAssist; `ClinicalWorkflowProvider` for programme wizard overrides |
| `EncounterMenu` | Eight Lovable sections, role gating, patient + encounter context, `encounter_id` on links |
| `ClinicalWizardHeader` | Step strip, back/next (skips role-disallowed steps), save + `impilo:ehr-save-draft` event, overview home, step pills gated by `isSectionVisible` |
| `ClinicalSupportStrip` | Comms Hub (`NotificationsCommsHub`), Help (`/support/knowledge-base`), System Support (`/support/tickets`) |
| `FloatingClinicalAssist` | Fixed Nompilo control; PHI-safe `/ask` query (`routePattern` anonymised UUIDs); Comms Hub + tickets escalation; `from=ehr` or `from=experience` by pathname |
| `AppLayout` | Sidebar + header + operational strip; **now** includes ClinicalSupportStrip + FloatingClinicalAssist when authenticated (operational / non-EHR parity) |

## Components missing (outside this app or future work)

- **Per-link Tshepo ABAC** in the browser for each encounter menu row (BFF/gateway remain authoritative).
- **Dedicated nested encounter URLs** per section (e.g. `/encounter/{id}/orders`); current model uses chart routes + optional `encounter_id`.
- **Shared package** exporting shell pieces into `ui/shared-ui` for other bundles (not implemented).
- **`ui/one-ui-shell`** does not import `ClinicalSupportStrip` / `FloatingClinicalAssist`; if the shell hosts embedded experience, parity is in the experience app unless ported.

## Components restored / changed (this pass)

| Change | Detail |
|--------|--------|
| **Patient chart layout** | `app/ehr/[patientId]/layout.tsx` wraps all chart routes in `EHRLayout` once so shell chrome cannot be omitted by a page forgetting the wrapper |
| **Page cleanup** | Removed duplicate `<EHRLayout>` from every `page.tsx` under `[patientId]` |
| **Programme wizard** | Session key `exp:clinical-wizard-workflow` JSON `{ "steps": [{ "id", "label" }] }` — ids must be a subset of default `ClinicalWorkflowStepId`; dispatch `impilo:clinical-wizard-workflow-changed` or `storage` to refresh |
| **Wizard + roles** | `encounterSectionForWizardStepId()` + `isSectionVisible()` on step pills; next/back skips disallowed steps |
| **App shell parity** | `AppLayout` shows support strip + floating assist for signed-in operational routes |

## Route mapping (Encounter menu → `/ehr` routes)

| Section | Primary route | Pathname hints (`sectionForPathname`) |
|---------|---------------|----------------------------------------|
| Overview | `/ehr/{patientId}` | Chart root; `summary`, `timeline`, `encounters`, `ips` |
| Assessment | `/ehr/{patientId}/encounter/{id}` if encounter, else `/ehr/{patientId}/vitals` | `encounter/*`, `vitals`, `allergies`, `history`, `assessments` |
| Problems & Diagnoses | `/ehr/{patientId}/conditions` | `conditions` |
| Orders & Results | `/ehr/{patientId}/orders` | `orders`, `results`, `imaging`, `procedures` |
| Care & Management | `/ehr/{patientId}/care-plans` | `care-plans`, `medications`, `goals`, `care-team` |
| Consults & Referrals | `/ehr/{patientId}/consults` | `consults`, `referrals`, `teleconsults` |
| Notes & Attachments | `/ehr/{patientId}/notes` | `notes`, `documents` |
| Visit Outcome | `/ehr/{patientId}/discharge` | `discharge` |

Non-overview links append `?encounter_id=` when an encounter id is known.

## Wizard stages (default)

Aligned with `DEFAULT_CLINICAL_WIZARD_STEPS` in `encounter-workspace-nav.ts`: Patient Context → Vitals & Triage → Assessment → Problems & Diagnoses → Orders & Results → Care Plan → Consults & Referrals → Notes & Attachments → Visit Outcome.

## Screenshots / component evidence

- No screenshots in-repo. Evidence: unit tests `src/lib/clinical/encounter-workspace-nav.test.ts`, `src/components/__tests__/EncounterMenu.test.tsx`, `src/components/clinical/ClinicalWizardHeader.test.tsx`.

## Authorisation / Tshepo

- UI: `useRoleGroup` + `isSectionVisible` (same coarse groups as `AuthGuardProvider`). EncounterMenu documents BFF/Tshepo enforcement on data access.
- Server: unchanged; no new client calls to `tshepo-authz-service` in this pass.

## Remaining gaps

- Fine-grained Tshepo decisions per section and per wizard step (optional API hook).
- `validateBeforeNext` / `onSaveDraft` props on `ClinicalWizardHeader` are still optional; chart pages do not pass validators yet.
- Programme wizard session format does not support arbitrary step ids (only known `ClinicalWorkflowStepId` values) so `buildWizardStepHref` stays correct.
- Port shell strip + floating assist into `ui/one-ui-shell` if product requires identical chrome outside `ui/experience`.

## Tests run (commands)

```bash
cd ui/experience
npm run lint
npm run type-check
npm test
npm run build
```

Fill in CI or local stdout after each run.
