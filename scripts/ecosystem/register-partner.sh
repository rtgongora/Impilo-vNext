#!/usr/bin/env bash
# register-partner.sh — Register a new partner client in the developer portal
#
# Usage: ./register-partner.sh [OPTIONS]
#
# Options:
#   --name NAME          Partner/client name (required)
#   --email EMAIL        Contact email (required)
#   --description DESC   Description of integration
#   --sandbox            Enable sandbox mode (default: true)
#   --no-sandbox         Disable sandbox mode
#
# Environment:
#   PORTAL_URL   — Developer portal URL (default: http://localhost:8087)
#   TENANT_ID    — Tenant UUID
#   POD_ID       — Pod identifier

set -euo pipefail

PORTAL_URL="${PORTAL_URL:-http://localhost:8087}"
TENANT_ID="${TENANT_ID:-$(python3 -c 'import uuid; print(uuid.uuid4())')}"
POD_ID="${POD_ID:-national-spine}"
REQUEST_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
CORRELATION_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
IDEMPOTENCY_KEY="reg-$(python3 -c 'import uuid; print(uuid.uuid4())')"

CLIENT_NAME=""
CONTACT_EMAIL=""
DESCRIPTION=""
SANDBOX_ENABLED="true"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --name) CLIENT_NAME="$2"; shift 2 ;;
        --email) CONTACT_EMAIL="$2"; shift 2 ;;
        --description) DESCRIPTION="$2"; shift 2 ;;
        --sandbox) SANDBOX_ENABLED="true"; shift ;;
        --no-sandbox) SANDBOX_ENABLED="false"; shift ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

if [ -z "${CLIENT_NAME}" ]; then
    echo "Error: --name is required"
    echo "Usage: ./register-partner.sh --name 'Partner Name' --email 'dev@partner.com'"
    exit 1
fi

if [ -z "${CONTACT_EMAIL}" ]; then
    echo "Error: --email is required"
    exit 1
fi

echo "============================================="
echo "  Register Partner Client"
echo "============================================="
echo ""
echo "Portal:      ${PORTAL_URL}"
echo "Tenant:      ${TENANT_ID}"
echo "Name:        ${CLIENT_NAME}"
echo "Email:       ${CONTACT_EMAIL}"
echo "Sandbox:     ${SANDBOX_ENABLED}"
echo ""

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    "${PORTAL_URL}/internal/v1/developer/clients" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -H "X-Pod-ID: ${POD_ID}" \
    -H "X-Request-ID: ${REQUEST_ID}" \
    -H "X-Correlation-ID: ${CORRELATION_ID}" \
    -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
    -d "{
        \"clientName\": \"${CLIENT_NAME}\",
        \"description\": \"${DESCRIPTION}\",
        \"contactEmail\": \"${CONTACT_EMAIL}\",
        \"sandboxEnabled\": ${SANDBOX_ENABLED}
    }" 2>/dev/null)

HTTP_CODE=$(echo "${RESPONSE}" | tail -1)
BODY=$(echo "${RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "201" ]; then
    echo "✓ Partner registered successfully!"
    echo ""
    echo "Response:"
    echo "${BODY}" | python3 -m json.tool 2>/dev/null || echo "${BODY}"
    echo ""

    CLIENT_ID=$(echo "${BODY}" | python3 -c "import sys,json; print(json.load(sys.stdin)['client_id'])" 2>/dev/null || echo "?")
    echo "─── Next Steps ───"
    echo "1. Issue API key:"
    echo "   ./scripts/ecosystem/issue-partner-keys.sh --client-id ${CLIENT_ID} --label 'Sandbox Key'"
    echo ""
    echo "2. Run contract certification:"
    echo "   ./scripts/ecosystem/run-contract-certification.sh --client-id ${CLIENT_ID}"
else
    echo "✗ Registration failed (HTTP ${HTTP_CODE})"
    echo "Response: ${BODY}"
    exit 1
fi
