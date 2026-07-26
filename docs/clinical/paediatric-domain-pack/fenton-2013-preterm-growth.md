# Fenton 2013 preterm growth — implementation report

Status: **built, test-green, not yet deployed.** Content ratification: `ENGINEERING_SEED`
pending MoHCC, matching every other content pack in this programme.

Lease and migration reservation: [`docs/registry/iatg-paediatric-leases.md`](../../registry/iatg-paediatric-leases.md).

## Why this exists

The mobile provider app shipped a tile called "Growth Chart (Fenton)" that opened a
free-text notes box. The specialty-tools honesty sweep made it unreachable, correctly, and
the RMNP lane declined to rebuild it, also correctly: preterm growth belongs to the
paediatric lane's single growth system of record.

Building it properly found something worse than the fake tile.

**A preterm infant was being scored against the WHO term standard, and the result looked
entirely plausible.** `CorrectedAge` clamps a negative corrected age to zero, so a baby born
at 28 weeks and weighed at two weeks old was read against the WHO curve for a *newborn*:
1.05 kg against a 3.35 kg median is a z-score below −6. `NutritionClassifier` turns a
weight-for-age below −3 into `SEVERE_ACUTE_MALNUTRITION`, which means a well-grown premature
baby was referred into the IMAM pathway on arithmetic alone. Nothing in the record showed
that anything had gone wrong: the row carried a z-score, a standard identifier and an engine
version, all of them internally consistent and all of them answering the wrong question.

This is the shape [[fallbacks-that-lie]] describes — a failure mode indistinguishable from
success — and it had passed unit tests, deployment and live proof, because every one of
those checks used a term baby.

## What changed

### The engine chooses the standard; the caller cannot

`GrowthEngine.assess` now selects between two references from gestational age:

| Child | Standard | Axis |
|---|---|---|
| Born at or after 37 weeks | WHO 2006 | age in days |
| Born before 37 weeks, up to 49 completed postmenstrual weeks | Fenton 2013 | completed postmenstrual weeks |
| Born before 37 weeks, from 50 postmenstrual weeks | WHO 2006 | corrected age in days |

There is deliberately no API for requesting a standard. "Score this preterm baby against the
term chart" is not a request the system should be able to express, because its answer is a
severe-malnutrition finding on a healthy infant.

The handover at 50 weeks is where the Fenton chart itself ends, and it leaves no unscored
gap: 50 weeks postmenstrual is 10 weeks corrected, well inside the WHO tables.

### Where the preterm reference is missing, nothing is substituted

If the Fenton tables are absent from a deployment, or have no published point at the week in
question, the measurement is stored **unscored** with a reason that says the term standard
was withheld deliberately. `GrowthEngineTest` asserts that invariant unconditionally — it
holds whether or not the data is loaded, so a deployment that loses the content pack fails
safe rather than silently reverting to the old behaviour.

This follows the pattern the pack already uses for WHO weight-for-length and the WHO 5–19
year reference: a named absence, never a substitution.

### Gestational age is resolved from the birth record

`gestational_age_weeks` previously reached PCT only if the caller put it in the request body,
and no caller did. Left there, the whole feature would have shipped inert — selectable only
by the unit that recorded the birth, with every later weighing at every clinic falling back
to the term standard.

`GrowthService` now reads it from the child's own newborn birth record when the request does
not carry it, and records where it came from (`SUPPLIED` / `NEWBORN_BIRTH_RECORD` /
`NOT_RECORDED`). A child scored as term because nobody wrote down a gestation is a different
clinical statement from a child scored as term because they were born at 39 weeks, and the
record now distinguishes them.

## The data

Transcribed verbatim from the authors' published bulk calculator
(`Bulk calculator wt hc l - Fenton 2013 growth chart - SD23 - v6`, ucalgary.ca/fenton):
completed weeks 22–49, both sexes, weight (grams), length and head circumference (cm).

Three checks were run before trusting it:

1. **Independent re-extraction.** The tables were pulled out of the source spreadsheet a
   second time by a separate script and diffed row by row: 164/164 rows, zero value
   differences.
2. **Cross-corroboration.** The values agree with the authors' separate exact-age calculator
   (v7) to within 1.4e-4 relative.
3. **End-to-end fixtures.** `FentonPretermStandardTest` asserts the z-scores from the eight
   worked examples shipped inside the source spreadsheet. These are the publishers' answers,
   not ours, so a change to the arithmetic, the tables or the gram/kilogram handling fails
   against them. Two of the eight sit beyond three standard deviations and exercise the WHO
   SD23 tail correction, which matters more here than anywhere else because a great many
   preterm infants genuinely live in the tails.

**A trap worth recording: the size-at-birth calculator on the same site is a different fit.**
Its LMS values disagree with these at every overlapping week (its length and head
circumference tables use L≈2; the growth chart uses L=1). Birth classification (SGA/AGA/LGA)
uses that table; postnatal growth monitoring uses this one. Merging them would be silent and
wrong.

### Known absences, reported rather than filled

- **No completed-week-50 row exists.** The weekly points sit at the midpoint of each week, so
  week 50 would fall past the chart's 50w0d endpoint. This is why the handover to WHO is at
  50 weeks and not later.
- **No 22-week length or head circumference.** The source states outright that the data does
  not exist. The engine reports these as named per-indicator gaps.
- **No BMI-for-age.** Fenton publishes no BMI reference, so BMI is reported as unavailable
  under it rather than computed from a chart that does not define it.

### Licence — an obligation that reaches the screen

The data is published under **CC BY-NC-ND 4.0**. Two conditions travel with it: any chart
drawn from it must display the label "Fenton 2013 Preterm Growth Chart" conspicuously, and
the development paper must be cited. Both are carried in the content pack's own metadata and
surfaced through the API to the UI, so a future surface cannot render the chart without the
attribution arriving alongside the curves.

**Two determinations are MoHCC's, not engineering's**, and are recorded rather than assumed:
whether a national public health service deployment satisfies the *NonCommercial* term, and
whether embedding the published weekly values verbatim for lookup is use rather than a
*derivative*. This lane took the conservative reading — values stored exactly as published,
no smoothing, no re-fitting, no interpolation between the published weekly points. Note that
the publishers direct data requests to tfenton@ucalgary.ca; a file being publicly served is
not by itself a licence to embed it.

## Two defects found on the way through, both in the path this feature needs

### The BFF scored every child twice, and disagreed with itself about preterm ones

`experience-bff` carried its own implementation of the LMS arithmetic and its own 669 KB copy
of the WHO tables, and re-scored each measurement on the way past. Its copy had **no
corrected-age handling**. So for any preterm infant, PCT persisted one z-score and the BFF
echoed a different one in the same response.

The BFF now depends on the one engine and recomputes nothing. Reading a stored score is not
the same as producing a second opinion about it.

### The growth read contract had never been reconciled, so the shell never showed a row

PCT returns flat snake_case rows. `useGrowth` reads `attributes.derived.*`. Nothing mapped
between them, so `mapMeasurement` threw on every row and the page fell into its error state —
which reads *"Growth measurements could not be loaded. This is not a record that the child has
no measurements."* An honest message concealing a client bug is still a screen that never
worked.

The BFF now shapes the resource the client was written against, built from the stamps PCT
already holds.

## What is surfaced

- `GET /internal/v1/growth` — measurements as resources, each carrying the standard it was
  read on, its label, its attribution, postmenstrual age, and any per-indicator scoring gaps.
- `GET /internal/v1/growth/reference-curves?patient_id=&indicator=` — the −3/−2/0/+2/+3
  curves for the standard **this child** is eligible for, on that standard's own axis, with
  the attribution its licence requires. Where no standard applies: no curves and a reason.
- The web growth page now **plots**. The chart component existed, was tested, and was mounted
  nowhere because nothing served the curves it requires — while the paediatric workspace
  advertised it as "plotted against the WHO standard, with faltering detection". The
  horizontal axis was age-in-days throughout and is now neutral, so a preterm chart reads
  "30w PMA" and never renders "30 weeks postmenstrual" and "30 days old" alike.
- The mobile tile stays unreachable as the sweep left it; its registry entry now names what
  exists and what is outstanding instead of "wave TBC".

## Corrected age now targets full term

Closes open question 1. `CorrectedAge` held one constant, `TERM_GESTATION_WEEKS = 37`, and used
it to answer two different questions: *is this baby preterm* and *how far back do we correct*.
The first is right at 37 weeks. The second is wrong there — the convention corrects to 40 weeks,
full term. So the correction was three weeks short for every preterm child, whatever their
gestation, and their growth was read about three weeks "old": compared against an older child's
median, which reads as slightly worse than they are.

There are now two constants, `PRETERM_THRESHOLD_WEEKS = 37` and
`TERM_CORRECTION_TARGET_WEEKS = 40`, and a test asserts they are not equal — collapsing them
again is the defect returning, not a simplification.

**Size and direction of the error, measured rather than reasoned about.** A girl born at 30
weeks, weighed at 50 weeks postmenstrual age — the first week on the WHO tables, so exactly where
the handover puts her:

| weight | z at 70 days corrected (correct) | z at 91 days (as it read before) |
|--------|----------------------------------|----------------------------------|
| 3.9 kg | −2.44 moderate                   | −3.17 **severe**                 |
| 4.2 kg | −1.88 normal                     | −2.59 moderate                   |

The error is about 0.7 SD, always in the direction of reading a preterm child as *worse* than
they are, and it **crosses the classification thresholds** that drive nutrition referral. It is
the conservative direction — over-referral, not a missed case — but a well-growing preterm baby
being told they are severely underweight is the same category error the Fenton work itself
closed, one layer further along.

It also restores the invariant the handover was designed around and which
`FENTON_MAX_POSTMENSTRUAL_WEEKS` already claimed in prose: 50 weeks postmenstrual is 10 weeks
corrected. It was 13. The chart now hands over without stepping the child forward in age.

**Already-stamped scores are left as they are. No backfill.** Reasons, in order of weight:

1. A stored z-score is a record of what a clinician was shown when they made a decision.
   Rewriting it in place destroys the evidence of why a referral was or was not made, and
   leaves an audit trail describing a screen nobody ever saw.
2. Every row carries `growth_engine_version`, so it stays interpretable as the thing it
   actually was. `1.0.0` = preterm scored on the term chart; `2.0.0` = preterm on Fenton,
   corrected to 37 weeks; `3.0.0` = corrected to full term. Each bump is major because a score
   is not comparable across it for the same child and the same measurement.
3. There is nothing material to restate. At the time of writing the estate holds **one** growth
   measurement in `pct.pct_growth_measurements`, stamped `1.0.0`, for a term child with no
   correction applied. This was checked, not assumed.

Point 3 is the reason this is cheap today and will not be later. Anyone reopening the question
once real preterm volume exists should reopen it as "how do we present a superseded score",
not as "can we rewrite it" — the answer to the second is no, for reason 1.

## Open questions this work raised and did not close

The original question 1 — corrected age targeting 37 weeks — is closed above.

1. **WHO weight-for-age does not apply the SD23 tail correction**, though the WHO technical
   report specifies it. Fenton does, because its own calculator does. Aligning WHO would move
   stored scores beyond ±3 SD, which is exactly where the severe-malnutrition threshold sits.
2. **Nothing consumes `growth_standard`.** CKP's growth interpreter reads the stamped z-score
   without reading which standard produced it, so a history spanning the Fenton→WHO handover
   is interpreted as though it were one continuous series. Note this now also spans an engine
   version change: a series can cross both a standard boundary and a corrected-age convention.
3. **Neonatal "Surfactant Protocol"** is registered as in-development and unreachable, but it
   sat one tile away from the Fenton fake and is worth the sweep confirming.

## Test coverage

| Suite | Tests | Of which new |
|---|---|---|
| `libs/paediatric-domain` | 92 | 11 Fenton fixtures and invariants; 3 corrected-age (threshold ≠ target, late preterm, handover arithmetic) |
| `pct-service` | 451 | 3 (preterm selection, birth-record resolution, unrecorded gestation) |
| `experience-bff` | 1235 | 7 (resource shape, attribution, reference curves, honest absence) |
| `ui/one-ui-shell` | 2501 | 4 (chart mounted, preterm axis, attribution, stated absence) |
| `apps/mobile/provider-app` | 266 | — (registry entry only) |

Nothing here has been run against a deployed estate.
