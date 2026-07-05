#!/usr/bin/env bash
# =============================================================================
# Adaptive Real-Time Session Suite — combined per-mode proof runner.
#
# Runs every session-mode proof against the LIVE preview estate, in the
# doctrine order (clinical first). Each proof is self-provisioning and
# idempotent; evidence tails land under reports/journeys/session-suite-<ts>/.
#
#   TELEMEDICINE       session-media-core + telehealth-patient-flow (browser)
#   RECORDING pipeline session-recording-proof.sh (egress → MinIO → doc SoR)
#   LEARNING_LIVE      learning-live-proof.sh (attendance-truth completion)
#   LEARNING_RECORDING learning-recording-proof.sh (watch-truth completion)
#   MEETING            meeting-flow.spec.ts (knock lobby → admit → publish)
#   LIVE_EVENT         live-event-stage.spec.ts (audience → stage promotion)
#
# Usage: bash scripts/e2e/run-session-suite.sh
#   PREVIEW_URL (default http://127.0.0.1) applies to every proof.
# =============================================================================
set -uo pipefail

REPO="$(cd "$(dirname "$0")/../.." && pwd)"
PREVIEW_URL="${PREVIEW_URL:-http://127.0.0.1}"
STAMP="$(date +%Y%m%d-%H%M%S)"
EVIDENCE="$REPO/reports/journeys/session-suite-$STAMP"
mkdir -p "$EVIDENCE"

FAILED=0
declare -a RESULTS=()

run_shell_proof() { # <name> <script>
  local name="$1" script="$2" log="$EVIDENCE/$1.log"
  echo ""; echo "=== $name ($script) ==="
  if PREVIEW_URL="$PREVIEW_URL" bash "$REPO/$script" > "$log" 2>&1; then
    tail -2 "$log"; RESULTS+=("PASS  $name")
  else
    tail -15 "$log"; RESULTS+=("FAIL  $name"); FAILED=1
  fi
}

run_browser_proof() { # <name> <spec...>
  local name="$1"; shift
  local log="$EVIDENCE/$name.log"
  echo ""; echo "=== $name ($*) ==="
  if (cd "$REPO/ui/one-ui-shell" && \
      PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL="$PREVIEW_URL" PREVIEW_SANDBOX_E2E=1 \
      npx playwright test "$@" --reporter=list --timeout=420000) > "$log" 2>&1; then
    grep -E "passed|✓" "$log" | tail -2; RESULTS+=("PASS  $name")
  else
    tail -15 "$log"; RESULTS+=("FAIL  $name"); FAILED=1
  fi
}

run_browser_proof telemedicine e2e/session-media-core.spec.ts e2e/telehealth-patient-flow.spec.ts
run_shell_proof   recording-pipeline scripts/e2e/session-recording-proof.sh
run_shell_proof   learning-live scripts/e2e/learning-live-proof.sh
run_shell_proof   learning-recording scripts/e2e/learning-recording-proof.sh
run_browser_proof meeting e2e/meeting-flow.spec.ts
run_browser_proof live-event e2e/live-event-stage.spec.ts

echo ""
echo "===== Session suite summary ====="
printf '%s\n' "${RESULTS[@]}" | tee "$EVIDENCE/summary.txt"
if [[ "$FAILED" -eq 0 ]]; then
  echo "PASS: all session-mode proofs green — evidence in $EVIDENCE"
else
  echo "FAIL: one or more session-mode proofs failed — evidence in $EVIDENCE" >&2
  exit 1
fi
