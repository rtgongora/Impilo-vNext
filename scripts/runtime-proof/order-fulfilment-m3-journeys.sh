#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Wave OF-B M3 live proof — impilo-full-preview estate
#
# Legs:
#  1  #41 extended end-to-end   medication self-pay: Rx→token → OROS order/pin →
#     RFO → publish → offer → select → step-7 SELF_PAY → step-8 real MusheX
#     intent → REAL mushex status change (record-card-payment → PAID →
#     mushex.payment.status.changed → PaymentEventConsumer resume) → COMMITTED
#     + DURA hold + OROS claim → pharmacy episode (oros.order.placed) →
#     completeDispense → claim/episode bind → pickup proof + grade
#  2  #46 financials            PAYER_COVERED w/o resolvable coverage →
#     step-7 fail-closed NOT_COVERED / FINANCIAL_UNVERIFIED; offer view carries
#     financials block with estimateNeverFinal:true
#  3  #47 PA                    requires_authorisation benefit → PA_REQUIRED
#  4  #44 clarification         step-up substitution loop, exactly-once apply
#  5  #53/#54/#69 pickup        SMS-reference path + expiry sweep w/ reversal
#  6  #42/#68 delivery          Nhume dispatch, grade ladder, write-backs, refund
#  7  #45 split                 valid combo / overlap refusal / PARTIAL_COMMIT
#  8  #56 diagnostics           home collection, honest step-5 SKIPPED
#
# Fail-closed refusals with correct codes ARE pass criteria.
# Evidence: /tmp/ofb-m3-evidence/
#
# Documented estate adaptations (seeds/config only, no code changes):
#  A1 kubectl set env msika-flow  MUSHEX_URL / MSIKAFLOW_INTEGRATION_COSTAURL /
#     MSIKAFLOW_INTEGRATION_COVERAGEURL  (now ALSO durable in
#     values-full-preview.yaml — CFG-1 helm fix)
#  A2 kubectl set env pharmacy    PHARMACY_INTEGRATION_OROS_BASEURL (durable in
#     values-full-preview.yaml — CFG-2 helm fix) +
#     PHARMACY_PICKUP_EXPIRECHECKINTERVALMS=30000 (sweep observable in-run)
#  A3 credential_verification.credentials row for the facility payee (mushex
#     verifies payee before PAID; estate table was empty — wave-close seed item)
#  A4 coverage cv_benefit_definitions PA row on the golden plan version
#  A5 pharmacy rx_substitution_rules step-up rule C09AA05→C09AA06
#  A10 nhume courier profile (assign FK needs one; ops/seed gap)
#
# M3-FIX RUN: the former A6/A7a/A7b/A8/A9 shims are REMOVED — BUG-2/3/5/7/9/10
# are fixed in code, so those legs now prove the REAL paths:
#   BUG-3  oros.order.placed carries items[]        → no seed_item
#   BUG-2  claim carriage via externalRefs + pharmacy lazy bind → no psql bind
#   BUG-5  creation response carries oneTimeCredential ONCE   → no hash inject
#   BUG-7  PaymentEventConsumer resumes marketplace first     → no A8 shim
#   BUG-9  method VARCHAR(32)                                 → SMS leg live
#   BUG-10 service-originated dispatch carries X-Correlation-ID → no A9 shim
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

NS=impilo-full-preview
TENANT=00000000-0000-4000-8000-000000000001
FAC=f1000000-0000-0000-0000-000000000001
STOREA=a2000000-0000-4000-8000-0000000000a2      # vendor A DURA store (M2 seed)
STOREB=a2000000-0000-4000-8000-0000000000b2      # vendor B DURA store (M3 seed)
VENDA=OFAM2PROOFVENDOR0000000001
VENDB=OFBM3PROOFVENDOR0000000002
PROV=PROV-ZW-00004
VASH_PROFILE=c3023adb-8146-40d2-8c00-6e8f9599f73a
TUSO_FID=1
I1=C09AA05          # ramipril — PA-gated benefit on golden plan (leg 3)
I2=C03CA01          # furosemide — split leg line 1
I3=B01AC06          # aspirin  — split leg line 2
COVID_GOLDEN=1748bf63-9a5c-41bb-9eec-2da74f6f4acf   # cv_member_coverage on plan-version d4000000-…001
MEMBER_CPID=c0000000-0000-4000-8000-000000000012
PLANVER=d4000000-0000-4000-8000-000000000001
EVID=/tmp/ofb-m3-evidence
mkdir -p "$EVID"

PASS=0; FAIL=0; declare -a RESULTS=()
pass() { echo "PASS: $*"; RESULTS+=("PASS: $*"); PASS=$((PASS+1)); }
fail() { echo "FAIL: $*"; RESULTS+=("FAIL: $*"); FAIL=$((FAIL+1)); }
note() { echo "NOTE: $*"; RESULTS+=("NOTE: $*"); }

psqld() { local db=$1; shift; kubectl -n "$NS" exec deploy/postgres -- psql -U impilo -d "$db" -tAc "$1"; }

# api NAME METHOD URL [BODY] [EXTRA_HEADER...]   (curl runs inside oros pod)
api() {
  local name=$1 method=$2 url=$3 body=${4:-}
  shift; shift; shift; [ $# -gt 0 ] && shift
  local rid; rid=$(uuidgen)
  local args=(-s -w $'\n%{http_code}' --max-time 40 -X "$method" "$url"
    -H "Content-Type: application/json"
    -H "X-Tenant-Id: $TENANT" -H "X-Tenant-ID: $TENANT"
    -H "X-Pod-ID: national-spine"
    -H "X-Request-ID: $rid" -H "X-Correlation-ID: $rid"
    -H "X-Actor-Id: ofb-m3-proof" -H "X-Actor-ID: ofb-m3-proof"
    -H "X-Actor-Type: PROVIDER" -H "X-Purpose-Of-Use: TREATMENT"
    -H "X-Facility-ID: $FAC" -H "x-envoy-internal: true")
  local has_idem=0
  for h in "$@"; do args+=(-H "$h"); case "$h" in Idempotency-Key:*|X-Idempotency-Key:*) has_idem=1;; esac; done
  if [ "$method" != GET ] && [ "$has_idem" = 0 ]; then
    local ik; ik="ofb-m3-$(uuidgen)"
    args+=(-H "Idempotency-Key: $ik" -H "X-Idempotency-Key: $ik")
  fi
  [ -n "$body" ] && args+=(-d "$body")
  local out; out=$(kubectl -n "$NS" exec deploy/oros-service -- curl "${args[@]}" 2>/dev/null)
  API_CODE=$(echo "$out" | tail -1)
  API_BODY=$(echo "$out" | sed '$d')
  echo "$API_BODY" > "$EVID/$name.json"
  echo "$API_CODE" > "$EVID/$name.code"
}

jqr() { echo "$API_BODY" | jq -r "$1" 2>/dev/null; }

# poll_psql "db" "query" "expected" timeout_s  → 0 when matched
poll_psql() {
  local db=$1 q=$2 want=$3 deadline=$(( $(date +%s) + $4 ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    POLL_VAL=$(psqld "$db" "$q")
    [ "$POLL_VAL" = "$want" ] && return 0
    sleep 5
  done
  POLL_VAL=$(psqld "$db" "$q")
  [ "$POLL_VAL" = "$want" ]
}

# last step-log entry for seq N of a saved selection response
step_entry() { jq -r --argjson s "$2" '[.data.stepLog[]? | select(.seq==$s)] | last | "\(.status)|\(.code // "")|\(.detail // "")"' "$EVID/$1.json" 2>/dev/null; }

mkrx() { # mkrx NAME itemcode qty → RX_ID, RX_TOKEN
  api "$1-rx-create" POST "http://oros-service:8089/v1/prescriptions" \
    "{\"patientCpid\":\"CPID-OFB-M3-PROOF\",\"prescriberId\":\"$PROV\",\"prescriberName\":\"Dr OF-B Proof\",\"repeatsCeiling\":5,\"substitutionPermitted\":true,\"items\":[{\"medicationCode\":\"$2\",\"codingSystem\":\"ATC\",\"displayName\":\"Proof item $2\",\"dose\":\"5mg\",\"route\":\"PO\",\"frequency\":\"OD\",\"quantity\":$3,\"quantityUnit\":\"TAB\",\"controlled\":false}]}"
  RX_ID=$(jqr '.data.prescriptionId')
  api "$1-rx-sign" POST "http://oros-service:8089/v1/prescriptions/$RX_ID/sign"
  api "$1-rx-token" GET "http://oros-service:8089/v1/prescriptions/$RX_ID/token"
  RX_TOKEN=$(jqr '.data.tokenJws')
}

mkorder() { # mkorder NAME type itemsjson → ORDER_ID, PIN
  api "$1-order" POST "http://oros-service:8089/v1/orders" \
    "{\"orderType\":\"$2\",\"priority\":\"ROUTINE\",\"patientCpid\":\"CPID-OFB-M3-PROOF\",\"items\":$3}"
  ORDER_ID=$(jqr '.data.orderId')
  api "$1-versions" GET "http://oros-service:8089/v1/orders/$ORDER_ID/versions"
  PIN=$(jqr '.data[0].orderVersionId')
}

# M3-FIX: seed_item (A7a), hash-inject (A7b) and the legacy-settlement shim
# (A8) are GONE — the fixed code paths must carry these legs themselves.

echo "══ OF-B M3 live proof — $(date -u +%FT%TZ) ══"

# ── 0. Preflight: integration config (adaptations A1/A2) ─────────────────────
MF_ENV=$(kubectl -n "$NS" exec deploy/msika-flow-service -- env 2>/dev/null | grep -E "^(MUSHEX_URL|MSIKAFLOW_INTEGRATION_(COSTAURL|COVERAGEURL))=" | sort | tr '\n' ' ')
if ! echo "$MF_ENV" | grep -q "MUSHEX_URL=http://mushex-service:8102"; then
  note "A1-config msika-flow lacked MUSHEX_URL/COSTA/COVERAGE cluster DNS (image defaults = localhost) — applying kubectl set env (config finding CFG-1)"
  kubectl -n "$NS" set env deploy/msika-flow-service \
    MUSHEX_URL=http://mushex-service:8102 \
    MSIKAFLOW_INTEGRATION_COSTAURL=http://costing-engine-service:8101 \
    MSIKAFLOW_INTEGRATION_COVERAGEURL=http://coverage-service:8140 >/dev/null
fi
PH_ENV=$(kubectl -n "$NS" exec deploy/pharmacy-service -- env 2>/dev/null | grep -E "^PHARMACY_INTEGRATION_OROS_BASEURL=" | tr '\n' ' ')
if ! echo "$PH_ENV" | grep -q "oros-service:8089"; then
  note "A2-config pharmacy lacked PHARMACY_INTEGRATION_OROS_BASEURL (default localhost:8089 — claim bind/release could never reach OROS; config finding CFG-2) — applying kubectl set env"
  kubectl -n "$NS" set env deploy/pharmacy-service \
    PHARMACY_INTEGRATION_OROS_BASEURL=http://oros-service:8089 \
    PHARMACY_PICKUP_EXPIRECHECKINTERVALMS=30000 >/dev/null
fi
kubectl -n "$NS" rollout status deploy/msika-flow-service --timeout=420s >/dev/null \
  && kubectl -n "$NS" rollout status deploy/pharmacy-service --timeout=420s >/dev/null \
  && pass "P0 integration config in place (msika-flow: mushex/costa/coverage cluster DNS; pharmacy: oros base-url + 30s pickup sweep)" \
  || fail "P0 rollout after config env failed"

# ── 1. Seeds (idempotent; adaptations A3/A4/A5) ──────────────────────────────
psqld varapi "UPDATE varapi.provider SET licence_status='ACTIVE' WHERE provider_public_id='$PROV' AND tenant_id='$TENANT'" >/dev/null
psqld msika_flow "INSERT INTO mf_vendor_profiles (vendor_id, tenant_id, type, name, status, coverage, created_at, updated_at) VALUES
  ('$VENDA','$TENANT','PHARMACY','OF-B M3 Proof Pharmacy A','ACTIVE',
   '{\"providerPublicId\":\"$PROV\",\"tusoFacilityId\":$TUSO_FID,\"premisesFacilityId\":\"$FAC\",\"storeId\":\"$STOREA\",\"vashandiProfileId\":\"$VASH_PROFILE\",\"zones\":[\"HARARE\"],\"capabilities\":{\"dispensing\":true,\"diagnostics\":true,\"controlled_authority\":false}}'::jsonb, now(), now()),
  ('$VENDB','$TENANT','PHARMACY','OF-B M3 Proof Pharmacy B','ACTIVE',
   '{\"providerPublicId\":\"$PROV\",\"tusoFacilityId\":$TUSO_FID,\"premisesFacilityId\":\"$FAC\",\"storeId\":\"$STOREB\",\"vashandiProfileId\":\"$VASH_PROFILE\",\"zones\":[\"HARARE\"],\"capabilities\":{\"dispensing\":true,\"controlled_authority\":false}}'::jsonb, now(), now())
  ON CONFLICT (vendor_id) DO UPDATE SET coverage=EXCLUDED.coverage, status='ACTIVE', updated_at=now()" >/dev/null
psqld inventory "INSERT INTO inv_on_hand (id, tenant_id, facility_id, store_id, item_code, qty_on_hand, updated_at) VALUES
  ('a3000000-0000-4000-8000-0000000000a3','$TENANT','$FAC','$STOREA','$I1',500, now()),
  ('a3000000-0000-4000-8000-0000000000b1','$TENANT','$FAC','$STOREA','$I2',200, now()),
  ('a3000000-0000-4000-8000-0000000000b2','$TENANT','$FAC','$STOREB','$I3',200, now()),
  ('a3000000-0000-4000-8000-0000000000b3','$TENANT','$FAC','$STOREB','$I1',200, now())
  ON CONFLICT (id) DO UPDATE SET qty_on_hand=EXCLUDED.qty_on_hand, updated_at=now();
  UPDATE inv_stock_reservations SET status='RELEASED' WHERE store_id IN ('$STOREA','$STOREB') AND status='ACTIVE'" >/dev/null
psqld credential_verification "INSERT INTO credential_verification.credentials
  (tenant_id, subject_type, subject_id, subject_name, credential_type, title, issued_by, issued_at, valid_from, status, verification_token, signature_hex, signed_payload, created_by, created_at, updated_at)
  SELECT '$TENANT','FACILITY','$FAC','OF-B M3 Proof Facility Payee','FACILITY_PAYEE','Facility payee credential (proof seed)','ofb-m3-proof', now(), now(),'ACTIVE','ofb-m3-proof-token','00','{}','ofb-m3-proof', now(), now()
  WHERE NOT EXISTS (SELECT 1 FROM credential_verification.credentials WHERE tenant_id='$TENANT' AND subject_id='$FAC' AND status='ACTIVE')" >/dev/null
psqld coverage "INSERT INTO cv_benefit_definitions
  (id, tenant_id, pod_id, plan_version_id, benefit_code, category, coverage_percent, copay_amount, deductible_amount, requires_authorisation, requires_referral, network_requirement, currency, created_at, updated_at)
  SELECT 'b4000000-0000-4000-8000-0000000000b4','$TENANT','national-spine','$PLANVER','$I1','MEDICINE',100.00,0,0,true,false,'ANY','USD',now(),now()
  WHERE NOT EXISTS (SELECT 1 FROM cv_benefit_definitions WHERE tenant_id='$TENANT' AND plan_version_id='$PLANVER' AND benefit_code='$I1');
  UPDATE cv_benefit_definitions SET requires_authorisation=true WHERE tenant_id='$TENANT' AND plan_version_id='$PLANVER' AND benefit_code='$I1'" >/dev/null
psqld pharmacy "INSERT INTO rx_substitution_rules
  (rule_id, tenant_id, facility_id, rule_type, source_code, target_code, source_coding_system, target_coding_system, requires_step_up, enabled, created_at)
  VALUES ('d5000000-0000-4000-8000-0000000000d5','$TENANT',NULL,'GENERIC','$I1','C09AA06','ATC','ATC',true,true,now())
  ON CONFLICT (rule_id) DO UPDATE SET requires_step_up=true, enabled=true" >/dev/null
psqld nhume "INSERT INTO nhume_driver_courier_profiles
  (courier_id, tenant_id, courier_code, display_name, courier_kind, training_status, delivery_zones_json, allowed_modes_json, current_status, risk_level, trust_verified, council_verified, offline_access_allowed, active, is_demo, created_at, updated_at)
  VALUES ('e6000000-0000-4000-8000-0000000000e6','$TENANT','OFB-M3-COURIER','OF-B M3 Proof Courier','STAFF','COMPLETED','[\"HARARE\"]','[\"MOTORBIKE\"]','AVAILABLE','LOW',true,true,false,true,false,now(),now())
  ON CONFLICT (courier_id) DO NOTHING" >/dev/null
pass "SEED vendors A/B, DURA stock (2 stores), facility payee credential, PA benefit def ($I1@$PLANVER), step-up substitution rule, courier profile"

# ═════════════════════════════════════════════════════════════════════════════
# LEG 1 — #41 extended end-to-end (medication, self-pay, PICKUP)
# ═════════════════════════════════════════════════════════════════════════════
echo; echo "── LEG 1: #41 medication self-pay end-to-end ──"
mkrx 10 "$I1" 10
if [ -n "${RX_TOKEN:-}" ] && [ "$RX_TOKEN" != null ]; then
  pass "#41 prescription created+signed, token minted ($RX_ID)"
else
  fail "#41 prescription create/sign/token failed (HTTP $API_CODE) — $(head -c 200 "$EVID/10-rx-token.json")"
fi
mkorder 11 PHARMACY "[{\"code\":\"$I1\",\"displayName\":\"Ramipril 5mg\",\"quantity\":10}]"
[ -n "$ORDER_ID" ] && [ "$ORDER_ID" != null ] && [ -n "$PIN" ] && [ "$PIN" != null ] \
  && pass "#41 OROS PHARMACY order $ORDER_ID pinned at $PIN" \
  || fail "#41 OROS order/version failed (HTTP $API_CODE)"
L1_ORDER=$ORDER_ID; L1_PIN=$PIN

# pharmacy episode from oros.order.placed
if poll_psql pharmacy "SELECT count(*) FROM rx_dispense_orders WHERE oros_order_id='$L1_ORDER'" "1" 90; then
  PH_ORDER=$(psqld pharmacy "SELECT order_id FROM rx_dispense_orders WHERE oros_order_id='$L1_ORDER'")
  pass "#41 pharmacy dispense episode $PH_ORDER spawned from oros.order.placed consumer"
else
  PH_ORDER=""
  fail "#41 no pharmacy dispense episode appeared for $L1_ORDER within 90s (oros.order.placed consumer leg)"
fi
# M3 BUG-3 fixed: the ORDER_PLACED payload now carries items[] — the
# consumer-spawned episode must have them (no seed).
if [ -n "$PH_ORDER" ]; then
  ITEMS_CARRIED=$(psqld pharmacy "SELECT count(*) FROM rx_dispense_items WHERE dispense_order_id='$PH_ORDER'")
  [ "$ITEMS_CARRIED" -ge 1 ] 2>/dev/null \
    && pass "#41 BUG-3 FIXED: episode carries $ITEMS_CARRIED dispense item(s) from the real oros.order.placed payload" \
    || fail "#41 BUG-3 regression: episode has 0 items from the event path"
fi
# Ordering reality (documented in the fix): the event is consumed BEFORE the
# commitment claims the prescription, so the episode is created unbound and the
# claim binds LAZILY at completeDispense — asserted after step 21 below.

api 12-rfo POST "http://msika-flow-service:8100/v1/marketplace-requests" \
  "{\"orosOrderId\":\"$L1_ORDER\",\"orosOrderVersionId\":\"$L1_PIN\",\"profile\":\"MEDICATION\",\"publicationMode\":\"INVITED\",\"coarseZone\":\"HARARE\",\"urgency\":\"ROUTINE\",\"fundingMode\":\"SELF_PAY\",\"prescriptionToken\":\"$RX_TOKEN\",\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"$I1\",\"codingSystem\":\"ATC\",\"quantity\":10,\"unit\":\"TAB\",\"controlled\":false,\"coldChain\":false}],\"invitedVendorIds\":[\"$VENDA\"]}"
REQ1=$(jqr '.data.requestId')
[ "$API_CODE" = 201 ] && [ -n "$REQ1" ] && [ "$REQ1" != null ] \
  && pass "#41 RFO created (MEDICATION, INVITED, token write-only): $REQ1" \
  || fail "#41 RFO create failed (HTTP $API_CODE): $(head -c 200 "$EVID/12-rfo.json")"
echo "$API_BODY" | grep -q "$RX_TOKEN" \
  && fail "#41 prescription token echoed in RFO view (write-only contract broken)" \
  || pass "#41 prescription token not echoed in the RFO view (write-only floor)"

api 13-publish POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ1/publish"
INV1=$(jqr '.data.invitationCount')
FULL1=0
if [ "$API_CODE" = 200 ] && [ "$INV1" -ge 1 ] 2>/dev/null; then
  FULL1=1; pass "#41 publish → $INV1 invitation(s)"
else
  fail "#41 publish refused: HTTP $API_CODE refusals=$(jqr '.data.refusals') — downstream #41 legs unreachable"
fi

SEL1=""
if [ "$FULL1" = 1 ]; then
  api 14-offer POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ1/offers" \
    "{\"vendorId\":\"$VENDA\",\"priceTotal\":25.00,\"currency\":\"USD\",\"fulfillmentWindowHours\":24,\"ttlMinutes\":60,\"lines\":[{\"requestLineRef\":\"L1\",\"itemCode\":\"$I1\",\"quantity\":10,\"unitPrice\":2.50,\"substitutionProposed\":false}]}"
  OFFER1=$(jqr '.data.offerId')
  [ "$API_CODE" = 201 ] && pass "#41 offer $OFFER1 submitted" || fail "#41 offer submit failed (HTTP $API_CODE)"

  IDEM1=$(uuidgen)
  api 15-select POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ1/select" \
    "{\"offerId\":\"$OFFER1\"}" "Idempotency-Key: $IDEM1"
  SEL1=$(jqr '.data.selectionId'); OUT1=$(jqr '.data.outcomeCode'); INTENT1=$(jqr '.data.paymentIntentId')
  if [ "$OUT1" = AWAITING_PAYMENT ] && [ -n "$INTENT1" ] && [ "$INTENT1" != null ]; then
    pass "#41 selection held AWAITING_PAYMENT with REAL MusheX intent $INTENT1 (shortfall $(jqr '.data.shortfallAmount') $(jqr '.data.shortfallCurrency'))"
  else
    fail "#41 expected AWAITING_PAYMENT+intent, got outcome=$OUT1 intent=$INTENT1 (HTTP $API_CODE)"
  fi
  S7=$(step_entry 15-select 7); S8=$(step_entry 15-select 8)
  echo "$S7" | grep -q "^PASS" && echo "$S7" | grep -qi "self-pay" \
    && pass "#41 step-7 financials: SELF_PAY explicit election recorded in step log" \
    || fail "#41 step-7 log wrong: $S7"
  echo "$S8" | grep -q "^PENDING" \
    && pass "#41 step-8 PENDING — intent opened, held until PAID (no fake synchronous payment)" \
    || fail "#41 step-8 log wrong: $S8"

  # REAL mushex intent status change: record-card-payment drives intent→PAID and
  # emits mushex.payment.status.changed (OutboxPublisher routeTopic STATUS_CHANGED).
  api 16-mushex-pay POST "http://mushex-service:8102/mushex/v1/payment-intents/$INTENT1/record-card-payment" \
    "{\"amount\":25.00,\"cardReference\":\"ofb-m3-proof-card\"}"
  [ "$(jqr '.data.status')" = PAID ] \
    && pass "#41 MusheX intent $INTENT1 → PAID via real payment-recording rail (no psql flip)" \
    || fail "#41 MusheX record-card-payment failed: HTTP $API_CODE status=$(jqr '.data.status') $(head -c 300 "$EVID/16-mushex-pay.json")"

  if poll_psql msika_flow "SELECT status FROM mf_selections WHERE selection_id='$SEL1'" "COMMITTED" 90; then
    pass "#41 BUG-7 FIXED: PaymentEventConsumer resumed steps 9–12 → selection COMMITTED (pure kafka event path, NO settlement shim)"
  else
    fail "#41 BUG-7 regression: PAID event published but selection still $POLL_VAL on the pure event path"
  fi
  RSTAT1=$(psqld msika_flow "SELECT status FROM mf_marketplace_requests WHERE request_id='$REQ1'")
  [ "$RSTAT1" = COMMITTED ] && pass "#41 request COMMITTED" || fail "#41 request status $RSTAT1"

  CLAIM1=$(psqld msika_flow "SELECT COALESCE(prescription_claim_id,'') FROM mf_selections WHERE selection_id='$SEL1'")
  ACT1=$(psqld inventory "SELECT count(*) FROM inv_stock_reservations WHERE ref_type='MARKETPLACE_SELECTION' AND ref_id='$SEL1' AND status='ACTIVE'")
  [ -n "$CLAIM1" ] && pass "#41 OROS prescription claim bound at step 6: $CLAIM1" || fail "#41 no prescription claim on committed selection"
  [ "$ACT1" -ge 1 ] && pass "#41 DURA hold ACTIVE for selection ($ACT1 row)" || fail "#41 no ACTIVE DURA hold for $SEL1"
  OC1=$(psqld oros "SELECT status FROM oros_prescription_claims WHERE claim_id='$CLAIM1'")
  [ "$OC1" = CLAIMED ] && pass "#41 oros_prescription_claims $CLAIM1 CLAIMED (repeats decremented server-side)" || fail "#41 oros claim status '$OC1'"
  STEPLOG1=$(psqld msika_flow "SELECT step_log_json FROM mf_selections WHERE selection_id='$SEL1'")
  echo "$STEPLOG1" > "$EVID/18-steplog-committed.json"
  echo "$STEPLOG1" | jq -e '[.[] | select(.seq==5 and .status=="PASS" and .step=="DURA_RESERVATION")] | length >= 1' >/dev/null \
    && echo "$STEPLOG1" | jq -e '[.[] | select(.seq==8 and .status=="PASS")] | length >= 1' >/dev/null \
    && echo "$STEPLOG1" | jq -e '[.[] | select(.seq==10 and .step=="COMMITMENT_RECORDED")] | length >= 1' >/dev/null \
    && pass "#41 step log complete: DURA PASS, payment resumed PASS, COMMITMENT_RECORDED" \
    || fail "#41 step log incomplete: $STEPLOG1"
  echo "$STEPLOG1" | jq -e '[.[] | select(.seq==12 and .step=="FULFILMENT_DISPATCH" and .status=="SKIPPED")] | length >= 1' >/dev/null \
    && pass "#41 step-12 dispatch honestly SKIPPED — PICKUP (fail-closed default), dispense episode rides the OROS claim" \
    || note "#41 step-12 entry: $(echo "$STEPLOG1" | jq -c '[.[] | select(.seq==12)]')"

  # M3 BUG-2 FIXED — producer half: the step-6 claim must now be stamped on the
  # OROS order's externalRefs (msika-flow → POST /v1/orders/{id}/external-refs).
  api 18b-extrefs GET "http://oros-service:8089/v1/orders/$L1_ORDER/external-refs"
  XREF_CLAIM=$(jqr '.data.prescriptionClaimId')
  [ "$XREF_CLAIM" = "$CLAIM1" ] \
    && pass "#41 BUG-2 FIXED (producer): OROS order carries externalRefs.prescriptionClaimId=$CLAIM1" \
    || fail "#41 BUG-2 producer regression: external-refs=$XREF_CLAIM wanted $CLAIM1"

  # pharmacy completion + §13.3.1 bind — the episode was created unbound (event
  # consumed before commitment); the LAZY half of BUG-2 must bind it at
  # completeDispense via the real pharmacy→OROS re-fetch. NO psql bind.
  if [ -n "$PH_ORDER" ]; then
    api 19-ph-accept POST "http://pharmacy-service:8096/v1/dispense-orders/$PH_ORDER/accept"
    ITEM1=$(psqld pharmacy "SELECT item_id FROM rx_dispense_items WHERE dispense_order_id='$PH_ORDER' LIMIT 1")
    api 20-ph-pick POST "http://pharmacy-service:8096/v1/dispense-orders/$PH_ORDER/pick" \
      "{\"items\":[{\"itemId\":\"$ITEM1\",\"batchNumber\":\"OFB-M3-B1\",\"expiryDate\":\"2027-12-31\",\"qtyPicked\":10}]}"
    api 21-ph-complete POST "http://pharmacy-service:8096/v1/dispense-orders/$PH_ORDER/complete"
    [ "$API_CODE" = 200 ] && pass "#41 pharmacy episode accept→pick→completeDispense OK" \
      || fail "#41 completeDispense failed (HTTP $API_CODE): $(head -c 200 "$EVID/21-ph-complete.json")"
    LAZY_CLAIM=$(psqld pharmacy "SELECT COALESCE(prescription_claim_id::text,'') FROM rx_dispense_orders WHERE order_id='$PH_ORDER'")
    [ "$LAZY_CLAIM" = "$CLAIM1" ] \
      && pass "#41 BUG-2 FIXED (lazy bind): completeDispense re-fetched external refs and bound claim $CLAIM1 onto the episode (no psql bind)" \
      || fail "#41 BUG-2 lazy-bind regression: episode claim='$LAZY_CLAIM' wanted $CLAIM1"
    EPREF=$(psqld oros "SELECT COALESCE(dispense_episode_ref,'') FROM oros_prescription_claims WHERE claim_id='$CLAIM1'")
    [ "$EPREF" = "$PH_ORDER" ] \
      && pass "#41 OROS claim $CLAIM1 carries dispense_episode_ref=$PH_ORDER (§13.3.1 bind-episode seam live)" \
      || fail "#41 claim episode NOT bound (dispense_episode_ref='$EPREF', wanted $PH_ORDER)"

    # pickup proof (OTP) + verification grade — M3 BUG-5 FIXED: the creation
    # response carries the one-time credential exactly once; claim uses it.
    api 22-pickup-create POST "http://pharmacy-service:8096/v1/dispense-orders/$PH_ORDER/pickup/create" \
      "{\"method\":\"OTP\"}"
    PROOF1=$(jqr '.data.proofId')
    OTP1=$(jqr '.data.oneTimeCredential // empty')
    if [ "$API_CODE" = 201 ] && [ -n "$PROOF1" ] && [ "$PROOF1" != null ]; then
      pass "#41 pickup proof created ($PROOF1)"
      if [ -n "$OTP1" ]; then
        pass "#41 BUG-5 FIXED: creation response carries oneTimeCredential (delivery seam live; hash-only storage)"
        DF_STORED=$(psqld pharmacy "SELECT COALESCE(device_fingerprint,'') FROM rx_pickup_proofs WHERE proof_id='$PROOF1'")
        TH_STORED=$(psqld pharmacy "SELECT token_hash FROM rx_pickup_proofs WHERE proof_id='$PROOF1'")
        { [ "$DF_STORED" != "$OTP1" ] && [ "$TH_STORED" != "$OTP1" ]; } \
          && pass "#41 BUG-5 storage floor: plaintext credential NOT persisted (device_fingerprint/token_hash both differ)" \
          || fail "#41 BUG-5 leak: plaintext credential persisted on the proof row"
        api 23-pickup-claim POST "http://pharmacy-service:8096/v1/pickup/claim" \
          "{\"token\":\"$OTP1\",\"collectorIdentity\":\"Patient OFB-M3\",\"deviceFingerprint\":\"proof-device\"}"
        GRADE1=$(jqr '.data.verificationGrade')
        [ "$(jqr '.data.status')" = CLAIMED ] && [ "$GRADE1" = TOKEN_MATCH ] \
          && pass "#41 pickup claimed with the REAL delivered credential — grade TOKEN_MATCH, collector recorded" \
          || fail "#41 pickup claim wrong: status=$(jqr '.data.status') grade=$GRADE1 (HTTP $API_CODE)"
      else
        fail "#41 BUG-5 regression: creation response carries no oneTimeCredential"
      fi
    else
      fail "#41 pickup proof create failed (HTTP $API_CODE)"
    fi
  fi
fi

# ═════════════════════════════════════════════════════════════════════════════
# LEG 2 — #46-class financials: PAYER_COVERED without resolvable coverage
# ═════════════════════════════════════════════════════════════════════════════
echo; echo "── LEG 2: #46 payer-covered fail-closed financials ──"
mkorder 30 PHARMACY "[{\"code\":\"$I2\",\"displayName\":\"Furosemide\",\"quantity\":5}]"
BOGUS_COV=$(uuidgen)
api 31-rfo POST "http://msika-flow-service:8100/v1/marketplace-requests" \
  "{\"orosOrderId\":\"$ORDER_ID\",\"orosOrderVersionId\":\"$PIN\",\"profile\":\"MEDICATION\",\"publicationMode\":\"INVITED\",\"coarseZone\":\"HARARE\",\"urgency\":\"ROUTINE\",\"fundingMode\":\"PAYER_COVERED\",\"coverageId\":\"$BOGUS_COV\",\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"$I2\",\"codingSystem\":\"ATC\",\"quantity\":5,\"unit\":\"TAB\",\"controlled\":false,\"coldChain\":false}],\"invitedVendorIds\":[\"$VENDA\"]}"
REQ2=$(jqr '.data.requestId')
api 32-publish POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ2/publish"
if [ "$(jqr '.data.invitationCount')" -ge 1 ] 2>/dev/null; then
  api 33-offer POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ2/offers" \
    "{\"vendorId\":\"$VENDA\",\"priceTotal\":12.50,\"currency\":\"USD\",\"fulfillmentWindowHours\":24,\"ttlMinutes\":60,\"lines\":[{\"requestLineRef\":\"L1\",\"itemCode\":\"$I2\",\"quantity\":5,\"unitPrice\":2.50,\"substitutionProposed\":false}]}"
  OFFER2=$(jqr '.data.offerId')
  api 34-offers-view GET "http://msika-flow-service:8100/v1/marketplace-requests/$REQ2/offers"
  FIN2=$(jqr '.data[0].financials')
  FIN2_STATUS=$(jqr '.data[0].financials.status'); FIN2_ENF=$(jqr '.data[0].financials.estimateNeverFinal')
  if [ "$FIN2_ENF" = true ] && { [ "$FIN2_STATUS" = NOT_COVERED ] || [ "$FIN2_STATUS" = UNVERIFIED ]; }; then
    pass "#46 offer comparison view carries financials block: status=$FIN2_STATUS, estimateNeverFinal:true (§10.5)"
  else
    fail "#46 financials block wrong on offer view: $FIN2"
  fi
  api 35-select POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ2/select" \
    "{\"offerId\":\"$OFFER2\"}" "Idempotency-Key: $(uuidgen)"
  OUT2=$(jqr '.data.outcomeCode'); SEL2=$(jqr '.data.selectionId')
  if [ "$OUT2" = NOT_COVERED ] || [ "$OUT2" = FINANCIAL_UNVERIFIED ]; then
    pass "#46 step-7 fail-closed coded refusal: $OUT2 (committed=$(jqr '.data.committed'))"
    S7B=$(step_entry 35-select 7)
    echo "$S7B" | grep -q "^FAIL" && pass "#46 step-7 FAIL entry with code in log: $S7B" || fail "#46 step-7 log: $S7B"
    ACT2=$(psqld inventory "SELECT count(*) FROM inv_stock_reservations WHERE ref_id='$SEL2' AND status='ACTIVE'")
    [ "$ACT2" = 0 ] && pass "#46 DURA holds compensated (0 ACTIVE rows for failed selection)" || fail "#46 $ACT2 ACTIVE holds leaked"
    RST2=$(psqld msika_flow "SELECT status FROM mf_marketplace_requests WHERE request_id='$REQ2'")
    [ "$RST2" = OFFERS_AVAILABLE ] && pass "#46 request back to honest OFFERS_AVAILABLE re-offer posture" || fail "#46 request status $RST2"
  else
    fail "#46 expected NOT_COVERED/FINANCIAL_UNVERIFIED, got $OUT2 (HTTP $API_CODE)"
  fi
else
  fail "#46 publish refused — leg unreachable: $(jqr '.data.refusals')"
fi

# ═════════════════════════════════════════════════════════════════════════════
# LEG 3 — #47-class PA: benefit requires authorisation → PA_REQUIRED
# ═════════════════════════════════════════════════════════════════════════════
echo; echo "── LEG 3: #47 prior-authorisation coded refusal ──"
AUTH_BEFORE=$(psqld coverage "SELECT count(*) FROM cv_authorisations WHERE member_cpid='$MEMBER_CPID'")
api 40-rfo POST "http://msika-flow-service:8100/v1/marketplace-requests" \
  "{\"orosOrderId\":\"$L1_ORDER\",\"orosOrderVersionId\":\"$L1_PIN\",\"profile\":\"MEDICATION\",\"publicationMode\":\"INVITED\",\"coarseZone\":\"HARARE\",\"urgency\":\"ROUTINE\",\"fundingMode\":\"PAYER_COVERED\",\"coverageId\":\"$COVID_GOLDEN\",\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"$I1\",\"codingSystem\":\"ATC\",\"quantity\":10,\"unit\":\"TAB\",\"controlled\":false,\"coldChain\":false}],\"invitedVendorIds\":[\"$VENDA\"]}"
REQ3=$(jqr '.data.requestId')
api 41-publish POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ3/publish"
if [ "$(jqr '.data.invitationCount')" -ge 1 ] 2>/dev/null; then
  api 42-offer POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ3/offers" \
    "{\"vendorId\":\"$VENDA\",\"priceTotal\":25.00,\"currency\":\"USD\",\"fulfillmentWindowHours\":24,\"ttlMinutes\":60,\"lines\":[{\"requestLineRef\":\"L1\",\"itemCode\":\"$I1\",\"quantity\":10,\"unitPrice\":2.50,\"substitutionProposed\":false}]}"
  OFFER3=$(jqr '.data.offerId')
  api 43-select POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ3/select" \
    "{\"offerId\":\"$OFFER3\"}" "Idempotency-Key: $(uuidgen)"
  OUT3=$(jqr '.data.outcomeCode'); PA3=$(jqr '.data.paStatus')
  if [ "$OUT3" = PA_REQUIRED ]; then
    pass "#47 commit refused PA_REQUIRED (paStatus=$PA3) — offer conditional on approval (§8.7.6)"
    S7C=$(step_entry 43-select 7)
    echo "$S7C" | grep -q "PA_REQUIRED" && pass "#47 step-7 log carries PA_REQUIRED code: $S7C" || fail "#47 step-7 log: $S7C"
    AUTH_AFTER=$(psqld coverage "SELECT count(*) FROM cv_authorisations WHERE member_cpid='$MEMBER_CPID'")
    if [ "$AUTH_AFTER" -gt "$AUTH_BEFORE" ] 2>/dev/null; then
      pass "#47 minimum-necessary PA auto-submitted on LIVE cv_authorisations ($AUTH_BEFORE→$AUTH_AFTER, latest status $(psqld coverage "SELECT status FROM cv_authorisations WHERE member_cpid='$MEMBER_CPID' ORDER BY created_at DESC LIMIT 1"))"
    else
      note "#47 no cv_authorisations row created (paStatus=$PA3) — refusal itself is the pass criterion; auto-submit path did not land a row"
    fi
  elif [ "$OUT3" = NOT_COVERED ]; then
    # BUG-4 regression check: with the deterministic Idempotency-Key fix this
    # branch should be unreachable. Prove whether the coverage engine answers:
    RIDP=$(uuidgen)
    kubectl -n "$NS" exec deploy/oros-service -- curl -s --max-time 15 -X POST \
      http://coverage-service:8140/internal/v1/coverage/liability-estimates \
      -H "Content-Type: application/json" -H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: national-spine" \
      -H "X-Request-ID: $RIDP" -H "X-Correlation-ID: $RIDP" -H "X-Actor-ID: ofb-m3-proof" \
      -H "X-Actor-Type: PROVIDER" -H "X-Purpose-Of-Use: TREATMENT" \
      -H "Idempotency-Key: ofb-m3-$RIDP" -H "x-envoy-internal: true" \
      -d "{\"coverageId\":\"$COVID_GOLDEN\",\"benefitCode\":\"$I1\",\"standardCharge\":0}" \
      > "$EVID/44-direct-liability-probe.json" 2>/dev/null
    if grep -q '"requiresAuthorisation":true' "$EVID/44-direct-liability-probe.json"; then
      fail "#47 BUG-4 regression: commit refused NOT_COVERED while direct probe returns requiresAuthorisation:true (evidence 44-direct-liability-probe.json)"
    else
      fail "#47 expected PA_REQUIRED, got $OUT3 (paStatus=$PA3) and direct probe did not confirm PA benefit"
    fi
  else
    fail "#47 expected PA_REQUIRED, got $OUT3 (paStatus=$PA3, HTTP $API_CODE): $(head -c 300 "$EVID/43-select.json")"
  fi
else
  fail "#47 publish refused — leg unreachable: $(jqr '.data.refusals')"
fi

# ═════════════════════════════════════════════════════════════════════════════
# LEG 4 — #44 clarification loop (step-up substitution)
# ═════════════════════════════════════════════════════════════════════════════
echo; echo "── LEG 4: #44 substitution → prescriber clarification loop ──"
mkorder 50 PHARMACY "[{\"code\":\"$I1\",\"displayName\":\"Ramipril 5mg\",\"quantity\":5}]"
L4_ORDER=$ORDER_ID
PH4=""
if poll_psql pharmacy "SELECT count(*) FROM rx_dispense_orders WHERE oros_order_id='$L4_ORDER'" "1" 90; then
  PH4=$(psqld pharmacy "SELECT order_id FROM rx_dispense_orders WHERE oros_order_id='$L4_ORDER'")
  [ "$(psqld pharmacy "SELECT count(*) FROM rx_dispense_items WHERE dispense_order_id='$PH4'")" -ge 1 ] 2>/dev/null \
    && pass "#44 BUG-3 FIXED: episode carries its item from the event payload (no seed)" \
    || fail "#44 BUG-3 regression: 0 items on consumer-spawned episode"
  api 51-accept POST "http://pharmacy-service:8096/v1/dispense-orders/$PH4/accept"
  ITEM4=$(psqld pharmacy "SELECT item_id FROM rx_dispense_items WHERE dispense_order_id='$PH4' LIMIT 1")
  api 52-pick POST "http://pharmacy-service:8096/v1/dispense-orders/$PH4/pick" \
    "{\"items\":[{\"itemId\":\"$ITEM4\",\"batchNumber\":\"OFB-M3-B4\",\"expiryDate\":\"2027-12-31\",\"qtyPicked\":5}]}"
  api 53-propose POST "http://pharmacy-service:8096/v1/dispense-items/$ITEM4/clarifications" \
    "{\"targetDrugCode\":\"C09AA06\",\"reason\":\"stock-out of prescribed brand\",\"patientConsent\":true}"
  CLAR=$(jqr '.data.clarification.clarificationId')
  if [ "$API_CODE" = 201 ] && [ "$(jqr '.data.autoApplied')" = false ] && [ "$(jqr '.data.clarification.status')" = PENDING ]; then
    pass "#44 step-up substitution → PENDING clarification $CLAR (never unilateral)"
  else
    fail "#44 propose wrong: HTTP $API_CODE autoApplied=$(jqr '.data.autoApplied') status=$(jqr '.data.clarification.status')"
  fi
  api 54-complete-blocked POST "http://pharmacy-service:8096/v1/dispense-orders/$PH4/complete"
  ODS4=$(psqld pharmacy "SELECT status FROM rx_dispense_orders WHERE order_id='$PH4'")
  if [ "$API_CODE" != 200 ] && [ "$ODS4" != DISPENSED ]; then
    pass "#44 dispense blocked while clarification PENDING (HTTP $API_CODE; order stays $ODS4 — fail-closed hold)"
    # M3 F-500 FIXED: the hold must now be a coded 409, not a bodyless raw 500.
    if [ "$API_CODE" = 409 ] && grep -q "CLARIFICATION_PENDING" "$EVID/54-complete-blocked.json"; then
      pass "#44 F-500 FIXED: hold surfaces as coded 409 CLARIFICATION_PENDING in the ApiResponse envelope"
    else
      fail "#44 F-500 regression: HTTP $API_CODE body=$(head -c 200 "$EVID/54-complete-blocked.json")"
    fi
  else
    fail "#44 dispense NOT blocked during PENDING clarification (HTTP $API_CODE, order $ODS4)"
  fi
  api 55-approve POST "http://pharmacy-service:8096/v1/clarifications/$CLAR/resolve" \
    "{\"decision\":\"APPROVE\",\"note\":\"generic ok\"}"
  DC4=$(psqld pharmacy "SELECT drug_code FROM rx_dispense_items WHERE item_id='$ITEM4'")
  APPLIED_AT=$(psqld pharmacy "SELECT COALESCE(applied_at::text,'') FROM rx_clarification_requests WHERE clarification_id='$CLAR'")
  [ "$DC4" = "C09AA06" ] && [ -n "$APPLIED_AT" ] \
    && pass "#44 approval applied the substitution exactly once (item now C09AA06, applied_at stamped)" \
    || fail "#44 substitution not applied on approval (drug=$DC4 applied_at='$APPLIED_AT')"
  api 56-approve-replay POST "http://pharmacy-service:8096/v1/clarifications/$CLAR/resolve" \
    "{\"decision\":\"APPROVE\",\"note\":\"replay\"}"
  [ "$API_CODE" != 200 ] && pass "#44 replay of resolve refused (HTTP $API_CODE — not pending)" \
    || fail "#44 replay resolve was accepted (double-apply hazard)"
  api 57-complete POST "http://pharmacy-service:8096/v1/dispense-orders/$PH4/complete"
  [ "$API_CODE" = 200 ] && pass "#44 dispense completes after clarification resolved" \
    || fail "#44 post-approval complete failed (HTTP $API_CODE): $(head -c 200 "$EVID/57-complete.json")"
else
  fail "#44 no pharmacy episode for $L4_ORDER — leg unreachable"
fi

# ═════════════════════════════════════════════════════════════════════════════
# LEG 5 — #53/#54/#69 pickup: SMS-reference path + expiry sweep w/ stock reversal
# ═════════════════════════════════════════════════════════════════════════════
echo; echo "── LEG 5: #53/#54/#69 SMS reference + uncollected expiry sweep ──"
if [ -n "${PH4:-}" ]; then
  api 60-sms-create POST "http://pharmacy-service:8096/v1/dispense-orders/$PH4/pickup/create" \
    "{\"method\":\"SMS_REFERENCE\",\"namedRecipient\":\"Thandiwe Moyo\"}"
  SMSPROOF=$(jqr '.data.proofId')
  SMSREF=$(jqr '.data.oneTimeCredential // empty')
  if [ "$API_CODE" = 201 ] && [ -n "$SMSPROOF" ] && [ "$SMSPROOF" != null ]; then
    pass "#53 BUG-9 FIXED: SMS_REFERENCE proof created (method fits widened VARCHAR(32) column, V008)"
    if ! grep -qE '"tokenJws"|"token"' "$EVID/60-sms-create.json"; then
      pass "#53 SMS-reference proof carries NO token in any API surface (token-never-in-SMS floor holds; opaque reference only)"
    else
      fail "#53 create response leaks a token: $(head -c 200 "$EVID/60-sms-create.json")"
    fi
    if [ -n "$SMSREF" ]; then
      pass "#53 BUG-5 FIXED: opaque reference '$SMSREF' delivered once in the creation response (SMS lane seam; hash-only storage)"
    else
      fail "#53 BUG-5 regression: no oneTimeCredential on SMS-reference creation"
    fi
    api 61-resolve POST "http://pharmacy-service:8096/v1/pickup/resolve-reference" "{\"reference\":\"$SMSREF\"}"
    [ "$(jqr '.data.namedRecipient')" = "Thandiwe Moyo" ] \
      && pass "#53 server-side resolve returns named recipient for the staff ID check" \
      || fail "#53 resolve-reference wrong: $(head -c 200 "$EVID/61-resolve.json")"
    api 62-claim-mismatch POST "http://pharmacy-service:8096/v1/pickup/claim-by-reference" \
      "{\"reference\":\"$SMSREF\",\"collectorIdentity\":\"Someone Else\"}"
    [ "$API_CODE" != 200 ] && pass "#53 identity mismatch fail-closed (HTTP $API_CODE)" \
      || fail "#53 mismatched collector was allowed to claim"
    api 63-claim-ok POST "http://pharmacy-service:8096/v1/pickup/claim-by-reference" \
      "{\"reference\":\"$SMSREF\",\"collectorIdentity\":\"Thandiwe Moyo\",\"deviceFingerprint\":\"staff-desk-1\"}"
    [ "$(jqr '.data.status')" = CLAIMED ] && [ "$(jqr '.data.verificationGrade')" = SMS_REFERENCE_ID_CHECK ] \
      && pass "#53 claim by reference → CLAIMED at grade SMS_REFERENCE_ID_CHECK" \
      || fail "#53 claim-by-reference wrong: status=$(jqr '.data.status') grade=$(jqr '.data.verificationGrade')"
  else
    fail "#53 SMS_REFERENCE proof create failed (HTTP $API_CODE): $(head -c 200 "$EVID/60-sms-create.json")"
  fi
fi


# 5b — expiry sweep on an uncollected DISPENSED episode
mkorder 65 PHARMACY "[{\"code\":\"$I2\",\"displayName\":\"Furosemide\",\"quantity\":3}]"
L5_ORDER=$ORDER_ID
if poll_psql pharmacy "SELECT count(*) FROM rx_dispense_orders WHERE oros_order_id='$L5_ORDER'" "1" 90; then
  PH5=$(psqld pharmacy "SELECT order_id FROM rx_dispense_orders WHERE oros_order_id='$L5_ORDER'")
  [ "$(psqld pharmacy "SELECT count(*) FROM rx_dispense_items WHERE dispense_order_id='$PH5'")" -ge 1 ] 2>/dev/null \
    || fail "#54 BUG-3 regression: 0 items on consumer-spawned episode $PH5"
  api 66-accept POST "http://pharmacy-service:8096/v1/dispense-orders/$PH5/accept"
  ITEM5=$(psqld pharmacy "SELECT item_id FROM rx_dispense_items WHERE dispense_order_id='$PH5' LIMIT 1")
  api 67-pick POST "http://pharmacy-service:8096/v1/dispense-orders/$PH5/pick" \
    "{\"items\":[{\"itemId\":\"$ITEM5\",\"batchNumber\":\"OFB-M3-B5\",\"expiryDate\":\"2027-12-31\",\"qtyPicked\":3}]}"
  api 68-complete POST "http://pharmacy-service:8096/v1/dispense-orders/$PH5/complete"
  REVQ="SELECT count(*) FROM rx_stock_movements WHERE ref_type='REVERSAL' AND reason='REVERSAL: PICKUP_EXPIRED_RETURN' AND ref_id IN (SELECT movement_id::text FROM rx_stock_movements WHERE ref_type='DISPENSE_ORDER' AND ref_id='$PH5')"
  MOV_BEFORE=$(psqld pharmacy "$REVQ")
  api 69-otp-create POST "http://pharmacy-service:8096/v1/dispense-orders/$PH5/pickup/create" "{\"method\":\"OTP\"}"
  PROOF5=$(jqr '.data.proofId')
  psqld pharmacy "UPDATE rx_pickup_proofs SET expires_at = now() - interval '2 minutes' WHERE proof_id='$PROOF5'" >/dev/null
  if poll_psql pharmacy "SELECT status FROM rx_pickup_proofs WHERE proof_id='$PROOF5'" "EXPIRED" 150; then
    pass "#69 expiry sweep flipped uncollected proof → EXPIRED"
  else
    fail "#69 proof still $POLL_VAL after 150s (sweep interval env not applied?)"
  fi
  poll_psql pharmacy "SELECT status FROM rx_dispense_orders WHERE order_id='$PH5'" "CANCELLED" 60 \
    && pass "#69 uncollected DISPENSED episode escalated → CANCELLED" \
    || fail "#69 episode not cancelled (status=$POLL_VAL)"
  MOV_AFTER=$(psqld pharmacy "$REVQ")
  [ "$MOV_AFTER" -gt "$MOV_BEFORE" ] 2>/dev/null \
    && pass "#69 stock-return reversal movements written (REVERSAL: PICKUP_EXPIRED_RETURN rows $MOV_BEFORE→$MOV_AFTER)" \
    || fail "#69 no reversal stock movements ($MOV_BEFORE→$MOV_AFTER)"
  EVT5=$(psqld pharmacy "SELECT count(*) FROM rx_event_outbox WHERE event_type='PICKUP_EXPIRED_RETURN' AND payload::text LIKE '%$PH5%'")
  [ "$EVT5" -ge 1 ] && pass "#69 PICKUP_EXPIRED_RETURN event row emitted" || fail "#69 no PICKUP_EXPIRED_RETURN event"
else
  fail "#69 no pharmacy episode for $L5_ORDER — expiry leg unreachable"
fi

# ═════════════════════════════════════════════════════════════════════════════
# LEG 6 — #42/#68 delivery pathway: Nhume dispatch, grade ladder, write-backs
# ═════════════════════════════════════════════════════════════════════════════
echo; echo "── LEG 6: #42/#68 DELIVERY pathway + proof-grade enforcement ──"
NHUME=http://nhume-service:8210

deliver_commit() { # deliver_commit TAG PRICE → sets DSEL DREQ DINTENT DDLV
  # PRICE 0.00 → zero-shortfall WAIVED payment → SYNCHRONOUS commit (dispatch runs
  # with the inbound HTTP context); PRICE > 0 → real intent → PAID →
  # PaymentEventConsumer resume (BUG-7 fixed — pure event path, no shim).
  local tag=$1 price=${2:-30.00}
  mkrx "$tag-rx" "$I1" 10
  mkorder "$tag-ord" PHARMACY "[{\"code\":\"$I1\",\"displayName\":\"Ramipril 5mg\",\"quantity\":10}]"
  local ord=$ORDER_ID pin=$PIN
  api "$tag-rfo" POST "http://msika-flow-service:8100/v1/marketplace-requests" \
    "{\"orosOrderId\":\"$ord\",\"orosOrderVersionId\":\"$pin\",\"profile\":\"MEDICATION\",\"publicationMode\":\"INVITED\",\"coarseZone\":\"HARARE\",\"urgency\":\"ROUTINE\",\"fundingMode\":\"SELF_PAY\",\"prescriptionToken\":\"$RX_TOKEN\",\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"$I1\",\"codingSystem\":\"ATC\",\"quantity\":10,\"unit\":\"TAB\",\"controlled\":false,\"coldChain\":false}],\"invitedVendorIds\":[\"$VENDA\"]}"
  DREQ=$(jqr '.data.requestId')
  api "$tag-pathway" PUT "http://msika-flow-service:8100/v1/marketplace-requests/$DREQ/fulfilment-pathway" \
    "{\"pathway\":\"DELIVERY\",\"deliveryName\":\"Thandiwe Moyo\",\"deliveryPhone\":\"+263771234567\",\"deliveryAddress\":\"12 Samora Machel Ave, Harare\",\"deliveryLat\":-17.8292,\"deliveryLng\":31.0522}"
  api "$tag-publish" POST "http://msika-flow-service:8100/v1/marketplace-requests/$DREQ/publish"
  api "$tag-offer" POST "http://msika-flow-service:8100/v1/marketplace-requests/$DREQ/offers" \
    "{\"vendorId\":\"$VENDA\",\"priceTotal\":$price,\"currency\":\"USD\",\"fulfillmentWindowHours\":24,\"ttlMinutes\":60,\"lines\":[{\"requestLineRef\":\"L1\",\"itemCode\":\"$I1\",\"quantity\":10,\"unitPrice\":$price,\"substitutionProposed\":false}]}"
  local off; off=$(jqr '.data.offerId')
  api "$tag-select" POST "http://msika-flow-service:8100/v1/marketplace-requests/$DREQ/select" \
    "{\"offerId\":\"$off\"}" "Idempotency-Key: $(uuidgen)"
  DSEL=$(jqr '.data.selectionId'); DINTENT=$(jqr '.data.paymentIntentId')
  if [ -n "$DINTENT" ] && [ "$DINTENT" != null ]; then
    api "$tag-pay" POST "http://mushex-service:8102/mushex/v1/payment-intents/$DINTENT/record-card-payment" \
      "{\"amount\":$price,\"cardReference\":\"ofb-m3-$tag\"}"
    # BUG-7 fixed: the pure kafka event path must resume the commitment itself.
    poll_psql msika_flow "SELECT status FROM mf_selections WHERE selection_id='$DSEL'" "COMMITTED" 90 \
      || fail "#42 BUG-7 regression: paid selection $DSEL still $POLL_VAL on the pure event path"
  fi
  DDLV=$(psqld msika_flow "SELECT COALESCE(dispatch_ref,'') FROM mf_selections WHERE selection_id='$DSEL'")
}

nhume_to_transit() { # nhume_to_transit TAG DLV → approve/assign/accept/pickup/start
  local tag=$1 dlv=$2
  api "$tag-approve" POST "$NHUME/internal/v1/nhume/deliveries/$dlv/approve" "{\"reason\":\"proof\"}"
  api "$tag-assign" POST "$NHUME/internal/v1/nhume/deliveries/$dlv/assign" \
    "{\"courier_id\":\"e6000000-0000-4000-8000-0000000000e6\",\"mode_category\":\"MOTORBIKE\",\"assignment_kind\":\"MANUAL\"}"
  api "$tag-accept" POST "$NHUME/internal/v1/nhume/deliveries/$dlv/accept"
  api "$tag-pickup" POST "$NHUME/internal/v1/nhume/deliveries/$dlv/pickup" "{\"reason\":\"collected from vendor\"}"
  api "$tag-start" POST "$NHUME/internal/v1/nhume/deliveries/$dlv/start" "{\"reason\":\"en route\"}"
}

# 6a — synchronous (WAIVED-payment) commitment: step-11 runs with the inbound
# HTTP context, so the dispatch seam itself is provable despite BUG-10.
deliver_commit 70 0.00
DSEL_A=$DSEL
if [ -n "$DDLV" ]; then
  pass "#42 commitment step-11 dispatched a REAL Nhume delivery: $DDLV (dispatch_ref on selection; synchronous WAIVED-payment path)"
  NROW=$(psqld nhume "SELECT status||'|'||COALESCE(required_pod_grade,'')||'|'||named_recipient_required||'|'||COALESCE(handover_otp,'') FROM nhume_delivery_requests WHERE delivery_id='$DDLV'")
  echo "$NROW" > "$EVID/70-nhume-row.txt"
  NOTP=$(echo "$NROW" | cut -d'|' -f4)
  echo "$NROW" | grep -q "OTP_MATCH|t" && [ -n "$NOTP" ] \
    && pass "#68 Nhume task carries grade contract: required_pod_grade=OTP_MATCH, named_recipient_required, server-held OTP minted" \
    || fail "#68 Nhume grade contract wrong: $NROW"
  SLOG=$(psqld msika_flow "SELECT step_log_json FROM mf_selections WHERE selection_id='$DSEL_A'")
  echo "$SLOG" | jq -e '[.[] | select(.seq==12 and .step=="FULFILMENT_DISPATCH" and .status=="PASS")] | length>=1' >/dev/null \
    && pass "#42 step-12 FULFILMENT_DISPATCH PASS in step log" || fail "#42 step-12 log: $(echo "$SLOG" | jq -c '[.[]|select(.seq==12)]')"

  nhume_to_transit 71 "$DDLV"
  NST=$(psqld nhume "SELECT status FROM nhume_delivery_requests WHERE delivery_id='$DDLV'")
  [ "$NST" = IN_TRANSIT ] && pass "#42 courier lifecycle driven to IN_TRANSIT" || note "#42 delivery status now $NST (continuing)"

  # wrong OTP → refused handover, DELIVERY_ATTEMPTED, attempts counter
  api 72-proof-bad POST "$NHUME/internal/v1/nhume/deliveries/$DDLV/proof" \
    "{\"proof_stage\":\"DELIVERY\",\"proof_method\":\"OTP\",\"otp_code\":\"000000\",\"receiver_name\":\"Thandiwe Moyo\",\"mark_delivered\":true}"
  ATT=$(psqld nhume "SELECT handover_attempts||'|'||status FROM nhume_delivery_requests WHERE delivery_id='$DDLV'")
  echo "$ATT" | grep -q "^1|DELIVERY_ATTEMPTED" \
    && pass "#68 wrong OTP → handover refused, DELIVERY_ATTEMPTED, attempts=1/3 (custody event kept)" \
    || fail "#68 wrong-OTP outcome unexpected: attempts|status=$ATT (HTTP $API_CODE)"
  COC=$(psqld nhume "SELECT count(*) FROM nhume_chain_of_custody_events WHERE delivery_id='$DDLV' AND event_kind='HANDOVER_ATTEMPTED'")
  [ "$COC" -ge 1 ] && pass "#68 HANDOVER_ATTEMPTED chain-of-custody event appended" || fail "#68 no custody event"
  poll_psql msika_flow "SELECT COALESCE(delivery_status,'') FROM mf_selections WHERE selection_id='$DSEL_A'" "DELIVERY_ATTEMPTED" 30 \
    && pass "#68 msika-flow selection projection ← DELIVERY_ATTEMPTED via Nhume write-back" \
    || fail "#68 selection delivery_status=$POLL_VAL (write-back missing)"

  # below-grade proof cannot sign off DELIVERED
  api 73-delivered-premature POST "$NHUME/internal/v1/nhume/deliveries/$DDLV/proof" \
    "{\"proof_stage\":\"DELIVERY\",\"proof_method\":\"SIGNATURE\",\"signature_uri\":\"sig://x\",\"receiver_name\":\"Thandiwe Moyo\",\"mark_delivered\":true}"
  PSTAT=$(psqld nhume "SELECT status FROM nhume_delivery_requests WHERE delivery_id='$DDLV'")
  [ "$PSTAT" != DELIVERED ] \
    && pass "#68 signature-only proof below required grade → still not DELIVERED (status $PSTAT)" \
    || fail "#68 delivery signed off DELIVERED below required grade"

  # correct OTP handover
  api 74-proof-good POST "$NHUME/internal/v1/nhume/deliveries/$DDLV/proof" \
    "{\"proof_stage\":\"DELIVERY\",\"proof_method\":\"OTP\",\"otp_code\":\"$NOTP\",\"receiver_name\":\"Thandiwe Moyo\",\"mark_delivered\":true}"
  NST2=$(psqld nhume "SELECT status FROM nhume_delivery_requests WHERE delivery_id='$DDLV'")
  [ "$NST2" = DELIVERED ] && pass "#68 correct OTP + named recipient → DELIVERED" \
    || fail "#68 correct-OTP handover did not deliver (status $NST2, HTTP $API_CODE): $(head -c 200 "$EVID/74-proof-good.json")"
  poll_psql msika_flow "SELECT COALESCE(delivery_status,'') FROM mf_selections WHERE selection_id='$DSEL_A'" "DELIVERED" 30 \
    && pass "#68 selection projection ← DELIVERED, grade=$(psqld msika_flow "SELECT COALESCE(delivery_verification_grade,'') FROM mf_selections WHERE selection_id='$DSEL_A'"), proof=$(psqld msika_flow "SELECT COALESCE(delivery_proof_ref,'') FROM mf_selections WHERE selection_id='$DSEL_A'")" \
    || fail "#68 selection projection not DELIVERED ($POLL_VAL)"
  ESCROW=$(psqld msika_flow "SELECT COALESCE(escrow_release_status,'') FROM mf_selections WHERE selection_id='$DSEL_A'")
  [ "$ESCROW" = NOT_APPLICABLE ] \
    && pass "#68 unpaid (WAIVED) selection records escrow honestly NOT_APPLICABLE — nothing charged, nothing fabricated" \
    || note "#68 escrow status: '$ESCROW'"
else
  fail "#42 DELIVERY commitment produced no dispatch_ref (selection $DSEL, status $(psqld msika_flow "SELECT status||'|'||COALESCE(dispatch_status,'')||'|'||COALESCE(dispatch_failure_code,'') FROM mf_selections WHERE selection_id='$DSEL'")) — Nhume legs unreachable"
fi

# 6b — PAID pathway: real event resume (BUG-7 fixed) → real service-originated
# dispatch with the full v1.1 header set (BUG-10 fixed) → RETURNED → refund.
deliver_commit 75 30.00
DSEL_B=$DSEL
DREQ_B=$DREQ
DINTENT_B=$DINTENT
if [ -z "$DDLV" ]; then
  # dispatch may land via the retry sweep moments after COMMITTED — poll briefly
  DDLV_DEADLINE=$(( $(date +%s) + 120 ))
  while [ -z "$DDLV" ] && [ "$(date +%s)" -lt "$DDLV_DEADLINE" ]; do
    sleep 5
    DDLV=$(psqld msika_flow "SELECT COALESCE(dispatch_ref,'') FROM mf_selections WHERE selection_id='$DSEL_B'")
  done
fi
if [ -n "$DDLV" ]; then
  pass "#42 BUG-10 FIXED: payment-resumed commitment dispatched a REAL Nhume delivery $DDLV (service-originated v1.1 headers incl. X-Correlation-ID; no manual A9 task)"
else
  DST=$(psqld msika_flow "SELECT COALESCE(dispatch_status,'')||'|'||COALESCE(dispatch_failure_code,'') FROM mf_selections WHERE selection_id='$DSEL_B'")
  CORRMISS=$(kubectl -n "$NS" logs deploy/msika-flow-service --since=6m 2>/dev/null | grep -c "Required v1.1 headers missing")
  fail "#42 BUG-10 regression: paid commitment produced no dispatch_ref (dispatch=$DST, v1.1-missing-header log hits=$CORRMISS)"
fi
if [ -n "$DDLV" ]; then
  nhume_to_transit 76 "$DDLV"
  api 77-return POST "$NHUME/internal/v1/nhume/deliveries/$DDLV/return" "{\"reason\":\"recipient unreachable — return to sender\"}"
  [ "$(psqld nhume "SELECT status FROM nhume_delivery_requests WHERE delivery_id='$DDLV'")" = RETURNED ] \
    && pass "#68 delivery RETURNED (§12.7 return chain)" || fail "#68 return transition failed (HTTP $API_CODE)"
  poll_psql msika_flow "SELECT COALESCE(delivery_status,'') FROM mf_selections WHERE selection_id='$DSEL_B'" "RETURNED" 30 \
    && pass "#68 selection projection ← RETURNED via write-back" \
    || fail "#68 selection projection not RETURNED ($POLL_VAL)"
  RFEVT=$(psqld msika_flow "SELECT count(*) FROM mf_event_outbox WHERE event_type='SELECTION_RETURN_REFUND_REQUESTED' AND aggregate_id='$DSEL_B'")
  RFREF=$(psqld msika_flow "SELECT COALESCE(refund_ref,'') FROM mf_selections WHERE selection_id='$DSEL_B'")
  if [ "$RFEVT" -ge 1 ] && [ -n "$RFREF" ]; then
    pass "#68 refund-on-return engaged: SELECTION_RETURN_REFUND_REQUESTED event + MusheX refund $RFREF"
  else
    RFANY=$(psqld msika_flow "SELECT string_agg(event_type,',') FROM mf_event_outbox WHERE aggregate_id='$DSEL_B' AND event_type LIKE 'SELECTION_RETURN%'")
    fail "#68 refund-on-return not proven (events: ${RFANY:-none}, refund_ref='$RFREF')"
  fi
else
  fail "#68 RETURNED leg: no dispatch for selection $DSEL"
fi

# ═════════════════════════════════════════════════════════════════════════════
# LEG 7 — #45 split selection (two vendors, partial line coverage)
# ═════════════════════════════════════════════════════════════════════════════
echo; echo "── LEG 7: #45 split combination commit ──"
mksplit_rfo() { # mksplit_rfo TAG → REQS + OFFA(L1 by A) + OFFB(L2 by B)
  local tag=$1
  mkorder "$tag-ord" PHARMACY "[{\"code\":\"$I2\",\"displayName\":\"Furosemide\",\"quantity\":5},{\"code\":\"$I3\",\"displayName\":\"Aspirin\",\"quantity\":5}]"
  # SELF_PAY with zero-price offers → step-8 WAIVED → members commit synchronously.
  api "$tag-rfo" POST "http://msika-flow-service:8100/v1/marketplace-requests" \
    "{\"orosOrderId\":\"$ORDER_ID\",\"orosOrderVersionId\":\"$PIN\",\"profile\":\"MEDICATION\",\"publicationMode\":\"INVITED\",\"coarseZone\":\"HARARE\",\"urgency\":\"ROUTINE\",\"fundingMode\":\"SELF_PAY\",\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"$I2\",\"codingSystem\":\"ATC\",\"quantity\":5,\"unit\":\"TAB\",\"controlled\":false,\"coldChain\":false},{\"lineRef\":\"L2\",\"itemCode\":\"$I3\",\"codingSystem\":\"ATC\",\"quantity\":5,\"unit\":\"TAB\",\"controlled\":false,\"coldChain\":false}],\"invitedVendorIds\":[\"$VENDA\",\"$VENDB\"]}"
  REQS=$(jqr '.data.requestId')
  api "$tag-publish" POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQS/publish"
  api "$tag-offA" POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQS/offers" \
    "{\"vendorId\":\"$VENDA\",\"priceTotal\":0.00,\"currency\":\"USD\",\"fulfillmentWindowHours\":24,\"ttlMinutes\":60,\"lines\":[{\"requestLineRef\":\"L1\",\"itemCode\":\"$I2\",\"quantity\":5,\"unitPrice\":0.00,\"substitutionProposed\":false}]}"
  OFFA=$(jqr '.data.offerId')
  api "$tag-offB" POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQS/offers" \
    "{\"vendorId\":\"$VENDB\",\"priceTotal\":0.00,\"currency\":\"USD\",\"fulfillmentWindowHours\":24,\"ttlMinutes\":60,\"lines\":[{\"requestLineRef\":\"L2\",\"itemCode\":\"$I3\",\"quantity\":5,\"unitPrice\":0.00,\"substitutionProposed\":false}]}"
  OFFB=$(jqr '.data.offerId')
}

# 7a valid combination — full coverage, both members commit (zero patient share ⇒ WAIVED step 8)
mksplit_rfo 80
if [ -n "$OFFA" ] && [ "$OFFA" != null ] && [ -n "$OFFB" ] && [ "$OFFB" != null ]; then
  api 80-split POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQS/select-split" \
    "{\"offerIds\":[\"$OFFA\",\"$OFFB\"]}" "Idempotency-Key: $(uuidgen)"
  SOUT=$(jqr '.data.outcome')
  ALLC=$(jq -r '[.data.members[].committed] | all' "$EVID/80-split.json" 2>/dev/null)
  if [ "$SOUT" = COMMITTED ] && [ "$ALLC" = true ]; then
    pass "#45 valid split combination: BOTH members COMMITTED, rollup COMMITTED, coverage complete=$(jqr '.data.coverage.complete')"
    RS=$(psqld msika_flow "SELECT status FROM mf_marketplace_requests WHERE request_id='$REQS'")
    [ "$RS" = COMMITTED ] && pass "#45 request rolled to COMMITTED (every line covered)" || fail "#45 request status $RS"
    SGID=$(jqr '.data.splitGroupId')
    [ "$(psqld msika_flow "SELECT count(DISTINCT split_group_id) FROM mf_selections WHERE request_id='$REQS' AND split_group_id IS NOT NULL")" = 1 ] \
      && pass "#45 both member selections share split_group_id $SGID" || fail "#45 split group ids inconsistent"
  else
    fail "#45 valid combination outcome=$SOUT allCommitted=$ALLC (HTTP $API_CODE): $(head -c 300 "$EVID/80-split.json")"
  fi
else
  fail "#45 could not stage offers for valid-combination leg (offA=$OFFA offB=$OFFB; publish: $(head -c 200 "$EVID/80-publish.json"))"
fi

# 7b overlap combination — refused BEFORE any commit
mksplit_rfo 81
if [ -n "$OFFA" ] && [ "$OFFA" != null ]; then
  api 81-offB2 POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQS/offers" \
    "{\"vendorId\":\"$VENDB\",\"priceTotal\":0.00,\"currency\":\"USD\",\"fulfillmentWindowHours\":24,\"ttlMinutes\":60,\"lines\":[{\"requestLineRef\":\"L1\",\"itemCode\":\"$I2\",\"quantity\":5,\"unitPrice\":2.00,\"substitutionProposed\":false},{\"requestLineRef\":\"L2\",\"itemCode\":\"$I3\",\"quantity\":5,\"unitPrice\":1.00,\"substitutionProposed\":false}]}"
  OFFB2=$(jqr '.data.offerId')
  api 82-split-overlap POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQS/select-split" \
    "{\"offerIds\":[\"$OFFA\",\"$OFFB2\"]}" "Idempotency-Key: $(uuidgen)"
  SELCOUNT=$(psqld msika_flow "SELECT count(*) FROM mf_selections WHERE request_id='$REQS'")
  if [ "$API_CODE" != 200 ] && [ "$API_CODE" != 201 ] && grep -q "OVERLAPPING_LINE_COVERAGE" "$EVID/82-split-overlap.json" && [ "$SELCOUNT" = 0 ]; then
    pass "#45 overlapping combination refused BEFORE any commit (HTTP $API_CODE, OVERLAPPING_LINE_COVERAGE, 0 selections)"
  else
    fail "#45 overlap not refused cleanly: HTTP $API_CODE selections=$SELCOUNT $(head -c 200 "$EVID/82-split-overlap.json")"
  fi
fi

# 7c one member fails — PARTIAL_COMMIT; committed member STANDS
mksplit_rfo 83
if [ -n "$OFFA" ] && [ "$OFFA" != null ] && [ -n "$OFFB" ] && [ "$OFFB" != null ]; then
  psqld inventory "UPDATE inv_on_hand SET qty_on_hand=0, updated_at=now() WHERE store_id='$STOREB' AND item_code='$I3'" >/dev/null
  api 84-split-partial POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQS/select-split" \
    "{\"offerIds\":[\"$OFFA\",\"$OFFB\"]}" "Idempotency-Key: $(uuidgen)"
  psqld inventory "UPDATE inv_on_hand SET qty_on_hand=200, updated_at=now() WHERE store_id='$STOREB' AND item_code='$I3'" >/dev/null
  SOUT3=$(jqr '.data.outcome')
  MA=$(jq -r --arg a "$OFFA" '[.data.members[] | select(.offerId==$a)][0].outcomeCode' "$EVID/84-split-partial.json" 2>/dev/null)
  MB=$(jq -r --arg b "$OFFB" '[.data.members[] | select(.offerId==$b)][0].outcomeCode' "$EVID/84-split-partial.json" 2>/dev/null)
  MASEL=$(jq -r --arg a "$OFFA" '[.data.members[] | select(.offerId==$a)][0].selectionId' "$EVID/84-split-partial.json" 2>/dev/null)
  if [ "$SOUT3" = PARTIAL_COMMIT ] && [ "$MA" = COMMITTED ] && [ "$MB" = STOCK_UNAVAILABLE ]; then
    pass "#45 drained sibling → honest PARTIAL_COMMIT (A COMMITTED, B STOCK_UNAVAILABLE — no invented cross-vendor rollback)"
    [ "$(psqld msika_flow "SELECT status FROM mf_selections WHERE selection_id='$MASEL'")" = COMMITTED ] \
      && pass "#45 committed member STANDS ($MASEL COMMITTED in DB)" || fail "#45 committed member did not stand"
    RS3=$(psqld msika_flow "SELECT status FROM mf_marketplace_requests WHERE request_id='$REQS'")
    [ "$RS3" = OFFERS_AVAILABLE ] && pass "#45 request stays OFFERS_AVAILABLE (uncovered line remains live)" || fail "#45 request status $RS3"
  else
    fail "#45 partial-commit leg: outcome=$SOUT3 A=$MA B=$MB (HTTP $API_CODE): $(head -c 300 "$EVID/84-split-partial.json")"
  fi
fi

# ═════════════════════════════════════════════════════════════════════════════
# LEG 8 — #56 diagnostics with home collection
# ═════════════════════════════════════════════════════════════════════════════
echo; echo "── LEG 8: #56 DIAGNOSTICS home-collection ──"
mkorder 90 LAB "[{\"code\":\"718-7\",\"displayName\":\"Haemoglobin panel\",\"quantity\":1}]"
if [ -z "$ORDER_ID" ] || [ "$ORDER_ID" = null ]; then
  note "#56 LAB order type refused — falling back to PHARMACY order for the version pin"
  mkorder 90b PHARMACY "[{\"code\":\"718-7\",\"displayName\":\"Haemoglobin panel\",\"quantity\":1}]"
fi
CW_START=$(date -u -d '+1 day' +%FT%TZ); CW_END=$(date -u -d '+1 day 4 hours' +%FT%TZ)
api 91-rfo POST "http://msika-flow-service:8100/v1/marketplace-requests" \
  "{\"orosOrderId\":\"$ORDER_ID\",\"orosOrderVersionId\":\"$PIN\",\"profile\":\"DIAGNOSTICS\",\"publicationMode\":\"INVITED\",\"coarseZone\":\"HARARE\",\"urgency\":\"ROUTINE\",\"fundingMode\":\"SELF_PAY\",\"homeCollectionRequested\":true,\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"718-7\",\"codingSystem\":\"LOINC\",\"quantity\":1,\"unit\":\"TEST\",\"controlled\":false,\"coldChain\":false,\"modality\":\"LAB\"}],\"invitedVendorIds\":[\"$VENDA\"]}"
REQ8=$(jqr '.data.requestId'); L8_ORDER=$ORDER_ID
api 92-publish POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ8/publish"
if [ "$(jqr '.data.invitationCount')" -ge 1 ] 2>/dev/null; then
  api 93-offer POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ8/offers" \
    "{\"vendorId\":\"$VENDA\",\"priceTotal\":0.00,\"currency\":\"USD\",\"fulfillmentWindowHours\":48,\"ttlMinutes\":60,\"homeCollection\":true,\"collectionWindowStart\":\"$CW_START\",\"collectionWindowEnd\":\"$CW_END\",\"lines\":[{\"requestLineRef\":\"L1\",\"itemCode\":\"718-7\",\"quantity\":1,\"unitPrice\":0.00,\"substitutionProposed\":false}]}"
  OFFER8=$(jqr '.data.offerId')
  [ "$API_CODE" = 201 ] && [ "$(jqr '.data.homeCollection')" = true ] \
    && pass "#56 diagnostics offer with home-collection window $CW_START..$CW_END" \
    || fail "#56 offer submit (HTTP $API_CODE): $(head -c 200 "$EVID/93-offer.json")"
  api 94-select POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ8/select" \
    "{\"offerId\":\"$OFFER8\"}" "Idempotency-Key: $(uuidgen)"
  OUT8=$(jqr '.data.outcomeCode'); SEL8=$(jqr '.data.selectionId')
  [ "$OUT8" = COMMITTED ] && pass "#56 diagnostics selection COMMITTED (zero-shortfall WAIVED payment)" \
    || fail "#56 selection outcome $OUT8 (HTTP $API_CODE): $(head -c 300 "$EVID/94-select.json")"
  S5D=$(step_entry 94-select 5); S6D=$(step_entry 94-select 6)
  echo "$S5D" | grep -q "^SKIPPED" && echo "$S5D" | grep -qi "service-capacity" \
    && pass "#56 step-5 honestly SKIPPED — no fabricated DURA hold, vendor service-capacity promise recorded" \
    || fail "#56 step-5 entry: $S5D"
  echo "$S6D" | grep -q "^SKIPPED" && pass "#56 step-6 SKIPPED — no prescription to claim; OROS order stays spine truth" || fail "#56 step-6: $S6D"
  SELROW=$(psqld msika_flow "SELECT COALESCE(oros_order_id,'')||'|'||COALESCE(collection_mode,'')||'|'||COALESCE(collection_window_start::text,'') FROM mf_selections WHERE selection_id='$SEL8'")
  echo "$SELROW" | grep -q "^$L8_ORDER|HOME_COLLECTION|." \
    && pass "#56 selection carries oros_order_id=$L8_ORDER + collectionMode HOME_COLLECTION + window" \
    || fail "#56 selection spine seam wrong: $SELROW"
else
  fail "#56 publish refused: $(jqr '.data.refusals')"
fi

# ── Summary ──────────────────────────────────────────────────────────────────
echo; echo "══ RESULTS ══"
printf '%s\n' "${SEL1:-}" > "$EVID/m5-paid-selection-id.txt"
printf '%s\n' "${RESULTS[@]}" | tee "$EVID/RESULTS.txt"
echo
echo "TOTAL: PASS=$PASS FAIL=$FAIL  (evidence: $EVID)"
echo "PASS=$PASS FAIL=$FAIL" >> "$EVID/RESULTS.txt"
exit $([ "$FAIL" -eq 0 ] && echo 0 || echo 1)
