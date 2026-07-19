#!/usr/bin/env bash
# Ruvimbo Wave 2 proof (authorisations + referrals + liability) — live through the real ingress.
# Reuses the W1 seeded plan hierarchy: enrol a fresh citizen on COV-MOHCC-CORE (has benefits),
# then referral -> validity -> consume; authorisation create -> submit -> review -> decide
# (line APPROVED reserves benefit); liability estimate (COSTA charge -> coverage split). DB truth.
set -uo pipefail
BASE="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
TENANT="00000000-0000-4000-8000-000000000001"
RES=(--resolve impilo.mohcc.gov.zw:443:127.0.0.1)
RUN="w2-$(date +%s)"; PASS=0; FAIL=0
CID="w2-citizen-$RUN"
PLAN="bbbbbbbb-0001-0000-0000-000000000001"   # COV-MOHCC-CORE (linked to plan version + benefits)
PSQL() { kubectl exec -n impilo-full-preview deploy/postgres -- psql -U impilo -d coverage -tAc "$1" 2>/dev/null; }
ccurl() { curl -sk -m15 "${RES[@]}" "$@"; }
jg() { python3 -c "import json,sys
d=json.load(sys.stdin);cur=d
for k in '$1'.split('.'):
 cur=cur.get(k) if isinstance(cur,dict) else (cur[int(k)] if isinstance(cur,list) and k.lstrip('-').isdigit() else None)
 if cur is None: break
print(cur if cur is not None else '')" 2>/dev/null; }
ok() { PASS=$((PASS+1)); echo "  ok: $*"; }
bad() { FAIL=$((FAIL+1)); echo "  FAIL: $*"; }
TOK=$(ccurl -X POST "$BASE/internal/v1/auth/login" -H 'Content-Type: application/json' -H "X-Tenant-ID: $TENANT" -H 'X-Pod-ID: p' -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" -H "Idempotency-Key: $RUN-l" -d '{"email":"learner.tembo","password":"ImpiloTest123!"}' | jg data.attributes.token)
[ -n "$TOK" ] && ok "login" || { bad "login"; exit 1; }
mkh() { HDRS=(-H "Content-Type: application/json" -H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: p" -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" -H "Authorization: Bearer $TOK" -H "X-Actor-ID: $CID" -H "X-Actor-Type: PROVIDER" -H "X-Purpose-Of-Use: COVERAGE_ADMIN" -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid|cut -c1-8)"); }
post() { mkh; ccurl -X POST "$BASE$1" "${HDRS[@]}" -d "$2"; }
postc() { mkh; ccurl -o /tmp/w2p.json -w '%{http_code}' -X POST "$BASE$1" "${HDRS[@]}" -d "$2"; }
get() { mkh; ccurl "$BASE$1" "${HDRS[@]}"; }

echo "== 0. enrol fresh citizen on COV-MOHCC-CORE =="
ENR=$(post /internal/v1/coverage/members "{\"clientId\":\"$CID\",\"planId\":\"$PLAN\",\"memberNumber\":\"W2-$RUN\",\"relationship\":\"SELF\",\"status\":\"ACTIVE\"}")
MID=$(echo "$ENR" | jg data.id)
[ -n "$MID" ] && ok "membership $MID" || bad "enrol: $(echo "$ENR"|head -c200)"

echo "== 1. referral create -> validity -> consume =="
REF=$(post /internal/v1/coverage/referrals "{\"coverageId\":\"$MID\",\"memberCpid\":\"$CID\",\"referringProviderId\":\"PRV-GP-1\",\"referredProviderId\":\"PRV-SPEC-1\",\"scope\":\"IMAGING\",\"reason\":\"CXR review\",\"permittedVisits\":2}")
REF_ID=$(echo "$REF" | jg data.id)
[ -n "$REF_ID" ] && ok "referral $REF_ID (2 visits)" || bad "referral: $(echo "$REF"|head -c200)"
VAL=$(get "/internal/v1/coverage/referrals/$REF_ID/validity")
[ "$(echo "$VAL"|jg data.valid)" = "True" ] && ok "referral valid (remaining $(echo "$VAL"|jg data.visitsRemaining))" || bad "validity: $(echo "$VAL"|head -c200)"
CONS=$(post "/internal/v1/coverage/referrals/$REF_ID/consume" "{}")
[ "$(echo "$CONS"|jg data.visitsUsed)" = "1" ] && ok "referral visit consumed (1/2)" || bad "consume: $(echo "$CONS"|head -c200)"

echo "== 2. authorisation create (line) -> submit -> review -> decide (approve reserves benefit) =="
AUTH=$(post /internal/v1/coverage/authorisations "{\"coverageId\":\"$MID\",\"authType\":\"DIAGNOSTIC\",\"requestingProviderId\":\"PRV-GP-1\",\"facilityId\":\"FAC-1\",\"referralId\":\"$REF_ID\",\"diagnosisSystem\":\"ICD-11\",\"diagnosisCode\":\"CA40\",\"clinicalJustification\":\"suspected pneumonia\",\"urgency\":\"ROUTINE\",\"lines\":[{\"benefitCode\":\"IMG_CXR\",\"serviceCode\":\"CXR\",\"description\":\"Chest X-ray\",\"quantityRequested\":1,\"estimatedAmount\":40}]}")
AUTH_ID=$(echo "$AUTH" | jg data.id)
LINE_ID=$(echo "$AUTH" | jg data.lines.0.id)
[ -n "$AUTH_ID" ] && [ -n "$LINE_ID" ] && ok "authorisation $AUTH_ID (DRAFT, line $LINE_ID)" || bad "auth create: $(echo "$AUTH"|head -c250)"
SUB=$(post "/internal/v1/coverage/authorisations/$AUTH_ID/submit" "{}")
[ "$(echo "$SUB"|jg data.status)" = "SUBMITTED" ] && ok "submitted" || bad "submit: $(echo "$SUB"|head -c200)"
REV=$(post "/internal/v1/coverage/authorisations/$AUTH_ID/acknowledge" "{\"reviewerId\":\"REV-1\"}")
[ "$(echo "$REV"|jg data.status)" = "ACKNOWLEDGED" ] && ok "acknowledged" || bad "ack: $(echo "$REV"|head -c200)"
DEC=$(post "/internal/v1/coverage/authorisations/$AUTH_ID/decision" "{\"reviewerId\":\"REV-1\",\"lines\":[{\"lineId\":\"$LINE_ID\",\"outcome\":\"APPROVED\",\"quantityApproved\":1,\"approvedAmount\":40}]}")
[ "$(echo "$DEC"|jg data.status)" = "APPROVED" ] && ok "APPROVED" || bad "decide: $(echo "$DEC"|head -c250)"
[ "$(echo "$DEC"|jg data.lines.0.lineStatus)" = "APPROVED" ] && ok "line APPROVED" || bad "line status"

echo "== 3. benefit reserved by the approval =="
ACC=$(get "/internal/v1/coverage/members/$MID/benefit-accumulators")
CXR_RES=$(echo "$ACC" | python3 -c "import json,sys
rows=(json.load(sys.stdin).get('data') or [])
for r in rows:
 if r.get('benefitCode')=='IMG_CXR': print(r.get('reserved'));break" 2>/dev/null)
python3 -c "print(abs(float('${CXR_RES:-0}')-40)<0.001)" 2>/dev/null | grep -q True && ok "IMG_CXR reserved 40 by authorisation" || bad "reserved=$CXR_RES (want 40)"

echo "== 4. illegal transition rejected (decide again) =="
CODE=$(postc "/internal/v1/coverage/authorisations/$AUTH_ID/decision" "{\"reviewerId\":\"REV-1\",\"lines\":[{\"lineId\":\"$LINE_ID\",\"outcome\":\"APPROVED\"}]}")
[ "$CODE" = "409" ] && ok "re-decide on terminal auth rejected HTTP $CODE" || bad "re-decide HTTP=$CODE (want 409)"

echo "== 5. patient-liability estimate (COSTA charge 40 -> MOHCC 100%% covered) =="
EST=$(post /internal/v1/coverage/liability-estimates "{\"coverageId\":\"$MID\",\"benefitCode\":\"IMG_CXR\",\"standardCharge\":40,\"facilityId\":\"FAC-1\"}")
PR=$(echo "$EST"|jg data.patientResponsibility); PE=$(echo "$EST"|jg data.payerEstimate)
python3 -c "print(abs(float('${PR:-99}'))<0.001 and abs(float('${PE:-0}')-40)<0.001)" 2>/dev/null | grep -q True && ok "estimate: payer 40, patient 0 (MOHCC full cover)" || bad "estimate payer=$PE patient=$PR"
echo "$EST" | grep -q ruvimbo-liability-v1 && ok "liability ruleset version recorded" || bad "no ruleset"
# private plan copay path: estimate on a non-covered benefit -> patient pays
EST2=$(post /internal/v1/coverage/liability-estimates "{\"coverageId\":\"$MID\",\"benefitCode\":\"NOT_A_BENEFIT\",\"standardCharge\":100,\"facilityId\":\"FAC-1\"}")
PR2=$(echo "$EST2"|jg data.patientResponsibility)
python3 -c "print(abs(float('${PR2:-0}')-100)<0.001)" 2>/dev/null | grep -q True && ok "non-covered benefit -> patient pays full 100" || bad "non-covered patient=$PR2"

echo "== 6. DB truth =="
[ "$(PSQL "select status from cv_authorisations where id='$AUTH_ID';")" = "APPROVED" ] && ok "cv_authorisations APPROVED" || bad "auth DB"
[ "$(PSQL "select line_status from cv_authorisation_lines where id='$LINE_ID';")" = "APPROVED" ] && ok "cv_authorisation_lines APPROVED" || bad "auth line DB"
[ "$(PSQL "select visits_used from cv_referrals where id='$REF_ID';")" = "1" ] && ok "cv_referrals visits_used=1" || bad "referral DB"
[ "$(PSQL "select count(*) from cv_liability_estimates where coverage_id='$MID';")" -ge 2 ] && ok "cv_liability_estimates rows" || bad "estimate DB"
OUTBOX=$(PSQL "select count(*) from cv_event_outbox where event_type like 'coverage.authorisation.%' and occurred_at > now() - interval '10 minutes';")
[ "${OUTBOX:-0}" -ge 1 ] && ok "authorisation.* events emitted ($OUTBOX)" || bad "no auth events"

echo ""
echo "RESULT: $PASS ok, $FAIL fail  (run=$RUN membership=${MID:-none} auth=${AUTH_ID:-none})"
[ "$FAIL" -eq 0 ] || exit 1
