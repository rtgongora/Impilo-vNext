#!/usr/bin/env bash
# =============================================================================
# Give the BFF service account the scoped ndila read role it needs.
#
# WHY: experience-bff now mints its own client_credentials token for
# service-originated calls (preview==prod auth wave, stage 2 slice 1). The
# public find-care lane is anonymous, so there is no inbound user token to
# forward and the BFF calls ndila with its OWN credential. That service account
# held ZERO realm roles, so ndila's SecurityConfig (hasAnyRole(NDILA_VIEWER_ROLES))
# rejected it and distance-matrix 401'd — the BFF then fell back to haversine and
# served a null ETA forever. A failure indistinguishable from "no data".
#
# WHY NDILA_VIEWER AND NOT AN EXISTING ROLE: ndila accepts several realm roles
# for its viewer tier, but every one that already exists in this realm is wider
# than the need — SYSTEM_ADMIN/DEVELOPER unlock ndila admin, tracking, delete and
# the HPA import; DISPATCH_COORDINATOR also carries editor + live-tracking;
# PUBLIC_HEALTH_OFFICER also carries intelligence + AI context packets; CITIZEN is
# a person role every other service authorizes on, so granting it to a service
# account would silently widen the BFF estate-wide. NDILA_VIEWER is referenced by
# ndila-service and nothing else, so it grants exactly one service's read tier.
#
# THE SECOND HALF — THE MAPPER: granting the role is not enough. This realm has no
# built-in "roles" client scope, and impilo-backend carried only session-note
# mappers plus the client-roles mapper added by grant-backend-admin-token-scope.sh.
# Nothing emitted realm_access.roles, so the token came back WITHOUT it and the
# grant was inert — ndila reads realm_access.roles. Verified against a live token
# before and after. The realm-roles mapper below is what makes the grant real.
#
# Spring prefix note: ndila's converter emits ROLE_ + <realm role name>, and
# hasAnyRole("NDILA_VIEWER") requires authority ROLE_NDILA_VIEWER. So the Keycloak
# role is named NDILA_VIEWER with NO ROLE_ prefix. Do not add one.
#
# Targeted kcadm only — NEVER a realm import. A realm re-import caused a 6-day
# total login outage on this estate (2026-07-20 → 26). Realm import is also
# first-import-only, so files/realm-impilo-preview.json (which now declares the
# role, the grant and the mapper) only helps a FRESH realm; an existing realm
# needs this script.
#
# Idempotent: every step skips if already present.
# =============================================================================
set -euo pipefail

NS="${NS:-impilo-full-preview}"
REALM="${REALM:-impilo}"
ROLE="${ROLE:-NDILA_VIEWER}"
SA="${SA:-service-account-impilo-backend}"
CLIENT="${CLIENT:-impilo-backend}"

POD=$(kubectl get pods -n "$NS" -l app=keycloak -o name | head -1)
[ -z "$POD" ] && { echo "keycloak pod not found in $NS"; exit 1; }
POD="${POD#pod/}"

# kcadm runs INSIDE the pod, where the admin credentials already exist as env
# vars — no secret is ever extracted to the operator's shell.
kc() { kubectl exec -n "$NS" "$POD" -- /opt/keycloak/bin/kcadm.sh "$@"; }
kca() {
  kubectl exec -n "$NS" "$POD" -- sh -c \
    '/opt/keycloak/bin/kcadm.sh config credentials --server http://localhost:8080 \
       --realm master --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null 2>&1
     '"$1"
}

echo "--- 1/3: realm role $ROLE ---"
if kca "/opt/keycloak/bin/kcadm.sh get roles/$ROLE -r $REALM >/dev/null 2>&1"; then
  echo "  already exists — skipping"
else
  kca "/opt/keycloak/bin/kcadm.sh create roles -r $REALM -s name=$ROLE \
        -s 'description=Read-only geospatial access to ndila-service (geocode, search, routes, distance-matrix, ETA). Scoped grant for the experience-bff service account; not a person role.'"
  echo "  created"
fi

echo "--- 2/3: grant $ROLE to $SA ---"
if kca "/opt/keycloak/bin/kcadm.sh get-roles -r $REALM --uusername $SA --fields name 2>/dev/null" | grep -q "\"$ROLE\""; then
  echo "  already granted — skipping"
else
  kca "/opt/keycloak/bin/kcadm.sh add-roles -r $REALM --uusername $SA --rolename $ROLE"
  echo "  granted"
fi

echo "--- 3/3: realm-roles protocol mapper on $CLIENT ---"
CID=$(kca "/opt/keycloak/bin/kcadm.sh get clients -r $REALM -q clientId=$CLIENT --fields id --format csv 2>/dev/null" | tr -d '"\r\n')
[ -z "$CID" ] && { echo "  client $CLIENT not found"; exit 1; }
if kca "/opt/keycloak/bin/kcadm.sh get clients/$CID/protocol-mappers/models -r $REALM --fields protocolMapper 2>/dev/null" \
     | grep -q "oidc-usermodel-realm-role-mapper"; then
  echo "  already present — skipping"
else
  kca "/opt/keycloak/bin/kcadm.sh create clients/$CID/protocol-mappers/models -r $REALM \
        -s 'name=realm roles' -s protocol=openid-connect \
        -s protocolMapper=oidc-usermodel-realm-role-mapper -s consentRequired=false \
        -s 'config.\"multivalued\"=true' -s 'config.\"access.token.claim\"=true' \
        -s 'config.\"id.token.claim\"=false' -s 'config.\"userinfo.token.claim\"=false' \
        -s 'config.\"claim.name\"=realm_access.roles' -s 'config.\"jsonType.label\"=String'"
  echo "  created"
fi

# Prove it end-to-end: mint a real service token and read the claim back. A grant
# that does not reach the token is the exact failure this script exists to fix, so
# asserting on the claim (not on the grant) is the only honest verification.
echo "--- verify: realm_access.roles in a live service token ---"
CLAIM=$(kubectl exec -n "$NS" deploy/experience-bff -- sh -c \
  'curl -s -X POST "http://keycloak:8080/realms/'"$REALM"'/protocol/openid-connect/token" \
     -d grant_type=client_credentials -d client_id='"$CLIENT"' \
     --data-urlencode "client_secret=$KEYCLOAK_BACKEND_SECRET" \
   | sed -n "s/.*\"access_token\":\"\([^\"]*\)\".*/\1/p" \
   | cut -d. -f2 | tr "_-" "/+" | base64 -d 2>/dev/null')
echo "  $CLAIM" | tr ',' '\n' | grep -i realm_access || true
case "$CLAIM" in
  *"\"$ROLE\""*) echo "--- OK: $ROLE reaches the token ---" ;;
  *) echo "--- STILL BROKEN: $ROLE absent from realm_access.roles ---"; exit 1 ;;
esac
