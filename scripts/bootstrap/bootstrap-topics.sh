#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Bootstrap: Kafka Topics
# =============================================================================
#
# Creates all platform Kafka topics. Idempotent — existing topics are skipped.
#
# Usage:
#   ./scripts/bootstrap/bootstrap-topics.sh
#   KAFKA_BOOTSTRAP=kafka:9092 ./scripts/bootstrap/bootstrap-topics.sh
#
# Environment:
#   KAFKA_BOOTSTRAP  (default: localhost:9092)
#   KAFKA_BIN        (default: auto-detected or docker exec)
#   USE_DOCKER       (default: true — runs via docker exec into kafka container)
#
# =============================================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'
NC='\033[0m'

info() { printf "${CYAN}[INFO]${NC}  %s\n" "$*"; }
ok()   { printf "${GREEN}[OK]${NC}    %s\n" "$*"; }
warn() { printf "${YELLOW}[WARN]${NC}  %s\n" "$*"; }
err()  { printf "${RED}[ERROR]${NC} %s\n" "$*" >&2; }

KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
USE_DOCKER="${USE_DOCKER:-true}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-impilo-kafka}"

# ── Kafka CLI wrapper ───────────────────────────────────────────────────────
kafka_cmd() {
    local cmd="$1"
    shift
    if [[ "${USE_DOCKER}" == "true" ]]; then
        docker exec "${KAFKA_CONTAINER}" /opt/kafka/bin/"${cmd}" "$@"
    else
        "${cmd}" "$@"
    fi
}

# ── Wait for Kafka ──────────────────────────────────────────────────────────
info "Waiting for Kafka at ${KAFKA_BOOTSTRAP}..."
MAX_WAIT=60
ELAPSED=0

while (( ELAPSED < MAX_WAIT )); do
    if kafka_cmd kafka-broker-api-versions.sh --bootstrap-server "${KAFKA_BOOTSTRAP}" > /dev/null 2>&1; then
        ok "Kafka ready (${ELAPSED}s)"
        break
    fi
    sleep 2
    ELAPSED=$(( ELAPSED + 2 ))
done

if (( ELAPSED >= MAX_WAIT )); then
    err "Kafka not ready within ${MAX_WAIT}s"
    exit 1
fi

# ── Topic definitions ───────────────────────────────────────────────────────
# Format: TOPIC_NAME:PARTITIONS:REPLICATION_FACTOR
TOPICS=(
    # Trust & Governance (TSHEPO)
    "platform.audit.events:3:1"
    "tshepo.audit.events:3:1"
    "platform.identity.events:3:1"
    "platform.consent.events:3:1"
    "platform.keys.events:3:1"
    "platform.offline.events:3:1"

    # Core Registries
    "vito.patient.events:3:1"
    "varapi.provider.events:3:1"
    "tuso.facility.events:3:1"
    "zibo.terminology.events:3:1"

    # Clinical
    "pct.encounter.events:3:1"
    "oros.order.events:3:1"
    "oros.result.events:3:1"
    "ubomi.visit.events:3:1"

    # Pharmacy & Supply Chain
    "pharmacy.dispensing.events:3:1"
    "pharmacy.stock.receipt:3:1"
    "pharmacy.reconcile.result:3:1"
    "inventory.stock.receipt:3:1"
    "inventory.reconcile.result:3:1"
    "elmis.requisition.forwarded:3:1"
    "elmis.order.forwarded:3:1"
    "msika.catalog.events:3:1"
    "msika.procurement.events:3:1"

    # Finance
    "mushex.claim.events:3:1"
    "costing.allocation.events:3:1"

    # Document & Credential
    "docs.document.events:3:1"
    "docs.credential.events:3:1"

    # Integration Hub
    "integration.dispatch.events:3:1"
    "integration.route.events:3:1"

    # IoT
    "impilo.iot.telemetry.device.raw:6:1"

    # Notifications
    "notification.outbound.events:3:1"

    # Platform-wide
    "platform.outbox.relay:6:1"
    "platform.dlq:3:1"
)

# ── Create topics ────────────────────────────────────────────────────────────
info "Creating ${#TOPICS[@]} topics..."
CREATED=0
EXISTED=0
FAILED=0

for topic_spec in "${TOPICS[@]}"; do
    IFS=':' read -r TOPIC PARTITIONS REPLICATION <<< "${topic_spec}"

    # Check if topic exists
    if kafka_cmd kafka-topics.sh --bootstrap-server "${KAFKA_BOOTSTRAP}" --describe --topic "${TOPIC}" > /dev/null 2>&1; then
        ok "EXISTS: ${TOPIC}"
        EXISTED=$(( EXISTED + 1 ))
    else
        if kafka_cmd kafka-topics.sh --bootstrap-server "${KAFKA_BOOTSTRAP}" \
            --create --topic "${TOPIC}" \
            --partitions "${PARTITIONS}" \
            --replication-factor "${REPLICATION}" 2>&1; then
            ok "CREATED: ${TOPIC} (partitions=${PARTITIONS}, rf=${REPLICATION})"
            CREATED=$(( CREATED + 1 ))
        else
            err "FAILED: ${TOPIC}"
            FAILED=$(( FAILED + 1 ))
        fi
    fi
done

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "=============================================="
echo "  Kafka Topic Bootstrap Summary"
echo "=============================================="
echo -e "  ${GREEN}Created${NC}:  ${CREATED}"
echo -e "  ${CYAN}Existed${NC}:  ${EXISTED}"
echo -e "  ${RED}Failed${NC}:   ${FAILED}"
echo "  Total:    ${#TOPICS[@]}"
echo "=============================================="

if (( FAILED > 0 )); then
    err "Some topics failed to create"
    exit 1
fi

ok "Kafka topic bootstrap complete"
