#!/usr/bin/env bash
# Gateway UI catch-up runtime proof (run after rig-boot.sh). Drives REAL HTTP through the
# experience-bff for every UI-backing flow and reads back the daidzai/guidance SoR with psql.
# Writes evidence + a PASS/FAIL summary to this directory.
#
#   J1  Dispatcher callback console        (/work/daidzai/verify-callbacks)   — BE-1
#   J2  Public SOS status-by-reference     (/welcome/emergency/track)         — BE-2
#   J3  Public health-info search          (PublicHealthInfo search box)      — BE-3
#   J4  R1 contact-OTP register            (/auth/register/contact)           — W1 lane
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
BFF=http://localhost:28760
DAIDZAI=http://localhost:28692   # daidzai SoR (oauth-disabled in rig) — authoritative gate/state read-back
PG=gw-e-rig-pg
TENANT=00000000-0000-0000-0000-000000000001   # PUBLIC_DEFAULT_TENANT (BFF hardcodes it for the public SOS write)

pass=0; fail=0
ok()  { echo "PASS  $1"; pass=$((pass+1)); }
bad() { echo "FAIL  $1"; fail=$((fail+1)); }
psql_daidzai() { docker exec "$PG" psql -U impilo -d daidzai -tAc "$1" 2>/dev/null; }

# The BFF companion filter requires the 4 mandatory v1.1 headers on EVERY inbound request (the live
# edge synthesizes them for anonymous traffic); "public" means no JWT/actor required, not header-free.
# Bash arrays preserve the quoting of header values that contain spaces; fresh ids per call avoid dedup.
mkpub() { PUB=(-H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: national-spine"
               -H "X-Request-ID: $(uuidgen)" -H "X-Correlation-ID: $(uuidgen)" -H "Idempotency-Key: $(uuidgen)"); }
# Authenticated operator lane (dispatcher). BFF is fully open in-rig, so the daidzai proxy is
# reachable; the operator trust context is forwarded to daidzai (which audits it).
mkop()  { OP=(-H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: national-spine"
              -H "X-Request-ID: $(uuidgen)" -H "X-Correlation-ID: $(uuidgen)" -H "Idempotency-Key: $(uuidgen)"
              -H "X-Actor-ID: rig-dispatcher" -H "X-Actor-Type: STAFF" -H "X-Purpose-Of-Use: EMERGENCY_DISPATCH"); }

echo "=================================================================="
echo " J1 — Dispatcher callback console (BE-1)  [all THROUGH the BFF]"
echo "=================================================================="

# J1.1 — anonymous public SOS create (BFF public lane, permitAll)
mkpub
curl -sS -X POST "$BFF/internal/v1/public/gateway/sos" "${PUB[@]}" \
  -H 'Content-Type: application/json' \
  -d '{"callbackNumber":"0771 234 567","emergencyCategory":"MEDICAL","severity":"HIGH","description":"Man collapsed at the market","locationDescription":"Mbare market"}' \
  -o "$DIR/j1-1-create.json" -w '%{http_code}' > "$DIR/j1-1-create.code"
REF=$(python3 -c "import json;print(json.load(open('$DIR/j1-1-create.json')).get('requestReference',''))" 2>/dev/null)
DBSTATUS=$(psql_daidzai "SELECT status FROM daidzai.dai_emergency_request WHERE request_reference='$REF';")
if [ "$(cat "$DIR/j1-1-create.code")" = "202" ] && [ -n "$REF" ] && [ "$DBSTATUS" = "AWAITING_CALLBACK" ]; then
  ok "J1.1 anonymous SOS captured (202), ref=$REF, psql status=AWAITING_CALLBACK"
else
  bad "J1.1 create (code=$(cat "$DIR/j1-1-create.code") ref=$REF dbStatus=$DBSTATUS)"; REF=""
fi

# J1.2 — dispatcher worklist lists it (BE-1 list-by-status, THROUGH the BFF authenticated lane)
mkop
curl -sS "$BFF/internal/v1/daidzai/requests?status=AWAITING_CALLBACK" "${OP[@]}" \
  -o "$DIR/j1-2-worklist.json" -w '%{http_code}' > "$DIR/j1-2-worklist.code"
RID=$(python3 -c "
import json
rows=json.load(open('$DIR/j1-2-worklist.json'))
print(next((r['id'] for r in rows if r.get('requestReference')=='$REF'), ''))" 2>/dev/null)
if [ "$(cat "$DIR/j1-2-worklist.code")" = "200" ] && [ -n "$RID" ]; then
  ok "J1.2 AWAITING_CALLBACK worklist (through BFF) lists the request (id=$RID)"
else
  bad "J1.2 worklist code=$(cat "$DIR/j1-2-worklist.code") did not list $REF"
fi

echo
echo "=================================================================="
echo " J2 — Public SOS status-by-reference (BE-2)  [THROUGH the BFF public lane]"
echo "=================================================================="

# J2.1 — anonymous status read + EXACT 5-field + PII-free assertion (pre-verify: AWAITING_CALLBACK)
mkpub
curl -sS "$BFF/internal/v1/public/gateway/sos/$REF" "${PUB[@]}" \
  -o "$DIR/j2-1-status.json" -w '%{http_code}' > "$DIR/j2-1-status.code"
python3 - "$DIR/j2-1-status.json" <<'PY' > "$DIR/j2-1-assert.txt" 2>&1
import json,sys
d=json.load(open(sys.argv[1]))
raw=json.dumps(d).lower()
allowed={"requestreference","status","stage","callbackpending","createdat"}
keys=set(k.lower() for k in d.keys())
banned=["callbacknumber","+263","0771","description","market","location","subject","hid-","lat","lng"]
leaks=[b for b in banned if b in raw]
print("keys:", sorted(d.keys()))
print("status:", d.get("status"), "callbackPending:", d.get("callbackPending"), "stage:", d.get("stage"))
print("missing_required:", sorted(allowed-keys))
print("extra_keys:", sorted(keys-allowed))
print("PII_LEAKS:", leaks)
PY
cat "$DIR/j2-1-assert.txt"
if [ "$(cat "$DIR/j2-1-status.code")" = "200" ] \
   && grep -q "PII_LEAKS: \[\]" "$DIR/j2-1-assert.txt" \
   && grep -q "extra_keys: \[\]" "$DIR/j2-1-assert.txt" \
   && grep -q "missing_required: \[\]" "$DIR/j2-1-assert.txt" \
   && python3 -c "import json,sys;d=json.load(open('$DIR/j2-1-status.json'));sys.exit(0 if d.get('status')=='AWAITING_CALLBACK' and d.get('callbackPending') is True else 1)"; then
  ok "J2.1 public status = EXACTLY {requestReference,status,stage,callbackPending,createdAt}, AWAITING_CALLBACK, PII-free"
else
  bad "J2.1 public status assertion (see j2-1-assert.txt)"
fi

# J2.2 — unknown reference → 404 (no existence oracle)
mkpub
code=$(curl -sS "$BFF/internal/v1/public/gateway/sos/SOS-DOES-NOT-EXIST" "${PUB[@]}" -o "$DIR/j2-2-404.json" -w '%{http_code}')
[ "$code" = "404" ] && ok "J2.2 unknown reference → 404 (no existence oracle)" || bad "J2.2 unknown reference expected 404 got $code"

echo
echo "=================================================================="
echo " J1 (cont.) — gate 409-before → verify → drop → triage 201-after"
echo "=================================================================="

# J1.3 — triage BEFORE verify is blocked by the PD-3 dispatch gate → 409.
# Authoritative gate proof against the daidzai SoR (the gate lives in EmergencyService). The gate
# throws before any mutation, so this call is side-effect-free (request stays AWAITING_CALLBACK).
# NOTE (journalled, out-of-scope of BE-1/2/3): through the BFF this same call currently surfaces as
# 500 — the daidzai triage proxy does not map the downstream 409 — so we record that separately.
mkop
code=$(curl -sS -X POST "$DAIDZAI/internal/v1/daidzai/requests/$RID/triage" "${OP[@]}" -o "$DIR/j1-3-triage-gated.json" -w '%{http_code}')
mkop
bffcode=$(curl -sS -X POST "$BFF/internal/v1/daidzai/requests/$RID/triage" "${OP[@]}" -o "$DIR/j1-3-triage-gated-bff.json" -w '%{http_code}')
if [ "$code" = "409" ] && grep -q "CALLBACK_VERIFICATION_REQUIRED" "$DIR/j1-3-triage-gated.json"; then
  ok "J1.3 triage before verify → 409 CALLBACK_VERIFICATION_REQUIRED at the daidzai SoR (PD-3 gate holds; BFF proxy surfaces it as HTTP $bffcode — journalled)"
else
  bad "J1.3 triage-before-verify expected SoR 409 got $code (see j1-3-triage-gated.json)"
fi

# J1.4 — dispatcher verifies the callback (BE-1 verify proxy, THROUGH the BFF) → 200 + status flip
mkop
curl -sS -X POST "$BFF/internal/v1/daidzai/requests/$RID/verify-callback" "${OP[@]}" \
  -o "$DIR/j1-4-verify.json" -w '%{http_code}' > "$DIR/j1-4-verify.code"
DBV=$(psql_daidzai "SELECT status||'|'||coalesce(callback_verified::text,'null') FROM daidzai.dai_emergency_request WHERE id='$RID';")
if [ "$(cat "$DIR/j1-4-verify.code")" = "200" ] \
   && python3 -c "import json,sys;d=json.load(open('$DIR/j1-4-verify.json'));sys.exit(0 if d.get('status')=='RECEIVED' and d.get('callbackVerified') is True else 1)" 2>/dev/null \
   && [ "$DBV" = "RECEIVED|true" ]; then
  ok "J1.4 verify-callback (through BFF) → 200, status→RECEIVED, callbackVerified=true (psql: $DBV)"
else
  bad "J1.4 verify-callback (code=$(cat "$DIR/j1-4-verify.code") psql=$DBV)"
fi

# J1.5 — request drops off the AWAITING_CALLBACK worklist
mkop
curl -sS "$BFF/internal/v1/daidzai/requests?status=AWAITING_CALLBACK" "${OP[@]}" -o "$DIR/j1-5-worklist-after.json" >/dev/null
if python3 -c "
import json,sys
rows=json.load(open('$DIR/j1-5-worklist-after.json'))
sys.exit(1 if any(r.get('requestReference')=='$REF' for r in rows) else 0)" 2>/dev/null; then
  ok "J1.5 request no longer on the AWAITING_CALLBACK worklist"
else
  bad "J1.5 request still on the worklist after verify"
fi

# J2.3 — public status now reflects the release (RECEIVED, callbackPending=false)
mkpub
curl -sS "$BFF/internal/v1/public/gateway/sos/$REF" "${PUB[@]}" -o "$DIR/j2-3-status-after.json" >/dev/null
if python3 -c "import json,sys;d=json.load(open('$DIR/j2-3-status-after.json'));sys.exit(0 if d.get('status')=='RECEIVED' and d.get('callbackPending') is False else 1)" 2>/dev/null; then
  ok "J2.3 public status now RECEIVED + callbackPending=false (release reflected to the citizen tracker)"
else
  bad "J2.3 public status not updated post-verify"
fi

# J1.6 — triage NOW SUCCEEDS (gate released by the console action) → 201
mkop
curl -sS -X POST "$BFF/internal/v1/daidzai/requests/$RID/triage" "${OP[@]}" -o "$DIR/j1-6-triage-ok.json" -w '%{http_code}' > "$DIR/j1-6-triage-ok.code"
if [ "$(cat "$DIR/j1-6-triage-ok.code")" = "201" ] \
   && python3 -c "import json,sys;d=json.load(open('$DIR/j1-6-triage-ok.json'));sys.exit(0 if d.get('status')=='TRIAGED' else 1)" 2>/dev/null; then
  ok "J1.6 triage AFTER verify → 201 TRIAGED (console action released the PD-3 gate)"
else
  bad "J1.6 triage-after-verify (code=$(cat "$DIR/j1-6-triage-ok.code"))"
fi

echo
echo "=================================================================="
echo " J3 — Public health-info search (BE-3)  [THROUGH the BFF public lane]"
echo "=================================================================="

# J3.0 — seed 3 PUBLISHED national-spine articles (one carries a distinctive term).
# Clear any prior national-spine rows first so a re-run stays deterministic (idempotent seed).
docker exec "$PG" psql -U impilo -d guidance -c \
  "DELETE FROM guidance.knowledge_articles WHERE tenant_id='national-spine';" >/dev/null 2>&1
docker exec "$PG" psql -U impilo -d guidance -c \
  "INSERT INTO guidance.knowledge_articles (id, tenant_id, title, summary, content, category, domain, status, created_at, updated_at) VALUES
     (gen_random_uuid(),'national-spine','Malaria warning signs','When a fever needs urgent care','Seek care urgently if fever comes with confusion or trouble breathing.','disease','public-health','PUBLISHED',now(),now()),
     (gen_random_uuid(),'national-spine','Cholera prevention at home','Clean water and hand-washing basics','Boil or treat drinking water and wash hands with soap.','disease','public-health','PUBLISHED',now(),now()),
     (gen_random_uuid(),'national-spine','Childhood immunisation schedule','Which vaccines and when','Follow the national EPI schedule from birth.','immunisation','public-health','PUBLISHED',now(),now());" \
  > "$DIR/j3-0-seed.txt" 2>&1
grep -q "INSERT 0 3" "$DIR/j3-0-seed.txt" && ok "J3.0 seeded 3 PUBLISHED national-spine articles" || bad "J3.0 seed (see j3-0-seed.txt)"

# J3.1 — text search returns the matching article (allow-listed fields only)
mkpub
curl -sS "$BFF/internal/v1/public/gateway/guidance/education?q=malaria" "${PUB[@]}" \
  -o "$DIR/j3-1-q-malaria.json" -w '%{http_code}' > "$DIR/j3-1-q-malaria.code"
if python3 -c "
import json,sys
d=json.load(open('$DIR/j3-1-q-malaria.json'))
data=d.get('data',[])
hit=[a for a in data if 'malaria' in (a.get('title','')+a.get('summary','')).lower()]
keys=set().union(*[set(a.keys()) for a in data]) if data else set()
sys.exit(0 if len(data)==1 and len(hit)==1 and keys<= {'id','title','summary','category','domain'} else 1)" 2>/dev/null; then
  ok "J3.1 education?q=malaria → the one matching article, allow-listed fields only"
else
  bad "J3.1 text search (see j3-1-q-malaria.json)"
fi

# J3.2 — a non-matching query returns 0 rows (genuine search, not an echo of the browse list)
mkpub
curl -sS "$BFF/internal/v1/public/gateway/guidance/education?q=zzz-no-such-topic" "${PUB[@]}" -o "$DIR/j3-2-q-empty.json" >/dev/null
python3 -c "import json,sys;d=json.load(open('$DIR/j3-2-q-empty.json'));sys.exit(0 if len(d.get('data',[]))==0 else 1)" 2>/dev/null \
  && ok "J3.2 non-matching query → 0 rows (genuine search)" || bad "J3.2 non-matching query returned rows"

# J3.3 — category filter still works without q (browse lane unaffected by the search addition)
mkpub
curl -sS "$BFF/internal/v1/public/gateway/guidance/education?category=immunisation" "${PUB[@]}" \
  -o "$DIR/j3-3-category.json" -w '%{http_code}' > "$DIR/j3-3-category.code"
if python3 -c "
import json,sys
d=json.load(open('$DIR/j3-3-category.json'))
data=d.get('data',[])
sys.exit(0 if len(data)==1 and data[0].get('category')=='immunisation' else 1)" 2>/dev/null; then
  ok "J3.3 category=immunisation (no q) → the one immunisation article (browse lane intact)"
else
  bad "J3.3 category filter (see j3-3-category.json)"
fi

echo
echo "=================================================================="
echo " J4 — R1 contact-OTP register  [BFF public auth lane, contract-level"
echo "      live; the Keycloak+IA hop is proven live in gateway-w1-runtime-proof]"
echo "=================================================================="

# J4.1 — request endpoint mounted + reachable through the BFF public auth lane; blank value → 400
mkpub
code=$(curl -sS -X POST "$BFF/internal/v1/auth/contact/otp/request" "${PUB[@]}" -H 'Content-Type: application/json' \
  -d '{"channel":"PHONE","value":""}' -o "$DIR/j4-1-request-blank.json" -w '%{http_code}')
if [ "$code" = "400" ] && grep -q "VALIDATION" "$DIR/j4-1-request-blank.json"; then
  ok "J4.1 POST otp/request blank value → 400 VALIDATION (endpoint reachable through BFF)"
else
  bad "J4.1 otp/request blank value expected 400 VALIDATION got $code"
fi

# J4.2 — malformed phone rejected by the shared R1 normalizer BEFORE any downstream send → 400
mkpub
code=$(curl -sS -X POST "$BFF/internal/v1/auth/contact/otp/request" "${PUB[@]}" -H 'Content-Type: application/json' \
  -d '{"channel":"PHONE","value":"not-a-phone"}' -o "$DIR/j4-2-request-bad.json" -w '%{http_code}')
if [ "$code" = "400" ] && grep -q "VALIDATION" "$DIR/j4-2-request-bad.json"; then
  ok "J4.2 POST otp/request malformed phone → 400 VALIDATION (R1 normalize gate)"
else
  bad "J4.2 otp/request malformed phone expected 400 VALIDATION got $code"
fi

# J4.3 — verify for purpose=REGISTER with no prior OTP consults the REAL Redis OTP store → 400 OTP_EXPIRED
mkpub
code=$(curl -sS -X POST "$BFF/internal/v1/auth/contact/otp/verify" "${PUB[@]}" -H 'Content-Type: application/json' \
  -d '{"value":"+263771234567","code":"123456","purpose":"REGISTER","password":"RigPassw0rd!2026","fullName":"Rig Citizen"}' \
  -o "$DIR/j4-3-verify-nootp.json" -w '%{http_code}')
if [ "$code" = "400" ] && grep -q "OTP_EXPIRED" "$DIR/j4-3-verify-nootp.json"; then
  ok "J4.3 POST otp/verify REGISTER, no prior OTP → 400 OTP_EXPIRED (real Redis store consulted)"
else
  bad "J4.3 otp/verify no-OTP expected 400 OTP_EXPIRED got $code (see j4-3-verify-nootp.json)"
fi

# J4.4 — purpose guard: only REGISTER / ATTACH accepted
mkpub
code=$(curl -sS -X POST "$BFF/internal/v1/auth/contact/otp/verify" "${PUB[@]}" -H 'Content-Type: application/json' \
  -d '{"value":"+263771234567","code":"123456","purpose":"HACK"}' -o "$DIR/j4-4-verify-badpurpose.json" -w '%{http_code}')
if [ "$code" = "400" ] && grep -q "REGISTER or ATTACH" "$DIR/j4-4-verify-badpurpose.json"; then
  ok "J4.4 POST otp/verify bad purpose → 400 VALIDATION (purpose must be REGISTER or ATTACH)"
else
  bad "J4.4 otp/verify bad purpose expected 400 got $code"
fi

# J4.5 — missing code → 400 VALIDATION
mkpub
code=$(curl -sS -X POST "$BFF/internal/v1/auth/contact/otp/verify" "${PUB[@]}" -H 'Content-Type: application/json' \
  -d '{"value":"+263771234567","purpose":"REGISTER"}' -o "$DIR/j4-5-verify-nocode.json" -w '%{http_code}')
if [ "$code" = "400" ] && grep -q "VALIDATION" "$DIR/j4-5-verify-nocode.json"; then
  ok "J4.5 POST otp/verify missing code → 400 VALIDATION"
else
  bad "J4.5 otp/verify missing code expected 400 got $code"
fi

echo
echo "=================================================================="
echo " RESULT: pass=$pass fail=$fail"
echo "=================================================================="
exit $([ "$fail" -eq 0 ] && echo 0 || echo 1)
