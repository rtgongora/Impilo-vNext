#!/usr/bin/env bash
# Single source of truth for "are the VM quality gates green at the current HEAD?".
#
# Exit 0  -> reports/pipeline/latest-summary.json exists, was produced at the current
#            HEAD commit, and recorded a passing verdict (vm_pipeline_passed, verdict=PASS,
#            no blocking failure).
# Exit 1  -> gates missing, stale (ran at a different commit), or failing.
# Exit 3  -> environment error (no repo / no git).
#
# Used by the deploy guards and by the .cursor beforeShellExecution hook so a preview
# deploy can never be performed (script OR raw kubectl/helm) without fresh passing gates.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH" 2>/dev/null || { echo "require-fresh-gates: repo not found at $REPO_PATH"; exit 3; }

REPORT="$REPO_PATH/reports/pipeline/latest-summary.json"
HEAD_SHA="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
[[ "$HEAD_SHA" != "unknown" ]] || { echo "require-fresh-gates: cannot resolve HEAD"; exit 3; }

if [[ ! -f "$REPORT" ]]; then
  echo "require-fresh-gates: no gate report at $REPORT"
  echo "  Run: bash scripts/pipeline/run-local-quality-gates.sh"
  exit 1
fi

python3 - "$REPORT" "$HEAD_SHA" <<'PY'
import json, sys

report, head = sys.argv[1], sys.argv[2]
try:
    d = json.load(open(report))
except Exception as e:  # noqa: BLE001
    print(f"require-fresh-gates: cannot read gate report: {e}")
    sys.exit(1)

commit = d.get("commit")
verdict = str(d.get("verdict", "")).upper()
deploy_ok = bool(d.get("vm_pipeline_passed")) and not d.get("blocking_failure")
passed = deploy_ok and (
    verdict == "PASS"
    or (verdict == "PASS WITH ADVISORY WARNINGS" and d.get("deploy_recommended"))
)

if commit != head:
    print(
        f"require-fresh-gates: STALE — gates ran at {str(commit)[:8]} but HEAD is {head[:8]}.\n"
        "  Re-run: bash scripts/pipeline/run-local-quality-gates.sh"
    )
    sys.exit(1)
if not passed:
    print(
        f"require-fresh-gates: gates did NOT pass (verdict={d.get('verdict')}, "
        f"blocking_failure={d.get('blocking_failure')}, vm_pipeline_passed={d.get('vm_pipeline_passed')})."
    )
    sys.exit(1)

print(f"require-fresh-gates: OK — VM gates PASS at {head[:8]}")
PY
