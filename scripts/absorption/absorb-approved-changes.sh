#!/usr/bin/env bash
# Apply approved absorption items only — no auto-commit, no full merge by default.
set -euo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/absorption/_absorption-common.sh
source "$SCRIPT_DIR/_absorption-common.sh"

BASE_INPUT="${1:-$DEFAULT_PRODUCT_TRUTH_BRANCH}"
SOURCE_INPUT="${2:?source required}"

ABSORPTION_MODE="absorb"
absorption_ensure_report_dir

MD_OUT="$(absorption_report_path latest-absorption.md)"
JSON_OUT="$(absorption_report_path latest-absorption.json)"
WARN_JSON="$(mktemp)"
trap 'rm -f "$WARN_JSON"' EXIT

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

# Completion / enablement metadata warnings (non-blocking)
python3 "$SCRIPT_DIR/_product-gates.py" absorb-warnings \
  --approval "$ABSORPTION_APPROVAL_FILE" \
  --out "$WARN_JSON" || true
COMPLETION_WARN_COUNT="$(python3 -c "import json; print(json.load(open('$WARN_JSON')).get('count',0))" 2>/dev/null || echo 0)"

APPLIED=()
FAILED=()
CONFLICTS=()
SKIPPED=()
COMPLETION_TRACKING=()

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

export ABS_APPLIED="$(IFS='|'; echo "${APPLIED[*]:-}")"
APPLIED_LIST="$(mktemp)"
python3 <<PY >"$APPLIED_LIST"
import json, os
applied = [a for a in os.environ.get("ABS_APPLIED","").split("|") if a]
print(json.dumps(applied))
PY

COMPLETION_MD="$(python3 "$SCRIPT_DIR/_product-gates.py" report \
  --gates "${ABSORPTION_REPORT_DIR}/latest-analysis.json" \
  --approval "$ABSORPTION_APPROVAL_FILE" \
  --absorb /dev/null \
  --verify /dev/null \
  --out /dev/null \
  --md-out /dev/stdout 2>/dev/null || true)"

{
  echo "# Change Absorption — Absorb Report"
  echo ""
  echo "- **Product Truth:** \`$ABSORPTION_BASE_REF\`"
  echo "- **Source:** \`$ABSORPTION_SOURCE_REF\`"
  echo "- **Approval file:** \`$ABSORPTION_APPROVAL_FILE\`"
  echo "- **Dry run:** $ABSORPTION_DRY_RUN"
  echo ""
  if [[ "$COMPLETION_WARN_COUNT" -gt 0 ]]; then
    echo "## Completion metadata warnings"
    echo ""
    echo "**$COMPLETION_WARN_COUNT** approved item(s) missing \`completion_needs\` or related product-owner fields."
    echo "Absorb was **not blocked** — placeholders were recorded. Update \`approved-plan.json\` for clarity."
    echo ""
    python3 -c "import json; [print(f\"- {w['message']}\") for w in json.load(open('$WARN_JSON')).get('warnings',[])]"
    echo ""
  fi
  echo "## Accepted Change Completion / Enablement Gate (post-absorb status)"
  echo ""
  echo "_Browser QA and preview smoke are reported as required follow-ups — they do not block absorb._"
  echo ""
  if [[ -n "$COMPLETION_MD" ]]; then
    echo "$COMPLETION_MD"
  else
    python3 <<PY
import json, os
doc = json.load(open(os.environ["APPROVAL_FILE"]))
applied_raw = json.load(open("$APPLIED_LIST"))
applied_ids = {a.split(":")[0] for a in applied_raw}
print("| Accepted change | Absorbed? | Works end-to-end? | Additional Product Truth work needed | Owner | Status |")
print("| --------------- | --------- | ----------------- | ------------------------------------ | ----- | ------ |")
for item in doc.get("approved_items", []):
    iid = item.get("id","")
    absorbed = "Yes" if iid in applied_ids else "No"
    needs = item.get("completion_needs") or ["NEEDS_TESTS", "NEEDS_BROWSER_QA"]
    if isinstance(needs, str):
        needs = [needs]
    print(f"| {iid}: {item.get('path','')} | {absorbed} | Partial | {', '.join(needs)} | engineering | Run verify --run-gates |")
PY
  fi
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

export ABS_CONFLICTS="$(IFS='|'; echo "${CONFLICTS[*]:-}")"
export ABS_FAILED="$(IFS='|'; echo "${FAILED[*]:-}")"
export ABS_SKIPPED="$(IFS='|'; echo "${SKIPPED[*]:-}")"
export APPROVAL_FILE="$ABSORPTION_APPROVAL_FILE"
export ABS_WARN_JSON="$WARN_JSON"
export ABS_APPLIED_LIST="$APPLIED_LIST"
export ABS_JSON_OUT="$JSON_OUT"
python3 <<'PY'
import json, os, sys
from datetime import datetime, timezone
from pathlib import Path

repo = Path(os.environ.get("REPO_PATH", "."))
sys.path.insert(0, str(repo / "scripts" / "absorption"))
import importlib.util
spec = importlib.util.spec_from_file_location("pg", repo / "scripts" / "absorption" / "_product-gates.py")
pg = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pg)

out = os.environ["ABS_JSON_OUT"]
applied = json.load(open(os.environ["ABS_APPLIED_LIST"]))
applied_ids_list = applied
conflicts = [c for c in os.environ.get("ABS_CONFLICTS","").split("|") if c]
approval = json.load(open(os.environ["APPROVAL_FILE"]))
warnings = json.load(open(os.environ["ABS_WARN_JSON"])).get("warnings", [])
completion_rows = pg.build_completion_tracking_table(approval.get("approved_items", []), applied_ids_list, None)
doc = {
    "mode": "absorb",
    "product_truth_branch": os.environ.get("ABSORPTION_BASE_REF",""),
    "source_branch": os.environ.get("ABSORPTION_SOURCE_REF",""),
    "base_mode": os.environ.get("ABSORPTION_BASE_MODE",""),
    "approval_file": os.environ.get("APPROVAL_FILE",""),
    "verdict": "CONFLICT_STOP" if conflicts else ("DRY_RUN" if os.environ.get("ABSORPTION_DRY_RUN")=="1" else "APPLIED"),
    "applied": applied_ids_list,
    "skipped": [s for s in os.environ.get("ABS_SKIPPED","").split("|") if s],
    "failed": [f for f in os.environ.get("ABS_FAILED","").split("|") if f],
    "conflicts": conflicts,
    "completion_metadata_warnings": warnings,
    "completion_tracking": completion_rows,
    "finished_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "final_recommendation": "Run verify --run-gates; complete completion_needs (browser QA does not block absorb)",
}
Path(out).write_text(json.dumps(doc, indent=2) + "\n")
PY
rm -f "$APPLIED_LIST"

echo "Report: $MD_OUT"
echo "JSON:   $JSON_OUT"
absorption_print_next_steps absorb

if [[ ${#CONFLICTS[@]} -gt 0 || ${#FAILED[@]} -gt 0 ]]; then
  exit 1
fi
