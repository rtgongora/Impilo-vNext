#!/usr/bin/env bash
# audit-page-fidelity.sh â€” Checks all route pages for stub vs real implementation
set -euo pipefail

echo "=== Page Fidelity Audit ==="
echo ""

PAGE_DIR="ui/one-ui-shell/src/app"
TOTAL=0
IMPLEMENTED=0
STUBS=0
MISSING=0

if [ ! -d "$PAGE_DIR" ]; then
  echo "ERROR: $PAGE_DIR not found"
  exit 1
fi

while IFS= read -r page; do
  TOTAL=$((TOTAL + 1))

  # Check if page is a stub (contains emptyStateLabel and no useState/useQuery)
  if grep -q "emptyStateLabel" "$page" 2>/dev/null; then
    has_hooks=$(grep -c "useQuery\|useMutation\|useState\|apiClient" "$page" 2>/dev/null || echo "0")
    if [ "$has_hooks" -le 1 ]; then
      STUBS=$((STUBS + 1))
      echo "  [STUB] $page"
    else
      IMPLEMENTED=$((IMPLEMENTED + 1))
      echo "  [IMPL] $page"
    fi
  elif grep -q "useQuery\|useMutation\|useState\|apiClient\|useRouter\|redirect" "$page" 2>/dev/null; then
    IMPLEMENTED=$((IMPLEMENTED + 1))
    echo "  [IMPL] $page"
  else
    # Check line count â€” very short files may be stubs
    lines=$(wc -l < "$page" 2>/dev/null || echo "0")
    if [ "$lines" -lt 20 ]; then
      STUBS=$((STUBS + 1))
      echo "  [STUB] $page ($lines lines)"
    else
      IMPLEMENTED=$((IMPLEMENTED + 1))
      echo "  [IMPL] $page ($lines lines)"
    fi
  fi
done < <(find "$PAGE_DIR" -name "page.tsx" -type f | sort)

echo ""
echo "=== Summary ==="
echo "  Total pages:    $TOTAL"
echo "  Implemented:    $IMPLEMENTED"
echo "  Stubs:          $STUBS"
echo "  Implementation: $(( IMPLEMENTED * 100 / TOTAL ))%"
echo ""

if [ "$STUBS" -gt 0 ]; then
  echo "  STATUS: INCOMPLETE â€” $STUBS stub pages remain"
  exit 1
else
  echo "  STATUS: COMPLETE â€” all pages implemented"
  exit 0
fi
