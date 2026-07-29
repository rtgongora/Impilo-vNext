#!/usr/bin/env bash
# =============================================================================
# C9 closure-run — 30-journey CITIZEN acceptance suite (live preview estate).
#
# Mirrors run-golden-journeys.sh. These are Playwright specs (the "journeys"
# project) that walk the public citizen front door exactly as an ordinary
# person would — MOSTLY anonymous, no seeded session — and prove the 10-point
# acceptance standard per journey, recording honest N/A where a point does not
# apply to a read-only public surface.
#
# Specs (ui/one-ui-shell/e2e/journeys/):
#   closure-run-frontdoor       J01–J09  welcome IA, find-care, facility, emergency, advisory
#   closure-run-participation    J10–J15  feedback, get-involved, download, provider onboarding
#   closure-run-intelligent      J16–J22  nompilo, status, language, a11y, 404
#   closure-run-continuity       J23–J30  auth continuity, drafts, back, degraded
#
# Usage (against the live preview):
#   bash scripts/e2e/run-closure-run-journeys.sh
# Env:
#   PLAYWRIGHT_BASE_URL  (default https://impilo.mohcc.gov.zw)
#   PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH  (browser binary; auto-detected if unset)
#   EVIDENCE_DIR         override evidence output dir
# =============================================================================
set -uo pipefail

REPO="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
RUN_TAG="${RUN_TAG:-$(date +%Y%m%d-%H%M%S)}"
EVIDENCE_DIR="${EVIDENCE_DIR:-$REPO/reports/journeys/closure-run-$RUN_TAG}"
BASE_URL="${PLAYWRIGHT_BASE_URL:-https://impilo.mohcc.gov.zw}"
mkdir -p "$EVIDENCE_DIR"

# Default the Chromium binary to the cached Playwright build if the caller did
# not pin one (matches the verified live-run recipe).
DEFAULT_CHROME="/home/robert/.cache/ms-playwright/chromium-1228/chrome-linux64/chrome"
if [[ -z "${PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH:-}" && -x "$DEFAULT_CHROME" ]]; then
  export PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH="$DEFAULT_CHROME"
fi

SUMMARY="$EVIDENCE_DIR/summary.txt"
{
  echo "C9 closure-run citizen acceptance — closure-run-$RUN_TAG"
  echo "target: $BASE_URL"
  echo "repo:   $(git -C "$REPO" rev-parse --short HEAD 2>/dev/null || echo unknown)"
  echo ""
} > "$SUMMARY"

cd "$REPO/ui/one-ui-shell"

# Hairpin-NAT detection: if the target host is not directly reachable but IS
# served by the local ingress, tell Chromium to resolve it to 127.0.0.1.
HOST="$(printf '%s' "$BASE_URL" | sed -E 's#https?://([^/:]+).*#\1#')"
if ! curl -skf -m 8 -o /dev/null "$BASE_URL/" 2>/dev/null; then
  if curl -skf -m 8 -o /dev/null --resolve "$HOST:443:127.0.0.1" "$BASE_URL/" 2>/dev/null; then
    export PLAYWRIGHT_HOST_RESOLVER_RULES="MAP $HOST 127.0.0.1"
    echo "NOTE: $HOST unreachable directly (hairpin NAT) — mapping to 127.0.0.1 for the browser."
  fi
fi

FAILED=0
run_spec() {
  local name="$1" spec="$2"
  local log="$EVIDENCE_DIR/$name.log"
  echo ""
  echo "=== $name ($spec) ==="
  if PLAYWRIGHT_BASE_URL="$BASE_URL" PLAYWRIGHT_SKIP_WEBSERVER=1 PREVIEW_SANDBOX_E2E=1 \
     npx playwright test "$spec" --project=journeys --workers=1 \
     --output="$EVIDENCE_DIR/artifacts/$name" >"$log" 2>&1; then
    echo "PASS  $name"
    echo "PASS  $name" >> "$SUMMARY"
  else
    FAILED=1
    echo "FAIL  $name — see $log"
    echo "FAIL  $name" >> "$SUMMARY"
    tail -n 30 "$log"
  fi
  # Per-journey pass/fail lines from the Playwright list reporter.
  grep -E '^\s*(✓|✘|ok|not ok|[0-9]+ (passed|failed))' "$log" 2>/dev/null | sed 's/^/    /' >> "$SUMMARY" || true
}

run_spec "closure-run-frontdoor"     "e2e/journeys/closure-run-frontdoor.journey.spec.ts"
run_spec "closure-run-participation" "e2e/journeys/closure-run-participation.journey.spec.ts"
run_spec "closure-run-intelligent"   "e2e/journeys/closure-run-intelligent.journey.spec.ts"
run_spec "closure-run-continuity"    "e2e/journeys/closure-run-continuity.journey.spec.ts"

echo ""
if [[ "$FAILED" == "0" ]]; then
  echo "PASS: all closure-run journeys green — evidence in $EVIDENCE_DIR"
else
  echo "FAIL: one or more closure-run journeys failed — evidence in $EVIDENCE_DIR" >&2
fi
echo "Summary: $SUMMARY"
exit "$FAILED"
