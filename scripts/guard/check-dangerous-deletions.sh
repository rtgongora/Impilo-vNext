#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
BASE="$(resolve_base_ref)"
FAIL=0

DELETED=$(git diff --diff-filter=D --name-only "$BASE" HEAD 2>/dev/null || true)
if [[ -z "$DELETED" ]]; then
  guard_pass "no deleted files vs $BASE"
  exit 0
fi

echo "Deleted files vs $BASE:"
echo "$DELETED"

while IFS= read -r f; do
  [[ -z "$f" ]] && continue
  case "$f" in
    # Test sources first: a deleted test is a warning, not a block. Matched by the
    # Maven/JS test roots rather than a bare *test* glob, which also caught production
    # names like LatestReading.java and would have downgraded a real deletion.
    */src/test/*|*/__tests__/*|*.test.ts|*.test.tsx|*.spec.ts|*.spec.tsx)
      guard_warn "test file deleted: $f" ;;
    services/*/*|services/*)
      guard_fail "service path deleted: $f"; FAIL=1 ;;
    ui/one-ui-shell/src/app/*)
      guard_fail "frontend route deleted: $f"; FAIL=1 ;;
    contracts/openapi/*|contracts/*)
      guard_fail "contract deleted: $f"; FAIL=1 ;;
    scripts/deploy/*|.github/workflows/*)
      guard_fail "deploy/CI script deleted: $f"; FAIL=1 ;;
    docs/architecture/*|docs/doctrine/*)
      guard_warn "architecture doc deleted: $f" ;;
  esac
done <<< "$DELETED"

LARGE=$(echo "$DELETED" | wc -l)
if [[ "$LARGE" -gt 20 ]]; then
  guard_warn "large deletion set ($LARGE files) — require explicit justification in PR/commit message"
fi

exit "$FAIL"
