# Specialty tool ownership map

Every specialty tool label advertised by the provider app, its current state, and the lane that
owns completing it. Generated from `apps/mobile/provider-app/src/data/specialtyToolRegistry.ts`,
which is the source of truth the app itself reads — this file cannot drift from behaviour without
the guard test failing.

**Why this exists.** Tool behaviour used to be chosen by a label's position in an array, so
Partograph rendered as a free-text box because it was first in its list and "Risk Assessment"
rendered a two-number adder because it was fourth. The positional mechanism is gone. Labels are
not deleted to hide gaps: an unbuilt tool says it is unbuilt and names who is completing it.

**States.** *Consolidated* — the real implementation lives elsewhere in the app and this entry
opens it. *In development* — not built here, owner and wave named. *Withdrawn (safety)* — also in
development, but the previous behaviour was actively unsafe and was removed ahead of routing.

## Consolidations and reference cleanup

| What | Where it went | References cleaned in the same change |
|---|---|---|
| Panel APGAR form | `APGARScreen` (already wired to `POST /internal/v1/apgar` → `inpatient.apgar_score`) | duplicate `ApgarForm` deleted; `recordApgar` import dropped from the panel |
| BFF `GET /workspaces/specialties` | Governed `forms-service` catalogue via the encounter-forms path | handler removed with a pointer comment; `fetchSpecialtyWorkspaces` client removed; `contracts/openapi/experience-bff.openapi.yaml` path removed; `reports/product/capability-matrix.json` regenerates from source |

The capability-matrix entry is generated from backend routes, so it clears on the next regen
rather than being hand-edited. The move is recorded here and in the handler comment, which are the
durable homes — hand-editing a generated report would be overwritten and would read as truth.

## The map

| Workspace | Tool | State | Owner | Wave |
|---|---|---|---|---|
| Anaesthesia | Pre-op Assessment | In development | Surgery | TBC |
| Anaesthesia | ASA Classification | In development | Surgery | TBC |
| Anaesthesia | Airway Assessment (Mallampati) | In development | Surgery | TBC |
| Anaesthesia | Anaesthetic Plan | In development | Surgery | TBC |
| Anaesthesia | Recovery Checklist | In development | Surgery | TBC |
| Anaesthesia | Pain Protocol | In development | Surgery | TBC |
| Burns Unit | Burns Assessment (Rule of 9s) | **Withdrawn (safety)** | Emergency | W1 |
| Burns Unit | Fluid Resuscitation (Parkland) | **Withdrawn (safety)** | Emergency | W1 |
| Burns Unit | Wound Chart | In development | Emergency | W1 |
| Burns Unit | Graft Planning | In development | Emergency | W1 |
| Burns Unit | Pain Ladder | In development | Emergency | W1 |
| Burns Unit | Nutrition Plan | In development | Emergency | W1 |
| Cardiology | ECG Interpretation | In development | AdultMedicine | W6+ |
| Cardiology | Troponin Tracker | In development | AdultMedicine | W6+ |
| Cardiology | ACS Protocol | In development | AdultMedicine | W6+ |
| Cardiology | Heart Failure Assessment | In development | AdultMedicine | W6+ |
| Cardiology | Anticoagulation Plan | In development | AdultMedicine | W6+ |
| Cardiology | Cardiac Rehab | In development | AdultMedicine | W6+ |
| Chemotherapy | Chemo Protocol Selection | In development | AdultMedicine | W6+ |
| Chemotherapy | Dose Calculator (BSA) | In development | AdultMedicine | W6+ |
| Chemotherapy | Pre-Chemo Checklist | In development | AdultMedicine | W6+ |
| Chemotherapy | Toxicity Grading (CTCAE) | In development | AdultMedicine | W6+ |
| Chemotherapy | Antiemetic Protocol | In development | AdultMedicine | W6+ |
| Chemotherapy | Blood Count Review | In development | AdultMedicine | W6+ |
| Dermatology | Lesion Mapping | In development | AdultMedicine | W6+ |
| Dermatology | Biopsy Request | In development | AdultMedicine | W6+ |
| Dermatology | Phototherapy Log | In development | AdultMedicine | W6+ |
| Dermatology | Dermatology Atlas | In development | AdultMedicine | W6+ |
| Dermatology | Patch Test Record | In development | AdultMedicine | W6+ |
| Dermatology | Wound Assessment | In development | AdultMedicine | W6+ |
| Dialysis | Dialysis Prescription | In development | AdultMedicine | W6+ |
| Dialysis | Fluid Balance | In development | AdultMedicine | W6+ |
| Dialysis | Kt/V Calculator | In development | AdultMedicine | W6+ |
| Dialysis | Access Assessment | In development | AdultMedicine | W6+ |
| Dialysis | Electrolyte Tracker | In development | AdultMedicine | W6+ |
| Dialysis | Dry Weight Trend | In development | AdultMedicine | W6+ |
| ENT | Audiometry Record | In development | Surgery | TBC |
| ENT | Tympanogram | In development | Surgery | TBC |
| ENT | Flexible Nasendoscopy | In development | Surgery | TBC |
| ENT | Voice Assessment | In development | Surgery | TBC |
| ENT | Thyroid Nodule FNA | In development | Surgery | TBC |
| ENT | Sleep Study Request | In development | Surgery | TBC |
| Gastroenterology | Endoscopy Report | In development | AdultMedicine | W6+ |
| Gastroenterology | Liver Function Trend | In development | AdultMedicine | W6+ |
| Gastroenterology | MELD Score | In development | AdultMedicine | TBC |
| Gastroenterology | Child-Pugh Score | In development | AdultMedicine | TBC |
| Gastroenterology | IBD Activity Index | In development | AdultMedicine | W6+ |
| Gastroenterology | Nutrition Assessment | In development | AdultMedicine | W6+ |
| Haematology | Blood Film Review | In development | AdultMedicine | W6+ |
| Haematology | Coagulation Panel | In development | AdultMedicine | W6+ |
| Haematology | Transfusion Request | In development | AdultMedicine | W6+ |
| Haematology | Sickle Cell Crisis Protocol | In development | AdultMedicine | W6+ |
| Haematology | Bone Marrow Report | In development | AdultMedicine | W6+ |
| Haematology | Anticoagulation Clinic | In development | AdultMedicine | W6+ |
| Intensive Care | APACHE II Score | In development | AdultMedicine | TBC |
| Intensive Care | SOFA Score | In development | AdultMedicine | TBC |
| Intensive Care | Ventilator Settings | In development | Emergency | TBC |
| Intensive Care | Sedation (RASS) | In development | Emergency | TBC |
| Intensive Care | Nutrition (NUTRIC) | In development | Emergency | TBC |
| Intensive Care | Daily ICU Checklist | In development | Emergency | TBC |
| Neonatal | APGAR Record | Consolidated → `APGARScreen` | — | real screen, persists |
| Neonatal | Gestational Age Assessment | In development | RMNP | TBC |
| Neonatal | Growth Chart (Fenton) | In development | Paediatrics | TBC |
| Neonatal | Surfactant Protocol | In development | RMNP | TBC |
| Neonatal | Bilirubin Chart | In development | RMNP | TBC |
| Neonatal | Feeding Plan | In development | RMNP | TBC |
| Nephrology | eGFR Trend | In development | AdultMedicine | W6+ |
| Nephrology | Urinalysis Review | In development | AdultMedicine | W6+ |
| Nephrology | Biopsy Report | In development | AdultMedicine | W6+ |
| Nephrology | Transplant Assessment | In development | AdultMedicine | W6+ |
| Nephrology | Immunosuppression Protocol | In development | AdultMedicine | W6+ |
| Nephrology | Dialysis Access | In development | AdultMedicine | W6+ |
| Neurology | NIHSS Score | In development | Emergency | TBC |
| Neurology | GCS Tracker | In development | Emergency | TBC |
| Neurology | Seizure Log | In development | AdultMedicine | W6+ |
| Neurology | Lumbar Puncture Record | In development | AdultMedicine | W6+ |
| Neurology | MS Relapse Assessment | In development | AdultMedicine | W6+ |
| Neurology | Cognitive Screen (MMSE/MoCA) | In development | AdultMedicine | W6+ |
| Obstetrics | Partograph | In development | RMNP | governed form delivered · evidence on file |
| Obstetrics | CTG Interpretation | In development | RMNP | governed form delivered · evidence on file |
| Obstetrics | Bishop Score | In development | RMNP | TBC |
| Obstetrics | PPH Protocol | In development | RMNP | TBC |
| Obstetrics | Eclampsia Protocol | In development | RMNP | TBC |
| Obstetrics | Neonatal Resuscitation | In development | RMNP | TBC |
| Oncology | Staging (TNM) | In development | AdultMedicine | W6+ |
| Oncology | Performance Status (ECOG) | In development | AdultMedicine | W6+ |
| Oncology | Treatment Plan | In development | AdultMedicine | W6+ |
| Oncology | Symptom Assessment (ESAS) | In development | AdultMedicine | W6+ |
| Oncology | Palliative Care Needs | In development | AdultMedicine | W6+ |
| Oncology | MDT Summary | In development | AdultMedicine | W6+ |
| Ophthalmology | Visual Acuity Record | In development | Surgery | TBC |
| Ophthalmology | IOP Measurement | In development | Surgery | TBC |
| Ophthalmology | Fundoscopy Report | In development | Surgery | TBC |
| Ophthalmology | Visual Field Test | In development | Surgery | TBC |
| Ophthalmology | Slit Lamp Findings | In development | Surgery | TBC |
| Ophthalmology | Refraction Record | In development | Surgery | TBC |
| Orthopaedics | Fracture Classification | In development | Surgery | TBC |
| Orthopaedics | Neurovascular Check | In development | Surgery | TBC |
| Orthopaedics | Cast/Splint Record | In development | Surgery | TBC |
| Orthopaedics | ROM Assessment | In development | Surgery | TBC |
| Orthopaedics | VTE Prophylaxis | In development | Surgery | TBC |
| Orthopaedics | Rehab Milestones | In development | Surgery | TBC |
| Psychiatry | Mental State Examination | In development | Emergency | W13 (mental-health) |
| Psychiatry | PHQ-9 | In development | Emergency | forms-service questionnaire now; interpretation awaits mental-health-service (8397) |
| Psychiatry | GAD-7 | In development | Emergency | forms-service questionnaire now; interpretation awaits mental-health-service (8397) |
| Psychiatry | Risk Assessment | **Withdrawn (safety)** | Emergency | TBC |
| Psychiatry | Capacity Assessment | In development | Emergency | W13 (mental-health) |
| Psychiatry | Section/Involuntary Hold | In development | Emergency | W13 (mental-health) |

## Routing status

All 108 labels are routed to a named owning lane. Ownership was assigned at
workspace level by the coordinator; an owning lane may flag an individual label back rather than
claim it, in which case it returns here rather than becoming unowned. A guard test fails if any
entry reverts to an unassigned owner.

