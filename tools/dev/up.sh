#!/usr/bin/env bash
##
## Impilo vNext — Start Experience Platform (Docker Compose)
##
## Starts Postgres + experience-bff + one-ui-shell (unified Experience) using compose.
## Waits for healthchecks before returning.
##
## Usage:
##   ./tools/dev/up.sh              # Maven package (wellness + BFF), then compose up
##   ./tools/dev/up.sh --build      # Same + docker compose --build
##   ./tools/dev/up.sh --skip-maven # Compose only (JARs must exist under services/*/target)
##   ./tools/dev/up.sh --infra      # Also start root postgres + Keycloak (no redis/kafka — those are in Experience compose)
##   ./tools/dev/up.sh --sovereign-host # experience + pharmacy + MusheX + dispatch
##
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
SERVICES_DIR="$ROOT_DIR/services"

BUILD_FLAG=""
INFRA_FLAG=""
SKIP_MAVEN=""
SOVEREIGN_FLAG=""

for arg in "$@"; do
  case $arg in
    --build) BUILD_FLAG="--build" ;;
    --infra) INFRA_FLAG="true" ;;
    --skip-maven) SKIP_MAVEN="true" ;;
    --sovereign-host) SOVEREIGN_FLAG="true" ;;
  esac
done

echo "========================================"
echo "  Impilo vNext — Experience Platform"
echo "========================================"

# ── Maven: wellness-service + experience-bff JARs for Docker images ──
if [ -z "$SKIP_MAVEN" ]; then
  echo ""
  MAVEN_MODULES="wellness-service,inpatient-service,pct-service,integration-hub,learning-service,experience-bff"
  if [ "$SOVEREIGN_FLAG" = "true" ]; then
        MAVEN_MODULES="${MAVEN_MODULES},pharmacy-service,mushex-service,dispatch-service,costing-engine-service,surveillance-service,ndila-service,nhume-service"
  fi
  echo "[INFO] Building ${MAVEN_MODULES} (Maven)..."
  cd "$SERVICES_DIR"
  mvn -B -pl "$MAVEN_MODULES" -am -DskipTests package -q
  echo "[OK] JARs ready"
fi

# ── Optionally start root infrastructure ─────────────────────
if [ "$INFRA_FLAG" = "true" ]; then
  echo "[INFO] Starting root Postgres + Keycloak (Experience compose already provides Redis + Kafka)..."
  docker compose -f "$ROOT_DIR/docker-compose.yml" up -d postgres keycloak
  echo "[OK] Root Postgres + Keycloak started"
  echo ""
fi

# ── Start Experience Platform ────────────────────────────────
echo "[INFO] Starting Experience Platform services..."
COMPOSE_ARGS=(-f "$ROOT_DIR/compose/experience/docker-compose.yml")
if [ "$SOVEREIGN_FLAG" = "true" ]; then
  COMPOSE_ARGS+=(-f "$ROOT_DIR/compose/experience/docker-compose.sovereign.yml")
  echo "[INFO] Sovereign Rx-path overlay enabled (pharmacy, MusheX, dispatch, Costa, surveillance)"
fi
docker compose "${COMPOSE_ARGS[@]}" up -d $BUILD_FLAG

echo ""
echo "[INFO] Waiting for services to become healthy..."

# Wait for BFF health
MAX_WAIT=120
WAITED=0
while [ $WAITED -lt $MAX_WAIT ]; do
  if curl -sf http://localhost:8160/actuator/health > /dev/null 2>&1; then
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

if [ "$SOVEREIGN_FLAG" = "true" ]; then
  echo ""
  echo "[INFO] Waiting for sovereign overlay health..."
  node "$ROOT_DIR/scripts/production-readiness/wait-sovereign-health.mjs" || echo "[WARN] Some sovereign services did not report healthy in time."
fi

echo ""
echo "========================================"
echo "  Experience Platform is running!"
echo ""
echo "  One UI Shell:      http://localhost:3000  (use --build after Wave 20 UI changes)"
echo "  Experience BFF:    http://localhost:8160"
echo "  Inpatient service: http://localhost:8121"
echo "  PCT service:       http://localhost:8088"
echo "  Integration hub:   http://localhost:8110"
echo "  Postgres:          localhost:5433"
echo "  Redis:          localhost:6379"
echo "  Kafka:          localhost:9092"
echo ""
echo "  Logs:   docker compose -f compose/experience/docker-compose.yml logs -f"
echo "  Stop:   docker compose -f compose/experience/docker-compose.yml down"
if [ "$SOVEREIGN_FLAG" = "true" ]; then
  echo ""
  echo "  Sovereign verify: node scripts/production-readiness/verify-demo-journeys.mjs --sovereign"
fi
echo "========================================"
