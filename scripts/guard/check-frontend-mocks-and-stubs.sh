#!/usr/bin/env bash
# Blocks production-path mocks/stubs per GAP_CLOSURE_RULES (delegates to one-ui-shell guard).
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

if [[ ! -f ui/one-ui-shell/package.json ]]; then
  guard_fail "one-ui-shell not found"
  exit 1
fi

if [[ ! -d ui/node_modules ]]; then
  echo "Installing UI deps for no-stub guard..."
  (cd ui && npm ci --ignore-scripts -w one-ui-shell) >/dev/null 2>&1 || true
fi

if (cd ui/one-ui-shell && npm run test:no-stubs); then
  guard_pass "no production mocks/stubs in one-ui-shell"
  exit 0
fi

guard_fail "no-stub guard failed — see GAP_CLOSURE_RULES.md"
exit 1
