# Paediatric Clinical Domain Pack — Implementation Report

**Status as of 2026-07-26.** Waves 1 and 2 are implemented, tested and pushed. Waves 3–5 are
designed and outstanding. Everything clinical shipped so far is `ENGINEERING_SEED` and requires
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
| **2. Sick newborn (10-day-old, poor feeding, hypothermia)** | **Backend complete.** The danger-sign engine raises possible serious bacterial infection with referral required and non-overridable; neonatal dose restrictions apply; the birth record and neonatal admission exist. No UI. |
| **1. Sick 14-month-old (fever, cough, poor feeding)** | **Partial.** Danger signs, age-banded vitals, growth with z-scores and faltering, and safe dose calculation all work. Missing: IMNCI classification (assess-and-classify tables), immunisation forecast, Dura stock check, Khuluma caregiver instructions, UI. |
| **3. Growth monitoring visit** | **Partial.** Measurement capture, WHO scoring, faltering detection and IMAM eligibility signalling work. Missing: the "what is due today" composition, the plotted chart, IMAM referral creation. |
| **4. Paediatric surgical emergency** | **Not started.** Existing theatre and inpatient spines are reusable; the surgical-abdomen pathway and paediatric surgical clerking are not built. |
| **5. Confidential adolescent visit** | **Not started.** The TSHEPO `SPECIALLY_PROTECTED` sensitivity class exists but has never been assigned; caregiver-context gating is designed, not built. |

Nothing here has been run against a deployed estate. These are unit- and integration-tested
behaviours, not live-proven journeys.

---

## 5. Remaining work

**Wave 3 — experience layer.** Form renderer upgrades (four unimplemented field kinds, a real
segmented control, and the clinical tri-state Yes/No/Unknown/Not-assessed that IMNCI needs and
that exists nowhere in the codebase); the paediatric workspace with sticky child context and a
"what is due today" panel; the plotted WHO growth chart; the reusable body-map framework.

**Wave 4 — integrated under-five care.** IMNCI and PSBI classification tables; growth intelligence
in the knowledge platform; the ZW EPI schedule as a governed Zibo artifact and the forecast engine;
`pct_observations` and the BUTANO Observation/Immunization write path; deepened form content.

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

**Still broken, deliberately untouched.** The maternity partograph and CTG surfaces follow exactly
the same broken pattern as growth and immunisation did: `experience-bff` controllers and a
well-built UI proxying to `pct-service` `/v1/maternity/**` endpoints that do not exist, with
orphaned tables `V34`–`V36`. The maternity UI renders and persists nothing. This was out of scope
for the paediatric pack but is the same defect class and should be scheduled.

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
