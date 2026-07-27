#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
BASE="$(resolve_base_ref)"
OUT="${GUARD_SUMMARY_FILE:-/tmp/impilo-change-summary.txt}"

{
  echo "Impilo change summary"
  echo "Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "Base: $BASE"
  echo "Head: $(git rev-parse --short HEAD)"
  echo "Branch: $(git branch --show-current)"
  echo ""
  echo "=== Files changed ==="
  git diff --stat "$BASE" HEAD 2>/dev/null || true
  echo ""
  echo "=== Added ==="
  git diff --diff-filter=A --name-only "$BASE" HEAD 2>/dev/null | head -100 || true
  echo ""
  echo "=== Deleted ==="
  git diff --diff-filter=D --name-only "$BASE" HEAD 2>/dev/null | head -100 || true
} | tee "$OUT"

echo "Summary written: $OUT"
