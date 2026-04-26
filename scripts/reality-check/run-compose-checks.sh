#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# Impilo vNext — Compose / Runtime Wiring Check
#
# Validates that dev/runtime composition is coherent:
#   1. Infrastructure services present and healthy
#   2. Backend services wired correctly
#   3. UI/apps wired
#   4. Healthcheck definitions
#   5. Smoke scripts target actual services
#   6. Dockerfile coverage gap
#
# Usage:
#   ./scripts/reality-check/run-compose-checks.sh [--live]
###############################################################################

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

PASS=0
FAIL=0
SKIP=0
TOTAL=0

log_phase() { echo -e "\n${CYAN}═══ $* ═══${NC}\n"; }
log_pass()  { echo -e "  ${GREEN}PASS${NC} $*"; ((PASS++)); ((TOTAL++)); }
log_fail()  { echo -e "  ${RED}FAIL${NC} $*"; ((FAIL++)); ((TOTAL++)); }
log_skip()  { echo -e "  ${YELLOW}SKIP${NC} $*"; ((SKIP++)); ((TOTAL++)); }

LIVE_MODE=false
[ "${1:-}" = "--live" ] && LIVE_MODE=true

echo ""
echo "============================================================"
echo "  Impilo vNext — Compose / Runtime Wiring Check"
echo "  Mode: $([ "$LIVE_MODE" = true ] && echo "LIVE" || echo "STRUCTURAL")"
echo "============================================================"
echo ""

# =============================================================================
# CHECK 1: Compose File Existence
# =============================================================================
log_phase "Check 1: Compose File Inventory"

COMPOSE_FILES=(
    "docker-compose.yml:Base infrastructure"
    "docker-compose.build.yml:Build phase"
    "docker-compose.runtime.yml:Full runtime"
)

for entry in "${COMPOSE_FILES[@]}"; do
    IFS=':' read -r file desc <<< "$entry"
    if [ -f "$REPO_ROOT/$file" ]; then
        log_pass "$file exists ($desc)"
    else
        log_fail "$file missing ($desc)"
    fi
done

# =============================================================================
# CHECK 2: Infrastructure Services in Runtime Compose
# =============================================================================
log_phase "Check 2: Infrastructure Services"

RUNTIME_COMPOSE="$REPO_ROOT/docker-compose.runtime.yml"
REQUIRED_INFRA=(
    "postgres:PostgreSQL 16"
    "redis:Redis 7"
    "kafka:Apache Kafka 3.7"
    "keycloak:Keycloak 25.x"
    "minio:MinIO (S3)"
    "hapi-fhir:HAPI FHIR R4"
)

if [ -f "$RUNTIME_COMPOSE" ]; then
    for entry in "${REQUIRED_INFRA[@]}"; do
        IFS=':' read -r svc desc <<< "$entry"
        if grep -qE "^\s+${svc}:" "$RUNTIME_COMPOSE"; then
            log_pass "Infra: $svc ($desc) — defined"
            # Check healthcheck
            # Use a simple heuristic: check if healthcheck block follows the service
            if grep -A 30 "^\s\+${svc}:" "$RUNTIME_COMPOSE" | grep -q "healthcheck"; then
                echo "    healthcheck: defined"
            else
                echo "    healthcheck: MISSING"
            fi
        else
            log_fail "Infra: $svc ($desc) — MISSING from runtime compose"
        fi
    done
else
    log_fail "docker-compose.runtime.yml not found"
fi

# =============================================================================
# CHECK 3: Edge Services
# =============================================================================
log_phase "Check 3: Edge / Gateway Services"

EDGE_SERVICES=(
    "envoy:Envoy Gateway"
    "opa:Open Policy Agent"
)

for entry in "${EDGE_SERVICES[@]}"; do
    IFS=':' read -r svc desc <<< "$entry"
    if grep -qE "^\s+${svc}:" "$RUNTIME_COMPOSE" 2>/dev/null; then
        log_pass "Edge: $svc ($desc) — defined"
    else
        log_fail "Edge: $svc ($desc) — MISSING"
    fi
done

# =============================================================================
# CHECK 4: Backend Services in Runtime Compose vs Total
# =============================================================================
log_phase "Check 4: Backend Service Coverage"

# Services in runtime compose (excluding infra, edge, ui)
INFRA_NAMES="postgres redis kafka keycloak minio hapi-fhir envoy opa orthanc"
UI_NAMES="one-ui-shell"
COMPOSE_BACKENDS=()

while IFS= read -r svc; do
    svc_clean=$(echo "$svc" | xargs)
    [ -z "$svc_clean" ] && continue
    if ! echo "$INFRA_NAMES $UI_NAMES" | grep -qw "$svc_clean"; then
        COMPOSE_BACKENDS+=("$svc_clean")
    fi
done < <(grep -E "^\s+[a-z]" "$RUNTIME_COMPOSE" 2>/dev/null | sed 's/://' | awk '{print $1}')

TOTAL_SERVICES=$(find "$REPO_ROOT/services" -maxdepth 1 -mindepth 1 -type d ! -name "shared-core" | wc -l)
COMPOSE_COUNT=${#COMPOSE_BACKENDS[@]}

echo "  Backend services in runtime compose: $COMPOSE_COUNT"
echo "  Total backend services in repo: $TOTAL_SERVICES"
echo "  Compose backends: ${COMPOSE_BACKENDS[*]}"
echo ""

if [ $COMPOSE_COUNT -ge $TOTAL_SERVICES ]; then
    log_pass "All backend services wired in compose"
else
    COVERAGE_PCT=$(( (COMPOSE_COUNT * 100) / TOTAL_SERVICES ))
    log_fail "Only $COMPOSE_COUNT/$TOTAL_SERVICES ($COVERAGE_PCT%) backend services in runtime compose"

    echo "  Services NOT in runtime compose:"
    for svc_dir in "$REPO_ROOT/services"/*/; do
        svc_name=$(basename "$svc_dir")
        [ "$svc_name" = "shared-core" ] && continue
        [ ! -f "$svc_dir/pom.xml" ] && continue
        FOUND=false
        for csvc in "${COMPOSE_BACKENDS[@]}"; do
            # Check various naming patterns
            if [ "$csvc" = "$svc_name" ] || [ "$csvc" = "${svc_name%-service}" ] || \
               [ "$csvc-service" = "$svc_name" ]; then
                FOUND=true
                break
            fi
        done
        if [ "$FOUND" = false ]; then
            echo "    MISSING: $svc_name"
        fi
    done
fi

# =============================================================================
# CHECK 5: Healthcheck Coverage in Compose
# =============================================================================
log_phase "Check 5: Healthcheck Coverage"

HEALTHCHECK_COUNT=$(grep -c "healthcheck:" "$RUNTIME_COMPOSE" 2>/dev/null || echo "0")
SERVICE_COUNT_COMPOSE=$(grep -cE "^\s+[a-z][a-z0-9_-]+:" "$RUNTIME_COMPOSE" 2>/dev/null || echo "0")

echo "  Services with healthcheck: $HEALTHCHECK_COUNT"
echo "  Total services in compose: $SERVICE_COUNT_COMPOSE"

if [ "$HEALTHCHECK_COUNT" -ge "$SERVICE_COUNT_COMPOSE" ]; then
    log_pass "All compose services have healthchecks"
else
    log_fail "Only $HEALTHCHECK_COUNT/$SERVICE_COUNT_COMPOSE services have healthchecks"
fi

# =============================================================================
# CHECK 6: Dockerfile Coverage
# =============================================================================
log_phase "Check 6: Dockerfile Coverage"

WITH_DOCKERFILE=0
WITHOUT_DOCKERFILE=0

for svc_dir in "$REPO_ROOT/services"/*/; do
    svc_name=$(basename "$svc_dir")
    [ "$svc_name" = "shared-core" ] && continue
    [ ! -f "$svc_dir/pom.xml" ] && continue

    if [ -f "$svc_dir/Dockerfile" ]; then
        ((WITH_DOCKERFILE++))
    else
        ((WITHOUT_DOCKERFILE++))
    fi
done

echo "  With Dockerfile: $WITH_DOCKERFILE"
echo "  Without Dockerfile: $WITHOUT_DOCKERFILE"

if [ $WITHOUT_DOCKERFILE -eq 0 ]; then
    log_pass "All services have Dockerfiles"
else
    log_fail "$WITHOUT_DOCKERFILE services missing Dockerfiles"
fi

# =============================================================================
# CHECK 7: Smoke Scripts
# =============================================================================
log_phase "Check 7: Smoke & Validation Scripts"

SMOKE_DIR="$REPO_ROOT/scripts/smoke"
EXPECTED_SCRIPTS=("smoke.sh" "event-bus-proof.sh" "route-parity.sh")

for script in "${EXPECTED_SCRIPTS[@]}"; do
    if [ -f "$SMOKE_DIR/$script" ]; then
        log_pass "Smoke script: $script exists"
        if [ -x "$SMOKE_DIR/$script" ]; then
            echo "    executable: yes"
        else
            echo "    executable: no (needs chmod +x)"
        fi
    else
        log_fail "Smoke script: $script missing"
    fi
done

# Dev runtime wrapper
if [ -f "$REPO_ROOT/scripts/dev-runtime.sh" ]; then
    log_pass "Dev runtime wrapper: scripts/dev-runtime.sh exists"
else
    log_fail "Dev runtime wrapper missing"
fi

# =============================================================================
# CHECK 8: Envoy Configuration
# =============================================================================
log_phase "Check 8: Envoy Configuration"

ENVOY_CONFIG="$REPO_ROOT/infra/envoy/envoy-runtime.yaml"
if [ -f "$ENVOY_CONFIG" ]; then
    log_pass "Envoy runtime config exists"

    # Check for ext_authz reference
    if grep -q "ext_authz\|external_authorization" "$ENVOY_CONFIG" 2>/dev/null; then
        log_pass "Envoy config references ext_authz"
    else
        log_skip "Envoy config may not have ext_authz (check manually)"
    fi
else
    ENVOY_ALT=$(find "$REPO_ROOT/infra/envoy" -name "*.yaml" -o -name "*.yml" 2>/dev/null | head -3)
    if [ -n "$ENVOY_ALT" ]; then
        echo "  Found alternative Envoy configs:"
        echo "$ENVOY_ALT"
        log_pass "Envoy configuration files exist"
    else
        log_fail "No Envoy configuration found"
    fi
fi

# =============================================================================
# CHECK 9: Live Runtime Test (only with --live flag)
# =============================================================================
if [ "$LIVE_MODE" = true ]; then
    log_phase "Check 9: Live Runtime Health"

    ENDPOINTS=(
        "http://localhost:8081/actuator/health:tshepo"
        "http://localhost:8082/actuator/health:vito"
        "http://localhost:8083/actuator/health:varapi"
        "http://localhost:8084/actuator/health:tuso"
        "http://localhost:8085/actuator/health:zibo"
        "http://localhost:8088/actuator/health:pct"
        "http://localhost:8089/actuator/health:oros"
        "http://localhost:8160/actuator/health:experience-bff"
        "http://localhost:3000:one-ui-shell"
        "http://localhost:9901/ready:envoy"
        "http://localhost:8181/health:opa"
    )

    for entry in "${ENDPOINTS[@]}"; do
        IFS=':' read -r scheme host port path svc <<< "$(echo "$entry" | sed 's|http://\([^:]*\):\([^/]*\)\(.*\):\([^:]*\)|http:\1:\2:\3:\4|')"
        url="${scheme}://${host}:${port}${path}"
        svc=$(echo "$entry" | rev | cut -d: -f1 | rev)
        url=$(echo "$entry" | rev | cut -d: -f2- | rev)

        HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" --connect-timeout 3 "$url" 2>/dev/null || echo "000")
        if [ "$HTTP_CODE" = "200" ]; then
            log_pass "$svc healthy (HTTP 200)"
        elif [ "$HTTP_CODE" = "000" ]; then
            log_fail "$svc unreachable at $url"
        else
            log_fail "$svc returned HTTP $HTTP_CODE"
        fi
    done
else
    log_phase "Check 9: Live Runtime Test (SKIPPED — use --live flag)"
    log_skip "Live runtime tests require 'docker compose -f docker-compose.runtime.yml up -d'"
fi

# =============================================================================
# SUMMARY
# =============================================================================
log_phase "Compose / Runtime Wiring Check Summary"

echo -e "  Total checks: $TOTAL"
echo -e "  ${GREEN}Passed: $PASS${NC}"
echo -e "  ${RED}Failed: $FAIL${NC}"
echo -e "  ${YELLOW}Skipped: $SKIP${NC}"
echo ""

if [ $FAIL -gt 0 ]; then
    echo -e "${RED}COMPOSE CHECK: ISSUES FOUND${NC}"
    exit 1
else
    echo -e "${GREEN}COMPOSE CHECK: PASSED${NC}"
    exit 0
fi
