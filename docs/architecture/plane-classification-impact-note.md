# Plane Classification Impact Note

## What Was Found

- `simba-service` existed in the Maven reactor but had been classified with a generic clinical `care-delivery` domain instead of wellness/lifestyle orchestration doctrine.
- `wellness-service` had been classified under Experience (`workflow-orchestration`) even though runtime ownership indicated wellness-domain behavior behind BFF proxying.
- `surveillance-service` and `campaigns-service` were in the Data plane but carried a generic `intelligence` domain, obscuring explicit public-health ownership.
- `mushe-wallet-service` exists on disk and in registry artifacts but is not included in `services/pom.xml` reactor modules.
- `shared-core` remains a reactor module represented as a registry library, which is valid operationally but still a classification edge case.

## Corrections Applied

- `simba-service` set to `primary_plane: enterprise`, `domain: wellness-personal-health-data`, as canonical wellness/personal-health-data system of record with explicit forbidden-responsibility boundaries.
- `wellness-service` set to `primary_plane: enterprise`, `domain: wellness-compatibility-alias`, as compatibility alias only with no canonical SoR ownership.
- `surveillance-service` set to `domain: public-health-surveillance` with explicit cross-plane touchpoints (`clinical`, `experience`, `integration`, `registry`, `trust`).
- `campaigns-service` set to `domain: public-health-campaigns` with explicit cross-plane touchpoints (`clinical`, `experience`, `integration`, `registry`, `trust`).

## Impact on Prior Plane Readiness Narratives

- Prior readiness narratives that treated wellness ownership as Experience-first should be interpreted as stale and superseded by this reconciliation.
- Public health capability narratives that relied on a generic `intelligence` domain label should be interpreted as taxonomy-incomplete.
- No plane was promoted to `READY` in this pass.
- Existing readiness states remain conservative; this pass is a classification/inventory correction pass, not a capability completion pass.

## Reports Requiring Interpretation Updates

- Trust, Registry, Experience, and Clinical reports that referenced old Simba/Wellness/Public Health taxonomy should be read with this correction note.
- Generated registry artifacts are the canonical source after this pass and should replace older manually edited tables.

## Remaining Uncertainties

- No unresolved reactor inclusion uncertainty remains for `mushe-wallet-service`; it is now included in `services/pom.xml` and compiles in reactor context.
- No unresolved `shared-core` classification ambiguity remains; it is ratified as an intentional reactor-built shared library.
- `public-health-operations` is now explicitly treated as a composite capability spanning `surveillance-service` and `campaigns-service` (+ `indawo-service` context), not a missing deployable module.

## Recommended Next Actions

- Keep `mushe-wallet-service` in the reactor and maintain parent/build alignment checks to prevent regressions.
- Preserve explicit public-health composite-capability documentation unless a future ADR introduces a dedicated module.
- Keep registry regeneration deterministic and CI-gated so taxonomy drift is detected automatically on future changes.
