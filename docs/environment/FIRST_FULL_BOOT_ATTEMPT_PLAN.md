# First Full Boot Attempt Plan

**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Namespace:** `impilo-full-preview`  
**Slice (do not modify):** `impilo-preview` at `http://41.57.127.235`

## Preconditions (this batch)

| Check | Status |
|-------|--------|
| Required images built (22/22) | Ready |
| Helm chart + `values-full-preview.yaml` | Ready |
| Official infra images pulled on VM | Done |
| k3s image import | Run on VM with sudo: `bash scripts/dev/import-full-vnext-images-k3s.sh preview-$(git rev-parse --short HEAD)` (requires passwordless sudo or interactive terminal) |
| HAPI database `hapi` | Init via postgres `initDatabases` + post-install Job |
| Ingress for full boot | **Disabled** — does not take over public URL |

## Routing strategy (selected: **A — port-forward first**)

Full boot **must not** disturb the slice Traefik ingress on `41.57.127.235`. Therefore:

1. **`ingress.enabled: false`** in `values-full-preview.yaml` for `impilo-full-preview`.
2. After authorized deploy, validate with **kubectl port-forward** from the VM or laptop with kubeconfig:

```bash
# Experience BFF (health/version, APIs)
kubectl port-forward -n impilo-full-preview svc/experience-bff 18160:8160

# One UI shell
kubectl port-forward -n impilo-full-preview svc/one-ui-shell 13000:3000

# Optional: HAPI metadata
kubectl port-forward -n impilo-full-preview svc/hapi-fhir 18090:8090
```

3. Smoke checks against local forwards:

```bash
curl -s http://127.0.0.1:18160/health/version
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:13000/
```

4. Confirm slice still healthy:

```bash
curl -s http://41.57.127.235/health/version
kubectl get pods -n impilo-preview
```

### Not used for first attempt

| Option | Why deferred |
|--------|----------------|
| B — `/full-preview` ingress path | Risk of Traefik rule overlap with slice; add only after port-forward proof |
| C — separate host | No DNS/host allocated yet |

## Deploy sequence (after authorization)

1. `bash scripts/deploy/full-boot-preview-deploy.sh --preflight`
2. `bash scripts/deploy/full-boot-preview-deploy.sh --dry-run`
3. Type authorization phrase: **`AUTHORIZE FULL BOOT PREVIEW DEPLOY`**
4. `bash scripts/deploy/full-boot-preview-deploy.sh`
5. `bash scripts/test/run-full-boot-smoke-tests.sh` (set `FULL_BOOT_BASE_URL=http://127.0.0.1:18160` if port-forward active)
6. `bash scripts/guard/check-full-boot-runtime-completeness.sh`

## Authorization

Phrase (exact):

```
AUTHORIZE FULL BOOT PREVIEW DEPLOY
```

Command:

```bash
bash scripts/deploy/full-boot-preview-deploy.sh
```

## Rollback

See `docs/environment/FULL_BOOT_ROLLBACK_AND_CLEANUP.md`.
