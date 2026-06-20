#!/usr/bin/env bash
# Shared helpers for scripts/preview/* deploy tooling.
set -euo pipefail

REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"

PREVIEW_AUDIT_DIR="${PREVIEW_AUDIT_DIR:-$REPO_PATH/reports/audits}"
PREVIEW_URL="${PREVIEW_URL:-http://41.57.127.235}"
FULL_BOOT_NAMESPACE="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
FULL_BOOT_RELEASE="${FULL_BOOT_RELEASE:-impilo-full-preview}"
CHART_DIR="${CHART_DIR:-deploy/helm/impilo-vnext}"
LOCAL_REGISTRY="${IMPILO_LOCAL_REGISTRY:-127.0.0.1:5000}"

# shellcheck source=scripts/guard/_guard-common.sh
source "$REPO_PATH/scripts/guard/_guard-common.sh"
# shellcheck source=scripts/full-boot/_full-boot-common.sh
source "$REPO_PATH/scripts/full-boot/_full-boot-common.sh"

preview_now() {
  date -u +%Y-%m-%dT%H:%M:%SZ
}

preview_ensure_audit_dir() {
  mkdir -p "$PREVIEW_AUDIT_DIR"
}

preview_head_sha() {
  git rev-parse HEAD 2>/dev/null || echo unknown
}

preview_short_sha() {
  git rev-parse --short HEAD 2>/dev/null || echo unknown
}

preview_branch() {
  git branch --show-current 2>/dev/null || echo unknown
}

preview_dirty_files() {
  git status --porcelain --untracked-files=no 2>/dev/null || true
}

preview_is_dirty() {
  [[ -n "$(preview_dirty_files)" ]]
}

preview_resolve_base_ref() {
  local explicit="${1:-}"
  if [[ -n "$explicit" ]]; then
    if git rev-parse --verify "${explicit}^{commit}" >/dev/null 2>&1; then
      echo "$explicit"
      return
    fi
    echo "ERROR: invalid base ref: $explicit" >&2
    return 1
  fi
  resolve_base_ref
}

preview_changed_files() {
  local base="$1"
  git diff --name-only "${base}...HEAD" 2>/dev/null || git diff --name-only "$base" HEAD 2>/dev/null || true
}

preview_check_kube() {
  if ! command -v kubectl >/dev/null 2>&1; then
    echo "FAIL: kubectl not found" >&2
    return 1
  fi
  if ! kubectl cluster-info >/dev/null 2>&1; then
    echo "FAIL: kubectl cannot reach cluster (check kube context)" >&2
    return 1
  fi
  echo "OK: kubectl cluster reachable"
  return 0
}

preview_check_registry() {
  if ! curl -sf "http://${LOCAL_REGISTRY}/v2/" >/dev/null 2>&1; then
    echo "WARN: local registry $LOCAL_REGISTRY not reachable — run: bash scripts/operator/registry-up.sh" >&2
    return 1
  fi
  echo "OK: local registry $LOCAL_REGISTRY reachable"
  return 0
}

preview_check_helm_release() {
  if helm status "$FULL_BOOT_RELEASE" -n "$FULL_BOOT_NAMESPACE" >/dev/null 2>&1; then
    local rev
    rev="$(helm history "$FULL_BOOT_RELEASE" -n "$FULL_BOOT_NAMESPACE" --max 1 -o json 2>/dev/null \
      | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d[0]["revision"] if d else "?")' 2>/dev/null || echo "?")"
    echo "OK: Helm release $FULL_BOOT_RELEASE revision $rev"
    return 0
  fi
  echo "WARN: Helm release $FULL_BOOT_RELEASE not found in $FULL_BOOT_NAMESPACE (first deploy needs full boot)" >&2
  return 1
}

preview_preflight_common() {
  local allow_dirty="${1:-0}"
  local require_release="${2:-0}"
  local fail=0

  echo "=== Preview preflight ==="
  echo "Branch: $(preview_branch)"
  echo "HEAD: $(preview_short_sha)"
  echo "Repo: $REPO_PATH"
  echo ""

  if preview_is_dirty; then
    if [[ "$allow_dirty" == "1" ]]; then
      echo "WARN: working tree is dirty (TARGETED_ALLOW_DIRTY=1)"
    else
      echo "FAIL: working tree is dirty — commit or stash before deploy" >&2
      echo "  Override: TARGETED_ALLOW_DIRTY=1 (targeted only, not recommended)" >&2
      fail=1
    fi
  else
    echo "OK: clean working tree"
  fi

  preview_check_kube || fail=1
  preview_check_registry || true
  if [[ "$require_release" == "1" ]]; then
    preview_check_helm_release || fail=1
  else
    preview_check_helm_release || true
  fi

  return "$fail"
}

preview_fetch_health_version() {
  local url="${1:-$PREVIEW_URL}"
  curl -sf -m 15 "${url%/}/health/version" 2>/dev/null || echo '{}'
}

preview_vm_gates_valid_for_head() {
  local vm_report="$REPO_PATH/reports/pipeline/latest-summary.json"
  local head
  head="$(preview_head_sha)"
  [[ -f "$vm_report" ]] || return 1
  python3 -c "
import json, sys
d = json.load(open('$vm_report'))
ok = d.get('commit') == sys.argv[1] and d.get('vm_pipeline_passed') and not d.get('blocking_failure')
sys.exit(0 if ok else 1)
" "$head" 2>/dev/null
}

preview_write_deploy_report() {
  local report_path="$1"
  local status="$2"   # PASS or FAIL
  local kind="$3"     # full-boot | targeted | explain
  local blast_json="${4:-}"
  local extra_md="${5:-}"

  preview_ensure_audit_dir
  local branch head short_sha preview_url
  branch="$(preview_branch)"
  head="$(preview_head_sha)"
  short_sha="$(preview_short_sha)"
  preview_url="$PREVIEW_URL"

  local helm_rev="n/a"
  if helm status "$FULL_BOOT_RELEASE" -n "$FULL_BOOT_NAMESPACE" >/dev/null 2>&1; then
    helm_rev="$(helm history "$FULL_BOOT_RELEASE" -n "$FULL_BOOT_NAMESPACE" --max 1 -o json 2>/dev/null \
      | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d[0]["revision"] if d else "n/a")' 2>/dev/null || echo n/a)"
  fi

  local health_json live_commit live_env
  health_json="$(preview_fetch_health_version)"
  live_commit="$(echo "$health_json" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("commit",""))' 2>/dev/null || true)"
  live_env="$(echo "$health_json" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("environment",""))' 2>/dev/null || true)"

  local readiness="n/a"
  if kubectl get namespace "$FULL_BOOT_NAMESPACE" >/dev/null 2>&1; then
    local ready total
    ready="$(kubectl get deploy -n "$FULL_BOOT_NAMESPACE" -o json 2>/dev/null | python3 -c "
import json,sys
d=json.load(sys.stdin)
ok=sum(1 for x in d.get('items',[]) if x.get('status',{}).get('readyReplicas',0)>=1)
print(ok)
" 2>/dev/null || echo 0)"
    total="$(kubectl get deploy -n "$FULL_BOOT_NAMESPACE" --no-headers 2>/dev/null | wc -l | tr -d ' ')"
    readiness="${ready}/${total}"
  fi

  {
    echo "# Preview Deploy Report — $kind"
    echo ""
    echo "- **Generated:** $(preview_now)"
    echo "- **Status:** $status"
    echo "- **Branch:** $branch"
    echo "- **HEAD commit:** \`$head\` ($short_sha)"
    echo "- **Preview URL:** $preview_url"
    echo "- **Namespace:** $FULL_BOOT_NAMESPACE"
    echo "- **Helm release:** $FULL_BOOT_RELEASE (revision $helm_rev)"
    echo "- **Runtime readiness:** $readiness deployments ready"
    echo "- **Public /health/version environment:** $live_env"
    echo "- **Public /health/version commit:** \`${live_commit:-n/a}\`"
    if [[ -n "$live_commit" && "$live_commit" == "$head" ]]; then
      echo "- **Commit alignment:** OK"
    elif [[ -n "$live_commit" ]]; then
      echo "- **Commit alignment:** MISMATCH (live ${live_commit:0:8} vs HEAD ${short_sha})"
    fi
    echo ""
    if [[ -n "$blast_json" && -f "$blast_json" ]]; then
      echo "## Blast radius"
      echo ""
      python3 - "$blast_json" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
for k in ("change_class", "change_classes", "full_boot_required", "targeted_deploy_allowed",
          "direct_services", "expanded_services", "images_to_build", "pipeline_only"):
    v = d.get(k)
    if v is not None:
        print(f"- **{k}:** {v}")
if d.get("refusal_reasons"):
    print("- **refusal_reasons:**")
    for r in d["refusal_reasons"]:
        print(f"  - {r}")
if d.get("rationale"):
    print("- **rationale:**")
    for r in d["rationale"]:
        print(f"  - {r}")
PY
      echo ""
    fi
    if [[ -n "$extra_md" ]]; then
      echo "$extra_md"
      echo ""
    fi
    echo "## health/version raw"
    echo ""
    echo '```json'
    echo "$health_json"
    echo '```'
  } >"$report_path"

  echo "Report: $report_path"
}

preview_run_blast_resolver() {
  node "$REPO_PATH/scripts/preview/resolve-blast-radius.mjs" "$@"
}

# --- k3s image import preflight (no silent skip on execute paths) ---

preview_k3s_import_helper_ready() {
  [[ -x /usr/local/sbin/impilo-k3s-import-images && -x /usr/local/sbin/impilo-k3s-list-images ]] \
    && sudo -n /usr/local/sbin/impilo-k3s-list-images "${FULL_BOOT_IMAGE_TAG:-preview}" "$REPO_PATH" >/dev/null 2>&1
}

preview_k3s_import_preflight() {
  local mode="${1:-report}"  # report | require
  local image_tag="${2:-${FULL_BOOT_IMAGE_TAG:-preview}}"
  local import_mode="passwordless-helper"

  echo "=== k3s import preflight ==="
  if preview_k3s_import_helper_ready; then
    echo "OK: passwordless k3s import helper ready (mode=$import_mode)"
    echo "K3S_IMPORT_MODE=$import_mode"
    return 0
  fi

  if [[ -x /usr/local/sbin/impilo-k3s-import-images ]]; then
    echo "FAIL: k3s import helper installed but passwordless sudo is not configured" >&2
    echo "  Install/configure: sudo bash scripts/operator/install-k3s-image-helper.sh" >&2
    echo "  Or product-owner checkpoint: bash scripts/operator/fullboot.sh sudo-checkpoint-run" >&2
  else
    echo "FAIL: k3s import helper missing at /usr/local/sbin/impilo-k3s-import-images" >&2
    echo "  Install: sudo bash scripts/operator/install-k3s-image-helper.sh" >&2
  fi

  if preview_check_registry; then
    import_mode="registry-pull"
    echo ""
    echo "Fallback path: registry-pull (cluster pulls from ${LOCAL_REGISTRY} at rollout)"
    echo "  Requires digest pinning + runtime-image-truth PASS after deploy."
    echo "  To proceed without passwordless import: export PREVIEW_K3S_IMPORT_MODE=registry-pull"
    if [[ "${PREVIEW_K3S_IMPORT_MODE:-}" == "registry-pull" ]]; then
      echo "OK: PREVIEW_K3S_IMPORT_MODE=registry-pull acknowledged (mode=$import_mode)"
      echo "K3S_IMPORT_MODE=$import_mode"
      return 0
    fi
  fi

  if [[ "$mode" == "require" ]]; then
    echo "FAIL: k3s import required but helper unavailable and registry-pull not acknowledged" >&2
    return 1
  fi

  echo "WARN: k3s import helper unavailable (dry-run/report only)"
  echo "K3S_IMPORT_MODE=unavailable"
  return 1
}

preview_run_k3s_import() {
  local image_tag="${1:-${FULL_BOOT_IMAGE_TAG:-preview}}"
  local images_csv="${2:-}"
  local import_mode="${PREVIEW_K3S_IMPORT_MODE:-}"

  if preview_k3s_import_helper_ready; then
    echo "--- k3s import (mode=passwordless-helper) ---"
    if [[ -n "$images_csv" ]]; then
      sudo -n /usr/local/sbin/impilo-k3s-import-images "$image_tag" "$REPO_PATH" \
        --runtime-only --only "$images_csv" --force
    else
      sudo -n /usr/local/sbin/impilo-k3s-import-images "$image_tag" "$REPO_PATH"
    fi
    return $?
  fi

  if [[ "$import_mode" == "registry-pull" ]]; then
    echo "--- k3s import skipped (mode=registry-pull; relying on registry pull at rollout) ---"
    preview_check_registry || return 1
    return 0
  fi

  echo "FAIL: k3s import required but no viable path (helper missing; PREVIEW_K3S_IMPORT_MODE not set)" >&2
  preview_k3s_import_preflight require "$image_tag" || true
  return 1
}
