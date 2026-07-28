#!/usr/bin/env bash
set -euo pipefail
# Remember where the caller actually stood, BEFORE cd-ing away. Everything below runs from
# REPO_PATH, so after the cd the tree the caller was in is unrecoverable — and a repo-path check
# made after it can never fail, which is the shape of a check that has outlived its guard.
_GUARD_CALLER_PWD="${_GUARD_CALLER_PWD:-$PWD}"
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

guard_pass() { echo "GUARD PASS  $1"; }
guard_fail() { echo "GUARD FAIL  $1"; return 1; }
guard_warn() { echo "GUARD WARN  $1"; }

# A guard must inspect the tree it was launched against.
#
# 58 scripts used to default REPO_PATH to the main checkout by absolute path, so a gate run from ANY
# worktree silently inspected /opt/impilo/repos/Impilo-vNext instead — it passed on someone else's
# code and said nothing about yours. The default is now script-relative, which fixes the common
# case: a script inside a worktree resolves to that worktree.
#
# This catches what that cannot — an explicitly exported REPO_PATH pointing somewhere other than the
# checkout the caller is standing in. That is almost always a stale export from an earlier command,
# and it produces the one outcome nobody investigates: a clean pass on the wrong tree.
guard_assert_repo_path() {
  local toplevel
  # From the CALLER's directory, not ours — we have already cd'd to REPO_PATH.
  toplevel="$(cd "${_GUARD_CALLER_PWD:-$PWD}" 2>/dev/null && git rev-parse --show-toplevel 2>/dev/null || true)"
  [[ -z "$toplevel" ]] && return 0          # not a git checkout; nothing to disagree with
  local resolved_repo resolved_top
  resolved_repo="$(cd "$REPO_PATH" 2>/dev/null && pwd -P || echo "$REPO_PATH")"
  resolved_top="$(cd "$toplevel" 2>/dev/null && pwd -P || echo "$toplevel")"
  if [[ "$resolved_repo" != "$resolved_top" ]]; then
    echo "GUARD FAIL  repo-path-disagreement"
    echo "  REPO_PATH           : $resolved_repo"
    echo "  git --show-toplevel : $resolved_top"
    echo "  The guard would inspect one tree while you are working in another, and report a"
    echo "  clean pass about code you did not change. Unset REPO_PATH, or set it to \$PWD."
    return 1
  fi
  return 0
}

# Guards that scan a whole tree must prove they reached it. A glob that matches nothing reports the
# same "no findings" as a clean tree, which is why a broken guard can sit green for weeks.
#   guard_assert_scanned "$count" "migration directories"
guard_assert_scanned() {
  local count="${1:-0}" what="${2:-items}"
  if [[ "$count" -eq 0 ]]; then
    echo "GUARD FAIL  scanned-nothing: found 0 $what"
    echo "  Matching nothing is a broken guard, not a clean tree."
    return 1
  fi
  return 0
}

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
    if git rev-parse --verify "${GUARD_BASE_REF}^{commit}" >/dev/null 2>&1; then
      echo "$GUARD_BASE_REF"
      return
    fi
  fi
  if [[ "${GITHUB_EVENT_NAME:-}" == "push" && -n "${GITHUB_EVENT_BEFORE:-}" ]]; then
    if git rev-parse --verify "${GITHUB_EVENT_BEFORE}^{commit}" >/dev/null 2>&1; then
      echo "${GITHUB_EVENT_BEFORE}"
      return
    fi
  fi
  if [[ "${GITHUB_EVENT_NAME:-}" == "pull_request" && -n "${GITHUB_BASE_REF:-}" ]]; then
    if git rev-parse --verify "origin/${GITHUB_BASE_REF}" >/dev/null 2>&1; then
      echo "origin/${GITHUB_BASE_REF}"
      return
    fi
  fi
  if git rev-parse --verify HEAD~1 >/dev/null 2>&1; then
    echo "HEAD~1"
    return
  fi
  if git rev-parse --verify origin/main >/dev/null 2>&1; then
    echo "origin/main"
    return
  fi
  if git rev-parse --verify origin/HEAD >/dev/null 2>&1; then
    echo "origin/HEAD"
    return
  fi
  echo "HEAD"
}
