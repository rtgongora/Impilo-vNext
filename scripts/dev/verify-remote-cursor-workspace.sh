#!/usr/bin/env bash
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
EXPECTED_BRANCH="${EXPECTED_BRANCH:-claude/staging-ux-orchestration-remediation-Yypyl}"
FAIL=0

warn() { echo "WARN  $*"; }
fail() { echo "FAIL  $*"; FAIL=1; }
ok() { echo "OK    $*"; }

echo "=== Cursor Remote SSH workspace verification ==="
echo "Host: $(hostname)"
echo "User: $(whoami)"
echo "Expected repo: $REPO_PATH"

if [[ "$(pwd)" == "$REPO_PATH"* ]]; then
  ok "shell cwd under repo path"
else
  warn "open Cursor folder at $REPO_PATH (current: $(pwd))"
fi

if [[ -d "$REPO_PATH/.git" ]]; then
  ok "git repo present"
  branch="$(git -C "$REPO_PATH" branch --show-current)"
  if [[ "$branch" == "$EXPECTED_BRANCH" ]]; then
    ok "branch $branch"
  else
    warn "branch is $branch (expected $EXPECTED_BRANCH)"
  fi
  ok "commit $(git -C "$REPO_PATH" rev-parse --short HEAD)"
else
  fail "repo missing at $REPO_PATH"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "$SCRIPT_DIR/check-tools.sh" || FAIL=1

if [[ -d "$REPO_PATH/ui/node_modules" ]]; then
  ok "ui/node_modules present"
else
  warn "ui/node_modules missing — run scripts/dev/install-dependencies.sh"
fi

if kubectl get ns impilo-preview >/dev/null 2>&1; then
  ok "impilo-preview namespace exists"
  running="$(kubectl get pods -n impilo-preview --no-headers 2>/dev/null | grep -c Running || true)"
  echo "      Running pods in impilo-preview: $running"
else
  warn "impilo-preview namespace missing"
fi

echo
if [[ "$FAIL" -eq 0 ]]; then
  echo "Workspace ready for Cursor Remote SSH development."
else
  echo "Workspace verification found issues — see messages above."
fi
exit "$FAIL"
