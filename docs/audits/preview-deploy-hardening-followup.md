# Preview Deploy Hardening Follow-up

**Date:** 2026-06-20  
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Baseline validation:** `af8e945d` (`docs/audits/preview-targeted-deploy-validation.md`)  
**Hardening HEAD:** (this commit)

## Executive summary

| Area | Status | Notes |
|------|--------|-------|
| Full-boot bundle-hash blocker | **Fixed** | Strict mode uses committed UI diff only; idempotent rebuilds pass |
| Bundle-hash policy formalized | **Done** | `strict` (full boot) vs `relaxed` (targeted); logged in build output |
| k3s import preflight | **Done** | Execute paths fail unless helper ready or `PREVIEW_K3S_IMPORT_MODE=registry-pull` |
| Component-aware `/health/version` | **Implemented** | Requires experience-bff image rollout to surface new JSON fields |
| Full boot on current HEAD | **Not re-run** | No `AUTHORIZE FULL BOOT PREVIEW DEPLOY` in this session |
| Targeted deploy validation | **Still valid** | Prior live pass at `4a3c525e`; current change set is class **I** (full boot required) |

---

## 1. Full-boot bundle-hash blocker (root cause + fix)

### Root cause

`build-full-vnext-images.sh` treated **working-tree porcelain** changes under `ui/one-ui-shell` as “UI source changes”. During full-estate image builds, generated artifacts (e.g. `registry-maturity.json`) made `_ui_sources_changed()` return true even when **committed** UI sources were unchanged (docs-only HEAD `fbaf3670`).

Strict guard then failed: `hash unchanged (73a43c3eb202d8a2) after UI source changes`.

### Correction (strict preserved)

- Replaced porcelain/working-tree detection with **committed-only** diff: `git diff <baseline_commit> HEAD -- ui/one-ui-shell ui/shared-ui`.
- Baseline commit read from `reports/full-boot/ui-bundle-build-meta.json`.
- If committed UI unchanged and bundle hash unchanged → **PASS** (idempotent rebuild) with explicit log line.
- If committed UI changed and hash unchanged → **FAIL** in `strict` mode; **WARN** in `relaxed` mode.
- Build logs always emit: `UI bundle hash policy: strict|relaxed`.

### Policy matrix

| Path | Env | Behaviour |
|------|-----|-----------|
| Full boot | `IMPILO_UI_BUNDLE_HASH_MODE=strict` | Fail on committed UI change + unchanged hash |
| Targeted | `IMPILO_UI_BUNDLE_HASH_MODE=relaxed` | Warn and continue (no silent bypass) |

Meta artifact now includes `source_tree_fingerprint` and `policy_mode`.

---

## 2. k3s import preflight

### Problem (validation)

Targeted execute skipped k3s import with only a WARN when passwordless helper was unavailable — ambiguous success.

### Fix

`scripts/preview/_preview-common.sh`:

- `preview_k3s_import_preflight` — reports helper status; on **execute** requires:
  1. Passwordless helper (`/usr/local/sbin/impilo-k3s-import-images` + `sudo -n`), **or**
  2. Explicit `PREVIEW_K3S_IMPORT_MODE=registry-pull` when local registry is reachable.
- `preview_run_k3s_import` — no silent skip on execute; deploy still **fails** if post-rollout `check-runtime-image-truth.sh` fails.

Operator guidance added to `docs/environment/PREVIEW_DEPLOY_OPERATOR_GUIDE.md`.

**VM check (2026-06-20):** `K3S_IMPORT_MODE=passwordless-helper` on live cluster.

---

## 3. Component-aware `/health/version`

### Problem

Targeted shell-only deploy advanced BFF `commit` via `global.gitCommit`, misrepresenting estate alignment.

### Implementation

- `PreviewVersionController` extended with: `deploymentMode`, `shellCommit`, `bffCommit`, `fullEstateCommit`, `fullEstateCertifiedCommit`, `helmRelease`, `helmRevision`, `imageDigests`, `generatedAt`.
- Legacy `commit` field = `bffCommit` (unchanged consumers see BFF truth).
- Helm templates: separate `shellGitCommit` / `bffGitCommit`; targeted shell-only deploy does **not** set `global.gitCommit`.
- Provenance values: `deploy/helm/impilo-vnext/values-preview-provenance.generated.yaml` (generated at deploy).
- Certified commit file: `reports/full-boot/full-estate-certified-commit.txt` (seeded `bc3033e4` from last known `FULL_ESTATE_PASS`).

### Activation note

New JSON fields appear only after **experience-bff** rolls out with the hardened controller + provenance env vars. Shell-only targeted deploy does not require BFF code change for digest truth, but **does** require one BFF rollout for full component-aware response.

---

## 4. Verification commands run

```bash
# Syntax
bash -n scripts/preview/_preview-common.sh
bash -n scripts/preview/_preview-deploy-provenance.sh
bash -n scripts/preview/targeted-deploy.sh
bash -n scripts/preview/full-boot.sh
bash -n scripts/deploy/full-boot-preview-deploy.sh

# BFF compile
cd services/experience-bff && mvn -q -DskipTests compile

# Blast radius (class I at hardening HEAD — full boot required)
bash scripts/preview/explain-blast-radius.sh

# Targeted dry-run (correctly refuses class I; k3s preflight OK on VM)
bash scripts/preview/targeted-deploy.sh --dry-run

# Full-boot deploy dry-run (helm template path)
bash scripts/deploy/full-boot-preview-deploy.sh --dry-run
```

`scripts/preview/full-boot.sh --dry-run` blocked on dirty working tree (generated artifacts from prior validation runs). Use clean tree or stash before wrapper dry-run.

---

## 5. Full boot re-validation

**Not executed** — requires interactive phrase `AUTHORIZE FULL BOOT PREVIEW DEPLOY` and ~45–90 min estate build.

Recommended post-merge verification:

```bash
git pull
bash scripts/pipeline/run-local-quality-gates.sh
bash scripts/preview/full-boot.sh
# confirm: FULL_ESTATE_PASS, 99/99, runtime-image-truth, /health/version fullEstateCommit
```

Expected: `one-ui-shell` strict bundle-hash **PASS** on docs/scripts-only commits; **FAIL** only when committed UI changes produce identical layout hash.

---

## 6. Remaining risks

| Risk | Mitigation |
|------|------------|
| BFF not yet rolled with new `/health/version` fields | One full-boot or targeted BFF deploy |
| `full-estate-certified-commit.txt` is manually seeded | Updated automatically on next `FULL_ESTATE_PASS` |
| Registry-pull fallback depends on cluster registry reachability | Runtime-image-truth remains blocking |
| Class I change set requires full boot | Cannot re-validate targeted path on this commit without `--files` scoping |

---

## 7. Files changed

| File | Change |
|------|--------|
| `scripts/build/build-full-vnext-images.sh` | Committed-only bundle-hash guard + policy logging |
| `scripts/preview/_preview-common.sh` | k3s import preflight helpers |
| `scripts/preview/_preview-deploy-provenance.sh` | **New** — provenance resolution + Helm values writer |
| `scripts/preview/targeted-deploy.sh` | Provenance helm sets, k3s preflight, relaxed policy |
| `scripts/deploy/full-boot-preview-deploy.sh` | Strict policy, provenance, certified commit recording |
| `services/experience-bff/.../PreviewVersionController.java` | Component-aware response |
| `deploy/helm/impilo-vnext/templates/{experience-bff,one-ui-shell}.yaml` | Per-component commit env vars |
| `docs/environment/PREVIEW_DEPLOY_OPERATOR_GUIDE.md` | Policy + preflight + health/version docs |
| `reports/full-boot/full-estate-certified-commit.txt` | Seed `bc3033e4` |
