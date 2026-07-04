#!/usr/bin/env bash
# =============================================================================
# W1 proof — recording/egress pipeline on the LIVE estate.
#
# Provisions a governed teleconsult, holds REAL two-party browser media
# (session-media-hold.spec.ts), then: policy gate (PATIENT refused) → PROVIDER
# starts recording → LiveKit Egress → MinIO artifact → rtc.recordings COMPLETE
# via the egress webhook → document-service register-external adoption →
# signed-url fetch proves the artifact exists and is served.
# =============================================================================
set -euo pipefail

TENANT_ID="${TENANT_ID:-00000000-0000-4000-8000-000000000001}"
NS="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
FACILITY_ID="${FACILITY_ID:-f1000000-0000-0000-0000-000000000001}"
PREVIEW_URL="${PREVIEW_URL:-http://127.0.0.1}"
RUN="recproof-$(date +%s)"
RTC="http://rtc-gateway-service:8196"
DOCS="http://document-service:8093"

PASS=0
ok()   { PASS=$((PASS+1)); echo "  ok: $*"; }
fail() { echo "  FAIL: $*" >&2; exit 1; }
step() { echo ""; echo "== $*"; }
jq1()  { python3 -c "import json,sys; d=json.load(sys.stdin); $1"; }
rpsql() { kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -d rtc_gateway -tAc "$1"; }

base_hdrs() { HDRS=(-H "Content-Type: application/json" -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-e2e" \
  -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN"); }
act_hdrs() { base_hdrs; HDRS+=(-H "X-Actor-ID: $1" -H "X-Actor-Type: PROVIDER" -H "X-Purpose-Of-Use: TREATMENT" \
  -H "X-Facility-ID: $FACILITY_ID" -H "Authorization: Bearer $2"); }
svc_curl() { # svc_curl <method> <url> <headers-array-name> [body] — via BFF pod (cluster network)
  local method="$1" url="$2"; shift 2
  kubectl exec -n "$NS" deploy/experience-bff -- curl -sS -X "$method" "$url" "${HDRS[@]}" \
    -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid | cut -c1-8)" ${1:+-d "$1"}
}

# ── 1. Provision a governed teleconsult (consented, pool-accepted) ───────────
step "1. provision teleconsult (consent + pool accept)"
base_hdrs
LOGIN=$(curl -s -X POST "$PREVIEW_URL/internal/v1/auth/login" "${HDRS[@]}" -H "Idempotency-Key: $RUN-l" \
  -d '{"email":"dr.mapfumo","password":"ImpiloTest123!"}')
TOKEN=$(echo "$LOGIN" | jq1 "print(d['data']['attributes']['token'])")
ANCHOR=$(echo "$LOGIN" | jq1 "print(d['data']['attributes']['user']['healthId'])")
base_hdrs
LOGIN_B=$(curl -s -X POST "$PREVIEW_URL/internal/v1/auth/login" "${HDRS[@]}" -H "Idempotency-Key: $RUN-lb" \
  -d '{"email":"nurse.chienda","password":"ImpiloTest123!"}')
TOKEN_B=$(echo "$LOGIN_B" | jq1 "print(d['data']['attributes']['token'])")
ANCHOR_B=$(echo "$LOGIN_B" | jq1 "print(d['data']['attributes']['user']['healthId'])")

act_hdrs "$ANCHOR" "$TOKEN"
PAT=$(curl -s -X POST "$PREVIEW_URL/internal/v1/patients" "${HDRS[@]}" -H "Idempotency-Key: $RUN-p" \
  -d "{\"given_name\":\"RecProof\",\"family_name\":\"$RUN\",\"date_of_birth\":\"1992-01-01\",\"sex\":\"MALE\",\"phone\":\"+263774$(date +%N | cut -c1-6)\",\"facility_id\":\"$FACILITY_ID\"}")
CPID=$(echo "$PAT" | jq1 "x=d['data']; a=x.get('attributes',x); print(a.get('cpid') or a.get('patient_id') or x.get('id'))")
act_hdrs "$ANCHOR" "$TOKEN"
TELE=$(curl -s -X POST "$PREVIEW_URL/internal/v1/teleconsult/sessions" "${HDRS[@]}" -H "Idempotency-Key: $RUN-t" \
  -d "{\"patientId\":\"$CPID\",\"urgency\":\"routine\",\"specialty\":\"GENERAL_MEDICINE\",\"clinicalQuestion\":\"Recording proof\",\"virtualMode\":\"video\"}")
TID=$(echo "$TELE" | jq1 "x=d['data']; print(x.get('referralId') or x.get('id'))")
[[ -n "$TID" ]] || fail "teleconsult not created: $(echo "$TELE" | head -c 300)"
act_hdrs "$ANCHOR" "$TOKEN"
curl -s -X PUT "$PREVIEW_URL/internal/v1/teleconsult/sessions/$TID/referral" "${HDRS[@]}" -H "Idempotency-Key: $RUN-r" \
  -d '{"routingType":"SPECIALTY_POOL","routingTarget":"GENERAL_MEDICINE","clinicalQuestion":"Recording proof"}' >/dev/null
act_hdrs "$ANCHOR" "$TOKEN"
curl -s -X POST "$PREVIEW_URL/internal/v1/teleconsult/sessions/$TID/consent" "${HDRS[@]}" -H "Idempotency-Key: $RUN-c" \
  -d "{\"patientId\":\"$CPID\",\"type\":\"TELEMEDICINE\"}" >/dev/null
act_hdrs "$ANCHOR" "$TOKEN"
curl -s -X POST "$PREVIEW_URL/internal/v1/teleconsult/sessions/$TID/submit" "${HDRS[@]}" -H "Idempotency-Key: $RUN-s" -d '{}' >/dev/null
act_hdrs "$ANCHOR_B" "$TOKEN_B"
curl -s -X POST "$PREVIEW_URL/internal/v1/teleconsult/sessions/$TID/accept" "${HDRS[@]}" -H "Idempotency-Key: $RUN-a" -d '{}' >/dev/null
ok "teleconsult $TID provisioned"

# ── 2. Hold real two-party media in browsers ─────────────────────────────────
step "2. two-party browser media hold (90s)"
HOLD_LOG=$(mktemp)
(cd "$(dirname "$0")/../../ui/one-ui-shell" && \
  PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL="$PREVIEW_URL" PREVIEW_SANDBOX_E2E=1 \
  MEDIA_HOLD_SESSION_ID="$TID" MEDIA_HOLD_MS=90000 \
  npx playwright test e2e/session-media-hold.spec.ts --reporter=list > "$HOLD_LOG" 2>&1) &
HOLD_PID=$!

JOINED=""
for i in $(seq 1 36); do
  JOINED=$(rpsql "SELECT count(DISTINCT participant_identity) FROM rtc.session_events
    WHERE session_id='$TID' AND event_type='participant_joined'" | tr -d '[:space:]')
  [[ "${JOINED:-0}" -ge 2 ]] && break
  sleep 5
done
[[ "${JOINED:-0}" -ge 2 ]] || { kill $HOLD_PID 2>/dev/null; tail -20 "$HOLD_LOG"; fail "participants never joined the room"; }
ok "both participants active in the room (webhook-verified)"

# ── 3. Recording policy gate: PATIENT refused ────────────────────────────────
step "3. policy gate — PATIENT may not start a telemedicine recording"
act_hdrs "$ANCHOR" "$TOKEN"
DENY_CODE=$(kubectl exec -n "$NS" deploy/experience-bff -- curl -sS -o /dev/null -w "%{http_code}" -X POST \
  "$RTC/internal/v1/rtc/sessions/$TID/recording/start" "${HDRS[@]}" -H "Idempotency-Key: $RUN-deny" \
  -d "{\"startedBy\":\"$CPID\",\"startedByRole\":\"PATIENT\"}")
[[ "$DENY_CODE" =~ ^4 ]] || fail "PATIENT recording start was not refused (HTTP $DENY_CODE)"
ok "PATIENT refused (HTTP $DENY_CODE)"

# ── 4. PROVIDER starts recording → egress ACTIVE ─────────────────────────────
step "4. PROVIDER starts recording (consent-gated)"
act_hdrs "$ANCHOR" "$TOKEN"
REC=$(svc_curl POST "$RTC/internal/v1/rtc/sessions/$TID/recording/start" HDRS \
  "{\"startedBy\":\"$ANCHOR\",\"startedByRole\":\"PROVIDER\"}")
echo "  start: $(echo "$REC" | head -c 200)"
echo "$REC" | grep -qiE "egress|REQUESTED|ACTIVE" || fail "recording start failed: $(echo "$REC" | head -c 400)"
ok "recording started"

sleep 30

step "5. stop recording → egress finalizes → webhook completes the row"
act_hdrs "$ANCHOR" "$TOKEN"
svc_curl POST "$RTC/internal/v1/rtc/sessions/$TID/recording/stop" HDRS '{}' >/dev/null
REC_ROW=""
for i in $(seq 1 24); do
  REC_ROW=$(rpsql "SELECT status || '|' || COALESCE(storage_bucket,'') || '|' || COALESCE(storage_key,'')
    FROM rtc.recordings WHERE session_id='$TID' ORDER BY id DESC LIMIT 1" | tr -d '[:space:]')
  [[ "$REC_ROW" == COMPLETE* ]] && break
  sleep 5
done
[[ "$REC_ROW" == COMPLETE* ]] || fail "recording never reached COMPLETE (row: $REC_ROW)"
BUCKET=$(echo "$REC_ROW" | cut -d'|' -f2)
KEY=$(echo "$REC_ROW" | cut -d'|' -f3)
[[ -n "$BUCKET" && -n "$KEY" ]] || fail "COMPLETE without storage location: $REC_ROW"
ok "recording COMPLETE → s3://$BUCKET/$KEY"

# ── 6. Artifact adoption + physical playback proof ───────────────────────────
step "6. document-service adopts the artifact → signed-url fetch"
act_hdrs "$ANCHOR" "$TOKEN"
REG=$(svc_curl POST "$DOCS/v1/internal/objects/register-external" HDRS \
  "{\"bucket\":\"$BUCKET\",\"key\":\"$KEY\",\"contentType\":\"video/mp4\",\"ownerService\":\"PCT\",\"title\":\"Recording proof $RUN\"}")
OBJ_ID=$(echo "$REG" | jq1 "
x=d.get('data') or d
a=x.get('attributes', x) if isinstance(x,dict) else {}
print(a.get('objectId') or a.get('id') or x.get('id') or '')")
[[ -n "$OBJ_ID" ]] || fail "register-external returned no object id: $(echo "$REG" | head -c 400)"
ok "artifact catalogued as object $OBJ_ID"

act_hdrs "$ANCHOR" "$TOKEN"
SIGNED=$(svc_curl GET "$DOCS/v1/internal/objects/$OBJ_ID/signed-url" HDRS)
URL=$(echo "$SIGNED" | jq1 "
x=d.get('data') or d
a=x.get('attributes', x) if isinstance(x,dict) else {}
print(a.get('url') or a.get('signedUrl') or a.get('signed_url') or '')")
[[ -n "$URL" ]] || fail "no signed url: $(echo "$SIGNED" | head -c 300)"
FETCH=$(kubectl exec -n "$NS" deploy/experience-bff -- curl -sS -o /dev/null -w "%{http_code}|%{size_download}" "$URL")
CODE=$(echo "$FETCH" | cut -d'|' -f1); SIZE=$(echo "$FETCH" | cut -d'|' -f2)
[[ "$CODE" == "200" && "${SIZE:-0}" -gt 10000 ]] || fail "artifact fetch failed (HTTP $CODE, $SIZE bytes)"
ok "artifact fetched via signed url ($SIZE bytes of real mp4)"

wait $HOLD_PID || { tail -20 "$HOLD_LOG"; fail "media holder failed"; }
ok "media holder exited clean"

echo ""
echo "PASS: recording/egress pipeline proven end-to-end ($PASS checks) — run tag $RUN"
