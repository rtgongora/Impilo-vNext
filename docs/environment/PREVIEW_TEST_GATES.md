# Preview Test Gates

Blocking gates **fail CI / local preview-gates** and **block manual deploy by default**. Advisory gates warn but do not block.

## Blocking gates

| Gate | Purpose | Command / script | Logs | Fix |
|------|---------|------------------|------|-----|
| Secret scan | No committed keys/tokens/env | `scripts/test/run-security-checks.sh` | `$GATE_LOG_DIR/security.log` | Remove secrets; use env/secrets store |
| Static checks | Lint, typecheck, deprecated guard | `scripts/test/run-static-checks.sh` | `static.log` | Fix lint/type errors |
| Change-safety | Deletions, duplicates, inventories | `scripts/guard/run-change-safety-gates.sh` | guard stdout | Update inventories; justify deletions |
| Frontend build/test | one-ui-shell production path | `scripts/test/run-frontend-checks.sh` | `frontend.log` | Fix UI tests/build |
| Backend build/test | Core/changed services | `scripts/test/run-backend-checks.sh` | `backend.log` | Fix Maven tests |
| API contract baseline | OpenAPI + endpoint presence | `scripts/test/run-api-contract-checks.sh` | `api-contracts.log` | Restore contracts or update clients |
| Integration baseline | Health, migrations smoke | `scripts/test/run-integration-checks.sh` | `integration.log` | Fix wiring/compose |
| HTTP regression | Key routes respond | `tests/regression/preview-http-regression.sh` | regression stdout | Restore routes or update tests |
| Manual deploy CI check | CI green for commit | `scripts/ci/collect-ci-feedback.sh` | script stdout | Fix CI or explicit override |
| Post-deploy smoke | Preview live | `scripts/deploy/preview-smoke-test.sh` | deploy stdout | Fix k3s/helm/images |
| Deployed commit verify | `/health/version` SHA | `manual-authorized-preview-deploy.sh` | curl output | Redeploy correct commit |

**Master runner:** `scripts/test/run-preview-gates.sh`

**CI:** Job `preview-pipeline-gates` in `ci.yml` (push to active branch only, no deploy).

## Advisory gates

| Gate | Purpose | Command | Status |
|------|---------|---------|--------|
| Mobile install/lint/test | Expo apps baseline | `scripts/test/run-mobile-checks.sh` | Partial — Android APK optional |
| iOS / TestFlight | macOS runner + certs | Documented in `MOBILE_TEST_GATE.md` | Not buildable on Linux VM |
| Playwright E2E | Full browser flows | `scripts/test/run-web-e2e.sh` | Skipped in CI preview job (`PREVIEW_GATES_SKIP_E2E=1`) |
| Dependency audit | npm/maven CVE scan | CI `security-scan` job | Advisory / partial |
| Visual / a11y / load | Not in baseline | — | Missing |
| Full container scan | Trivy etc. | — | Later |

## GitHub Actions mapping

| CI job | Maps to |
|--------|---------|
| `backend-test`, `trust-*`, `frontend-*`, `e2e-test` | Core CI (existing) |
| `change-safety-gates` | `run-change-safety-gates.sh` |
| `preview-pipeline-gates` | `run-preview-gates.sh` |
| `deprecated-surface-guard` (workflow) | `check-deprecated-surfaces.sh` |
| `security-scan` | Partial security gates |

**No** `deploy-preview-sandbox` job in `ci.yml` — preview deploy is manual only.
