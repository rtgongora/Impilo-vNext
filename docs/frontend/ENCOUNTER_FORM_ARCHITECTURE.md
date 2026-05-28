# Encounter Form Architecture — DAK forms + Role-specific forms (COEXIST)

> Decision recorded: 2026-05-28. Lands as part of GAP-010 Phase 1e of the
> `ui/experience` ↔ `ui/one-ui-shell` convergence.

## Context

Two encounter-form systems evolved in parallel during the divergent fork:

- **`ui/one-ui-shell`** ships a discipline-aware role-specific renderer:
  `RoleSpecificEncounterForm` (in `src/components/encounter/StructuredEncounterForms.tsx`),
  which dispatches to one of ~22 cadre-specific structured form variants
  (e.g. `NURSE`, `PHYSIOTHERAPIST`, `RADIOGRAPHER`, `CHW`, `EHO`, `PHARMACIST`)
  based on the active provider's role groups.
- **`ui/experience`** ships a WHO DAK–aligned clinical-form subsystem under
  `src/lib/clinical-forms/`: `DakFormRenderer`, FHIR Questionnaire mapping
  (`fhir-questionnaire-mapping/to-questionnaire-response.ts`), CodeableConcept
  terminology bindings (`terminology-bindings/anc-codes.ts`), indicator
  mapping (`indicator-mapping/anc-indicators.ts`), decision-support hooks
  (`decision-support-hooks/anc-decision-support.ts`), validation, and
  visibility evaluation. The first exemplar definition is
  Antenatal-contact-1 (`clinical-form-definitions/antenatal-contact-1-exemplar.ts`).

These are **not duplicates of one another**. They solve adjacent but different
problems:

- `RoleSpecificEncounterForm` is the right primary capture for general
  encounters because the structured fields differ by who is documenting
  (a physiotherapist's HPI ≠ a radiographer's). It is the long-tail discipline
  catalogue.
- `DakFormRenderer` is the right primary capture for **public-health
  programmes** (ANC, postnatal, EPI, HIV care plan, NCD) where the WHO DAK
  defines the canonical form, indicators, and FHIR Questionnaire shape that
  the system must conform to regardless of cadre.

## Decision: COEXIST by form type, dispatch in the encounter page

Both renderers ship in the converged `ui/one-ui-shell`. The encounter page
(`src/app/ehr/[patientId]/encounter/[encounterId]/page.tsx`) acts as the
dispatcher:

- **DAK panel** — gated on `isClinical && isActive && dakRuntime`, where
  `dakRuntime` is computed from the encounter's `encounterType` matching
  `ANC` / `ANTENATAL` / `MATERNITY` / `OBSTETRIC` and the patient's
  `gender`. When that gate opens, the WHO DAK Antenatal-contact-1 exemplar
  is offered (with a focus mode via `ActiveDataEntryLayout` that demotes
  the journey panel and CDS alerts to a side rail).
- **Role-specific notes panel** — always available for clinicians while the
  encounter is active. `RoleSpecificEncounterForm` picks the cadre-specific
  variant based on the user's roles and persists the structured payload
  through the existing clinical-notes endpoint.

The two surfaces operate independently and can be used in the same
encounter (e.g. an ANC visit where the midwife uses the DAK form for the
WHO-mandated capture and the role-specific renderer for free-text PROGRESS
notes).

## Rejected alternatives

- **Adopt DAK as canonical and retire `RoleSpecificEncounterForm`.** This
  would lose the discipline-aware structured capture for non-programme
  encounters and force every cadre into a single generic shape, which
  the existing `RoleSpecificEncounterForm` deliberately avoids.
- **Adopt RoleSpecific as canonical and retire DAK.** This would lose the
  WHO DAK alignment, FHIR Questionnaire mapping, indicator computation, and
  decision-support hooks the public-health programmes depend on.

## Programme catalogue (initial)

| Form | Definition file | Status |
|---|---|---|
| Antenatal contact 1 (WHO DAK ANC v1) | `lib/clinical-forms/clinical-form-definitions/antenatal-contact-1-exemplar.ts` | Available — exemplar |
| ANC contacts 2–8 | _not yet defined_ | Backlog |
| Postnatal contacts | _not yet defined_ | Backlog |
| EPI contact | _not yet defined_ | Backlog |
| HIV care plan | _not yet defined_ | Backlog |
| NCD chronic-care | _not yet defined_ | Backlog |

New programme forms are added by:

1. Creating a `ClinicalFormDefinition` under
   `lib/clinical-forms/clinical-form-definitions/`.
2. Wiring its DAK → indicator and DAK → terminology bindings under the
   matching `dak-mapping/`, `indicator-mapping/`, and `terminology-bindings/`
   subfolders.
3. Extending the encounter-page dispatcher gate (`dakRuntime` computation +
   the `<DakFormRenderer form={...}>` selection) to recognise the encounter
   type.

## Test surface

`src/lib/clinical-forms/__tests__/clinical-forms.test.ts` covers visibility
evaluation, validation, vitals reference checks, and indicator computation.
The encounter-page dispatcher inherits the existing
`src/app/ehr/[patientId]/encounter/[encounterId]/page.test.tsx` which is
extended in this same wave to cover DAK gate selection.

## File-level change

| File | Status |
|---|---|
| `lib/clinical-forms/**` (16 files) | Lifted from `ui/experience` |
| `components/clinical/EncounterVitalsGuidance.tsx` | Lifted from `ui/experience` |
| `app/ehr/[patientId]/encounter/[encounterId]/page.tsx` | Adopted experience version (DAK + RoleSpecific dispatcher); preserved shell `<EHRLayout>` wrapper until Phase 1b's segment-layout lands |
