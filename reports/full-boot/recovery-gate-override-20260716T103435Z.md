# Full Boot recovery gate override evidence

**Recorded (UTC):** 2026-07-16T10:34:35.927538+00:00
**Authorised override:** `AUTHORIZE FULLBOOT_SKIP_GATES=1`
**Scope:** one-time recovery deployment only

## Release pin
- **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`
- **HEAD:** `32f2c4fa6304024503c1131247256f3a2f8cad2a`
- **Checkpoint:** `cp-20260716114608-32f2c4fa6` (cleanup_duplicate_k3s_import_processes, success=True)
- **Build control:** `FULL_BOOT_SKIP_BUILD=0`
- **Deploy auth:** `AUTHORIZE FULL BOOT PREVIEW DEPLOY` (already issued)

## Override semantics (committed)
`FULLBOOT_SKIP_GATES=1` in `scripts/deploy/full-boot-preview-deploy.sh` skips **only** the pre-deploy CI/VM evidence gate (`run_predeploy_gate`). It does **not** skip:
source/branch pinning, static/code gates already run, parity/no-mock gates already run, full-boot discovery, preflight, Helm dry-run, image rebuild, digest resolution, registry verification, chart integrity, Helm deploy, or post-deploy validation.

## Pre-deploy gate result (must remain exactly this)
- **Verdict:** FAIL
- **Passed:** 25
- **Failed:** 2 — ['Preview sandbox runtime smoke', 'Preview persistence E2E']
- **vm_pipeline_passed:** False
- **Commit on report:** 32f2c4fa6304024503c1131247256f3a2f8cad2a

## Accepted failures only
1. Preview sandbox runtime smoke
2. Preview persistence E2E

### Smoke evidence
```
Preview sandbox runtime smoke — internal http://127.0.0.1 (public https://impilo.mohcc.gov.zw)
FAIL  GET /health/version unreachable
FAIL  GET / ingress
FAIL  GET /auth/login/provider-id status=404
FAIL  GET /registry status=404
FAIL  GET /work/vashandi/workforce status=404
FAIL  GET /enterprise status=404
FAIL  GET /learning/library status=404
FAIL  GET /dags status=404
Preview runtime smoke FAILED
```

### Persistence E2E evidence
```
FAIL  preview unreachable at http://127.0.0.1/health/version — cannot run persistence E2E
```

## Namespace deletion
- **impilo-full-preview:** NotFound at evidence time
- **Deletion timing:** 2026-07-16T09:52:43Z approx (deploy started; cleanup before evidence gate in /tmp/fullboot-rebuild-deploy-32f2c4fa6.log)
- **Cause:** authorised `fullboot.sh deploy` cleaned the namespace before the evidence gate; that namespace owned the public ingress (Highest-Validated-Stack-Wins)

## Why deployment is required
The two failing gates test the public/local preview runtime. That runtime is unavailable because its ingress-owning namespace was deleted by the authorised recovery path. Completing rebuild-deploy restores the runtime those gates validate.

## Mandatory repayment
After Helm completes: `unset FULLBOOT_SKIP_GATES` and re-run the full quality gate suite at the same HEAD. Both previously skipped gates must PASS. Override must not be treated as a green result by itself.
