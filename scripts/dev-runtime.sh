#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Dev Runtime Convenience Wrapper (Wave 18)
# =============================================================================
#
# Usage:
#   ./scripts/dev-runtime.sh build   — Build all service images + JARs
#   ./scripts/dev-runtime.sh up      — Start infra + services + UI + edge
#   ./scripts/dev-runtime.sh down    — Stop and remove all containers
#   ./scripts/dev-runtime.sh smoke   — Run smoke + compliance tests
#   ./scripts/dev-runtime.sh logs    — Tail logs from all services
#   ./scripts/dev-runtime.sh status  — Show container health status
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

BUILD_COMPOSE="$PROJECT_ROOT/docker-compose.build.yml"
RUNTIME_COMPOSE="$PROJECT_ROOT/docker-compose.runtime.yml"
SMOKE_SCRIPT="$SCRIPT_DIR/smoke/smoke.sh"
EVENT_BUS_SCRIPT="$SCRIPT_DIR/smoke/event-bus-proof.sh"
ROUTE_PARITY_SCRIPT="$SCRIPT_DIR/smoke/route-parity.sh"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

log_info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ── Ensure .env exists ───────────────────────────────────────────────
ensure_env() {
    if [ ! -f "$PROJECT_ROOT/.env" ]; then
        log_warn ".env not found — copying from .env.example"
        cp "$PROJECT_ROOT/.env.example" "$PROJECT_ROOT/.env"
    fi
}

# ── Ensure vendor cache dirs exist ───────────────────────────────────
ensure_caches() {
    mkdir -p "$PROJECT_ROOT/vendor/m2/repository"
    mkdir -p "$PROJECT_ROOT/vendor/node-cache"
}

# ── BUILD ────────────────────────────────────────────────────────────
cmd_build() {
    log_info "Building all services (Maven + UI)..."
    ensure_env
    ensure_caches

    log_info "Phase 1: Maven build (all backend services)"
    docker compose -f "$BUILD_COMPOSE" run --rm maven-builder

    log_info "Phase 2: UI build (Experience)"
    docker compose -f "$BUILD_COMPOSE" run --rm ui-builder

    log_info "Phase 3: Building Docker images for runtime"
    docker compose -f "$RUNTIME_COMPOSE" build

    log_ok "Build complete. Run './scripts/dev-runtime.sh up' to start."
}

# ── UP ───────────────────────────────────────────────────────────────
cmd_up() {
    log_info "Starting Impilo Dev Runtime..."
    ensure_env

    log_info "Starting infrastructure (postgres, redis, kafka, keycloak, minio, hapi-fhir)..."
    docker compose -f "$RUNTIME_COMPOSE" up -d postgres redis kafka keycloak minio hapi-fhir

    log_info "Waiting for infrastructure readiness..."
    docker compose -f "$RUNTIME_COMPOSE" up -d --wait postgres redis kafka

    log_info "Reconciling databases for the current Postgres volume..."
    docker compose -f "$RUNTIME_COMPOSE" up --force-recreate --no-deps postgres-db-ensure

    log_info "Starting edge (OPA + Envoy)..."
    docker compose -f "$RUNTIME_COMPOSE" up -d opa envoy

    log_info "Starting backend services..."
    docker compose -f "$RUNTIME_COMPOSE" up -d tshepo vito varapi tuso zibo pct oros learning-service experience-bff

    log_info "Starting UI..."
    docker compose -f "$RUNTIME_COMPOSE" up -d one-ui-shell

    log_ok "All services starting. Use './scripts/dev-runtime.sh status' to check health."
    log_info "Envoy gateway: http://localhost:10000"
    log_info "One UI Shell: http://localhost:3000"
    log_info "Keycloak:      http://localhost:8080"
    log_info "HAPI FHIR:     http://localhost:8090/fhir"
    log_info "OPA:           http://localhost:8181"
    log_info "Envoy admin:   http://localhost:9901"
}

# ── DOWN ─────────────────────────────────────────────────────────────
cmd_down() {
    log_info "Stopping Impilo Dev Runtime..."
    docker compose -f "$RUNTIME_COMPOSE" down
    log_ok "All containers stopped."
}

# ── SMOKE ────────────────────────────────────────────────────────────
cmd_smoke() {
    log_info "Running smoke + compliance tests..."

    if [ -x "$SMOKE_SCRIPT" ]; then
        bash "$SMOKE_SCRIPT"
    else
        log_error "Smoke script not found at $SMOKE_SCRIPT"
        exit 1
    fi

    if [ -x "$EVENT_BUS_SCRIPT" ]; then
        log_info "Running event-bus proof..."
        bash "$EVENT_BUS_SCRIPT"
    else
        log_warn "Event-bus proof script not found — skipping"
    fi

    if [ -x "$ROUTE_PARITY_SCRIPT" ]; then
        log_info "Running route parity check..."
        bash "$ROUTE_PARITY_SCRIPT"
    else
        log_warn "Route parity script not found — skipping"
    fi
}

# ── LOGS ─────────────────────────────────────────────────────────────
cmd_logs() {
    docker compose -f "$RUNTIME_COMPOSE" logs -f --tail=50
}

# ── STATUS ───────────────────────────────────────────────────────────
cmd_status() {
    docker compose -f "$RUNTIME_COMPOSE" ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
}

# ── MAIN ─────────────────────────────────────────────────────────────
case "${1:-help}" in
    build)  cmd_build ;;
    up)     cmd_up ;;
    down)   cmd_down ;;
    smoke)  cmd_smoke ;;
    logs)   cmd_logs ;;
    status) cmd_status ;;
    help|*)
        echo "Usage: $0 {build|up|down|smoke|logs|status}"
        echo ""
        echo "  build   Build all service images + JARs"
        echo "  up      Start infra + services + UI + edge enforcement"
        echo "  down    Stop and remove all containers"
        echo "  smoke   Run smoke + compliance + event-bus tests"
        echo "  logs    Tail logs from all services"
        echo "  status  Show container health status"
        ;;
esac
