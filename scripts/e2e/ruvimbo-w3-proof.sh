#!/usr/bin/env bash
# Ruvimbo Wave 3 proof (claims v2 + line-level adjudication + EOB + settlement) — live ingress.
# Enrol fresh citizen on COV-MOHCC-CORE; approve an authorisation for IMG_CXR (reserves benefit);
# file a 3-line claim (covered / authorised / non-covered) -> scrub -> submit -> adjudicate;
# assert independent line outcomes, settlement instruction, EOB, lineage. DB truth.
set -uo pipefail
BASE="${PREVIEW_URL:-https://impilo.mohcc.gov.zw}"
TENANT="00000000-0000-4000-8000-000000000001"
RES=(--resolve impilo.mohcc.gov.zw:443:127.0.0.1)
RUN="w3-$(date +%s)"; PASS=0; FAIL=0
CID="w3-citizen-$RUN"
PLAN="bbbbbbbb-0001-0000-0000-000000000001"   # COV-MOHCC-CORE (IMG_CXR requires auth; GP_CONSULT does not)
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
mkh() { HDRS=(-H "Content-Type: application/json" -H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: p" -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" -H "Authorization: Bearer $TOK" -H "X-Actor-ID: $CID" -H "X-Actor-Type: PROVIDER" -H "X-Purpose-Of-Use: CLAIMS" -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid|cut -c1-8)"); }
post() { mkh; ccurl -X POST "$BASE$1" "${HDRS[@]}" -d "$2"; }
postc() { mkh; ccurl -o /tmp/w3.json -w '%{http_code}' -X POST "$BASE$1" "${HDRS[@]}" -d "$2"; }
get() { mkh; ccurl "$BASE$1" "${HDRS[@]}"; }

echo "== 0. enrol + verify citizen on COV-MOHCC-CORE =="
MID=$(post /internal/v1/coverage/members "{\"clientId\":\"$CID\",\"planId\":\"$PLAN\",\"memberNumber\":\"W3-$RUN\",\"relationship\":\"SELF\",\"status\":\"ACTIVE\"}" | jg data.id)
[ -n "$MID" ] && ok "membership $MID" || bad "enrol"
post "/internal/v1/coverage/members/$MID/verify" "{\"verified\":true}" >/dev/null

echo "== 1. authorise IMG_CXR (reserves benefit) =="
AUTH=$(post /internal/v1/coverage/authorisations "{\"coverageId\":\"$MID\",\"authType\":\"DIAGNOSTIC\",\"requestingProviderId\":\"PRV-1\",\"facilityId\":\"FAC-1\",\"lines\":[{\"benefitCode\":\"IMG_CXR\",\"serviceCode\":\"CXR\",\"quantityRequested\":1,\"estimatedAmount\":40}]}")
AUTH_ID=$(echo "$AUTH"|jg data.id); ALINE=$(echo "$AUTH"|jg data.lines.0.id)
post "/internal/v1/coverage/authorisations/$AUTH_ID/submit" "{}" >/dev/null
DEC=$(post "/internal/v1/coverage/authorisations/$AUTH_ID/decision" "{\"reviewerId\":\"REV-1\",\"lines\":[{\"lineId\":\"$ALINE\",\"outcome\":\"APPROVED\",\"quantityApproved\":1,\"approvedAmount\":40}]}")
[ "$(echo "$DEC"|jg data.status)" = "APPROVED" ] && ok "authorisation APPROVED (IMG_CXR reserved)" || bad "auth decide: $(echo "$DEC"|head -c200)"

echo "== 2. file 3-line claim (covered / authorised / non-covered) =="
CLAIM=$(post /internal/v1/coverage/v2/claims "{\"coverageId\":\"$MID\",\"claimType\":\"OUTPATIENT\",\"facilityId\":\"FAC-1\",\"providerId\":\"PRV-1\",\"encounterId\":\"ENC-$RUN\",\"authorisationId\":\"$AUTH_ID\",\"lines\":[{\"benefitCode\":\"GP_CONSULT\",\"description\":\"GP visit\",\"quantity\":1,\"submittedAmount\":30},{\"benefitCode\":\"IMG_CXR\",\"description\":\"Chest X-ray\",\"quantity\":1,\"submittedAmount\":40},{\"benefitCode\":\"NOT_A_BENEFIT\",\"description\":\"Cosmetic\",\"quantity\":1,\"submittedAmount\":50}]}")
CLAIM_ID=$(echo "$CLAIM"|jg data.id)
[ -n "$CLAIM_ID" ] && ok "claim $(echo "$CLAIM"|jg data.claimNumber) (id $CLAIM_ID, total 120)" || bad "claim create: $(echo "$CLAIM"|head -c250)"

echo "== 3. scrub (pass, warnings) -> submit -> adjudicate =="
SCR=$(post "/internal/v1/coverage/v2/claims/$CLAIM_ID/scrub" "{}")
[ "$(echo "$SCR"|jg data.pass)" = "True" ] && ok "scrub pass" || bad "scrub: $(echo "$SCR"|head -c250)"
echo "$SCR" | grep -q BENEFIT_NOT_COVERED && ok "scrub warns non-covered line" || bad "no scrub warning"
SUB=$(post "/internal/v1/coverage/v2/claims/$CLAIM_ID/submit" "{}")
[ "$(echo "$SUB"|jg data.status)" = "SUBMITTED" ] && ok "submitted" || bad "submit: $(echo "$SUB"|head -c200)"
ADJ=$(post "/internal/v1/coverage/v2/claims/$CLAIM_ID/adjudicate" "{}")
ASTATUS=$(echo "$ADJ"|jg data.status)
[ "$ASTATUS" = "PAYMENT_SCHEDULED" ] || [ "$ASTATUS" = "PARTIALLY_APPROVED" ] && ok "adjudicated ($ASTATUS)" || bad "adjudicate: $(echo "$ADJ"|head -c300)"

echo "== 4. independent line outcomes (ordered lines 1=GP 2=CXR 3=non-covered) =="
LINES=$(get "/internal/v1/coverage/v2/claims/$CLAIM_ID")
[ "$(echo "$LINES"|jg data.lines.0.lineStatus)" = "APPROVED" ] && nf "$(echo "$LINES"|jg data.lines.0.approvedAmount)" 30 | grep -q True && ok "GP_CONSULT APPROVED (payer 30)" || bad "gp line: $(echo "$LINES"|jg data.lines.0.lineStatus)/$(echo "$LINES"|jg data.lines.0.approvedAmount)"
[ "$(echo "$LINES"|jg data.lines.1.lineStatus)" = "APPROVED" ] && nf "$(echo "$LINES"|jg data.lines.1.approvedAmount)" 40 | grep -q True && ok "IMG_CXR APPROVED via auth (payer 40)" || bad "cx line: $(echo "$LINES"|jg data.lines.1.lineStatus)/$(echo "$LINES"|jg data.lines.1.approvedAmount)"
[ "$(echo "$LINES"|jg data.lines.2.lineStatus)" = "DENIED" ] && nf "$(echo "$LINES"|jg data.lines.2.deniedAmount)" 50 | grep -q True && ok "NOT_A_BENEFIT DENIED (patient 50)" || bad "nc line: $(echo "$LINES"|jg data.lines.2.lineStatus)/$(echo "$LINES"|jg data.lines.2.deniedAmount)"
APP=$(echo "$LINES"|jg data.approvedAmount); PAT=$(echo "$LINES"|jg data.patientResponsibility)
nf "$APP" 70 | grep -q True && ok "claim approved 70" || bad "approved=$APP"
nf "$PAT" 50 | grep -q True && ok "patient responsibility 50" || bad "patient=$PAT"
echo "$LINES" | grep -q ruvimbo-adjudication-v1 && ok "adjudication ruleset recorded" || bad "no ruleset"

echo "== 5. settlement instruction + EOB + events =="
SREF=$(echo "$LINES"|jg data.settlementRef)
[ -n "$SREF" ] && ok "settlement ref $SREF" || bad "no settlement ref"
EOB=$(get "/internal/v1/coverage/v2/claims/$CLAIM_ID/explanation-of-benefits")
echo "$EOB" | grep -qi 'not covered by your plan' && ok "EOB plain-language denial explanation" || bad "EOB: $(echo "$EOB"|head -c250)"
EVT=$(get "/internal/v1/coverage/v2/claims/$CLAIM_ID/events")
echo "$EVT" | grep -q adjudicated && ok "claim event trail (adjudicated)" || bad "events: $(echo "$EVT"|head -c200)"

echo "== 6. lineage: void a fresh fully-covered claim =="
C2=$(post /internal/v1/coverage/v2/claims "{\"coverageId\":\"$MID\",\"claimType\":\"OUTPATIENT\",\"facilityId\":\"FAC-1\",\"providerId\":\"PRV-1\",\"lines\":[{\"benefitCode\":\"GP_CONSULT\",\"submittedAmount\":30}]}" | jg data.id)
post "/internal/v1/coverage/v2/claims/$C2/submit" "{}" >/dev/null
post "/internal/v1/coverage/v2/claims/$C2/adjudicate" "{}" >/dev/null
VD=$(post "/internal/v1/coverage/v2/claims/$C2/void" "{\"reason\":\"proof lineage\"}")
[ "$(echo "$VD"|jg data.status)" = "VOIDED" ] && ok "claim voided (lineage)" || bad "void: $(echo "$VD"|head -c200)"

echo "== 7. DB truth =="
[ "$(PSQL "select count(*) from cv_claim_lines where claim_id='$CLAIM_ID';")" = "3" ] && ok "cv_claim_lines = 3" || bad "claim lines DB"
[ "$(PSQL "select line_status from cv_claim_lines where claim_id='$CLAIM_ID' and benefit_code='IMG_CXR';")" = "APPROVED" ] && ok "IMG_CXR line APPROVED in DB" || bad "cx DB"
[ "$(PSQL "select settlement_status from cv_remittances where claim_id='$CLAIM_ID';")" = "PENDING" ] && ok "cv_remittances settlement PENDING (MUSHEX gated)" || bad "remittance DB"
[ "$(PSQL "select count(*) from cv_claim_events where claim_id='$CLAIM_ID';")" -ge 3 ] && ok "cv_claim_events trail" || bad "claim events DB"
OUTBOX=$(PSQL "select count(*) from cv_event_outbox where event_type in ('coverage.claim.adjudicated','coverage.settlement.initiated') and occurred_at > now() - interval '10 minutes';")
[ "${OUTBOX:-0}" -ge 2 ] && ok "claim.adjudicated + settlement.initiated events ($OUTBOX)" || bad "outbox events ($OUTBOX)"

echo ""
echo "RESULT: $PASS ok, $FAIL fail  (run=$RUN claim=${CLAIM_ID:-none})"
[ "$FAIL" -eq 0 ] || exit 1
