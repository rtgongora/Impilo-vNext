#!/usr/bin/env bash
# scan-fake-writes.sh — Detect UI mutations that don't call real APIs
# Looks for patterns where save/submit handlers use only local state (setState, alert, console.log)
# without corresponding API calls (apiClient, fetch, axios, useMutation)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXIT_CODE=0

echo "=== Scanning for fake writes in UI pages ==="
echo ""

# Find page.tsx files with form submission patterns but no API calls
for page in $(find "$REPO_ROOT/ui" "$REPO_ROOT/apps" -name "page.tsx" -type f 2>/dev/null); do
  rel="${page#$REPO_ROOT/}"

  # Check if page has submit/save/create/approve/reject handlers
  if grep -qE "(handleSubmit|handleSave|handleCreate|handleApprove|handleReject|onSubmit|onClick.*save|onClick.*submit)" "$page"; then
    # Check if it also has real API calls
    if ! grep -qE "(apiClient\.|fetch\(|axios\.|useMutation|\.post\(|\.put\(|\.patch\(|\.delete\()" "$page"; then
      echo "FAKE-WRITE: $rel"
      echo "  Has submit/save handler but no API call detected"
      EXIT_CODE=1
    fi
  fi
done

# Check for alert() usage as error handling (should be toast/notification)
echo ""
echo "=== Scanning for alert() usage (should be proper UX) ==="
for page in $(find "$REPO_ROOT/ui" "$REPO_ROOT/apps" -name "*.tsx" -name "*.ts" -type f 2>/dev/null); do
  rel="${page#$REPO_ROOT/}"
  if grep -qn "alert(" "$page" 2>/dev/null; then
    count=$(grep -c "alert(" "$page" 2>/dev/null || true)
    echo "ALERT-USAGE: $rel ($count occurrences)"
    # alert() is a UX concern, not a blocker — don't fail CI
  fi
done

echo ""
if [ $EXIT_CODE -eq 0 ]; then
  echo "PASS: No fake writes detected"
else
  echo "FAIL: Fake writes found — pages have submit handlers without API calls"
fi

exit $EXIT_CODE
