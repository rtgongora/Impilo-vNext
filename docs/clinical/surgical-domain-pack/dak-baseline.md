# Surgery and Surgical Specialties — DAK-structured baseline

**Wave 0.4.** WHO has published no SMART DAK for surgery, so this applies the DAK *structure* to a
baseline assembled from WHO ECO, the WHO Surgical Safety Checklist, the WHO SSI guideline, the
Lancet Commission on Global Surgery indicators and Zimbabwe's NSOAS 2022-2025.

The machine-checked half lives in
[`docs/clinical-governance/surgery/standards-baseline.json`](../../clinical-governance/surgery/standards-baseline.json)
with its coverage decisions in
[`coverage-exclusions.json`](../../clinical-governance/surgery/coverage-exclusions.json); both feed
the shared [standards traceability matrix](../../clinical-governance/rmnp/dak-traceability-matrix.md).
This document is the design half: the nine DAK components, written so a wave can be implemented
against them.

Related: [audit](audit.md) · [boundary ADR](../../architecture/adr/ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES.md) ·
[procedures pipeline baseline](../procedures-pipeline/dak-baseline.md)

---

## 1. Recommendations

The narrative statements the pack must satisfy. Each traces to a declared standard.

| # | Recommendation | Traces to |
|---|---|---|
| R1 | Surgical care is one continuum from presentation to long-term outcome, not a theatre booking. A patient with a surgical condition has one course; operations are events within it. | `ECO.WHA76-2.CONTINUUM` |
| R2 | Every operation traces to a recorded surgical decision — the diagnosis, its certainty, the natural history, the non-operative options considered, and the patient's preference. | `ECO.WHA76-2.SURGICAL_ANAESTHESIA_PERIOP`, `PS.INFORMED_CONSENT` |
| R3 | Consent is the record of a conversation, not a signature or an uploaded image. | `PS.INFORMED_CONSENT` |
| R4 | A patient placed on a waiting list cannot disappear from it. Cancellation, deferral and removal each carry a reason, a responsible owner and a next action. | `ZW.NSOAS.2022.TIMELY_AFFORDABLE_SAFE` |
| R5 | Identity, procedure, site and side are verified before every operation, and laterality is structured data, not prose. | `PS.WRONG_SITE.PREVENTION`, `SSC.2009.TIME_OUT` |
| R6 | The WHO Surgical Safety Checklist is completed in three phases, and Sign Out is blocked by an unreconciled count. | `SSC.2009.SIGN_IN`, `SSC.2009.TIME_OUT`, `SSC.2009.SIGN_OUT` |
| R7 | Surgical-site infection prevention is applied across the preoperative, intraoperative and postoperative periods, and wound classification is captured at operation so infection can be attributed. | `SSI.2018.*` |
| R8 | A complication is a managed pathway — recognised, graded, owned, investigated, treated, disclosed and closed — not a logged event. | `PS.GLOBAL_ACTION_PLAN.2021_2030`, `SSI.2018.POSTOPERATIVE` |
| R9 | A surgical episode does not close while a histology result that could change the plan is unreviewed. | `RESULTS.CRITICAL_ACKNOWLEDGEMENT` (procedures baseline) |
| R10 | Every implant a patient carries is reachable from a recall, and the patient is told what is inside them. | `MED.DEVICE.UDI_TRACEABILITY` (procedures baseline) |
| R11 | Referral is routed on capability, not geography alone: the destination must be able to perform the procedure. | `ECO.WHA76-2.REFERRAL_NETWORKS`, `LCOGS.BELLWETHER.*` |
| R12 | Bellwether capability — laparotomy, caesarean delivery, open fracture care — is a declared property of a facility, because it is how essential surgical access is judged. | `LCOGS.BELLWETHER.*`, `LCOGS.IND.1.TIMELY_ACCESS` |
| R13 | Surgical activity is reported by facility level, because the national strategy's central finding is that 83% of key tracer operations happen at central and provincial hospitals. | `ZW.NSOAS.2022.DISTRICT_ACCESS` |
| R14 | Financial state never delays emergency surgery and never presents as clinical cancellation. | `ECO.WHA76-2.FINANCING`, `LCOGS.IND.5/6` |
| R15 | Decision support is explainable, versioned and nationally governed: a clinician can see which rule fired, on what evidence, from which source, at which version. | `ECO.WHA76-2.SAFETY_CHECKLISTS`, national governance |
| R16 | Surgery never overrides provider judgement. Guidance is advisory, auditable, and refusable with a recorded reason. | Nompilo doctrine |

## 2. Personas

Reusing the vocabulary already in the platform rather than inventing a parallel set. TSHEPO
policy rules already carry `SURGEON`, `ANAESTHETIST` and `NURSE`; ZIBO V006 carries 22 clinical
specialty codes.

| Persona | Existing vocabulary | Gap this pack must close |
|---|---|---|
| Surgeon | `SURGEON` in tshepo-authz V029/V035 | no subspecialty dimension; ZIBO has `SURGERY`, `ORTHOPAEDICS`, `UROLOGY`, `ENT`, `OPHTHALMOLOGY` but not colorectal, upper-GI/HPB, breast and endocrine, vascular, neurosurgery, cardiothoracic, maxillofacial, plastics, paediatric surgery or surgical oncology |
| Anaesthetist | `ANAESTHETIST` | no sedation-depth competence tier |
| Theatre / scrub nurse | `NURSE` + `scrub_nurse_id` on the episode | scrub, circulating and recovery roles are one undifferentiated `NURSE` |
| Surgical ward nurse | `NURSE` | no surgical ward workspace to act in |
| Preoperative assessment nurse | nursing preop assessment (V010) | real |
| Trainee / registrar | none | **the critical gap** — no supervision requirement, no countersignature, and nothing prevents a trainee being surfaced as an independent authorised operator |
| Surgical clinic clerk / waiting-list coordinator | none | the role that owns "no patient disappears" has no surface |
| Histopathologist | OROS reporting roles | real |
| Radiologist | OROS imaging roles | real |
| Rehabilitation therapist | none in surgical context | stage 16 of the journey is absent |
| Stoma / wound care nurse | none | specialist follow-up roles absent |
| Patient and caregiver | Khuluma, Nompilo, caregiver view | no surgical-specific surface: no implant card, no recovery plan, no warning signs |

## 3. Scenarios

The ten demonstrations the specification requires, expressed as the journeys the pack must prove.
Each names the stages it exercises and what would make it fail honestly.

| # | Scenario | Stages | The assertion that matters |
|---|---|---|---|
| 1 | Elective hernia, clinic to operation to follow-up | 1-20 | one surgical episode spans clinic, list, theatre and follow-up — not four unrelated records |
| 2 | Emergency laparotomy from Emergency through theatre and complication monitoring | 1, 3-4, 9-14 | emergency bypasses optimisation and financial authorisation without bypassing site/side verification |
| 3 | Breast cancer: assessment, MDT, surgery, histology, oncology handoff | 1-6, 11-13, 17-20 | the episode cannot close on unreviewed histology, and the MDT decision is on the record |
| 4 | Diabetic foot from Medicine and Vascular Surgery to intervention and rehabilitation | 1-5, 11-16 | shared care across two specialties on one episode; amputation prevention is visible |
| 5 | Paediatric surgical case invoking the Paediatric Pack | all | age-specific dosing, guardian consent and child assent come from the Paediatric Pack, not a surgical copy |
| 6 | Obstetric operation invoking the Reproductive Pack | all | caesarean reuses the maternity pack; no parallel obstetric model |
| 7 | Orthopaedic implant with complete traceability | 6-13, 17-20 | a recall reaches this patient, and the patient holds implant information |
| 8 | Cancelled operation with safe rescheduling | 7, 9 | the patient is still on the list afterwards with a reason, an owner and a next action |
| 9 | Postoperative sepsis and unplanned return to theatre | 13-15, 19 | the complication pathway drives escalation and a second operation joins the same episode |
| 10 | Histology result that changes the care plan | 17-20 | acknowledgement of a critical result reopens planning rather than being filed |

## 4. Workflows

The twenty journey stages, with the owning service after the boundary ADR.

| Stage | Owner | State today |
|---|---|---|
| 1 Presentation or referral | `referral-service` + `surgery-service` | referral real; 11 of 15 entry pathways absent |
| 2 Surgical assessment | `surgery-service` + `forms-service` | absent |
| 3 Diagnosis | `surgery-service` | absent |
| 4 Decision-making | `surgery-service` | absent |
| 5 Optimisation | `surgery-service` + Medicine/Nutrition/Rehab/Madi | absent |
| 6 Consent | `mvumo-service` | bundle real, content absent |
| 7 Scheduling and prioritisation | `scheduling-service` | waitlist + sessions real, thin |
| 8 Prehabilitation | `surgery-service` | absent |
| 9 Preoperative assessment | `inpatient-service` | real |
| 10 Anaesthesia coordination | `inpatient-service` | real |
| 11 Theatre | `inpatient-service` theatre face | real, live-proven |
| 12 Recovery | `inpatient-service` PACU | real, Aldrete-gated |
| 13 Inpatient care | `inpatient-service` | real; surgical ward workspace absent |
| 14 Complication management | `surgery-service` + `rito-quality-safety-service` | events only |
| 15 Discharge | `inpatient-service` | real |
| 16 Rehabilitation | `surgery-service` + rehabilitation | absent |
| 17 Histology and results | `oros-service` | real; not gated on an episode |
| 18 Surveillance | `surgery-service` | absent |
| 19 Reoperation or recurrence | `surgery-service` | absent |
| 20 Long-term outcomes | `surgery-service` | absent |

**Boundary reminder.** Stages 9-13 are the theatre lane and are already built behind ten live
rigs. This pack builds stages 1-8 and 14-20 and *links* them; it does not rebuild 9-13. The
specification's §12 "repair missing links" is therefore construction on the clinic side, not
repair on the theatre side.

## 5. Core data

The concepts `surgery-service` must own, none of which exist anywhere today.

**Episode spine** — surgical episode; surgical condition; anatomical site; laterality; disease
stage; urgency (elective, urgent, emergency, expedited cancer); operative indication;
non-operative option considered; planned procedure; performed procedure; specialty; PCT anchor;
link to each `inpatient.procedure_episode`.

**Modifiers** — day case, minor, major, staged, damage-control, reoperation, revision, palliative.

**Decision record** — diagnosis and certainty; natural history; expected benefit; material risks;
anaesthetic, blood, functional and fertility implications; stoma possibility; implant possibility;
rehabilitation expectation; financial and access implications; patient preference; final decision;
who decided and when.

**Assessment** — presenting problem; symptom timeline; system history; previous surgery;
anaesthetic history; wound-healing history; bleeding and thrombosis; infection; medicines;
anticoagulants; allergies; nutrition; frailty; functional status; tobacco; alcohol; pregnancy
status; social support; transport; work and livelihood impact; patient goals; examination;
imaging; pathology; differential diagnosis; surgical risk; treatment options.

**Longitudinal objects** — implant, drain, stoma, wound, each with site, date, operator,
indication, and removal or revision. Implants federate to the existing inventory registry rather
than being copied.

**Outcome** — complication instances; functional outcome; patient-reported outcome; surveillance
plan; recurrence; mortality with a declared perioperative window.

**Waiting list additions** (`scheduling-service`) — cancer priority; deterioration risk; required
surgeon; required anaesthesia; required equipment; implant; blood; ICU or ward requirement;
preoperative tasks; patient contact; clinical revalidation date.

## 6. Decision logic

Governed content in `clinical-knowledge-platform-service`, using the existing
`clinical.rule_definitions` framework with its layer, applicability and structured-logic columns —
so a threshold change is a content release, not a deployment.

| Layer | Surgical rules |
|---|---|
| `DATA_VALIDATION` | laterality required where the procedure is lateralised; wound classification required at operation close |
| `DANGER_SIGN` | postoperative sepsis, haemorrhage, anastomotic leak recognition thresholds; acute limb ischaemia; testicular torsion time window |
| `CLASSIFICATION` | surgical urgency; ASA and frailty grading; cancer staging entry; wound class |
| `THERAPY` | antibiotic prophylaxis agent and timing; VTE prophylaxis; blood preparation (group and screen versus crossmatch); nutrition and anaemia optimisation |
| `MONITORING` | postoperative observation frequency by procedure and setting; drain and stoma monitoring |
| `FOLLOW_UP` | histology review due; surveillance interval by condition and stage; implant follow-up |

Every rule carries source, version, approving authority and adaptation type. Where the Ministry
has not ratified content, the authority is recorded explicitly as `PENDING_MOHCC_RATIFICATION` —
never left blank. Guidance is advisory: a clinician may override with a recorded reason, and the
override is auditable.

## 7. Indicators

The twenty the specification requires, mapped to the six Lancet Commission core indicators and to
the national strategy. `Y` marks the four with a real projection today.

surgical volume `Y` · waiting time · cancellation · day-of-surgery cancellation · emergency versus
elective `Y` · procedure type `Y` · bellwether access · caesarean integration · mortality ·
complications · unplanned return to theatre · readmission · surgical-site infection · length of
stay · histology closure · implant outcomes · functional outcomes · patient-reported outcomes ·
equity · financial protection.

Stratification is not optional: `ZW.NSOAS.2022.DISTRICT_ACCESS` makes **facility level** a
required dimension, because the strategy's own finding is expressed in it.

## 8. Functional requirements

FR1 create and maintain a surgical episode spanning all twenty stages, PCT-anchored · FR2 accept
all fifteen entry pathways · FR3 record a structured surgical assessment · FR4 record a surgical
decision with alternatives considered · FR5 maintain a waiting list no patient can silently leave ·
FR6 plan and track optimisation and prehabilitation · FR7 verify identity, procedure, site and
side before operation · FR8 link each operation to its `procedure_episode` without duplicating it ·
FR9 capture a specialty-structured operative record · FR10 present a surgical ward workspace ·
FR11 manage complications as pathways · FR12 block episode closure on unreviewed histology ·
FR13 track implants, drains, stomas and wounds longitudinally · FR14 produce discharge, follow-up
and surveillance plans · FR15 serve explainable versioned decision support · FR16 answer facility
surgical capability including bellwethers · FR17 operate offline across the nine required surfaces ·
FR18 report the twenty indicators stratified by facility level and equity.

## 9. Non-functional requirements

NFR1 **fail safe** — an unavailable dependency blocks a procedure it cannot clear, never silently
allows it; emergency override is audited, never implicit · NFR2 **never delay emergency care** for
financial, administrative or connectivity reasons · NFR3 **no duplicate system of record** — the
operation lives in `inpatient.procedure_episode`, the request in OROS, the implant in inventory ·
NFR4 **auditable** — every clinically meaningful action carries actor, role, purpose of use and
time · NFR5 **offline-capable** with declared conflict classes and visibly stale resource data ·
NFR6 **content-governed** — clinical thresholds are versioned content, not code · NFR7
**explainable** — any guidance can show its rule, evidence, source and version · NFR8
**accessible** and available in the languages consent is taken in · NFR9 **UI-reachable** — no
capability counts as delivered until experience-bff proxies it and a browser journey exercises it ·
NFR10 **regression-safe** — the ten theatre rigs stay green through every wave that touches
`inpatient-service`.

---

## Honest limitations of this baseline

1. **No WHO surgical DAK exists.** This is the DAK *structure* over a hand-assembled baseline. It
   carries the same coverage discipline but not the same provenance.
2. **Two standards are declared at coarser granularity than their sources.** The WHO SSI guideline
   holds 29 recommendations on 23 topics and the Surgical Safety Checklist holds 19 items; neither
   decomposition is vendored in this repository, so neither is claimed. Vendoring them with
   manifest hashes under `docs/reference/who-dak/` is recorded as follow-up.
3. **The Zimbabwe NSOAS 2022-2025 is cited from its launch record, not its text.** The document
   itself is not vendored here, so only what is publicly stated about it is declared.
4. **Clinical content authority is the Ministry.** This pack builds the engine, the governance
   structure and a curated seed. Ratification of the content is not ours to assert, and unratified
   content says so.
