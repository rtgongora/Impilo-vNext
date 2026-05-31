#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_gate-common.sh"
cd "$REPO_PATH"

if [[ "${PREVIEW_GATES_SKIP_E2E:-0}" == "1" ]]; then
  gate_warn "web e2e skipped (PREVIEW_GATES_SKIP_E2E=1)"
  exit 0
fi

FAIL=0
gate_run "playwright-install" bash -c 'cd ui/one-ui-shell && npx playwright install --with-deps chromium' || FAIL=1
gate_run "playwright-smoke" bash -c 'cd ui/one-ui-shell && npx playwright test --project=chromium --reporter=list e2e/citizen-life-compose.spec.ts --workers=1' || FAIL=1

exit "$FAIL"
