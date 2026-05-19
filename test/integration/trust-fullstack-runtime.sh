#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/compose/trust/docker-compose.trust-e2e.yml"
COMPOSE_PROJECT_NAME="${TRUST_E2E_COMPOSE_PROJECT:-trust-e2e-ci}"
ARTIFACT_DIR="${TRUST_E2E_ARTIFACT_DIR:-$ROOT_DIR/.tmp/trust-e2e-artifacts}"
WORK_DIR="${TRUST_E2E_WORK_DIR:-$ROOT_DIR/.tmp/trust-e2e-work}"
KEEP_STACK="${TRUST_E2E_KEEP_STACK:-0}"
STARTUP_TIMEOUT_SECONDS="${TRUST_E2E_STARTUP_TIMEOUT_SECONDS:-420}"

FAILED=0

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "[trust-e2e] Missing required command: $1" >&2; exit 1; }
}

compose() {
  docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" "$@"
}

dump_diagnostics() {
  mkdir -p "$ARTIFACT_DIR"
  echo "[trust-e2e] Collecting diagnostics into $ARTIFACT_DIR"
  compose ps >"$ARTIFACT_DIR/compose-ps.txt" 2>&1 || true
  compose logs --no-color >"$ARTIFACT_DIR/compose-logs.txt" 2>&1 || true

  for service in postgres redis kafka keycloak tshepo-consent tshepo-authz tshepo-audit mvumo tshepo; do
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
    echo "[trust-e2e] TRUST_E2E_KEEP_STACK=1 set; skipping compose down"
    return
  fi
  compose down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT
trap 'FAILED=1; echo "[trust-e2e] Failure at line $LINENO"' ERR

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
        echo "[trust-e2e] Service $service is $health"
        return 0
      fi
    fi

    now="$(date +%s)"
    elapsed=$((now - started_at))
    if (( elapsed >= timeout )); then
      echo "[trust-e2e] Timed out waiting for service healthy: $service" >&2
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
      echo "[trust-e2e] Health endpoint UP: $name ($url)"
      return 0
    fi
    now="$(date +%s)"
    elapsed=$((now - started_at))
    if (( elapsed >= timeout )); then
      echo "[trust-e2e] Timed out waiting for health endpoint: $name ($url)" >&2
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
  echo "[trust-e2e] docker compose v2 is required but unavailable" >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "[trust-e2e] Docker daemon is unavailable. Start Docker and retry." >&2
  exit 1
fi
if ! compose config >/dev/null; then
  echo "[trust-e2e] Compose file validation failed: $COMPOSE_FILE" >&2
  exit 1
fi

echo "[trust-e2e] Starting trust runtime stack (project=$COMPOSE_PROJECT_NAME)..."
compose up -d --build

for s in postgres redis kafka keycloak tshepo-consent tshepo-authz tshepo-audit mvumo tshepo; do
  wait_service_healthy "$s"
done

wait_http_up "mvumo" "http://localhost:38195/actuator/health"
wait_http_up "tshepo-authz" "http://localhost:38081/actuator/health"
wait_http_up "tshepo-consent" "http://localhost:38182/actuator/health"
wait_http_up "tshepo-audit" "http://localhost:38183/actuator/health"
wait_http_up "tshepo-legacy" "http://localhost:38079/actuator/health"

UUID_TENANT="00000000-0000-0000-0000-000000000001"
UUID_CORR="$(python - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"

echo "[trust-e2e] Creating MVUMO consent request..."
CREATE_RESP="$(curl -fsS --retry 3 --retry-delay 2 --retry-connrefused -X POST "http://localhost:38195/internal/v1/mvumo/consent-requests" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: ${UUID_TENANT}" \
  -d '{"subjectPatientRef":"Patient/runtime-e2e-1","consentType":"DIGITAL_TELEMEDICINE","requiredAssurance":"L1_SIMPLE_DIGITAL"}')"

CONSENT_ID="$(printf "%s" "$CREATE_RESP" | python -c 'import json,sys; print(json.load(sys.stdin)["data"]["id"])')"

SESSION_RESP="$(curl -fsS --retry 3 --retry-delay 2 --retry-connrefused -X POST "http://localhost:38195/internal/v1/mvumo/remote-sessions" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: ${UUID_TENANT}" \
  -d "{\"consentRequestId\":\"${CONSENT_ID}\",\"channel\":\"TOKEN_LINK\",\"ttlMinutes\":30}")"

SESSION_ID="$(printf "%s" "$SESSION_RESP" | python -c 'import json,sys; print(json.load(sys.stdin)["data"]["sessionId"])')"
SESSION_TOKEN="$(printf "%s" "$SESSION_RESP" | python -c 'import json,sys; print(json.load(sys.stdin)["data"]["token"])')"

echo "[trust-e2e] MVUMO verify + grant happy path..."
curl -fsS --retry 3 --retry-delay 1 --retry-connrefused -X POST "http://localhost:38195/internal/v1/mvumo/remote-sessions/${SESSION_ID}/verify" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: ${UUID_TENANT}" \
  -H "X-Actor-Id: provider-runtime-1" \
  -H "X-Purpose-Of-Use: TREATMENT" \
  -H "X-Correlation-Id: ${UUID_CORR}" \
  -d "{\"token\":\"${SESSION_TOKEN}\"}" >"$WORK_DIR/trust-verify.json"

curl -fsS --retry 3 --retry-delay 1 --retry-connrefused -X POST "http://localhost:38195/internal/v1/mvumo/remote-sessions/${SESSION_ID}/grant" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: ${UUID_TENANT}" \
  -H "X-Actor-Id: provider-runtime-1" \
  -H "X-Purpose-Of-Use: TREATMENT" \
  -H "X-Correlation-Id: ${UUID_CORR}" \
  -d '{"grantedByRef":"provider-runtime-1","reason":"runtime-e2e-grant"}' >"$WORK_DIR/trust-grant.json"

python - "$WORK_DIR/trust-verify.json" "$WORK_DIR/trust-grant.json" <<'PY'
import json,sys
verify=json.load(open(sys.argv[1]))
grant=json.load(open(sys.argv[2]))
assert verify["data"]["sessionState"]=="VERIFIED", verify
assert grant["data"]["sessionState"]=="GRANTED", grant
assert grant["data"]["consentState"]=="GRANTED", grant
PY

echo "[trust-e2e] TSHEPO authz denied flow..."
DENY_CODE="$(curl -sS -o "$WORK_DIR/authz-deny.json" -w "%{http_code}" -X POST "http://localhost:38081/v1/authorize" \
  -H "X-Tenant-Id: ${UUID_TENANT}" \
  -H "X-Actor-Id: provider-runtime-1" \
  -H "X-Actor-Type: PROVIDER")"
[[ "$DENY_CODE" == "403" ]] || { echo "[trust-e2e] Expected authz deny 403, got $DENY_CODE"; exit 1; }

echo "[trust-e2e] TSHEPO consent denied flow..."
CONSENT_DENY="$(curl -fsS --retry 3 --retry-delay 1 --retry-connrefused "http://localhost:38182/v1/consent/evaluate?tenantId=${UUID_TENANT}&actorId=provider-runtime-2&subjectRef=Patient/no-consent&purpose=TREATMENT&scope=clinical-data")"
printf "%s" "$CONSENT_DENY" | python -c 'import json,sys; d=json.load(sys.stdin); assert d["data"]["permitted"] is False, d'

echo "[trust-e2e] Dependency failure fail-close check..."
compose stop tshepo-consent >/dev/null
FAIL_CODE="$(curl -sS -o "$WORK_DIR/mvumo-fail.json" -w "%{http_code}" -X POST "http://localhost:38195/internal/v1/mvumo/evaluate" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: ${UUID_TENANT}" \
  -d '{"actorId":"provider-runtime-1","subjectRef":"Patient/runtime-e2e-1","purpose":"TREATMENT","scope":"clinical-data"}')"
[[ "$FAIL_CODE" == "503" ]] || { echo "[trust-e2e] Expected mvumo dependency failure 503, got $FAIL_CODE"; exit 1; }
compose start tshepo-consent >/dev/null
wait_service_healthy "tshepo-consent" 180
wait_http_up "tshepo-consent" "http://localhost:38182/actuator/health" 180

echo "[trust-e2e] Missing trust context fail-close check..."
CTX_CODE="$(curl -sS -o "$WORK_DIR/mvumo-context.json" -w "%{http_code}" -X POST "http://localhost:38195/internal/v1/mvumo/remote-sessions/${SESSION_ID}/verify" \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"${SESSION_TOKEN}\"}")"
[[ "$CTX_CODE" == "400" ]] || { echo "[trust-e2e] Expected missing-context 400, got $CTX_CODE"; exit 1; }

echo "[trust-e2e] Legacy telemetry endpoint reachability check..."
LEGACY_USAGE="$(curl -fsS --retry 3 --retry-delay 1 --retry-connrefused "http://localhost:38079/internal/v1/legacy/route-usage")"
printf "%s" "$LEGACY_USAGE" | python -c 'import json,sys; d=json.load(sys.stdin); assert "totalLegacyHits" in d, d; assert "routes" in d, d; assert "recentEvents" in d, d'

echo "[trust-e2e] Persisted outbox evidence check..."
docker exec "$(compose ps -q postgres)" \
  psql -U impilo -d mvumo -t -c "select count(*) from mvumo.event_outbox;" >"$WORK_DIR/mvumo-outbox-count.txt"
OUTBOX_COUNT="$(tr -d '[:space:]' <"$WORK_DIR/mvumo-outbox-count.txt")"
[[ "${OUTBOX_COUNT:-0}" -gt 0 ]] || { echo "[trust-e2e] Expected mvumo outbox rows > 0"; exit 1; }

echo "[trust-e2e] PASS - full runtime harness assertions succeeded"
