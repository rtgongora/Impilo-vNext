# Convergence Inventory — `ui/experience` ↔ `ui/one-ui-shell`

> Generated: 2026-05-28. Source of truth for the GAP-010 web-shell merge.
>
> **Status: CLOSED (2026-05-28).** All eight phases (0, 1a–1f, 2, 3, 4, 5, 6, 7, 8) have landed.
> `ui/experience/` has been removed. The canonical web shell is `ui/one-ui-shell` with
> `EXPECTED_ROUTE_COUNT = 374`. The Phase-8 CI guard (`.github/workflows/deprecated-surface-guard.yml`)
> blocks any new files from being added under the retired path.
>
> Inventory regeneration: `pwsh scripts/frontend/merge-inventory.ps1` (planned; current data captured below).

## Purpose

This document is the **merge contract** for converging the two divergent evolutions of the Impilo web shell into a single surface at `ui/one-ui-shell`. It classifies every file in `ui/experience/src/**` against `ui/one-ui-shell/src/**` so that the deletion of `ui/experience/` in Phase 6 is provably non-destructive of capability.

This is integration work, not retirement: each side holds capabilities the other lacks, and the merged shell must end with the **union** of those capabilities.

## Summary

| Bucket | Count | Disposition |
|---|---|---|
| Files in `ui/experience/src/**` | 829 | — |
| Files in `ui/one-ui-shell/src/**` | 1,005 | — |
| **IDENTICAL** (same path, byte-equal hash) | 449 | Already merged. Cleanup-only. |
| **DRIFTED** (same path, different hash) | 328 | Phase 1/2 union-merge per per-file review. |
| **ONLY-IN-EXPERIENCE** | 52 | Phase 1 lifts each into `ui/one-ui-shell` (with explicit drop reasoning if obsolete). |
| **ONLY-IN-ONE-UI-SHELL** | 228 | Already in destination — Nompilo command bar, journey routes, accessibility toolbar, etc. |

Of the 328 DRIFTED files, 57 are larger in `ui/experience` than in `ui/one-ui-shell` — these are the priority candidates for *experience-side capability adoption* during the merge.

## Bucket A — IDENTICAL (449 files)

Same path in both trees, byte-identical content. Already merged in effect. Will disappear with the folder in Phase 6 with zero capability loss.

Examples (not exhaustive — full list available from `.tmp-merge-inventory.json` regeneration):

- `/app/finance/commerce-integrations/page.tsx`
- `/components/clinical/ClinicalToolbar.tsx`
- `/components/clinical/ClinicalKnowledgeDock.tsx`
- `/middleware.ts`

No action required for these files.

## Bucket B — ONLY-IN-EXPERIENCE (52 files) — must lift or document obsolete

Every file below exists only in `ui/experience/src/**`. Each is assigned to a Phase 1 wave. If any file is reclassified as **UNIQUE-TO-EXPERIENCE-OBSOLETE** during execution, the reasoning is recorded inline in the relevant Phase 1 commit message.

### Phase 1a — Clinical-chrome capabilities (8 files)

| File | Size | Disposition |
|---|---|---|
| `/components/clinical/ClinicalSupportStrip.tsx` | 3,271 | Lift to `ui/one-ui-shell/src/components/clinical/` |
| `/components/clinical/ClinicalWizardHeader.tsx` | 7,576 | Lift to `ui/one-ui-shell/src/components/clinical/` |
| `/components/clinical/ClinicalWizardHeader.test.tsx` | 842 | Lift alongside source |
| `/components/clinical/ClinicalWorkflowContext.tsx` | 713 | Lift to `ui/one-ui-shell/src/components/clinical/` |
| `/components/clinical/FloatingClinicalAssist.tsx` | 4,789 | Lift to `ui/one-ui-shell/src/components/clinical/` |
| `/components/clinical/EncounterVitalsGuidance.tsx` | 2,088 | Lift to `ui/one-ui-shell/src/components/clinical/` |
| `/components/clinical/PatientJourneyContextPanel.tsx` | 13,655 | Lift to `ui/one-ui-shell/src/components/clinical/` |
| `/components/clinical/ClinicalFinanceContextStrip.tsx` | 1,405 | Lift to `ui/one-ui-shell/src/components/clinical/` |

Plus the helper module used by `ClinicalWizardHeader`:

| File | Size | Disposition |
|---|---|---|
| `/lib/clinical/encounter-workspace-nav.ts` | 7,890 | Lift to `ui/one-ui-shell/src/lib/clinical/` |
| `/lib/clinical/encounter-workspace-nav.test.ts` | 2,647 | Lift alongside source |

### Phase 1c — Telemedicine workflow capabilities (6 files)

| File | Size | Disposition |
|---|---|---|
| `/components/clinical/TelemedicineWorkflowStrip.tsx` | 1,585 | Lift to `ui/one-ui-shell/src/components/clinical/` |
| `/components/clinical/TelemedicineWorkflowLegend.tsx` | 1,094 | Lift to `ui/one-ui-shell/src/components/clinical/` |
| `/components/clinical/TelemedicineAssistantSignals.tsx` | 2,797 | Lift to `ui/one-ui-shell/src/components/clinical/` |
| `/lib/clinical/telemedicine-facility-lens.ts` | 1,902 | Lift to `ui/one-ui-shell/src/lib/clinical/` |
| `/lib/clinical/telemedicine-facility-lens.test.ts` | 2,363 | Lift alongside source |
| `/lib/clinical/telemedicine-workflow-stages.ts` | 1,483 | Lift to `ui/one-ui-shell/src/lib/clinical/` |
| `/lib/clinical/telemedicine-workflow-stages.test.ts` | 1,715 | Lift alongside source |

### Phase 1d — Registry localization + Vito wizard (6 files)

| File | Size | Disposition |
|---|---|---|
| `/components/registry/CountryPicker.tsx` | 2,359 | Lift to `ui/one-ui-shell/src/components/registry/` |
| `/components/registry/ZimbabweLocationCascader.tsx` | 9,129 | Lift to `ui/one-ui-shell/src/components/registry/` |
| `/components/registry/VitoClientRegistrationWizard.tsx` | 21,843 | Lift to `ui/one-ui-shell/src/components/registry/` |
| `/lib/registry/iso3166.ts` | 1,798 | Lift to `ui/one-ui-shell/src/lib/registry/` |
| `/lib/registry/iso3166.test.ts` | 1,435 | Lift alongside source |
| `/lib/registry/zimbabweAdmin.ts` | 278 | Lift to `ui/one-ui-shell/src/lib/registry/` |

Plus the BFF-backed walk-in flow rewire (Phase 1d updates the *DRIFTED* `/app/queue/walk-in/page.tsx` to import the new `VitoClientRegistrationWizard`).

### Phase 1e — Clinical-forms subsystem (16 files, COEXIST decision)

User decision (2026-05-28): coexist. Lift the entire subsystem into `ui/one-ui-shell` and route by form type.

| File | Size | Disposition |
|---|---|---|
| `/lib/clinical-forms/index.ts` | 1,074 | Lift to `ui/one-ui-shell/src/lib/clinical-forms/` |
| `/lib/clinical-forms/types.ts` | 4,679 | Lift |
| `/lib/clinical-forms/patient-context.ts` | 1,738 | Lift |
| `/lib/clinical-forms/evaluate-visibility.ts` | 2,453 | Lift |
| `/lib/clinical-forms/validate-form.ts` | 1,580 | Lift |
| `/lib/clinical-forms/clinical-form-definitions/antenatal-contact-1-exemplar.ts` | 8,113 | Lift |
| `/lib/clinical-forms/clinical-form-renderer/DakFormRenderer.tsx` | 10,029 | Lift |
| `/lib/clinical-forms/clinical-form-renderer/ActiveDataEntryLayout.tsx` | 1,817 | Lift |
| `/lib/clinical-forms/clinical-form-renderer/useClinicalFormDraft.ts` | 1,817 | Lift |
| `/lib/clinical-forms/dak-mapping/who-dak-antenatal-care-v1.ts` | 1,612 | Lift |
| `/lib/clinical-forms/decision-support-hooks/anc-decision-support.ts` | 1,215 | Lift |
| `/lib/clinical-forms/fhir-questionnaire-mapping/to-questionnaire-response.ts` | 2,374 | Lift |
| `/lib/clinical-forms/indicator-mapping/anc-indicators.ts` | 445 | Lift |
| `/lib/clinical-forms/terminology-bindings/anc-codes.ts` | 629 | Lift |
| `/lib/clinical-forms/vitals-reference/evaluate.ts` | 2,385 | Lift |
| `/lib/clinical-forms/vitals-reference/metadata.ts` | 5,298 | Lift |
| `/lib/clinical-forms/__tests__/clinical-forms.test.ts` | 4,098 | Lift |

Plus a dispatcher in the merged encounter page (Phase 1e edits the DRIFTED `/app/ehr/[patientId]/encounter/[encounterId]/page.tsx`) that routes DAK-keyed forms to `DakFormRenderer` and other forms to the existing `RoleSpecificEncounterForm`. Architecture rationale documented in `docs/frontend/ENCOUNTER_FORM_ARCHITECTURE.md`.

### Phase 1f — Page stragglers + Mvumo comms (10 files)

| File | Size | Disposition |
|---|---|---|
| `/app/ehr/[patientId]/layout.tsx` | 451 | Lift to `ui/one-ui-shell/src/app/ehr/[patientId]/layout.tsx` (single shell wrapper for all chart routes) |
| `/app/ehr/[patientId]/preferences/communications/page.tsx` | 3,872 | Lift to same path under one-ui-shell; register in `routes.ts` |
| `/hooks/queries/useMvumoCommsEvaluate.ts` | 1,183 | Lift to `ui/one-ui-shell/src/hooks/queries/` |
| `/app/facility-operations/page.tsx` | 3,777 | Lift to `ui/one-ui-shell/src/app/operations/facility-operations/page.tsx` (path change to match `routes.ts` line 383) |
| `/app/facility-operations/page.test.tsx` | 2,758 | Lift alongside (adjusted import path) |
| `/app/facility-operations/district-view/page.tsx` | 7,956 | Lift to `ui/one-ui-shell/src/app/operations/facility-operations/district-view/page.tsx` |
| `/app/facility-operations/patient-flow/page.tsx` | 9,996 | Lift to `ui/one-ui-shell/src/app/operations/facility-operations/patient-flow/page.tsx` |
| `/app/facility-operations/patient-flow/page.test.tsx` | 4,070 | Lift alongside |
| `/app/facility-operations/resources/page.tsx` | 4,588 | Lift to `ui/one-ui-shell/src/app/operations/facility-operations/resources/page.tsx` |
| `/lib/operations/facilityOperationsNav.ts` | 4,974 | Lift to `ui/one-ui-shell/src/lib/operations/` |
| `/lib/operations/facilityOperationsNav.test.ts` | 2,325 | Lift alongside source |

After Phase 1f, register the four new `/operations/facility-operations*` routes against `routes.ts` and bump `EXPECTED_ROUTE_COUNT` from 370 to 374.

### Phase 2 unique helper

| File | Size | Disposition |
|---|---|---|
| `/lib/finance/tariff-library-groups.test.ts` | 1,907 | Lift to `ui/one-ui-shell/src/lib/finance/__tests__/` (companion to existing shell tariff-library-groups module) |

## Bucket C — DRIFTED-EXPERIENCE-LARGER (57 files) — priority union-merge candidates

These are files where the experience tree's version is larger than the shell tree's version, suggesting experience-side capability that may not yet be in shell. Each requires a per-file review during Phase 2 (or the relevant Phase 1 wave if the file is structurally tied to a capability lift).

Top-priority entries (delta > 1KB):

| Delta | File | Phase | Note |
|---|---|---|---|
| +8,436 | `/app/ehr/[patientId]/encounter/[encounterId]/page.tsx` | 1e | DAK form dispatch logic from experience version. |
| +6,572 | `/app/ehr/[patientId]/encounters/page.tsx` | 2 | Encounter list capabilities. |
| +6,317 | `/app/inventory/stock-management/page.tsx` | 2 | Stock-management features. |
| +4,476 | `/app/queue/page.tsx` | 2 | Queue dashboard features. |
| +3,922 | `/app/ehr/[patientId]/summary/page.tsx` | 2 | Summary tab features. |
| +3,511 | `/components/EHRLayout.tsx` | 1b | Adopt experience version (clinical chrome + wizard). |
| +3,387 | `/app/ehr/[patientId]/imaging/page.tsx` | 2 | Imaging tab features. |
| +3,293 | `/app/admin/keys/page.tsx` | 2 | Admin keys page. Note `routes.ts` marks /admin/keys as Blocked (typed BFF unavailable) — verify experience version still respects that. |
| +3,042 | `/components/notifications/NotificationsCommsHub.tsx` | 2 | Notification hub features. |
| +2,876 | `/app/telemedicine/new/page.tsx` | 1c or 2 | Telemedicine new-session features. |
| +2,640 | `/app/admin/federation/page.tsx` | 2 | Federation admin (Blocked per `routes.ts` — verify). |
| +2,496 | `/app/telemedicine/page.tsx` | 1c | Telemedicine workflow strip integration. |
| +1,422 | `/hooks/queries/useInventory.ts` | 2 | Inventory hook features. |
| +1,182 | `/app/ehr/[patientId]/encounter/[encounterId]/page.test.tsx` | 1e | Encounter test cases. |

Remaining 43 entries with smaller deltas (`+5` through `+997`) — most of these are likely incremental edits in experience that need per-file review, but the small magnitude suggests they're either (a) bug fixes already in shell under a different shape, or (b) minor experience-only additions safely foldable into shell.

The full list is in `.tmp-merge-inventory.json` and the regeneration script. During Phase 2 each such file is opened and reviewed; the merge commit notes which lines came from experience.

## Bucket D — DRIFTED-SHELL-LARGER (271 files) — likely shell already canonical

These are files where the shell version is larger than the experience version, suggesting the shell is already the canonical superset. Per-file review during Phase 2 confirms this; the default disposition is "shell version stays, experience copy disappears with the folder."

Notable entries already verified by inspection during Phase 0 read:

- `/lib/routes.ts` (exp=373 lines, shell=543) — shell is canonical superset.
- `/app/finance/costa/page.tsx` (exp=187, shell=610) — shell is canonical superset.
- `/lib/api-client.ts` (exp=423, shell=438) — shell newer; verify no experience-only behaviour.

## Out-of-tree references

Per Phases 3–5 of the plan, the following non-`src/` references to `ui/experience` get repointed to `ui/one-ui-shell`:

- `.github/workflows/ci.yml` — `frontend-lint`, `frontend-test`, `e2e-test`, `e2e-compose-smoke`
- `scripts/runtime-validation/build-all.sh`, `scripts/smoke/route-parity.sh`, `scripts/experience/smoke/ui-route-parity.sh`
- `scripts/clinical/inspect-ehr-workflows.sh`, `scripts/clinical/run-ehr-steel-threads.sh`
- `scripts/lovable-fidelity/audit-{component,flow,page}-fidelity.sh`, `scripts/lovable-fidelity/discover-lovable-sources.sh`
- `scripts/completeness/generate-completeness-report.mjs` line 29 (`UI_ROOT`)
- `scripts/completeness/sync-registry-ui-refs.mjs` line 16 (output path)
- `docker-compose.build.yml` line 14 (header comment)
- `docs/architecture/services-registry.yaml` lines 7316, 7337, 7344, 7345 (`module_path`, `deployment_unit`)
- `docs/architecture/core-transaction-service-compliance.yaml` lines 2578–2579
- `compose/experience/README.md` (Stage-1 paragraph + `cd ui/experience` references)
- `CLAUDE.md` — "Golden Thread" `ui/experience/src/lib/api-client.ts` → `ui/one-ui-shell/src/lib/api-client.ts`
- `AGENTS.md` — "Testing Guidelines" `playwright.config.ts at ui/experience/` → `at ui/one-ui-shell/`

The Keycloak `experience-ui` OIDC client id, the `experience-bff` service, the `zw.gov.mohcc.impilo.experience.*` Java package, the `experience` architectural plane label, and the `compose/experience/` directory all stay — these are not the UX stack.

## Sign-off

Phase 6 (folder removal + workspaces drop) is gated on this inventory being current and on Phases 1a–1f + Phase 2 having landed and passed CI. The guardrail in Phase 8 ensures the merged-from fork cannot be silently re-created.
