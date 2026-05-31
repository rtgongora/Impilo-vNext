# Pipeline Gap Matrix

| Category | Status | Evidence | Risk if missing | Recommendation | Priority |
|----------|--------|----------|-----------------|----------------|----------|
| Static correctness | Implemented | `run-static-checks.sh`, CI lint | Bad merges break build | Keep in blocking gates | immediate |
| Build correctness | Partial | frontend/backend scripts; full monorepo build heavy | Runtime failures in preview | Expand changed-service detection | next |
| Frontend tests | Implemented | Vitest, `test:routes`, `test:no-stubs` | UI regressions | Keep blocking | immediate |
| Backend tests | Partial | BFF + tshepo in CI; not all 60+ services | Domain regressions | Add changed-service matrix | next |
| API contract checks | Partial | `run-api-contract-checks.sh` | Silent API breaks | Add response-shape diffs | next |
| Integration checks | Partial | trust compose in CI; light integration script | Wiring failures | Expand BFF wire tests | later |
| Regression tests | Partial | HTTP baseline + Playwright | Broken routes undetected | Grow HTTP + E2E list | immediate |
| Mobile tests | Partial | `check-mobile-parity.sh` + advisory build | Mobile drift | Stabilize APK/iOS; keep new-gap blocking | next |
| Backend–frontend parity | Implemented | `check-backend-frontend-parity.sh` | Silent UX gaps | Keep blocking for new gaps | immediate |
| Secrets/security | Partial | `run-security-checks.sh` | Credential leak | Add gitleaks/trufflehog optional | next |
| Dangerous deletion guard | Implemented | `check-dangerous-deletions.sh` | Lost functionality | Keep blocking | immediate |
| Duplicate service guard | Implemented | `check-duplicate-services.sh` | Architecture drift | Keep blocking | immediate |
| Architecture inventories | Implemented | `SERVICE_INVENTORY.md` + sync script | Context loss for agents | Regen on major changes | immediate |
| CI feedback collection | Implemented | `collect-ci-feedback.sh` | Deploy without knowing CI | `gh auth login` on VM | immediate |
| Human authorization | Implemented | manual deploy + cursor rules | Unauthorized preview | Enforce in agent workflow | immediate |
| Manual preview deployment | Implemented | `deploy-preview.yml`, manual script | Stale preview | User runs after CI green | immediate |
| Post-deploy smoke | Implemented | `preview-smoke-test.sh` | False “deployed” claims | Always run | immediate |
| Commit/version confirmation | Implemented | `/health/version` in manual script | Wrong build live | Never skip | immediate |
| Documentation | Implemented | `docs/environment/*` this wave | Process drift | Update on workflow change | immediate |
| Formal staging separation | Documented | `FUTURE_FORMAL_TEST_STAGING_REQUIREMENTS.md` | Confusion with preview | Keep preview as sandbox only | later |
| GitHub Actions reliability | Partial | historical fast failures | False blocked deploy | Fix org runners/billing | immediate |
| Auto-deploy after push | **Removed** | no `deploy-preview-sandbox` in ci.yml | Unwanted prod-like churn | Do not re-enable without approval | — |
| Full vNext build/boot | **Implemented (prep)** | `build-full-vnext.sh`, `generate-full-boot-artifacts.mjs`, classification YAML | False “full platform” claims | Run build + completeness gate; full deploy only with authorization | immediate |
| Full boot runtime gate | **Implemented** | `check-full-boot-runtime-completeness.sh` | Slice mistaken for full OS | Block “full vNext” language until FULL_BOOT_PASS | immediate |
| Doctrine compliance gate | **Partial** | `check-doctrine-compliance.sh` (automatable subset) | Doctrine drift | Expand blocking checks over time | next |
