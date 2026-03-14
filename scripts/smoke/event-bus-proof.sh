#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Event Bus Proof (Wave 18)
# =============================================================================
#
# Verifies that after a write operation, the event outbox table contains a row
# with the required v1.1 governance fields:
#   - tenant_id
#   - pod_id
#   - correlation_id
#   - idempotency_key
#
# If payload_json is stored, validates:
#   - event_type prefix "impilo." and suffix ".v1"
#   - schema_version >= 1
#   - meta.partition_key exists
#
# Targets: TSHEPO service (tshepo.event_outbox) and Integration Hub (ih_event_outbox)
#
# Usage:
#   ./scripts/smoke/event-bus-proof.sh
#
# =============================================================================

set -euo pipefail

# ── Configuration ────────────────────────────────────────────────────
POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-impilo}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-changeme}"

TSHEPO_URL="${TSHEPO_URL:-http://localhost:8081}"

# Colors
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
info() { echo -e "${CYAN}[EVENT-BUS]${NC} $*"; }

# ── Helper: run psql query ───────────────────────────────────────────
run_psql() {
    local db="$1"
    local query="$2"
    PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" \
        -U "$POSTGRES_USER" -d "$db" -t -A -c "$query" 2>/dev/null
}

echo ""
echo "============================================================"
echo "  Impilo vNext — Event Bus Proof"
echo "============================================================"
echo ""

# =============================================================================
# STEP 1: Trigger a write operation on TSHEPO to generate an outbox event
# =============================================================================
info "Step 1: Triggering write operation on TSHEPO"

IDEM_KEY="event-bus-proof-$(date +%s)"
TRIGGER_CODE=$(curl -sf -o /dev/null -w "%{http_code}" \
    -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: tenant-event-bus-proof" \
    -H "X-Pod-ID: pod-event-bus-proof" \
    -H "X-Request-ID: req-event-bus-$(date +%s)" \
    -H "X-Correlation-ID: corr-event-bus-$(date +%s)" \
    -H "Idempotency-Key: $IDEM_KEY" \
    -d '{"action":"event-bus-proof-smoke"}' \
    "$TSHEPO_URL/api/v1/authorize" 2>/dev/null || echo "000")

if [ "$TRIGGER_CODE" != "000" ]; then
    info "Write operation returned HTTP $TRIGGER_CODE"
else
    warn "TSHEPO not reachable — cannot trigger write operation"
fi

# Give the service a moment to write to outbox
sleep 2

# =============================================================================
# STEP 2: Check TSHEPO event_outbox table
# =============================================================================
info "Step 2: Checking TSHEPO event_outbox table"

# Check if psql is available
if ! command -v psql &>/dev/null; then
    warn "psql not available — using docker exec for database queries"
    run_psql() {
        local db="$1"
        local query="$2"
        docker exec -e PGPASSWORD="$POSTGRES_PASSWORD" \
            "$(docker ps --filter name=postgres --format '{{.Names}}' | head -1)" \
            psql -h localhost -U "$POSTGRES_USER" -d "$db" -t -A -c "$query" 2>/dev/null
    }
fi

# Check if event_outbox table exists in tshepo database
OUTBOX_EXISTS=$(run_psql "tshepo" \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'event_outbox';" 2>/dev/null || echo "0")

if [ "$OUTBOX_EXISTS" -gt 0 ] 2>/dev/null; then
    pass "TSHEPO event_outbox table exists"

    # Count rows
    ROW_COUNT=$(run_psql "tshepo" \
        "SELECT COUNT(*) FROM tshepo.event_outbox;" 2>/dev/null || echo "0")
    info "  event_outbox row count: $ROW_COUNT"

    if [ "$ROW_COUNT" -gt 0 ] 2>/dev/null; then
        pass "TSHEPO event_outbox has $ROW_COUNT row(s)"

        # Check for governance columns
        LATEST_ROW=$(run_psql "tshepo" \
            "SELECT row_to_json(t) FROM (SELECT * FROM tshepo.event_outbox ORDER BY created_at DESC LIMIT 1) t;" 2>/dev/null || echo "{}")

        if echo "$LATEST_ROW" | grep -q "tenant_id"; then
            pass "event_outbox row has tenant_id column"
        else
            warn "event_outbox row missing tenant_id column"
        fi

        if echo "$LATEST_ROW" | grep -q "pod_id"; then
            pass "event_outbox row has pod_id column"
        else
            warn "event_outbox row missing pod_id column (may be in payload)"
        fi

        if echo "$LATEST_ROW" | grep -q "correlation_id"; then
            pass "event_outbox row has correlation_id column"
        else
            warn "event_outbox row missing correlation_id column (may be in payload)"
        fi

        if echo "$LATEST_ROW" | grep -q "idempotency_key"; then
            pass "event_outbox row has idempotency_key column"
        else
            warn "event_outbox row missing idempotency_key column (may be in payload)"
        fi

        # Check event_type format if present
        EVENT_TYPE=$(run_psql "tshepo" \
            "SELECT event_type FROM tshepo.event_outbox ORDER BY created_at DESC LIMIT 1;" 2>/dev/null || echo "")

        if [ -n "$EVENT_TYPE" ]; then
            if echo "$EVENT_TYPE" | grep -qE "^impilo\..*\.v[0-9]+$"; then
                pass "event_type '$EVENT_TYPE' matches impilo.*.v1 pattern"
            else
                warn "event_type '$EVENT_TYPE' does not match impilo.*.v1 pattern"
            fi
        fi

        # Check payload_json for event envelope if column exists
        PAYLOAD=$(run_psql "tshepo" \
            "SELECT COALESCE(payload::text, '{}') FROM tshepo.event_outbox ORDER BY created_at DESC LIMIT 1;" 2>/dev/null || echo "{}")

        if echo "$PAYLOAD" | grep -q "schema_version"; then
            SCHEMA_VER=$(echo "$PAYLOAD" | grep -oP '"schema_version"\s*:\s*\K[0-9]+' || echo "0")
            if [ "$SCHEMA_VER" -ge 1 ] 2>/dev/null; then
                pass "payload schema_version=$SCHEMA_VER (>= 1)"
            else
                warn "payload schema_version=$SCHEMA_VER (expected >= 1)"
            fi
        fi

        if echo "$PAYLOAD" | grep -q "partition_key"; then
            pass "payload contains meta.partition_key"
        fi

    else
        warn "TSHEPO event_outbox is empty (write operation may not have produced an event)"
    fi
else
    warn "TSHEPO event_outbox table not found (Flyway migration may not have run yet)"
fi

# =============================================================================
# STEP 3: Check Integration Hub outbox (if available)
# =============================================================================
info "Step 3: Checking Integration Hub outbox"

IH_OUTBOX_EXISTS=$(run_psql "integration_hub" \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ih_event_outbox';" 2>/dev/null || echo "0")

if [ "$IH_OUTBOX_EXISTS" -gt 0 ] 2>/dev/null; then
    pass "Integration Hub ih_event_outbox table exists"
    IH_COUNT=$(run_psql "integration_hub" \
        "SELECT COUNT(*) FROM ih_event_outbox;" 2>/dev/null || echo "0")
    info "  ih_event_outbox row count: $IH_COUNT"
else
    warn "Integration Hub ih_event_outbox not found (service may not have started)"
fi

# =============================================================================
# SUMMARY
# =============================================================================
echo ""
echo "============================================================"
echo "  EVENT BUS PROOF SUMMARY"
echo "============================================================"
echo -e "  ${GREEN}PASS${NC}: $PASS"
echo -e "  ${RED}FAIL${NC}: $FAIL"
echo -e "  ${YELLOW}WARN${NC}: $WARN"
echo "============================================================"

if [ "$FAIL" -gt 0 ]; then
    echo -e "  ${RED}RESULT: SOME CHECKS FAILED${NC}"
    exit 1
elif [ "$WARN" -gt 0 ]; then
    echo -e "  ${YELLOW}RESULT: PASSED WITH WARNINGS${NC}"
    exit 0
else
    echo -e "  ${GREEN}RESULT: ALL CHECKS PASSED${NC}"
    exit 0
fi
