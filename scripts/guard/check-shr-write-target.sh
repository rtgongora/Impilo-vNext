#!/usr/bin/env bash
# Governed FHIR writes must land in the SHR, not in the stock HAPI server beside it.
#
# The estate runs two R4 servers on port 8090. `butano-service` is the SHR: HAPI FHIR JPA with
# header validation, tenant enforcement, PII prevention, provenance stamping and terminology
# validation on the write path. `hapi-fhir` is stock `hapiproject/hapi` with none of that and no
# authentication at all. Measured 2026-08-08 from inside the namespace:
#
#   GET http://hapi-fhir:8090/fhir/Patient          no credential, no headers -> 200 + Bundle
#   GET http://butano-service:8090/fhir/Patient     no credential, no headers -> 403
#     "Missing mandatory trust headers: X-Tenant-Id, X-Correlation-Id, X-Actor-Id,
#      X-Actor-Type, X-Purpose-Of-Use"
#
# fhir-gateway-service holds the consent PEP, and `fhir_route` is empty in every environment
# measured -- so FHIR_GATEWAY_DEFAULT_TARGET_BASE is not a fallback, it is THE delivery target for
# every forward the gateway serves. Pointing it at the stock server meant the PEP evaluated
# consent and then handed the resource to a store any pod could read back without one.
#
# This guard pins the direction of that fix. It is deliberately narrow: it fails only on a FHIR
# *target* naming hapi-fhir, not on every mention of the name -- the NetworkPolicy that contains
# the stock server, and any document describing the split, must keep saying it.
set -uo pipefail
cd "$(dirname "$0")/../.."

FAIL=0
CHECKED=0
note() { printf '  %s\n' "$1"; }
fail() { printf 'FAIL: %s\n' "$1" >&2; FAIL=1; }

echo "=== SHR write-target guard ==="

# The env vars that decide where a FHIR resource is written. Each is a target, not a label.
TARGET_KEYS='FHIR_GATEWAY_DEFAULT_TARGET_BASE|FHIR_BASE_URL|HAPI_FHIR_URL'

# Files that legitimately set them: the preview values, the generated values, and the two
# generators that produce the generated values.
TARGET_FILES=(
  deploy/helm/impilo-vnext/values-full-preview.yaml
  deploy/helm/impilo-vnext/values-full-preview-runtime.generated.yaml
  deploy/helm/impilo-vnext/values-full-preview-bff-env.generated.yaml
  scripts/full-boot/generate-full-preview-runtime-values.mjs
  scripts/full-boot/generate-full-preview-bff-downstream-env.mjs
)

for f in "${TARGET_FILES[@]}"; do
  if [ ! -f "$f" ]; then
    fail "$f is missing — this guard cannot check a file that is not there"
    continue
  fi
  # Assignments only (KEY: value / KEY = "value"), so a sentence mentioning the variable in a
  # comment is not mistaken for a setting.
  ASSIGNMENTS=$(grep -nE "^[^#]*\b($TARGET_KEYS)\b[[:space:]]*[:=]" "$f" || true)
  if [ -z "$ASSIGNMENTS" ]; then
    continue
  fi
  while IFS= read -r line; do
    CHECKED=$((CHECKED + 1))
    if printf '%s' "$line" | grep -q 'hapi-fhir'; then
      fail "$f:${line%%:*} routes a FHIR write at the ungoverned stock server:"
      printf '    %s\n' "${line#*:}" >&2
    fi
  done <<< "$ASSIGNMENTS"
done

# A guard that finds nothing to check reports success. Anchor on the assignments actually
# expected: five files, and at least one target assignment in each of the three that carry them.
if [ "$CHECKED" -lt 4 ]; then
  fail "only $CHECKED FHIR target assignments found — expected at least 4. The keys or the file"
  fail "layout moved and this guard is now checking almost nothing."
else
  note "$CHECKED FHIR target assignments checked"
fi

# The positive half: the preview must actually name the SHR, not merely avoid naming hapi-fhir.
PREVIEW=deploy/helm/impilo-vnext/values-full-preview.yaml
if grep -qE "^[^#]*FHIR_GATEWAY_DEFAULT_TARGET_BASE:[[:space:]]*http://butano-service:8090/fhir" "$PREVIEW"; then
  note "gateway default target is butano-service (the SHR)"
else
  fail "$PREVIEW does not point FHIR_GATEWAY_DEFAULT_TARGET_BASE at http://butano-service:8090/fhir;"
  fail "an empty or absent value is NO_ROUTE for every governed write, which is not the fix either"
fi

if [ "$FAIL" -eq 0 ]; then
  echo "PASS: every governed FHIR write target is the SHR"
fi
exit "$FAIL"
