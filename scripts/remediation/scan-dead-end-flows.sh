#!/usr/bin/env bash
# scan-dead-end-flows.sh — Detect pages that render but have no data fetch or workflow
# A dead-end page shows static content with no API integration
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXIT_CODE=0

echo "=== Scanning for dead-end flow pages ==="
echo ""

for page in $(find "$REPO_ROOT/ui" "$REPO_ROOT/apps" -name "page.tsx" -type f 2>/dev/null); do
  rel="${page#$REPO_ROOT/}"

  # Skip layout files and loading files
  if echo "$rel" | grep -qE "(layout|loading|error|not-found)\.tsx$"; then
    continue
  fi

  # Check if page has any data fetching pattern
  has_fetch=false
  if grep -qE "(useQuery|useMutation|useEffect|apiClient|fetch\(|axios|\.get\(|\.post\(|loadData|load[A-Z])" "$page"; then
    has_fetch=true
  fi

  # Check if page is just static content (only JSX, no state/effects)
  has_state=false
  if grep -qE "(useState|useReducer|useEffect|useCallback|useMemo)" "$page"; then
    has_state=true
  fi

  # Page is dead-end if it has no fetch and no state management
  if [ "$has_fetch" = false ] && [ "$has_state" = false ]; then
    # Check if it's a hub/layout page (links only) — these are acceptable
    link_count=$(grep -c "href=" "$page" 2>/dev/null || echo "0")
    if [ "$link_count" -gt 2 ]; then
      echo "HUB-PAGE: $rel (navigation only, $link_count links — acceptable)"
    else
      echo "DEAD-END: $rel"
      echo "  No data fetch, no state management, not a hub page"
      EXIT_CODE=1
    fi
  fi
done

echo ""
if [ $EXIT_CODE -eq 0 ]; then
  echo "PASS: No dead-end flow pages detected"
else
  echo "FAIL: Dead-end pages found — static content with no workflow"
fi

exit $EXIT_CODE
