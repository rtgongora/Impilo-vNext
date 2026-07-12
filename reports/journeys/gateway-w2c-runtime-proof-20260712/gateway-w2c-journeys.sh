#!/usr/bin/env bash
# Gateway W2c journeys — gateway-emergency-anon. Requires the rig booted (rig-boot.sh).
# Writes evidence artifacts next to this script and prints PASS/FAIL per check.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
BFF=http://localhost:28462
DAI=http://localhost:28392
# Fixed test tenant sent as the inbound X-Tenant-ID on the anonymous BFF call; the trust-header
# forwarding interceptor propagates it to daidzai, so the row lands under this tenant and the
# direct dispatcher calls below use the same one. An anonymous guest is "no auth/actor" — the web
# client (apiClient.getV11Headers) still sends the 4 mandatory platform headers, so we mirror that.
TENANT=11111111-1111-1111-1111-111111111111
PASS=0; FAIL=0
ok(){ echo "PASS  $1"; PASS=$((PASS+1)); }
bad(){ echo "FAIL  $1"; FAIL=$((FAIL+1)); }

# Anonymous public web client: mandatory platform headers only, NO Authorization / X-Actor-ID.
# Populates the ANON array fresh (unique request/correlation ids) for each call.
mk_anon(){ ANON=(-H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: national-spine"
  -H "X-Request-ID: $(uuidgen)" -H "X-Correlation-ID: $(uuidgen)"
  -H "Idempotency-Key: $(uuidgen)" -H "Content-Type: application/json"); }

# Authenticated dispatcher/responder lane (direct to daidzai): full trust context, fresh
# Idempotency-Key + ids per call (daidzai rejects a reused key with IDENTITY_CONFLICT).
mk_dai(){ DAI_H=(-H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: national-spine"
  -H "X-Request-ID: $(uuidgen)" -H "X-Correlation-ID: $(uuidgen)"
  -H "X-Actor-ID: dispatcher-rig" -H "X-Actor-Type: STAFF"
  -H "Idempotency-Key: $(uuidgen)" -H "Content-Type: application/json"); }

echo "── Check 1: anonymous SOS POST (no auth) with callback → 202 + reference ──"
mk_anon
c1=$(curl -s -o "$HERE/c1-sos-response.json" -w '%{http_code}' -X POST "$BFF/internal/v1/public/gateway/sos" \
  "${ANON[@]}" -H "X-Forwarded-For: 41.0.0.1" \
  -d '{"callbackNumber":"0771 234 567","emergencyCategory":"MEDICAL","severity":"HIGH","description":"Man collapsed at the market","locationDescription":"Mbare Musika"}')
REF=$(python3 -c "import json;print(json.load(open('$HERE/c1-sos-response.json')).get('requestReference',''))" 2>/dev/null)
[ "$c1" = "202" ] && [ -n "$REF" ] && ok "202 + reference=$REF" || bad "expected 202+ref, got $c1 ref=$REF"

echo "── Check 1b: psql read-back (AWAITING_CALLBACK, unverified, PUBLIC_ANONYMOUS) ──"
docker exec gw2c-rig-pg psql -U impilo -d daidzai -A -F'|' -t \
  -c "SELECT request_reference,status,callback_verified,requester_type,callback_number,subject_identity_mode,channel FROM daidzai.dai_emergency_request WHERE request_reference='$REF';" \
  > "$HERE/c1-readback.txt" 2>&1
cat "$HERE/c1-readback.txt"
grep -q "|AWAITING_CALLBACK|f|PUBLIC_ANONYMOUS|+263771234567|ANONYMOUS|PUBLIC_WEB" "$HERE/c1-readback.txt" \
  && ok "row AWAITING_CALLBACK/unverified/PUBLIC_ANONYMOUS, callback normalized to E.164" \
  || bad "read-back row mismatch"

echo "── Check 2: POST without callbackNumber → 400, no row created ──"
BEFORE=$(docker exec gw2c-rig-pg psql -U impilo -d daidzai -A -t -c "SELECT count(*) FROM daidzai.dai_emergency_request;")
mk_anon
c2=$(curl -s -o "$HERE/c2-nocallback.json" -w '%{http_code}' -X POST "$BFF/internal/v1/public/gateway/sos" \
  "${ANON[@]}" -H "X-Forwarded-For: 41.0.0.2" \
  -d '{"emergencyCategory":"MEDICAL","description":"no number"}')
AFTER=$(docker exec gw2c-rig-pg psql -U impilo -d daidzai -A -t -c "SELECT count(*) FROM daidzai.dai_emergency_request;")
cat "$HERE/c2-nocallback.json"; echo
c2code=$(python3 -c "import json;print(json.load(open('$HERE/c2-nocallback.json')).get('error',{}).get('code',''))" 2>/dev/null)
[ "$c2" = "400" ] && [ "$c2code" = "VALIDATION" ] && [ "$BEFORE" = "$AFTER" ] \
  && ok "400 VALIDATION (missing callback) and row count unchanged ($BEFORE)" \
  || bad "expected 400 VALIDATION + no-row, got $c2 code=$c2code (before=$BEFORE after=$AFTER)"

# resolve the request id for the check-1 reference
REQ_ID=$(docker exec gw2c-rig-pg psql -U impilo -d daidzai -A -t -c "SELECT id FROM daidzai.dai_emergency_request WHERE request_reference='$REF';")

echo "── Check 3: triage the AWAITING_CALLBACK request → 409 (dispatch gate holds) ──"
mk_dai
c3=$(curl -s -o "$HERE/c3-triage-gated.json" -w '%{http_code}' -X POST "$DAI/internal/v1/daidzai/requests/$REQ_ID/triage" "${DAI_H[@]}")
cat "$HERE/c3-triage-gated.json"; echo
c3code=$(python3 -c "import json;print(json.load(open('$HERE/c3-triage-gated.json')).get('error',{}).get('code',''))" 2>/dev/null)
{ [ "$c3" = "409" ] || [ "$c3" = "422" ]; } && [ "$c3code" = "CALLBACK_VERIFICATION_REQUIRED" ] \
  && ok "$c3 CALLBACK_VERIFICATION_REQUIRED" || bad "expected 409 gate, got $c3 code=$c3code"

echo "── Check 4: dispatcher verify-callback → 200, then triage → succeeds (incident created) ──"
mk_dai
c4v=$(curl -s -o "$HERE/c4-verify.json" -w '%{http_code}' -X POST "$DAI/internal/v1/daidzai/requests/$REQ_ID/verify-callback" "${DAI_H[@]}")
cat "$HERE/c4-verify.json"; echo
mk_dai
c4t=$(curl -s -o "$HERE/c4-triage-ok.json" -w '%{http_code}' -X POST "$DAI/internal/v1/daidzai/requests/$REQ_ID/triage" "${DAI_H[@]}")
cat "$HERE/c4-triage-ok.json"; echo
INC=$(python3 -c "import json;print(json.load(open('$HERE/c4-triage-ok.json')).get('incidentReference',''))" 2>/dev/null)
[ "$c4v" = "200" ] && [ "$c4t" = "201" ] && [ -n "$INC" ] \
  && ok "verify 200 → triage 201, incident=$INC" || bad "verify=$c4v triage=$c4t incident=$INC"

echo "── Check 5: rate-limit — hammer public SOS → 429 + Retry-After at threshold ──"
RL_IP=41.0.0.5; hit429=0; retry=""
for n in $(seq 1 8); do
  mk_anon
  code=$(curl -s -o "$HERE/c5-hit-$n.json" -w '%{http_code}' -D "$HERE/c5-hdr-$n.txt" -X POST "$BFF/internal/v1/public/gateway/sos" \
    "${ANON[@]}" -H "X-Forwarded-For: $RL_IP" \
    -d '{"callbackNumber":"+263772000111","emergencyCategory":"MEDICAL"}')
  echo "  attempt $n → $code"
  if [ "$code" = "429" ]; then hit429=1; retry=$(grep -i '^retry-after:' "$HERE/c5-hdr-$n.txt" | tr -d '\r' | awk '{print $2}'); break; fi
done
[ "$hit429" = "1" ] && [ -n "$retry" ] && ok "429 with Retry-After=$retry at per-IP threshold (limit=5/600s)" || bad "no 429 observed (hit429=$hit429 retry=$retry)"

echo "── Check 6: naming — 202 body carries no internal service names ──"
mk_anon
curl -s -o "$HERE/c6-naming.json" -X POST "$BFF/internal/v1/public/gateway/sos" \
  "${ANON[@]}" -H "X-Forwarded-For: 41.0.0.6" \
  -d '{"callbackNumber":"+263773000222","emergencyCategory":"TRAUMA"}'
cat "$HERE/c6-naming.json"; echo
if grep -iqE 'daidzai|tshepo|vito|butano|tuso|keycloak|localhost|nhume|ndila|khuluma|pct|8392|28392' "$HERE/c6-naming.json"; then
  bad "internal name leaked in 202 body"
else
  ok "202 body clean of internal service names"
fi

echo
echo "==== gateway-emergency-anon: PASS=$PASS FAIL=$FAIL ===="
exit $([ "$FAIL" = "0" ] && echo 0 || echo 1)
