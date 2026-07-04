#!/usr/bin/env bash
# Post-deploy smoke: Scenario B billing + coverage split + claim + shortfall
# card settlement against the live preview estate. NOT part of the default
# pre-deploy gates — run after a deploy lane completes (opt in with
# IMPILO_SCENARIO_SMOKE=1 to force in other lanes).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/_gate-common.sh"

NS="${IMPILO_PREVIEW_NAMESPACE:-impilo-full-preview}"

if ! kubectl get deploy -n "$NS" experience-bff >/dev/null 2>&1; then
  if [[ "${IMPILO_SCENARIO_SMOKE:-0}" == "1" ]]; then
    gate_fail "scenario-b smoke: preview estate unreachable (namespace $NS)"
  fi
  gate_warn "scenario-b smoke skipped: preview estate unreachable (namespace $NS)"
  exit 0
fi

gate_run "scenario-b billing/coverage/shortfall" bash "$REPO_PATH/test/integration/scenario-b-billing-coverage-shortfall.sh"
