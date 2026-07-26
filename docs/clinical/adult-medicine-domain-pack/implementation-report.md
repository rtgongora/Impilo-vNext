# Adult Medicine and Medical Specialties Clinical Domain Pack — Implementation Report

**Status as of 2026-07-26.** W0 (vertical repair), W1 (canonical clinical spine) and most of W2
(adult decision-support foundation) are implemented, tested and pushed. W3 onward are designed and
outstanding. Everything clinical shipped so far is `ENGINEERING_SEED` and requires MoHCC and
specialist ratification before it is used to drive care.

Lease and coordination record: [`docs/registry/iatg-adult-medicine-leases.md`](../../registry/iatg-adult-medicine-leases.md).

---

## 1. What the audit found

The brief asked for an Adult Medicine pack. What the estate needed first was for the medical record
to be **true**.

**The adult problem list was dead on every path.** `experience-bff` called pct-service at
`/v1/conditions`, which pct-service has never served; pct-service serves `/v1/problems`, which
nothing in the repository had ever called. An orphaned system of record on one side, a 404 on the
other. A recent honesty sweep had already hardened the BFF to return 502 — but `PatientBanner`
destructured only `data`, no `isError`, and fell through to `?? []`, with `allergiesUnavailable`
correctly captured on the line immediately above. So the banner on every clinical screen reported
**"No active conditions" for every patient in the system**, after the BFF started telling the truth.

**The write path was broken twice over.** The web client posts camelCase; the BFF request record
declared only snake_case, with no naming strategy and no key transformation anywhere. Jackson bound
every field to null and `@Valid` returned 400 *before* the request left the BFF. The create path
could not have worked even after the endpoint was fixed.

**`PatientFacts` — the CDS input contract — was paediatric-shaped.** Age, sex, pregnancy,
programmes, conditions. Renal function, hepatic function, weight and the current medicine list could
not reach an engine at all, so every renal-dosing, interaction-checking and deprescribing
requirement in the brief was **unimplementable**, not merely unimplemented.

**HIV, TB and NCD had no data model anywhere.** Verified by search: no `viral_load`, `art_regimen`,
`cd4` or `tb_treatment` concept exists. The two deepest requirements of the brief are green field.

**What already existed and was kept.** The PCT form-response spine with cadre-aware obligations;
`pct_observations` with `data_absent_reason` and a proven outbox → Kafka → BUTANO FHIR hop;
`pct_allergies`; CKP's three-valued `PredicateEvaluator`, tabular rules, classification and
interpretation engines, dose calculator and rule-governance; `inpatient.procedure_episode` (whose
`theatre_id` is nullable, so it serves bedside medical procedures); OROS's order/result/specimen
estate; `telemonitoring-service`, which already covers most of the brief's longitudinal-monitoring
section; the EDLIZ 2025 PDF with SHA-256 provenance. None of it was rebuilt.

---

## 2. What was built

### W0 — the vertical repair

| Component | What it does |
|---|---|
| `PctServiceClient` → `/v1/problems` | One clinical truth, one endpoint. "Condition" stays the BFF's outward noun because the UI is built on it; the translation lives in the composition layer rather than adding a second endpoint to the system of record. |
| `ConditionsController` translation | PCT's `subject_cpid`/`display`/`code`+`code_system` ⇄ the UI's `ConditionResource`. Refuses to surface a non-ICD code as an `icdCode`, and never forwards a client-supplied author — PCT stamps it from the authenticated trust context. |
| Client honesty sweep | `PatientBanner`, the conditions page, history, emergency and consults now distinguish "none recorded" from "could not be read". Mutation-proven. |
| `pct_problems.severity` (V060) | Somewhere to record a severity the experience layer has always collected. |

### W1 — the canonical clinical spine

| Migration | What it does |
|---|---|
| **V100** | Certainty as an axis **orthogonal** to kind. `category` widened to seven kinds (adding `SYNDROME` and `GUIDELINE_CLASSIFICATION`); new nullable `diagnostic_certainty`; `clinical_status` gains `RECURRENCE`, `RELAPSE`, `REMISSION`. |
| **V101** | `pct_medical_episodes` — a course of care over time. Many-to-many to problems. Closing requires a reason. |
| **V102** | `pct_clinical_documents` — the clinical index document-service never owned. |
| **V103** | `pct_problem_links` — evidence, complications, comorbidity, medicines, investigations, goals. |
| Duplicate guard | Adding a problem already open answers **409** with the existing problem and two resolutions, rather than refusing or silently merging. |

### W2 — the adult decision-support foundation

| Component | What it does |
|---|---|
| `PatientFacts` + `DatedMeasurement` | Weight, renal function, hepatic impairment and the medicine list reach an engine for the first time, each carrying **when it was true**. |
| **V106** `pct_structured_history` | Social, family, functional, past procedures, advance directives. |
| `OrosMedicationIntegration` | The current medicine list, over the S2S `client_credentials` seam. |
| **V107** `pct_medication_reconciliations` | The comparison event and its findings. |
| **V108** | `emergency_episode_id` promoted from a soft reference to a real FK, once the emergency lane's V200 landed. `RESTRICT`, `NOT VALID` then `VALIDATE`. Proven on the live schema: applies, validates, and refuses a dangling reference. |

---

## 3. The safety properties that recur

Five principles are enforced structurally rather than left to callers. Each addresses a way clinical
software misleads quietly, and each was learned from a defect found in this estate.

1. **Absent is never normal, and never a default.** Unstated severity, certainty, social risk and
   hepatic stage all stay NULL. "Mild" is the one severity that stops a reader looking; `CONFIRMED`
   is the one certainty that ends an investigation; "low" is the one risk that closes a
   conversation; `COMPLETED` is the one episode ending that turns a lost patient into a success
   statistic. Absent facts are **omitted** from the rules map rather than nulled, because a null
   that coerces to zero is dialysis-grade renal failure.

2. **A failed read is not a finding.** "No conditions", "no allergies", "no advance directive", "no
   medicines", "no active ED visits" and "nothing is blocking this discharge" are affirmative
   clinical claims. None may be produced by a call that failed — at the controller *or* at the
   client that consumes it.

3. **Stale is a third state, and more dangerous than missing.** A gap prompts someone to measure; a
   stale value looks entirely usable and prompts nothing. `DatedMeasurement` reports its age and
   deliberately does not decide what counts as stale — that varies by drug and context and belongs
   in governed content. A value whose date nobody recorded reports its age as null, never zero.

4. **One disease, one entry.** Status can express recurrence so a relapse does not become a second
   problem; the duplicate guard stops three clinics forking one diagnosis; an internal handover
   takes ownership by setting `responsible_service`, never by re-recording.

5. **Unfinished must never read as clean.** A reconciliation completes only when every discrepancy
   has a decided action and the record names who finished it.

---

## 4. Two guards, and why they are complements

| Guard | Catches | Owner |
|---|---|---|
| `npm run test:query-honesty` | A client destructuring `data` without an error signal | Emergency lane |
| `DownstreamRouteContractTest` | The BFF calling a path no downstream serves | This lane |

They are complements by construction: **a wrong route that the BFF has already turned into an empty
200 has no error left to discard.** The route guard found ten more dead paths on its first honest
run, including `/v1/vitals` and a citizen-facing `/v1/records`; its baseline is a debt register that
may only shrink, and a second assertion fails if an entry is kept alive after being fixed — which is
what forced this pack to retire its own stale in-development notice.

**Both guards were decorative on their first version and both were caught by mutation.** Mine
collected method mappings as absolute when they are relative to the class mapping, so a bare
`@RequestMapping("/v1")` made every `/v1/*` prefix look served — it passed while aimed at the exact
defect it was written for. The fleet rule that came out of it: **a guard's first honest run should
be a mutation, not a pass.**

---

## 5. Verification standard

Established with the coordinator and now the fleet standard, from this pack's live evidence:

- **Landed** = `flyway_schema_history` says so **on the target, in the right schema**. pct's history
  lives in the `pct` schema; an unqualified query against `public` returns "relation does not exist"
  and reads exactly like a missing migration.
- **Landed ≠ correct.** A migration is correct when a constraint it declared can be shown to **bite**
  there. Every probe write carries a negative control alongside the positive, because a hand-repaired
  or old-jar table can be present, shape-correct and constraintless — invisible to a positive probe
  until bad data is in.

Proven live on `impilo-full-preview`: V100–V103 applied `success=true`; positive write `WRITE_OK`;
negative `pct_problem_links_no_self_link` refusal; `probe rows left: 0/0`.

---

## 6. Tests

| Suite | Tests | Status |
|---|---|---|
| `pct-service` | 482 | green |
| `experience-bff` | 1235 | green |
| `one-ui-shell` | 2498 (609 files) | green |

`GrowthServiceTest.scoresPretermInfantAgainstCorrectedAge` was red across three runs during this
work — the paediatric lane's corrected-age change in flight. Flagged, never touched, and fixed by
its owner. pct-service closes with a clean suite.

---

## 7. Remaining work

**W2 (nearly done).** CV risk / WHO HEARTS. Hepatic staging is blocked on the governed Child-Pugh
instrument — part of the coordinator-assigned Child-Pugh / MELD / APACHE II / SOFA slice, which must
be **forms-service governed definitions persisted via PCT**, not mobile-local forms.

**W3 — HIV and TB DAKs.** The deepest requirement in the brief and entirely green field. Unblocked:
`SPECIALLY_PROTECTED` now ships in SHADOW, so build against the confidential-lane convention and
**state the ENFORCE gap in the deliverable** rather than waiting for the MoHCC/PO governance act.

**W4** multimorbidity engine · **W5** inpatient medicine + medical procedures through
`procedures-service` · **W6+** specialty workspaces (13 in the brief), geriatrics/ICOPE, palliative,
oncology, analytics, offline, and the ten required demonstrations.

**Not started, and worth stating plainly:** no specialty workspace exists yet; the demonstrations in
§23 of the brief are unproven; BUTANO still maps only `Condition` and `CarePlan` of the 25 FHIR
resources in §19; and no DAK artefact registry exists in Zibo — the only DAK mapping in the
repository remains a single UI file.

---

## 8. Risks

**Clinical content requiring ratification.** Every vocabulary, threshold and cut-off shipped is an
engineering seed. Correctly structured, tested and traceable — but not national protocol.

**Nothing is owed to another lane.** The emergency-episode FK — the one cross-pack item this pack
carried — is closed at V108.

**Migration numbering on a shared tree.** pct **V104/V105 are permanently retired** (applied under
one name by the IMAM lane, then renamed to V400/V401 — applied-then-renamed is never reusable). This
pack's block is **V100–V129 excluding V104–V105**; next free is **V109**.

**Five sessions share one working tree.** `/home/robert/Impilo-vNext` is a symlink to the same repo.
Path-scoped commits always; `git pull --ff-only`, and on failure **merge, never rebase** — rebase
demands a clean worktree and `--autostash` would sweep a peer's uncommitted work into your stash.
Verify with `git diff --name-only origin/<branch>..HEAD` listing only your own files. A peer's push
carries your local commits.
