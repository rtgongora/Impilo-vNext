# Clinical Procedures Pipeline — DAK-structured baseline

**Wave 0.4.** The pipeline is a cross-cutting platform capability, so this baseline declares only
what constrains *any* invasive intervention regardless of specialty. Specialty indications belong
to the owning pack.

Machine-checked half:
[`docs/clinical-governance/procedures/standards-baseline.json`](../../clinical-governance/procedures/standards-baseline.json)
and [`coverage-exclusions.json`](../../clinical-governance/procedures/coverage-exclusions.json),
feeding the shared [standards traceability matrix](../../clinical-governance/rmnp/dak-traceability-matrix.md).

Related: [audit](audit.md) · [boundary ADR](../../architecture/adr/ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES.md) ·
[surgical pack baseline](../surgical-domain-pack/dak-baseline.md)

---

## 1. Recommendations

| # | Recommendation | Traces to |
|---|---|---|
| R1 | Every procedure has an indication and a named owning service. The pipeline decides whether it is *safely ready*; the specialty decides whether it is *clinically right*. | boundary ADR |
| R2 | A request cannot disappear. Every rejection, cancellation, deferral or delay carries a reason and a next action. | `PS.GLOBAL_ACTION_PLAN.2021_2030` |
| R3 | What a procedure requires is declared once, as governed versioned content, and readiness is derived from it — not hardcoded per setting. | `PS.GLOBAL_ACTION_PLAN.2021_2030` |
| R4 | Identity, procedure, site and side are verified before every invasive intervention, with site marking where laterality or multiple structures apply. | `PS.WRONG_SITE.PREVENTION` |
| R5 | Readiness changes execution state. An unresolved requirement has a named owner, and an override is audited rather than implicit. | `PS.GLOBAL_ACTION_PLAN.2021_2030` |
| R6 | Consent records the conversation — purpose, benefits, material risks, alternatives, consequences of declining, questions, language, interpreter, who took it and when. | `PS.INFORMED_CONSENT` |
| R7 | Capacity, guardian consent, child assent, substitute decision-making, refusal and withdrawal are first-class, not edge cases. | `PS.CAPACITY_ASSENT_GUARDIAN` |
| R8 | A trainee is never surfaced as an independent authorised operator. Supervision and countersignature are enforced, not advisory. | `PS.GLOBAL_ACTION_PLAN.2021_2030` |
| R9 | Requirements follow the sedation depth that may be reached, not the depth intended. | `SEDATION.CONTINUUM.DEPTH` |
| R10 | Mandatory monitoring and a trained anaesthesia provider are readiness requirements that block. | `ANAESTHESIA.WFSA_WHO.MONITORING` |
| R11 | A specimen cannot be accepted unlabelled or mismatched, and its custody is traceable from collection to clinical action. | `SPECIMEN.CHAIN_OF_CUSTODY` |
| R12 | Every device and implant is reachable from a recall, and the patient is told what is inside them. | `MED.DEVICE.UDI_TRACEABILITY` |
| R13 | Transfusion is a procedure: it has a safety pause with bedside positive patient identification and structured observation. | `BLOOD.TRANSFUSION.APPROPRIATE_USE` |
| R14 | Sterility is traceable where the risk warrants it — a generic sterile checkbox is not acceptable. | `IPC.CORE.STERILE_PROCESSING`, `IPC.CORE.ASEPTIC_TECHNIQUE` |
| R15 | No patient disappears after "procedure completed". Recovery is completed, aftercare is issued, results are reviewed. | `RESULTS.CRITICAL_ACKNOWLEDGEMENT` |
| R16 | A complication reopens or extends the procedure episode and reconnects to clinical care. | `PS.GLOBAL_ACTION_PLAN.2021_2030` |
| R17 | Payment state never masquerades as clinical cancellation, and never delays an emergency procedure. | `ECO.WHA76-2.FINANCING` (surgical baseline) |
| R18 | Specialty packs reuse this engine. A parallel procedure system is a defect, not a variation. | boundary ADR |

## 2. Personas

Nine operator roles the specification names, against what the platform can express today.

| Role | Expressible today | Gap |
|---|---|---|
| Independent operator | partially — VARAPI scope `SURGERY`, status `APPROVED` | no per-procedure privilege |
| Supervised operator | **no** | nothing distinguishes supervised from independent |
| Trainer | **no** | — |
| Assistant | `scrub_nurse_id`-style fields only | not a modelled role |
| Sedation provider | **no** | distinct from anaesthesia provider; absent |
| Anaesthesia provider | `ANAESTHETIST` | real, no depth tier |
| Nurse | `NURSE` | undifferentiated |
| Technician | **no** | — |
| Observer | **no** | matters for consent and for who is in the room |

Plus: requesting clinician (real, on the OROS order), fulfilment team (real, on the procedure
worklist), patient and caregiver (Khuluma, Nompilo, caregiver view — no procedure-specific
surface).

**The load-bearing gap is supervision.** Competence today is one coarse VARAPI check, so the
platform cannot answer "may this person do this procedure, alone?" — which is the question §7
exists to answer.

## 3. Scenarios

The ten demonstrations, with what each is really testing.

| # | Scenario | Really tests |
|---|---|---|
| 1 | Lumbar puncture, indication to result review | a bedside procedure has anywhere to execute at all |
| 2 | Emergency chest drain, Emergency to recovery to imaging confirmation | emergency path keeps site/side verification while dropping scheduling |
| 3 | Paediatric procedure with guardian consent and child assent | assent is distinct from consent, and both are recorded |
| 4 | Obstetric procedure invoking maternity privacy and safety rules | the Reproductive Pack governs, the pipeline executes |
| 5 | Endoscopy with sedation, biopsy, pathology and follow-up | sedation depth drives requirements; specimen closes the loop |
| 6 | Dialysis session with access, prescription, observations and complications | a recurring procedure is not a one-shot episode |
| 7 | Image-guided biopsy with specimen chain of custody | custody survives a hand-off between services |
| 8 | Implant procedure with full lot and recall traceability | recall reaches the patient |
| 9 | Cancelled procedure with rebooking and patient communication | the request survives cancellation |
| 10 | Complication escalating into Emergency and Surgery | the episode reopens and reconnects |

Scenarios 1, 2, 5, 6 and 7 are all currently unexecutable for the same reason: there is no
non-theatre setting.

## 4. Workflows — the canonical 25-step lifecycle

| Steps | Owner | State |
|---|---|---|
| 1 identify or recommend | requesting specialty | `PROPOSED` state absent |
| 2-6 request, verify indication, urgency, duplication, contraindications | `oros-service` request + `procedures-service` checks | request real; duplication and contraindication checks absent |
| 7 select procedure and technique | `procedures-service` catalogue | catalogue absent |
| 8 consent | `mvumo-service` | bundle real, content absent |
| 9 schedule | `scheduling-service` | real for theatre only |
| 10 verify competence | `procedures-service` over VARAPI/Vashandi | one coarse check |
| 11 verify facility and equipment | `procedures-service` over TUSO | spaces yes, procedure capability no |
| 12 complete preparation | `inpatient-service` execution | real for theatre |
| 13 confirm patient, procedure, site, side | `inpatient-service` execution | **site/side unverifiable — no structured field** |
| 14 safety pause | `procedures-service` templates | surgical only |
| 15 analgesia, sedation, anaesthesia | `inpatient-service` + Dura | anaesthesia real, sedation continuum absent |
| 16-19 perform, findings, specimens/devices/implants, immediate outcome | `inpatient-service` execution | real |
| 20 recover and monitor | `inpatient-service` PACU | real for theatre |
| 21 aftercare | `procedures-service` templates + Khuluma/Nompilo | absent |
| 22 issue and reconcile results | `oros-service` | real |
| 23 detect complications | `procedures-service` + owning specialty | events only |
| 24 arrange follow-up | `booking-service` | partial |
| 25 close the episode | `inpatient-service` + owning specialty | closes without result review |

## 5. Core data

**Catalogue** (`procedures-service`, net-new) — canonical identifier; clinical name; synonyms;
category; specialty ownership; diagnostic or therapeutic purpose; anatomical site; laterality;
technique variants; age and pregnancy constraints; indications; contraindications; required data;
consent type; sedation or anaesthesia requirement; required cadre; privilege; countersignature;
equipment; consumables; medicines; blood, imaging, laboratory and IPC requirements; preparation;
checklist; findings template; specimens; devices; expected duration; recovery; observation;
complications; aftercare; follow-up; coding; version; approval.

**Request additions** (`oros-service`) — `PROPOSED`, `ABORTED`, `FAILED`, `PARTIALLY_COMPLETED`,
`REPEATED`; mandatory reason and next action on every non-progressing transition.

**Execution additions** (`inpatient-service`) — `catalogue_ref`; `request_ref`; `setting`;
`lifecycle_state` beside the preserved theatre `status`; **structured site and side with a start
gate**.

**Readiness verdict** — `READY`, `READY_WITH_ACCEPTED_EXCEPTION`, `NOT_READY`,
`EMERGENCY_OVERRIDE`, each unresolved item carrying an owner.

**Consent content** (`mvumo-service`) — purpose; benefits; material risks; alternatives;
consequences of no procedure; questions asked; language; interpreter; person obtaining; time;
capacity; assent; refusal; withdrawal; photography; specimen use; research separation.

**Competence** — privilege per catalogue entry; supervision requirement; trainee status;
countersignature; recent-competence window; the nine operator roles.

**Aftercare** — site care; activity restrictions; diet; medicines; pain plan; device care; warning
signs; emergency contact; follow-up; result-review plan; escort; work or school advice.

## 6. Decision logic

Governed content in `clinical-knowledge-platform-service`, reusing `clinical.rule_definitions`.

| Layer | Pipeline rules |
|---|---|
| `DATA_VALIDATION` | site and side required for lateralised procedures; specimen label completeness; consent completeness before start |
| `DANGER_SIGN` | contraindication detection; anticoagulation risk; pregnancy exclusion; sedation depth exceeded |
| `CLASSIFICATION` | procedure class; sedation level; ASA; readiness verdict |
| `THERAPY` | antibiotic prophylaxis; reversal agents; analgesia by class |
| `MONITORING` | observation frequency and duration by class and sedation depth; recovery discharge criteria |
| `FOLLOW_UP` | result review due; aftercare check-in; device follow-up |

Duplication and appropriateness are also decision logic, not code: the twelve detections in §5 —
duplicate request, recent equivalent, missing prerequisite, conflicting procedure,
contraindication, unsafe timing, wrong site, wrong side, wrong patient, inappropriate facility,
required specialist unavailable, required equipment unavailable — are inspectable rules whose
output is a clarification or a safe alternative, never a silent cancellation.

## 7. Indicators

The twenty-four required: procedure volume · indication · specialty · waiting time · cancellation ·
delay reason · completion · failure · aborted · complications · unplanned admission · unplanned
surgery · specimen adequacy · result turnaround · result acknowledgement · sedation safety ·
infection · device outcomes · operator and supervision · equity · cost · stockout impact.

**Analytics are for safety and improvement, not operator punishment.** The operator and
supervision indicator is the one that can do harm if reported naively; it is designed as a
supervision-adequacy measure, not a league table, and that constraint is a requirement rather than
a convention.

## 8. Functional requirements

FR1 one canonical catalogue with governed versioning · FR2 request lifecycle no request can leave
silently · FR3 duplication and appropriateness detection producing clarification or alternative ·
FR4 catalogue-driven readiness with per-item owners and four verdicts · FR5 competence and
privilege enforcement including supervision and countersignature · FR6 consent capturing the
conversation, with capacity, assent, refusal and withdrawal · FR7 class-specific safety pauses ·
FR8 structured site and side, verified before start · FR9 sedation continuum with depth-derived
requirements · FR10 execution record with specialty templates · FR11 specimen custody preventing
unlabelled or mismatched specimens · FR12 device and implant traceability with recall reach and
patient information · FR13 recovery completion and aftercare generation and delivery · FR14
complication management reopening the episode · FR15 scheduling and resource orchestration with no
double-booking across incompatible locations · FR16 facility capability answers · FR17 IPC and
sterile-processing traceability · FR18 commodity integration with batch, expiry, wastage and
controlled-medicine handling · FR19 financial integration after clinical prioritisation · FR20
FHIR mapping across the eighteen named resources · FR21 offline operation with the six conflict
classes · FR22 the twenty-four indicators.

## 9. Non-functional requirements

NFR1 **fail safe** — `procedures-service` is on the readiness path, so its unavailability blocks
rather than allows; emergency override is audited · NFR2 **engine-not-store** — evaluate here,
persist in the executing service; no second record of the same fact · NFR3 **never delay emergency
care** · NFR4 **auditable** to actor, role, purpose of use and time · NFR5 **offline-capable**
with declared conflict classes, temporary identities and stale-data visibility · NFR6
**content-governed** — catalogue and rules are releases, not deployments · NFR7 **explainable** ·
NFR8 **idempotent** — every write keyed, safe to replay · NFR9 **UI-reachable** — nothing counts
as delivered until experience-bff proxies it · NFR10 **regression-safe** — the ten theatre rigs
stay green.

---

## The one gap worth reading twice

Nothing in this platform verifies **site and side**. They are recorded on a surgical referral and
displayed on the theatre case banner, but no structured field holds them on the procedure episode,
and no gate refuses to start a procedure whose site and side were not confirmed. The WHO Time Out
asks the question and there is nowhere to put the answer.

Wrong-site surgery is a never event. This is the pipeline's most serious finding, it is fixed in
P4 with the start gate, and it is recorded as its own standard
(`PS.WRONG_SITE.PREVENTION`) so the guard holds the programme to it.
