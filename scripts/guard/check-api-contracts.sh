#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
BASE="$(resolve_base_ref)"
FAIL=0

DELETED=$(git diff --diff-filter=D --name-only "$BASE" HEAD -- 'contracts/' 2>/dev/null || true)
if [[ -n "$DELETED" ]]; then
  guard_fail "OpenAPI/contract files deleted:"
  echo "$DELETED"
  FAIL=1
fi

ADDED=$(git diff --diff-filter=A --name-only "$BASE" HEAD -- 'contracts/openapi/' 2>/dev/null || true)
if [[ -n "$ADDED" ]]; then
  if git diff --name-only "$BASE" HEAD | guard_filter -q 'docs/architecture/API_ENDPOINT_INVENTORY.md'; then
    guard_pass "API inventory updated for new contracts"
  else
    guard_warn "new OpenAPI files without API_ENDPOINT_INVENTORY.md update"
  fi
fi

guard_pass "API contract deletion scan complete"
exit "$FAIL"
