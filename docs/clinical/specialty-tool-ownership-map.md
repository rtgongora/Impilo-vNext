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
| Anaesthesia | Pre-op Assessment | In development | UNASSIGNED | TBC |
| Anaesthesia | ASA Classification | In development | UNASSIGNED | TBC |
| Anaesthesia | Airway Assessment (Mallampati) | In development | UNASSIGNED | TBC |
| Anaesthesia | Anaesthetic Plan | In development | UNASSIGNED | TBC |
| Anaesthesia | Recovery Checklist | In development | UNASSIGNED | TBC |
| Anaesthesia | Pain Protocol | In development | UNASSIGNED | TBC |
| Burns Unit | Burns Assessment (Rule of 9s) | **Withdrawn (safety)** | Emergency | W1 |
| Burns Unit | Fluid Resuscitation (Parkland) | **Withdrawn (safety)** | Emergency | W1 |
| Burns Unit | Wound Chart | In development | UNASSIGNED | TBC |
| Burns Unit | Graft Planning | In development | UNASSIGNED | TBC |
| Burns Unit | Pain Ladder | In development | UNASSIGNED | TBC |
| Burns Unit | Nutrition Plan | In development | UNASSIGNED | TBC |
| Cardiology | ECG Interpretation | In development | UNASSIGNED | TBC |
| Cardiology | Troponin Tracker | In development | UNASSIGNED | TBC |
| Cardiology | ACS Protocol | In development | UNASSIGNED | TBC |
| Cardiology | Heart Failure Assessment | In development | UNASSIGNED | TBC |
| Cardiology | Anticoagulation Plan | In development | UNASSIGNED | TBC |
| Cardiology | Cardiac Rehab | In development | UNASSIGNED | TBC |
| Chemotherapy | Chemo Protocol Selection | In development | UNASSIGNED | TBC |
| Chemotherapy | Dose Calculator (BSA) | In development | UNASSIGNED | TBC |
| Chemotherapy | Pre-Chemo Checklist | In development | UNASSIGNED | TBC |
| Chemotherapy | Toxicity Grading (CTCAE) | In development | UNASSIGNED | TBC |
| Chemotherapy | Antiemetic Protocol | In development | UNASSIGNED | TBC |
| Chemotherapy | Blood Count Review | In development | UNASSIGNED | TBC |
| Dermatology | Lesion Mapping | In development | UNASSIGNED | TBC |
| Dermatology | Biopsy Request | In development | UNASSIGNED | TBC |
| Dermatology | Phototherapy Log | In development | UNASSIGNED | TBC |
| Dermatology | Dermatology Atlas | In development | UNASSIGNED | TBC |
| Dermatology | Patch Test Record | In development | UNASSIGNED | TBC |
| Dermatology | Wound Assessment | In development | UNASSIGNED | TBC |
| Dialysis | Dialysis Prescription | In development | UNASSIGNED | TBC |
| Dialysis | Fluid Balance | In development | UNASSIGNED | TBC |
| Dialysis | Kt/V Calculator | In development | UNASSIGNED | TBC |
| Dialysis | Access Assessment | In development | UNASSIGNED | TBC |
| Dialysis | Electrolyte Tracker | In development | UNASSIGNED | TBC |
| Dialysis | Dry Weight Trend | In development | UNASSIGNED | TBC |
| ENT | Audiometry Record | In development | UNASSIGNED | TBC |
| ENT | Tympanogram | In development | UNASSIGNED | TBC |
| ENT | Flexible Nasendoscopy | In development | UNASSIGNED | TBC |
| ENT | Voice Assessment | In development | UNASSIGNED | TBC |
| ENT | Thyroid Nodule FNA | In development | UNASSIGNED | TBC |
| ENT | Sleep Study Request | In development | UNASSIGNED | TBC |
| Gastroenterology | Endoscopy Report | In development | UNASSIGNED | TBC |
| Gastroenterology | Liver Function Trend | In development | UNASSIGNED | TBC |
| Gastroenterology | MELD Score | In development | AdultMedicine | TBC |
| Gastroenterology | Child-Pugh Score | In development | AdultMedicine | TBC |
| Gastroenterology | IBD Activity Index | In development | UNASSIGNED | TBC |
| Gastroenterology | Nutrition Assessment | In development | UNASSIGNED | TBC |
| Haematology | Blood Film Review | In development | UNASSIGNED | TBC |
| Haematology | Coagulation Panel | In development | UNASSIGNED | TBC |
| Haematology | Transfusion Request | In development | UNASSIGNED | TBC |
| Haematology | Sickle Cell Crisis Protocol | In development | UNASSIGNED | TBC |
| Haematology | Bone Marrow Report | In development | UNASSIGNED | TBC |
| Haematology | Anticoagulation Clinic | In development | UNASSIGNED | TBC |
| Intensive Care | APACHE II Score | In development | AdultMedicine | TBC |
| Intensive Care | SOFA Score | In development | AdultMedicine | TBC |
| Intensive Care | Ventilator Settings | In development | UNASSIGNED | TBC |
| Intensive Care | Sedation (RASS) | In development | Emergency | TBC |
| Intensive Care | Nutrition (NUTRIC) | In development | UNASSIGNED | TBC |
| Intensive Care | Daily ICU Checklist | In development | UNASSIGNED | TBC |
| Neonatal | APGAR Record | Consolidated → `APGARScreen` | — | real screen, persists |
| Neonatal | Gestational Age Assessment | In development | UNASSIGNED | TBC |
| Neonatal | Growth Chart (Fenton) | In development | Paediatrics | TBC |
| Neonatal | Surfactant Protocol | In development | UNASSIGNED | TBC |
| Neonatal | Bilirubin Chart | In development | UNASSIGNED | TBC |
| Neonatal | Feeding Plan | In development | UNASSIGNED | TBC |
| Nephrology | eGFR Trend | In development | UNASSIGNED | TBC |
| Nephrology | Urinalysis Review | In development | UNASSIGNED | TBC |
| Nephrology | Biopsy Report | In development | UNASSIGNED | TBC |
| Nephrology | Transplant Assessment | In development | UNASSIGNED | TBC |
| Nephrology | Immunosuppression Protocol | In development | UNASSIGNED | TBC |
| Nephrology | Dialysis Access | In development | UNASSIGNED | TBC |
| Neurology | NIHSS Score | In development | Emergency | TBC |
| Neurology | GCS Tracker | In development | Emergency | TBC |
| Neurology | Seizure Log | In development | UNASSIGNED | TBC |
| Neurology | Lumbar Puncture Record | In development | UNASSIGNED | TBC |
| Neurology | MS Relapse Assessment | In development | UNASSIGNED | TBC |
| Neurology | Cognitive Screen (MMSE/MoCA) | In development | UNASSIGNED | TBC |
| Obstetrics | Partograph | In development | RMNP | governed form delivered · evidence on file |
| Obstetrics | CTG Interpretation | In development | RMNP | governed form delivered · evidence on file |
| Obstetrics | Bishop Score | In development | RMNP | TBC |
| Obstetrics | PPH Protocol | In development | UNASSIGNED | TBC |
| Obstetrics | Eclampsia Protocol | In development | UNASSIGNED | TBC |
| Obstetrics | Neonatal Resuscitation | In development | UNASSIGNED | TBC |
| Oncology | Staging (TNM) | In development | UNASSIGNED | TBC |
| Oncology | Performance Status (ECOG) | In development | UNASSIGNED | TBC |
| Oncology | Treatment Plan | In development | UNASSIGNED | TBC |
| Oncology | Symptom Assessment (ESAS) | In development | UNASSIGNED | TBC |
| Oncology | Palliative Care Needs | In development | UNASSIGNED | TBC |
| Oncology | MDT Summary | In development | UNASSIGNED | TBC |
| Ophthalmology | Visual Acuity Record | In development | UNASSIGNED | TBC |
| Ophthalmology | IOP Measurement | In development | UNASSIGNED | TBC |
| Ophthalmology | Fundoscopy Report | In development | UNASSIGNED | TBC |
| Ophthalmology | Visual Field Test | In development | UNASSIGNED | TBC |
| Ophthalmology | Slit Lamp Findings | In development | UNASSIGNED | TBC |
| Ophthalmology | Refraction Record | In development | UNASSIGNED | TBC |
| Orthopaedics | Fracture Classification | In development | UNASSIGNED | TBC |
| Orthopaedics | Neurovascular Check | In development | UNASSIGNED | TBC |
| Orthopaedics | Cast/Splint Record | In development | UNASSIGNED | TBC |
| Orthopaedics | ROM Assessment | In development | UNASSIGNED | TBC |
| Orthopaedics | VTE Prophylaxis | In development | UNASSIGNED | TBC |
| Orthopaedics | Rehab Milestones | In development | UNASSIGNED | TBC |
| Psychiatry | Mental State Examination | In development | UNASSIGNED | TBC |
| Psychiatry | PHQ-9 | In development | Emergency | forms-service questionnaire now; interpretation awaits mental-health-service (8397) |
| Psychiatry | GAD-7 | In development | Emergency | forms-service questionnaire now; interpretation awaits mental-health-service (8397) |
| Psychiatry | Risk Assessment | **Withdrawn (safety)** | Emergency | TBC |
| Psychiatry | Capacity Assessment | In development | UNASSIGNED | TBC |
| Psychiatry | Section/Involuntary Hold | In development | UNASSIGNED | TBC |

## Awaiting a routing decision (91)

These have no owning lane yet. They are **not** orphan withdrawals — each says plainly in-app that
it is unbuilt and that its owner is being assigned — but they are not finished until a lane is
named. Grouped by workspace for a single coordinator decision:

- **Anaesthesia** — Pre-op Assessment, ASA Classification, Airway Assessment (Mallampati), Anaesthetic Plan, Recovery Checklist, Pain Protocol
- **Burns Unit** — Wound Chart, Graft Planning, Pain Ladder, Nutrition Plan
- **Cardiology** — ECG Interpretation, Troponin Tracker, ACS Protocol, Heart Failure Assessment, Anticoagulation Plan, Cardiac Rehab
- **Chemotherapy** — Chemo Protocol Selection, Dose Calculator (BSA), Pre-Chemo Checklist, Toxicity Grading (CTCAE), Antiemetic Protocol, Blood Count Review
- **Dermatology** — Lesion Mapping, Biopsy Request, Phototherapy Log, Dermatology Atlas, Patch Test Record, Wound Assessment
- **Dialysis** — Dialysis Prescription, Fluid Balance, Kt/V Calculator, Access Assessment, Electrolyte Tracker, Dry Weight Trend
- **ENT** — Audiometry Record, Tympanogram, Flexible Nasendoscopy, Voice Assessment, Thyroid Nodule FNA, Sleep Study Request
- **Gastroenterology** — Endoscopy Report, Liver Function Trend, IBD Activity Index, Nutrition Assessment
- **Haematology** — Blood Film Review, Coagulation Panel, Transfusion Request, Sickle Cell Crisis Protocol, Bone Marrow Report, Anticoagulation Clinic
- **Intensive Care** — Ventilator Settings, Nutrition (NUTRIC), Daily ICU Checklist
- **Neonatal** — Gestational Age Assessment, Surfactant Protocol, Bilirubin Chart, Feeding Plan
- **Nephrology** — eGFR Trend, Urinalysis Review, Biopsy Report, Transplant Assessment, Immunosuppression Protocol, Dialysis Access
- **Neurology** — Seizure Log, Lumbar Puncture Record, MS Relapse Assessment, Cognitive Screen (MMSE/MoCA)
- **Obstetrics** — PPH Protocol, Eclampsia Protocol, Neonatal Resuscitation
- **Oncology** — Staging (TNM), Performance Status (ECOG), Treatment Plan, Symptom Assessment (ESAS), Palliative Care Needs, MDT Summary
- **Ophthalmology** — Visual Acuity Record, IOP Measurement, Fundoscopy Report, Visual Field Test, Slit Lamp Findings, Refraction Record
- **Orthopaedics** — Fracture Classification, Neurovascular Check, Cast/Splint Record, ROM Assessment, VTE Prophylaxis, Rehab Milestones
- **Psychiatry** — Mental State Examination, Capacity Assessment, Section/Involuntary Hold

