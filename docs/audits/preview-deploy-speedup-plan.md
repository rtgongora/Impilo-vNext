# Preview Deploy Speed-Up Plan

**Audit date:** 2026-06-20  
**Companion:** [`preview-full-boot-pipeline-truth.md`](preview-full-boot-pipeline-truth.md), [`preview-blast-radius-strategy.md`](preview-blast-radius-strategy.md)

Practical optimisation plan for faster full boot and faster preview deploy without compromising Product Truth, digest alignment, schema safety, tests, or runtime validation.

---

## 1. Current bottlenecks

| Bottleneck | Impact | Current behaviour |
|------------|--------|-------------------|
| Full estate image build | 30–90+ minutes | Sequential Python loop in `build-full-vnext-images.sh` |
| Full Maven reactor | 10–30 minutes | `build-full-vnext.sh` builds all targets |
| Full registry push | 10–20 minutes | All runtime refs even if unchanged |
| Full k3s import | 5–15 minutes | All runtime IDs with `--force` |
| Helm `--wait` full estate | 20–45 minutes | All deployments rollout |
| Full pipeline gates | 15–40 minutes | All 16 phases blocking |
| Push skip on existing tag | Stale image risk | Skips push if `:preview` exists — good for speed, bad if sources changed |

---

## 2. Optimisation items

### 2.1 Avoid rebuilding unchanged services

**Current:** `--full-estate` builds all non-`not-required` targets every full boot.

**Plan:**
- Targeted path uses `build-full-vnext-images.sh --only <id>` for affected services only.
- Compare source fingerprint (git tree hash of service dir + libs) against `full-image-build-records.json` before build.
- Skip image build when fingerprint matches and digest still valid in registry.

**Phase:** 1 (targeted tooling) + Phase 2 (content-hash gate)

---

### 2.2 Use changed-path detection

**Current:** `classify-touched-areas.sh` and `analyze-branch-intake.sh` exist but are not wired to deploy.

**Plan:**
- `scripts/preview/resolve-blast-radius.mjs` unifies path → service mapping.
- `explain-blast-radius.sh` shows rebuild set before any build.

**Phase:** 1 (implemented in this wave)

---

### 2.3 Use a service ownership map

**Current:** `docs/registry/services-registry.yaml` + `config/full-boot-service-classification.yml`.

**Plan:**
- Resolver reads both for image eligibility and plane classification.
- Reject targeted deploy for trust-plane services (class H).

**Phase:** 1

---

### 2.4 Use dependency graph expansion

**Current:** Maven `-am` in `build-full-vnext.sh` per module; registry `consumes_from` unused in deploy.

**Plan:**
- Resolver expands 1-hop `consumes_from` / `exposes_to` for class E.
- Cap expansion at threshold (default 10); above → full boot.

**Phase:** 1

---

### 2.5 Cache frontend/backend builds

**Current:** Pipeline always runs full frontend build; Maven reactor always runs.

**Plan:**
- Targeted: `PIPELINE_ONLY` subset from blast-radius.
- Maven: `build-full-vnext.sh --only-modules <csv>` for affected modules only.
- Turborepo remote cache (future): configure in `ui/turbo.json`.

**Phase:** 1 (pipeline subset) + Phase 2 (Maven scoping)

---

### 2.6 Docker BuildKit / buildx layer caching

**Current:** Layer cache default on; `IMPILO_IMAGE_NO_CACHE=1` disables.

**Plan:**
- Ensure `DOCKER_BUILDKIT=1` in image build scripts.
- Add cache mount for Maven `.m2` in JAR template Dockerfile (Phase 2).
- Prebuild base JRE image (`impilo-jre-base`) once per session.

**Phase:** 2

---

### 2.7 Prebuild shared base images

**Current:** `scripts/build/templates/impilo-jre-runtime.Dockerfile` rebuilds JRE layer per service.

**Plan:**
- Build `impilo/jre-base:preview` once at session start.
- Reference as `FROM impilo/jre-base:preview` in template.

**Phase:** 2

---

### 2.8 Avoid invalidating Docker layers unnecessarily

**Current:** `CACHE_BUST` build-arg uses git commit + content fingerprint for BFF/shell.

**Plan:**
- Scope `CACHE_BUST` to changed service only in targeted builds.
- Do not bump global `CACHE_BUST` on unrelated commits.

**Phase:** 1 (targeted path) + Phase 2 (build script)

---

### 2.9 Split validation into tiers

| Tier | When | Phases |
|------|------|--------|
| **Fast affected** | Targeted deploy | `PIPELINE_ONLY` from resolver |
| **Critical guardrails** | Always | `security`, `change-safety` |
| **Full estate** | Full boot / release | All 16 phases + `PIPELINE_FULL_BOOT_BLOCKING=1` |

**Phase:** 1

---

### 2.10 Separate targeted preview from full boot validation

**Current:** Only `full-boot-preview-deploy.sh` exists for `impilo-full-preview`.

**Plan:**
- `scripts/preview/targeted-deploy.sh` — selective build/deploy/validate.
- `scripts/preview/full-boot.sh` — golden path with full estate checks.
- Targeted deploy does **not** require `FULL_ESTATE_PASS`; documents that full boot is still needed for promotion.

**Phase:** 1

---

### 2.11 Parallelise independent image builds

**Current:** Sequential Python loop.

**Plan:**
- Add `IMPILO_BUILD_PARALLEL` (default 2 on VM, max 4).
- Use `xargs -P` or background job pool with failure aggregation.

**Phase:** 2

---

### 2.12 Parallelise safe test groups

**Current:** Pipeline phases are sequential.

**Plan:**
- Run `static` + `security` in parallel (no shared state).
- Backend module tests in parallel per service (CI already partially does this).

**Phase:** 3

---

### 2.13 Helm upgrade only changed releases/components

**Current:** Full `helm upgrade --wait` for all deployments.

**Plan (targeted):**
- Merge digest YAML for affected services only (`resolve-image-digests.sh --only`).
- `helm upgrade` without global `--wait`; `kubectl rollout status` per affected Deployment only.
- Unchanged deployments retain prior digest pins.

**Phase:** 1

---

### 2.14 Avoid unnecessary pod restarts

**Current:** Full boot force-imports all runtime images; Recreate strategy rolls changed deployments.

**Plan:**
- Targeted: import and rollout restart only affected deployments.
- Do not mass `rollout restart` when digest unchanged.

**Phase:** 1

---

### 2.15 Improve rollout waiting logic

**Current:** `kubectl rollout status deployment -n NS` waits for all deployments (45m timeout).

**Plan:**
- Targeted: wait only affected deployment names.
- Full boot: keep global wait but add per-deployment progress logging.
- Use `staged-wave-rollout-restart.sh` pattern for large estates.

**Phase:** 1 (targeted) + Phase 2 (full boot logging)

---

### 2.16 Reuse already-built images by digest

**Current:** Push skips if tag exists; may serve stale content.

**Plan:**
- Targeted path uses `IMPILO_PUSH_FORCE=1` to always push fresh layers.
- Compare registry digest vs local docker image ID before skip.
- `check-runtime-image-truth.sh --only` validates only changed services.

**Phase:** 1

---

### 2.17 Migrations idempotent and only when needed

**Current:** Flyway runs on every pod start.

**Plan:**
- Ensure migrations are idempotent (existing doctrine).
- Targeted deploy: only rollout owning service pod (migration runs once per deploy).
- Refuse targeted deploy for cross-service schema changes.

**Phase:** 1 (refusal gates) + ongoing migration hygiene

---

### 2.18 One explicit full boot command for release confidence

**Plan:**
```bash
bash scripts/preview/full-boot.sh
# Requires: AUTHORIZE FULL BOOT PREVIEW DEPLOY
```

Wraps quality gates + `full-boot-preview-deploy.sh` + audit report.

**Phase:** 1

---

### 2.19 Quicker command for normal preview iteration

**Plan:**
```bash
bash scripts/preview/explain-blast-radius.sh
bash scripts/preview/targeted-deploy.sh --dry-run
bash scripts/preview/targeted-deploy.sh --execute
# Requires: AUTHORIZE TARGETED PREVIEW DEPLOY
```

**Phase:** 1

---

### 2.20 Mandatory deployed commit and image digest verification

**Plan:**
- Every deploy path writes `reports/audits/preview-*-deploy-<sha>.md`.
- Report includes: branch, HEAD, changed files, affected services, image tags/digests, Helm revision, readiness count, `/health/version`, PASS/FAIL.
- Targeted: `check-runtime-image-truth.sh --only <ids>`.
- Full boot: existing blocking post-rollout truth check.

**Phase:** 1

---

## 3. Phased rollout

### Phase 1 — This implementation wave

- [x] Three audit documents
- [ ] `scripts/preview/{resolve-blast-radius,explain-blast-radius,full-boot,targeted-deploy}.sh`
- [ ] `resolve-image-digests.sh --only` merge mode
- [ ] `check-runtime-image-truth.sh --only` alias
- [ ] `IMPILO_PUSH_FORCE=1`
- [ ] `build-full-vnext.sh --only-modules`
- [ ] Operator guide

**Expected speedup:** 5–20× for single-service / frontend-only changes (minutes vs hours).

### Phase 2 — Build parallelism and caching

- Parallel image builds (`IMPILO_BUILD_PARALLEL`)
- Maven module scoping in all build paths
- JRE base image prebuild
- BuildKit cache mounts
- Incremental Helm diff reporting

**Expected speedup:** 2–4× on full boot image build phase.

### Phase 3 — CI and test parallelism

- Parallel static/security phases
- Per-service backend test sharding
- Turborepo remote cache
- Optional remote registry (GHCR mirror) for digest reuse across VMs

---

## 4. Commands after Phase 1

```bash
# Fast iteration (ordinary dev)
bash scripts/preview/explain-blast-radius.sh
bash scripts/preview/targeted-deploy.sh --dry-run
bash scripts/preview/targeted-deploy.sh --execute

# Release-quality full estate
bash scripts/preview/full-boot.sh

# Legacy (unchanged)
bash scripts/deploy/full-boot-preview-deploy.sh
bash scripts/operator/full-estate.sh update
```

---

## 5. Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Targeted deploy leaves mixed digest estate | Per-service digest truth check; refuse high-risk classes |
| Stale image from push skip | `IMPILO_PUSH_FORCE=1` on targeted path |
| Under-built Maven dependents | `-am` flag + registry 1-hop expansion |
| Parallel builds OOM on VM | Default `IMPILO_BUILD_PARALLEL=2` |
| Operator bypasses full boot for release | Document full boot as promotion gate; targeted reports state "not FULL_ESTATE_PASS" |

---

## 6. Success metrics

| Metric | Target (targeted path) | Target (full boot) |
|--------|------------------------|-------------------|
| Single-service change → preview | < 15 minutes | N/A |
| Frontend-only change → preview | < 10 minutes | N/A |
| Full estate deploy | N/A | Current baseline; Phase 2 reduces 30%+ |
| Digest alignment post-deploy | 100% affected services | 100% runtime estate |
| `/health/version` commit match | Mandatory | Mandatory |
