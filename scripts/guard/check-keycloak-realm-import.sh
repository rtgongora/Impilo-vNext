#!/usr/bin/env bash
# =============================================================================
# GUARD — Keycloak realm JSON must actually import.
#
# WHY THIS EXISTS (2026-07-20 → 2026-07-26 incident, 6 days of total auth outage):
# commit 0b625f727 ("feat(keycloak): WebAuthn passwordless realm config for
# passkey login") added 11 realm fields named `webAuthnPasswordlessPolicy*`.
# Keycloak 25 expects `webAuthnPolicyPasswordless*` (Policy BEFORE Passwordless).
# Realm import rejects unknown properties, so Keycloak threw
# UnrecognizedPropertyException at startup and DIED ON EVERY RESTART for six
# days. endpoints/keycloak was empty for nine. Web login AND mobile login were
# both dead the entire time and nobody noticed, because nothing depends on
# Keycloak until a human tries to sign in.
#
# The field names look completely plausible to a reviewer. Only Keycloak itself
# can tell you they are wrong — so this guard asks Keycloak, by performing a
# real import into a throwaway in-container database.
#
# Scope: every realm JSON the estate ships. Runs in CI on changes to those files
# (.github/workflows/keycloak-realm-guard.yml), and is runnable locally.
#
# Usage:  bash scripts/guard/check-keycloak-realm-import.sh [file ...]
#         KEYCLOAK_IMAGE=quay.io/keycloak/keycloak:25.0 bash scripts/guard/...
# Exit:   0 = every realm file imports cleanly · 1 = at least one is broken
# =============================================================================
set -uo pipefail
source "$(dirname "$0")/_guard-common.sh"
# _guard-common.sh sets -e. We deliberately turn it back off: this guard uses a
# `fail` accumulator so every realm file is reported, and the whole point is to
# capture and explain a NON-ZERO docker exit rather than die silently on it.
set +e

KEYCLOAK_IMAGE="${KEYCLOAK_IMAGE:-quay.io/keycloak/keycloak:25.0}"

# Realm files the estate actually deploys. Keep in sync with the Helm chart and
# any operator seed tooling.
DEFAULT_REALMS=(
  "deploy/helm/impilo-vnext/files/realm-impilo-preview.json"
  "infra/keycloak/realm-impilo-production.json"
  "tools/auth/impilo-realm.json"
)

REALMS=("$@")
[ ${#REALMS[@]} -eq 0 ] && REALMS=("${DEFAULT_REALMS[@]}")

if ! command -v docker >/dev/null 2>&1; then
  guard_warn "docker not available — cannot run Keycloak import validation (skipped)"
  echo "         This guard is authoritative only with docker; CI provides it."
  exit 0
fi

fail=0
for realm in "${REALMS[@]}"; do
  if [ ! -f "$realm" ]; then
    guard_warn "realm file not found, skipping: $realm"
    continue
  fi

  # 1) JSON must parse at all — fast, and gives a precise line/column.
  if ! python3 -c "import json,sys; json.load(open(sys.argv[1]))" "$realm" 2>/tmp/realm-json-err.$$; then
    guard_fail "invalid JSON: $realm"
    sed 's/^/         /' /tmp/realm-json-err.$$
    rm -f /tmp/realm-json-err.$$
    fail=1
    continue
  fi
  rm -f /tmp/realm-json-err.$$

  # 2) Keycloak must accept every property. `kc.sh import` runs the same
  #    RealmRepresentation deserialisation as startup --import-realm, against a
  #    throwaway dev-file H2 inside the container. Nothing outside is touched.
  out="$(docker run --rm \
          -v "$(cd "$(dirname "$realm")" && pwd)/$(basename "$realm")":/tmp/realm.json:ro \
          --entrypoint /opt/keycloak/bin/kc.sh \
          "$KEYCLOAK_IMAGE" import --file /tmp/realm.json 2>&1)"
  rc=$?

  if [ $rc -ne 0 ]; then
    guard_fail "Keycloak rejected realm import: $realm"
    # Surface the decisive line — the unknown/!invalid property — not 200 lines of Quarkus noise.
    echo "$out" | grep -iE "UnrecognizedPropertyException|Unrecognized field|not marked as ignorable|InvalidFormatException|MismatchedInputException|ERROR:" \
      | head -4 | sed 's/^/         /'
    echo "         (full output: re-run this guard locally on $realm)"
    fail=1
  else
    guard_pass "realm imports cleanly: $realm"
  fi
done

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "GUARD FAIL  Keycloak realm import validation"
  echo "  A realm file above will crash Keycloak at startup — it will not degrade,"
  echo "  it will fail to boot, taking ALL web and mobile authentication with it."
  echo ""
  echo "  WHY THIS IS EASY TO MISS: --import-realm SKIPS realms that already exist."
  echo "  On a running estate a broken realm file therefore changes nothing and"
  echo "  looks harmless — right up until Keycloak restarts for any reason and"
  echo "  cannot come back. The error stays invisible between those two moments."
  echo "  In the 2026-07-20 incident that gap was six days of total auth outage."
  echo ""
  echo "  Check field spelling against the Keycloak RealmRepresentation for this"
  echo "  version; the error above names the offending property."
  echo "  Runbook: docs/runbooks/keycloak-auth-outage-runbook.md"
  exit 1
fi
exit 0
