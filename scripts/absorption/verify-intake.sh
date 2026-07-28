#!/usr/bin/env bash
# Post-absorption verification — maps touched areas to existing VM gates.
set -euo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/absorption/_absorption-common.sh
source "$SCRIPT_DIR/_absorption-common.sh"

BASE_INPUT="${1:-$DEFAULT_PRODUCT_TRUTH_BRANCH}"
SOURCE_INPUT="${2:?source required}"

ABSORPTION_MODE="verify"
absorption_ensure_report_dir

MD_OUT="$(absorption_report_path latest-verification.md)"
JSON_OUT="$(absorption_report_path latest-verification.json)"
LOG_DIR="/tmp/impilo-absorption-verify"
mkdir -p "$LOG_DIR"

absorption_check_branch_safety "Verify — branch safety"
absorption_resolve_context "$BASE_INPUT" "$SOURCE_INPUT"

# Use working tree changes vs base for post-absorb verification, else source diff
mapfile -t WT_CHANGED < <(git diff --name-only "$ABSORPTION_BASE_COMMIT" 2>/dev/null || true)
if [[ ${#WT_CHANGED[@]} -eq 0 ]]; then
  mapfile -t WT_CHANGED < <(absorption_changed_files)
fi

FILES_LIST="$(mktemp)"
printf '%s\n' "${WT_CHANGED[@]}" >"$FILES_LIST"
bash "$SCRIPT_DIR/classify-touched-areas.sh" - "$FILES_LIST" > /tmp/verify-classify.txt 2>&1 || true
TOUCHED_JSON="${ABSORPTION_REPORT_DIR}/touched-areas.json"
PIPELINE_ONLY="$(python3 -c "import json; print(json.load(open('$TOUCHED_JSON')).get('pipeline_only','security,change-safety'))" 2>/dev/null || echo "security,static,change-safety")"

CHECKS=()
RESULTS=()

run_check() {
  local name="$1" area="$2" cmd="$3"
  local log="$LOG_DIR/${name// /_}.log"
  local result="SKIP"
  local classification="UNKNOWN"
  local notes="Not executed (--run-gates not set)"

  if [[ "$ABSORPTION_RUN_GATES" == "1" && "$ABSORPTION_DRY_RUN" != "1" ]]; then
    if eval "$cmd" >"$log" 2>&1; then
      result="PASS"
      classification="N/A"
      notes="See $log"
    else
      result="FAIL"
      # Heuristic: if failure log mentions source-only paths, mark UNKNOWN
      if grep -qiE 'preview|curl|502|503|connection refused|PREVIEW_BASE' "$log" 2>/dev/null; then
        classification="UNKNOWN"
        notes="Preview/live dependency — not necessarily CAUSED_BY_INTAKE"
      elif grep -qiE 'parity docs out of sync|generate-parity' "$log" 2>/dev/null; then
        classification="CAUSED_BY_INTAKE"
        notes="Likely intake doc/sync gap"
      else
        classification="CAUSED_BY_INTAKE"
        notes="Failed after absorption — review log"
      fi
    fi
  fi

  CHECKS+=("$name")
  RESULTS+=("$name|$area|$result|$classification|$notes|$cmd")
}

run_check "static-checks" "frontend/routes" "bash scripts/test/run-static-checks.sh"
run_check "frontend-checks" "frontend" "bash scripts/test/run-frontend-checks.sh"
run_check "backend-checks" "backend/bff" "bash scripts/test/run-backend-checks.sh"
run_check "change-safety" "guards" "GUARD_BASE_REF=$ABSORPTION_BASE_COMMIT bash scripts/guard/run-change-safety-gates.sh"
run_check "parity-web" "parity" "GUARD_BASE_REF=$ABSORPTION_BASE_COMMIT bash scripts/guard/check-backend-frontend-parity.sh"
run_check "parity-mobile" "mobile" "GUARD_BASE_REF=$ABSORPTION_BASE_COMMIT bash scripts/guard/check-mobile-parity.sh"
run_check "api-contracts" "contracts" "bash scripts/test/run-api-contract-checks.sh"

if [[ "$ABSORPTION_RUN_GATES" == "1" && "$ABSORPTION_DRY_RUN" != "1" ]]; then
  absorption_info "Running targeted VM pipeline: PIPELINE_ONLY=$PIPELINE_ONLY"
  PIPELINE_ONLY="$PIPELINE_ONLY" PIPELINE_SKIP_TOOLCHECK=1 PIPELINE_SKIP_REGRESSION=1 \
    PIPELINE_SKIP_E2E=1 PIPELINE_SKIP_INTEGRATION=1 \
    bash scripts/pipeline/run-local-quality-gates.sh >"$LOG_DIR/vm-pipeline.log" 2>&1 && \
    RESULTS+=("vm-pipeline|all|PASS|N/A|See $LOG_DIR/vm-pipeline.log|run-local-quality-gates.sh") || \
    RESULTS+=("vm-pipeline|all|FAIL|CAUSED_BY_INTAKE|See $LOG_DIR/vm-pipeline.log|run-local-quality-gates.sh")
fi

VERDICT="RECOMMEND_RUN_GATES"
[[ "$ABSORPTION_RUN_GATES" == "1" ]] && VERDICT="VERIFY_COMPLETE"

{
  echo "# Change Absorption — Verification Report"
  echo ""
  echo "- **Product Truth:** \`$ABSORPTION_BASE_REF\`"
  echo "- **Source context:** \`$ABSORPTION_SOURCE_REF\`"
  echo "- **Run gates:** $ABSORPTION_RUN_GATES"
  echo "- **Suggested PIPELINE_ONLY:** \`$PIPELINE_ONLY\`"
  echo ""
  echo "## Verification table"
  echo ""
  echo "| Check | Command | Area | Result | Failure classification | Notes |"
  echo "| ----- | ------- | ---- | ------ | -------------------- | ----- |"
  for r in "${RESULTS[@]}"; do
    IFS='|' read -r name area result class notes cmd <<< "$r"
    echo "| $name | \`$cmd\` | $area | $result | $class | $notes |"
  done
  echo ""
  echo "## Post-absorption authority"
  echo "Existing VM gate stack remains quality authority:"
  echo "\`bash scripts/pipeline/run-local-quality-gates.sh\`"
  echo ""
  echo "### Failure classification legend"
  echo "- **CAUSED_BY_INTAKE** — likely introduced by absorbed changes"
  echo "- **PRE_EXISTING** — present on Product Truth before intake (manual baseline compare)"
  echo "- **UNKNOWN** — preview/infra dependency or inconclusive"
  echo ""
  echo "**Limitation:** PRE_EXISTING requires optional baseline run on pre-absorb commit."
  echo ""
  echo "## Accepted Change Completion / Enablement Gate (verification view)"
  echo ""
  APPROVAL_FOR_VERIFY="${ABSORPTION_APPROVAL_FILE:-$(absorption_report_path approved-plan.json)}"
  if [[ -f "$APPROVAL_FOR_VERIFY" ]]; then
    python3 "$SCRIPT_DIR/_product-gates.py" report \
      --gates "${ABSORPTION_REPORT_DIR}/latest-analysis.json" \
      --approval "$APPROVAL_FOR_VERIFY" \
      --absorb "${ABSORPTION_REPORT_DIR}/latest-absorption.json" \
      --verify "$JSON_OUT" \
      --out /dev/null \
      --md-out /dev/stdout 2>/dev/null | grep -A500 "## Accepted Change Completion" | head -40 || \
      echo "_Run report mode after absorb for full completion tracking._"
  else
    echo "_No approved-plan.json — completion enablement table available after absorb + approval file._"
  fi
  echo ""
  echo "_Browser QA and preview smoke remain product-owner follow-ups even when VM gates pass._"
} >"$MD_OUT"

export ABS_VERIFY_ROWS="$(printf '%s\n' "${RESULTS[@]}")"
python3 - "$JSON_OUT" "$VERDICT" "$PIPELINE_ONLY" <<'PY'
import json, sys, os
from datetime import datetime, timezone
out, verdict, pipeline_only = sys.argv[1], sys.argv[2], sys.argv[3]
rows = []
for line in os.environ.get("ABS_VERIFY_ROWS", "").splitlines():
    parts = line.split("|")
    if len(parts) >= 6:
        rows.append({
            "check": parts[0], "area": parts[1], "result": parts[2],
            "failure_classification": parts[3], "notes": parts[4], "command": parts[5],
        })
doc = {
    "mode": "verify",
    "product_truth_branch": os.environ.get("ABSORPTION_BASE_REF"),
    "source_branch": os.environ.get("ABSORPTION_SOURCE_REF"),
    "base_mode": os.environ.get("ABSORPTION_BASE_MODE"),
    "verdict": verdict,
    "pipeline_only": pipeline_only,
    "run_gates": os.environ.get("ABSORPTION_RUN_GATES") == "1",
    "verification": {"checks": rows},
    "checks_run": [r["check"] for r in rows],
    "finished_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "final_recommendation": "bash scripts/pipeline/run-local-quality-gates.sh",
}
open(out, "w").write(json.dumps(doc, indent=2) + "\n")
PY

echo "Report: $MD_OUT"
echo "JSON:   $JSON_OUT"
absorption_print_next_steps verify
