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
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
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
import base64, json, os, subprocess, sys, urllib.parse, urllib.request

URL = os.environ["KEYCLOAK_URL"].rstrip("/")
REALM = os.environ["REALM_NAME"]
REALM_JSON = os.environ["REALM_JSON"]
NAMESPACE = os.environ["NAMESPACE"]
SECRET = os.environ["SECRET"]
DRY = os.environ.get("DRY_RUN") == "1"

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

# Prefer env creds (compose/bootstrap path); fall back to the k8s secret (estate path).
admin_user = os.environ.get("KEYCLOAK_ADMIN") or k8s_secret("keycloak-admin-user")
admin_pw = os.environ.get("KEYCLOAK_ADMIN_PASSWORD") or k8s_secret("keycloak-admin-password")
_, tok = req("POST", "/realms/master/protocol/openid-connect/token", form={
    "grant_type": "password", "client_id": "admin-cli",
    "username": admin_user, "password": admin_pw})
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
_, basic_scopes = req("GET", f"/admin/realms/{REALM}/client-scopes", token)
basic_id = next((s["id"] for s in (basic_scopes or []) if s.get("name") == "basic"), None)
if basic_id:
    for client_name in ("experience-ui", "impilo-backend", "integration-test"):
        _, cl = req("GET", f"/admin/realms/{REALM}/clients?clientId={urllib.parse.quote(client_name)}", token)
        client = (cl or [None])[0]
        if not client:
            continue
        if "basic" not in (client.get("defaultClientScopes") or []):
            if DRY:
                print(f"client {client_name}: would add default scope 'basic' (dry-run)")
            else:
                req("PUT", f"/admin/realms/{REALM}/clients/{client['id']}/default-client-scopes/{basic_id}", token)
                print(f"client {client_name}: default scope 'basic' added (sub claim restored)")

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
            req("PUT", f"/admin/realms/{REALM}/users/{uid}/reset-password", token, body={
                "type": "password", "value": password, "temporary": False})
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
