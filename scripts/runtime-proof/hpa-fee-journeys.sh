#!/usr/bin/env bash
# ============================================================================
# HPA regulatory fee rails — runtime proof (J-FEE-1..4).
#
# Boots tuso + costing-engine + mushex on scratch Postgres/Redis/Redpanda and
# drives the fee loop end-to-end with curl + psql read-backs:
#   J-FEE-1  configured fee: submit → AWAITING_FEE/DUE → COSTA charge OPEN
#            → fee_reference write-back → payment-intent → sandbox settle →
#            mushex.payment.status.changed → PAID → COSTA charge SETTLED →
#            ready-for-inspection allowed
#   J-FEE-2  gate negative: ready-for-inspection rejected while DUE
#   J-FEE-3  waiver: DUE → WAIVED → gate opens → COSTA charge WAIVED
#   J-FEE-4  honest NOT_CONFIGURED: fee required, no active amount → no charge,
#            no gate, application proceeds
#
# The rig configures a TEST amount through the governed endpoint and marks it
# RIG-TEST-CONFIG — it is NOT an SI 78 value.
#
# Usage: EV=<dir> bash scripts/runtime-proof/hpa-fee-journeys.sh   (KEEP_RIG=1 to inspect)
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-/tmp/hpa-fee-evidence}; RIGLOG=${RIGLOG:-/tmp/hpa-fee-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-4000-8000-000000000001
PGPORT=15655; RPORT=16422; KPORT=19093
TUSO=http://localhost:28084; COSTA=http://localhost:28101; MUSHEX=http://localhost:28102
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
TPSQL(){ docker exec hpa-fee-pg psql -U impilo -d tuso -tAc "$1"; }
CPSQL(){ docker exec hpa-fee-pg psql -U impilo -d costa_db -tAc "$1"; }
hdr(){ local a=${1:-HPA_REGISTRAR}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Actor-ID:rig-$a -H X-Actor-Type:$a -H X-Purpose-Of-Use:REGULATION"; }
mid(){ python3 -c "import json,sys;r=json.load(sys.stdin);d=r.get('data') if isinstance(r,dict) and 'data' in r else r;print((d or {}).get('$1',''))"; }
poll(){ local v=""; for i in $(seq 1 "$4"); do v=$($1 "$2"); [ "$v" = "$3" ] && { echo "$v"; return 0; }; sleep 3; done; echo "$v"; return 1; }

say "RIG: infra + services (tuso + costa + mushex)"
docker rm -f hpa-fee-pg hpa-fee-redis hpa-fee-kafka >/dev/null 2>&1
docker run -d --name hpa-fee-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name hpa-fee-redis -p $RPORT:6379 redis:7-alpine >/dev/null
docker run -d --name hpa-fee-kafka -p $KPORT:$KPORT redpandadata/redpanda:latest \
  redpanda start --overprovisioned --smp 1 --memory 512M --reserve-memory 0M --node-id 0 --check=false \
  --kafka-addr PLAINTEXT://0.0.0.0:$KPORT --advertise-kafka-addr PLAINTEXT://localhost:$KPORT >/dev/null
sleep 12
for db in tuso costa_db mushex_db; do docker exec hpa-fee-pg psql -U impilo -d postgres -c "CREATE DATABASE $db" >/dev/null 2>&1; done

jar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }
COMMON="POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:$KPORT \
SPRING_KAFKA_LISTENER_AUTO_STARTUP=true SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=$RPORT"

env $COMMON POSTGRES_DB=tuso SERVER_PORT=28084 \
  MUSHEX_URL=http://localhost:28102 TUSO_MUSHEX_SANDBOX_SIMULATION=true \
  nohup java -jar "$(jar tuso-service)" > "$RIGLOG/tuso.log" 2>&1 & echo $! > "$RIGLOG/tuso.pid"
env $COMMON SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$PGPORT/costa_db POSTGRES_DB=costa_db SERVER_PORT=28101 \
  nohup java -jar "$(jar costing-engine-service)" > "$RIGLOG/costa.log" 2>&1 & echo $! > "$RIGLOG/costa.pid"
env $COMMON SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$PGPORT/mushex_db POSTGRES_DB=mushex_db SERVER_PORT=28102 \
  MUSHEX_HMAC_PEPPER=rig-pepper-0123456789 MUSHEX_SANDBOX_APPLY_SIMULATION_OUTCOME=true \
  MUSHEX_SANDBOX_BYPASS_CREDENTIAL_CHECK=true CREDENTIAL_VERIFICATION_ENABLED=false CREDENTIAL_PAYEE_VERIFICATION_ENABLED=false \
  nohup java -jar "$(jar mushex-service)" > "$RIGLOG/mushex.log" 2>&1 & echo $! > "$RIGLOG/mushex.pid"

UP=0
for svc in "tuso 28084" "costa 28101" "mushex 28102"; do set -- $svc
  for i in $(seq 1 40); do c=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$2/actuator/health 2>/dev/null); [ "$c" = 200 ] && break; sleep 3; done
  echo "   $1 ($2): $c" | tee -a "$EV/journal.txt"; [ "$c" = 200 ] && UP=$((UP+1))
done
[ "$UP" = 3 ] && ok "3/3 services healthy" || { bad "only $UP/3 healthy"; tail -30 "$RIGLOG/tuso.log"; [ -z "${KEEP_RIG:-}" ] && docker rm -f hpa-fee-pg hpa-fee-redis hpa-fee-kafka >/dev/null 2>&1; exit 1; }

# helper: create → attach a document → submit an application; echoes applicationId
new_app(){ # $1 catalogueCode
  local body appid
  body="{\"applicationType\":\"NEW_REGISTRATION\",\"applicantName\":\"Rig Applicant\",\"catalogueCode\":\"$1\",\"facilityName\":\"Rig Facility $1 $$-$RANDOM\",\"facilityType\":\"CLINIC\",\"ownershipType\":\"PRIVATE\",\"province\":\"Harare\",\"district\":\"Harare\"}"
  curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications -d "$body" >/dev/null
  appid=$(TPSQL "SELECT application_id FROM tuso.facility_application ORDER BY created_at DESC LIMIT 1")
  # submission requires at least one supporting document
  curl -sS $(hdr FACILITY_APPLICANT) -X POST $TUSO/v1/internal/facility-registry/documents \
    -d "{\"applicationId\":\"$appid\",\"documentType\":\"APPLICATION_FORM\",\"fileReference\":\"rig://doc/$appid\"}" >/dev/null
  curl -sS $(hdr FACILITY_APPLICANT) -X POST $TUSO/v1/internal/facility-registry/applications/$appid/submit >/dev/null
  echo "$appid"
}

# ── Configure a governed TEST fee amount (marked non-SI) ─────────────────────
say "CONFIG: governed TEST fee amount (RIG-TEST-CONFIG — not SI 78)"
FEE=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/fee-schedules \
  -d '{"feeCode":"FEE_INITIAL_REGISTRATION_PRIVATE","appliesToCatalogueCode":"INITIAL_REGISTRATION_PRIVATE","amount":120.00,"currency":"USD","sourceInstrument":"RIG-TEST-CONFIG (not SI 78)","notes":"rig test amount"}' | mid feeId)
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/fee-schedules/$FEE/approve -d '{}' >/dev/null
FSTAT=$(TPSQL "SELECT status FROM tuso.regulatory_fee_schedule WHERE fee_id='$FEE'")
[ "$FSTAT" = "ACTIVE" ] && ok "TEST fee configured + approved ACTIVE (120.00 USD)" || bad "fee not active: $FSTAT"

# ── J-FEE-1: full loop ───────────────────────────────────────────────────────
say "J-FEE-1: configured fee → charge → pay → PAID → SETTLED → inspection"
A1=$(new_app "INITIAL_REGISTRATION_PRIVATE")
FS=$(TPSQL "SELECT fee_state||'|'||current_workflow_state FROM tuso.facility_application WHERE application_id='$A1'")
echo "   after submit: $FS" | tee -a "$EV/journal.txt"
echo "$FS" | grep -q "^DUE|AWAITING_FEE$" && ok "submit → fee DUE + AWAITING_FEE" || bad "expected DUE/AWAITING_FEE got $FS"
CHG=$(poll CPSQL "SELECT charge_status FROM costa_charge_records WHERE source_ref='$A1' AND source_type='REGULATORY_APPLICATION_FEE'" OPEN 12)
[ "$CHG" = "OPEN" ] && ok "COSTA opened a REGULATORY_APPLICATION_FEE charge (Kafka fee_due)" || bad "no costa charge: $CHG"
FREF=$(poll TPSQL "SELECT CASE WHEN fee_reference IS NULL THEN '' ELSE 'set' END FROM tuso.facility_application WHERE application_id='$A1'" set 8)
[ "$FREF" = "set" ] && ok "costa charge id written back as fee_reference (costa.charge.created)" || bad "fee_reference not linked"
curl -sS $(hdr FACILITY_APPLICANT) -X POST $TUSO/v1/internal/facility-registry/applications/$A1/fee/payment-intent >/dev/null
PREF=$(TPSQL "SELECT payment_reference FROM tuso.facility_application WHERE application_id='$A1'")
[ -n "$PREF" ] && ok "MusheX intent minted ($PREF)" || bad "no payment intent"
PAID=$(poll TPSQL "SELECT fee_state FROM tuso.facility_application WHERE application_id='$A1'" PAID 20)
[ "$PAID" = "PAID" ] && ok "MONEY LOOP: fee PAID via mushex.payment.status.changed" || bad "fee not PAID: $PAID"
SETL=$(poll CPSQL "SELECT charge_status FROM costa_charge_records WHERE source_ref='$A1' AND source_type='REGULATORY_APPLICATION_FEE'" SETTLED 12)
[ "$SETL" = "SETTLED" ] && ok "COSTA charge SETTLED (fee_paid)" || bad "costa charge not settled: $SETL"
RFI=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/applications/$A1/ready-for-inspection)
[ "$RFI" = "200" ] && ok "ready-for-inspection ALLOWED after PAID" || bad "ready-for-inspection blocked after PAID ($RFI)"

# ── J-FEE-2: gate negative ───────────────────────────────────────────────────
say "J-FEE-2: gate holds while DUE"
A2=$(new_app "INITIAL_REGISTRATION_PRIVATE")
RFI2=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/applications/$A2/ready-for-inspection)
[ "$RFI2" = "409" ] || [ "$RFI2" = "400" ] || [ "$RFI2" = "500" ] && ok "ready-for-inspection REJECTED while fee DUE (HTTP $RFI2)" || bad "gate did not hold: $RFI2"

# ── J-FEE-3: waiver ──────────────────────────────────────────────────────────
say "J-FEE-3: waiver opens the gate + voids the charge"
A3=$(new_app "INITIAL_REGISTRATION_PRIVATE")
poll CPSQL "SELECT charge_status FROM costa_charge_records WHERE source_ref='$A3' AND source_type='REGULATORY_APPLICATION_FEE'" OPEN 12 >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications/$A3/fee/waive -d '{"reason":"rig public-interest waiver"}' >/dev/null
W=$(TPSQL "SELECT fee_state||'|'||COALESCE(fee_waiver_reason,'-') FROM tuso.facility_application WHERE application_id='$A3'")
echo "   after waive: $W" | tee -a "$EV/journal.txt"
echo "$W" | grep -q "^WAIVED|rig public-interest waiver$" && ok "fee WAIVED with reason recorded" || bad "waiver wrong: $W"
CW=$(poll CPSQL "SELECT charge_status FROM costa_charge_records WHERE source_ref='$A3' AND source_type='REGULATORY_APPLICATION_FEE'" WAIVED 12)
[ "$CW" = "WAIVED" ] && ok "COSTA charge WAIVED (fee_waived)" || bad "costa charge not waived: $CW"
RFI3=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/applications/$A3/ready-for-inspection)
[ "$RFI3" = "200" ] && ok "ready-for-inspection ALLOWED after WAIVED" || bad "gate stuck after waiver ($RFI3)"

# ── J-FEE-4: honest NOT_CONFIGURED ───────────────────────────────────────────
say "J-FEE-4: fee required but no configured amount → honest, no gate"
A4=$(new_app "RENEWAL")
NC=$(TPSQL "SELECT fee_state FROM tuso.facility_application WHERE application_id='$A4'")
echo "   RENEWAL fee_state: $NC" | tee -a "$EV/journal.txt"
[ "$NC" = "NOT_CONFIGURED" ] && ok "unconfigured fee recorded NOT_CONFIGURED (not silently skipped)" || bad "expected NOT_CONFIGURED got $NC"
NCCHG=$(CPSQL "SELECT count(*) FROM costa_charge_records WHERE source_ref='$A4'")
[ "$NCCHG" = "0" ] && ok "no charge fabricated for an unconfigured fee" || bad "charge created despite no amount ($NCCHG)"
RFI4=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/applications/$A4/ready-for-inspection)
[ "$RFI4" = "200" ] && ok "NOT_CONFIGURED never gates inspection" || bad "unconfigured fee wrongly gated ($RFI4)"

echo "RESULT: PASS=$PASS FAIL=$FAIL" | tee -a "$EV/journal.txt"
if [ -z "${KEEP_RIG:-}" ]; then
  for p in tuso costa mushex; do [ -f "$RIGLOG/$p.pid" ] && kill "$(cat "$RIGLOG/$p.pid")" 2>/dev/null; done
  docker rm -f hpa-fee-pg hpa-fee-redis hpa-fee-kafka >/dev/null 2>&1
fi
[ "$FAIL" = 0 ]
