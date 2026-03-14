#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Observability Readiness Verification
# =============================================================================
#
# Checks that a service exposes the expected actuator endpoints and that
# the Prometheus metrics endpoint returns expected content.
#
# Usage:
#   ./scripts/ops/verify-observability.sh <host> <port>
#   ./scripts/ops/verify-observability.sh localhost 8340
#
# Exit codes:
#   0 — all checks passed
#   1 — one or more checks failed
# =============================================================================

set -euo pipefail

HOST="${1:-localhost}"
PORT="${2:-8080}"
BASE_URL="http://${HOST}:${PORT}"
PASS=0
FAIL=0

check() {
    local description="$1"
    local url="$2"
    local expected_status="${3:-200}"
    local body_contains="${4:-}"

    local http_code
    local body

    body=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${url}" 2>/dev/null) || body="000"
    http_code="${body}"

    # Re-fetch body if we need to check content
    if [[ -n "${body_contains}" && "${http_code}" == "${expected_status}" ]]; then
        body=$(curl -s --max-time 5 "${url}" 2>/dev/null) || body=""
        if echo "${body}" | grep -q "${body_contains}"; then
            echo "  PASS  ${description} (HTTP ${http_code}, contains '${body_contains}')"
            PASS=$((PASS + 1))
            return 0
        else
            echo "  FAIL  ${description} (HTTP ${http_code}, missing '${body_contains}')"
            FAIL=$((FAIL + 1))
            return 0
        fi
    fi

    if [[ "${http_code}" == "${expected_status}" ]]; then
        echo "  PASS  ${description} (HTTP ${http_code})"
        PASS=$((PASS + 1))
    else
        echo "  FAIL  ${description} (expected HTTP ${expected_status}, got ${http_code})"
        FAIL=$((FAIL + 1))
    fi
}

echo "======================================================================"
echo "Impilo Observability Readiness — ${BASE_URL}"
echo "======================================================================"
echo ""

# ── Health endpoint ──
check "GET /actuator/health returns 200" \
      "${BASE_URL}/actuator/health" \
      "200" \
      "status"

# ── Prometheus metrics endpoint ──
check "GET /actuator/prometheus returns 200" \
      "${BASE_URL}/actuator/prometheus" \
      "200" \
      "jvm_memory"

# ── Prometheus contains http_server_requests metric ──
check "Prometheus metrics include http_server_requests" \
      "${BASE_URL}/actuator/prometheus" \
      "200" \
      "http_server_requests"

# ── Prometheus contains application tag ──
check "Prometheus metrics include application tag" \
      "${BASE_URL}/actuator/prometheus" \
      "200" \
      "application"

# ── Info endpoint ──
check "GET /actuator/info returns 200" \
      "${BASE_URL}/actuator/info" \
      "200"

# ── Health liveness probe ──
check "GET /actuator/health/liveness returns 200" \
      "${BASE_URL}/actuator/health/liveness" \
      "200" \
      "UP"

# ── Health readiness probe ──
check "GET /actuator/health/readiness returns 200" \
      "${BASE_URL}/actuator/health/readiness" \
      "200" \
      "UP"

echo ""
echo "======================================================================"
echo "Results: ${PASS} passed, ${FAIL} failed"
echo "======================================================================"

if [[ ${FAIL} -gt 0 ]]; then
    exit 1
fi
exit 0
