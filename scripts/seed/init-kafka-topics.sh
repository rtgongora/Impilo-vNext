#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Seed: Trust Channel Kafka Topics
# =============================================================================
#
# Creates the high-assurance Trust Channel Kafka topics.
# Idempotent — existing topics are skipped.
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
MAX_WAIT=30
ELAPSED=0

while (( ELAPSED < MAX_WAIT )); do
    if kafka_cmd kafka-broker-api-versions.sh --bootstrap-server "${KAFKA_BOOTSTRAP}" > /dev/null 2>&1; then
        ok "Kafka ready"
        break
    fi
    sleep 2
    ELAPSED=$(( ELAPSED + 2 ))
done

if (( ELAPSED >= MAX_WAIT )); then
    err "Kafka not ready within ${MAX_WAIT}s"
    exit 1
fi

# ── Trust Channel Topics ────────────────────────────────────────────────────
# Settings: 3 partitions, RF 3, min.isr 2, retention 1 year
PARTITIONS=3
REPLICATION_FACTOR="${REPLICATION_FACTOR:-3}"
MIN_ISR=2
RETENTION_MS=31536000000

TRUST_TOPICS=(
    "trust.audit.chain"
    "trust.access.log"
    "trust.policy.change"
    "trust.cpid.mapping"
    "trust.consent.update"
    "trust.threat.intel"
)

info "Initializing ${#TRUST_TOPICS[@]} Trust Channel topics..."

for TOPIC in "${TRUST_TOPICS[@]}"; do
    if kafka_cmd kafka-topics.sh --bootstrap-server "${KAFKA_BOOTSTRAP}" --describe --topic "${TOPIC}" > /dev/null 2>&1; then
        ok "EXISTS: ${TOPIC}"
    else
        if kafka_cmd kafka-topics.sh --bootstrap-server "${KAFKA_BOOTSTRAP}" \
            --create --topic "${TOPIC}" \
            --if-not-exists \
            --partitions "${PARTITIONS}" \
            --replication-factor "${REPLICATION_FACTOR}" \
            --config min.insync.replicas="${MIN_ISR}" \
            --config retention.ms="${RETENTION_MS}" 2>&1; then
            ok "CREATED: ${TOPIC}"
        else
            err "FAILED: ${TOPIC}"
            exit 1
        fi
    fi
done

ok "Trust Channel topics initialized successfully"
