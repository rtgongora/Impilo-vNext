# shellcheck shell=bash
# Shared library for scripts/operator/fullboot.sh (human sudo checkpoint workflow).
[[ -n "${_IMPILO_FULLBOOT_LIB_LOADED:-}" ]] && return 0
_IMPILO_FULLBOOT_LIB_LOADED=1

IMPILO_FULLBOOT_NS="${IMPILO_FULLBOOT_NS:-impilo-full-preview}"
IMPILO_SLICE_NS="${IMPILO_SLICE_NS:-impilo-preview}"
IMPILO_PROTECTED_NS="$IMPILO_SLICE_NS"

fb_repo() {
  echo "${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
}

fb_reports() {
  echo "$(fb_repo)/reports/full-boot"
}

fb_tag() {
  echo "${FULL_BOOT_IMAGE_TAG:-preview}"
}

fb_ensure_reports() {
  mkdir -p "$(fb_reports)"
}

fb_helper_passwordless() {
  [[ -x /usr/local/sbin/impilo-k3s-import-images && -x /usr/local/sbin/impilo-k3s-list-images ]] \
    && sudo -n /usr/local/sbin/impilo-k3s-list-images "$(fb_tag)" "$(fb_repo)" >/dev/null 2>&1
}

fb_verify_images() {
  local log
  log="$(fb_reports)/k3s-image-verify-preview.log"
  NS="$IMPILO_FULLBOOT_NS" REPORT="$log" REPO_PATH="$(fb_repo)" \
    bash "$(fb_repo)/scripts/dev/verify-full-boot-k3s-images.sh" "$(fb_tag)" 2>&1 | tee "$log"
}

fb_verify_passed() {
  local log="${1:-$(fb_reports)/k3s-image-verify-preview.log}"
  [[ -f "$log" ]] && grep -q 'IMAGE_PRESENCE: PASS' "$log" && grep -q 'SUMMARY ok=22 fail=0' "$log"
}

fb_import_passwordless() {
  sudo -n /usr/local/sbin/impilo-k3s-import-images "$(fb_tag)" "$(fb_repo)"
}

fb_state_file() { echo "$(fb_reports)/fullboot-workflow-state.json"; }
fb_checkpoint_json() { echo "$(fb_reports)/sudo-checkpoint.json"; }
fb_checkpoint_md() { echo "$(fb_reports)/sudo-checkpoint.md"; }
fb_checkpoint_result_json() { echo "$(fb_reports)/sudo-checkpoint-result.json"; }
fb_checkpoint_result_md() { echo "$(fb_reports)/sudo-checkpoint-result.md"; }

fb_write_json() {
  local path="$1"
  local content="$2"
  printf '%s\n' "$content" >"$path"
}

fb_checkpoint_id() {
  echo "cp-$(date +%Y%m%d%H%M%S)-$(git -C "$(fb_repo)" rev-parse --short HEAD 2>/dev/null || echo nosha)"
}

fb_allowed_checkpoint_action() {
  case "$1" in
    import_full_boot_images_to_k3s | list_full_boot_k3s_images | cleanup_stale_k3s_import_temp_files)
      return 0
      ;;
    *) return 1 ;;
  esac
}

fb_write_checkpoint() {
  local action="$1"
  local success_marker="$2"
  local vm_command="$3"
  local log_path="${4:-$(fb_reports)/sudo-checkpoint-run.log}"
  local resume_after="${5:-verify_images_and_continue_deploy}"

  fb_ensure_reports
  local cid branch commit ts repo
  cid="$(fb_checkpoint_id)"
  branch="$(git -C "$(fb_repo)" branch --show-current 2>/dev/null || echo unknown)"
  commit="$(git -C "$(fb_repo)" rev-parse --short HEAD 2>/dev/null || echo unknown)"
  ts="$(date -Is)"
  repo="$(fb_repo)"

  python3 - "$cid" "$ts" "$repo" "$branch" "$commit" "$action" "$success_marker" "$vm_command" "$log_path" "$resume_after" <<'PY' >"$(fb_reports)/sudo-checkpoint.json"
import json, sys
cid, ts, repo, branch, commit, action, marker, vm_cmd, log_path, resume_after = sys.argv[1:11]
doc = {
    "checkpoint_id": cid,
    "timestamp": ts,
    "repo_path": repo,
    "branch": branch,
    "commit": commit,
    "required_action": action,
    "exact_command_to_run": vm_cmd,
    "expected_success_marker": marker,
    "log_file_path": log_path,
    "resume_command": "bash scripts/operator/fullboot.sh continue",
    "safety_note": "Runs only the named checkpoint action. Does not deploy full boot. Does not modify impilo-preview.",
    "affected_namespace": "impilo-full-preview",
    "protected_namespace": "impilo-preview",
    "resume_after_success": resume_after,
}
print(json.dumps(doc, indent=2))
PY

  cat >"$(fb_reports)/sudo-checkpoint.md" <<EOF
# Full boot — human sudo checkpoint

**Checkpoint ID:** $cid  
**Time:** $ts  
**Branch:** $branch @ $commit  

## What needs your consent

Privileged action (sudo password): **$action**

- **Affected namespace:** \`impilo-full-preview\` (full boot only)
- **Protected (untouched):** \`impilo-preview\` (slice at http://41.57.127.235)

## What to run (one sequence)

Open PowerShell:

\`\`\`powershell
ssh -p 2276 robert@41.57.127.235
\`\`\`

Inside the VM:

\`\`\`bash
cd $repo
git pull
sudo -v
bash scripts/operator/fullboot.sh sudo-checkpoint-run
\`\`\`

This performs **only**: $action  
It will **not** deploy unless you separately authorize deploy later.

## After it succeeds

Tell Cursor:

\`\`\`text
sudo checkpoint completed
\`\`\`

Cursor will run:

\`\`\`bash
bash scripts/operator/fullboot.sh continue
\`\`\`

## Success marker

\`$marker\`

## Log

\`$log_path\`
EOF

  fb_write_json "$(fb_state_file)" "$(python3 - <<PY
import json
from datetime import datetime, timezone
print(json.dumps({
  "workflow": "fullboot-deploy",
  "updated_at": datetime.now(timezone.utc).isoformat(),
  "status": "awaiting_sudo_checkpoint",
  "checkpoint_id": "$cid",
  "image_tag": "$(fb_tag)",
  "next_step_after_continue": "$resume_after",
}, indent=2))
PY
)"
}

fb_print_product_owner_block() {
  local action="$1"
  cat <<EOF

================================================================================
HUMAN SUDO CHECKPOINT — product owner action required
================================================================================

Cursor has finished all non-sudo preparation. One privileged step needs your
sudo password in **your** terminal (not in chat).

Open PowerShell and run:

  ssh -p 2276 robert@41.57.127.235

Then inside the VM run:

  cd $(fb_repo)
  git pull
  sudo -v
  bash scripts/operator/fullboot.sh sudo-checkpoint-run

This will ONLY perform: $action
It will NOT deploy full boot unless you later authorize deploy separately.

When it finishes successfully, tell Cursor:

  sudo checkpoint completed

Cursor will then run: bash scripts/operator/fullboot.sh continue

Details: $(fb_reports)/sudo-checkpoint.md
================================================================================
EOF
}

fb_sudo_run() {
  if [[ "$(id -u)" -eq 0 ]]; then
    "$@"
  else
    sudo "$@"
  fi
}

fb_run_checkpoint_action() {
  local action="$1"
  local log="$2"
  local repo="$(fb_repo)"
  local tag="$(fb_tag)"
  : >"$log"

  case "$action" in
    import_full_boot_images_to_k3s)
      if [[ ! -x /usr/local/sbin/impilo-k3s-import-images ]]; then
        echo "ERROR: install helper first: sudo bash scripts/operator/install-k3s-image-helper.sh" | tee -a "$log"
        return 1
      fi
      echo "Running: impilo-k3s-import-images $tag" | tee -a "$log"
      fb_sudo_run /usr/local/sbin/impilo-k3s-import-images "$tag" "$repo" 2>&1 | tee -a "$log"
      local rc=${PIPESTATUS[0]}
      if [[ "$rc" -ne 0 ]]; then
        return "$rc"
      fi
      fb_sudo_run /usr/local/sbin/impilo-k3s-list-images "$tag" "$repo" 2>&1 | tee -a "$log"
      return "${PIPESTATUS[0]}"
      ;;
    list_full_boot_k3s_images)
      if [[ ! -x /usr/local/sbin/impilo-k3s-list-images ]]; then
        echo "ERROR: helper not installed" | tee -a "$log"
        return 1
      fi
      fb_sudo_run /usr/local/sbin/impilo-k3s-list-images "$tag" "$repo" 2>&1 | tee -a "$log"
      return "${PIPESTATUS[0]}"
      ;;
    cleanup_stale_k3s_import_temp_files)
      echo "Removing /tmp/impilo-image-*.tar" | tee -a "$log"
      fb_sudo_run rm -f /tmp/impilo-image-*.tar 2>&1 | tee -a "$log"
      local left
      left="$(ls /tmp/impilo-image-*.tar 2>/dev/null | wc -l | tr -d ' ')"
      if [[ "$left" != "0" ]]; then
        echo "FAIL: $left tar files remain" | tee -a "$log"
        return 1
      fi
      echo "OK: stale import temp files removed" | tee -a "$log"
      return 0
      ;;
    *)
      echo "ERROR: disallowed checkpoint action: $action" | tee -a "$log"
      return 1
      ;;
  esac
}

fb_checkpoint_success() {
  local action="$1"
  local log="$2"
  case "$action" in
    import_full_boot_images_to_k3s | list_full_boot_k3s_images)
      grep -q 'IMAGE_PRESENCE: PASS' "$log" && grep -q 'SUMMARY ok=22 fail=0' "$log"
      ;;
    cleanup_stale_k3s_import_temp_files)
      grep -q 'OK: stale import temp files removed' "$log"
      ;;
    *) return 1 ;;
  esac
}

fb_write_checkpoint_result() {
  local success="$1"
  local log="$2"
  local cp_json
  cp_json="$(fb_checkpoint_json)"
  python3 - "$success" "$log" "$cp_json" <<'PY' >"$(fb_reports)/sudo-checkpoint-result.json"
import json, sys
from datetime import datetime, timezone
success, log_path, cp_path = sys.argv[1], sys.argv[2], sys.argv[3]
cp = json.load(open(cp_path))
out = {
    "checkpoint_id": cp.get("checkpoint_id"),
    "completed_at": datetime.now(timezone.utc).isoformat(),
    "required_action": cp.get("required_action"),
    "success": success.lower() == "true",
    "log_file_path": log_path,
    "resume_command": cp.get("resume_command", "bash scripts/operator/fullboot.sh continue"),
}
print(json.dumps(out, indent=2))
PY
  {
    echo "# Sudo checkpoint result"
    echo ""
    echo "- **Success:** $success"
    echo "- **Log:** $log"
    echo ""
    echo "Tell Cursor: **sudo checkpoint completed** (if success)"
    echo ""
    echo "Then Cursor runs: \`bash scripts/operator/fullboot.sh continue\`"
  } >"$(fb_reports)/sudo-checkpoint-result.md"
}

fb_clear_checkpoint_pending() {
  rm -f "$(fb_checkpoint_json)" "$(fb_checkpoint_md)" 2>/dev/null || true
}

fb_workflow_update() {
  local status="$1"
  local next="${2:-}"
  python3 - "$status" "$next" "$(fb_state_file)" "$(fb_tag)" <<'PY'
import json, sys
from datetime import datetime, timezone
status, next_step, path, tag = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
doc = {}
try:
    doc = json.load(open(path))
except Exception:
    doc = {"workflow": "fullboot-deploy"}
doc["updated_at"] = datetime.now(timezone.utc).isoformat()
doc["status"] = status
doc["image_tag"] = tag
if next_step:
    doc["next_step_after_continue"] = next_step
json.dump(doc, open(path, "w"), indent=2)
print(path)
PY
}
