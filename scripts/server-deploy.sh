#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Server Deployment Script
# =============================================================================
#
# Deploys the Impilo vNext platform on a target server with Docker Compose.
#
# Usage:
#   ./scripts/server-deploy.sh discover    — Discover server environment
#   ./scripts/server-deploy.sh prereqs     — Install prerequisites
#   ./scripts/server-deploy.sh build       — Build all service images + JARs
#   ./scripts/server-deploy.sh up          — Start all services
#   ./scripts/server-deploy.sh down        — Stop all services
#   ./scripts/server-deploy.sh status      — Show container health status
#   ./scripts/server-deploy.sh logs [svc]  — Tail logs (all or specific service)
#   ./scripts/server-deploy.sh health      — Run health checks on all services
#   ./scripts/server-deploy.sh full        — Full deploy: prereqs → build → up → health
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

BUILD_COMPOSE="$PROJECT_ROOT/docker-compose.build.yml"
RUNTIME_COMPOSE="$PROJECT_ROOT/docker-compose.runtime.yml"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

log_info()  { echo -e "${CYAN}[INFO]${NC}  $(date '+%H:%M:%S') $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $(date '+%H:%M:%S') $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $(date '+%H:%M:%S') $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $(date '+%H:%M:%S') $*"; }
log_step()  { echo -e "${BOLD}${CYAN}═══ $* ═══${NC}"; }

# ── Server Discovery ──────────────────────────────────────────────────
cmd_discover() {
    log_step "SERVER DISCOVERY"
    echo "Hostname:       $(hostname)"
    echo "User:           $(whoami)"
    echo "Working dir:    $(pwd)"
    echo "Kernel:         $(uname -r)"
    echo ""
    echo "--- OS ---"
    cat /etc/os-release 2>/dev/null | grep -E "^(NAME|VERSION|ID)=" || echo "Unknown OS"
    echo ""
    echo "--- Memory ---"
    free -h 2>/dev/null || echo "free not available"
    echo ""
    echo "--- Disk ---"
    df -h / 2>/dev/null || echo "df not available"
    echo ""
    echo "--- Docker ---"
    docker --version 2>/dev/null || echo "Docker NOT INSTALLED"
    docker compose version 2>/dev/null || echo "Docker Compose NOT INSTALLED"
    echo ""
    echo "--- Java ---"
    java -version 2>&1 || echo "Java NOT INSTALLED"
    echo ""
    echo "--- Maven ---"
    mvn -version 2>/dev/null || echo "Maven NOT INSTALLED"
    echo ""
    echo "--- Node ---"
    node -v 2>/dev/null || echo "Node NOT INSTALLED"
    npm -v 2>/dev/null || echo "npm NOT INSTALLED"
    echo ""
    echo "--- Git ---"
    git --version 2>/dev/null || echo "git NOT INSTALLED"
    echo ""
    echo "--- Listening Ports ---"
    ss -tulpn 2>/dev/null | head -30 || netstat -tulpn 2>/dev/null | head -30 || echo "Port scan not available"
    echo ""
    echo "--- Running Containers ---"
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || echo "No running containers"
}

# ── Prerequisites ─────────────────────────────────────────────────────
cmd_prereqs() {
    log_step "INSTALLING PREREQUISITES"

    # Docker
    if ! command -v docker &>/dev/null; then
        log_info "Installing Docker..."
        curl -fsSL https://get.docker.com | sh
        sudo systemctl enable --now docker
        sudo usermod -aG docker "$(whoami)" || true
        log_ok "Docker installed"
    else
        log_ok "Docker already installed: $(docker --version)"
    fi

    # Docker Compose (plugin)
    if ! docker compose version &>/dev/null; then
        log_info "Installing Docker Compose plugin..."
        DOCKER_CONFIG=${DOCKER_CONFIG:-$HOME/.docker}
        mkdir -p "$DOCKER_CONFIG/cli-plugins"
        COMPOSE_VERSION="v2.29.1"
        ARCH=$(uname -m)
        curl -SL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-${ARCH}" \
            -o "$DOCKER_CONFIG/cli-plugins/docker-compose"
        chmod +x "$DOCKER_CONFIG/cli-plugins/docker-compose"
        log_ok "Docker Compose plugin installed"
    else
        log_ok "Docker Compose already installed: $(docker compose version)"
    fi

    # Git
    if ! command -v git &>/dev/null; then
        log_info "Installing git..."
        sudo apt-get update -qq && sudo apt-get install -y -qq git || \
        sudo yum install -y git || \
        sudo dnf install -y git
        log_ok "git installed"
    else
        log_ok "git already installed: $(git --version)"
    fi

    # Ensure project env file
    if [ ! -f "$PROJECT_ROOT/.env" ]; then
        if [ -f "$PROJECT_ROOT/.env.server" ]; then
            cp "$PROJECT_ROOT/.env.server" "$PROJECT_ROOT/.env"
            log_ok "Created .env from .env.server"
        else
            cp "$PROJECT_ROOT/.env.example" "$PROJECT_ROOT/.env"
            log_warn "Created .env from .env.example — edit with real values!"
        fi
    else
        log_ok ".env already exists"
    fi

    # Ensure vendor cache dirs
    mkdir -p "$PROJECT_ROOT/vendor/m2/repository"
    mkdir -p "$PROJECT_ROOT/vendor/node-cache"
    log_ok "Vendor cache directories ready"

    log_ok "Prerequisites check complete"
}

# ── Build ─────────────────────────────────────────────────────────────
cmd_build() {
    log_step "BUILDING ALL SERVICES"

    log_info "Phase 1: Maven build (all backend services)..."
    docker compose -f "$BUILD_COMPOSE" run --rm maven-builder
    log_ok "Maven build complete"

    log_info "Phase 2: UI build (Experience)..."
    docker compose -f "$BUILD_COMPOSE" run --rm ui-builder
    log_ok "UI build complete"

    log_info "Phase 3: Building Docker images for runtime..."
    docker compose -f "$RUNTIME_COMPOSE" build --parallel
    log_ok "Docker images built"
}

# ── Start Services ────────────────────────────────────────────────────
cmd_up() {
    log_step "STARTING IMPILO vNEXT PLATFORM"

    # Phase 1: Infrastructure
    log_info "Phase 1: Starting infrastructure..."
    docker compose -f "$RUNTIME_COMPOSE" up -d postgres redis kafka minio
    log_info "Waiting for postgres, redis, kafka to be healthy..."
    docker compose -f "$RUNTIME_COMPOSE" up -d --wait postgres redis kafka
    log_ok "Core infrastructure ready"

    # Phase 2: Keycloak + HAPI FHIR (depends on postgres)
    log_info "Phase 2: Starting Keycloak + HAPI FHIR..."
    docker compose -f "$RUNTIME_COMPOSE" up -d keycloak hapi-fhir
    log_ok "Identity + FHIR starting"

    # Phase 3: Edge (OPA + Envoy)
    log_info "Phase 3: Starting edge layer (OPA + Envoy)..."
    docker compose -f "$RUNTIME_COMPOSE" up -d opa envoy
    log_ok "Edge layer starting"

    # Phase 4: Backend services
    log_info "Phase 4: Starting backend services..."
    docker compose -f "$RUNTIME_COMPOSE" up -d tshepo vito varapi tuso zibo pct oros experience-bff
    log_ok "Backend services starting"

    # Phase 5: UI
    log_info "Phase 5: Starting Experience UI..."
    docker compose -f "$RUNTIME_COMPOSE" up -d experience-ui
    log_ok "UI starting"

    log_info "Waiting for services to stabilize (60s)..."
    sleep 60

    log_step "PLATFORM STARTED"
    echo ""
    echo "Service URLs:"
    echo "  Envoy Gateway:    http://$(hostname -I | awk '{print $1}'):10000"
    echo "  Experience UI:    http://$(hostname -I | awk '{print $1}'):3020"
    echo "  Keycloak:         http://$(hostname -I | awk '{print $1}'):8080"
    echo "  HAPI FHIR:        http://$(hostname -I | awk '{print $1}'):8090/fhir"
    echo "  OPA:              http://$(hostname -I | awk '{print $1}'):8181"
    echo "  MinIO Console:    http://$(hostname -I | awk '{print $1}'):9001"
    echo "  Envoy Admin:      http://$(hostname -I | awk '{print $1}'):9901"
    echo ""
    echo "Backend Services:"
    echo "  TSHEPO (Trust):   http://$(hostname -I | awk '{print $1}'):8081"
    echo "  VITO (Client):    http://$(hostname -I | awk '{print $1}'):8082"
    echo "  VARAPI (Provider):http://$(hostname -I | awk '{print $1}'):8083"
    echo "  TUSO (Facility):  http://$(hostname -I | awk '{print $1}'):8084"
    echo "  ZIBO (Terms):     http://$(hostname -I | awk '{print $1}'):8085"
    echo "  PCT (Care):       http://$(hostname -I | awk '{print $1}'):8088"
    echo "  OROS (Orders):    http://$(hostname -I | awk '{print $1}'):8089"
    echo "  Experience BFF:   http://$(hostname -I | awk '{print $1}'):8160"
}

# ── Stop Services ─────────────────────────────────────────────────────
cmd_down() {
    log_step "STOPPING IMPILO vNEXT PLATFORM"
    docker compose -f "$RUNTIME_COMPOSE" down
    log_ok "All containers stopped"
}

# ── Status ────────────────────────────────────────────────────────────
cmd_status() {
    log_step "CONTAINER STATUS"
    docker compose -f "$RUNTIME_COMPOSE" ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
}

# ── Logs ──────────────────────────────────────────────────────────────
cmd_logs() {
    local service="${1:-}"
    if [ -n "$service" ]; then
        docker compose -f "$RUNTIME_COMPOSE" logs -f --tail=100 "$service"
    else
        docker compose -f "$RUNTIME_COMPOSE" logs -f --tail=50
    fi
}

# ── Health Checks ─────────────────────────────────────────────────────
cmd_health() {
    log_step "HEALTH CHECK — ALL SERVICES"
    local pass=0
    local fail=0
    local total=0

    check_health() {
        local name="$1"
        local url="$2"
        total=$((total + 1))
        if curl -sf --connect-timeout 5 --max-time 10 "$url" > /dev/null 2>&1; then
            log_ok "$name — healthy ($url)"
            pass=$((pass + 1))
        else
            log_error "$name — UNHEALTHY ($url)"
            fail=$((fail + 1))
        fi
    }

    # Infrastructure
    check_health "PostgreSQL"    "http://localhost:5432" || true  # TCP check below
    docker compose -f "$RUNTIME_COMPOSE" exec -T postgres pg_isready -U impilo > /dev/null 2>&1 && \
        { log_ok "PostgreSQL — healthy (pg_isready)"; pass=$((pass + 1)); } || \
        { log_error "PostgreSQL — UNHEALTHY"; fail=$((fail + 1)); }
    total=$((total + 1))

    check_health "Redis"         "http://localhost:6379" || true
    docker compose -f "$RUNTIME_COMPOSE" exec -T redis redis-cli ping > /dev/null 2>&1 && \
        { log_ok "Redis — healthy (PONG)"; pass=$((pass + 1)); } || \
        { log_error "Redis — UNHEALTHY"; fail=$((fail + 1)); }
    total=$((total + 1))

    # Services
    check_health "Keycloak"       "http://localhost:8080/health/ready"
    check_health "HAPI FHIR"      "http://localhost:8090/fhir/metadata"
    check_health "OPA"             "http://localhost:8181/health"
    check_health "Envoy"           "http://localhost:9901/ready"
    check_health "TSHEPO"          "http://localhost:8081/actuator/health"
    check_health "VITO"            "http://localhost:8082/actuator/health"
    check_health "VARAPI"          "http://localhost:8083/actuator/health"
    check_health "TUSO"            "http://localhost:8084/actuator/health"
    check_health "ZIBO"            "http://localhost:8085/actuator/health"
    check_health "PCT"             "http://localhost:8088/actuator/health"
    check_health "OROS"            "http://localhost:8089/actuator/health"
    check_health "Experience BFF"  "http://localhost:8160/actuator/health"
    check_health "Experience UI"   "http://localhost:3020"

    echo ""
    log_step "RESULTS: $pass passed / $fail failed / $total total"

    if [ "$fail" -gt 0 ]; then
        return 1
    fi
}

# ── Full Deploy ───────────────────────────────────────────────────────
cmd_full() {
    log_step "FULL DEPLOYMENT — IMPILO vNEXT"
    echo "Starting at $(date)"
    echo ""

    cmd_discover
    echo ""

    cmd_prereqs
    echo ""

    cmd_build
    echo ""

    cmd_up
    echo ""

    log_info "Running final health checks..."
    if cmd_health; then
        echo ""
        log_step "DEPLOYMENT COMPLETE"
        echo ""
        log_ok "All services are healthy!"
    else
        echo ""
        log_step "DEPLOYMENT PARTIAL"
        echo ""
        log_warn "Some services are unhealthy. Check logs with:"
        echo "  ./scripts/server-deploy.sh logs <service-name>"
    fi

    echo ""
    echo "Completed at $(date)"
}

# ── Main ──────────────────────────────────────────────────────────────
case "${1:-help}" in
    discover)  cmd_discover ;;
    prereqs)   cmd_prereqs ;;
    build)     cmd_build ;;
    up)        cmd_up ;;
    down)      cmd_down ;;
    status)    cmd_status ;;
    logs)      shift; cmd_logs "$@" ;;
    health)    cmd_health ;;
    full)      cmd_full ;;
    help|*)
        echo "Usage: $0 {discover|prereqs|build|up|down|status|logs|health|full}"
        echo ""
        echo "  discover   Inspect server environment"
        echo "  prereqs    Install/verify prerequisites (Docker, Compose, git)"
        echo "  build      Build all service images + JARs"
        echo "  up         Start all services in dependency order"
        echo "  down       Stop and remove all containers"
        echo "  status     Show container health status"
        echo "  logs       Tail logs (optionally for a specific service)"
        echo "  health     Run health checks on all services"
        echo "  full       Full deploy: discover → prereqs → build → up → health"
        ;;
esac
