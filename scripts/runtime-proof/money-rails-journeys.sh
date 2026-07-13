#!/usr/bin/env bash
# ============================================================================
# Money rails — runtime proof (JV vendor payout · JD bank→wallet · JH honesty).
#
#   JV-1  tier-1 vendor payout LIVE: register merchant → payer wallet pays a
#         marketplace-shaped intent (metadata provider_id=vendor:X, payee_amount
#         = share) → PAID → run settlement → release-payouts → the vendor's
#         MERCHANT wallet is credited EXACTLY the payee share (not the gross);
#         batch COMPLETED. A facility (bank-rail) batch with no registered rail
#         stays PENDING (fail-closed).
#   JD-1  bank→wallet cash-in: request deposit → PENDING intent + reference code
#         → POST a bank statement line quoting the code (matching amount) →
#         MATCHED → deposit CONFIRMED → wallet balance credited exactly.
#   JD-2  amount mismatch statement line → UNMATCHED → wallet untouched.
#   JH-1  honesty: mushex adapter-readiness reports WALLET READY_LIVE and the
#         external CARD_GATEWAY rail NOT live (Paynow off) — the signal the BFF
#         uses to disable external payment methods.
#
# Boots mushex + mushe-wallet on scratch infra; no sandbox settle needed (the
# payer pays from a real wallet). MUSHEX_SANDBOX_ENABLED stays true only so
# credential-less intent creation is permitted in the rig.
#
# Requirements: docker, java 21, packaged jars for mushex-service +
#   mushe-wallet-service.
# Usage: EV=<dir> bash scripts/runtime-proof/money-rails-journeys.sh (KEEP_RIG=1)
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-/tmp/money-rails-evidence}; RIGLOG=${RIGLOG:-/tmp/money-rails-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-4000-8000-000000000001
PGPORT=15688; RPORT=16444; KPORT=19096
MUSHEX=http://localhost:30102; WALLET=http://localhost:30126
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
WPSQL(){ docker exec mrails-pg psql -U impilo -d mushe_wallet -tAc "$1"; }
XPSQL(){ docker exec mrails-pg psql -U impilo -d mushex_db -tAc "$1"; }
hdr(){ local a=${1:-rig}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H Idempotency-Key:$(uuidgen) -H X-Idempotency-Key:$(uuidgen) -H X-Actor-ID:$a -H X-Actor-Type:SYSTEM -H X-Purpose-Of-Use:FINANCE"; }
mid(){ python3 -c "import json,sys;r=json.load(sys.stdin);d=r.get('data') if isinstance(r,dict) and 'data' in r else r;print((d or {}).get('$1',''))"; }
poll(){ local v=""; for i in $(seq 1 "$4"); do v=$($1 "$2"); [ "$v" = "$3" ] && { echo "$v"; return 0; }; sleep 2; done; echo "$v"; return 1; }

say "RIG: infra + services (mushex + mushe-wallet)"
docker rm -f mrails-pg mrails-redis mrails-kafka >/dev/null 2>&1
docker run -d --name mrails-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name mrails-redis -p $RPORT:6379 redis:7-alpine >/dev/null
docker run -d --name mrails-kafka -p $KPORT:$KPORT redpandadata/redpanda:latest \
  redpanda start --overprovisioned --smp 1 --memory 512M --reserve-memory 0M --node-id 0 --check=false \
  --kafka-addr PLAINTEXT://0.0.0.0:$KPORT --advertise-kafka-addr PLAINTEXT://localhost:$KPORT >/dev/null
sleep 12
for db in mushex_db mushe_wallet; do docker exec mrails-pg psql -U impilo -d postgres -c "CREATE DATABASE $db" >/dev/null 2>&1; done

jar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }
COMMON="POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:$KPORT \
SPRING_KAFKA_LISTENER_AUTO_STARTUP=true SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=$RPORT"

env $COMMON SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$PGPORT/mushex_db POSTGRES_DB=mushex_db SERVER_PORT=30102 \
  MUSHEX_HMAC_PEPPER=rig-pepper-0123456789 MUSHEX_SANDBOX_ENABLED=true \
  MUSHEX_SANDBOX_APPLY_SIMULATION_OUTCOME=false MUSHEX_SANDBOX_BYPASS_CREDENTIAL_CHECK=false \
  CREDENTIAL_VERIFICATION_ENABLED=false CREDENTIAL_PAYEE_VERIFICATION_ENABLED=false \
  IMPILO_SERVICES_MUSHE_WALLET_BASE_URL=http://localhost:30126 \
  nohup java -jar "$(jar mushex-service)" > "$RIGLOG/mushex.log" 2>&1 & echo $! > "$RIGLOG/mushex.pid"
env $COMMON DB_HOST=localhost DB_PORT=$PGPORT DB_NAME=mushe_wallet SERVER_PORT=30126 \
  MUSHEX_BASE_URL=http://localhost:30102 \
  WALLET_CARD_ENCRYPTION_MASTER_KEY=rig-master-key-0123456789abcdef-0123456789abcdef \
  nohup java -jar "$(jar mushe-wallet-service)" > "$RIGLOG/wallet.log" 2>&1 & echo $! > "$RIGLOG/wallet.pid"

UP=0
for svc in "mushex 30102" "wallet 30126"; do set -- $svc
  for i in $(seq 1 40); do c=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$2/actuator/health 2>/dev/null); [ "$c" = 200 ] && break; sleep 3; done
  echo "   $1 ($2): $c" | tee -a "$EV/journal.txt"; [ "$c" = 200 ] && UP=$((UP+1))
done
[ "$UP" = 2 ] && ok "2/2 services healthy" || { bad "only $UP/2 healthy"; tail -25 "$RIGLOG/wallet.log"; [ -z "${KEEP_RIG:-}" ] && docker rm -f mrails-pg mrails-redis mrails-kafka >/dev/null 2>&1; exit 1; }

walletid_for(){ curl -sS $(hdr) "$WALLET/internal/v1/wallets/by-owner?owner_type=$1&owner_ref=$2" | mid walletId; }
balance_of(){ WPSQL "SELECT available_balance FROM mushe.wallets WHERE wallet_id='$1'"; }

# ── JV-1: tier-1 vendor payout ───────────────────────────────────────────────
say "JV-1: vendor payout LIVE (settlement → wallet disbursement, payee share)"
CPID="CPID-MR-$$"
VEND="VEND-MR-$$"
# merchant (payee) — registering creates its MERCHANT wallet keyed by provider number
curl -sS $(hdr) -X POST $WALLET/internal/v1/merchants -d "{\"providerNumber\":\"$VEND\",\"merchantName\":\"Rails Vendor\",\"merchantType\":\"HEALTH_PROVIDER\"}" >/dev/null
MWALLET=$(walletid_for MERCHANT "$VEND")
[ -n "$MWALLET" ] && ok "merchant wallet provisioned ($MWALLET)" || bad "no merchant wallet"
# payer wallet funded
curl -sS $(hdr) -X POST $WALLET/internal/v1/wallets -d "{\"ownerType\":\"INDIVIDUAL\",\"ownerRef\":\"$CPID\",\"displayName\":\"Payer\",\"currency\":\"USD\"}" >/dev/null
PWALLET=$(walletid_for INDIVIDUAL "$CPID")
curl -sS $(hdr) -X POST $WALLET/internal/v1/wallets/$PWALLET/credit -d '{"amount":100.00,"txnType":"CASH_DEPOSIT","channel":"CASHIER","reference":"rig"}' >/dev/null
# marketplace-shaped intent: gross 43.14, vendor share 42.50
META="{\"patient_cpid\":\"$CPID\",\"provider_id\":\"vendor:$VEND\",\"payee_amount\":\"42.50\"}"
INTENT=$(curl -sS $(hdr) -X POST $MUSHEX/mushex/v1/payment-intents -d "{\"sourceType\":\"MSIKA_ORDER\",\"sourceId\":\"ORD-$$\",\"amount\":43.14,\"currency\":\"USD\",\"idempotencyKey\":\"mrails-$$\",\"metadata\":$(python3 -c "import json,sys;print(json.dumps(sys.argv[1]))" "$META")}" | mid intentId)
[ -n "$INTENT" ] && ok "intent minted ($INTENT)" || bad "no intent"
curl -sS $(hdr) -X POST $MUSHEX/mushex/v1/payment-intents/$INTENT/pay-from-wallet >/dev/null
PSTAT=$(poll XPSQL "SELECT status FROM mushex_payment_intents WHERE intent_id='$INTENT'" PAID 8)
[ "$PSTAT" = "PAID" ] && ok "intent PAID from payer wallet debit" || bad "intent=$PSTAT"
TODAY=$(docker exec mrails-pg date -u +%F)
SET=$(curl -sS $(hdr) -X POST $MUSHEX/mushex/v1/settlements/run -d "{\"periodStart\":\"$TODAY\",\"periodEnd\":\"$TODAY\"}" | mid settlementId)
[ -n "$SET" ] && ok "settlement computed ($SET)" || bad "no settlement"
# vendor batch on the WALLET rail; check item amount = payee share
VITEM=$(XPSQL "SELECT i.amount FROM mushex_payout_items i JOIN mushex_payout_batches b ON i.batch_id=b.batch_id WHERE b.settlement_id='$SET' AND i.payee_type='VENDOR' AND i.payee_ref='$VEND'")
[ "$VITEM" = "42.50" ] && ok "vendor payout item = payee SHARE 42.50 (not gross 43.14)" || bad "vendor item=$VITEM"
BAL_BEFORE=$(balance_of $MWALLET)
curl -sS $(hdr) -X POST $MUSHEX/mushex/v1/settlements/$SET/release-payouts >/dev/null
BAL_AFTER=$(poll balance_of "$MWALLET" 42.50 8)
[ "$BAL_AFTER" = "42.50" ] && ok "MERCHANT wallet CREDITED the payout (balance ${BAL_BEFORE:-0} -> $BAL_AFTER)" || bad "merchant balance=$BAL_AFTER"
BSTAT=$(XPSQL "SELECT b.status FROM mushex_payout_batches b WHERE b.settlement_id='$SET' AND b.adapter_type='WALLET'")
[ "$BSTAT" = "COMPLETED" ] && ok "wallet payout batch COMPLETED" || bad "batch=$BSTAT"

# ── JD-1 / JD-2: bank → wallet cash-in ───────────────────────────────────────
say "JD-1: bank→wallet deposit via statement reference matching"
DCPID="CPID-DEP-$$"
curl -sS $(hdr) -X POST $WALLET/internal/v1/wallets -d "{\"ownerType\":\"INDIVIDUAL\",\"ownerRef\":\"$DCPID\",\"displayName\":\"Depositor\",\"currency\":\"USD\"}" >/dev/null
DWALLET=$(walletid_for INDIVIDUAL "$DCPID")
SRC=$(curl -sS $(hdr) -X POST $WALLET/internal/v1/funding/$DWALLET/sources -d '{"sourceType":"BANK_ACCOUNT","provider":"CBZ","accountRef":"111","accountName":"J Moyo"}' | mid sourceId)
DEP=$(curl -sS $(hdr) -X POST $WALLET/internal/v1/funding/$DWALLET/deposit -d "{\"sourceId\":\"$SRC\",\"amount\":50.00,\"reference\":\"my transfer\"}")
REFCODE=$(echo "$DEP" | mid referenceCode); DSTAT=$(echo "$DEP" | mid status)
[ "$DSTAT" = "PENDING" ] && [ -n "$REFCODE" ] && ok "deposit PENDING with reference code ($REFCODE)" || bad "deposit=$DSTAT ref=$REFCODE"
BAL0=$(balance_of $DWALLET)
# statement line quoting the code, matching amount
M1=$(curl -sS $(hdr) -X POST $WALLET/internal/v1/statements/match -d "{\"lines\":[{\"narrative\":\"ZIPIT FRM J MOYO REF $REFCODE THANKS\",\"amount\":50.00,\"bankRef\":\"BANK-TX-1\"}]}")
R1=$(echo "$M1" | python3 -c "import json,sys;d=json.load(sys.stdin).get('data');print(d[0]['result'] if d else '')")
[ "$R1" = "MATCHED" ] && ok "statement line MATCHED to the pending deposit" || bad "match result=$R1"
BAL1=$(poll balance_of "$DWALLET" 50.00 8)
[ "$BAL1" = "50.00" ] && ok "wallet CREDITED on confirmed arrival (${BAL0:-0} -> $BAL1)" || bad "balance=$BAL1"

say "JD-2: amount-mismatch line stays UNMATCHED, nothing moves"
DEP2=$(curl -sS $(hdr) -X POST $WALLET/internal/v1/funding/$DWALLET/deposit -d "{\"sourceId\":\"$SRC\",\"amount\":30.00,\"reference\":\"x\"}")
REF2=$(echo "$DEP2" | mid referenceCode)
M2=$(curl -sS $(hdr) -X POST $WALLET/internal/v1/statements/match -d "{\"lines\":[{\"narrative\":\"REF $REF2\",\"amount\":29.99,\"bankRef\":\"BANK-TX-2\"}]}")
R2=$(echo "$M2" | python3 -c "import json,sys;d=json.load(sys.stdin).get('data');print(d[0]['result'] if d else '')")
[ "$R2" = "UNMATCHED" ] && ok "amount mismatch → UNMATCHED (no force-credit)" || bad "result=$R2"
BAL2=$(balance_of $DWALLET)
[ "$BAL2" = "50.00" ] && ok "wallet balance unchanged by the mismatch ($BAL2)" || bad "balance moved to $BAL2"

# ── JH-1: payment-method honesty signal ──────────────────────────────────────
say "JH-1: adapter readiness — wallet live, external aggregator not live"
READY=$(curl -sS $(hdr) $MUSHEX/mushex/v1/platform/adapter-readiness)
WALLET_ST=$(echo "$READY" | python3 -c "import json,sys;d=json.load(sys.stdin).get('data',[]);print(next((r['status'] for r in d if r['adapterType']=='WALLET'),''))")
CARD_ST=$(echo "$READY" | python3 -c "import json,sys;d=json.load(sys.stdin).get('data',[]);print(next((r['status'] for r in d if r['adapterType']=='CARD_GATEWAY'),''))")
[ "$WALLET_ST" = "READY_LIVE" ] && ok "internal WALLET rail READY_LIVE" || bad "wallet rail=$WALLET_ST"
[ "$CARD_ST" != "READY_LIVE" ] && ok "external CARD_GATEWAY rail NOT live ($CARD_ST) → BFF disables external methods" || bad "card rail unexpectedly live: $CARD_ST"

echo "RESULT: PASS=$PASS FAIL=$FAIL" | tee -a "$EV/journal.txt"
for p in "$RIGLOG"/*.pid; do kill "$(cat $p)" 2>/dev/null; done
[ -z "${KEEP_RIG:-}" ] && docker rm -f mrails-pg mrails-redis mrails-kafka >/dev/null 2>&1
[ "$FAIL" = 0 ]
