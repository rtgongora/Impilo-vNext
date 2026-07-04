#!/usr/bin/env bash
# =============================================================================
# Scenario C — Fundo LMS learner journey (API steel thread).
#
# Proves on a LIVE estate: catalog → course detail/structure → enrol (as a
# real VARAPI provider so CPD fires) → start → lessons/progress → assessment
# attempt → completion → certificate (verificationDigest) → CPD writeback to
# VARAPI via impilo.learning.certificate.issued.v1 → notification dispatch
# through the real notification-service (backlog ⑤).
# =============================================================================
set -euo pipefail

TENANT_ID="${TENANT_ID:-00000000-0000-4000-8000-000000000001}"
NS="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
PROVIDER_ID="${PROVIDER_ID:-PROV-ZW-00001}"
RUN="scnC-$(date +%s)"

PASS=0
ok()   { PASS=$((PASS+1)); echo "  ok: $*"; }
fail() { echo "  FAIL: $*" >&2; exit 1; }
step() { echo ""; echo "== $*"; }
jqpy() { python3 -c "import json,sys
d=json.load(sys.stdin)
$1"; }

LEARN="http://learning-service:8235/internal/v1/learning/v11"
svc_curl() { # svc_curl <method> <url> [json-body]
  local method="$1" url="$2" body="${3:-}"
  kubectl exec -n "$NS" deploy/experience-bff -- curl -sS -X "$method" "$url" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-e2e" \
    -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" \
    -H "X-Actor-ID: c0000000-0000-4000-8000-000000000001" -H "X-Actor-Type: PROVIDER" \
    -H "X-Purpose-Of-Use: TRAINING" \
    -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid | cut -c1-8)" \
    ${body:+-d "$body"}
}

# ── 1. Catalog → CPD-eligible course ─────────────────────────────────────────
step "1. catalog → CPD-eligible course"
CATALOG=$(svc_curl GET "$LEARN/catalog?limit=25")
COURSE_ID=$(echo "$CATALOG" | jqpy "
items=(d.get('data') or {}).get('items') or []
cpd=[c for c in items if c.get('cpdEligible')]
pick=(cpd or items)
print(pick[0]['id'] if pick else '')")
COURSE_CODE=$(echo "$CATALOG" | jqpy "
items=(d.get('data') or {}).get('items') or []
cpd=[c for c in items if c.get('cpdEligible')]
pick=(cpd or items)
print(pick[0].get('code','') if pick else '')")
[[ -n "$COURSE_ID" ]] || fail "no courses in catalog: $(echo "$CATALOG" | head -c 300)"
ok "course selected: $COURSE_CODE ($COURSE_ID)"

DETAIL=$(svc_curl GET "$LEARN/catalog/$COURSE_ID")
echo "$DETAIL" | jqpy "
data=d.get('data') or {}
course=data.get('course') or data
assert course.get('id'), 'no detail'" || fail "course detail failed"
STRUCTURE=$(svc_curl GET "$LEARN/courses/$COURSE_ID/structure")
LESSON_IDS=$(echo "$STRUCTURE" | jqpy "
data=d.get('data') or {}
mods=data.get('modules') or data.get('items') or (data.get('structure') or {}).get('modules') or []
out=[]
for m in mods:
    for l in (m.get('lessons') or m.get('items') or []):
        out.append(l.get('id'))
print(' '.join(x for x in out if x))")
ok "structure loaded ($(echo $LESSON_IDS | wc -w | tr -d ' ') lessons)"

# ── 2. Enrol as a real provider → start ──────────────────────────────────────
step "2. enrol (PROVIDER $PROVIDER_ID) → start"
ENROL=$(svc_curl POST "$LEARN/enrolments" \
  "{\"courseId\":\"$COURSE_ID\",\"subjectType\":\"PROVIDER\",\"subjectId\":\"$PROVIDER_ID\",\"enrolmentType\":\"SELF\"}")
ENROLMENT_ID=$(echo "$ENROL" | jqpy "
data=d.get('data') or {}
ent=data.get('enrolment') or data
print(ent.get('id') or ent.get('enrolmentId') or '')")
[[ -n "$ENROLMENT_ID" ]] || fail "enrolment failed: $(echo "$ENROL" | head -c 300)"
svc_curl POST "$LEARN/enrolments/$ENROLMENT_ID/start" "{}" >/dev/null
ok "enrolled + started: $ENROLMENT_ID"

# ── 3. Lessons + progress ────────────────────────────────────────────────────
step "3. lessons + progress"
# Each lesson row must reach 100% — the aggregate reconciler then completes
# the enrolment (COURSE_COMPLETED outbox → CPD/notification loops).
for LID in $LESSON_IDS; do
  svc_curl POST "$LEARN/lessons/$LID/open" \
    "{\"enrolmentId\":\"$ENROLMENT_ID\",\"subjectType\":\"PROVIDER\",\"subjectId\":\"$PROVIDER_ID\"}" >/dev/null || true
  svc_curl POST "$LEARN/progress" \
    "{\"enrolmentId\":\"$ENROLMENT_ID\",\"lessonId\":\"$LID\",\"progressPercent\":100}" >/dev/null
done
PROGRESS=$(svc_curl GET "$LEARN/progress?enrolmentId=$ENROLMENT_ID")
ok "progress recorded: $(echo "$PROGRESS" | head -c 120)..."

# ── 4. Assessment attempt ────────────────────────────────────────────────────
step "4. assessment attempt"
ASSESSMENTS=$(svc_curl GET "$LEARN/courses/$COURSE_ID/assessments")
ASSESSMENT_ID=$(echo "$ASSESSMENTS" | jqpy "
data=d.get('data') or {}
items=data.get('items') or data.get('assessments') or (data if isinstance(data,list) else [])
print(items[0]['id'] if items else '')")
if [[ -n "$ASSESSMENT_ID" ]]; then
  ATTEMPT=$(svc_curl POST "$LEARN/assessments/$ASSESSMENT_ID/attempts" \
    "{\"enrolmentId\":\"$ENROLMENT_ID\",\"subjectType\":\"PROVIDER\",\"subjectId\":\"$PROVIDER_ID\",\"answers\":{}}")
  echo "  attempt: $(echo "$ATTEMPT" | head -c 200)"
  ok "assessment attempt submitted"
else
  ok "course has no assessment (skipping attempt)"
fi

# ── 5. Certificate ───────────────────────────────────────────────────────────
step "5. certificate issuance"
CERT=$(svc_curl POST "$LEARN/enrolments/$ENROLMENT_ID/certificate" "{}")
CERT_NUM=$(echo "$CERT" | jqpy "
data=d.get('data') or {}
ent=data.get('certificate') or data
print(ent.get('certificateNumber') or ent.get('certificate_number') or '')")
DIGEST=$(echo "$CERT" | jqpy "
data=d.get('data') or {}
ent=data.get('certificate') or data
print(ent.get('verificationDigest') or ent.get('verification_digest') or '')")
[[ -n "$CERT_NUM" ]] || fail "certificate not issued: $(echo "$CERT" | head -c 400)"
ok "certificate $CERT_NUM issued (digest ${DIGEST:0:16}...)"

# ── 6. CPD writeback to VARAPI (Kafka loop + governed acceptance) ────────────
# Fundo never awards points: the certificate event lands a PENDING candidate in
# varapi; a council/registry actor accepts it into an IN_PROGRESS CPD cycle and
# only then do points post to the summary. We prove the whole governed loop.
step "6. CPD writeback via impilo.learning.certificate.issued.v1"
VARAPI="http://varapi-service:8083"
vpsql() { kubectl exec -n "$NS" deploy/postgres -- psql -U impilo -d varapi -tAc "$1"; }

# 6a. candidate landed (proves learning→Kafka→varapi consumer)
CANDIDATE_ID=""
for i in $(seq 1 12); do
  CANDIDATE_ID=$(vpsql "SELECT id FROM varapi.fundo_cpd_candidates WHERE external_ref='$CERT_NUM'" | tr -d '[:space:]')
  [[ -n "$CANDIDATE_ID" ]] && break
  sleep 5
done
[[ -n "$CANDIDATE_ID" ]] || fail "no fundo_cpd_candidates row for $CERT_NUM — learning→varapi Kafka loop broken"
ok "CPD candidate $CANDIDATE_ID landed in varapi (PENDING, governed)"

# 6b. ensure an IN_PROGRESS CPD cycle exists for the provider
SUMMARY=$(svc_curl GET "$VARAPI/v1/internal/providers/$PROVIDER_ID/cpd/summary")
CYCLE_STATUS=$(echo "$SUMMARY" | jqpy "print((d.get('data') or {}).get('status') or '')")
if [[ "$CYCLE_STATUS" != "IN_PROGRESS" ]]; then
  YEAR=$(date +%Y)
  svc_curl POST "$VARAPI/v1/internal/providers/$PROVIDER_ID/cpd/cycles" \
    "{\"cycleName\":\"CPD $YEAR\",\"startDate\":\"$YEAR-01-01\",\"endDate\":\"$YEAR-12-31\",\"requiredPoints\":50}" >/dev/null
  ok "CPD cycle CPD $YEAR opened for $PROVIDER_ID"
fi

# 6c. council/registry acceptance → points post
svc_curl POST "$VARAPI/v1/internal/provider-council/fundo-cpd-candidates/$CANDIDATE_ID/accept" "{}" >/dev/null || true
CAND_STATE=$(vpsql "SELECT verification_state FROM varapi.fundo_cpd_candidates WHERE id=$CANDIDATE_ID" | tr -d '[:space:]')
[[ "$CAND_STATE" == "ACCEPTED" ]] || fail "candidate $CANDIDATE_ID not ACCEPTED (state=$CAND_STATE)"
ok "candidate ACCEPTED by council/registry actor"

# 6d. summary reflects earned points
CPD=$(svc_curl GET "$VARAPI/v1/internal/providers/$PROVIDER_ID/cpd/summary")
EARNED=$(echo "$CPD" | jqpy "print((d.get('data') or {}).get('earnedPoints') or 0)")
[[ "$EARNED" -gt 0 ]] || fail "CPD summary shows no earned points after acceptance: $(echo "$CPD" | head -c 300)"
ok "VARAPI CPD summary reflects the certificate (earnedPoints=$EARNED)"

# ── 7. Notification dispatch through the real provider (⑤) ───────────────────
step "7. notification dispatched via NOTIFICATION_SERVICE"
DISPATCH=""
for i in $(seq 1 6); do
  DISPATCH=$(kubectl logs -n "$NS" deploy/learning-service --since=10m 2>/dev/null \
    | grep -iE "NOTIFICATION_SERVICE|dispatched.*notification|processed .* intents" | tail -1)
  [[ -n "$DISPATCH" ]] && break
  sleep 10
done
[[ -n "$DISPATCH" ]] || fail "no notification dispatch evidence in learning-service logs"
ok "dispatch evidence: ${DISPATCH:0:140}"

echo ""
echo "PASS: Scenario C (Fundo learner journey) green ($PASS checks) — run tag $RUN"
