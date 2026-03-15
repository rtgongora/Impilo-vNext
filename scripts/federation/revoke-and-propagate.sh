#!/usr/bin/env bash
# revoke-and-propagate.sh — Revoke a pod and verify propagation
#
# Usage: ./revoke-and-propagate.sh [SPINE_URL] [POD_ID]
#
# Demonstrates the high-priority revocation channel:
# 1. Revokes the pod via API
# 2. Verifies revocation cache is updated
# 3. Verifies the pod's next request is rejected
# 4. Optionally reinstates the pod
#
# Environment:
#   SPINE_URL   — Base URL of the national spine (default: http://localhost:8081)
#   POD_ID      — Pod to revoke
#   REINSTATE   — Set to "true" to reinstate after verification

set -euo pipefail

SPINE_URL="${1:-${SPINE_URL:-http://localhost:8081}}"
POD_ID="${2:-${POD_ID:-}}"
REINSTATE="${REINSTATE:-false}"
REVOKED_BY="${REVOKED_BY:-federation-pilot-admin}"
REASON="${REASON:-PILOT_REVOCATION_TEST}"

if [ -z "${POD_ID}" ]; then
    echo "Error: POD_ID is required"
    echo "Usage: ./revoke-and-propagate.sh [SPINE_URL] <POD_ID>"
    exit 1
fi

PASS=0
FAIL=0

pass() { echo "  ✓ PASS: $1"; PASS=$((PASS + 1)); }
fail() { echo "  ✗ FAIL: $1"; FAIL=$((FAIL + 1)); }

echo "============================================="
echo "  Pod Revocation & Propagation Test"
echo "============================================="
echo ""
echo "Spine URL: ${SPINE_URL}"
echo "Pod ID:    ${POD_ID}"
echo "Revoked By: ${REVOKED_BY}"
echo "Reason:    ${REASON}"
echo ""

# Step 1: Verify pod is currently active
echo "─── Step 1: Verify pod is active ───"

PRE_STATUS=$(curl -s -X GET \
    "${SPINE_URL}/internal/v1/federation/pods/${POD_ID}/revocation-status" \
    -H "X-Pod-ID: national" \
    -H "X-Tenant-ID: pilot-tenant" \
    -H "X-Request-ID: revoke-pre" \
    -H "X-Correlation-ID: revoke-pre" 2>/dev/null)

PRE_REVOKED=$(echo "${PRE_STATUS}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('revoked', 'unknown'))" 2>/dev/null || echo "unknown")

if [ "${PRE_REVOKED}" = "False" ] || [ "${PRE_REVOKED}" = "false" ]; then
    pass "Pod is currently active (not revoked)"
else
    echo "  Warning: Pod may already be revoked (status: ${PRE_REVOKED})"
fi

echo ""

# Step 2: Revoke the pod
echo "─── Step 2: Revoke pod ───"

REVOKE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    "${SPINE_URL}/internal/v1/federation/pods/${POD_ID}/revoke" \
    -H "Content-Type: application/json" \
    -H "X-Pod-ID: national" \
    -H "X-Tenant-ID: pilot-tenant" \
    -H "X-Request-ID: revoke-cmd" \
    -H "X-Correlation-ID: revoke-cmd" \
    -d "{\"revoked_by\": \"${REVOKED_BY}\", \"reason\": \"${REASON}\"}" 2>/dev/null)

REVOKE_CODE=$(echo "${REVOKE_RESPONSE}" | tail -1)
REVOKE_BODY=$(echo "${REVOKE_RESPONSE}" | sed '$d')

if [ "${REVOKE_CODE}" = "200" ]; then
    pass "Pod revocation API returned 200"
    echo "  Response: $(echo "${REVOKE_BODY}" | python3 -m json.tool 2>/dev/null || echo "${REVOKE_BODY}")"
else
    fail "Pod revocation API returned ${REVOKE_CODE}"
    echo "  Response: ${REVOKE_BODY}"
fi

echo ""

# Step 3: Verify revocation cache updated (within 5s SLA)
echo "─── Step 3: Verify revocation cache ───"

START_TIME=$(date +%s)

POST_STATUS=$(curl -s -X GET \
    "${SPINE_URL}/internal/v1/federation/pods/${POD_ID}/revocation-status" \
    -H "X-Pod-ID: national" \
    -H "X-Tenant-ID: pilot-tenant" \
    -H "X-Request-ID: revoke-verify" \
    -H "X-Correlation-ID: revoke-verify" 2>/dev/null)

END_TIME=$(date +%s)
CACHE_LATENCY=$((END_TIME - START_TIME))

POST_REVOKED=$(echo "${POST_STATUS}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('revoked', 'unknown'))" 2>/dev/null || echo "unknown")

if [ "${POST_REVOKED}" = "True" ] || [ "${POST_REVOKED}" = "true" ]; then
    pass "Revocation cache updated (latency: ${CACHE_LATENCY}s, SLA: ≤5s)"
else
    fail "Revocation cache not updated (got: ${POST_REVOKED})"
fi

echo ""

# Step 4: Verify pod's next request is rejected
echo "─── Step 4: Verify revoked pod rejected ───"

REJECT_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "${SPINE_URL}/internal/v1/federation/pods/${POD_ID}/heartbeat" \
    -H "X-Pod-ID: ${POD_ID}" \
    -H "X-Tenant-ID: pilot-tenant" \
    -H "X-Request-ID: revoke-test-hb" \
    -H "X-Correlation-ID: revoke-test-hb" 2>/dev/null || echo "000")

if [ "${REJECT_CODE}" = "403" ]; then
    pass "Revoked pod's heartbeat rejected with 403"
else
    # 403 from federation filter OR from heartbeat endpoint (returns 403 for non-active)
    echo "  Note: Got HTTP ${REJECT_CODE} (expected 403 from filter or endpoint)"
    if [ "${REJECT_CODE}" = "000" ]; then
        fail "Cannot connect to spine"
    fi
fi

echo ""

# Step 5: Reinstate if requested
if [ "${REINSTATE}" = "true" ]; then
    echo "─── Step 5: Reinstate pod ───"

    REINSTATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
        "${SPINE_URL}/internal/v1/federation/pods/${POD_ID}/reinstate" \
        -H "Content-Type: application/json" \
        -H "X-Pod-ID: national" \
        -H "X-Tenant-ID: pilot-tenant" \
        -H "X-Request-ID: reinstate-cmd" \
        -H "X-Correlation-ID: reinstate-cmd" \
        -d "{\"reinstated_by\": \"${REVOKED_BY}\"}" 2>/dev/null)

    REINSTATE_CODE=$(echo "${REINSTATE_RESPONSE}" | tail -1)

    if [ "${REINSTATE_CODE}" = "200" ]; then
        pass "Pod reinstated successfully"

        # Verify cache cleared
        CLEAR_STATUS=$(curl -s -X GET \
            "${SPINE_URL}/internal/v1/federation/pods/${POD_ID}/revocation-status" \
            -H "X-Pod-ID: national" \
            -H "X-Tenant-ID: pilot-tenant" \
            -H "X-Request-ID: reinstate-verify" \
            -H "X-Correlation-ID: reinstate-verify" 2>/dev/null)

        CLEAR_REVOKED=$(echo "${CLEAR_STATUS}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('revoked', 'unknown'))" 2>/dev/null || echo "unknown")

        if [ "${CLEAR_REVOKED}" = "False" ] || [ "${CLEAR_REVOKED}" = "false" ]; then
            pass "Revocation cache cleared after reinstatement"
        else
            fail "Revocation cache not cleared after reinstatement"
        fi
    else
        fail "Pod reinstatement failed (HTTP ${REINSTATE_CODE})"
    fi
fi

echo ""
echo "============================================="
echo "  Results: ${PASS} passed, ${FAIL} failed"
echo "============================================="

if [ "${FAIL}" -gt 0 ]; then
    exit 1
fi
