#!/usr/bin/env bash
#
# reconcile-client-secrets.sh — set each confidential Keycloak client's secret to
# its intended value from the impilo-app-secrets k8s Secret.
#
# WHY: the realm import (files/realm-impilo-preview.json) uses "${env.KC_CLIENT_SECRET_*}"
# placeholders. Keycloak's realm import is first-import-only and the ${env.X}
# substitution is NOT applied on the running estate, so each confidential client is
# imported with the LITERAL placeholder string as its secret. Every consumer (BFF
# signup via impilo-backend, ops-console login, etc.) then sends the real secret and
# gets 401 unauthorized_client — e.g. citizen signup shows "identity service not ready".
#
# Runs admin calls via a Keycloak port-forward using the HOST's curl (the Keycloak
# container image has no curl). Idempotent. Grant check applies only to
# service-account clients; ops-console has no service account (client_credentials
# 401 there is expected — put=204 is its success signal).
set -uo pipefail

NS="${NAMESPACE:-impilo-full-preview}"
REALM="${KEYCLOAK_REALM:-impilo}"
LPORT="${KC_RECONCILE_PORT:-18080}"

# clientId : k8s secret key holding its intended secret
CLIENTS="impilo-backend:keycloak-backend-secret
impilo-bff:keycloak-client-secret-bff
impilo-admin-cli:keycloak-client-secret-admin-cli
impilo-ops-console:keycloak-client-secret-ops-console"

sec() { kubectl get secret impilo-app-secrets -n "$NS" -o jsonpath="{.data.$1}" 2>/dev/null | base64 -d; }
AUSER=$(sec keycloak-admin-user); APASS=$(sec keycloak-admin-password)
[ -z "$APASS" ] && { echo "reconcile: keycloak admin password not found in $NS/impilo-app-secrets"; exit 1; }

echo "--- Reconcile Keycloak confidential client secrets ($NS realm=$REALM) ---"
kubectl port-forward -n "$NS" svc/keycloak "${LPORT}:8080" >/tmp/kc-reconcile-pf.log 2>&1 &
PF=$!; trap 'kill $PF 2>/dev/null' EXIT
KURL="http://127.0.0.1:${LPORT}"
for i in $(seq 1 30); do curl -sf -o /dev/null "$KURL/realms/master/.well-known/openid-configuration" 2>/dev/null && break; sleep 1; done

TK=$(curl -s -X POST "$KURL/realms/master/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=admin-cli \
  --data-urlencode "username=$AUSER" --data-urlencode "password=$APASS" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -z "$TK" ] && { echo "  ADMIN TOKEN FAILED"; exit 1; }

echo "$CLIENTS" | while IFS=: read -r CID KEY; do
  [ -z "$CID" ] && continue
  WANT=$(sec "$KEY")
  [ -z "$WANT" ] && { echo "  SKIP $CID (secret key $KEY empty)"; continue; }
  # First "id" in the ?clientId= response is the client's own UUID. Must grab the
  # FIRST match — a greedy sed grabs a nested protocol-mapper id and 404s the PUT.
  UUID=$(curl -s -H "Authorization: Bearer $TK" "$KURL/admin/realms/$REALM/clients?clientId=$CID" \
    | grep -oE '"id":"[^"]*"' | head -1 | sed 's/"id":"\([^"]*\)"/\1/')
  [ -z "$UUID" ] && { echo "  MISS $CID (client not found)"; continue; }
  curl -s -H "Authorization: Bearer $TK" "$KURL/admin/realms/$REALM/clients/$UUID" > /tmp/kc-c.json
  # replace whatever the secret currently is (incl. literal ${env...}) with WANT
  awk -v w="$WANT" 'BEGIN{RS="";FS=""} {sub(/"secret":"[^"]*"/,"\"secret\":\"" w "\""); print}' /tmp/kc-c.json > /tmp/kc-c2.json
  code=$(curl -s -o /dev/null -w "%{http_code}" -X PUT \
    -H "Authorization: Bearer $TK" -H "Content-Type: application/json" \
    "$KURL/admin/realms/$REALM/clients/$UUID" --data @/tmp/kc-c2.json)
  gr=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$KURL/realms/$REALM/protocol/openid-connect/token" \
    -d grant_type=client_credentials -d client_id="$CID" --data-urlencode "client_secret=$WANT")
  echo "  FIX  $CID  put=$code grant=$gr"
done
rm -f /tmp/kc-c.json /tmp/kc-c2.json
echo "--- Reconcile done (put=204 = success; ops-console grant 401 is expected: no service account) ---"
