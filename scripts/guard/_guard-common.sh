#!/usr/bin/env bash
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"

guard_pass() { echo "GUARD PASS  $1"; }
guard_fail() { echo "GUARD FAIL  $1"; return 1; }
guard_warn() { echo "GUARD WARN  $1"; }

# Prefer ripgrep when installed; grep -E works on minimal VMs (stdin or files).
guard_filter() {
  local quiet=0
  [[ "${1:-}" == "-q" ]] && { quiet=1; shift; }
  local pat="$1"
  shift
  if command -v rg >/dev/null 2>&1; then
    [[ $quiet -eq 1 ]] && rg -q "$pat" "$@" || rg "$pat" "$@"
  else
    [[ $quiet -eq 1 ]] && grep -qE "$pat" "$@" || grep -E "$pat" "$@"
  fi
}

resolve_base_ref() {
  if [[ -n "${GUARD_BASE_REF:-}" ]]; then
    echo "$GUARD_BASE_REF"
    return
  fi
  if [[ "${GITHUB_EVENT_NAME:-}" == "pull_request" && -n "${GITHUB_BASE_REF:-}" ]]; then
    echo "origin/${GITHUB_BASE_REF}"
    return
  fi
  if git rev-parse --verify origin/main >/dev/null 2>&1; then
    echo "origin/main"
    return
  fi
  echo "HEAD~1"
}
