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
  smoke                 Post-deploy runtime smoke tests
  completeness          Post-deploy runtime completeness gate
  diagnose              Post-deploy pod/deployment failure summary
  helper-status         Installed vs repo k3s helper version + import lock state
  registry-status     Local OCI registry health
  registry-up         Start local registry (no sudo)
  cleanup-imports     Stop duplicate imports via sudo checkpoint if needed
  refresh-stale27-containerd-refs  Scoped containerd ref refresh for 27 stale services (sudo checkpoint)
  sudo-checkpoint-run   Product owner: run pending privileged checkpoint only
  sudo-checkpoint-status  Show pending checkpoint and last result
  wave-status           Show full-boot wave expansion progress
  wave-build <n>        Build images for wave N
  wave-deploy <n>       Deploy wave N (requires deploy authorization)
  wave-report           Report wave status

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
  if [[ "${FULL_BOOT_DIGEST_PIN_FORCE_IMPORT:-}" == "1" ]]; then
    echo "SKIP: verify-images short-circuit disabled (digest-pinned force import)"
    return 1
  fi
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

fb_guard_import_not_active() {
  if fb_import_active; then
    echo "FAIL: k3s image import already running — cannot start another import or deploy."
    fb_request_cleanup_imports_checkpoint
  fi
}

fb_import_images_flow() {
  fb_ensure_reports
  fb_guard_import_not_active
  if fb_helper_outdated; then
    echo "WARN: installed k3s helper is outdated (repo v$(fb_helper_version_repo) vs installed v$(fb_helper_version_installed))"
    fb_request_cleanup_imports_checkpoint
  fi
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

# Data volumes that hold authoritative state (2026-07-18 wipe incident). A clean rebuild
# preserves these by DEFAULT; destroying them requires the explicit FULL_BOOT_WIPE_DATA
# path below. The PVC templates also carry helm.sh/resource-policy=keep so 'helm
# uninstall' alone can never delete them.
FB_DATA_PVCS="postgres-data keycloak-data minio-data kafka-data orthanc-data postgres-backups ndila-martin-data redroid-data"
FB_WIPE_PHRASE="WIPE ALL IMPILO PREVIEW DATA"

fb_cleanup_full_boot_namespace() {
  echo "--- cleanup impilo-full-preview (non-sudo kubectl) ---"
  echo "impilo-preview is NOT modified."
  if [[ "${FULL_BOOT_WIPE_DATA:-0}" == "1" ]]; then
    echo ""
    echo "!!! FULL_BOOT_WIPE_DATA=1 — this DESTROYS ALL DATA VOLUMES:"
    echo "!!!   $FB_DATA_PVCS"
    echo "!!!   (every database, citizen account, uploaded object, DICOM study, backup)"
    echo "!!! Type the phrase to proceed: $FB_WIPE_PHRASE"
    local confirm=""
    read -r confirm || true
    if [[ "$confirm" != "$FB_WIPE_PHRASE" ]]; then
      echo "FAIL: wipe phrase mismatch — data volumes NOT destroyed; aborting cleanup." >&2
      return 1
    fi
    echo "--- DATA WIPE authorized: deleting namespace incl. data PVCs ---"
    helm uninstall impilo-full-preview -n "$IMPILO_FULLBOOT_NS" 2>/dev/null || true
    kubectl delete namespace "$IMPILO_FULLBOOT_NS" --ignore-not-found --wait=false 2>/dev/null || true
    echo "OK: full wipe requested (namespace may terminate in background)"
    return 0
  fi
  # DEFAULT: clean-room rebuild of WORKLOADS ONLY — data PVCs (and the namespace that
  # holds them, plus the TLS secret) are preserved. 'helm uninstall' skips the PVCs via
  # their keep-annotation; we then clear remaining workload objects but never the PVCs.
  echo "--- data-preserving clean rebuild: PVCs kept ($FB_DATA_PVCS) ---"
  helm uninstall impilo-full-preview -n "$IMPILO_FULLBOOT_NS" 2>/dev/null || true
  kubectl -n "$IMPILO_FULLBOOT_NS" delete deployments,statefulsets,daemonsets,jobs,cronjobs,pods --all --wait=false 2>/dev/null || true
  echo "OK: workloads cleared; namespace + data volumes preserved."
  echo "    (To destroy data too: FULL_BOOT_WIPE_DATA=1 + typed phrase '$FB_WIPE_PHRASE')"
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
  # Deploy mode. DEFAULT = in-place: 'helm upgrade --install' rolls only the changed
  # Deployments and leaves everything else (incl. the namespace-scoped TLS secret,
  # IngressRoutes and running pods) untouched — no full teardown, no estate-wide
  # downtime. Opt into a destructive clean-room rebuild (wipe + redeploy from zero)
  # only when you genuinely need a clean slate, via FULL_BOOT_CLEAN_REBUILD=1.
  echo "--- Fresh pre-deploy PostgreSQL backup (blocking) ---"
  NAMESPACE="$IMPILO_FULLBOOT_NS" bash scripts/full-boot/create-predeploy-backup.sh
  export FULL_BOOT_PREDEPLOY_BACKUP_DONE=1

  if [[ "${FULL_BOOT_CLEAN_REBUILD:-0}" == "1" ]]; then
    echo "--- CLEAN REBUILD (FULL_BOOT_CLEAN_REBUILD=1): rebuilding workloads in $IMPILO_FULLBOOT_NS ---"
    echo "    DATA IS PRESERVED by default (postgres/keycloak/minio/kafka/orthanc/backup PVCs kept)."
    echo "    Destroying data additionally requires FULL_BOOT_WIPE_DATA=1 + a typed confirmation"
    echo "    phrase. A full wipe also destroys non-chart objects (TLS secret, IngressRoutes);"
    echo "    the deploy tail restores the edge; the root-only TLS secret then needs"
    echo "    'sudo /usr/local/bin/sync-mohcc-gov-tls.sh'."
    fb_cleanup_full_boot_namespace || { echo "FAIL: cleanup aborted"; return 1; }
  else
    echo "--- IN-PLACE upgrade (default): namespace preserved; helm upgrade rolls only changed services ---"
    echo "    (set FULL_BOOT_CLEAN_REBUILD=1 for a destructive clean-room rebuild)"
  fi
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

  if [[ "${action:-}" == "cleanup_duplicate_k3s_import_processes" ]]; then
    echo "--- continue: post-cleanup foundation ---"
    bash "$REPO/scripts/operator/registry-up.sh" || true
    bash "$REPO/scripts/build/push-images-to-local-registry.sh" required 2>/dev/null || true
    if ! fb_k3s_registry_configured; then
      echo "NOTE: k3s registry mirror not configured — run: bash scripts/operator/fullboot.sh registry-config"
    fi
    bash "$0" helper-status
    echo "--- verify-images ---"
    fb_verify_images || true
    fb_workflow_update "foundation_ready" "phase1_deploy_when_authorized"
    echo "Foundation cleanup complete. Full boot deploy NOT run (phase 0)."
    echo "Next: bash scripts/operator/fullboot.sh registry-status"
    echo "       bash scripts/operator/fullboot.sh verify-images"
    exit 0
  fi

  if [[ "${action:-}" == "configure_k3s_local_registry" ]]; then
    echo "--- continue: registry configured ---"
    bash "$REPO/scripts/operator/registry-up.sh" || true
    fb_workflow_update "registry_ready"
    exit 0
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
    curl -s https://impilo.mohcc.gov.zw/health/version 2>/dev/null | head -c 200 || echo "slice health: unreachable"
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
    fi
    if fb_import_active; then
      echo "IMPORT: ACTIVE (duplicate — run: bash scripts/operator/fullboot.sh cleanup-imports)"
    else
      echo "IMPORT: idle"
    fi
    bash "$0" helper-status 2>/dev/null | tail -8 || true
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
  helper-status)
    echo "=== k3s image helper status ==="
    repo_v="$(fb_helper_version_repo)"
    inst_v="$(fb_helper_version_installed)"
    echo "repo_version: $repo_v"
    echo "installed_version: ${inst_v:-none}"
    if fb_helper_outdated; then
      echo "HELPER_STATUS: OUTDATED (installed=${inst_v:-none} repo=${repo_v:-?}; run cleanup-imports)"
    else
      echo "HELPER_STATUS: CURRENT (v$inst_v)"
    fi
    if fb_import_active; then
      echo "IMPORT_STATUS: ACTIVE"
      pgrep -af 'impilo-k3s-import-images' || true
    else
      echo "IMPORT_STATUS: idle"
    fi
    ;;
  registry-status)
    bash "$REPO/scripts/operator/registry-status.sh"
    ;;
  registry-up)
    bash "$REPO/scripts/operator/registry-up.sh"
    ;;
  registry-config)
    fb_ensure_reports
    if fb_k3s_registry_configured; then
      echo "OK: k3s already configured for local registry"
      exit 0
    fi
    fb_request_registry_checkpoint
    ;;
  cleanup-imports)
    fb_ensure_reports
    if fb_import_active || fb_helper_outdated; then
      fb_request_cleanup_imports_checkpoint
    fi
    echo "OK: no duplicate imports detected; helper version $(fb_helper_version_installed)"
    ;;
  refresh-stale27-containerd-refs)
    fb_ensure_reports
    fb_guard_import_not_active
    fb_request_refresh_stale27_containerd_checkpoint
    ;;
  deploy)
    fb_ensure_reports
    fb_guard_import_not_active
    fb_workflow_update "started" "ensure_images"
    fb_import_images_flow
    fb_run_prepare
    fb_run_deploy_if_authorized
    ;;
  wave-status|  wave-report)
    bash "$REPO/scripts/operator/wave-ready-matrix.sh" 2>/dev/null || true
    WAVE_REPORT="$(fb_reports)/wave-status.json"
    if [[ -f "$WAVE_REPORT" ]]; then
      python3 -m json.tool "$WAVE_REPORT" 2>/dev/null || cat "$WAVE_REPORT"
    else
      python3 - "$REPO" <<'PY'
import yaml, json, pathlib
repo = pathlib.Path(__import__("sys").argv[1])
waves = yaml.safe_load((repo / "config/full-boot-waves.yml").read_text())
print(json.dumps(waves, indent=2))
PY
    fi
    ;;
  wave-build)
    WAVE_N="${2:-1}"
    echo "--- wave-build $WAVE_N (cumulative waves 0..$WAVE_N) ---"
    bash "$REPO/scripts/build/build-full-vnext-images.sh" --wave "$WAVE_N"
    export FULL_BOOT_IMAGE_TAG="$(fb_tag)"
    IMPILO_PUSH_WAVE="$WAVE_N" bash "$REPO/scripts/build/push-images-to-local-registry.sh" wave "$WAVE_N"
    ;;
  wave-deploy)
    WAVE_N="${2:-}"
    [[ -n "$WAVE_N" ]] || { echo "Usage: fullboot.sh wave-deploy <wave-number>"; exit 2; }
    echo "--- wave-deploy $WAVE_N (cumulative enablement through wave $WAVE_N) ---"
    fb_ensure_reports
    fb_guard_import_not_active
    export FULL_BOOT_MAX_WAVE="$WAVE_N"
    node "$REPO/scripts/full-boot/generate-full-preview-runtime-values.mjs" --max-wave "$WAVE_N"
    if ! fb_deploy_authorized; then
      echo "Set FULLBOOT_DEPLOY_AUTHORIZED=1 with phrase AUTHORIZE FULL BOOT PREVIEW DEPLOY"
      fb_workflow_update "awaiting_deploy_auth" "wave_deploy_$WAVE_N"
      fb_print_deploy_auth_block
      exit 0
    fi
    export FULL_BOOT_IMAGE_TAG="$(fb_tag)"
    export FULL_BOOT_SKIP_BUILD=1
    unset FULL_BOOT_SKIP_IMPORT || true
    fb_import_images_flow
    # Wave-sequenced rollout is a sanctioned intermediate (wave_sequenced_full_estate), so it
    # explicitly opts into the partial step with --allow-partial. The final wave must reach the
    # full estate; waves are sequencing, not optionality.
    printf '%s\n' "$DEPLOY_PHRASE" | FULL_BOOT_MAX_WAVE="$WAVE_N" FULLBOOT_DEPLOY_AUTHORIZED=1 \
      bash "$REPO/scripts/deploy/full-boot-preview-deploy.sh" --allow-partial
    bash "$REPO/scripts/operator/wave-gates.sh" "$WAVE_N" || true
    bash "$REPO/scripts/build/build-non-runtime-registry-lane.sh" || true
    fb_workflow_update "wave_deploy_completed" "wave_$WAVE_N"
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
  smoke)
    export FULL_BOOT_NAMESPACE="$IMPILO_FULLBOOT_NS"
    bash scripts/test/run-full-boot-smoke-tests.sh
    ;;
  completeness)
    export FULL_BOOT_NAMESPACE="$IMPILO_FULLBOOT_NS"
    bash scripts/guard/check-full-boot-runtime-completeness.sh
    ;;
  diagnose)
    fb_ensure_reports
    diag="$(fb_reports)/fullboot-diagnose.log"
    {
      echo "=== Full boot diagnose ($(date -Is)) ==="
      echo "Namespace: $IMPILO_FULLBOOT_NS"
      echo ""
      echo "--- Helm ---"
      helm list -n "$IMPILO_FULLBOOT_NS" 2>&1 || true
      helm status impilo-full-preview -n "$IMPILO_FULLBOOT_NS" 2>&1 || true
      echo ""
      echo "--- Deployments (not Available) ---"
      kubectl get deploy -n "$IMPILO_FULLBOOT_NS" -o wide 2>&1 || true
      python3 <<'PY' 2>/dev/null || true
import json, subprocess
ns = "impilo-full-preview"
raw = subprocess.check_output(["kubectl", "get", "deploy", "-n", ns, "-o", "json"], text=True)
items = json.loads(raw).get("items", [])
not_ready = []
for d in items:
    name = d["metadata"]["name"]
    spec = d["spec"].get("replicas", 1)
    status = d.get("status", {})
    avail = status.get("availableReplicas", 0) or 0
    ready = status.get("readyReplicas", 0) or 0
    if avail < spec or ready < spec:
        not_ready.append(f"{name}: ready={ready}/{spec} available={avail}/{spec}")
print("NOT_READY_DEPLOYMENTS:", len(not_ready))
for line in not_ready:
    print(" ", line)
PY
      echo ""
      echo "--- CrashLoopBackOff pods ---"
      kubectl get pods -n "$IMPILO_FULLBOOT_NS" --field-selector=status.phase=Running 2>/dev/null | head -5 || true
      kubectl get pods -n "$IMPILO_FULLBOOT_NS" 2>/dev/null | grep -E 'CrashLoopBackOff|Error|ImagePull|CreateContainer' || echo "(none matching filter)"
      echo ""
      echo "--- Pods Running but not Ready ---"
      kubectl get pods -n "$IMPILO_FULLBOOT_NS" 2>/dev/null | awk 'NR==1 || ($2 !~ /^[0-9]+\/[0-9]+$/ || $2 !~ /^([0-9]+)\/\1$/)' | head -40 || true
      echo ""
      echo "--- Recent events (last 25) ---"
      kubectl get events -n "$IMPILO_FULLBOOT_NS" --sort-by=.lastTimestamp 2>/dev/null | tail -25 || true
    } | tee "$diag"
    echo "Diagnose log: $diag"
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
