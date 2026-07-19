#!/usr/bin/env bash
# Ruvimbo (Coverage) Wave 1 foundation proof — end-to-end through the REAL ingress
# (Traefik 127.0.0.1:443 -> envoy -> experience-bff -> coverage-service), with DB truth.
#
# Proves the spec §42 Wave-1 chain on a self-contained new payer, plus benefits +
# eligibility v2 + signed tokens on the enrolled membership:
#   payer -> scheme -> product -> draft version -> benefit -> PUBLISH (immutable) ->
#   flat plan linked to payer+version -> enrol principal -> duplicate 409 -> verify ->
#   dependant -> benefit accumulators -> reserve (+ over-limit 409) -> eligibility v2
#   (reason codes + remaining + copay) -> signed token issue + verify -> suspend payer
#   blocks new enrolment (409) -> reactivate. DB truth on every table.
set -uo pipefail
BASE="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
TENANT="00000000-0000-4000-8000-000000000001"
RES=(--resolve impilo.mohcc.gov.zw:443:127.0.0.1)
RUN="ruv-$(date +%s)"
PASS=0; FAIL=0
CID="ruv-citizen-$RUN"          # fresh principal each run (no duplicate collisions)
DEP="ruv-dependant-$RUN"
PSQL() { kubectl exec -n impilo-full-preview deploy/postgres -- psql -U impilo -d coverage -tAc "$1" 2>/dev/null; }
ccurl() { curl -sk -m15 "${RES[@]}" "$@"; }
jg() { python3 -c "import json,sys
d=json.load(sys.stdin)
cur=d
for k in '$1'.split('.'):
 cur=cur.get(k) if isinstance(cur,dict) else (cur[int(k)] if isinstance(cur,list) and k.lstrip('-').isdigit() else None)
 if cur is None: break
print(cur if cur is not None else '')" 2>/dev/null; }
ok() { PASS=$((PASS+1)); echo "  ok: $*"; }
bad() { FAIL=$((FAIL+1)); echo "  FAIL: $*"; }

TOK=$(ccurl -X POST "$BASE/internal/v1/auth/login" -H 'Content-Type: application/json' \
  -H "X-Tenant-ID: $TENANT" -H 'X-Pod-ID: p' -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" \
  -H "X-Correlation-ID: $RUN" -H "Idempotency-Key: $RUN-l" \
  -d '{"email":"learner.tembo","password":"ImpiloTest123!"}' | jg data.attributes.token)
[ -n "$TOK" ] && ok "login" || { bad "login"; exit 1; }
mkh() { HDRS=(-H "Content-Type: application/json" -H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: p" \
  -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" \
  -H "Authorization: Bearer $TOK" -H "X-Actor-ID: $CID" -H "X-Actor-Type: CITIZEN" \
  -H "X-Purpose-Of-Use: COVERAGE_ADMIN" \
  -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid|cut -c1-8)"); }
post() { mkh; ccurl -X POST "$BASE$1" "${HDRS[@]}" -d "$2"; }
postc() { mkh; ccurl -o /tmp/ruv.json -w '%{http_code}' -X POST "$BASE$1" "${HDRS[@]}" -d "$2"; }
get() { mkh; ccurl "$BASE$1" "${HDRS[@]}"; }

echo "== 1. create payer =="
PAYER=$(post /internal/v1/coverage/payers "{\"payerCode\":\"PAY-$RUN\",\"legalName\":\"Test Payer $RUN\",\"organisationType\":\"MEDICAL_AID\"}")
PAYER_ID=$(echo "$PAYER" | jg data.id)
[ -n "$PAYER_ID" ] && ok "payer $PAYER_ID" || bad "payer: $(echo "$PAYER"|head -c200)"

echo "== 2. scheme -> product -> draft version =="
SCHEME_ID=$(post "/internal/v1/coverage/payers/$PAYER_ID/schemes" "{\"schemeCode\":\"SCH-$RUN\",\"name\":\"Scheme $RUN\",\"coverageType\":\"MEDICAL_AID\"}" | jg data.id)
[ -n "$SCHEME_ID" ] && ok "scheme $SCHEME_ID" || bad "scheme"
PRODUCT_ID=$(post "/internal/v1/coverage/schemes/$SCHEME_ID/products" "{\"productCode\":\"PRD-$RUN\",\"name\":\"Product $RUN\"}" | jg data.id)
[ -n "$PRODUCT_ID" ] && ok "product $PRODUCT_ID" || bad "product"
VER=$(post "/internal/v1/coverage/products/$PRODUCT_ID/versions" "{\"currency\":\"USD\",\"approvalAuthority\":\"proof\"}")
VER_ID=$(echo "$VER" | jg data.id)
VER_STATUS=$(echo "$VER" | jg data.status)
[ "$VER_STATUS" = "DRAFT" ] && ok "version $VER_ID DRAFT" || bad "version status=$VER_STATUS"

echo "== 3. add benefit + publish (then immutable) =="
BEN=$(post "/internal/v1/coverage/plan-versions/$VER_ID/benefits" "{\"benefitCode\":\"GP_CONSULT\",\"category\":\"PROFESSIONAL_FEE\",\"description\":\"GP visit\",\"coveragePercent\":80,\"copayAmount\":5,\"annualLimit\":100}")
[ -n "$(echo "$BEN"|jg data.id)" ] && ok "benefit GP_CONSULT (limit 100, copay 5)" || bad "benefit: $(echo "$BEN"|head -c200)"
PUB=$(post "/internal/v1/coverage/plan-versions/$VER_ID/publish" "{}")
[ "$(echo "$PUB"|jg data.status)" = "PUBLISHED" ] && ok "version PUBLISHED" || bad "publish: $(echo "$PUB"|head -c200)"
CODE=$(postc "/internal/v1/coverage/plan-versions/$VER_ID/publish" "{}")
[ "$CODE" = "400" ] && ok "re-publish rejected (immutable) HTTP $CODE" || bad "re-publish HTTP=$CODE (want 400)"

echo "== 4. flat live plan linked to payer + version =="
PLAN=$(post /internal/v1/coverage/plans "{\"planCode\":\"COV-$RUN\",\"planName\":\"Plan $RUN\",\"payerId\":\"PAY-$RUN\",\"planType\":\"PRIVATE\",\"effectiveFrom\":\"$(date +%F)\",\"payerRef\":\"$PAYER_ID\",\"planVersionId\":\"$VER_ID\"}")
PLAN_ID=$(echo "$PLAN" | jg data.id)
[ -n "$PLAN_ID" ] && ok "plan $PLAN_ID (linked)" || bad "plan: $(echo "$PLAN"|head -c200)"

echo "== 5. enrol principal + duplicate 409 =="
CID2="$CID"; CID="$CID"   # actor already CID
ENR=$(post /internal/v1/coverage/members "{\"clientId\":\"$CID\",\"planId\":\"$PLAN_ID\",\"memberNumber\":\"M-$RUN\",\"relationship\":\"SELF\",\"status\":\"ACTIVE\"}")
MID=$(echo "$ENR" | jg data.id)
[ -n "$MID" ] && ok "enrolled membership $MID" || bad "enrol: $(echo "$ENR"|head -c200)"
CODE=$(postc /internal/v1/coverage/members "{\"clientId\":\"$CID\",\"planId\":\"$PLAN_ID\",\"memberNumber\":\"M-$RUN-dup\",\"relationship\":\"SELF\",\"status\":\"ACTIVE\"}")
[ "$CODE" = "409" ] && ok "duplicate enrolment rejected HTTP $CODE" || bad "duplicate HTTP=$CODE (want 409)"

echo "== 6. verify membership (verification axis) =="
VM=$(post "/internal/v1/coverage/members/$MID/verify" "{\"source\":\"REGISTRY_ATTESTED\",\"verified\":true}")
[ "$(echo "$VM"|jg data.verificationStatus)" = "VERIFIED" ] && ok "membership VERIFIED" || bad "verify: $(echo "$VM"|head -c200)"

echo "== 7. add dependant =="
DEPR=$(post "/internal/v1/coverage/members/$MID/dependants" "{\"dependantClientId\":\"$DEP\",\"relationship\":\"CHILD\",\"memberNumber\":\"D-$RUN\"}")
DEP_ID=$(echo "$DEPR" | jg data.id)
[ -n "$DEP_ID" ] && ok "dependant $DEP_ID (CHILD)" || bad "dependant: $(echo "$DEPR"|head -c200)"

echo "== 8. benefit accumulators + reservation-aware ledger =="
ACC=$(get "/internal/v1/coverage/members/$MID/benefit-accumulators")
echo "$ACC" | grep -q GP_CONSULT && ok "accumulators list GP_CONSULT" || bad "accumulators: $(echo "$ACC"|head -c200)"
RES1=$(post "/internal/v1/coverage/members/$MID/benefits/reserve" "{\"benefitCode\":\"GP_CONSULT\",\"amount\":60,\"reference\":\"$RUN-r1\"}")
[ "$(echo "$RES1"|jg data.applied)" = "True" ] && ok "reserved 60 (remaining $(echo "$RES1"|jg data.remaining))" || bad "reserve: $(echo "$RES1"|head -c200)"
CODE=$(postc "/internal/v1/coverage/members/$MID/benefits/reserve" "{\"benefitCode\":\"GP_CONSULT\",\"amount\":60,\"reference\":\"$RUN-r2\"}")
# 100 limit, 60 reserved, +60 would exceed -> engine returns applied=false -> BFF 409
[ "$CODE" = "409" ] && ok "over-limit reserve rejected HTTP $CODE" || bad "over-limit HTTP=$CODE (want 409)"
# idempotent replay of r1 returns the same snapshot, no double-apply
REPLAY=$(post "/internal/v1/coverage/members/$MID/benefits/reserve" "{\"benefitCode\":\"GP_CONSULT\",\"amount\":60,\"reference\":\"$RUN-r1\"}")
[ "$(echo "$REPLAY"|jg data.reserved)" = "60.00" ] && ok "idempotent replay (reserved still 60.00)" || bad "replay reserved=$(echo "$REPLAY"|jg data.reserved)"

echo "== 9. eligibility v2 + signed token =="
EV=$(post /internal/v1/coverage/eligibility/v2 "{\"coverageId\":\"$MID\",\"benefitCode\":\"GP_CONSULT\",\"facilityId\":\"FAC-1\",\"issueToken\":true}")
EV_STATUS=$(echo "$EV" | jg data.status)
echo "$EV_STATUS" | grep -qiE 'ELIGIBLE|PARTIALLY' && ok "eligibility v2 status=$EV_STATUS" || bad "eligibility v2: $(echo "$EV"|head -c250)"
echo "$EV" | grep -q ruvimbo-eligibility-v1 && ok "ruleset version recorded" || bad "no ruleset version"
echo "$EV" | grep -q COVERAGE_ACTIVE && ok "reason codes present" || bad "no reason codes"
TOKEN=$(echo "$EV" | jg data.signedToken)
[ -n "$TOKEN" ] && ok "signed token issued" || bad "no token"
VT=$(post /internal/v1/coverage/eligibility/tokens/verify "{\"token\":\"$TOKEN\"}")
[ "$(echo "$VT"|jg data.valid)" = "True" ] && ok "token verifies valid" || bad "token verify: $(echo "$VT"|head -c200)"
BADVT=$(post /internal/v1/coverage/eligibility/tokens/verify "{\"token\":\"${TOKEN}tamper\"}")
[ "$(echo "$BADVT"|jg data.valid)" = "False" ] && ok "tampered token rejected" || bad "tampered token accepted!"

echo "== 10. suspended payer blocks NEW enrolment; reactivate restores =="
post "/internal/v1/coverage/payers/$PAYER_ID/suspend" "{}" >/dev/null
CODE=$(postc /internal/v1/coverage/members "{\"clientId\":\"ruv-blocked-$RUN\",\"planId\":\"$PLAN_ID\",\"memberNumber\":\"BLK-$RUN\",\"relationship\":\"SELF\",\"status\":\"ACTIVE\"}")
[ "$CODE" = "409" ] && ok "suspended payer blocks new enrolment HTTP $CODE" || bad "suspend-gate HTTP=$CODE (want 409)"
post "/internal/v1/coverage/payers/$PAYER_ID/reactivate" "{}" >/dev/null
CODE=$(postc /internal/v1/coverage/members "{\"clientId\":\"ruv-afterreact-$RUN\",\"planId\":\"$PLAN_ID\",\"memberNumber\":\"AR-$RUN\",\"relationship\":\"SELF\",\"status\":\"ACTIVE\"}")
[ "$CODE" = "201" ] && ok "reactivated payer accepts enrolment HTTP $CODE" || bad "reactivate HTTP=$CODE (want 201)"

echo "== 11. DB truth =="
[ "$(PSQL "select count(*) from cv_payers where payer_code='PAY-$RUN';")" = "1" ] && ok "cv_payers row" || bad "cv_payers"
[ "$(PSQL "select status from cv_plan_versions where id='$VER_ID';")" = "PUBLISHED" ] && ok "cv_plan_versions PUBLISHED" || bad "plan_versions"
[ "$(PSQL "select verification_status from cv_member_coverage where id='$MID';")" = "VERIFIED" ] && ok "membership VERIFIED in DB" || bad "membership verification"
[ "$(PSQL "select count(*) from cv_member_coverage where principal_member_id='$MID';")" = "1" ] && ok "dependant row linked" || bad "dependant DB"
[ "$(PSQL "select reserved_amount from cv_benefit_accumulators where member_id='$MID' and benefit_code='GP_CONSULT';")" = "60.00" ] && ok "accumulator reserved 60.00" || bad "accumulator DB"
[ "$(PSQL "select count(*) from cv_benefit_movements m join cv_benefit_accumulators a on a.id=m.accumulator_id where a.member_id='$MID';")" = "1" ] && ok "one movement row (idempotent — replay did not duplicate)" || bad "movement ledger DB"
EVID=$(PSQL "select ruleset_version from cv_eligibility_checks where coverage_id='$MID' order by checked_at desc limit 1;")
[ "$EVID" = "ruvimbo-eligibility-v1" ] && ok "eligibility decision persisted with ruleset version" || bad "eligibility DB ($EVID)"
OUTBOX=$(PSQL "select count(*) from cv_event_outbox where correlation_id::text like '%' and event_type like 'coverage.%' and occurred_at > now() - interval '10 minutes';")
[ "${OUTBOX:-0}" -ge 1 ] && ok "coverage.* events emitted to outbox ($OUTBOX recent)" || bad "no outbox events"

echo ""
echo "RESULT: $PASS ok, $FAIL fail  (run=$RUN payer=$PAYER_ID membership=${MID:-none})"
[ "$FAIL" -eq 0 ] || exit 1
