#!/usr/bin/env bash
# ext_authz upstream header allowlist guard.
#
# Envoy's HTTP ext_authz mode copies a header from the PDP's response onto the upstream
# request ONLY if that header is named in `allowed_upstream_headers`. Anything the PDP
# sets and the list omits is dropped in silence, and whatever the client sent under the
# same name survives in its place.
#
# gRPC ext_authz has no such list — it applies every header the PDP returns. The local
# dev and runtime configs use gRPC; the deployed Helm config uses HTTP. So a header the
# PDP starts setting works perfectly in dev and quietly does nothing in the preview
# estate, with no error anywhere. That asymmetry already cost this repo the authoritative
# identity re-injection: PolicyEngine overrode x-actor-id from the validated session
# specifically to defeat header-injection impersonation, and the override reached Envoy
# and stopped there.
#
# This guard reads what PolicyEngine.buildHeaderMutations actually puts, resolves the
# TrustHeaders constants to wire names, and fails if the Helm allowlist does not cover
# every one of them.
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

POLICY_ENGINE="services/tshepo-authz-service/src/main/java/zw/gov/mohcc/impilo/tshepo/authz/core/PolicyEngine.java"
TRUST_HEADERS="libs/tshepo-contracts/src/main/java/zw/gov/mohcc/impilo/tshepo/contracts/headers/TrustHeaders.java"
HELM_ENVOY="deploy/helm/impilo-vnext/templates/envoy.yaml"

for f in "$POLICY_ENGINE" "$TRUST_HEADERS" "$HELM_ENVOY"; do
  if [[ ! -f "$f" ]]; then
    guard_fail "missing $f — this guard cannot verify what it cannot read"
    exit 1
  fi
done

# Header names the PDP sets on an ALLOW, as TrustHeaders constant names.
mapfile -t CONSTANTS < <(
  awk '/private Map<String, String> buildHeaderMutations/,/^    }$/' "$POLICY_ENGINE" \
    | grep -o 'headers\.put(TrustHeaders\.[A-Z_]*' \
    | sed 's/.*TrustHeaders\.//' \
    | sort -u
)

if [[ ${#CONSTANTS[@]} -eq 0 ]]; then
  guard_fail "parsed zero headers out of buildHeaderMutations — the guard is broken, not the code"
  exit 1
fi

# Only the allowed_upstream_headers block counts. The same header name also appears in
# authorization_request.allowed_headers and in the public lane's request_headers_to_remove,
# so a file-wide grep passes on headers that are nowhere near the upstream allowlist — the
# first draft of this guard did exactly that and reported a clean bill on a broken config.
UPSTREAM_BLOCK=$(awk '
  /allowed_upstream_headers:/ { collecting = 1; next }
  collecting && /^[[:space:]]*-[[:space:]]*exact:/ { print; next }
  collecting && /^[[:space:]]*patterns:/ { next }
  collecting && /^[[:space:]]*#/ { next }
  collecting && NF { collecting = 0 }
' "$HELM_ENVOY")

if [[ -z "$UPSTREAM_BLOCK" ]]; then
  guard_fail "found no allowed_upstream_headers entries in $HELM_ENVOY — the guard is broken, not the code"
  exit 1
fi

MISSING=()
UNRESOLVED=()
for const in "${CONSTANTS[@]}"; do
  wire=$(grep -oE "String[[:space:]]+${const}[[:space:]]*=[[:space:]]*\"[^\"]+\"" "$TRUST_HEADERS" \
    | grep -oE '"[^"]+"' | tr -d '"' | head -1)
  if [[ -z "$wire" ]]; then
    UNRESOLVED+=("$const")
    continue
  fi
  if ! grep -qE "^[[:space:]]*-[[:space:]]*exact:[[:space:]]*\"${wire}\"[[:space:]]*$" <<<"$UPSTREAM_BLOCK"; then
    MISSING+=("$wire (TrustHeaders.$const)")
  fi
done

FAILED=0

if [[ ${#UNRESOLVED[@]} -gt 0 ]]; then
  guard_fail "could not resolve to a wire name: ${UNRESOLVED[*]}" || FAILED=1
fi

if [[ ${#MISSING[@]} -gt 0 ]]; then
  echo "GUARD FAIL  the PDP sets these on ALLOW but $HELM_ENVOY drops them before upstream:"
  for m in "${MISSING[@]}"; do echo "              - $m"; done
  echo "            Add each to authorization_response.allowed_upstream_headers."
  FAILED=1
else
  guard_pass "all ${#CONSTANTS[@]} PDP-set ALLOW headers survive to upstream in the Helm ext_authz allowlist"
fi

exit "$FAILED"
