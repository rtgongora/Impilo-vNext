# Adult Medicine pack — handover brief

**For:** whoever picks this up next (Fable 5 / Opus 5 in Cursor).
**Written:** 2026-07-28, at the point the previous session reached its usage limit. It is a handover,
not a completion report — §25 Definition of Done is **NOT MET** and this brief says exactly where.
The four register inaccuracies named in §4 below **have been corrected** in
`completion-register.md` as part of committing this file; the confessions are kept because the
*pattern* behind them is the most useful thing in here.
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl` (canonical). Worktree
`/opt/impilo/repos/Impilo-vNext/.claude/worktrees/gifted-rubin-406cae`, branch
`claude/gifted-rubin-406cae`. HEAD `9776f8ec3`, everything pushed, working tree clean apart from
`.claude/settings.local.json` and untracked `reports/journeys/*` evidence dirs.

---

## 1. Read these first, in this order

| Document | Why |
|---|---|
| `docs/clinical/adult-medicine-domain-pack/brief.md` | **The source of truth.** 25 sections, verbatim, product-owner supplied. Cite it by section number; never paraphrase it into a report. |
| `docs/clinical/adult-medicine-domain-pack/completion-register.md` | **Per-section status.** Start here for "what is done". PARTIAL is never counted as delivered. |
| `docs/clinical/adult-medicine-domain-pack/demonstrations.md` | §23's ten journeys and what each would expose. |
| `docs/clinical/adult-medicine-domain-pack/analytics-coverage.md` | §21: 6 implemented / 6 partial / 9 not computable, each with owner. |
| `docs/clinical/adult-medicine-domain-pack/specialty-map.md` | §8's thirteen and the architecture bet behind them. |
| `docs/registry/iatg-adult-medicine-leases.md` | Migration bands, what this pack may touch, cross-lane contracts. |
| `docs/clinical/adult-medicine-domain-pack/implementation-report.md` | Historical. It predates the register; **where they differ the register wins**, and its own banner says so. |

---

## 2. Current state

**3 DONE · 18 PARTIAL · 2 NOT BUILT** against the brief's 25 sections. **§25 Definition of Done is NOT
MET** — five of its ten conditions outstanding.

DONE: §2 domain boundary, §4 canonical episode/problem model, §16 clinical decision support.

**Verified baselines — hold or beat these:**

```
pct-service            794/794
butano-service          53/53
clinical-knowledge-…  1145/1145
medicine UI (vitest)    88/88
demonstrations rig      35/35  (+8 explicitly stated gaps)
next build              exit 0
EXPECTED_ROUTE_COUNT    824
```

**What this pack owns and where it lives**

- `services/pct-service` migrations **V100–V115** (V104/V105 permanently retired) — problems,
  medical episodes, clinical documents, problem links, structured history, medication
  reconciliation, HIV/TB programmes, prevention programmes, pregnancy link, chronic registers,
  examination framework, consultation + MDT, result action.
- `services/clinical-knowledge-platform-service` — 12+ governed content packs under
  `src/main/resources/clinical/`, plus the `multimorbidity/` package (§9 engine).
- `libs/medicine-domain` — pure clinical arithmetic, 90 tests. **New this programme.**
- `ui/one-ui-shell/src/features/medicine/**` — workspace, CDS, examination, multimorbidity,
  registers, specialties, consultation.
- `scripts/runtime-proof/medicine-programmes-journeys.sh` and `…/medicine-demonstrations.sh`.

---

## 3. Two defects that outrank all planned work

Both verified verbatim in the code, not taken on report.

### P0 — `/clinical-tools` ships sixteen calculators that compute nothing and say "Validated"

`ui/one-ui-shell/src/app/clinical-tools/page.tsx:536` — `CalculatorDetail` renders a static panel. No
inputs, no arithmetic, no persistence. Its own text reads *"Enter patient parameters to compute…
Results are calculated client-side"* and *"Full calculator form connected to Clinical Knowledge
Platform (port 8270)"* — it is connected to nothing; the only live call on that page is the formulary
search at `:570`. It then renders a badge reading **"Validated"**.

Affected: **MELD, Child-Pugh, eGFR CKD-EPI**, SOFA, GCS, CHA₂DS₂-VASc, CURB-65, Wells DVT/PE, BSA,
creatinine clearance, anion gap, corrected calcium, corrected sodium and more.

This is the severest instance of the defect class this whole programme removed — and worse than the
others because it asserts validation. **It also contradicts this pack's own work on screen:** the
nephrology `notBuilt` entry says "eGFR is consumed but never computed" while that page tells a
clinician eGFR CKD-EPI is Validated and point-of-care.

**Fix:** `libs/medicine-domain` genuinely computes eGFR CKD-EPI 2021 + KDIGO staging, NYHA, Rockwood
CFS, opioid equianalgesia and treatment burden — wire those. For the rest, strip the "Validated"
badge and the false CKP-connection line and state plainly that the calculator is not built. Add a
guard test that fails if a calculator renders without either a computation or an explicit not-built
state.

**Corroboration:** `apps/mobile/provider-app/src/data/specialtyToolRegistry.ts` is an independent
survey of the same ground that reaches the *opposite, honest* conclusion — `"MELD Score": "Absent
from the estate."` Its WIRED/CONSOLIDATED/IN_DEVELOPMENT scheme with a totality guard test is the
pattern to copy.

### P1 — duplicate MDT system of record, and it is mine

`services/pct-service/…/V051__mdt_board_sessions.sql` (`pct_mdt_sessions`, `pct_mdt_case_items` —
telemedicine lane, TM-B15) predates `V114__consultation_and_mdt.sql` (`pct_mdt_decisions`) by weeks.
Two tables, two vocabularies, two BFF controllers (`TeleconsultController` `/mdt/sessions` vs
`ConsultationsController` `/mdt`). A clinician recording an MDT outcome has two places to put it and
no rule about which.

They model different things — V051 the **meeting** (chaired agenda, per-participant
consensus/dissent, pseudonymised identity policy, anchored to a *referral*), V114 the **decision**
(attribution, treatment intent, next action, anchored to a *problem*) — but both claim to be the MDT
record, and that is a duplicate system of record under the estate's plainest guardrail.

**Do not unilaterally alter V051.** Write a consolidation proposal (likely: V051 owns the session,
V114 owns the decision, explicit link, one stated rule) and take it to the telemedicine lane. Until
resolved, both surfaces should acknowledge the other exists.

---

## 4. Honest feedback — where I got things wrong

Read this before trusting any artefact I produced. Three of my own errors, and the pattern behind
them.

**I built the duplicate MDT table because I trusted my own artefact as evidence.** For V113
(examination) I ran a proper system-of-record search and found the trauma survey and the newborn
JSONB blob. For V114 I did not — I went straight from the demonstration rig's line
`CANNOT "MDT has NO record at all"` to building. **That line was my own earlier analysis, not a
search result.** The rig's CANNOT lines read like findings because running something produces them,
but a CANNOT asserts *absence*, and absence is exactly what a search proves and an assumption does
not. **Treat every CANNOT line in `medicine-demonstrations.sh` as a hypothesis until you have
searched.**

**I over-corrected once and got the correction half wrong.** Earlier I loudly retracted an overstated
offline blocker, citing `useOfflineStore` as live in two mobile screens. It is live in **one**
(`HouseholdListScreen.tsx:27`); `ScreeningScreen.tsx:21` imports it and only ever calls
`useSyncEngine()`. The register still repeats my wrong citation. I also recorded the read-gate
blocker as being in `offline-edge-service` when it is in `tshepo-offline-service`.

**I listed a capability as missing that this pack itself had already built.** Advance-care planning
appears on the geriatrics *and* palliative `notBuilt` lists. It exists — `pct_advance_directives`,
V106, our own migration, with a live UI at `/ehr/[id]/advance-directives`.

**The pattern:** every one of these was a claim about absence made without a search. The pack's
safety properties are strong precisely because they were mutation-proven; my *claims about the
estate* were not held to the same standard. Apply the mutation discipline to assertions, not only to
code.

**What I'd tell you to trust:** the migrations and their constraints (every one has clean-boot
runtime proof with negative controls), the mutation-proven guards, and `analytics-coverage.md`
(built by searching, not asserting). **What to re-verify:** any statement in the register about
another service, and any `notBuilt` entry.

---

## 5. Outstanding work, in order

### Wave 0 — the two defects above
`/clinical-tools`, then the MDT consolidation proposal.

### Wave 1 — inert engines and wrong records (hours, not days)

1. **Turn on the CKP Kafka relay.** `impilo.clinical.kafka.relay-enabled` defaults to `false`, so
   every `DetectedIssue` the §9 engine produces sits in `clinical.event_outbox` and reaches nobody.
   Configuration per environment + a runtime proof that a row reaches BUTANO.
2. **Compose the four missing §9 sources in the BFF.**
   `services/experience-bff/…/controller/MultimorbidityController.java` sends conditions and
   medicines only. Add appointments, investigations, functional status, patient priorities, care
   team. **Four of seven detections report UNDETERMINED because nobody sends them, not because the
   engine cannot answer.** Preserve the omit-never-empty rule: a source that could not be read must
   be *absent* from the body, never `[]` — the engine distinguishes them and the whole honesty model
   rests on it.
3. **Expose the structured-history writes through the BFF.** pct serves `POST` for social history,
   family history, functional assessments, procedures and advance directives; the BFF controller is
   `@GetMapping`-only. So advance directives and ECOG/Barthel/Lawton/Katz are readable and
   unrecordable. One controller, five methods.
4. **Fix the register inaccuracies** in §4 above, plus the four §8 corrections in §6 below.

### Wave 2 — close the §25 conditions this pack can close

5. **Transfer of care (pct V116).** A consultation can *recommend* a takeover and deliberately cannot
   perform one; nothing else performs one either. Needs the transfer act, its acceptance by the
   receiving service, and the ownership move as a consequence of **acceptance, never of an opinion**.
   Do not weaken `ConsultationService`'s existing guard (it captures the owner before the write and
   throws if it moved).
6. **§7's eleven graphics.** `graphic`/`site`/`laterality` are stored and CHECK-validated by V113 and
   **no diagram renders anywhere**. Pure UI over existing fields; do not invent a parallel shape.
7. **§19 `DetectedIssue` retirement.** Stop the duplicate anticoagulant and the engine goes quiet —
   no event — so the archived issue stays FINAL forever. Needs a "was detected, now is not" signal.
   Guessing from silence is the failure this pack is written against, so this is a design step.
8. **§23's service layer.** Record layer proven 35/35; drive the ten journeys through the services
   next. `medicine-demonstrations.sh` is the template.

### Wave 3 — remaining pack-owned breadth

§6 full clerking (~25 of 35 items + the conditional don't-re-document logic, the harder half) ·
§5 journey state · §10 interaction checking, renal/hepatic dosing (arithmetic now in
`libs/medicine-domain`), formulary/refill · §13 ward workspace (~18 items) · §1's five missing DAK
parts · §17 malaria, sickle-cell, RHD · §22's 25 named scenarios · §19's remaining ~23 FHIR producers
· §3's unmodelled care settings · §12's other 15 procedures (CKP content) · §18 integration breadth
(each needs a *reason* before wiring) · §24's DAK traceability for non-HIV/TB specialties.

---

## 6. §8 — the estate sweep result (this changes the lists)

The thirteen `notBuilt` lists in
`ui/one-ui-shell/src/features/medicine/specialties/specialty-config.ts` were authored **from the
brief, not from a search**. A sweep found them substantially right but wrong in ~14 places.

**Strike and replace with integration — already owned elsewhere:**

| Listed as notBuilt | Owned by | Note |
|---|---|---|
| Transfusion planning (§8.9) | **madi-service** — full blood service: orders, samples, crossmatch, reservation, issue, transfusion episodes, adverse reactions, haemovigilance; live UI `/madi/transfusion` | A second transfusion SoR would be a serious violation |
| Glucose monitoring + device data (§8.5) | **telemonitoring-service** — DIABETES programme, seeded bands, LOINC metrics, device assignment, alerting | Link |
| Advance-care planning (§8.12, §8.13) | **This pack** — `pct_advance_directives` V106 + UI | Remove from both lists |
| Tobacco cessation (§8.2) | **simba-service** — `PLAN-TOBACCO-QUIT`, risk-factor escalation | Link |

**Have a real spine — need a specialty-facing view, not a system of record:** endoscopy, biopsy,
dialysis preparation + vascular access (all governed in `procedures-service` with consent templates,
safety pauses, BLOCK-severity capability gates), ECG/echo/spirometry (catalogued, fulfilled through
the guarded PROCEDURE workflow), volume-status tracking (inpatient `fluid_balance_record` + the
telemonitoring heart-failure weight trigger).

**~30 correctly absent:** EEG, cardiac device follow-up, peak flow, inhaler assessment,
symptom-control scores, HbA1c *trend*, insulin titration, seizure *classification*, disease-activity
scores, INR/anticoagulation monitoring, TNM staging, CTCAE toxicity, survivorship, clinical
photography, continence, bereavement, palliative symptom instruments.

**A guard exists and must keep passing:** a test fails the build if any specialty's `notBuilt` list
is empty. An empty list is a claim to have built everything §8 named.

---

## 7. Routed — not this pack's to build

| Item | Owner | Smallest unblocking change |
|---|---|---|
| §20 offline read gate | **tshepo-offline-service** | Add `READ_PROBLEM`/`READ_PROGRAMME`/`READ_CONDITION` to `OfflineRulesEngine.READ_ACTIONS:51` **and** extend the `READ_PATIENT`-covers branch at `:124`. Done that way, **no token or config change is needed** — clinicians already hold `READ_PATIENT`. Otherwise `OfflineProperties.java:48` and `application.yml:51` must grow too. |
| §20 replay breadth | **offline-edge-service** | The client is not the constraint — `OfflineEdgeService.replayActions:143` only dispatches to FHIR for two vitals action types. Generalise `ButanoFhirClient.postObservation` → `postResource(type, …)` and add dispatch arms. |
| §11 patient-level duplicate ordering | **oros-service** (+ CKP for intervals) | A live guard already exists: `OrderStateMachine.guardNoDuplicateTeleconsultOrder:218`, called from `placeOrder`/`createDraft`, 409, indexed. It is **teleconsult-scoped and keyed on `sourceRef`**. Broaden to `patientCpid + ziboOrderCode` against `MultimorbidityEngine`'s `repeatIntervals`. |
| §11 order sets | **oros-service** | No backend concept at all. `ui/…/data/orderSets.ts` is a hardcoded constant; `EncounterCart`/`OrderSetPicker` are **dead — nothing renders them**. The BFF's `applyOrderSet` route starts a pathway session and places no order despite the name. |
| §15 measurement domains | **telemonitoring-service** | Symptoms, PROs, functional status, medication adherence, fluid status, dialysis data, peak flow; plus per-reading `performer` and clinical `context`. Extensions inside `telemonitoring`. |
| §15 enrolment surface | **telemonitoring-service + BFF** | Plan create/approve, device issue, programme admin are backend-only. Today the only enrolment path is an OROS order coded `TM-MON-<PROGRAMME>`. |
| Dead code | **oros-service** | `ResultService.acknowledgeResult(UUID)` looks a result up and returns it, saving nothing, while its javadoc promises delegation. **Confirmed uncalled** — dead, not a live no-op. Its signature is incompatible with `AcknowledgementService.acknowledge(String orderId, …)`, likely why delegation was never written. Delete or implement. |
| Dead routes | **wellness lane** | `/monitoring/care-plans` and `/monitoring/provider-dashboard` are registered, rendered, and have no data hooks. |

**This pack owns one §15 item:** a problem-list anchor from `tm_monitoring_plans` to `pct_problems`.
Copy `surgery-service`'s `PctProblemContributionClient`. We are the requester; telemonitoring builds.

---

## 8. Flags a fresh agent will otherwise trip over

**§15 nearly caused a serious violation.** `telemonitoring` V004 made that service the **single
designated SHR writer** for monitoring-band Observations, explicitly closing a three-writer sprawl.
An adult-medicine home-vitals table would be **writer number four**. Going from "§15 NOT BUILT" to
"build §15" would have hit the sharpest guardrail in the estate.

**Eleven of telemonitoring's twenty programmes ship `'{}'` thresholds and will never alert** —
`PALLIATIVE_CARE`, `REHABILITATION`, `ELDERLY_FRAILTY`, `MEDICATION_ADHERENCE` among them. Do not
read those as existing capability. The seven real ones: HYPERTENSION, DIABETES, HEART_FAILURE,
COPD_CHRONIC_RESP, HIGH_RISK_PREGNANCY, HOME_OXYGEN, FEVER_OUTBREAK_FOLLOWUP.

**The procedures catalogue records `PENDING_MOHCC_RATIFICATION` and nothing gates on it.** Status
stored, not enforced. Confirm before treating endoscopy/dialysis as clinically live.

**`SPECIALLY_PROTECTED` ships inert** (`CATEGORY_MAP_RATIFIED = false`). HIV confidentiality is
*derived and displayed* but not *enforced*. This is why a patient-level register is **refused** for
confidential programmes rather than filtered — serving it would attach a label that restricts
nothing. Do not "fix" that refusal by filtering.

**Migration headroom will bite.** The lease reserves pct **V100–V129** (V104/V105 retired). V100–V103
and V106–V115 are used — **fourteen slots left**, and Waves 2–3 plausibly need most of them. **Ask
the coordinator for a band extension before the band is exhausted.** Renumbering an applied migration
is never reusable; V104/V105 are the standing proof.

**Two MDT tables** (see P1) — do not add a third.

---

## 9. Environment traps that will waste your time

- **`next build` needs upstreams exported** or it fails before compiling:
  `API_GATEWAY_URL`, `NEXT_PUBLIC_API_GATEWAY_URL`, `BFF_URL`, `NEXT_PUBLIC_BFF_URL`. It takes well
  over two minutes — do not wrap it in a short timeout.
- **`next build` is the UI gate. Never `tsc --noEmit`** — it reports SUCCESS when imports cannot
  resolve, which is worse than no check.
- **`mvn -pl services/<svc>` does not resolve here** (reactor root is `services/pom.xml`). Use
  `mvn -o -f services/<svc>/pom.xml test`.
- **`mvn … | grep … | tail` masks failure** — the pipeline exit is `tail`'s. Check `${PIPESTATUS[0]}`
  or don't pipe.
- **`*IT`-named tests are skipped by surefire.** Name Spring context tests `*Test` or they silently
  never run. A whole class of tests here did exactly that.
- **`@SpringBootTest` without `@Transactional` leaks rows across methods** and the failure looks like
  a broken query. It isn't.
- **JVM tests run against H2 and never apply a migration** — only a real-Postgres boot or the
  runtime-proof catches entity↔table drift.
- **`pg_isready` is not sufficient in a rig** — the postgres entrypoint runs a *temporary* init
  server and `pg_isready` answers for it. Wait on a real query.
- **The route-count merge trap fires most merges.** `EXPECTED_ROUTE_COUNT` is hand-maintained; two
  lanes can produce the same number and git auto-merges the line while the array gains both.
  **Always re-derive from the file after any merge** and let `routes.test.ts` verify it. Never by
  arithmetic.
- **Shared checkout.** `/home/robert/Impilo-vNext` symlinks to `/opt/impilo/repos/Impilo-vNext`;
  several sessions share one git index. Always `git commit -- <paths>`; **never** `git commit -a`,
  never stash a foreign edit, and prefer merge over rebase (rebase's autostash sweeps other people's
  work). Never symlink `node_modules` across trees — a `next build` walked one and deleted the shared
  source three times.
- **A peer's uncommitted work will break your build** for a few minutes at a time. Re-run before
  diagnosing.

---

## 10. The verification standard — please keep it

This pack's value is mostly that its claims are checkable. Four rules did the work:

1. **Mutation-prove every guard.** Break it, watch it go red, restore. Three separate defects this
   programme found were checks that *could not fail* — including one where I guarded the wrong side
   of a condition and the test passed either way.
2. **Every migration gets clean-boot runtime proof with positive AND negative controls.** Landed is
   not correct: a table can be present, shape-correct and constraintless.
3. **A failed read must never render as an absence.** "No conditions" and "the list did not load" are
   different clinical claims and only the first may come from a call that succeeded. This is the
   single most repeated fix in the pack.
4. **The reassuring option must justify itself.** `NO_ACTION_NEEDED` needs a reason; `NOT_EXAMINED` is
   never `NORMAL`; an unread source is `UNDETERMINED`, never clear.

And the documentation rule: **update `completion-register.md` in the same commit as the work it
describes, and never count PARTIAL as delivered.** The register exists because this pack was twice
reported as complete off individually-true sentences.
