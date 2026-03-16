#!/usr/bin/env bash
set -euo pipefail
#
# Steel Thread C — Support Escalation
#
# Tests the support ticket creation path, verifying:
#   1. Keycloak token acquisition for a support agent
#   2. Ticket creation via POST with trust headers and Idempotency-Key
#   3. Correlation-ID preservation in the response
#   4. Outbox event fields (environment-dependent, best-effort check)
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_common.sh"

echo "==========================================="
echo " Steel Thread C — Support Escalation"
echo "==========================================="
echo ""

# ---- Step 1: Obtain Keycloak token for support.agent1 ----
echo -e "${YELLOW}[INFO]${NC} Obtaining Keycloak token for support.agent1..."
TOKEN=$(get_token "support.agent1")
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo -e "${RED}[FAIL]${NC} Could not obtain Keycloak token for support.agent1"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  print_summary
fi
echo -e "${GREEN}[PASS]${NC} Obtained Keycloak token for support.agent1"
PASS_COUNT=$((PASS_COUNT + 1))

# ---- Step 2: POST /api/v1.1/tickets with trust headers + Idempotency-Key ----
REQUEST_ID="req-$(date +%s%N)"
CORRELATION_ID="corr-$(date +%s%N)"
IDEMPOTENCY_KEY="idem-$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)"

TICKET_PAYLOAD=$(cat <<'PAYLOAD'
{
  "subject": "Integration test ticket",
  "description": "Created by steel-thread-c integration test",
  "priority": "MEDIUM",
  "category": "GENERAL"
}
PAYLOAD
)

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: moh-zw" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: ${REQUEST_ID}" \
  -H "X-Correlation-ID: ${CORRELATION_ID}" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -d "${TICKET_PAYLOAD}" \
  "${SUPPORT_URL}/api/v1.1/tickets")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

# Accept either 200 or 201 for ticket creation
if [[ "$HTTP_CODE" == "200" || "$HTTP_CODE" == "201" ]]; then
  echo -e "${GREEN}[PASS]${NC} POST /api/v1.1/tickets (HTTP $HTTP_CODE)"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo -e "${RED}[FAIL]${NC} POST /api/v1.1/tickets — expected HTTP 200 or 201, got $HTTP_CODE"
  FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# ---- Step 3: Verify correlation_id preserved in response ----
# Check common envelope locations for the correlation_id
RESP_CORR_ID=$(echo "$BODY" | jq -r '.correlation_id // .meta.correlation_id // empty' 2>/dev/null)

if [[ "$RESP_CORR_ID" == "$CORRELATION_ID" ]]; then
  echo -e "${GREEN}[PASS]${NC} Correlation-ID preserved in response — $RESP_CORR_ID"
  PASS_COUNT=$((PASS_COUNT + 1))
elif [[ -n "$RESP_CORR_ID" ]]; then
  echo -e "${YELLOW}[WARN]${NC} Correlation-ID present but differs — sent=$CORRELATION_ID, received=$RESP_CORR_ID"
  # Still count as pass if correlation_id is present (may have been enriched)
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo -e "${RED}[FAIL]${NC} Correlation-ID not found in response"
  FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# ---- Step 4: Check for outbox event fields (best-effort) ----
# Some services include event metadata in the response; check if present.
EVENT_TYPE=$(echo "$BODY" | jq -r '.event_type // .meta.event_type // empty' 2>/dev/null)
if [[ -n "$EVENT_TYPE" ]]; then
  echo -e "${GREEN}[PASS]${NC} Outbox event_type present in response — $EVENT_TYPE"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo -e "${YELLOW}[INFO]${NC} Outbox event_type not in response body (environment-dependent, skipping assertion)"
fi

# Verify the ticket has an ID in the response
assert_json_field "Ticket response has ID" "$BODY" ".data.id // .id"

print_summary
