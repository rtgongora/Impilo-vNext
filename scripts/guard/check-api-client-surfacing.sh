#!/usr/bin/env bash
# Detect API client modules with no imports from app/features (orphan clients on changed files).
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"
BASE="$(resolve_base_ref)"
FAIL=0

NEW_CLIENTS=$(git diff --diff-filter=A --name-only "$BASE"...HEAD -- \
  'ui/one-ui-shell/src/lib/' 'ui/one-ui-shell/src/hooks/' 2>/dev/null \
  | guard_filter '\.(ts|tsx)$' || true)

if [[ -z "$NEW_CLIENTS" ]]; then
  guard_pass "no new API client/hook files to verify"
  exit 0
fi

while IFS= read -r f; do
  [[ -z "$f" ]] && continue
  base="$(basename "$f" .ts)"
  base="${base%.tsx}"
  if [[ "$base" == use* ]]; then
    hook="$base"
  else
    continue
  fi
  if git grep -l "$hook" -- 'ui/one-ui-shell/src/app' 'ui/one-ui-shell/src/features' 'ui/one-ui-shell/src/components' \
    >/dev/null 2>&1; then
    continue
  fi
  guard_warn "new hook $f has no app/features import yet (wire route or document internal-only)"
done <<< "$NEW_CLIENTS"

guard_pass "API client surfacing scan complete"
exit "$FAIL"
