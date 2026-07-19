#!/usr/bin/env bash
# Coverage enrolment completion proof — through the real ingress:
# citizen → eligibility → enrol member → member record persists → My Coverage shows it.
# (The browser page renders this same flow; this script is the deterministic
#  write-path proof with DB truth, unaffected by the headless consent-gate flake.)
set -uo pipefail
BASE="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"; TENANT="00000000-0000-4000-8000-000000000001"
CID="${CITIZEN_CPID:-c0000000-0000-4000-8000-000000000012}"        # learner.tembo
RES=(--resolve impilo.mohcc.gov.zw:443:127.0.0.1); RUN="cov-$(date +%s)"; PASS=0; FAIL=0
ccurl(){ curl -sk -m12 "${RES[@]}" "$@"; }
jg(){ python3 -c "import json,sys;d=json.load(sys.stdin);cur=d
for k in '$1'.split('.'):
 cur=cur.get(k) if isinstance(cur,dict) else (cur[int(k)] if isinstance(cur,list) and k.isdigit() else None)
 if cur is None:break
print(cur if cur is not None else '')" 2>/dev/null; }
ok(){ PASS=$((PASS+1)); echo "  ok: $*"; }; bad(){ FAIL=$((FAIL+1)); echo "  FAIL: $*"; }

TOK=$(ccurl -X POST "$BASE/internal/v1/auth/login" -H 'Content-Type: application/json' -H "X-Tenant-ID: $TENANT" -H 'X-Pod-ID: p' -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" -H "Idempotency-Key: $RUN-l" -d '{"email":"learner.tembo","password":"ImpiloTest123!"}' | jg data.attributes.token)
[ -n "$TOK" ] && ok "citizen login" || { bad "login"; exit 1; }
mkh(){ HDRS=(-H "Content-Type: application/json" -H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: p" -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" -H "Authorization: Bearer $TOK" -H "X-Actor-ID: $CID" -H "X-Actor-Type: CITIZEN" -H "X-Purpose-Of-Use: CARE_COORDINATION" -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid|cut -c1-8)"); }

echo "== 1. plans (real seeded) — pick one the citizen is NOT already enrolled on =="
mkh; PLANS_JSON=$(ccurl "$BASE/internal/v1/coverage/plans" "${HDRS[@]}")
mkh; ENROLLED=$(ccurl "$BASE/internal/v1/coverage/member/$CID" "${HDRS[@]}")
PLAN=$(python3 -c "
import json,sys
plans=(json.loads('''$PLANS_JSON''').get('data') or [])
enrolled=set()
try:
  for m in (json.loads('''$ENROLLED''').get('data') or []): enrolled.add(m.get('planId'))
except Exception: pass
for p in plans:
  if p.get('id') not in enrolled: print(p.get('id')); break
else:
  print(plans[0].get('id') if plans else '')" 2>/dev/null)
[ -n "$PLAN" ] && ok "plan available (unenrolled): $PLAN" || bad "no plans"

echo "== 2. eligibility =="
mkh; ELIG=$(ccurl -X POST "$BASE/internal/v1/coverage/eligibility/enrollment" "${HDRS[@]}" -d "{\"clientId\":\"$CID\",\"planId\":\"$PLAN\"}" | jg data.resultCode)
[ "$ELIG" = "ELIGIBLE" ] && ok "eligible" || bad "eligibility: $ELIG"

echo "== 3. enrol member =="
mkh; MEMNO="MEM-$RUN"; CODE=$(ccurl -o /tmp/enr.json -w '%{http_code}' -X POST "$BASE/internal/v1/coverage/members" "${HDRS[@]}" -d "{\"clientId\":\"$CID\",\"planId\":\"$PLAN\",\"memberNumber\":\"$MEMNO\",\"relationship\":\"SELF\",\"status\":\"ACTIVE\"}")
MID=$(jg data.id < /tmp/enr.json)
[ "$CODE" = "201" ] && [ -n "$MID" ] && ok "enrolled member id=$MID ($MEMNO)" || bad "enrol HTTP=$CODE: $(head -c 200 /tmp/enr.json)"

echo "== 4. My Coverage now shows the plan =="
mkh; MEM=$(ccurl "$BASE/internal/v1/coverage/member/$CID" "${HDRS[@]}")
echo "$MEM" | grep -q "$PLAN" && ok "My Coverage returns the enrolled plan" || bad "My Coverage missing plan: $(echo "$MEM"|head -c 200)"

echo "== 5. DB truth =="
ROW=$(kubectl exec -n impilo-full-preview deploy/postgres -- psql -U impilo -d coverage -tAc "select status from cv_member_coverage where member_number='$MEMNO';" 2>/dev/null)
[ "$ROW" = "ACTIVE" ] && ok "cv_member_coverage row ACTIVE" || bad "no member row ($ROW)"

echo ""; echo "RESULT: $PASS ok, $FAIL fail  (run=$RUN member=${MID:-none})"
[ "$FAIL" -eq 0 ] || exit 1
