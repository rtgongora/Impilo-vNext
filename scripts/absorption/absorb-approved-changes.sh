#!/usr/bin/env bash
# Apply approved absorption items only — no auto-commit, no full merge by default.
set -euo pipefail

REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/absorption/_absorption-common.sh
source "$SCRIPT_DIR/_absorption-common.sh"

BASE_INPUT="${1:-$DEFAULT_PRODUCT_TRUTH_BRANCH}"
SOURCE_INPUT="${2:?source required}"

ABSORPTION_MODE="absorb"
absorption_ensure_report_dir

MD_OUT="$(absorption_report_path latest-absorption.md)"
JSON_OUT="$(absorption_report_path latest-absorption.json)"

[[ -n "$ABSORPTION_APPROVAL_FILE" && -f "$ABSORPTION_APPROVAL_FILE" ]] || \
  absorption_die "absorb mode requires --approval-file pointing to an existing JSON plan"

absorption_check_branch_safety "Absorb — branch safety"
absorption_resolve_context "$BASE_INPUT" "$SOURCE_INPUT"

# Validate approval file
export APPROVAL_FILE="$ABSORPTION_APPROVAL_FILE"
python3 <<'PY' || absorption_die "Approval file validation failed"
import json, os, sys
path = os.environ["APPROVAL_FILE"]
doc = json.load(open(path))
required = ["product_truth_branch", "source_branch", "approved_items"]
for k in required:
    if k not in doc:
        print(f"missing key: {k}", file=sys.stderr)
        sys.exit(1)
if not doc.get("approved_items"):
    print("approved_items is empty", file=sys.stderr)
    sys.exit(1)
if doc.get("allow_full_merge") and os.environ.get("ABSORPTION_ALLOW_FULL_MERGE") != "1":
    print("allow_full_merge in file but ABSORPTION_ALLOW_FULL_MERGE!=1", file=sys.stderr)
    sys.exit(1)
base = os.environ.get("ABSORPTION_BASE_REF", "")
src = os.environ.get("ABSORPTION_SOURCE_REF", "")
if doc["source_branch"].replace("origin/", "") not in src.replace("origin/", "") and doc["source_branch"] != src:
    print(f"source_branch mismatch: file={doc['source_branch']} cli={src}", file=sys.stderr)
    sys.exit(1)
ptb = doc["product_truth_branch"]
if ptb not in (base, base.replace("origin/", ""), os.environ.get("DEFAULT_PRODUCT_TRUTH_BRANCH", "")):
    if base.replace("origin/", "") not in ptb and ptb.replace("origin/", "") not in base.replace("origin/", ""):
        print(f"product_truth_branch mismatch: file={ptb} base={base}", file=sys.stderr)
        sys.exit(1)
print("approval file OK")
PY

APPLIED=()
FAILED=()
CONFLICTS=()
SKIPPED=()

apply_item() {
  local id="$1" action="$2" path="$3" commit="$4"
  absorption_info "Applying $id: $action $path"

  if [[ "$ABSORPTION_DRY_RUN" == "1" ]]; then
    APPLIED+=("$id:DRY_RUN:$action:$path")
    return 0
  fi

  case "$action" in
    RESTORE_FILE_FROM_SOURCE|ACCEPT_DIRECT)
      if ! git checkout "$ABSORPTION_SOURCE_COMMIT" -- "$path" 2>/dev/null; then
        FAILED+=("$id:checkout failed:$path")
        CONFLICTS+=("$path")
        return 1
      fi
      APPLIED+=("$id:RESTORE_FILE_FROM_SOURCE:$path")
      ;;
    CHERRY_PICK_NO_COMMIT)
      [[ -n "$commit" ]] || { FAILED+=("$id:missing commit"); return 1; }
      if ! git cherry-pick -n "$commit" 2>/dev/null; then
        git cherry-pick --abort 2>/dev/null || true
        FAILED+=("$id:cherry-pick conflict:$commit")
        CONFLICTS+=("$path:$commit")
        return 1
      fi
      APPLIED+=("$id:CHERRY_PICK_NO_COMMIT:$commit")
      ;;
    APPLY_PATCH_FILE)
      absorption_warn "$id: APPLY_PATCH_FILE requires MANUAL_STEP_REQUIRED — not automated"
      SKIPPED+=("$id:MANUAL_STEP_REQUIRED:$path")
      ;;
    ACCEPT_SELECTED_HUNKS_MANUAL|MANUAL_ADAPTATION_MARKER)
      SKIPPED+=("$id:MANUAL_STEP_REQUIRED:$path")
      ;;
    DOCUMENT_ONLY|SKIP)
      SKIPPED+=("$id:$action:$path")
      ;;
    *)
      FAILED+=("$id:unknown action $action")
      return 1
      ;;
  esac
}

# Process approved items from JSON
while IFS=$'\t' read -r id action path commit; do
  [[ -z "$id" ]] && continue
  apply_item "$id" "$action" "$path" "$commit" || {
    absorption_error "Stopped on conflict/failure at item $id"
    break
  }
done < <(python3 <<PY
import json, os
doc = json.load(open(os.environ["APPROVAL_FILE"]))
for item in doc.get("approved_items", []):
    print("\t".join([
        item.get("id",""),
        item.get("action",""),
        item.get("path",""),
        item.get("commit",""),
    ]))
PY
)

DIFF_STAT="$(git diff --stat 2>/dev/null | tail -20 || true)"

{
  echo "# Change Absorption — Absorb Report"
  echo ""
  echo "- **Product Truth:** \`$ABSORPTION_BASE_REF\`"
  echo "- **Source:** \`$ABSORPTION_SOURCE_REF\`"
  echo "- **Approval file:** \`$ABSORPTION_APPROVAL_FILE\`"
  echo "- **Dry run:** $ABSORPTION_DRY_RUN"
  echo ""
  echo "## Applied"
  for a in "${APPLIED[@]:-}"; do echo "- $a"; done
  [[ ${#APPLIED[@]} -eq 0 ]] && echo "- none"
  echo ""
  echo "## Skipped / manual"
  for s in "${SKIPPED[@]:-}"; do echo "- $s"; done
  [[ ${#SKIPPED[@]} -eq 0 ]] && echo "- none"
  echo ""
  echo "## Failed / conflicts"
  for f in "${FAILED[@]:-}"; do echo "- $f"; done
  for c in "${CONFLICTS[@]:-}"; do echo "- CONFLICT: $c"; done
  [[ ${#FAILED[@]} -eq 0 && ${#CONFLICTS[@]} -eq 0 ]] && echo "- none"
  echo ""
  echo "## Working tree diff (stat)"
  echo '```'
  echo "$DIFF_STAT"
  echo '```'
  echo ""
  echo "**No automatic commit.** Review and commit manually when satisfied."
} >"$MD_OUT"

export ABS_APPLIED="$(IFS='|'; echo "${APPLIED[*]:-}")"
export ABS_CONFLICTS="$(IFS='|'; echo "${CONFLICTS[*]:-}")"
export ABS_FAILED="$(IFS='|'; echo "${FAILED[*]:-}")"
export ABS_SKIPPED="$(IFS='|'; echo "${SKIPPED[*]:-}")"
export APPROVAL_FILE="$ABSORPTION_APPROVAL_FILE"
python3 - "$JSON_OUT" <<'PY'
import json, sys, os
from datetime import datetime, timezone
out = sys.argv[1]
applied = [a for a in os.environ.get("ABS_APPLIED","").split("|") if a]
conflicts = [c for c in os.environ.get("ABS_CONFLICTS","").split("|") if c]
doc = {
    "mode": "absorb",
    "product_truth_branch": os.environ.get("ABSORPTION_BASE_REF",""),
    "source_branch": os.environ.get("ABSORPTION_SOURCE_REF",""),
    "base_mode": os.environ.get("ABSORPTION_BASE_MODE",""),
    "approval_file": os.environ.get("APPROVAL_FILE",""),
    "verdict": "CONFLICT_STOP" if conflicts else ("DRY_RUN" if os.environ.get("ABSORPTION_DRY_RUN")=="1" else "APPLIED"),
    "applied": applied,
    "skipped": [s for s in os.environ.get("ABS_SKIPPED","").split("|") if s],
    "failed": [f for f in os.environ.get("ABS_FAILED","").split("|") if f],
    "conflicts": conflicts,
    "finished_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "final_recommendation": "Run verify --run-gates then scripts/pipeline/run-local-quality-gates.sh",
}
open(out, "w").write(json.dumps(doc, indent=2) + "\n")
PY

echo "Report: $MD_OUT"
echo "JSON:   $JSON_OUT"
absorption_print_next_steps absorb

if [[ ${#CONFLICTS[@]} -gt 0 || ${#FAILED[@]} -gt 0 ]]; then
  exit 1
fi
