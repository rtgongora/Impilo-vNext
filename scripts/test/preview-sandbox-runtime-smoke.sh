#!/usr/bin/env bash
# Preview sandbox runtime smoke — API-level proof against Dev Preview ingress.
# Complements Playwright: ui/one-ui-shell/e2e/preview-sandbox-cohesion.spec.ts
set -euo pipefail

PREVIEW_URL="${PREVIEW_URL:-http://41.57.127.235}"
FAIL=0

pass() { echo "PASS  $*"; }
fail() { echo "FAIL  $*"; FAIL=1; }

echo "Preview sandbox runtime smoke — ${PREVIEW_URL}"

if curl -sf --max-time 15 "${PREVIEW_URL}/health/version" >/tmp/preview-health-version.json; then
  COMMIT=$(python3 -c 'import json; print(json.load(open("/tmp/preview-health-version.json")).get("commit","?")[:12])')
  pass "GET /health/version commit=${COMMIT}"
else
  fail "GET /health/version unreachable"
fi

if curl -sf --max-time 15 -o /dev/null -w "%{http_code}" "${PREVIEW_URL}/" | grep -qE '^(200|302|304|307)$'; then
  pass "GET / ingress"
else
  fail "GET / ingress"
fi

for path in /auth/login/provider-id /registry /work/vashandi/workforce /enterprise /learning/library /dags; do
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 20 "${PREVIEW_URL}${path}" || echo "000")
  if [[ "$code" =~ ^(200|302|304|307)$ ]]; then
    pass "GET ${path} status=${code}"
  else
    fail "GET ${path} status=${code}"
  fi
done

if [[ "$FAIL" -ne 0 ]]; then
  echo "Preview runtime smoke FAILED"
  exit 1
fi

echo "Preview runtime smoke PASSED"
echo "For browser proof run:"
echo "  cd ui/one-ui-shell && PREVIEW_SANDBOX_E2E=1 PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL=${PREVIEW_URL} npx playwright test e2e/preview-sandbox-cohesion.spec.ts"
