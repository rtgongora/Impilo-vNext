#!/usr/bin/env bash
# =============================================================================
# Provision the governed synthetic preview-only CITIZEN test identity.
#
# Authorised by Product Owner for Checkpoint 3 authenticated runtime proof only.
# Creates (idempotently) a Keycloak user with CITIZEN role and no other authority,
# stores the generated credential in a dedicated Kubernetes Secret that no
# application workload mounts, and never prints the credential.
#
# Usage:
#   bash scripts/operator/provision-preview-test-citizen.sh
#   bash scripts/operator/provision-preview-test-citizen.sh --dry-run
#
# Environment:
#   KEYCLOAK_NAMESPACE   default: impilo-full-preview
#   APP_SECRETS          default: impilo-app-secrets (admin/reconciler credentials)
#   TEST_SECRET          default: impilo-preview-test-identity (dedicated store)
#   TEST_USERNAME        default: preview.test.citizen
# =============================================================================
set -euo pipefail

REPO_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_PATH"

NAMESPACE="${KEYCLOAK_NAMESPACE:-impilo-full-preview}"
APP_SECRETS="${APP_SECRETS:-impilo-app-secrets}"
TEST_SECRET="${TEST_SECRET:-impilo-preview-test-identity}"
TEST_USERNAME="${TEST_USERNAME:-preview.test.citizen}"
DRY_RUN=0
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=1

if [[ -z "${KEYCLOAK_URL:-}" ]]; then
  KC_IP="$(kubectl get svc keycloak -n "$NAMESPACE" -o jsonpath='{.spec.clusterIP}')"
  KEYCLOAK_URL="http://${KC_IP}:8080"
fi

export KEYCLOAK_URL NAMESPACE APP_SECRETS TEST_SECRET TEST_USERNAME DRY_RUN REPO_PATH

python3 - <<'PY'
import base64, json, os, secrets, string, subprocess, sys, urllib.parse, urllib.request

URL = os.environ["KEYCLOAK_URL"].rstrip("/")
NS = os.environ["NAMESPACE"]
APP_SECRETS = os.environ["APP_SECRETS"]
TEST_SECRET = os.environ["TEST_SECRET"]
USERNAME = os.environ["TEST_USERNAME"]
DRY = os.environ.get("DRY_RUN") == "1"
REALM = "impilo"

def k8s_secret_key(secret, key):
    out = subprocess.check_output([
        "kubectl", "get", "secret", secret, "-n", NS,
        "-o", f"jsonpath={{.data.{key}}}"], stderr=subprocess.DEVNULL)
    if not out:
        raise RuntimeError(f"missing secret key {secret}/{key}")
    return base64.b64decode(out).decode()

def req(method, path, token=None, body=None, form=None, ok=(200, 201, 204)):
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
        with urllib.request.urlopen(r, timeout=30) as resp:
            raw = resp.read()
            return resp.status, json.loads(raw) if raw else None, dict(resp.headers.items())
    except urllib.error.HTTPError as e:
        raw = e.read()
        if e.code in ok:
            return e.code, (json.loads(raw) if raw else None), dict(e.headers.items())
        text = raw.decode(errors="replace")[:300]
        raise RuntimeError(f"{method} {path} -> {e.code}: {text}") from None

def mint_admin_token():
    """Prefer least-privilege user-admin client; fall back to reconciler, then master admin."""
    attempts = [
        ("impilo", "impilo-user-admin", "keycloak-user-admin-secret"),
        ("impilo", "impilo-realm-reconciler", "keycloak-reconciler-secret"),
    ]
    for realm, client_id, secret_key in attempts:
        try:
            secret = k8s_secret_key(APP_SECRETS, secret_key)
            _, tok, _ = req("POST", f"/realms/{realm}/protocol/openid-connect/token", form={
                "grant_type": "client_credentials",
                "client_id": client_id,
                "client_secret": secret,
            })
            print(f"admin_auth: client_credentials {client_id}@{realm}")
            return tok["access_token"]
        except Exception as exc:
            print(f"admin_auth: {client_id} unavailable ({type(exc).__name__})")
    admin_user = k8s_secret_key(APP_SECRETS, "keycloak-admin-user")
    admin_pw = k8s_secret_key(APP_SECRETS, "keycloak-admin-password")
    _, tok, _ = req("POST", "/realms/master/protocol/openid-connect/token", form={
        "grant_type": "password", "client_id": "admin-cli",
        "username": admin_user, "password": admin_pw,
    })
    print("admin_auth: master admin-cli password grant")
    return tok["access_token"]

def generate_password(length=32):
    alphabet = string.ascii_letters + string.digits + "!@#%^*_+-="
    # Ensure composition requirements without embedding dictionary words.
    parts = [
        secrets.choice(string.ascii_uppercase),
        secrets.choice(string.ascii_lowercase),
        secrets.choice(string.digits),
        secrets.choice("!@#%^*_+-="),
    ]
    parts += [secrets.choice(alphabet) for _ in range(length - 4)]
    secrets.SystemRandom().shuffle(parts)
    return "".join(parts)

def find_user(token, username):
    _, users, _ = req(
        "GET",
        f"/admin/realms/{REALM}/users?username={urllib.parse.quote(username)}&exact=true",
        token,
    )
    return (users or [None])[0]

def user_realm_role_list(token, user_id):
    _, roles, _ = req("GET", f"/admin/realms/{REALM}/users/{user_id}/role-mappings/realm", token)
    return roles or []

def user_roles(token, user_id):
    return {r["name"] for r in user_realm_role_list(token, user_id)}

def resolve_citizen_role(token, user_id):
    """Resolve CITIZEN via the user-scoped role-mapping APIs.

    The least-privilege impilo-user-admin client has manage-users but not
    view-realm, so GET /roles/CITIZEN returns 403. Available/assigned role
    mappings on a user are readable and sufficient.
    """
    for role in user_realm_role_list(token, user_id):
        if role.get("name") == "CITIZEN":
            print("role CITIZEN: PRESENT (already mapped)")
            return role
    _, available, _ = req(
        "GET",
        f"/admin/realms/{REALM}/users/{user_id}/role-mappings/realm/available?max=200",
        token,
    )
    for role in (available or []):
        if role.get("name") == "CITIZEN":
            print("role CITIZEN: PRESENT (available)")
            return role
    raise RuntimeError("required realm role 'CITIZEN' is absent — refuse to invent it")

token = mint_admin_token()

existing = find_user(token, USERNAME)
created = False
if existing is None:
    body = {
        "username": USERNAME,
        "enabled": True,
        "emailVerified": True,
        "email": f"{USERNAME}@preview.impilo.local",
        "firstName": "Preview",
        "lastName": "TestCitizen",
        "attributes": {
            "impilo.identity.kind": ["SYNTHETIC_PREVIEW_TEST"],
            "impilo.identity.purpose": ["authenticated-runtime-proof"],
            "impilo.identity.environment": ["preview"],
            "impilo.identity.no_real_person": ["true"],
            "impilo.identity.no_clinical_record": ["true"],
        },
        "requiredActions": [],
    }
    if DRY:
        print(f"user {USERNAME}: would CREATE (dry-run)")
        user_id = "dry-run"
    else:
        status, _, headers = req("POST", f"/admin/realms/{REALM}/users", token, body=body, ok=(201, 409))
        if status == 409:
            existing = find_user(token, USERNAME)
            user_id = existing["id"]
            print(f"user {USERNAME}: EXISTS (race)")
        else:
            loc = headers.get("Location") or headers.get("location") or ""
            user_id = loc.rstrip("/").split("/")[-1]
            created = True
            print(f"user {USERNAME}: CREATE id={user_id}")
else:
    user_id = existing["id"]
    print(f"user {USERNAME}: EXISTS id={user_id} enabled={existing.get('enabled')}")
    # Keep attributes/authority constrained on every run.
    if not DRY:
        patch = {
            "enabled": True,
            "emailVerified": True,
            "email": f"{USERNAME}@preview.impilo.local",
            "firstName": "Preview",
            "lastName": "TestCitizen",
            "attributes": {
                "impilo.identity.kind": ["SYNTHETIC_PREVIEW_TEST"],
                "impilo.identity.purpose": ["authenticated-runtime-proof"],
                "impilo.identity.environment": ["preview"],
                "impilo.identity.no_real_person": ["true"],
                "impilo.identity.no_clinical_record": ["true"],
            },
            "requiredActions": [],
        }
        req("PUT", f"/admin/realms/{REALM}/users/{user_id}", token, body={**existing, **patch, "id": user_id})
        print(f"user {USERNAME}: attributes reconciled")

if not DRY and user_id != "dry-run":
    citizen_role = resolve_citizen_role(token, user_id)
    have = user_roles(token, user_id)
    # Strip every realm role except CITIZEN (and Keycloak's default-roles-* which is unavoidable).
    to_remove = [
        r for r in user_realm_role_list(token, user_id)
        if r["name"] != "CITIZEN" and not r["name"].startswith("default-roles-")
    ]
    if to_remove:
        req("DELETE", f"/admin/realms/{REALM}/users/{user_id}/role-mappings/realm", token, body=to_remove, ok=(204, 200))
        print(f"user {USERNAME}: removed non-citizen roles={[r['name'] for r in to_remove]}")
    if "CITIZEN" not in have:
        req("POST", f"/admin/realms/{REALM}/users/{user_id}/role-mappings/realm", token, body=[citizen_role])
        print(f"user {USERNAME}: granted CITIZEN")
    else:
        print(f"user {USERNAME}: CITIZEN already mapped")

def write_test_secret(password_value):
    """Create-or-replace the dedicated Secret WITHOUT kubectl apply.

    `kubectl apply` writes stringData into kubectl.kubernetes.io/last-applied-configuration,
    which would leave the password in plaintext annotation history. We use
    `kubectl create` / `kubectl replace` against a base64 data manifest instead.
    """
    import tempfile
    payload = {
        "apiVersion": "v1",
        "kind": "Secret",
        "metadata": {
            "name": TEST_SECRET,
            "namespace": NS,
            "labels": {
                "app.kubernetes.io/part-of": "impilo-trust",
                "impilo.mohcc.gov.zw/secret-class": "preview-test-identity",
                "impilo.mohcc.gov.zw/environment": "preview",
            },
            "annotations": {
                "impilo.mohcc.gov.zw/purpose": "authenticated-runtime-proof",
                "impilo.mohcc.gov.zw/owner": "product-owner+security-owner",
                "impilo.mohcc.gov.zw/identity": USERNAME,
                "impilo.mohcc.gov.zw/no-workload-mount": "true",
            },
        },
        "type": "Opaque",
        "data": {
            k: base64.b64encode(v.encode()).decode()
            for k, v in {
                "username": USERNAME,
                "password": password_value,
                "realm": REALM,
                "client_id_web": "experience-ui",
                "client_id_mobile_citizen": "impilo-mobile-citizen",
                "purpose": "Checkpoint 3 authenticated browser + Redroid runtime proof",
                "environment": "preview",
                "identity_kind": "SYNTHETIC_PREVIEW_TEST",
            }.items()
        },
    }
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as fh:
        json.dump(payload, fh)
        path = fh.name
    try:
        exists = subprocess.call(
            ["kubectl", "get", "secret", TEST_SECRET, "-n", NS],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) == 0
        if exists:
            subprocess.check_call(["kubectl", "replace", "-f", path], stdout=subprocess.DEVNULL)
        else:
            subprocess.check_call(["kubectl", "create", "-f", path], stdout=subprocess.DEVNULL)
    finally:
        os.unlink(path)

# Credential: reuse existing secret if present (idempotent, no rotation on re-run);
# otherwise generate and store. Never print.
password = None
minted_new_password = False
secret_exists = subprocess.call(
    ["kubectl", "get", "secret", TEST_SECRET, "-n", NS],
    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL) == 0
if secret_exists:
    try:
        stored_user = k8s_secret_key(TEST_SECRET, "username")
        stored_pw = k8s_secret_key(TEST_SECRET, "password")
        if stored_user == USERNAME and stored_pw:
            password = stored_pw
            print(f"secret {TEST_SECRET}: REUSE existing credential for {USERNAME}")
    except Exception:
        password = None

if password is None:
    password = generate_password()
    minted_new_password = True
    if DRY:
        print(f"secret {TEST_SECRET}: would CREATE/UPDATE credential (dry-run; value not shown)")
    else:
        write_test_secret(password)
        print(f"secret {TEST_SECRET}: STORED (username + password; value not shown)")

if not DRY and user_id != "dry-run":
    if minted_new_password or created:
        req("PUT", f"/admin/realms/{REALM}/users/{user_id}/reset-password", token, body={
            "type": "password",
            "value": password,
            "temporary": False,
        })
        print(f"user {USERNAME}: password set (temporary=false)")
    else:
        print(f"user {USERNAME}: password unchanged (secret reused)")

    # Final authority check — report non-secret metadata only.
    final = find_user(token, USERNAME)
    roles = sorted(user_roles(token, user_id))
    print("METADATA_BEGIN")
    print(json.dumps({
        "username": USERNAME,
        "user_id": user_id,
        "enabled": final.get("enabled"),
        "email": final.get("email"),
        "realm_roles": roles,
        "attributes": {k: v for k, v in (final.get("attributes") or {}).items()},
        "required_actions": final.get("requiredActions") or [],
        "secret_name": TEST_SECRET,
        "secret_namespace": NS,
        "created_this_run": created,
        "password_minted_this_run": minted_new_password,
    }, indent=2))
    print("METADATA_END")

print("PROVISION_OK")
PY
