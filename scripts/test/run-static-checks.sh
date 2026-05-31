#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_gate-common.sh"
cd "$REPO_PATH"
FAIL=0

gate_run "deprecated-surface-guard" bash scripts/guard/check-deprecated-surfaces.sh || FAIL=1

gate_run "frontend-route-parity" bash -c 'cd ui/one-ui-shell && npm run test:routes' || FAIL=1

gate_run "frontend-no-stub-guard" bash -c 'cd ui/one-ui-shell && npm run test:no-stubs' || FAIL=1

gate_run "frontend-launcher-guard" bash -c 'cd ui/one-ui-shell && npm run test:launchers' || FAIL=1

gate_run "registry-maturity-sync" bash scripts/test/verify-registry-maturity-sync.sh || FAIL=1

exit "$FAIL"
