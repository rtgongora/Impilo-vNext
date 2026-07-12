set -u
MSIKA=http://localhost:28086; FLOW=http://localhost:28100
TEN=00000000-0000-4000-8000-000000000001
PASS=0; FAIL=0
ok(){ echo "   PASS: $1"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1"; FAIL=$((FAIL+1)); }
PSQL(){ docker exec msika-rig-pg psql -U impilo -d "$1" -tAc "$2"; }
hdr(){ local r=$1 a=${2:-rig-$1}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Idempotency-Key:$(uuidgen) -H X-Actor-ID:$a -H X-Actor-Type:$r -H X-Purpose-Of-Use:MARKETPLACE"; }
mid(){ python3 -c "import json,sys;r=json.load(sys.stdin);d=r.get('data') if isinstance(r,dict) and 'data' in r else r;print(d.get('$1') or d.get('id') or '')"; }

echo "== BUYER lane: seed priced listing → cart → checkout → pay → PAID (real MusheX + Kafka)"
ITEMCODE="ZW-STARTER-EQP-001"
ITEMID=$(PSQL msika "SELECT item_id FROM msika_catalog_items WHERE canonical_code='$ITEMCODE'")
SF=$(curl -sS $(hdr MARKETPLACE_SELLER) -X POST $MSIKA/v1/storefronts -d '{"sellerType":"VENDOR","sellerId":"VEND-BUY-1","displayName":"Rig Supplies"}' | mid storefrontId)
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/storefronts/$SF/verify -d '{"verificationStatus":"VERIFIED","notes":"rig"}' >/dev/null
LST=$(curl -sS $(hdr MARKETPLACE_SELLER) -X POST $MSIKA/v1/listings -d "{\"storefrontId\":\"$SF\",\"catalogItemId\":\"$ITEMID\",\"sellerType\":\"VENDOR\",\"sellerId\":\"VEND-BUY-1\",\"title\":\"Rig Item\",\"summary\":\"priced\",\"priceAmount\":42.50,\"priceCurrency\":\"ZWG\"}" | mid listingId)
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$LST/submit >/dev/null
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$LST/approve -d '{"notes":"ok"}' >/dev/null
PUBC=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$LST/publish)
[ "$PUBC" = 200 ] && [ "$(PSQL msika "SELECT price_amount FROM msika_listings WHERE listing_id='$LST'")" = "42.50" ] && ok "listing published with price 42.50 (V008 price gate satisfied)" || bad "publish=$PUBC"

B="rig-buyer-$$"
CART=$(curl -sS $(hdr PATIENT $B) "$FLOW/v1/carts/open?channel=WEB" | mid cartId)
ADD=$(curl -sS $(hdr PATIENT $B) -X POST $FLOW/v1/carts/$CART/items -d "{\"msikaCoreCode\":\"$ITEMCODE\",\"listingId\":\"$LST\",\"kind\":\"PRODUCT\",\"qty\":1,\"fulfillmentMode\":\"PHARMACY_PICKUP\"}")
echo "$ADD" | grep -q '"success":true' && ok "add-to-cart persisted line (listingId carried)" || bad "add: $(echo $ADD|head -c 120)"
CO=$(curl -sS $(hdr PATIENT $B) -X POST $FLOW/v1/carts/$CART/checkout -d '{"orderType":"OTC_PRODUCT_ORDER","idempotencyKey":"rigco-'$$'"}')
echo "$CO" > "$EV/buyer-checkout.json"
ORDER=$(echo "$CO" | mid orderId)
UNITP=$(PSQL msika_flow "SELECT unit_price FROM mf_order_lines WHERE order_id='$ORDER' LIMIT 1")
TOTAL=$(PSQL msika_flow "SELECT amount_total FROM mf_orders WHERE order_id='$ORDER'")
[ -n "$ORDER" ] && ok "checkout created order $ORDER" || bad "checkout: $(echo $CO|head -c 150)"
[ -n "$UNITP" ] && [ "$UNITP" != "0.00" ] && [ "$UNITP" != "0" ] && ok "line priced from listing snapshot (unit=$UNITP total=$TOTAL) — NOT the hardcoded zero" || bad "unit_price=$UNITP (zero-price bug not fixed)"

curl -sS $(hdr PATIENT $B) -X POST $FLOW/v1/orders/$ORDER/validate >/dev/null 2>&1
 curl -sS $(hdr PATIENT $B) -X POST $FLOW/v1/orders/$ORDER/price >/dev/null 2>&1
PAY=$(curl -sS $(hdr PATIENT $B) -X POST $FLOW/v1/orders/$ORDER/pay)
echo "$PAY" > "$EV/buyer-pay.json"
INTENT=$(echo "$PAY" | python3 -c "import json,sys;r=json.load(sys.stdin);d=r.get('data',r);print(d.get('mushexPaymentIntentId') or '')")
[ -n "$INTENT" ] && [ "$INTENT" != "null" ] && ok "pay minted a REAL MusheX intent id ($INTENT)" || bad "no intent: $(echo $PAY|head -c 150)"
MXI=$(PSQL mushex_db "SELECT count(*) FROM payment_intents WHERE source_id='$ORDER'" 2>/dev/null)
[ "${MXI:-0}" -ge 1 ] && ok "MusheX holds the intent for this order (real HTTP handoff, not a local fake)" || bad "mushex intent count=$MXI"

STATUS=""
for i in $(seq 1 30); do STATUS=$(PSQL msika_flow "SELECT status FROM mf_orders WHERE order_id='$ORDER'"); [ "$STATUS" = "PAID" ] && break; sleep 5; done
[ "$STATUS" = "PAID" ] && ok "MONEY LOOP CLOSED: order PAID via real MusheX sandbox settle + Kafka payment.status.changed → PaymentEventConsumer" || bad "order stuck at '$STATUS' (loop did not close)"
echo "   final: order=$STATUS settlement=$(PSQL msika_flow "SELECT status FROM mf_settlements WHERE order_id='$ORDER'")"
echo "BUYER RESULT: PASS=$PASS FAIL=$FAIL"
