#!/usr/bin/env bash
#
# reconcile-client-secrets.sh — set each confidential Keycloak client's secret to
# its KC_CLIENT_SECRET_* env value.
#
# WHY: the realm import (files/realm-impilo-preview.json) uses "${env.KC_CLIENT_SECRET_*}"
# placeholders. Keycloak's realm import is first-import-only and the ${env.X}
# substitution is NOT applied on the running estate, so the clients are imported
# with the LITERAL placeholder string as their secret. Every consumer (BFF signup
# via impilo-backend, ops-console login, etc.) then sends the real secret and gets
# 401 unauthorized_client — e.g. citizen signup shows "identity service not ready".
#
# This reconciles the DB to what the import intended. It reads the authoritative
# KC_CLIENT_SECRET_* values from the running Keycloak pod's own env (same source
# the consumers use), so no secret is passed on the host command line. Idempotent.
#
# Run standalone after a fullboot, or wired into the deploy tail.
set -uo pipefail

NS="${NAMESPACE:-impilo-full-preview}"
REALM="${KEYCLOAK_REALM:-impilo}"

# clientId : env var holding its intended secret
CLIENTS="impilo-backend:KC_CLIENT_SECRET_BACKEND
impilo-bff:KC_CLIENT_SECRET_BFF
impilo-admin-cli:KC_CLIENT_SECRET_ADMIN_CLI
impilo-ops-console:KC_CLIENT_SECRET_OPS_CONSOLE"

KCPOD=$(kubectl get pod -n "$NS" -l app=keycloak -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
[ -z "$KCPOD" ] && { echo "reconcile: keycloak pod not found in $NS"; exit 1; }
AUSER=$(kubectl get secret impilo-app-secrets -n "$NS" -o jsonpath='{.data.keycloak-admin-user}' | base64 -d)
APASS=$(kubectl get secret impilo-app-secrets -n "$NS" -o jsonpath='{.data.keycloak-admin-password}' | base64 -d)
[ -z "$APASS" ] && { echo "reconcile: keycloak admin password not found"; exit 1; }

echo "--- Reconcile Keycloak confidential client secrets ($NS realm=$REALM) ---"
# All admin work runs INSIDE the keycloak pod (its env holds the intended secrets).
kubectl exec -n "$NS" "$KCPOD" -- sh -s -- "$AUSER" "$APASS" "$REALM" "$CLIENTS" <<'INNER'
AUSER="$1"; APASS="$2"; REALM="$3"; CLIENTS="$4"
KURL="http://localhost:8080"
TK=$(curl -s -X POST "$KURL/realms/master/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=admin-cli \
  --data-urlencode "username=$AUSER" --data-urlencode "password=$APASS" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -z "$TK" ] && { echo "  ADMIN TOKEN FAILED"; exit 1; }
echo "$CLIENTS" | while IFS=: read -r CID ENVV; do
  [ -z "$CID" ] && continue
  WANT=$(printenv "$ENVV")
  if [ -z "$WANT" ]; then echo "  SKIP $CID ($ENVV not set)"; continue; fi
  UUID=$(curl -s -H "Authorization: Bearer $TK" "$KURL/admin/realms/$REALM/clients?clientId=$CID" \
    | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | head -1)
  if [ -z "$UUID" ]; then echo "  MISS $CID (client not found)"; continue; fi
  CUR=$(curl -s -H "Authorization: Bearer $TK" "$KURL/admin/realms/$REALM/clients/$UUID/client-secret" \
    | sed -n 's/.*"value":"\([^"]*\)".*/\1/p')
  if [ "$CUR" = "$WANT" ]; then echo "  OK   $CID (already correct)"; continue; fi
  curl -s -H "Authorization: Bearer $TK" "$KURL/admin/realms/$REALM/clients/$UUID" > /tmp/c.json
  sed "s|\"secret\":\"[^\"]*\"|\"secret\":\"$WANT\"|" /tmp/c.json > /tmp/c2.json
  code=$(curl -s -o /dev/null -w "%{http_code}" -X PUT \
    -H "Authorization: Bearer $TK" -H "Content-Type: application/json" \
    "$KURL/admin/realms/$REALM/clients/$UUID" --data @/tmp/c2.json)
  # verify grant for service-account clients
  gr=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$KURL/realms/$REALM/protocol/openid-connect/token" \
    -d grant_type=client_credentials -d client_id="$CID" --data-urlencode "client_secret=$WANT")
  echo "  FIX  $CID  put=$code grant=$gr"
done
rm -f /tmp/c.json /tmp/c2.json
INNER
echo "--- Reconcile done ---"
