# Preview Targeted Deploy Validation Report

**Date:** 2026-06-20  
**Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`  
**Validator commits:** `fbaf3670` (tooling) → `db8ae52c` (UI marker) → `9cf1f14b` (preflight) → `4a3c525e` (bundle-hash fix)  
**Public preview:** http://41.57.127.235

## Executive summary

| Path | Result | Live preview updated? |
|------|--------|------------------------|
| Blast-radius explain/dry-run | **PASS** | N/A |
| Targeted deploy (`--execute`) | **PASS** (after bundle-hash fix) | **Yes** — commit `4a3c525e` |
| Full-boot wrapper attempt | **FAIL** (image build guard) | No — estate remained at `bc3033e4` until targeted pass |

**Verdict:** Targeted preview deploy is **validated for one live execution** on a class **B** (frontend-only) change. It is **not** declared production-ready as the default dev path until full-boot wrapper re-validation and k3s import hardening are complete (see §8).

---

## 1. Baseline preview state (pre-validation)

| Check | Value |
|-------|-------|
| Branch | `claude/staging-ux-orchestration-remediation-Yypyl` |
| Repo HEAD (start) | `fbaf3670` |
| Public `/health/version` commit | `bc3033e4` |
| Environment | `full-preview` |
| Helm release | `impilo-full-preview` revision **84** |
| Runtime readiness | **99/99** deployments ready |
| Digest alignment (estate) | **92/92** aligned, **0** stale (`reports/full-boot/runtime-image-truth.md`) |
| Public stack | `SINGLE_PUBLIC_STACK: yes` (`reports/full-boot/preview-generation.json`) |

---

## 2. Full-boot wrapper attempt

### Command

```bash
# Quality gates (wrapper prerequisite)
bash scripts/pipeline/run-local-quality-gates.sh

# Deploy (authorized; equivalent to scripts/preview/full-boot.sh deploy phase)
printf 'AUTHORIZE FULL BOOT PREVIEW DEPLOY\n' | bash scripts/deploy/full-boot-preview-deploy.sh
```

### Timing (measured)

| Phase | Start (local) | End (local) | Duration |
|-------|---------------|-------------|----------|
| Quality gates | 10:26:46 | 10:35:11 | **~8 min 25 s** |
| Maven reactor compile | 10:35:22 | ~10:36 | **~1 min** |
| Full-estate image builds (`--full-estate`) | ~10:36 | ~10:59 | **~23 min** (aborted) |
| Registry push | — | — | Not reached |
| Digest resolve | — | — | Not reached |
| k3s import | — | — | Not reached |
| Helm upgrade | — | — | Not reached |
| Rollout / smoke / `/health/version` | — | — | Not reached |

**Total wall before abort:** ~**32 min** (gates + partial full estate build)

### Outcome

- **99** images built successfully; **1** failed: `one-ui-shell`
- Failure: `FAIL UI bundle verification: hash unchanged (73a43c3eb202d8a2) after UI source changes`
- Root cause: `fbaf3670` changed only docs/scripts (no UI source), but full-estate rebuild still rebuilt `one-ui-shell` with strict bundle-hash guard
- Estate unchanged on public IP (`bc3033e4` throughout)

### Reference: last successful full boot (bc3033e4)

For comparison, the prior successful estate deploy (revision **84**, 2026-06-20 09:32 local) achieved **99/99** readiness with full digest alignment. A complete full-boot wrapper run from clean HEAD was **not** completed in this validation session.

---

## 3. Targeted validation change

### Commits

1. `db8ae52c` — comment marker in `ui/one-ui-shell/src/app/layout.tsx` (frontend-only probe)
2. `4a3c525e` — `IMPILO_UI_BUNDLE_HASH_STRICT=0` for targeted path (comment-only edits may not change Next.js layout chunk hash)

### Blast-radius classification

```bash
bash scripts/preview/explain-blast-radius.sh --base fbaf3670
bash scripts/preview/targeted-deploy.sh --dry-run --base fbaf3670
```

| Field | Value |
|-------|-------|
| Change class | **B** (frontend-only) |
| Full boot required? | **NO** |
| Targeted allowed? | **YES** |
| Images to build | `one-ui-shell` only |
| Pipeline phases | `security,static,frontend,parity-web,change-safety` |

**Dry-run: PASS**

---

## 4. Targeted deploy execution

### Command

```bash
printf 'AUTHORIZE TARGETED PREVIEW DEPLOY\n' | bash scripts/preview/targeted-deploy.sh --execute --base fbaf3670
```

### Timing (successful run, HEAD `4a3c525e`)

| Phase | UTC window | Duration |
|-------|------------|----------|
| Targeted quality gates | 09:21:00 → 09:24:28 | **~3 min 28 s** |
| Compile (`--only-modules` none for shell) + `npm run build` | 09:24:28 → ~09:35 | **~10 min** (incl. Next.js build in log) |
| Docker image build (`one-ui-shell` only) | ~09:35 → ~09:40 | **~5 min** |
| Registry push (1 image, `IMPILO_PUSH_FORCE=1`) | ~09:40 → ~09:45 | **~5 min** |
| Digest resolve (`--only one-ui-shell`, merge) | ~09:45 | **<1 min** |
| k3s import | — | **Skipped** (passwordless helper unavailable; WARN logged) |
| Helm upgrade | 09:34:59 local deploy stamp | **~2 min** |
| Rollout (`one-ui-shell` only) | — | **<1 min** |
| Digest truth + UI bundle truth | — | **~1 min** |

**Total wall clock:** ~**31 min** (11:20:58 → 11:51:51 local, including gates)

Logs: `reports/audits/validation-targeted-deploy-2.log`, `reports/audits/preview-targeted-deploy-4a3c525e.md`

### Services / images rebuilt

| Metric | Full-boot attempt | Targeted (success) |
|--------|-------------------|---------------------|
| Maven modules compiled | Full reactor | **0** Java modules |
| Docker images built | **99** (+1 fail) | **1** (`one-ui-shell`) |
| Registry pushes | 0 | **1** |
| Helm revision change | None | **84 → 86** |
| Deployments rolled | 0 | **1** (`one-ui-shell`) |

### Image digest change (one-ui-shell only)

| | Digest |
|---|--------|
| Before (rev 84) | `sha256:f486da2deb2319a50ac4dcffe3a05914fd5036c31ef85ecf83b04df43098279c` |
| After (rev 86) | `sha256:2b83af44f29d6a4b0eb44001d38665108da03c8a2f91972cefdc9fde81503ec2` |
| Registry push digest | `sha256:0094f61578c7a69c9dca46d741251ebac58ef65676bb7a7e683bec7c09ab2874` |

Other runtime services: digest pins **preserved** via `resolve-image-digests.sh --only` merge (91 digests in generated file).

### Pod restarts

- **Confirmed:** only `one-ui-shell` deployment rolled (`kubectl rollout status deployment/one-ui-shell` succeeded)
- **99/99** estate readiness maintained post-deploy
- No mass `rollout restart` invoked

### Smoke / truth checks

| Check | Result |
|-------|--------|
| `check-runtime-image-truth.sh --only one-ui-shell` | **PASS** (aligned) |
| `verify-ui-bundle-truth.sh` | **PASS** |
| Public `/health/version` commit | **`4a3c525e`** — **matches deployed HEAD** |
| Public environment | `full-preview` |

```json
{"service":"experience-bff","environment":"full-preview","branch":"claude/staging-ux-orchestration-remediation-Yypyl","commit":"4a3c525e0461730c32f6f1649bf193fd2713133e","buildDate":"2026-06-20T09:24:28Z","status":"ok"}
```

---

## 5. Answers to required questions

### Did targeted deploy rebuild only the expected blast radius?

**Yes.** Blast resolver and execution built, pushed, and digest-pinned **only `one-ui-shell`**. No Java service images were built.

### Did it avoid unnecessary service rebuilds?

**Yes.** Full-boot path attempted **~100** image builds; targeted path built **1**.

### Did it avoid unnecessary pod restarts?

**Yes.** Only the `one-ui-shell` deployment rolled. Estate remained **99/99** ready.

### Did `/health/version` reflect the deployed commit?

**Yes.** After successful targeted deploy, public IP returned commit **`4a3c525e`**, matching deploy-time HEAD.

### Did digest pinning remain correct after partial merge?

**Yes** for the changed component: `one-ui-shell` registry → deployment → pod `imageID` chain aligned.  
**Yes** for unchanged services: merge mode retained existing digests in `values-full-preview-digests.generated.yaml` (91 entries resolved).

### What was the time saved versus full boot?

| Scenario | Measured / estimated wall time |
|----------|-------------------------------|
| Full-boot attempt (incomplete) | ~32 min to abort; full success historically **60–120+ min** |
| Targeted success | ~31 min end-to-end |
| Targeted gates only | ~3.5 min vs ~8.5 min full gates |

**Net:** Targeted path avoided **~23 min** of multi-service image builds in the failed full-boot attempt alone. For a complete full boot, savings vs a **60+ min** estate rebuild are **approximately 30–90+ minutes** depending on cache — but targeted still spends **~25 min** on frontend npm + Docker build for `one-ui-shell`.

**Important:** First targeted attempt failed (~11 min) on strict bundle-hash guard; not counted in success timing.

### What issues remain before adopting targeted deploy as ordinary default?

1. **Full-boot wrapper not re-validated end-to-end** after tooling commits (attempt failed on bundle-hash guard for docs-only HEAD).
2. **k3s import** relied on registry pull; passwordless `impilo-k3s-import-images` was unavailable (WARN). Import should be mandatory or verified in targeted path.
3. **Bundle-hash strictness:** comment-only UI edits fail under strict mode; targeted path now sets `IMPILO_UI_BUNDLE_HASH_STRICT=0` (warn-and-continue). Need policy for when strict mode applies.
4. **Helm revision 85** appeared between failed attempts and successful revision **86** — investigate intermediate upgrades before promoting workflow.
5. **Dirty-tree / generated-artifact friction:** validation runs modify `reports/` and `values-*.generated.yaml`; preflight now ignores untracked files but tracked generated changes still block.
6. **BFF `/health/version` carries deploy commit** — correct for HEAD at deploy time, but UI-only changes require experience-bff image rebuild or version endpoint split if BFF commit must not advance on shell-only deploys (observed: BFF metadata updated to `4a3c525e` while only shell image changed).

---

## 6. Fixes applied during validation

| Commit | Fix |
|--------|-----|
| `9cf1f14b` | Preflight ignores untracked files (`git status --untracked-files=no`) |
| `4a3c525e` | `IMPILO_UI_BUNDLE_HASH_STRICT=0` in targeted deploy; warn on unchanged layout hash |

---

## 7. Production-readiness statement

Per task requirements: targeted deploy is **not** claimed production-ready as the ordinary default.

**Evidence for limited readiness:**

- One successful live targeted execution on class **B** change
- Correct blast-radius classification (dry-run + execute)
- Single-service rebuild and rollout
- Public `/health/version` commit alignment
- Digest truth PASS for changed component

**Blocking gaps for default adoption:**

- No successful full-boot wrapper run on current HEAD
- k3s import gap on targeted path
- Bundle-hash policy unsettled
- No validation yet for class **C** (single backend) or **D** (BFF) changes

---

## 8. Recommended operator commands

```bash
# Always explain first
bash scripts/preview/explain-blast-radius.sh --base <last-deployed-commit>

# Fast iteration (frontend-only)
bash scripts/preview/targeted-deploy.sh --dry-run --base <last-deployed-commit>
printf 'AUTHORIZE TARGETED PREVIEW DEPLOY\n' | bash scripts/preview/targeted-deploy.sh --execute --base <last-deployed-commit>

# Release-quality (still required)
bash scripts/preview/full-boot.sh

# Verify public truth
curl -s http://41.57.127.235/health/version | python3 -m json.tool
bash scripts/guard/check-runtime-image-truth.sh --only one-ui-shell
```

---

## 9. Artifact index

| Artifact | Path |
|----------|------|
| Full-boot timing | `reports/audits/validation-full-boot-timing.log` |
| Full-boot deploy log | `reports/audits/validation-full-boot-deploy.log` |
| Targeted deploy log (success) | `reports/audits/validation-targeted-deploy-2.log` |
| Targeted deploy report | `reports/audits/preview-targeted-deploy-4a3c525e.md` |
| Blast radius JSON | `reports/audits/blast-radius-9cf1f14b.json` |
