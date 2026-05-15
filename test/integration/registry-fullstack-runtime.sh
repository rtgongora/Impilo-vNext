#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/compose/registry/docker-compose.registry-e2e.yml"
COMPOSE_PROJECT_NAME="${REGISTRY_E2E_COMPOSE_PROJECT:-registry-e2e-ci}"
ARTIFACT_DIR="${REGISTRY_E2E_ARTIFACT_DIR:-$ROOT_DIR/.tmp/registry-e2e-artifacts}"
WORK_DIR="${REGISTRY_E2E_WORK_DIR:-$ROOT_DIR/.tmp/registry-e2e-work}"
KEEP_STACK="${REGISTRY_E2E_KEEP_STACK:-0}"
STARTUP_TIMEOUT_SECONDS="${REGISTRY_E2E_STARTUP_TIMEOUT_SECONDS:-480}"

FAILED=0

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "[registry-e2e] Missing required command: $1" >&2; exit 1; }
}

compose() {
  docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" "$@"
}

dump_diagnostics() {
  mkdir -p "$ARTIFACT_DIR"
  echo "[registry-e2e] Collecting diagnostics into $ARTIFACT_DIR"
  compose ps >"$ARTIFACT_DIR/compose-ps.txt" 2>&1 || true
  compose logs --no-color >"$ARTIFACT_DIR/compose-logs.txt" 2>&1 || true
  for service in postgres redis kafka keycloak tshepo-authz vito varapi tuso zibo msika ubomi experience-bff; do
    local cid
    cid="$(compose ps -q "$service" 2>/dev/null || true)"
    if [[ -n "$cid" ]]; then
      docker inspect "$cid" >"$ARTIFACT_DIR/inspect-${service}.json" 2>&1 || true
    fi
  done
}

cleanup() {
  if [[ "$FAILED" -eq 1 ]]; then
    dump_diagnostics
  fi
  if [[ "$KEEP_STACK" == "1" ]]; then
    echo "[registry-e2e] REGISTRY_E2E_KEEP_STACK=1 set; skipping compose down"
    return
  fi
  compose down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT
trap 'FAILED=1; echo "[registry-e2e] Failure at line $LINENO"' ERR

wait_service_healthy() {
  local service="$1"
  local timeout="${2:-$STARTUP_TIMEOUT_SECONDS}"
  local started_at now elapsed cid health
  started_at="$(date +%s)"
  while true; do
    cid="$(compose ps -q "$service" 2>/dev/null || true)"
    if [[ -n "$cid" ]]; then
      health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || true)"
      if [[ "$health" == "healthy" || "$health" == "running" ]]; then
        echo "[registry-e2e] Service $service is $health"
        return 0
      fi
    fi
    now="$(date +%s)"
    elapsed=$((now - started_at))
    if (( elapsed >= timeout )); then
      echo "[registry-e2e] Timed out waiting for service healthy: $service" >&2
      return 1
    fi
    sleep 2
  done
}

wait_http_up() {
  local name="$1"
  local url="$2"
  local timeout="${3:-$STARTUP_TIMEOUT_SECONDS}"
  local started_at now elapsed
  started_at="$(date +%s)"
  while true; do
    if curl -fsS "$url" | python -c 'import json,sys; print("ok" if json.load(sys.stdin).get("status")=="UP" else "bad")' | grep -q '^ok$'; then
      echo "[registry-e2e] Health endpoint UP: $name ($url)"
      return 0
    fi
    now="$(date +%s)"
    elapsed=$((now - started_at))
    if (( elapsed >= timeout )); then
      echo "[registry-e2e] Timed out waiting for health endpoint: $name ($url)" >&2
      return 1
    fi
    sleep 2
  done
}

mkdir -p "$ARTIFACT_DIR" "$WORK_DIR"
require_cmd docker
require_cmd curl
require_cmd python

if ! docker compose version >/dev/null 2>&1; then
  echo "[registry-e2e] docker compose v2 is required but unavailable" >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "[registry-e2e] Docker daemon is unavailable. Start Docker and retry." >&2
  exit 1
fi
if ! compose config >/dev/null; then
  echo "[registry-e2e] Compose file validation failed: $COMPOSE_FILE" >&2
  exit 1
fi

echo "[registry-e2e] Starting registry runtime stack (project=$COMPOSE_PROJECT_NAME)..."
compose up -d --build

for s in postgres redis kafka keycloak tshepo-authz vito varapi tuso zibo msika ubomi experience-bff; do
  wait_service_healthy "$s"
done

wait_http_up "tshepo-authz" "http://localhost:48081/actuator/health"
wait_http_up "vito" "http://localhost:48082/actuator/health"
wait_http_up "varapi" "http://localhost:48083/actuator/health"
wait_http_up "tuso" "http://localhost:48084/actuator/health"
wait_http_up "zibo" "http://localhost:48085/actuator/health"
wait_http_up "msika" "http://localhost:48086/actuator/health"
wait_http_up "ubomi" "http://localhost:48087/actuator/health"
wait_http_up "experience-bff" "http://localhost:48160/actuator/health"

REQ_ID="registry-e2e-req-1"
CORR_ID="$(python - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"

echo "[registry-e2e] BFF->TUSO facility flow success check..."
FACILITY_RESP="$(curl -fsS --retry 3 --retry-delay 1 --retry-connrefused "http://localhost:48160/internal/v1/facilities?page=0&size=5" \
  -H "X-Tenant-Id: tenant-registry-e2e" \
  -H "X-Request-Id: ${REQ_ID}" \
  -H "X-Correlation-Id: ${CORR_ID}")"
printf "%s" "$FACILITY_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); assert "data" in d and "meta" in d, d'

echo "[registry-e2e] BFF registry geo route success check..."
GEO_RESP="$(curl -fsS --retry 3 --retry-delay 1 --retry-connrefused "http://localhost:48160/internal/v1/registry/geo/zw/districts?provinceCode=HA" \
  -H "X-Request-Id: ${REQ_ID}" \
  -H "X-Correlation-Id: ${CORR_ID}")"
printf "%s" "$GEO_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); assert "data" in d and "meta" in d, d'

echo "[registry-e2e] BFF fail-close behavior when TUSO unavailable..."
compose stop tuso >/dev/null
FAIL_CODE="$(curl -sS -o "$WORK_DIR/facility-fail.json" -w "%{http_code}" "http://localhost:48160/internal/v1/facilities?page=0&size=2" \
  -H "X-Tenant-Id: tenant-registry-e2e" \
  -H "X-Request-Id: ${REQ_ID}" \
  -H "X-Correlation-Id: ${CORR_ID}")"
[[ "$FAIL_CODE" == "502" ]] || { echo "[registry-e2e] Expected BFF fail-close 502, got $FAIL_CODE"; exit 1; }
compose start tuso >/dev/null
wait_service_healthy "tuso" 240
wait_http_up "tuso" "http://localhost:48084/actuator/health" 240

echo "[registry-e2e] Registry service security posture check (no anonymous mutation reads)..."
SEC_CODE="$(curl -sS -o "$WORK_DIR/vito-anon.json" -w "%{http_code}" "http://localhost:48082/v1/client-registry/clients")"
if [[ "$SEC_CODE" != "401" && "$SEC_CODE" != "403" ]]; then
  echo "[registry-e2e] Expected secured vito endpoint (401/403), got $SEC_CODE"
  exit 1
fi

echo "[registry-e2e] Cross-plane trust dependency reachability check..."
AUTHZ_DENY_CODE="$(curl -sS -o "$WORK_DIR/authz-deny.json" -w "%{http_code}" -X POST "http://localhost:48081/v1/authorize" \
  -H "X-Tenant-Id: tenant-registry-e2e" \
  -H "X-Actor-Id: actor-registry-e2e" \
  -H "X-Actor-Type: PROVIDER")"
[[ "$AUTHZ_DENY_CODE" == "403" ]] || { echo "[registry-e2e] Expected authz deny 403, got $AUTHZ_DENY_CODE"; exit 1; }

echo "[registry-e2e] PASS - registry runtime harness assertions succeeded"
