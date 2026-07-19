#!/usr/bin/env bash
# =============================================================================
# Restore admin-role claims to impilo-backend's service-account token.
#
# WHY: citizen self-registration is dead in the real UI (found by the
# from-zero golden-thread journey, 2026-07-19). The BFF register flow uses the
# impilo-backend service account for Keycloak admin calls. The account HOLDS
# realm-management manage-users/view-users/query-users/manage-realm — BUT the
# access token carries an EMPTY resource_access, so every admin call 403s
# ("Identity service permissions need to be configured").
#
# ROOT CAUSE (not fullScopeAllowed — that is already true): this realm has NO
# "roles" client scope, and impilo-backend carries only session-note protocol
# mappers, so nothing ever emits the realm-management roles into
# resource_access. The fix is a client-roles protocol mapper on the client
# (mirrors the standard "roles" scope's mapper). Committed to
# files/realm-impilo-preview.json for future full-boots; this script applies it
# to the live realm (realm import is first-import-only).
#
# Operator-run (Keycloak realm mutations are classifier-blocked for agents).
# Idempotent: skips if the mapper already exists.
# =============================================================================
set -euo pipefail

NS="${NS:-impilo-full-preview}"
REALM="${REALM:-impilo}"
LPORT="${LPORT:-18090}"

sec(){ kubectl get secret impilo-app-secrets -n "$NS" -o jsonpath="{.data.$1}" | base64 -d; }
AUSER=$(sec keycloak-admin-user); APASS=$(sec keycloak-admin-password)
[ -z "$APASS" ] && { echo "keycloak admin password not found in $NS/impilo-app-secrets"; exit 1; }

echo "--- Add client-roles mapper to impilo-backend ($NS realm=$REALM) ---"
kubectl port-forward -n "$NS" svc/keycloak "${LPORT}:8080" >/tmp/kc-scope-pf.log 2>&1 &
PF=$!; trap 'kill $PF 2>/dev/null' EXIT
KURL="http://127.0.0.1:${LPORT}"
for i in $(seq 1 30); do curl -sf -o /dev/null "$KURL/realms/master/.well-known/openid-configuration" 2>/dev/null && break; sleep 1; done

TK=$(curl -s -X POST "$KURL/realms/master/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=admin-cli \
  --data-urlencode "username=$AUSER" --data-urlencode "password=$APASS" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
[ -z "$TK" ] && { echo "  ADMIN TOKEN FAILED"; exit 1; }

CID=$(curl -s -H "Authorization: Bearer $TK" "$KURL/admin/realms/$REALM/clients?clientId=impilo-backend" \
  | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d[0]["id"] if d else "")')
[ -z "$CID" ] && { echo "  impilo-backend client not found"; exit 1; }

HAS=$(curl -s -H "Authorization: Bearer $TK" "$KURL/admin/realms/$REALM/clients/$CID/protocol-mappers/models" \
  | python3 -c 'import sys,json;print(any(m.get("protocolMapper")=="oidc-usermodel-client-role-mapper" for m in json.load(sys.stdin)))')

if [ "$HAS" = "True" ]; then
  echo "  client-roles mapper already present — nothing to change"
else
  MAPPER='{"name":"client roles","protocol":"openid-connect","protocolMapper":"oidc-usermodel-client-role-mapper","consentRequired":false,"config":{"multivalued":"true","userinfo.token.claim":"false","id.token.claim":"false","access.token.claim":"true","claim.name":"resource_access.${client_id}.roles","jsonType.label":"String","usermodel.clientRoleMapping.clientId":""}}'
  CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $TK" \
    -H "Content-Type: application/json" \
    "$KURL/admin/realms/$REALM/clients/$CID/protocol-mappers/models" -d "$MAPPER")
  echo "  POST client-roles mapper -> $CODE"
  [ "$CODE" = "201" ] || { echo "  ADD MAPPER FAILED"; exit 1; }
fi

# Verify: a fresh service-account token must now reach the admin users API.
BSEC=$(sec keycloak-backend-secret)
BT=$(curl -s -X POST "$KURL/realms/$REALM/protocol/openid-connect/token" \
  -d grant_type=client_credentials -d client_id=impilo-backend --data-urlencode "client_secret=$BSEC" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
PROBE=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $BT" "$KURL/admin/realms/$REALM/users?max=1")
echo "  service-account admin probe -> $PROBE (want 200)"
[ "$PROBE" = "200" ] && echo "--- OK: registration admin path restored ---" || { echo "--- STILL BROKEN ---"; exit 1; }
