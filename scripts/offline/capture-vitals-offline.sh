#!/usr/bin/env bash
# capture-vitals-offline.sh — End-to-end offline vitals capture workflow
#
# Usage: ./capture-vitals-offline.sh [EDGE_URL]
#
# Demonstrates the full Wave 22 offline pilot workflow:
# 1. Issues an HMAC entitlement with device binding
# 2. Captures vitals offline (with hash chain)
# 3. Replays to BUTANO
# 4. Checks conflict queue
#
# Environment:
#   EDGE_URL    — Base URL of offline-edge-service (default: http://localhost:8360)
#   TENANT_ID   — Tenant UUID
#   POD_ID      — Pod identifier
#   ACTOR_ID    — Clinician actor ID

set -euo pipefail

EDGE_URL="${1:-${EDGE_URL:-http://localhost:8360}}"
TENANT_ID="${TENANT_ID:-$(python3 -c 'import uuid; print(uuid.uuid4())')}"
POD_ID="${POD_ID:-pilot-pod-1}"
ACTOR_ID="${ACTOR_ID:-nurse-001}"
FACILITY_REF="${FACILITY_REF:-facility-harare-central}"
DEVICE_FP="${DEVICE_FP:-device-fp-sha256-$(date +%s)}"
REQUEST_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
CORRELATION_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"

PASS=0
FAIL=0

pass() { echo "  ✓ PASS: $1"; PASS=$((PASS + 1)); }
fail() { echo "  ✗ FAIL: $1"; FAIL=$((FAIL + 1)); }

echo "============================================="
echo "  Offline Vitals Capture — E2E Pilot"
echo "============================================="
echo ""
echo "Edge URL:     ${EDGE_URL}"
echo "Tenant:       ${TENANT_ID}"
echo "Actor:        ${ACTOR_ID}"
echo "Facility:     ${FACILITY_REF}"
echo "Device FP:    ${DEVICE_FP}"
echo ""

# ─── Step 1: Issue entitlement with device binding ───
echo "─── Step 1: Issue entitlement ───"

ENTITLEMENT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    "${EDGE_URL}/internal/v1/offline/entitlements" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -H "X-Pod-ID: ${POD_ID}" \
    -H "X-Request-ID: ${REQUEST_ID}" \
    -H "X-Correlation-ID: ${CORRELATION_ID}" \
    -d "{
        \"actorId\": \"${ACTOR_ID}\",
        \"facilityRef\": \"${FACILITY_REF}\",
        \"workflowType\": \"CAPTURE_VITALS\",
        \"deviceFingerprint\": \"${DEVICE_FP}\",
        \"maxOfflineEncounters\": 50
    }" 2>/dev/null)

ENT_CODE=$(echo "${ENTITLEMENT_RESPONSE}" | tail -1)
ENT_BODY=$(echo "${ENTITLEMENT_RESPONSE}" | sed '$d')

if [ "${ENT_CODE}" = "201" ]; then
    pass "Entitlement issued (201)"
    ENTITLEMENT_ID=$(echo "${ENT_BODY}" | python3 -c "import sys,json; print(json.load(sys.stdin)['entitlementId'])" 2>/dev/null || echo "")
    TOKEN_HASH=$(echo "${ENT_BODY}" | python3 -c "import sys,json; print(json.load(sys.stdin)['tokenHash'])" 2>/dev/null || echo "")
    echo "  Entitlement ID: ${ENTITLEMENT_ID}"
    echo "  Token Hash:     ${TOKEN_HASH:0:16}..."

    # Check device fingerprint in response
    DEVICE_IN_RESPONSE=$(echo "${ENT_BODY}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('deviceFingerprint',''))" 2>/dev/null || echo "")
    if [ "${DEVICE_IN_RESPONSE}" = "${DEVICE_FP}" ]; then
        pass "Device fingerprint stored in entitlement"
    else
        fail "Device fingerprint not in response"
    fi
else
    fail "Entitlement issuance failed (HTTP ${ENT_CODE})"
    echo "  Response: ${ENT_BODY}"
    echo ""; echo "Cannot continue without entitlement."; exit 1
fi

echo ""

# ─── Step 2: Capture vitals ───
echo "─── Step 2: Capture vitals (4 readings) ───"

PREV_HASH=""
READINGS=(
    '{"code":"85354-9","display":"Blood pressure","value":"120/80","unit":"mmHg"}'
    '{"code":"8867-4","display":"Heart rate","value":72,"unit":"bpm"}'
    '{"code":"8310-5","display":"Body temperature","value":36.8,"unit":"°C"}'
    '{"code":"2708-6","display":"SpO2","value":98,"unit":"%"}'
)

for i in "${!READINGS[@]}"; do
    SEQ=$((i + 1))
    IDEM_KEY="pilot-vital-${ENTITLEMENT_ID}-${SEQ}"
    CAPTURED_AT=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    CAPTURE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
        "${EDGE_URL}/internal/v1/offline/actions" \
        -H "Content-Type: application/json" \
        -H "X-Tenant-ID: ${TENANT_ID}" \
        -H "X-Pod-ID: ${POD_ID}" \
        -H "X-Request-ID: $(python3 -c 'import uuid; print(uuid.uuid4())')" \
        -H "X-Correlation-ID: ${CORRELATION_ID}" \
        -H "X-Idempotency-Key: ${IDEM_KEY}" \
        -d "{
            \"entitlementId\": \"${ENTITLEMENT_ID}\",
            \"patientRef\": \"CPID-PILOT-12345\",
            \"actionType\": \"CAPTURE_VITALS\",
            \"payload\": ${READINGS[$i]},
            \"capturedAt\": \"${CAPTURED_AT}\",
            \"deviceId\": \"${DEVICE_FP}\",
            \"sequenceNum\": ${SEQ}
        }" 2>/dev/null)

    CAP_CODE=$(echo "${CAPTURE_RESPONSE}" | tail -1)

    if [ "${CAP_CODE}" = "201" ]; then
        pass "Vital ${SEQ} captured: $(echo "${READINGS[$i]}" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("display","?"))' 2>/dev/null)"
    else
        fail "Vital ${SEQ} capture failed (HTTP ${CAP_CODE})"
    fi
done

echo ""

# ─── Step 3: Replay to BUTANO ───
echo "─── Step 3: Replay to BUTANO ───"

REPLAY_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    "${EDGE_URL}/internal/v1/offline/replay/${ENTITLEMENT_ID}" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -H "X-Pod-ID: ${POD_ID}" \
    -H "X-Request-ID: $(python3 -c 'import uuid; print(uuid.uuid4())')" \
    -H "X-Correlation-ID: ${CORRELATION_ID}" 2>/dev/null)

REPLAY_CODE=$(echo "${REPLAY_RESPONSE}" | tail -1)
REPLAY_BODY=$(echo "${REPLAY_RESPONSE}" | sed '$d')

if [ "${REPLAY_CODE}" = "200" ]; then
    REPLAY_STATUS=$(echo "${REPLAY_BODY}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('status',''))" 2>/dev/null || echo "")
    REPLAYED_COUNT=$(echo "${REPLAY_BODY}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('replayed_count',0))" 2>/dev/null || echo "0")
    pass "Replay completed: status=${REPLAY_STATUS}, replayed=${REPLAYED_COUNT}"
else
    echo "  Note: Replay returned HTTP ${REPLAY_CODE} (BUTANO may not be running)"
    echo "  This is expected in environments without BUTANO configured."
fi

echo ""

# ─── Step 4: Check conflict queue ───
echo "─── Step 4: Check conflict queue ───"

CONFLICT_RESPONSE=$(curl -s -X GET \
    "${EDGE_URL}/internal/v1/offline/conflicts?status=PENDING" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -H "X-Pod-ID: ${POD_ID}" \
    -H "X-Request-ID: $(python3 -c 'import uuid; print(uuid.uuid4())')" \
    -H "X-Correlation-ID: ${CORRELATION_ID}" 2>/dev/null)

PENDING_COUNT=$(echo "${CONFLICT_RESPONSE}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('pending_count',0))" 2>/dev/null || echo "?")
echo "  Pending conflicts: ${PENDING_COUNT}"

echo ""
echo "============================================="
echo "  Results: ${PASS} passed, ${FAIL} failed"
echo "============================================="

if [ "${FAIL}" -gt 0 ]; then
    exit 1
fi
