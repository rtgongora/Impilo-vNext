#!/usr/bin/env bash
# ============================================================================
# Provider-as-patient perks (Lane C) — runtime proof (PP-1..6).
#
# THE POINT: governed advantages for RECOGNISED health workers when THEY seek
# services — dual-source recognition (VARAPI licence + Vashandi workforce),
# COSTA HEALTH_WORKER fee exemption, coverage subsidy cap + suspension, and
# marketplace professional self-authorization. Every perk is DB-governed and
# DISABLED by default; this rig flips each governance switch explicitly and
# proves both the ON and OFF behaviour. Advantages are administrative/
# financial only — nothing here touches triage or queues.
#
#   PP-1  provider-as-patient encounter -> encounter enriched HEALTH_WORKER
#         (recognition ON + rule ACTIVE) -> bill shows the exemption with
#         EXEMPTION_HEALTH_WORKER party allocation + rule trace
#   PP-2  suspended licence -> NOT recognised -> encounter NOT enriched,
#         no discount
#   PP-3  rule flipped back to DISABLED -> encounter still tagged (policy
#         says the person IS a health worker) but bill discount = 0
#   PP-4  subsidy opt-in (programme activated) -> cap drawdown across two
#         bills: first consume inside cap succeeds, second exceeding the cap
#         is REJECTED
#   PP-5  licence lapses -> recognition sweep SUSPENDS the enrolment
#   PP-6  professional regulated purchase: recognised licensed provider buyer
#         self-satisfies prescriber standing on a FLAGGED risk class (order
#         validates + REGULATED_PRO_PURCHASE audit event); a non-provider
#         buyer is DENIED on the same listing
#
# Requirements: docker, java 21, packaged jars for: varapi-service
#   vashandi-workforce-service costing-engine-service coverage-service
#   msika-service msika-flow-service
# Usage: EV=<dir> bash scripts/runtime-proof/provider-perks-journeys.sh (KEEP_RIG=1 to inspect)
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-/tmp/provider-perks-evidence}; RIGLOG=${RIGLOG:-/tmp/provider-perks-logs}
mkdir -p "$EV" "$RIGLOG"
TEN=00000000-0000-4000-8000-000000000001
FAC=22222222-2222-4222-8222-222222222222
PGPORT=15679; KPORT=19096; RPORT=16435
VARAPI=http://localhost:29083; VASHANDI=http://localhost:29167
COSTA=http://localhost:29101; COVERAGE=http://localhost:29140
MSIKA=http://localhost:29086; FLOW=http://localhost:29100
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
PSQL(){ docker exec pperk-rig-pg psql -U impilo -d "$1" -tAc "$2"; }
hdr(){ local r=$1 a=${2:-rig-$1}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Idempotency-Key:$(uuidgen) -H Idempotency-Key:$(uuidgen) -H X-Actor-ID:$a -H X-Actor-Type:$r -H X-Purpose-Of-Use:BILLING"; }
mid(){ python3 -c "import json,sys;r=json.load(sys.stdin);d=r.get('data') if isinstance(r,dict) and 'data' in r else r;print((d or {}).get('$1') or (d or {}).get('id') or '')"; }
poll(){ local v=""; for i in $(seq 1 "$4"); do v=$(PSQL "$1" "$2"); [ "$v" = "$3" ] && { echo "$v"; return 0; }; sleep 5; done; echo "$v"; return 1; }
produce(){ # $1 topic $2 json — publish one event through redpanda
  echo "$2" | docker exec -i pperk-rig-kafka rpk topic produce "$1" --brokers localhost:$KPORT >/dev/null; }

say "RIG: infra + services (varapi + vashandi + costa + coverage + msika + flow)"
docker rm -f pperk-rig-pg pperk-rig-kafka pperk-rig-redis >/dev/null 2>&1
docker run -d --name pperk-rig-pg -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -p $PGPORT:5432 postgres:16-alpine >/dev/null
docker run -d --name pperk-rig-kafka -p $KPORT:$KPORT redpandadata/redpanda:latest \
  redpanda start --overprovisioned --smp 1 --memory 512M --reserve-memory 0M --node-id 0 --check=false \
  --kafka-addr PLAINTEXT://0.0.0.0:$KPORT --advertise-kafka-addr PLAINTEXT://localhost:$KPORT >/dev/null
docker run -d --name pperk-rig-redis -p $RPORT:6379 redis:7-alpine >/dev/null
sleep 12
for db in varapi vashandi_db costa_db coverage_db msika msika_flow; do
  docker exec pperk-rig-pg psql -U impilo -d postgres -c "CREATE DATABASE $db" >/dev/null 2>&1
done

jar(){ ls "$REPO/services/$1/target/$1-"*.jar 2>/dev/null | grep -v original | head -1; }
COMMON="POSTGRES_HOST=localhost POSTGRES_PORT=$PGPORT POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:$KPORT \
SPRING_KAFKA_LISTENER_AUTO_STARTUP=true SPRING_DATA_REDIS_HOST=localhost SPRING_DATA_REDIS_PORT=$RPORT"

# varapi (db name fixed 'varapi' via POSTGRES_HOST/PORT)
env $COMMON SERVER_PORT=29083 \
  nohup java -jar "$(jar varapi-service)" > "$RIGLOG/varapi.log" 2>&1 & echo $! > "$RIGLOG/varapi.pid"
# vashandi (DB_HOST/DB_PORT/DB_NAME convention)
env $COMMON DB_HOST=localhost DB_PORT=$PGPORT DB_NAME=vashandi_db SERVER_PORT=29167 \
  nohup java -jar "$(jar vashandi-workforce-service)" > "$RIGLOG/vashandi.log" 2>&1 & echo $! > "$RIGLOG/vashandi.pid"
# costa: recognition ENABLED for this rig (production default is disabled)
env $COMMON SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$PGPORT/costa_db SERVER_PORT=29101 \
  COSTA_RECOGNITION_ENABLED=true \
  IMPILO_INTEGRATIONS_VARAPI_BASE_URL=$VARAPI \
  IMPILO_INTEGRATIONS_VASHANDI_BASE_URL=$VASHANDI \
  nohup java -jar "$(jar costing-engine-service)" > "$RIGLOG/costa.log" 2>&1 & echo $! > "$RIGLOG/costa.pid"
# coverage: fast sweep so PP-5 proves suspension without waiting for 02:20
env $COMMON SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$PGPORT/coverage_db SERVER_PORT=29140 \
  SPRING_APPLICATION_JSON="{\"impilo\":{\"services\":{\"varapi-base-url\":\"$VARAPI\",\"vashandi-base-url\":\"$VASHANDI\"}},\"coverage\":{\"health-worker\":{\"sweep-cron\":\"*/10 * * * * *\"}}}" \
  nohup java -jar "$(jar coverage-service)" > "$RIGLOG/coverage.log" 2>&1 & echo $! > "$RIGLOG/coverage.pid"
env $COMMON POSTGRES_DB=msika SERVER_PORT=29086 \
  nohup java -jar "$(jar msika-service)" > "$RIGLOG/msika.log" 2>&1 & echo $! > "$RIGLOG/msika.pid"
env $COMMON POSTGRES_DB=msika_flow SERVER_PORT=29100 \
  MSIKA_CORE_URL=$MSIKA MSIKA_BASE_URL=$MSIKA \
  SPRING_APPLICATION_JSON="{\"msika-flow\":{\"integration\":{\"varapi-url\":\"$VARAPI\"}}}" \
  nohup java -jar "$(jar msika-flow-service)" > "$RIGLOG/flow.log" 2>&1 & echo $! > "$RIGLOG/flow.pid"

sleep 75
UP=0
for svc in "varapi 29083" "vashandi 29167" "costa 29101" "coverage 29140" "msika 29086" "flow 29100"; do
  set -- $svc
  for i in $(seq 1 12); do c=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$2/actuator/health 2>/dev/null); [ "$c" = 200 ] && break; sleep 5; done
  echo "   $1 ($2): $c" | tee -a "$EV/journal.txt"; [ "$c" = 200 ] && UP=$((UP+1))
done
[ "$UP" = 6 ] && ok "6/6 services healthy" || { bad "only $UP/6 healthy"; [ -z "${KEEP_RIG:-}" ] && docker rm -f pperk-rig-pg pperk-rig-kafka pperk-rig-redis >/dev/null 2>&1; exit 1; }

# ── FIXTURES: two providers in VARAPI (good standing / suspended) ────────────
say "FIXTURE: VARAPI providers (HW1 licenced-active, HW2 suspended)"
HW1=$(uuidgen); HW2=$(uuidgen); CITIZEN=$(uuidgen)
seed_provider(){ # $1 healthId $2 lifecycle $3 licence(or NULL) $4 status $5 pubid
  PSQL varapi "INSERT INTO varapi.provider
      (tenant_id, provider_ref, provider_public_id, given_name, family_name,
       profession, cadre, status, lifecycle_status, licence_status, impilo_health_id, active_flag)
    VALUES ('$TEN', '$(uuidgen)', '$5', 'Rig', 'Provider',
       'Medical Doctor', 'GENERAL_PRACTITIONER', '$4', '$2', $3, '$1', $( [ "$4" = "ACTIVE" ] && echo true || echo false ))" >/dev/null; }
seed_provider "$HW1" LICENCED_ACTIVE "'ACTIVE'" ACTIVE "PPRIG$(date +%s)A"
seed_provider "$HW2" SUSPENDED "'SUSPENDED'" SUSPENDED "PPRIG$(date +%s)B"

R1=$(curl -sS $(hdr SYSTEM costa-billing) "$VARAPI/v1/internal/providers/by-health-id/$HW1/recognition")
echo "$R1" > "$EV/pp0-recognition-hw1.json"
echo "$R1" | grep -q '"recognised":true' && ok "HW1 recognised by VARAPI (licenced-active)" || bad "HW1 recognition: $(echo $R1|head -c 160)"
R2=$(curl -sS $(hdr SYSTEM costa-billing) "$VARAPI/v1/internal/providers/by-health-id/$HW2/recognition")
echo "$R2" | grep -q '"recognised":false' && ok "HW2 NOT recognised (suspended) — generic body" || bad "HW2 recognition: $(echo $R2|head -c 160)"
R3=$(curl -sS $(hdr SYSTEM costa-billing) "$VARAPI/v1/internal/providers/by-health-id/$(uuidgen)/recognition")
[ "$(echo "$R2" | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"])')" = "$(echo "$R3" | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"])')" ] \
  && ok "suspended and unknown ids return IDENTICAL generic bodies (anti-enumeration)" || bad "negative bodies differ"

# ── PP-1: recognised provider-as-patient gets the governed discount ──────────
say "PP-1: encounter enrichment + HEALTH_WORKER exemption on the bill"
PSQL costa_db "UPDATE costa_exemption_rules SET status='ACTIVE' WHERE category='HEALTH_WORKER' AND tenant_id='$TEN'" >/dev/null
J1="pp1-journey-$$"
produce pct.encounter.started "{\"eventId\":\"$J1:STARTED\",\"journeyId\":\"$J1\",\"tenantId\":\"$TEN\",\"facilityId\":\"$FAC\",\"patientCpid\":\"$HW1\",\"encounterType\":\"OPD\"}"
EX1=$(poll costa_db "SELECT exemption_category FROM costa_encounters WHERE pct_journey_id='$J1'" HEALTH_WORKER 12)
[ "$EX1" = "HEALTH_WORKER" ] && ok "encounter enriched exemption_category=HEALTH_WORKER" || bad "encounter exemption=$EX1"
ENC1=$(PSQL costa_db "SELECT encounter_id FROM costa_encounters WHERE pct_journey_id='$J1'")
BILL1=$(PSQL costa_db "SELECT bill_id FROM costa_bill_headers WHERE encounter_id='$ENC1' LIMIT 1")
[ -n "$BILL1" ] && ok "DRAFT bill auto-created ($BILL1)" || bad "no bill for encounter $ENC1"
curl -sS $(hdr SYSTEM costa-billing) -X POST "$COSTA/costa/v1/bills/$BILL1/lines" \
  -d '{"msikaCode":"CONSULT-GP","description":"GP consultation","kind":"SERVICE","qty":1,"costMethod":"TARIFF"}' > "$EV/pp1-line.json"
curl -sS $(hdr SYSTEM costa-billing) -X POST "$COSTA/costa/v1/bills/$BILL1/recompute" > "$EV/pp1-recompute.json"
DISC1=$(PSQL costa_db "SELECT total_discount FROM costa_bill_headers WHERE bill_id='$BILL1'")
[ "$DISC1" = "12.50" ] && ok "bill discount = 12.50 (50% of 25.00 CONSULT-GP, capped rule)" || bad "discount=$DISC1 (want 12.50)"
PARTY1=$(PSQL costa_db "SELECT count(*) FROM costa_bill_parties WHERE bill_id='$BILL1' AND 'EXEMPTION_HEALTH_WORKER' = ANY(reason_codes)")
[ "$PARTY1" = "1" ] && ok "EXEMPTION_HEALTH_WORKER party allocation present" || bad "exemption party rows=$PARTY1"
TRACE1=$(PSQL costa_db "SELECT trace_summary FROM costa_bill_headers WHERE bill_id='$BILL1'")
echo "$TRACE1" > "$EV/pp1-trace.json"
echo "$TRACE1" | grep -q '"rule_id"' && ok "split trace carries the governing rule id" || bad "no rule trace: $(echo $TRACE1|head -c 160)"

# ── PP-2: suspended licence -> no recognition -> no discount ────────────────
say "PP-2: suspended provider gets NO enrichment and NO discount"
J2="pp2-journey-$$"
produce pct.encounter.started "{\"eventId\":\"$J2:STARTED\",\"journeyId\":\"$J2\",\"tenantId\":\"$TEN\",\"facilityId\":\"$FAC\",\"patientCpid\":\"$HW2\",\"encounterType\":\"OPD\"}"
for i in $(seq 1 12); do ENC2=$(PSQL costa_db "SELECT encounter_id FROM costa_encounters WHERE pct_journey_id='$J2'"); [ -n "$ENC2" ] && break; sleep 5; done
EX2=$(PSQL costa_db "SELECT COALESCE(exemption_category,'NONE') FROM costa_encounters WHERE pct_journey_id='$J2'")
[ "$EX2" = "NONE" ] && ok "suspended provider NOT enriched (exemption_category NULL)" || bad "exemption=$EX2"
BILL2=$(PSQL costa_db "SELECT bill_id FROM costa_bill_headers WHERE encounter_id='$ENC2' LIMIT 1")
curl -sS $(hdr SYSTEM costa-billing) -X POST "$COSTA/costa/v1/bills/$BILL2/lines" \
  -d '{"msikaCode":"CONSULT-GP","description":"GP consultation","kind":"SERVICE","qty":1,"costMethod":"TARIFF"}' >/dev/null
curl -sS $(hdr SYSTEM costa-billing) -X POST "$COSTA/costa/v1/bills/$BILL2/recompute" >/dev/null
DISC2=$(PSQL costa_db "SELECT total_discount FROM costa_bill_headers WHERE bill_id='$BILL2'")
[ "$DISC2" = "0.00" ] && ok "no discount for suspended licence (discount=$DISC2)" || bad "discount=$DISC2 (want 0.00)"

# ── PP-3: governed disable -> tag may exist, discount must not ───────────────
say "PP-3: rule DISABLED -> no discount even for a recognised health worker"
PSQL costa_db "UPDATE costa_exemption_rules SET status='DISABLED' WHERE category='HEALTH_WORKER' AND tenant_id='$TEN'" >/dev/null
J3="pp3-journey-$$"
produce pct.encounter.started "{\"eventId\":\"$J3:STARTED\",\"journeyId\":\"$J3\",\"tenantId\":\"$TEN\",\"facilityId\":\"$FAC\",\"patientCpid\":\"$HW1\",\"encounterType\":\"OPD\"}"
EX3=$(poll costa_db "SELECT exemption_category FROM costa_encounters WHERE pct_journey_id='$J3'" HEALTH_WORKER 12)
[ "$EX3" = "HEALTH_WORKER" ] && ok "encounter still tagged (recognition truth unchanged)" || bad "exemption=$EX3"
ENC3=$(PSQL costa_db "SELECT encounter_id FROM costa_encounters WHERE pct_journey_id='$J3'")
BILL3=$(PSQL costa_db "SELECT bill_id FROM costa_bill_headers WHERE encounter_id='$ENC3' LIMIT 1")
curl -sS $(hdr SYSTEM costa-billing) -X POST "$COSTA/costa/v1/bills/$BILL3/lines" \
  -d '{"msikaCode":"CONSULT-GP","description":"GP consultation","kind":"SERVICE","qty":1,"costMethod":"TARIFF"}' >/dev/null
curl -sS $(hdr SYSTEM costa-billing) -X POST "$COSTA/costa/v1/bills/$BILL3/recompute" >/dev/null
DISC3=$(PSQL costa_db "SELECT total_discount FROM costa_bill_headers WHERE bill_id='$BILL3'")
[ "$DISC3" = "0.00" ] && ok "DISABLED rule is invisible to the engine (discount=$DISC3)" || bad "discount=$DISC3 (want 0.00)"

# ── PP-4: subsidy opt-in + cap drawdown across two bills ─────────────────────
say "PP-4: HEALTH_WORKER_RECOGNITION opt-in + annual-cap drawdown (cap 500)"
PSQL coverage_db "UPDATE cv_subsidy_programs SET status='ACTIVE' WHERE program_code='SUB-HEALTH-WORKER-RECOGNITION' AND tenant_id='$TEN'" >/dev/null
PROG=$(PSQL coverage_db "SELECT id FROM cv_subsidy_programs WHERE program_code='SUB-HEALTH-WORKER-RECOGNITION' AND tenant_id='$TEN'")
DENY=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr PATIENT "$CITIZEN") -X POST "$COVERAGE/internal/v1/coverage/subsidies/$PROG/enrolments" -d "{\"health_id\":\"$CITIZEN\"}")
[ "$DENY" = "403" ] && ok "non-recognised citizen opt-in REJECTED server-side (HTTP $DENY)" || bad "citizen opt-in HTTP $DENY (want 403)"
ENR=$(curl -sS $(hdr PATIENT "$HW1") -X POST "$COVERAGE/internal/v1/coverage/subsidies/$PROG/enrolments" -d "{\"health_id\":\"$HW1\"}")
echo "$ENR" > "$EV/pp4-optin.json"
ENRID=$(echo "$ENR" | mid id)
[ -n "$ENRID" ] && ok "recognised HW1 opted in (enrolment $ENRID)" || bad "opt-in failed: $(echo $ENR|head -c 160)"
EB=$(PSQL coverage_db "SELECT enrolled_by FROM cv_subsidy_enrolments WHERE id='$ENRID'")
[ "$EB" = "SELF_OPT_IN:RECOGNITION:LICENSED_PROVIDER" ] && ok "enrolled_by records SELF_OPT_IN:RECOGNITION:LICENSED_PROVIDER" || bad "enrolled_by=$EB"
EC=$(PSQL coverage_db "SELECT exemption_category FROM cv_subsidy_enrolments WHERE id='$ENRID'")
[ "$EC" = "HEALTH_WORKER" ] && ok "exemption_category=HEALTH_WORKER on the unified ledgered row" || bad "exemption_category=$EC"
C1=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr SYSTEM costa-billing) -X POST "$COVERAGE/internal/v1/coverage/subsidies/enrolments/$ENRID/consume" -d '{"amount":300.00,"reference":"pp4-bill-A"}')
[ "$C1" = "200" ] && ok "bill A drawdown 300.00 inside cap (HTTP $C1)" || bad "consume A HTTP $C1"
C2=$(curl -sS -o /dev/null -w '%{http_code}' $(hdr SYSTEM costa-billing) -X POST "$COVERAGE/internal/v1/coverage/subsidies/enrolments/$ENRID/consume" -d '{"amount":250.00,"reference":"pp4-bill-B"}')
[ "$C2" != "200" ] && ok "bill B drawdown 250.00 EXCEEDS cap 500 -> rejected (HTTP $C2)" || bad "cap breach accepted?!"
CONSUMED=$(PSQL coverage_db "SELECT consumed_amount FROM cv_subsidy_balances WHERE enrolment_id='$ENRID'")
[ "$CONSUMED" = "300.00" ] && ok "ledger shows exactly 300.00 consumed" || bad "consumed=$CONSUMED"

# ── PP-5: licence lapse suspends the enrolment (scheduled sweep) ─────────────
say "PP-5: lapsed licence -> sweep SUSPENDS the recognition enrolment"
PSQL varapi "UPDATE varapi.provider SET lifecycle_status='LAPSED', licence_status=NULL, status='INACTIVE', active_flag=false WHERE impilo_health_id='$HW1'" >/dev/null
ST=$(poll coverage_db "SELECT status FROM cv_subsidy_enrolments WHERE id='$ENRID'" SUSPENDED 12)
[ "$ST" = "SUSPENDED" ] && ok "enrolment SUSPENDED by the recognition sweep" || bad "enrolment status=$ST"

# Restore HW1 for PP-6 (marketplace lane needs a recognised buyer again).
PSQL varapi "UPDATE varapi.provider SET lifecycle_status='LICENCED_ACTIVE', licence_status='ACTIVE', status='ACTIVE', active_flag=true WHERE impilo_health_id='$HW1'" >/dev/null

# ── PP-6: professional regulated purchase (allow) / non-provider (deny) ─────
say "PP-6: professional self-authorization on a FLAGGED risk class"
PSQL msika "UPDATE msika_risk_friction_mapping SET professional_self_authorizable=true WHERE risk_classification='HIGH_RISK'" >/dev/null
ITEMCODE="ZW-STARTER-EQP-001"
PSQL msika "UPDATE msika_catalog_items SET restrictions='{\"provider_only_order\": true}'::jsonb, risk_classification='HIGH_RISK' WHERE canonical_code='$ITEMCODE'" >/dev/null
ITEMID=$(PSQL msika "SELECT item_id FROM msika_catalog_items WHERE canonical_code='$ITEMCODE'")
SF=$(curl -sS $(hdr MARKETPLACE_SELLER) -X POST $MSIKA/v1/storefronts -d '{"sellerType":"VENDOR","sellerId":"VEND-PP6","displayName":"Pro Purchase Supplies"}' | mid storefrontId)
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/storefronts/$SF/verify -d '{"verificationStatus":"VERIFIED","notes":"rig"}' >/dev/null
LST=$(curl -sS $(hdr MARKETPLACE_SELLER) -X POST $MSIKA/v1/listings -d "{\"storefrontId\":\"$SF\",\"catalogItemId\":\"$ITEMID\",\"sellerType\":\"VENDOR\",\"sellerId\":\"VEND-PP6\",\"title\":\"Professional Item\",\"summary\":\"rig\",\"priceAmount\":30.00,\"priceCurrency\":\"ZWG\"}" | mid listingId)
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$LST/submit >/dev/null
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$LST/approve -d '{"notes":"ok"}' >/dev/null
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$LST/publish >/dev/null
[ -n "$LST" ] && ok "flagged listing published ($LST)" || bad "no listing"

order_for(){ # $1 buyer-health-id — echoes orderId (checked out, NOT yet validated)
  local b=$1 cart order
  cart=$(curl -sS $(hdr PATIENT $b) "$FLOW/v1/carts/open?channel=WEB" | mid cartId)
  curl -sS $(hdr PATIENT $b) -X POST $FLOW/v1/carts/$cart/items -d "{\"msikaCoreCode\":\"$ITEMCODE\",\"listingId\":\"$LST\",\"kind\":\"PRODUCT\",\"qty\":1,\"fulfillmentMode\":\"PHARMACY_PICKUP\"}" >/dev/null
  order=$(curl -sS $(hdr PATIENT $b) -X POST $FLOW/v1/carts/$cart/checkout -d "{\"orderType\":\"OTC_PRODUCT_ORDER\",\"idempotencyKey\":\"$b-pp6\"}" | mid orderId)
  echo "$order"
}

O_PRO=$(order_for "$HW1")
V_PRO=$(curl -sS -o "$EV/pp6-pro-validate.json" -w '%{http_code}' $(hdr PATIENT "$HW1") -X POST $FLOW/v1/orders/$O_PRO/validate)
[ "$V_PRO" = "200" ] && ok "recognised provider buyer VALIDATED provider-only order (HTTP $V_PRO)" || bad "pro validate HTTP $V_PRO: $(head -c 160 $EV/pp6-pro-validate.json)"
AUD=$(PSQL msika_flow "SELECT count(*) FROM mf_order_events WHERE order_id='$O_PRO' AND reason_code='REGULATED_PRO_PURCHASE'")
[ "$AUD" = "1" ] && ok "REGULATED_PRO_PURCHASE audit event recorded" || bad "audit rows=$AUD"

O_CIT=$(order_for "$CITIZEN")
V_CIT=$(curl -sS -o "$EV/pp6-citizen-validate.json" -w '%{http_code}' $(hdr PATIENT "$CITIZEN") -X POST $FLOW/v1/orders/$O_CIT/validate)
[ "$V_CIT" != "200" ] && ok "non-provider buyer DENIED on the same listing (HTTP $V_CIT)" || bad "citizen order validated?!"
grep -q "only be ordered by a provider" "$EV/pp6-citizen-validate.json" && ok "denial reason is the provider-only restriction" || bad "unexpected denial body"

echo "RESULT: PASS=$PASS FAIL=$FAIL" | tee -a "$EV/journal.txt"
for p in "$RIGLOG"/*.pid; do kill "$(cat $p)" 2>/dev/null; done
[ -z "${KEEP_RIG:-}" ] && docker rm -f pperk-rig-pg pperk-rig-kafka pperk-rig-redis >/dev/null 2>&1
[ "$FAIL" = 0 ]
