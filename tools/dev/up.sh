#!/usr/bin/env bash
##
## Impilo vNext — Start Experience Platform (Docker Compose)
##
## Starts Postgres + experience-bff + one-ui-shell (unified Experience) using compose.
## Waits for healthchecks before returning.
##
## Usage:
##   ./tools/dev/up.sh          # Start all
##   ./tools/dev/up.sh --build  # Rebuild and start
##   ./tools/dev/up.sh --infra  # Start with root infra (Kafka, Redis, Keycloak)
##
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

BUILD_FLAG=""
INFRA_FLAG=""

for arg in "$@"; do
  case $arg in
    --build) BUILD_FLAG="--build" ;;
    --infra) INFRA_FLAG="true" ;;
  esac
done

echo "========================================"
echo "  Impilo vNext — Experience Platform"
echo "========================================"

# ── Optionally start root infrastructure ─────────────────────
if [ "$INFRA_FLAG" = "true" ]; then
  echo "[INFO] Starting root infrastructure (Postgres, Redis, Kafka, Keycloak)..."
  docker compose -f "$ROOT_DIR/docker-compose.yml" up -d postgres redis kafka keycloak
  echo "[OK] Root infrastructure started"
  echo ""
fi

# ── Start Experience Platform ────────────────────────────────
echo "[INFO] Starting Experience Platform services..."
docker compose -f "$ROOT_DIR/compose/experience/docker-compose.yml" up -d $BUILD_FLAG

echo ""
echo "[INFO] Waiting for services to become healthy..."

# Wait for BFF health
MAX_WAIT=120
WAITED=0
while [ $WAITED -lt $MAX_WAIT ]; do
  if curl -sf http://localhost:8160/health > /dev/null 2>&1; then
    break
  fi
  sleep 2
  WAITED=$((WAITED + 2))
  echo "  waiting... ($WAITED/${MAX_WAIT}s)"
done

if [ $WAITED -ge $MAX_WAIT ]; then
  echo "[WARN] BFF health check timed out after ${MAX_WAIT}s"
  echo "       Check logs: docker compose -f compose/experience/docker-compose.yml logs experience-bff"
  exit 1
fi

echo ""
echo "========================================"
echo "  Experience Platform is running!"
echo ""
echo "  One UI Shell:   http://localhost:3000"
echo "  Experience BFF: http://localhost:8160"
echo "  Postgres:       localhost:5433"
echo ""
echo "  Logs:   docker compose -f compose/experience/docker-compose.yml logs -f"
echo "  Stop:   docker compose -f compose/experience/docker-compose.yml down"
echo "========================================"
