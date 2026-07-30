# Adult Medicine — the ten required demonstrations (§23)

**Source: [`brief.md`](brief.md) §23, verbatim.** This file supersedes `proposed-demonstrations.md`,
which was written while the brief was unavailable and proposed a different set. That file was not
wrong so much as **a different kind of thing**: it proposed ten assertions about the pack's *safety
properties* (a failed read must not render as "no disease"; an empty alert list has three meanings).
§23 asks for ten **clinical journeys** — end-to-end proofs that a person can be carried through real
care without the record fragmenting. Both matter; only the second is §23. The former set is retained
in the appendix, mapped to the tests that now carry it.

## Status, stated plainly

A journey has three layers: the **record** can carry it, the **services** can execute it, and a
**clinician** can walk it in the product. Two of those layers are now proven; the third is not.

`scripts/runtime-proof/medicine-demonstrations.sh` proves the **record** layer on real Postgres:

```
record layer: PASS=35 FAIL=0
8 stated gaps — parts the record cannot yet carry (honest CANNOT lines after estate search).
This is NOT ten clinician walkthroughs and must not be reported as such.
```

`scripts/runtime-proof/medicine-demonstrations-service.sh` proves the **service** layer — HTTP
through experience-bff and pct-service (and CKP where pregnancy CDS / multimorbidity need it), on a
clean Docker Postgres + Redpanda + Redis rig:

```
service layer: PASS=30 FAIL=0 SKIP=0
7 stated gaps (hypotheses after estate search) — SPECIALLY_PROTECTED still SHADOW; HF titration
workspace; dialysis prep SoR; procedures execution outside this rig; oncology staging/cycles;
patient priorities SoR; pregnancyStatus not auto-composed from V111 into the medical view.
This is NOT a clinician walkthrough in the product.
```

Estate search overturned several earlier CANNOT lines before the service rig was written: a generic
care-plan SoR already exists (rehab/secondary-prevention goals); pregnancy-aware rules fire when
`pregnancyStatus` is supplied; HIV register honesty is a real 403; V116 transfer ownership moves
only on accept with `accepting_ref`. The seven remaining CANNOTs are absences, not inventable
capabilities.

The clinician layer is still unproven. Listing the ten journeys is not proving a walkthrough.

The `CANNOT` lines in those scripts are the authority for what is missing, because they are
produced by running the thing rather than maintained by hand beside it.

The "blocked on" column below is the human-readable form of the same register. Where the service
layer has since carried a journey, the row's outstanding gap is the remaining CANNOT — not a claim
that the journey is still wholly blocked.

## The ten

Each names the journey, the surfaces it crosses, and — the part that makes it evidence rather than a
demo — **the failure it would expose**. A demonstration that can only pass proves nothing.

### 1. Newly detected hypertension and diabetes → integrated care plan
*Screening → problem list → registers → one consolidated plan.*
**Would expose:** two diseases detected in one visit producing two parallel plans, two appointment
series and two monitoring schedules — the "folder of specialist forms" the brief exists to prevent.
**Blocked on:** chronic registers (W-C), consolidated plan on `pct_medical_episodes`
`episode_type='MULTIMORBIDITY'` (W-B).

### 2. HIV, TB and diabetes managed without three disconnected records
*One person anchor, three programme views, confidentiality intact.*
**Would expose:** the specific failure §8.7 names — duplicated HIV/TB records. Also the inverse and
more dangerous failure: a confidentiality label applied to the HIV view that does not actually
restrict access. Today `SPECIALLY_PROTECTED` is built but inert (`CATEGORY_MAP_RATIFIED = false`),
which this journey must **state**, not paper over.
**Blocked on:** diabetes register (W-C); the confidentiality seam remains a declared gap.

### 3. Heart-failure admission: Emergency → inpatient → titration → follow-up
*Handover, ward round, medicine titration across settings, discharge into clinic.*
**Would expose:** the titration history being lost at each setting boundary — the outpatient clinic
unable to see what the ward changed and why.
**Blocked on:** cardiology workspace and titration tracking (W-E, §8.1).

### 4. CKD → dialysis preparation → Procedures Pipeline
*Register → staging → access planning → procedure request through the common pipeline.*
**Would expose:** dialysis preparation modelled as a private nephrology workflow instead of
executing through the shared Procedures Pipeline, which is exactly the duplication §12 forbids.
**Blocked on:** nephrology workspace (W-E, §8.4); eGFR **derivation** — today `egfr` is a supplied
fact, so a CKD stage can be recorded that no measurement supports.

### 5. Stroke: Emergency → medical admission → rehabilitation → secondary prevention
*Acute handover, inpatient care, rehab goals, long-term prevention.*
**Would expose:** the secondary-prevention plan never being created, or being created and never
followed up — the commonest real-world failure in this pathway.
**Blocked on:** neurology workspace (W-E, §8.6), rehabilitation seam.

### 6. Decompensated liver disease → paracentesis through the Procedures Pipeline
*Indication → appropriateness → procedure → interpretation → back to the problem list.*
**Would expose:** a procedure performed whose result never returns to the problem list — §25's
"results are reviewed and actioned" failing silently.
**Blocked on:** partially reachable — paracentesis indication/appropriateness content exists. The
gap is the return path (§11 acknowledgement and action tracking).

### 7. Suspected cancer → diagnosis → MDT → oncology → palliative
*Referral, staging, multidisciplinary decision, treatment intent, palliative support.*
**Would expose:** the MDT decision existing as a note rather than a governed decision with
authorship — an outcome nobody can later attribute.
**Blocked on:** §14 consultation/MDT (not built), oncology workspace (W-E, §8.10).

### 8. Older person: ICOPE assessment → consolidated multimorbidity plan
*Intrinsic-capacity domains → detected conflicts → one prioritised plan.*
**Would expose:** ICOPE producing a sixth parallel assessment rather than folding into the person's
single plan; and polypharmacy being *displayed* rather than *detected*.
**Blocked on:** W-B. Note this journey is the direct test of the live fabrication W-B removes —
`duplicateTherapyDetected` is currently a dropdown a clinician ticks, so today the system would
report duplicate therapy only because a human already knew.

### 9. Pregnant patient whose medical treatment is coordinated with the Maternity Pack
*Medical problem + pregnancy episode, one record, two owners.*
**Would expose:** a medicine unsafe in pregnancy being continued because the medical view cannot see
the pregnancy episode. The V111 link is deliberately read-only and one-directional; this journey
proves that direction is sufficient, or shows it is not.
**Blocked on:** partially reachable — the V111 pregnancy-episode link exists. The gap is
pregnancy-aware medicine review in the medical workspace.

### 10. Complex medical patient needing surgical consultation without loss of ownership
*Consultation request, surgical opinion, medical team retains the patient.*
**Would expose:** the two failures the brief names explicitly — **loss of ownership** (the patient
silently becoming surgical) and **duplicated clerking** (the surgical team re-documenting a history
that already exists).
**Blocked on:** §14 consultation/MDT (not built).

## How they must be proven

Follow `scripts/runtime-proof/medicine-demonstrations.sh` (record) and
`scripts/runtime-proof/medicine-demonstrations-service.sh` (service): positive **and** negative
controls, against a live estate / clean service rig, with failures named clinically rather than by
status code. A journey that asserts `200 OK` has proven that a server responded, not that a person
was cared for. The clinician walkthrough remains outstanding.

---

## Appendix — the safety proofs (formerly `proposed-demonstrations.md`)

The ten proposals written while the brief was unavailable are not discarded; most are already
enforced as tests. They are the pack's honesty properties, not its §23 list.

| Former proposal | Now carried by |
|---|---|
| Diagnose → SHR reaches BUTANO | `OutboxPublisherRouteTest`, `ConditionShrBoundaryTest` |
| One disease one entry (duplicate enrolment refused) | `uq_pct_programme_enrolments_active`, constraint-bite guard |
| Regimen change keeps history | `uq_pct_treatment_regimens_current`, constraint-bite guard |
| Exit requires a reason | V108 CHECK, constraint-bite guard |
| Failed read ≠ no disease | `medicine-workspace.test.tsx` |
| Three meanings of empty | `cds-topic-panel.test.tsx` |
| Unstated certainty stays unstated | `ConditionShrBoundaryTest` |
| Confidential lane is stated, not implied | declared gap — `SPECIALLY_PROTECTED` inert |
| Ward admission has three states | `medical-ward-round.test.tsx` |
| Cohort counts are not headcounts | `ProgrammeCohortShapingTest`, `ProgrammeCohortQueryTest` |

Two remain unenforced and are honest debts: the confidentiality lane above, and end-to-end proof of
the SHR hop against a live estate rather than a unit boundary.
