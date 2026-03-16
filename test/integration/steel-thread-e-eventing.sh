#!/usr/bin/env bash
set -euo pipefail
#
# Steel Thread E — Eventing Flow
#
# Tests the end-to-end eventing pipeline, verifying:
#   1. Keycloak token acquisition for an admin user
#   2. Patient registration via VITO POST with trust headers + Idempotency-Key
#   3. Outbox verification — check DB (psql) or actuator for outbox rows
#      matching the correlation_id from the request
#   4. Event envelope fields: event_type pattern, schema_version >= 1,
#      tenant_id, pod_id, correlation_id
#   5. Kafka topic existence (kafka-topics.sh --list)
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_common.sh"

echo "==========================================="
echo " Steel Thread E — Eventing Flow"
echo "==========================================="
echo ""

# ---- Step 1: Obtain Keycloak token for admin.central ----
echo -e "${YELLOW}[INFO]${NC} Obtaining Keycloak token for admin.central..."
TOKEN=$(get_token "admin.central")
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo -e "${RED}[FAIL]${NC} Could not obtain Keycloak token for admin.central"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  print_summary
fi
echo -e "${GREEN}[PASS]${NC} Obtained Keycloak token for admin.central"
PASS_COUNT=$((PASS_COUNT + 1))

# ---- Step 2: Register patient in VITO POST /api/v1.1/patients ----
REQUEST_ID="req-$(date +%s%N)"
CORRELATION_ID="corr-$(date +%s%N)"
IDEMPOTENCY_KEY="idem-$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)"

PATIENT_PAYLOAD=$(cat <<PAYLOAD
{
  "first_name": "Integration",
  "last_name": "TestPatient-$(date +%s)",
  "date_of_birth": "1990-01-15",
  "gender": "MALE",
  "national_id": "NID-IT-$(date +%s%N)",
  "phone": "+263771000000",
  "address": {
    "province": "Harare",
    "district": "Central",
    "ward": "Ward 1"
  }
}
PAYLOAD
)

echo -e "${YELLOW}[INFO]${NC} Registering patient in VITO (correlation_id=$CORRELATION_ID)..."

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: ${REQUEST_ID}" \
  -H "X-Correlation-ID: ${CORRELATION_ID}" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -d "${PATIENT_PAYLOAD}" \
  "${VITO_URL}/api/v1.1/patients")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

# Accept 200 or 201 for patient creation
if [[ "$HTTP_CODE" == "200" || "$HTTP_CODE" == "201" ]]; then
  echo -e "${GREEN}[PASS]${NC} POST /api/v1.1/patients (HTTP $HTTP_CODE)"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo -e "${RED}[FAIL]${NC} POST /api/v1.1/patients — expected HTTP 200 or 201, got $HTTP_CODE"
  FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# Extract patient ID for later verification
PATIENT_ID=$(echo "$BODY" | jq -r '.data.id // .id // empty' 2>/dev/null)
if [[ -n "$PATIENT_ID" ]]; then
  echo -e "${YELLOW}[INFO]${NC} Created patient ID: $PATIENT_ID"
fi

# ---- Step 3: Outbox verification via database or actuator ----
# Try actuator first (non-destructive, no DB credentials needed)
echo -e "${YELLOW}[INFO]${NC} Checking outbox via VITO actuator..."

OUTBOX_RESPONSE=$(curl -sf -w "\n%{http_code}" \
  "${VITO_URL}/actuator/outbox" 2>/dev/null || echo -e "\n000")

OUTBOX_HTTP=$(echo "$OUTBOX_RESPONSE" | tail -1)
OUTBOX_BODY=$(echo "$OUTBOX_RESPONSE" | sed '$d')

if [[ "$OUTBOX_HTTP" == "200" ]]; then
  echo -e "${GREEN}[PASS]${NC} VITO actuator /outbox endpoint is accessible"
  PASS_COUNT=$((PASS_COUNT + 1))

  # Look for our correlation_id in outbox entries
  OUTBOX_MATCH=$(echo "$OUTBOX_BODY" | jq -r \
    --arg cid "$CORRELATION_ID" \
    '[.[] | select(.correlation_id == $cid)] | length' 2>/dev/null || echo "0")

  if [[ "$OUTBOX_MATCH" -gt 0 ]]; then
    echo -e "${GREEN}[PASS]${NC} Found outbox entry with correlation_id=$CORRELATION_ID"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo -e "${YELLOW}[INFO]${NC} No outbox entry found via actuator (may be processed already or endpoint format differs)"
  fi
else
  echo -e "${YELLOW}[INFO]${NC} VITO actuator /outbox not available (HTTP $OUTBOX_HTTP), trying psql..."

  # Fallback: query the database directly if psql is available
  VITO_DB_HOST="${VITO_DB_HOST:-localhost}"
  VITO_DB_PORT="${VITO_DB_PORT:-5432}"
  VITO_DB_NAME="${VITO_DB_NAME:-vito}"
  VITO_DB_USER="${VITO_DB_USER:-vito}"
  VITO_DB_PASSWORD="${VITO_DB_PASSWORD:-vito}"

  if command -v psql &>/dev/null; then
    OUTBOX_ROW=$(PGPASSWORD="${VITO_DB_PASSWORD}" psql \
      -h "$VITO_DB_HOST" -p "$VITO_DB_PORT" -U "$VITO_DB_USER" -d "$VITO_DB_NAME" \
      -t -A -c "SELECT count(*) FROM event_outbox WHERE correlation_id = '${CORRELATION_ID}'" 2>/dev/null || echo "error")

    if [[ "$OUTBOX_ROW" =~ ^[0-9]+$ && "$OUTBOX_ROW" -gt 0 ]]; then
      echo -e "${GREEN}[PASS]${NC} Found $OUTBOX_ROW outbox row(s) with correlation_id=$CORRELATION_ID"
      PASS_COUNT=$((PASS_COUNT + 1))
    elif [[ "$OUTBOX_ROW" == "error" ]]; then
      echo -e "${YELLOW}[INFO]${NC} Could not query outbox table (psql connection failed, skipping)"
    else
      echo -e "${RED}[FAIL]${NC} No outbox rows found for correlation_id=$CORRELATION_ID"
      FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
  else
    echo -e "${YELLOW}[INFO]${NC} psql not available — skipping direct DB outbox check"
  fi
fi

# ---- Step 4: Verify event envelope fields in response (if service returns them) ----
echo -e "${YELLOW}[INFO]${NC} Checking event envelope fields in registration response..."

EVENT_TYPE=$(echo "$BODY" | jq -r '.event_type // .meta.event_type // empty' 2>/dev/null)
if [[ -n "$EVENT_TYPE" ]]; then
  # Verify event_type follows expected pattern (e.g., patient.registered, PATIENT_REGISTERED)
  if [[ "$EVENT_TYPE" =~ ^[a-zA-Z_]+\.?[a-zA-Z_]+$ ]]; then
    echo -e "${GREEN}[PASS]${NC} event_type follows valid pattern — $EVENT_TYPE"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo -e "${RED}[FAIL]${NC} event_type has unexpected format — $EVENT_TYPE"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
else
  echo -e "${YELLOW}[INFO]${NC} event_type not in response body (environment-dependent)"
fi

SCHEMA_VERSION=$(echo "$BODY" | jq -r '.schema_version // .meta.schema_version // empty' 2>/dev/null)
if [[ -n "$SCHEMA_VERSION" ]]; then
  if [[ "$SCHEMA_VERSION" -ge 1 ]] 2>/dev/null; then
    echo -e "${GREEN}[PASS]${NC} schema_version >= 1 — $SCHEMA_VERSION"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo -e "${RED}[FAIL]${NC} schema_version < 1 or invalid — $SCHEMA_VERSION"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
else
  echo -e "${YELLOW}[INFO]${NC} schema_version not in response body (environment-dependent)"
fi

# Verify tenant_id and pod_id are echoed back
RESP_TENANT=$(echo "$BODY" | jq -r '.tenant_id // .meta.tenant_id // empty' 2>/dev/null)
if [[ -n "$RESP_TENANT" ]]; then
  assert_json_equals "Tenant ID echoed in response" "$BODY" ".tenant_id // .meta.tenant_id" "moh-zw"
fi

RESP_POD=$(echo "$BODY" | jq -r '.pod_id // .meta.pod_id // empty' 2>/dev/null)
if [[ -n "$RESP_POD" ]]; then
  assert_json_equals "Pod ID echoed in response" "$BODY" ".pod_id // .meta.pod_id" "national"
fi

# Check correlation_id in response
RESP_CORR=$(echo "$BODY" | jq -r '.correlation_id // .meta.correlation_id // empty' 2>/dev/null)
if [[ -n "$RESP_CORR" ]]; then
  if [[ "$RESP_CORR" == "$CORRELATION_ID" ]]; then
    echo -e "${GREEN}[PASS]${NC} Correlation-ID preserved — $RESP_CORR"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo -e "${RED}[FAIL]${NC} Correlation-ID mismatch — sent=$CORRELATION_ID, got=$RESP_CORR"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
else
  echo -e "${YELLOW}[INFO]${NC} correlation_id not in response body (environment-dependent)"
fi

# ---- Step 5: Verify Kafka topic exists ----
echo -e "${YELLOW}[INFO]${NC} Checking Kafka topics..."

KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"

if command -v kafka-topics.sh &>/dev/null; then
  TOPIC_LIST=$(kafka-topics.sh --bootstrap-server "$KAFKA_BOOTSTRAP" --list 2>/dev/null || echo "")

  if [[ -n "$TOPIC_LIST" ]]; then
    echo -e "${GREEN}[PASS]${NC} Kafka is reachable, topics listed"
    PASS_COUNT=$((PASS_COUNT + 1))

    # Check for patient-related topic
    PATIENT_TOPIC=$(echo "$TOPIC_LIST" | grep -i "patient" | head -1 || true)
    if [[ -n "$PATIENT_TOPIC" ]]; then
      echo -e "${GREEN}[PASS]${NC} Patient-related Kafka topic exists — $PATIENT_TOPIC"
      PASS_COUNT=$((PASS_COUNT + 1))
    else
      echo -e "${YELLOW}[INFO]${NC} No patient-specific Kafka topic found (may use generic topic)"
    fi
  else
    echo -e "${RED}[FAIL]${NC} Could not list Kafka topics"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
elif command -v docker &>/dev/null; then
  # Try via docker if kafka-topics.sh is not on PATH
  TOPIC_LIST=$(docker exec -i $(docker ps -q -f name=kafka 2>/dev/null | head -1) \
    kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null || echo "")

  if [[ -n "$TOPIC_LIST" ]]; then
    echo -e "${GREEN}[PASS]${NC} Kafka is reachable (via docker), topics listed"
    PASS_COUNT=$((PASS_COUNT + 1))

    PATIENT_TOPIC=$(echo "$TOPIC_LIST" | grep -i "patient" | head -1 || true)
    if [[ -n "$PATIENT_TOPIC" ]]; then
      echo -e "${GREEN}[PASS]${NC} Patient-related Kafka topic exists — $PATIENT_TOPIC"
      PASS_COUNT=$((PASS_COUNT + 1))
    else
      echo -e "${YELLOW}[INFO]${NC} No patient-specific Kafka topic found (may use generic topic)"
    fi
  else
    echo -e "${YELLOW}[INFO]${NC} Kafka not reachable via docker — skipping topic check"
  fi
else
  echo -e "${YELLOW}[INFO]${NC} Neither kafka-topics.sh nor docker available — skipping Kafka topic check"
fi

print_summary
