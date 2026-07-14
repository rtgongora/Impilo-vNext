#!/usr/bin/env bash
# ============================================================================
# Crowdfunding money layer (Lane B part 1) — runtime proof (CFM-1..4).
#
# THE POINT: mushe-wallet owns ALL crowdfunding money. A CAMPAIGN
# bill_contribution_request escrows contributions in a dedicated
# CROWDFUND_ESCROW wallet; donors are REALLY debited (atomic transfer, exactly
# once under an idempotency key); PSP donations only count when the deposit
# intent is CONFIRMED (single guarded credit + exactly-once contribution row);
# releases move escrow → real beneficiary; cancellation refunds newest-first
# with honest UNCLAIMED and REFUND_PENDING (shortfall) legs — the escrow never
# goes negative.
#
#   CFM-1  wallet donation: campaign create (escrow minted) → donor contributes
#          25.00 with a fixed idempotency key, submitted TWICE → donor debited
#          exactly once, escrow credited once, 1 contribution row, and a
#          wallet.bill_contribution.recorded outbox row
#   CFM-2  PSP donation: psp-donate 15.00 → PENDING CROWDFUNDING intent (no
#          credit) → confirm TWICE (callback replay) → single escrow credit,
#          exactly one origin=PSP contribution row, raised total advanced
#   CFM-3  release: second donor chips in 5.00, then release 10.00 → real
#          beneficiary credited, escrow reduced; over-release rejected (422)
#   CFM-4  cancel-and-refund: newest-first — PSP 15.00 (no donor ref) →
#          UNCLAIMED_DONATIONS system wallet; donor2 5.00 → refunded to donor
#          wallet; donor1 25.00 → escrow short (10.00 released) → held
#          REFUND_PENDING; request CANCELLED_REFUNDING; refunded outbox rows
#
# Requirements: docker, java 21, packaged jar for mushe-wallet-service.
# Degrades honestly: missing prerequisites are reported and the rig exits 2
# (nothing ran) rather than pretending.
# Usage: EV=<dir> bash scripts/runtime-proof/crowdfund-money-journeys.sh (KEEP_RIG=1 to inspect)
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-/tmp/crowdfund-money-evidence}; RIGLOG=${RIGLOG:-/tmp/crowdfund-money-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-4000-8000-000000000001
PGPORT=15681; KPORT=19096
WALLET=http://localhost:29127
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
PSQL(){ docker exec cfm-rig-pg psql -U impilo -d mushe_wallet -tAc "$1"; }
hdr(){ local r=${1:-SYSTEM} a=${2:-rig-actor} k=${3:-$(uuidgen)} # $3 = fixed Idempotency-Key for replay tests
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Idempotency-Key:$k -H Idempotency-Key:$k -H X-Actor-ID:$a -H X-Actor-Type:$r -H X-Purpose-Of-Use:SOCIAL_FUNDING"; }
mid(){ python3 -c "import json,sys;r=json.load(sys.stdin);d=r.get('data') if isinstance(r,dict) and 'data' in r else r;print((d or {}).get('$1') or '')"; }

# ── Prerequisites: never fake a rig ─────────────────────────────────────────
MISSING=""
command -v docker >/dev/null 2>&1 || MISSING="$MISSING docker"
command -v java >/dev/null 2>&1 || MISSING="$MISSING java"
command -v python3 >/dev/null 2>&1 || MISSING="$MISSING python3"
JAR=$(ls "$REPO/services/mushe-wallet-service/target/mushe-wallet-service-"*.jar 2>/dev/null | grep -v original | head -1)
[ -n "${JAR:-}" ] || MISSING="$MISSING mushe-wallet-service-jar(mvn -pl mushe-wallet-service package)"
if [ -n "$MISSING" ]; then
  echo "NOT RUN — missing prerequisites:$MISSING" | tee -a "$EV/journal.txt"
  echo "Nothing was executed; no evidence was fabricated." | tee -a "$EV/journal.txt"
  exit 2
fi

say "RIG: scratch postgres + redpanda + mushe-wallet-service"
docker rm -f cfm-rig-pg cfm-rig-kafka >/dev/null 2>&1
docker run -d --name cfm-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo \
  -e POSTGRES_DB=mushe_wallet -p $PGPORT:5432 postgres:16-alpine >/dev/null || { bad "postgres container"; exit 1; }
docker run -d --name cfm-rig-kafka -p $KPORT:$KPORT redpandadata/redpanda:latest \
  redpanda start --overprovisioned --smp 1 --memory 512M --reserve-memory 0M --node-id 0 --check=false \
  --kafka-addr PLAINTEXT://0.0.0.0:$KPORT --advertise-kafka-addr PLAINTEXT://localhost:$KPORT >/dev/null
sleep 10

env POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
  DB_HOST=localhost DB_PORT=$PGPORT DB_NAME=mushe_wallet SERVER_PORT=29127 \
  IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true \
  SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:$KPORT SPRING_KAFKA_LISTENER_AUTO_STARTUP=true \
  MUSHEX_BASE_URL=http://localhost:1 \
  WALLET_CARD_ENCRYPTION_KEY=rig-master-key-0123456789abcdef-0123456789abcdef \
  nohup java -jar "$JAR" > "$RIGLOG/wallet.log" 2>&1 & echo $! > "$RIGLOG/wallet.pid"

C=000
for i in $(seq 1 24); do
  C=$(curl -s -o /dev/null -w '%{http_code}' $WALLET/actuator/health 2>/dev/null); [ "$C" = 200 ] && break; sleep 5
done
[ "$C" = 200 ] && ok "mushe-wallet healthy (29127)" || { bad "wallet not healthy ($C) — see $RIGLOG/wallet.log"; [ -z "${KEEP_RIG:-}" ] && docker rm -f cfm-rig-pg cfm-rig-kafka >/dev/null 2>&1; exit 1; }

wallet_for(){ # $1 ownerRef $2 initial-credit — echoes walletId
  local w
  w=$(curl -sS $(hdr) -X POST $WALLET/internal/v1/wallets -d "{\"ownerType\":\"INDIVIDUAL\",\"ownerRef\":\"$1\",\"displayName\":\"Rig wallet $1\",\"currency\":\"USD\"}" | mid walletId)
  if [ -n "$2" ] && [ "$2" != "0" ]; then
    curl -sS $(hdr) -X POST $WALLET/internal/v1/wallets/$w/credit -d "{\"amount\":$2,\"txnType\":\"CASH_DEPOSIT\",\"channel\":\"CASHIER\",\"reference\":\"rig-float\",\"description\":\"rig float\"}" >/dev/null
  fi
  echo "$w"
}
bal(){ PSQL "SELECT available_balance FROM mushe.wallets WHERE wallet_id='$1'"; }

# ── CFM-1: wallet donation, double-submitted, debits exactly once ────────────
say "CFM-1: campaign escrow + idempotent donor-debit contribution"
CAMPAIGN=$(uuidgen)
BEN=$(wallet_for "cfm-beneficiary-$$" 0)
DON1=$(wallet_for "cfm-donor1-$$" 100.00)
REQ=$(curl -sS $(hdr) -X POST $WALLET/internal/v1/bill-contributions -d "{\"title\":\"Surgery fund\",\"targetAmount\":200.00,\"currency\":\"USD\",\"createdBy\":\"daidzai\",\"origin\":\"CAMPAIGN\",\"campaignRef\":\"$CAMPAIGN\",\"beneficiaryTargetWalletId\":\"$BEN\"}")
echo "$REQ" > "$EV/cfm1-create.json"
REQID=$(echo "$REQ" | mid id); TOKEN=$(echo "$REQ" | mid shareToken); ESCROW=$(echo "$REQ" | mid beneficiaryWalletId)
[ -n "$REQID" ] && [ -n "$TOKEN" ] && ok "campaign request created ($REQID)" || bad "create failed: $(echo $REQ|head -c 160)"
ETYPE=$(PSQL "SELECT owner_type FROM mushe.wallets WHERE wallet_id='$ESCROW'")
[ "$ETYPE" = "CROWDFUND_ESCROW" ] && ok "escrow wallet minted (CROWDFUND_ESCROW:$CAMPAIGN)" || bad "escrow owner_type=$ETYPE"
IDEM1="cfm1-idem-$$"
for i in 1 2; do
  curl -sS $(hdr PATIENT donor1 "$IDEM1") -X POST $WALLET/internal/v1/bill-contributions/$TOKEN/contribute \
    -d "{\"amount\":25.00,\"contributorRef\":\"cpid-donor1\",\"contributorName\":\"Donor One\",\"contributorWalletId\":\"$DON1\"}" > "$EV/cfm1-contrib-$i.json"
done
B1=$(bal $DON1); E1=$(bal $ESCROW)
[ "$B1" = "75.00" ] && ok "donor debited EXACTLY once on double-submit (balance $B1)" || bad "donor balance=$B1 (want 75.00)"
[ "$E1" = "25.00" ] && ok "escrow credited once ($E1)" || bad "escrow=$E1"
NC=$(PSQL "SELECT count(*) FROM mushe.bill_contributions WHERE request_id='$REQID'")
[ "$NC" = "1" ] && ok "exactly 1 contribution row" || bad "contribution rows=$NC"
NE=$(PSQL "SELECT count(*) FROM mushe.event_outbox WHERE event_type='wallet.bill_contribution.recorded'")
[ "$NE" = "1" ] && ok "recorded outbox event written" || bad "recorded outbox rows=$NE"

# ── CFM-2: PSP donation settles once via deposit-intent confirm ──────────────
say "CFM-2: PSP donation — PENDING intent, double-confirm credits once"
PSP=$(curl -sS $(hdr) -X POST $WALLET/internal/v1/bill-contributions/$TOKEN/psp-donate \
  -d '{"amount":15.00,"currency":"USD","channel":"MOBILE_MONEY","contributorName":"Gogo","message":"Makorokoto","isAnonymous":true}')
echo "$PSP" > "$EV/cfm2-psp-donate.json"
DEP=$(echo "$PSP" | mid depositId); REFCODE=$(echo "$PSP" | mid referenceCode)
[ -n "$REFCODE" ] && ok "PSP intent minted with reference code $REFCODE" || bad "psp-donate: $(echo $PSP|head -c 160)"
DST=$(PSQL "SELECT status||'|'||purpose||'|'||coalesce(purpose_ref,'') FROM mushe.deposit_intents WHERE deposit_id='$DEP'")
[ "$DST" = "PENDING|CROWDFUNDING|$TOKEN" ] && ok "intent PENDING/CROWDFUNDING against the share token" || bad "intent=$DST"
E2=$(bal $ESCROW)
[ "$E2" = "25.00" ] && ok "no credit before confirmation (escrow still $E2)" || bad "escrow moved early: $E2"
for i in 1 2; do
  curl -sS $(hdr SYSTEM psp-callback) -X POST $WALLET/internal/v1/funding/deposits/$DEP/confirm \
    -d '{"externalRef":"PAYNOW-RIG-1"}' > "$EV/cfm2-confirm-$i.json"
done
E3=$(bal $ESCROW)
[ "$E3" = "40.00" ] && ok "double-confirm credited escrow exactly once ($E3)" || bad "escrow=$E3 (want 40.00)"
NPSP=$(PSQL "SELECT count(*) FROM mushe.bill_contributions WHERE request_id='$REQID' AND origin='PSP'")
[ "$NPSP" = "1" ] && ok "exactly 1 PSP contribution row (idem deposit:$DEP)" || bad "psp rows=$NPSP"
RAISED=$(PSQL "SELECT raised_amount FROM mushe.bill_contribution_requests WHERE id='$REQID'")
[ "$RAISED" = "40.00" ] && ok "raised total advanced to $RAISED" || bad "raised=$RAISED"

# ── CFM-3: release escrow to the real beneficiary ────────────────────────────
say "CFM-3: second donor + partial release to beneficiary; over-release rejected"
DON2=$(wallet_for "cfm-donor2-$$" 20.00)
curl -sS $(hdr PATIENT donor2 "cfm3-idem-$$") -X POST $WALLET/internal/v1/bill-contributions/$TOKEN/contribute \
  -d "{\"amount\":5.00,\"contributorRef\":\"cpid-donor2\",\"contributorName\":\"Donor Two\",\"contributorWalletId\":\"$DON2\"}" >/dev/null
REL=$(curl -sS $(hdr OPERATOR case-officer) -X POST $WALLET/internal/v1/bill-contributions/$REQID/release -d '{"amount":10.00}')
echo "$REL" > "$EV/cfm3-release.json"
BB=$(bal $BEN); E4=$(bal $ESCROW)
[ "$BB" = "10.00" ] && ok "beneficiary received the release ($BB)" || bad "beneficiary=$BB"
[ "$E4" = "35.00" ] && ok "escrow reduced to $E4" || bad "escrow=$E4"
OVER=$(curl -sS -o "$EV/cfm3-overrelease.json" -w '%{http_code}' $(hdr OPERATOR case-officer) -X POST $WALLET/internal/v1/bill-contributions/$REQID/release -d '{"amount":999.00}')
[ "$OVER" = "422" ] && ok "over-release rejected (HTTP $OVER)" || bad "over-release HTTP $OVER"
NREL=$(PSQL "SELECT count(*) FROM mushe.event_outbox WHERE event_type='wallet.bill_contribution.released'")
[ "$NREL" = "1" ] && ok "released outbox event written" || bad "released outbox rows=$NREL"

# ── CFM-4: cancel-and-refund — refunded / unclaimed / shortfall legs ─────────
say "CFM-4: cancel-and-refund drains escrow newest-first, honestly"
CAN=$(curl -sS $(hdr OPERATOR case-officer) -X POST $WALLET/internal/v1/bill-contributions/$REQID/cancel-and-refund)
echo "$CAN" > "$EV/cfm4-cancel.json"
RST=$(PSQL "SELECT status FROM mushe.bill_contribution_requests WHERE id='$REQID'")
[ "$RST" = "CANCELLED_REFUNDING" ] && ok "request CANCELLED_REFUNDING (shortfall remains)" || bad "request status=$RST"
D2=$(bal $DON2)
[ "$D2" = "20.00" ] && ok "donor2 made whole (5.00 back, balance $D2)" || bad "donor2=$D2"
UNC=$(PSQL "SELECT available_balance FROM mushe.wallets WHERE owner_type='SYSTEM_UNCLAIMED' AND owner_ref='UNCLAIMED_DONATIONS'")
[ "$UNC" = "15.00" ] && ok "unresolvable PSP donation parked in UNCLAIMED_DONATIONS ($UNC)" || bad "unclaimed=$UNC"
PEND=$(PSQL "SELECT refund_status FROM mushe.bill_contributions WHERE request_id='$REQID' AND amount=25.00")
[ "$PEND" = "REFUND_PENDING" ] && ok "released-away 25.00 held REFUND_PENDING (no invented money)" || bad "25.00 refund_status=$PEND"
E5=$(bal $ESCROW)
[ "$E5" = "15.00" ] && ok "escrow never negative (remaining $E5 reserved for the pending refund)" || bad "escrow=$E5"
NREF=$(PSQL "SELECT count(*) FROM mushe.event_outbox WHERE event_type='wallet.bill_contribution.refunded'")
[ "$NREF" = "2" ] && ok "refunded outbox events for the 2 moved rows" || bad "refunded outbox rows=$NREF"
D1=$(bal $DON1)
[ "$D1" = "75.00" ] && ok "donor1 untouched while pending ($D1)" || bad "donor1=$D1"

echo "RESULT: PASS=$PASS FAIL=$FAIL" | tee -a "$EV/journal.txt"
kill "$(cat "$RIGLOG/wallet.pid")" 2>/dev/null
[ -z "${KEEP_RIG:-}" ] && docker rm -f cfm-rig-pg cfm-rig-kafka >/dev/null 2>&1
[ "$FAIL" = 0 ]
