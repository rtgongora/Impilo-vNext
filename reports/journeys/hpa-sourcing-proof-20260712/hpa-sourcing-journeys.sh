#!/usr/bin/env bash
# HPA requirement-sourcing journeys — tuso ↔ msika marketplace link proof.
set -u
TUSO=http://localhost:28084
MSIKA=http://localhost:28086
TEN=00000000-0000-4000-8000-000000000001
EV=${EV:-/tmp/hpa-sourcing-evidence}
mkdir -p "$EV"
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
PSQL() { docker exec hpa-proof-pg psql -U impilo -d "$1" -tAc "$2"; }
hdr() { # role [actor]
  local role=$1; local actor=${2:-sourcing-proof-$1}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine \
-H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Idempotency-Key:$(uuidgen) \
-H X-Actor-ID:$actor -H X-Actor-Type:$role -H X-Purpose-Of-Use:MARKETPLACE"
}
jqpy() { python3 -c "import json,sys;d=json.load(sys.stdin);print($1)"; }
# msika catalog/listing controllers wrap payloads in {success,data}; sourcing does not.
mid() { python3 -c "import json,sys;r=json.load(sys.stdin);d=r.get('data') if isinstance(r,dict) and 'data' in r else r;print(d.get('$1') or d.get('id') or '')"; }

# ═══ SJ1 — Batch resolve: mapped equipment → category; medicine → restricted; staffing → absent
say "SJ1: requirement resolution (partial by design)"
EQCODE=$(PSQL msika "SELECT requirement_code FROM msika_requirement_sourcing_map WHERE status='APPROVED' AND sourcing_category_code NOT IN ('MEDICINES_GENERAL','CONTROLLED_MEDICINES') LIMIT 1")
MEDCODE=$(PSQL msika "SELECT requirement_code FROM msika_requirement_sourcing_map m JOIN msika_sourcing_categories c ON c.code=m.sourcing_category_code WHERE c.restricted LIMIT 1")
R=$(curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/sourcing/resolve \
  -d "{\"codes\":[\"$EQCODE\",\"$MEDCODE\",\"THR-099\",\"NOPE-999\"]}")
echo "$R" > "$EV/sj1-resolve.json"
HAS_EQ=$(echo "$R" | jqpy "1 if '$EQCODE' in d else 0")
RESTR=$(echo "$R" | jqpy "d.get('$MEDCODE',{}).get('restricted')")
CRED=$(echo "$R" | jqpy "d.get('$MEDCODE',{}).get('requiredCredential')")
ABSENT=$(echo "$R" | jqpy "0 if 'NOPE-999' in d or 'THR-099' in d else 1")
[ "$HAS_EQ" = "1" ] && ok "mapped equipment code $EQCODE resolves to a category" || bad "eq resolve"
[ "$RESTR" = "True" ] && [ "$CRED" = "PHARMACEUTICAL_WHOLESALER_CERTIFICATE" ] && ok "medicine code $MEDCODE restricted + credential requirement" || bad "med restricted=$RESTR cred=$CRED"
[ "$ABSENT" = "1" ] && ok "unmapped/unknown codes absent (partiality preserved)" || bad "unmapped leaked in"
NCAT=$(curl -sS $(hdr MARKETPLACE_OPERATOR) $MSIKA/v1/sourcing/categories | jqpy "len(d)")
[ "$NCAT" -ge 40 ] && ok "$NCAT active sourcing categories" || bad "categories=$NCAT"

# ═══ SJ2 — Category listing search + live count
say "SJ2: category-filtered listings + counts"
EQCAT=$(PSQL msika "SELECT sourcing_category_code FROM msika_requirement_sourcing_map WHERE requirement_code='$EQCODE'")
CATALOG=$(curl -sS $(hdr CATALOG_ADMIN) -X POST $MSIKA/v1/catalogs -d '{"name":"Proof Catalog","scope":"NATIONAL"}' | mid catalogId)
ITEM=$(curl -sS $(hdr CATALOG_ADMIN) -X POST $MSIKA/v1/catalogs/$CATALOG/items \
  -d "{\"kind\":\"PRODUCT\",\"canonicalCode\":\"PROOF-AUTOCLAVE\",\"displayName\":\"Bench autoclave 23L\",\"sourcingCategoryCode\":\"$EQCAT\"}" | mid itemId)
SF=$(curl -sS $(hdr MARKETPLACE_SELLER) -X POST $MSIKA/v1/storefronts \
  -d '{"sellerType":"VENDOR","sellerId":"VEND-PROOF-1","displayName":"Proof Medical Supplies"}' | mid storefrontId)
LST=$(curl -sS $(hdr MARKETPLACE_SELLER) -X POST $MSIKA/v1/listings \
  -d "{\"storefrontId\":\"$SF\",\"catalogItemId\":\"$ITEM\",\"sellerType\":\"VENDOR\",\"sellerId\":\"VEND-PROOF-1\",\"title\":\"Bench autoclave 23L\",\"summary\":\"Proof listing\"}" | mid listingId)
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$LST/submit >/dev/null
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$LST/approve -d '{"notes":"ok"}' >/dev/null
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$LST/publish >/dev/null
FOUND=$(curl -sS $(hdr CITIZEN) "$MSIKA/v1/listings/search?category=$EQCAT" | python3 -c "import json,sys;r=json.load(sys.stdin);d=r.get('data') if isinstance(r,dict) and 'data' in r else r;print(d.get('totalElements') or len(d.get('content',[])))")
[ "$FOUND" -ge 1 ] && ok "category search '$EQCAT' finds the published listing" || bad "category search found=$FOUND"
CNT=$(curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/sourcing/resolve -d "{\"codes\":[\"$EQCODE\"]}" | jqpy "d['$EQCODE']['publishedListingCount']")
[ "$CNT" -ge 1 ] && ok "resolution carries live published-listing count ($CNT)" || bad "count=$CNT"

# ═══ SJ3 — Restricted publish gate: blocked unverified → verified after HPA licence check
say "SJ3: restricted-category licence gate"
MEDCAT=$(PSQL msika "SELECT sourcing_category_code FROM msika_requirement_sourcing_map WHERE requirement_code='$MEDCODE'")
MITEM=$(curl -sS $(hdr CATALOG_ADMIN) -X POST $MSIKA/v1/catalogs/$CATALOG/items \
  -d "{\"kind\":\"PRODUCT\",\"canonicalCode\":\"PROOF-ADRENALINE\",\"displayName\":\"Adrenaline 1mg ampoules\",\"sourcingCategoryCode\":\"$MEDCAT\"}" | mid itemId)
SF2=$(curl -sS $(hdr MARKETPLACE_SELLER) -X POST $MSIKA/v1/storefronts \
  -d '{"sellerType":"VENDOR","sellerId":"VEND-PHARMA-1","displayName":"Proof Pharma Wholesale"}' | mid storefrontId)
MLST=$(curl -sS $(hdr MARKETPLACE_SELLER) -X POST $MSIKA/v1/listings \
  -d "{\"storefrontId\":\"$SF2\",\"catalogItemId\":\"$MITEM\",\"sellerType\":\"VENDOR\",\"sellerId\":\"VEND-PHARMA-1\",\"title\":\"Adrenaline ampoules\",\"summary\":\"Restricted proof\"}" | mid listingId)
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$MLST/submit >/dev/null
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$MLST/approve -d '{"notes":"ok"}' >/dev/null
DENY=$(curl -sS -o /dev/null -w "%{http_code}" $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$MLST/publish)
AUDITDENY=$(PSQL msika "SELECT count(*) FROM msika_listing_audit WHERE listing_id='$MLST' AND action='POLICY_DENIED'")
[ "$DENY" -ge 400 ] && [ "$AUDITDENY" -ge 1 ] && ok "unverified seller blocked from restricted publish (HTTP $DENY, POLICY_DENIED audited)" || bad "deny=$DENY audit=$AUDITDENY"
# verify seller via HPA certificate truth (tuso J2-style public-route wholesaler)
R2=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications \
  -d '{"applicationType":"NEW_REGISTRATION","catalogueCode":"INITIAL_REGISTRATION_PUBLIC_MISSION_LA","pathway":"PUBLIC","applicantName":"Proof Pharma Wholesale","facilityName":"Proof Pharma Wholesale","facilityType":"PHARMACEUTICAL_WHOLESALER","province":"Harare"}')
WFAC=$(echo "$R2" | jqpy "d['data']['master']['facilityId']")
WAPP=$(echo "$R2" | jqpy "d['data']['applications'][0]['applicationId']")
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/documents -d "{\"facilityId\":$WFAC,\"applicationId\":\"$WAPP\",\"documentType\":\"PREMISES_PLAN\",\"fileReference\":\"doc://proof\"}" >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications/$WAPP/submit >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/committee-reviews \
  -d "{\"facilityId\":$WFAC,\"applicationId\":\"$WAPP\",\"committeeType\":\"REGISTRATION_COMMITTEE\",\"decision\":\"APPROVED\",\"issueCertificate\":true}" >/dev/null
WCODE=$(PSQL tuso "SELECT verification_code FROM tuso.facility_certificate WHERE facility_id=$WFAC AND status='ACTIVE'")
[ -n "$WCODE" ] && ok "wholesaler certificate issued with verification code" || bad "no wholesaler cert"
# public verify → eligible? then storefront verify (rig has no BFF: perform the same two steps the BFF composes)
PUB=$(curl -sS $TUSO/v1/public/facilities/certificates/verify/$WCODE)
echo "$PUB" > "$EV/sj3-public-verify.json"
WOK=$(echo "$PUB" | jqpy "1 if d.get('verified') and d.get('status')=='ACTIVE' and 'WHOLESAL' in (str(d.get('facilityType',''))+str(d.get('certificateType',''))).upper() else 0")
[ "$WOK" = "1" ] && ok "HPA public verify proves ACTIVE wholesaler scope" || bad "verify=$(echo $PUB|head -c 120)"
curl -sS $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/storefronts/$SF2/verify \
  -d "{\"verificationStatus\":\"VERIFIED\",\"notes\":\"Wholesaler licence verified via HPA public register [method=HPA_PUBLIC_CERTIFICATE_VERIFY code=$WCODE]\"}" >/dev/null
PUBOK=$(curl -sS -o /dev/null -w "%{http_code}" $(hdr MARKETPLACE_OPERATOR) -X POST $MSIKA/v1/listings/$MLST/publish)
[ "$PUBOK" = "200" ] && ok "verified wholesaler publishes restricted listing" || bad "post-verify publish=$PUBOK"

# ═══ SJ4 — Demand signal: aggregate counts, small-cell floor, no identifiers
say "SJ4: aggregate demand signal"
# seed 3+ open findings on distinct facilities for one requirement code (small-cell floor = 3)
for i in 1 2 3; do
  RF=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications \
    -d "{\"applicationType\":\"NEW_REGISTRATION\",\"catalogueCode\":\"INITIAL_REGISTRATION_PRIVATE\",\"pathway\":\"PRIVATE\",\"applicantName\":\"Demand Proof $i\",\"facilityName\":\"Demand Proof Clinic $i\",\"facilityType\":\"CLINIC\",\"province\":\"Harare\"}")
  DFAC=$(echo "$RF" | jqpy "d['data']['master']['facilityId']")
  DINS=$(curl -sS $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/inspections \
    -d "{\"facilityId\":$DFAC,\"inspectionType\":\"INITIAL\"}" | jqpy "d['data']['inspectionId']")
  DVIS=$(curl -sS $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/inspections/$DINS/visits -d '{"mode":"ONSITE"}' | jqpy "d['data']['visitId']")
  curl -sS $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/visits/$DVIS/responses \
    -d "{\"items\":[{\"itemCode\":\"$EQCODE\",\"itemSection\":\"Equipment\",\"requirementText\":\"Required equipment\",\"response\":\"NON_COMPLIANT\",\"comments\":\"Missing\"}]}" >/dev/null
done
SIG=$(curl -sS $(hdr HPA_REGISTRAR) $TUSO/v1/internal/facility-registry/demand-signal)
echo "$SIG" > "$EV/sj4-demand-signal.json"
DCOUNT=$(echo "$SIG" | jqpy "next((r['count'] for r in d['data']['openFindings'] if r['checklistItemCode']=='$EQCODE'), 0)")
[ "$DCOUNT" -ge 3 ] && ok "demand signal aggregates $DCOUNT open findings for $EQCODE by province" || bad "signal count=$DCOUNT"
SMALL=$(echo "$SIG" | jqpy "sum(1 for r in d['data']['openFindings'] if r['count'] < d['data']['smallCellFloor'])")
[ "$SMALL" = "0" ] && ok "small-cell suppression holds (floor $(echo "$SIG" | jqpy "d['data']['smallCellFloor']"))" || bad "small cells leaked=$SMALL"

# ═══ SJ5 — NEGATIVE: no facility identifiers anywhere in supply-side payloads
say "SJ5: negative — supplier payloads carry no facility identifiers"
LEAK=$(echo "$SIG" | python3 -c "
import json,sys,re
raw=sys.stdin.read()
d=json.loads(raw)['data']
keys=set()
def walk(o):
    if isinstance(o,dict):
        for k,v in o.items(): keys.add(k.lower()); walk(v)
    elif isinstance(o,list):
        for v in o: walk(v)
walk(d)
bad=[k for k in keys if re.search(r'facilityid|applicationid|facilityname|healthid|practitioner|certificateid',k)]
print(len(bad))")
[ "$LEAK" = "0" ] && ok "no identifier-shaped keys in demand-signal payload" || bad "leaked keys=$LEAK"
grep -qE "Demand Proof Clinic" "$EV/sj4-demand-signal.json" && bad "facility NAME leaked into signal" || ok "no facility names in signal"

echo "RESULT: PASS=$PASS FAIL=$FAIL" | tee -a "$EV/journal.txt"
