#!/usr/bin/env bash
# IMAM live proof — through the real ingress, on the endpoints the UI actually calls.
#
# The screens at /ehr/[patientId]/imam and /clinical/nutrition-tracing read and write exactly these
# paths, so this is the deterministic proof of the vertical they sit on: enrolment routed from an
# IMNCI classification by the governed pack, the discharge rule in both directions, the defaulter
# worklist, and the refusal to certify a cure on criteria that were not met.
#
# Step 1 is also the positive proof that pct-service's client_credentials token to the clinical
# knowledge platform is working. That call is to an OAuth2 resource server: without a valid token it
# returns 401, pct-service degrades exactly as designed, and enrolment answers 503 "protocol
# unreachable". The degradation is well-behaved enough that an auth misconfiguration is
# indistinguishable from a network blip — so the programme, review interval and content version
# coming back stamped from the pack is the evidence, and their absence is the alarm.
set -uo pipefail
BASE="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
TENANT="${TENANT_ID:-00000000-0000-0000-0000-000000000001}"
RESOLVE_IP="${PREVIEW_RESOLVE_IP:-10.50.1.67}"
# A child with a resolvable care context, kept distinct from any episode seeded for a UI walk.
SUBJECT="${IMAM_SUBJECT:-b7bc7fd0-c53f-42c7-9b2e-826ae7197ea0}"
JOURNEY="${IMAM_JOURNEY:-01KXWR2031GXTYGD69YX2N50F9}"
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
              -H "X-Actor-ID: nurse.chienda" -H "X-Purpose-Of-Use: TREATMENT"
              -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid | cut -c1-8)"); }

echo "== 0. leave no open episode behind from an earlier run =="
mkh; OPEN=$(ccurl "$BASE/internal/v1/imam/episodes?patient_id=$SUBJECT" "${HDRS[@]}" \
  | jq_py "import json,sys
rows=json.load(sys.stdin).get('data') or []
print(next((r['imam_episode_id'] for r in rows if r['status']=='ACTIVE'), ''))")
if [ -n "$OPEN" ]; then
  mkh; ccurl -o /dev/null -X POST "$BASE/internal/v1/imam/episodes/$OPEN/outcome" "${HDRS[@]}" \
    -d '{"outcome":"TRANSFERRED_OUT","outcome_by":"nurse.chienda","outcome_note":"closed by imam-episode-proof re-run"}'
  echo "  closed leftover episode $OPEN"
fi

echo "== 1. enrolment routed by the governed pack (proves the CKP client_credentials token) =="
# Backdated so the child is overdue immediately and the worklist assertions below are meaningful.
ADMITTED="$(date -u -d '21 days ago' +%Y-%m-%dT09:00:00Z 2>/dev/null || date -u +%Y-%m-%dT09:00:00Z)"
mkh; ENROL=$(ccurl -X POST "$BASE/internal/v1/imam/episodes" "${HDRS[@]}" \
  -d "{\"patient_id\":\"$SUBJECT\",\"journey_id\":\"$JOURNEY\",\"enrolled_by\":\"nurse.chienda\",\"source_classification\":\"UNCOMPLICATED_SEVERE_ACUTE_MALNUTRITION\",\"admission_muac_cm\":10.8,\"admission_oedema\":\"ABSENT\",\"admission_weight_kg\":7.0,\"admission_age_days\":400,\"admission_appetite_test\":\"PASSED\",\"enrolled_at\":\"$ADMITTED\"}")
EP=$(printf '%s' "$ENROL" | jq_py "import json,sys;print(json.load(sys.stdin)['data']['imam_episode_id'])")
if [ -z "$EP" ]; then
  bad "enrolment failed — a 503 here means the knowledge-platform token is not working: $(printf '%s' "$ENROL" | head -c 300)"
  exit 1
fi
printf '%s' "$ENROL" | jq_py "import json,sys
d=json.load(sys.stdin)['data']
assert d['programme']=='OTP', 'classification did not route to OTP: %s' % d['programme']
assert d['admission_criterion']=='IMNCI_CLASSIFICATION', 'not admitted on the classification'
assert d['review_interval_days']==7, 'review interval not from the pack'
assert d['content_version'], 'no content version stamped — the pack was never consulted'
assert d['approval_status']=='ENGINEERING_SEED', 'approval status not stamped'
print('  programme=%s criterion=%s interval=%sd pack=%s (%s)' % (
  d['programme'], d['admission_criterion'], d['review_interval_days'],
  d['content_version'], d['approval_status']))" \
  && ok "programme, schedule and pack version resolved by the knowledge platform — the token works" \
  || bad "enrolment succeeded but carries no governed content; the pack was not consulted"

echo "== 2. the episode detail the /ehr/[patientId]/imam screen reads =="
mkh; DETAIL=$(ccurl "$BASE/internal/v1/imam/episodes/$EP" "${HDRS[@]}")
printf '%s' "$DETAIL" | jq_py "import json,sys
d=json.load(sys.stdin)['data']; a=d.get('assessment') or {}
assert a.get('discharge_criteria'), 'no discharge criteria in the assessment'
assert a.get('discharge_eligible') is not None, 'verdict is null — the platform was unreachable'
print('  attendance=%s missed=%s reviews=%s discharge_eligible=%s' % (
  a.get('attendance_status'), a.get('consecutive_missed_visits'),
  a.get('reviews_recorded'), a.get('discharge_eligible')))" \
  && ok "episode detail carries a live, non-null assessment" \
  || bad "episode detail missing or unevaluated: $(printf '%s' "$DETAIL" | head -c 200)"

echo "== 3. every criterion carries the sentence the screen shows =="
printf '%s' "$DETAIL" | jq_py "import json,sys
cs=(json.load(sys.stdin)['data'].get('assessment') or {}).get('discharge_criteria') or []
missing=[c['code'] for c in cs if not c.get('detail')]
assert cs and not missing, 'criteria with no explanation: %s' % missing
for c in cs: print('  %-28s %s' % (c['code'], 'MET' if c['met'] else 'not met'))" \
  && ok "all criteria explained" || bad "a criterion has no detail — the UI would show a bare 'not met'"

echo "== 4. an unreviewed, overdue child is on the worklist and flagged never-reviewed =="
mkh; QUEUE=$(ccurl "$BASE/internal/v1/imam/tracing-queue" "${HDRS[@]}")
printf '%s' "$QUEUE" | jq_py "import json,sys
rows=json.load(sys.stdin).get('data') or []
mine=[r for r in rows if r['imam_episode_id']=='$EP']
assert mine, 'the overdue child is not on the worklist'
r=mine[0]
assert r['never_reviewed'] is True, 'enrolled and never seen, but not flagged never_reviewed'
print('  %s %s missed=%s never_reviewed=%s' % (r['patient_id'][:18], r['attendance_status'],
  r['consecutive_missed_visits'], r['never_reviewed']))" \
  && ok "worklist lists the child and marks that nobody has ever reviewed them" \
  || bad "worklist wrong: $(printf '%s' "$QUEUE" | head -c 200)"

echo "== 5. recording a review through the UI's write path =="
mkh; VISIT=$(ccurl -X POST "$BASE/internal/v1/imam/episodes/$EP/visits" "${HDRS[@]}" \
  -d "{\"attended\":true,\"recorded_by\":\"nurse.chienda\",\"visit_date\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"muac_cm\":11.9,\"weight_kg\":7.4,\"oedema\":\"ABSENT\",\"danger_signs_present\":false,\"rutf_sachets_issued\":21,\"clinical_note\":\"$RUN\"}")
printf '%s' "$VISIT" | jq_py "import json,sys
v=json.load(sys.stdin)['data']
assert v.get('imam_visit_id'), 'no visit id'
assert v.get('recorded_by'), 'the review has no author'
assert v.get('discharge_eligible') is not None, 'verdict null — the platform was unreachable on write'
assert v.get('rutf_sachets_recommended') is not None, 'no ration came back from the pack'
print('  visit %s by %s; discharge_eligible=%s attendance=%s ration=%s/week' % (
  v['visit_number'], v['recorded_by'], v['discharge_eligible'], v['attendance_status'],
  v['rutf_sachets_recommended']))" && ok "review recorded, attributed, and stamped with its assessment" \
  || bad "review write failed: $(printf '%s' "$VISIT" | head -c 300)"

echo "== 6. a clinical write with no identifiable author is refused, not 500'd =="
NOAUTHOR=$(ccurl -o /dev/null -w '%{http_code}' -X POST "$BASE/internal/v1/imam/episodes/$EP/visits" \
  -H "Content-Type: application/json" -H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: national-spine" \
  -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" \
  -H "Authorization: Bearer $TOK" -H "X-Purpose-Of-Use: TREATMENT" \
  -H "Idempotency-Key: $RUN-noauthor" \
  -d "{\"attended\":true,\"visit_date\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"muac_cm\":11.9}")
[ "$NOAUTHOR" = "400" ] && ok "authorless write refused with 400 (was a 500 from the NOT NULL constraint)" \
  || bad "expected 400 for a write with no author, got $NOAUTHOR"

echo "== 7. a child just seen leaves the tracing worklist =="
mkh; STILL=$(ccurl "$BASE/internal/v1/imam/tracing-queue" "${HDRS[@]}" | jq_py "import json,sys
print(sum(1 for r in (json.load(sys.stdin).get('data') or []) if r['imam_episode_id']=='$EP'))")
[ "${STILL:-1}" = "0" ] && ok "traced child no longer on the worklist" \
  || bad "child still listed after being seen — the worklist would never clear"

echo "== 8. one good measurement does not discharge a child =="
mkh; REFUSED=$(ccurl -o /dev/null -w '%{http_code}' -X POST \
  "$BASE/internal/v1/imam/episodes/$EP/outcome" "${HDRS[@]}" -d '{"outcome":"CURED","outcome_by":"nurse.chienda"}')
[ "$REFUSED" = "422" ] && ok "premature cure refused with 422 through the full stack" \
  || bad "expected 422 for a cure with unmet criteria, got $REFUSED"

echo "== 9. the same cure is accepted with a stated reason, and the reason is stored =="
mkh; CLOSED=$(ccurl -X POST "$BASE/internal/v1/imam/episodes/$EP/outcome" "${HDRS[@]}" \
  -d "{\"outcome\":\"CURED\",\"outcome_by\":\"nurse.chienda\",\"discharge_override_reason\":\"$RUN — proof: discharged against criteria with a stated reason\"}")
printf '%s' "$CLOSED" | jq_py "import json,sys
d=json.load(sys.stdin)['data']
assert d['status']=='CLOSED' and d['outcome']=='CURED', 'not closed as cured'
assert d.get('discharge_override_reason'), 'the override reason was not stored'
assert d.get('discharge_criteria_met') is not True, 'criteria reported met when they were not'
print('  closed: criteria_met=%s override=%r' % (d.get('discharge_criteria_met'), d['discharge_override_reason'][:40]))" \
  && ok "override accepted and recorded on the episode" \
  || bad "override close failed: $(printf '%s' "$CLOSED" | head -c 300)"

echo
echo "IMAM live proof: $PASS passed, $FAIL failed  (subject $SUBJECT, episode $EP)"
[ "$FAIL" -eq 0 ]
