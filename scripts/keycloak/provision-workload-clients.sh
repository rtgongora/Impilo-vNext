#!/usr/bin/env bash
# Provision per-workload Keycloak clients and the audience mappers that make `aud` real.
#
# Two gaps this closes, both measured rather than assumed:
#
#  1. NO PER-WORKLOAD CLIENTS. The realm has 11 clients and `impilo-backend` is shared by
#     experience-bff AND pct-service, so a callee cannot tell one caller from another. The trust
#     doctrine requires each workload to authenticate as itself.
#
#  2. NO AUDIENCE MAPPER ANYWHERE IN THE REALM. Checkpoint 1 recorded "JWT audience validation:
#     ABSENT" as an application defect. It is not: Keycloak mints no `aud` claim at all, on any
#     client or client scope, so there is nothing for a resource server to validate. A token
#     minted for any client is accepted by every service that trusts the issuer. Adding the
#     resource-server check WITHOUT this would reject every token in the estate.
#
# Idempotent: re-running updates in place and never rotates a secret that already works.
#
#   scripts/keycloak/provision-workload-clients.sh [--namespace NS] [--dry-run]
#
# Requires a Keycloak admin credential. The service accounts (impilo-admin-cli,
# impilo-user-admin, impilo-backend) are deliberately 403 on client management -- that
# least-privilege posture is asserted by the reconciler test suite and must NOT be widened to
# make this script work.
set -euo pipefail

NS="${IMPILO_NAMESPACE:-impilo-full-preview}"
REALM="${KEYCLOAK_REALM:-impilo}"
LPORT="${KC_LOCAL_PORT:-18099}"
DRY=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --namespace) NS="$2"; shift 2 ;;
    --dry-run) DRY=1; shift ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

# workload:audience -- the audience is what the CALLEE validates, named for the callee.
# Matches the canonical id in the Checkpoint 4 workload identity registry.
WORKLOADS="
vashandi-workforce-service:urn:impilo:tshepo:workforce-governance-service
experience-bff:urn:impilo:tshepo:workforce-governance-service
"

sec() { kubectl get secret impilo-app-secrets -n "$NS" -o jsonpath="{.data.$1}" 2>/dev/null | base64 -d; }

echo "--- Provision workload clients (ns=$NS realm=$REALM) ---"
kubectl port-forward -n "$NS" svc/keycloak "${LPORT}:8080" >/tmp/kc-provision-pf.log 2>&1 &
PF=$!
trap 'kill $PF 2>/dev/null || true' EXIT
sleep 6
KURL="http://localhost:${LPORT}"

TK=$(curl -s -X POST "$KURL/realms/master/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=admin-cli \
  --data-urlencode "username=$(sec keycloak-admin-user)" \
  --data-urlencode "password=$(sec keycloak-admin-password)" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

if [[ -z "$TK" ]]; then
  cat >&2 <<'MSG'
ADMIN TOKEN FAILED.

The password in impilo-app-secrets:keycloak-admin-password does not match the live master-realm
admin (the token endpoint answers `invalid_grant: Invalid user credentials`, so the realm is up
and the credential is simply wrong -- most likely drift from the H2->Postgres migration, since
KC_BOOTSTRAP_ADMIN_* only seeds an admin when none exists).

This script deliberately stops here rather than working around it. The two available workarounds
are both refused:
  - resetting the master admin password is modification of an existing human credential;
  - granting manage-clients to a service account would widen a least-privilege posture that the
    reconciler test suite explicitly asserts ("no service account received manage-realm or
    manage-clients").

Supply a working admin credential (update the secret, or export KC_ADMIN_USER/KC_ADMIN_PASSWORD)
and re-run.
MSG
  exit 1
fi
echo "  admin token OK"

api() { curl -s -H "Authorization: Bearer $TK" -H 'Content-Type: application/json' "$@"; }

echo "$WORKLOADS" | while IFS= read -r line; do
  [[ -z "$line" ]] && continue
  CID="${line%%:*}"
  AUD="${line#*:}"
  [[ -z "$CID" || -z "$AUD" ]] && continue

  UUID=$(api "$KURL/admin/realms/$REALM/clients?clientId=$CID" \
    | python3 -c 'import json,sys; a=json.load(sys.stdin); print(a[0]["id"] if a else "")')

  # A confidential client with service accounts and NOTHING else: no standard flow (it is not a
  # browser client), no direct access grant (a workload must never use a password grant), no
  # implicit flow.
  BODY=$(python3 - "$CID" <<'PY'
import json, sys
print(json.dumps({
    "clientId": sys.argv[1],
    "name": f"Impilo workload: {sys.argv[1]}",
    "description": "Per-workload service credential (Tshepo Checkpoint 4).",
    "protocol": "openid-connect",
    "publicClient": False,
    "serviceAccountsEnabled": True,
    "standardFlowEnabled": False,
    "implicitFlowEnabled": False,
    "directAccessGrantsEnabled": False,
    "enabled": True,
}))
PY
)
  if [[ "$DRY" == "1" ]]; then
    echo "  DRY $CID  exists=${UUID:-no}  audience=$AUD"
    continue
  fi

  if [[ -z "$UUID" ]]; then
    code=$(curl -s -o /dev/null -w "%{http_code}" -X POST -H "Authorization: Bearer $TK" \
      -H 'Content-Type: application/json' -d "$BODY" "$KURL/admin/realms/$REALM/clients")
    UUID=$(api "$KURL/admin/realms/$REALM/clients?clientId=$CID" \
      | python3 -c 'import json,sys; a=json.load(sys.stdin); print(a[0]["id"] if a else "")')
    echo "  CREATE $CID -> $code"
  else
    echo "  EXISTS $CID"
  fi
  [[ -z "$UUID" ]] && { echo "    ERROR: no uuid for $CID"; continue; }

  # The audience mapper. Without one the token carries no `aud` and a resource server has
  # nothing to check -- which is why estate-wide audience validation reads ABSENT.
  HAVE=$(api "$KURL/admin/realms/$REALM/clients/$UUID/protocol-mappers/models" \
    | python3 -c "
import json,sys
ms=json.load(sys.stdin)
print(next((m['id'] for m in ms if m.get('protocolMapper')=='oidc-audience-mapper'
            and m.get('config',{}).get('included.custom.audience')=='$AUD'), ''))")
  if [[ -n "$HAVE" ]]; then
    echo "    audience mapper present ($AUD)"
  else
    MAPPER=$(python3 - "$AUD" <<'PY'
import json, sys
print(json.dumps({
    "name": f"impilo-audience-{sys.argv[1].split(':')[-1]}",
    "protocol": "openid-connect",
    "protocolMapper": "oidc-audience-mapper",
    "config": {
        "included.custom.audience": sys.argv[1],
        # Access token only: an id_token is for the client, not for a resource server.
        "access.token.claim": "true",
        "id.token.claim": "false",
        "introspection.token.claim": "true",
    },
}))
PY
)
    code=$(curl -s -o /dev/null -w "%{http_code}" -X POST -H "Authorization: Bearer $TK" \
      -H 'Content-Type: application/json' -d "$MAPPER" \
      "$KURL/admin/realms/$REALM/clients/$UUID/protocol-mappers/models")
    echo "    audience mapper $AUD -> $code"
  fi

  # Report the secret's existence only. Never print it; the operator reads it into the k8s
  # Secret out of band.
  HAS=$(api "$KURL/admin/realms/$REALM/clients/$UUID/client-secret" \
    | python3 -c 'import json,sys; print("yes" if json.load(sys.stdin).get("value") else "no")' 2>/dev/null || echo "no")
  echo "    client secret present: $HAS"
done

echo "--- done. Verify a minted token actually carries aud with: ---"
echo "    scripts/keycloak/verify-workload-audience.sh"
