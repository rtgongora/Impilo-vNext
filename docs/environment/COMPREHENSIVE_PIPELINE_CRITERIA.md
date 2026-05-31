# Comprehensive Pipeline Criteria

## What Makes This Pipeline Comprehensive

A comprehensive Impilo vNext pipeline includes all of the following. Items marked **partial** or **missing** are tracked in [PIPELINE_GAP_MATRIX.md](./PIPELINE_GAP_MATRIX.md).

### 1. Static correctness

Lint, typecheck, formatting where available, compile checks, no broken imports, deprecated surface guard.

**Evidence:** `scripts/test/run-static-checks.sh`, `deprecated-surface-guard.yml`, CI `frontend-lint`.

### 2. Build correctness

Frontend production build, backend service build, mobile/container checks where practical.

**Evidence:** `run-frontend-checks.sh`, `run-backend-checks.sh`, CI `backend-test`, image build scripts under `scripts/deploy/`.

### 3. Unit and service-level tests

Frontend Vitest, backend Maven tests, mobile unit tests where available.

**Evidence:** `ui/one-ui-shell` tests, CI `frontend-test`, `backend-test`.

### 4. API contract protection

OpenAPI validation, BFF compatibility, client endpoint references.

**Evidence:** `contracts/openapi/`, `scripts/test/run-api-contract-checks.sh`, `scripts/guard/check-api-contracts.sh`.

### 5. Integration checks

Migrations, service startup, gateway/BFF, auth surface, Postgres/Redis/Kafka where applicable, health/version.

**Evidence:** `run-integration-checks.sh`, CI trust fullstack compose jobs.

### 6. Regression tests

Routes load, major workflows, services/APIs exist, mobile baseline.

**Evidence:** `tests/regression/preview-http-regression.sh`, Playwright in CI.

### 7. AI-agent change-safety guards

Dangerous deletions, duplicate services/features, inventory checks, large diff warnings.

**Evidence:** `scripts/guard/run-change-safety-gates.sh`, architecture inventories.

### 8. Security and secrets checks

No committed secrets/keys/production env/real PHI; advisory CVE/container scan.

**Evidence:** `run-security-checks.sh`, CI `security-scan`.

### 9. Mobile pipeline coverage

Audit, install, lint/test, Android preview when stable, iOS documented.

**Evidence:** `MOBILE_APP_AUDIT.md`, `run-mobile-checks.sh` (advisory).

### 10. Preview deployment controls

**No auto-deploy after push**; user-authorized manual deploy; CI summarized first; block on failed gates; explicit override only.

**Evidence:** `ci.yml` (no deploy job), `deploy-preview.yml` (`workflow_dispatch`), `manual-authorized-preview-deploy.sh`.

### 11. Post-deploy smoke tests

Preview URL, pods, BFF health, `/health/version` commit, key routes.

**Evidence:** `preview-smoke-test.sh`, deploy script version check.

### 12. Human-readable feedback loop

Cursor collects CI, summarizes risks/deletions, user decides.

**Evidence:** `collect-ci-feedback.sh`, `CURSOR_CI_FEEDBACK_TEMPLATE.md`, `.cursor/rules/ci-feedback-and-manual-deploy.mdc`.

### 13. Documentation and repeatability

Pipeline, gates, failure handling, owner checklist; formal staging separate.

**Evidence:** `docs/environment/*`, `OWNER_PREVIEW_TEST_CHECKLIST.md`, `FUTURE_FORMAL_TEST_STAGING_REQUIREMENTS.md`.
