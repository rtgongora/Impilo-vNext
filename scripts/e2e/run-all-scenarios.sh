#!/usr/bin/env bash
# =============================================================================
# Combined journey proof — Scenarios A (clinical), B (billing+coverage+card),
# C (Fundo learner). Runs each steel thread against the LIVE preview estate and
# captures the full transcripts as evidence under reports/journeys/.
#
# Scenario D (coverage incl. failure paths) is proven inside Scenario B
# (ELIGIBLE split + INELIGIBLE:NO_COVER negative path).
#
# Usage: bash scripts/e2e/run-all-scenarios.sh
#   EVIDENCE_DIR=...   override evidence output dir
# =============================================================================
set -uo pipefail

REPO="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
RUN_TAG="journeys-$(date +%Y%m%d-%H%M%S)"
EVIDENCE_DIR="${EVIDENCE_DIR:-$REPO/reports/journeys/$RUN_TAG}"
mkdir -p "$EVIDENCE_DIR"

declare -A SCRIPTS=(
  [scenario-a]="$REPO/scripts/e2e/scenario-a-clinical-journey.sh"
  [scenario-b]="$REPO/test/integration/scenario-b-billing-coverage-shortfall.sh"
  [scenario-c]="$REPO/scripts/e2e/fundo-learner-journey.sh"
)
ORDER=(scenario-a scenario-b scenario-c)

FAILED=0
SUMMARY="$EVIDENCE_DIR/summary.txt"
{
  echo "Combined journey proof — $RUN_TAG"
  echo "estate: $(curl -sf -m 10 "${PREVIEW_URL:-http://127.0.0.1}/health/version" 2>/dev/null | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("fullEstateCommit") or d.get("commit") or "unknown")' 2>/dev/null || echo unknown)"
  echo ""
} > "$SUMMARY"

for name in "${ORDER[@]}"; do
  script="${SCRIPTS[$name]}"
  log="$EVIDENCE_DIR/$name.log"
  echo ""
  echo "=== $name ($script) ==="
  if bash "$script" >"$log" 2>&1; then
    verdict=$(grep -E "^PASS:" "$log" | tail -1)
    echo "PASS  $name — ${verdict:-see $log}"
    echo "PASS  $name — ${verdict:-}" >> "$SUMMARY"
  else
    FAILED=1
    echo "FAIL  $name — see $log"
    echo "FAIL  $name" >> "$SUMMARY"
    tail -n 20 "$log"
  fi
done

echo ""
if [[ "$FAILED" == "0" ]]; then
  echo "PASS: all scenarios green — evidence in $EVIDENCE_DIR"
else
  echo "FAIL: one or more scenarios failed — evidence in $EVIDENCE_DIR" >&2
fi
exit "$FAILED"
