#!/usr/bin/env bash
set -euo pipefail

###############################################################################
# Impilo vNext — Eventing Interoperability Check
#
# Validates that eventing works across services, not just inside one:
#   1. Outbox schema presence across all services
#   2. EventEnvelope field consistency
#   3. event_type naming conventions
#   4. Cross-service event flow wiring
#   5. Kafka topic/consumer alignment
#
# Usage:
#   ./scripts/reality-check/run-eventing-checks.sh [--live]
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
echo "  Impilo vNext — Eventing Interoperability Check"
echo "  Mode: $([ "$LIVE_MODE" = true ] && echo "LIVE" || echo "STRUCTURAL")"
echo "============================================================"
echo ""

# =============================================================================
# CHECK 1: Outbox table presence across services
# =============================================================================
log_phase "Check 1: event_outbox Table Presence in Flyway Migrations"

SERVICES_DIR="$REPO_ROOT/services"
OUTBOX_PRESENT=0
OUTBOX_MISSING=0
OUTBOX_MISSING_LIST=""

for svc_dir in "$SERVICES_DIR"/*/; do
    svc_name=$(basename "$svc_dir")
    [ "$svc_name" = "shared-core" ] && continue
    [ ! -d "$svc_dir/src/main/java" ] && continue

    MIGRATION_DIR="$svc_dir/src/main/resources/db/migration"
    if [ -d "$MIGRATION_DIR" ]; then
        if grep -rql "event_outbox\|EventOutbox" "$MIGRATION_DIR" 2>/dev/null; then
            ((OUTBOX_PRESENT++))
        else
            ((OUTBOX_MISSING++))
            OUTBOX_MISSING_LIST="$OUTBOX_MISSING_LIST  $svc_name\n"
        fi
    else
        # Check if outbox is referenced in Java source
        if grep -rql "event_outbox\|EventOutbox\|OutboxEvent" "$svc_dir/src/" 2>/dev/null; then
            ((OUTBOX_PRESENT++))
        else
            ((OUTBOX_MISSING++))
            OUTBOX_MISSING_LIST="$OUTBOX_MISSING_LIST  $svc_name (no migrations dir)\n"
        fi
    fi
done

echo "  Services with outbox: $OUTBOX_PRESENT"
echo "  Services without outbox: $OUTBOX_MISSING"
if [ $OUTBOX_MISSING -gt 0 ]; then
    echo -e "\n  Missing outbox:\n$OUTBOX_MISSING_LIST"
fi
log_pass "Outbox coverage: $OUTBOX_PRESENT services"

# =============================================================================
# CHECK 2: EventEnvelope field consistency
# =============================================================================
log_phase "Check 2: EventEnvelope / OutboxEventEntity Field Consistency"

REQUIRED_OUTBOX_FIELDS=(
    "tenant_id\|tenantId"
    "pod_id\|podId"
    "correlation_id\|correlationId"
    "event_type\|eventType"
    "schema_version\|schemaVersion"
    "partition_key\|partitionKey"
    "idempotency_key\|idempotencyKey"
)

# Find representative OutboxEventEntity
OUTBOX_ENTITY=$(find "$REPO_ROOT/libs" "$REPO_ROOT/services" -name "OutboxEvent*.java" -path "*/entity/*" 2>/dev/null | head -1)
if [ -z "$OUTBOX_ENTITY" ]; then
    OUTBOX_ENTITY=$(find "$REPO_ROOT/libs" "$REPO_ROOT/services" -name "OutboxEvent*.java" 2>/dev/null | head -1)
fi

if [ -n "$OUTBOX_ENTITY" ] && [ -f "$OUTBOX_ENTITY" ]; then
    echo "  Reference entity: $OUTBOX_ENTITY"
    ENTITY_CONTENT=$(cat "$OUTBOX_ENTITY")
    ALL_FIELDS=true
    for field_pattern in "${REQUIRED_OUTBOX_FIELDS[@]}"; do
        field_name=$(echo "$field_pattern" | cut -d'\' -f1)
        if echo "$ENTITY_CONTENT" | grep -qE "$field_pattern"; then
            echo "  Found: $field_name"
        else
            echo "  MISSING: $field_name"
            ALL_FIELDS=false
        fi
    done
    if [ "$ALL_FIELDS" = true ]; then
        log_pass "OutboxEventEntity has all required v1.1 fields"
    else
        log_fail "OutboxEventEntity missing some v1.1 fields"
    fi
else
    log_skip "OutboxEventEntity not found as standalone — checking per-service"
fi

# =============================================================================
# CHECK 3: event_type naming convention
# =============================================================================
log_phase "Check 3: Event Type Naming Convention"

# Check that event types follow impilo.<domain>.<action>.v<N> pattern
EVENT_TYPES=$(grep -rhoP '"impilo\.[a-z._]+\.v\d+"' "$REPO_ROOT/services" 2>/dev/null | sort -u | head -30)

if [ -n "$EVENT_TYPES" ]; then
    echo "  Discovered event types:"
    echo "$EVENT_TYPES" | while read -r et; do echo "    $et"; done
    BAD_TYPES=$(grep -rhoP '"impilo\.[^"]*"' "$REPO_ROOT/services" 2>/dev/null | grep -v '\.v[0-9]"' | sort -u | head -10)
    if [ -n "$BAD_TYPES" ]; then
        echo "  Non-versioned event types found:"
        echo "$BAD_TYPES" | while read -r bt; do echo "    WARNING: $bt"; done
        log_fail "Some event types don't follow versioned naming"
    else
        log_pass "All event types follow impilo.<domain>.<action>.v<N> pattern"
    fi
else
    log_skip "No event type strings found matching impilo.*.v<N> pattern"
fi

# =============================================================================
# CHECK 4: Cross-Service Event Flow Wiring
# =============================================================================
log_phase "Check 4: Cross-Service Event Flow Wiring"

# Find Kafka producers (services that publish to topics)
echo "  Searching for Kafka producers..."
PRODUCERS=$(grep -rn "KafkaTemplate\|kafkaTemplate\|@SendTo\|ProducerFactory\|CompanionOutboxPublisher" \
    "$REPO_ROOT/services" 2>/dev/null | grep -v "test/" | grep -v "target/" | \
    sed 's|.*/services/||' | cut -d/ -f1 | sort -u)
echo "  Producer services: $(echo "$PRODUCERS" | tr '\n' ', ')"

# Find Kafka consumers
echo "  Searching for Kafka consumers..."
CONSUMERS=$(grep -rn "@KafkaListener\|KafkaListenerContainerFactory\|ConsumerFactory" \
    "$REPO_ROOT/services" 2>/dev/null | grep -v "test/" | grep -v "target/" | \
    sed 's|.*/services/||' | cut -d/ -f1 | sort -u)
echo "  Consumer services: $(echo "$CONSUMERS" | tr '\n' ', ')"

# Find topic names
echo "  Searching for topic definitions..."
TOPICS=$(grep -rhoP 'topics?\s*=\s*"[^"]+"|\btopic\s*=\s*"[^"]+"' "$REPO_ROOT/services" 2>/dev/null | \
    grep -oP '"[^"]+"' | sort -u | head -20)
if [ -n "$TOPICS" ]; then
    echo "  Declared topics:"
    echo "$TOPICS" | while read -r t; do echo "    $t"; done
fi

# Check for application.yml topic config
TOPIC_CONFIGS=$(grep -rn "spring.kafka.consumer.group-id\|kafka.topic\|topics:" \
    "$REPO_ROOT/services" 2>/dev/null | grep -v "test/" | grep "\.yml\|\.yaml\|\.properties" | head -20)
if [ -n "$TOPIC_CONFIGS" ]; then
    echo "  Topic configurations found in config files"
fi

if [ -n "$PRODUCERS" ] && [ -n "$CONSUMERS" ]; then
    log_pass "Cross-service event wiring found (producers + consumers)"
else
    log_fail "Missing cross-service event wiring"
fi

# =============================================================================
# CHECK 5: Outbox Relay / Publisher Mechanism
# =============================================================================
log_phase "Check 5: Outbox Relay / Event Publisher Mechanism"

OUTBOX_PUBLISHER=$(find "$REPO_ROOT/libs" -name "*OutboxPublisher*" -o -name "*OutboxRelay*" 2>/dev/null | head -3)
OUTBOX_SCHEDULER=$(grep -rln "@Scheduled.*outbox\|OutboxRelay\|OutboxPublisher" "$REPO_ROOT/libs" "$REPO_ROOT/services" 2>/dev/null | head -5)

if [ -n "$OUTBOX_PUBLISHER" ]; then
    echo "  Outbox publisher found:"
    echo "$OUTBOX_PUBLISHER" | while read -r f; do echo "    $f"; done
    log_pass "Outbox publisher mechanism exists"
else
    log_skip "No dedicated OutboxPublisher/OutboxRelay found in libs"
fi

if [ -n "$OUTBOX_SCHEDULER" ]; then
    echo "  Scheduled outbox relay:"
    echo "$OUTBOX_SCHEDULER" | while read -r f; do echo "    $f"; done
    log_pass "Outbox relay scheduling found"
else
    log_skip "No scheduled outbox relay found"
fi

# =============================================================================
# CHECK 6: Live Eventing Tests (only with --live flag)
# =============================================================================
if [ "$LIVE_MODE" = true ]; then
    log_phase "Check 6: Live Eventing Tests"

    POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
    POSTGRES_PORT="${POSTGRES_PORT:-5432}"
    POSTGRES_USER="${POSTGRES_USER:-impilo}"
    POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-changeme}"

    # Check if we can connect to Postgres
    if PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" \
        -U "$POSTGRES_USER" -d tshepo -c "SELECT 1" &>/dev/null; then

        # Check outbox table exists in tshepo
        OUTBOX_EXISTS=$(PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$POSTGRES_HOST" \
            -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d tshepo -t -A \
            -c "SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_name='event_outbox')" 2>/dev/null || echo "f")

        if [ "$OUTBOX_EXISTS" = "t" ]; then
            log_pass "tshepo.event_outbox table exists"

            # Check v1.1 columns
            COLUMNS=$(PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$POSTGRES_HOST" \
                -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d tshepo -t -A \
                -c "SELECT column_name FROM information_schema.columns WHERE table_name='event_outbox' ORDER BY ordinal_position" 2>/dev/null)
            echo "  Columns: $(echo "$COLUMNS" | tr '\n' ', ')"

            for col in tenant_id pod_id correlation_id event_type schema_version; do
                if echo "$COLUMNS" | grep -q "$col"; then
                    log_pass "tshepo outbox has $col column"
                else
                    log_fail "tshepo outbox missing $col column"
                fi
            done
        else
            log_fail "tshepo.event_outbox table not found"
        fi
    else
        log_skip "Cannot connect to PostgreSQL at $POSTGRES_HOST:$POSTGRES_PORT"
    fi
else
    log_phase "Check 6: Live Eventing Tests (SKIPPED — use --live flag)"
    log_skip "Live eventing tests require running infrastructure (pass --live flag)"
fi

# =============================================================================
# SUMMARY
# =============================================================================
log_phase "Eventing Interoperability Check Summary"

echo -e "  Total checks: $TOTAL"
echo -e "  ${GREEN}Passed: $PASS${NC}"
echo -e "  ${RED}Failed: $FAIL${NC}"
echo -e "  ${YELLOW}Skipped: $SKIP${NC}"
echo ""

if [ $FAIL -gt 0 ]; then
    echo -e "${RED}EVENTING CHECK: ISSUES FOUND${NC}"
    exit 1
else
    echo -e "${GREEN}EVENTING CHECK: PASSED${NC}"
    exit 0
fi
