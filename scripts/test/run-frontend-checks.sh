#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_gate-common.sh"
cd "$REPO_PATH"
FAIL=0

gate_run "ui-install" bash -c 'cd ui && npm ci --ignore-scripts -w one-ui-shell' || FAIL=1
gate_run "frontend-lint" bash -c 'cd ui/one-ui-shell && npm run lint' || FAIL=1
gate_run "frontend-typecheck" bash -c 'cd ui/one-ui-shell && npm run type-check' || FAIL=1
# The Playwright specs are excluded from the app's tsconfig, so they were type-checked by nothing:
# a spec that could not compile reported zero failures instead of an error. Separate pass, because
# pulling test code into the app's config would let a test-only type error fail the production build.
gate_run "frontend-typecheck-e2e" bash -c 'cd ui/one-ui-shell && npm run test:typecheck-e2e' || FAIL=1
gate_run "frontend-unit-tests" bash -c 'cd ui/one-ui-shell && npm test' || FAIL=1
gate_run "frontend-build" bash -c 'cd ui/one-ui-shell && npm run build' || FAIL=1

exit "$FAIL"
