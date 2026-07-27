#!/usr/bin/env bash
# Q2 legitimacy fixture (PO ruling 2026-07-27, relayed via coordinator).
# Stamps a SMALL NAMED SET of real Ministry facilities with a PLATFORM_OPERATIONAL
# GOVERNMENT_OPERATIONAL_EXCEPTION (allowed_on_platform=true) through the real audited
# tuso endpoint, so maternity/find-care/EmONC proofs get positives. This is a PREVIEW
# TEST FIXTURE, not a regulatory determination.
#
# The fail-closed default must SURVIVE: an unstamped neighbour stays operational=false.
set -uo pipefail
NS=impilo-full-preview
REGISTRY_PLANE=00000000-0000-0000-0000-000000000001
ACTOR="preview-fixture-q2-legitimacy"
REASON="PREVIEW TEST FIXTURE (PO Q2 ruling 2026-07-27): platform-operational stamp for proof enablement only. NOT a regulatory determination and says nothing about this facility's real-world operating status."
EVIDENCE="PREVIEW_FIXTURE:Q2_LEGITIMACY:2026-07-27"

TUSO(){ kubectl -n $NS exec -i deploy/tuso-service -- curl -s "$@"; }
H(){ echo "-H Content-Type:application/json -H X-Tenant-ID:$REGISTRY_PLANE -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Actor-ID:$ACTOR -H X-Actor-Type:SYSTEM -H X-Purpose-Of-Use:ADMINISTRATION -H X-Facility-ID:$(uuidgen)"; }

# uuid|id|code|name  — the named fixture set (2 tertiary + 3 provincial, 5 provinces, all CEMONC-capable)
SET=(
  "9b222c87-e96a-5f6e-80eb-00b55e40ab2e|344|ZW000E0E|Parirenyatwa Group of Hospitals"
  "ccd6a682-3ec9-5953-a788-27d6644df2e3|1750|ZW090A0D|Mpilo Central Hospital"
  "7eae1583-562b-5a97-91ed-65cebdf407dc|1557|ZW07040A|Gweru Provincial Hospital"
  "d80e2e3d-642a-5134-a910-35901510e0fd|1017|ZW08050A|Masvingo Provincial Hospital"
  "c98b1da4-8bee-5d85-b992-c261787011ba|476|ZW01440A|Chinhoyi Provincial Hospital"
)
NEG_ID=1771; NEG_NAME="United Bulawayo Hospital (negative control — stays unstamped)"

op(){ TUSO $(H) "http://localhost:8084/v1/internal/facilities/$1/status-summary" | grep -o '"operational":[a-z]*' | head -1; }

echo "=================== BEFORE ==================="
for row in "${SET[@]}"; do IFS='|' read -r uuid id code name <<<"$row"; echo "  $name [$code]: $(op "$id")"; done
echo "  $NEG_NAME: $(op "$NEG_ID")"

echo "=================== STAMP (audited PUT PLATFORM_OPERATIONAL) ==================="
for row in "${SET[@]}"; do
  IFS='|' read -r uuid id code name <<<"$row"
  body=$(printf '{"status":"GOVERNMENT_OPERATIONAL_EXCEPTION","allowedOnPlatform":true,"evidenceRef":"%s","reason":"%s"}' "$EVIDENCE" "$REASON")
  code_http=$(TUSO -o /dev/null -w "%{http_code}" -X PUT $(H) -d "$body" \
    "http://localhost:8084/v1/internal/facilities/$uuid/source-legitimacy/PLATFORM_OPERATIONAL")
  echo "  $name [$code]: PUT -> HTTP $code_http"
done

echo "=================== AFTER ==================="
for row in "${SET[@]}"; do IFS='|' read -r uuid id code name <<<"$row"; echo "  $name [$code]: $(op "$id")"; done
echo "  $NEG_NAME: $(op "$NEG_ID")   <-- MUST still be false (fail-closed survives)"
