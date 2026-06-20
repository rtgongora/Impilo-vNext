#!/usr/bin/env bash
# Targeted preview deploy — affected services only when blast-radius class permits.
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"
source scripts/preview/_preview-common.sh
source scripts/deploy/_preview-deploy-metadata.sh
source scripts/full-boot/_full-boot-common.sh

BASE_REF=""
FILES_CSV=""
MODE="dry-run"
FORCE_GATES=0
AUTH_PHRASE="AUTHORIZE TARGETED PREVIEW DEPLOY"
ALLOW_DIRTY="${TARGETED_ALLOW_DIRTY:-0}"

NAMESPACE="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
RELEASE_NAME="${FULL_BOOT_RELEASE:-impilo-full-preview}"
CHART_DIR="${CHART_DIR:-deploy/helm/impilo-vnext}"
VALUES_FILE="$CHART_DIR/values-full-preview.yaml"
RUNTIME_VALUES="$CHART_DIR/values-full-preview-runtime.generated.yaml"
BFF_ENV_VALUES="$CHART_DIR/values-full-preview-bff-env.generated.yaml"
DIGESTS_VALUES="$CHART_DIR/values-full-preview-digests.generated.yaml"
IMAGE_TAG="${FULL_BOOT_IMAGE_TAG:-preview}"

usage() {
  cat <<EOF
Usage: $0 [--dry-run | --execute] [--base REF] [--force-gates] [--files a,b]

  --dry-run   (default) Show plan only; no build or deploy
  --execute   Build/deploy affected services (requires phrase: $AUTH_PHRASE)

Refuses when blast-radius class requires full boot — use scripts/preview/full-boot.sh instead.

Environment:
  TARGETED_ALLOW_DIRTY=1   Allow deploy with dirty working tree (not recommended)
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) MODE="dry-run"; shift ;;
    --execute) MODE="execute"; shift ;;
    --base) BASE_REF="${2:-}"; shift 2 ;;
    --files) FILES_CSV="${2:-}"; shift 2 ;;
    --force-gates) FORCE_GATES=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 2 ;;
  esac
done

SHORT_SHA="$(preview_short_sha)"
REPORT_PATH="$PREVIEW_AUDIT_DIR/preview-targeted-deploy-${SHORT_SHA}.md"

echo "=== Targeted Preview Deploy ($MODE) ==="

if [[ "$MODE" == "dry-run" ]]; then
  preview_ensure_audit_dir
  echo "Branch: $(preview_branch)"
  echo "HEAD: $(preview_short_sha)"
  preview_is_dirty && echo "WARN: working tree is dirty (dry-run continues)"
  preview_check_kube || echo "WARN: kubectl not reachable (dry-run continues)"
  preview_check_registry || true
  preview_check_helm_release || echo "WARN: Helm release not found (first deploy needs full boot)"
else
  preview_preflight_common "$ALLOW_DIRTY" 1 || {
    preview_write_deploy_report "$REPORT_PATH" "FAIL" "targeted" "" "Preflight failed."
    exit 1
  }
fi

BASE="$(preview_resolve_base_ref "$BASE_REF")"
RESOLVER_ARGS=(--base "$BASE")
[[ -n "$FILES_CSV" ]] && RESOLVER_ARGS+=(--files "$FILES_CSV")

BLAST_JSON="$(preview_run_blast_resolver "${RESOLVER_ARGS[@]}")"
if [[ ! -f "$BLAST_JSON" ]]; then
  echo "FAIL: blast radius resolver failed" >&2
  exit 1
fi

python3 - "$BLAST_JSON" <<'PY' || REFUSE=1
import json, sys
d = json.load(open(sys.argv[1]))
if d.get("full_boot_required") or not d.get("targeted_deploy_allowed"):
    print("REFUSE: targeted deploy not allowed for this change set.")
    for r in d.get("refusal_reasons", []):
        print(f"  - {r}")
    if not d.get("refusal_reasons"):
        print(f"  - Change class {d.get('change_class')} requires full boot")
    print("")
    print("Use: bash scripts/preview/full-boot.sh")
    sys.exit(2)
imgs = d.get("images_to_build") or []
if not imgs and d.get("change_class") != "A":
    print("REFUSE: no images to build and not docs-only")
    sys.exit(2)
sys.exit(0)
PY

if [[ "${REFUSE:-0}" == "1" ]]; then
  preview_write_deploy_report "$REPORT_PATH" "REFUSED" "targeted" "$BLAST_JSON" "Targeted deploy refused — use full boot."
  exit 2
fi

IMAGES_CSV="$(python3 -c "import json; print(','.join(json.load(open('$BLAST_JSON')).get('images_to_build',[])))")"
PIPELINE_ONLY="$(python3 -c "import json; print(json.load(open('$BLAST_JSON')).get('pipeline_only',''))")"

echo "Affected images: ${IMAGES_CSV:-none}"
echo "Pipeline phases: $PIPELINE_ONLY"
echo ""

if [[ "$MODE" == "dry-run" ]]; then
  bash scripts/preview/explain-blast-radius.sh --base "$BASE" ${FILES_CSV:+--files "$FILES_CSV"}
  preview_write_deploy_report "$REPORT_PATH" "DRY-RUN" "targeted" "$BLAST_JSON" "Dry-run only — no build or deploy."
  echo ""
  echo "DRY-RUN PASS: use --execute to deploy (phrase: $AUTH_PHRASE)"
  exit 0
fi

# Authorization
read -r -p "Type authorization phrase: " user_auth
if [[ "$user_auth" != "$AUTH_PHRASE" ]]; then
  echo "ABORT: authorization phrase mismatch."
  preview_write_deploy_report "$REPORT_PATH" "FAIL" "targeted" "$BLAST_JSON" "Authorization phrase mismatch."
  exit 1
fi

# Quality gates (subset)
if [[ "$FORCE_GATES" == "1" ]]; then
  echo "--- Running full quality gates (--force-gates) ---"
  if ! bash scripts/pipeline/run-local-quality-gates.sh; then
    preview_write_deploy_report "$REPORT_PATH" "FAIL" "targeted" "$BLAST_JSON" "Quality gates failed."
    exit 1
  fi
else
  echo "--- Running targeted quality gates: PIPELINE_ONLY=$PIPELINE_ONLY ---"
  if ! PIPELINE_ONLY="$PIPELINE_ONLY" bash scripts/pipeline/run-local-quality-gates.sh; then
    preview_write_deploy_report "$REPORT_PATH" "FAIL" "targeted" "$BLAST_JSON" "Targeted quality gates failed."
    exit 1
  fi
fi

if [[ -z "$IMAGES_CSV" ]]; then
  echo "OK: docs-only change — no deploy required"
  preview_write_deploy_report "$REPORT_PATH" "PASS" "targeted" "$BLAST_JSON" "Docs-only: no images deployed."
  exit 0
fi

full_boot_ensure_artifacts
resolve_preview_deploy_metadata

# Compile affected modules
echo "--- Compile affected modules ---"
IFS=',' read -ra IMG_IDS <<<"$IMAGES_CSV"
MODULES=()
for id in "${IMG_IDS[@]}"; do
  [[ -z "$id" ]] && continue
  case "$id" in
    one-ui-shell) ;;
    experience-bff) MODULES+=("experience-bff") ;;
    *) MODULES+=("$id") ;;
  esac
done

if [[ ${#MODULES[@]} -gt 0 ]]; then
  if ! bash scripts/build/build-full-vnext.sh --only-modules "$(IFS=,; echo "${MODULES[*]}")"; then
    preview_write_deploy_report "$REPORT_PATH" "FAIL" "targeted" "$BLAST_JSON" "Targeted compile failed."
    exit 1
  fi
fi
if [[ "$IMAGES_CSV" == *"one-ui-shell"* ]]; then
  if ! (cd ui/one-ui-shell && npm run build); then
    preview_write_deploy_report "$REPORT_PATH" "FAIL" "targeted" "$BLAST_JSON" "one-ui-shell build failed."
    exit 1
  fi
fi

# Build images
echo "--- Build images (targeted) ---"
for id in "${IMG_IDS[@]}"; do
  [[ -z "$id" ]] && continue
  if ! bash scripts/build/build-full-vnext-images.sh --only "$id"; then
    preview_write_deploy_report "$REPORT_PATH" "FAIL" "targeted" "$BLAST_JSON" "Image build failed: $id"
    exit 1
  fi
done

# Push (force fresh layers)
echo "--- Push images to registry ---"
bash scripts/operator/registry-up.sh || true
export IMPILO_PUSH_ONLY="$IMAGES_CSV"
export IMPILO_PUSH_FORCE=1
if ! bash scripts/build/push-images-to-local-registry.sh runtime; then
  preview_write_deploy_report "$REPORT_PATH" "FAIL" "targeted" "$BLAST_JSON" "Registry push failed."
  exit 1
fi

# Digest pin (merge affected only)
echo "--- Resolve digests (targeted merge) ---"
if ! bash scripts/full-boot/resolve-image-digests.sh --tag "$IMAGE_TAG" --only "$IMAGES_CSV"; then
  echo "WARN: digest resolve incomplete — continuing with available pins"
fi

# k3s import
echo "--- Import images into k3s ---"
if [[ -x /usr/local/sbin/impilo-k3s-import-images ]] && sudo -n true 2>/dev/null; then
  if ! sudo -n /usr/local/sbin/impilo-k3s-import-images "$IMAGE_TAG" "$REPO_PATH" \
    --runtime-only --only "$IMAGES_CSV" --force; then
    preview_write_deploy_report "$REPORT_PATH" "FAIL" "targeted" "$BLAST_JSON" "k3s import failed."
    exit 1
  fi
else
  echo "WARN: passwordless k3s import helper unavailable — run: bash scripts/operator/fullboot.sh import-images"
fi

# Regenerate helm values if needed
node scripts/full-boot/generate-full-preview-runtime-values.mjs >/dev/null
node scripts/full-boot/generate-full-preview-bff-downstream-env.mjs >/dev/null

HELM_ARGS=(-f "$VALUES_FILE")
[[ -f "$RUNTIME_VALUES" ]] && HELM_ARGS+=(-f "$RUNTIME_VALUES")
[[ -f "$BFF_ENV_VALUES" ]] && HELM_ARGS+=(-f "$BFF_ENV_VALUES")
[[ -f "$DIGESTS_VALUES" ]] && HELM_ARGS+=(-f "$DIGESTS_VALUES")

echo "--- Helm upgrade (targeted digest update) ---"
EXTRA=()
[[ -f "$DIGESTS_VALUES" ]] && EXTRA+=(--set global.imagePullPolicy=Always)

helm upgrade --install "$RELEASE_NAME" "$CHART_DIR" \
  -n "$NAMESPACE" \
  "${HELM_ARGS[@]}" \
  "${EXTRA[@]}" \
  --set global.gitBranch="$PREVIEW_DEPLOY_BRANCH" \
  --set global.gitCommit="$PREVIEW_DEPLOY_COMMIT" \
  --set global.buildDate="$PREVIEW_DEPLOY_BUILD_DATE" \
  --set global.imageTag="$IMAGE_TAG" \
  --set images.experienceBff.tag="$IMAGE_TAG" \
  --set images.oneUiShell.tag="$IMAGE_TAG" \
  --wait --timeout "${TARGETED_HELM_WAIT_TIMEOUT:-20m}" --atomic=false

# Rollout status for affected deployments only
echo "--- Rollout status (affected) ---"
ROLLOUT_FAIL=0
for id in "${IMG_IDS[@]}"; do
  [[ -z "$id" ]] && continue
  if kubectl get deploy "$id" -n "$NAMESPACE" >/dev/null 2>&1; then
    if ! kubectl rollout status "deployment/$id" -n "$NAMESPACE" --timeout="${TARGETED_ROLLOUT_TIMEOUT:-10m}"; then
      echo "FAIL: rollout $id" >&2
      ROLLOUT_FAIL=1
    fi
  else
    echo "WARN: deployment $id not found in $NAMESPACE"
  fi
done

# Validation
echo "--- Runtime image truth (targeted) ---"
TRUTH_OK=1
bash scripts/guard/check-runtime-image-truth.sh --only "$IMAGES_CSV" --ns "$NAMESPACE" || TRUTH_OK=0

if [[ "$IMAGES_CSV" == *"one-ui-shell"* ]]; then
  bash scripts/test/verify-ui-bundle-truth.sh --url "$PREVIEW_URL" || echo "WARN: UI bundle truth failed"
fi
if [[ "$IMAGES_CSV" == *"experience-bff"* ]]; then
  bash scripts/test/verify-bff-behaviour-truth.sh --url "$PREVIEW_URL" --ns "$NAMESPACE" || echo "WARN: BFF behaviour truth failed"
fi

HEALTH="$(preview_fetch_health_version)"
LIVE_COMMIT="$(echo "$HEALTH" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("commit",""))' 2>/dev/null || true)"
COMMIT_OK=0
[[ "$LIVE_COMMIT" == "$(preview_head_sha)" ]] && COMMIT_OK=1

STATUS="PASS"
[[ "$ROLLOUT_FAIL" != "0" || "$TRUTH_OK" != "1" || "$COMMIT_OK" != "1" ]] && STATUS="FAIL"

EXTRA_MD="**Note:** Targeted deploy does not assert FULL_ESTATE_PASS. Run \`scripts/preview/full-boot.sh\` before release promotion."
preview_write_deploy_report "$REPORT_PATH" "$STATUS" "targeted" "$BLAST_JSON" "$EXTRA_MD"

echo ""
echo "=== Final: $STATUS ==="
echo "Branch: $(preview_branch)"
echo "HEAD: $SHORT_SHA"
echo "Affected: $IMAGES_CSV"
echo "Preview URL: $PREVIEW_URL"
echo "Report: $REPORT_PATH"

[[ "$STATUS" == "PASS" ]] || exit 1
exit 0
