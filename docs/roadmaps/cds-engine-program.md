# CDS Engine Program (Track B) — Real End-to-End Intelligent Clinical Decision Support

> **Status / sequencing:** This is **Track B**, a product initiative that spun off from gap-register
> rows **G029/G029b** (clinical-AI fabrication — now closed). Per PO decision (2026-06-24), Track B is
> **deferred until Track A (the gap register, Waves G→H→I in
> [`agent-led-fullstack-completeness-roadmap.md`](agent-led-fullstack-completeness-roadmap.md)) reaches
> zero**. Run Track B in **dedicated, single-focus sessions** thereafter — never blended with
> remediation (that blending caused scope sprawl).
>
> **Already landed (pushed):** W1–W3 (grounded LLM clinical reasoning on AIDiagnosticAssistant +
> ActiveCDSBanner, fail-closed, auditable) and **Phase 0 part 1** — `UcumUnitConverter` (real
> `org.fhir:ucum`, commit `63f87e3c`). **Next:** Phase 0 part 2 (Zibo `OBSERVATION_DEFINITION`) →
> Phase 1 keystone.

## Context

W1–W3 gave a real **grounded clinical reasoning layer** (Claude via llm-orchestration over EDLIZ +
deterministic rules, fail-closed, auditable). It is **not yet an intelligent CDS engine**: it does not
**interpret observations against patient-appropriate reference ranges**, and its context lacks labs,
sex, pregnancy/gestational age, trends, and derived values. So it cannot say "this potassium is
critically high *for this patient*." Track B closes that gap, end to end.

## Reference-range doctrine (authoritative source per data type)
- **Labs:** the **performing lab's delivered reference interval is authoritative** (intervals are
  assay/method/population-specific). Today it arrives in OROS `PostResultRequest.summary` (unstructured)
  and is dropped — never mapped to FHIR `Observation.referenceRange`/`interpretation`. Capture it
  structurally; preferred source for any lab result.
- **Vitals + standard analytes + derived values:** governed **FHIR `ObservationDefinition.qualifiedInterval`**
  (R4 resource for age/sex/gestation/condition-keyed intervals), hosted in **Zibo** (JSONB +
  canonicalUrl/version/provenance/status; needs the artifact type added), with **cited provenance**
  (national guidelines, NEWS2, WHO growth standards).
- **UI assets** (`investigationCatalog.ts`, `vitals-reference/metadata.ts`) are a **provisional DRAFT
  seed only** — never authoritative — superseded by cited ObservationDefinitions and lab intervals.
- **UCUM unit conversion** (delivered, Phase 0) for unit-safe comparison; unconvertible → `UNIT_MISMATCH`.
- **Fail-closed:** no resolvable range → `NO_REFERENCE_RANGE` (never a fabricated "normal"); implausible
  value → data-quality flag (never "critical").

## Architecture

```
 lab-delivered interval ─┐                          ┌─ FHIR ObservationDefinition (Zibo, cited, governed)
 (authoritative, labs)   ▼                          ▼
        ┌──────────── RangeResolver (lab-interval → ObservationDefinition → none) ────────────┐
 UCUM   │   InterpretationEngine: classify N/L/H/CRIT + FHIR interpretation + delta/trend       │
 conv.  │   ClinicalCalculators: BMI/eGFR/MAP/anionGap/correctedCa/paeds-z (+ reuse NEWS2/APGAR)│
        └──────────────────────────────── interpreted observations + derived values ───────────┘
                                   │ (additively extend ClinicalEvaluationContext)
            ┌──────────────────────┴───────────────────────┐
   ClinicalRulesEngine (new: AKI, hyperK, pregnancy-contra,  Grounded LLM reasoner (narrates the
   critical-lab) — AUTHORITATIVE safety                       interpreted picture, can't invent flags)
            └──────────────────────┬───────────────────────┘
        Unified CDS patient-context bundle (BFF assembles → CKP interprets)
            └── AIDiagnosticAssistant + ActiveCDSBanner (render interpreted flags + ranges)
                closed-loop → booking recall + discharge; pathways → order sets
```

## Phases (each atomic, runtime-verifiable, committed+pushed)

- **Phase 0 — Standards foundations.** (a) ✅ UCUM converter (`clinical/terminology/UcumUnitConverter`,
  `63f87e3c`). (b) Zibo: add `OBSERVATION_DEFINITION` to `ArtifactType` (append-only; ordinal-persisted)
  + FHIR validation + `effectiveStart/End` columns on `ArtifactEntity`.
- **Phase 1 — KEYSTONE: context-aware interpretation of vitals + labs on both surfaces.** CKP
  `interpretation/` (`RangeResolver`: lab-interval → ObservationDefinition by LOINC + patient context
  (age/ageBand/sex/pregnancy/gestation/specimen, UCUM-normalized) → none; `InterpretationEngine`:
  NORMAL/LOW/HIGH/CRITICAL_* + FHIR code + delta/trend; `NO_REFERENCE_RANGE` never NORMAL) + models +
  Zibo ObservationDefinition client/cache. Single-source seed generator
  (`ui/.../scripts/export-reference-ranges.ts`) → cited DRAFT ObservationDefinitions + CI hash-guard
  (no UI/backend drift). Additive `ClinicalEvaluationContext` extension (sex, pregnancyStatus,
  gestationalAgeWeeks, interpretedObservations, derivedValues) + `fromMap` branch; **`ClinicalContextEnricher`
  MUST pass new fields through**. New rules: AKI (rising creatinine/low eGFR), hyperkalaemia,
  pregnancy-contraindicated drug, generic critical-lab. New `POST /internal/v1/clinical/interpretation/evaluate`
  + BFF proxy. Both surfaces render H/L/CRIT flags + range used. Ships on age/sex; pregnancy ranges
  gated to Phase 6.
- **Phase 2 — Unified context bundle + backend calculators + exhaustive surfaces.** BFF
  `GET /internal/v1/clinical/patient-context/{patientId}?encounterId=` assembles the exhaustive bundle
  with a `context_completeness` map (fail-closed: false slice ⇒ suppress dependent rules). Backend
  `calculators/` (BMI, eGFR CKD-EPI 2021, MAP, anion gap, corrected calcium, paeds z-score; reuse
  inpatient NEWS2/APGAR) → `DERIVED` analytes through the same interpretation+rules path. Both UI
  surfaces switch to one `usePatientCdsContext` hook.
- **Phase 3 — Lab pipeline carries structured intervals + FHIR interpretation.** OROS structured
  reference-interval + interpretation on `PostResultRequest`/`ResultEntity`; data-warehouse
  `GoldLabEntity` structured low/high/interpretation; `FhirPublisher`/`ButanoIntegration` populate FHIR
  `Observation.referenceRange` + `Observation.interpretation`.
- **Phase 4 — Pathways → order sets.** Activate dormant `branchLogic`/`recommendationLogic` in
  `PathwaySessionService.advance()`; emit `recommended_investigations`/`recommended_orders`
  (result-feedback depends on Phase 3).
- **Phase 5 — Closed-loop follow-up.** `POST /internal/v1/clinical/recommendations/{traceId}/follow-up`
  → booking recall + discharge linkage carrying `sourceTraceId`.
- **Phase 6 — VITO demographics normalization.** Structured `pregnant`/`gestationalAgeWeeks`/`weight`/
  `height` on `ClientEntity` (out of free-text `demographics` JSON) → unlocks pregnancy/gestation ranges.
- **Phase 7 — (infra/data-migration, last) authoritative range library + LOINC coding + backfill.**
  Cited clinically-governed ObservationDefinition library promoted DRAFT→ACTIVE; real LOINC coding of
  live results via Zibo LIMS→LOINC `MappingIndex`; `GoldLabEntity` backfill. Everything above degrades
  gracefully (`context_completeness`, `NO_REFERENCE_RANGE`) until this lands.

## Reuse (do not reinvent)
Zibo `ArtifactEntity`/`ArtifactService` (host ObservationDefinition; provenance/version/status) +
`MappingIndex`; CKP `TraceService`/outbox (audit incl. selected range id+version),
`ClinicalRulesEngine`/`ClinicalEvaluationContext`/`ClinicalContextEnricher`, `reasoning/*` +
`GroundingValidator`, `CdsInsightService` subset-guardrail, `ClinicalKnowledgeItemEntity` (effective
dates + `sourceRefsJson` citations); data-warehouse `GoldLabEntity` (trends); inpatient
`EarlyWarningScoreEntity`/`ApgarScoreEntity` (NEWS2/APGAR); BFF `EhrPatientSummaryController` + IPS +
`StructuredHistoryController`; booking `AppointmentController`; `PathwaySessionService`; UI seed assets.

## Safety / governance invariants (every phase)
Authoritative source per data type (lab-delivered preferred; cited ObservationDefinition otherwise; UI
seed = DRAFT/provisional, superseded). UCUM-safe or `UNIT_MISMATCH`; `NO_REFERENCE_RANGE` never NORMAL;
implausible → data-quality flag. Every interpretation/recommendation auditable via TraceService (range
id+version recorded); advisory, never overrides clinician; LLM can never assert a flag/range the
deterministic interpreter didn't produce (extend the subset-guardrail); `context_completeness`
fail-closed (missing slice ⇒ suppress, never fabricate).

## Verification (per phase)
Unit: UCUM conversions; RangeResolver precedence (age/sex/pregnancy/gestation/specimen, wildcard
fallback, missing→empty, unit-mismatch); InterpretationEngine classification incl. critical +
NO_REFERENCE_RANGE-never-NORMAL + trend; calculators vs known vectors; new rules fire + existing rules
unchanged (safety regression); seed drift hash-guard; trace records selected range version. Runtime-proof
(CLI-Postgres + LLM stack where relevant): boot CKP (+ Zibo seeded) + Postgres; POST
`/interpretation/evaluate` with out-of-range observations → correct interpretation + range provenance +
audit trace; missing range → `NO_REFERENCE_RANGE`; lab-delivered interval preferred. Per-surface: open
a seeded patient → interpreted flags render; remove data → fail-closed. Each phase pushes atomic commits
and updates `docs/audits/product-truth-full-gap-register.md`.

## Risks & mitigations
- **Clinical safety of thresholds:** provisional ranges ship DRAFT/advisory + labelled; lab-delivered
  intervals preferred; cited authoritative library replaces seed in Phase 7; critical thresholds gated.
- **UCUM correctness:** real library + tests; fail-closed on unconvertible.
- **Missing demographics:** resolver degrades to widest applicable range + flags reduced specificity;
  never guesses sex/pregnancy; pregnancy ranges gated to Phase 6.
- **LOINC gaps:** resolve by local code + flag; un-codable results flagged, not fabricated (Phase 7).
- **Scale / scope creep:** strictly phased, each independently shippable; intelligence lands in Phase 1;
  Phase 7 (infra/backfill) last with graceful degradation. One focus per session.
