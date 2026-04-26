#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Wait for Platform Readiness
# =============================================================================
#
# Dependency-aware readiness checker. Waits for services in layer order,
# with health endpoint polling (no naive sleep-only waiting).
#
# Usage:
#   ./scripts/runtime/wait-for-readiness.sh [profile]
#   ./scripts/runtime/wait-for-readiness.sh lite    (default)
#   ./scripts/runtime/wait-for-readiness.sh full
#
# Exits 0 if all services healthy, 1 if any fail.
# =============================================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'
BOLD='\033[1m'; NC='\033[0m'

info()   { printf "${CYAN}[INFO]${NC}  %s\n" "$*"; }
ok()     { printf "${GREEN}[OK]${NC}    %s\n" "$*"; }
warn()   { printf "${YELLOW}[WARN]${NC}  %s\n" "$*"; }
err()    { printf "${RED}[ERROR]${NC} %s\n" "$*" >&2; }
step()   { printf "\n${BOLD}── %s ──${NC}\n" "$*"; }

PROFILE="${1:-lite}"
TOTAL_PASS=0
TOTAL_FAIL=0
TOTAL_WARN=0

# ── Health check function ───────────────────────────────────────────────────
# Polls a URL until it returns 200 or times out.
# Arguments: name, url, timeout_seconds
wait_for_health() {
    local name="$1"
    local url="$2"
    local timeout="${3:-120}"
    local elapsed=0
    local interval=3

    while (( elapsed < timeout )); do
        local code
        code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 "${url}" 2>/dev/null || echo "000")

        if [[ "${code}" == "200" ]]; then
            ok "${name} — healthy (${elapsed}s)"
            TOTAL_PASS=$(( TOTAL_PASS + 1 ))
            return 0
        fi

        printf "  ${CYAN}waiting${NC} %s (%ds/%ds) HTTP=%s\r" "${name}" "${elapsed}" "${timeout}" "${code}"
        sleep "${interval}"
        elapsed=$(( elapsed + interval ))
    done

    echo ""
    err "${name} — not healthy after ${timeout}s"
    TOTAL_FAIL=$(( TOTAL_FAIL + 1 ))
    return 1
}

# ── TCP port check ──────────────────────────────────────────────────────────
wait_for_port() {
    local name="$1"
    local host="$2"
    local port="$3"
    local timeout="${4:-60}"
    local elapsed=0

    while (( elapsed < timeout )); do
        if (echo > /dev/tcp/"${host}"/"${port}") 2>/dev/null; then
            ok "${name} — port ${port} open (${elapsed}s)"
            TOTAL_PASS=$(( TOTAL_PASS + 1 ))
            return 0
        fi
        sleep 2
        elapsed=$(( elapsed + 2 ))
    done

    err "${name} — port ${port} not open after ${timeout}s"
    TOTAL_FAIL=$(( TOTAL_FAIL + 1 ))
    return 1
}

# =============================================================================
echo ""
echo "============================================================"
echo "  Impilo vNext — Platform Readiness Check (${PROFILE})"
echo "============================================================"
echo ""

# ── Layer 0: Infrastructure ─────────────────────────────────────────────────
step "Layer 0: Infrastructure"

wait_for_port "PostgreSQL" "localhost" "5432" 30 || true
wait_for_port "Redis"      "localhost" "6379" 15 || true
wait_for_port "Kafka"      "localhost" "9092" 30 || true

# ── Layer 1: Shared Services ────────────────────────────────────────────────
step "Layer 1: Shared Services"

wait_for_health "Keycloak"  "http://localhost:8080/health/ready" 120 || true
wait_for_health "HAPI FHIR" "http://localhost:8090/fhir/metadata" 90 || true

# ── Layer 2: Edge ────────────────────────────────────────────────────────────
step "Layer 2: Edge"

wait_for_health "OPA"    "http://localhost:8181/health" 30 || true
wait_for_health "Envoy"  "http://localhost:9901/ready" 30 || true

# ── Layer 3: Ring 0 (Trust) ─────────────────────────────────────────────────
step "Layer 3: Ring 0 — Trust & Governance"

wait_for_health "TSHEPO" "http://localhost:8081/actuator/health" 120 || true

# ── Layer 4: Ring 1 (Core Registries) ────────────────────────────────────────
step "Layer 4: Ring 1 — Core Registries"

wait_for_health "VITO"   "http://localhost:8082/actuator/health" 120 || true
wait_for_health "VARAPI" "http://localhost:8083/actuator/health" 120 || true
wait_for_health "TUSO"   "http://localhost:8084/actuator/health" 120 || true
wait_for_health "ZIBO"   "http://localhost:8085/actuator/health" 120 || true

# ── Layer 5: Ring 2 (Clinical Core) ─────────────────────────────────────────
step "Layer 5: Ring 2 — Clinical Core"

wait_for_health "PCT"  "http://localhost:8088/actuator/health" 120 || true
wait_for_health "OROS" "http://localhost:8089/actuator/health" 120 || true

if [[ "${PROFILE}" != "lite" ]]; then
    wait_for_health "Ubomi"     "http://localhost:8087/actuator/health" 90 || true
    wait_for_health "Pharmacy"  "http://localhost:8097/actuator/health" 90 || true
    wait_for_health "Inventory" "http://localhost:8098/actuator/health" 90 || true
    wait_for_health "Msika"     "http://localhost:8086/actuator/health" 90 || true
fi

# ── Layer 7: Apps ────────────────────────────────────────────────────────────
step "Layer 7: Applications"

wait_for_health "Experience BFF" "http://localhost:8160/actuator/health" 90 || true
wait_for_health "One UI Shell"  "http://localhost:3000" 60 || true

# ── Layer 8: Observability (integration/pilot only) ──────────────────────────
if [[ "${PROFILE}" == "integration" || "${PROFILE}" == "pilot" ]]; then
    step "Layer 8: Observability"

    wait_for_health "Prometheus" "http://localhost:9090/-/healthy" 30 || true
    wait_for_health "Grafana"    "http://localhost:3100/api/health" 30 || true
    wait_for_health "OTel"       "http://localhost:13133/" 20 || true
    wait_for_health "Jaeger"     "http://localhost:16686" 20 || true
fi

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "============================================================"
echo "  Readiness Summary"
echo "============================================================"
echo -e "  ${GREEN}Healthy${NC}:  ${TOTAL_PASS}"
echo -e "  ${RED}Failed${NC}:   ${TOTAL_FAIL}"
echo "============================================================"

if (( TOTAL_FAIL > 0 )); then
    warn "${TOTAL_FAIL} service(s) not healthy"
    echo ""
    echo "  Troubleshooting:"
    echo "    1. Check logs: ./scripts/runtime/platformctl.sh logs <service>"
    echo "    2. Check status: ./scripts/runtime/platformctl.sh status"
    echo "    3. Restart specific service: docker compose restart <service>"
    exit 1
else
    ok "All services healthy"
fi
