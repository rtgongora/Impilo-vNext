# medicine-domain

Pure Java 21 adult-medicine primitives shared by the clinical plane. No Spring, no I/O, no Jackson —
every reference value is a compiled constant, so the same arithmetic runs inside a service, a batch
job or an offline package, and builds without a network or a container.

```
mvn -o -f libs/medicine-domain/pom.xml test
```

## The entry criterion: arithmetic, never policy

A function belongs in this library when it computes a number, or applies a classification whose
boundaries are fixed in a published source and are the same in every country. A function does **not**
belong here when it decides what to do about that number.

| Belongs here | Belongs in governed CKP content |
|---|---|
| eGFR from creatinine, age and sex | The eGFR at which a patient is referred |
| The KDIGO category an eGFR falls into | What to do at each KDIGO category |
| The 10-year CVD risk band | The band at which a statin is started |
| The NYHA class implied by three symptom answers | What each class entitles a patient to |
| The total daily oral morphine equivalent | The reduction applied when switching opioid |
| Composite burden from counts and supplied cut-points | Where the cut-points sit |

Thresholds a caller supplies are fine — `TreatmentBurdenScore` and
`CognitiveScreenScore.atOrBelowCutOff` both take them as parameters, which puts the decision in
governed content where a ministry can change it without a code release.

## Known frontend drift (follow-up)

`ui/one-ui-shell/src/components/clinical/MedscapeTools.tsx` implements CKD-EPI 2021 eGFR in
TypeScript (around the `gfr` memo), duplicating `EgfrCalculator` in this library. Do not "fix"
that by copying more arithmetic into the browser — route the tool through a BFF-backed calculator
endpoint that calls this library, so web and service cannot disagree on a creatinine.

## Two safety properties every calculator holds

**Cannot-compute is a first-class answer.** Every calculator returns a result type carrying either a
value or a `RefusalReason` with clinician-readable text. There is no magic sentinel, no null standing
in for an answer, no silent default. A missing creatinine that becomes "eGFR 90" reads as a healthy
kidney and a clinician acts on it by not acting; "serum creatinine was not recorded" reads as a task.

**Out of range is refused, not extrapolated.** Every equation here is a curve fitted inside a range,
and none of them degrade gracefully outside it. A creatinine of 106 typed into a mg/dL field, a
diastolic reading in the systolic box, a frailty score of 12 on a nine-point scale, a cognitive total
above the instrument's maximum — all refused with a named reason rather than clamped or scored.

## What is in it

| Package | Class | What it computes |
|---|---|---|
| `renal` | `EgfrCalculator` | CKD-EPI 2021 creatinine eGFR, race-free, from µmol/L or mg/dL |
| `renal` | `KdigoStage` | KDIGO GFR categories G1, G2, G3a, G3b, G4, G5 |
| `cardio` | `CvdRiskCalculator` | 10-year CVD risk band, laboratory and non-laboratory variants |
| `cardio` | `CvdRiskBand` | The five WHO 2019 risk bands |
| `cardio` | `NyhaClass` | NYHA functional classification I–IV, with a classifier from symptoms |
| `frailty` | `ClinicalFrailtyScale` | Rockwood CFS levels 1–9 |
| `cognition` | `CognitiveScreenScore` | Proportion-of-maximum banding for any cognitive screen |
| `pharm` | `OpioidEquianalgesic` | Oral morphine equivalent, single drug and whole regimen |
| `burden` | `TreatmentBurdenScore` | Composite treatment burden from counts and supplied cut-points |
| `common` | `RefusalReason`, `Sex` | The shared cannot-compute vocabulary and the equation sex variable |

## Nothing is vendored, and everything is a seed

**No primary source is vendored under `docs/reference` for anything in this library.** Every
coefficient, boundary and factor was transcribed from the published literature named in the javadoc,
and every one of them is an engineering seed pending MoHCC ratification. The `CONTENT_VERSION`
constant on each class exists so that a stored result stays interpretable as the thing it actually
was when a value later changes.

Three items need explicit attention before this library is presented as authoritative:

### 1. The WHO CVD risk charts are not implemented — a model in their shape is

`CvdRiskCalculator` does **not** read WHO chart cells; none are in this repository. It implements the
documented risk-factor model the charts are built from — the same variables, the same age,
blood-pressure, cholesterol and BMI columns, the same laboratory/non-laboratory split, the same five
output bands — combined by an additive points model written by Impilo engineering.

The points are ours, not WHO's. They are an ordinal index calibrated only so that the model is
monotone in every risk factor and lands on the right band at the two corners of the chart that are
qualitatively unambiguous. No interior cell is claimed to match WHO.

Consequently the class **produces no percentage, ever**, and a test asserts that no percent accessor
exists anywhere in the package. The `cvdRisk10yrPercent` field in the medicine CDS surface **cannot**
be populated from this calculator. It must stay clinician-entered, or be replaced by a band, until
the AFR-E charts are vendored and the cell lookup implemented. `CvdRiskCalculator.CHART_CELLS_VENDORED`
is `false` and every result is stamped `Basis.IMPILO_ENGINEERING_SEED_MODEL` and carries a caveat
string that a surface must display.

### 2. The Clinical Frailty Scale descriptors are licensed and are not reproduced

The CFS descriptive text is copyright Dalhousie University and Impilo holds no licence.
`ClinicalFrailtyScale.summary()` returns a short Impilo-written paraphrase, adequate to tell the
levels apart in a picker and **not** adequate to score a patient against. A deployment that shows the
scale to clinicians needs the licensed chart.
`ClinicalFrailtyScale.OFFICIAL_DESCRIPTORS_LICENSED` is `false` for a build guard to read.

### 3. Published opioid conversion tables disagree with each other

The factors follow the Faculty of Pain Medicine (RCoA) table, which is the one aligned with UK and
Commonwealth practice. The US CDC morphine-milligram-equivalent table gives **0.15 for codeine**
where this library uses 0.1, and published oxycodone ratios span 1.5–2 where this library uses 1.5.
In both cases the more conservative figure was chosen — it reports a *lower* equivalent for the same
dose. Transdermal fentanyl uses a single 3.6 factor where the manufacturer publishes a range
(25 µg/h maps to roughly 60–134 mg/24h). Each `Opioid` constant carries its own `source()` string.

**Methadone deliberately has no factor.** Its ratio to morphine is dose-dependent and non-linear —
roughly 4:1 low down, 20:1 or more above 1000 mg/day — and it accumulates over days, so any single
number is badly wrong at one end of the range and dangerous at the other. Every conversion involving
it returns `REQUIRES_SPECIALIST_CALCULATION`, and a regimen containing it has **no total at all**:
the partial sum is exposed under a name nobody can mistake for one, because methadone is usually the
largest contributor and dropping it understates the patient's opioid burden.

### And one thing this library computes that no validated instrument backs

`TreatmentBurdenScore` is an Impilo engineering construct. The published treatment-burden instruments
(Tran et al., BMC Med 2012; Mair and May, BMJ 2014) are patient-reported and item-scored; this is a
three-dimension index over counts already in the record. It is a way of ordering a list, not a
measurement of a person, and it emits no band — where "high burden" starts is a decision about who
gets attention and is made in governed content.

## Standing clinical caveats the caller must carry

- **eGFR assumes a steady state.** In acute kidney injury the creatinine is still moving and the eGFR
  is meaningless. This library cannot detect that and does not try. eGFR is also unreliable at the
  extremes of muscle mass and in pregnancy, and it is indexed to 1.73 m² — absolute clearance for drug
  dosing needs de-indexing by the patient's own body surface area, which is not done here.
- **A KDIGO GFR category is not a diagnosis of CKD.** CKD needs the abnormality to have persisted more
  than three months, and G1/G2 are not CKD without a marker of kidney damage. The full KDIGO staging
  is a grid of GFR against albuminuria; this is one axis of it.
- **An oral morphine equivalent is not a switching dose.** Incomplete cross-tolerance means the
  calculated equivalent is routinely reduced 25–50% before prescribing. That reduction is a clinical
  decision and is not in this library. A number from here used directly as a prescription is an
  overdose. The conversion is also not symmetric and there is deliberately no inverse method.
- **NYHA class is observer-dependent.** Published agreement between two clinicians assessing the same
  patient is around 50%. A stored class should carry who assigned it.
- **The CFS is not for people under 65**, nor for people of any age whose limitations come from a
  stable single-system disability. `applicabilityCaveat(ageYears)` returns the text; it does not
  block, because this library does not overrule a clinician.

## Tests

90 tests across 7 classes, JUnit 5 + AssertJ. Each test comment states the clinical harm the
assertion stands between a patient and, rather than restating the code. Coverage is deliberately
weighted towards the cannot-compute paths and the out-of-range refusals, because those are the
behaviours a well-meaning future change is most likely to "fix".

Two of the guards were proven by breaking what they guard and confirming red: giving methadone a
linear factor fails three tests, and adding a percent accessor to `CvdRiskCalculator.Result` fails
`thereIsNoWayToGetAPercentageOutOfThisPackage`.
