#!/usr/bin/env bash
# Preview sandbox Playwright — cohesion + persistence proofs against live ingress.
set -euo pipefail
source "$(dirname "$0")/_gate-common.sh"
cd "$REPO_PATH"

PREVIEW_URL="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
# The gate runs on the VM / inside the cluster LAN, where the PUBLIC URL hairpin-NATs. Reach the app
# via the node-local ingress; PREVIEW_URL remains the documented external URL.
PREVIEW_INTERNAL_URL="${PREVIEW_INTERNAL_URL:-http://127.0.0.1}"

if [[ "${PREVIEW_SMOKE_SKIP:-0}" == "1" ]]; then
  gate_warn "preview persistence E2E skipped (PREVIEW_SMOKE_SKIP=1)"
  exit 0
fi

if ! curl -sf --max-time 10 "${PREVIEW_INTERNAL_URL}/health/version" >/dev/null; then
  gate_fail "preview unreachable at ${PREVIEW_INTERNAL_URL}/health/version — cannot run persistence E2E"
fi

gate_run "playwright-chromium-install" bash -c 'cd ui/one-ui-shell && npx playwright install chromium'

gate_run "preview-persistence-e2e" bash -c "
  cd ui/one-ui-shell && \
  PREVIEW_SANDBOX_E2E=1 \
  PLAYWRIGHT_SKIP_WEBSERVER=1 \
  PLAYWRIGHT_BASE_URL='${PREVIEW_INTERNAL_URL}' \
  npx playwright test \
    e2e/preview-sandbox-persistence.spec.ts \
    e2e/preview-sandbox-cohesion.spec.ts \
    e2e/preview-sandbox-journeys.spec.ts \
    --project=chromium \
    --reporter=list \
    --workers=1
"
