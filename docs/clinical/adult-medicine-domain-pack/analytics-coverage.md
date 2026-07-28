# Adult Medicine — §21 analytics coverage register

**Source: [`brief.md`](brief.md) §21, verbatim.** §21 names **21 indicators**. This file lists every
one of them and says, for each, whether it is implemented, partially implemented, or not computable
from data that exists today — and for the last two, exactly which data is missing and which service
would own it.

**The register exists because a partial analytics surface is indistinguishable from a complete one.**
Six indicators shipped out of twenty-one looks finished to everyone who did not count. Nothing here
is a plan; it is a statement of what the estate can and cannot answer as of 2026-07-28.

## The count

| State | Indicators |
|---|---|
| **IMPLEMENTED** — the indicator §21 names, computed from real data | **6** |
| **PARTIAL** — a named, narrower thing is computed; the rest is stated as missing | **6** |
| **NOT COMPUTABLE** — the data does not exist in a form that can answer the question | **9** |

PARTIAL is never counted as delivered. Each partial entry below states in one sentence what it does
*not* measure, and that sentence is also on the API response, so a consumer cannot receive the number
without receiving its limit.

## Why these run inside PCT and not in reporting-service

reporting-service holds a **separate database** with no view of `pct.*` — no foreign data wrapper, no
dblink, no ETL. A seeded SQL report definition naming `pct_problems` or `pct_programme_enrolments`
would be registered, would show as **ACTIVE** on the governed report list, and would never execute.
Five governed report definitions in this estate are already in exactly that state: they name another
service's schema from reporting's own database, and they have never returned a row.

So every indicator below is a query **PCT itself executes** against its own tables, reached over PCT's
own API. An indicator that cannot run is worse than a missing one, because it is counted as done.

## Three rules every implemented indicator obeys

1. **Every count states its unit.** Rows, episodes, links, reconciliations, regimen changes and people
   are different units and the response says which one it is counting. This is not pedantry: someone
   in this estate totalled programme cohort rows into a patient count, which is why
   `cohortCounts` has carried a sentence forbidding it ever since.
2. **A zero denominator produces no rate.** `IndicatorRate` returns `numerator`, `denominator` and a
   `rate` of `null` with a written reason when the denominator is empty. A clinic that enrolled nobody
   has not achieved 0% control, and one patient assessed as controlled is not 100% national success.
3. **An absent value is its own number.** `control_status` NULL means *never assessed* — not
   controlled and not uncontrolled — and is excluded from the control rate and reported separately.
   The same rule governs problems with no review date, confirmed diagnoses with no onset date, and
   functional assessments that could not be scored.

---

## IMPLEMENTED (6)

### 1. Disease detection
`GET /v1/medicine-analytics/detection?category=&from=&to=`
Problems first recorded in the window, grouped by code **and diagnostic certainty**, with a separate
distinct-people count. Certainty is grouped rather than filtered: summing SUSPECTED into CONFIRMED
counts a suspicion as a diagnosis. The response states that detection means *first written down
here*, so a rise during a rollout reads as the record filling up rather than incidence rising.
Source: `pct_problems`.

### 2. Control and target attainment
`GET /v1/programme-enrolments/control-attainment?programme=&facility_id=`
Control-status distribution across open enrolments per programme, plus an `assessment_coverage` rate.
NULL control status is reported as `never_assessed` and excluded from the control rate's denominator;
`TARGET_NOT_SET` is likewise excluded, because a patient with no agreed target cannot be attaining
one. Source: `pct_programme_enrolments`.

### 3. Complications
`GET /v1/medicine-analytics/complications?from=&to=`
`COMPLICATION_OF` problem links, grouped by underlying condition and by the complication itself.
Links pointing at a non-problem target carry no code to group by and are counted separately rather
than dropped. The response states that this measures **documentation, not incidence** — an unlinked
diabetic foot ulcer is invisible to it, so a low number may mean good care or poor linking and this
indicator cannot tell you which. Source: `pct_problem_links` joined to `pct_problems`.

### 4. Polypharmacy
`GET /v1/medicine-analytics/polypharmacy?from=&to=&threshold=`
Medicine-count distribution across **completed** reconciliations, with a configurable threshold
(default 5). Abandoned and in-progress reconciliations are excluded — their item list stops where the
clinician stopped, and counting it makes an interrupted review look like a patient on fewer drugs.
Reconciliations with **zero items** are added back into the denominator, since a group-by over items
cannot see them and dropping them inflates the rate. Counts reconciliations, not people; the headcount
is returned separately. Source: `pct_medication_reconciliations` + `..._items`.

### 5. Diagnostic delays
`GET /v1/medicine-analytics/diagnostic-interval?from=&to=`
Days from reported onset to the recording of a confirmed diagnosis, as median, p90 and max — not a
mean, because the distribution is heavily skewed and one childhood-onset record destroys a mean.
Confirmed diagnoses with no onset date are counted and returned as `excluded_no_onset_date`; records
whose onset falls *after* the diagnosis was recorded are a data error, not a negative delay, and are
counted separately rather than averaged in. Source: `pct_problems`.

### 6. Palliative access
`GET /v1/medicine-analytics/episodes?facility_id=&from=&to=`
People with a palliative episode over people with any medical episode in the window. Measured in
**people, not episodes** — someone with three palliative episodes has been reached once, and counting
episodes would make a service that re-opens them look like a service reaching more patients. The
response states that this is recorded access: palliative care given without an episode (a community
visit, a hospice referral, symptom control inside another episode) does not appear, and the unmet-need
denominator does not exist anywhere in the estate. Source: `pct_medical_episodes`.

---

## PARTIAL (6)

Each of these ships a real, named, narrower measurement. None of them is the §21 indicator.

### 7. Mortality
**Ships:** deaths as a programme exit reason (`died_among_exits` on
`GET /v1/programme-enrolments/programme-outcomes`) and as a medical-episode end reason
(`died_among_ended` on `GET /v1/medicine-analytics/episodes`).
**Does not measure:** a mortality rate. There is no population denominator; a death of someone never
enrolled, or after their enrolment or episode closed, is invisible. Case fatality by condition needs
the death to be linked to the problem that caused it.
**Missing data / owner:** **coded** cause of death, linked to the problem list. `pct_death_cases`
holds the WHO certification draft (`cod_immediate`, `cod_antecedent`, `cod_underlying`,
`cod_contributory`) — but as free text, with no code system and no reference to `pct_problems`, so it
cannot be grouped or joined to a diagnosis. `pct_verbal_autopsy` is likewise unlinked. Civil death
registration is not held by PCT at all (UBOMI).

### 8. Follow-up completion
**Ships:** the *due* half — `GET /v1/medicine-analytics/review-state` returns open problems as
overdue / due today / scheduled / **no review date**, the last kept out of the compliant group and out
of the overdue denominator.
**Does not measure:** whether the patient attended. That is the word "completion" in §21.
**Missing data / owner:** appointment and attendance records — `booking-service` owns booking and
appointment canonical records; PCT holds no attendance table. Joining the two would also need the
appointment to reference the problem or episode it exists to follow up.

### 9. Functional outcomes
**Ships:** `GET /v1/medicine-analytics/functional-assessments` — assessments by instrument, split on
scored versus attempted-but-unscored, plus the number of people with a repeat measure on the same
instrument (the denominator any change score would need).
**Does not measure:** change. No score delta is computed.
**Missing data / owner:** a closed instrument vocabulary. `assessment_type` is free text and
`max_score` is optional, so a 60 on a Barthel index and a 60 on a cognitive screen are not comparable
and cannot be normalised. Owner: this pack (a vocabulary and a migration), not another service.

### 10. Stockouts
**Ships:** `GET /v1/programme-enrolments/regimen-change-reasons` — regimen changes by reason, with
`STOCK_OUT` broken out. This counts the times a shortage was severe enough to change a patient's
treatment, which is a real and serious signal.
**Does not measure:** stock availability. A shortage that was absorbed, substituted at the counter, or
that simply sent the patient home with nothing leaves no trace in the clinical record, and there is no
denominator of facility-days at risk.
**Missing data / owner:** stock-on-hand and stockout events — `pharmacy-service` and the inventory
eLMIS adapter.

### 11. Programme indicators from the HIV and TB DAKs
**Ships:** the building blocks on `GET /v1/programme-enrolments/programme-outcomes` — currently open
enrolments by status, new enrolments in the window, and exits by every reason with favourable-outcome,
died and lost-to-follow-up rates.
**Does not measure:** the DAK indicators as defined. Two gaps, both stated on the response:
- **It is an exit-period indicator, not a start cohort.** A DAK treatment-success rate follows the
  people who *started* treatment in a period through to outcome; this counts those who *ended* in the
  period, whenever they started. The response says so explicitly rather than borrowing the name.
- **No age/sex disaggregation** (see Equity) and **no viral-load suppression**, which is the core HIV
  DAK indicator.
**Missing data / owner:** laboratory results for viral load — OROS diagnostics / the lab lane;
demographics — VITO.

### 12. NCD indicators based on WHO PEN and national requirements
**Ships:** register size and the control/target-attainment distribution per chronic register
(hypertension, diabetes, CKD, chronic respiratory) — items 2 and the register read.
**Does not measure:** the PEN protocol indicators proper — blood-pressure and blood-glucose measurement
coverage, and total cardiovascular-risk stratification.
**Missing data / owner:** an observation-to-programme link. `pct_observations` exists but nothing ties
a BP reading to a hypertension enrolment, so "what share of the register had a BP recorded this
quarter" cannot be asked. CVD risk scoring would be `rules-service` / the CDS lane over that link.

---

## NOT COMPUTABLE (9)

### 13. Hospitalisation
`pct_admissions` exists, but it carries no link to `pct_problems`, `pct_medical_episodes` or
`pct_programme_enrolments` — only a free-text `admitting_diagnosis`. Admissions therefore cannot be
attributed to a medical condition, which is the whole of this indicator. **Owner:**
`inpatient-service` holds the canonical admission record; the missing artefact is a coded
admission-diagnosis link, which needs a migration and is outside read-only analytics work.

### 14. Readmission
Same blocker as hospitalisation, plus one of its own. An **all-cause** 30-day interval is
arithmetically derivable from `pct_admissions.admitted_at` / `discharged_at` — but a *medicine*
readmission rate needs the diagnosis link that does not exist, and `pct_admissions` is the
journey-side handshake (`inpatient_admission_ref`) rather than the authoritative record. Computing it
here would create a second system of record for an inpatient statistic. **Owner:**
`inpatient-service`.

### 15. Missed appointments
PCT holds no appointment or attendance table. `pct_referrals` carries an `appointment_id` and a
`scheduled_at` that point at an external scheduler, and nothing records whether the person arrived.
**Owner:** `booking-service` (booking and appointment canonical records).

### 16. Medicine adherence
Adherence needs dispensing and refill history — collection dates, quantities, expected coverage
periods. PCT holds a medication *reconciliation*, which is a point-in-time comparison of lists, not a
supply record. **Owner:** `pharmacy-service` (dispensing), with `oros-service` for the prescription.

### 17. Antimicrobial use
Reconciliation items carry `code` and `code_system`, but nothing classifies a code as an antimicrobial
or places it on the WHO AWaRe list, so antimicrobials cannot be separated from other medicines. The
denominator (prescribing or dispensing volume, or patient-days) is also not held here. **Owner:**
`product-registry-service` / terminology for the classification; `pharmacy-service` for the volume.

### 18. Referral delays
`pct_referrals` and `pct_referral_transitions` hold `submitted_at`, `routed_at`, `scheduled_at` and
`completed_at`, so a **generic** referral turnaround is computable from PCT data. It is not an
adult-medicine indicator: no referral is linked to a problem or a medical episode, so referrals cannot
be scoped to medicine, to a condition, or to a specialty pathway. **Owner:** the referral lane; the
missing artefact is a referral-to-problem link (a migration).

### 19. Procedure completion
Procedure requests are `oros-service`, execution is `inpatient-service` (`procedure_episode` and its
satellites, the execution system of record for the whole estate), and the catalogue and readiness
engine are `procedures-service`. PCT holds `pct_past_procedures`, which is a *history* a clinician
typed — not a request that can be followed to completion. Nothing in PCT can answer "was the requested
procedure done". **Owner:** `procedures-service` (the execution index joining an OROS request to the
executing service).

### 20. Patient-reported outcomes
No PROM instrument exists anywhere in the estate. `pct_form_response` and the structured-forms engine
could carry one, but a PROM needs a defined, versioned instrument with scoring — none is defined, so
there is nothing to aggregate. **Owner:** the Encounter Structured Forms engine plus a governed
instrument definition; this is content, not code.

### 21. Equity
PCT holds **CPID only and no PII by design**, so age, sex, district, disability and any wealth or
insurance proxy are absent. Every indicator above is therefore unstratifiable, which also blocks the
age/sex disaggregation the HIV and TB DAKs require. **Owner:** `vito-service` holds the demographics.
The join cannot be done inside PCT without importing PII into a service that is architecturally
forbidden to hold it; it belongs in a governed de-identified demographic dimension in the data
warehouse / NDR, or in an aggregate-only join at that layer.

---

## Where the code is

| Layer | File |
|---|---|
| Enrolment aggregates | `services/pct-service/.../persistence/repository/ProgrammeEnrolmentRepository.java` |
| Regimen change reasons | `services/pct-service/.../persistence/repository/TreatmentRegimenRepository.java` |
| Problem aggregates | `services/pct-service/.../persistence/repository/ProblemRepository.java` |
| Complication links | `services/pct-service/.../persistence/repository/ProblemLinkRepository.java` |
| Episode aggregates | `services/pct-service/.../persistence/repository/MedicalEpisodeRepository.java` |
| Polypharmacy | `services/pct-service/.../persistence/repository/MedicationReconciliation{,Item}Repository.java` |
| Functional assessments | `services/pct-service/.../persistence/repository/FunctionalAssessmentRepository.java` |
| Indicator services | `core/clinical/MedicineAnalyticsService.java`, `core/clinical/ProgrammeEnrolmentService.java` |
| The zero-denominator rule | `core/clinical/IndicatorRate.java` |
| API | `api/controller/MedicineAnalyticsController.java`, `api/controller/ProgrammeEnrolmentController.java` |
| Tests | `src/test/java/.../core/clinical/MedicineAnalyticsTest.java` (32 tests) |

The tests are named `*Test`, not `*IT`, deliberately: surefire in this repo skips `*IT` classes and
failsafe is not wired for them, so an integration-shaped test with that suffix never runs. Five of the
32 turn red if `IndicatorRate` is changed to render an empty denominator as 0%.
