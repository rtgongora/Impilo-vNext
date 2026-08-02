#!/usr/bin/env bash
# Visibility header strip guard — W14-C / ENVOY-GATE extension.
#
# Asserts all three Envoy copies strip x-confidential-categories (and the full visibility
# obligation header set) on routes where clients must not forge grants before ext_authz
# re-adds them. Wired from run-change-safety-gates.sh.
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

FAILED=0
REQUIRED_HEADER="x-confidential-categories"

ENVoy_FILES=(
  infra/envoy/envoy.yaml
  infra/envoy/envoy-runtime.yaml
  deploy/helm/impilo-vnext/templates/envoy.yaml
)

for f in "${ENVoy_FILES[@]}"; do
  if [[ ! -f "$f" ]]; then
    guard_fail "missing Envoy config: $f" || FAILED=1
    continue
  fi
  if ! grep -q "$REQUIRED_HEADER" "$f"; then
    guard_fail "$f does not list $REQUIRED_HEADER in request_headers_to_remove" || FAILED=1
    continue
  fi
  # Each file must strip on at least one authenticated BFF route block (not public-only).
  if ! awk '
    /request_headers_to_remove:/ { in_strip=1; has_conf=0; next }
    in_strip && /^[[:space:]]*- "[^"]+"/ {
      if ($0 ~ /x-confidential-categories/) has_conf=1
      next
    }
    # A comment, a Helm directive or a blank line does NOT end the list. Treating them as
    # terminators closed every block on its first "# Decision inputs:" line, two lines after
    # it opened, so the scan never reached the headers below and the guard failed on ANY
    # input — including a correct chart. A check that cannot pass proves nothing when it fails.
    in_strip && /^[[:space:]]*(#|\{\{|$)/ { next }
    in_strip {
      if (has_conf) found=1
      in_strip=0
    }
    END { if (in_strip && has_conf) found=1; exit(found ? 0 : 1) }
  ' "$f"; then
    guard_fail "$f lists $REQUIRED_HEADER but not inside a request_headers_to_remove block" || FAILED=1
  else
    guard_pass "$f strips $REQUIRED_HEADER"
  fi
done

# ENVOY-GATE: public-lane blocks in the two infra files stay identical.
extract_public_block() {
  awk '/prefix: "\/internal\/v1\/public\/"/{on=1} on{print} on && /disabled: true/{exit}' "$1" \
    | sed 's/^[[:space:]]*//'
}
A=$(extract_public_block infra/envoy/envoy.yaml)
B=$(extract_public_block infra/envoy/envoy-runtime.yaml)
if [[ -z "$A" || -z "$B" ]]; then
  guard_fail "public-lane route missing from an infra Envoy config" || FAILED=1
elif [[ "$A" != "$B" ]]; then
  guard_fail "public-lane route blocks differ between envoy.yaml and envoy-runtime.yaml (ENVOY-GATE)" || FAILED=1
else
  guard_pass "infra public-lane parity (ENVOY-GATE)"
fi

# --- x-original-method / x-original-path pairing (CP6) -------------------------------------
# These are DECISION INPUTS: they select which resource the PDP authorizes the request against.
# A client able to set them redirects the question rather than forging the answer, which is worse
# than spoofing x-actor-id. They must therefore be stripped everywhere x-actor-id is stripped.
#
# This is DEFENCE IN DEPTH, not the only cover. AuthorizeWireTest.aliasesAreStrippedAtTheEdge
# already reads this same chart and asserts exact-strip-line count equality against x-actor-id.
# (An earlier revision of this comment claimed that test does not read the chart. That was wrong
# — it was asserted from a truncated read of the file that stopped two lines short of the method.)
# The two differ usefully: that one is whole-file counts in the BFF suite, this one is per strip
# block in the blocking change-safety phase, so it also catches counts that match while a single
# block is unpaired.
#
# Asserted PER STRIP BLOCK. A whole-file count IS viable — AuthorizeWireTest does exactly that
# and passes — but only with an EXACT line match (l.strip().equals("- \"x-actor-id\"")). A
# substring grep for x-actor-id also catches regenerate/allowlist contexts and yields 5 where the
# strip lists hold 3, which is what made an earlier draft of this check fail on a correct chart.
# Per-block is kept because it is the strictly stronger statement: it also catches counts that
# balance across the file while one individual block is unpaired.
# Every Envoy config that carries ext_authz, not only the deployed chart. The infra/ copies are
# mounted by docker-compose.runtime.yml and never reach Kubernetes, so they are not a production
# exposure -- but a developer running that stack would exercise a WEAKER trust boundary than
# preview and see it pass, which is how a divergence becomes the thing someone later copies back
# into the chart. Two lines each; no reason to let them drift.
for CHART in deploy/helm/impilo-vnext/templates/envoy.yaml \
             infra/envoy/envoy.yaml \
             infra/envoy/envoy-runtime.yaml; do
if [[ -f "$CHART" ]]; then
  if bad=$(awk '
      /request_headers_to_remove:/ { blk=NR; in_strip=1; a=0; m=0; p=0; next }
      in_strip && /^[[:space:]]*- "[^"]+"/ {
        if ($0 ~ /"x-actor-id"/)        a=1
        if ($0 ~ /"x-original-method"/) m=1
        if ($0 ~ /"x-original-path"/)   p=1
        next
      }
      in_strip && /^[[:space:]]*(#|\{\{|$)/ { next }
      in_strip { if (a && (!m || !p)) printf "block@L%d strips x-actor-id but method=%d path=%d\n", blk, m, p; in_strip=0 }
      END { if (in_strip && a && (!m || !p)) printf "block@L%d strips x-actor-id but method=%d path=%d\n", blk, m, p }
    ' "$CHART") && [[ -n "$bad" ]]; then
    guard_fail "$CHART: $bad" || FAILED=1
  else
    guard_pass "$CHART pairs x-original-method/x-original-path in every x-actor-id strip block"
  fi
fi
done

if [[ "$FAILED" == "1" ]]; then
  exit 1
fi
guard_pass "check-visibility-header-strip complete"
