#!/usr/bin/env bash
# IMAM live proof — through the real ingress, on the endpoints the UI actually calls.
#
# The screens at /ehr/[patientId]/imam and /clinical/nutrition-tracing read and write exactly
# these paths, so this is the deterministic proof of the vertical the UI sits on: enrolment
# routed from an IMNCI classification, the discharge rule in both directions, the tracing
# worklist, and the refusal to certify a cure on criteria that were not met.
set -uo pipefail
BASE="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
TENANT="${TENANT_ID:-00000000-0000-0000-0000-000000000001}"
RESOLVE_IP="${PREVIEW_RESOLVE_IP:-10.50.1.67}"
RES=(--resolve "impilo.mohcc.gov.zw:443:${RESOLVE_IP}")
RUN="imam-$(date +%s)"
PASS=0; FAIL=0
ccurl(){ curl -sk -m20 "${RES[@]}" "$@"; }
ok(){ PASS=$((PASS+1)); echo "  ok: $*"; }
bad(){ FAIL=$((FAIL+1)); echo "  FAIL: $*"; }
jq_py(){ python3 -c "$1" 2>/dev/null; }

TOK=$(ccurl -X POST "$BASE/internal/v1/auth/login" -H 'Content-Type: application/json' \
  -H "X-Tenant-ID: $TENANT" -H 'X-Pod-ID: national-spine' \
  -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" \
  -H "Idempotency-Key: $RUN-login" \
  -d '{"email":"nurse.chienda","password":"ImpiloTest123!"}' \
  | jq_py "import json,sys;print(json.load(sys.stdin)['data']['attributes']['token'])")
[ -n "$TOK" ] && ok "clinical login" || { bad "login"; exit 1; }

mkh(){ HDRS=(-H "Content-Type: application/json" -H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: national-spine"
              -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN"
              -H "Authorization: Bearer $TOK" -H "X-Actor-Type: PROVIDER"
              -H "X-Purpose-Of-Use: TREATMENT" -H "X-Actor-ID: nurse.chienda"
              -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid | cut -c1-8)"); }

echo "== 1. the tracing worklist the /clinical/nutrition-tracing screen reads =="
mkh; QUEUE=$(ccurl "$BASE/internal/v1/imam/tracing-queue" "${HDRS[@]}")
ROWS=$(printf '%s' "$QUEUE" | jq_py "import json,sys;print(len(json.load(sys.stdin).get('data') or []))")
[ "${ROWS:-0}" -ge 1 ] && ok "worklist returns $ROWS overdue child(ren)" || bad "worklist empty or unreadable: $QUEUE"
NEVER=$(printf '%s' "$QUEUE" | jq_py "import json,sys;print(sum(1 for r in (json.load(sys.stdin).get('data') or []) if r.get('never_reviewed')))")
[ "${NEVER:-0}" -ge 1 ] && ok "never-reviewed children are flagged separately ($NEVER)" \
  || bad "no never_reviewed flag present — the most urgent row would look like any other"

# The child whose episode has at least one review; used for the review write below.
TARGET=$(printf '%s' "$QUEUE" | jq_py "import json,sys
rows=json.load(sys.stdin).get('data') or []
seen=[r for r in rows if not r.get('never_reviewed')]
print((seen or rows)[0]['imam_episode_id'] if (seen or rows) else '')")
TARGET_PID=$(printf '%s' "$QUEUE" | jq_py "import json,sys
rows=json.load(sys.stdin).get('data') or []
seen=[r for r in rows if not r.get('never_reviewed')]
print((seen or rows)[0]['patient_id'] if (seen or rows) else '')")
[ -n "$TARGET" ] && ok "target episode $TARGET" || { bad "no episode to work with"; exit 1; }

echo "== 2. the episode detail the /ehr/[patientId]/imam screen reads =="
mkh; EP=$(ccurl "$BASE/internal/v1/imam/episodes/$TARGET" "${HDRS[@]}")
printf '%s' "$EP" | jq_py "import json,sys
d=json.load(sys.stdin)['data']; a=d.get('assessment') or {}
assert d.get('programme'), 'no programme'
assert a.get('discharge_criteria') is not None, 'no discharge criteria in the assessment'
assert 'discharge_eligible' in a, 'no discharge verdict'
print('')" && ok "episode detail carries the live assessment and its criteria" \
  || bad "episode detail missing the assessment the screen renders"
printf '%s' "$EP" | jq_py "import json,sys
a=json.load(sys.stdin)['data'].get('assessment') or {}
print('  attendance=%s missed=%s reviews=%s discharge_eligible=%s' % (
  a.get('attendance_status'), a.get('consecutive_missed_visits'),
  a.get('reviews_recorded'), a.get('discharge_eligible')))"

echo "== 3. every criterion carries the sentence the screen shows =="
printf '%s' "$EP" | jq_py "import json,sys
cs=(json.load(sys.stdin)['data'].get('assessment') or {}).get('discharge_criteria') or []
bad=[c['code'] for c in cs if not c.get('detail')]
assert cs, 'no criteria'
assert not bad, 'criteria with no explanation: %s' % bad
for c in cs: print('  %-28s %s' % (c['code'], 'MET' if c['met'] else 'not met'))" \
  && ok "all criteria explained" || bad "a criterion has no detail — the UI would show a bare 'not met'"

echo "== 4. recording a review through the UI's write path =="
mkh; VISIT=$(ccurl -X POST "$BASE/internal/v1/imam/episodes/$TARGET/visits" "${HDRS[@]}" \
  -d "{\"attended\":true,\"recorded_by\":\"nurse.chienda\",\"visit_date\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"muac_cm\":11.9,\"weight_kg\":7.4,\"oedema\":\"ABSENT\",\"danger_signs_present\":false,\"rutf_sachets_issued\":21,\"clinical_note\":\"$RUN\"}")
printf '%s' "$VISIT" | jq_py "import json,sys
v=json.load(sys.stdin)['data']
assert v.get('imam_visit_id'), 'no visit id'
print('  visit %s recorded; discharge_eligible=%s attendance=%s recommended=%s' % (
  v.get('visit_number'), v.get('discharge_eligible'), v.get('attendance_status'),
  v.get('rutf_sachets_recommended')))" && ok "review recorded and stamped with its assessment" \
  || bad "review write failed: $(printf '%s' "$VISIT" | head -c 300)"

echo "== 5. a child just seen leaves the tracing worklist =="
mkh; QUEUE2=$(ccurl "$BASE/internal/v1/imam/tracing-queue" "${HDRS[@]}")
STILL=$(printf '%s' "$QUEUE2" | jq_py "import json,sys
print(sum(1 for r in (json.load(sys.stdin).get('data') or []) if r['imam_episode_id']=='$TARGET'))")
[ "${STILL:-1}" = "0" ] && ok "traced child no longer on the worklist" \
  || bad "child still listed after being seen — the worklist would never clear"

echo "== 6. a cure is refused on criteria that were not met =="
mkh; REFUSED=$(ccurl -o /dev/null -w '%{http_code}' -X POST \
  "$BASE/internal/v1/imam/episodes/$TARGET/outcome" "${HDRS[@]}" -d '{"outcome":"CURED"}')
[ "$REFUSED" = "422" ] && ok "premature cure refused with 422 through the full stack" \
  || bad "expected 422 for a cure with unmet criteria, got $REFUSED"

echo "== 7. the same cure is accepted with a stated reason, and the reason is stored =="
mkh; CLOSED=$(ccurl -X POST "$BASE/internal/v1/imam/episodes/$TARGET/outcome" "${HDRS[@]}" \
  -d "{\"outcome\":\"CURED\",\"discharge_override_reason\":\"$RUN — proof: discharged against criteria with a stated reason\"}")
printf '%s' "$CLOSED" | jq_py "import json,sys
d=json.load(sys.stdin)['data']
assert d['status']=='CLOSED' and d['outcome']=='CURED', 'not closed as cured'
assert d.get('discharge_override_reason'), 'the override reason was not stored'
assert d.get('discharge_criteria_met') is not True, 'criteria reported met when they were not'
print('  closed: criteria_met=%s override=%r' % (d.get('discharge_criteria_met'), d['discharge_override_reason'][:40]))" \
  && ok "override accepted and recorded on the episode" \
  || bad "override close failed: $(printf '%s' "$CLOSED" | head -c 300)"

echo
echo "IMAM live proof: $PASS passed, $FAIL failed  (patient $TARGET_PID, episode $TARGET)"
[ "$FAIL" -eq 0 ]
