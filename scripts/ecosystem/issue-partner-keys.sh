#!/usr/bin/env bash
# issue-partner-keys.sh — Issue or rotate API keys for a partner client
#
# Usage: ./issue-partner-keys.sh [OPTIONS]
#
# Options:
#   --client-id UUID     Client UUID (required)
#   --label LABEL        Key label (default: "API Key")
#   --expires-in-days N  Expiry in days (default: 30 for sandbox, 365 for prod)
#   --rotate KEY_ID      Rotate existing key (issues new, revokes old)
#   --sandbox            Mark as sandbox key
#
# Environment:
#   PORTAL_URL   — Developer portal URL (default: http://localhost:8087)
#   TENANT_ID    — Tenant UUID
#   POD_ID       — Pod identifier

set -euo pipefail

PORTAL_URL="${PORTAL_URL:-http://localhost:8087}"
TENANT_ID="${TENANT_ID:-$(python3 -c 'import uuid; print(uuid.uuid4())')}"
POD_ID="${POD_ID:-national-spine}"

CLIENT_ID=""
LABEL="API Key"
EXPIRES_DAYS=""
ROTATE_KEY_ID=""
SANDBOX=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --client-id) CLIENT_ID="$2"; shift 2 ;;
        --label) LABEL="$2"; shift 2 ;;
        --expires-in-days) EXPIRES_DAYS="$2"; shift 2 ;;
        --rotate) ROTATE_KEY_ID="$2"; shift 2 ;;
        --sandbox) SANDBOX=true; shift ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

if [ -z "${CLIENT_ID}" ] && [ -z "${ROTATE_KEY_ID}" ]; then
    echo "Error: --client-id is required (or --rotate for rotation)"
    echo "Usage: ./issue-partner-keys.sh --client-id <uuid> --label 'My Key'"
    exit 1
fi

# Default expiry based on environment
if [ -z "${EXPIRES_DAYS}" ]; then
    if [ "${SANDBOX}" = true ]; then
        EXPIRES_DAYS=30
    else
        EXPIRES_DAYS=365
    fi
fi

REQUEST_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
CORRELATION_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
IDEMPOTENCY_KEY="key-$(python3 -c 'import uuid; print(uuid.uuid4())')"

echo "============================================="
if [ -n "${ROTATE_KEY_ID}" ]; then
    echo "  Rotate Partner API Key"
else
    echo "  Issue Partner API Key"
fi
echo "============================================="
echo ""
echo "Portal:      ${PORTAL_URL}"
echo "Client ID:   ${CLIENT_ID:-N/A}"
echo "Label:       ${LABEL}"
echo "Expires in:  ${EXPIRES_DAYS} days"
echo "Sandbox:     ${SANDBOX}"
echo ""

if [ -n "${ROTATE_KEY_ID}" ]; then
    # ─── Rotate existing key ───
    echo "Rotating key: ${ROTATE_KEY_ID}"

    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
        "${PORTAL_URL}/internal/v1/developer/keys/${ROTATE_KEY_ID}/rotate" \
        -H "Content-Type: application/json" \
        -H "X-Tenant-ID: ${TENANT_ID}" \
        -H "X-Pod-ID: ${POD_ID}" \
        -H "X-Request-ID: ${REQUEST_ID}" \
        -H "X-Correlation-ID: ${CORRELATION_ID}" \
        -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
        -d "{
            \"label\": \"${LABEL}\",
            \"expiresInDays\": ${EXPIRES_DAYS}
        }" 2>/dev/null)
else
    # ─── Issue new key ───
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
        "${PORTAL_URL}/internal/v1/developer/clients/${CLIENT_ID}/keys" \
        -H "Content-Type: application/json" \
        -H "X-Tenant-ID: ${TENANT_ID}" \
        -H "X-Pod-ID: ${POD_ID}" \
        -H "X-Request-ID: ${REQUEST_ID}" \
        -H "X-Correlation-ID: ${CORRELATION_ID}" \
        -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
        -d "{
            \"label\": \"${LABEL}\",
            \"scopes\": [\"read\", \"write\"],
            \"expiresInDays\": ${EXPIRES_DAYS}
        }" 2>/dev/null)
fi

HTTP_CODE=$(echo "${RESPONSE}" | tail -1)
BODY=$(echo "${RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "201" ]; then
    echo "✓ API key issued successfully!"
    echo ""
    echo "╔══════════════════════════════════════════════════════════╗"
    echo "║  IMPORTANT: Save the api_key below. It is shown ONCE.  ║"
    echo "╚══════════════════════════════════════════════════════════╝"
    echo ""
    echo "${BODY}" | python3 -m json.tool 2>/dev/null || echo "${BODY}"
    echo ""

    API_KEY=$(echo "${BODY}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('api_key','?'))" 2>/dev/null || echo "?")
    KEY_PREFIX=$(echo "${BODY}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('key_prefix','?'))" 2>/dev/null || echo "?")

    echo "─── Quick Start ───"
    echo "Use this key in API requests:"
    echo "  curl -H 'X-API-Key: ${API_KEY}' \\"
    echo "       -H 'X-Tenant-ID: ${TENANT_ID}' \\"
    echo "       -H 'X-Pod-ID: ${POD_ID}' \\"
    echo "       -H 'X-Request-ID: <uuid>' \\"
    echo "       -H 'X-Correlation-ID: <uuid>' \\"
    echo "       ${PORTAL_URL}/internal/v1/developer/discovery"
else
    echo "✗ Key issuance failed (HTTP ${HTTP_CODE})"
    echo "Response: ${BODY}"
    exit 1
fi
