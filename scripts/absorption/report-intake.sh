#!/usr/bin/env bash
# Consolidate analysis, absorption, and verification into final intake report.
set -euo pipefail

REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
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
OUT_MD="$(absorption_report_path latest-intake-report.md)"
OUT_JSON="$(absorption_report_path latest-intake-report.json)"

load_json() {
  local f="$1"
  [[ -f "$f" ]] && cat "$f" || echo '{}'
}

FINAL_REC="NEEDS_ANALYSIS"
if [[ -f "$VERIFY_JSON" ]]; then
  FINAL_REC="$(python3 -c "import json; print(json.load(open('$VERIFY_JSON')).get('final_recommendation','VERIFY_COMPLETE'))" 2>/dev/null || echo NEEDS_ANALYSIS)"
elif [[ -f "$ANALYSIS_JSON" ]]; then
  FINAL_REC="$(python3 -c "import json; print(json.load(open('$ANALYSIS_JSON')).get('final_recommendation','NEEDS_ANALYSIS'))" 2>/dev/null || echo NEEDS_ANALYSIS)"
fi

{
  echo "# Change Absorption — Final Intake Report"
  echo ""
  echo "- **Generated:** $(absorption_now)"
  echo ""
  echo "## 1. Executive summary"
  echo "Intake session for source \`$ABSORPTION_SOURCE_REF\` against Product Truth \`$ABSORPTION_BASE_REF\`."
  echo "Base mode: **$ABSORPTION_BASE_MODE**."
  echo ""
  if [[ -f "$ANALYSIS_JSON" ]]; then
    echo "## Analysis snapshot"
    python3 -c "import json; d=json.load(open('$ANALYSIS_JSON')); print('- Verdict:', d.get('verdict')); print('- Inferred purpose:', d.get('inferred_source_purpose'))"
  else
    echo "_No analysis report — run analysis mode first._"
  fi
  echo ""
  if [[ -f "$ABSORB_MD" ]]; then
    echo "## Absorption snapshot"
    head -30 "$ABSORB_MD"
  else
    echo "_No absorption report yet._"
  fi
  echo ""
  if [[ -f "$VERIFY_MD" ]]; then
    echo "## Verification snapshot"
    head -40 "$VERIFY_MD"
  else
    echo "_No verification report yet._"
  fi
  echo ""
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
    "analysis": analysis,
    "absorption": absorb,
    "verification": verify.get("verification", verify),
    "decisions": analysis.get("decisions", []),
    "conflicts_forecast": analysis.get("conflicts_forecast", []),
    "product_owner_decisions": analysis.get("product_owner_decisions", []),
    "final_recommendation": verify.get("final_recommendation") or analysis.get("final_recommendation"),
}
pathlib.Path("$OUT_JSON").write_text(json.dumps(doc, indent=2) + "\n")
PY

echo "Report: $OUT_MD"
echo "JSON:   $OUT_JSON"
absorption_print_next_steps report
