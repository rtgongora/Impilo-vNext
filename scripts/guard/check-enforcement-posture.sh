#!/usr/bin/env bash
# Freezes the set of services that are open regardless of any bypass flag.
#
# CP8's headline finding: 16 services are UNCONDITIONAL_OPEN or have NO_SECURITY_CONFIG *and do
# not declare the bypass flag at all*. They are therefore invisible to bypass retirement — you can
# retire all 95 declared bypasses and truthfully report "bypasses retired" while these 16 stay
# wide open, among them abis-service (biometrics), audit-ledger-service (audit records) and
# matcher-engine (identity matching).
#
# This guard does NOT fix them. It stops the set growing, and makes each removal from the baseline
# a deliberate, reviewed act rather than a silent drift. Shrinking the baseline is the CP8 work
# itself; the guard fails if the baseline and reality disagree in EITHER direction, so a service
# that is quietly fixed must also be removed from the file — a stale allowlist that still names a
# now-secured service is how an exemption outlives its reason.
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

BASELINE="docs/security/trust-audit/checkpoint-8/unconditionally-open-services.txt"
FAIL=0

if [[ ! -f "$BASELINE" ]]; then
  echo "GUARD FAIL  baseline missing: $BASELINE"
  exit 1
fi

ACTUAL="$(python3 scripts/security/audit-enforcement-posture.py --json \
  | python3 -c '
import json,sys
rows = json.load(sys.stdin)
open_ = [r["service"] for r in rows
         if r["posture"] in ("UNCONDITIONAL_OPEN", "NO_SECURITY_CONFIG")]
print("\n".join(sorted(open_)))')"

if [[ -z "$ACTUAL" ]]; then
  # An empty result means the audit found nothing rather than that nothing is open. Treated as a
  # failure: a guard whose measurement silently returned nothing would pass forever.
  echo "GUARD FAIL  posture audit produced no rows — the audit is broken, not the estate clean"
  exit 1
fi

EXPECTED="$(grep -vE '^\s*(#|$)' "$BASELINE" | sort)"

NEW="$(comm -23 <(echo "$ACTUAL") <(echo "$EXPECTED"))"
GONE="$(comm -13 <(echo "$ACTUAL") <(echo "$EXPECTED"))"

if [[ -n "$NEW" ]]; then
  echo "GUARD FAIL  service(s) became open with no bypass flag to retire:"
  echo "$NEW" | sed 's/^/    /'
  echo "    A service with no securityFilterChain, or one ending in an unconditional"
  echo "    permitAll, cannot be closed by retiring any flag. Add a real chain."
  FAIL=1
fi

if [[ -n "$GONE" ]]; then
  echo "GUARD FAIL  baseline names service(s) that are no longer open:"
  echo "$GONE" | sed 's/^/    /'
  echo "    Remove them from $BASELINE. An exemption that outlives its reason is"
  echo "    indistinguishable from an unreviewed one."
  FAIL=1
fi

if [[ $FAIL -eq 0 ]]; then
  echo "GUARD PASS  $(echo "$ACTUAL" | wc -l) unconditionally-open services, exactly matching the reviewed baseline"
fi
exit $FAIL
