# Two things called "observations" — the ruling

**Decided 2026-08-02**, before any observation UI was built, because building onto whichever store
was nearer to hand is how a duplicate system of record gets created without anyone deciding to
create one.

## The question

Two stores are both called "observations":

| | ward chart entry | `pct.pct_observations` |
|---|---|---|
| Owner | inpatient-service | pct-service |
| Table | `inpatient.clinical_chart_entry` | `pct.pct_observations` |
| Read by the UI via | `hooks/queries/useObservations.ts` → BFF `/internal/v1/observations` → `InpatientServiceClient` | nothing |
| Shape | `chart_type` + an opaque `parameters` jsonb bag | one row per coded fact |
| Coded? | no — no code, no code system, no units | `code` + `code_system` + `value_*` + `value_unit` |
| Absence | a missing row | `data_absent_reason`, enforced by a CHECK |
| Interpretation | none | `interpretation` + the reference range used **at the time** |
| Continuum anchor | none | `journey_id` / `encounter_id` / `subject_cpid` (CC-5) |
| Reaches the SHR | no | outbox → Kafka → BUTANO → `/fhir/Observation` |

## What was checked

- `InpatientClinicalController` `/internal/v1` `POST|GET /observations` delegates to
  `InpatientClinicalService.recordObservation`, which calls `recordChartEntry(body, chartType)` and
  saves a `ClinicalChartEntryEntity`. **The inpatient `/observations` endpoint is the ward chart
  under an alias** — the same store that serves `obs-chart`, `fluid-balance`, `drug-chart`,
  `fit-chart`, `diet-chart` and `turn-chart`.
- `ObservationEntity.derivedFromType` admits `FORM_RESPONSE | GROWTH_MEASUREMENT |
  LABOUR_OBSERVATION`. There is no `CHART_ENTRY`, and nothing anywhere derives a
  `pct_observations` row from a ward chart entry.
- Neither `docs/registry/services-registry.yaml` nor `docs/registry/system-of-record-map.md`
  records an ownership claim over "observation" for either service.

## The ruling

**A legitimate split, not a duplicate system of record — but the naming is the defect, and one
real gap follows from it.**

They are not two answers to the same question. A ward chart entry is a *documentation artefact*:
a timed row on a named nursing chart, whose payload is deliberately unmodelled because the chart
is whatever the ward's chart is. A `pct_observations` row is a *clinical fact*: one code, one
value or one stated reason there is none, interpreted against a range that is stored alongside so
a later revision cannot silently reinterpret history, anchored to the care continuum, and bound
for the shared record. Collapsing them would lose one of the two — either the chart becomes a
schema migration per clinical question, or the clinical fact loses its code and its absent-reason
and stops being addressable by the growth, danger-sign and classification engines.

So the guardrail against duplicate system-of-record functionality is **not** breached. Each owns a
different thing.

### But the alias is a live trap, and this ruling exists because of it

Calling the ward chart `/internal/v1/observations` is what makes the two look like the same store.
It is the reason this question had to be asked at all, and it is exactly the shape that invites the
next session to wire a coded clinical observation into a jsonb chart bag because the hook was
already there. **`useObservations.ts` is a ward-chart hook.** It is now commented as one.

### The gap this exposed, recorded rather than quietly closed

**A ward observation never becomes a clinical fact.** A temperature charted on the ward lands in a
jsonb bag with no code, and nothing derives a `pct_observations` row from it — so it is invisible
to the SHR, to the IPS, to the next facility, and to every engine that addresses observations by
code. The danger-sign engine cannot see a temperature the ward recorded an hour ago.

This is the same shape as [`pct-problems-never-reach-the-shr`]: readable on one surface, never
written to the record. It is **not** in scope of the paediatric UI wave and is not fixed here.
It is registered so that it is a known gap rather than a discovery someone makes later:

> `derived_from_type = 'CHART_ENTRY'` and a derivation from `clinical_chart_entry` into
> `pct_observations` for the coded subset (temperature, respiratory rate, saturation, pulse) is
> outstanding work owned by the inpatient/PCT lane.

## What this ruling permits and forbids in this wave

- **Permitted**: reading `pct_observations` by code where an engine names an outstanding
  observation, so an `INDETERMINATE` classification can say what is missing.
- **Forbidden**: writing a coded clinical observation through `useObservations.ts`, and adding a
  second read path onto the ward chart for anything the classification engines consume.

No paediatric surface in this wave writes an observation to either store.
