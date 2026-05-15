#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ARTIFACT_DIR="${ENTERPRISE_E2E_ARTIFACT_DIR:-$ROOT_DIR/.tmp/enterprise-e2e-artifacts}"
WORK_DIR="${ENTERPRISE_E2E_WORK_DIR:-$ROOT_DIR/.tmp/enterprise-e2e-work}"
STARTUP_TIMEOUT_SECONDS="${ENTERPRISE_E2E_STARTUP_TIMEOUT_SECONDS:-180}"

EXPERIENCE_BFF_BASE_URL="${EXPERIENCE_BFF_BASE_URL:-http://localhost:8160}"
MUSHEX_BASE_URL="${MUSHEX_BASE_URL:-http://localhost:8086}"
COSTA_BASE_URL="${COSTA_BASE_URL:-http://localhost:8087}"
COVERAGE_BASE_URL="${COVERAGE_BASE_URL:-http://localhost:8088}"
GL_BASE_URL="${GL_BASE_URL:-http://localhost:8089}"
PROCUREMENT_BASE_URL="${PROCUREMENT_BASE_URL:-http://localhost:8090}"
HR_PAYROLL_BASE_URL="${HR_PAYROLL_BASE_URL:-http://localhost:8091}"
MSIKA_FLOW_BASE_URL="${MSIKA_FLOW_BASE_URL:-http://localhost:8092}"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "[enterprise-e2e] Missing required command: $1" >&2; exit 1; }
}

wait_http_up() {
  local name="$1"
  local url="$2"
  local timeout="${3:-$STARTUP_TIMEOUT_SECONDS}"
  local started_at now elapsed

  started_at="$(date +%s)"
  while true; do
    if curl -fsS "$url" | python -c 'import json,sys; print("ok" if json.load(sys.stdin).get("status")=="UP" else "bad")' | rg -q '^ok$'; then
      echo "[enterprise-e2e] Health endpoint UP: $name ($url)"
      return 0
    fi
    now="$(date +%s)"
    elapsed=$((now - started_at))
    if (( elapsed >= timeout )); then
      echo "[enterprise-e2e] Timed out waiting for health endpoint: $name ($url)" >&2
      return 1
    fi
    sleep 2
  done
}

assert_typed_501() {
  local method="$1"
  local url="$2"
  local expected_code="$3"
  local output_file="$4"

  local status
  if [[ "$method" == "POST" ]]; then
    status="$(curl -sS -o "$output_file" -w "%{http_code}" -X POST "$url" \
      -H "Content-Type: application/json" \
      -H "X-Request-Id: req-enterprise-e2e-501" \
      -H "X-Correlation-Id: corr-enterprise-e2e-501" \
      -d '{}')"
  else
    status="$(curl -sS -o "$output_file" -w "%{http_code}" -X GET "$url" \
      -H "X-Request-Id: req-enterprise-e2e-501" \
      -H "X-Correlation-Id: corr-enterprise-e2e-501")"
  fi

  [[ "$status" == "501" ]] || {
    echo "[enterprise-e2e] Expected HTTP 501 from $url, got $status" >&2
    return 1
  }

  python - "$output_file" "$expected_code" <<'PY'
import json, sys
payload = json.load(open(sys.argv[1], encoding="utf-8"))
expected_code = sys.argv[2]
assert "error" in payload, payload
assert payload["error"].get("code") == expected_code, payload
assert "meta" in payload, payload
PY
}

assert_non_5xx() {
  local url="$1"
  local output_file="$2"
  local status
  status="$(curl -sS -o "$output_file" -w "%{http_code}" -X GET "$url" \
    -H "X-Request-Id: req-enterprise-e2e-live" \
    -H "X-Correlation-Id: corr-enterprise-e2e-live")"

  if [[ "$status" =~ ^5 ]]; then
    echo "[enterprise-e2e] Expected non-5xx from $url, got $status" >&2
    return 1
  fi
}

mkdir -p "$ARTIFACT_DIR" "$WORK_DIR"
require_cmd curl
require_cmd python
require_cmd rg

echo "[enterprise-e2e] Waiting for enterprise service health endpoints..."
wait_http_up "experience-bff" "$EXPERIENCE_BFF_BASE_URL/actuator/health"
wait_http_up "mushex-service" "$MUSHEX_BASE_URL/actuator/health"
wait_http_up "costing-engine-service" "$COSTA_BASE_URL/actuator/health"
wait_http_up "coverage-service" "$COVERAGE_BASE_URL/actuator/health"
wait_http_up "general-ledger-service" "$GL_BASE_URL/actuator/health"
wait_http_up "procurement-service" "$PROCUREMENT_BASE_URL/actuator/health"
wait_http_up "hr-payroll-service" "$HR_PAYROLL_BASE_URL/actuator/health"
wait_http_up "msika-flow-service" "$MSIKA_FLOW_BASE_URL/actuator/health"

echo "[enterprise-e2e] Verifying explicit fail-close for unwired billing shortcuts..."
assert_typed_501 "GET" "$EXPERIENCE_BFF_BASE_URL/internal/v1/mobile/provider/billing/charges" \
  "BILLING_ROUTE_UNAVAILABLE" "$WORK_DIR/mobile-billing-charges-501.json"
assert_typed_501 "POST" "$EXPERIENCE_BFF_BASE_URL/internal/v1/mobile/provider/billing/charge" \
  "BILLING_ROUTE_UNAVAILABLE" "$WORK_DIR/mobile-billing-charge-501.json"

echo "[enterprise-e2e] Verifying long-tail enterprise routes are runtime-reachable..."
assert_non_5xx "$EXPERIENCE_BFF_BASE_URL/internal/v1/erp/procurement/suppliers" "$WORK_DIR/procurement-suppliers.json"
assert_non_5xx "$EXPERIENCE_BFF_BASE_URL/internal/v1/erp/hr/employees" "$WORK_DIR/hr-employees.json"
assert_non_5xx "$EXPERIENCE_BFF_BASE_URL/internal/v1/finance/patient-accounts/runtime-e2e-patient" "$WORK_DIR/patient-account.json"
assert_non_5xx "$EXPERIENCE_BFF_BASE_URL/internal/v1/finance/payment-plans?patient_cpid=runtime-e2e-patient" \
  "$WORK_DIR/payment-plans.json"

cp -f "$WORK_DIR"/*.json "$ARTIFACT_DIR"/ 2>/dev/null || true
echo "[enterprise-e2e] PASS - enterprise runtime proof harness assertions succeeded"
