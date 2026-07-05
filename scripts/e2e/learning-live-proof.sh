#!/usr/bin/env bash
# =============================================================================
# W3 proof — LEARNING_LIVE completion driven by media truth, on the LIVE estate.
#
# Chain: course + ATTENDANCE_THRESHOLD completion rule (studio API) → LIVE
# scheduled session (LiveSessionIntegration schedules the Impilo Live event) →
# learner enrols → facilitator + learner hold REAL browser media in the
# classroom → webhook-accurate attendance accrues → browsers leave → room
# finishes → live event ENDS (W0 consumer) → impilo.live.attendance/event
# events → learning policy evaluates → enrolment COMPLETED → certificate.
# No client-reported attendance, no blind progress=100 anywhere.
# =============================================================================
set -euo pipefail

TENANT_ID="${TENANT_ID:-00000000-0000-4000-8000-000000000001}"
NS="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
FACILITY_ID="${FACILITY_ID:-f1000000-0000-0000-0000-000000000001}"
PREVIEW_URL="${PREVIEW_URL:-http://127.0.0.1}"
RUN="lliveproof-$(date +%s)"
LEARNER_ID="${LEARNER_ID:-PROV-ZW-00007}"

PASS=0
ok()   { PASS=$((PASS+1)); echo "  ok: $*"; }
fail() { echo "  FAIL: $*" >&2; exit 1; }
step() { echo ""; echo "== $*"; }
jq1()  { python3 -c "import json,sys; d=json.load(sys.stdin); $1"; }
lpsql() { kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -d learning -tAc "$1" 2>/dev/null || true; }
vpsql() { kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -d live -tAc "$1" 2>/dev/null || true; }

base_hdrs() { HDRS=(-H "Content-Type: application/json" -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-e2e" \
  -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN"); }
act_hdrs() { base_hdrs; HDRS+=(-H "X-Actor-ID: $1" -H "X-Actor-Type: PROVIDER" -H "X-Purpose-Of-Use: TRAINING" \
  -H "X-Facility-ID: $FACILITY_ID" -H "Authorization: Bearer $2" \
  -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid | cut -c1-8)"); }

LEARN="$PREVIEW_URL/internal/v1/learning/v11"

# ── 1. Studio: course + attendance completion rule + LIVE session ────────────
step "1. course + ATTENDANCE_THRESHOLD rule + LIVE session (studio APIs)"
base_hdrs
LOGIN=$(curl -s -X POST "$PREVIEW_URL/internal/v1/auth/login" "${HDRS[@]}" -H "Idempotency-Key: $RUN-l" \
  -d '{"email":"dr.mapfumo","password":"ImpiloTest123!"}')
TOKEN=$(echo "$LOGIN" | jq1 "print(d['data']['attributes']['token'])")
ANCHOR=$(echo "$LOGIN" | jq1 "print(d['data']['attributes']['user']['healthId'])")

act_hdrs "$ANCHOR" "$TOKEN"
COURSE=$(curl -s -X POST "$LEARN/catalog" "${HDRS[@]}" \
  -d "{\"code\":\"W3-$RUN\",\"title\":\"Live Classroom Proof $RUN\",\"description\":\"W3 media-truth completion\",\"category\":\"CLINICAL\",\"level\":\"FOUNDATION\",\"status\":\"PUBLISHED\",\"cpdEligible\":false}")
COURSE_ID=$(echo "$COURSE" | jq1 "
x=d.get('data') or {}
c=x.get('course') or x
print(c.get('id') or '')")
[[ -n "$COURSE_ID" ]] || fail "course create failed: $(echo "$COURSE" | head -c 300)"
ok "course created: $COURSE_ID"

act_hdrs "$ANCHOR" "$TOKEN"
RULE=$(curl -s -X POST "$LEARN/courses/$COURSE_ID/completion-rules" "${HDRS[@]}" \
  -d '{"ruleType":"ATTENDANCE_THRESHOLD","thresholdValue":1,"required":true}')
echo "$RULE" | grep -qi "ATTENDANCE_THRESHOLD" || fail "rule create failed: $(echo "$RULE" | head -c 300)"
ok "ATTENDANCE_THRESHOLD(1 min) rule attached"

STARTS=$(date -u -d "+1 minute" +%Y-%m-%dT%H:%M:%SZ)
ENDS=$(date -u -d "+2 hours" +%Y-%m-%dT%H:%M:%SZ)
act_hdrs "$ANCHOR" "$TOKEN"
SESSION=$(curl -s -X POST "$LEARN/sessions" "${HDRS[@]}" \
  -d "{\"courseId\":\"$COURSE_ID\",\"sessionType\":\"VIRTUAL\",\"sessionMode\":\"LIVE\",\"title\":\"W3 live class $RUN\",\"startsAt\":\"$STARTS\",\"endsAt\":\"$ENDS\",\"facilitator\":\"PROV-ZW-00001\"}")
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
[[ -n "$LIVE_EVENT_ID" ]] || fail "session has no live_event_id — LiveSessionIntegration did not schedule"
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

# ── 3. Classroom media hold (facilitator + learner, real browsers) ──────────
step "3. classroom media hold (real browsers, ~100s)"
HOLD_LOG=$(mktemp)
(cd "$(dirname "$0")/../../ui/one-ui-shell" && \
  PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_BASE_URL="$PREVIEW_URL" PREVIEW_SANDBOX_E2E=1 \
  CLASSROOM_SESSION_ID="$SESSION_ID" CLASSROOM_HOLD_MS=100000 \
  npx playwright test e2e/classroom-media-hold.spec.ts --reporter=list > "$HOLD_LOG" 2>&1) &
HOLD_PID=$!

ATTEND=""
for i in $(seq 1 40); do
  ATTEND=$(vpsql "SELECT count(*) FROM live.live_event_attendance WHERE event_id='$LIVE_EVENT_ID'" 2>/dev/null | tr -d '[:space:]')
  [[ "${ATTEND:-0}" -ge 2 ]] && break
  sleep 5
done
[[ "${ATTEND:-0}" -ge 2 ]] || { kill $HOLD_PID 2>/dev/null; tail -25 "$HOLD_LOG"; fail "live attendance never recorded both participants"; }
ok "live_event_attendance has $ATTEND participants (server-side truth)"

wait $HOLD_PID || { tail -25 "$HOLD_LOG"; fail "classroom hold failed"; }
ok "hold complete — browsers left the room"

# ── 4. Event-driven completion chain ─────────────────────────────────────────
step "4. room finish → event ENDED → attendance mapped → policy → COMPLETED"
EVENT_STATE=""
for i in $(seq 1 30); do
  EVENT_STATE=$(vpsql "SELECT status FROM live.live_events WHERE id='$LIVE_EVENT_ID'" | tr -d '[:space:]')
  [[ "$EVENT_STATE" == "ENDED" || "$EVENT_STATE" == "PROCESSING_REPLAY" || "$EVENT_STATE" == "PUBLISHED_REPLAY" ]] && break
  sleep 5
done
[[ -n "$EVENT_STATE" ]] || fail "live event state unreadable"
[[ "$EVENT_STATE" == "ENDED" || "$EVENT_STATE" == *REPLAY* ]] || fail "live event never ENDED (state $EVENT_STATE)"
ok "live event $EVENT_STATE via room-finished webhook"

MAPPED=""
for i in $(seq 1 24); do
  MAPPED=$(lpsql "SELECT count(*) FROM lrn_session_attendance WHERE session_id='$SESSION_ID'" | tr -d '[:space:]')
  [[ "${MAPPED:-0}" -ge 1 ]] && break
  sleep 5
done
[[ "${MAPPED:-0}" -ge 1 ]] || fail "attendance never mapped into lrn_session_attendance"
ok "$MAPPED attendance row(s) mapped into Fundo"

ENROL_STATE=""
for i in $(seq 1 24); do
  ENROL_STATE=$(lpsql "SELECT status FROM lrn_enrolment WHERE id='$ENROLMENT_ID'" | tr -d '[:space:]')
  [[ "$ENROL_STATE" == "COMPLETED" ]] && break
  sleep 5
done
[[ "$ENROL_STATE" == "COMPLETED" ]] || fail "enrolment never COMPLETED via attendance policy (state $ENROL_STATE)"
ok "enrolment COMPLETED by ATTENDANCE_THRESHOLD policy — media truth drove completion"

CERT=$(lpsql "SELECT certificate_number FROM lrn_certificate WHERE enrolment_id='$ENROLMENT_ID'" | tr -d '[:space:]')
[[ -n "$CERT" ]] || fail "no certificate issued for the completed enrolment"
ok "certificate issued: $CERT"

echo ""
echo "PASS: learning-live completion proven end-to-end ($PASS checks) — run tag $RUN"
