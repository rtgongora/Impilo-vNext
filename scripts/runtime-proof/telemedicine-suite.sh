#!/usr/bin/env bash
# ============================================================================
# Telemedicine runtime-proof SUITE (TM-B20).
#
# One CI-runnable entry point that runs every telemedicine runtime-proof script
# in sequence and aggregates their verdicts. Each member script self-rigs its own
# infra (docker postgres/kafka/redis + freshly packaged jars) and asserts against
# the live rig with psql/event/HTTP checks + negative paths — the honest pattern
# (never a mock in the proof path).
#
# A member is SKIPPED (not FAILED) when its required jars are absent, so the suite
# degrades honestly on a partial checkout; CI packages the jars first (see below)
# and then every member runs for real.
#
# CI usage:
#   mvn -q -pl services/pct-service -am -DskipTests package
#   mvn -q -pl services/vito-service,services/experience-bff,services/costing-engine-service -am -DskipTests package
#   bash scripts/runtime-proof/telemedicine-suite.sh
#
# Exit non-zero if any member that RAN reported a failure.
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO"
SUITE_EV=${SUITE_EV:-/tmp/telemedicine-suite-evidence}; mkdir -p "$SUITE_EV"

# member script | jars it needs (space-separated) | one-line description
MEMBERS=(
  "scripts/runtime-proof/teleconsult-hardening-journeys.sh|pct-service|A1 guard + B4 timer + TM-B20 event versioning (J-TH-1..4)"
  "scripts/runtime-proof/virtual-care-journeys.sh|vito-service pct-service costing-engine-service experience-bff|citizen virtual-care spine + consent/routability negatives (J-VC-1..3)"
  "scripts/runtime-proof/virtual-care-full-journeys.sh|vito-service pct-service tuso-service experience-bff|sovereign VH directory + activation + SLA + duty read-back (J-VC-4..7)"
)

have_jar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }

RAN=0; SUITE_FAIL=0; SUITE_SKIP=0
echo "=================================================================="
echo " TELEMEDICINE RUNTIME-PROOF SUITE (TM-B20)"
echo "=================================================================="
for m in "${MEMBERS[@]}"; do
  IFS='|' read -r script jars desc <<< "$m"
  missing=""
  for j in $jars; do [ -n "$(have_jar "$j")" ] || missing="$missing $j"; done
  echo ""
  echo ">>> $script"
  echo "    $desc"
  if [ -n "$missing" ]; then
    echo "    SKIP — missing jar(s):$missing (run mvn package for these services)"
    SUITE_SKIP=$((SUITE_SKIP+1)); continue
  fi
  RAN=$((RAN+1))
  logf="$SUITE_EV/$(basename "$script").log"
  if EV="$SUITE_EV/$(basename "$script" .sh)" bash "$script" > "$logf" 2>&1; then
    echo "    PASS — $(grep RESULT "$logf" | tail -1)"
  else
    echo "    FAIL — $(grep RESULT "$logf" | tail -1) (see $logf)"
    SUITE_FAIL=$((SUITE_FAIL+1))
  fi
done

echo ""
echo "=================================================================="
echo " SUITE RESULT: ran=$RAN failed=$SUITE_FAIL skipped=$SUITE_SKIP"
echo "=================================================================="
[ "$SUITE_FAIL" = 0 ]
