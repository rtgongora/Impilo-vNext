# Patient Safety — Form pack specification

Versioned form packs are owned by **forms-service**. Reports carry `form_pack_key`,
`form_pack_version` and the captured `form_data` so the intake form and the SoR stay aligned. Field
groups below are derived from the MCAZ source forms (PVF01 Rev10 ADR, Standard AEFI, AEFI
Investigation) and map onto the `ps_*` domain.

## Pack: `patient-safety.adr` (ADR — MCAZ PVF01 Rev10)

| Group | Fields | Maps to |
|---|---|---|
| Patient | initials, CPID, sex, date of birth / age, weight | `ps_safety_report.subject_*` |
| Suspect medicine(s) | name, manufacturer, batch/lot, expiry, dose, route, frequency, therapy start/stop, indication, dechallenge, rechallenge | `ps_product_exposure` (role=SUSPECT) |
| Concomitant medicines | name, dose, route, dates | `ps_product_exposure` (role=CONCOMITANT) |
| Reaction(s) | description (verbatim), MedDRA (optional), onset, end, severity, outcome | `ps_safety_event` |
| Seriousness | serious?, criteria (death, life-threatening, hospitalisation, disability, congenital anomaly, other medically important) | `ps_safety_report.is_serious` / `seriousness_reasons` |
| Reporter | name, profession, facility, phone, email | `ps_safety_report.reporter_*` |
| Narrative | free text clinical course | `ps_safety_report.narrative` |

## Pack: `patient-safety.aefi` (AEFI — Standard AEFI form)

| Group | Fields | Maps to |
|---|---|---|
| Patient | initials, CPID, sex, DOB/age | `ps_safety_report.subject_*` |
| Vaccine(s) | name, manufacturer, batch/lot, expiry, dose number, vaccination date, anatomical site | `ps_product_exposure` (kind=VACCINE) |
| Event(s) | AEFI term, onset interval, severity, outcome | `ps_safety_event` |
| Seriousness | serious?, criteria | `ps_safety_report.is_serious` / `seriousness_reasons` |
| Reporter / facility | reporter, facility, district | `ps_safety_report.reporter_*` |
| Narrative | clinical description | `ps_safety_report.narrative` |

## Pack: `patient-safety.aefi-investigation` (Serious AEFI investigation)

| Group | Fields | Maps to |
|---|---|---|
| Case linkage | case reference, investigation reference, assigned investigator, planned date | `ps_aefi_investigation` |
| Field findings | immunisation error?, cold-chain, programmatic factors, clinical findings, lab results | `ps_aefi_investigation.field_findings` (JSON) |
| Classification | WHO AEFI causality category (consistent / inconsistent / indeterminate / unclassifiable) | `ps_aefi_investigation.final_classification` |
| Summary | investigation summary + recommendations | `ps_aefi_investigation.summary` |

## Seeding (forms-service, separate files)

Each pack is registered in forms-service via its own migration/seed (separate from any other lane's
form packs), using the `fs_form_schemas` / `fs_form_schema_versions` model:
`POST /internal/v1/forms` → `POST /internal/v1/forms/{id}/versions` → `POST /internal/v1/forms/{id}/publish`.
The PoC stores `form_pack_key`/`form_pack_version`/`form_data` on the report; runtime rendering and
schema-validation binding via forms-service is the next integration step (see known-limitations).
