#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_gate-common.sh"
cd "$REPO_PATH"
FAIL=0

gate_run "ui-install" bash -c 'cd ui && npm ci --ignore-scripts -w one-ui-shell' || FAIL=1
gate_run "frontend-lint" bash -c 'cd ui/one-ui-shell && npm run lint' || FAIL=1
gate_run "frontend-typecheck" bash -c 'cd ui/one-ui-shell && npm run type-check' || FAIL=1
gate_run "frontend-unit-tests" bash -c 'cd ui/one-ui-shell && npm test' || FAIL=1
gate_run "frontend-build" bash -c 'cd ui/one-ui-shell && npm run build' || FAIL=1

exit "$FAIL"
