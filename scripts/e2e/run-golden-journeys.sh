#!/usr/bin/env bash
# =============================================================================
# Golden-journey acceptance run — persona-based BROWSER walkthroughs.
#
# Unlike the API steel threads (run-all-scenarios.sh), these are Playwright
# specs that log in through the real /auth/login page as the persona truth pack
# (docs/demo/persona-truth-pack.md) and prove the 10-point acceptance standard
# per journey: access, actionable buttons, form, save, state change, cross-user
# visibility, notification, audit, resume, logical end.
#
# Journeys (ui/one-ui-shell/e2e/journeys/ + the proven telehealth spec):
#   start-menu-discoverability   every persona finds their services/actions
#   fundo-author-learner         trainer publishes → learner enrols/resumes
#   clinical-day                 clerk → queue → nurse sees → doctor calls/chart
#   diagnostics-imaging          doctor orders → radiographer performs/releases
#                                → doctor inbox + notification
#   telehealth-patient-flow      citizen waits → doctor admits → live media
#
# Usage (against the live preview):
#   bash scripts/e2e/run-golden-journeys.sh            # assumes personas seeded
#   bash scripts/e2e/run-golden-journeys.sh --seed     # seeds personas first
# Env:
#   PLAYWRIGHT_BASE_URL (default https://impilo.mohcc.gov.zw)
#   PERSONA_PASSWORD    (default ImpiloTest123!)
#   EVIDENCE_DIR        override evidence output dir
# =============================================================================
set -uo pipefail

REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
RUN_TAG="golden-journeys-$(date +%Y%m%d-%H%M%S)"
EVIDENCE_DIR="${EVIDENCE_DIR:-$REPO/reports/journeys/$RUN_TAG}"
BASE_URL="${PLAYWRIGHT_BASE_URL:-https://impilo.mohcc.gov.zw}"
mkdir -p "$EVIDENCE_DIR"

if [[ "${1:-}" == "--seed" ]]; then
  echo "=== seeding persona truth pack ==="
  bash "$REPO/scripts/operator/seed-persona-truth-pack.sh" | tee "$EVIDENCE_DIR/persona-seed.log"
fi

SUMMARY="$EVIDENCE_DIR/summary.txt"
{
  echo "Golden-journey acceptance — $RUN_TAG"
  echo "target: $BASE_URL"
  echo "estate: $(curl -sf -m 10 "${PREVIEW_URL:-http://127.0.0.1}/health/version" 2>/dev/null | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("fullEstateCommit") or d.get("commit") or "unknown")' 2>/dev/null || echo unknown)"
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
  local name="$1" spec="$2" project="$3"
  local log="$EVIDENCE_DIR/$name.log"
  echo ""
  echo "=== $name ($spec) ==="
  if PLAYWRIGHT_BASE_URL="$BASE_URL" PLAYWRIGHT_SKIP_WEBSERVER=1 PREVIEW_SANDBOX_E2E=1 \
     npx playwright test "$spec" --project="$project" --workers=1 \
     --output="$EVIDENCE_DIR/artifacts/$name" >"$log" 2>&1; then
    echo "PASS  $name"
    echo "PASS  $name" >> "$SUMMARY"
  else
    FAILED=1
    echo "FAIL  $name — see $log"
    echo "FAIL  $name" >> "$SUMMARY"
    tail -n 25 "$log"
  fi
}

run_spec "start-menu-discoverability" "e2e/journeys/start-menu-discoverability.journey.spec.ts" "journeys"
run_spec "fundo-author-learner"       "e2e/journeys/fundo-author-learner.journey.spec.ts"       "journeys"
run_spec "clinical-day"               "e2e/journeys/clinical-day.journey.spec.ts"               "journeys"
run_spec "diagnostics-imaging"        "e2e/journeys/diagnostics-imaging.journey.spec.ts"        "journeys"
run_spec "governance-intake"          "e2e/journeys/governance-intake.journey.spec.ts"          "journeys"
run_spec "workforce-management"       "e2e/journeys/workforce-management.journey.spec.ts"       "journeys"
# Two-party telemedicine with real media — already proven spec, included as the
# teleconsult golden journey (citizen waits tokenless → doctor admits → media).
run_spec "telehealth-patient-flow"    "e2e/telehealth-patient-flow.spec.ts"                     "chromium"

echo ""
if [[ "$FAILED" == "0" ]]; then
  echo "PASS: all golden journeys green — evidence in $EVIDENCE_DIR"
else
  echo "FAIL: one or more golden journeys failed — evidence in $EVIDENCE_DIR" >&2
fi
exit "$FAILED"
