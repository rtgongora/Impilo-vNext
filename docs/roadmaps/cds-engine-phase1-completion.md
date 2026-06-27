# CDS Engine — Phase 0.2 + Phase 1 (KEYSTONE) Completion Note

**Status:** ✅ Complete and pushed. Branch `intake/wave-b-tshepo-gdhcn-trust-primitives`.
**Scope:** Context-aware interpretation of vitals + labs against patient-appropriate reference intervals,
end to end (governed artifact → backend interpretation + rules + audit → BFF → both UI surfaces).

This is the genuine "intelligence leap" the program targets: the system can now say a value is HIGH / LOW /
CRITICAL **for this patient** (age/sex-aware), with provenance and an audit trace, rather than echoing a
single global threshold — and it is **honest** when it cannot (NO_REFERENCE_RANGE is never "normal").

## What shipped (atomic commits)

| Slice | Commit | Summary |
|------|--------|---------|
| Phase 0.2 | `d29e62e07` | Zibo `OBSERVATION_DEFINITION` artifact type (append-only) + `effectiveStart/End` window + V003 migration + `createDraft` overload. |
| 1a | `53000fa75` | CKP `clinical/interpretation/` — `RangeResolver` + `InterpretationEngine` + models + `ObservationDefinitionProvider` seam + fail-closed `EmptyObservationDefinitionProvider`. |
| 1c | `56b542ef1` | `ClinicalEvaluationContext` extension (sex/pregnancy/gestation/interpretedObservations/derivedValues) + `ClinicalContextEnricher` passthrough + rules CRITICAL_LAB_VALUE / HYPERKALAEMIA / ACUTE_KIDNEY_INJURY_SUSPECTED (+ RENAL_IMPAIRMENT_LOW_EGFR) / PREGNANCY_CONTRAINDICATED_DRUG. |
| 1d | `30c521422` | `InterpretationEvaluationService` + `POST /internal/v1/clinical/interpretation/evaluate` + BFF client/controller proxy (fail-honest). |
| 1b | `55127bdbf` | `ObservationDefinitionParser` (FHIR R4 qualifiedInterval merge) + `ZiboObservationDefinitionProvider` (TTL cache, trust-forwarded, fail-closed) + Zibo `GET /v1/artifacts/observation-definitions`. |
| 1e | `ace36bbd0` | Single-source generator `export-reference-ranges.ts` + drift-guard test + cited DRAFT vitals seed JSON + idempotent `ObservationDefinitionSeeder`. |
| 1f | `82873fe15` | Web (`useInterpretClinical`, `vitalsToObservationInputs`, `InterpretedObservationFlags`, `InterpretedVitalsPanel`, wired into `EncounterVitalsGuidance`) + mobile (`interpretation-flag`, `LabResultCard` flag) render interpreted flags. |

## Doctrine upheld (and tested)
- **Authoritative source per data type:** lab-delivered interval preferred for labs; governed FHIR
  `ObservationDefinition.qualifiedInterval` (cited, in Zibo) otherwise; UI seed assets are DRAFT-only,
  superseded.
- **`NO_REFERENCE_RANGE` is never NORMAL**; UCUM-unconvertible → `UNIT_MISMATCH` (no silent wrong compare);
  implausible value → `DATA_QUALITY` (never "critical").
- **Fail-closed everywhere:** empty provider default, Zibo-fetch failure keeps prior/empty cache, BFF proxy
  returns empty interpretation on upstream error — never fabricated flags.
- **Auditable + advisory:** every evaluation records a `TraceService` trace including the **selected range
  id + version**; results are advisory and never override clinician judgement; the LLM can never assert a
  flag the deterministic interpreter didn't produce.
- **No UI↔backend drift:** the committed Zibo seed is generated from the single-source UI vitals assets and
  guarded by a structural-equality test (`UPDATE_SEED=1` regenerates).

## Verification
- **CKP:** `mvn -o test` green (98 tests incl. context-booting pathway IT) — RangeResolver (13),
  InterpretationEngine (14), interpretation rules (10), evaluate service (2), parser (5), Zibo provider (2).
- **Zibo:** `mvn -o test` green — ArtifactService (9 incl. OBSERVATION_DEFINITION + window), seeder (2).
- **BFF:** interpretation proxy test (2, pass-through + fail-honest).
- **Web (one-ui-shell):** vitest green — vitals mapping (3), flags render incl. NO_REFERENCE_RANGE-not-normal
  (3), seed drift guard (2); `tsc --noEmit` clean.
- **Mobile (mobile-design-system):** vitest green — interpretation-flag mapping (4); `tsc --noEmit` clean.

## Operational notes / known follow-ups (by design, graceful degradation)
- **Lab values:** interpretation is fully functional for labs **carrying a lab-delivered interval**. Real
  live lab result values + structured intervals flow in **Phase 3** (OROS/data-warehouse/FHIR publisher);
  until then the labs UI surface (`LabResultsSystem`) has no result values to interpret.
- **Vitals governed ranges:** vitals resolve against the **cited DRAFT** seed (`status DRAFT`, advisory).
  Promotion DRAFT→ACTIVE with authoritative cited ranges + real LOINC coding is **Phase 7**.
- **Tenant alignment:** the seed loads under `impilo.zibo.seed.tenant-id` (default
  `00000000-…-0001`); production must align the CKP request tenant with the seed tenant (or seed per
  tenant). Until aligned, vitals degrade to `NO_REFERENCE_RANGE` (honest, not fabricated).
- **Pregnancy ranges** are carried through the model but **gated OFF** until Phase 6 (VITO demographics
  normalization); never inferred.

## Gap register
G029 / G029b (clinical-AI fabrication) lineage: this phase replaces threshold-free / fabricated
interpretation with governed, cited, fail-closed, auditable interpretation. See
`docs/audits/product-truth-full-gap-register.md`.
