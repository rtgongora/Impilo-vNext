#!/usr/bin/env bash
# =============================================================================
# Deterministic fixture-driven unit tests for scripts/operator/keycloak-mfa-reconcile.sh
#
# No live realm: the reconciler runs against a stateful in-process stub of the
# Keycloak admin API (stub_keycloak.py + fixtures/initial-state.json). The stub
# records every request, so the suite proves both what the reconciler did and
# what it must never do. In-cluster completed Job evidence remains the separate
# integration proof (docs/security/trust-audit/checkpoint-3/).
#
# Covered properties:
#   1.  plan produces a redacted diff (hashes + unified diff, no secrets)
#   2.  repeated apply is idempotent (second apply converges, state stable)
#   3.  unexpected live-state hash is rejected (exit 65)
#   4.  users and credentials are never deleted (no DELETE, no credential writes)
#   5.  wildcard redirects are rejected/replaced with exact URIs
#   6.  public clients require PKCE S256
#   7.  implicit and password (direct access) grants remain disabled
#   8.  service-account changes are least privilege (only missing roles granted)
#   9.  secrets and credential data are absent from all output
#   10. configuration version is recorded on the realm
#   11. recovery + WebAuthn policy are reconciled correctly
# =============================================================================
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
RECONCILER="$REPO/scripts/operator/keycloak-mfa-reconcile.sh"

WORK="$(mktemp -d)"
STATE="$WORK/state.json"
REQLOG="$WORK/requests.jsonl"
PORT=$(( (RANDOM % 2000) + 18000 ))
cp "$HERE/fixtures/initial-state.json" "$STATE"
: >"$REQLOG"

# Distinctive secret values: none of these may ever appear in reconciler output.
export STUB_RECONCILER_CLIENT_ID="impilo-realm-reconciler"
export STUB_RECONCILER_CLIENT_SECRET="UNIT-RECONCILER-SECRET-XYZZY"
BFF_SECRET="UNIT-BFF-SECRET-PLUGH"
USER_ADMIN_SECRET="UNIT-USERADMIN-SECRET-QUUX"
EVENT_READER_SECRET="UNIT-EVENTREADER-SECRET-THUD"

python3 "$HERE/stub_keycloak.py" "$STATE" "$REQLOG" "$PORT" &
STUB_PID=$!
trap 'kill $STUB_PID 2>/dev/null; rm -rf "$WORK"' EXIT
for _ in $(seq 1 50); do
  curl -s -o /dev/null "http://127.0.0.1:$PORT/admin/realms/impilo" && break
  sleep 0.1
done

run_reconciler() {
  local mode="$1" expected_hash="${2:-}"
  KEYCLOAK_URL="http://127.0.0.1:$PORT" \
  KEYCLOAK_RECONCILER_CLIENT_ID="$STUB_RECONCILER_CLIENT_ID" \
  KEYCLOAK_RECONCILER_CLIENT_SECRET="$STUB_RECONCILER_CLIENT_SECRET" \
  KEYCLOAK_BFF_CLIENT_SECRET="$BFF_SECRET" \
  KEYCLOAK_USER_ADMIN_SECRET="$USER_ADMIN_SECRET" \
  KEYCLOAK_EVENT_READER_SECRET="$EVENT_READER_SECRET" \
  KEYCLOAK_EXTERNAL_HOST="impilo.mohcc.gov.zw" \
  IMPILO_MFA_POLICY_VERSION="1.0.0-unit" \
  EXPECTED_USER_COUNT=42 \
  EXPECTED_CURRENT_HASH="$expected_hash" \
  bash "$RECONCILER" "$mode"
}

PASS=0
FAIL=0
check() {
  local name="$1"; shift
  if "$@"; then
    echo "ok   - $name"; PASS=$((PASS + 1))
  else
    echo "FAIL - $name"; FAIL=$((FAIL + 1))
  fi
}

live_state() { python3 -c "import json;print(json.dumps(json.load(open('$STATE.live'))))"; }
jq_live() { live_state | jq -e "$1" >/dev/null; }

# ── 1. plan: redacted diff, zero mutations ───────────────────────────────────
PLAN_OUT="$(run_reconciler plan 2>&1)"; PLAN_RC=$?
CURRENT_HASH="$(sed -n 's/.*CURRENT_HASH=\([0-9a-f]*\).*/\1/p' <<<"$PLAN_OUT" | head -1)"
DESIRED_HASH="$(sed -n 's/.*DESIRED_HASH=\([0-9a-f]*\).*/\1/p' <<<"$PLAN_OUT" | head -1)"

check "plan exits 0" test "$PLAN_RC" -eq 0
check "plan reports current and desired hashes" test -n "$CURRENT_HASH" -a -n "$DESIRED_HASH"
check "plan emits a unified diff of managed posture" grep -q '^+.*bruteForceProtected' <<<"$PLAN_OUT"
check "plan declares PLAN_ONLY (no state change)" grep -q 'PLAN_ONLY' <<<"$PLAN_OUT"
check "plan performed no mutating request" bash -c "! grep -E '\"method\": \"(PUT|DELETE)\"' '$REQLOG' | grep -qv token"
check "plan output contains no secret material" bash -c "! grep -qE 'XYZZY|PLUGH|QUUX|THUD' <<<'$PLAN_OUT'"

# ── 3. wrong expected hash is rejected before any mutation ───────────────────
: >"$REQLOG"
WRONG_OUT="$(run_reconciler apply 0000000000000000000000000000000000000000000000000000000000000000 2>&1)"; WRONG_RC=$?
check "apply with unexpected live-state hash is refused (exit 65)" test "$WRONG_RC" -eq 65
check "hash refusal happens before any mutation" bash -c "! grep -qE '\"method\": \"(PUT|POST)\"' <(grep -v token '$REQLOG')"

# ── first apply ──────────────────────────────────────────────────────────────
: >"$REQLOG"
APPLY_OUT="$(run_reconciler apply "$CURRENT_HASH" 2>&1)"; APPLY_RC=$?
check "apply exits 0" test "$APPLY_RC" -eq 0
check "apply reports APPLIED with policy version" grep -q 'APPLIED: policy=1.0.0-unit' <<<"$APPLY_OUT"
check "apply output contains no secret material" bash -c "! grep -qE 'XYZZY|PLUGH|QUUX|THUD' <<<'$APPLY_OUT'"

# ── 4. users/credentials never deleted or written ────────────────────────────
check "no DELETE request was ever issued" bash -c "! grep -q '\"method\": \"DELETE\"' '$REQLOG'"
check "no credential endpoint was ever written" bash -c "! grep -E '\"method\": \"(PUT|POST)\"' '$REQLOG' | grep -qE 'credentials|reset-password'"
check "no user was removed from the fixture" jq_live '.users | length == 2'
check "user password credentials survived untouched" jq_live '.user_credentials["user-workforce-1"][0].type == "password"'

# ── 5. wildcard redirects replaced with exact URIs ───────────────────────────
check "experience-ui redirect is the exact callback (wildcards gone)" \
  jq_live '.clients[] | select(.clientId == "experience-ui") | .redirectUris == ["https://impilo.mohcc.gov.zw/internal/v1/auth/oidc/callback"]'
check "mobile citizen redirect is the exact scheme callback" \
  jq_live '.clients[] | select(.clientId == "impilo-mobile-citizen") | .redirectUris == ["impilo-citizen://auth/callback"]'
check "no wildcard redirect anywhere after apply" \
  bash -c "! jq -e '.clients[].redirectUris[]? | select(contains(\"*\"))' '$STATE.live' >/dev/null"

# ── 6. public clients require PKCE S256 ─────────────────────────────────────
check "mobile citizen is public with PKCE S256" \
  jq_live '.clients[] | select(.clientId == "impilo-mobile-citizen") | .publicClient == true and .attributes["pkce.code.challenge.method"] == "S256"'
check "mobile provider is public with PKCE S256" \
  jq_live '.clients[] | select(.clientId == "impilo-mobile-provider") | .publicClient == true and .attributes["pkce.code.challenge.method"] == "S256"'
check "experience-ui is confidential with PKCE S256" \
  jq_live '.clients[] | select(.clientId == "experience-ui") | .publicClient == false and .attributes["pkce.code.challenge.method"] == "S256"'

# ── 7. implicit and password grants disabled ─────────────────────────────────
check "implicit flow disabled on all managed clients" \
  bash -c "! jq -e '.clients[] | select(.clientId | test(\"experience-ui|impilo-mobile\")) | select(.implicitFlowEnabled == true)' '$STATE.live' >/dev/null"
check "direct access (password) grant disabled on all managed clients" \
  bash -c "! jq -e '.clients[] | select(.clientId | test(\"experience-ui|impilo-mobile\")) | select(.directAccessGrantsEnabled == true)' '$STATE.live' >/dev/null"

# ── 8. service accounts least privilege ─────────────────────────────────────
check "event-reader gained exactly view-events" \
  jq_live '.client_role_mappings["sa-uuid-event-reader"]["uuid-realm-management"] | map(.name) == ["view-events"]'
check "user-admin roles unchanged (already complete, no re-grant)" \
  bash -c "! grep '\"method\": \"POST\"' '$REQLOG' | grep -q 'sa-uuid-user-admin'"
check "no service account received manage-realm or manage-clients" \
  bash -c "! jq -e '.client_role_mappings[][] | .[] | select(.name == \"manage-realm\" or .name == \"manage-clients\")' '$STATE.live' >/dev/null"

# ── 10. configuration version recorded ───────────────────────────────────────
check "policy version recorded on realm attributes" \
  jq_live '.realm.attributes["impilo.mfa.policyVersion"] == "1.0.0-unit"'
check "config hash recorded on realm attributes" \
  jq_live ".realm.attributes[\"impilo.mfa.configHash\"] == \"$DESIRED_HASH\""

# ── 11. recovery and WebAuthn policy reconciled ──────────────────────────────
check "OTP policy is governed (HmacSHA256, 6 digits, 30s)" \
  jq_live '.realm.otpPolicyAlgorithm == "HmacSHA256" and .realm.otpPolicyDigits == 6 and .realm.otpPolicyPeriod == 30'
check "WebAuthn AAL3 policy governed (direct attestation, cross-platform, UV required)" \
  jq_live '.realm.webAuthnPolicyAttestationConveyancePreference == "direct" and .realm.webAuthnPolicyAuthenticatorAttachment == "cross-platform" and .realm.webAuthnPolicyUserVerificationRequirement == "required" and .realm.webAuthnPolicyRpId == "impilo.mohcc.gov.zw"'
check "WebAuthn passkey policy governed (UV required, resident key)" \
  jq_live '.realm.webAuthnPolicyPasswordlessUserVerificationRequirement == "required" and .realm.webAuthnPolicyPasswordlessRequireResidentKey == "Yes"'
check "recovery-codes required action registered and enabled" \
  jq_live '.required_actions[] | select(.alias == "CONFIGURE_RECOVERY_AUTHN_CODES") | .enabled == true'
check "brute force + events enabled per governed posture" \
  jq_live '.realm.bruteForceProtected == true and .realm.failureFactor == 5 and .realm.eventsEnabled == true and .realm.adminEventsEnabled == true'
check "workforce user without MFA got enrollment required-actions merged" \
  jq_live '.users[] | select(.id == "user-workforce-1") | .requiredActions | contains(["CONFIGURE_TOTP", "CONFIGURE_RECOVERY_AUTHN_CODES"])'
check "citizen user stays progressive (no forced enrollment)" \
  jq_live '.users[] | select(.id == "user-citizen-1") | .requiredActions == []'

# ── 2. repeated apply is idempotent ──────────────────────────────────────────
PLAN2_OUT="$(run_reconciler plan 2>&1)"
CURRENT2_HASH="$(sed -n 's/.*CURRENT_HASH=\([0-9a-f]*\).*/\1/p' <<<"$PLAN2_OUT" | head -1)"
DESIRED2_HASH="$(sed -n 's/.*DESIRED_HASH=\([0-9a-f]*\).*/\1/p' <<<"$PLAN2_OUT" | head -1)"
check "after apply the live state equals desired (converged)" test "$CURRENT2_HASH" == "$DESIRED2_HASH"

STATE_BEFORE="$(live_state | jq -S .)"
APPLY2_OUT="$(run_reconciler apply "$CURRENT2_HASH" 2>&1)"; APPLY2_RC=$?
STATE_AFTER="$(live_state | jq -S .)"
check "second apply exits 0" test "$APPLY2_RC" -eq 0
check "second apply leaves state byte-identical (idempotent)" test "$STATE_BEFORE" == "$STATE_AFTER"
check "second apply output contains no secret material" bash -c "! grep -qE 'XYZZY|PLUGH|QUUX|THUD' <<<'$APPLY2_OUT'"

echo
echo "RESULT: pass=$PASS fail=$FAIL"
test "$FAIL" -eq 0
