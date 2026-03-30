#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Seed: All Platform Kafka Topics
# =============================================================================
#
# Creates ALL platform Kafka topics across every bus namespace.
# Idempotent — existing topics are skipped (--if-not-exists).
#
# Buses and their policies:
#   trust.*      RF 3, min.isr 2, retention 1yr (audit) / 30d (revocation)
#   kernel.*     RF 3, min.isr 2, retention 14d
#   clinical.*   RF 3, min.isr 2, retention 7d
#   telemetry.*  RF 2, min.isr 1, retention 30d
#   analytics.*  RF 2, min.isr 1, retention 90d
#   platform.*   RF 1, min.isr 1, retention 30d  (internal platform events)
#   Legacy       RF 1, no min.isr  (backward-compat during P4-2 DualEmitPolicy rollout)
#
# Usage:
#   ./scripts/seed/init-kafka-topics.sh
#   KAFKA_BOOTSTRAP=kafka:9092 USE_DOCKER=false ./scripts/seed/init-kafka-topics.sh
#
# Environment:
#   KAFKA_BOOTSTRAP    (default: localhost:9092)
#   KAFKA_CONTAINER    (default: impilo-kafka)
#   USE_DOCKER         (default: true — runs via docker exec into kafka container)
#   REPLICATION_FACTOR (default: 3, set to 1 for single-broker dev environments)
#
# =============================================================================

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'
NC='\033[0m'

info()    { printf "${CYAN}[INFO]${NC}  %s\n" "$*"; }
ok()      { printf "${GREEN}[OK]${NC}    %s\n" "$*"; }
warn()    { printf "${YELLOW}[WARN]${NC}  %s\n" "$*"; }
err()     { printf "${RED}[ERROR]${NC} %s\n" "$*" >&2; }
section() { printf "\n${BOLD}${CYAN}══ %s ══${NC}\n" "$*"; }

KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
USE_DOCKER="${USE_DOCKER:-true}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-impilo-kafka}"
REPLICATION_FACTOR="${REPLICATION_FACTOR:-3}"

CREATED=0
EXISTED=0
FAILED=0

# ── Kafka CLI wrapper ────────────────────────────────────────────────────────
kafka_cmd() {
    local cmd="$1"
    shift
    if [[ "${USE_DOCKER}" == "true" ]]; then
        docker exec "${KAFKA_CONTAINER}" /opt/kafka/bin/"${cmd}" "$@"
    else
        "${cmd}" "$@"
    fi
}

# ── Wait for Kafka ───────────────────────────────────────────────────────────
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

# ── Topic creation helper ────────────────────────────────────────────────────
# Usage: create_topic <topic> <partitions> <rf> [min_isr] [retention_ms]
create_topic() {
    local TOPIC="$1"
    local PARTITIONS="${2:-3}"
    local RF="${3:-${REPLICATION_FACTOR}}"
    local MIN_ISR="${4:-}"
    local RETENTION_MS="${5:-}"

    if kafka_cmd kafka-topics.sh --bootstrap-server "${KAFKA_BOOTSTRAP}" \
        --describe --topic "${TOPIC}" > /dev/null 2>&1; then
        ok "EXISTS:  ${TOPIC}"
        EXISTED=$(( EXISTED + 1 ))
        return
    fi

    local CREATE_ARGS=(
        --bootstrap-server "${KAFKA_BOOTSTRAP}"
        --create --if-not-exists
        --topic "${TOPIC}"
        --partitions "${PARTITIONS}"
        --replication-factor "${RF}"
    )

    [[ -n "${MIN_ISR}" ]]     && CREATE_ARGS+=(--config "min.insync.replicas=${MIN_ISR}")
    [[ -n "${RETENTION_MS}" ]] && CREATE_ARGS+=(--config "retention.ms=${RETENTION_MS}")

    if kafka_cmd kafka-topics.sh "${CREATE_ARGS[@]}" > /dev/null 2>&1; then
        ok "CREATED: ${TOPIC} (p=${PARTITIONS}, rf=${RF}${MIN_ISR:+, min.isr=${MIN_ISR}}${RETENTION_MS:+, ret=${RETENTION_MS}ms})"
        CREATED=$(( CREATED + 1 ))
    else
        err "FAILED:  ${TOPIC}"
        FAILED=$(( FAILED + 1 ))
    fi
}

# Retention constants (milliseconds)
RETENTION_7D=604800000
RETENTION_14D=1209600000
RETENTION_30D=2592000000
RETENTION_90D=7776000000
RETENTION_1Y=31536000000

# ============================================================================
# TRUST BUS — High-assurance trust channel
# RF 3 | min.isr 2 | acks=all enforced by producers
# ============================================================================
section "Trust Bus (trust.*)"

# Audit chain (long retention — compliance requirement)
create_topic "trust.audit.chain"              3 "${REPLICATION_FACTOR}" 2 "${RETENTION_1Y}"
create_topic "trust.access.log"               3 "${REPLICATION_FACTOR}" 2 "${RETENTION_1Y}"
create_topic "trust.policy.change"            3 "${REPLICATION_FACTOR}" 2 "${RETENTION_1Y}"
create_topic "trust.cpid.mapping"             3 "${REPLICATION_FACTOR}" 2 "${RETENTION_1Y}"
create_topic "trust.consent.update"           6 "${REPLICATION_FACTOR}" 2 "${RETENTION_1Y}"
create_topic "trust.threat.intel"             3 "${REPLICATION_FACTOR}" 2 "${RETENTION_1Y}"

# Revocation channels (P1-2) — 30-day retention, high throughput
create_topic "trust.revocation.consent"       6 "${REPLICATION_FACTOR}" 2 "${RETENTION_30D}"
create_topic "trust.revocation.privilege"     6 "${REPLICATION_FACTOR}" 2 "${RETENTION_30D}"
create_topic "trust.revocation.identity"      6 "${REPLICATION_FACTOR}" 2 "${RETENTION_30D}"

# Federation events (P1-1/P1-2)
create_topic "trust.federation.pod_registered" 3 "${REPLICATION_FACTOR}" 2 "${RETENTION_30D}"
create_topic "trust.federation.merge"          3 "${REPLICATION_FACTOR}" 2 "${RETENTION_30D}"

# Decision evidence (P1-3) — high throughput, 30d
create_topic "trust.decision_evidence"        12 "${REPLICATION_FACTOR}" 2 "${RETENTION_30D}"

# Control channel (ControlChannelPublisher in tshepo-authz-service)
create_topic "impilo.control.revocation.v1"          3 "${REPLICATION_FACTOR}" 2 "${RETENTION_30D}"
create_topic "impilo.control.privilege_revocation.v1" 3 "${REPLICATION_FACTOR}" 2 "${RETENTION_30D}"

# ============================================================================
# KERNEL BUS — Registry & infrastructure events
# RF 3 | min.isr 2 | retention 14d
# ============================================================================
section "Kernel Bus (kernel.*) — VITO Client Registry"

create_topic "kernel.vito.identity"           6 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.vito.alias"              3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.vito.cards"              3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.vito.wallet"             3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.vito.issuance"           3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.vito.dedup"              3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.vito.offline"            3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.vito.pickup"             3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.vito.print"              3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.vito.snapshots"          3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.vito.events"             3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"

section "Kernel Bus (kernel.*) — TUSO Facility Registry"

create_topic "kernel.tuso.facility"           6 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.tuso.service.area"       3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.tuso.resource"           3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.tuso.snapshots"          3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.tuso.events"             3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"

section "Kernel Bus (kernel.*) — VARAPI Provider Registry"

create_topic "kernel.varapi.provider"         6 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.varapi.council"          3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.varapi.credential"       3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.varapi.snapshots"        3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.varapi.events"           3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"

section "Kernel Bus (kernel.*) — MSIKA Product/Service Catalog"

create_topic "kernel.msika.catalog.tariff"    3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.msika.catalog.published" 3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.msika.item.changed"      3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.msika.mapping.approved"  3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.msika.snapshots"         3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.msika.events"            3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"

section "Kernel Bus (kernel.*) — ZIBO Terminology Service"

create_topic "kernel.zibo.artifact.lifecycle" 3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.zibo.pack.lifecycle"     3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.zibo.pack.assigned"      3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.zibo.validation.failed"  3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.zibo.snapshots"          3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.zibo.events"             3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"

section "Kernel Bus (kernel.*) — MUSHEX Finance/Claims"

create_topic "kernel.mushex.payment.intent.created"      6 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.mushex.payment.status.changed"      6 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.mushex.payment.refund.changed"      3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.mushex.payment.settlement.released" 3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.mushex.payment.fraud.flagged"       3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.mushex.payment.remittance.issued"   3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.mushex.payment.remittance.claimed"  3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.mushex.claim.submitted"             6 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.mushex.claim.adjudicated"           6 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.mushex.claim.paid"                  6 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.mushex.claim.disputed"              3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.mushex.events"                      3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"

section "Kernel Bus (kernel.*) — UBOMI CRVS Birth/Death"

create_topic "kernel.ubomi.birth"             6 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.ubomi.death"             6 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.ubomi.events"            3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"

section "Kernel Bus (kernel.*) — COSTA Costing/Billing"

create_topic "kernel.costa.bill.draft.created"     3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.costa.bill.finalized"          3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.costa.bill.voided"             3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.costa.invoice.issued"          3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.costa.payment.intent.created"  3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.costa.payment.status.changed"  3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.costa.claim.pack.created"      3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.costa.estimate.created"        3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.costa.ruleset.published"       3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.costa.events"                  3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"

section "Kernel Bus (kernel.*) — Inventory/Supply Chain"

create_topic "kernel.inventory.ledger.event.created" 6 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.inventory.onhand.updated"        6 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.inventory.stockout.risk"         3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.inventory.count.completed"       3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.inventory.requisition.created"   3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.inventory.requisition.fulfilled" 3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.inventory.reconcile.resolved"    3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"
create_topic "kernel.inventory.events"                3 "${REPLICATION_FACTOR}" 2 "${RETENTION_14D}"

# ============================================================================
# CLINICAL BUS — Patient care events
# RF 3 | min.isr 2 | retention 7d
# ============================================================================
section "Clinical Bus (clinical.*) — PCT Care Tracking"

create_topic "clinical.pct.journey.state_changed"  6 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pct.encounter.started"      6 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pct.encounter.completed"    6 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pct.triage.recorded"        6 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pct.queue.item.updated"     3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pct.admission.updated"      3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pct.discharge.started"      3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pct.discharge.completed"    3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pct.death.recorded"         3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pct.death.completed"        3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pct.task.updated"           3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pct.transfer.updated"       3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pct.events"                 3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"

section "Clinical Bus (clinical.*) — OROS Orders & Results"

create_topic "clinical.oros.order.placed"          6 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.oros.order.status_changed"  6 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.oros.order.routed"          3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.oros.order.cancelled"       3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.oros.workstep.changed"      3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.oros.result.available"      6 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.oros.result.critical"       6 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.oros.ack.received"          3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.oros.ack.escalation"        3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.oros.sla.breached"          3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.oros.reconcile.updated"     3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.oros.events"                3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"

section "Clinical Bus (clinical.*) — Pharmacy Dispensing"

create_topic "clinical.pharmacy.dispense.accepted"        6 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pharmacy.dispense.partial"         3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pharmacy.dispense.complete"        6 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pharmacy.dispense.backordered"     3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pharmacy.dispense.cancelled"       3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pharmacy.stock.movement.recorded"  3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pharmacy.reversal.completed"       3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pharmacy.pickup.claimed"           3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pharmacy.mushex.charge"            3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pharmacy.reconcile.updated"        3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.pharmacy.events"                   3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"

section "Clinical Bus (clinical.*) — Inpatient Bed Management"

create_topic "clinical.inpatient.admission.created"       6 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.inpatient.admission.transferred"   3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.inpatient.admission.discharged"    3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.inpatient.admission.death_discharge" 3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.inpatient.bed.assigned"            3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"
create_topic "clinical.inpatient.events"                  3 "${REPLICATION_FACTOR}" 2 "${RETENTION_7D}"

# ============================================================================
# TELEMETRY BUS — IoT readings and platform metrics
# RF 2 | min.isr 1 | retention 30d
# ============================================================================
section "Telemetry Bus (telemetry.*)"

create_topic "telemetry.iot.device.raw"           6 2 1 "${RETENTION_30D}"
create_topic "telemetry.iot.reading.ingested"     6 2 1 "${RETENTION_30D}"
create_topic "telemetry.iot.alert.triggered"      3 2 1 "${RETENTION_30D}"
create_topic "telemetry.platform.heartbeat"       3 2 1 "${RETENTION_30D}"
create_topic "telemetry.platform.metric"          6 2 1 "${RETENTION_30D}"
create_topic "telemetry.platform.health"          3 2 1 "${RETENTION_30D}"
create_topic "telemetry.platform.sla.breach"      3 2 1 "${RETENTION_30D}"

# ============================================================================
# ANALYTICS BUS — Gold-layer materialized facts
# RF 2 | min.isr 1 | retention 90d
# ============================================================================
section "Analytics Bus (analytics.*)"

create_topic "analytics.clinical.encounter.gold"   3 2 1 "${RETENTION_90D}"
create_topic "analytics.clinical.triage.gold"      3 2 1 "${RETENTION_90D}"
create_topic "analytics.clinical.order.gold"       3 2 1 "${RETENTION_90D}"
create_topic "analytics.finance.claim.gold"        3 2 1 "${RETENTION_90D}"
create_topic "analytics.finance.payment.gold"      3 2 1 "${RETENTION_90D}"
create_topic "analytics.registry.patient.gold"     3 2 1 "${RETENTION_90D}"
create_topic "analytics.registry.facility.gold"    3 2 1 "${RETENTION_90D}"
create_topic "analytics.registry.provider.gold"    3 2 1 "${RETENTION_90D}"
create_topic "analytics.surveillance.signal.gold"  3 2 1 "${RETENTION_90D}"
create_topic "analytics.pharmacy.dispense.gold"    3 2 1 "${RETENTION_90D}"

# ============================================================================
# PLATFORM / INTERNAL — Cross-cutting infrastructure topics
# RF 1 dev / RF 3 prod | retention 30d
# ============================================================================
section "Platform Bus (platform.*)"

create_topic "platform.audit.events"               3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "platform.identity.events"            3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "platform.consent.events"             3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "platform.keys.events"                3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "platform.offline.events"             3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "platform.outbox.relay"               6 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "platform.dlq"                        3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"

# ============================================================================
# LEGACY / DUAL-EMIT COMPATIBILITY TOPICS
# Created to support the DualEmitPolicy transition period (P4-2).
# These will be decommissioned after all publishers migrate to namespaced names.
# ============================================================================
section "Legacy Topics (backward compat — P4-2 DualEmitPolicy rollout)"

# VITO legacy topics (impilo.vito.* and vito.*)
create_topic "vito.identity"             3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "vito.alias"                3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "vito.cards"                3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "vito.wallet"               3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "vito.issuance"             3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "vito.dedup"                3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "vito.offline"              3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "vito.pickup"               3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "vito.print"                3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "vito.events"               3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "impilo.vito.identity"      3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "impilo.vito.alias"         3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "impilo.vito.cards"         3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "impilo.vito.wallet"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "impilo.vito.issuance"      3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "impilo.vito.dedup"         3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "impilo.vito.offline"       3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "impilo.vito.pickup"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "impilo.vito.print"         3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "impilo.vito.snapshots"     3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "impilo.vito.events"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"

# TUSO legacy topics
create_topic "tuso.facility"             3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "tuso.service.area"         3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "tuso.resource"             3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "tuso.events"               3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "tshepo.audit.events"       3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"

# VARAPI legacy topics
create_topic "varapi.provider"           3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "varapi.council"            3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "varapi.credential"         3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "varapi.events"             3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"

# MSIKA legacy topics
create_topic "msika.core.catalog.published"  3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.core.item.changed"       3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.core.mapping.approved"   3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.core.events"             3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.order.created"      3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.order.validated"    3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.order.priced"       3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.order.paid"         3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.order.routed"       3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.order.accepted"     3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.order.completed"    3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.order.cancelled"    3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.order.failed"       3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.pickup.issued"      3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.pickup.claimed"     3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.vendor.applied"     3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.vendor.approved"    3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.vendor.suspended"   3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.refund.requested"   3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.refund.completed"   3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "msika.flow.events"             3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"

# ZIBO legacy topics
create_topic "zibo.artifact.lifecycle"   3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "zibo.pack.lifecycle"       3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "zibo.pack.assigned"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "zibo.validation.failed"    3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "zibo.events"               3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"

# PCT legacy topics (will move to clinical.pct.*)
create_topic "pct.journey.state_changed"  3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pct.queue.item.updated"     3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pct.encounter.started"      3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pct.encounter.completed"    3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pct.admission.updated"      3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pct.discharge.started"      3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pct.discharge.completed"    3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pct.death.recorded"         3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pct.death.completed"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pct.task.updated"           3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pct.triage.recorded"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pct.transfer.updated"       3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pct.events"                 3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"

# OROS legacy topics (will move to clinical.oros.*)
create_topic "oros.order.placed"          3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "oros.order.status_changed"  3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "oros.order.routed"          3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "oros.order.cancelled"       3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "oros.workstep.changed"      3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "oros.result.available"      3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "oros.result.critical"       3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "oros.ack.received"          3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "oros.ack.escalation"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "oros.sla.breached"          3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "oros.reconcile.updated"     3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "oros.events"                3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"

# Pharmacy legacy topics (will move to clinical.pharmacy.*)
create_topic "pharmacy.dispense.accepted"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pharmacy.dispense.partial"         3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pharmacy.dispense.complete"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pharmacy.dispense.backordered"     3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pharmacy.dispense.cancelled"       3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pharmacy.stock.movement.recorded"  3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pharmacy.reversal.completed"       3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pharmacy.pickup.claimed"           3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pharmacy.mushex.charge"            3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pharmacy.reconcile.updated"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "pharmacy.events"                   3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"

# COSTA legacy topics (will move to kernel.costa.*)
create_topic "costa.bill.draft.created"      3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "costa.bill.approval.requested" 3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "costa.bill.approved"           3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "costa.bill.finalized"          3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "costa.bill.voided"             3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "costa.invoice.issued"          3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "costa.payment.intent.created"  3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "costa.payment.status_changed"  3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "costa.refund.issued"           3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "costa.claim.pack.created"      3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "costa.estimate.created"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "costa.ruleset.published"       3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "costa.events"                  3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"

# Inventory legacy topics (will move to kernel.inventory.*)
create_topic "inventory.ledger.event.created" 3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "inventory.onhand.updated"       3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "inventory.stockout.risk"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "inventory.count.completed"      3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "inventory.requisition.created"  3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "inventory.requisition.fulfilled" 3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "inventory.mushex.charge"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "inventory.reconcile.resolved"   3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "inventory.events"               3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"

# IoT ingestion legacy topic
create_topic "impilo.iot.telemetry.device.raw"       6 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.iot.telemetry.reading.ingested" 6 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"

# Miscellaneous platform topics
create_topic "docstore.documents"                3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "docstore.events"                   3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "butano.resource.created"           3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "butano.resource.updated"           3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "butano.reconcile.completed"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "butano.events"                     3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "creds.credential.issued"           3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "creds.credential.revoked"          3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "creds.credential.verified"         3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "creds.events"                      3 "${REPLICATION_FACTOR}" "" "${RETENTION_14D}"
create_topic "share.link"                        3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "share.events"                      3 "${REPLICATION_FACTOR}" "" "${RETENTION_7D}"
create_topic "impilo.campaigns.created.v1"       3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.campaigns.enrolled.v1"      3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.campaigns.dispatched.v1"    3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.dags.policy.created.v1"     3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.dags.access.requested.v1"   3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.dags.access.approved.v1"    3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.dags.access.denied.v1"      3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.pipeline.event.ingested.v1" 6 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.pipeline.watermark.updated.v1" 3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.pipeline.events"            3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.ia.attestation.recorded.v1" 3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.ia.risk.assessed.v1"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.ndr.dataset.created.v1"     3 "${REPLICATION_FACTOR}" "" "${RETENTION_90D}"
create_topic "impilo.ndr.dataset.versioned.v1"   3 "${REPLICATION_FACTOR}" "" "${RETENTION_90D}"
create_topic "impilo.obs.dashboard.created.v1"   3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.obs.alert-rule.created.v1"  3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.reporting.report.created.v1"  3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.reporting.report.ran.v1"      3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.reporting.schedule.created.v1" 3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.secharden.pack.created.v1"  3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.secharden.scan.completed.v1" 3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.surv.signal.created.v1"     3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.surv.signal.hit.v1"         3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"
create_topic "impilo.surv.case.opened.v1"        3 "${REPLICATION_FACTOR}" "" "${RETENTION_30D}"

# ============================================================================
# Summary
# ============================================================================
TOTAL=$(( CREATED + EXISTED + FAILED ))

echo ""
echo "════════════════════════════════════════════════"
echo "  Kafka Topic Initialization Summary"
echo "════════════════════════════════════════════════"
printf "  ${GREEN}Created${NC}:  %d\n" "${CREATED}"
printf "  ${CYAN}Existed${NC}:  %d\n" "${EXISTED}"
printf "  ${RED}Failed${NC}:   %d\n" "${FAILED}"
echo "  ─────────────────────────────────────"
printf "  Total:    %d\n" "${TOTAL}"
echo "════════════════════════════════════════════════"

if (( FAILED > 0 )); then
    err "Some topics failed to create — check Kafka connectivity and permissions"
    exit 1
fi

ok "All Kafka topics initialized successfully"
