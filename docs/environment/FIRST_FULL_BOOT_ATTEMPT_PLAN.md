# First Full Boot Attempt Plan

**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Namespace:** `impilo-full-preview`  
**Slice (do not modify):** `impilo-preview` at `http://41.57.127.235`

## Full-registry expansion (waves 0–7)

All **runtime K8s microservices** are declared in
`values-full-preview-runtime.generated.yaml` (from classification + waves).
Wave *N* cumulatively enables services in `config/full-boot-waves.yml` through wave *N*.
Non-runtime registry entries use `scripts/build/build-non-runtime-registry-lane.sh`
(build/validate only, `run-not-applicable` in k3s).

```bash
bash scripts/guard/check-registry-inventory-contract.sh
bash scripts/operator/fullboot.sh wave-build 1
bash scripts/operator/fullboot.sh wave-deploy 1   # + AUTHORIZE FULL BOOT PREVIEW DEPLOY
bash scripts/operator/report-preview-generation.sh
```

## Preconditions (this batch)

| Check | Status |
|-------|--------|
| Required images built (22/22) | Ready |
| Helm chart + `values-full-preview.yaml` + runtime generated overlay | Ready |
| Official infra images pulled on VM | Done |
| k3s image import | Narrow helper: `sudo bash scripts/operator/install-k3s-image-helper.sh` then `bash scripts/operator/fullboot.sh import-images` |
| HAPI database `hapi` | Init via postgres `initDatabases` + post-install Job |
| Ingress for full boot | **Enabled** — the full stack owns the public IP (Highest-Validated-Stack-Wins) |

## VM: import and verify (before authorized deploy)

**One-time (technical operator):**

```bash
cd /opt/impilo/repos/Impilo-vNext
sudo bash scripts/operator/install-k3s-image-helper.sh
bash scripts/operator/test-k3s-image-helper.sh
```

**Each full boot attempt (Cursor orchestrates; product owner authorizes only):**

```bash
cd /opt/impilo/repos/Impilo-vNext
bash scripts/operator/fullboot.sh deploy
# If checkpoint required: product owner runs sudo-checkpoint-run once, then:
# Tell Cursor: sudo checkpoint completed
bash scripts/operator/fullboot.sh continue
```

If passwordless helper is installed, Cursor rarely needs a sudo checkpoint. Legacy fallback: checkpoint `import_full_boot_images_to_k3s` via `sudo-checkpoint-run` (not manual `k3s ctr` loops).

Expect verify output (authoritative = containerd, not pod Running):

```
IMAGE_PRESENCE: PASS  (22/22 present)
SUMMARY ok=22 fail=0
RUNTIME_DEPLOYMENT: NOT_APPLICABLE
```

`v-*` pods are **temporary image checks only** — `Completed` is OK; `StartError` on HAPI/Keycloak-like images without `sleep` is **not** a missing-image signal if `IMAGE_PRESENCE` shows PASS.

Optional: `sudo k3s ctr images list -q | grep -E 'impilo|postgres|kafka|keycloak|minio|hapi|envoy' | head -100`

Syntax check (non-destructive):

```bash
bash -n scripts/dev/import-full-vnext-images-k3s.sh
bash -n scripts/dev/verify-full-boot-k3s-images.sh
```

## Routing strategy (current: **full stack owns the public IP**)

**Highest-Validated-Stack-Wins.** The public IP `http://41.57.127.235` surfaces the latest
validated preview generation. The full stack (`impilo-full-preview`) owns the Traefik ingress;
the legacy 4-service slice (`impilo-preview`) is a rollback fallback with its ingress disabled.
Exactly one ingress claims the public entrypoint (the ingress name is the Helm release name, so
slice and full-stack ingresses never silently collide).

1. **`ingress.enabled: true`** in `values-full-preview.yaml`; **`ingress.enabled: false`** in
   `values-preview.yaml` (slice). The cutover also deletes any live slice ingress object.
2. Validate directly on the public IP after an authorized deploy:

```bash
curl -s http://41.57.127.235/health/version          # environment: full-preview + deployed commit
curl -s -o /dev/null -w "%{http_code}\n" http://41.57.127.235/   # 307 -> /auth/login (shell)
bash scripts/operator/report-preview-generation.sh    # SINGLE_PUBLIC_STACK: yes
```

3. Browser confirmation: open `http://41.57.127.235`, Sign In with any email/password for a
   CITIZEN preview session (Keycloak realm seeding is a follow-up to unlock provider/clinical
   authenticated flows; until then the BFF issues a local fallback session).
4. Internal-only debugging (optional) still works via port-forward:

```bash
kubectl port-forward -n impilo-full-preview svc/experience-bff 18160:8160
kubectl port-forward -n impilo-full-preview svc/one-ui-shell 13000:3000
```

### Rollback

Re-enable the slice ingress and remove the full-stack ingress (see
[FULL_BOOT_OPERATOR_MODE.md](./FULL_BOOT_OPERATOR_MODE.md) "Rollback to the slice").

### Superseded options

| Option | Status |
|--------|--------|
| A — port-forward only, ingress disabled | Superseded: was the first-attempt safety posture; IP now serves the full stack |
| B — `/full-preview` ingress path | Not needed — the full stack owns `/` directly |
| C — separate host | Future: a DNS hostname can replace the bare IP |

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
