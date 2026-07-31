#!/usr/bin/env bash
# J-EP-5..9 — emergency episode spine contracts against a live local estate.
#
# Pathway content proofs stay in emergency-pathway-integrity.sh (J-EP-1..4).
# This script exercises the HTTP surfaces the browser drive also uses.
#
#   bash scripts/dev/emergency-drive-rig.sh up   # or any PCT+BFF on the URLs below
#   bash scripts/runtime-proof/emergency-episode-journeys.sh
#
set -euo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

BFF_BASE="${DRIVE_BFF_URL:-http://127.0.0.1:8160}"
FACILITY_ID="${DRIVE_FACILITY_ID:-11111111-1111-1111-1111-111111111111}"
# Canonical seed tenant UUID (api-client.ts / V2 seed) — slug "mohcc" is rejected by pct.
TENANT="${DRIVE_TENANT_ID:-00000000-0000-4000-8000-000000000001}"
HEALTH_ID="${DRIVE_HEALTH_ID:-local-drive-provider-001}"

HDR=(-H "Content-Type: application/json"
     -H "X-Tenant-ID: ${TENANT}"
     -H "X-Pod-ID: national-spine"
     -H "X-Request-ID: j-ep-spine-$(date +%s)"
     -H "X-Correlation-ID: j-ep-spine"
     -H "X-Facility-ID: ${FACILITY_ID}"
     -H "X-Actor-ID: ${HEALTH_ID}"
     -H "X-Actor-Type: PROVIDER"
     -H "Idempotency-Key: j-ep-$(uuidgen 2>/dev/null || echo "j-ep-$RANDOM")")

pass=0
fail=0
say() { printf '%s\n' "$*"; }
ok() { say "  PASS  $*"; pass=$((pass + 1)); }
bad() { say "  FAIL  $*"; fail=$((fail + 1)); }

say "=== J-EP episode spine journeys ==="
say "BFF: ${BFF_BASE}"

# Probe
if ! curl -fsS -o /dev/null --max-time 5 "${BFF_BASE}/actuator/health" 2>/dev/null \
   && ! curl -fsS -o /dev/null --max-time 5 "${BFF_BASE}/internal/v1/emergency-episodes/command-summary?facilityId=${FACILITY_ID}" "${HDR[@]}" 2>/dev/null; then
  # command-summary may 401/500 without actuator — try it as reachability
  code=$(curl -sS -o /tmp/j-ep-probe.json -w "%{http_code}" --max-time 8 \
    "${BFF_BASE}/internal/v1/emergency-episodes/command-summary?facilityId=${FACILITY_ID}" "${HDR[@]}" || true)
  if [[ -z "${code}" || "${code}" == "000" ]]; then
    say "Estate not reachable at ${BFF_BASE} — skip J-EP-5..9 (boot emergency-drive-rig.sh)"
    exit 0
  fi
fi

say "J-EP-5  activation POST mints an emergency episode (201)"
body=$(curl -sS -w "\n%{http_code}" --max-time 30 \
  -X POST "${BFF_BASE}/internal/v1/emergency-episodes" \
  "${HDR[@]}" \
  -H "Idempotency-Key: j-ep5-$(date +%s%N)" \
  -d "{\"entryRoute\":\"AMBULANCE\",\"episodeClass\":\"TRAUMA\",\"facilityId\":\"${FACILITY_ID}\"}" || true)
code=$(printf '%s' "$body" | tail -n1)
json=$(printf '%s' "$body" | sed '$d')
if [[ "$code" == "201" ]]; then
  episode_id=$(printf '%s' "$json" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',d).get('episodeId') or d.get('data',d).get('episode_id') or d.get('episodeId') or '')" 2>/dev/null || true)
  if [[ -n "${episode_id}" ]]; then
    ok "J-EP-5 episode=${episode_id}"
  else
    bad "J-EP-5 201 but no episode id in body"
  fi
else
  bad "J-EP-5 expected 201 got ${code}"
  episode_id=""
fi

say "J-EP-6  command summary returns a board payload"
code=$(curl -sS -o /tmp/j-ep6.json -w "%{http_code}" --max-time 20 \
  "${BFF_BASE}/internal/v1/emergency-episodes/command-summary?facilityId=${FACILITY_ID}" "${HDR[@]}" || true)
if [[ "$code" == "200" ]] && python3 -c "import json; d=json.load(open('/tmp/j-ep6.json')); assert 'items' in d or 'data' in d or 'error' in d" 2>/dev/null; then
  ok "J-EP-6 status=${code}"
else
  bad "J-EP-6 status=${code}"
fi

say "J-EP-7  disposition GET for undisposed episode is 200 (absence ≠ 404)"
if [[ -n "${episode_id:-}" ]]; then
  code=$(curl -sS -o /tmp/j-ep7.json -w "%{http_code}" --max-time 20 \
    "${BFF_BASE}/internal/v1/emergency-episodes/${episode_id}/disposition" "${HDR[@]}" || true)
  if [[ "$code" == "200" ]]; then
    ok "J-EP-7 disposition absence readable as 200"
  else
    bad "J-EP-7 expected 200 got ${code} (404 would collapse absence into unreadability)"
  fi
else
  say "  SKIP  J-EP-7 (no episode from J-EP-5)"
fi

say "J-EP-8  honest-open contract documented (unit suite is SoR)"
# Structural check: the best-effort method still exists and comments the failure mode.
if rg -q "recordEpisodeDispositionBestEffort" \
     services/pct-service/src/main/java/zw/gov/mohcc/impilo/pct/core/EdVisitService.java \
  && rg -q "stays open|stay openly|honest" \
     services/pct-service/src/main/java/zw/gov/mohcc/impilo/pct/core/EdVisitService.java; then
  ok "J-EP-8 honest-open contract present in EdVisitService"
else
  bad "J-EP-8 honest-open contract missing from EdVisitService"
fi

say "J-EP-9  analytics UI names live keys and NOT_COMPUTABLE absences (no zero grid)"
page=ui/one-ui-shell/src/app/clinical/emergency/analytics/page.tsx
if rg -q "emergency-episode-summary" "$page" \
  && rg -q "analytics-w17-live" "$page" \
  && rg -q "analytics-pending-measures|ABSENT_MEASURES|Still not computable" "$page" \
  && ! rg -q "deaths:\s*0|mortality:\s*0" "$page"; then
  ok "J-EP-9 analytics honesty shape"
else
  bad "J-EP-9 analytics page missing live keys or reintroduced zero claims"
fi

say ""
say "J-EP spine: ${pass} passed, ${fail} failed"
[[ "$fail" -eq 0 ]]
