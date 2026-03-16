#!/usr/bin/env bash
set -euo pipefail
#
# Steel Thread D — Messaging Flow
#
# Tests the notification/messaging path, verifying:
#   1. Keycloak token acquisition for a provider user
#   2. Notification creation via POST with trust headers + Idempotency-Key
#   3. Trust header enforcement (missing header → 400)
#   4. Error envelope format correctness
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/_common.sh"

echo "==========================================="
echo " Steel Thread D — Messaging Flow"
echo "==========================================="
echo ""

# ---- Step 1: Obtain Keycloak token for dr.mapfumo ----
echo -e "${YELLOW}[INFO]${NC} Obtaining Keycloak token for dr.mapfumo..."
TOKEN=$(get_token "dr.mapfumo")
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo -e "${RED}[FAIL]${NC} Could not obtain Keycloak token for dr.mapfumo"
  FAIL_COUNT=$((FAIL_COUNT + 1))
  print_summary
fi
echo -e "${GREEN}[PASS]${NC} Obtained Keycloak token for dr.mapfumo"
PASS_COUNT=$((PASS_COUNT + 1))

# ---- Step 2: POST /api/v1.1/notifications with trust headers + Idempotency-Key ----
REQUEST_ID="req-$(date +%s%N)"
CORRELATION_ID="corr-$(date +%s%N)"
IDEMPOTENCY_KEY="idem-$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)"

NOTIFICATION_PAYLOAD=$(cat <<'PAYLOAD'
{
  "recipient_id": "patient-001",
  "channel": "SMS",
  "template": "appointment_reminder",
  "parameters": {
    "date": "2026-03-20",
    "time": "10:00",
    "provider": "Dr. Mapfumo"
  }
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
  -d "${NOTIFICATION_PAYLOAD}" \
  "${NOTIFICATION_URL}/api/v1.1/notifications")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

# Accept 200, 201, or 202 (accepted for async processing)
if [[ "$HTTP_CODE" == "200" || "$HTTP_CODE" == "201" || "$HTTP_CODE" == "202" ]]; then
  echo -e "${GREEN}[PASS]${NC} POST /api/v1.1/notifications (HTTP $HTTP_CODE)"
  PASS_COUNT=$((PASS_COUNT + 1))
else
  echo -e "${RED}[FAIL]${NC} POST /api/v1.1/notifications — expected HTTP 200/201/202, got $HTTP_CODE"
  FAIL_COUNT=$((FAIL_COUNT + 1))
fi

# ---- Step 3: Verify trust header enforcement — missing X-Tenant-ID → 400 ----
REQUEST_ID_BAD="req-$(date +%s%N)"
CORRELATION_ID_BAD="corr-$(date +%s%N)"

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -H "X-Pod-ID: national" \
  -H "X-Request-ID: ${REQUEST_ID_BAD}" \
  -H "X-Correlation-ID: ${CORRELATION_ID_BAD}" \
  -H "Idempotency-Key: idem-bad-$(date +%s%N)" \
  -d "${NOTIFICATION_PAYLOAD}" \
  "${NOTIFICATION_URL}/api/v1.1/notifications")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

assert_status "POST /api/v1.1/notifications without X-Tenant-ID" "400" "$HTTP_CODE"

# ---- Step 4: Verify error envelope format ----
assert_json_field "Error envelope has code" "$BODY" ".code"
assert_json_field "Error envelope has message" "$BODY" ".message"
assert_json_field "Error envelope has request_id" "$BODY" ".request_id"
assert_json_field "Error envelope has correlation_id" "$BODY" ".correlation_id"

print_summary
