#!/usr/bin/env bash
# =============================================================================
# Add the realm-roles protocol mapper to the mobile PKCE clients.
#
# WHY: the mobile clients (impilo-mobile-citizen / impilo-mobile-provider) were
# imported with NO protocol mappers and the realm has no built-in `roles` client
# scope, so their tokens never carried a `realm_access.roles` claim. Verified
# 2026-07-26: citizen.moyo, vashandi.worker (CLINICIAN+NURSE) and superadmin
# ALL returned `realm_access.roles: []`.
#
# That matters because the mobile apps derive ACTOR TYPE from that claim —
# apps/mobile/packages/mobile-auth/src/authStore.ts:356 resolveActorType():
#     roles.includes("CLINICIAN"|"NURSE"|"PHARMACIST")        -> PROVIDER
#     roles.includes("SYSTEM_ADMIN"|"FACILITY_ADMIN"|"FINANCE") -> OPERATOR
#     otherwise                                                -> CITIZEN
# With an empty array EVERY user falls through to CITIZEN. A clinician signing
# into Impilo Provider is classified as a citizen: wrong X-Actor-Type on every
# trust header, and provider-app/src/navigation/ModeSwitcher.tsx:47 reads the
# same claim, so the provider/supervisor/outreach modes never appear.
#
# It fails CLOSED (down-privileges rather than up), which is the safe direction,
# but the provider app is unusable for its actual purpose until this is applied.
#
# The web BFF is unaffected — it resolves roles server-side from its own identity
# path, which is why this was invisible from the web lane.
#
# The realm file carries this mapper too, but `--import-realm` skips realms that
# already exist, so an existing estate needs this script.
#
# Usage: NAMESPACE=impilo-full-preview bash scripts/keycloak/add-mobile-roles-mapper.sh
# =============================================================================
set -uo pipefail
NS="${NAMESPACE:-impilo-full-preview}"
PORT="${PORT:-18080}"

AU="$(kubectl -n "$NS" get secret impilo-app-secrets -o jsonpath='{.data.keycloak-admin-user}' | base64 -d)"
AP="$(kubectl -n "$NS" get secret impilo-app-secrets -o jsonpath='{.data.keycloak-admin-password}' | base64 -d)"
[ -z "$AU" ] && { echo "FAIL: could not read keycloak admin creds from impilo-app-secrets" >&2; exit 1; }

kubectl -n "$NS" port-forward svc/keycloak "${PORT}:8080" >/dev/null 2>&1 &
PF=$!
trap 'kill $PF 2>/dev/null' EXIT
sleep 6

KC="http://127.0.0.1:${PORT}"
TOK="$(curl -sS --max-time 15 -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d client_id=admin-cli -d grant_type=password -d "username=$AU" -d "password=$AP" 2>/dev/null \
  | python3 -c 'import json,sys;print(json.load(sys.stdin).get("access_token",""))')"
[ -z "$TOK" ] && { echo "FAIL: could not obtain Keycloak admin token" >&2; exit 1; }

MAPPER='{"name":"realm roles","protocol":"openid-connect",
 "protocolMapper":"oidc-usermodel-realm-role-mapper",
 "config":{"multivalued":"true","claim.name":"realm_access.roles","jsonType.label":"String",
           "access.token.claim":"true","id.token.claim":"true","userinfo.token.claim":"true"}}'

fail=0
for c in impilo-mobile-citizen impilo-mobile-provider; do
  CID="$(curl -sS --max-time 15 -H "Authorization: Bearer $TOK" "$KC/admin/realms/impilo/clients?clientId=$c" 2>/dev/null \
    | python3 -c 'import json,sys;d=json.load(sys.stdin);print(d[0]["id"] if d else "")')"
  if [ -z "$CID" ]; then printf 'FAIL  %-24s client not found in realm\n' "$c"; fail=1; continue; fi

  if curl -sS --max-time 15 -H "Authorization: Bearer $TOK" \
       "$KC/admin/realms/impilo/clients/$CID/protocol-mappers/models" 2>/dev/null \
       | grep -q '"realm roles"'; then
    printf 'OK    %-24s realm-roles mapper already present\n' "$c"
    continue
  fi

  code="$(curl -sS --max-time 15 -o /dev/null -w '%{http_code}' -X POST \
    "$KC/admin/realms/impilo/clients/$CID/protocol-mappers/models" \
    -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" -d "$MAPPER")"
  case "$code" in
    201) printf 'OK    %-24s realm-roles mapper added\n' "$c" ;;
    409) printf 'OK    %-24s mapper already exists (409)\n' "$c" ;;
    *)   printf 'FAIL  %-24s HTTP %s adding mapper\n' "$c" "$code"; fail=1 ;;
  esac
done

[ "$fail" -ne 0 ] && { echo "One or more mobile clients could not be updated." >&2; exit 1; }
echo "PASS: mobile clients emit realm_access.roles."
echo "Verify with a real login — the token should now carry the user's roles:"
echo "  a clinician must resolve to PROVIDER, not CITIZEN."
exit 0
