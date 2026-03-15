#!/usr/bin/env bash
# break-glass-review.sh — Break-glass activation and review cycle
#
# Usage: ./break-glass-review.sh [EDGE_URL]
#
# Demonstrates the Wave 22 break-glass workflow:
# 1. Activates break-glass mode
# 2. Verifies event is in PENDING_REVIEW status
# 3. Reviews the event (APPROVED)
# 4. Verifies review recorded
#
# Environment:
#   EDGE_URL    — Base URL of offline-edge-service (default: http://localhost:8360)
#   TENANT_ID   — Tenant UUID
#   POD_ID      — Pod identifier

set -euo pipefail

EDGE_URL="${1:-${EDGE_URL:-http://localhost:8360}}"
TENANT_ID="${TENANT_ID:-$(python3 -c 'import uuid; print(uuid.uuid4())')}"
POD_ID="${POD_ID:-pilot-pod-1}"
ACTOR_ID="${ACTOR_ID:-nurse-001}"
REVIEWER="${REVIEWER:-manager-001}"
REQUEST_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
CORRELATION_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"

PASS=0
FAIL=0

pass() { echo "  ✓ PASS: $1"; PASS=$((PASS + 1)); }
fail() { echo "  ✗ FAIL: $1"; FAIL=$((FAIL + 1)); }

echo "============================================="
echo "  Break-Glass Activation & Review Pilot"
echo "============================================="
echo ""
echo "Edge URL:  ${EDGE_URL}"
echo "Tenant:    ${TENANT_ID}"
echo "Actor:     ${ACTOR_ID}"
echo "Reviewer:  ${REVIEWER}"
echo ""

# ─── Step 1: Activate break-glass ───
echo "─── Step 1: Activate break-glass ───"

BG_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    "${EDGE_URL}/internal/v1/offline/break-glass" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -H "X-Pod-ID: ${POD_ID}" \
    -H "X-Request-ID: ${REQUEST_ID}" \
    -H "X-Correlation-ID: ${CORRELATION_ID}" \
    -H "X-User-ID: ${ACTOR_ID}" \
    -d "{
        \"actor_id\": \"${ACTOR_ID}\",
        \"facility_id\": \"facility-harare-central\",
        \"patient_ref\": \"CPID-PILOT-67890\",
        \"reason\": \"Emergency: patient transferred from Facility-B, no connectivity to re-scope entitlement\",
        \"override_type\": \"SCOPE_EXTENSION\",
        \"original_scope\": \"FACILITY-A\",
        \"extended_scope\": \"FACILITY-A,FACILITY-B\"
    }" 2>/dev/null)

BG_CODE=$(echo "${BG_RESPONSE}" | tail -1)
BG_BODY=$(echo "${BG_RESPONSE}" | sed '$d')

if [ "${BG_CODE}" = "201" ]; then
    pass "Break-glass activated (201)"
    EVENT_ID=$(echo "${BG_BODY}" | python3 -c "import sys,json; print(json.load(sys.stdin)['event_id'])" 2>/dev/null || echo "")
    BG_STATUS=$(echo "${BG_BODY}" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])" 2>/dev/null || echo "")
    echo "  Event ID: ${EVENT_ID}"
    echo "  Status:   ${BG_STATUS}"

    if [ "${BG_STATUS}" = "PENDING_REVIEW" ]; then
        pass "Status is PENDING_REVIEW"
    else
        fail "Expected PENDING_REVIEW, got ${BG_STATUS}"
    fi
else
    fail "Break-glass activation failed (HTTP ${BG_CODE})"
    echo "  Response: ${BG_BODY}"
    echo ""; echo "Cannot continue."; exit 1
fi

echo ""

# ─── Step 2: Verify in review queue ───
echo "─── Step 2: Verify in review queue ───"

LIST_RESPONSE=$(curl -s -X GET \
    "${EDGE_URL}/internal/v1/offline/break-glass?status=PENDING_REVIEW" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -H "X-Pod-ID: ${POD_ID}" \
    -H "X-Request-ID: $(python3 -c 'import uuid; print(uuid.uuid4())')" \
    -H "X-Correlation-ID: ${CORRELATION_ID}" 2>/dev/null)

PENDING=$(echo "${LIST_RESPONSE}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('pending_count',0))" 2>/dev/null || echo "?")
echo "  Pending reviews: ${PENDING}"

if [ "${PENDING}" != "0" ] && [ "${PENDING}" != "?" ]; then
    pass "Review queue contains pending items"
else
    fail "Review queue is empty"
fi

echo ""

# ─── Step 3: Review the event ───
echo "─── Step 3: Review break-glass event ───"

REVIEW_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    "${EDGE_URL}/internal/v1/offline/break-glass/${EVENT_ID}/review" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -H "X-Pod-ID: ${POD_ID}" \
    -H "X-Request-ID: $(python3 -c 'import uuid; print(uuid.uuid4())')" \
    -H "X-Correlation-ID: ${CORRELATION_ID}" \
    -H "X-User-ID: ${REVIEWER}" \
    -d "{
        \"resolution\": \"APPROVED\",
        \"reviewed_by\": \"${REVIEWER}\",
        \"notes\": \"Verified: legitimate emergency transfer, patient required immediate care\"
    }" 2>/dev/null)

REV_CODE=$(echo "${REVIEW_RESPONSE}" | tail -1)
REV_BODY=$(echo "${REVIEW_RESPONSE}" | sed '$d')

if [ "${REV_CODE}" = "200" ]; then
    REV_STATUS=$(echo "${REV_BODY}" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])" 2>/dev/null || echo "")
    pass "Review completed: status=${REV_STATUS}"

    if [ "${REV_STATUS}" = "APPROVED" ]; then
        pass "Resolution is APPROVED"
    else
        fail "Expected APPROVED, got ${REV_STATUS}"
    fi
else
    fail "Review failed (HTTP ${REV_CODE})"
    echo "  Response: ${REV_BODY}"
fi

echo ""

# ─── Step 4: Verify review recorded ───
echo "─── Step 4: Verify review recorded ───"

GET_RESPONSE=$(curl -s -X GET \
    "${EDGE_URL}/internal/v1/offline/break-glass/${EVENT_ID}" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -H "X-Pod-ID: ${POD_ID}" \
    -H "X-Request-ID: $(python3 -c 'import uuid; print(uuid.uuid4())')" \
    -H "X-Correlation-ID: ${CORRELATION_ID}" 2>/dev/null)

REVIEWED_BY=$(echo "${GET_RESPONSE}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reviewed_by',''))" 2>/dev/null || echo "")
REVIEWED_AT=$(echo "${GET_RESPONSE}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('reviewed_at',''))" 2>/dev/null || echo "")

if [ -n "${REVIEWED_BY}" ] && [ -n "${REVIEWED_AT}" ]; then
    pass "Review audit trail recorded (by=${REVIEWED_BY})"
else
    fail "Review audit trail missing"
fi

# ─── Step 5: Verify double-review blocked ───
echo ""
echo "─── Step 5: Verify double-review blocked ───"

DOUBLE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "${EDGE_URL}/internal/v1/offline/break-glass/${EVENT_ID}/review" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -H "X-Pod-ID: ${POD_ID}" \
    -H "X-Request-ID: $(python3 -c 'import uuid; print(uuid.uuid4())')" \
    -H "X-Correlation-ID: ${CORRELATION_ID}" \
    -d "{\"resolution\": \"FLAGGED\", \"reviewed_by\": \"other-manager\"}" 2>/dev/null || echo "000")

if [ "${DOUBLE_CODE}" = "409" ]; then
    pass "Double-review blocked (409 ALREADY_REVIEWED)"
else
    echo "  Note: Got HTTP ${DOUBLE_CODE} (expected 409)"
fi

echo ""
echo "============================================="
echo "  Results: ${PASS} passed, ${FAIL} failed"
echo "============================================="

if [ "${FAIL}" -gt 0 ]; then
    exit 1
fi
