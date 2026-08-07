#!/usr/bin/env bash
# Fails when a service's PRODUCTION filter chain does not authenticate, or exempts the v1.1
# probe POST on it.
#
# WHY THIS EXISTS ALONGSIDE check-enforcement-posture.sh
# ------------------------------------------------------
# That guard freezes the set of services with NO enforcement at all. It classifies a config that
# contains `permitAll` together with a `disable-oauth-for-tests` binding as FLAG_IS_SEAM and
# passes it, without ever checking which branch the estate runs.
#
# So a config that is permitAll in BOTH branches passes it. Proven by mutation on 2026-08-07:
# community-service was edited to terminate its production branch in permitAll and
# check-enforcement-posture.sh still printed "GUARD PASS ... exactly matching the reviewed
# baseline", while this guard went red. That is the most likely regression shape there is.
#
# WHAT IT ENFORCES  (see scripts/security/audit-production-chain.py for the full reasoning)
#   R1  the chain that configures oauth2ResourceServer must end in a CLOSED terminal
#   R2  that chain must not permit /internal/v1/test-command
#
# Both rules were proven RED by mutation before this guard was committed. Re-prove them if you
# change the audit: a check nobody has seen fail is a check nobody has tested.
#
# Rollback: delete this file and its entry in scripts/test/run-api-contract-checks.sh.
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

AUDIT="scripts/security/audit-production-chain.py"
if [[ ! -f "$AUDIT" ]]; then
  echo "GUARD FAIL  audit script missing: $AUDIT"
  exit 1
fi

OUT="$(python3 "$AUDIT" 2>&1)" || {
  echo "GUARD FAIL  audit script errored:"
  echo "$OUT"
  exit 1
}

AUDITED="$(sed -n 's/^production chains audited: \([0-9]*\)$/\1/p' <<<"$OUT")"

# ANCHOR. A guard whose measurement silently returned nothing reports OK forever — the estate has
# ~100 services and every one of them that enforces has an oauth2 chain, so a collapse to a
# handful means the scanner stopped finding configs, not that the estate changed shape.
MIN_EXPECTED=60
if [[ -z "$AUDITED" ]] || (( AUDITED < MIN_EXPECTED )); then
  echo "GUARD FAIL  audit examined ${AUDITED:-0} production chains, expected >= $MIN_EXPECTED"
  echo "            The scanner is broken, not the estate clean. Check that"
  echo "            security_configs() still matches this repo's layout."
  exit 1
fi

FINDINGS="$(sed -n 's/^services with findings: \([0-9]*\)$/\1/p' <<<"$OUT")"
if [[ "${FINDINGS:-0}" != "0" ]]; then
  echo "GUARD FAIL  $FINDINGS service(s) have a production chain that does not authenticate:"
  grep -E '^  [a-z0-9-]+: R[12] ' <<<"$OUT" | sed 's/^/  /'
  echo
  echo "  R1: give the chain .anyRequest().authenticated() and an oauth2ResourceServer. Keep"
  echo "      /actuator/health permitted — a credential on the readiness probe fails the pod."
  echo "  R2: /internal/v1/test-command belongs on the test chain only. Nothing in production"
  echo "      calls it; the golden-contract suites are in-JVM and run on the test chain."
  exit 1
fi

echo "GUARD PASS  $AUDITED production chains authenticate; none exempts the v1.1 probe POST"
exit 0
