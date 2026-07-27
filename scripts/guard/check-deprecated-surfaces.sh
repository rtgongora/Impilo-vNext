#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
BASE="$(resolve_base_ref)"
RETIRED_UI_PATHS=(
  'ui/experience/'
  'ui/ehr/'
  'ui/mushex-finance-console/'
  'ui/mushex-ops-console/'
  'ui/mushex-payer-portal/'
)
ADDED=$(git diff --diff-filter=A --name-only "$BASE" HEAD -- "${RETIRED_UI_PATHS[@]}" 2>/dev/null || true)
if [[ -n "$ADDED" ]]; then
  guard_fail "new files under retired UI sidecar paths:"
  echo "$ADDED"
  exit 1
fi
guard_pass "no new files under retired UI sidecar paths"
exit 0
