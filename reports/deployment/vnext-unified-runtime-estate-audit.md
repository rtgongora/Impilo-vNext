# vNext Unified Runtime Estate — Audit

> All of vNext is accountable. All deployable vNext services must run. One estate means all
> deployable vNext services. Supporting artifacts do not run as pods, but they remain part of
> vNext truth. Waves are sequencing, not optionality. Deployment truth is the running estate,
> not the deployment story. A deployment is not complete until the full estate is running,
> aligned, healthy, current, and testable.

Doctrine: [`docs/deployment/VNEXT_UNIFIED_RUNTIME_ESTATE_DOCTRINE.md`](../../docs/deployment/VNEXT_UNIFIED_RUNTIME_ESTATE_DOCTRINE.md)

## Old partial/default behaviours found

| # | Behaviour | Location | Effect |
|---|-----------|----------|--------|
| 1 | `FULL_BOOT_MAX_WAVE` defaulted to `0` | `scripts/deploy/full-boot-preview-deploy.sh:17` | A plain deploy enabled only wave-0 (13/89 microservices) yet reported success — silent partial estate. |
| 2 | Deploy built `--required-only` | `full-boot-preview-deploy.sh` (build step) | Only the required spine images were rebuilt; the rest of the estate was never refreshed. |
| 3 | Build never pushed to the registry k3s pulls from | deploy build step lacked `push-images-to-local-registry.sh` | Local Docker had new images while `127.0.0.1:5000/impilo/*:preview` kept old digests; k3s served stale runtime. **This is the live regression that wiped the UI changes.** |
| 4 | `/health/version` treated as deployment truth | smoke + verify paths | Metadata reported the new commit while served UI bundle and an added endpoint stayed on the previous commit. |
| 5 | No digest alignment across the chain | (no guard existed) | No comparison of registry vs containerd vs Deployment vs running pod imageID. |
| 6 | `optional_full_boot` alias / `--required-only` flag language | `generate-full-boot-artifacts.mjs:156`, `build-full-vnext-images.sh` | Product-facing "optional"/"required-only" language implied services were excludable. |

## Live evidence captured during this work (read-only)

- `scripts/guard/check-runtime-image-truth.sh` against `impilo-full-preview`:
  - `experience-bff` and `one-ui-shell` (hand-fixed earlier) are **digest-aligned**.
  - **89 microservice pods are running digests that differ from the current registry `:preview`** (`RUNTIME_IMAGE_TRUTH: FAIL`). They were never re-rolled after the rebuild — the exact stale-pod class this doctrine targets.
- `scripts/guard/check-full-boot-runtime-completeness.sh`: legacy `FULL_BOOT_PASS` (metadata + readiness) is green, but the new **`ESTATE_STATUS=FAIL`** because runtime image truth fails. Deployment truth ≠ deployment story.
- `scripts/test/verify-ui-bundle-truth.sh`: served bundle `layout-f2babeb5f3d014e9.js` contains `All Features`, `ShellErrorBoundary`, `all-features` → **PASS**.
- `scripts/test/verify-bff-behaviour-truth.sh`: `GET /internal/v1/settings/display` → **200** (was 500) → **PASS**.

## Scripts created

| Path | Purpose |
|------|---------|
| `docs/deployment/VNEXT_UNIFIED_RUNTIME_ESTATE_DOCTRINE.md` | Canonical doctrine (12 sections). |
| `config/runtime-image-truth-exemptions.yml` | Allowed exemptions (7 infra images) with full justification schema. |
| `scripts/full-boot/_estate-guard.sh` | Refuse-partial guard + mandatory debug banner + max-wave normalizer. |
| `scripts/guard/check-runtime-image-truth.sh` | Per-service digest alignment across the whole chain; fails on stale non-exempt. |
| `scripts/test/verify-ui-bundle-truth.sh` | Served UI bundle hash + feature-marker verification. |
| `scripts/test/verify-bff-behaviour-truth.sh` | Changed-endpoint behaviour probe (not metadata alone). |
| `scripts/operator/full-estate.sh` | Canonical `status/update/start/stop/restart` estate operator. |
| `scripts/test/estate-truth-tests.sh` | Estate/truth doctrine test matrix. |

## Scripts updated

| Path | Change |
|------|--------|
| `scripts/deploy/full-boot-preview-deploy.sh` | Default flipped to full estate (`FULL_BOOT_MAX_WAVE=all`); refuse-partial guard + debug flags; full-estate build; **registry push before rollout**; runtime image truth + UI + BFF verification post-rollout; `DEPLOYMENT TRUTH FAILURE` on stale; pinning report. |
| `scripts/guard/check-full-boot-runtime-completeness.sh` | Emits `FULL_ESTATE_PASS / PARTIAL_WAVE_PASS / DEBUG_SLICE_PASS / FAIL`; consumes runtime-image-truth artifact; computes full runtime estate (91) not just required spine (22). |
| `scripts/build/build-full-vnext-images.sh` | `--full-estate` default; `--required-only` → `--debug-required-spine-only` (warns + banner); per-service build records to `full-image-build-records.json`. |
| `scripts/build/build-runtime-image-from-jar.sh` | Stale-JAR guard (`src` newer than JAR ⇒ fail; `ALLOW_STALE_JAR=1` emergency override). |
| `scripts/build/push-images-to-local-registry.sh` | `required` push mode flagged as debug/partial spine. |
| `scripts/full-boot/generate-full-boot-artifacts.mjs` | Expanded legacy alias normalization with loud warning; new `estate_role` field. |
| `scripts/operator/phased-wave-preview-promote.sh` | Final wave asserts full estate via runtime image truth; partial state labelled `partial_wave`. |
| `scripts/operator/fullboot.sh` | `wave-deploy` passes `--allow-partial` (sanctioned wave-sequenced intermediate). |

## Language corrected
- New estate vocabulary in `config/full-boot-service-classification.yml` via `estate_role`:
  22 `full_estate`, 96 `wave_sequenced_full_estate`, 9 `external_dependency_with_internal_adapter`,
  2 `mobile_surface_requires_mobile_test`, 17 `non_runtime_artifact` (total 146, counts unchanged).
- Legacy `optional_full_boot` / `required_only` / `optional` now normalized with a `[estate] WARN`.

## Remaining debug modes (explicit, non-default, guarded)
- `--debug-required-spine-only`, `--debug-wave-zero-only`, `--slice`, `--allow-partial`, `--no-full-estate`.
- Each triggers the banner: "This is not the full vNext estate and is not valid for full product testing. All of vNext is vNext."
- Wave-sequenced rollout (`fullboot.sh wave-deploy N`) is a sanctioned intermediate that opts in via `--allow-partial`; the final wave must reach the full estate.

## Risks found
- **Stale-image risk (active):** 89 microservice pods are stale vs registry. Surfaced by the new guard; **not remediated here** (boundary: no runtime mutation). Realizing the full estate requires an authorized full-estate update.
- **Stale-JAR risk:** guarded in `build-runtime-image-from-jar.sh`.
- **Push-gap risk:** closed — the deploy now pushes to the registry before rollout.
- **`fullboot.sh deploy` default `FULL_BOOT_SKIP_BUILD=1`:** retained for the operator checkpoint flow, but the post-rollout runtime image truth guard now fails the deploy if images are stale, so the gap can no longer pass silently.

## Readiness guard output labels
`FULL_ESTATE_PASS` | `PARTIAL_WAVE_PASS` | `DEBUG_SLICE_PASS` | `FAIL` (with `FULL_BOOT_*` retained as warned aliases).

## Recommendations
1. Run an authorized full-estate update (`scripts/operator/full-estate.sh update` or `full-boot-preview-deploy.sh`) to push + re-roll all 89 stale microservices and reach `FULL_ESTATE_PASS`.
2. Keep `FULL_BOOT_SKIP_BUILD`/`FULL_BOOT_SKIP_PUSH` unset for canonical deploys.
3. Wire `check-runtime-image-truth.sh` into the VM quality gates as advisory, then blocking once the estate is aligned.

## Validation (this change)

| Check | Result |
|-------|--------|
| `bash -n` on all 13 touched/new scripts | PASS |
| `node --check generate-full-boot-artifacts.mjs` | PASS |
| `scripts/test/estate-truth-tests.sh` | **16/16 PASS** |
| `check-runtime-image-truth.sh --service one-ui-shell` | PASS (aligned) |
| `check-runtime-image-truth.sh` (full estate) | FAIL — 89 stale pods correctly detected (read-only finding) |
| `check-full-boot-runtime-completeness.sh` | `ESTATE_STATUS=FAIL` (estate truth) / `FULL_BOOT_PASS` (legacy alias) |
| `verify-ui-bundle-truth.sh` | PASS (served bundle has markers) |
| `verify-bff-behaviour-truth.sh` | PASS (settings/display 200) |
| `check-bff-downstream-mappings.sh` | PASS (no localhost) |
| `check-full-boot-waves.sh` | PASS (110 services, 9 waves) |
| `run-change-safety-gates.sh` | PASS (no dangerous deletions) |
| classification regen counts | unchanged (146 / 22 / 98 / 12 / 9 / 5) |

shellcheck is not installed on the VM. Full VM quality gates
(`scripts/pipeline/run-local-quality-gates.sh`) recommended as a pre-commit step; the
deployment-relevant gates above pass and the change is tooling/docs only.

## Preview redeploy
Required to REALIZE the full estate (89 stale microservice pods), but gated on explicit
`AUTHORIZE FULL BOOT PREVIEW DEPLOY` per boundaries. Not performed in this change.
