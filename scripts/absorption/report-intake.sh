#!/usr/bin/env bash
# Consolidate analysis, absorption, and verification into final intake report.
set -euo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/absorption/_absorption-common.sh
source "$SCRIPT_DIR/_absorption-common.sh"

BASE_INPUT="${1:-$DEFAULT_PRODUCT_TRUTH_BRANCH}"
SOURCE_INPUT="${2:?source required}"

ABSORPTION_MODE="report"
absorption_ensure_report_dir
absorption_resolve_context "$BASE_INPUT" "$SOURCE_INPUT"

ANALYSIS_MD="$(absorption_report_path latest-analysis.md)"
ANALYSIS_JSON="$(absorption_report_path latest-analysis.json)"
ABSORB_MD="$(absorption_report_path latest-absorption.md)"
ABSORB_JSON="$(absorption_report_path latest-absorption.json)"
VERIFY_MD="$(absorption_report_path latest-verification.md)"
VERIFY_JSON="$(absorption_report_path latest-verification.json)"
APPROVAL_JSON="${ABSORPTION_APPROVAL_FILE:-$(absorption_report_path approved-plan.json)}"
OUT_MD="$(absorption_report_path latest-intake-report.md)"
OUT_JSON="$(absorption_report_path latest-intake-report.json)"
PO_MD="$(mktemp)"
PO_JSON="$(mktemp)"
trap 'rm -f "$PO_MD" "$PO_JSON"' EXIT

GATES_ARG="$ANALYSIS_JSON"
[[ -f "$GATES_ARG" ]] || GATES_ARG="/dev/null"
ABSORB_ARG="${ABSORB_JSON:-/dev/null}"
[[ -f "$ABSORB_ARG" ]] || ABSORB_ARG="/dev/null"
VERIFY_ARG="${VERIFY_JSON:-/dev/null}"
[[ -f "$VERIFY_ARG" ]] || VERIFY_ARG="/dev/null"
APPROVAL_ARG="/dev/null"
[[ -f "$APPROVAL_JSON" ]] && APPROVAL_ARG="$APPROVAL_JSON"

python3 "$SCRIPT_DIR/_product-gates.py" report \
  --gates "$GATES_ARG" \
  --approval "$APPROVAL_ARG" \
  --absorb "$ABSORB_ARG" \
  --verify "$VERIFY_ARG" \
  --out "$PO_JSON" \
  --md-out "$PO_MD" 2>/dev/null || echo "" >"$PO_MD"

FINAL_REC="NEEDS_ANALYSIS"
if [[ -f "$PO_JSON" ]]; then
  FINAL_REC="$(python3 -c "import json; d=json.load(open('$PO_JSON')); print(d.get('executive_summary',{}).get('8_final_product_owner_recommendation','NEEDS_ANALYSIS'))" 2>/dev/null || echo NEEDS_ANALYSIS)"
fi
if [[ "$FINAL_REC" == "NEEDS_ANALYSIS" && -f "$VERIFY_JSON" ]]; then
  FINAL_REC="$(python3 -c "import json; print(json.load(open('$VERIFY_JSON')).get('final_recommendation','VERIFY_COMPLETE'))" 2>/dev/null || echo NEEDS_ANALYSIS)"
elif [[ "$FINAL_REC" == "NEEDS_ANALYSIS" && -f "$ANALYSIS_JSON" ]]; then
  FINAL_REC="$(python3 -c "import json; print(json.load(open('$ANALYSIS_JSON')).get('final_recommendation','NEEDS_ANALYSIS'))" 2>/dev/null || echo NEEDS_ANALYSIS)"
fi

{
  echo "# Change Absorption — Final Intake Report"
  echo ""
  echo "- **Generated:** $(absorption_now)"
  echo ""
  if [[ -s "$PO_MD" ]]; then
    cat "$PO_MD"
    echo ""
    echo "---"
    echo ""
  fi
  echo "## Session detail"
  echo ""
  echo "Intake session for source \`$ABSORPTION_SOURCE_REF\` against Product Truth \`$ABSORPTION_BASE_REF\`."
  echo "Base mode: **$ABSORPTION_BASE_MODE**."
  echo ""
  if [[ -f "$ANALYSIS_JSON" ]]; then
    echo "### Analysis snapshot"
    python3 -c "import json; d=json.load(open('$ANALYSIS_JSON')); print('- Verdict:', d.get('verdict')); print('- Inferred purpose:', d.get('inferred_source_purpose'))"
    echo ""
  else
    echo "_No analysis report — run analysis mode first._"
    echo ""
  fi
  if [[ -f "$ABSORB_MD" ]]; then
    echo "### Absorption snapshot"
    head -35 "$ABSORB_MD"
    echo ""
  else
    echo "_No absorption report yet._"
    echo ""
  fi
  if [[ -f "$VERIFY_MD" ]]; then
    echo "### Verification snapshot"
    head -40 "$VERIFY_MD"
    echo ""
  else
    echo "_No verification report yet._"
    echo ""
  fi
  if [[ -f "$ANALYSIS_MD" ]]; then
    echo "## Technical decision table (from analysis)"
    echo ""
    grep -A200 "## 24. Per-file technical classification table" "$ANALYSIS_MD" 2>/dev/null | head -25 || \
      grep -A200 "## 24. Absorption decision table" "$ANALYSIS_MD" 2>/dev/null | head -25 || \
      echo "_See $ANALYSIS_MD for full technical table._"
    echo ""
  fi
  echo "## Final recommendation"
  echo "$FINAL_REC"
  echo ""
  echo "## Next commands"
  echo '```bash'
  echo "bash scripts/absorption/run-change-absorption.sh analysis --source $ABSORPTION_SOURCE_REF"
  echo "bash scripts/absorption/run-change-absorption.sh absorb --source $ABSORPTION_SOURCE_REF --approval-file reports/absorption/approved-plan.json"
  echo "bash scripts/absorption/run-change-absorption.sh verify --source $ABSORPTION_SOURCE_REF --run-gates"
  echo "bash scripts/pipeline/run-local-quality-gates.sh"
  echo '```'
} >"$OUT_MD"

python3 <<PY
import json, pathlib
from datetime import datetime, timezone

def load(p):
    try:
        return json.loads(pathlib.Path(p).read_text())
    except Exception:
        return {}

analysis = load("$ANALYSIS_JSON")
absorb = load("$ABSORB_JSON")
verify = load("$VERIFY_JSON")
po = load("$PO_JSON")

doc = {
    "mode": "report",
    "product_truth_branch": "$ABSORPTION_BASE_REF",
    "source_branch": "$ABSORPTION_SOURCE_REF",
    "base_mode": "$ABSORPTION_BASE_MODE",
    "source_group": analysis.get("source_group"),
    "expected_value": analysis.get("expected_value"),
    "inferred_source_purpose": analysis.get("inferred_source_purpose"),
    "started_at": analysis.get("started_at"),
    "finished_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "verdict": analysis.get("verdict") or "INCOMPLETE",
    "executive_summary": po.get("executive_summary") or analysis.get("executive_summary", {}),
    "completion_tracking": po.get("completion_tracking") or absorb.get("completion_tracking", []),
    "deferred_items": po.get("deferred_items") or analysis.get("deferred_items", []),
    "analysis": analysis,
    "absorption": absorb,
    "verification": verify.get("verification", verify),
    "decisions": analysis.get("decisions", []),
    "conflicts_forecast": analysis.get("conflicts_forecast", []),
    "product_owner_decisions": analysis.get("deferred_items", []),
    "final_recommendation": po.get("executive_summary", {}).get("8_final_product_owner_recommendation")
        or verify.get("final_recommendation")
        or analysis.get("final_recommendation"),
}
pathlib.Path("$OUT_JSON").write_text(json.dumps(doc, indent=2) + "\n")
PY

echo "Report: $OUT_MD"
echo "JSON:   $OUT_JSON"
absorption_print_next_steps report
