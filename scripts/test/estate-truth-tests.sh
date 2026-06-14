#!/usr/bin/env bash
# estate-truth-tests.sh — doctrine test matrix for the vNext unified runtime estate.
#
# All of vNext is accountable. Deployment truth is the running estate, not the deployment story.
# These tests use fixtures/static analysis only and do NOT mutate the live cluster.
set -uo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"

PASS=0
FAIL=0
note() { echo "  $*"; }
ok() { echo "PASS  $1"; PASS=$((PASS + 1)); }
bad() { echo "FAIL  $1"; FAIL=$((FAIL + 1)); }

# 1) Partial mode requires an explicit flag (refuse-partial).
(
  source scripts/full-boot/_estate-guard.sh
  ( estate_refuse_partial 0 0 ) >/dev/null 2>&1
  rc=$?
  [[ "$rc" == "2" ]]
) && ok "partial deploy without a debug flag is refused (exit 2)" \
   || bad "partial deploy without a debug flag should be refused"

# 2) Full estate is allowed without any flag.
(
  source scripts/full-boot/_estate-guard.sh
  estate_refuse_partial all 0 >/dev/null 2>&1
) && ok "full estate (all) is allowed by default" \
   || bad "full estate (all) should be allowed by default"

# 3) Debug flag present is detected.
(
  source scripts/full-boot/_estate-guard.sh
  estate_debug_flag_present --foo --allow-partial
) && ok "explicit debug flag is detected" \
   || bad "explicit debug flag should be detected"

# 4) max-wave normalizer: empty/all -> all; numeric preserved.
(
  source scripts/full-boot/_estate-guard.sh
  [[ "$(estate_normalize_max_wave '')" == "all" && "$(estate_normalize_max_wave 3)" == "3" ]]
) && ok "max-wave normalizer maps empty->all and preserves numeric" \
   || bad "max-wave normalizer incorrect"

# 5) required-only is NOT the default in the deploy script (default is full estate 'all').
if grep -q 'FULL_BOOT_MAX_WAVE:-all' scripts/deploy/full-boot-preview-deploy.sh; then
  ok "deploy default is full estate (FULL_BOOT_MAX_WAVE:-all)"
else
  bad "deploy default should be full estate"
fi

# 6) Deploy build uses --full-estate (not --required-only) by default.
if grep -q 'IMAGE_BUILD_FLAG="--full-estate"' scripts/deploy/full-boot-preview-deploy.sh; then
  ok "deploy builds full estate by default"
else
  bad "deploy should build full estate by default"
fi

# 7) Deploy pushes to the registry before rollout (push-gap closed).
if grep -q 'push-images-to-local-registry.sh' scripts/deploy/full-boot-preview-deploy.sh; then
  ok "deploy pushes images to registry before rollout"
else
  bad "deploy must push images to registry before rollout"
fi

# 8) Completeness guard emits the estate labels and never returns FULL_ESTATE_PASS for wave-0.
if grep -q 'FULL_ESTATE_PASS' scripts/guard/check-full-boot-runtime-completeness.sh \
   && grep -q 'PARTIAL_WAVE_PASS' scripts/guard/check-full-boot-runtime-completeness.sh; then
  ok "completeness guard emits estate labels"
else
  bad "completeness guard must emit estate labels"
fi

# 9) Runtime image truth guard exists, is read-only (no mutating verbs).
if [[ -f scripts/guard/check-runtime-image-truth.sh ]]; then
  if grep -vE '^\s*#' scripts/guard/check-runtime-image-truth.sh \
      | grep -qE 'ctr +images +rm|ctr +images +prune|kubectl +set +image|helm +upgrade|docker +push'; then
    bad "runtime image truth guard must be read-only"
  else
    ok "runtime image truth guard is read-only"
  fi
else
  bad "runtime image truth guard missing"
fi

# 10) Stale-JAR guard present in the JAR image builder.
if grep -q 'stale JAR' scripts/build/build-runtime-image-from-jar.sh; then
  ok "stale-JAR guard present"
else
  bad "stale-JAR guard missing"
fi

# 11) Per-service build records are written.
if grep -q 'full-image-build-records.json' scripts/build/build-full-vnext-images.sh; then
  ok "per-service build records written"
else
  bad "per-service build records missing"
fi

# 12) Non-runtime artifacts are accountable but not counted as pods (estate_role present).
if grep -q 'non_runtime_artifact' config/full-boot-service-classification.yml; then
  ok "non_runtime_artifact estate role present in classification"
else
  bad "estate_role classification missing"
fi

# 13) full-estate stop is non-destructive (no delete/uninstall/prune of data).
if grep -vE '^\s*#' scripts/operator/full-estate.sh \
    | grep -qE 'kubectl +delete|helm +uninstall|delete +(pvc|pv|secret|namespace|ns)|ctr.* (rm|prune)'; then
  bad "full-estate.sh must not contain destructive verbs"
else
  ok "full-estate.sh is non-destructive (scale/rollout only)"
fi

# 14) No product-facing 'optional' / 'required-only' language as a default in the deploy script.
if grep -nE '\-\-required-only' scripts/deploy/full-boot-preview-deploy.sh | grep -vq 'debug'; then
  bad "deploy script still uses --required-only as a non-debug default"
else
  ok "deploy script no longer uses --required-only as default"
fi

# 15) DEPLOYMENT TRUTH FAILURE message present (drift detection).
if grep -q 'DEPLOYMENT TRUTH FAILURE' scripts/deploy/full-boot-preview-deploy.sh; then
  ok "deployment truth failure message present"
else
  bad "deployment truth failure message missing"
fi

# 16) UI bundle + BFF behaviour verifiers exist.
if [[ -f scripts/test/verify-ui-bundle-truth.sh && -f scripts/test/verify-bff-behaviour-truth.sh ]]; then
  ok "UI bundle + BFF behaviour verifiers exist"
else
  bad "UI/BFF behaviour verifiers missing"
fi

echo ""
echo "ESTATE_TRUTH_TESTS: pass=$PASS fail=$FAIL"
[[ "$FAIL" == "0" ]] || exit 1
