# Surgery and Surgical Specialties — repository audit

**Wave 0.1.** What the repository actually contains today, what is genuinely absent, and a
section-by-section coverage matrix against the Surgery and Surgical Specialties specification
(§1–25).

Audited at `09b28436e` on `claude/staging-ux-orchestration-remediation-Yypyl`, 2026-07-26.
Every claim is a file read or a grep.

Companion documents: [procedures pipeline audit](../procedures-pipeline/audit.md) ·
[boundary ADR](../../architecture/adr/ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES.md)

---

## 1. The headline

**Theatre is built. Surgery is not.**

The specification's own framing is exactly right: theatre is one location and one phase. The
repository has that phase in real depth — and almost nothing on either side of it.

`inpatient.procedure_episode` is a **case**, not a **course**. It records one trip to an
operating room: booking, preoperative assessment, WHO checklist, consent, intraoperative events,
counts, specimens, implants, PACU, discharge. It has no notion of the disease that made the
operation necessary, no notion of the decision that chose it over the alternatives, and no notion
of what happened to the patient afterwards beyond that admission.

Concretely, of the twenty journey stages the specification enumerates:

| Stages | Coverage |
|---|---|
| 1–4 presentation → assessment → diagnosis → decision-making | **absent** except a referral row |
| 5 optimisation | **absent** |
| 6 consent | built (procedure/anaesthesia/transfusion bundle) |
| 7 scheduling and prioritisation | partial (waitlist + OR sessions, thin fields) |
| 8 prehabilitation | **absent** |
| 9–13 preoperative → anaesthesia → theatre → recovery → inpatient | built, deep, proven |
| 14 complication management | partial (events, not pathways) |
| 15 discharge | built |
| 16 rehabilitation | **absent** |
| 17 histology and results | built in OROS, not gated on a surgical episode |
| 18 surveillance | **absent** |
| 19 reoperation or recurrence | **absent** |
| 20 long-term outcomes | **absent** |

Six of twenty stages are real. Ten are absent. That is the gap this pack closes.

## 2. What exists and is worth keeping

Nothing in the theatre or trauma streams should be discarded or duplicated. The following is real
and becomes the surgical pack's foundation:

| Capability | Where | Note |
|---|---|---|
| Perioperative episode + 20 satellite tables | `inpatient-service` V010–V036, V065–V066 | the operative phase; extend, never fork |
| WHO Surgical Safety Checklist, three phases | `procedure_checklist_item` | Sign-Out gated on count reconciliation |
| Consent bundle + emergency exception | `procedure_consent` V025, mvumo V007 | two-doctor / deferred / proxy paths real |
| Readiness engine + blocker board | V018, V026, `TheatreReadinessBoardService` | multi-owner, fails safe |
| Signable operative note → FHIR Composition | `procedure_note`, `ButanoProcedureClient` | draft/signed/amended states |
| Specimens → histopathology | V022 → oros V012/V014 | discharge summary surfaces pending results |
| Counts → retained-item safety | V023 → RITO | real Sign-Out gate |
| Blood | V020 → MADI reserved + compatible | real gate |
| Implants, UDI, lot, serial, expiry | V027 + inventory `ImplantTraceabilityService` | national registry, recall trace by UDI or lot |
| Instrument sets + CSSD cycles | V028, tuso V026 | real |
| Anaesthesia chart + ASA/Aldrete scoring | V024, V012 | real time-series |
| PACU depth + Aldrete discharge gate | V031 | escalation, unplanned ICU, return to theatre |
| Surgical discharge summary | V032 | surfaces surviving pending histopathology |
| Trauma correlation | V034 `trauma_episode_id`, daidzai phase registration | one coherent episode across programmes |
| Surgical referral | `referral.surgical_referral` V002 | indication, laterality, anatomical site, urgency, target specialty, decision |
| Surgical waitlist | `scheduling.surgical_waitlist_entry` V002 | P1–P4, target timeframe, deferral/cancellation, JSONB history |
| Theatre sessions, OR list, resource reservations, conflict detection | scheduling V003 | blocks double-booked surgeon on a real reservation |
| Facility spaces, beds, equipment, instrument sets | tuso V024–V026 | real |
| Surgical procedure codes | zibo V004 | **10 concepts** |
| Clinical specialty codes | zibo V006 | real |
| Governed versioned inspectable rules | CKP `clinical.rule_definitions` + V006 | no surgical content yet |
| Costing bundle, utilisation reporting | costa V024, reporting V002 | real `theatre.*` consumers |
| Body-map primitive | `ui/one-ui-shell/src/features/body-map` | 396 lines, 2 region sets |
| UI | theatre board/case/referrals, surgical waitlist, theatre lists, EHR procedures, theatre reports | real, BFF-proxied |

## 3. What is genuinely absent

1. **No surgical disease model.** There is no `surgical_episode`, no `surgical_condition`, no
   disease stage, no clinically-owned laterality (only a referral column), no operative
   indication, no recorded non-operative option, no planned-versus-performed reconciliation, no
   complication as a managed pathway, no histology closure, no implant/drain/stoma/wound as
   longitudinal objects, no functional outcome, no surveillance plan, no recurrence, no
   reoperation. §3 is absent in full.
2. **No surgical clinic.** §5's roughly thirty assessment elements — presenting problem, symptom
   timeline, previous surgery, anaesthetic history, wound-healing history, bleeding and
   thrombosis, anticoagulants, nutrition, frailty, functional status, tobacco, alcohol,
   pregnancy, social support, transport, livelihood impact, patient goals, examination,
   differential diagnosis, surgical risk, treatment options, shared decision — have no home.
   The surgical journey currently *begins* at a booking.
3. **No specialty extensions.** Zero of the fifteen specialties in §6. There is no specialty
   dimension on the episode at all beyond `referral.target_specialty`.
4. **No surgical graphics.** §7 asks for seventeen maps with eleven attributes per finding. The
   body-map primitive has two region sets and no clinical consumer outside the DAK form renderer.
5. **No decision record.** §8's eighteen elements — certainty, natural history, expected benefit,
   material risks, anaesthetic/blood/functional/fertility implications, stoma possibility,
   implant possibility, financial and access implications, patient preference, final decision —
   are absent. Consent today records that consent was granted, not what was decided or why.
   The specification's "do not reduce consent to a signature" is not yet satisfied.
6. **Waiting list is thin.** Against §9's nineteen fields, roughly seven are firm
   (urgency, clinical priority, date listed, required procedure, deferral, cancellation,
   rescheduling) and several more are untyped `special_requirements` / `history` JSONB. Absent:
   cancer priority, deterioration risk, required surgeon, required anaesthesia, required
   equipment, implant, blood, ICU-or-ward, preoperative tasks, patient contact, clinical
   revalidation. The "no patient may disappear after cancellation" invariant is unenforced —
   `REMOVED` and `CANCELLED` are terminal statuses with no follow-up obligation.
7. **No prehabilitation or optimisation** — none of §10's sixteen domains.
8. **Complications are events, not pathways.** `procedure_safety_event` and
   `procedure_intraop_event` record that something happened and route it to RITO. §15's twenty
   named complications, each with recognition → severity → immediate action → responsible team →
   investigation → treatment → disclosure and communication → outcome, do not exist.
9. **Pathology closure is unenforceable.** The discharge summary *surfaces* pending
   histopathology, which is good, but §16's "do not close a surgical episode with an unreviewed
   histology result" needs a surgical episode to gate — and there isn't one. OROS has the
   `ACKNOWLEDGED` state that would make the gate real.
10. **Implants are tracked; drains, stomas and wounds are not.** Correcting an error in the
    first draft of this audit: `inventory-service` does hold a national implant registry
    (`ImplantTraceabilityService`, recall trace by UDI or lot across all patients, per-patient
    and per-unit history). What §17 still lacks is patient-facing implant information, the
    removal/revision lifecycle, and drains, stomas and wounds as longitudinal objects at all.
11. **No surgical decision support.** CKP has the right machinery — versioned, applicability-
    scoped, structurally inspectable rules — and no surgical content in it.
12. **Facility capability is spaces, not capability.** §20's fourteen dimensions (surgical
    specialty, theatre, anaesthesia, recovery, ICU, blood, imaging, pathology, sterilisation,
    equipment, implant, rehabilitation, paediatric capability, cancer capability) cannot be asked
    of TUSO today.
13. **No offline surgical operation** (§22) and **none of §23's twenty indicators** beyond
    theatre utilisation and a case register.

## 4. Coverage matrix — specification §1–25

Legend: **BUILT** — real and proven · **PARTIAL** — real but materially short · **ABSENT** —
nothing exists.

| § | Section | Status | Evidence / gap |
|---|---|---|---|
| 1 | Standards baseline (WHO + Zimbabwe + specialty) | ABSENT | no surgical standards traceability; the DAK-structured baseline is Wave 0.4 |
| 2 | Domain boundaries surgery/theatre/trauma/emergency/pipeline | PARTIAL | theatre↔trauma boundary is real and proven; surgery has no boundary because it has no service. Closed by the ADR. |
| 3 | Surgical episode + condition model (17 concepts, 13 modifiers) | **ABSENT** | keystone gap |
| 4 | Entry pathways (15) | PARTIAL | primary-care/specialist/emergency referral + trauma handoff real; 11 absent |
| 5 | General surgical assessment (~30 elements) | **ABSENT** | preoperative nursing/anaesthetic assessment exists; surgical assessment does not |
| 6 | 15 surgical specialties | **ABSENT** | — |
| 7 | Surgical graphics (17 maps) | **ABSENT** | body-map primitive only |
| 8 | Surgical decision-making (18 elements) | **ABSENT** | consent records grant, not decision |
| 9 | Waiting lists + prioritisation (19 fields) | PARTIAL | ~7 firm; no-disappearance invariant unenforced |
| 10 | Prehabilitation + optimisation (16 domains) | **ABSENT** | — |
| 11 | Preoperative workflow (22 items) | PARTIAL | identity, consent, allergies, fasting, anaesthetic assessment, airway, equipment, theatre, destination real; site/side verification, VTE plan, antibiotic plan, pregnancy, specialist attendance thin or absent |
| 12 | Theatre integration — reuse, repair links | PARTIAL | everything listed under "reuse" is real; every link listed under "repair" is missing because both ends do not exist |
| 13 | Operative record (26 fields) | PARTIAL | procedure, indication, surgeons, anaesthesia, times, findings, blood loss, specimens, implants, counts, complications real; position, preparation, incision, steps, technique, fluids, drains, stomas, closure, wound classification, postoperative instructions absent; no specialty templates |
| 14 | Recovery + surgical ward workspace (18 tiles) | PARTIAL | PACU real and gated; the ward workspace as specified is absent |
| 15 | Complications (20 pathways) | PARTIAL | events + RITO routing; no pathways |
| 16 | Pathology + specimen closure (10 states) | PARTIAL | OROS has the states; the surgical closure gate has nothing to gate |
| 17 | Implants, devices, drains, stomas (12 attributes) | PARTIAL | implant registry with recall real; drains, stomas and wounds absent; patient-facing information and removal/revision absent |
| 18 | Discharge + long-term follow-up (18 items) | PARTIAL | surgical discharge summary real; surveillance, future surgery, restrictions, fit note, transport absent |
| 19 | Decision support (12 areas) | **ABSENT** | CKP machinery ready, no content |
| 20 | Facility capability (14 dimensions) | PARTIAL | spaces/beds/equipment/instrument sets; capability query absent |
| 21 | Integration; finance must not delay emergency | PARTIAL | costa bundle real; the emergency-not-delayed invariant is unproven |
| 22 | Offline (9 surfaces) | **ABSENT** | — |
| 23 | Analytics (20 indicators) | PARTIAL | 2 of 20 |
| 24 | 10 demonstrations | PARTIAL | theatre rigs prove elective, emergency, obstetric, day-case, cancellation, complication journeys at case level; none of the ten as written (they span the whole course) |
| 25 | Expected outputs (14) | PARTIAL | this audit is the first |

**Score: 0 BUILT · 16 PARTIAL · 9 ABSENT.**

Note the contrast with the pipeline matrix: there, everything was built for one setting. Here,
the middle of the journey is built and both ends are missing. Nothing scores BUILT at section
level because every section spans the whole course, and no section of the course is complete
end-to-end.

## 5. Consequences for the plan

1. **S1 (episode + condition model) is the keystone.** Sections 3, 8, 15, 16, 18 and 23 all need
   a surgical episode to attach to. Nothing downstream is honest without it.
2. **§12's "repair missing links" is not repair work** — it is construction on the clinic side.
   The theatre end of every link already exists. This is genuinely reassuring: the hard,
   safety-critical half is done and proven.
3. **§16 becomes cheap and high-value.** OROS already has `ACKNOWLEDGED`; the surgical episode
   gives it something to gate. This is one of the specification's strongest safety requirements
   and it is close to free once S1 lands.
4. **The specialties (§6) must be content over shared infrastructure**, not fifteen parallel
   builds. The shared spine is S1–S14; each specialty then contributes indications, operative
   content, templates, maps and rules.
5. **§7's maps and the pipeline's §12 maps overlap heavily** and are built once as one feature.
6. **Do not fork theatre.** Every one of §12's "reuse" items is real and live-proven behind ten
   rigs. Those rigs are the regression gate for every wave that touches `inpatient-service`.
