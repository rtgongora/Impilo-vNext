# WHO DAK–aligned clinical forms — implementation audit

**Scope:** Impilo vNext (`ui/experience`, `ui/ehr`, `ui/shared-ui`, BFF/clinical services under `services/`, `docs/clinical`, `docs/product`, `docs/architecture`).  
**Date:** April 2026  
**Intent:** Document the pre-existing ad hoc form landscape, the new DAK-aware framework, one exemplar, and remaining gaps.

---

## 1. WHO DAKs reviewed (conceptual alignment)

Implementation aligns with the **WHO Digital Adaptation Kit (DAK)** pattern: software-neutral packaging of guidelines into personas, workflows, core data elements, decision support, indicators, and non-functional expectations. For this pass we **did not bulk-import** every published WHO DAK artefact; we anchored on:

| DAK / guideline family | Use in Impilo vNext |
|------------------------|---------------------|
| **WHO Antenatal Care (ANC)** | Reference metadata, first-contact business process steps, programme indicators, decision-support placeholders, and the **Antenatal contact 1** exemplar form. |
| **SMART Guidelines / FHIR** | `Questionnaire` / `QuestionnaireResponse` mapping layer for structured answers (especially coded values). |

Future passes should add explicit citations to specific WHO publication IDs and L2/L3 artefacts as they are onboarded.

---

## 2. Pre-change audit — where forms lived

### 2.1 `ui/experience` (primary clinician workspace)

| Area | Location / pattern | Dominant field types |
|------|----------------------|------------------------|
| **Vitals** | Encounter page, dedicated vitals route | Numeric (+ implicit unit in label/placeholder), free-text pain where used |
| **Triage** | `/queue/triage`, encounter triage panel | Acuity (ordinal / segmented), danger signs (boolean map over string labels), quick vitals (numeric), notes (free text) |
| **Examination** | Encounter — system-by-system text areas | **Free text** per system |
| **SOAP / notes** | Encounter clinical note | Type (coded string select), SOAP + body — **free text** heavy |
| **Orders / results** | EHR chart sections, orders page | Mix of API-driven lists and text |
| **Referrals / telemedicine** | Consults, encounters summary | Structured status in API; UI mix of selects and narrative |
| **ANC / maternity** | Maternity hub, partograph/CTG features, encounter type hints | Maternity-specific **visualisations** and flows; prior ANC data entry was not a single DAK-bound definition |
| **Child / growth** | Growth chart page | Numeric + plotting; not previously DAK-metadata-driven |
| **Immunizations** | Chart + BFF endpoints | Coded catalogue where API provides codes; otherwise text |
| **Discharge / visit outcome** | Discharge route linked from encounter | Mixed structured + narrative |

### 2.2 `ui/ehr`

Legacy/shell EHR (`EncounterPanel.tsx`): **VitalsForm**, **DiagnosisForm**, **PrescriptionForm** as local components — mostly **numeric vitals**, **text diagnosis**, **text prescription** without shared DAK metadata.

### 2.3 `ui/shared-ui`

Shared primitives (buttons, layout, etc.); **no** central clinical form definition layer before this work.

### 2.4 Services (`services/experience-bff`, clinical domains)

REST aggregation for vitals, conditions, immunizations, growth, FHIR gateway, clinical notes, queue triage, etc. **Business rules** for clinical thresholds were not consistently centralised for the Experience UI prior to the vitals reference layer.

Named services in the original brief (`zibo-service`, `rules-service`, `butano-service`, `vito-service`, `pct-service`, `oros-service`) may hold additional rules or document storage; **they are not yet wired** to the new client-side form definition package. Follow-up: map each service’s rule endpoints into `decision-support-hooks` and server-side validation.

### 2.5 Documentation (`docs/clinical`, `docs/product`, `docs/architecture`)

Used for product/architecture context; **no** prior single source of truth for DAK form definitions. This audit file closes that gap for the ANC exemplar track.

---

## 3. Field type classification (legacy vs target)

| Legacy pattern | Typical examples | DAK framework target |
|----------------|------------------|----------------------|
| Free text | Examination systems, triage notes, complaint | **Coded choice + optional narrative** |
| Numeric | BP, HR, SpO₂, RR, temp, weight | **Numeric + unit + central thresholds** |
| Boolean / checkbox grid | Danger signs | **Multi-select coded** with optional severity/duration |
| Plain dropdown | Note type | **Coded single** with binding |
| Unbound diagnosis text | Problem list entry in demos | **Terminology-bound** (ICD/SNOMED) |
| Segmented acuity | Triage levels | **`segmented` / radio** with explicit codes |

---

## 4. Form framework created (`ui/experience/src/lib/clinical-forms/`)

| Module | Responsibility |
|--------|----------------|
| `types.ts` | `ClinicalFormDefinition`, sections, fields, visibility rules, validation, versioning, localisation keys, audit metadata, FHIR / indicator / decision-support references |
| `patient-context.ts` | Age band, sex, pregnancy flag derivation from patient record |
| `evaluate-visibility.ts` | Patient-, programme-, encounter-, role-aware visibility |
| `validate-form.ts` | Required, numeric range, text length; NaN-safe |
| `clinical-form-definitions/` | Concrete forms (e.g. ANC contact 1) |
| `clinical-form-renderer/` | `DakFormRenderer`, `ActiveDataEntryLayout`, `useClinicalFormDraft` (localStorage autosave) |
| `dak-mapping/` | WHO DAK reference ids and business process steps |
| `terminology-bindings/` | LOINC/SNOMED-style constants for exemplar |
| `decision-support-hooks/` | Pluggable rules returning alerts |
| `indicator-mapping/` | Programme indicator codes |
| `fhir-questionnaire-mapping/` | `QuestionnaireResponse` item assembly |
| `vitals-reference/` | Central plausible / normal / critical bands; age-aware evaluation |

---

## 5. Exemplar implemented — Antenatal care (first contact)

- **Business process:** `dak-mapping/who-dak-antenatal-care-v1.ts` — `ANC_FIRST_CONTACT_PROCESS` steps.  
- **Structured definition:** `clinical-form-definitions/antenatal-contact-1-exemplar.ts` — `ANTENATAL_CONTACT_1_FORM`.  
- **Coded fields:** Presenting complaint, danger signs, counselling topics, referral decision, etc.  
- **Decision support:** `decision-support-hooks/anc-decision-support.ts` — exemplar gravida / referral placeholders.  
- **Indicators:** `indicator-mapping/anc-indicators.ts`.  
- **FHIR:** `fhir-questionnaire-mapping/to-questionnaire-response.ts` — coded answers emit `valueCoding`.  
- **Rendering:** Encounter workspace — WHO DAK structured forms card, focus mode with `ActiveDataEntryLayout`.  
- **Tests:** `src/lib/clinical-forms/__tests__/clinical-forms.test.ts`.

---

## 6. Free text reduction (this pass)

| Topic | Before | After (exemplar / encounter) |
|-------|--------|------------------------------|
| Presenting complaint | Often narrative-first | **Coded** complaint + optional narrative |
| Counselling / screening | N/A in one blob | **Multi-select coded** |
| Referral | Narrative-only in many flows | **Structured** urgency / reason codes + narrative |

**Still free text by design:** clinical justification, unusual findings, additional notes.

Encounter-level examination and SOAP blocks remain **legacy free text** until migrated into structured definitions.

---

## 7. Patient-aware rules

Implemented via `ClinicalFieldVisibilityRule` evaluation:

- Obstetric fields gated on **female** + relevant **age band** (excludes neonatal for exemplar).  
- Programme / encounter type drives ANC runtime context on the encounter page.

**Gaps:** Known conditions, current medications, and allergies are available for CDS alerts but are **not yet** full visibility dimensions on every field.

---

## 8. Vitals rules (central layer)

- Metadata and thresholds in `vitals-reference/metadata.ts` and `evaluate.ts`.  
- `EncounterVitalsGuidance` consumes evaluation for inline hints on the encounter page.

**Gaps:** Override reason is not yet persisted with the vitals POST payload; trend charts are not driven from the same metadata package.

---

## 9. Active data entry UX

- Toggle **focused structured entry**: primary column for the DAK form; journey/alerts compact in side rail.  
- Draft autosave via `localStorage`; inline validation messages in renderer; progress indicator.

**Gaps:** Full dashboard collapse across all encounter widgets; server-side draft sync.

---

## 10. Verification

Commands run from `ui/experience`:

- `npm run lint` (warnings remain repo-wide; clinical-forms-specific issues addressed where introduced)  
- `npm run type-check`  
- `npm run test`  
- `npm run build`

---

## 11. Remaining gaps (prioritised)

1. **Migrate** examination, SOAP, triage queue composer, and discharge flows to shared definitions.  
2. **Service integration:** persist `QuestionnaireResponse`, indicators, and DS results via BFF + clinical services.  
3. **Terminology:** live ValueSet expansion from a terminology server; replace static constants.  
4. **Additional DAK exemplars:** HIV, TB, immunization, IMCI, postnatal, intrapartum — reuse the same packages.  
5. **Citizen vs provider** field visibility and consent-aware rendering.  
6. **Registry-backed** geographic and facility/provider pickers bound in the form model.  
7. **Offline:** queue drafts when API unavailable; sync conflict handling.

---

## 12. Summary

The repository now contains a **reusable DAK-shaped clinical form framework** in Experience, a **complete ANC first-contact exemplar** with workflow reference, indicators, decision-support hooks, FHIR mapping, patient-aware visibility, central vitals evaluation, focused data-entry UX, and **automated tests**. Legacy pages retain ad hoc forms until progressively ported onto this pattern.
