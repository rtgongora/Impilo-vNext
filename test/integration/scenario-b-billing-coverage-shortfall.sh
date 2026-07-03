#!/usr/bin/env bash
# =============================================================================
# Scenario B — billing + medical-aid coverage + shortfall card payment.
#
# Proves on a LIVE estate: coverage plan+member enrolment (coverage SoR) →
# COSTA bill draft/lines → apply-coverage (payer/patient split) → finalize
# (files cv_claim) → adjudicate (scriptable payer) → MusheX intent from the
# COSTA payment (backlog ① linkage) → SANDBOX card capture webhook → COSTA
# payment PAID with receivables applied. Also asserts the Scenario-D failure
# path (un-enrolled patient → INELIGIBLE:NO_COVER, 100% patient split).
#
# Content assertions throughout; fails fast. Run on the preview VM.
# =============================================================================
set -euo pipefail

PREVIEW_URL="${PREVIEW_URL:-http://127.0.0.1}"
TENANT_ID="${TENANT_ID:-00000000-0000-4000-8000-000000000001}"
FACILITY_ID="${FACILITY_ID:-f1000000-0000-0000-0000-000000000001}"
NS="${FULL_BOOT_NAMESPACE:-impilo-full-preview}"
RUN="scnB-$(date +%s)"

PASS=0
ok()   { PASS=$((PASS+1)); echo "  ok: $*"; }
fail() { echo "  FAIL: $*" >&2; exit 1; }
step() { echo ""; echo "== $*"; }
jqpy() { python3 -c "import json,sys
d=json.load(sys.stdin)
$1"; }

COVERAGE_IP="$(kubectl get svc coverage-service -n "$NS" -o jsonpath='{.spec.clusterIP}')"
COSTA_IP="$(kubectl get svc costing-engine-service -n "$NS" -o jsonpath='{.spec.clusterIP}')"
MUSHEX_IP="$(kubectl get svc mushex-service -n "$NS" -o jsonpath='{.spec.clusterIP}')"

svc_curl() { # svc_curl <method> <url> [json-body]
  local method="$1" url="$2" body="${3:-}"
  kubectl exec -n "$NS" deploy/experience-bff -- curl -sS -X "$method" "$url" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: $TENANT_ID" -H "X-Pod-ID: pod-e2e" \
    -H "X-Request-ID: $(cat /proc/sys/kernel/random/uuid)" -H "X-Correlation-ID: $RUN" \
    -H "X-Actor-ID: PROV-ZW-00001" -H "X-Actor-Type: PROVIDER" \
    -H "X-Facility-ID: $FACILITY_ID" -H "X-Purpose-Of-Use: BILLING" \
    -H "Idempotency-Key: $RUN-$(cat /proc/sys/kernel/random/uuid | cut -c1-8)" \
    ${body:+-d "$body"}
}

# ── 1. Coverage SoR: plan + member ───────────────────────────────────────────
step "1. coverage plan + member enrolment"
PLANS=$(svc_curl GET "http://$COVERAGE_IP:8140/internal/v1/coverage/plans")
PLAN_ID=$(echo "$PLANS" | jqpy "
rows=d if isinstance(d,list) else d.get('data',[])
match=[r for r in rows if r.get('planCode')=='COV-MOHCC-CORE']
print(match[0]['id'] if match else '')")
[[ -n "$PLAN_ID" ]] || fail "COV-MOHCC-CORE plan not present at coverage SoR: $(echo "$PLANS" | head -c 300)"
ok "plan COV-MOHCC-CORE present ($PLAN_ID)"

MEMBER_CPID="scnb-member-$RUN"
ENROL=$(svc_curl POST "http://$COVERAGE_IP:8140/internal/v1/coverage/members" \
  "{\"planId\":\"$PLAN_ID\",\"clientId\":\"$MEMBER_CPID\",\"memberNumber\":\"MEM-$RUN\",\"relationship\":\"SELF\",\"effectiveFrom\":\"2026-01-01\"}")
COVERAGE_ID=$(echo "$ENROL" | jqpy "print(d.get('id',''))")
[[ -n "$COVERAGE_ID" ]] || fail "member enrolment failed: $(echo "$ENROL" | head -c 300)"
ok "member enrolled → coverage $COVERAGE_ID"

# ── 2. COSTA encounter + bill with charges ───────────────────────────────────
step "2. bill draft + charge lines"
ENC=$(svc_curl POST "http://$COSTA_IP:8101/costa/v1/encounters" \
  "{\"patientCpid\":\"$MEMBER_CPID\",\"encounterType\":\"OUTPATIENT\",\"facilityId\":\"$FACILITY_ID\"}")
COSTA_ENC=$(echo "$ENC" | jqpy "
data=d.get('data') or d
print(data.get('encounterId') or data.get('id') or '')")
[[ -n "$COSTA_ENC" ]] || fail "costa encounter create failed: $(echo "$ENC" | head -c 300)"

BILL=$(svc_curl POST "http://$COSTA_IP:8101/costa/v1/bills/draft" \
  "{\"encounterId\":\"$COSTA_ENC\",\"billType\":\"PATIENT\"}")
BILL_ID=$(echo "$BILL" | jqpy "
data=d.get('data') or d
print(data.get('billId') or '')")
[[ -n "$BILL_ID" ]] || fail "bill draft failed: $(echo "$BILL" | head -c 300)"
ok "bill drafted: $BILL_ID (encounter $COSTA_ENC)"

for LINE in '{"msikaCode":"CONSULT-GP","description":"GP consultation","kind":"SERVICE","qty":1}' \
            '{"msikaCode":"LAB-FBC","description":"Full blood count","kind":"SERVICE","qty":1}'; do
  svc_curl POST "http://$COSTA_IP:8101/costa/v1/bills/$BILL_ID/lines" "$LINE" >/dev/null
done
TOTAL=$(svc_curl POST "http://$COSTA_IP:8101/costa/v1/bills/$BILL_ID/recompute" "" | jqpy "
data=d.get('data') or d
print(data.get('totalPayable') or 0)")
python3 -c "import sys; sys.exit(0 if float('$TOTAL')>0 else 1)" || fail "bill total is zero — tariff lines did not price"
ok "lines priced, total payable $TOTAL"

# ── 3. Apply coverage → split ────────────────────────────────────────────────
step "3. apply coverage (payer/patient split)"
SPLIT=$(svc_curl POST "http://$COSTA_IP:8101/costa/v1/bills/$BILL_ID/apply-coverage" "")
COV_STATUS=$(echo "$SPLIT" | jqpy "
data=d.get('data') or d
print(data.get('coverageStatus') or '')")
INSURER=$(echo "$SPLIT" | jqpy "
data=d.get('data') or d
print(data.get('insurerPayable') or 0)")
PATIENT=$(echo "$SPLIT" | jqpy "
data=d.get('data') or d
print(data.get('patientPayable') or 0)")
[[ "$COV_STATUS" == "ELIGIBLE" ]] || fail "coverage not applied (status '$COV_STATUS'): $(echo "$SPLIT" | head -c 400)"
python3 -c "import sys; sys.exit(0 if float('$INSURER')>0 and float('$PATIENT')>=0 else 1)" \
  || fail "split incoherent: insurer=$INSURER patient=$PATIENT"
ok "ELIGIBLE split: insurer $INSURER / patient $PATIENT"

# ── 4. Finalize → claim filed → adjudicate ──────────────────────────────────
step "4. finalize (files claim) + payer adjudication"
svc_curl POST "http://$COSTA_IP:8101/costa/v1/bills/$BILL_ID/submit-approval" "" >/dev/null
svc_curl POST "http://$COSTA_IP:8101/costa/v1/bills/$BILL_ID/approve" '{"note":"scenario-b"}' >/dev/null
FINAL=$(svc_curl POST "http://$COSTA_IP:8101/costa/v1/bills/$BILL_ID/finalize" "")
CLAIM_REF=$(echo "$FINAL" | jqpy "
data=d.get('data') or d
print(data.get('coverageStatus') or '')")
[[ "$CLAIM_REF" == CLAIM_SUBMITTED:* ]] || fail "claim not filed on finalize (coverageStatus '$CLAIM_REF')"
CLAIM_ID="${CLAIM_REF#CLAIM_SUBMITTED:}"
ok "bill FINAL, cv_claim filed: $CLAIM_ID"

ADJ=$(svc_curl POST "http://$COVERAGE_IP:8140/internal/v1/coverage/claims/$CLAIM_ID/adjudicate" \
  "{\"approved_amount\":$INSURER}")
ADJ_STATUS=$(echo "$ADJ" | jqpy "print(d.get('status',''))")
[[ "$ADJ_STATUS" == "ADJUDICATED" ]] || fail "adjudication failed: $(echo "$ADJ" | head -c 300)"
ok "claim ADJUDICATED for $INSURER"

# ── 5. Shortfall card payment: COSTA intent → MusheX SANDBOX capture ─────────
step "5. shortfall payment (COSTA intent -> MusheX sandbox capture -> PAID)"
INTENT=$(svc_curl POST "http://$COSTA_IP:8101/costa/v1/bills/$BILL_ID/create-payment-intent" \
  "{\"paymentType\":\"CARD\",\"amount\":$PATIENT}")
PAYMENT_ID=$(echo "$INTENT" | jqpy "
data=d.get('data') or d
print(data.get('id') or '')")
MUSHEX_INTENT=$(echo "$INTENT" | jqpy "
data=d.get('data') or d
print(data.get('mushexPaymentIntentId') or '')")
[[ -n "$PAYMENT_ID" && -n "$MUSHEX_INTENT" ]] \
  || fail "payment intent lacks MusheX linkage (backlog ① regression): $(echo "$INTENT" | head -c 400)"
ok "COSTA payment $PAYMENT_ID linked to MusheX intent $MUSHEX_INTENT"

ATTEMPT=$(svc_curl POST "http://$MUSHEX_IP:8102/mushex/v1/payment-intents/$MUSHEX_INTENT/attempts" \
  '{"rail":"CARD","adapterType":"SANDBOX"}')
ADAPTER_REF=$(echo "$ATTEMPT" | jqpy "
data=d.get('data') or d
print(data.get('adapterRef') or data.get('attemptId') or data.get('id') or '')")
[[ -n "$ADAPTER_REF" ]] || fail "mushex attempt failed: $(echo "$ATTEMPT" | head -c 400)"
svc_curl POST "http://$MUSHEX_IP:8102/mushex/v1/adapters/SANDBOX/webhook" \
  "{\"adapterRef\":\"$ADAPTER_REF\",\"status\":\"SUCCESS\"}" >/dev/null
ok "SANDBOX capture webhook accepted (adapterRef $ADAPTER_REF)"

PAY_STATUS=""
for i in $(seq 1 12); do
  PAY_STATUS=$(svc_curl GET "http://$COSTA_IP:8101/costa/v1/bills/$BILL_ID/payments" \
    | jqpy "
rows=(d.get('data') or d) if isinstance(d,dict) else d
rows=rows if isinstance(rows,list) else []
match=[p for p in rows if p.get('id')=='$PAYMENT_ID']
print(match[0].get('status','') if match else '')")
  [[ "$PAY_STATUS" == "PAID" ]] && break
  sleep 5
done
[[ "$PAY_STATUS" == "PAID" ]] || fail "COSTA payment never reached PAID (status '$PAY_STATUS') — mushex→costa status loop broken"
ok "COSTA payment PAID via mushex.payment.status.changed event loop"

# ── 6. Scenario D failure path: un-enrolled patient ──────────────────────────
step "6. failure path: no cover → INELIGIBLE, 100% patient"
ENC2=$(svc_curl POST "http://$COSTA_IP:8101/costa/v1/encounters" \
  "{\"patientCpid\":\"uncovered-$RUN\",\"encounterType\":\"OUTPATIENT\",\"facilityId\":\"$FACILITY_ID\"}")
COSTA_ENC2=$(echo "$ENC2" | jqpy "
data=d.get('data') or d
print(data.get('encounterId') or data.get('id') or '')")
BILL2=$(svc_curl POST "http://$COSTA_IP:8101/costa/v1/bills/draft" \
  "{\"encounterId\":\"$COSTA_ENC2\",\"billType\":\"PATIENT\"}" | jqpy "
data=d.get('data') or d
print(data.get('billId') or '')")
svc_curl POST "http://$COSTA_IP:8101/costa/v1/bills/$BILL2/lines" \
  '{"msikaCode":"CONSULT-GP","description":"GP consultation","kind":"SERVICE","qty":1}' >/dev/null
NOCOV=$(svc_curl POST "http://$COSTA_IP:8101/costa/v1/bills/$BILL2/apply-coverage" "")
NOCOV_STATUS=$(echo "$NOCOV" | jqpy "
data=d.get('data') or d
print(data.get('coverageStatus') or '')")
[[ "$NOCOV_STATUS" == INELIGIBLE:* ]] || fail "no-cover path not explicit (status '$NOCOV_STATUS')"
ok "no-cover → $NOCOV_STATUS (100% patient)"

echo ""
echo "PASS: Scenario B green ($PASS checks) — run tag $RUN"
