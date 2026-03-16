#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Platform Control (platformctl.sh)
# =============================================================================
#
# Master control entrypoint for the Impilo vNext platform.
# Manages the full lifecycle: build, bootstrap, start, verify, stop.
#
# Usage:
#   ./scripts/runtime/platformctl.sh <command> [options]
#
# Commands:
#   up lite          Start dev-lite profile (infra + core services + UI)
#   up full          Start dev-full profile (all services)
#   up integration   Start integration profile (all + observability)
#   up pilot         Start pilot profile (production-like)
#   down             Stop all platform services
#   status           Show health status of all running services
#   verify           Run readiness checks on all services
#   logs [service]   Tail logs (all services or specific service)
#   bootstrap        Run all bootstrap scripts (auth, topics, schemas, seed)
#   smoke            Run smoke test suite
#   build            Build all services (Maven + UI)
#   steel-threads    Run steel thread verification
#   evidence         Collect runtime evidence pack
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
RUNTIME_DIR="${PROJECT_ROOT}/ops/runtime"
BOOTSTRAP_DIR="${PROJECT_ROOT}/scripts/bootstrap"
ENV_DIR="${RUNTIME_DIR}/environments"

# ── Colors ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'
BOLD='\033[1m'; NC='\033[0m'

info()   { printf "${CYAN}[INFO]${NC}  %s\n" "$*"; }
ok()     { printf "${GREEN}[OK]${NC}    %s\n" "$*"; }
warn()   { printf "${YELLOW}[WARN]${NC}  %s\n" "$*"; }
err()    { printf "${RED}[ERROR]${NC} %s\n" "$*" >&2; }
header() { printf "\n${BOLD}═══ %s ═══${NC}\n\n" "$*"; }
step()   { printf "${BOLD}── %s ──${NC}\n" "$*"; }

# ── Compose file lists per profile ───────────────────────────────────────────
COMPOSE_INFRA="-f ${RUNTIME_DIR}/docker-compose.infra.yml"
COMPOSE_SHARED="-f ${RUNTIME_DIR}/docker-compose.shared.yml"
COMPOSE_EDGE="-f ${RUNTIME_DIR}/docker-compose.edge.yml"
COMPOSE_KERNEL="-f ${RUNTIME_DIR}/docker-compose.kernel.yml"
COMPOSE_OPS="-f ${RUNTIME_DIR}/docker-compose.operations.yml"
COMPOSE_APPS="-f ${RUNTIME_DIR}/docker-compose.apps.yml"
COMPOSE_OBS="-f ${RUNTIME_DIR}/docker-compose.observability.yml"

COMPOSE_LITE="${COMPOSE_INFRA} ${COMPOSE_SHARED} ${COMPOSE_EDGE} ${COMPOSE_KERNEL} ${COMPOSE_OPS} ${COMPOSE_APPS}"
COMPOSE_FULL="${COMPOSE_LITE}"
COMPOSE_INTEGRATION="${COMPOSE_FULL} ${COMPOSE_OBS}"
COMPOSE_PILOT="${COMPOSE_INTEGRATION}"

# Also support the existing monolith compose file
COMPOSE_LEGACY="-f ${PROJECT_ROOT}/docker-compose.runtime.yml"

get_compose_files() {
    local profile="${1:-lite}"
    case "${profile}" in
        lite)        echo "${COMPOSE_LITE}" ;;
        full)        echo "${COMPOSE_FULL}" ;;
        integration) echo "${COMPOSE_INTEGRATION}" ;;
        pilot)       echo "${COMPOSE_PILOT}" ;;
        legacy)      echo "${COMPOSE_LEGACY}" ;;
        *)           err "Unknown profile: ${profile}"; exit 1 ;;
    esac
}

get_env_file() {
    local profile="${1:-lite}"
    local env_file="${ENV_DIR}/${profile}.env"
    if [[ "${profile}" == "lite" ]]; then
        env_file="${ENV_DIR}/dev-lite.env"
    elif [[ "${profile}" == "full" ]]; then
        env_file="${ENV_DIR}/dev-full.env"
    fi
    echo "${env_file}"
}

# ── Prerequisites check ─────────────────────────────────────────────────────
check_prerequisites() {
    step "Checking prerequisites"

    local missing=false

    if ! command -v docker &> /dev/null; then
        err "docker not found — install Docker first"
        missing=true
    fi

    if ! docker compose version &> /dev/null 2>&1; then
        err "docker compose not found — install Docker Compose v2"
        missing=true
    fi

    if ! docker info &> /dev/null 2>&1; then
        err "Docker daemon not running — start Docker first"
        missing=true
    fi

    if [[ "${missing}" == "true" ]]; then
        exit 1
    fi

    ok "Prerequisites satisfied (docker, compose, daemon)"
}

# ── Load environment ─────────────────────────────────────────────────────────
load_env() {
    local profile="${1:-lite}"
    local env_file
    env_file=$(get_env_file "${profile}")

    if [[ -f "${env_file}" ]]; then
        info "Loading environment: ${env_file}"
        set -a
        # shellcheck source=/dev/null
        source "${env_file}"
        set +a
    else
        warn "Profile env file not found: ${env_file}"
        if [[ -f "${PROJECT_ROOT}/.env" ]]; then
            info "Falling back to .env"
            set -a
            # shellcheck source=/dev/null
            source "${PROJECT_ROOT}/.env"
            set +a
        fi
    fi
}

# ── Ensure .env exists ──────────────────────────────────────────────────────
ensure_env() {
    if [[ ! -f "${PROJECT_ROOT}/.env" ]]; then
        if [[ -f "${PROJECT_ROOT}/.env.example" ]]; then
            warn ".env not found — copying from .env.example"
            cp "${PROJECT_ROOT}/.env.example" "${PROJECT_ROOT}/.env"
        fi
    fi
}

# =============================================================================
# COMMANDS
# =============================================================================

# ── UP ───────────────────────────────────────────────────────────────────────
cmd_up() {
    local profile="${1:-lite}"
    local compose_files
    compose_files=$(get_compose_files "${profile}")

    header "Impilo vNext — Starting Platform (${profile})"

    check_prerequisites
    ensure_env
    load_env "${profile}"

    # Phase 1: App config bootstrap
    step "Phase 1: Configuration check"
    bash "${BOOTSTRAP_DIR}/bootstrap-app-config.sh"

    # Phase 2: Infrastructure
    step "Phase 2: Starting infrastructure (Layer 0)"
    docker compose ${COMPOSE_INFRA} up -d
    info "Waiting for infrastructure readiness..."
    docker compose ${COMPOSE_INFRA} up -d --wait postgres redis kafka

    # Phase 3: Shared services
    step "Phase 3: Starting shared services (Layer 1)"
    docker compose ${COMPOSE_INFRA} ${COMPOSE_SHARED} up -d keycloak hapi-fhir
    if [[ "${profile}" != "lite" ]]; then
        docker compose ${COMPOSE_INFRA} ${COMPOSE_SHARED} up -d orthanc 2>/dev/null || true
    fi

    # Phase 4: Edge
    step "Phase 4: Starting edge (Layer 2)"
    docker compose ${COMPOSE_INFRA} ${COMPOSE_SHARED} ${COMPOSE_EDGE} up -d opa envoy

    # Phase 5: Kernel (Ring 0 + Ring 1)
    step "Phase 5: Starting kernel services (Layers 3+4)"
    docker compose ${COMPOSE_INFRA} ${COMPOSE_SHARED} ${COMPOSE_EDGE} ${COMPOSE_KERNEL} up -d

    # Phase 6: Operations + Apps (for full profiles)
    step "Phase 6: Starting operations + apps (Layers 5-7)"
    if [[ "${profile}" == "lite" ]]; then
        # Lite: only PCT, OROS, BFF, UI
        docker compose ${compose_files} up -d pct oros experience-bff 2>/dev/null || true
        docker compose ${compose_files} up -d experience-ui 2>/dev/null || true
    else
        docker compose ${compose_files} up -d
    fi

    # Phase 7: Observability (for integration/pilot)
    if [[ "${profile}" == "integration" || "${profile}" == "pilot" ]]; then
        step "Phase 7: Starting observability (Layer 8)"
        docker compose ${compose_files} up -d prometheus grafana otel-collector jaeger 2>/dev/null || true
    fi

    # Phase 8: Readiness
    step "Phase 8: Waiting for readiness"
    bash "${SCRIPT_DIR}/wait-for-readiness.sh" "${profile}" || true

    # Print success info
    header "Platform Started (${profile})"
    echo ""
    echo "  Service URLs:"
    echo "  ─────────────────────────────────────────"
    echo "  Envoy Gateway:    http://localhost:10000"
    echo "  Experience UI:    http://localhost:3020"
    echo "  Keycloak:         http://localhost:8080"
    echo "  HAPI FHIR:        http://localhost:8090/fhir"
    echo "  OPA:              http://localhost:8181"
    echo "  Envoy Admin:      http://localhost:9901"
    echo ""
    echo "  Core Services:"
    echo "  ─────────────────────────────────────────"
    echo "  TSHEPO (Trust):   http://localhost:8081"
    echo "  VITO (Clients):   http://localhost:8082"
    echo "  VARAPI (Providers):http://localhost:8083"
    echo "  TUSO (Facilities):http://localhost:8084"
    echo "  ZIBO (Terms):     http://localhost:8085"
    echo "  PCT (Care):       http://localhost:8088"
    echo "  OROS (Orders):    http://localhost:8089"
    echo "  Experience BFF:   http://localhost:8160"
    echo ""

    if [[ "${profile}" == "integration" || "${profile}" == "pilot" ]]; then
        echo "  Observability:"
        echo "  ─────────────────────────────────────────"
        echo "  Prometheus:       http://localhost:9090"
        echo "  Grafana:          http://localhost:3100  (admin/admin)"
        echo "  Jaeger:           http://localhost:16686"
        echo ""
    fi

    echo "  Next steps:"
    echo "  ─────────────────────────────────────────"
    echo "  Bootstrap:   ./scripts/runtime/platformctl.sh bootstrap"
    echo "  Verify:      ./scripts/runtime/platformctl.sh verify"
    echo "  Smoke test:  ./scripts/runtime/platformctl.sh smoke"
    echo "  Status:      ./scripts/runtime/platformctl.sh status"
    echo "  Logs:        ./scripts/runtime/platformctl.sh logs"
    echo "  Stop:        ./scripts/runtime/platformctl.sh down"
    echo ""
}

# ── DOWN ─────────────────────────────────────────────────────────────────────
cmd_down() {
    header "Impilo vNext — Stopping Platform"

    # Stop in reverse layer order
    step "Stopping observability (Layer 8)"
    docker compose ${COMPOSE_OBS} down 2>/dev/null || true

    step "Stopping apps (Layer 7)"
    docker compose ${COMPOSE_APPS} down 2>/dev/null || true

    step "Stopping operations (Layer 5-6)"
    docker compose ${COMPOSE_OPS} down 2>/dev/null || true

    step "Stopping kernel (Layer 3-4)"
    docker compose ${COMPOSE_KERNEL} down 2>/dev/null || true

    step "Stopping edge (Layer 2)"
    docker compose ${COMPOSE_EDGE} down 2>/dev/null || true

    step "Stopping shared (Layer 1)"
    docker compose ${COMPOSE_SHARED} down 2>/dev/null || true

    step "Stopping infrastructure (Layer 0)"
    docker compose ${COMPOSE_INFRA} down 2>/dev/null || true

    # Also stop legacy compose if running
    docker compose ${COMPOSE_LEGACY} down 2>/dev/null || true

    ok "All platform services stopped"
}

# ── STATUS ───────────────────────────────────────────────────────────────────
cmd_status() {
    header "Impilo vNext — Platform Status"

    # Collect status from all compose files
    for layer_name in "Infrastructure" "Shared" "Edge" "Kernel" "Operations" "Apps" "Observability"; do
        local compose_flag=""
        case "${layer_name}" in
            Infrastructure) compose_flag="${COMPOSE_INFRA}" ;;
            Shared)         compose_flag="${COMPOSE_INFRA} ${COMPOSE_SHARED}" ;;
            Edge)           compose_flag="${COMPOSE_INFRA} ${COMPOSE_SHARED} ${COMPOSE_EDGE}" ;;
            Kernel)         compose_flag="${COMPOSE_INFRA} ${COMPOSE_SHARED} ${COMPOSE_EDGE} ${COMPOSE_KERNEL}" ;;
            Operations)     compose_flag="${COMPOSE_INFRA} ${COMPOSE_SHARED} ${COMPOSE_EDGE} ${COMPOSE_KERNEL} ${COMPOSE_OPS}" ;;
            Apps)           compose_flag="${COMPOSE_INFRA} ${COMPOSE_SHARED} ${COMPOSE_EDGE} ${COMPOSE_KERNEL} ${COMPOSE_OPS} ${COMPOSE_APPS}" ;;
            Observability)  compose_flag="${COMPOSE_INTEGRATION}" ;;
        esac

        echo ""
        step "${layer_name}"
        docker compose ${compose_flag} ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || true
    done
}

# ── VERIFY ───────────────────────────────────────────────────────────────────
cmd_verify() {
    header "Impilo vNext — Verify Platform"
    bash "${SCRIPT_DIR}/wait-for-readiness.sh" "${1:-lite}"
}

# ── LOGS ─────────────────────────────────────────────────────────────────────
cmd_logs() {
    local service="${1:-}"
    local all_compose="${COMPOSE_FULL}"

    if [[ -n "${service}" ]]; then
        docker compose ${all_compose} logs -f --tail=50 "${service}" 2>/dev/null || \
            docker compose ${COMPOSE_LEGACY} logs -f --tail=50 "${service}" 2>/dev/null || \
            err "Service '${service}' not found"
    else
        docker compose ${all_compose} logs -f --tail=20 2>/dev/null || \
            docker compose ${COMPOSE_LEGACY} logs -f --tail=20 2>/dev/null || true
    fi
}

# ── BOOTSTRAP ────────────────────────────────────────────────────────────────
cmd_bootstrap() {
    header "Impilo vNext — Bootstrap"

    step "1/5 App configuration"
    bash "${BOOTSTRAP_DIR}/bootstrap-app-config.sh"

    step "2/5 Authentication (Keycloak)"
    bash "${BOOTSTRAP_DIR}/bootstrap-auth.sh"

    step "3/5 Kafka topics"
    bash "${BOOTSTRAP_DIR}/bootstrap-topics.sh"

    step "4/5 Database schemas"
    bash "${BOOTSTRAP_DIR}/bootstrap-schemas.sh"

    step "5/5 Seed data"
    bash "${BOOTSTRAP_DIR}/bootstrap-seed-data.sh"

    ok "Bootstrap complete"
}

# ── SMOKE ────────────────────────────────────────────────────────────────────
cmd_smoke() {
    header "Impilo vNext — Smoke Tests"
    bash "${SCRIPT_DIR}/run-smoke-suite.sh"
}

# ── BUILD ────────────────────────────────────────────────────────────────────
cmd_build() {
    header "Impilo vNext — Build All Services"

    ensure_env
    mkdir -p "${PROJECT_ROOT}/vendor/m2/repository"
    mkdir -p "${PROJECT_ROOT}/vendor/node-cache"

    step "Phase 1: Maven build (all backend services)"
    docker compose -f "${PROJECT_ROOT}/docker-compose.build.yml" run --rm maven-builder

    step "Phase 2: UI build (Experience)"
    docker compose -f "${PROJECT_ROOT}/docker-compose.build.yml" run --rm ui-builder

    step "Phase 3: Building Docker images"
    local all_compose
    all_compose=$(get_compose_files "full")
    docker compose ${all_compose} build

    ok "Build complete"
}

# ── STEEL THREADS ────────────────────────────────────────────────────────────
cmd_steel_threads() {
    header "Impilo vNext — Steel Thread Verification"
    bash "${SCRIPT_DIR}/run-steel-threads.sh"
}

# ── EVIDENCE ─────────────────────────────────────────────────────────────────
cmd_evidence() {
    header "Impilo vNext — Collect Runtime Evidence"
    bash "${SCRIPT_DIR}/collect-runtime-evidence.sh"
}

# =============================================================================
# MAIN
# =============================================================================

print_usage() {
    echo "Usage: $0 <command> [options]"
    echo ""
    echo "Commands:"
    echo "  up <profile>     Start platform (lite|full|integration|pilot)"
    echo "  down             Stop all platform services"
    echo "  status           Show health status"
    echo "  verify [profile] Run readiness checks"
    echo "  logs [service]   Tail logs"
    echo "  bootstrap        Run all bootstrap scripts"
    echo "  smoke            Run smoke test suite"
    echo "  build            Build all services"
    echo "  steel-threads    Run steel thread verification"
    echo "  evidence         Collect runtime evidence"
    echo ""
    echo "Profiles:"
    echo "  lite             Minimal dev (default) — core services only"
    echo "  full             Full dev — all services"
    echo "  integration      Full + observability"
    echo "  pilot            Production-like"
    echo ""
    echo "Examples:"
    echo "  $0 up lite       # Start minimal platform"
    echo "  $0 up full       # Start full platform"
    echo "  $0 bootstrap     # Bootstrap auth, topics, schemas"
    echo "  $0 smoke         # Run smoke tests"
    echo "  $0 down          # Stop everything"
}

case "${1:-help}" in
    up)             cmd_up "${2:-lite}" ;;
    down)           cmd_down ;;
    status)         cmd_status ;;
    verify)         cmd_verify "${2:-lite}" ;;
    logs)           cmd_logs "${2:-}" ;;
    bootstrap)      cmd_bootstrap ;;
    smoke)          cmd_smoke ;;
    build)          cmd_build ;;
    steel-threads)  cmd_steel_threads ;;
    evidence)       cmd_evidence ;;
    help|--help|-h) print_usage ;;
    *)              err "Unknown command: $1"; print_usage; exit 1 ;;
esac
