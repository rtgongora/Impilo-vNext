# Adult Medicine and Medical Specialties Pack — Lease Record

Delivery-boundary record for the **Adult Medicine and Medical Specialties Clinical Domain Pack**
(opened 2026-07-26, base anchor `4fd5dc1d5`, branch
`claude/staging-ux-orchestration-remediation-Yypyl`). Companion to
[`iatg-emergency-leases.md`](iatg-emergency-leases.md),
[`iatg-surgery-procedures-leases.md`](iatg-surgery-procedures-leases.md),
[`iatg-rmnp-leases.md`](iatg-rmnp-leases.md) and
[`iatg-trauma-leases.md`](iatg-trauma-leases.md).

Follows the precedent set by the surgery/procedures programme (`0ac48f8ce`) that a domain pack
authors its own lease file. Reservations are asserted now and are open to coordinator amendment.

---

## 1. Working rules inherited, not reinvented

The shared-tree rules in [`iatg-emergency-leases.md`](iatg-emergency-leases.md) §1 apply to this
pack verbatim and are not restated: path-scoped commits, `git pull --ff-only`, verify with
`git show --stat HEAD` before pushing, and — cutting a migration number requires **both**
`ls .../db/migration | sort -V | tail` **and** `git status --porcelain services/*/.../db/migration/`,
because the dangerous neighbour is the migration nobody has committed yet.

That rule was written from this pack's `pct` **V060**, which was untracked when the emergency lane
found it. **V060 is now committed** (`5f12f390e`) and visible to history-based checks; the
exception note in the emergency lease §3c can be relaxed to an ordinary claim, but V058–V069 still
must not be read as contiguous.

## 2. Lane ownership

`pct-service` is shared with the emergency and RMNP lanes, so ownership here is stated by
**concern**, not by service.

| Owns (may edit) | Notes |
|---|---|
| `pct-service` — the **longitudinal problem list** (`pct_problems`, `ProblemController`, `ProblemService`), the medical episode, adult clerking and structured history, medication reconciliation, chronic-disease and programme enrolment (HIV/TB/NCD) | Emergency owns `emergency_episode`, `ed_*`, triage, alerts, disposition and handover in the same service. RMNP owns the pregnancy episode and labour observations. |
| `clinical-knowledge-platform-service` — adult medical rule **content**, CV risk, multimorbidity and deprescribing engines | Content lives in classpath JSON, not migrations (R6, following RMNP and emergency). `rules/tabular/**` is hot for RMNP — extend via content and the `bandKey` / `appliesWhen` hooks, never by editing `PredicateEvaluator`. |
| `libs/medicine-domain/**` — **NEW library** | Pure Java, no Spring, mirroring `libs/paediatric-domain`. To be registered in the `libraries:` block (which `libs/paediatric-domain` is still missing from — emergency lease §7). |
| `services/experience-bff/**` — problem-list, clerking, chronic-care and specialty controllers only | Never the emergency, trauma, Daidzai or Madi controllers. |
| `ui/one-ui-shell/src/{app/ehr/[patientId],features/medicine}/**` — problem list, clerking, specialty workspaces | The EHR patient surfaces. Emergency owns `app/clinical/emergency`. |
| `docs/clinical/adult-medicine-domain-pack/**`, `docs/clinical-governance/medicine/**` | |
| `scripts/runtime-proof/medicine-*.sh`, `scripts/guard/check-medicine-*.sh` | |

**Call-only, never migrated by this pack:** `procedures-service` and `surgery-service` (new,
surgery lane) · `inpatient-service` `procedure_*` and `resuscitation_*` · `daidzai-service` ·
`madi-service` · `vito-service`.

**Shared, requires a handoff:** `services/pom.xml`, `docker-compose.runtime.yml`,
`config/full-boot-service-classification.yml`, `deploy/helm/**`, `docs/runbooks/port-allocation.md`,
everything else under `docs/registry/`.

## 3. Reserved migration blocks

Heads verified at `4fd5dc1d5` by **both** methods required in §1; no untracked migrations existed
anywhere in the tree at that moment. **Every block sits above every existing claim**, so a
collision is impossible in either direction.

**One band per lane.** The estate has since converged on separating lanes by hundred rather than by
adjacent range: **Adult Medicine V100s · Emergency V200s · Surgery/Procedures V300s**, with the
low numbers left to the original services and the closed trauma programme. This pack's block was
already the V100 band and is unchanged by that convergence; the table below is restated against the
leases as committed rather than as first drafted.

| Service | Head today | Existing claims (as committed) | **Adult Medicine block** | Sub-ranges |
|---|---|---|---|---|
| `pct-service` | V100 | trauma V035–V069 · RMNP V058/V059/V061–V069 · **this pack V060 + V100** · emergency V200–V239 · **paediatric/IMAM V104–V105 (carve-out, see note)** | **V100–V129 excl. V104–V105 (retired)** | **landed: V100 problems · V101 medical episode · V102 clinical documents · V103 problem links · V106 structured history · V107 medication reconciliation · V108 programme enrolment (HIV/TB) · V109 treatment regimen · V110 prevention programmes (PrEP/TPT)** · chronic registers V111–V120 · reserve V121–V129 |
| `clinical-knowledge-platform-service` | V006 | surgery V007–V020 · RMNP V007–V009 · emergency V200–V229 | **V051–V080** | rule-governance + source provenance V051 · adult content tranches V052–V070 · reserve V071–V080 |
| `inpatient-service` | V066 | trauma V035–V064 (**dead space**) · surgery V067–V080 · emergency V200–V229 | **V111–V130** | medical ward workspace V111–V114 · reserve V115–V130 |
| `zibo-service` | V007 | surgery V008–V014 · emergency V200–V219 · **confidential map V008** | **V035–V049** | **HIV/TB programme value sets V035 (landed)** · further adult value sets V036–V040 · DAK artifact registry V041–V044 |
| `oros-service` | V017 | trauma V015–V024 · surgery V018–V024 · emergency V200–V219 | **V050–V069** | result acknowledgement + action tracking V050–V052 |
| `butano-service` | V002 | none | **V010–V029** | FHIR resource coverage for the medical record |
| `telemonitoring-service` | V005 | none | **V010–V029** | adult chronic monitoring programmes |

`pct` V061–V069 remains RMNP's; this pack does **not** treat the gap between V060 and V100 as
available.

> **Coordinator amendment, 2026-07-26, second revision (V104–V105 PERMANENTLY RETIRED).** History: the
> IMAM lane's `V058/V059` collided with RMNP's identically numbered migrations and were renumbered to
> V104/V105 — inside this pack's block — which the first revision of this note ceded to IMAM as a
> carve-out. IMAM has since renumbered again to **V400/V401** (joining the hundred-band convention:
> Adult V100s · Emergency V200s · Surgery V300s · paediatric/IMAM V400s), so the carve-out is moot.
> **V104 and V105 are retired and must never be used by any lane**: they were applied on at least one
> estate database under the IMAM names before the rename, and a third lane reusing them is the exact
> situation that forces the next live flyway-history repair. They are scar tissue; leave the gap.
> This pack's block reads **V100–V129 excl. V104–V105 (retired)**; landed through **V110**
> (V106 structured history · V107 medication reconciliation · **V108 programme enrolment** ·
> **V109 treatment regimen** · **V110 prevention programmes** — widens the programme + regimen-stage
> CHECKs to admit HIV_PREVENTION/TB_PREVENTION and the PREVENTIVE stage); next free is **V111**.
> The W3 programme migrations took V108–V110 rather than the originally-planned V113–V116 slot:
> V106/V107 had already consumed the clerking/reconciliation numbers, and the programme spine was
> the next work. Sub-ranges in the table below are a plan, not a contract.
>
> **W3 (HIV/TB DAK programmes) is COMPLETE.** All 13 HIV/TB standards in
> `docs/clinical-governance/medicine/standards-baseline.json` are SHIPPED (treatment-and-monitoring,
> the diagnostic front door, prevention programmes PrEP/TPT, TB/HIV co-infection, and PMTCT); the
> coverage-exclusions register is empty. One integration seam is tracked outside the DAK register:
> the soft HIV_CARE-enrolment → RMNP-pregnancy-episode link (PMTCT), pending the RMNP lane.
> Process notes for every lane: (1) a renumber must target numbers above ALL committed lease claims, or
> obtain a carve-out written into the affected lease; (2) **a carve-out is valid only while the state it
> accommodates holds** — write the condition into the note (this note's first revision went stale within
> hours when the renumber it accommodated was itself renumbered); (3) a migration is landed when
> `flyway_schema_history` says so on the target, not when the merge is green.

> ⚠️ **Read the lease files, not the announcement messages.** The emergency lane's cross-session
> message announced pct V070–V099, inpatient V067–V094, ckp V010–V029. Its *committed* lease says
> pct V200–V239, inpatient V200–V229, ckp V200–V229 — and the message's inpatient and CKP ranges
> would have collided head-on with surgery's committed V067–V080 and V007–V020. The file was
> already correct; the message was a stale draft. Reconciled 2026-07-26 against
> `iatg-emergency-leases.md` and `iatg-surgery-procedures-leases.md` as committed.

## 4. Cross-pack contracts this pack consumes rather than reinvents

The emergency lane froze four seams (`iatg-emergency-leases.md` §5). This pack accepts all four and
records how each binds here.

1. **`pct.emergency_episode` is the canonical emergency episode.** This pack's *medical episode* is
   a different object and must not be built as a rival: an emergency episode is one facility
   presentation, whereas a medical episode is the longitudinal arc of a medical problem across
   contacts, facilities and years (FHIR `EpisodeOfCare`, not `Encounter`). The medical episode
   **links to** `emergency_episode_id` where a presentation began in emergency care; it never
   replaces, contains or re-derives it, and this pack creates no acuity concept.
2. **Handover acceptance transfers responsibility, and nothing else does.** See §4a — the seam is
   now frozen in detail, agreed with the emergency lane on 2026-07-26.
3. **Acuity has exactly one authority: WHO IITT.** This pack adds no fifth severity scale.
   `pct_problems.severity` (added at V060) is deliberately a *different* concept and is recorded as
   such here so it is never mistaken for one: it is the clinician's assessment of how severe a
   **standing problem** is — moderate persistent asthma, Child-Pugh B cirrhosis — not how urgent
   **this presentation** is. Triage acuity is a property of an arrival; problem severity is a
   property of a disease. Where this pack needs presentation urgency it derives from IITT priority.
4. **Traceability is shared machinery.** This pack declares its standards in
   `docs/clinical-governance/medicine/standards-baseline.json` and runs them through the existing
   `scripts/clinical/dak/build-traceability-matrix.py` and `scripts/guard/check-dak-traceability.sh`.
   No rival matrix or guard. New families this pack registers: `WHO_PEN` · `WHO_HEARTS` ·
   `WHO_ICOPE` · `WHO_MHGAP` · `WHO_AMS` (antimicrobial stewardship) · `WHO_PALLIATIVE`.
   `WHO_DAK` is RMNP's family and is **shared, not forked** — this pack's HIV and TB DAK artefacts
   register under it, because a second DAK family would split one traceability surface in two.
   `EDLIZ` and `ZW_POLICY` are shared and already exist.
   **Landed (W3–W6):** `docs/clinical-governance/medicine/standards-baseline.json` declares 21
   standards, ALL SHIPPED and cited by governed CKP content, coverage-exclusions register empty:
   13 HIV/TB under `WHO_DAK` (W3); `WHO_HEARTS` + `WHO_PEN` (W4 CV-risk + deprescribing);
   `MEDICINE.PROCEDURE_APPROPRIATENESS`/EDLIZ (W5 procedures); and `WHO_ICOPE`, `WHO_MHGAP`,
   `WHO_AMS`, `WHO_PALLIATIVE`, `WHO_CANCER` (W6 specialty CDS). The shared matrix and
   `check-dak-traceability.sh` run over them unchanged — no rival machinery. All content is
   `ENGINEERING_SEED` (primaryTextVendored=false) pending MoHCC ratification.

## 4a. The emergency → medicine handover, frozen

Agreed with the emergency lane 2026-07-26, before either side wrote it. Emergency owns recognition
through disposition; this pack owns definitive medical-specialty management after stabilisation and
classification. The handshake is the only thing that moves responsibility across that line.

**The invariant.** Raising a request transfers nothing. Emergency stays accountable until this pack
writes an acceptance carrying **its own record id** — for admission, the existing `pct_admission_id`
echo, not a new mechanism. There is deliberately no timeout that discharges responsibility, and this
pack does not want one: a patient nobody has accepted is emergency's patient, and a clock that
silently reassigned them would produce exactly the unowned-patient failure the handshake exists to
prevent.

**Sequence.**

1. Emergency sets disposition `ADMIT_MEDICINE` and creates
   `pct.emergency_handover(handover_kind = TO_MEDICINE)`. The episode enters
   `OPEN_AWAITING_ACCEPTANCE`.
2. This pack reads pending handovers, and on accepting a patient creates the admission, then
   `POST /v1/emergency/handovers/{id}/accept` carrying `pct_admission_id`.
3. `DECLINED` and `EXPIRED` return the patient to emergency care; expiry raises a Rito case.

**Four things this pack commits to.**

- **Accept is idempotent on `pct_admission_id`.** A retried acceptance must not create a second
  admission or a second acceptance. Replay is normal on an intermittent link, and a duplicated
  admission is worse than a failed one.
- **The clinical record does not restart at the door.** Problems raised in emergency stay the same
  `pct_problems` rows — this pack takes ownership by setting `responsible_service`, never by
  re-recording the diagnosis. Re-clerking a stabilised patient into a second problem list is the
  duplication the W1 cross-clinic guard exists to stop, and an internal handover is the case where
  it would happen most.
- **Certainty travels and is usually not CONFIRMED.** A problem raised in emergency is typically
  `SUSPECTED` or `WORKING`; this pack refines it. Emergency should record what it actually knows
  rather than defaulting, because a `WORKING` diagnosis promoted to `CONFIRMED` by an admission
  handshake is a diagnosis nobody made.
- **Acuity is not carried forward as severity.** IITT priority is a property of the arrival and
  stops at the door. `pct_problems.severity` is a property of the disease and is this pack's to set.
  No fifth scale, and no silent translation between the two.

> 🔴 **V108 WITHDRAWN — a cross-lane FK cannot live in the referencing lane's band.**
> The emergency-episode foreign key was written at V108 and withdrawn before deployment. **Flyway
> applies in version order, and V108 < V200**, so on a clean full boot the constraint would run
> before `emergency_episode` exists: `ERROR: relation "emergency_episode" does not exist`.
> Reproduced independently on a scratch schema. It passed a live dry-run only because the preview
> estate already had V200 deployed — the dry-run proved the constraint *applies and bites*, and
> never tested whether it could apply *in migration order on a clean database*. Green in one
> environment is not green.
>
> **The general rule, and it binds every lane:** with one band per lane, a cross-lane foreign key
> must live in the band of the lane that owns the **referenced** table, never the referencing one.
> Bands solve collisions and create a dependency-ordering trap, and the trap is invisible anywhere
> the referenced table is already deployed. Contributed by the emergency lane, whose band
> convention surfaced it and who caught it.
>
> **Where the constraint now lives:** the emergency lane's block, above V200, carrying this pack's
> migration header reasoning verbatim. `pct_medical_episodes` is still this pack's table; only the
> constraint that references *their* table sits in *their* range, which is what the rule requires.

**The FK, agreed and scheduled.** `pct_medical_episodes.emergency_episode_id` is a soft reference
today only because `pct.emergency_episode` does not exist; a hard constraint would couple the two
lanes' deploy order. Once the emergency lane pushes V200 it is promoted, **written by this pack in
this pack's block** — one table, one migration block, so anyone reading V100–V129 to understand
`pct_medical_episodes` sees its whole history rather than an incomplete picture they cannot detect.

Confirmed against the estate on disk by the emergency lane, 2026-07-26:

- Target is `pct.emergency_episode(episode_id)`.
- The PK is a **single-column UUID, not composite with `tenant_id`** — the house convention across
  every recent pct table, and every cross-table FK in pct references one column. So this pack's
  column list does not change, and the `(tenant_id, emergency_episode_id)` index stays correct as
  an access path.
- **No row is ever hard-deleted.** A merge sets `MERGED` and `merged_into_id`; nothing cascades into
  or out of the episode. `ON DELETE RESTRICT` is therefore a backstop, not a workflow.
- Added **`NOT VALID` first, then `VALIDATE CONSTRAINT` as a separate statement**. By then the table
  may hold rows, and a plain `ADD CONSTRAINT` takes a lock and aborts the entire migration on the
  first dangling value — the same failure class as this pack's V100 CHECK, which is not worth
  repeating now that it has bitten once.

**What this pack needs from emergency on the handover row:** `emergency_episode_id`,
`subject_cpid`, `journey_id`, the problems raised (as `pct_problems` ids, not free text), the
disposition, and the requesting clinician. Nothing else is required for acceptance.

Additionally, from the surgery/procedures lane: **`procedures-service` (port 8395) is the execution
authority for procedures.** This pack owns indication, appropriateness, the specialty plan,
interpretation and long-term follow-up for medical procedures (lumbar puncture, paracentesis,
pleural aspiration, dialysis access, endoscopy, bone marrow), and calls that service to execute
them. It migrates no `procedure_*` table and builds no rival pipeline.

## 5. Engineering constraints inherited

From the RMNP lane via the emergency lease §5a, restated only where they bind this pack:

- **`PredicateEvaluator` is shared by four engines** — any edit is a retest-all-four event. The
  adult CDS engines extend via content and the `bandKey` / `appliesWhen` hooks.
- **Never overload `ageDays`.** Facts are one flat map per request, so overloading a fact key
  corrupts every other rule in the same evaluation. Adult banding (eGFR, blood pressure, HbA1c)
  uses `bandKey`.
- **`pct` sets `validate-on-migrate: false`** (`application.yml:31`), which hides a Flyway
  *validate* failure without making a lower-versioned migration apply — the schema can diverge from
  what the code expects with nothing saying so.
- The `any` combinator's evidence short-circuit — which would have under-reported triggering
  findings for every adult rule too — was **already fixed** by the CKP lane in `0299a57cb`. This
  pack does not re-fix it.

## 6. Findings this pack has already fixed, that other lanes depend on

| Fix | Commit | Why other lanes care |
|---|---|---|
| `pct_problems.severity` | `5f12f390e` | The problem list had nowhere to record a severity the experience layer has always collected. Any lane writing a problem can now record one — and must leave it NULL when it is not stated. |
| BFF problem list pointed at `/v1/problems` | `1b4e5bcf1` | The BFF called `/v1/conditions`, which pct-service has **never served**, so every problem-list read 404'd and every write threw, on every clinical surface. `pct_problems` existed the whole time and nothing had ever called it. Any lane reading a patient's problems through the BFF was reading nothing. |
| Clients stopped rendering a failed read as an empty list | `4fd5dc1d5` | `PatientBanner` — on every clinical screen — discarded `isError` and fell through to `?? []`, so every patient read as having no conditions even after the BFF was hardened to 502. **The lesson generalises: hardening a controller achieves nothing while the query consumer turns the error back into an empty list.** Lanes that hardened a BFF endpoint should check their consumers. |

## 6a. `pct_problems` now has closed vocabularies — any lane writing a problem must use them

`V100` added CHECK constraints to `pct_problems`. Two existing values were **renamed**:
`RISK` → `RISK_FACTOR` and `SOCIAL` → `SOCIAL_CIRCUMSTANCE`. Any lane that writes a problem — the
emergency lane raising a diagnosis at triage, RMNP recording an obstetric problem — must send a
value from the closed set or the write is refused:

- `category` — DIAGNOSIS · SYMPTOM · SYNDROME · GUIDELINE_CLASSIFICATION · RISK_FACTOR ·
  FUNCTIONAL_CONSEQUENCE · SOCIAL_CIRCUMSTANCE
- `diagnostic_certainty` — SUSPECTED · DIFFERENTIAL · WORKING · CONFIRMED · REFUTED, **or null**
- `clinical_status` — ACTIVE · RECURRENCE · RELAPSE · REMISSION · INACTIVE · RESOLVED

`ProblemService` validates before the write, so an unrecognised value returns a 400 naming the value
and the allowed set rather than a Postgres constraint-violation 500. `ProblemVocabularyTest` parses
the migration and fails the build if the Java and SQL copies drift.

Also new and worth knowing: adding a problem that matches one already open on the patient returns
**409** with the existing problem and two resolutions (`SAME_PROBLEM_RETURNING`,
`DISTINCT_PROBLEM`) rather than creating a second row. A lane writing problems needs to handle that
status, and a lane treating 409 as a generic failure will lose the write.

**Adopted from RMNP, and it applies here too: derive only TRUE, never FALSE.** "No pregnancy has
been recorded" and "she is not pregnant" are different statements and only the first is one this
system can make. The problem list follows the same rule throughout — an unstated severity or
certainty is null rather than a default, a problem list that could not be read is an error rather
than an empty list, and the duplicate guard's silence means "nothing matched", never "this is
definitely new".

## 7. Known defects recorded so nobody builds on them

- **`pct_problems` cannot express the canonical problem model.** It is flat: code, display,
  `clinical_status` (ACTIVE/INACTIVE/RESOLVED), `category` (DIAGNOSIS/SYMPTOM/RISK/SOCIAL), onset,
  severity, notes. It carries no diagnostic certainty, verification status, evidence, responsible
  service, review date, recurrence or complication linkage — so a suspected diagnosis, a working
  diagnosis and a confirmed one are today the same row. W1 evolves it; **do not build a second
  problem store in the meantime.**
- **Completion, not deletion — and the wave is named.** Product-owner rule issued fleet-wide
  2026-07-26: *we don't delete things to hide incomplete functionality, we complete functionality.*
  For this pack that binds two items. The structured-history endpoints are owed **real PCT storage
  in W2**, not a permanent honest failure; the 502 they return today is a transitional state and
  every response now carries an `in_development` block naming this lane and W2, because an
  unattributed "unavailable" is indistinguishable from an outage and a reader cannot tell whether
  to wait, escalate, or record the history elsewhere. The same applies to the Child-Pugh, MELD,
  APACHE II, SOFA and adult chronic instruments assigned to this lane: any in-development notice
  names lane and wave until the governed forms-service definitions land.

  What was removed from that controller was a **fabrication, not a capability** — a
  `GOLDEN_PATH_DEMO_PATIENT` fixture that no user could reach, and five dead `ResultSet` mappers for
  tables that were never created. The test for distinguishing the two, proposed by the emergency
  lane and worth reusing: **does removing this make a capability less available to a user?** A
  zero-caller shadow hides nothing; a live caller on a path nobody serves is a broken feature, and
  deleting it would hide exactly the incompleteness the rule exists to surface.

- **The structured-history vertical is unbacked.** `experience-bff`
  `/internal/v1/ehr/{social,family,functional,procedures,advance-directives}-history` calls
  pct-service `/v1/ehr/*` paths that **do not exist**, and falls through to a
  `GOLDEN_PATH_DEMO_PATIENT` fixture — demo data on a production path. For every other patient it
  returns an affirmative empty history, including "no advance directive". The orphan tables
  `social_history_entries`, `family_history_members`, `family_history_conditions`,
  `functional_assessments`, `patient_procedures` and `advance_directives` live in the BFF's own
  Flyway scripts (`V32`), and **the BFF has no datasource outside the CI test profile**, so they
  have never been created. W2 builds the system of record. Count rows before dropping anything.
- **`PatientFacts` is paediatric-shaped** — `ageMonths`, `ageDays`, `sex`, `pregnant`, `programmes`,
  `conditionCodes`, `gestationalAge`. Renal function, hepatic function, weight, current medicines
  and comorbidity burden cannot reach the CDS engines at all, so renal dosing, interaction
  checking, contraindication and deprescribing are unimplementable until it grows. W2. **Any lane
  needing an adult clinical fact should coordinate rather than add a parallel context object.**
- ~~**BUTANO maps only `Condition` and `CarePlan`**~~ — **CORRECTED 2026-07-28, and the truth is
  worse.** Verified against the code: **nothing writes `Condition` or `CarePlan` into BUTANO at all.**
  Both appear only on the *read* side (`TimelineService`, `ReconciliationService`,
  `ResourceStatsService`, `IpsBundleGenerator` — each already enumerating 11–12 types). The one
  `Condition` publisher, `experience-bff`'s `FhirPublisher`, is **dead code that nothing injects.**
  BUTANO actually ingests `Patient`, `Observation`, `ImagingStudy`, `DiagnosticReport`,
  `DocumentReference`, `ServiceRequest`.
  **Consequence any lane writing clinical truth should know: the PCT problem list never reaches the
  SHR.** `ProblemService` writes an outbox row (`aggregateType = "PROBLEM"`) but
  `OutboxPublisher.routeTopic()` has no arm for it and no BUTANO listener consumes one — so a
  recorded diagnosis is absent from the SHR, the IPS bundle and the cross-facility timeline.
  `EpisodeOfCare`, `ClinicalImpression`, `RiskAssessment`, `DetectedIssue`, `GuidanceResponse`,
  `MedicationStatement`, `Flag` and `Goal` likewise have no producer — but note `butano-service` is
  an unrestricted HAPI JPA server, so storage/REST for all of them already works: **the missing half
  is always the producer, never the FHIR store.** A "readable-but-never-written" resource looks
  mapped and is not; that is how this claim survived unchallenged.
- **No HIV, TB or NCD model exists anywhere in the repository.** Verified by search: no
  `viral_load`, `art_regimen`, `cd4`, `tb_treatment` or programme-enrolment concept. The two
  deepest requirements of this pack are green field.

## 8. Wave index

W0 problem-list vertical repair (**done** — `5f12f390e`, `1b4e5bcf1`, `4fd5dc1d5`) ·
W1 canonical problem and episode model · W2 adult CDS foundation (`PatientFacts`, medication
reconciliation, CV risk) · W3 HIV and TB DAKs · W4 chronic disease + multimorbidity engine ·
W5 inpatient medicine + medical procedure integration · W6+ specialty workspaces, geriatrics/ICOPE,
palliative, oncology, analytics, offline, demonstrations.

## 9. Open question for the coordinator

**Confidentiality blocks W3.** The HIV/TB requirement is explicitly "one person record with
appropriate programme views and confidentiality", which rests on `SPECIALLY_PROTECTED`. That class
is currently decorative: it appears only in its own declaration and in one
`ResourceSensitivityClassifier` switch arm that maps it to the *same* visibility tier as
`FULL_CLINICAL`. Nothing assigns it, no policy branches on it. Building HIV care on it would
manufacture a false assurance for the clinician deciding whether it is safe to write something
down, and for the patient told their record is confidential. Either the enforcement seam lands
first, or HIV/TB ships at `FULL_CLINICAL` with the gap stated on the record. Recorded for a
product-owner ruling; the paediatric pack's Wave 5 is blocked on the same seam.
