#!/usr/bin/env bash
# Ruvimbo Wave 4 proof (COB + employer roster + fraud + capitation + claims switch) — live ingress.
# COB waterfall over dual coverage; employer roster stage->validate->apply; fraud screen a
# claim-after-termination; capitation report; gateway routes internal(ACCEPTED) vs external(gated PENDING).
set -uo pipefail
BASE="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
TENANT="00000000-0000-4000-8000-000000000001"
RES=(--resolve impilo.mohcc.gov.zw:443:127.0.0.1)
RUN="w4-$(date +%s)"; PASS=0; FAIL=0
CID="w4-citizen-$RUN"
PLAN_GOV="bbbbbbbb-0001-0000-0000-000000000001"   # COV-MOHCC-CORE (GP_CONSULT 100%)
PLAN_PVT="bbbbbbbb-0001-0000-0000-000000000002"   # COV-PRIVATE-PLUS (GP_CONSULT 80%)
PSQL() { kubectl exec -n impilo-full-preview deploy/postgres -- psql -U impilo -d coverage -tAc "$1" 2>/dev/null; }
ccurl() { curl -sk -m15 "${RES[@]}" "$@"; }
jg() { python3 -c "import json,sys
d=json.load(sys.stdin);cur=d
for k in '$1'.split('.'):
 cur=cur.get(k) if isinstance(cur,dict) else (cur[int(k)] if isinstance(cur,list) and k.lstrip('-').isdigit() else None)
 if cur is None: break
print(cur if cur is not None else '')" 2>/dev/null; }
nf() { python3 -c "print(abs(float('${1:-0}')-float('$2'))<0.01)" 2>/dev/null; }
ok() { PASS=$((PASS+1)); echo "  ok: $*"; }
bad() { FAIL=$((FAIL+1)); echo "  FAIL: $*"; }
TOK=$(ccurl -X POST "$BASE/internal/v1/auth/login" -H 'Content-Type: application/json' -H "X-Tenant-ID: $TENANT" -H 'X-Pod-ID: p' -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" -H "Idempotency-Key: $RUN-l" -d '{"email":"learner.tembo","password":"ImpiloTest123!"}' | jg data.attributes.token)
[ -n "$TOK" ] && ok "login" || { bad "login"; exit 1; }
mkh() { HDRS=(-H "Content-Type: application/json" -H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: p" -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" -H "Authorization: Bearer $TOK" -H "X-Actor-ID: $CID" -H "X-Actor-Type: PROVIDER" -H "X-Purpose-Of-Use: COVERAGE_ADMIN" -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid|cut -c1-8)"); }
post() { mkh; ccurl -X POST "$BASE$1" "${HDRS[@]}" -d "$2"; }
get() { mkh; ccurl "$BASE$1" "${HDRS[@]}"; }

echo "== 1. coordination of benefits (dual coverage: MOHCC primary 100%, Private secondary) =="
# Enrol the SAME cpid on both plans (priority 1 default; the waterfall orders by priority).
M1=$(post /internal/v1/coverage/members "{\"clientId\":\"$CID\",\"planId\":\"$PLAN_GOV\",\"memberNumber\":\"G-$RUN\",\"relationship\":\"SELF\",\"status\":\"ACTIVE\"}" | jg data.id)
M2=$(post /internal/v1/coverage/members "{\"clientId\":\"$CID\",\"planId\":\"$PLAN_PVT\",\"memberNumber\":\"P-$RUN\",\"relationship\":\"SELF\",\"status\":\"ACTIVE\"}" | jg data.id)
[ -n "$M1" ] && [ -n "$M2" ] && ok "dual coverage enrolled ($M1 / $M2)" || bad "dual enrol ($M1/$M2)"
COB=$(post /internal/v1/coverage/cob/coordinate "{\"memberCpid\":\"$CID\",\"benefitCode\":\"GP_CONSULT\",\"standardCharge\":100}")
TPP=$(echo "$COB"|jg data.totalPayerPaid); PR=$(echo "$COB"|jg data.patientResponsibility)
# MOHCC 100% pays 100 first -> patient 0 (secondary sees 0 remaining). total payer <= allowed.
nf "$TPP" 100 | grep -q True && ok "COB total payer paid 100 (waterfall)" || bad "cob total=$TPP"
nf "$PR" 0 | grep -q True && ok "COB patient responsibility 0" || bad "cob patient=$PR"
echo "$COB" | grep -q ruvimbo-cob-v1 && ok "COB ruleset recorded + waterfall present" || bad "no cob ruleset"

echo "== 2. employer roster: register -> stage -> validate -> apply =="
EMP=$(post /internal/v1/coverage/employers "{\"employerCode\":\"EMP-$RUN\",\"name\":\"Acme Mining $RUN\"}" | jg data.id)
[ -n "$EMP" ] && ok "employer $EMP" || bad "employer register"
BATCH=$(post "/internal/v1/coverage/employers/$EMP/roster-batches" "{\"planId\":\"$PLAN_PVT\",\"uploadedBy\":\"hr\",\"rows\":[{\"clientId\":\"w4-emp1-$RUN\",\"memberNumber\":\"E1\",\"relationship\":\"SELF\"},{\"clientId\":\"w4-emp2-$RUN\",\"memberNumber\":\"E2\",\"relationship\":\"SELF\"}]}")
BID=$(echo "$BATCH"|jg data.id)
[ "$(echo "$BATCH"|jg data.status)" = "STAGED" ] && [ "$(echo "$BATCH"|jg data.totalRows)" = "2" ] && ok "roster STAGED (2 rows)" || bad "stage: $(echo "$BATCH"|head -c200)"
VAL=$(post "/internal/v1/coverage/employers/roster-batches/$BID/validate" "{}")
[ "$(echo "$VAL"|jg data.status)" = "VALIDATED" ] && [ "$(echo "$VAL"|jg data.validRows)" = "2" ] && ok "roster VALIDATED (2 valid)" || bad "validate: $(echo "$VAL"|head -c200)"
APP=$(post "/internal/v1/coverage/employers/roster-batches/$BID/apply" "{}")
[ "$(echo "$APP"|jg data.status)" = "APPLIED" ] && [ "$(echo "$APP"|jg data.appliedRows)" = "2" ] && ok "roster APPLIED (2 memberships created)" || bad "apply: $(echo "$APP"|head -c200)"
# apply again -> not VALIDATED -> 409
CODE=$(mkh; ccurl -o /dev/null -w '%{http_code}' -X POST "$BASE/internal/v1/coverage/employers/roster-batches/$BID/apply" "${HDRS[@]}" -d '{}')
[ "$CODE" = "409" ] && ok "re-apply rejected HTTP $CODE" || bad "re-apply HTTP=$CODE (want 409)"

echo "== 3. fraud: screen a claim after membership termination =="
# fresh single-plan member, file+adjudicate a claim, terminate, then screen -> flags.
FM=$(post /internal/v1/coverage/members "{\"clientId\":\"w4-fraud-$RUN\",\"planId\":\"$PLAN_GOV\",\"memberNumber\":\"F-$RUN\",\"relationship\":\"SELF\",\"status\":\"ACTIVE\"}" | jg data.id)
FC=$(post /internal/v1/coverage/v2/claims "{\"coverageId\":\"$FM\",\"claimType\":\"OUTPATIENT\",\"facilityId\":\"FAC-1\",\"providerId\":\"PRV-9\",\"encounterId\":\"ENCF-$RUN\",\"lines\":[{\"benefitCode\":\"GP_CONSULT\",\"submittedAmount\":30}]}" | jg data.id)
post "/internal/v1/coverage/members/$FM/verify" "{\"verified\":true}" >/dev/null
post "/internal/v1/coverage/members/$FM/terminate" "{\"reason\":\"proof\"}" >/dev/null
SCR=$(post "/internal/v1/coverage/fraud-flags/screen-claim/$FC" "{}")
echo "$SCR" | grep -q CLAIM_AFTER_TERMINATION && ok "fraud flag CLAIM_AFTER_TERMINATION raised" || bad "fraud screen: $(echo "$SCR"|head -c250)"
FID=$(echo "$SCR"|jg data.0.id)
REV=$(post "/internal/v1/coverage/fraud-flags/$FID/review" "{\"status\":\"CONFIRMED\",\"reviewerId\":\"INV-1\",\"resolution\":\"recover\"}")
[ "$(echo "$REV"|jg data.status)" = "CONFIRMED" ] && ok "fraud flag reviewed -> CONFIRMED (governed)" || bad "fraud review: $(echo "$REV"|head -c200)"

echo "== 4. capitation report =="
CAP=$(post /internal/v1/coverage/capitation-reports "{\"providerId\":\"PRV-CAP-$RUN\",\"facilityId\":\"FAC-1\",\"payerId\":\"PAYER-MOHCC\",\"periodStart\":\"2026-07-01\",\"periodEnd\":\"2026-07-31\",\"memberCount\":250,\"encounterCount\":410,\"capitationAmount\":5000}")
[ "$(echo "$CAP"|jg data.memberCount)" = "250" ] && ok "capitation report (250 members, \$5000)" || bad "capitation: $(echo "$CAP"|head -c200)"

echo "== 5. claims switch: internal ACCEPTED vs external gated PENDING =="
GIN=$(post /internal/v1/coverage/gateway/transactions "{\"transactionType\":\"CLAIM_SUBMISSION\",\"senderRef\":\"FAC-1\",\"receiverPayer\":\"PAYER-MOHCC\",\"route\":\"INTERNAL_ADJUDICATION\"}")
[ "$(echo "$GIN"|jg data.technicalAck)" = "ACCEPTED" ] && [ "$(echo "$GIN"|jg data.businessAck)" = "ACCEPTED" ] && ok "internal route: technical+business ACCEPTED" || bad "internal gw: $(echo "$GIN"|head -c200)"
GEX=$(post /internal/v1/coverage/gateway/transactions "{\"transactionType\":\"CLAIM_SUBMISSION\",\"senderRef\":\"FAC-1\",\"receiverPayer\":\"PAYER-EXTERNAL\",\"route\":\"EXTERNAL_API\"}")
[ "$(echo "$GEX"|jg data.technicalAck)" = "ACCEPTED" ] && [ "$(echo "$GEX"|jg data.businessAck)" = "PENDING" ] && ok "external route: technical ACCEPTED, business PENDING (credential-gated, not faked)" || bad "external gw: $(echo "$GEX"|head -c200)"

echo "== 6. DB truth =="
[ "$(PSQL "select count(*) from cv_cob_decisions where member_cpid='$CID';")" -ge 1 ] && ok "cv_cob_decisions row" || bad "cob DB"
[ "$(PSQL "select count(*) from cv_member_coverage where member_category='EMPLOYER_SPONSORED' and client_id in ('w4-emp1-$RUN','w4-emp2-$RUN');")" = "2" ] && ok "2 EMPLOYER_SPONSORED memberships applied" || bad "roster DB"
[ "$(PSQL "select status from cv_fraud_flags where id='$FID';")" = "CONFIRMED" ] && ok "cv_fraud_flags CONFIRMED" || bad "fraud DB"
[ "$(PSQL "select count(*) from cv_capitation_reports where provider_id='PRV-CAP-$RUN';")" = "1" ] && ok "cv_capitation_reports row" || bad "capitation DB"
[ "$(PSQL "select count(*) from cv_gateway_transactions where correlation_id is not null and created_at > now() - interval '10 minutes';")" -ge 2 ] && ok "cv_gateway_transactions rows" || bad "gateway DB"
OUTBOX=$(PSQL "select count(*) from cv_event_outbox where event_type in ('coverage.cob.coordinated','coverage.roster.applied','coverage.fraud.flagged','coverage.switch.routed') and occurred_at > now() - interval '10 minutes';")
[ "${OUTBOX:-0}" -ge 4 ] && ok "W4 events emitted ($OUTBOX)" || bad "outbox events ($OUTBOX)"

echo ""
echo "RESULT: $PASS ok, $FAIL fail  (run=$RUN)"
[ "$FAIL" -eq 0 ] || exit 1
