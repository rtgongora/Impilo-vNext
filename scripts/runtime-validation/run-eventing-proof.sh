#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEST_DIR="$ROOT_DIR/test/integration"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; BOLD='\033[1m'; NC='\033[0m'

# Source test helpers
if [[ -f "$TEST_DIR/_common.sh" ]]; then
  source "$TEST_DIR/_common.sh"
else
  echo -e "${RED}[ERROR] test/integration/_common.sh not found${NC}"
  exit 1
fi

echo -e "${BOLD}=== Eventing Proof ===${NC}"

# Step 1: Get auth token
echo ""
echo -e "${BOLD}--- Step 1: Obtain auth token ---${NC}"
TOKEN=$(get_token "admin" "admin")
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo -e "${RED}[FAIL] Could not obtain auth token${NC}"
  exit 1
fi
echo -e "${GREEN}[OK]${NC} Auth token obtained"

# Step 2: Create a patient record in VITO
echo ""
echo -e "${BOLD}--- Step 2: POST patient to VITO ---${NC}"
IDEM_KEY="idem-eventing-$(date +%s%N)"
REQUEST_ID="req-eventing-$(date +%s%N)"
CORRELATION_ID="corr-eventing-$(date +%s%N)"

RESPONSE=$(curl -sf -w "\n%{http_code}" -X POST "${VITO_URL}/api/v1/patients" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: ${REQUEST_ID}" \
  -H "X-Correlation-ID: ${CORRELATION_ID}" \
  -H "Idempotency-Key: ${IDEM_KEY}" \
  -d '{
    "givenName": "Eventing",
    "familyName": "TestPatient",
    "dateOfBirth": "1990-01-15",
    "gender": "male",
    "nationalId": "EV-'"$(date +%s)"'"
  }' 2>/dev/null) || true

HTTP_STATUS=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [[ "$HTTP_STATUS" -ge 200 && "$HTTP_STATUS" -lt 300 ]]; then
  echo -e "${GREEN}[PASS]${NC} Patient created (HTTP $HTTP_STATUS)"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo -e "${RED}[FAIL]${NC} Patient creation failed (HTTP $HTTP_STATUS)"
  echo "  Body: $BODY"
  FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# Step 3: Query event_outbox
echo ""
echo -e "${BOLD}--- Step 3: Query event_outbox ---${NC}"
sleep 2  # Allow time for outbox write

PG_CONTAINER=$(docker ps -qf name=postgres 2>/dev/null || true)
if [[ -z "$PG_CONTAINER" ]]; then
  echo -e "${RED}[FAIL]${NC} PostgreSQL container not found"
  FAIL_COUNT=$((FAIL_COUNT + 1))
else
  OUTBOX_RESULT=$(docker exec "$PG_CONTAINER" psql -U impilo -d vito -t -c \
    "SELECT event_id, event_type, tenant_id, pod_id, correlation_id, schema_version FROM event_outbox ORDER BY created_at DESC LIMIT 5;" 2>/dev/null) || true

  if [[ -n "$OUTBOX_RESULT" ]]; then
    echo -e "${GREEN}[OK]${NC} event_outbox query returned results:"
    echo "$OUTBOX_RESULT"

    # Verify event_type prefix
    if echo "$OUTBOX_RESULT" | grep -q "impilo\."; then
      echo -e "${GREEN}[PASS]${NC} event_type starts with 'impilo.'"
      PASS_COUNT=$((PASS_COUNT + 1))
    else
      echo -e "${RED}[FAIL]${NC} event_type does not start with 'impilo.'"
      FAIL_COUNT=$((FAIL_COUNT + 1))
    fi

    # Verify schema_version >= 1
    SCHEMA_VER=$(echo "$OUTBOX_RESULT" | head -1 | awk -F'|' '{print $NF}' | tr -d ' ')
    if [[ -n "$SCHEMA_VER" && "$SCHEMA_VER" -ge 1 ]] 2>/dev/null; then
      echo -e "${GREEN}[PASS]${NC} schema_version >= 1 ($SCHEMA_VER)"
      PASS_COUNT=$((PASS_COUNT + 1))
    else
      echo -e "${RED}[FAIL]${NC} schema_version not >= 1 (got: '$SCHEMA_VER')"
      FAIL_COUNT=$((FAIL_COUNT + 1))
    fi

    # Verify tenant_id
    if echo "$OUTBOX_RESULT" | grep -q "moh-zw"; then
      echo -e "${GREEN}[PASS]${NC} tenant_id = moh-zw"
      PASS_COUNT=$((PASS_COUNT + 1))
    else
      echo -e "${RED}[FAIL]${NC} tenant_id != moh-zw"
      FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
  else
    echo -e "${RED}[FAIL]${NC} event_outbox returned no results"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
fi

# Step 4: Check Kafka topics
echo ""
echo -e "${BOLD}--- Step 4: Check Kafka topics ---${NC}"
KAFKA_CONTAINER=$(docker ps -qf name=kafka 2>/dev/null || true)
if [[ -z "$KAFKA_CONTAINER" ]]; then
  echo -e "${RED}[FAIL]${NC} Kafka container not found"
  FAIL_COUNT=$((FAIL_COUNT + 1))
else
  TOPICS=$(docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-topics.sh \
    --list --bootstrap-server localhost:9092 2>/dev/null) || true

  if [[ -n "$TOPICS" ]]; then
    echo -e "${GREEN}[PASS]${NC} Kafka topics listed:"
    echo "$TOPICS" | sed 's/^/  /'
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo -e "${RED}[FAIL]${NC} Could not list Kafka topics"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
fi

# Summary
print_summary
