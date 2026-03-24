#!/usr/bin/env bash
# scan-thin-route-implementations.sh — Detect pages with polished UI but no substance
# Looks for pages that have cards/tables/badges but no real data behind them
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXIT_CODE=0

echo "=== Scanning for thin route implementations ==="
echo ""

for page in $(find "$REPO_ROOT/ui" "$REPO_ROOT/apps" -name "page.tsx" -type f 2>/dev/null); do
  rel="${page#$REPO_ROOT/}"

  # Skip layout/loading/error pages
  if echo "$rel" | grep -qE "(layout|loading|error|not-found)\.tsx$"; then
    continue
  fi

  # Count "UI richness" indicators
  ui_indicators=0
  for pattern in "className=" "table" "thead" "tbody" "badge" "card" "grid" "flex"; do
    if grep -q "$pattern" "$page" 2>/dev/null; then
      ui_indicators=$((ui_indicators + 1))
    fi
  done

  # Count "real data" indicators
  data_indicators=0
  for pattern in "useQuery" "useEffect" "apiClient" "fetch(" "useState" "\.get(" "\.post("; do
    if grep -q "$pattern" "$page" 2>/dev/null; then
      data_indicators=$((data_indicators + 1))
    fi
  done

  # Flag pages with rich UI but no data
  if [ "$ui_indicators" -ge 4 ] && [ "$data_indicators" -eq 0 ]; then
    echo "THIN-ROUTE: $rel"
    echo "  UI indicators: $ui_indicators, Data indicators: $data_indicators"
    EXIT_CODE=1
  fi
done

# Check for hardcoded sample/mock data arrays
echo ""
echo "=== Scanning for hardcoded mock data ==="
for page in $(find "$REPO_ROOT/ui" "$REPO_ROOT/apps" -name "page.tsx" -type f 2>/dev/null); do
  rel="${page#$REPO_ROOT/}"
  if grep -qE "(SAMPLE_|MOCK_|DEMO_|FAKE_|DUMMY_|HARDCODED_)" "$page" 2>/dev/null; then
    echo "MOCK-DATA: $rel"
    grep -n "(SAMPLE_\|MOCK_\|DEMO_\|FAKE_\|DUMMY_\|HARDCODED_)" "$page" 2>/dev/null | head -5
    EXIT_CODE=1
  fi
done

echo ""
if [ $EXIT_CODE -eq 0 ]; then
  echo "PASS: No thin route implementations detected"
else
  echo "FAIL: Thin routes or mock data found"
fi

exit $EXIT_CODE
