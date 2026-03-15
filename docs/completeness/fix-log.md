# Impilo vNext — Completeness Audit Fix Log

> Date: 2026-03-15
> Audit session: claude/review-project-manifest-jb5O0

---

## Fixes Applied

### 1. Created Completeness Audit Scripts
**Category**: Infrastructure / CI
**Files created**:
- `scripts/completeness/inspect-components.sh` — Enumerates all 113 components, validates presence
- `scripts/completeness/check-service-minimums.sh` — Checks each service for pom.xml, Application.java, migrations, tests, security config, outbox
- `scripts/completeness/check-app-runnability.sh` — Checks web/mobile apps for package.json, source files, framework config, tests
- `scripts/completeness/check-doc-and-acceptance-coverage.sh` — Validates acceptance packs, architecture docs, completeness docs, service READMEs
- `scripts/completeness/run-all.sh` — Master runner for all checks
**Impact**: CI-friendly audit suite for continuous completeness monitoring

### 2. Created Completeness Audit Documentation
**Category**: Documentation
**Files created**:
- `docs/completeness/full-platform-completeness-audit.md` — Comprehensive audit report
- `docs/completeness/component-classification-matrix.md` — Classification of all 113 components
- `docs/completeness/fix-log.md` — This file
- `docs/completeness/blockers-and-remaining-risks.md` — Outstanding blockers

### 3. Created Completeness Audit Acceptance Pack
**Category**: Acceptance
**Files created**:
- `docs/acceptance/completeness-audit-acceptance-pack.md` — Formal acceptance evidence

---

## Fixes NOT Applied (with reasoning)

### A. Missing Unit Tests for 15 Services
**Reason**: Each service requires domain-specific test logic. Adding meaningful unit tests requires deep understanding of each service's business rules. GoldenContractIT provides baseline compliance coverage. Adding stub tests would violate the "no placeholders" rule.
**Recommendation**: Service teams should add tests during their next feature iteration.

### B. Missing READMEs for 52 Services
**Reason**: While READMEs can be generated from code structure, meaningful READMEs require service-owner input on purpose, configuration, dependencies, and operational notes. Generic READMEs would be low-value placeholders.
**Recommendation**: Add README generation to service template; require README in PR review.

### C. Missing Helm Charts for 39 Services
**Reason**: Helm chart creation requires knowledge of resource limits, replica counts, health probe paths, environment variables, and deployment strategy specific to each service. Generating from a template would produce untested charts.
**Recommendation**: Adopt a shared Helm library chart with per-service values files.

### D. Empty `ui/ehr` Removal
**Reason**: Removing a directory is a destructive operation. The `ui/ehr` app may be referenced by workspace configs or CI. Marking it as FRAGILE/superseded is safer.
**Recommendation**: Team should confirm supersession by `ui/experience` and delete if confirmed.

### E. Web UI Tests
**Reason**: Each web UI requires test infrastructure setup (jest/vitest, testing-library, MSW for API mocking). Adding test files without running infrastructure would be placeholders.
**Recommendation**: Add test setup to the turbo/npm workspace configuration.

### F. TODO.md Update
**Reason**: TODO.md is a project management artifact. Updating it requires careful verification of each item's completion status against the codebase. Incorrect updates could mislead the team.
**Status**: Documented in audit as outdated. Team should reconcile.
