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
    in_strip && !/^[[:space:]]*- "/ {
      if (has_conf) found=1
      in_strip=0
    }
    END { exit(found ? 0 : 1) }
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

if [[ "$FAILED" == "1" ]]; then
  exit 1
fi
guard_pass "check-visibility-header-strip complete"
