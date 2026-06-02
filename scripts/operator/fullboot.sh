#!/usr/bin/env bash
# Full boot operator workflow — Cursor owns orchestration; product owner owns authorization.
# Human sudo checkpoint: one terminal block + password, then "sudo checkpoint completed".
set -euo pipefail

REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO"
export REPO_PATH="$REPO"

# shellcheck source=scripts/operator/fullboot-lib.sh
source "$REPO/scripts/operator/fullboot-lib.sh"

TAG="$(fb_tag)"
CMD="${1:-help}"
DEPLOY_PHRASE="AUTHORIZE FULL BOOT PREVIEW DEPLOY"
CHECKPOINT_EXIT=2

usage() {
  cat <<EOF
Usage: bash scripts/operator/fullboot.sh <command>

Commands:
  help                  This help
  status                Slice + full-boot namespace + helper + checkpoint state
  prepare               Chart integrity + preflight + dry-run (no deploy)
  verify-images         Verify 22 required images in k3s (no deploy)
  import-images         Import images (passwordless helper, or human sudo checkpoint)
  deploy                Orchestrate toward deploy (stops at sudo checkpoint or deploy auth)
  continue              Resume after successful sudo checkpoint (Cursor runs this)
  retry                 Re-attempt last workflow step from state
  report                Summary of verify/checkpoint/workflow logs
  sudo-checkpoint-run   Product owner: run pending privileged checkpoint only
  sudo-checkpoint-status  Show pending checkpoint and last result

Human sudo checkpoint (when passwordless helper unavailable):
  1. Cursor runs deploy/import-images → writes reports/full-boot/sudo-checkpoint.*
  2. Product owner runs ONE ssh block + sudo-checkpoint-run (password once)
  3. Product owner tells Cursor: sudo checkpoint completed
  4. Cursor runs: bash scripts/operator/fullboot.sh continue

Deploy authorization (separate from sudo):
  export FULLBOOT_DEPLOY_AUTHORIZED=1
  printf '%s\n' '$DEPLOY_PHRASE' | bash scripts/operator/fullboot.sh deploy

Environment:
  FULL_BOOT_IMAGE_TAG          (default: preview)
  FULL_BOOT_SKIP_BUILD         (set 1 to skip image rebuild on deploy)
  FULLBOOT_DEPLOY_AUTHORIZED   (set 1 with phrase on stdin to run Helm deploy)

Protected namespace: impilo-preview (never modified by this script)
Full boot namespace: impilo-full-preview
EOF
}

fb_deploy_authorized() {
  [[ "${FULLBOOT_DEPLOY_AUTHORIZED:-}" == "1" ]] || return 1
  if [[ -t 0 ]]; then
    return 1
  fi
  local phrase
  phrase="$(head -n1)"
  [[ "$phrase" == "$DEPLOY_PHRASE" ]]
}

fb_ensure_images_ready() {
  local log
  log="$(fb_reports)/k3s-image-verify-preview.log"
  echo "--- verify-images (skip import if already PASS) ---"
  if fb_verify_images; then
    if fb_verify_passed "$log"; then
      echo "OK: images already present (IMAGE_PRESENCE: PASS, ok=22 fail=0)"
      return 0
    fi
  fi
  return 1
}

fb_try_import_without_checkpoint() {
  if fb_helper_passwordless; then
    echo "--- import-images (passwordless helper) ---"
    local log="$(fb_reports)/k3s-image-import-helper.log"
    fb_import_passwordless 2>&1 | tee "$log"
    return 0
  fi
  if [[ -x /usr/local/sbin/impilo-k3s-import-images ]]; then
    echo "Helper installed but passwordless sudo not active — human sudo checkpoint required."
    return 1
  fi
  echo "Narrow k3s helper not installed — human sudo checkpoint or one-time install required."
  return 1
}

fb_request_import_checkpoint() {
  local action="import_full_boot_images_to_k3s"
  local marker="IMAGE_PRESENCE: PASS and SUMMARY ok=22 fail=0"
  local log="$(fb_reports)/sudo-checkpoint-run.log"
  local vm_cmd="cd $REPO && git pull && sudo -v && bash scripts/operator/fullboot.sh sudo-checkpoint-run"
  fb_write_checkpoint "$action" "$marker" "$vm_cmd" "$log" "verify_images_and_prepare"
  fb_print_product_owner_block "$action"
  exit "$CHECKPOINT_EXIT"
}

fb_import_images_flow() {
  fb_ensure_reports
  if fb_ensure_images_ready; then
    return 0
  fi
  if fb_try_import_without_checkpoint; then
    fb_verify_images || true
    if fb_verify_passed "$(fb_reports)/k3s-image-verify-preview.log"; then
      return 0
    fi
  fi
  # Valid previous checkpoint result?
  if [[ -f "$(fb_checkpoint_result_json)" ]]; then
    if python3 -c "import json; exit(0 if json.load(open('$(fb_checkpoint_result_json)')).get('success') else 1)" 2>/dev/null; then
      fb_verify_images || true
      if fb_verify_passed "$(fb_reports)/k3s-image-verify-preview.log"; then
        echo "OK: prior sudo checkpoint result still valid"
        return 0
      fi
    fi
  fi
  fb_request_import_checkpoint
}

fb_cleanup_full_boot_namespace() {
  echo "--- cleanup impilo-full-preview (non-sudo kubectl) ---"
  echo "impilo-preview is NOT modified."
  helm uninstall impilo-full-preview -n "$IMPILO_FULLBOOT_NS" 2>/dev/null || true
  kubectl delete namespace "$IMPILO_FULLBOOT_NS" --ignore-not-found --wait=false 2>/dev/null || true
  echo "OK: cleanup requested (namespace may terminate in background)"
}

fb_run_prepare() {
  export FULL_BOOT_IMAGE_TAG="$TAG"
  bash scripts/deploy/check-helm-chart-integrity.sh
  bash scripts/deploy/full-boot-preview-deploy.sh --preflight
  bash scripts/deploy/full-boot-preview-deploy.sh --dry-run
  bash scripts/guard/check-full-boot-runtime-completeness.sh || true
}

fb_print_deploy_auth_block() {
  cat <<EOF

================================================================================
DEPLOY AUTHORIZATION REQUIRED (separate from sudo)
================================================================================

Sudo consent does NOT authorize Helm deploy. When images are verified and you
are ready to deploy into impilo-full-preview only:

  export FULL_BOOT_IMAGE_TAG=$TAG
  export FULL_BOOT_SKIP_BUILD=1
  unset FULL_BOOT_SKIP_IMPORT
  export FULLBOOT_DEPLOY_AUTHORIZED=1
  printf '%s\n' '$DEPLOY_PHRASE' | bash scripts/operator/fullboot.sh deploy

Or tell Cursor to deploy with phrase: $DEPLOY_PHRASE

impilo-preview remains untouched.
================================================================================
EOF
}

fb_run_deploy_if_authorized() {
  if ! fb_deploy_authorized; then
    fb_workflow_update "awaiting_deploy_auth" "run_deploy_with_phrase"
    fb_print_deploy_auth_block
    return 0
  fi
  echo "--- authorized full boot deploy ---"
  export FULL_BOOT_IMAGE_TAG="$TAG"
  export FULL_BOOT_SKIP_BUILD="${FULL_BOOT_SKIP_BUILD:-1}"
  unset FULL_BOOT_SKIP_IMPORT || true
  fb_cleanup_full_boot_namespace
  printf '%s\n' "$DEPLOY_PHRASE" | bash scripts/deploy/full-boot-preview-deploy.sh
  fb_workflow_update "deploy_completed"
}

fb_continue_workflow() {
  fb_ensure_reports
  local cp_json="$(fb_checkpoint_json)"
  local res_json="$(fb_checkpoint_result_json)"

  if [[ -f "$cp_json" ]] && [[ ! -f "$res_json" ]]; then
    echo "FAIL: sudo checkpoint pending — product owner must run sudo-checkpoint-run first."
    echo "See: $(fb_checkpoint_md)"
    fb_print_product_owner_block "$(python3 -c "import json; print(json.load(open('$cp_json'))['required_action'])")"
    exit 1
  fi

  if [[ -f "$res_json" ]]; then
    if ! python3 -c "import json; exit(0 if json.load(open('$res_json')).get('success') else 1)" 2>/dev/null; then
      echo "FAIL: last sudo checkpoint did not succeed."
      cat "$(fb_checkpoint_result_md)" 2>/dev/null || true
      exit 1
    fi
    local log
    log="$(python3 -c "import json; print(json.load(open('$res_json')).get('log_file_path',''))")"
    local action
    action="$(python3 -c "import json; print(json.load(open('$res_json')).get('required_action',''))")"
    if ! fb_checkpoint_success "$action" "$log"; then
      echo "FAIL: success marker not found in checkpoint log."
      echo "Expected: IMAGE_PRESENCE: PASS and SUMMARY ok=22 fail=0 (for import/list)"
      exit 1
    fi
    echo "OK: sudo checkpoint succeeded ($action)"
    fb_clear_checkpoint_pending
  fi

  echo "--- continue: verify images ---"
  fb_verify_images || true
  if ! fb_verify_passed "$(fb_reports)/k3s-image-verify-preview.log"; then
    echo "FAIL: images not verified after checkpoint — may need import checkpoint again."
    exit 1
  fi

  echo "--- continue: prepare (preflight + dry-run) ---"
  fb_run_prepare
  fb_workflow_update "awaiting_deploy_auth" "run_deploy_with_phrase"
  fb_print_deploy_auth_block
}

case "$CMD" in
  status)
    echo "=== Impilo full boot status ==="
    echo "Branch: $(git branch --show-current) @ $(git rev-parse --short HEAD)"
    echo "Image tag: $TAG"
    echo "Repo: $REPO"
    echo ""
    echo "--- Slice (protected: $IMPILO_PROTECTED_NS) ---"
    curl -s http://41.57.127.235/health/version 2>/dev/null | head -c 200 || echo "slice health: unreachable"
    echo ""
    kubectl get pods -n "$IMPILO_SLICE_NS" 2>/dev/null | head -20 || echo "$IMPILO_SLICE_NS: not reachable"
    echo ""
    echo "--- Full boot ($IMPILO_FULLBOOT_NS) ---"
    kubectl get deploy -n "$IMPILO_FULLBOOT_NS" 2>/dev/null || echo "$IMPILO_FULLBOOT_NS: empty or missing"
    helm list -n "$IMPILO_FULLBOOT_NS" 2>/dev/null || true
    echo ""
    if fb_helper_passwordless; then
      echo "k3s image helper: INSTALLED (passwordless)"
    elif [[ -x /usr/local/sbin/impilo-k3s-import-images ]]; then
      echo "k3s image helper: INSTALLED (sudo password required for import/list)"
    else
      echo "k3s image helper: NOT INSTALLED"
      echo "  One-time: sudo bash scripts/operator/install-k3s-image-helper.sh"
    fi
    bash "$0" sudo-checkpoint-status
    ;;
  prepare)
    fb_run_prepare
    ;;
  verify-images)
    fb_ensure_reports
    fb_verify_images
    ;;
  import-images)
    fb_import_images_flow
    ;;
  deploy)
    fb_ensure_reports
    fb_workflow_update "started" "ensure_images"
    fb_import_images_flow
    fb_run_prepare
    fb_run_deploy_if_authorized
    ;;
  continue)
    fb_continue_workflow
    ;;
  retry)
    state="$(fb_state_file)"
    if [[ ! -f "$state" ]]; then
      echo "No workflow state — run: bash scripts/operator/fullboot.sh deploy"
      exit 1
    fi
    st="$(python3 -c "import json; print(json.load(open('$state')).get('status',''))")"
    case "$st" in
      awaiting_sudo_checkpoint)
        fb_print_product_owner_block "$(python3 -c "import json; print(json.load(open('$(fb_checkpoint_json)')).get('required_action','import'))" 2>/dev/null || echo import_full_boot_images_to_k3s)"
        exit "$CHECKPOINT_EXIT"
        ;;
      awaiting_deploy_auth)
        fb_continue_workflow 2>/dev/null || true
        fb_print_deploy_auth_block
        ;;
      *)
        echo "Retry: re-running deploy preparation (images + prepare)"
        fb_import_images_flow
        fb_run_prepare
        fb_print_deploy_auth_block
        ;;
    esac
    ;;
  report)
    fb_ensure_reports
    echo "=== Full boot report ==="
    echo "Time: $(date -Is)"
    echo "Branch: $(git branch --show-current) @ $(git rev-parse --short HEAD)"
    echo ""
    for f in k3s-image-verify-preview.log k3s-image-import-helper.log sudo-checkpoint-run.log; do
      p="$(fb_reports)/$f"
      if [[ -f "$p" ]]; then
        echo "--- $f (last 15 lines) ---"
        tail -n 15 "$p"
        echo ""
      fi
    done
    bash "$0" sudo-checkpoint-status
    ;;
  sudo-checkpoint-status)
    echo "--- sudo checkpoint status ---"
    if [[ -f "$(fb_checkpoint_json)" ]]; then
      echo "Pending checkpoint:"
      python3 -m json.tool "$(fb_checkpoint_json)" 2>/dev/null || cat "$(fb_checkpoint_json)"
      echo ""
      echo "Product owner guide: $(fb_checkpoint_md)"
    else
      echo "No pending checkpoint file."
    fi
    if [[ -f "$(fb_checkpoint_result_json)" ]]; then
      echo ""
      echo "Last checkpoint result:"
      python3 -m json.tool "$(fb_checkpoint_result_json)" 2>/dev/null || cat "$(fb_checkpoint_result_json)"
    else
      echo "No checkpoint result yet."
    fi
    if [[ -f "$(fb_state_file)" ]]; then
      echo ""
      echo "Workflow state:"
      python3 -m json.tool "$(fb_state_file)" 2>/dev/null || cat "$(fb_state_file)"
    fi
    ;;
  sudo-checkpoint-run)
    fb_ensure_reports
    cp="$(fb_checkpoint_json)"
    if [[ ! -f "$cp" ]]; then
      echo "FAIL: no pending checkpoint at $cp"
      echo "Cursor should run deploy/import-images first to create one."
      exit 1
    fi
    action="$(python3 -c "import json; print(json.load(open('$cp'))['required_action'])")"
    cid="$(python3 -c "import json; print(json.load(open('$cp'))['checkpoint_id'])")"
    if ! fb_allowed_checkpoint_action "$action"; then
      echo "FAIL: disallowed checkpoint action: $action"
      exit 1
    fi
    log="$(python3 -c "import json; print(json.load(open('$cp')).get('log_file_path','$(fb_reports)/sudo-checkpoint-run.log'))")"
    echo "=== sudo-checkpoint-run: $action (checkpoint $cid) ==="
    echo "impilo-preview will NOT be modified."
    if ! fb_run_checkpoint_action "$action" "$log"; then
      fb_write_checkpoint_result "false" "$log"
      echo ""
      echo "CHECKPOINT: FAILED"
      echo "See log: $log"
      exit 1
    fi
    if ! fb_checkpoint_success "$action" "$log"; then
      fb_write_checkpoint_result "false" "$log"
      echo ""
      echo "CHECKPOINT: FAILED (success marker missing)"
      echo "See log: $log"
      exit 1
    fi
    fb_write_checkpoint_result "true" "$log"
    echo ""
    echo "CHECKPOINT: SUCCESS"
    echo ""
    echo "Tell Cursor: sudo checkpoint completed"
    echo ""
    echo "Cursor will run: bash scripts/operator/fullboot.sh continue"
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    echo "Unknown command: $CMD"
    usage
    exit 1
    ;;
esac
