# Clinical Procedures Pipeline — repository audit

**Wave 0.1.** What the repository actually contains today, what is genuinely absent, and a
section-by-section coverage matrix against the Clinical Procedures Pipeline specification (§1–30).

Audited at `09b28436e` on `claude/staging-ux-orchestration-remediation-Yypyl`, 2026-07-26.
Every claim below is a file read or a grep, not a recollection. Where an earlier draft of this
audit was wrong, the correction is stated explicitly rather than quietly fixed. Three of them are
recorded here, and each shrinks the work: the request lifecycle, the results chain and the implant
registry are all more built than the first pass credited.

Companion documents: [surgical pack audit](../surgical-domain-pack/audit.md) ·
[boundary ADR](../../architecture/adr/ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES.md)

---

## 1. The headline

A procedure execution engine already exists, is deep, and is live-proven. It is not named that,
and it is not reachable as a cross-cutting capability.

`inpatient-service` owns `procedure_episode` plus roughly twenty satellite tables
(`V010`–`V036`, `V065`–`V066`) with two API faces over **one** aggregate —
`/internal/v1/procedures/**` (25 endpoints, the clinical wizard) and `/internal/v1/theatre/**`
(48 mappings, the board). The engine lives in
`core/{TheatreService, ProcedureEpisodeService, TheatreReadinessBoardService,
TheatreCommoditiesService, AnaesthesiaScoringEngine, TheatreProjectionReconciler}` —
`TheatreService` alone is 2,072 lines.

Ten live runtime-proof rigs exist under `scripts/runtime-proof/theatre-*.sh` (elective,
elective-completeness, clinical-safety, commodities, recovery-reporting, emergency, alt, authz,
persistence, queue-drainage). The theatre programme's completion gate recorded 23 PASS /
3 functional-with-limitation / 1 PARTIAL / 0 BLOCKED.

So the pipeline's problem is **not** that execution is missing. It is that:

1. nothing declares what a procedure *requires* — there is no catalogue;
2. readiness is hardcoded to theatre rather than derived from requirements;
3. competence is a single coarse check;
4. the lifecycle and the settings are theatre-shaped, so a bedside, clinic, endoscopy, dialysis
   or interventional-radiology procedure has nowhere to execute;
5. nothing guarantees a request cannot disappear.

## 2. Corrections to the first draft of this audit

### 2.1 OROS already owns the procedure request — and it is far richer than assumed

`oros_orders.order_type` has included `PROCEDURE` since `V001`. `V011__workflow_state.sql`
generalised fulfilment into a category-agnostic `workflow_state` column with **deny-by-default
per-category transition guards** — `ImagingFulfilmentWorkflow`, `LabWorkflow`,
**`ProcedureWorkflow`** — dispatched through a `WorkflowGuardRegistry`, all under
`services/oros-service/.../core/workflow/`.

`ProcedureWorkflowState` declares **19 states**, and `ProcedureWorkflow` encodes a complete
transition map:

```
RECEIVED · ACCEPTED · SCHEDULED · ARRIVED · IN_PROGRESS · PERFORMED · REPORT_PENDING
PRELIMINARY_REPORT · FINAL_REPORT · RELEASED · ACKNOWLEDGED · CLOSED
RETURNED_FOR_CLARIFICATION · REJECTED · CANCELLED · DEFERRED · NO_SHOW · REASSIGNED
AMENDED · SUPERSEDED
```

The UI worklist at `/diagnostics/procedure-worklist` surfaces only the first seven, which is why
the first draft of this audit under-read the backend.

Two consequences:

- **The request record must stay in OROS.** A second request table in a new service would be the
  duplicate system-of-record the project guardrails forbid. §4's state list is delivered by
  *extending an existing guard*, not by rebuilding a lifecycle.
- **§4 is much closer to done than assumed.** Mapping the specification's states onto what
  exists, only five are missing: `PROPOSED` (recommended but not yet ordered), `ABORTED`,
  `FAILED`, `PARTIALLY_COMPLETED`, `REPEATED`. Everything else is present with legal transitions
  already encoded.
- **§16 is substantially built.** The preliminary / final / released / acknowledged / amended /
  superseded chain is exactly the specification's "distinguish performed from findings from
  preliminary from final from interpretation from action from communication", and `ACKNOWLEDGED`
  gives the "not closed while results are unreviewed" invariant a real state to hang on.

### 2.2 A national implant registry already exists

`inventory-service` holds one, and the first draft of this audit missed it.
`ImplantTraceabilityService` over `ImplantUnitEntity` and `PatientImplantEntity`, exposed at
`/v1/internal/implants`, answers three questions the specification asks for: recall trace by UDI
or lot **across all patients**, every implant a given patient carries, and the history of a given
unit. Product, manufacturer, lot, serial, expiry and anatomical site are captured at insertion.

So §14 is largely built. Two real gaps remain inside it: information given to the patient about
their own implant, and removal, revision or replacement as tracked events.

### 2.3 Ten rigs, not nine

`ls scripts/runtime-proof/theatre-*.sh` returns ten scripts. An earlier count of nine was wrong.

### 2.4 One prior decision this programme closes

`docs/architecture/clinical-procedure-or-context-map.md` ends with an explicitly open question:

> Remaining decision: whether to keep procedure episode workflow state in PCT (coordinator-only)
> with references, or introduce a future sovereign procedure/theatre service via ADR.

That decision is now taken — see the boundary ADR. This programme closes an open item rather than
reopening a settled one. That map also records that PCT already carries `procedure`,
`procedure_room` and `operating_room` encounter contexts, which is the anchor a non-theatre
procedure needs.

---

## 3. What exists and is reusable

| Capability | Where | State |
|---|---|---|
| Procedure episode aggregate | `inpatient.procedure_episode` (V010) | real, live-proven |
| WHO checklist SIGN_IN / TIME_OUT / SIGN_OUT | `procedure_checklist_item` (V010) | real, surgery-shaped only |
| Consent bundle — procedure / anaesthesia / transfusion | `procedure_consent` (V025), mvumo V007 | real; depth gaps at §6 |
| Multi-owner readiness + blocker resolution + board | V018, V026, `TheatreReadinessBoardService` | real; hardcoded domains |
| Execution record — intraop events, vitals, signable operative note | V010, V018, `ButanoProcedureClient` | real, FHIR-projected |
| Specimens | `procedure_specimen` (V022) → oros V012 lab specimens, V014 histopathology | real end-to-end |
| Counts → retained-item safety | `procedure_count` (V023) → RITO | real; Sign-Out gated |
| Blood | `procedure_blood_link` (V020) → MADI reserve/compatible | real |
| Patient + specimen transport | `procedure_transport` (V021) → NHUME PATIENT (nhume V006) | real |
| Implants + UDI | `procedure_implant` (V027) → inventory `ImplantTraceabilityService` | real; national registry with recall trace by UDI or lot |
| Instrument sets + CSSD cycles | V028, tuso V026 `instrument_set_cssd_cycle` | real |
| Controlled drugs + witness | `procedure_controlled_drug` (V030) | real |
| Anaesthesia chart time-series | `procedure_anaesthesia_chart_entry` (V024) | real |
| ASA / Aldrete scoring | V012, `AnaesthesiaScoringEngine` | real |
| PACU depth + Aldrete discharge gate + admission continuity | V031 | real |
| Surgical discharge surfacing pending histopathology | V032 | real |
| Safety events → RITO | `procedure_safety_event` (V018, widened V029) | real |
| Optimistic locking → 409 STALE_WRITE | V065 | real |
| Order + result lifecycle, 19-state procedure guard | oros V011, `core/workflow/**` | real |
| Result observations, histopathology report, order versions | oros V013, V014, V016 | real |
| Fulfilment worklist + workflow transitions in UI | `/diagnostics/procedure-worklist`, BFF diagnostics proxy | real |
| Facility spaces, beds, equipment, instrument sets | tuso V024–V026 | real |
| Terminology governance | zibo (V004 surgical codes — **10 concepts**, V006 specialties) | thin seed |
| Governed, versioned, inspectable rules | CKP `clinical.rule_definitions` + V006 applicability/logic columns | real, no procedure content |
| Commodity consumption | inventory (Dura) `POST /v1/internal/consumption/clinical` refType=PROCEDURE | real |
| Costing bundle + utilisation reporting | costa V024, reporting V002 — real Kafka consumers of `theatre.*` | real |
| Body-map primitive | `ui/one-ui-shell/src/features/body-map` (396 lines, 2 region sets) | primitive only |
| Encounter contexts procedure / procedure_room / operating_room | pct | real |

## 4. What is genuinely absent

1. **No canonical procedure catalogue.** Nothing anywhere declares a procedure's identifier,
   synonyms, category, specialty ownership, purpose, site, laterality, technique variants, age or
   pregnancy constraints, indications, contraindications, required data, consent type, sedation
   requirement, required cadre, privilege, countersignature, equipment, consumables, medicines,
   blood, imaging, laboratory, IPC requirements, preparation, checklist, findings template,
   specimens, devices, expected duration, recovery, observation, complications, aftercare,
   follow-up, coding, version or approval. This is §3 in full, and it is the keystone: readiness,
   duplication detection, competence and safety pauses are all *functions of* the catalogue, so
   none of them can be built properly before it.
2. **Five request states missing** (see §2.1) and no enforced "every rejection, cancellation or
   delay carries a reason and a next action".
3. **No appropriateness or duplication engine** — none of §5's twelve detections.
4. **Competence is one coarse check.** `TheatreReadinessClient` asks VARAPI for
   `scope=SURGERY, status=APPROVED`. There is no per-procedure privilege, no supervision
   requirement, no trainee-never-independent rule, no countersignature, no recent-competence
   window, and none of §7's nine operator roles.
5. **Readiness is theatre-shaped and hardcoded**, not derived from catalogue requirements, and
   its verdict vocabulary lacks §8's `READY_WITH_ACCEPTED_EXCEPTION`.
6. **Episode lifecycle is theatre-shaped** — `BOOKED → PREOP → READY_FOR_THEATRE → IN_PROGRESS →
   PACU → COMPLETED` (+`CANCELLED`, `DECEASED`). No `ABORTED`, `FAILED`,
   `PARTIALLY_COMPLETED`, `REPEATED`.
7. **No non-theatre settings.** `triage_priority` covers `IMMEDIATE / EMERGENCY / URGENT /
   ELECTIVE / DAY_CASE` — urgency, not setting. Bedside, clinic, ward, critical-care, endoscopy,
   catheter-laboratory, interventional-radiology, dialysis, infusion, transfusion-as-procedure,
   dental, ophthalmic and dermatological procedures have nowhere to execute.
8. **One safety pause, not ten.** The WHO surgical checklist is real; §9's other nine contexts
   (bedside, sedation, endoscopy, interventional imaging, dialysis, transfusion, implant
   insertion, specimen collection, generic invasive) do not exist.
9. **Sedation is ASA plus airway fields**, not §10's seven levels with reversal agents and
   explicit recovery criteria.
10. **Aftercare is not generated or delivered.** §17's twelve outputs and five delivery channels
    (clinical summary, Khuluma, Nompilo, printed/offline handout, caregiver view) are absent.
11. **No offline scope for procedures** (§25) and none of its six named conflict classes.
12. **No FHIR mapping** for most of §24's eighteen resources; the operative note projects a
    Composition and nothing else does.
13. **Analytics is two report definitions** (theatre utilisation, case register) against §26's
    twenty-four indicators.
14. **Graphics is a primitive.** §12 asks for twenty maps; `features/body-map` has two region
    sets and no clinical consumer outside the DAK form renderer.

---

## 5. Coverage matrix — specification §1–30

Legend: **BUILT** — real and proven · **PARTIAL** — real but materially short of the section ·
**ABSENT** — nothing exists · **DELEGATED** — owned by a named peer, this programme wires it.

| § | Section | Status | Evidence / gap |
|---|---|---|---|
| 1 | Canonical 25-step lifecycle | PARTIAL | steps 12–21 real for theatre; 1–11 and 22–25 absent or theatre-only |
| 2 | 40 procedure classes | PARTIAL | theatre / day-case / emergency real; 30+ classes have no setting |
| 3 | Canonical catalogue (~45 attributes) | **ABSENT** | keystone gap; zibo has 10 codes, no requirements model |
| 4 | Procedure request + 15 states | PARTIAL | oros `ProcedureWorkflow` 19 states; missing PROPOSED/ABORTED/FAILED/PARTIALLY_COMPLETED/REPEATED + reason-and-next-action invariant |
| 5 | Appropriateness + duplication (12 detections) | **ABSENT** | — |
| 6 | Consent | PARTIAL | mvumo bundle real (procedure/anaesthesia/transfusion) + emergency exception; capacity, assent, adolescent confidentiality, substitute decision-making, withdrawal, photography, specimen use, research separation absent |
| 7 | Competence + privileges | PARTIAL | one VARAPI scope check; no per-procedure privilege, supervision, trainee rule, countersignature |
| 8 | Readiness engine | PARTIAL | real multi-owner engine + blockers, but hardcoded and theatre-only; no READY_WITH_ACCEPTED_EXCEPTION |
| 9 | Safety pause (10 contexts) | PARTIAL | WHO surgical checklist only |
| 10 | Analgesia / sedation / anaesthesia | PARTIAL | ASA, airway, chart, scoring real; 7 sedation levels, reversal, recovery criteria absent |
| 11 | Execution record | BUILT | intraop events, vitals, technique, findings, signable note → Butano |
| 12 | Procedure graphics (20 maps) | **ABSENT** | body-map primitive only |
| 13 | Specimens + chain of custody | PARTIAL | episode → oros specimen → histopathology real; custody, adequacy, label-mismatch prevention thin |
| 14 | Devices + implants | PARTIAL | national registry real (recall by UDI or lot, per-patient, per-unit); patient-facing information and the removal/revision lifecycle absent |
| 15 | Recovery | PARTIAL | PACU real and gated; non-theatre recovery absent |
| 16 | Results + interpretation | BUILT | oros preliminary/final/released/acknowledged/amended/superseded + observations + histopathology |
| 17 | Aftercare | **ABSENT** | — |
| 18 | Complications | PARTIAL | safety events + intraop events + RITO routing real; the 19 classes and reopen-episode semantics absent |
| 19 | Scheduling + resource orchestration | PARTIAL | scheduling sessions/OR list/reservations + conflict detection real for theatre only |
| 20 | Facility capability | PARTIAL | tuso spaces/beds/equipment/instrument sets real; procedure-level capability question absent |
| 21 | IPC + sterile processing | PARTIAL | instrument set + CSSD cycle real; hand hygiene, PPE, skin prep, single-use, reprocessing limits, exposure incident, waste absent |
| 22 | Medication + commodity | PARTIAL | inventory consumption + controlled drug witness real; contrast, reversal agents, wastage thin |
| 23 | Financial | PARTIAL | costa surgical bundle real; estimate/authorisation/denial/appeal absent; emergency-rules invariant unproven |
| 24 | Interoperability (18 resources) | PARTIAL | Composition + some Observation; ServiceRequest, Specimen, Device, DeviceUseStatement, Task, DetectedIssue, GuidanceResponse, Provenance absent |
| 25 | Offline | **ABSENT** | offline-sync exists; no procedure scope |
| 26 | Analytics (24 indicators) | PARTIAL | 2 of 24 |
| 27 | Tests (22 named) | PARTIAL | ~8 covered by theatre rigs; site/side, duplication, trainee, sedation, specimen mismatch, recall, offline absent |
| 28 | 10 demonstrations | PARTIAL | none of the ten as written; theatre rigs prove adjacent surgical journeys |
| 29 | Expected outputs | PARTIAL | this audit is the first |
| 30 | Definition of done (12 clauses) | PARTIAL | 4 of 12 hold today |

**Score: 3 BUILT · 21 PARTIAL · 6 ABSENT.**

The distribution is the useful signal. Almost nothing is missing outright; almost everything is
built for exactly one setting. The programme is therefore mostly *generalisation plus a
catalogue*, not greenfield construction — which is why the execution engine must be extended in
place rather than replaced.

---

## 6. Consequences for the plan

1. **P1 (catalogue) is the keystone.** Sections 5, 7, 8, 9 and 17 are all functions of it and
   cannot be honestly built first.
2. **P2 shrinks** to five states plus the reason-and-next-action invariant plus the execution
   index, because OROS already carries the lifecycle.
3. **§16 is nearly free** — it needs wiring to the surgical episode's closure gate, not building.
4. **P4 (generalise the engine) is the highest-risk wave.** It touches a 2,072-line service
   behind ten live rigs, and those rigs are the regression gate.
5. **§21 IPC is larger than it looks** — one real CSSD cycle table against thirteen required
   elements.
6. The 20 maps of §12 and the 17 of the surgical pack's §7 overlap heavily and must be built once
   as a shared feature, not twice.

---

## 7. P5 addendum — adolescent confidentiality is a PARTIAL, and why

Pipeline §6 requires adolescent confidentiality. P5 ships everything else in that section —
the recorded conversation, capacity, guardian consent, child assent as an act distinct from
guardian consent, substitute decision-making with a basis, refusal, withdrawal, and the
photography / specimen-use / research separations. Adolescent confidentiality specifically
ships as **PARTIAL**, and this records exactly why rather than letting it look done.

`SPECIALLY_PROTECTED` has moved since this programme opened, and the movement is real:
`ResourceSensitivityClassifier` now has `isSpeciallyProtected(resourceType)` and a
`PROTECTED_LANE_MARKERS` check that runs *first*, deliberately, so that
`confidential-encounters` is not downgraded to `FULL_CLINICAL` by the substring match on
`encounter`. That is a genuine enforcement seam and it did not exist in Phase 0.

What has **not** moved is the line the whole guarantee rests on:

```java
case FULL_CLINICAL, SPECIALLY_PROTECTED -> DataVisibilityTier.FULL_IDENTIFIED_CLINICAL;
```

Specially-protected content still resolves to the same visibility tier as ordinary clinical
data. `DataVisibilityTier` has six values and the protected lane shares the top one with
everything else, so a resource can be *classified* as confidential and still be *seen* by
anyone who can see a routine observation. A separate lane that ends in a shared tier is a
label, not a boundary.

**The position taken, following the reproductive lane's on HEADSS:** collecting
adolescent-confidential consent into a record that only looks protected is worse than not
collecting it, because the clinician and the adolescent both act on an assurance the system
does not keep. So the consent model carries the fields, the pipeline does not yet claim the
confidentiality, and the gap is stated here and in the coverage register rather than in a
footnote nobody reads.

**What would close it:** `SPECIALLY_PROTECTED` resolving to a distinct visibility tier,
caregiver context as a first-class input to the policy decision, and both access and refusal
audited. That is trust-plane work owned by the TSHEPO lane, not this programme, and it is
larger than a consent wave. Recorded as the blocker it is.
