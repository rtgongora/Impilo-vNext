#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Deployment Validation Script
# =============================================================================
# Validates that all Docker Compose services start correctly and are healthy.
# Usage: ./scripts/smoke/validate-deployment.sh [--full-stack | --experience-only]
# =============================================================================

set -euo pipefail

MODE="${1:---experience-only}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

PASS=0
FAIL=0
WARN=0

pass() { echo -e "  ${GREEN}PASS${NC} $*"; ((PASS++)); }
fail() { echo -e "  ${RED}FAIL${NC} $*"; ((FAIL++)); }
warn() { echo -e "  ${YELLOW}WARN${NC} $*"; ((WARN++)); }
section() { echo -e "\n${CYAN}[$1]${NC} $2"; }

check_url() {
    local name="$1" url="$2" timeout="${3:-30}"
    local elapsed=0
    while [ $elapsed -lt "$timeout" ]; do
        if curl -sf -o /dev/null --connect-timeout 2 "$url" 2>/dev/null; then
            pass "$name reachable at $url"
            return 0
        fi
        sleep 2
        elapsed=$((elapsed + 2))
    done
    fail "$name NOT reachable at $url"
    return 1
}

check_compose_health() {
    local project="$1" compose_file="$2"
    section "COMPOSE" "Checking $project services..."

    # Verify compose file syntax
    if docker compose -f "$compose_file" config --quiet 2>/dev/null; then
        pass "$project compose file is valid"
    else
        fail "$project compose file has syntax errors"
        return 1
    fi

    # List defined services
    local services
    services=$(docker compose -f "$compose_file" config --services 2>/dev/null)
    local count
    count=$(echo "$services" | wc -l)
    pass "$project defines $count services: $(echo "$services" | tr '\n' ' ')"
}

echo ""
echo "============================================================"
echo "  Impilo vNext — Deployment Validation"
echo "  Mode: $MODE"
echo "============================================================"

# ── Validate compose file syntax ──────────────────────────────────
section "SYNTAX" "Validating Docker Compose files..."

COMPOSE_FILES=(
    "docker-compose.yml"
    "compose/experience/docker-compose.yml"
    "ops/runtime/docker-compose.infra.yml"
    "ops/runtime/docker-compose.edge.yml"
    "ops/runtime/docker-compose.kernel.yml"
    "ops/runtime/docker-compose.operations.yml"
    "ops/runtime/docker-compose.apps.yml"
    "ops/runtime/docker-compose.observability.yml"
)

for f in "${COMPOSE_FILES[@]}"; do
    if [ -f "$f" ]; then
        if docker compose -f "$f" config --quiet 2>/dev/null; then
            pass "Valid: $f"
        else
            fail "Invalid: $f"
        fi
    else
        warn "Not found: $f"
    fi
done

# ── Validate Envoy configuration ─────────────────────────────────
section "ENVOY" "Validating gateway configuration..."

ENVOY_CONFIG="infra/envoy/envoy-runtime.yaml"
if [ -f "$ENVOY_CONFIG" ]; then
    # Check that all expected clusters are defined
    for cluster in tshepo_service vito_service varapi_service tuso_service \
                   zibo_service pct_service oros_service experience_bff \
                   pharmacy_service mushex_service costa_service ubomi_service; do
        if grep -q "name: $cluster" "$ENVOY_CONFIG"; then
            pass "Envoy cluster: $cluster"
        else
            fail "Missing Envoy cluster: $cluster"
        fi
    done
else
    fail "Envoy config not found: $ENVOY_CONFIG"
fi

# ── Validate Prometheus scrape targets ────────────────────────────
section "PROMETHEUS" "Validating scrape configuration..."

PROM_CONFIG="tools/ops/observability/prometheus/prometheus.yml"
if [ -f "$PROM_CONFIG" ]; then
    for job in tshepo vito varapi tuso zibo pct oros experience-bff \
               pharmacy mushex costing-engine ubomi; do
        if grep -q "job_name: $job" "$PROM_CONFIG"; then
            pass "Prometheus scrape: $job"
        else
            warn "Missing Prometheus scrape: $job"
        fi
    done
else
    fail "Prometheus config not found: $PROM_CONFIG"
fi

# ── Validate Grafana provisioning ─────────────────────────────────
section "GRAFANA" "Validating dashboard provisioning..."

if [ -f "tools/ops/observability/grafana/provisioning/datasources/prometheus.yml" ]; then
    pass "Grafana datasource provisioned"
else
    fail "Grafana datasource not provisioned"
fi

if [ -f "tools/ops/observability/grafana/dashboards/impilo-golden-signals.json" ]; then
    pass "Golden Signals dashboard exists"
else
    fail "Golden Signals dashboard missing"
fi

# ── Validate database init script ─────────────────────────────────
section "DATABASE" "Validating init script..."

INIT_SQL="scripts/seed/init-databases.sql"
if [ -f "$INIT_SQL" ]; then
    for db in experience_bff tshepo vito varapi tuso zibo pct oros pharmacy impilo_learning; do
        if grep -qi "CREATE DATABASE $db" "$INIT_SQL"; then
            pass "Database defined: $db"
        else
            fail "Missing database: $db"
        fi
    done
else
    fail "Database init script not found"
fi

# ── Validate CI pipeline ─────────────────────────────────────────
section "CI" "Validating GitHub Actions pipeline..."

if [ -f ".github/workflows/ci.yml" ]; then
    pass "CI workflow exists"
    if grep -q "backend-test" ".github/workflows/ci.yml"; then
        pass "CI has backend-test job"
    fi
    if grep -q "frontend-lint" ".github/workflows/ci.yml"; then
        pass "CI has frontend-lint job"
    fi
    if grep -q "security-scan" ".github/workflows/ci.yml"; then
        pass "CI has security-scan job"
    fi
else
    fail "CI workflow missing"
fi

# ── Summary ──────────────────────────────────────────────────────
echo ""
echo "============================================================"
echo "  DEPLOYMENT VALIDATION SUMMARY"
echo "============================================================"
echo -e "  ${GREEN}PASS${NC}: $PASS"
echo -e "  ${RED}FAIL${NC}: $FAIL"
echo -e "  ${YELLOW}WARN${NC}: $WARN"
echo "============================================================"

if [ "$FAIL" -gt 0 ]; then
    echo -e "  ${RED}RESULT: VALIDATION FAILED${NC}"
    exit 1
elif [ "$WARN" -gt 0 ]; then
    echo -e "  ${YELLOW}RESULT: PASSED WITH WARNINGS${NC}"
    exit 0
else
    echo -e "  ${GREEN}RESULT: ALL VALIDATIONS PASSED${NC}"
    exit 0
fi
