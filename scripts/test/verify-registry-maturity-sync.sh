#!/usr/bin/env bash
# Fail only when registry-maturity.json service rows drift, not when generatedAt timestamp updates.
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"
FILE="ui/one-ui-shell/src/generated/registry-maturity.json"

node scripts/frontend/sync-registry-maturity.mjs

if git diff --quiet -- "$FILE" 2>/dev/null; then
  exit 0
fi

CONTENT_DRIFT=$(git diff -U0 -- "$FILE" | grep -E '^[+-]' | grep -v '^[+-][+-][+-]' | grep -v 'generatedAt' || true)
if [[ -z "$CONTENT_DRIFT" ]]; then
  echo "registry-maturity: generatedAt-only drift (acceptable)"
  exit 0
fi

echo "registry-maturity content drift:"
echo "$CONTENT_DRIFT"
exit 1
