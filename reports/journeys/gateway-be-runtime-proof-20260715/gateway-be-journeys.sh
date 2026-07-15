#!/usr/bin/env bash
# Gateway BE-additions runtime proof (run after rig-boot.sh). Writes evidence JSON + a PASS/FAIL
# summary to this directory. Exercises all three BE features end-to-end through the experience-bff.
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"
BFF=http://localhost:28562
GUIDANCE=http://localhost:28560
DAIDZAI=http://localhost:28492   # daidzai SoR (oauth-disabled in rig); BE-1 authenticated list/verify proven here, BFF proxy is unit-tested
PG=gw-be-rig-pg
TENANT=00000000-0000-0000-0000-000000000001   # PUBLIC_DEFAULT_TENANT (BFF hardcodes it for the public SOS write)

pass=0; fail=0
ok()   { echo "PASS  $1"; pass=$((pass+1)); }
bad()  { echo "FAIL  $1"; fail=$((fail+1)); }

# The BFF companion filter requires the 4 mandatory v1.1 headers on EVERY inbound request (the live
# edge synthesizes them for anonymous traffic); "public" means no JWT/actor required, not header-free.
# Fresh request/correlation ids per call to avoid idempotency dedup.
mkpub(){ PUB=(-H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: national-spine"
             -H "X-Request-ID: $(uuidgen)" -H "X-Correlation-ID: $(uuidgen)"
             -H "Idempotency-Key: $(uuidgen)"); }
mkdai(){ DAI=(-H "X-Tenant-ID: $TENANT" -H "X-Pod-ID: national-spine"
             -H "X-Request-ID: $(uuidgen)" -H "X-Correlation-ID: $(uuidgen)"
             -H "Idempotency-Key: $(uuidgen)"
             -H "X-Actor-ID: rig-dispatcher" -H "X-Actor-Type: STAFF" -H "X-Purpose-Of-Use: EMERGENCY_DISPATCH"); }

echo "=================================================================="
echo " CHECK 1 — SOS: create → worklist → public status (PII-free) → verify → worklist drop"
echo "=================================================================="

# 1a — anonymous public SOS write (permitAll)
mkpub
curl -sS -X POST "$BFF/internal/v1/public/gateway/sos" "${PUB[@]}" \
  -H 'Content-Type: application/json' \
  -d '{"callbackNumber":"0771 234 567","emergencyCategory":"MEDICAL","severity":"HIGH","description":"Man collapsed at the market","locationDescription":"Mbare market"}' \
  -o "$DIR/c1-sos-create.json" -w '%{http_code}' > "$DIR/c1-sos-create.code"
REF=$(python3 -c "import json;print(json.load(open('$DIR/c1-sos-create.json')).get('requestReference',''))" 2>/dev/null)
if [ "$(cat "$DIR/c1-sos-create.code")" = "202" ] && [ -n "$REF" ]; then
  ok "1a anonymous SOS captured (202), reference=$REF"
else
  bad "1a anonymous SOS create (code=$(cat "$DIR/c1-sos-create.code") ref=$REF)"; REF=""
fi

# 1b — dispatcher callback worklist (BE-1 list-by-status), daidzai SoR direct
mkdai
curl -sS "${DAI[@]}" "$DAIDZAI/internal/v1/daidzai/requests?status=AWAITING_CALLBACK" \
  -o "$DIR/c1b-worklist.json" -w '%{http_code}' > "$DIR/c1b-worklist.code"
RID=$(python3 -c "
import json
rows=json.load(open('$DIR/c1b-worklist.json'))
print(next((r['id'] for r in rows if r.get('requestReference')=='$REF'), ''))" 2>/dev/null)
if [ -n "$RID" ]; then ok "1b worklist lists the request (id=$RID)"; else bad "1b worklist did not list $REF"; fi

# 1c — anonymous public status-by-reference (permitAll) + PII-free assertion
mkpub
curl -sS "${PUB[@]}" "$BFF/internal/v1/public/gateway/sos/$REF" \
  -o "$DIR/c1c-public-status.json" -w '%{http_code}' > "$DIR/c1c-public-status.code"
python3 - "$DIR/c1c-public-status.json" <<'PY' > "$DIR/c1c-assert.txt" 2>&1
import json,sys
d=json.load(open(sys.argv[1]))
raw=json.dumps(d).lower()
allowed={"requestreference","status","stage","callbackpending","createdat"}
keys=set(k.lower() for k in d.keys())
banned=["callbacknumber","+263","0771","description","market","location","subject","hid-","lat","lng"]
leaks=[b for b in banned if b in raw]
print("keys:", sorted(d.keys()))
print("status:", d.get("status"), "callbackPending:", d.get("callbackPending"), "stage:", d.get("stage"))
print("extra_keys:", sorted(keys-allowed))
print("PII_LEAKS:", leaks)
PY
cat "$DIR/c1c-assert.txt"
if grep -q "PII_LEAKS: \[\]" "$DIR/c1c-assert.txt" \
   && grep -q "extra_keys: \[\]" "$DIR/c1c-assert.txt" \
   && python3 -c "import json;d=json.load(open('$DIR/c1c-public-status.json'));import sys;sys.exit(0 if d.get('status')=='AWAITING_CALLBACK' and d.get('callbackPending') is True else 1)"; then
  ok "1c public status is AWAITING_CALLBACK + callbackPending + PII-free (no callback#/description/location/subject)"
else
  bad "1c public status assertion (see c1c-assert.txt)"
fi

# 1d — dispatcher verifies callback (BE-1 verify), daidzai SoR direct
mkdai
curl -sS -X POST "${DAI[@]}" "$DAIDZAI/internal/v1/daidzai/requests/$RID/verify-callback" \
  -o "$DIR/c1d-verify.json" -w '%{http_code}' > "$DIR/c1d-verify.code"
if python3 -c "import json;d=json.load(open('$DIR/c1d-verify.json'));import sys;sys.exit(0 if d.get('status')=='RECEIVED' and d.get('callbackVerified') is True else 1)" 2>/dev/null; then
  ok "1d verify-callback flipped status→RECEIVED, callbackVerified=true"
else
  bad "1d verify-callback (see c1d-verify.json)"
fi

# 1e — request no longer on the AWAITING_CALLBACK worklist (daidzai SoR direct)
mkdai
curl -sS "${DAI[@]}" "$DAIDZAI/internal/v1/daidzai/requests?status=AWAITING_CALLBACK" \
  -o "$DIR/c1e-worklist-after.json" -w '%{http_code}' > "$DIR/c1e-worklist-after.code"
if python3 -c "
import json,sys
rows=json.load(open('$DIR/c1e-worklist-after.json'))
sys.exit(1 if any(r.get('requestReference')=='$REF' for r in rows) else 0)" 2>/dev/null; then
  ok "1e worklist no longer lists the verified request"
else
  bad "1e request still on AWAITING_CALLBACK worklist after verify"
fi

# 1f — public status now reflects the release (RECEIVED, callbackPending=false)
mkpub
curl -sS "${PUB[@]}" "$BFF/internal/v1/public/gateway/sos/$REF" -o "$DIR/c1f-public-status-after.json" >/dev/null
if python3 -c "import json;d=json.load(open('$DIR/c1f-public-status-after.json'));import sys;sys.exit(0 if d.get('status')=='RECEIVED' and d.get('callbackPending') is False else 1)" 2>/dev/null; then
  ok "1f public status now RECEIVED + callbackPending=false"
else
  bad "1f public status not updated post-verify"
fi

# 1g — unknown reference → 404 (no existence oracle)
mkpub
code=$(curl -sS "${PUB[@]}" "$BFF/internal/v1/public/gateway/sos/SOS-DOES-NOT-EXIST" -o "$DIR/c1g-404.json" -w '%{http_code}')
[ "$code" = "404" ] && ok "1g unknown reference → 404" || bad "1g unknown reference expected 404 got $code"

echo
echo "=================================================================="
echo " CHECK 2 — Guidance: seed PUBLISHED article → public education text search"
echo "=================================================================="

# 2a — seed a PUBLISHED national-spine article
docker exec "$PG" psql -U impilo -d guidance -c \
  "INSERT INTO guidance.knowledge_articles (id, tenant_id, title, summary, content, category, domain, status, created_at, updated_at)
   VALUES (gen_random_uuid(), 'national-spine', 'Malaria warning signs',
           'When a fever needs urgent care', 'Seek care urgently if fever comes with confusion or trouble breathing.',
           'disease', 'public-health', 'PUBLISHED', now(), now());" > "$DIR/c2-seed.txt" 2>&1
grep -q "INSERT 0 1" "$DIR/c2-seed.txt" && ok "2a seeded PUBLISHED article" || bad "2a seed failed (see c2-seed.txt)"

# 2b — anonymous public education text search (permitAll)
mkpub
curl -sS "${PUB[@]}" "$BFF/internal/v1/public/gateway/guidance/education?q=malaria" \
  -o "$DIR/c2b-search.json" -w '%{http_code}' > "$DIR/c2b-search.code"
if python3 -c "
import json,sys
d=json.load(open('$DIR/c2b-search.json'))
data=d.get('data',[])
hit=any('malaria' in (a.get('title','')+a.get('summary','')).lower() for a in data)
# allow-listed topic fields only
keys=set().union(*[set(a.keys()) for a in data]) if data else set()
sys.exit(0 if hit and keys<= {'id','title','summary','category','domain'} else 1)" 2>/dev/null; then
  ok "2b education?q=malaria returned the seeded article (allow-listed fields only)"
else
  bad "2b education text search (see c2b-search.json)"
fi

# 2c — a non-matching query returns no rows (search is real, not echo)
mkpub
curl -sS "${PUB[@]}" "$BFF/internal/v1/public/gateway/guidance/education?q=zzz-no-such-topic" -o "$DIR/c2c-search-empty.json" >/dev/null
python3 -c "import json,sys;d=json.load(open('$DIR/c2c-search-empty.json'));sys.exit(0 if len(d.get('data',[]))==0 else 1)" 2>/dev/null \
  && ok "2c non-matching query returns 0 rows (genuine search)" || bad "2c non-matching query returned rows"

echo
echo "=================================================================="
echo " RESULT: pass=$pass fail=$fail"
echo "=================================================================="
exit $([ "$fail" -eq 0 ] && echo 0 || echo 1)
