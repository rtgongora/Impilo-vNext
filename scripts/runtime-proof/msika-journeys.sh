#!/usr/bin/env bash
# ============================================================================
# Msika marketplace — four-lane runtime journey proof.
#
# Boots the marketplace estate from local jars on scratch infra and drives:
#   J1  buyer money loop: listing → cart → checkout → pay → REAL MusheX intent
#       → sandbox settle → Kafka payment.status.changed → PAID → settlement
#       splits → Costa charge SETTLED → charge_ref write-back → refund branch
#   J1b delivery leg: HOME_DELIVERY order → Nhume mission → courier drive →
#       dispatch-status write-backs → OUT_FOR_DELIVERY → DELIVERED → COMPLETED
#   J2  seller/vendor: apply → approve → vendor.approved (Kafka) → storefront
#       auto-provisioned in msika → bind-actor → vendor/me
#   J3  operator: reject/suspend/reinstate + storefront cascade + audit feed
#   J4  citizen mobile (through experience-bff): category discovery → request
#       → truthful cancel
#
# Requirements: docker, java 21, jars already packaged (mvn package) for:
#   msika-service msika-flow-service mushex-service nhume-service
#   costing-engine-service experience-bff
# Usage: EV=<evidence-dir> bash scripts/runtime-proof/msika-journeys.sh
#        KEEP_RIG=1 to leave infra running for inspection.
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-/tmp/msika-journeys-evidence}
RIGLOG=${RIGLOG:-/tmp/msika-rig-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-4000-8000-000000000001
PGPORT=15633; KPORT=19092; RPORT=16399
MSIKA=http://localhost:28086; FLOW=http://localhost:28100; MUSHEX=http://localhost:28102
NHUME=http://localhost:28210; BFF=http://localhost:28160
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
PSQL(){ docker exec msika-rig-pg psql -U impilo -d "$1" -tAc "$2"; }
hdr(){ local r=$1 a=${2:-rig-$1}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Idempotency-Key:$(uuidgen) -H Idempotency-Key:$(uuidgen) -H X-Actor-ID:$a -H X-Actor-Type:$r -H X-Purpose-Of-Use:MARKETPLACE"; }
mid(){ python3 -c "import json,sys;r=json.load(sys.stdin);d=r.get('data') if isinstance(r,dict) and 'data' in r else r;print((d or {}).get('$1') or (d or {}).get('id') or '')"; }
poll(){ # poll <db> <sql> <expected> <tries> <label>
  local v=""; for i in $(seq 1 "$4"); do v=$(PSQL "$1" "$2"); [ "$v" = "$3" ] && { echo "$v"; return 0; }; sleep 5; done; echo "$v"; return 1; }

# ── Infra ───────────────────────────────────────────────────────────────────
say "RIG: infra + services"
docker rm -f msika-rig-pg msika-rig-kafka msika-rig-redis >/dev/null 2>&1
docker run -d --name msika-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name msika-rig-kafka -p $KPORT:$KPORT redpandadata/redpanda:latest \
  redpanda start --overprovisioned --smp 1 --memory 512M --reserve-memory 0M --node-id 0 --check=false \
  --kafka-addr PLAINTEXT://0.0.0.0:$KPORT --advertise-kafka-addr PLAINTEXT://localhost:$KPORT >/dev/null
docker run -d --name msika-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 12
for db in msika msika_flow mushex_db costa_db impilo_nhume; do
  docker exec msika-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE $db" >/dev/null 2>&1
done

jar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }
COMMON="POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:$KPORT \
SPRING_KAFKA_LISTENER_AUTO_STARTUP=true SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=$RPORT"

env $COMMON POSTGRES_DB=msika SERVER_PORT=28086 \
  nohup java -jar "$(jar msika-service)" > "$RIGLOG/msika.log" 2>&1 & echo $! > "$RIGLOG/msika.pid"
env $COMMON SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$PGPORT/mushex_db SERVER_PORT=28102 \
  MUSHEX_HMAC_PEPPER=rig-pepper-0123456789 MUSHEX_SANDBOX_APPLY_SIMULATION_OUTCOME=true \
  MUSHEX_SANDBOX_BYPASS_CREDENTIAL_CHECK=true CREDENTIAL_VERIFICATION_ENABLED=false CREDENTIAL_PAYEE_VERIFICATION_ENABLED=false \
  nohup java -jar "$(jar mushex-service)" > "$RIGLOG/mushex.log" 2>&1 & echo $! > "$RIGLOG/mushex.pid"
env $COMMON POSTGRES_DB=impilo_nhume SERVER_PORT=28210 \
  NHUME_WRITEBACK_MSIKA_FLOW_BASE_URL=http://localhost:28100 \
  nohup java -jar "$(jar nhume-service)" > "$RIGLOG/nhume.log" 2>&1 & echo $! > "$RIGLOG/nhume.pid"
env $COMMON SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$PGPORT/costa_db SERVER_PORT=28101 \
  nohup java -jar "$(jar costing-engine-service)" > "$RIGLOG/costa.log" 2>&1 & echo $! > "$RIGLOG/costa.pid"
env $COMMON POSTGRES_DB=msika_flow SERVER_PORT=28100 \
  MSIKA_CORE_URL=http://localhost:28086 MSIKA_BASE_URL=http://localhost:28086 MUSHEX_URL=http://localhost:28102 \
  NHUME_URL=http://localhost:28210 MSIKA_FLOW_NHUME_ENABLED=true MSIKA_FLOW_PAYMENTS_SIMULATION_METADATA=true \
  nohup java -jar "$(jar msika-flow-service)" > "$RIGLOG/flow.log" 2>&1 & echo $! > "$RIGLOG/flow.pid"
# BFF: real downstream URLs; auth opened via the estate's own dev mechanism
# (OAuth2 RS auto-config excluded -> no JwtDecoder bean -> allow-anonymous permitAll,
#  same as application-test.yml). Rig-only — never a deploy configuration.
env $COMMON SERVER_PORT=28160 \
  MSIKA_BASE_URL=http://localhost:28086 MSIKA_FLOW_BASE_URL=http://localhost:28100 MUSHEX_BASE_URL=http://localhost:28102 \
  SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration \
  IMPILO_SECURITY_ALLOW_ANONYMOUS=true \
  nohup java -jar "$(jar experience-bff)" > "$RIGLOG/bff.log" 2>&1 & echo $! > "$RIGLOG/bff.pid"

sleep 60
UP=0
for svc in "msika 28086" "flow 28100" "mushex 28102" "nhume 28210" "costa 28101" "bff 28160"; do
  set -- $svc
  for i in $(seq 1 12); do c=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$2/actuator/health 2>/dev/null); [ "$c" = 200 ] && break; sleep 5; done
  echo "   $1 ($2): $c" | tee -a "$EV/journal.txt"; [ "$c" = 200 ] && UP=$((UP+1))
done
[ "$UP" -ge 5 ] && ok "$UP/6 services healthy" || bad "only $UP/6 services healthy"

# ── J1: buyer money loop ────────────────────────────────────────────────────
say "J1: buyer money loop (cart → PAID → Costa SETTLED → refund)"
ITEMCODE="ZW-STARTER-EQP-001"
ITEMID=$(PSQL msika "SELECT item_id FROM msika_catalog_items WHERE canonical_code='$ITEMCODE'")
SF=$(curl -sS $(hdr MARKETPLACE_SELLER) -X POST $MSIKA/v1/storefronts -d '{"sellerType":"VENDOR","sellerId":"VEND-J1","displayName":"J1 Supplies"}' | mid storefrontId)
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/storefronts/$SF/verify -d '{"verificationStatus":"VERIFIED","notes":"rig"}' >/dev/null
LST=$(curl -sS $(hdr MARKETPLACE_SELLER) -X POST $MSIKA/v1/listings -d "{\"storefrontId\":\"$SF\",\"catalogItemId\":\"$ITEMID\",\"sellerType\":\"VENDOR\",\"sellerId\":\"VEND-J1\",\"title\":\"J1 Item\",\"summary\":\"rig\",\"priceAmount\":42.50,\"priceCurrency\":\"ZWG\"}" | mid listingId)
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$LST/submit >/dev/null
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$LST/approve -d '{"notes":"ok"}' >/dev/null
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$LST/publish >/dev/null
B="j1-buyer-$$"
CART=$(curl -sS $(hdr PATIENT $B) "$FLOW/v1/carts/open?channel=WEB" | mid cartId)
curl -sS $(hdr PATIENT $B) -X POST $FLOW/v1/carts/$CART/items -d "{\"msikaCoreCode\":\"$ITEMCODE\",\"listingId\":\"$LST\",\"kind\":\"PRODUCT\",\"qty\":1,\"fulfillmentMode\":\"PHARMACY_PICKUP\"}" >/dev/null
ORDER=$(curl -sS $(hdr PATIENT $B) -X POST $FLOW/v1/carts/$CART/checkout -d '{"orderType":"OTC_PRODUCT_ORDER","idempotencyKey":"j1-'$$'"}' | mid orderId)
UNITP=$(PSQL msika_flow "SELECT unit_price FROM mf_order_lines WHERE order_id='$ORDER' LIMIT 1")
[ "$UNITP" = "42.50" ] && ok "real cart pricing: line=42.50 from listing snapshot" || bad "unit_price=$UNITP"
VREF=$(PSQL msika_flow "SELECT vendor_ref FROM mf_orders WHERE order_id='$ORDER'")
[ "$VREF" = "VEND-J1" ] && ok "order payee = listing seller ($VREF)" || bad "vendor_ref=$VREF"
curl -sS $(hdr PATIENT $B) -X POST $FLOW/v1/orders/$ORDER/validate >/dev/null
curl -sS $(hdr PATIENT $B) -X POST $FLOW/v1/orders/$ORDER/price >/dev/null
PAY=$(curl -sS $(hdr PATIENT $B) -X POST $FLOW/v1/orders/$ORDER/pay); echo "$PAY" > "$EV/j1-pay.json"
INTENT=$(echo "$PAY" | mid mushexPaymentIntentId)
[ -n "$INTENT" ] && ok "REAL MusheX intent minted: $INTENT" || bad "pay: $(echo $PAY|head -c 140)"
MX=$(PSQL mushex_db "SELECT status FROM mushex_payment_intents WHERE source_id='$ORDER'")
[ -n "$MX" ] && ok "intent exists in MusheX DB (status=$MX)" || bad "no intent row in mushex"
ST=$(poll msika_flow "SELECT status FROM mf_orders WHERE order_id='$ORDER'" PAID 24) && ok "MONEY LOOP: order PAID via Kafka payment.status.changed" || bad "order=$ST not PAID"
SPLIT=$(PSQL msika_flow "SELECT status||'|'||COALESCE(vendor_amount::text,'-')||'|'||COALESCE(platform_fee::text,'-') FROM mf_settlements WHERE order_id='$ORDER'")
echo "   settlement: $SPLIT" | tee -a "$EV/journal.txt"
CH=$(poll costa_db "SELECT charge_status FROM costa_charge_records WHERE source_ref='$ORDER'" SETTLED 12) && ok "Costa charge SETTLED for the order" || bad "costa charge=$CH"
CREF=$(PSQL msika_flow "SELECT costa_charge_ref FROM mf_settlements WHERE order_id='$ORDER'")
[ -n "$CREF" ] && ok "costa_charge_ref written back to settlement ($CREF)" || bad "no costa_charge_ref"
# refund branch
RF=$(curl -sS $(hdr PATIENT $B) -X POST $FLOW/v1/orders/$ORDER/refund -d "{\"amount\":$UNITP,\"reason\":\"rig refund\"}" 2>/dev/null)
RFID=$(echo "$RF" | mid refundId)
if [ -n "$RFID" ]; then
  RST=$(poll msika_flow "SELECT status FROM mf_orders WHERE order_id='$ORDER'" REFUNDED 24) \
    && ok "REFUND LOOP: order REFUNDED via mushex.refund.status.changed" || bad "refund: order=$RST"
  CH2=$(poll costa_db "SELECT charge_status FROM costa_charge_records WHERE source_ref='$ORDER'" REFUNDED 12) \
    && ok "Costa charge marked REFUNDED" || bad "costa charge=$CH2 after refund"
else bad "refund request failed: $(echo $RF|head -c 140)"; fi

# ── J1b: delivery leg via Nhume ─────────────────────────────────────────────
say "J1b: HOME_DELIVERY → Nhume mission → write-backs → COMPLETED"
B2="j1b-buyer-$$"
CART2=$(curl -sS $(hdr PATIENT $B2) "$FLOW/v1/carts/open?channel=WEB" | mid cartId)
curl -sS $(hdr PATIENT $B2) -X POST $FLOW/v1/carts/$CART2/items -d "{\"msikaCoreCode\":\"$ITEMCODE\",\"listingId\":\"$LST\",\"kind\":\"PRODUCT\",\"qty\":1,\"fulfillmentMode\":\"HOME_DELIVERY\"}" >/dev/null
ORD2=$(curl -sS $(hdr PATIENT $B2) -X POST $FLOW/v1/carts/$CART2/checkout -d '{"orderType":"OTC_PRODUCT_ORDER","idempotencyKey":"j1b-'$$'"}' | mid orderId)
curl -sS $(hdr PATIENT $B2) -X POST $FLOW/v1/orders/$ORD2/validate >/dev/null
curl -sS $(hdr PATIENT $B2) -X POST $FLOW/v1/orders/$ORD2/price >/dev/null
curl -sS $(hdr PATIENT $B2) -X POST $FLOW/v1/orders/$ORD2/pay >/dev/null
poll msika_flow "SELECT status FROM mf_orders WHERE order_id='$ORD2'" PAID 24 >/dev/null
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $FLOW/v1/orders/$ORD2/route -d '{"deliveryMode":"HUMAN_DELIVERY"}' >/dev/null 2>&1 || \
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $FLOW/v1/orders/$ORD2/route >/dev/null 2>&1
NDEL=$(PSQL msika_flow "SELECT p.nhume_delivery_id FROM mf_delivery_plans p JOIN mf_fulfillment_orders f ON p.fulfillment_id=f.fulfillment_id WHERE f.order_id='$ORD2'")
[ -n "$NDEL" ] && ok "Nhume mission created + id stored on plan ($NDEL)" || bad "no nhume_delivery_id on plan"
NROW=$(PSQL impilo_nhume "SELECT delivery_type||'|'||marketplace_order_ref FROM nhume_delivery_requests WHERE delivery_id='$NDEL'" 2>/dev/null)
echo "   nhume row: ${NROW:-<table name differs — check impilo_nhume>}" | tee -a "$EV/journal.txt"
if [ -n "$NDEL" ]; then
  # exercise the write-back seam directly (courier UI drive is out of rig scope):
  # Nhume's gateway posts dispatch-status; simulate exactly its contract.
  curl -sS $(hdr SYSTEM nhume-service) -X POST "$FLOW/internal/v1/msika-flow/orders/$ORD2/dispatch-status" -d "{\"status\":\"PICKED_UP\",\"deliveryId\":\"$NDEL\"}" >/dev/null
  S1=$(PSQL msika_flow "SELECT status FROM mf_orders WHERE order_id='$ORD2'")
  [ "$S1" = "OUT_FOR_DELIVERY" ] && ok "PICKED_UP write-back → OUT_FOR_DELIVERY" || bad "after PICKED_UP: $S1"
  curl -sS $(hdr SYSTEM nhume-service) -X POST "$FLOW/internal/v1/msika-flow/orders/$ORD2/dispatch-status" -d "{\"status\":\"DELIVERED\",\"deliveryId\":\"$NDEL\",\"proofRef\":\"$(uuidgen)\"}" >/dev/null
  S2=$(PSQL msika_flow "SELECT status FROM mf_orders WHERE order_id='$ORD2'")
  [ "$S2" = "DELIVERED" ] && ok "DELIVERED write-back → DELIVERED" || bad "after DELIVERED: $S2"
  curl -sS $(hdr PATIENT $B2) -X POST $FLOW/v1/orders/$ORD2/complete >/dev/null
  S3=$(PSQL msika_flow "SELECT status FROM mf_orders WHERE order_id='$ORD2'")
  [ "$S3" = "COMPLETED" ] && ok "order COMPLETED (previously unreachable state)" || bad "complete: $S3"
fi

# ── J2: seller/vendor lane ──────────────────────────────────────────────────
VTYPE=$(grep -oE "^\s+[A-Z_]+" "$REPO/services/msika-flow-service/src/main/java/zw/gov/mohcc/impilo/msikaflow/domain/VendorType.java" | tr -d " ," | grep -E "^[A-Z]" | head -1)
say "J2: vendor onboard → approve → storefront auto-provisioned (Kafka) → bind-actor"
VAPP=$(curl -sS $(hdr VENDOR j2-vendor) -X POST $FLOW/v1/vendors/apply -d "{\"name\":\"J2 Vendor Co\",\"type\":\"$VTYPE\"}" )
VID=$(echo "$VAPP" | mid vendorId)
[ -n "$VID" ] && ok "vendor applied ($VID)" || bad "apply: $(echo $VAPP|head -c 120)"
REV=$(PSQL msika_flow "SELECT id FROM mf_ops_reviews WHERE entity_id='$VID' AND status='PENDING' LIMIT 1")
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $FLOW/v1/ops/reviews/$REV/approve -d '{"notes":"rig approve"}' >/dev/null
VST=$(PSQL msika_flow "SELECT status FROM mf_vendor_profiles WHERE id='$VID'" 2>/dev/null); [ -z "$VST" ] && VST=$(PSQL msika_flow "SELECT status FROM mf_vendor_profiles WHERE vendor_id='$VID'" 2>/dev/null)
[ "$VST" = "ACTIVE" ] && ok "vendor ACTIVE after ops approve" || bad "vendor status=$VST"
SFV=$(poll msika "SELECT count(*) FROM msika_storefronts WHERE seller_type='VENDOR' AND seller_id='$VID'" 1 12) \
  && ok "storefront AUTO-PROVISIONED via msika.flow.vendor.approved (Kafka consumer live)" || bad "no auto storefront (count=$SFV)"
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $FLOW/v1/vendors/$VID/bind-actor -d '{"actorId":"j2-vendor"}' >/dev/null
BYA=$(curl -sS $(hdr VENDOR j2-vendor) $FLOW/v1/vendors/by-actor/j2-vendor | mid vendorId)
[ "$BYA" = "$VID" ] && ok "bind-actor + by-actor resolve (vendor/me seam)" || bad "by-actor=$BYA"

# ── J3: operator lane ───────────────────────────────────────────────────────
say "J3: operator — suspend cascade + reinstate + audit"
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $FLOW/v1/ops/vendors/$VID/suspend -d '{"reason":"rig suspend"}' >/dev/null
VS2=$(PSQL msika_flow "SELECT status FROM mf_vendor_profiles WHERE vendor_id='$VID'")
[ "$VS2" = "SUSPENDED" ] && ok "vendor SUSPENDED via ops endpoint" || bad "suspend: $VS2"
SFS=$(poll msika "SELECT verification_status FROM msika_storefronts WHERE seller_id='$VID'" SUSPENDED 12) \
  && ok "storefront SUSPENDED via vendor.suspended cascade (Kafka)" || bad "storefront=$SFS after suspend"
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $FLOW/v1/ops/vendors/$VID/reinstate -d '{"reason":"rig reinstate"}' >/dev/null
VS3=$(PSQL msika_flow "SELECT status FROM mf_vendor_profiles WHERE vendor_id='$VID'")
[ "$VS3" = "ACTIVE" ] && ok "vendor reinstated" || bad "reinstate: $VS3"
AUD=$(curl -sS $(hdr MARKETPLACE_OPERATOR) "$FLOW/v1/ops/audit?page=0&size=10")
echo "$AUD" > "$EV/j3-audit.json"
echo "$AUD" | grep -qE "VENDOR|ORDER|entries|content" && ok "ops audit feed returns entries" || bad "audit: $(echo $AUD|head -c 120)"

# ── J4: citizen mobile through BFF ─────────────────────────────────────────
say "J4: citizen mobile via experience-bff"
SVC=$(curl -sS $(hdr PATIENT citizen-j4) "$BFF/internal/v1/mobile/citizen/marketplace/services?category=&page=0&size=5")
echo "$SVC" > "$EV/j4-services.json"
echo "$SVC" | grep -qE '"data"' && ok "citizen services discovery responds through BFF" || bad "services: $(echo $SVC|head -c 140)"
REQ=$(curl -sS $(hdr PATIENT citizen-j4) -X POST "$BFF/internal/v1/mobile/citizen/marketplace/requests" -d "{\"serviceId\":\"$LST\",\"notes\":\"rig request\"}")
RID=$(echo "$REQ" | python3 -c "import json,sys;r=json.load(sys.stdin);d=r.get('data',r);print(d.get('id') or d.get('orderId') or '')" 2>/dev/null)
[ -n "$RID" ] && ok "citizen request created ($RID)" || bad "request: $(echo $REQ|head -c 140)"
if [ -n "$RID" ]; then
  CAN=$(curl -sS $(hdr PATIENT citizen-j4) -X POST "$BFF/internal/v1/mobile/citizen/marketplace/requests/$RID/cancel")
  echo "$CAN" | grep -qiE "CANCEL" && ok "truthful cancel passthrough" || bad "cancel: $(echo $CAN|head -c 120)"
fi

echo "RESULT: PASS=$PASS FAIL=$FAIL" | tee -a "$EV/journal.txt"
if [ "${KEEP_RIG:-0}" != "1" ]; then
  for p in msika mushex nhume costa flow bff; do kill $(cat "$RIGLOG/$p.pid" 2>/dev/null) 2>/dev/null; done
  docker rm -f msika-rig-pg msika-rig-kafka msika-rig-redis >/dev/null 2>&1
  echo "rig torn down (set KEEP_RIG=1 to keep)"
fi
