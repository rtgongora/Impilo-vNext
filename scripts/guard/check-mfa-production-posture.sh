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

if grep -R -n -E 'SMS.*MFA|messagingOtp:[[:space:]]*\n[[:space:]]*enabled:[[:space:]]*true' \
  "$ROOT/docs/security/mfa-policy-v1.yaml" >/dev/null 2>&1; then
  reject "messaging OTP is enabled as MFA"
else
  pass "messaging OTP remains disabled"
fi

echo "SUMMARY failures=$fail"
[[ "$fail" -eq 0 ]]
