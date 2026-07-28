# Adult Medicine and Medical Specialties Clinical Domain Pack — Implementation Report

> **START HERE, 2026-07-28.** The per-section truth of this pack now lives in
> [`completion-register.md`](completion-register.md) — one row per brief section, with PARTIAL never
> counted as delivered. This report predates it and describes waves rather than sections, so where
> the two differ the register wins.
>
> Summary of the register: **3 sections DONE, 17 PARTIAL, 3 NOT BUILT.** This report's accurate claim
> that "the clinical decision-support backbone is complete" has twice been read as "the pack is
> complete". The backbone is a minority of the brief.

**Status as of 2026-07-27.** W0 (vertical repair), W1 (canonical clinical spine), most of W2
(adult decision-support foundation) and **W3 (HIV and TB as governed DAK-traceable programmes)** are
implemented, tested and pushed. **W4 (chronic-disease CDS), W5 (medical-procedure indication) and W6
(specialty decision support — geriatrics/ICOPE, mental health, antimicrobial stewardship, palliative,
oncology) are also implemented and on canonical** as governed content; what remains is experience-shell
surface (specialty UI workspaces, inpatient ward, analytics, offline) and the §23 demonstrations.
Everything clinical shipped
so far is `ENGINEERING_SEED` and requires MoHCC and specialist ratification before it is used to
drive care.

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
| ~~V108~~ | **Withdrawn.** The emergency-episode FK cannot live in this pack's band — see §5a. The constraint now lives in the emergency lane's block above V200. |

---

### W3 — HIV and TB as governed programmes

HIV and TB were verified green field — no `viral_load`, `art_regimen`, `cd4` or `tb_treatment`
concept existed anywhere. W3 adds the two genuinely new concepts on the existing spine and extends
rather than forks everything else.

| Component | What it does |
|---|---|
| **V108** `pct_programme_enrolments` | Governed longitudinal membership in `HIV_CARE` or `TB_TREATMENT` (extensible to NCD). Anchors to the diagnosis in `pct_problems` via a hard FK rather than re-recording it — "one disease, one entry" made structural. A partial-unique index refuses a second active enrolment per programme; `EXITED` requires a reason and a date, never a default, so a lost-to-follow-up patient cannot read as a treatment success. |
| **V109** `pct_treatment_regimens` | The ART line / TB phase a person is on. A change is a new row; the current regimen is the one with no `ended_on`, at most one per enrolment; a stage that does not belong to the programme (an ART line on a TB enrolment) is refused. |
| zibo **V035** value sets | ART/TB regimens, programme observations (viral load, CD4, WHO stage, TB bacteriology) mapped to LOINC where confident, and WHO cohort treatment outcomes. Extends the V008 confidential map so HIV observation codes classify as HIV content. |
| CKP `hiv-programme-rules.json` / `tb-programme-rules.json` + `ProgrammeGuidanceService` | Governed DAK decision support: HIV viral-load interpretation and treatment failure, advanced disease; TB phase transition, bacteriological monitoring, resistance and outcome. Mirrors the danger-sign engine — content-driven, three-valued, never edits `PredicateEvaluator`. |
| DAK traceability (`docs/clinical-governance/medicine/`) | HIV/TB standards declared under the shared `WHO_DAK` family; 6 SHIPPED, 7 DEFERRED with owner-wave. The guard went red→green (rules cited dakIds no baseline declared). |
| `experience-bff` `ProgrammesController` + `one-ui-shell` programme surface | `/internal/v1/programmes/**` over the real pct APIs and CKP guidance; the EHR page lists enrolments with the confidential-lane badge, current regimen and guidance panels. Holds the failed-read-is-not-a-finding discipline throughout. |

**Viral load, CD4 and TB bacteriology are `pct_observations` (V057), not new tables** — the
enrolment and the regimen are the only genuinely new objects.

**Confidentiality, and the ENFORCE gap stated plainly.** HIV is specially protected; TB is not (TB
is notifiable, confidential from nobody — conflating them would obstruct a public-health duty and
dilute what the protected class means). W3 wires the confidential lane where it belongs: zibo
classifies the HIV codes (V035), pct derives `confidential` from the programme (one source of truth,
no second flag to drift) and exposes it, and the BFF/UI present it. **What W3 does not do, and does
not claim to:** the `SPECIALLY_PROTECTED` enforcement — the PDP filtering HIV content on reads and
the write-time stamping of records via the zibo classifier — ships in SHADOW and is the
`SPECIALLY_PROTECTED` seam's own remaining scope. No HIV record is stamped with a protection class
today, because zibo withholds the stampable class while its map is unratified. A record never
carries a protection label that does not protect it.

**Proven and pending-live.** The migration truth is proven on a clean Postgres by
`scripts/runtime-proof/medicine-programmes-journeys.sh`: 78 pct migrations apply in version order (no
cross-band ordering trap for V108/V109), and all W3 constraints bite with positive and negative
controls (anchor FK, programme CHECK, both partial-uniques, EXITED-requires-reason), `probe rows
left: 0`. The BFF ingress proof — a positive trust assertion and a tokenless negative control
against the deployed estate — is **pending the pct redeploy carrying V108/V109**, coordinated with
the vitals-vertical session; it is stated as pending, not implied.

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

### 5a. Where this standard was not enough — a correction

The V108 foreign key passed exactly the verification above and would still have failed on a clean
full boot. Flyway applies in version order and V108 < V200, so the constraint ran before
`emergency_episode` existed. The dry-run proved it *applied* and *bit*, both true, against a preview
schema where the referenced table was already deployed — it never tested whether it could apply **in
migration order on a clean database**.

**A negative control only covers the axis you point it at.** Mine was pointed at dangling
references, which is the failure a reader expects, rather than at ordering, which is the one the
band convention creates. Withdrawn before deployment (`ac620b355`), reproduced independently on a
scratch schema first.

**The general rule, contributed by the emergency lane whose band convention surfaced it:** with one
band per lane, a cross-lane dependency — a foreign key, an insert referencing a higher-band table,
an `ALTER` against one — must live in the band of the lane that owns the **referenced** object,
never the referencing one. A lower band always applies first, so it can never depend on a higher
one. The trap is invisible in every environment except a clean boot.

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

**W3 — HIV and TB DAKs. COMPLETE** (this session), in full — nothing deferred out of the wave. The
medicine coverage-exclusions register is empty; all thirteen HIV/TB standards are SHIPPED, cited by
governed content:
- Treatment-and-monitoring core (V108/V109 data model, zibo V035, CKP content): viral-load
  monitoring, treatment failure, advanced disease, TB monitoring, DR-TB, TB outcome.
- Diagnostic front door (CKP content): HIV testing algorithm (HIV.TESTING), TB symptom screening
  (TB.SCREENING), TB diagnostic algorithm (TB.DIAGNOSIS).
- Prevention programmes (V110 widens the programme + regimen-stage vocabularies to admit
  HIV_PREVENTION and TB_PREVENTION with a PREVENTIVE stage; CKP content): HIV PrEP (HIV.PREP) and TB
  preventive therapy (TB.TPT), plus TB/HIV co-infection (HIV.TB_COINFECTION).
- PMTCT (CKP content): maternal ART, maternal viral load in pregnancy, infant prophylaxis, early
  infant diagnosis (HIV.PMTCT).

Confidential lane wired (HIV care and PrEP confidential; TB and TB-prevention FULL_CLINICAL), ENFORCE
gap stated. Runtime-proven: 79 pct migrations apply in version order on a clean boot (V108/V109/V110),
10/10 constraint bites; 135 CKP content fixtures across eight packs green.

**The PMTCT cross-lane data link is built and proven** (V111, `pct_programme_enrolments.pregnancy_episode_id`).
The RMNP lane accepted the seam in detail: a nullable, read-only soft FK from the HIV_CARE enrolment
to `pct.pct_pregnancy_episodes(pregnancy_episode_id)` — the pointer lives on the HIV side only, never
a back-reference on the maternal spine, never written back, and one-directional so the maternity
summary cannot infer HIV status from the link's existence. V059 (< V111) creates the target, so the
FK is safe in this pack's band; runtime-proven — 81 migrations apply in clean-boot order, the FK
refuses a dangling episode and is convalidated. The infant side is this pack's: an HIV_CARE enrolment
on the infant CPID, triggered by RMNP's birth-time `pct_newborn_birth_records.hiv_exposure` flag,
owning prophylaxis + EID (no new maternal/newborn record). At RMNP's request the migration is shared
for their review before it goes to canonical.

**One live proof outstanding, unchanged:** the BFF ingress tokenless negative control is unprovable
on preview (auth disabled estate-wide, `allow-anonymous=true`); positive ingress is green.

**W4 — chronic-disease CDS. DONE.** Cardiovascular risk (WHO HEARTS) and deprescribing / renal-
medication safety (WHO PEN), as governed content on the shared tabular engine evaluated against the
`PatientFacts` extended in W2. Closes the W2-remaining CV-risk item.

**W5 — medical-procedure indication/interpretation. DONE** (the governed decision surface).
`procedure-indication-rules.json` (EDLIZ) owns indication, contraindication and interpretation for
LP, paracentesis (incl. SBP interpretation) and pleural aspiration; `procedures-service` remains the
execution authority. The inpatient medical-ward *workspace* (inpatient-service V111–V114 band) is UI/
service surface, not yet built.

**W6 — specialty decision support. DONE** for the governed-content core, grounded in the WHO families
the lease reserved: geriatrics/ICOPE (WHO_ICOPE), mental health (WHO_MHGAP), antimicrobial stewardship
(WHO_AMS), palliative care (WHO_PALLIATIVE), and oncology early diagnosis (WHO_CANCER). All eight
medicine-CDS topics are reachable through the CKP evaluator and the BFF passthrough.

Every W4–W6 pack is `ENGINEERING_SEED` (primaryTextVendored=false); all standards are SHIPPED in the
traceability matrix and the DAK guard is green. 383 content fixtures across twelve packs run through
the real engine.

**Experience surface — DONE (28 Jul 2026).** The backbone is no longer API-only:
- `/ehr/[patientId]/medicine` — the medicine workspace (programmes, problem list, allergies), each
  block degrading independently so a failed read never renders as "none".
- `/ehr/[patientId]/medicine/cds` — all eight governed CDS topics, with an empty alert list rendered
  three distinct ways (nothing fired / incomplete / evaluation failed), because only one of those is
  an all-clear.
- Two pages that had shipped **unregistered and unreachable** (`programmes`,
  `workspace/[specialty]`) are registered and in the chart sidebar.
- The specialty workspace stopped misleading in both directions: order-set "buttons" with no
  handler are now reference chips, and tools that said "Not available yet" while their pages existed
  are wired.

**Inpatient medical ward — DONE.** `/ehr/[patientId]/ward-round` is patient-centred, complementing
the ward-centred boards under `/clinical/inpatient` rather than duplicating them. It exists because no
existing round surface shows the problem list. Admission status is three states — a failed read
renders as UNKNOWN, never as "not admitted", because the dangerous direction of a failed read is
always the reassuring one.

**Analytics — DONE, by a different route than planned.** `GET /v1/programme-enrolments/cohort-counts`
in the system of record, **not** a reporting-service definition: reporting holds a separate database
and cannot see `pct.*`, so a seeded SQL template would be registered, ACTIVE and unrunnable. (Five
existing regulatory definitions are already in exactly that state — proven live against preview and
routed to the ROM lane.) Counts enrolments, not people, and says so. The query is **executed** by `ProgrammeCohortQueryTest`
— a context test that could not exist until the pct Spring context was made bootable under H2
(all six `@SpringBootTest` classes here are named `*IT`, which surefire skips, so the context could
not boot and nothing was trying to boot it; the two facts protected each other).

**Offline — NOT built, and the reason is structural rather than "not got to yet."** Three independent
blockers, each verified and each in a service this pack does not own:

| Blocker | Evidence | Owner |
|---|---|---|
| The offline action vocabulary has no read for problems or programmes | `READ_PROBLEM` / `READ_PROGRAMME` / `READ_CONDITION`: **0 occurrences** in `tshepo-offline-service`; `READ_ACTIONS` is a hardcoded `Set.of(READ_PATIENT, READ_MEDICATION, READ_ENCOUNTER, READ_OBSERVATION)` | trust plane |
| ~~No offline collection is registered in production~~ — **CORRECTED, this was wrong** | The mobile seam **exists and works**: `useOfflineStore<Household>("households")` is live in `HouseholdListScreen.tsx` and `ScreeningScreen.tsx`. My original claim came from grepping the *factory* (`createCollection`, whose only references are intra-package) instead of the *hook* that wraps it. Grep the consumer-facing API, not the internal one. | — (no blocker) |
| The edge replay surface carries one resource | `offline-edge-service` `ButanoFhirClient` posts `/fhir/Observation` **only** | offline-edge |

So an HIV/TB clinician offline cannot see whether a patient is on ART or which regimen — the single
most valuable offline fact in this domain.

**Two genuine blockers, not three.** Both are read-authorization and replay breadth in services this
pack does not own; the mobile side is ready and has a working exemplar to copy. **The minimal change
that would unblock it** is additive and read-only: add `READ_PROBLEM` and `READ_PROGRAMME` to
`OfflineRulesEngine.READ_ACTIONS`, and extend the edge replay beyond `Observation`. This pack would
then be **consuming an existing seam rather than building one** — a materially better position than
first reported, and the reason the correction was worth making rather than quietly leaving.

**Remaining:** the ten §23 demonstrations — **none of which is runnable end to end today.**

> ⚠ **Correction (2026-07-28) — this paragraph previously said the brief was not in this repository
> and the §23 list was unrecoverable.** That was true when written and is no longer. The product
> owner supplied the brief, and it is now committed verbatim as
> [`brief.md`](brief.md) — the pack's source of truth. The real §23 list is in
> [`demonstrations.md`](demonstrations.md); the earlier `proposed-demonstrations.md` has been
> retired, with its ten proposals mapped in that file's appendix to the tests that now carry them.
>
> **The proposals were not merely different — they were a different kind of thing.** They asserted
> the pack's *safety properties* (a failed read must not render as "no disease"). §23 asks for ten
> *clinical journeys*. Comparing the two makes the honest scale of the remaining work visible: this
> report's claim that "the clinical decision-support backbone is complete" is accurate, but the
> backbone is a minority of the brief. Read [`brief.md`](brief.md) §§1–25 against this report before
> treating any part of the pack as finished.

The clinical decision-support backbone (§16) is complete and is the pack's strongest asset. What
remains is the multimorbidity engine (§9), chronic registers, the examination framework (§7), the
thirteen specialty workspaces (§8), diagnostic orchestration (§11), consultation and MDT (§14),
interoperability breadth (§19), offline (§20), analytics (§21), and the demonstration proofs (§23).

> ⚠ **Correction (2026-07-28) — the earlier BUTANO statement in this report was wrong, and it
> understated the gap.** This report previously said "BUTANO still maps only `Condition` and
> `CarePlan`". Verified against the code, **nothing writes `Condition` or `CarePlan` into BUTANO at
> all.** They appear only on the *read* side — in `TimelineService`, `ReconciliationService`,
> `ResourceStatsService` and `IpsBundleGenerator`, each of which already enumerates 11–12 resource
> types. The only `Condition` publisher in the tree is `experience-bff`'s `FhirPublisher`, which is
> **dead code — nothing injects it.**
>
> What BUTANO actually ingests today: `Patient`, `Observation`, `ImagingStudy`, `DiagnosticReport`,
> `DocumentReference`, `ServiceRequest` (via `ButanoEventConsumer` and OROS's `ButanoIntegration`).
>
> **The real gap is bigger than a missing mapping: this pack's central artefact — the problem list —
> never reaches the shared health record.** `ProblemService` does write an outbox row
> (`aggregateType = "PROBLEM"`), but `OutboxPublisher.routeTopic()` has no arm for it and BUTANO
> consumes no problem-shaped topic. So a diagnosis recorded in PCT is invisible to the SHR, the
> IPS bundle and cross-facility timeline. Correcting the claim here rather than leaving it: a
> readable-but-never-written resource looks mapped and is not. Raised with the coordinator; the fix
> (a producer arm + consumer + read-side registration) is scoped in the delivery plan.
>
> Because `butano-service` is an unrestricted HAPI JPA server, storage/REST for `EpisodeOfCare`,
> `MedicationStatement`, `RiskAssessment`, `DetectedIssue`, `Goal` and `Flag` already work — the
> missing half is always the **producer**, never the FHIR store.

---

## 8. Risks

**Clinical content requiring ratification.** Every vocabulary, threshold and cut-off shipped is an
engineering seed. Correctly structured, tested and traceable — but not national protocol.

**One item sits with another lane by design.** The emergency-episode FK is written in the emergency
lane's block above V200, for the ordering reason in §5a. Nothing else is owed in either direction.

**Migration numbering on a shared tree.** pct **V104/V105 are permanently retired** (applied under
one name by the IMAM lane, then renamed to V400/V401 — applied-then-renamed is never reusable). This
pack's block is **V100–V129 excluding V104–V105**; next free is **V109**.

**Five sessions share one working tree.** `/home/robert/Impilo-vNext` is a symlink to the same repo.
Path-scoped commits always; `git pull --ff-only`, and on failure **merge, never rebase** — rebase
demands a clean worktree and `--autostash` would sweep a peer's uncommitted work into your stash.
Verify with `git diff --name-only origin/<branch>..HEAD` listing only your own files. A peer's push
carries your local commits.
