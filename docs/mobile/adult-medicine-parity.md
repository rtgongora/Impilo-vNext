# Adult medicine — mobile parity (Wave 7 first mirrors)

**Branch:** `claude/adult-medicine-waves-0-2`  
**Date:** 2026-07-31  
**Scope:** Provider app first mirrors of the web adult-medicine pack — honest partial parity only.

## Status summary

| Surface | Web route / BFF | Mobile location | Status |
|---|---|---|---|
| Medicine workspace (programmes, problems, allergies) | `/ehr/[patientId]/medicine` · `/internal/v1/programmes`, `/conditions`, `/allergies` | Encounter → **Medicine** tab; Tools → **Medicine** | **PARTIAL** — read compose with unavailable ≠ empty |
| Medicine CDS evaluate (8 topics) | `/ehr/[patientId]/medicine/cds` · `POST /internal/v1/medicine/cds/{topic}/evaluate` | Encounter → Medicine (embedded); Tools → **Med CDS** | **PARTIAL** — evaluate only; no fact forms |
| Clerking continuity | `/ehr/[patientId]/history` · `/internal/v1/clerking/*` | Encounter → **Clerking**; Tools → **Clerking** | **PARTIAL** — problems + visit attestations read-only |
| Chronic registers worklist | `/clinical/chronic-registers` · `GET /internal/v1/programmes/register` | Tools → **Registers** | **PARTIAL** — facility-scoped list; no control write-back |
| Examination (§7) | `/ehr/[patientId]/examination` | — | **NOT BUILT** on mobile |
| Multimorbidity view | `/ehr/[patientId]/multimorbidity` | — | **NOT BUILT** on mobile |
| Thirteen specialty workspaces (§8) | `/ehr/[patientId]/medicine/specialty/{key}` | Tools → Specialty (108 labels) | **NOT BUILT** — registry stays `IN_DEVELOPMENT`; web spine noted on cardiology labels |
| Ambulatory order sets / renal dosing | Medicine workspace panels on web | — | **NOT BUILT** on mobile |
| Legacy generic CDS tab | `/internal/v1/mobile/provider/clinical/cds/evaluate` | Tools → CDS | **Separate** — not medicine-pack CDS; kept unchanged |

## Honesty rules (enforced on mobile mirrors)

1. Failed reads render as **unavailable**, never as empty clinical claims.
2. **NOT BUILT** panels name what remains web-only (examination, specialty tools, clerking write-back).
3. Specialty tool registry does **not** mark AdultMedicine labels `WIRED` unless a real mobile surface exists.

## Tests

- `apps/mobile/provider-app/src/__tests__/provider/MedicineWorkspace.test.ts` — pure summary grouping
- Existing `SpecialtyWorkspaceTools.test.ts` — registry guard unchanged (no false WIRED)

## Related web docs

- `docs/clinical/adult-medicine-domain-pack/implementation-report.md`
- `ui/one-ui-shell/src/features/medicine/workspace/MedicineWorkspaceShell.tsx`
