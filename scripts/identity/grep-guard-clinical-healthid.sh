#!/usr/bin/env bash
# Identity Contract §7 CI guard (Wave B5):
# Clinical-plane services must not reference Health IDs in production source.
# Actor/worker identity (providers, staff) legitimately uses the HID and is
# allowlisted per line via the marker comment  // actor-plane HID  where needed.
set -euo pipefail
cd "$(dirname "$0")/../.."

# Clinical-plane services (subject data keyed by CPID only)
CLINICAL=(
  services/pct-service
  services/inpatient-service
  services/pharmacy-service
  services/oros-service
  services/madi-service
  services/booking-service
  services/indawo-service
  services/patient-safety-service
  services/rito-quality-safety-service
  services/butano-service
  services/butano-fhir
  services/fhir-gateway-service
)

PATTERN='healthId|health_id|HealthId'

fail=0
for svc in "${CLINICAL[@]}"; do
  [ -d "$svc/src/main" ] || continue
  # Hits, excluding allowlisted lines and pure-comment mentions of the contract rule
  hits=$(grep -rnE "$PATTERN" "$svc/src/main" \
      --include='*.java' \
      | grep -v 'actor-plane HID' \
      | grep -viE '^\S+:[0-9]+:\s*(\*|//|--).*(never|not a|no longer|must not|retired|contract)' \
      || true)
  if [ -n "$hits" ]; then
    echo "VIOLATION in $svc:"
    echo "$hits" | head -20
    fail=1
  fi
done

if [ "$fail" -eq 0 ]; then
  echo "grep-guard PASS: no Health ID references in clinical-plane production source"
else
  echo ""
  echo "grep-guard FAIL: clinical services must key subjects by CPID (Identity Contract §7)."
  echo "If a hit is legitimate actor/worker identity, annotate the line with: // actor-plane HID"
  exit 1
fi
