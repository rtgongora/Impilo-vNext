#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Wave OF-A M2 live proof — impilo-full-preview estate
# Journeys: #41 (order → RFO → offer → selection → DURA reservation),
#           #48 (offer TTL / reservation expiry fail-close),
#           #49 (insufficient stock fail-close, nothing charged),
#           #52 (controlled-line publication gating §13.4)
#
# Fail-closed behaviours ARE pass criteria: a 4xx with the right code is a PASS.
# Evidence: every raw response lands in /tmp/ofa-m2-evidence/.
# Idempotent-ish: fixed seed identities (vendor/stock), fresh ULID orders and
# requests per run, stale ACTIVE reservations on the proof store are released
# at seed time.
#
# Known live blocker exercised by this script (BUG-1, see final summary):
#   msika-flow TusoClient sends no trust headers → tuso status-summary throws
#   tenant-isolation SecurityException → eligibility precondition 3 UNVERIFIED
#   → publish fail-closes NO_ELIGIBLE_VENDORS. The script proves every leg that
#   remains reachable and records the structured refusal as evidence.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

NS=impilo-full-preview
TENANT=00000000-0000-4000-8000-000000000001
FAC=f1000000-0000-0000-0000-000000000001       # vashandi-bound premises + DURA facility
STORE=a2000000-0000-4000-8000-0000000000a2     # DURA store (seeded)
ONHAND_ROW=a3000000-0000-4000-8000-0000000000a3
VENDOR=OFAM2PROOFVENDOR0000000001              # 26-char msika-flow vendor id
PROV=PROV-ZW-00004                             # varapi provider (licence seeded ACTIVE)
VASH_PROFILE=c3023adb-8146-40d2-8c00-6e8f9599f73a
TUSO_FID=1                                     # Harare Central Hospital (ACTIVE/OPERATIONAL)
ITEM=C09AA05
EVID=/tmp/ofa-m2-evidence
mkdir -p "$EVID"

PASS=0; FAIL=0; declare -a RESULTS=()
pass() { echo "PASS: $*"; RESULTS+=("PASS: $*"); PASS=$((PASS+1)); }
fail() { echo "FAIL: $*"; RESULTS+=("FAIL: $*"); FAIL=$((FAIL+1)); }
note() { echo "NOTE: $*"; RESULTS+=("NOTE: $*"); }

psqld() { local db=$1; shift; kubectl -n "$NS" exec deploy/postgres -- psql -U impilo -d "$db" -tAc "$1"; }

# api NAME METHOD URL [BODY] [EXTRA_HEADER...]
# Runs curl inside the oros-service pod (it has curl; msika-flow does not).
# Body → $EVID/NAME.json ; last line HTTP code → API_CODE ; body → API_BODY
api() {
  local name=$1 method=$2 url=$3 body=${4:-}; shift; shift; shift; [ $# -gt 0 ] && shift
  local rid; rid=$(uuidgen)
  local args=(-s -w $'\n%{http_code}' --max-time 30 -X "$method" "$url"
    -H "Content-Type: application/json"
    -H "X-Tenant-Id: $TENANT" -H "X-Tenant-ID: $TENANT"
    -H "X-Pod-ID: national-spine"
    -H "X-Request-ID: $rid" -H "X-Correlation-ID: $rid"
    -H "X-Actor-Id: ofa-m2-proof" -H "X-Actor-ID: ofa-m2-proof"
    -H "X-Actor-Type: PROVIDER" -H "X-Purpose-Of-Use: TREATMENT"
    -H "X-Facility-ID: $FAC" -H "x-envoy-internal: true")
  for h in "$@"; do args+=(-H "$h"); done
  [ -n "$body" ] && args+=(-d "$body")
  local out; out=$(kubectl -n "$NS" exec deploy/oros-service -- curl "${args[@]}" 2>/dev/null)
  API_CODE=$(echo "$out" | tail -1)
  API_BODY=$(echo "$out" | sed '$d')
  echo "$API_BODY" > "$EVID/$name.json"
  echo "$API_CODE" > "$EVID/$name.code"
}

jqr() { echo "$API_BODY" | jq -r "$1" 2>/dev/null; }

echo "══ OF-A M2 live proof — $(date -u +%FT%TZ) ══"

# ── 0. Preflight: msika-flow integration env (finding F1 — was missing) ──────
ENVDUMP=$(kubectl -n "$NS" exec deploy/msika-flow-service -- env 2>/dev/null | grep -E "^(VARAPI_URL|TUSO_URL)=" | sort | tr '\n' ' ')
echo "$ENVDUMP" > "$EVID/00-msika-flow-env.txt"
if echo "$ENVDUMP" | grep -q "VARAPI_URL=http://varapi-service:8083" && echo "$ENVDUMP" | grep -q "TUSO_URL=http://tuso-service:8084"; then
  pass "F1-config msika-flow VARAPI_URL/TUSO_URL point at cluster DNS ($ENVDUMP)"
else
  note "F1-config msika-flow missing VARAPI_URL/TUSO_URL (localhost defaults) — applying kubectl set env"
  kubectl -n "$NS" set env deploy/msika-flow-service VARAPI_URL=http://varapi-service:8083 TUSO_URL=http://tuso-service:8084 >/dev/null
  kubectl -n "$NS" rollout status deploy/msika-flow-service --timeout=300s >/dev/null && pass "F1-config env applied + rollout complete" || fail "F1-config rollout failed"
fi

# ── 1. Seeds (idempotent) ────────────────────────────────────────────────────
psqld varapi "UPDATE varapi.provider SET licence_status='ACTIVE' WHERE provider_public_id='$PROV' AND tenant_id='$TENANT'" >/dev/null
psqld msika_flow "INSERT INTO mf_vendor_profiles (vendor_id, tenant_id, type, name, status, coverage, created_at, updated_at)
  VALUES ('$VENDOR','$TENANT','PHARMACY','OF-A M2 Proof Pharmacy','ACTIVE',
  '{\"providerPublicId\":\"$PROV\",\"tusoFacilityId\":$TUSO_FID,\"premisesFacilityId\":\"$FAC\",\"storeId\":\"$STORE\",\"vashandiProfileId\":\"$VASH_PROFILE\",\"zones\":[\"HARARE\"],\"capabilities\":{\"dispensing\":true,\"controlled_authority\":false}}'::jsonb, now(), now())
  ON CONFLICT (vendor_id) DO UPDATE SET coverage=EXCLUDED.coverage, status='ACTIVE', updated_at=now()" >/dev/null
psqld inventory "INSERT INTO inv_on_hand (id, tenant_id, facility_id, store_id, item_code, qty_on_hand, updated_at)
  VALUES ('$ONHAND_ROW','$TENANT','$FAC','$STORE','$ITEM',100, now())
  ON CONFLICT (id) DO UPDATE SET qty_on_hand=100, updated_at=now();
  UPDATE inv_stock_reservations SET status='RELEASED' WHERE store_id='$STORE' AND status='ACTIVE'" >/dev/null
pass "SEED varapi licence ACTIVE / vendor profile / inv_on_hand 100 x $ITEM (stale ACTIVE holds released)"

# Registry truth probes (evidence that seeds satisfy the six preconditions' inputs)
api 01-varapi-standing GET "http://varapi-service:8083/v1/internal/providers/$PROV/standing-summary"
[ "$(jqr '.data.active')" = "true" ] && [ "$(jqr '.data.hasValidLicense')" = "true" ] \
  && pass "P1-input VARAPI standing: active+hasValidLicense for $PROV" \
  || fail "P1-input VARAPI standing not active/licensed: $(jqr '.data')"
api 02-vashandi-assignments GET "http://vashandi-workforce-service:8167/v1/internal/vashandi/assignments"
echo "$API_BODY" | jq -e --arg p "$VASH_PROFILE" --arg f "$FAC" \
  '[.[]? // .data[]? | select(.workforceProfileId==$p and .facilityId==$f and .status=="active")] | length > 0' >/dev/null 2>&1 \
  && pass "P4-input VASHANDI active assignment binds $VASH_PROFILE → $FAC" \
  || fail "P4-input no active VASHANDI assignment found"

# ── 2. Journey #41 leg A — OROS order + immutable version pin ────────────────
api 10-oros-place POST "http://oros-service:8089/v1/orders" \
  "{\"orderType\":\"PHARMACY\",\"priority\":\"ROUTINE\",\"patientCpid\":\"CPID-OFA-M2-PROOF\",\"items\":[{\"code\":\"$ITEM\",\"displayName\":\"Ramipril 5mg\",\"quantity\":10}]}"
ORDER_ID=$(jqr '.data.orderId')
[ "$API_CODE" = 201 ] && [ -n "$ORDER_ID" ] && [ "$ORDER_ID" != null ] \
  && pass "#41 OROS PHARMACY order placed: $ORDER_ID" || fail "#41 OROS order placement (HTTP $API_CODE)"
api 11-oros-versions GET "http://oros-service:8089/v1/orders/$ORDER_ID/versions"
PIN=$(jqr '.data[0].orderVersionId')
[ -n "$PIN" ] && [ "$PIN" != null ] && pass "#41 immutable version pin obtained: $PIN" || fail "#41 no order version pin"

# ── 3. Journey #41 leg B — version-pin fail-close + RFO create + PII floor ──
api 12-rfo-wrong-pin POST "http://msika-flow-service:8100/v1/marketplace-requests" \
  "{\"orosOrderId\":\"$ORDER_ID\",\"orosOrderVersionId\":\"BOGUS-PIN\",\"profile\":\"MEDICATION\",\"publicationMode\":\"INVITED\",\"coarseZone\":\"HARARE\",\"urgency\":\"ROUTINE\",\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"$ITEM\",\"codingSystem\":\"ATC\",\"quantity\":10,\"unit\":\"TAB\",\"controlled\":false,\"coldChain\":false}],\"invitedVendorIds\":[\"$VENDOR\"]}"
[ "$API_CODE" = 400 ] && echo "$API_BODY" | grep -q ORDER_VERSION_UNKNOWN \
  && pass "#41 bogus version pin fail-closed: 400 ORDER_VERSION_UNKNOWN" \
  || fail "#41 bogus pin not rejected (HTTP $API_CODE)"

api 13-rfo-create POST "http://msika-flow-service:8100/v1/marketplace-requests" \
  "{\"orosOrderId\":\"$ORDER_ID\",\"orosOrderVersionId\":\"$PIN\",\"profile\":\"MEDICATION\",\"publicationMode\":\"INVITED\",\"coarseZone\":\"HARARE\",\"urgency\":\"ROUTINE\",\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"$ITEM\",\"codingSystem\":\"ATC\",\"quantity\":10,\"unit\":\"TAB\",\"controlled\":false,\"coldChain\":false}],\"invitedVendorIds\":[\"$VENDOR\"]}"
REQ_ID=$(jqr '.data.requestId')
[ "$API_CODE" = 201 ] && [ -n "$REQ_ID" ] && [ "$REQ_ID" != null ] \
  && pass "#41 RFO created (INVITED, version-pinned): $REQ_ID" || fail "#41 RFO create (HTTP $API_CODE)"

SNAP=$(psqld msika_flow "SELECT published_lines_json FROM mf_marketplace_requests WHERE request_id='$REQ_ID'")
echo "$SNAP" > "$EVID/14-published-snapshot.json"
if echo "$SNAP" | grep -qiE "CPID-OFA-M2-PROOF|patient|prescriber|address|ofa-m2-proof"; then
  fail "#41 PII floor: published snapshot leaks identity material"
else
  pass "#41 PII floor: published_lines_json carries coded lines only (no patient/actor identifiers)"
fi

# ── 4. Journey #41 leg C — publish → invitations (BUG-1 gate) ────────────────
api 15-rfo-publish POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ_ID/publish"
FULL_PATH=0
if [ "$API_CODE" = 200 ] && [ "$(jqr '.data.invitationCount')" -ge 1 ] 2>/dev/null; then
  FULL_PATH=1
  pass "#41 publish → $(jqr '.data.invitationCount') invitation(s), status $(jqr '.data.request.status')"
elif echo "$API_BODY" | grep -q "PREMISES_UNVERIFIED"; then
  pass "#41 publish fail-closed with STRUCTURED refusal (§4.3, never silent): NO_ELIGIBLE_VENDORS {$VENDOR=PREMISES_UNVERIFIED} — BUG-1 evidence"
  fail "#41 BLOCKED: eligibility precondition 3 cannot be verified live (BUG-1: TusoClient sends no trust headers; see summary) — invitation/offer/selection legs unreachable via API"
else
  fail "#41 publish unexpected outcome HTTP $API_CODE: $(echo "$API_BODY" | head -c 300)"
fi

# ── 5. Full path (only reachable if BUG-1 is absent in deployed image) ───────
if [ "$FULL_PATH" = 1 ]; then
  api 16-offer POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ_ID/offers" \
    "{\"vendorId\":\"$VENDOR\",\"priceTotal\":25.00,\"currency\":\"ZWG\",\"fulfillmentWindowHours\":24,\"ttlMinutes\":60,\"lines\":[{\"requestLineRef\":\"L1\",\"itemCode\":\"$ITEM\",\"quantity\":10,\"unitPrice\":2.50,\"substitutionProposed\":false}]}"
  OFFER_ID=$(jqr '.data.offerId')
  [ "$API_CODE" = 201 ] && pass "#41 offer submitted: $OFFER_ID (grade $(jqr '.data.lines[0].stockGrade'))" || fail "#41 offer submit (HTTP $API_CODE)"
  IDEM=$(uuidgen)
  api 17-select POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ_ID/select" \
    "{\"offerId\":\"$OFFER_ID\"}" "Idempotency-Key: $IDEM"
  [ "$(jqr '.data.outcomeCode')" = COMMITTED ] && pass "#41 selection COMMITTED: $(jqr '.data.selectionId')" || fail "#41 selection outcome $(jqr '.data.outcomeCode')"
  ACT=$(psqld inventory "SELECT count(*) FROM inv_stock_reservations WHERE ref_type='MARKETPLACE_SELECTION' AND status='ACTIVE' AND store_id='$STORE'")
  [ "$ACT" -ge 1 ] && pass "#41 DURA inv_stock_reservations ACTIVE row present" || fail "#41 no ACTIVE DURA reservation"
  PROJ=$(psqld msika_flow "SELECT count(*) FROM mf_reservations WHERE order_id='$ORDER_ID'")
  [ "$PROJ" -ge 1 ] && pass "#41 mf_reservations projection row present" || fail "#41 no mf_reservations projection"
  RSTAT=$(psqld msika_flow "SELECT status FROM mf_marketplace_requests WHERE request_id='$REQ_ID'")
  [ "$RSTAT" = COMMITTED ] && pass "#41 request COMMITTED" || fail "#41 request status $RSTAT"
  # replay idempotency
  api 18-select-replay POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ_ID/select" \
    "{\"offerId\":\"$OFFER_ID\"}" "Idempotency-Key: $IDEM"
  [ "$(jqr '.data.replayed')" = "true" ] && pass "#41 RC-1 idempotent replay honoured" || fail "#41 RC-1 replay not honoured"

  # ── 5b. Journey #48 — RC-2: select AFTER offer TTL lapse → OFFER_EXPIRED ───
  api 40-rfo-rc2 POST "http://msika-flow-service:8100/v1/marketplace-requests" \
    "{\"orosOrderId\":\"$ORDER_ID\",\"orosOrderVersionId\":\"$PIN\",\"profile\":\"MEDICATION\",\"publicationMode\":\"INVITED\",\"coarseZone\":\"HARARE\",\"urgency\":\"ROUTINE\",\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"$ITEM\",\"codingSystem\":\"ATC\",\"quantity\":10,\"unit\":\"TAB\",\"controlled\":false,\"coldChain\":false}],\"invitedVendorIds\":[\"$VENDOR\"]}"
  REQ2=$(jqr '.data.requestId')
  api 41-rc2-publish POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ2/publish"
  api 42-rc2-offer POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ2/offers" \
    "{\"vendorId\":\"$VENDOR\",\"priceTotal\":25.00,\"currency\":\"ZWG\",\"fulfillmentWindowHours\":24,\"ttlMinutes\":60,\"lines\":[{\"requestLineRef\":\"L1\",\"itemCode\":\"$ITEM\",\"quantity\":10,\"unitPrice\":2.50,\"substitutionProposed\":false}]}"
  OFFER2=$(jqr '.data.offerId')
  if [ -n "$OFFER2" ] && [ "$OFFER2" != null ]; then
    psqld msika_flow "UPDATE mf_fulfillment_offers SET ttl_expires_at = now() - interval '1 minute' WHERE offer_id='$OFFER2'" >/dev/null
    api 43-rc2-select POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ2/select" \
      "{\"offerId\":\"$OFFER2\"}" "Idempotency-Key: $(uuidgen)"
    OST=$(psqld msika_flow "SELECT status FROM mf_fulfillment_offers WHERE offer_id='$OFFER2'")
    [ "$(jqr '.data.outcomeCode')" = OFFER_EXPIRED ] && [ "$OST" = EXPIRED ] \
      && pass "#48 RC-2 select after TTL lapse → outcome OFFER_EXPIRED, offer EXPIRED, nothing charged" \
      || fail "#48 RC-2 expected OFFER_EXPIRED/EXPIRED, got outcome=$(jqr '.data.outcomeCode') offer=$OST"
  else
    fail "#48 RC-2 leg could not obtain an offer (HTTP $API_CODE)"
  fi

  # ── 5c. Journey #49 — drained stock at selection → STOCK_UNAVAILABLE, offer FAILED_REVALIDATION ──
  api 44-rfo-frv POST "http://msika-flow-service:8100/v1/marketplace-requests" \
    "{\"orosOrderId\":\"$ORDER_ID\",\"orosOrderVersionId\":\"$PIN\",\"profile\":\"MEDICATION\",\"publicationMode\":\"INVITED\",\"coarseZone\":\"HARARE\",\"urgency\":\"ROUTINE\",\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"$ITEM\",\"codingSystem\":\"ATC\",\"quantity\":10,\"unit\":\"TAB\",\"controlled\":false,\"coldChain\":false}],\"invitedVendorIds\":[\"$VENDOR\"]}"
  REQ3=$(jqr '.data.requestId')
  api 45-frv-publish POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ3/publish"
  api 46-frv-offer POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ3/offers" \
    "{\"vendorId\":\"$VENDOR\",\"priceTotal\":25.00,\"currency\":\"ZWG\",\"fulfillmentWindowHours\":24,\"ttlMinutes\":60,\"lines\":[{\"requestLineRef\":\"L1\",\"itemCode\":\"$ITEM\",\"quantity\":10,\"unitPrice\":2.50,\"substitutionProposed\":false}]}"
  OFFER3=$(jqr '.data.offerId')
  if [ -n "$OFFER3" ] && [ "$OFFER3" != null ]; then
    psqld inventory "UPDATE inv_on_hand SET qty_on_hand=0, updated_at=now() WHERE id='$ONHAND_ROW'" >/dev/null
    api 47-frv-select POST "http://msika-flow-service:8100/v1/marketplace-requests/$REQ3/select" \
      "{\"offerId\":\"$OFFER3\"}" "Idempotency-Key: $(uuidgen)"
    OST3=$(psqld msika_flow "SELECT status FROM mf_fulfillment_offers WHERE offer_id='$OFFER3'")
    psqld inventory "UPDATE inv_on_hand SET qty_on_hand=100, updated_at=now() WHERE id='$ONHAND_ROW'" >/dev/null
    [ "$(jqr '.data.outcomeCode')" = STOCK_UNAVAILABLE ] && [ "$OST3" = FAILED_REVALIDATION ] \
      && pass "#49 drained stock at selection → outcome STOCK_UNAVAILABLE, offer FAILED_REVALIDATION (coded, nothing charged)" \
      || fail "#49 expected STOCK_UNAVAILABLE/FAILED_REVALIDATION, got outcome=$(jqr '.data.outcomeCode') offer=$OST3"
  else
    fail "#49 FAILED_REVALIDATION leg could not obtain an offer (HTTP $API_CODE)"
  fi
else
  note "#48/#49 msika-flow offer legs (RC-2 select-after-TTL, FAILED_REVALIDATION on drained stock) unreachable — same BUG-1 gate; proving DURA-side equivalents instead"
fi

# ── 6. DURA direct legs — #41 reservation truth, #49 fail-close, #48 expiry ──
api 20-availability GET "http://inventory-service:8098/v1/dura/reservations/availability?facilityId=$FAC&storeId=$STORE&itemCode=$ITEM"
AVAIL0=$(jqr '.data.available')
[ "$AVAIL0" -ge 15 ] 2>/dev/null && pass "DURA availability baseline: available=$AVAIL0 (onHand $(jqr '.data.onHand'), reserved $(jqr '.data.reserved'))" || fail "DURA availability baseline unusable: $API_BODY"

# #41 leg: conditional reserve within stock
PROOF_REF="ofa-m2-proof-$(date +%s)"
api 21-reserve POST "http://inventory-service:8098/v1/dura/reservations" \
  "{\"facilityId\":\"$FAC\",\"storeId\":\"$STORE\",\"itemCode\":\"$ITEM\",\"qty\":10,\"uom\":\"TAB\",\"refType\":\"MARKETPLACE_SELECTION\",\"refId\":\"$PROOF_REF\",\"reason\":\"OF-A M2 proof #41 leg\",\"expiresAt\":\"$(date -u -d '+1 hour' +%FT%TZ)\"}"
RES_ID=$(jqr '.data.reservationId')
DB_ST=$(psqld inventory "SELECT status FROM inv_stock_reservations WHERE reservation_id='$RES_ID'" 2>/dev/null)
[ "$API_CODE" = 200 ] && [ "$DB_ST" = ACTIVE ] \
  && pass "#41 DURA conditional reserve → inv_stock_reservations $RES_ID ACTIVE" \
  || fail "#41 DURA reserve failed (HTTP $API_CODE, db=$DB_ST)"

# #49: request beyond remaining stock → fail-close, no hold, nothing charged
api 22-over-reserve POST "http://inventory-service:8098/v1/dura/reservations" \
  "{\"facilityId\":\"$FAC\",\"storeId\":\"$STORE\",\"itemCode\":\"$ITEM\",\"qty\":500,\"refType\":\"MARKETPLACE_SELECTION\",\"refId\":\"$PROOF_REF-over\",\"reason\":\"OF-A M2 proof #49 leg\"}"
OVER=$(psqld inventory "SELECT count(*) FROM inv_stock_reservations WHERE ref_id='$PROOF_REF-over'")
if [ "$API_CODE" != 200 ] && [ "$OVER" = 0 ]; then
  pass "#49 over-stock reserve fail-closed (HTTP $API_CODE), zero reservation rows, nothing charged"
  [ "$API_CODE" = 500 ] && note "#49 BUG-2: refusal surfaces as raw 500, not a coded 4xx — inventory-service has no handler for IllegalStateException ('Insufficient available stock', ReservationServiceImpl.java:72-75); msika-flow InventoryClient still fail-closes on any non-2xx (RC-3)"
else
  fail "#49 over-stock reserve was not refused or left a row (HTTP $API_CODE, rows=$OVER)"
fi
api 23-availability-after GET "http://inventory-service:8098/v1/dura/reservations/availability?facilityId=$FAC&storeId=$STORE&itemCode=$ITEM"
[ "$(jqr '.data.available')" = "$((AVAIL0-10))" ] && pass "#49 availability integrity: $AVAIL0 → $(jqr '.data.available') (only the qty-10 hold)" || fail "#49 availability drifted: $(jqr '.data')"

# #48: expiry sweeper flips backdated ACTIVE → EXPIRED; msika-flow projection follows via kafka
api 24-reserve-shortttl POST "http://inventory-service:8098/v1/dura/reservations" \
  "{\"facilityId\":\"$FAC\",\"storeId\":\"$STORE\",\"itemCode\":\"$ITEM\",\"qty\":5,\"refType\":\"MARKETPLACE_SELECTION\",\"refId\":\"$PROOF_REF-exp\",\"reason\":\"OF-A M2 proof #48 leg\",\"expiresAt\":\"$(date -u -d '+2 hour' +%FT%TZ)\"}"
EXP_ID=$(jqr '.data.reservationId')
if [ -n "$EXP_ID" ] && [ "$EXP_ID" != null ]; then
  # Projection row seeded so the kafka consumer has something to flip (the real
  # commitment writer is blocked by BUG-1 and would anyway violate the
  # mf_reservations FKs — BUG-3; the FK-satisfying parent rows are seeded here).
  PROJ_ID=$(echo "OFAM2PROJ$(date +%s)00000000000000" | cut -c1-26)
  PORD=OFAM2PROOFORDER00000000001
  PLINE=OFAM2PROOFLINE000000000001
  psqld msika_flow "INSERT INTO mf_orders (order_id, tenant_id, actor_id, order_type, status)
      VALUES ('$PORD','$TENANT','ofa-m2-proof','PHARMACY','CREATED') ON CONFLICT (order_id) DO NOTHING;
    INSERT INTO mf_order_lines (line_id, order_id, msika_core_code, kind, qty, unit_price, line_total, state)
      VALUES ('$PLINE','$PORD','$ITEM','PRODUCT',5,1.00,5.00,'CREATED') ON CONFLICT (line_id) DO NOTHING;
    INSERT INTO mf_reservations (id, order_id, line_id, system, status, reservation_ref, expires_at, created_at, updated_at)
      VALUES ('$PROJ_ID','$PORD','$PLINE','INVENTORY','CONFIRMED','$EXP_ID', now(), now(), now())" >/dev/null
  psqld inventory "UPDATE inv_stock_reservations SET expires_at = now() - interval '1 minute' WHERE reservation_id='$EXP_ID'" >/dev/null
  DEADLINE=$(( $(date +%s) + 150 )); SWEPT=""
  while [ $(date +%s) -lt $DEADLINE ]; do
    ST=$(psqld inventory "SELECT status FROM inv_stock_reservations WHERE reservation_id='$EXP_ID'")
    [ "$ST" = EXPIRED ] && SWEPT=1 && break
    sleep 10
  done
  [ -n "$SWEPT" ] && pass "#48 DURA expiry sweeper flipped backdated ACTIVE → EXPIRED within 150s ($EXP_ID)" \
                  || fail "#48 reservation still $(psqld inventory "SELECT status FROM inv_stock_reservations WHERE reservation_id='$EXP_ID'") after 150s"
  DEADLINE=$(( $(date +%s) + 120 )); FLIPPED=""
  while [ $(date +%s) -lt $DEADLINE ]; do
    PST=$(psqld msika_flow "SELECT status FROM mf_reservations WHERE reservation_ref='$EXP_ID'")
    [ "$PST" = EXPIRED ] && FLIPPED=1 && break
    sleep 10
  done
  [ -n "$FLIPPED" ] && pass "#48 msika-flow mf_reservations projection followed to EXPIRED via kafka (inventory.reservation.status_changed)" \
                    || fail "#48 projection still '$(psqld msika_flow "SELECT status FROM mf_reservations WHERE reservation_ref='$EXP_ID'")' after 120s — kafka leg broken"
else
  fail "#48 could not create short-TTL reservation (HTTP $API_CODE)"
fi

# release the #41 hold (compensation path + leaves the estate clean)
api 25-release POST "http://inventory-service:8098/v1/dura/reservations/$RES_ID/release"
[ "$(psqld inventory "SELECT status FROM inv_stock_reservations WHERE reservation_id='$RES_ID'")" = RELEASED ] \
  && pass "#41 DURA release compensation → RELEASED" || fail "#41 release failed (HTTP $API_CODE)"

# ── 7. Journey #52 — controlled-line gating (§13.4) ──────────────────────────
api 30-controlled-open POST "http://msika-flow-service:8100/v1/marketplace-requests" \
  "{\"orosOrderId\":\"$ORDER_ID\",\"orosOrderVersionId\":\"$PIN\",\"profile\":\"MEDICATION\",\"publicationMode\":\"OPEN\",\"coarseZone\":\"HARARE\",\"urgency\":\"ROUTINE\",\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"$ITEM\",\"codingSystem\":\"ATC\",\"quantity\":10,\"unit\":\"TAB\",\"controlled\":true,\"coldChain\":false}]}"
[ "$API_CODE" = 400 ] && echo "$API_BODY" | grep -q CONTROLLED_OPEN_BROADCAST_FORBIDDEN \
  && pass "#52 controlled + OPEN → 400 CONTROLLED_OPEN_BROADCAST_FORBIDDEN (§13.4.1)" \
  || fail "#52 controlled OPEN broadcast not refused (HTTP $API_CODE)"

api 31-controlled-invited POST "http://msika-flow-service:8100/v1/marketplace-requests" \
  "{\"orosOrderId\":\"$ORDER_ID\",\"orosOrderVersionId\":\"$PIN\",\"profile\":\"MEDICATION\",\"publicationMode\":\"INVITED\",\"coarseZone\":\"HARARE\",\"urgency\":\"ROUTINE\",\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"$ITEM\",\"codingSystem\":\"ATC\",\"quantity\":10,\"unit\":\"TAB\",\"controlled\":true,\"coldChain\":false}],\"invitedVendorIds\":[\"$VENDOR\"]}"
CREQ=$(jqr '.data.requestId')
api 32-controlled-publish-noauth POST "http://msika-flow-service:8100/v1/marketplace-requests/$CREQ/publish"
echo "$API_BODY" | grep -q "CONTROLLED_AUTHORITY_MISSING" \
  && pass "#52 INVITED publish, vendor w/o controlled_authority → refusal CONTROLLED_AUTHORITY_MISSING recorded, 0 invitations (§13.4 pre-eligibility filter — controlled lines never exposed)" \
  || fail "#52 expected CONTROLLED_AUTHORITY_MISSING refusal, got HTTP $API_CODE: $(echo "$API_BODY" | head -c 200)"

# flip authority → the §13.4 filter must now admit the vendor into full eligibility
psqld msika_flow "UPDATE mf_vendor_profiles SET coverage = jsonb_set(coverage::jsonb,'{capabilities,controlled_authority}','true'), updated_at=now() WHERE vendor_id='$VENDOR'" >/dev/null
api 33-controlled-invited2 POST "http://msika-flow-service:8100/v1/marketplace-requests" \
  "{\"orosOrderId\":\"$ORDER_ID\",\"orosOrderVersionId\":\"$PIN\",\"profile\":\"MEDICATION\",\"publicationMode\":\"INVITED\",\"coarseZone\":\"HARARE\",\"urgency\":\"ROUTINE\",\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"$ITEM\",\"codingSystem\":\"ATC\",\"quantity\":10,\"unit\":\"TAB\",\"controlled\":true,\"coldChain\":false}],\"invitedVendorIds\":[\"$VENDOR\"]}"
CREQ2=$(jqr '.data.requestId')
api 34-controlled-publish-auth POST "http://msika-flow-service:8100/v1/marketplace-requests/$CREQ2/publish"
if [ "$API_CODE" = 200 ] && [ "$(jqr '.data.invitationCount')" -ge 1 ] 2>/dev/null; then
  pass "#52 authority flipped → vendor invited ($(jqr '.data.invitationCount') invitation(s))"
elif echo "$API_BODY" | grep -q "PREMISES_UNVERIFIED" && ! echo "$API_BODY" | grep -q "CONTROLLED_AUTHORITY_MISSING"; then
  pass "#52 authority flipped → §13.4 gate PASSED (refusal changed CONTROLLED_AUTHORITY_MISSING → PREMISES_UNVERIFIED); invitation itself still blocked by BUG-1 only"
  fail "#52 full invited leg unprovable live (BUG-1 blocks precondition 3) — controlled gate itself proven both ways"
else
  fail "#52 post-flip publish unexpected: HTTP $API_CODE $(echo "$API_BODY" | head -c 200)"
fi
# restore seed posture
psqld msika_flow "UPDATE mf_vendor_profiles SET coverage = jsonb_set(coverage::jsonb,'{capabilities,controlled_authority}','false'), updated_at=now() WHERE vendor_id='$VENDOR'" >/dev/null

# ── Summary ──────────────────────────────────────────────────────────────────
echo
echo "══ RESULTS ══"
printf '%s\n' "${RESULTS[@]}" | tee "$EVID/RESULTS.txt"
echo
echo "TOTAL: PASS=$PASS FAIL=$FAIL  (evidence: $EVID)"
echo "PASS=$PASS FAIL=$FAIL" >> "$EVID/RESULTS.txt"
exit $([ "$FAIL" -eq 0 ] && echo 0 || echo 1)
