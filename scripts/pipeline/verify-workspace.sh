#!/usr/bin/env bash
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
EXPECTED_BRANCH="${EXPECTED_BRANCH:-claude/staging-ux-orchestration-remediation-Yypyl}"

cd "$REPO_PATH"
echo "path: $(pwd)"
echo "hostname: $(hostname)"
echo "user: $(whoami)"
echo "branch: $(git branch --show-current)"
echo "commit: $(git rev-parse HEAD) ($(git rev-parse --short HEAD))"
echo "remote:"
git remote -v | head -2

if [[ "$(pwd)" != "$REPO_PATH" ]]; then
  echo "FAIL: not in expected repo path $REPO_PATH"
  exit 1
fi

if ! git remote get-url origin | grep -q 'Impilo-vNext'; then
  echo "WARN: origin remote may not be Impilo-vNext"
fi

BR="$(git branch --show-current)"
if [[ "$BR" != "$EXPECTED_BRANCH" && "${PIPELINE_ALLOW_ANY_BRANCH:-0}" != "1" ]]; then
  echo "WARN: branch is $BR (expected $EXPECTED_BRANCH)"
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "WARN: working tree has uncommitted changes"
  git status -sb
fi

echo "workspace: OK"
exit 0
