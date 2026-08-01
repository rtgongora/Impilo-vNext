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
FLOW_ALIAS="impilo-browser-step-up-v1"

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
  local status response="$tmp/kc-response.json"
  status="$(curl --silent --show-error --request PUT \
    --header "Authorization: Bearer $token" --header 'Content-Type: application/json' \
    --data-binary "@$2" --output "$response" --write-out '%{http_code}' "$admin_base$1")"
  if [[ "$status" -lt 200 || "$status" -ge 300 ]]; then
    echo "KEYCLOAK_PUT_FAILED endpoint=$1 status=$status response=$(jq -c . "$response" 2>/dev/null || head -c 500 "$response")" >&2
    return 1
  fi
}
kc_post() {
  local status response="$tmp/kc-response.json"
  status="$(curl --silent --show-error --request POST \
    --header "Authorization: Bearer $token" --header 'Content-Type: application/json' \
    --data-binary "@$2" --output "$response" --write-out '%{http_code}' "$admin_base$1")"
  if [[ "$status" -lt 200 || "$status" -ge 300 ]]; then
    echo "KEYCLOAK_POST_FAILED endpoint=$1 status=$status response=$(jq -c . "$response" 2>/dev/null || head -c 500 "$response")" >&2
    return 1
  fi
}

flow_exists() {
  kc_get '/authentication/flows' | jq -e --arg alias "$1" '.[] | select(.alias == $alias)' >/dev/null
}

flow_executions() {
  kc_get "/authentication/flows/$1/executions"
}

set_execution_requirement() {
  local flow_alias="$1" execution_id="$2" requirement="$3"
  flow_executions "$flow_alias" | jq -e --arg id "$execution_id" --arg requirement "$requirement" '
    .[] | select(.id == $id) | .requirement = $requirement
  ' >"$tmp/execution-update.json"
  kc_put "/authentication/flows/$flow_alias/executions" "$tmp/execution-update.json"
}

add_execution() {
  local flow_alias="$1" provider="$2" requirement="$3" id
  jq -n --arg provider "$provider" '{provider: $provider}' >"$tmp/execution-create.json"
  kc_post "/authentication/flows/$flow_alias/executions/execution" "$tmp/execution-create.json"
  id="$(flow_executions "$flow_alias" | jq -er --arg provider "$provider" '
    [.[] | select(.providerId == $provider)] | last | .id')"
  set_execution_requirement "$flow_alias" "$id" "$requirement"
  printf '%s' "$id"
}

add_subflow() {
  local parent_alias="$1" alias="$2" requirement="$3" id
  jq -n --arg alias "$alias" '{alias: $alias, type: "basic-flow", provider: "registration-page-form", description: "", builtIn: false}' \
    >"$tmp/subflow-create.json"
  kc_post "/authentication/flows/$parent_alias/executions/flow" "$tmp/subflow-create.json"
  id="$(flow_executions "$parent_alias" | jq -er --arg alias "$alias" '
    [.[] | select(.authenticationFlow == true and .displayName == $alias)] | last | .id')"
  set_execution_requirement "$parent_alias" "$id" "$requirement"
}

configure_loa_condition() {
  local execution_id="$1" alias="$2" level="$3" max_age="$4"
  jq -n --arg alias "$alias" --arg level "$level" --arg maxAge "$max_age" '{
    alias: $alias,
    config: {"loa-condition-level": $level, "loa-max-age": $maxAge}
  }' >"$tmp/loa-config.json"
  kc_post "/authentication/executions/$execution_id/config" "$tmp/loa-config.json"
}

verify_impilo_flow() {
  flow_exists "$FLOW_ALIAS" || return 1
  flow_executions "$FLOW_ALIAS" | jq -e '
    any(.providerId == "auth-cookie" and .requirement == "ALTERNATIVE") and
    any(.authenticationFlow == true and .displayName == "impilo-auth" and .requirement == "ALTERNATIVE")
  ' >/dev/null || return 1
  flow_executions impilo-auth | jq -e '
    any(.displayName == "impilo-loa1" and .requirement == "CONDITIONAL") and
    any(.displayName == "impilo-loa2" and .requirement == "CONDITIONAL") and
    any(.displayName == "impilo-loa3" and .requirement == "CONDITIONAL")
  ' >/dev/null || return 1
  flow_executions impilo-loa1 | jq -e '
    any(.providerId == "conditional-level-of-authentication" and .requirement == "REQUIRED") and
    any(.providerId == "auth-username-password-form" and .requirement == "REQUIRED")
  ' >/dev/null || return 1
  flow_executions impilo-aal2-methods | jq -e '
    any(.providerId == "auth-otp-form" and .requirement == "ALTERNATIVE") and
    any(.providerId == "auth-recovery-authn-code-form" and .requirement == "ALTERNATIVE") and
    any(.providerId == "webauthn-authenticator-passwordless" and .requirement == "ALTERNATIVE")
  ' >/dev/null || return 1
  flow_executions impilo-loa3 | jq -e '
    any(.providerId == "conditional-level-of-authentication" and .requirement == "REQUIRED") and
    any(.providerId == "webauthn-authenticator" and .requirement == "REQUIRED")
  ' >/dev/null
}

ensure_impilo_flow() {
  if flow_exists "$FLOW_ALIAS"; then
    verify_impilo_flow || {
      echo "REFUSING: existing $FLOW_ALIAS flow differs from the governed v1 manifest" >&2
      exit 68
    }
    return
  fi

  jq -n --arg alias "$FLOW_ALIAS" '{alias: $alias, description: "Impilo governed AAL1/AAL2/AAL3 browser flow", providerId: "basic-flow", topLevel: true, builtIn: false}' \
    >"$tmp/flow-create.json"
  kc_post '/authentication/flows' "$tmp/flow-create.json"
  add_execution "$FLOW_ALIAS" auth-cookie ALTERNATIVE >/dev/null
  add_subflow "$FLOW_ALIAS" impilo-auth ALTERNATIVE

  add_subflow impilo-auth impilo-loa1 CONDITIONAL
  configure_loa_condition "$(add_execution impilo-loa1 conditional-level-of-authentication REQUIRED)" "Impilo AAL1" 1 36000
  add_execution impilo-loa1 auth-username-password-form REQUIRED >/dev/null

  add_subflow impilo-auth impilo-loa2 CONDITIONAL
  configure_loa_condition "$(add_execution impilo-loa2 conditional-level-of-authentication REQUIRED)" "Impilo AAL2" 2 0
  add_subflow impilo-loa2 impilo-aal2-methods REQUIRED
  add_execution impilo-aal2-methods auth-otp-form ALTERNATIVE >/dev/null
  add_execution impilo-aal2-methods auth-recovery-authn-code-form ALTERNATIVE >/dev/null
  add_execution impilo-aal2-methods webauthn-authenticator-passwordless ALTERNATIVE >/dev/null

  add_subflow impilo-auth impilo-loa3 CONDITIONAL
  configure_loa_condition "$(add_execution impilo-loa3 conditional-level-of-authentication REQUIRED)" "Impilo AAL3" 3 0
  add_execution impilo-loa3 webauthn-authenticator REQUIRED >/dev/null

  verify_impilo_flow || {
    echo "REFUSING SUCCESS: governed browser flow did not match its manifest after creation" >&2
    exit 70
  }
}

kc_get '' >"$tmp/realm.json"
user_count="$(kc_get '/users/count' | jq -er '.')"
if [[ "$user_count" != "$EXPECTED_USER_COUNT" ]]; then
  echo "REFUSING: expected $EXPECTED_USER_COUNT users, live realm reports $user_count" >&2
  exit 65
fi

managed_filter='{
  loginTheme, browserFlow, acrToLoAMapping,
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
  .loginTheme = "impilo" |
  .browserFlow = "impilo-browser-step-up-v1" |
  .acrToLoAMapping = {
    "urn:impilo:aal1": 1,
    "urn:impilo:aal2": 2,
    "urn:impilo:aal3": 3
  } |
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
for client_id in experience-ui impilo-mobile-citizen impilo-mobile-provider; do
  if ! jq -e --arg id "$client_id" '.[] | select(.clientId == $id)' "$tmp/clients.json" >/dev/null; then
    echo "REFUSING: required client is absent: $client_id" >&2
    exit 66
  fi
done

required_actions="$(kc_get '/authentication/required-actions')"
printf '%s' "$required_actions" >"$tmp/required-actions.json"
missing_registerable_actions=()
for alias in CONFIGURE_TOTP webauthn-register webauthn-register-passwordless CONFIGURE_RECOVERY_AUTHN_CODES; do
  if ! jq -e --arg alias "$alias" '.[] | select(.alias == $alias)' "$tmp/required-actions.json" >/dev/null; then
    kc_get '/authentication/unregistered-required-actions' >"$tmp/unregistered-required-actions.json"
    if jq -e --arg alias "$alias" '.[] | select(.providerId == $alias)' "$tmp/unregistered-required-actions.json" >/dev/null; then
      echo "REQUIRED_ACTION_STATE=$alias:missing-registerable"
      missing_registerable_actions+=("$alias")
    else
      echo "REFUSING: Keycloak does not expose or offer required action $alias" >&2
      exit 67
    fi
  fi
done

if verify_impilo_flow; then
  echo "FLOW_STATE=governed-v1"
elif flow_exists "$FLOW_ALIAS"; then
  echo "FLOW_STATE=drifted"
  exit 68
else
  echo "FLOW_STATE=absent (apply will create non-destructively)"
fi

if [[ "$MODE" == plan ]]; then
  echo "PLAN_ONLY: no realm, client, user, credential, or required-action state changed"
  exit 0
fi

[[ -n "$EXPECTED_CURRENT_HASH" ]] || { echo "EXPECTED_CURRENT_HASH is mandatory for apply" >&2; exit 64; }
[[ "$EXPECTED_CURRENT_HASH" == "$current_hash" ]] || {
  echo "REFUSING: live managed state changed after planning (expected $EXPECTED_CURRENT_HASH, got $current_hash)" >&2
  exit 65
}

# Realms migrated from an older Keycloak do not automatically register newly
# available required-action providers. Register only providers that Keycloak
# itself reports as available; this does not alter users or credentials.
for alias in "${missing_registerable_actions[@]}"; do
  jq --arg alias "$alias" '.[] | select(.providerId == $alias)' \
    "$tmp/unregistered-required-actions.json" >"$tmp/register-action-$alias.json"
  kc_post '/authentication/register-required-action' "$tmp/register-action-$alias.json"
done
required_actions="$(kc_get '/authentication/required-actions')"
printf '%s' "$required_actions" >"$tmp/required-actions.json"
for alias in CONFIGURE_TOTP webauthn-register webauthn-register-passwordless CONFIGURE_RECOVERY_AUTHN_CODES; do
  jq -e --arg alias "$alias" '.[] | select(.alias == $alias)' "$tmp/required-actions.json" >/dev/null || {
    echo "REFUSING: required action $alias is still absent after registration" >&2
    exit 70
  }
done

ensure_impilo_flow

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
update_client impilo-mobile-citizen '["impilo-citizen://auth/callback"]' true
update_client impilo-mobile-provider '["impilo-provider://auth/callback"]' true

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
verify_impilo_flow || {
  echo "REFUSING SUCCESS: governed browser flow verification failed" >&2
  exit 70
}
echo "APPLIED: policy=$CONFIG_VERSION hash=$after_hash workforce_required_actions_assigned=$workforce_assigned"
