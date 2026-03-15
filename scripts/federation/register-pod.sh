#!/usr/bin/env bash
# register-pod.sh — Register a pod with the national spine
#
# Usage: ./register-pod.sh [SPINE_URL] [POD_ID] [POD_NAME] [REGION] [CERT_FILE]
#
# Environment:
#   SPINE_URL    — Base URL of the national spine (default: http://localhost:8081)
#   ADMIN_TOKEN  — JWT admin token for authentication
#   POD_ID       — UUID for the pod (auto-generated if not provided)
#   POD_NAME     — Human-readable pod name
#   REGION       — Geographic region
#   CERT_FILE    — Path to PEM certificate file

set -euo pipefail

SPINE_URL="${1:-${SPINE_URL:-http://localhost:8081}}"
POD_ID="${2:-${POD_ID:-$(uuidgen || python3 -c 'import uuid; print(uuid.uuid4())')}}"
POD_NAME="${3:-${POD_NAME:-Facility-Pod-Pilot-1}}"
REGION="${4:-${REGION:-Harare}}"
CERT_FILE="${5:-${CERT_FILE:-}}"
ADMIN_TOKEN="${ADMIN_TOKEN:-}"
FACILITY_ID="${FACILITY_ID:-$(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')}"

echo "=== Federation Pod Registration ==="
echo "Spine URL:  ${SPINE_URL}"
echo "Pod ID:     ${POD_ID}"
echo "Pod Name:   ${POD_NAME}"
echo "Region:     ${REGION}"
echo ""

# Read certificate if file provided, else use a placeholder for dev
if [ -n "${CERT_FILE}" ] && [ -f "${CERT_FILE}" ]; then
    POD_CERT=$(cat "${CERT_FILE}")
    echo "Certificate: loaded from ${CERT_FILE}"
else
    POD_CERT="-----BEGIN CERTIFICATE-----
MIIBkTCB+wIJALRiMLAh2sFdMA0GCSqGSIb3DQEBCwUAMBExDzANBgNVBAMMBnBp
bG90MTAeFw0yNjAzMTUwMDAwMDBaFw0yNzAzMTUwMDAwMDBaMBExDzANBgNVBAMM
BnBpbG90MTBcMA0GCSqGSIb3DQEBAQUAA0sAMEgCQQC7o96HtiY/4jLZqVbsXyFM
SFUBzpaZPK3J/E6LVEkUOVQf1JZjvjW+VfVLDQ5d5N8FUOvH6NjW+VfVLDQ5d5MH
AgMBAAEwDQYJKoZIhvcNAQELBQADQQBVdYdMhHl2L0n2EPFdJoQwVGGBv8v6N8zZ
-----END CERTIFICATE-----"
    echo "Certificate: using development placeholder (provide CERT_FILE for production)"
fi

# Build auth header if token available
AUTH_HEADER=""
if [ -n "${ADMIN_TOKEN}" ]; then
    AUTH_HEADER="-H \"Authorization: Bearer ${ADMIN_TOKEN}\""
fi

# Step 1: Send registration request
echo ""
echo ">>> Step 1: Sending registration request..."

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    "${SPINE_URL}/internal/v1/federation/pods/register" \
    -H "Content-Type: application/json" \
    -H "X-Pod-ID: national" \
    -H "X-Tenant-ID: pilot-tenant" \
    -H "X-Request-ID: $(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')" \
    -H "X-Correlation-ID: $(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')" \
    ${ADMIN_TOKEN:+-H "Authorization: Bearer ${ADMIN_TOKEN}"} \
    -d "{
        \"podId\": \"${POD_ID}\",
        \"podName\": \"${POD_NAME}\",
        \"podCertificate\": $(echo "${POD_CERT}" | python3 -c 'import sys,json; print(json.dumps(sys.stdin.read()))'),
        \"capabilities\": [\"PATIENT_REGISTRY\", \"CLINICAL_CAPTURE\", \"BILLING\"],
        \"region\": \"${REGION}\",
        \"facilityIds\": [\"${FACILITY_ID}\"],
        \"requestedDataClasses\": [\"PATIENT\", \"ENCOUNTER\", \"OBSERVATION\"],
        \"requestedMaxOfflineHours\": 72
    }" 2>/dev/null)

HTTP_CODE=$(echo "${RESPONSE}" | tail -1)
BODY=$(echo "${RESPONSE}" | sed '$d')

echo "HTTP Status: ${HTTP_CODE}"

if [ "${HTTP_CODE}" = "201" ]; then
    echo "Registration successful!"
    echo "${BODY}" | python3 -m json.tool 2>/dev/null || echo "${BODY}"

    REGISTRATION_ID=$(echo "${BODY}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('registration_id',''))" 2>/dev/null || echo "")
    echo ""
    echo "=== Registration Summary ==="
    echo "Registration ID: ${REGISTRATION_ID}"
    echo "Pod ID:          ${POD_ID}"
    echo "Pod Name:        ${POD_NAME}"
    echo ""

    # Step 2: Verify with heartbeat
    echo ">>> Step 2: Sending initial heartbeat..."
    HB_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
        "${SPINE_URL}/internal/v1/federation/pods/${POD_ID}/heartbeat" \
        -H "X-Pod-ID: national" \
        -H "X-Tenant-ID: pilot-tenant" \
        -H "X-Request-ID: $(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')" \
        -H "X-Correlation-ID: $(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')" \
        ${ADMIN_TOKEN:+-H "Authorization: Bearer ${ADMIN_TOKEN}"} 2>/dev/null)

    HB_CODE=$(echo "${HB_RESPONSE}" | tail -1)
    HB_BODY=$(echo "${HB_RESPONSE}" | sed '$d')

    echo "Heartbeat HTTP Status: ${HB_CODE}"
    if [ "${HB_CODE}" = "200" ]; then
        echo "Heartbeat acknowledged!"
        echo "${HB_BODY}" | python3 -m json.tool 2>/dev/null || echo "${HB_BODY}"
    else
        echo "Heartbeat failed: ${HB_BODY}"
    fi
else
    echo "Registration failed!"
    echo "${BODY}" | python3 -m json.tool 2>/dev/null || echo "${BODY}"
    exit 1
fi

echo ""
echo "=== Pod Registration Complete ==="
echo "Save this pod ID for use with issue-pilot-token.sh: ${POD_ID}"
