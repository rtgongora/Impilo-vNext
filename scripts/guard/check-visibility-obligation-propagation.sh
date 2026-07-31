#!/usr/bin/env bash
# Visibility-obligation propagation: the BFF must be able to carry the PDP's visibility decision to
# every downstream that consumes it.
#
# THE TRAP THIS EXISTS FOR. The experience BFF calls sovereign services DIRECTLY
# (PCT_BASE_URL: http://pct-service:8088), so Envoy's ext_authz never re-runs on that hop and a
# downstream sees only the headers ServiceClientConfig's interceptor chooses to forward. For most of
# the estate's life that set omitted x-obligations and all ten flat visibility headers, which means
# every visibility guard downstream saw a NULL PROFILE on every BFF-mediated call.
#
# The three guards disagree about what a null profile means, and that is what makes this dangerous
# rather than merely untidy:
#
#   AggregateVisibilityGuard, ClinicalVisibilityGuard  -- PERMISSIVE on null, restrictive on present.
#   SpeciallyProtectedVisibilityGuard                  -- FAIL-CLOSED on null ("no profile means the
#                                                         PDP never said yes").
#
# So the omission simultaneously LOOSENS two guards and, once pct's class stamping is enabled, makes
# the third withhold every stamped record from EVERY requester including its author — while the direct
# Envoy route keeps working and every test stays green. X-Purpose-Of-Use IS forwarded, so emergency
# reads would survive and routine care would not, which is the worst possible failure signature: it
# looks like the feature works.
#
# WHAT IS CHECKED, and why these three things.
#   1. The propagator exists and is WIRED into the interceptor. A forwarding bean nobody calls
#      forwards nothing, and that is invisible in review.
#   2. Its forwarded set covers EVERY header VisibilityHeaderParser reads. The parser overlays the
#      nested visibilityProfile from x-obligations onto the flat headers field by field, so a missing
#      header does not degrade to "absent" — it yields a profile assembled from two different
#      decisions, i.e. a partial grant nobody made. Adding a header to the parser without adding it
#      here must fail the build.
#   3. The flag's default is reported, not enforced. Whether propagation is ON is a governance
#      decision recorded in configuration; whether the estate is CAPABLE of it is an engineering
#      invariant, and only the second belongs in a build gate.
set -uo pipefail
_GUARD_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_PATH="${REPO_PATH:-$(cd "$_GUARD_DIR/../.." && pwd)}"
cd "$REPO_PATH"
source "$_GUARD_DIR/_guard-common.sh"
set +e

echo "=== Visibility-obligation propagation guard ==="

PARSER="services/shared-core/src/main/java/zw/gov/mohcc/impilo/shared/visibility/VisibilityHeaderParser.java"
PROPAGATOR="services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/VisibilityObligationPropagator.java"
INTERCEPTOR="services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/ServiceClientConfig.java"
TRUST_HEADERS="libs/tshepo-contracts/src/main/java/zw/gov/mohcc/impilo/tshepo/contracts/headers/TrustHeaders.java"

FAIL=0

for f in "$PARSER" "$PROPAGATOR" "$INTERCEPTOR" "$TRUST_HEADERS"; do
    if [[ ! -f "$f" ]]; then
        echo "FAIL: missing $f"
        echo "      This guard cannot answer its question without it. If the file moved, update this"
        echo "      guard rather than deleting the check."
        FAIL=1
    fi
done
if [[ "$FAIL" -ne 0 ]]; then
    echo "Visibility-obligation propagation guard: FAILED"
    exit 1
fi

# ── 1. The propagator is wired into the interceptor ───────────────────────────
if ! grep -q 'visibilityObligationPropagator.propagate(' "$INTERCEPTOR"; then
    echo "FAIL: $INTERCEPTOR never calls VisibilityObligationPropagator.propagate()"
    echo "      A forwarding bean nobody invokes forwards nothing. The obligation would be absent on"
    echo "      every BFF-mediated call, and SpeciallyProtectedVisibilityGuard fails closed."
    FAIL=1
fi

# ── 2. Every header the parser reads is in the forwarded set ──────────────────
# Derived from the parser rather than hardcoded here: a guard with its own copy of the list stops
# tracking the thing it is guarding the first time someone extends the profile.
PARSER_CONSTANTS=$(grep -oE 'TrustHeaders\.[A-Z_]+' "$PARSER" | sed 's/TrustHeaders\.//' | sort -u)

guard_assert_scanned "$(printf '%s\n' "$PARSER_CONSTANTS" | grep -c .)" \
  "visibility header constants read by VisibilityHeaderParser" || {
  echo "      The parser was found but no TrustHeaders.* reference was extracted, so this guard would"
  echo "      report success about an empty set. Check whether the parser stopped using the constants."
  echo "Visibility-obligation propagation guard: FAILED"
  exit 1
}

MISSING=""
for c in $PARSER_CONSTANTS; do
    if ! grep -qE "TrustHeaders\.$c\b" "$PROPAGATOR"; then
        MISSING="$MISSING $c"
    fi
done

if [[ -n "$MISSING" ]]; then
    echo "FAIL: VisibilityHeaderParser reads these headers, and the propagator does not forward them:"
    for c in $MISSING; do
        header=$(grep -oE "String $c *= *\"[^\"]+\"" "$TRUST_HEADERS" | grep -oE '"[^"]+"' | tr -d '"')
        echo "        TrustHeaders.$c  (${header:-unresolved})"
    done
    cat <<'EOF'

      WHY THIS BLOCKS. The parser overlays the nested visibilityProfile from x-obligations onto the
      flat headers FIELD BY FIELD. A header that is read but not forwarded therefore does not degrade
      to "absent" — the downstream builds a profile from two different decisions, which is a grant
      nobody made. Add the constant to VisibilityObligationPropagator.VISIBILITY_HEADERS.
EOF
    FAIL=1
fi

# ── 3. Report the flag default and who is affected ────────────────────────────
FLAG_DEFAULT=$(grep -oE 'experience\.trust\.propagate-obligations:[a-z]+' "$PROPAGATOR" \
  | head -1 | cut -d: -f2)
CONSUMERS=$(git ls-files --cached --others --exclude-standard -- 'services/*/src/main/**/*.java' \
  | xargs -r grep -lE 'VisibilityContextHolder|SpeciallyProtectedVisibilityGuard|AggregateVisibilityGuard|ClinicalVisibilityGuard' 2>/dev/null \
  | cut -d/ -f2 | sort -u | grep -v '^shared-core$')

echo "  forwarded set covers all $(printf '%s\n' "$PARSER_CONSTANTS" | grep -c .) header(s) the parser reads"
echo "  experience.trust.propagate-obligations default: ${FLAG_DEFAULT:-unresolved}"
echo "  downstream services consuming a visibility profile:"
printf '%s\n' "$CONSUMERS" | grep -c . >/dev/null
for s in $CONSUMERS; do echo "    - $s"; done

if [[ "${FLAG_DEFAULT:-}" == "false" ]]; then
    cat <<'EOF'
  NOTE: propagation is DISABLED, so those services still see a null profile on BFF-mediated calls.
        That is deliberate and currently BLOCKING, not merely pending: the deployed Envoy renders
        ext_authz off with no request_headers_to_remove, so every visibility header is client-forgeable
        and the BFF's non-forwarding is the only thing between a forged x-confidential-categories and
        pct's fail-closed guard. Enabling this flag is gated on the edge strip. This guard proves the
        capability exists; it deliberately does not enforce the flag's value.
EOF
fi

if [[ "$FAIL" -ne 0 ]]; then
    echo "Visibility-obligation propagation guard: FAILED"
    exit 1
fi

echo "Visibility-obligation propagation guard: OK"
exit 0
