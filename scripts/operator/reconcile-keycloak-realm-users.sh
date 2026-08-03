#!/usr/bin/env bash
# Reconcile seeded realm users from tools/auth/impilo-realm.json into a RUNNING Keycloak.
#
# Keycloak only imports the realm file into an EMPTY database, so seeded users added
# after the estate's first boot never materialise (login: "Invalid credentials").
# This script idempotently upserts each seeded user: create-if-missing, reset the
# seeded password, ensure realm roles exist and are mapped.
#
# Never prints secrets. Defaults target the k3s full-preview estate.
#
# Usage:
#   bash scripts/operator/reconcile-keycloak-realm-users.sh [--dry-run]
#
# Environment overrides:
#   KEYCLOAK_URL          (default: http://<clusterIP of svc/keycloak in $NAMESPACE>:8080)
#   KEYCLOAK_NAMESPACE    (default: impilo-full-preview)
#   KEYCLOAK_SECRET       (default: impilo-app-secrets; keys keycloak-admin-user/-password)
#   REALM_NAME            (default: impilo)
#   REALM_JSON            (default: tools/auth/impilo-realm.json)
set -euo pipefail
REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

NAMESPACE="${KEYCLOAK_NAMESPACE:-impilo-full-preview}"
SECRET="${KEYCLOAK_SECRET:-impilo-app-secrets}"
REALM_NAME="${REALM_NAME:-impilo}"
REALM_JSON="${REALM_JSON:-tools/auth/impilo-realm.json}"
DRY_RUN=0
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=1

if [[ -z "${KEYCLOAK_URL:-}" ]]; then
  KC_IP="$(kubectl get svc keycloak -n "$NAMESPACE" -o jsonpath='{.spec.clusterIP}')"
  KEYCLOAK_URL="http://${KC_IP}:8080"
fi

export KEYCLOAK_URL REALM_NAME REALM_JSON NAMESPACE SECRET DRY_RUN
python3 - <<'PY'
import base64, json, os, subprocess, sys, urllib.error, urllib.parse, urllib.request

URL = os.environ["KEYCLOAK_URL"].rstrip("/")
REALM = os.environ["REALM_NAME"]
REALM_JSON = os.environ["REALM_JSON"]
NAMESPACE = os.environ["NAMESPACE"]
SECRET = os.environ["SECRET"]
DRY = os.environ.get("DRY_RUN") == "1"

class _DrySkip(Exception):
    pass

def k8s_secret(key):
    out = subprocess.check_output([
        "kubectl", "get", "secret", SECRET, "-n", NAMESPACE,
        "-o", f"jsonpath={{.data.{key}}}"])
    return base64.b64decode(out).decode()

def req(method, path, token=None, body=None, form=None, ok=(200, 201, 204, 409)):
    url = path if path.startswith("http") else URL + path
    data = None
    headers = {}
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=20) as resp:
            raw = resp.read()
            return resp.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        if e.code in ok:
            return e.code, None
        body_text = e.read().decode(errors="replace")
        e.body_text = body_text
        print(f"FAIL {method} {path} -> {e.code} {body_text[:200]!r}", file=sys.stderr)
        raise

# Authenticate as a SERVICE ACCOUNT via client_credentials.
#
# This previously did a password grant against realms/master. That realm has ZERO users on
# this estate (KC_BOOTSTRAP_ADMIN_* only seeds when no admin exists, so it never fired against
# the pre-existing DB), so this script — the persona reseeder added after the 2026-07-18 incident
# where a Keycloak reset lost every persona — could never actually run. It sits behind
# `|| echo WARN` in full-boot-preview-deploy.sh, so it failed into a warning every boot.
#
# impilo-realm-reconciler carries exactly the rights used below and no more:
#   manage-users   -> personas, reset-password, realm role-mappings
#   manage-realm   -> realm roles, users/profile unmanagedAttributePolicy
#   manage-clients -> the `basic` default-scope repair (restores the `sub` claim)
# Verified against the live admin API: GET client-scopes / users / roles all 200.
admin_client = os.environ.get("KEYCLOAK_ADMIN_CLIENT_ID", "impilo-realm-reconciler")
admin_secret = os.environ.get("KEYCLOAK_ADMIN_CLIENT_SECRET") or k8s_secret("keycloak-reconciler-secret")
_, tok = req("POST", f"/realms/{REALM}/protocol/openid-connect/token", form={
    "grant_type": "client_credentials", "client_id": admin_client,
    "client_secret": admin_secret})
token = tok["access_token"]

realm = json.load(open(REALM_JSON))
seeded_roles = {r["name"] for r in realm.get("roles", {}).get("realm", [])}

# Ensure realm roles exist.
_, existing_roles = req("GET", f"/admin/realms/{REALM}/roles?max=500", token)
have_roles = {r["name"] for r in (existing_roles or [])}
for role in sorted(seeded_roles - have_roles):
    print(f"role {role}: CREATE" + (" (dry-run)" if DRY else ""))
    if not DRY:
        req("POST", f"/admin/realms/{REALM}/roles", token, body={"name": role})

# --- Realm preconditions for identity-anchor claims -------------------------
# 1) Keycloak 24+ declarative user profile drops "unmanaged" attributes silently
#    unless the policy allows them. Our identity anchors (health_id, provider_id,
#    facility_id, tenant_id, cpid) are unmanaged — enable ADMIN_EDIT so admin API
#    writes stick but end users cannot edit their own anchors.
_, prof = req("GET", f"/admin/realms/{REALM}/users/profile", token)
if prof is not None and prof.get("unmanagedAttributePolicy") not in ("ENABLED", "ADMIN_EDIT"):
    if DRY:
        print("users/profile: would set unmanagedAttributePolicy=ADMIN_EDIT (dry-run)")
    else:
        prof["unmanagedAttributePolicy"] = "ADMIN_EDIT"
        req("PUT", f"/admin/realms/{REALM}/users/profile", token, body=prof)
        print("users/profile: unmanagedAttributePolicy=ADMIN_EDIT")

# 2) Keycloak 25 moved the `sub` claim into the `basic` client scope. Clients
#    imported from older realm exports lack it, producing sub-less access tokens
#    (the BFF then mints a random person anchor per login). Ensure each browser/
#    backend client carries `basic` as a default scope.
#    This repair must DEGRADE, never abort: it runs before the persona loop below, so
#    a credential lacking client rights would otherwise kill the reseed at the top.
#    Tolerate ONLY 403 — a 404/5xx/transport failure is a different condition and must
#    still raise. Never exit non-zero: the caller (full-boot-preview-deploy.sh:637)
#    swallows failure behind `|| echo WARN`, so a non-zero exit here is indistinguishable
#    from the dead state this script is climbing out of. Hence the greppable marker.
SUB_MARKER = "UNRESOLVED_SUB_CLAIM"
basic_scopes, skip_reason = None, None
try:
    _, basic_scopes = req("GET", f"/admin/realms/{REALM}/client-scopes", token)
except urllib.error.HTTPError as e:
    if e.code != 403:
        raise
    skip_reason = "credential lacks client rights (403 on client-scopes)"

# Mirrors verbatim what Keycloak itself creates for a current realm — read off this
# estate's own `master` realm, which has `basic` because Keycloak created it there.
# `sub` is the Keycloak user UUID: opaque, never a national identifier.
# Two client scopes Keycloak creates for a current realm but which this realm — imported
# from an older export — never had. Both were measured absent, and each breaks a distinct
# thing. Definitions mirror this estate's own `master` realm verbatim.
#
#   basic -> `sub`. Without it jwt.getSubject() is null and every session carries
#            user.id=null: the person anchor is anonymous on every successful login.
#   acr   -> `acr`. Without it the token has NO acr claim, so the BFF's
#            OidcSessionService.aalRank() scores it 0 and every AAL2 (work/clinical)
#            login is refused with OIDC_AAL_NOT_SATISFIED — even though Keycloak DID
#            perform the OTP step-up. The realm's `AuthnContextClassRef` scope is a
#            SAML mapper and does not emit it.
REQUIRED_SCOPES = [
    {
        "name": "basic", "protocol": "openid-connect",
        "description": "OpenID Connect scope for the sub and auth_time claims",
        "attributes": {"include.in.token.scope": "false", "display.on.consent.screen": "false"},
        "protocolMappers": [
            {"name": "sub", "protocol": "openid-connect", "protocolMapper": "oidc-sub-mapper",
             "config": {"access.token.claim": "true", "introspection.token.claim": "true"}},
            {"name": "auth_time", "protocol": "openid-connect",
             "protocolMapper": "oidc-usersessionmodel-note-mapper",
             "config": {"user.session.note": "AUTH_TIME", "claim.name": "auth_time",
                        "jsonType.label": "long", "access.token.claim": "true",
                        "id.token.claim": "true", "introspection.token.claim": "true"}},
        ],
    },
    {
        "name": "acr", "protocol": "openid-connect",
        "description": "OpenID Connect scope for the acr claim (authentication assurance level)",
        "attributes": {"include.in.token.scope": "false", "display.on.consent.screen": "false"},
        "protocolMappers": [
            {"name": "acr loa level", "protocol": "openid-connect",
             "protocolMapper": "oidc-acr-mapper",
             "config": {"access.token.claim": "true", "id.token.claim": "true",
                        "introspection.token.claim": "true"}},
        ],
    },
]

def ensure_scope(spec, existing):
    """Create the scope if missing, attach it to every OIDC client, and add it to the
    realm defaults. Returns a skip reason string, or None on success."""
    name = spec["name"]
    sid = next((x["id"] for x in (existing or []) if x.get("name") == name), None)
    if sid is None:
        if DRY:
            return f"realm has no '{name}' client scope; would CREATE it and attach (dry-run)"
        try:
            req("POST", f"/admin/realms/{REALM}/client-scopes", token, body=spec)
            _, refreshed = req("GET", f"/admin/realms/{REALM}/client-scopes", token)
            sid = next((x["id"] for x in (refreshed or []) if x.get("name") == name), None)
            if sid is None:
                return f"created '{name}' client scope but could not resolve its id afterwards"
            print(f"client-scope '{name}': CREATED")
        except urllib.error.HTTPError as e:
            if e.code != 403:
                raise
            return f"credential lacks client rights (403 creating the '{name}' scope)"

    _, all_clients = req("GET", f"/admin/realms/{REALM}/clients", token)
    for client in (all_clients or []):
        if client.get("protocol") not in (None, "openid-connect"):
            continue
        if client.get("bearerOnly"):
            # Validates tokens, never mints them; Keycloak also refuses writes to its own
            # realm-management client, and that 403 previously killed the whole loop.
            continue
        cname = client.get("clientId")
        if name in (client.get("defaultClientScopes") or []):
            continue
        if DRY:
            print(f"client {cname}: would add default scope '{name}' (dry-run)")
            continue
        try:
            req("PUT", f"/admin/realms/{REALM}/clients/{client['id']}/default-client-scopes/{sid}", token)
            print(f"client {cname}: default scope '{name}' added")
        except urllib.error.HTTPError as e:
            if e.code != 403:
                raise
            print(f"WARN {SUB_MARKER}: client {cname}: 403 attaching '{name}' — continuing")

    # Realm-level default, so clients created LATER inherit it. Without this the repair
    # fixes today's clients and leaves the class.
    _, realm_defaults = req("GET", f"/admin/realms/{REALM}/default-default-client-scopes", token)
    if not any(sc.get("name") == name for sc in (realm_defaults or [])):
        if DRY:
            print(f"realm: would add '{name}' to default-client-scopes (dry-run)")
        else:
            req("PUT", f"/admin/realms/{REALM}/default-default-client-scopes/{sid}", token)
            print(f"realm: '{name}' added to default-client-scopes")
    return None


if skip_reason is None:
    for _spec in REQUIRED_SCOPES:
        _why = ensure_scope(_spec, basic_scopes)
        if _why:
            print(f"WARN {SUB_MARKER}: {_spec['name']}-scope repair skipped — {_why}")
            print(f"WARN {SUB_MARKER}: tokens remain incomplete — a missing 'basic' means "
                  f"user.id=null; a missing 'acr' means every AAL2 login is refused")
        # refresh the scope list so a scope created above is visible to the next iteration
        _, basic_scopes = req("GET", f"/admin/realms/{REALM}/client-scopes", token)
else:
    print(f"WARN {SUB_MARKER}: client-scope repair skipped — {skip_reason}")
    print(f"WARN {SUB_MARKER}: access tokens remain sub-less and acr-less; user.id=null "
          f"and AAL2 logins are refused")

created = updated = skipped = 0
for u in realm.get("users", []):
    username = u.get("username")
    if not username or username.startswith("service-account-"):
        continue
    creds = u.get("credentials") or []
    password = creds[0].get("value") if creds else None
    _, found = req("GET", f"/admin/realms/{REALM}/users?username={urllib.parse.quote(username)}&exact=true", token)
    user = next((x for x in (found or []) if x.get("username") == username), None)
    if user is None:
        print(f"user {username}: CREATE" + (" (dry-run)" if DRY else ""))
        created += 1
        if DRY:
            continue
        req("POST", f"/admin/realms/{REALM}/users", token, body={
            "username": username,
            "email": u.get("email"),
            "firstName": u.get("firstName"),
            "lastName": u.get("lastName"),
            "enabled": u.get("enabled", True),
            "emailVerified": True,
            "attributes": u.get("attributes", {}),
        })
        _, found = req("GET", f"/admin/realms/{REALM}/users?username={urllib.parse.quote(username)}&exact=true", token)
        user = next((x for x in (found or []) if x.get("username") == username), None)
    else:
        updated += 1
        print(f"user {username}: exists — reconciling password/roles/attributes" + (" (dry-run)" if DRY else ""))
    if DRY or user is None:
        continue
    uid = user["id"]
    # Attribute sync: seeded identity anchors (health_id, provider_id, facility_id,
    # tenant_id) feed JWT claims via the impilo-trust-headers scope mappers. Users
    # created before this script existed have empty attributes — merge, seed wins.
    want_attrs = u.get("attributes") or {}
    have_attrs = user.get("attributes") or {}
    if any(have_attrs.get(k) != v for k, v in want_attrs.items()):
        merged = dict(have_attrs)
        merged.update(want_attrs)
        if DRY:
            print(f"  would sync attributes: {sorted(want_attrs.keys())} (dry-run)")
        else:
            req("PUT", f"/admin/realms/{REALM}/users/{uid}", token, body={
                "username": username,
                "email": u.get("email") or user.get("email"),
                "firstName": u.get("firstName") or user.get("firstName"),
                "lastName": u.get("lastName") or user.get("lastName"),
                "enabled": user.get("enabled", True),
                "emailVerified": True,
                "attributes": merged,
            })
            print(f"  attributes synced: {sorted(want_attrs.keys())}")
    if password:
        try:
            if DRY:
                print(f"  would reset password (dry-run)")
                raise _DrySkip()
            req("PUT", f"/admin/realms/{REALM}/users/{uid}/reset-password", token, body={
                "type": "password", "value": password, "temporary": False})
        except _DrySkip:
            pass
        except Exception as e:
            # Password-history rejection means the seeded password is already
            # current — that is the reconciled state, not a failure.
            if "PasswordHistory" in getattr(e, "body_text", ""):
                print(f"  password already current (history policy)")
            else:
                print(f"  WARN password reset failed for {username}: {e}", file=sys.stderr)
    want = set(u.get("realmRoles", []))
    if want:
        _, current = req("GET", f"/admin/realms/{REALM}/users/{uid}/role-mappings/realm", token)
        have = {r["name"] for r in (current or [])}
        missing = want - have
        reps = []
        for role in sorted(missing):
            status, rep = req("GET", f"/admin/realms/{REALM}/roles/{urllib.parse.quote(role)}", token)
            if rep:
                reps.append({"id": rep["id"], "name": rep["name"]})
        if reps:
            req("POST", f"/admin/realms/{REALM}/users/{uid}/role-mappings/realm", token, body=reps)
            print(f"  roles += {sorted(r['name'] for r in reps)}")

print(f"SUMMARY created={created} reconciled={updated}")
PY
