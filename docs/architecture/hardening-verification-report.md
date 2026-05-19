# Hardening Verification Report

Date: 2026-05-14  
Scope: Verification and consolidation of the architecture-hardening baseline (no broad remediation).

## 1) Acceptance Criteria Status

- No uncontrolled canonical plane vocabulary remains: **PASS**
- Generators are deterministic: **PASS**
- Changed Java code compiles/tests cleanly: **PASS**
- Route compatibility is verified and documented: **PASS**
- Hardening verification report created: **PASS**
- Remaining gaps are visible and explicit: **PASS**

## 2) Commands Run and Results

### Vocabulary and taxonomy verification

- `rg "plane:\\s*(finance|marketplace|ops|knowledge)"` -> **PASS** (no matches after normalization)
- `rg "\\b(finance|marketplace|ops|knowledge)\\s+plane\\b" -i` -> **PASS** (no matches after normalization)
- `rg "primary_plane:\\s*(finance|marketplace|ops|knowledge)"` -> **PASS** (no matches)

### Registry/completeness generation pipeline

- `cd scripts/registry && npm run generate:all` -> **PASS**
- `cd scripts/completeness && npm run report` -> **PASS**
- Verified generated artifacts exist and are populated:
  - `docs/registry/services-registry.yaml`
  - `docs/registry/services-index.md`
  - `docs/registry/planes-index.md`
  - `docs/registry/service-plane-map.md`
  - `docs/registry/service-ownership-matrix.md`
  - `docs/registry/service-readiness-register.md`
  - `docs/registry/system-of-record-map.md`
  - `docs/registry/forbidden-responsibilities-map.md`
  - `docs/registry/cross-plane-contract-map.md`
  - `docs/reports/completeness-report.md`
  - `docs/reports/completeness-report.json`

### Determinism verification

- Executed generator pipeline twice in sequence:
  - pass 1: `scripts/registry generate:all` + `scripts/completeness report`
  - pass 2: same commands
- Compared `git diff --name-only` after pass 1 vs pass 2 -> **PASS** (`DETERMINISM_CHECK=PASS`)

### Build/test safety

- `cd services && .\\mvnw.cmd -pl experience-bff,tshepo-authz-service -am test` -> **FAIL** (wrapper script not present in this repo path)
- Fallback used: `cd services && mvn -pl experience-bff,tshepo-authz-service -am test` -> **PASS** (`BUILD SUCCESS`)
  - includes new route compatibility test execution:
    - `ClinicalKnowledgeControllerRouteCompatibilityTest` -> **PASS**
  - reactor summary successful for impacted modules and dependencies.
- `cd scripts/completeness && npm run lint-learning-openapi-local` -> **PASS**
- UI lint for changed route/messaging surfaces:
  - `ui/one-ui-shell`: targeted `next lint --file ...` -> **PASS**
  - `ui/experience`: targeted `next lint --file ...` -> **PASS**

## 3) BFF Route Compatibility Verification

Controller:
- `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/ClinicalKnowledgeController.java`
- Mapping: `@RequestMapping({"/internal/v1/clinical", "/internal/v1/clinical-knowledge"})`

Verification evidence:
- Added and passed `ClinicalKnowledgeControllerRouteCompatibilityTest` proving both prefixes resolve successfully for `/assistant/ask`.
- Full module tests passed without ambiguous-mapping startup errors.
- OpenAPI source contract remains canonical on `/internal/v1/clinical/*` (`contracts/openapi/clinical-knowledge-platform.openapi.yaml`).
- Frontend clinical-tools consumers migrated to canonical prefix `/internal/v1/clinical/*`; compatibility alias retained for temporary backward compatibility.

Route policy:
- **Canonical:** `/internal/v1/clinical/*`
- **Temporary compatibility alias:** `/internal/v1/clinical-knowledge/*`

## 4) Consolidation Changes Applied During Verification

Main consolidations performed:
- Normalized legacy plane wording (`finance plane`, `ops plane`, `integration/ops plane`) to canonical plane + domain wording in affected docs/comments/labels.
- Normalized infrastructure/observability `plane` labels away from legacy values (`finance`, `operations`, etc.) to canonical values.
- Migrated `ui/experience` and `ui/one-ui-shell` clinical-tools API calls to canonical `/internal/v1/clinical/*`.
- Added explicit canonical-vs-compatibility note to `ClinicalKnowledgeController`.

## 5) Generated Artifact Drift

- Unexpected drift between deterministic runs: **NONE**
- Expected regenerated output updates from source-of-truth pipeline: **PRESENT** (registry/completeness artifacts refreshed)
- Noted environment warning: LF/CRLF conversion warnings appeared during git operations (non-functional; formatting/environmental).

## 6) Files Changed in This Verification Pass (Key)

- `docs/architecture/hardening-verification-report.md` (this report)
- `services/experience-bff/src/test/java/zw/gov/mohcc/impilo/experience/controller/ClinicalKnowledgeControllerRouteCompatibilityTest.java`
- `services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/ClinicalKnowledgeController.java` (canonical/compatibility note)
- `ui/experience/src/app/clinical-tools/page.tsx` and `ui/one-ui-shell/src/app/clinical-tools/page.tsx` (frontend migration to canonical route prefix)
- `docs/registry/backend-to-frontend-wiring-map.md` (canonical vs alias route documentation)
- Canonical plane wording normalization updates across impacted docs/comments/labels in `docs/*`, `tools/ops/observability/prometheus/prometheus.yml`, and `infra/k8s/namespaces/*.yaml`

## 7) Build/Test Failures

- Hard build/test failures after fallback command path correction: **NONE**
- Environmental limitation observed: Testcontainers-dependent suites were skipped where Docker was unavailable (existing behavior, not introduced by this pass).

## 8) Remaining Risks and Unresolved Decisions

- Compatibility alias lifecycle: `clinical-knowledge` prefix should be retired after confirming no external consumers depend on it.
- Infra label semantics: canonical `plane` labels are now normalized, but domain granularity in monitoring labels may need a formal schema extension (`domain` label) for finer operational slicing.
- Existing branch has broad pre-existing staged/unstaged scope from hardening; verification confirms baseline coherence, but service-by-service readiness work remains by design.

## 9) Baseline Safety Verdict

This branch is **safe to use as the architectural control baseline** for the next service-by-service production-readiness audit stage, with the above risks explicitly tracked and not hidden.

