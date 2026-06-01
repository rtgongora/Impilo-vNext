#!/usr/bin/env bash
# Full boot preview deploy — impilo-full-preview only; does not modify impilo-preview slice.
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"
source scripts/full-boot/_full-boot-common.sh
source scripts/deploy/_preview-deploy-metadata.sh

AUTH_PHRASE="AUTHORIZE FULL BOOT PREVIEW DEPLOY"
NAMESPACE="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
CHART_DIR="deploy/helm/impilo-vnext"
VALUES_FILE="$CHART_DIR/values-full-preview.yaml"
RELEASE_NAME="impilo-full-preview"
MODE="deploy"

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
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 1 ;;
  esac
done

full_boot_ensure_artifacts
resolve_preview_deploy_metadata
IMAGE_TAG="${FULL_BOOT_IMAGE_TAG:-$(full_boot_image_tag)}"

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
  helm lint "$CHART_DIR" -f "$VALUES_FILE" >/dev/null
  helm template "$RELEASE_NAME" "$CHART_DIR" -n "$NAMESPACE" -f "$VALUES_FILE" \
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
    -f "$VALUES_FILE" \
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

read -r -p "Type authorization phrase: " user_auth
if [[ "$user_auth" != "$AUTH_PHRASE" ]]; then
  echo "ABORT: authorization phrase mismatch."
  exit 1
fi

run_preflight || true

if ! bash scripts/build/build-full-vnext.sh; then
  echo "ABORT: full build failed for required targets."
  exit 1
fi

if ! bash scripts/build/build-full-vnext-images.sh --required-only; then
  echo "ABORT: required image build failed."
  exit 1
fi

bash scripts/dev/import-full-vnext-images-k3s.sh "$IMAGE_TAG" || true

kubectl create namespace "$NAMESPACE" 2>/dev/null || true

helm upgrade --install "$RELEASE_NAME" "$CHART_DIR" \
  -n "$NAMESPACE" \
  -f "$VALUES_FILE" \
  --set global.gitBranch="$PREVIEW_DEPLOY_BRANCH" \
  --set global.gitCommit="$PREVIEW_DEPLOY_COMMIT" \
  --set global.buildDate="$PREVIEW_DEPLOY_BUILD_DATE" \
  --set global.imageTag="$IMAGE_TAG" \
  --set images.experienceBff.tag="$IMAGE_TAG" \
  --set images.oneUiShell.tag="$IMAGE_TAG" \
  --wait --timeout 25m

kubectl rollout status deployment -n "$NAMESPACE" --timeout=600s || true
bash scripts/test/run-full-boot-smoke-tests.sh
bash scripts/guard/check-full-boot-runtime-completeness.sh || true

echo "Full boot deploy finished. Namespace: $NAMESPACE"
