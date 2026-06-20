# Preview Deploy Operator Guide

**Public preview URL:** http://41.57.127.235  
**VM workspace:** `/opt/impilo/repos/Impilo-vNext`  
**Full-boot namespace:** `impilo-full-preview`  
**Slice namespace (fallback):** `impilo-preview`

## When to use which command

| Scenario | Command |
|----------|---------|
| Understand what would rebuild | `bash scripts/preview/explain-blast-radius.sh` |
| Ordinary dev (single service, UI, BFF) | `bash scripts/preview/targeted-deploy.sh --execute` |
| Shared contracts, schema, auth/trust, Helm, release | `bash scripts/preview/full-boot.sh` |
| Legacy full boot (same deploy, fewer wrappers) | `bash scripts/deploy/full-boot-preview-deploy.sh` |
| Legacy slice (4 services, no public ingress) | `bash scripts/deploy/manual-authorized-preview-deploy.sh` |

**Recommended default:**
- **Targeted deploy** for ordinary development iteration
- **Full boot** for shared contracts, schema/migrations, auth/trust plane, Helm/infra, registry/classification changes, release candidates, and final preview validation

## Prerequisites

```bash
cd /opt/impilo/repos/Impilo-vNext
git status                    # prefer clean tree for deploy
kubectl cluster-info          # k3s reachable
bash scripts/operator/registry-up.sh   # local registry 127.0.0.1:5000
```

Quality gates before deploy:

```bash
bash scripts/pipeline/run-local-quality-gates.sh
bash scripts/pipeline/cursor-local-feedback.sh
```

## 1. Explain blast radius (always start here)

```bash
bash scripts/preview/explain-blast-radius.sh
```

Optional:

```bash
bash scripts/preview/explain-blast-radius.sh --base origin/main
bash scripts/preview/explain-blast-radius.sh --files services/pharmacy-service/src/...
```

**Output:**
- Console summary (change class, full boot required?, images to build)
- `reports/audits/blast-radius-<sha>.json`
- `reports/audits/blast-radius-explain-<timestamp>.md`

## 2. Targeted preview deploy (fast iteration)

### Dry-run (default safe mode)

```bash
bash scripts/preview/targeted-deploy.sh --dry-run
```

### Execute

```bash
bash scripts/preview/targeted-deploy.sh --execute
```

When prompted, type:

```
AUTHORIZE TARGETED PREVIEW DEPLOY
```

**Refusal:** If change class requires full boot (contracts, Helm, trust plane, large expansion), the script exits 2 and directs you to full boot.

**Environment overrides:**

| Variable | Effect |
|----------|--------|
| `TARGETED_ALLOW_DIRTY=1` | Allow deploy with uncommitted changes (not recommended) |
| `GUARD_BASE_REF` | Override git base for blast-radius detection |
| `BLAST_RADIUS_EXPANSION_THRESHOLD` | Max services for class E targeted deploy (default 10) |
| `IMPILO_PUSH_FORCE=1` | Force registry push even if tag exists (set automatically on execute) |
| `PREVIEW_K3S_IMPORT_MODE=registry-pull` | Acknowledge registry-pull fallback when passwordless import helper is unavailable (execute still requires runtime-image-truth PASS) |

**Report:** `reports/audits/preview-targeted-deploy-<sha>.md`

**Note:** Targeted deploy does **not** assert `FULL_ESTATE_PASS`. Run full boot before release promotion.

## 3. Full boot (release-quality)

```bash
bash scripts/preview/full-boot.sh
```

When prompted by the underlying deploy script, type:

```
AUTHORIZE FULL BOOT PREVIEW DEPLOY
```

Options:

```bash
bash scripts/preview/full-boot.sh --force-gates   # re-run all quality gates
bash scripts/preview/full-boot.sh --dry-run       # gates only, no deploy
```

**Report:** `reports/audits/preview-full-boot-deploy-<sha>.md`

**Post-deploy checks (automatic):**
- `check-runtime-image-truth.sh` (blocking)
- `check-full-boot-runtime-completeness.sh`
- `verify-ui-bundle-truth.sh`
- `verify-bff-behaviour-truth.sh`
- `http://41.57.127.235/health/version` component-aware provenance (`deploymentMode`, `shellCommit`, `bffCommit`, `fullEstateCommit`, …)

## UI bundle hash policy

| Path | Mode | Behaviour |
|------|------|-----------|
| Full boot | `strict` (`IMPILO_UI_BUNDLE_HASH_MODE=strict`) | Fails only when **committed** UI sources changed and layout chunk hash is unchanged |
| Targeted | `relaxed` (`IMPILO_UI_BUNDLE_HASH_MODE=relaxed`) | Warns and continues (comment-only or no-op UI edits) |

Build logs always emit `UI bundle hash policy: <mode>`. There is no silent bypass.

## k3s image import preflight

Before **execute**, scripts run `preview_k3s_import_preflight`:

1. **Preferred:** passwordless helper at `/usr/local/sbin/impilo-k3s-import-images` (install via `sudo bash scripts/operator/install-k3s-image-helper.sh`).
2. **Fallback:** export `PREVIEW_K3S_IMPORT_MODE=registry-pull` when the local registry is reachable — deploy proceeds only if post-rollout `check-runtime-image-truth.sh` passes.

Dry-run reports helper status without blocking.

## `/health/version` component fields

After the experience-bff image with `PreviewVersionController` rolls out, the public endpoint includes:

| Field | Meaning |
|-------|---------|
| `deploymentMode` | `full-boot` or `targeted` |
| `shellCommit` | one-ui-shell deploy commit |
| `bffCommit` | experience-bff commit (legacy `commit` mirrors this) |
| `fullEstateCommit` | Last full-estate alignment commit |
| `fullEstateCertifiedCommit` | Last `FULL_ESTATE_PASS` commit (`reports/full-boot/full-estate-certified-commit.txt`) |
| `helmRelease` / `helmRevision` | Active Helm release metadata |
| `imageDigests` | Affected image digests from last deploy |
| `generatedAt` | Provenance record timestamp |

Targeted shell-only deploys must **not** advance `bffCommit`; only `shellCommit` changes.

## Authorization phrases

| Path | Phrase |
|------|--------|
| Full boot | `AUTHORIZE FULL BOOT PREVIEW DEPLOY` |
| Targeted deploy | `AUTHORIZE TARGETED PREVIEW DEPLOY` |
| Slice deploy | `AUTHORIZE DEPLOY` or `AUTHORIZE DEPLOY WITH VM GATES` |

Deploy is **never** automatic after push.

## Safety checks

All preview scripts verify:

- Git branch and HEAD commit
- Dirty working tree (block unless `TARGETED_ALLOW_DIRTY=1`)
- kubectl cluster reachability
- Local registry (warn if down)
- Helm release presence (required for targeted execute)
- Failed health checks → FAIL in report

Failures are never silently skipped.

## Blast-radius classes (summary)

See [`docs/audits/preview-blast-radius-strategy.md`](../audits/preview-blast-radius-strategy.md) for full detail.

| Class | Targeted OK? |
|-------|--------------|
| A Docs-only | Yes (no deploy) |
| B Frontend-only | Yes |
| C Single backend | Yes |
| D BFF/experience | Yes |
| E Shared contract/lib | Only if expansion ≤ threshold |
| F DB migration | No (multi-service) |
| G Helm/infra | No |
| H Security/trust | No |
| I Cross-platform | No |

## Audit documents

| Document | Purpose |
|----------|---------|
| [`preview-full-boot-pipeline-truth.md`](../audits/preview-full-boot-pipeline-truth.md) | Current pipeline map |
| [`preview-blast-radius-strategy.md`](../audits/preview-blast-radius-strategy.md) | Classification and rules |
| [`preview-deploy-speedup-plan.md`](../audits/preview-deploy-speedup-plan.md) | Optimisation roadmap |

## Legacy commands (unchanged)

```bash
bash scripts/deploy/full-boot-preview-deploy.sh
bash scripts/operator/full-estate.sh update
bash scripts/operator/fullboot.sh deploy
bash scripts/deploy/manual-authorized-preview-deploy.sh
```

## Troubleshooting

| Symptom | Action |
|---------|--------|
| Targeted refused | Run `explain-blast-radius.sh`; use `full-boot.sh` |
| Registry unreachable | `bash scripts/operator/registry-up.sh` |
| k3s import fails | `sudo bash scripts/operator/install-k3s-image-helper.sh` then `bash scripts/operator/fullboot.sh import-images` |
| k3s import preflight blocks targeted execute | Install helper **or** `export PREVIEW_K3S_IMPORT_MODE=registry-pull` (registry must be reachable) |
| Stale images | Ensure `IMPILO_PUSH_FORCE=1` on targeted path; check `reports/full-boot/runtime-image-truth.md` |
| `/health/version` bffCommit advanced on shell-only deploy | Re-deploy experience-bff with hardened provenance Helm values; verify `deploymentMode=targeted` |
| GitHub CI blocked | Run VM gates; use `AUTHORIZE DEPLOY WITH VM GATES` for slice only |

## Example session

```bash
cd /opt/impilo/repos/Impilo-vNext
git pull
bash scripts/pipeline/run-local-quality-gates.sh

# After editing pharmacy-service
bash scripts/preview/explain-blast-radius.sh
bash scripts/preview/targeted-deploy.sh --dry-run
bash scripts/preview/targeted-deploy.sh --execute

# Before release promotion
bash scripts/preview/full-boot.sh
curl -s http://41.57.127.235/health/version | python3 -m json.tool
```
