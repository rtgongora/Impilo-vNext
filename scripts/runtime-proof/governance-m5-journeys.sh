#!/usr/bin/env bash
# OF-D / M5 live proof — journeys #43 and #67.
# Runs only after the full-preview in-place deployment has completed.
set -uo pipefail

NS=${NS:-impilo-full-preview}
TENANT=${TENANT:-00000000-0000-4000-8000-000000000001}
EVID=${EVID:-/tmp/ofd-m5-evidence}
mkdir -p "$EVID"

PASS=0
FAIL=0
declare -a RESULTS=()
pass() { echo "PASS: $*"; RESULTS+=("PASS: $*"); PASS=$((PASS + 1)); }
fail() { echo "FAIL: $*"; RESULTS+=("FAIL: $*"); FAIL=$((FAIL + 1)); }

psqln() {
  kubectl -n "$NS" exec deploy/postgres -- psql -U impilo -d nhume -tAc "$1"
}
psqlm() {
  kubectl -n "$NS" exec deploy/postgres -- psql -U impilo -d msika_flow -tAc "$1"
}

api() {
  local name=$1 method=$2 path=$3 body=${4:-}
  local rid out
  rid=$(uuidgen)
  local args=(-sS -w $'\n%{http_code}' --max-time 40 -X "$method"
    "http://nhume-service:8080$path"
    -H "Content-Type: application/json"
    -H "X-Tenant-ID: $TENANT"
    -H "X-Pod-ID: national-spine"
    -H "X-Request-ID: $rid"
    -H "X-Correlation-ID: $rid"
    -H "X-Actor-ID: ofd-m5-proof"
    -H "X-Actor-Type: PROVIDER"
    -H "X-Purpose-Of-Use: TREATMENT"
    -H "Idempotency-Key: ofd-$rid")
  [ -n "$body" ] && args+=(-d "$body")
  out=$(kubectl -n "$NS" exec deploy/oros-service -- curl "${args[@]}" 2>/dev/null)
  API_CODE=$(echo "$out" | tail -1)
  API_BODY=$(echo "$out" | sed '$d')
  printf '%s\n' "$API_BODY" > "$EVID/$name.json"
  printf '%s\n' "$API_CODE" > "$EVID/$name.code"
}

jget() { echo "$API_BODY" | jq -r "$1 // empty" 2>/dev/null; }

poll_sql() {
  local query=$1 expected=$2 timeout=$3 deadline value
  deadline=$(( $(date +%s) + timeout ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    value=$(psqln "$query")
    [ "$value" = "$expected" ] && return 0
    sleep 3
  done
  return 1
}

create_delivery() {
  local name=$1 sensor=$2 cold=$3 selection=${4:-}
  local links="{}"
  [ -n "$selection" ] && links="{\"links\":{\"msikaFlowSelectionRef\":\"$selection\"}}"
  api "$name-create" POST /internal/v1/nhume/deliveries \
    "{\"delivery_type\":\"MEDICINE\",\"priority\":\"URGENT\",\"request_source\":\"OFD_M5_PROOF\",\"requesting_actor_id\":\"ofd-m5-proof\",\"requesting_actor_type\":\"PROVIDER\",\"origin\":{\"kind\":\"FACILITY\",\"ref\":\"OFD-ORIGIN\",\"label\":\"Proof pharmacy\",\"lat\":-17.8292,\"lng\":31.0522},\"destination\":{\"kind\":\"ADDRESS\",\"ref\":\"OFD-DEST\",\"label\":\"Proof destination\",\"lat\":-17.7690,\"lng\":31.0510},\"recipient\":{\"kind\":\"PATIENT\",\"ref\":\"OFD-PATIENT\",\"name\":\"Preview proof patient\",\"phone\":\"+263000000000\",\"language\":\"en\"},\"items\":[{\"item_kind\":\"MEDICATION\",\"item_ref\":\"OFD-COLD-1\",\"description\":\"Cold-chain proof item\",\"quantity\":1,\"unit_of_measure\":\"EA\",\"cold_chain\":$cold}],\"packages\":[{\"package_code\":\"OFD-PKG-$(date +%s%N)\",\"package_type\":\"SEALED\",\"seal_id\":\"OFD-SEAL\",\"cold_chain\":$cold}],\"cold_chain_required\":$cold,\"chain_of_custody_required\":true,\"sensor_device_ref\":\"$sensor\",\"cold_min_c\":2,\"cold_max_c\":8,\"metadata\":$links}"
  DELIVERY_ID=$(jget '.delivery_id')
  [ "$API_CODE" = "201" ] && [ -n "$DELIVERY_ID" ]
}

advance_to_transit() {
  local id=$1 courier=$2
  api "advance-submit-$id" POST "/internal/v1/nhume/deliveries/$id/submit" '{"reason":"M5 proof"}'
  [ "$API_CODE" = 200 ] || return 1
  api "advance-approve-$id" POST "/internal/v1/nhume/deliveries/$id/approve" '{"reason":"M5 proof"}'
  [ "$API_CODE" = 200 ] || return 1
  api "advance-assign-$id" POST "/internal/v1/nhume/deliveries/$id/assign" \
    "{\"courier_id\":\"$courier\",\"assignment_kind\":\"MANUAL\"}"
  [ "$API_CODE" = 201 ] || return 1
  api "advance-accept-$id" POST "/internal/v1/nhume/deliveries/$id/accept" '{}'
  [ "$API_CODE" = 200 ] || return 1
  api "advance-pickup-$id" POST "/internal/v1/nhume/deliveries/$id/pickup" '{"reason":"sealed pickup"}'
  [ "$API_CODE" = 200 ] || return 1
  api "advance-start-$id" POST "/internal/v1/nhume/deliveries/$id/start" '{"reason":"departed"}'
  [ "$API_CODE" = 200 ] || return 1
}

COURIER=$(psqln "select courier_id from nhume_driver_courier_profiles where tenant_id='$TENANT' and active=true order by trust_verified desc, created_at limit 1")
if [ -z "$COURIER" ]; then
  fail "#43/#67 — no active courier profile is available"
else
  pass "#43/#67 — active courier fixture resolved ($COURIER)"
fi

# Journey #43 — cold-chain sensor bus → unsuppressible excursion → quarantine/return.
SENSOR="OFD-TEMP-$(date +%s)"
SELECTION=${M5_SELECTION_ID:-}
[ -z "$SELECTION" ] && [ -f /tmp/ofb-m3-evidence/m5-paid-selection-id.txt ] \
  && SELECTION=$(cat /tmp/ofb-m3-evidence/m5-paid-selection-id.txt)
if create_delivery cold "$SENSOR" true "$SELECTION"; then
  COLD_ID=$DELIVERY_ID
  pass "#43a — cold delivery created with sealed sensor and product band ($COLD_ID)"
else
  fail "#43a — cold delivery create HTTP $API_CODE"
  COLD_ID=
fi

if [ -n "$COLD_ID" ] && advance_to_transit "$COLD_ID" "$COURIER"; then
  pass "#43b — cold cargo advanced to IN_TRANSIT"
else
  fail "#43b — cold cargo could not reach IN_TRANSIT"
fi

if [ -n "$COLD_ID" ]; then
  EVENT="{\"reading_id\":\"$(uuidgen)\",\"tenant_id\":\"$TENANT\",\"device_id\":\"$SENSOR\",\"metric_type\":\"TEMPERATURE\",\"metric_value\":\"12.7\",\"unit\":\"C\",\"recorded_at\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"source\":\"OFD_M5_PROOF\"}"
  printf '%s\n' "$EVENT" | kubectl -n "$NS" exec -i deploy/kafka -- \
    kafka-console-producer.sh --bootstrap-server localhost:9092 \
    --topic telemetry.iot.device.reading >/dev/null 2>&1
  if poll_sql "select excursion_flagged::text from nhume_delivery_requests where delivery_id='$COLD_ID'" true 45; then
    pass "#43c — IoT bus excursion flagged the bound delivery"
  else
    fail "#43c — IoT bus excursion did not flag the delivery"
  fi
  COC=$(psqln "select count(*) from nhume_chain_of_custody_events where delivery_id='$COLD_ID' and event_kind in ('TEMPERATURE_READING','TEMPERATURE_EXCURSION')")
  EXC=$(psqln "select count(*) from nhume_delivery_exceptions where delivery_id='$COLD_ID' and exception_kind='COLD_CHAIN_EXCURSION' and severity='CRITICAL'")
  EVT=$(psqln "select count(*) from nhume_event_outbox where aggregate_id='$COLD_ID' and event_type='nhume.delivery.temperature_excursion.v1'")
  [ "$COC" -ge 2 ] && [ "$EXC" -ge 1 ] && [ "$EVT" -ge 1 ] \
    && pass "#43d — excursion is unsuppressible: custody + CRITICAL exception + versioned event" \
    || fail "#43d — incomplete excursion evidence (custody=$COC exception=$EXC event=$EVT)"

  api cold-decision POST "/internal/v1/nhume/deliveries/$COLD_ID/cold-chain/stability-decision" \
    '{"decision":"QUARANTINE","notes":"Out-of-band proof reading; do not hand over"}'
  STATUS=$(jget '.status')
  DECISION=$(jget '.stability_decision')
  [ "$API_CODE" = 200 ] && [ "$STATUS" = RETURNED ] && [ "$DECISION" = QUARANTINE ] \
    && pass "#43e — pharmacist QUARANTINE routed cargo to RETURNED" \
    || fail "#43e — quarantine result HTTP=$API_CODE status=$STATUS decision=$DECISION"
  NOTIFY=$(psqln "select count(*) from nhume_delivery_notification_events where delivery_id='$COLD_ID' and event_code like 'COLD_CHAIN_%'")
  [ "$NOTIFY" -ge 1 ] && pass "#43f — safety notification evidence persisted" \
    || fail "#43f — no cold-chain safety notification evidence"
  if [ -n "$SELECTION" ]; then
    REFUND=$(psqlm "select coalesce(refund_ref,'') from mf_selections where selection_id='$SELECTION'")
    EVENT_COUNT=$(psqlm "select count(*) from mf_event_outbox where aggregate_id='$SELECTION' and event_type='SELECTION_RETURN_REFUND_REQUESTED'")
    [ -n "$REFUND" ] && [ "$EVENT_COUNT" -ge 1 ] \
      && pass "#43g — RETURNED write-back engaged the real refund rail ($REFUND)" \
      || fail "#43g — patient make-whole refund was not engaged (selection=$SELECTION)"
  else
    fail "#43g — no paid M3 selection was supplied for the patient make-whole refund leg"
  fi
fi

# Journey #67 — evidence-gated drone corridor, blocked weather, honest ROAD fallback.
CORRIDOR="OFD-HRE-$(date +%s)"
psqln "insert into nhume_mode_corridors(corridor_id,tenant_id,corridor_code,display_name,mode,enabled,max_payload_grams,max_distance_km,weather_status,evidence_ref) values(gen_random_uuid(),'$TENANT','$CORRIDOR','M5 proof corridor','DRONE',true,2000,25,'BLOCKED','evidence://ofd-m5/weather-gate') on conflict(tenant_id,corridor_code) do update set enabled=true,weather_status='BLOCKED',evidence_ref=excluded.evidence_ref" >/dev/null

if create_delivery mode "OFD-MODE-SENSOR" false ""; then
  MODE_ID=$DELIVERY_ID
  psqln "update nhume_delivery_packages set weight_grams=500 where delivery_id='$MODE_ID'" >/dev/null
  api mode-submit POST "/internal/v1/nhume/deliveries/$MODE_ID/submit" '{"reason":"M5 mode proof"}'
  api mode-approve POST "/internal/v1/nhume/deliveries/$MODE_ID/approve" '{"reason":"M5 mode proof"}'
  api mode-assign POST "/internal/v1/nhume/deliveries/$MODE_ID/assign" \
    "{\"courier_id\":\"$COURIER\",\"assignment_kind\":\"MANUAL\"}"
  MODE=$(psqln "select coalesce(delivery_mode,'') from nhume_delivery_requests where delivery_id='$MODE_ID'")
  REASON=$(psqln "select coalesce(mode_fallback_reason,'') from nhume_delivery_requests where delivery_id='$MODE_ID'")
  [ "$API_CODE" = 201 ] && [ "$MODE" = ROAD ] && [ "$REASON" = "WEATHER_GATE_FAILED:BLOCKED" ] \
    && pass "#67a — blocked weather produced governed ROAD fallback" \
    || fail "#67a — fallback HTTP=$API_CODE mode=$MODE reason=$REASON"
  MISSION=$(psqln "select count(*) from nhume_autonomous_missions where delivery_id='$MODE_ID' and status='NOT_FLOWN' and fallback_reason='WEATHER_GATE_FAILED:BLOCKED'")
  CUSTODY=$(psqln "select count(*) from nhume_chain_of_custody_events where delivery_id='$MODE_ID' and event_kind='MODE_FALLBACK'")
  OUTBOX=$(psqln "select count(*) from nhume_event_outbox where aggregate_id='$MODE_ID' and event_type='nhume.delivery.mode_fallback.v1'")
  [ "$MISSION" -eq 1 ] && [ "$CUSTODY" -ge 1 ] && [ "$OUTBOX" -ge 1 ] \
    && pass "#67b — NOT_FLOWN mission retained; custody and versioned fallback event persisted" \
    || fail "#67b — fallback evidence mission=$MISSION custody=$CUSTODY outbox=$OUTBOX"
  FLIGHT=$(psqln "select count(*) from nhume_autonomous_missions where status in ('FLOWN','IN_FLIGHT','AIRBORNE','LANDED','FLIGHT_COMPLETED','FLIGHT_IN_PROGRESS')")
  [ "$FLIGHT" -eq 0 ] && pass "#67c — estate contains zero prohibited flight claims" \
    || fail "#67c — found $FLIGHT prohibited flight claims"
else
  fail "#67a — mode delivery create HTTP $API_CODE"
fi

printf '%s\n' "${RESULTS[@]}" > "$EVID/summary.txt"
printf 'TOTALS: PASS=%d FAIL=%d\n' "$PASS" "$FAIL" | tee -a "$EVID/summary.txt"
[ "$FAIL" -eq 0 ]
