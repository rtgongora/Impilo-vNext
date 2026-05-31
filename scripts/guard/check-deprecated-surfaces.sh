#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
BASE="$(resolve_base_ref)"
ADDED=$(git diff --diff-filter=A --name-only "$BASE"...HEAD -- 'ui/experience/' 'ui/ehr/' 2>/dev/null || true)
if [[ -n "$ADDED" ]]; then
  guard_fail "new files under retired ui/experience or ui/ehr:"
  echo "$ADDED"
  exit 1
fi
guard_pass "no new files under ui/experience or ui/ehr"
exit 0
