#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
fail=0

reject() { echo "FAIL: $*" >&2; fail=$((fail + 1)); }
pass() { echo "PASS: $*"; }

if grep -q 'IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS' "$ROOT/deploy/helm/impilo-vnext/templates/_helpers.tpl"; then
  reject "full-preview Helm still injects the OAuth test bypass"
else
  pass "full-preview Helm does not inject the OAuth test bypass"
fi

if grep -q 'start-dev' "$ROOT/deploy/helm/impilo-vnext/templates/keycloak.yaml"; then
  reject "Keycloak template still uses start-dev"
else
  pass "Keycloak template uses the optimized production runtime"
fi

grep -q 'tag: "26.7.0"' "$ROOT/deploy/helm/impilo-vnext/values-full-preview.yaml" \
  && pass "Keycloak 26.7.0 is pinned in preview values" \
  || reject "Keycloak 26.7.0 is not pinned"

grep -q 'vendor: postgres' "$ROOT/deploy/helm/impilo-vnext/values-full-preview.yaml" \
  && pass "Keycloak PostgreSQL is configured" \
  || reject "Keycloak PostgreSQL is not configured"

grep -q 'EXPECTED_CURRENT_HASH is mandatory' "$ROOT/scripts/operator/keycloak-mfa-reconcile.sh" \
  && pass "realm apply requires an expected-current hash" \
  || reject "realm reconciler lacks drift guard"

grep -q 'CONFIGURE_RECOVERY_AUTHN_CODES' "$ROOT/scripts/operator/keycloak-mfa-reconcile.sh" \
  && pass "recovery-code support is gated" \
  || reject "recovery-code required action is absent"

grep -q 'impilo-browser-step-up-v1' "$ROOT/scripts/operator/keycloak-mfa-reconcile.sh" \
  && grep -q 'conditional-level-of-authentication' "$ROOT/scripts/operator/keycloak-mfa-reconcile.sh" \
  && pass "governed AAL browser flow is reconciled" \
  || reject "governed AAL browser flow is absent"

grep -q 'COPY --chown=1000:1000 themes/impilo/' "$ROOT/infra/keycloak/Dockerfile" \
  && test -f "$ROOT/infra/keycloak/themes/impilo/login/theme.properties" \
  && pass "Impilo Keycloak enrollment theme is packaged" \
  || reject "Impilo Keycloak enrollment theme is not packaged"

if grep -q 'default:[[:space:]]*$' "$ROOT/services/tshepo-authz-service/src/main/java/zw/gov/mohcc/impilo/tshepo/authz/stepup/StepUpMode.java" \
  && grep -q 'return TOTP' "$ROOT/services/tshepo-authz-service/src/main/java/zw/gov/mohcc/impilo/tshepo/authz/stepup/StepUpMode.java"; then
  reject "unknown Tshepo step-up methods still fall back to TOTP"
else
  pass "unknown and legacy Tshepo factor methods fail closed"
fi

if grep -q 'TotpEnrolmentService' "$ROOT/services/tshepo-authz-service/src/main/java/zw/gov/mohcc/impilo/tshepo/authz/api/TotpEnrolmentController.java"; then
  reject "Tshepo TOTP endpoint still receives authenticator secrets"
else
  pass "Tshepo authenticator endpoint is a secret-free compatibility tombstone"
fi

if grep -R -n -E 'SMS.*MFA|messagingOtp:[[:space:]]*\n[[:space:]]*enabled:[[:space:]]*true' \
  "$ROOT/docs/security/mfa-policy-v1.yaml" >/dev/null 2>&1; then
  reject "messaging OTP is enabled as MFA"
else
  pass "messaging OTP remains disabled"
fi

echo "SUMMARY failures=$fail"
[[ "$fail" -eq 0 ]]
