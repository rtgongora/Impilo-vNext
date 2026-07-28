#!/usr/bin/env bash
# Shared helpers for preview gate scripts.
set -euo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
GATE_LOG_DIR="${GATE_LOG_DIR:-/tmp/impilo-preview-gates}"
mkdir -p "$GATE_LOG_DIR"

gate_pass() {
  echo "PASS  $1"
}

gate_fail() {
  echo "FAIL  $1"
  return 1
}

gate_warn() {
  echo "WARN  $1"
}

gate_run() {
  local name="$1"
  shift
  local log="$GATE_LOG_DIR/$(echo "$name" | tr ' /' '__').log"
  echo ""
  echo "=== GATE: $name ==="
  echo "CMD: $*"
  if "$@" >"$log" 2>&1; then
    gate_pass "$name"
    echo "LOG: $log"
    return 0
  fi
  gate_fail "$name (see $log)"
  tail -n 40 "$log" || true
  return 1
}
