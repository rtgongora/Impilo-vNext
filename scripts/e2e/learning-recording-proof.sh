#!/usr/bin/env bash
# =============================================================================
# W4 proof — LEARNING_RECORDING on the LIVE estate.
#
# Chain: course + WATCH_THRESHOLD(90) completion rule → LIVE session (Fundo
# webinar → LEARNING_LIVE room) → learner enrols → facilitator + learner hold
# REAL classroom media → facilitator records via governed egress → recording
# lands in MinIO + document-service → browsers leave → event ENDS → replay
# PUBLISHED with the catalogued artifact → learning adopts it as a Fundo media
# asset (impilo.live.replay.published.v1 consumer) → learner fetches the
# signed playback ref → watch-progress reaches the threshold → completion
# policy COMPLETES the enrolment → certificate. Watch truth drives completion.
# =============================================================================
set -euo pipefail

TENANT_ID="${TENANT_ID:-00000000-0000-4000-8000-000000000001}"
NS="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
FACILITY_ID="${FACILITY_ID:-f1000000-0000-0000-0000-000000000001}"
PREVIEW_URL="${PREVIEW_URL:-http://127.0.0.1}"
RUN="lrecproof-$(date +%s)"
LEARNER_ID="${LEARNER_ID:-PROV-ZW-00007}"
RTC="http://rtc-gateway-service:8196"

PASS=0
ok()   { PASS=$((PASS+1)); echo "  ok: $*"; }
fail() { echo "  FAIL: $*" >&2; exit 1; }
step() { echo ""; echo "== $*"; }
jq1()  { python3 -c "import json,sys; d=json.load(sys.stdin); $1"; }
lpsql() { kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -d learning -tAc "$1" 2>/dev/null || true; }
vpsql() { kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -d live -tAc "$1" 2>/dev/null || true; }
rpsql() { kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -d rtc_gateway -tAc "$1" 2>/dev/null || true; }

base_hdrs() { HDRS=(-H "Content-Type: application/json" -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-e2e" \
  -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN"); }
act_hdrs() { base_hdrs; HDRS+=(-H "X-Actor-ID: $1" -H "X-Actor-Type: PROVIDER" -H "X-Purpose-Of-Use: TRAINING" \
  -H "X-Facility-ID: $FACILITY_ID" -H "Authorization: Bearer $2" \
  -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid | cut -c1-8)"); }
svc_curl() { # svc_curl <method> <url> [body] — in-cluster via the BFF pod, using HDRS
  local method="$1" url="$2" body="${3:-}"
  kubectl exec -n "$NS" deploy/experience-bff -- curl -sS -X "$method" "$url" "${HDRS[@]}" \
    -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid | cut -c1-8)" ${body:+-d "$body"}
}

LEARN="$PREVIEW_URL/internal/v1/learning/v11"

# ── 1. Studio: course + WATCH_THRESHOLD rule + LIVE session ──────────────────
step "1. course + WATCH_THRESHOLD(90) rule + LIVE session"
base_hdrs
LOGIN=$(curl -s -X POST "$PREVIEW_URL/internal/v1/auth/login" "${HDRS[@]}" -H "Idempotency-Key: $RUN-l" \
  -d '{"email":"dr.mapfumo","password":"ImpiloTest123!"}')
TOKEN=$(echo "$LOGIN" | jq1 "print(d['data']['attributes']['token'])")
ANCHOR=$(echo "$LOGIN" | jq1 "print(d['data']['attributes']['user']['healthId'])")

act_hdrs "$ANCHOR" "$TOKEN"
COURSE=$(curl -s -X POST "$LEARN/catalog" "${HDRS[@]}" \
  -d "{\"code\":\"W4-$RUN\",\"title\":\"Recording Proof $RUN\",\"description\":\"W4 watch-truth completion\",\"category\":\"CLINICAL\",\"level\":\"FOUNDATION\",\"status\":\"PUBLISHED\",\"cpdEligible\":false}")
COURSE_ID=$(echo "$COURSE" | jq1 "
x=d.get('data') or {}
c=x.get('course') or x
print(c.get('id') or '')")
[[ -n "$COURSE_ID" ]] || fail "course create failed: $(echo "$COURSE" | head -c 300)"
ok "course created: $COURSE_ID"

act_hdrs "$ANCHOR" "$TOKEN"
RULE=$(curl -s -X POST "$LEARN/courses/$COURSE_ID/completion-rules" "${HDRS[@]}" \
  -d '{"ruleType":"WATCH_THRESHOLD","thresholdValue":90,"required":true}')
echo "$RULE" | grep -qi "WATCH_THRESHOLD" || fail "rule create failed: $(echo "$RULE" | head -c 300)"
ok "WATCH_THRESHOLD(90) rule attached"

STARTS=$(date -u -d "+1 minute" +%Y-%m-%dT%H:%M:%SZ)
ENDS=$(date -u -d "+2 hours" +%Y-%m-%dT%H:%M:%SZ)
act_hdrs "$ANCHOR" "$TOKEN"
SESSION=$(curl -s -X POST "$LEARN/sessions" "${HDRS[@]}" \
  -d "{\"courseId\":\"$COURSE_ID\",\"sessionType\":\"VIRTUAL\",\"sessionMode\":\"LIVE\",\"title\":\"W4 recorded class $RUN\",\"startsAt\":\"$STARTS\",\"endsAt\":\"$ENDS\",\"facilitator\":\"PROV-ZW-00001\"}")
SESSION_ID=$(echo "$SESSION" | jq1 "
x=d.get('data') or {}
s=x.get('session') or x
print(s.get('id') or '')")
[[ -n "$SESSION_ID" ]] || fail "session create failed: $(echo "$SESSION" | head -c 400)"

LIVE_EVENT_ID=""
for i in $(seq 1 6); do
  LIVE_EVENT_ID=$(lpsql "SELECT live_event_id FROM lrn_scheduled_learning_session WHERE id='$SESSION_ID'" | tr -d '[:space:]')
  [[ -n "$LIVE_EVENT_ID" && "$LIVE_EVENT_ID" != "NULL" ]] && break
  sleep 3
done
[[ -n "$LIVE_EVENT_ID" ]] || fail "session has no live_event_id"
ok "LIVE session $SESSION_ID → live event $LIVE_EVENT_ID"

# ── 2. Learner enrols ────────────────────────────────────────────────────────
step "2. learner enrolment"
act_hdrs "$ANCHOR" "$TOKEN"
ENROL=$(curl -s -X POST "$LEARN/enrolments" "${HDRS[@]}" \
  -d "{\"courseId\":\"$COURSE_ID\",\"subjectType\":\"PROVIDER\",\"subjectId\":\"$LEARNER_ID\",\"enrolmentType\":\"SELF\"}")
ENROLMENT_ID=$(echo "$ENROL" | jq1 "
x=d.get('data') or {}
e=x.get('enrolment') or x
print(e.get('id') or e.get('enrolmentId') or '')")
[[ -n "$ENROLMENT_ID" ]] || fail "enrolment failed: $(echo "$ENROL" | head -c 300)"
ok "learner $LEARNER_ID enrolled: $ENROLMENT_ID"

# ── 3. Classroom media hold + governed recording ─────────────────────────────
step "3. classroom media + facilitator recording (~60s)"
HOLD_LOG="${TMPDIR:-/tmp}/w4-classroom-hold-$RUN.log"
(cd "$(dirname "$0")/../../ui/one-ui-shell" && \
  PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL="$PREVIEW_URL" PREVIEW_SANDBOX_E2E=1 \
  CLASSROOM_SESSION_ID="$SESSION_ID" CLASSROOM_HOLD_MS=70000 \
  npx playwright test e2e/classroom-media-hold.spec.ts --reporter=list > "$HOLD_LOG" 2>&1) &
HOLD_PID=$!

RTC_SESSION=""
for i in $(seq 1 30); do
  RTC_SESSION=$(rpsql "SELECT id FROM rtc.rtc_sessions WHERE owning_ref='$LIVE_EVENT_ID' ORDER BY created_at DESC LIMIT 1" | tr -d '[:space:]')
  [[ -n "$RTC_SESSION" ]] && break
  sleep 4
done
[[ -n "$RTC_SESSION" ]] || { kill $HOLD_PID 2>/dev/null || true; fail "rtc session never provisioned for the live event"; }
ok "rtc session: $RTC_SESSION"

# wait for both parties to be publishing before recording
ATTEND=""
for i in $(seq 1 30); do
  ATTEND=$(vpsql "SELECT count(*) FROM live.live_event_attendance WHERE event_id='$LIVE_EVENT_ID'" | tr -d '[:space:]')
  [[ "${ATTEND:-0}" -ge 2 ]] && break
  sleep 4
done
[[ "${ATTEND:-0}" -ge 2 ]] || { kill $HOLD_PID 2>/dev/null || true; tail -30 "$HOLD_LOG" || true; fail "both participants never joined"; }
ok "both participants in the room"

act_hdrs "$ANCHOR" "$TOKEN"
REC=$(svc_curl POST "$RTC/internal/v1/rtc/sessions/$RTC_SESSION/recording/start" \
  '{"startedBy":"PROV-ZW-00001","startedByRole":"FACILITATOR"}')
echo "$REC" | grep -qiE "egress|REQUESTED|ACTIVE" || { kill $HOLD_PID 2>/dev/null || true; fail "recording start failed: $(echo "$REC" | head -c 400)"; }
ok "recording started (facilitator)"

sleep 35
act_hdrs "$ANCHOR" "$TOKEN"
svc_curl POST "$RTC/internal/v1/rtc/sessions/$RTC_SESSION/recording/stop" '{}' >/dev/null
ok "recording stopped"

wait $HOLD_PID || { tail -30 "$HOLD_LOG" || true; fail "classroom hold failed"; }
ok "hold complete — browsers left"

# ── 4. Replay published with catalogued artifact ─────────────────────────────
step "4. event ends → recording catalogued → replay PUBLISHED"
EVENT_STATE=""
for i in $(seq 1 36); do
  EVENT_STATE=$(vpsql "SELECT status FROM live.live_events WHERE id='$LIVE_EVENT_ID'" | tr -d '[:space:]')
  [[ "$EVENT_STATE" == "PUBLISHED_REPLAY" ]] && break
  sleep 5
done
[[ "$EVENT_STATE" == "PUBLISHED_REPLAY" ]] || fail "replay never PUBLISHED (state $EVENT_STATE)"
ok "live event PUBLISHED_REPLAY"

# ── 5. Learning adopts the replay as a Fundo media asset ─────────────────────
step "5. impilo.live.replay.published.v1 → lrn_media_asset adoption"
ASSET_ID=""
for i in $(seq 1 24); do
  ASSET_ID=$(lpsql "SELECT id FROM lrn_media_asset WHERE live_event_id='$LIVE_EVENT_ID'" | tr -d '[:space:]')
  [[ -n "$ASSET_ID" ]] && break
  sleep 5
done
[[ -n "$ASSET_ID" ]] || fail "replay was never adopted into lrn_media_asset"
ok "media asset adopted: $ASSET_ID"

# ── 6. Learner playback ref (signed URL) ─────────────────────────────────────
step "6. enrolment-gated playback ref resolves to a servable artifact"
act_hdrs "$ANCHOR" "$TOKEN"
PLAY=$(curl -s "$LEARN/media/$ASSET_ID/playback-ref?enrolmentId=$ENROLMENT_ID" "${HDRS[@]}")
PLAY_URL=$(echo "$PLAY" | jq1 "
x=d.get('data') or {}
a=x.get('asset') or x
print(a.get('playbackUrl') or a.get('signedUrl') or a.get('url') or '')")
[[ -n "$PLAY_URL" ]] || fail "playback-ref did not resolve: $(echo "$PLAY" | head -c 400)"
BYTES=$(kubectl exec -n "$NS" deploy/experience-bff -- curl -sS -o /dev/null -w "%{size_download}" "$PLAY_URL" 2>/dev/null || echo 0)
[[ "${BYTES:-0}" -gt 100000 ]] || fail "signed artifact fetch too small ($BYTES bytes)"
ok "artifact served via signed url ($BYTES bytes)"

# ── 7. Watch truth → threshold → completion → certificate ────────────────────
step "7. watch-progress ≥90% → policy completes → certificate"
act_hdrs "$ANCHOR" "$TOKEN"
WP=$(curl -s -X POST "$LEARN/media/$ASSET_ID/watch-progress" "${HDRS[@]}" \
  -d "{\"enrolmentId\":\"$ENROLMENT_ID\",\"positionSeconds\":55,\"watchedSeconds\":56,\"durationSeconds\":60}")
echo "$WP" | grep -qi "watchProgress" || fail "watch-progress upsert failed: $(echo "$WP" | head -c 300)"
ok "watch progress recorded (93%)"

ENROL_STATE=""
for i in $(seq 1 24); do
  ENROL_STATE=$(lpsql "SELECT status FROM lrn_enrolment WHERE id='$ENROLMENT_ID'" | tr -d '[:space:]')
  [[ "$ENROL_STATE" == "COMPLETED" ]] && break
  sleep 5
done
[[ "$ENROL_STATE" == "COMPLETED" ]] || fail "enrolment never COMPLETED via WATCH_THRESHOLD (state $ENROL_STATE)"
ok "enrolment COMPLETED by WATCH_THRESHOLD policy — watch truth drove completion"

CERT=$(lpsql "SELECT certificate_number FROM lrn_certificate WHERE enrolment_id='$ENROLMENT_ID'" | tr -d '[:space:]')
[[ -n "$CERT" ]] || fail "no certificate issued"
ok "certificate issued: $CERT"

echo ""
echo "PASS: learning-recording proven end-to-end ($PASS checks) — run tag $RUN"
