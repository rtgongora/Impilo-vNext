#!/usr/bin/env bash
# =============================================================================
# Impilo vNext — Collect Runtime Evidence
# =============================================================================
#
# Captures a snapshot of the running platform state for acceptance/audit:
#   - Container status and health
#   - Service health endpoint responses
#   - Keycloak realm state
#   - Kafka topic list
#   - OPA policy summary
#   - Smoke test results
#
# Output: docs/evidence/runtime-evidence-<timestamp>.txt
#
# Usage:
#   ./scripts/runtime/collect-runtime-evidence.sh
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

TIMESTAMP=$(date +%Y%m%d-%H%M%S)
EVIDENCE_DIR="${PROJECT_ROOT}/docs/evidence"
EVIDENCE_FILE="${EVIDENCE_DIR}/runtime-evidence-${TIMESTAMP}.txt"

mkdir -p "${EVIDENCE_DIR}"

# Write to both file and stdout
tee_output() {
    tee -a "${EVIDENCE_FILE}"
}

{
    echo "============================================================"
    echo "  Impilo vNext — Runtime Evidence Pack"
    echo "  Collected: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
    echo "  Host: $(hostname)"
    echo "============================================================"
    echo ""

    # ── Docker status ──────────────────────────────────────────────────
    echo "=== Docker Container Status ==="
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null | grep -i "impilo\|postgres\|redis\|kafka\|keycloak\|hapi\|envoy\|opa\|minio\|orthanc\|experience\|prometheus\|grafana\|jaeger\|otel" || echo "(no impilo containers running)"
    echo ""

    # ── Service health checks ──────────────────────────────────────────
    echo "=== Service Health Checks ==="
    SERVICES=(
        "PostgreSQL:TCP:localhost:5432"
        "Redis:TCP:localhost:6379"
        "Kafka:TCP:localhost:9092"
        "Keycloak:http://localhost:8080/health/ready"
        "HAPI-FHIR:http://localhost:8090/fhir/metadata"
        "OPA:http://localhost:8181/health"
        "Envoy:http://localhost:9901/ready"
        "TSHEPO:http://localhost:8081/actuator/health"
        "VITO:http://localhost:8082/actuator/health"
        "VARAPI:http://localhost:8083/actuator/health"
        "TUSO:http://localhost:8084/actuator/health"
        "ZIBO:http://localhost:8085/actuator/health"
        "PCT:http://localhost:8088/actuator/health"
        "OROS:http://localhost:8089/actuator/health"
        "Experience-BFF:http://localhost:8160/actuator/health"
        "Experience-UI:http://localhost:3020"
    )

    for svc in "${SERVICES[@]}"; do
        IFS=':' read -r name rest <<< "${svc}"
        if [[ "${rest}" == "TCP"* ]]; then
            IFS=':' read -r _ _ host port <<< "${svc}"
            if (echo > /dev/tcp/"${host}"/"${port}") 2>/dev/null; then
                echo "  HEALTHY: ${name} (port ${port})"
            else
                echo "  DOWN:    ${name} (port ${port})"
            fi
        else
            url="${svc#*:}"
            code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 3 "${url}" 2>/dev/null || echo "000")
            if [[ "${code}" == "200" ]]; then
                echo "  HEALTHY: ${name} (HTTP ${code})"
            else
                echo "  DOWN:    ${name} (HTTP ${code})"
            fi
        fi
    done
    echo ""

    # ── Keycloak realm ─────────────────────────────────────────────────
    echo "=== Keycloak Realm State ==="
    KC_WELL_KNOWN=$(curl -s "http://localhost:8080/realms/impilo/.well-known/openid-configuration" 2>/dev/null || echo "{}")
    if echo "${KC_WELL_KNOWN}" | grep -q "issuer"; then
        echo "  Realm 'impilo': ACTIVE"
        echo "  Issuer: $(echo "${KC_WELL_KNOWN}" | grep -o '"issuer":"[^"]*"' | cut -d'"' -f4)"
    else
        echo "  Realm 'impilo': NOT FOUND or Keycloak not running"
    fi
    echo ""

    # ── Kafka topics ───────────────────────────────────────────────────
    echo "=== Kafka Topics ==="
    docker exec impilo-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null || echo "(could not list topics)"
    echo ""

    # ── OPA policies ───────────────────────────────────────────────────
    echo "=== OPA Policies ==="
    curl -s "http://localhost:8181/v1/policies" 2>/dev/null | grep -o '"id":"[^"]*"' | cut -d'"' -f4 || echo "(could not query OPA)"
    echo ""

    # ── Git state ──────────────────────────────────────────────────────
    echo "=== Git State ==="
    echo "  Branch: $(git -C "${PROJECT_ROOT}" branch --show-current 2>/dev/null || echo 'unknown')"
    echo "  Commit: $(git -C "${PROJECT_ROOT}" log -1 --oneline 2>/dev/null || echo 'unknown')"
    echo ""

    echo "============================================================"
    echo "  Evidence collection complete"
    echo "  Output: ${EVIDENCE_FILE}"
    echo "============================================================"

} | tee_output

echo ""
echo "Evidence saved to: ${EVIDENCE_FILE}"
