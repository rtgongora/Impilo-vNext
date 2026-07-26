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

| Service | Head today | Existing claims | **Adult Medicine block** | Sub-ranges |
|---|---|---|---|---|
| `pct-service` | V060 | trauma V035–V069 · RMNP V058/V059/V061–V069 · **this pack V060** · emergency V070–V099 | **V100–V129** | problem model V100–V103 · medical episode V104–V105 · clerking + structured history V106–V109 · medication reconciliation V110–V112 · programme enrolment (HIV/TB/NCD) V113–V116 · chronic registers V117–V120 · reserve V121–V129 |
| `clinical-knowledge-platform-service` | V006 | surgery V007–V020 · emergency V021–V040 · RMNP V041–V050 | **V051–V080** | rule-governance + source provenance V051 · adult content tranches V052–V070 · reserve V071–V080 |
| `inpatient-service` | V066 | trauma V035–V064 (**dead space**) · surgery V067–V080 · emergency V081–V110 | **V111–V130** | medical ward workspace V111–V114 · reserve V115–V130 |
| `zibo-service` | V007 | surgery V008–V014 · emergency V015–V034 | **V035–V049** | adult medical value sets V035–V040 · DAK artifact registry V041–V044 |
| `oros-service` | V017 | trauma V015–V024 · surgery V018–V024 · emergency V030–V049 | **V050–V069** | result acknowledgement + action tracking V050–V052 |
| `butano-service` | V002 | none | **V010–V029** | FHIR resource coverage for the medical record |
| `telemonitoring-service` | V005 | none | **V010–V029** | adult chronic monitoring programmes |

`pct` V061–V069 remains RMNP's; this pack does **not** treat the gap between V060 and V070 as
available.

## 4. Cross-pack contracts this pack consumes rather than reinvents

The emergency lane froze four seams (`iatg-emergency-leases.md` §5). This pack accepts all four and
records how each binds here.

1. **`pct.emergency_episode` is the canonical emergency episode.** This pack's *medical episode* is
   a different object and must not be built as a rival: an emergency episode is one facility
   presentation, whereas a medical episode is the longitudinal arc of a medical problem across
   contacts, facilities and years (FHIR `EpisodeOfCare`, not `Encounter`). The medical episode
   **links to** `emergency_episode_id` where a presentation began in emergency care; it never
   replaces, contains or re-derives it, and this pack creates no acuity concept.
2. **Handover acceptance transfers responsibility, and nothing else does.** Emergency → Medicine
   admission uses the **existing `pct_admission_id` handshake echo**, which the emergency lane has
   already committed to reusing. Raising an admission request leaves the episode
   `OPEN_AWAITING_ACCEPTANCE`; only this pack's own accepting write, carrying this pack's record id,
   permits closure. Demonstrations 3 and 5 (heart failure and stroke, both arriving through
   emergency) are the proofs. **This is the seam the emergency lease flagged for coordination with
   Adult Medicine.**
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

## 7. Known defects recorded so nobody builds on them

- **`pct_problems` cannot express the canonical problem model.** It is flat: code, display,
  `clinical_status` (ACTIVE/INACTIVE/RESOLVED), `category` (DIAGNOSIS/SYMPTOM/RISK/SOCIAL), onset,
  severity, notes. It carries no diagnostic certainty, verification status, evidence, responsible
  service, review date, recurrence or complication linkage — so a suspected diagnosis, a working
  diagnosis and a confirmed one are today the same row. W1 evolves it; **do not build a second
  problem store in the meantime.**
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
- **BUTANO maps only `Condition` and `CarePlan`** of the resources the medical record needs.
  `EpisodeOfCare`, `ClinicalImpression`, `RiskAssessment`, `DetectedIssue`, `GuidanceResponse`,
  `MedicationStatement`, `Flag` and `Goal` have no mapping.
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
