#!/usr/bin/env bash
# =============================================================================
# Reconcile impilo-backend's token scope with the realm source of truth.
#
# WHY: citizen self-registration was dead in the real UI (found by the
# from-zero golden-thread journey, 2026-07-19). The BFF's register flow uses
# the impilo-backend service account for Keycloak admin calls; the service
# account HOLDS realm-management manage-users/view-users/query-users/
# manage-realm, but the LIVE client has fullScopeAllowed=false (drifted from
# files/realm-impilo-preview.json, which says true — realm import is
# first-import-only), so client_credentials tokens carry an EMPTY
# resource_access and every admin call 403s ("Identity service permissions
# need to be configured").
#
# Operator-run (Keycloak realm mutations are classifier-blocked for agents),
# same posture as reconcile-client-secrets.sh. Idempotent.
# =============================================================================
set -euo pipefail

NS="${NS:-impilo-full-preview}"
REALM="${REALM:-impilo}"
LPORT="${LPORT:-18090}"

sec(){ kubectl get secret impilo-app-secrets -n "$NS" -o jsonpath="{.data.$1}" | base64 -d; }
AUSER=$(sec keycloak-admin-user); APASS=$(sec keycloak-admin-password)
[ -z "$APASS" ] && { echo "keycloak admin password not found in $NS/impilo-app-secrets"; exit 1; }

echo "--- Grant impilo-backend full token scope ($NS realm=$REALM) ---"
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

FULL=$(curl -s -H "Authorization: Bearer $TK" "$KURL/admin/realms/$REALM/clients/$CID" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin).get("fullScopeAllowed"))')
echo "  live fullScopeAllowed=$FULL (realm source file says True)"

if [ "$FULL" != "True" ] && [ "$FULL" != "true" ]; then
  BODY=$(curl -s -H "Authorization: Bearer $TK" "$KURL/admin/realms/$REALM/clients/$CID" \
    | python3 -c 'import sys,json;d=json.load(sys.stdin);d["fullScopeAllowed"]=True;print(json.dumps(d))')
  CODE=$(curl -s -o /dev/null -w '%{http_code}' -X PUT -H "Authorization: Bearer $TK" \
    -H "Content-Type: application/json" "$KURL/admin/realms/$REALM/clients/$CID" -d "$BODY")
  echo "  PUT fullScopeAllowed=true -> $CODE"
  [ "$CODE" = "204" ] || { echo "  UPDATE FAILED"; exit 1; }
else
  echo "  already true — nothing to change"
fi

# Verify: a fresh service-account token must now reach the admin users API.
BSEC=$(sec keycloak-backend-secret)
BT=$(curl -s -X POST "$KURL/realms/$REALM/protocol/openid-connect/token" \
  -d grant_type=client_credentials -d client_id=impilo-backend --data-urlencode "client_secret=$BSEC" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
PROBE=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $BT" "$KURL/admin/realms/$REALM/users?max=1")
echo "  service-account admin probe -> $PROBE (want 200)"
[ "$PROBE" = "200" ] && echo "--- OK: registration admin path restored ---" || { echo "--- STILL BROKEN ---"; exit 1; }
