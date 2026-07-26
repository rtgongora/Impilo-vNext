# Paediatric Clinical Domain Pack — Implementation Report

**Status as of 2026-07-26.** Waves 1 and 2 are implemented, tested and pushed, along with the first
slice of Wave 3. The rest of Waves 3–5 are designed and outstanding. Everything clinical shipped so far is `ENGINEERING_SEED` and requires
MoHCC and paediatric specialist ratification before it is used to drive care.

---

## 1. What the audit found

A six-agent audit of the repository (three exploring, three designing) established the starting
position. Two findings shaped everything that followed.

### The broken vertical

`experience-bff` had shipped Growth, Immunizations and Maternity controllers, a real and tested
WHO 2006 LMS z-score engine with 684 KB of growth tables, and wired UI pages for the growth chart
and immunisation record. All of them proxied to `pct-service` endpoints **that had never been
built**. The BFF caught the resulting 404 and returned an empty list.

The clinical consequence: a nurse could weigh a child, record it, see it accepted, and lose it.
Same for a vaccination. The failure was invisible from every surface — the UI showed success, the
BFF returned 200, and the data went nowhere. Orphaned tables (`V33` growth, `V6` immunizations,
`V34`–`V36` labour/partograph/CTG) sat in the BFF schema with no JPA entity reading them.

### Children were being treated as small adults

- The clinical rules engine applied adult vital-sign thresholds to every patient. A tachycardia
  alert at 120 bpm fires on nearly every well infant; a bradycardia alert at 50 bpm never fires
  for a neonate at 80 bpm, which is peri-arrest.
- The ward early warning score was never calculated at all — the total arrived from the client and
  the server only banded it. No paediatric score existed.
- There was no dose calculator anywhere in the platform: one hard-coded gentamicin band table
  returning a string, and free-text dose expressions elsewhere.
- A newborn had no clinical record of their own. `docs/security/clinical-write-subject-relationship-control.md`
  already documented the consequence: an APGAR score recorded against the mother's encounter is
  refused by the care-relationship guard.

### What already existed and was worth keeping

The PCT form-response spine with cadre-aware obligation resolution and extraction provenance; the
governed form-definition model with age applicability; the clinical-knowledge-platform's rule,
condition-guidance and pathway schema with trace and override auditing; its age-aware
interpretation engine; Zibo's governed terminology artifacts with paediatric reference intervals;
inpatient APGAR and the theatre obstetric→neonatal seam; UBOMI birth notification; VITO's
newborn-without-national-ID registration and guardian linkage. None of this was rebuilt.

---

## 2. What was built

### Wave 1 — safety foundation (6 commits)

| Component | What it does |
|---|---|
| `libs/paediatric-domain` | Pure Java module (no Spring) so one implementation serves services, batch jobs and offline packages. Age to the day; corrected age for prematurity; the ten-band pathway router; the WHO 2006 growth engine extracted from the BFF; nutrition classification; growth-trajectory analysis. |
| `pct_growth_measurements` | Growth registry with measurement conditions (posture, equipment, clothing, observer, confidence). Z-scores stamped at write with standard and engine version. `GET/POST /v1/growth`, `GET /v1/growth/trajectory`. |
| `pct_immunizations` | Administered-dose registry with vial traceability and five distinct not-given outcomes. `GET/POST /v1/immunizations`, `POST /{id}/verify`. |
| `pct_newborn_birth_records` | The child's own birth summary, journey and encounter, opened idempotently from the theatre delivery event or a maternity form. |
| inpatient EWS/PEWS | Server-side NEWS2 and age-banded paediatric scoring from versioned threshold content; client scores recomputed and discrepancies flagged. |
| `NeonatalAdmissionHandler` | Admits the baby in their own right on handover to NICU/SCBU/neonatal care, closing the documented APGAR-403 gap. |
| Age facts + registry | `PatientFacts` gains age in days and gestational age; both registry files hand-edited with the new ownership claims and prohibitions. |

### Wave 2 — decision-support core (3 commits)

| Component | What it does |
|---|---|
| Age-aware vital alerts | Heart-rate and blood-pressure thresholds banded by age from a shared table. Two paediatric-only alerts added: hypotension (a late sign of shock in children) and hypothermia (a danger sign, not a cold room). |
| `PredicateEvaluator` | Three-valued evaluation over a closed operator vocabulary. Clinical logic is inspectable data, not script. |
| Danger-sign engine | Nine rules — ETAT emergency signs, IMNCI general danger signs, young-infant possible serious bacterial infection, hypoglycaemia, complicated severe malnutrition. `POST /internal/v1/clinical/paediatric/danger-signs/evaluate`. |
| `DoseCalculationService` | Weight-based dosing from governed content with enforced maxima, measurable volumes, shown arithmetic, and a critical non-overridable alert on overdose. Replaces the hard-coded gentamicin table. |

### Wave 3 slice 1 — clinical data capture (1 commit)

| Component | What it does |
|---|---|
| `SegmentedControl` (shared-ui) | One-of-N picking as a single interaction with every option visible, 44px targets, radiogroup keyboard semantics, automatic fallback to a select when options are too many or too wordy to fit a phone row. |
| `clinical_assertion` / `exam_finding` field kinds | Yes/No/Unknown/Not-assessed, and the seven examination states. Options come from a canonical set so the same answer means the same thing on every form. |
| Four unimplemented renderer kinds | `boolean`, `datetime`, `terminology_bound` and `fhir_mapped` were declared in the model and rendered the literal text "Unsupported field kind" into clinical forms — a live defect. All four now render properly. |

The capture layer previously could not express the distinction the decision-support engines
refuse to make. A danger sign left blank was indistinguishable from one explicitly ruled out.
Recording "not assessed" now stays possible and produces a warning rather than a block, because
forcing a clinician to pick yes or no to clear a form is how false negatives get written down.

### The safety properties that recur

Three principles are enforced structurally rather than left to callers, because each addresses a
way clinical software kills people quietly:

1. **Silence is never reassurance.** A sign that was not assessed evaluates to unknown, not absent.
   Assessments report what they could not assess and mark themselves incomplete. A confident
   all-clear built on questions nobody asked is the most dangerous output a clinical system can give.
2. **No calculation on missing foundations.** No dose without a verified weight and a known age; no
   paediatric score without an age; no growth z-score outside the range the standard covers. Each
   returns a stated reason rather than a number.
3. **Every recommendation is traceable and reviewable.** Source, version and approval status travel
   with every alert and suggestion; thresholds live in versioned content files so a clinical change
   is a reviewable diff rather than a code deployment.

---

## 3. Tests

| Suite | Tests | Status |
|---|---|---|
| `libs/paediatric-domain` | 77 | green |
| `pct-service` | 259 | green |
| `inpatient-service` | 134 | green |
| `clinical-knowledge-platform-service` | 205 | green |
| `one-ui-shell` clinical-forms + shared-ui | 56 | green |

Two governance gates run clinical content against the live engines: `PaediatricRuleContentTest`
executes every danger-sign rule's own fixtures, and `DoseCalculationServiceTest` executes every
dosing rule's. A content edit that changes clinical behaviour fails the build before it can reach
a patient. Both also assert that every rule cites a source, names a required action, declares the
observations it needs, and does not claim ratification it does not have.

A bug was found by these tests during development: a twelve-year-old fell between the paediatric
early-warning bands and would have been returned as "not calculable" — a hole in the content
presenting as a data problem with the patient. Both threshold tables now have a test that walks
every age from birth and asserts a band exists.

---

## 4. Journeys — honest status

| Definition-of-Done journey | Status |
|---|---|
| **2. Sick newborn (10-day-old, poor feeding, hypothermia)** | **Backend complete and live-proven.** Both CDS layers now answer, and they agree: the danger-sign engine raises possible serious bacterial infection (critical, non-overridable, referral required), and the young-infant classification chart independently returns the same category as pink with an urgent-referral disposition and the neonatal antibiotic first dose at the head of the plan. Neonatal dose restrictions apply; the birth record and neonatal admission exist. No UI. |
| **1. Sick 14-month-old (fever, cough, poor feeding)** | **Backend complete and live-proven.** Danger signs, age-banded vitals, growth with z-scores and faltering, safe dose calculation, and now the IMNCI assess-and-classify tables. Proven on the estate: chest indrawing with a positive malaria test, MUAC 11.9 and some palmar pallor returns severe pneumonia (pink), malaria, moderate acute malnutrition and anaemia, with an urgent-referral disposition and a severity-ordered treatment plan. Missing: immunisation forecast, Dura stock check, Khuluma caregiver instructions, UI. |
| **3. Growth monitoring visit** | **Partial.** Measurement capture, WHO scoring, faltering detection and IMAM eligibility signalling work. Missing: the "what is due today" composition, the plotted chart, IMAM referral creation. |
| **4. Paediatric surgical emergency** | **Not started.** Existing theatre and inpatient spines are reusable; the surgical-abdomen pathway and paediatric surgical clerking are not built. |
| **5. Confidential adolescent visit** | **Not started.** The TSHEPO `SPECIALLY_PROTECTED` sensitivity class exists but has never been assigned; caregiver-context gating is designed, not built. |

**Live-proof status (updated 2026-07-26).** The earlier statement that none of this had run against
a deployed estate no longer holds. pct-service, inpatient-service and the clinical knowledge
platform are deployed to the full preview estate with migrations applied, and the following are
proven against a real database rather than a fixture:

- Growth: a z-score of −2.9 with severe acute malnutrition classified, carrying its standard and
  engine version; the care-relationship guard returning 403 for an unrelated actor.
- Immunisation: a dose recorded elsewhere moving from pending to verified.
- Newborn: preterm and low-birth-weight flags derived, and a replayed delivery event proven
  idempotent.
- PEWS age-banding: identical vitals (heart rate 150, respiratory rate 45) scoring 0/NONE for a
  three-month-old and 6/MEDIUM with escalation for a ten-year-old. This is the defect the wave
  existed to fix, shown fixed on live data.
- Danger signs: a ten-day-old with poor feeding raising possible serious bacterial infection —
  critical, non-overridable, referral required, citing source, version and approval status.
- The honesty property, which matters more than any single rule: a child with nothing assessed
  returns `incomplete: true` with 23 unassessed signs and never an all-clear, while a fully
  assessed negative child returns `incomplete: false`.

The dosing engine is tested but has no HTTP surface of its own, so it has not been exercised live.

**Known limitation, found while proving the above.** The predicate evaluator's `any` combinator
short-circuits, so `triggering_findings` reports only the inputs evaluated up to the first match.
The ten-day-old fired on feeding and did not list the qualifying temperature of 35.0 °C. The alert
and the required action are identical either way, so this is not a safety defect, but the evidence
list under-reports what actually qualified, which matters when a clinician reviews why a rule
fired. The fix is to keep evaluating for evidence collection while still short-circuiting the
verdict.

---

## 4a. Maternity — a broken vertical closed alongside this pack

Not part of the original paediatric brief, but adjacent to it: the newborn record begins at a
delivery, and the delivery record did not exist.

experience-bff had shipped `/v1/labour-monitoring` and `/v1/maternity/**` calling fourteen
pct-service endpoints that were never built. The BFF caught the resulting 404 and returned HTTP 200
with an empty list, so a ward reading a labouring woman's record saw "no observations" when the
truth was that the system could not ask. Cardiotocography was served from an in-process map that
emptied on every pod restart. Nothing a midwife recorded was ever stored.

The BFF carries its own Flyway scripts V33–V36 for these tables, but the BFF has no datasource
outside the CI test profile, so they have never run. Verified across every preview database before
any work began: the tables exist nowhere and hold no rows. There was nothing to migrate, only a
contract to make real.

**One observation table, not two.** The orphan schemas defined `labour_monitoring_entries` and
`maternity_partograph_points` with identical clinical columns. Reproducing both would have created
two systems of record for the same fact, and a partograph drawn from one while the labour record
listed the other would disagree about the same woman. A partograph point is a labour observation;
only an open session distinguishes them, so there is one table with a nullable session reference,
and an observation recorded while a session is open joins it automatically.

`PartographProgressEngine` evaluates the WHO classic partograph rather than merely storing points
for drawing: the alert line rises 1 cm/hour from the recognition of active labour, with the action
line four hours to its right. The alert line's origin is pinned at the first active-phase reading
and never moved, so correcting a later reading cannot silently rewrite whether the labour had
crossed it. The latent phase is excluded rather than judged, because measuring from admission would
condemn a normal latent phase as obstruction. An empty partograph reports `INSUFFICIENT_DATA` and
lists every observation as never recorded — never the reassuring case.

Proven live on the preview estate: 4 cm at 06:00 reads left of the alert line; 5 cm at 08:00
crosses the alert line; 5 cm at 12:00 crosses the action line and returns "requires a decision now"
with its guideline source and content version. A cardiotocography chunk declaring more samples than
it carries records the shortfall rather than interpolating it, because a flat line the transducer
never measured is indistinguishable on screen from one it did.

**Deploying caught a defect the tests did not.** The empty partograph initially reported
`INSUFFICIENT_DATA` beside "0 outstanding observations" — a reassuring line attached to the most
alarming case, because the outstanding check short-circuited on an empty list. Fixed, redeployed
and reproven. This is the same shape as the newborn-episode transaction bug found earlier in this
programme: **check what an empty collection reports, not only what a populated one does.**

**A systemic finding left deliberately open.** The pattern
`catch (Exception) → ResponseEntity.ok(empty)` appears at 126 sites across the BFF controllers.
The two verticals this lane owns (growth and labour monitoring) now return 502 with a message
stating explicitly that the failure must not be read as an absence. The remainder is registered as
separate work rather than swept in silently. The regression risk there is on the client side: any
surface that currently renders an empty list will begin receiving a 502 and needs an error state.

---

## 5. Remaining work

**Wave 3 — experience layer.** Slice 1 (form renderer, segmented control, clinical tri-state) is
done. Remaining: the paediatric workspace with sticky child context and a "what is due today"
panel; the plotted WHO growth chart; the reusable body-map framework; the paediatric BFF
controllers.

**Wave 4 — integrated under-five care.** The IMNCI assess-and-classify tables are **done and
live-proven** — nine tables for the child aged 2 months to 5 years and four for the sick young
infant from birth, age-routed behind one endpoint,
`POST /internal/v1/clinical/paediatric/imnci/classify`, which reports which chart it used.

The young-infant possible-serious-bacterial-infection row reuses the danger-sign rule's predicate
verbatim, and a test asserts across eight presentations that the alert layer and the classification
layer never disagree about the same baby. **Anyone editing one must edit the other**: a critical
alert displayed beside a classification saying serious infection is unlikely would destroy a
clinician's trust in both, and separate layers are only safe when something holds them together.

The **Zimbabwe EPI schedule and immunisation forecast engine** are also done and live-proven
(`POST /internal/v1/clinical/paediatric/immunisation/forecast`). The schedule is governed content
because the EPI programme owns it, and the engine is stateless: the dose history is clinical record
and stays in pct-service, so the caller supplies it and the same forecast runs unchanged against an
offline copy.

Two design points worth carrying forward. **Age eligibility and the minimum interval are separate
gates** — a defaulting child given pentavalent 1 today is old enough for dose 2 but would not be
immunised by it for another four weeks, and forecasting it as due would waste a dose and record a
child as protected who is not. And **dependence between doses is expressed by the minimum interval,
not by dose numbering**: treating "dose 1 follows dose 0" as a dependency stranded the entire polio
primary series behind a birth dose that can no longer be given, which would have affected every
baby not born in a facility.

The **observation registry (`pct_observations`) and the BUTANO write path** are built. This closed
a third broken vertical: `FormExtractionService` already extracted observation values, recorded
them as `PENDING` and emitted an event, but no observations table existed and nothing consumed the
event — its own comment said "No direct PCT→BUTANO Observation write method exists". The registry's
defining feature is `data_absent_reason`: a row may carry no value and a reason instead, which is
the difference between "the temperature is normal" and "nobody took the temperature". A database
CHECK enforces value-or-reason and the service rejects the ambiguous case with a 400.

The SHR boundary is enforced at the consumer rather than trusted of the producer. The mapping
copies an explicit list of coded fields, so a producer that later began leaking a name, date of
birth or national identifier could still not write one into the shared record; a test feeds a
deliberately contaminated payload through and asserts none of it survives.

**Honest status of the last hop.** PCT is deployed and live-proven. The BUTANO consumer is built
and unit-proven (11 boundary tests) but the Kafka hop has **not** been demonstrated end to end,
for an environment reason rather than a code one: `SPRING_KAFKA_LISTENER_AUTO_STARTUP=false` is the
estate default and `butano-service` is not in the opt-in list, so no BUTANO consumer has ever run
on preview — not identity, consent, imaging or observations. The shared record currently holds one
Patient resource and nothing else, which is the visible consequence. Enabling BUTANO's listeners is
a one-line opt-in following the pattern already documented in `values-full-preview.yaml`, but it
starts identity, consent and imaging consumption across the estate, so it is a deployment decision
rather than something this lane should switch on unilaterally.

Proving the vertical also caught a real integration bug: `OutboxPublisher.routeTopic` had no case
for `pct.observation.recorded`, so it fell through to the `pct.events` catch-all while BUTANO
subscribed to the specific topic. The event published successfully to a topic nobody was listening
on, with every individual step reporting success. **Adding a cross-service event means adding a
route case — the route is the contract.**

Remaining: growth intelligence in the knowledge platform; enabling the BUTANO listeners and
proving the hop; deepened form content; and migrating the locally declared
antigen codes onto a governed Zibo value set once zibo-service holds a vaccine terminology (it
holds none today, which is why the schedule declares its own code system with a documented
migration point).

The classification engine introduced one concept worth knowing about before extending the content.
`optionalCriterion` marks a branch whose unknown value is treated as not-met rather than blocking,
and it exists because some criteria depend on equipment a facility may not have — weight-for-height
needs a height board, and fever needs a thermometer. Without it, every nutrition classification and
every fever screen at a rural clinic would read as incomplete, and a safety flag that fires on
every child is worse than no flag: it teaches people to dismiss the one that matters. The waiver
never hides the gap — waived criteria are reported on the result. **It must only ever be applied to
a criterion that is an additional route to a finding, never the sole way to exclude one.**

**Wave 5+.** Paediatric surgery, nutrition/IMAM episodes end to end, the adolescent confidential
pathway, the remaining body-map instruments, quality and surveillance analytics.

---

## 6. Risks and dependencies

**Clinical content requiring ratification.** Every threshold, dose, cut-off and danger-sign
definition shipped so far is an engineering seed. They are correctly structured, tested and
traceable, but they are not national protocol. Nothing should drive care until MoHCC and a
paediatric specialist have signed off: the early-warning bands, the vital-alert bands, the nine
danger-sign rules, the five dosing rules, the MUAC and malnutrition cut-offs.

**Data gaps that cannot be closed by engineering.** The WHO weight-for-length/height tables are not
in the embedded dataset, so wasting — the defining indicator — cannot be scored; the code says so
on every nutrition assessment rather than substituting weight-for-age, which measures something
else. The WHO 5–19 year reference is also absent, so children over five are not scored at all.
Both are data-acquisition tasks.

**Still broken, now owned.** The maternity partograph and CTG surfaces follow exactly the same
pattern as growth and immunisation did: `experience-bff` controllers and a well-built UI proxying
to `pct-service` `/v1/maternity/**` endpoints that do not exist, with orphaned tables `V34`–`V36`.
The BFF swallow is explicit — `catch (Exception e) { return ok(...) }` returns HTTP 200 with empty
defaults, so no layer above can tell it failed. Ownership of the fix has been agreed with the
session that holds the BFF lane; it is queued, not abandoned.

**Do not drop the orphaned tables without counting rows first.** "Orphaned" here means unread, not
empty. The swallow is on the PCT call, so anything the BFF persisted locally before the vertical
broke could still be sitting in `V33`–`V36`. Any rows are real clinical measurements needing
migration into `pct_growth_measurements`, not deletion.

**A general lesson worth carrying beyond this pack.** A downstream 404 returned as HTTP 200 with
empty defaults is indistinguishable from "no data exists" at every layer above it, which is why
this class of defect survived so long across three separate verticals. New BFF proxies should fail
loudly — 502 upstream-unavailable, as `EncounterFormsController` does — rather than degrade to a
cheerful empty list.

**Decisions taken that a product owner may wish to revisit.** Emergency danger-sign alerts are
non-overridable. A newborn rooming in with their mother is not separately admitted. Civil birth
notification stays a human act and is never auto-submitted from a clinical event. Month-end
birthdays complete a month late rather than early, so a minimum-age gate never opens early.

---

## 7. Where the code is

| Area | Path |
|---|---|
| Shared paediatric domain | `libs/paediatric-domain/` |
| Growth, immunisation, newborn records | `services/pct-service/.../core/clinical/`, migrations V053–V055 |
| Early warning and neonatal admission | `services/inpatient-service/.../core/`, migration V066, `resources/clinical/ews-thresholds.json` |
| Rules framework and danger signs | `services/clinical-knowledge-platform-service/.../rules/tabular/`, `.../danger/`, migration V006 |
| Dosing | `services/clinical-knowledge-platform-service/.../prescribing/` |
| Clinical content | `services/*/src/main/resources/clinical/*.json` |
