#!/usr/bin/env bash
set -Eeuo pipefail

# Idempotent, non-deleting Keycloak MFA desired-state reconciler.
#
# Usage:
#   keycloak-mfa-reconcile.sh plan
#   EXPECTED_CURRENT_HASH=<hash from plan> keycloak-mfa-reconcile.sh apply
#
# Authentication is client-credentials only. The reconciler client is deliberately
# separate from the BFF user-management and audit event-reader service accounts.

MODE="${1:-plan}"
case "$MODE" in plan|apply) ;; *) echo "usage: $0 plan|apply" >&2; exit 64 ;; esac

: "${KEYCLOAK_URL:?KEYCLOAK_URL is required}"
: "${KEYCLOAK_RECONCILER_CLIENT_ID:?KEYCLOAK_RECONCILER_CLIENT_ID is required}"
: "${KEYCLOAK_RECONCILER_CLIENT_SECRET:?KEYCLOAK_RECONCILER_CLIENT_SECRET is required}"

REALM="${KEYCLOAK_REALM:-impilo}"
EXTERNAL_HOST="${KEYCLOAK_EXTERNAL_HOST:-impilo.mohcc.gov.zw}"
CONFIG_VERSION="${IMPILO_MFA_POLICY_VERSION:-1.0.0}"
EXPECTED_USER_COUNT="${EXPECTED_USER_COUNT:-42}"
EXPECTED_CURRENT_HASH="${EXPECTED_CURRENT_HASH:-}"
APPROVED_AAGUIDS="${IMPILO_AAL3_APPROVED_AAGUIDS:-}"

for command in curl jq sha256sum mktemp diff; do
  command -v "$command" >/dev/null || { echo "missing command: $command" >&2; exit 69; }
done

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
chmod 700 "$tmp"

token_endpoint="${KEYCLOAK_URL%/}/realms/${REALM}/protocol/openid-connect/token"
admin_base="${KEYCLOAK_URL%/}/admin/realms/${REALM}"
token="$(curl --fail --silent --show-error --request POST "$token_endpoint" \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode grant_type=client_credentials \
  --data-urlencode "client_id=$KEYCLOAK_RECONCILER_CLIENT_ID" \
  --data-urlencode "client_secret=$KEYCLOAK_RECONCILER_CLIENT_SECRET" | jq -er '.access_token')"

kc_get() { curl --fail --silent --show-error --header "Authorization: Bearer $token" "$admin_base$1"; }
kc_put() {
  curl --fail --silent --show-error --request PUT \
    --header "Authorization: Bearer $token" --header 'Content-Type: application/json' \
    --data-binary "@$2" "$admin_base$1" >/dev/null
}
kc_post() {
  curl --fail --silent --show-error --request POST \
    --header "Authorization: Bearer $token" --header 'Content-Type: application/json' \
    --data-binary "@$2" "$admin_base$1" >/dev/null
}

kc_get '' >"$tmp/realm.json"
user_count="$(kc_get '/users/count' | jq -er '.')"
if [[ "$user_count" != "$EXPECTED_USER_COUNT" ]]; then
  echo "REFUSING: expected $EXPECTED_USER_COUNT users, live realm reports $user_count" >&2
  exit 65
fi

managed_filter='{
  sslRequired, bruteForceProtected, permanentLockout, failureFactor,
  waitIncrementSeconds, quickLoginCheckMilliSeconds, minimumQuickLoginWaitSeconds,
  maxFailureWaitSeconds, maxDeltaTimeSeconds,
  otpPolicyType, otpPolicyAlgorithm, otpPolicyDigits, otpPolicyPeriod, otpPolicyLookAheadWindow,
  webAuthnPolicyRpEntityName, webAuthnPolicyRpId, webAuthnPolicySignatureAlgorithms,
  webAuthnPolicyAttestationConveyancePreference, webAuthnPolicyAuthenticatorAttachment,
  webAuthnPolicyRequireResidentKey, webAuthnPolicyUserVerificationRequirement,
  webAuthnPolicyAcceptableAaguids,
  webAuthnPolicyPasswordlessRpEntityName, webAuthnPolicyPasswordlessRpId,
  webAuthnPolicyPasswordlessSignatureAlgorithms,
  webAuthnPolicyPasswordlessAttestationConveyancePreference,
  webAuthnPolicyPasswordlessAuthenticatorAttachment,
  webAuthnPolicyPasswordlessRequireResidentKey,
  webAuthnPolicyPasswordlessUserVerificationRequirement,
  eventsEnabled, eventsExpiration, adminEventsEnabled, adminEventsDetailsEnabled,
  rememberMe, verifyEmail, resetPasswordAllowed
}'

jq -S "$managed_filter" "$tmp/realm.json" >"$tmp/current-managed.json"
current_hash="$(sha256sum "$tmp/current-managed.json" | awk '{print $1}')"

if [[ -n "$APPROVED_AAGUIDS" ]]; then
  jq -Rn --arg value "$APPROVED_AAGUIDS" '$value | split(",") | map(gsub("^\\s+|\\s+$"; "")) | map(select(length > 0))' >"$tmp/aaguids.json"
else
  printf '[]\n' >"$tmp/aaguids.json"
fi

jq -S --arg host "$EXTERNAL_HOST" --slurpfile aaguids "$tmp/aaguids.json" '
  .sslRequired = "external" |
  .bruteForceProtected = true |
  .permanentLockout = false |
  .failureFactor = 5 |
  .waitIncrementSeconds = 60 |
  .quickLoginCheckMilliSeconds = 1000 |
  .minimumQuickLoginWaitSeconds = 60 |
  .maxFailureWaitSeconds = 900 |
  .maxDeltaTimeSeconds = 43200 |
  .otpPolicyType = "totp" |
  .otpPolicyAlgorithm = "HmacSHA256" |
  .otpPolicyDigits = 6 |
  .otpPolicyPeriod = 30 |
  .otpPolicyLookAheadWindow = 1 |
  .webAuthnPolicyRpEntityName = "Impilo privileged security key" |
  .webAuthnPolicyRpId = $host |
  .webAuthnPolicySignatureAlgorithms = ["ES256"] |
  .webAuthnPolicyAttestationConveyancePreference = "direct" |
  .webAuthnPolicyAuthenticatorAttachment = "cross-platform" |
  .webAuthnPolicyRequireResidentKey = "Yes" |
  .webAuthnPolicyUserVerificationRequirement = "required" |
  .webAuthnPolicyAcceptableAaguids = $aaguids[0] |
  .webAuthnPolicyPasswordlessRpEntityName = "Impilo passkey" |
  .webAuthnPolicyPasswordlessRpId = $host |
  .webAuthnPolicyPasswordlessSignatureAlgorithms = ["ES256"] |
  .webAuthnPolicyPasswordlessAttestationConveyancePreference = "none" |
  .webAuthnPolicyPasswordlessAuthenticatorAttachment = "not specified" |
  .webAuthnPolicyPasswordlessRequireResidentKey = "Yes" |
  .webAuthnPolicyPasswordlessUserVerificationRequirement = "required" |
  .eventsEnabled = true |
  .eventsExpiration = 2592000 |
  .adminEventsEnabled = true |
  .adminEventsDetailsEnabled = false |
  .rememberMe = false |
  .verifyEmail = true |
  .resetPasswordAllowed = true
' "$tmp/current-managed.json" >"$tmp/desired-managed.json"

desired_hash="$(sha256sum "$tmp/desired-managed.json" | awk '{print $1}')"
echo "REALM=$REALM USERS=$user_count CURRENT_HASH=$current_hash DESIRED_HASH=$desired_hash POLICY_VERSION=$CONFIG_VERSION"
diff --unified "$tmp/current-managed.json" "$tmp/desired-managed.json" || true

clients_json="$(kc_get '/clients?max=500')"
printf '%s' "$clients_json" >"$tmp/clients.json"
for client_id in experience-ui citizen-app provider-app; do
  if ! jq -e --arg id "$client_id" '.[] | select(.clientId == $id)' "$tmp/clients.json" >/dev/null; then
    echo "REFUSING: required client is absent: $client_id" >&2
    exit 66
  fi
done

required_actions="$(kc_get '/authentication/required-actions')"
printf '%s' "$required_actions" >"$tmp/required-actions.json"
for alias in CONFIGURE_TOTP webauthn-register webauthn-register-passwordless CONFIGURE_RECOVERY_AUTHN_CODES; do
  jq -e --arg alias "$alias" '.[] | select(.alias == $alias)' "$tmp/required-actions.json" >/dev/null || {
    echo "REFUSING: Keycloak does not expose required action $alias (26.3+ required)" >&2
    exit 67
  }
done

if [[ "$MODE" == plan ]]; then
  echo "PLAN_ONLY: no realm, client, user, credential, or required-action state changed"
  exit 0
fi

[[ -n "$EXPECTED_CURRENT_HASH" ]] || { echo "EXPECTED_CURRENT_HASH is mandatory for apply" >&2; exit 64; }
[[ "$EXPECTED_CURRENT_HASH" == "$current_hash" ]] || {
  echo "REFUSING: live managed state changed after planning (expected $EXPECTED_CURRENT_HASH, got $current_hash)" >&2
  exit 65
}

# Merge only managed posture fields. Users, credentials, groups, roles, components,
# keys, and unmanaged realm settings are not present in this update payload.
jq --slurpfile desired "$tmp/desired-managed.json" --arg hash "$desired_hash" --arg version "$CONFIG_VERSION" '
  . * $desired[0] |
  .attributes = ((.attributes // {}) + {
    "impilo.mfa.configHash": $hash,
    "impilo.mfa.policyVersion": $version
  })
' "$tmp/realm.json" >"$tmp/realm-update.json"
kc_put '' "$tmp/realm-update.json"

update_client() {
  local client_id="$1" redirects_json="$2" public_client="$3" secret="${4:-}"
  local uuid
  uuid="$(jq -er --arg id "$client_id" '.[] | select(.clientId == $id) | .id' "$tmp/clients.json")"
  kc_get "/clients/$uuid" >"$tmp/client-$client_id.json"
  jq --argjson redirects "$redirects_json" --argjson public "$public_client" --arg secret "$secret" '
    .redirectUris = $redirects |
    .webOrigins = [] |
    .publicClient = $public |
    .standardFlowEnabled = true |
    .implicitFlowEnabled = false |
    .directAccessGrantsEnabled = false |
    .secret = (if ($secret | length) > 0 then $secret else .secret end) |
    .attributes = ((.attributes // {}) + {
      "pkce.code.challenge.method": "S256",
      "post.logout.redirect.uris": ($redirects | join("##"))
    })
  ' "$tmp/client-$client_id.json" >"$tmp/client-$client_id-update.json"
  kc_put "/clients/$uuid" "$tmp/client-$client_id-update.json"
}

: "${KEYCLOAK_BFF_CLIENT_SECRET:?KEYCLOAK_BFF_CLIENT_SECRET is required for apply}"
: "${KEYCLOAK_USER_ADMIN_SECRET:?KEYCLOAK_USER_ADMIN_SECRET is required for apply}"
: "${KEYCLOAK_EVENT_READER_SECRET:?KEYCLOAK_EVENT_READER_SECRET is required for apply}"

update_client experience-ui '["https://impilo.mohcc.gov.zw/internal/v1/auth/oidc/callback"]' false "$KEYCLOAK_BFF_CLIENT_SECRET"
update_client citizen-app '["impilo-citizen://auth/callback"]' true
update_client provider-app '["impilo-provider://auth/callback"]' true

ensure_service_client() {
  local client_id="$1" secret="$2"
  local uuid
  uuid="$(jq -r --arg id "$client_id" '.[] | select(.clientId == $id) | .id' "$tmp/clients.json")"
  if [[ -z "$uuid" ]]; then
    jq -n --arg id "$client_id" --arg secret "$secret" '{
      clientId: $id, secret: $secret, enabled: true, publicClient: false,
      serviceAccountsEnabled: true, standardFlowEnabled: false,
      implicitFlowEnabled: false, directAccessGrantsEnabled: false,
      protocol: "openid-connect"
    }' >"$tmp/service-$client_id.json"
    kc_post '/clients' "$tmp/service-$client_id.json"
    clients_json="$(kc_get '/clients?max=500')"
    printf '%s' "$clients_json" >"$tmp/clients.json"
    uuid="$(jq -er --arg id "$client_id" '.[] | select(.clientId == $id) | .id' "$tmp/clients.json")"
  else
    kc_get "/clients/$uuid" >"$tmp/service-$client_id.json"
    jq --arg secret "$secret" '.secret = $secret | .enabled = true | .publicClient = false |
      .serviceAccountsEnabled = true | .standardFlowEnabled = false |
      .implicitFlowEnabled = false | .directAccessGrantsEnabled = false' \
      "$tmp/service-$client_id.json" >"$tmp/service-$client_id-update.json"
    kc_put "/clients/$uuid" "$tmp/service-$client_id-update.json"
  fi
  printf '%s' "$uuid"
}

grant_management_roles() {
  local service_uuid="$1"; shift
  local service_user management_uuid
  service_user="$(kc_get "/clients/$service_uuid/service-account-user" | jq -er '.id')"
  management_uuid="$(jq -er '.[] | select(.clientId == "realm-management") | .id' "$tmp/clients.json")"
  kc_get "/clients/$management_uuid/roles" >"$tmp/management-roles.json"
  jq --arg roles "$*" '[.[] | select(.name as $name | ($roles | split(" ") | index($name)))]' \
    "$tmp/management-roles.json" >"$tmp/roles-grant.json"
  kc_post "/users/$service_user/role-mappings/clients/$management_uuid" "$tmp/roles-grant.json"
}

user_admin_uuid="$(ensure_service_client impilo-user-admin "$KEYCLOAK_USER_ADMIN_SECRET")"
event_reader_uuid="$(ensure_service_client impilo-event-reader "$KEYCLOAK_EVENT_READER_SECRET")"
grant_management_roles "$user_admin_uuid" view-users query-users manage-users
grant_management_roles "$event_reader_uuid" view-events

for alias in CONFIGURE_TOTP webauthn-register webauthn-register-passwordless CONFIGURE_RECOVERY_AUTHN_CODES; do
  jq -e --arg alias "$alias" '.[] | select(.alias == $alias) | .enabled == true' "$tmp/required-actions.json" >/dev/null || {
    jq --arg alias "$alias" '.[] | select(.alias == $alias) | .enabled = true' "$tmp/required-actions.json" >"$tmp/action-$alias.json"
    kc_put "/authentication/required-actions/$alias" "$tmp/action-$alias.json"
  }
done

# Classify existing users by effective business roles. Required actions are merged,
# never replaced. Citizen-only users remain progressive. Credentials are never read
# beyond their type and are never changed by this enrollment assignment.
page=0
workforce_assigned=0
while :; do
  users="$(kc_get "/users?first=$((page * 100))&max=100")"
  [[ "$(jq 'length' <<<"$users")" -gt 0 ]] || break
  while IFS= read -r user_id; do
    roles="$(kc_get "/users/$user_id/role-mappings/realm/composite")"
    if jq -e '[.[].name | select(
        . != "CITIZEN" and . != "CAREGIVER" and . != "CARE_PARTNER" and
        . != "offline_access" and . != "uma_authorization" and
        (startswith("default-roles-") | not)
      )] | length > 0' <<<"$roles" >/dev/null; then
      credentials="$(kc_get "/users/$user_id/credentials")"
      if ! jq -e '[.[].type | select(. == "otp" or . == "webauthn" or . == "webauthn-passwordless")] | length > 0' <<<"$credentials" >/dev/null; then
        kc_get "/users/$user_id" >"$tmp/user.json"
        jq '.requiredActions = (((.requiredActions // []) + ["CONFIGURE_TOTP", "CONFIGURE_RECOVERY_AUTHN_CODES"]) | unique)' \
          "$tmp/user.json" >"$tmp/user-update.json"
        kc_put "/users/$user_id" "$tmp/user-update.json"
        workforce_assigned=$((workforce_assigned + 1))
      fi
    fi
  done < <(jq -r '.[].id' <<<"$users")
  page=$((page + 1))
done

kc_get '' | jq -S "$managed_filter" >"$tmp/after-managed.json"
after_hash="$(sha256sum "$tmp/after-managed.json" | awk '{print $1}')"
[[ "$after_hash" == "$desired_hash" ]] || {
  echo "REFUSING SUCCESS: post-apply hash mismatch ($after_hash != $desired_hash)" >&2
  exit 70
}
echo "APPLIED: policy=$CONFIG_VERSION hash=$after_hash workforce_required_actions_assigned=$workforce_assigned"
