#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/compose/core-transaction/docker-compose.core-transaction-e2e.yml"
COMPOSE_PROJECT_NAME="${CORE_TX_E2E_COMPOSE_PROJECT:-core-tx-e2e-ci}"
ARTIFACT_DIR="${CORE_TX_E2E_ARTIFACT_DIR:-$ROOT_DIR/.tmp/core-tx-e2e-artifacts}"
WORK_DIR="${CORE_TX_E2E_WORK_DIR:-$ROOT_DIR/.tmp/core-tx-e2e-work}"
KEEP_STACK="${CORE_TX_E2E_KEEP_STACK:-0}"
STARTUP_TIMEOUT_SECONDS="${CORE_TX_E2E_STARTUP_TIMEOUT_SECONDS:-360}"

FAILED=0

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "[core-tx-e2e] Missing required command: $1" >&2; exit 1; }
}

compose() {
  docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" "$@"
}

dump_diagnostics() {
  mkdir -p "$ARTIFACT_DIR"
  compose ps >"$ARTIFACT_DIR/compose-ps.txt" 2>&1 || true
  compose logs --no-color >"$ARTIFACT_DIR/compose-logs.txt" 2>&1 || true
}

cleanup() {
  if [[ "$FAILED" -eq 1 ]]; then
    dump_diagnostics
  fi
  if [[ "$KEEP_STACK" == "1" ]]; then
    echo "[core-tx-e2e] CORE_TX_E2E_KEEP_STACK=1 set; skipping compose down"
    return
  fi
  compose down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT
trap 'FAILED=1; echo "[core-tx-e2e] Failure at line $LINENO"' ERR

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
        echo "[core-tx-e2e] Service $service is $health"
        return 0
      fi
    fi
    now="$(date +%s)"
    elapsed=$((now - started_at))
    if (( elapsed >= timeout )); then
      echo "[core-tx-e2e] Timed out waiting for service healthy: $service" >&2
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
    if curl -fsS "$url" | python -c 'import json,sys; print("ok" if json.load(sys.stdin).get("status")=="UP" else "bad")' | python -c 'import sys; sys.exit(0 if sys.stdin.read().strip()=="ok" else 1)'; then
      echo "[core-tx-e2e] Health endpoint UP: $name ($url)"
      return 0
    fi
    now="$(date +%s)"
    elapsed=$((now - started_at))
    if (( elapsed >= timeout )); then
      echo "[core-tx-e2e] Timed out waiting for health endpoint: $name ($url)" >&2
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
  echo "[core-tx-e2e] docker compose v2 is required but unavailable" >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "[core-tx-e2e] Docker daemon is unavailable. Start Docker and retry." >&2
  exit 1
fi
if ! compose config >/dev/null; then
  echo "[core-tx-e2e] Compose file validation failed: $COMPOSE_FILE" >&2
  exit 1
fi

echo "[core-tx-e2e] Starting core transaction runtime stack (project=$COMPOSE_PROJECT_NAME)..."
compose up -d --build

for s in redis kafka workflow-mock pct-mock costa-mock experience-bff; do
  wait_service_healthy "$s"
done

wait_http_up "experience-bff" "http://localhost:48161/actuator/health"

REQ_ID="core-tx-e2e-req-1"
CORR_ID="$(python - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"

COMMON_HEADERS=(
  -H "X-Tenant-Id: tenant-core-tx-e2e"
  -H "X-Pod-Id: national-spine"
  -H "X-Request-Id: ${REQ_ID}"
  -H "X-Correlation-Id: ${CORR_ID}"
)

echo "[core-tx-e2e] Verifying list endpoint..."
LIST_RESP="$(curl -fsS "http://localhost:48161/internal/v1/core-transactions?state=TRUST_CONTEXT_ESTABLISHED" "${COMMON_HEADERS[@]}")"
printf "%s" "$LIST_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); assert "data" in d and "items" in d["data"] and len(d["data"]["items"])>=1, d'

echo "[core-tx-e2e] Verifying get endpoint..."
GET_RESP="$(curl -fsS "http://localhost:48161/internal/v1/core-transactions/tx-1" "${COMMON_HEADERS[@]}")"
printf "%s" "$GET_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); assert d["data"]["transaction"]["id"]=="tx-1", d'
printf "%s" "$GET_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); reasons=d["data"].get("blockerReasons", []); assert any("triage note" in r.lower() for r in reasons), d'

echo "[core-tx-e2e] Verifying alias endpoint..."
ALIAS_RESP="$(curl -fsS "http://localhost:48161/experience/core-transactions/tx-1/next-actions" "${COMMON_HEADERS[@]}")"
printf "%s" "$ALIAS_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); assert "nextActions" in d["data"], d'

echo "[core-tx-e2e] Verifying journeys endpoint..."
JOURNEYS_RESP="$(curl -fsS "http://localhost:48161/internal/v1/core-transactions/tx-1/journeys" "${COMMON_HEADERS[@]}")"
printf "%s" "$JOURNEYS_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); j=d["data"]["journeys"]; assert "person" in j and "provider" in j and "platform" in j, d'
printf "%s" "$JOURNEYS_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); assert isinstance(d["data"]["journeys"]["person"].get("blockers"), list), d'
printf "%s" "$JOURNEYS_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); reasons=d["data"]["journeys"]["person"].get("blockerReasons", []); assert any("triage note" in r.lower() for r in reasons), d'

echo "[core-tx-e2e] Verifying nompilo endpoint..."
NOMPILO_RESP="$(curl -fsS "http://localhost:48161/internal/v1/core-transactions/tx-1/nompilo" "${COMMON_HEADERS[@]}")"
printf "%s" "$NOMPILO_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); n=d["data"]["nompilo"]; assert n.get("available") is True and "mode" in n, d'
printf "%s" "$NOMPILO_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); n=d["data"]["nompilo"]; assert isinstance(n.get("currentGuidance"), list) and len(n["currentGuidance"])>=1, d'

echo "[core-tx-e2e] Verifying feedback endpoint..."
FEEDBACK_RESP="$(curl -fsS -X POST "http://localhost:48161/internal/v1/core-transactions/tx-1/feedback" \
  "${COMMON_HEADERS[@]}" \
  -H "Content-Type: application/json" \
  -d '{"channel":"IN_APP","promptId":"feedback-in-app","response":"helpful"}')"
printf "%s" "$FEEDBACK_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); assert d["data"]["status"]=="ACCEPTED", d'

echo "[core-tx-e2e] Verifying nompilo handoff endpoint..."
HANDOFF_RESP="$(curl -fsS -X POST "http://localhost:48161/internal/v1/core-transactions/tx-1/nompilo/handoff" \
  "${COMMON_HEADERS[@]}" \
  -H "Content-Type: application/json" \
  -d '{"handoffOptionId":"handoff-helpdesk"}')"
printf "%s" "$HANDOFF_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); assert d["data"]["status"]=="ACCEPTED", d'

echo "[core-tx-e2e] Verifying nompilo command endpoint..."
COMMAND_RESP="$(curl -fsS -X POST "http://localhost:48161/internal/v1/core-transactions/tx-1/nompilo/command" \
  "${COMMON_HEADERS[@]}" \
  -H "Content-Type: application/json" \
  -d '{"query":"find anc service near me","surface":"GLOBAL_SEARCH_BAR"}')"
printf "%s" "$COMMAND_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); assert d["data"]["status"]=="ACCEPTED" and "intent" in d["data"], d'

echo "[core-tx-e2e] Verifying alias journeys endpoint..."
ALIAS_JOURNEYS_RESP="$(curl -fsS "http://localhost:48161/experience/core-transactions/tx-1/journeys" "${COMMON_HEADERS[@]}")"
printf "%s" "$ALIAS_JOURNEYS_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); assert "journeys" in d["data"], d'

echo "[core-tx-e2e] Verifying action endpoint..."
ACTION_RESP="$(curl -fsS -X POST "http://localhost:48161/internal/v1/core-transactions/tx-1/actions/START_TRIAGE" \
  "${COMMON_HEADERS[@]}" \
  -H "Content-Type: application/json" \
  -d '{"note":"start triage"}')"
printf "%s" "$ACTION_RESP" | python -c 'import json,sys; d=json.load(sys.stdin); assert "data" in d, d'

echo "[core-tx-e2e] PASS - core transaction runtime harness assertions succeeded"
