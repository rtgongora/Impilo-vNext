#!/usr/bin/env bash
# Full boot preview deploy — impilo-full-preview only; does not modify impilo-preview slice.
#
# All of vNext is accountable. All deployable vNext services must run. One estate means all
# deployable vNext services. Waves are sequencing, not optionality. Deployment truth is the
# running estate, not the deployment story. A deployment is not complete until the full estate
# is running, aligned, healthy, current, and testable.
#
# DEFAULT = full estate. To run an explicitly partial debug mode, pass one of:
#   --debug-required-spine-only | --debug-wave-zero-only | --slice | --allow-partial | --no-full-estate
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"
source scripts/full-boot/_full-boot-common.sh
source scripts/full-boot/_estate-guard.sh
source scripts/deploy/_preview-deploy-metadata.sh

AUTH_PHRASE="AUTHORIZE FULL BOOT PREVIEW DEPLOY"
NAMESPACE="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
CHART_DIR="deploy/helm/impilo-vnext"
VALUES_FILE="$CHART_DIR/values-full-preview.yaml"
RUNTIME_VALUES="$CHART_DIR/values-full-preview-runtime.generated.yaml"
BFF_ENV_VALUES="$CHART_DIR/values-full-preview-bff-env.generated.yaml"
RELEASE_NAME="impilo-full-preview"
MODE="deploy"
# DEFAULT is the FULL ESTATE. 'all' => no --max-wave passed downstream (every microservice enabled).
# A numeric value is only honoured when an explicit debug/partial flag is also present.
FULL_BOOT_MAX_WAVE="$(estate_normalize_max_wave "${FULL_BOOT_MAX_WAVE:-all}")"
ESTATE_DEBUG_SET=0

# Public preview IP. Per Highest-Validated-Stack-Wins this is now served by the full stack.
PREVIEW_URL="${PREVIEW_URL:-http://41.57.127.235}"
# Dual-mode CI/VM evidence gate (mirrors manual-authorized-preview-deploy.sh).
BYPASS_CI="${BYPASS_CI:-0}"
BYPASS_REASON="${BYPASS_REASON:-}"
FULLBOOT_SKIP_GATES="${FULLBOOT_SKIP_GATES:-0}"
VM_REPORT="$REPO_PATH/reports/pipeline/latest-summary.json"

# Pre-deploy evidence gate: require GitHub CI green OR VM local gates for HEAD,
# unless explicitly bypassed. Does not replace the AUTH_PHRASE; it runs before it.
run_predeploy_gate() {
  if [[ "$FULLBOOT_SKIP_GATES" == "1" ]]; then
    echo "WARN: FULLBOOT_SKIP_GATES=1 — skipping CI/VM evidence gate (operator override)."
    return 0
  fi
  local branch expected_sha ci_infra code_test deploy_blocked vm_passed fb
  branch="$(git -C "$REPO_PATH" branch --show-current 2>/dev/null || echo unknown)"
  expected_sha="$(git -C "$REPO_PATH" rev-parse HEAD 2>/dev/null || echo unknown)"
  ci_infra="no"; code_test="unknown"; deploy_blocked="yes"; vm_passed="no"

  echo "--- Pre-deploy evidence gate (branch=$branch commit=${expected_sha:0:8}) ---"
  if [[ "$BYPASS_CI" == "1" ]]; then
    echo "WARN: BYPASS_CI=1 — $BYPASS_REASON"
    return 0
  fi

  fb="$(mktemp)"
  bash scripts/ci/collect-ci-feedback.sh "$branch" | tee "$fb" || true
  grep -q "ci_infra_failure: yes" "$fb" && ci_infra="yes"
  grep -q "code_test_result: pass" "$fb" && code_test="pass"
  grep -q "deploy_blocked: no" "$fb" && deploy_blocked="no"
  rm -f "$fb"

  if [[ -f "$VM_REPORT" ]]; then
    local vm_ok
    vm_ok="$(python3 -c "
import json,sys
d=json.load(open('$VM_REPORT'))
print('yes' if d.get('commit')==sys.argv[1] and d.get('vm_pipeline_passed') and not d.get('blocking_failure') else 'no')
" "$expected_sha" 2>/dev/null || echo no)"
    [[ "$vm_ok" == "yes" ]] && vm_passed="yes"
    echo "VM local pipeline: $([[ "$vm_ok" == yes ]] && echo PASS for commit || echo not valid for HEAD)"
  else
    echo "VM local pipeline: no report at $VM_REPORT (run scripts/pipeline/run-local-quality-gates.sh)"
  fi

  if [[ "$code_test" == "pass" && "$deploy_blocked" == "no" ]]; then
    echo "GATE PASS: GitHub CI green for HEAD."
  elif [[ "$ci_infra" == "yes" && "$vm_passed" == "yes" ]]; then
    echo "GATE PASS: GitHub CI infra-blocked but VM local gates passed for HEAD."
  else
    echo ""
    echo "GATE BLOCKED: no green GitHub CI and no valid VM local gates for HEAD."
    echo "  Run: bash scripts/pipeline/run-local-quality-gates.sh"
    echo "  Or:  BYPASS_CI=1 BYPASS_REASON='...' (explicit owner approval)"
    echo "  Or:  FULLBOOT_SKIP_GATES=1 (operator override, not recommended)"
    exit 1
  fi
}

# Post-deploy public verification: the IP must serve THIS stack/commit.
verify_public_ip() {
  echo ""
  echo "=== Post-deploy public verification ($PREVIEW_URL) ==="
  local vjson live_commit live_env
  vjson="$(curl -sf -m 15 "$PREVIEW_URL/health/version" || true)"
  echo "health/version: $vjson"
  live_commit="$(echo "$vjson" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("commit",""))' 2>/dev/null || true)"
  live_env="$(echo "$vjson" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("environment",""))' 2>/dev/null || true)"
  if [[ "$live_env" != "full-preview" ]]; then
    echo "FAIL: public IP environment '$live_env' != full-preview (is the full-stack ingress active?)"
    return 1
  fi
  if [[ -n "$PREVIEW_DEPLOY_COMMIT" && "$live_commit" != "$PREVIEW_DEPLOY_COMMIT" ]]; then
    echo "FAIL: public IP commit '$live_commit' != deployed '$PREVIEW_DEPLOY_COMMIT'"
    return 1
  fi
  echo "OK: $PREVIEW_URL serves full-preview at commit ${live_commit:0:8}"
}

usage() {
  cat <<EOF
Usage: $0 [--preflight | --dry-run | --help]

  --preflight  Validate images, values, chart render; do not deploy.
  --dry-run    helm template + helm upgrade --install --dry-run; do not deploy.
  (default)    Requires phrase: $AUTH_PHRASE

Slice namespace $SLICE_NAMESPACE is never modified by this script.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --preflight) MODE="preflight"; shift ;;
    --dry-run) MODE="dry-run"; shift ;;
    --debug-required-spine-only) ESTATE_DEBUG_SET=1; FULL_BOOT_MAX_WAVE=0; shift ;;
    --debug-wave-zero-only) ESTATE_DEBUG_SET=1; FULL_BOOT_MAX_WAVE=0; shift ;;
    --slice|--allow-partial|--no-full-estate) ESTATE_DEBUG_SET=1; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 1 ;;
  esac
done

# Refuse a partial vNext deploy unless an explicit debug/partial flag was passed.
# 'all' => full estate; anything else requires an explicit debug flag.
estate_refuse_partial "$FULL_BOOT_MAX_WAVE" "$ESTATE_DEBUG_SET"

full_boot_ensure_artifacts
resolve_preview_deploy_metadata
# Stable registry tag for preview; commit-scoped tags are opt-in via FULL_BOOT_IMAGE_TAG.
IMAGE_TAG="${FULL_BOOT_IMAGE_TAG:-preview}"

echo "=== Full boot preview ($MODE) ==="
echo "Branch: $PREVIEW_DEPLOY_BRANCH"
echo "Commit: $(git rev-parse --short HEAD)"
echo "Target namespace: $NAMESPACE"
echo "Slice namespace (untouched): $SLICE_NAMESPACE"
echo ""

if [[ ! -f "$VALUES_FILE" ]]; then
  echo "FAIL: missing $VALUES_FILE"
  exit 1
fi

helm_values_args() {
  local args=(-f "$VALUES_FILE")
  # Full estate ('all') => no --max-wave so EVERY runtime microservice is enabled.
  # A numeric wave is only used in explicit debug/partial mode.
  if [[ "$FULL_BOOT_MAX_WAVE" == "all" ]]; then
    node scripts/full-boot/generate-full-preview-runtime-values.mjs >/dev/null
  else
    node scripts/full-boot/generate-full-preview-runtime-values.mjs --max-wave "$FULL_BOOT_MAX_WAVE" >/dev/null
  fi
  node scripts/full-boot/generate-full-preview-bff-downstream-env.mjs >/dev/null
  if [[ -f "$RUNTIME_VALUES" ]]; then
    args+=(-f "$RUNTIME_VALUES")
  else
    echo "WARN: missing $RUNTIME_VALUES"
  fi
  if [[ -f "$BFF_ENV_VALUES" ]]; then
    args+=(-f "$BFF_ENV_VALUES")
  else
    echo "WARN: missing $BFF_ENV_VALUES"
  fi
  printf '%s\0' "${args[@]}"
}

readarray -d '' HELM_VALUE_FILES < <(helm_values_args)
if [[ "$FULL_BOOT_MAX_WAVE" == "all" ]]; then
  echo "Helm values: ${VALUES_FILE} + runtime + BFF downstream env (FULL ESTATE - all runtime microservices)"
else
  echo "Helm values: ${VALUES_FILE} + runtime + BFF downstream env (DEBUG partial: max wave $FULL_BOOT_MAX_WAVE)"
fi

echo "--- Chart integrity ---"
if ! bash scripts/deploy/check-helm-chart-integrity.sh; then
  exit 1
fi

# --- Preflight checks ---
run_preflight() {
  local fail=0
  echo "--- Preflight: namespace ---"
  if kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
    echo "OK: namespace $NAMESPACE exists (will reuse)"
  else
    echo "OK: namespace $NAMESPACE can be created"
    kubectl create namespace "$NAMESPACE" --dry-run=client -o name >/dev/null
  fi

  echo "--- Preflight: slice preservation ---"
  if kubectl get namespace "$SLICE_NAMESPACE" >/dev/null 2>&1; then
    local slice_pods
    slice_pods="$(kubectl get pods -n "$SLICE_NAMESPACE" --no-headers 2>/dev/null | wc -l | tr -d ' ')"
    echo "OK: $SLICE_NAMESPACE present ($slice_pods pods); this script will not helm upgrade it"
  else
    echo "WARN: slice namespace $SLICE_NAMESPACE not found"
  fi

  echo "--- Preflight: required images ---"
  python3 scripts/full-boot/audit-helm-deployability.py >/dev/null
  python3 <<PY
import json, pathlib, subprocess, os, sys
root = pathlib.Path("$REPO_PATH")
cls = __import__("yaml").safe_load((root / "config/full-boot-service-classification.yml").read_text())
required = [e for e in cls["classifications"] if e.get("classification") == "required_full_boot"]
tag = os.environ.get("IMAGE_TAG", "preview")
missing = []
for e in required:
    off = e.get("official_image")
    if off:
        ref = off if ":" in off else f"{off}:latest"
    else:
        img = e.get("image_name") or f"impilo/{e['id']}"
        ref = f"{img}:{tag}"
    r = subprocess.run(["docker", "image", "inspect", ref], capture_output=True)
    if r.returncode != 0:
        missing.append(ref)
if missing:
    print("WARN: missing local docker images (import/build before deploy):")
    for m in missing[:25]:
        print(f"  - {m}")
    if len(missing) > 25:
        print(f"  ... and {len(missing)-25} more")
    sys.exit(0)
print(f"OK: inspected required impilo images with tag {tag}")
PY

  echo "--- Preflight: values & secrets placeholders ---"
  if grep -E '(password|secret|token)\s*:\s*[^p]' "$VALUES_FILE" | grep -viE 'change-me|preview-|impilo-preview' >/dev/null 2>&1; then
    echo "WARN: values may contain non-placeholder secrets — review $VALUES_FILE"
  else
    echo "OK: values use preview placeholders"
  fi

  echo "--- Preflight: helm lint/template ---"
  helm lint "$CHART_DIR" "${HELM_VALUE_FILES[@]}" >/dev/null
  helm template "$RELEASE_NAME" "$CHART_DIR" -n "$NAMESPACE" "${HELM_VALUE_FILES[@]}" \
    --set global.gitBranch="$PREVIEW_DEPLOY_BRANCH" \
    --set global.gitCommit="$PREVIEW_DEPLOY_COMMIT" \
    --set global.buildDate="$PREVIEW_DEPLOY_BUILD_DATE" \
    --set global.imageTag="$IMAGE_TAG" \
    --set images.experienceBff.tag="$IMAGE_TAG" \
    --set images.oneUiShell.tag="$IMAGE_TAG" >/dev/null
  echo "OK: helm lint + template"
  echo "PREFLIGHT_PASS"
}

run_dry_run() {
  run_preflight
  echo "--- Dry-run: helm upgrade --install ---"
  helm upgrade --install "$RELEASE_NAME" "$CHART_DIR" \
    -n "$NAMESPACE" \
    "${HELM_VALUE_FILES[@]}" \
    --create-namespace \
    --dry-run=client \
    --set global.gitBranch="$PREVIEW_DEPLOY_BRANCH" \
    --set global.gitCommit="$PREVIEW_DEPLOY_COMMIT" \
    --set global.buildDate="$PREVIEW_DEPLOY_BUILD_DATE" \
    --set global.imageTag="$IMAGE_TAG" \
    --set images.experienceBff.tag="$IMAGE_TAG" \
    --set images.oneUiShell.tag="$IMAGE_TAG" 2>&1 | tail -20
  echo "DRY_RUN_PASS"
}

case "$MODE" in
  preflight)
    run_preflight
    exit 0
    ;;
  dry-run)
    run_dry_run
    exit 0
    ;;
esac

run_predeploy_gate

read -r -p "Type authorization phrase: " user_auth
if [[ "$user_auth" != "$AUTH_PHRASE" ]]; then
  echo "ABORT: authorization phrase mismatch."
  exit 1
fi

run_preflight || true

# Image build target: full estate by default; required spine only in explicit debug mode.
IMAGE_BUILD_FLAG="--full-estate"
PUSH_MODE="runtime"
if [[ "$ESTATE_DEBUG_SET" == "1" && "$FULL_BOOT_MAX_WAVE" != "all" ]]; then
  IMAGE_BUILD_FLAG="--debug-required-spine-only"
  PUSH_MODE="required"
fi

if [[ "${FULL_BOOT_SKIP_BUILD:-}" == "1" ]]; then
  echo "SKIP: FULL_BOOT_SKIP_BUILD=1 (images already built)"
else
  if ! bash scripts/build/build-full-vnext.sh; then
    echo "ABORT: full build failed for required targets."
    exit 1
  fi
  if ! bash scripts/build/build-full-vnext-images.sh "$IMAGE_BUILD_FLAG"; then
    echo "ABORT: estate image build failed ($IMAGE_BUILD_FLAG)."
    exit 1
  fi
fi

# RUNTIME IMAGE TRUTH: build is not enough. Every built image MUST be pushed to the registry
# k3s pulls from, BEFORE rollout. Skipping this is the stale-pod failure class this doctrine
# exists to prevent.
if [[ "${FULL_BOOT_SKIP_PUSH:-}" == "1" ]]; then
  echo "WARN: FULL_BOOT_SKIP_PUSH=1 - skipping registry push. k3s may serve stale images."
else
  echo "--- Push images to local registry ($PUSH_MODE) [runtime image truth] ---"
  if ! bash scripts/build/push-images-to-local-registry.sh "$PUSH_MODE"; then
    echo "ABORT: registry push failed - rollout would serve stale images."
    exit 1
  fi
fi

if [[ "${FULL_BOOT_SKIP_IMPORT:-}" == "1" ]]; then
  echo "SKIP: FULL_BOOT_SKIP_IMPORT=1 (k3s images already verified)"
  echo "WARN: deploy may use stale containerd layers if import was not run for tag $IMAGE_TAG"
else
  echo "--- Import images into k3s/containerd (mandatory) ---"
  if ! bash scripts/operator/fullboot.sh import-images; then
    echo "ABORT: k3s image import failed."
    echo "  Install helper: sudo bash scripts/operator/install-k3s-image-helper.sh"
    echo "  Then: bash scripts/operator/fullboot.sh import-images"
    exit 1
  fi
  echo "--- Verify image presence in k3s ---"
  if ! bash scripts/operator/fullboot.sh verify-images; then
    echo "ABORT: k3s image verification failed (need IMAGE_PRESENCE PASS, ok=22 fail=0)"
    exit 1
  fi
fi

kubectl create namespace "$NAMESPACE" 2>/dev/null || true

helm upgrade --install "$RELEASE_NAME" "$CHART_DIR" \
  -n "$NAMESPACE" \
  "${HELM_VALUE_FILES[@]}" \
  --set global.gitBranch="$PREVIEW_DEPLOY_BRANCH" \
  --set global.gitCommit="$PREVIEW_DEPLOY_COMMIT" \
  --set global.buildDate="$PREVIEW_DEPLOY_BUILD_DATE" \
  --set global.imageTag="$IMAGE_TAG" \
  --set images.experienceBff.tag="$IMAGE_TAG" \
  --set images.oneUiShell.tag="$IMAGE_TAG" \
  --wait --timeout "${FULL_BOOT_HELM_WAIT_TIMEOUT:-60m}" --atomic=false

kubectl rollout status deployment -n "$NAMESPACE" --timeout=600s || true
bash scripts/test/run-full-boot-smoke-tests.sh

# --- RUNTIME IMAGE TRUTH: prove the running estate, not the deployment story. ---
echo "--- Runtime image truth (digest alignment across the chain) ---"
RUNTIME_TRUTH_OK=1
bash scripts/guard/check-runtime-image-truth.sh || RUNTIME_TRUTH_OK=0

bash scripts/guard/check-full-boot-runtime-completeness.sh || true

# --- BFF/shell image pinning report (Area 10) ---
echo "--- Image pinning report ---"
echo "Pinning: global.imageTag=$IMAGE_TAG; experience-bff and one-ui-shell use tag '$IMAGE_TAG'."
if [[ "$IMAGE_TAG" == "preview" ]]; then
  echo "Pinning mode: mutable ':preview' tag, re-pushed and digest-verified this deploy (not a frozen commit)."
else
  echo "Pinning mode: commit-scoped tag '$IMAGE_TAG' (explicit). Confirm all other services align to the intended target."
fi

# --- UI bundle + BFF behaviour truth (served bundle / changed endpoint, not metadata alone) ---
echo "--- Served UI bundle truth ---"
bash scripts/test/verify-ui-bundle-truth.sh --url "$PREVIEW_URL" || echo "WARN: UI bundle truth check failed."
echo "--- BFF behaviour truth ---"
bash scripts/test/verify-bff-behaviour-truth.sh --url "$PREVIEW_URL" --ns "$NAMESPACE" || echo "WARN: BFF behaviour truth check failed."

# Record which preview generation is now public and assert a single public stack.
bash scripts/operator/report-preview-generation.sh || true

# The whole point of the pipeline: the public IP must now show THIS stack/commit.
verify_public_ip || echo "WARN: public IP verification failed — check ingress cutover."

# --- DEPLOYMENT TRUTH: Helm metadata must not outrun running images. ---
if [[ "$RUNTIME_TRUTH_OK" != "1" ]]; then
  echo ""
  echo "DEPLOYMENT TRUTH FAILURE: Helm metadata is newer than running images. k3s is still serving stale runtime. Push/import/pin images and reroll."
  echo "See reports/full-boot/runtime-image-truth.md for the per-service digest alignment."
  exit 1
fi

echo ""
echo "Full boot deploy finished. Namespace: $NAMESPACE"
echo "All of vNext is vNext: full estate deploy is complete only when runtime image truth passes."
echo "Confirm in a browser: open $PREVIEW_URL (Sign In with any email/password for a CITIZEN preview session)."
