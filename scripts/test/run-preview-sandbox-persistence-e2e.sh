#!/usr/bin/env bash
# Preview sandbox Playwright — cohesion + persistence proofs against live ingress.
set -euo pipefail
source "$(dirname "$0")/_gate-common.sh"
cd "$REPO_PATH"

PREVIEW_URL="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
# Health check stays on the node-local HTTP ingress (hairpin NAT blocks the public IP).
PREVIEW_INTERNAL_URL="${PREVIEW_INTERNAL_URL:-http://127.0.0.1}"
# Browser proofs need HTTPS so Chromium can store `__Host-impilo_session` cookies.
PREVIEW_BROWSER_URL="${PREVIEW_BROWSER_URL:-$PREVIEW_URL}"

if [[ "${PREVIEW_SMOKE_SKIP:-0}" == "1" ]]; then
  gate_warn "preview persistence E2E skipped (PREVIEW_SMOKE_SKIP=1)"
  exit 0
fi

if ! curl -sf --max-time 10 "${PREVIEW_INTERNAL_URL}/health/version" >/dev/null; then
  gate_fail "preview unreachable at ${PREVIEW_INTERNAL_URL}/health/version — cannot run persistence E2E"
fi

HOST="$(printf '%s' "$PREVIEW_BROWSER_URL" | sed -E 's#https?://([^/:]+).*#\1#')"
# Hairpin NAT: map the public hostname to local ingress inside Chromium.
if ! curl -skf -m 8 -o /dev/null "$PREVIEW_BROWSER_URL/health/version" 2>/dev/null; then
  if curl -skf -m 8 -o /dev/null --resolve "$HOST:443:127.0.0.1" "$PREVIEW_BROWSER_URL/health/version" 2>/dev/null; then
    export PLAYWRIGHT_HOST_RESOLVER_RULES="MAP $HOST 127.0.0.1"
    gate_warn "hairpin NAT — Chromium host-resolver maps $HOST → 127.0.0.1"
  fi
fi

gate_run "playwright-chromium-install" bash -c 'cd ui/one-ui-shell && npx playwright install chromium'

gate_run "preview-persistence-e2e" bash -c "
  cd ui/one-ui-shell && \
  PREVIEW_SANDBOX_E2E=1 \
  PLAYWRIGHT_SKIP_WEBSERVER=1 \
  PLAYWRIGHT_BASE_URL='${PREVIEW_BROWSER_URL}' \
  PREVIEW_INTERNAL_URL='${PREVIEW_INTERNAL_URL}' \
  PLAYWRIGHT_HOST_RESOLVER_RULES='${PLAYWRIGHT_HOST_RESOLVER_RULES:-}' \
  npx playwright test \
    e2e/preview-sandbox-persistence.spec.ts \
    e2e/preview-sandbox-cohesion.spec.ts \
    e2e/preview-sandbox-journeys.spec.ts \
    --project=chromium \
    --reporter=list \
    --workers=1
"
