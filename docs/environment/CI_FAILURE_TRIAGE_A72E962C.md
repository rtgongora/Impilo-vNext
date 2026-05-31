# CI Failure Triage — `a72e962c`

**Run:** [26703129031](https://github.com/rtgongora/Impilo-vNext/actions/runs/26703129031)  
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Commit:** `a72e962ca08374c67cd92c33c6a2b3c946a00a25`

## Executive summary

| Finding | Detail |
|---------|--------|
| **Primary root cause** | **GitHub Actions account billing lock** — all 22 jobs failed in ~2s with **0 steps** |
| **Evidence** | Run annotations: *"The job was not started because your account is locked due to a billing issue."* |
| **Code defects from CI** | **None proven** — tests never executed on GitHub runners |
| **VM local gates** | Security, change-safety, frontend lint/test/build, backend BFF/tshepo **pass** |
| **Pipeline fixes applied** | Registry maturity timestamp gate, change-safety base ref for `push`, improved `collect-ci-feedback.sh` |

**Owner action to get green CI:** Fix GitHub billing for `rtgongora/Impilo-vNext` (Settings → Billing). Re-run workflow after unlock.

**VM `gh`:** Not installed. Install + auth:

```bash
sudo apt install -y gh
gh auth login
```

---

## Per-job triage (run 26703129031)

All jobs share the same failure mode unless noted.

| # | Job | Failed step | Error summary | Likely cause | Category | Fix | Blocking? | Fix now? |
|---|-----|-------------|---------------|--------------|----------|-----|-------------|----------|
| 1 | Backend Tests | *(none — 0 steps)* | Job not started | Billing lock | environment issue | Resolve GitHub billing | Yes | No (account) |
| 2 | Change Safety Gates | *(none)* | Job not started | Billing lock | environment issue | Resolve billing | Yes | No |
| 3 | Frontend Lint & Type Check | *(none)* | Job not started | Billing lock | environment issue | Resolve billing | Yes | No |
| 4 | Frontend Unit Tests | *(none)* | Job not started | Billing lock | environment issue | Resolve billing | Yes | No |
| 5 | Security Scan | *(none)* | Job not started | Billing lock | environment issue | Resolve billing | Yes | No |
| 6 | E2E Compose Smoke | *(none)* | Job not started | Billing lock | environment issue | Resolve billing | Yes | No |
| 7 | E2E Sovereign Smoke | *(none)* | Job not started | Billing lock | environment issue | Resolve billing | Yes | No |
| 8 | E2E Tests | skipped | Upstream not run | Billing / needs graph | environment issue | Resolve billing | Yes | No |
| 9 | Preview Pipeline Gates (VM parity) | skipped | `needs` not satisfied | Prior jobs failed infra | CI configuration (needs) | Billing first | Yes | N/A until CI runs |
| 10 | Trust E2E Gates | skipped | needs | Billing | environment issue | Resolve billing | Yes | No |
| 11 | Trust Fullstack Runtime E2E | skipped | needs | Billing | environment issue | Resolve billing | Yes | No |
| 12 | Registry Fullstack Runtime E2E | skipped | needs | Billing | environment issue | Resolve billing | Yes | No |
| 13 | Core Transaction * jobs | skipped | needs | Billing | environment issue | Resolve billing | Mixed | No |
| 14 | Frontend parity docs up-to-date | *(none)* | Job not started | Billing lock | environment issue | Resolve billing | Yes | No |
| 15 | Parity matrix up-to-date | *(none)* | Job not started | Billing lock | environment issue | Resolve billing | Yes | No |
| 16 | Mobile E2E smoke (Maestro) | *(none)* | Job not started | Billing lock | environment issue | Resolve billing | Advisory candidate later | No |
| 17 | Learning rollout readiness gates | *(none)* | Job not started | Billing lock | environment issue | Resolve billing | Yes | No |
| 18 | Completeness report (informational) | *(none)* | Job not started | Billing lock | environment issue | Should stay advisory | Advisory | No |
| 19 | Service Registry Validation (Advisory) | *(none)* | Job not started | Billing lock | environment issue | Resolve billing | Advisory (name) | No |
| 20 | Service Registry Soft-Gate (Advisory) | *(none)* | Job not started | Billing lock | environment issue | Resolve billing | Advisory | No |

---

## Local VM verification (same commit)

| Script | Result |
|--------|--------|
| `run-security-checks.sh` | PASS |
| `run-change-safety-gates.sh` | PASS |
| `run-frontend-checks.sh` | PASS (lint, typecheck, unit, build) |
| `run-backend-checks.sh` | PASS (BFF, tshepo-authz) |
| `run-static-checks.sh` (before fix) | FAIL `registry-maturity-sync` (generatedAt-only) |
| `run-static-checks.sh` (after fix) | Expected PASS |

---

## Fixes in follow-up commit

1. **`scripts/test/verify-registry-maturity-sync.sh`** — ignore `generatedAt`-only drift (CI + preview gates).
2. **`scripts/guard/_guard-common.sh`** — use `github.event.before` on push; fall back to `origin/HEAD` (no `origin/main` on this repo).
3. **`.github/workflows/ci.yml`** — pass `GUARD_BASE_REF` / `GITHUB_EVENT_BEFORE`; use shared maturity verifier.
4. **`scripts/ci/collect-ci-feedback.sh`** — detect 0-step infra failures and report billing guidance.

---

## Gates not weakened

- No tests removed.
- No blocking → advisory demotions for real code gates.
- Billing failures documented as **infra**, not greenwashed.

---

## After billing is restored

1. Re-run workflow on latest branch commit.
2. `bash scripts/ci/collect-ci-feedback.sh` — confirm `ci_jobs_zero_steps: 0`.
3. Triage any **real** step failures from logs.
4. User authorizes preview deploy only if blocking gates pass.
