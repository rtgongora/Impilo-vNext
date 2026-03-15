#!/usr/bin/env bash
# issue-pilot-token.sh — Issue a federation JWT for a registered pod
#
# Usage: ./issue-pilot-token.sh [POD_ID] [KEYCLOAK_URL]
#
# This script obtains a federation-scoped JWT from Keycloak for use
# in the federation pilot. The token has aud=impilo-federation and
# includes pod_id in its claims.
#
# Environment:
#   KEYCLOAK_URL    — Keycloak base URL (default: http://localhost:8080)
#   KEYCLOAK_REALM  — Realm name (default: impilo)
#   CLIENT_ID       — Keycloak client ID for the pod
#   CLIENT_SECRET   — Keycloak client secret
#   POD_ID          — Pod identifier (matches registration)

set -euo pipefail

POD_ID="${1:-${POD_ID:-}}"
KEYCLOAK_URL="${2:-${KEYCLOAK_URL:-http://localhost:8080}}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-impilo}"
CLIENT_ID="${CLIENT_ID:-federation-pilot-pod}"
CLIENT_SECRET="${CLIENT_SECRET:-}"

if [ -z "${POD_ID}" ]; then
    echo "Error: POD_ID is required"
    echo "Usage: ./issue-pilot-token.sh <POD_ID> [KEYCLOAK_URL]"
    exit 1
fi

echo "=== Federation Pilot Token Issuance ==="
echo "Pod ID:       ${POD_ID}"
echo "Keycloak URL: ${KEYCLOAK_URL}"
echo "Realm:        ${KEYCLOAK_REALM}"
echo "Client ID:    ${CLIENT_ID}"
echo ""

TOKEN_ENDPOINT="${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token"

echo ">>> Requesting federation token from ${TOKEN_ENDPOINT}..."

# Request token with federation audience
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${TOKEN_ENDPOINT}" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=client_credentials" \
    -d "client_id=${CLIENT_ID}" \
    -d "client_secret=${CLIENT_SECRET}" \
    -d "audience=impilo-federation" \
    -d "scope=federation" 2>/dev/null || echo -e "\n000")

HTTP_CODE=$(echo "${RESPONSE}" | tail -1)
BODY=$(echo "${RESPONSE}" | sed '$d')

echo "HTTP Status: ${HTTP_CODE}"

if [ "${HTTP_CODE}" = "200" ]; then
    ACCESS_TOKEN=$(echo "${BODY}" | python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null || echo "")

    if [ -n "${ACCESS_TOKEN}" ] && [ "${ACCESS_TOKEN}" != "" ]; then
        echo "Token obtained successfully!"
        echo ""

        # Decode JWT payload (without verification — for display only)
        PAYLOAD=$(echo "${ACCESS_TOKEN}" | cut -d. -f2 | python3 -c "
import sys, base64, json
data = sys.stdin.read().strip()
# Add padding
data += '=' * (4 - len(data) % 4)
decoded = base64.urlsafe_b64decode(data)
claims = json.loads(decoded)
print(json.dumps(claims, indent=2))
" 2>/dev/null || echo "(could not decode)")

        echo "=== Token Claims ==="
        echo "${PAYLOAD}"
        echo ""
        echo "=== Token (for use with verify-federation.sh) ==="
        echo "${ACCESS_TOKEN}"
        echo ""

        # Verify federation audience
        AUD=$(echo "${PAYLOAD}" | python3 -c "
import sys, json
claims = json.load(sys.stdin)
aud = claims.get('aud', '')
if isinstance(aud, list):
    print(','.join(aud))
else:
    print(aud)
" 2>/dev/null || echo "unknown")

        echo "Audience: ${AUD}"
        if echo "${AUD}" | grep -q "federation\|impilo-federation"; then
            echo "Federation audience: VERIFIED"
        else
            echo "WARNING: Token does not contain federation audience!"
            echo "Ensure Keycloak client '${CLIENT_ID}' has 'impilo-federation' in its audience mappers."
        fi
    else
        echo "Token response did not contain access_token"
        echo "${BODY}" | python3 -m json.tool 2>/dev/null || echo "${BODY}"
    fi
elif [ "${HTTP_CODE}" = "000" ]; then
    echo "ERROR: Could not connect to Keycloak at ${KEYCLOAK_URL}"
    echo ""
    echo "For local development without Keycloak, create a self-signed JWT:"
    echo ""

    # Generate a self-signed test JWT for development
    echo "=== Development Mode: Generating self-signed federation JWT ==="
    DEV_TOKEN=$(python3 -c "
import base64, json, hmac, hashlib, time

header = {'alg': 'HS256', 'typ': 'JWT'}
payload = {
    'iss': 'tshepo-federation',
    'sub': '${POD_ID}',
    'aud': 'impilo-federation',
    'iat': int(time.time()),
    'exp': int(time.time()) + 3600,
    'pod_id': '${POD_ID}',
    'authority_scope': {
        'data_classes': ['PATIENT', 'ENCOUNTER', 'OBSERVATION'],
        'facilities': []
    },
    'federation_version': '1.0'
}

def b64url(data):
    return base64.urlsafe_b64encode(json.dumps(data).encode()).rstrip(b'=').decode()

h = b64url(header)
p = b64url(payload)
sig = base64.urlsafe_b64encode(
    hmac.new(b'federation-pilot-secret', f'{h}.{p}'.encode(), hashlib.sha256).digest()
).rstrip(b'=').decode()

print(f'{h}.{p}.{sig}')
" 2>/dev/null)

    if [ -n "${DEV_TOKEN}" ]; then
        echo "Development JWT generated (aud=impilo-federation, pod_id=${POD_ID})"
        echo ""
        echo "=== Token ==="
        echo "${DEV_TOKEN}"
        echo ""
        echo "WARNING: This is a development token. Use Keycloak for production."
    else
        echo "Failed to generate dev token (requires python3)"
    fi
else
    echo "Token request failed!"
    echo "${BODY}" | python3 -m json.tool 2>/dev/null || echo "${BODY}"
    exit 1
fi
