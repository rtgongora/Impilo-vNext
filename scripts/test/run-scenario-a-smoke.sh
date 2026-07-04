#!/usr/bin/env bash
# Post-deploy smoke: Scenario A clinical journey steel thread against the live
# preview estate. NOT part of the default pre-deploy gates — run after a deploy
# lane completes (opt in with IMPILO_SCENARIO_SMOKE=1 to force in other lanes).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_gate-common.sh"

NS="${IMPILO_PREVIEW_NAMESPACE:-impilo-full-preview}"

if ! kubectl get deploy -n "$NS" experience-bff >/dev/null 2>&1; then
  if [[ "${IMPILO_SCENARIO_SMOKE:-0}" == "1" ]]; then
    gate_fail "scenario-a smoke: preview estate unreachable (namespace $NS)"
  fi
  gate_warn "scenario-a smoke skipped: preview estate unreachable (namespace $NS)"
  exit 0
fi

gate_run "scenario-a clinical journey" bash "$REPO_PATH/scripts/e2e/scenario-a-clinical-journey.sh"
