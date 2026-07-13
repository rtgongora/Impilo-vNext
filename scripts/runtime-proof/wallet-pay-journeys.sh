#!/usr/bin/env bash
# ============================================================================
# Citizen wallet pay-confirm seam — runtime proof (JW-1..3).
#
# THE POINT: settle a marketplace order from a REAL wallet debit with
# MUSHEX_SANDBOX_APPLY_SIMULATION_OUTCOME=false — the intent must NOT
# auto-settle at creation; it reaches PAID only because MusheX debited the
# citizen's CPID-keyed Mushe wallet through the new pay-from-wallet leg
# (the M10 open production seam, closed in Stage 1).
#
#   JW-1  real money lane: listing → cart → checkout → price → pay (intent
#         minted, status CREATED — asserted NOT auto-settled) → wallet created
#         + credited → BFF /internal/v1/wallet/pay (reference = intent) →
#         mushex debits wallet → intent PAID → Kafka → order PAID → COSTA
#         charge SETTLED → wallet balance reduced by exactly the order total
#   JW-2  insufficient balance → pay fails clean, intent NOT paid, order not
#         PAID, wallet balance unchanged
#   JW-3  idempotent double-confirm → second confirm succeeds, wallet debited
#         exactly once
#
# Honesty note on flags: APPLY_SIMULATION_OUTCOME=false and BYPASS_CREDENTIAL_
# CHECK=false (no fabricated settlement, no metadata escape hatch;
# MSIKA_FLOW_PAYMENTS_SIMULATION_METADATA=false). MUSHEX_SANDBOX_ENABLED=true
# and CREDENTIAL_PAYEE_VERIFICATION_ENABLED=false remain ONLY because the
# credential-verification sidecar (8094) is not part of this rig and mushex
# fail-closes intent creation without it; neither flag can settle an intent.
#
# Requirements: docker, java 21, packaged jars for: msika-service
#   msika-flow-service mushex-service mushe-wallet-service
#   costing-engine-service experience-bff
# Usage: EV=<dir> bash scripts/runtime-proof/wallet-pay-journeys.sh (KEEP_RIG=1 to inspect)
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-/tmp/wallet-pay-evidence}; RIGLOG=${RIGLOG:-/tmp/wallet-pay-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-4000-8000-000000000001
PGPORT=15677; KPORT=19094; RPORT=16433
MSIKA=http://localhost:29086; FLOW=http://localhost:29100; MUSHEX=http://localhost:29102
WALLET=http://localhost:29126; COSTA=http://localhost:29101; BFF=http://localhost:29160
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
PSQL(){ docker exec wpay-rig-pg psql -U impilo -d "$1" -tAc "$2"; }
hdr(){ local r=$1 a=${2:-rig-$1}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Idempotency-Key:$(uuidgen) -H Idempotency-Key:$(uuidgen) -H X-Actor-ID:$a -H X-Actor-Type:$r -H X-Purpose-Of-Use:MARKETPLACE"; }
mid(){ python3 -c "import json,sys;r=json.load(sys.stdin);d=r.get('data') if isinstance(r,dict) and 'data' in r else r;print((d or {}).get('$1') or (d or {}).get('id') or '')"; }
poll(){ local v=""; for i in $(seq 1 "$4"); do v=$(PSQL "$1" "$2"); [ "$v" = "$3" ] && { echo "$v"; return 0; }; sleep 5; done; echo "$v"; return 1; }

say "RIG: infra + services (msika + flow + mushex + mushe-wallet + costa + bff)"
docker rm -f wpay-rig-pg wpay-rig-kafka wpay-rig-redis >/dev/null 2>&1
docker run -d --name wpay-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name wpay-rig-kafka -p $KPORT:$KPORT redpandadata/redpanda:latest \
  redpanda start --overprovisioned --smp 1 --memory 512M --reserve-memory 0M --node-id 0 --check=false \
  --kafka-addr PLAINTEXT://0.0.0.0:$KPORT --advertise-kafka-addr PLAINTEXT://localhost:$KPORT >/dev/null
docker run -d --name wpay-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 12
for db in msika msika_flow mushex_db mushe_wallet costa_db; do
  docker exec wpay-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE $db" >/dev/null 2>&1
done

jar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }
COMMON="POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:$KPORT \
SPRING_KAFKA_LISTENER_AUTO_STARTUP=true SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=$RPORT"

env $COMMON POSTGRES_DB=msika SERVER_PORT=29086 \
  nohup java -jar "$(jar msika-service)" > "$RIGLOG/msika.log" 2>&1 & echo $! > "$RIGLOG/msika.pid"
# PRODUCTION settlement posture: NO auto-settle, NO credential bypass.
env $COMMON SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$PGPORT/mushex_db SERVER_PORT=29102 \
  MUSHEX_HMAC_PEPPER=rig-pepper-0123456789 \
  MUSHEX_SANDBOX_ENABLED=true \
  MUSHEX_SANDBOX_APPLY_SIMULATION_OUTCOME=false \
  MUSHEX_SANDBOX_BYPASS_CREDENTIAL_CHECK=false \
  CREDENTIAL_VERIFICATION_ENABLED=false CREDENTIAL_PAYEE_VERIFICATION_ENABLED=false \
  IMPILO_SERVICES_MUSHE_WALLET_BASE_URL=http://localhost:29126 \
  nohup java -jar "$(jar mushex-service)" > "$RIGLOG/mushex.log" 2>&1 & echo $! > "$RIGLOG/mushex.pid"
env $COMMON DB_HOST=localhost DB_PORT=$PGPORT DB_NAME=mushe_wallet SERVER_PORT=29126 \
  MUSHEX_BASE_URL=http://localhost:29102 \
  WALLET_CARD_ENCRYPTION_MASTER_KEY=rig-master-key-0123456789abcdef-0123456789abcdef \
  nohup java -jar "$(jar mushe-wallet-service)" > "$RIGLOG/wallet.log" 2>&1 & echo $! > "$RIGLOG/wallet.pid"
env $COMMON SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$PGPORT/costa_db SERVER_PORT=29101 \
  nohup java -jar "$(jar costing-engine-service)" > "$RIGLOG/costa.log" 2>&1 & echo $! > "$RIGLOG/costa.pid"
env $COMMON POSTGRES_DB=msika_flow SERVER_PORT=29100 \
  MSIKA_CORE_URL=http://localhost:29086 MSIKA_BASE_URL=http://localhost:29086 MUSHEX_URL=http://localhost:29102 \
  MSIKA_FLOW_PAYMENTS_SIMULATION_METADATA=false \
  nohup java -jar "$(jar msika-flow-service)" > "$RIGLOG/flow.log" 2>&1 & echo $! > "$RIGLOG/flow.pid"
# BFF: rig-only open auth (same mechanism as msika rig; never a deploy config).
env $COMMON SERVER_PORT=29160 \
  MSIKA_BASE_URL=http://localhost:29086 MSIKA_FLOW_BASE_URL=http://localhost:29100 \
  MUSHEX_BASE_URL=http://localhost:29102 MUSHE_WALLET_BASE_URL=http://localhost:29126 \
  SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration \
  IMPILO_SECURITY_ALLOW_ANONYMOUS=true \
  nohup java -jar "$(jar experience-bff)" > "$RIGLOG/bff.log" 2>&1 & echo $! > "$RIGLOG/bff.pid"

sleep 60
UP=0
for svc in "msika 29086" "flow 29100" "mushex 29102" "wallet 29126" "costa 29101" "bff 29160"; do
  set -- $svc
  for i in $(seq 1 12); do c=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$2/actuator/health 2>/dev/null); [ "$c" = 200 ] && break; sleep 5; done
  echo "   $1 ($2): $c" | tee -a "$EV/journal.txt"; [ "$c" = 200 ] && UP=$((UP+1))
done
[ "$UP" = 6 ] && ok "6/6 services healthy" || { bad "only $UP/6 healthy"; [ -z "${KEEP_RIG:-}" ] && docker rm -f wpay-rig-pg wpay-rig-kafka wpay-rig-redis >/dev/null 2>&1; exit 1; }

# ── Marketplace fixture: one published listing ───────────────────────────────
say "FIXTURE: published listing"
ITEMCODE="ZW-STARTER-EQP-001"
ITEMID=$(PSQL msika "SELECT item_id FROM msika_catalog_items WHERE canonical_code='$ITEMCODE'")
SF=$(curl -sS $(hdr MARKETPLACE_SELLER) -X POST $MSIKA/v1/storefronts -d '{"sellerType":"VENDOR","sellerId":"VEND-W1","displayName":"Wallet Lane Supplies"}' | mid storefrontId)
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/storefronts/$SF/verify -d '{"verificationStatus":"VERIFIED","notes":"rig"}' >/dev/null
LST=$(curl -sS $(hdr MARKETPLACE_SELLER) -X POST $MSIKA/v1/listings -d "{\"storefrontId\":\"$SF\",\"catalogItemId\":\"$ITEMID\",\"sellerType\":\"VENDOR\",\"sellerId\":\"VEND-W1\",\"title\":\"Wallet Lane Item\",\"summary\":\"rig\",\"priceAmount\":42.50,\"priceCurrency\":\"ZWG\"}" | mid listingId)
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$LST/submit >/dev/null
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$LST/approve -d '{"notes":"ok"}' >/dev/null
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$LST/publish >/dev/null
[ -n "$LST" ] && ok "listing published ($LST)" || bad "no listing"

order_for(){ # $1 buyer — echoes orderId, leaves order priced+intent minted
  local b=$1 cart order
  cart=$(curl -sS $(hdr PATIENT $b) "$FLOW/v1/carts/open?channel=WEB" | mid cartId)
  curl -sS $(hdr PATIENT $b) -X POST $FLOW/v1/carts/$cart/items -d "{\"msikaCoreCode\":\"$ITEMCODE\",\"listingId\":\"$LST\",\"kind\":\"PRODUCT\",\"qty\":1,\"fulfillmentMode\":\"PHARMACY_PICKUP\"}" >/dev/null
  order=$(curl -sS $(hdr PATIENT $b) -X POST $FLOW/v1/carts/$cart/checkout -d "{\"orderType\":\"OTC_PRODUCT_ORDER\",\"idempotencyKey\":\"$b\"}" | mid orderId)
  curl -sS $(hdr PATIENT $b) -X POST $FLOW/v1/orders/$order/validate >/dev/null
  curl -sS $(hdr PATIENT $b) -X POST $FLOW/v1/orders/$order/price >/dev/null
  echo "$order"
}

wallet_for(){ # $1 cpid $2 initial-credit — echoes walletId
  local w
  w=$(curl -sS $(hdr SYSTEM rig-cashier) -X POST $WALLET/internal/v1/wallets -d "{\"ownerType\":\"INDIVIDUAL\",\"ownerRef\":\"$1\",\"displayName\":\"Rig wallet $1\",\"currency\":\"USD\"}" | mid walletId)
  if [ -n "$2" ] && [ "$2" != "0" ]; then
    curl -sS $(hdr SYSTEM rig-cashier) -X POST $WALLET/internal/v1/wallets/$w/credit -d "{\"amount\":$2,\"txnType\":\"CASH_DEPOSIT\",\"channel\":\"CASHIER\",\"reference\":\"rig-float\",\"description\":\"rig float\"}" >/dev/null
  fi
  echo "$w"
}

balance_of(){ PSQL mushe_wallet "SELECT available_balance FROM mushe.wallets WHERE wallet_id='$1'"; }

# ── JW-1: the real-money lane ────────────────────────────────────────────────
say "JW-1: wallet-funded order → intent PAID from REAL debit (no auto-settle)"
B1="jw1-buyer-$$"
O1=$(order_for $B1)
PAY=$(curl -sS $(hdr PATIENT $B1) -X POST $FLOW/v1/orders/$O1/pay); echo "$PAY" > "$EV/jw1-pay.json"
I1=$(echo "$PAY" | mid mushexPaymentIntentId)
[ -n "$I1" ] && ok "REAL MusheX intent minted ($I1)" || bad "no intent: $(echo $PAY|head -c 140)"
sleep 6
MX0=$(PSQL mushex_db "SELECT status FROM mushex_payment_intents WHERE intent_id='$I1'")
[ "$MX0" = "CREATED" ] || [ "$MX0" = "PENDING" ] && ok "NO AUTO-SETTLE: intent still $MX0 after creation (simulation off)" || bad "intent auto-settled?! status=$MX0"
W1=$(wallet_for $B1 120.00)
BAL0=$(balance_of $W1)
[ "$BAL0" = "120.00" ] && ok "citizen wallet funded (balance=$BAL0)" || bad "wallet balance=$BAL0"
CONF=$(curl -sS $(hdr PATIENT $B1) -X POST $BFF/internal/v1/wallet/pay -d "{\"method\":\"MUSHE_WALLET\",\"amount\":42.50,\"reference\":\"$I1\",\"description\":\"order $O1\"}")
echo "$CONF" > "$EV/jw1-confirm.json"
echo "$CONF" | grep -q 'INTENT_WALLET_PAYMENT' && ok "BFF routed confirm through the money SoR (INTENT_WALLET_PAYMENT)" || bad "confirm: $(echo $CONF|head -c 160)"
MX1=$(poll mushex_db "SELECT status FROM mushex_payment_intents WHERE intent_id='$I1'" PAID 6)
[ "$MX1" = "PAID" ] && ok "intent PAID from the REAL wallet debit" || bad "intent=$MX1"
OST=$(poll msika_flow "SELECT status FROM mf_orders WHERE order_id='$O1'" PAID 24)
[ "$OST" = "PAID" ] && ok "order PAID via mushex.payment.status.changed" || bad "order=$OST"
CH=$(poll costa_db "SELECT charge_status FROM costa_charge_records WHERE source_ref='$O1'" SETTLED 12)
[ "$CH" = "SETTLED" ] && ok "COSTA charge SETTLED" || bad "costa charge=$CH"
BAL1=$(balance_of $W1)
DEBITED=$(awk "BEGIN{printf \"%.2f\", 120.00-${BAL1:-0}}")
TOTAL=$(PSQL msika_flow "SELECT amount_total FROM mf_orders WHERE order_id='$O1'")
[ "$DEBITED" = "$TOTAL" ] && ok "wallet debited EXACTLY the order total ($DEBITED of $TOTAL)" || bad "debited=$DEBITED vs total=$TOTAL (bal=$BAL1)"

# ── JW-2: insufficient balance fails clean ───────────────────────────────────
say "JW-2: insufficient balance → fail clean, nothing moves"
B2="jw2-buyer-$$"
O2=$(order_for $B2)
I2=$(curl -sS $(hdr PATIENT $B2) -X POST $FLOW/v1/orders/$O2/pay | mid mushexPaymentIntentId)
W2=$(wallet_for $B2 1.00)
CONF2=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr PATIENT $B2) -X POST $BFF/internal/v1/wallet/pay -d "{\"method\":\"MUSHE_WALLET\",\"amount\":42.50,\"reference\":\"$I2\"}")
[ "$CONF2" != "201" ] && ok "confirm REJECTED on insufficient balance (HTTP $CONF2)" || bad "confirm returned 201 with 1.00 balance"
MX2=$(PSQL mushex_db "SELECT status FROM mushex_payment_intents WHERE intent_id='$I2'")
[ "$MX2" != "PAID" ] && ok "intent NOT paid ($MX2)" || bad "intent PAID without funds"
BAL2=$(balance_of $W2)
[ "$BAL2" = "1.00" ] && ok "wallet balance untouched ($BAL2)" || bad "balance=$BAL2"

# ── JW-3: idempotent double-confirm ──────────────────────────────────────────
say "JW-3: double-confirm debits exactly once"
CONF3=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr PATIENT $B1) -X POST $BFF/internal/v1/wallet/pay -d "{\"method\":\"MUSHE_WALLET\",\"amount\":42.50,\"reference\":\"$I1\"}")
[ "$CONF3" = "201" ] && ok "second confirm idempotent (HTTP $CONF3, already-PAID short-circuit)" || bad "second confirm HTTP $CONF3"
BAL3=$(balance_of $W1)
[ "$BAL3" = "$BAL1" ] && ok "wallet NOT debited twice (balance still $BAL3)" || bad "balance moved: $BAL1 -> $BAL3"

echo "RESULT: PASS=$PASS FAIL=$FAIL" | tee -a "$EV/journal.txt"
for p in "$RIGLOG"/*.pid; do kill "$(cat $p)" 2>/dev/null; done
[ -z "${KEEP_RIG:-}" ] && docker rm -f wpay-rig-pg wpay-rig-kafka wpay-rig-redis >/dev/null 2>&1
[ "$FAIL" = 0 ]
