set -u
NS=impilo-full-preview
TEN=00000000-0000-4000-8000-000000000001
PASS=0; FAIL=0
ok(){ echo "   PASS: $1"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1"; FAIL=$((FAIL+1)); }
H(){ echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Actor-ID:${1:-dr-live} -H X-Actor-Type:PROVIDER -H X-Purpose-Of-Use:TREATMENT -H X-Facility-ID:$(uuidgen)"; }
OROS(){ kubectl -n $NS exec deploy/oros-service -- curl -s "$@"; }
KEYS(){ kubectl -n $NS exec deploy/tshepo-keys-service -- curl -s "$@"; }
PSQL(){ kubectl -n $NS exec deploy/postgres -- psql -U impilo -d "$1" -tAc "$2" 2>/dev/null | tr -d '[:space:]'; }
JV(){ python3 -c "import json,sys
r=json.load(sys.stdin); d=r.get('data') if isinstance(r,dict) and 'data' in r else r
cur=d
for k in '$1'.split('.'): cur=(cur or {}).get(k) if isinstance(cur,dict) else None
print(cur if cur is not None else '')"; }

echo "== L1: key provisioning (live)"
PROV=$(OROS -X POST $(H key-admin) http://tshepo-keys-service:8184/v1/keys/provision -d "{\"tenantId\":\"$TEN\",\"purpose\":\"PRESCRIPTION_SIGNING\"}")
KID=$(echo "$PROV" | python3 -c "import json,sys; print(json.load(sys.stdin).get('keyId',''))" 2>/dev/null)
[ -n "$KID" ] && ok "PRESCRIPTION_SIGNING key live ($KID)" || bad "provision: $PROV"

echo "== L2: prescription sign + token + claim (live)"
CPID="LIVE-CPID-$(date +%s)"
RX=$(OROS -X POST $(H dr-live) http://localhost:8089/v1/prescriptions -d "{
 \"patientCpid\":\"$CPID\",\"repeatsCeiling\":2,\"indication\":\"HTN\",
 \"items\":[{\"medicationCode\":\"C09AA05\",\"displayName\":\"Ramipril 5mg\",\"dose\":\"5mg\"}]}")
RXID=$(echo "$RX" | JV prescriptionId)
[ -n "$RXID" ] && ok "prescription drafted live ($RXID)" || bad "draft: $RX"
SG=$(OROS -X POST $(H dr-live) http://localhost:8089/v1/prescriptions/$RXID/sign)
[ "$(echo "$SG" | JV status)" = "SIGNED_ACTIVE" ] && ok "signed via live tshepo-keys" || bad "sign: $SG"
TK=$(OROS $(H dr-live) http://localhost:8089/v1/prescriptions/$RXID/token | JV tokenJws)
[ -n "$TK" ] && ok "token minted" || bad "no token"
CL=$(OROS -X POST $(H pharmacist-live) -H "Idempotency-Key:live-$(uuidgen)" http://localhost:8089/v1/prescriptions/claims -d "{\"token\":\"$TK\",\"vendorRef\":\"live-pharmacy\"}")
[ "$(echo "$CL" | JV repeatsRemaining)" = "1" ] && ok "claim decremented live (2→1)" || bad "claim: $CL"
[ "$(PSQL oros "SELECT count(*) FROM oros_prescription_claims WHERE prescription_id='$RXID'")" = "1" ] && ok "claim row persisted" || bad "claim row missing"
EV=$(PSQL oros "SELECT count(*) FROM oros_event_outbox WHERE aggregate_id='$RXID' AND event_type IN ('PRESCRIPTION_SIGNED','PRESCRIPTION_ACTIVATED','PRESCRIPTION_CLAIMED')")
[ "$EV" = "3" ] && ok "outbox events (signed/activated/claimed)" || bad "outbox events=$EV"

echo "== L3: order versioning live"
OD=$(OROS -X POST $(H dr-live) http://localhost:8089/v1/orders -d "{
 \"orderType\":\"PHARMACY\",\"patientCpid\":\"$CPID\",\"ziboOrderCode\":\"LIVE-1\",\"items\":[{\"code\":\"C09AA05\",\"displayName\":\"Ramipril\",\"quantity\":1}]}")
OID=$(echo "$OD" | JV orderId)
AM=$(OROS -X POST $(H dr-live) http://localhost:8089/v1/orders/$OID/amend -d '{"clinicalNotes":"amended","reason":"live proof"}')
[ "$(echo "$AM" | JV version)" = "2" ] && ok "order amend → v2 live" || bad "amend: $AM"

echo "== L4: allergy SoR live (BFF contract)"
PCT(){ kubectl -n $NS exec deploy/pct-service -- curl -s "$@"; }
GATE=$(PCT -o /dev/null -w '%{http_code}' -X POST $(H nurse-live) http://localhost:8088/v1/allergies -d "{\"patient_id\":\"$CPID\",\"allergen\":\"Penicillin\"}")
[ "$GATE" = "403" ] && ok "subject-relationship gate blocks context-free write (403)" || bad "gate http=$GATE"
HE(){ echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine -H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Actor-ID:nurse-live -H X-Actor-Type:PROVIDER -H X-Purpose-Of-Use:EMERGENCY -H X-Facility-ID:$(uuidgen)"; }
AL=$(PCT -X POST $(HE) http://localhost:8088/v1/allergies -d "{\"patient_id\":\"$CPID\",\"allergen\":\"Penicillin\",\"allergen_code\":\"J01CE01\",\"severity\":\"SEVERE\"}")
[ -n "$(echo "$AL" | JV allergy_id)" ] && ok "allergy recorded under emergency waiver (coded)" || bad "allergy: $AL"
LS=$(PCT $(H nurse-live) "http://localhost:8088/v1/allergies?patient_id=$CPID")
echo "$LS" | grep -q "J01CE01" && ok "allergy list serves coded record" || bad "list: $LS"

echo "RESULT: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" = 0 ] || exit 1
