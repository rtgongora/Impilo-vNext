#!/usr/bin/env bash
# scan-ui-thin-pages.sh — Identify thin/shell UI pages that lack API integration
set -euo pipefail

echo "=== Stub Density Audit: UI Thin Page Scan ==="
echo "Date: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo ""

echo "--- All page.tsx files and their API integration status ---"
echo ""
echo "Format: [STATUS] file (evidence)"
echo ""

find ui/ apps/ -name "page.tsx" -not -path "*/node_modules/*" -not -path "*/.next/*" 2>/dev/null | sort | while read -r page; do
  has_api=0
  has_fetch=$(grep -c "apiClient\|useQuery\|useMutation\|fetch(\|\.get(\|\.post(" "$page" 2>/dev/null || true)
  has_mock=$(grep -c "SAMPLE_\|MOCK_\|FAKE_" "$page" 2>/dev/null || true)
  has_placeholder=$(grep -ci "coming soon\|will be rendered\|placeholder\|not yet" "$page" 2>/dev/null || true)
  line_count=$(wc -l < "$page" 2>/dev/null || echo 0)

  if [ "$has_mock" -gt 0 ]; then
    echo "[MOCK-DATA] $page (${line_count}L, mock constants found)"
  elif [ "$has_placeholder" -gt 0 ] && [ "$has_fetch" -eq 0 ]; then
    echo "[SHELL]     $page (${line_count}L, placeholder text, no API)"
  elif [ "$has_fetch" -eq 0 ] && [ "$line_count" -lt 20 ]; then
    echo "[THIN]      $page (${line_count}L, no API integration)"
  elif [ "$has_fetch" -gt 0 ]; then
    echo "[REAL]      $page (${line_count}L, ${has_fetch} API calls)"
  else
    echo "[REVIEW]    $page (${line_count}L)"
  fi
done

echo ""
echo "=== Scan complete ==="
