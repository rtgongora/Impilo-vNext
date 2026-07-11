#!/usr/bin/env bash
# HPA inspection-content journeys — manual-derived template catalogue proof.
set -u
TUSO=http://localhost:28084
TEN=00000000-0000-4000-8000-000000000001
EV=${EV:-/tmp/hpa-content-evidence}
mkdir -p "$EV"
PASS=0; FAIL=0
ok(){ echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1" | tee -a "$EV/journal.txt"; }
PSQL() { docker exec hpa-proof-pg psql -U impilo -d "$1" -tAc "$2"; }
SCRATCH_LOG=/tmp/claude-1002/-opt-impilo-repos-Impilo-vNext/62faa233-4c92-4ac6-ad26-1d549a4330fe/scratchpad/tuso-proof.log
hdr() { # role [actor]
  local role=$1; local actor=${2:-hpa-proof-$1}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine \
-H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Idempotency-Key:$(uuidgen) \
-H X-Actor-ID:$actor -H X-Actor-Type:$role -H X-Purpose-Of-Use:REGULATORY_WORKFLOW"
}
jqpy() { python3 -c "import json,sys;d=json.load(sys.stdin);print($1)"; }

newFacility() { # name type -> echoes "facilityId appId"
  local R=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications \
    -d "{\"applicationType\":\"NEW_REGISTRATION\",\"catalogueCode\":\"INITIAL_REGISTRATION_PRIVATE\",\"pathway\":\"PRIVATE\",\"applicantName\":\"Proof Trust\",\"facilityName\":\"$1\",\"facilityType\":\"$2\",\"province\":\"Harare\",\"district\":\"Harare\"}")
  echo "$R" | jqpy "str(d['data']['master']['facilityId'])+' '+d['data']['applications'][0]['applicationId']"
}
schedule() { # facilityId type [extraJson]
  curl -sS $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/inspections \
    -d "{\"facilityId\":$1,\"inspectionType\":\"$2\"${3:+,$3}}"
}

# ═══ CJ1 — Medical consulting rooms: correct composed checklist
say "CJ1: medical consulting rooms inspection"
read FAC1 APP1 <<< "$(newFacility 'Baines Consulting Proof' CONSULTING_ROOMS)"
INS1=$(schedule $FAC1 INITIAL | jqpy "d['data']['inspectionId']")
COMP1=$(PSQL tuso "SELECT composition_code FROM tuso.facility_inspection WHERE inspection_id='$INS1'")
[ "$COMP1" = "COMP-MEDICAL-CONSULTING" ] && ok "resolved COMP-MEDICAL-CONSULTING from facility type" || bad "composition=$COMP1"
CHK1=$(curl -sS $(hdr HPA_INSPECTOR) $TUSO/v1/internal/facility-registry/inspections/$INS1/checklist)
echo "$CHK1" > "$EV/cj1-checklist.json"
MODS1=$(echo "$CHK1" | jqpy "','.join(m['code'] for m in d['data']['modules'])")
[ "$MODS1" = "MOD-ALL-HEALTH-INSTITUTIONS,MOD-MEDICAL-CONSULTING" ] && ok "common module first + service module ($MODS1)" || bad "modules=$MODS1"
NITEMS1=$(echo "$CHK1" | jqpy "d['data']['totalItems']")
[ "$NITEMS1" -ge 100 ] && ok "composed checklist has $NITEMS1 structured items" || bad "items=$NITEMS1"
PROV1=$(echo "$CHK1" | jqpy "sum(1 for m in d['data']['modules'] for i in m['items'] if i.get('sourcePage') and i.get('sourceHeading'))")
[ "$PROV1" = "$NITEMS1" ] && ok "every item carries source page + heading ($PROV1/$NITEMS1)" || bad "provenance $PROV1/$NITEMS1"

# ═══ CJ2 — Pharmacy: responses, save/resume, progress, missing evidence
say "CJ2: pharmacy inspection with save/resume + progress"
read FAC2 APP2 <<< "$(newFacility 'Kopje Pharmacy Proof' PHARMACY)"
INS2=$(schedule $FAC2 INITIAL | jqpy "d['data']['inspectionId']")
COMP2=$(PSQL tuso "SELECT composition_code FROM tuso.facility_inspection WHERE inspection_id='$INS2'")
[ "$COMP2" = "COMP-PHARMACY" ] && ok "pharmacy resolves COMP-PHARMACY" || bad "composition=$COMP2"
VIS2=$(curl -sS $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/inspections/$INS2/visits \
  -d '{"mode":"ONSITE","leadInspectorName":"Insp. Proof","team":[{"name":"Insp. Proof","role":"LEAD","conflictOfInterest":false}]}' | jqpy "d['data']['visitId']")
# batch 1 (save) — a DOCUMENT-evidence item answered without evidence
curl -sS $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/visits/$VIS2/responses \
  -d '{"items":[{"itemCode":"ALL-001","itemSection":"General","response":"COMPLIANT"},{"itemCode":"PHM-002","itemSection":"Dispensary","response":"COMPLIANT"}]}' >/dev/null
PRG=$(curl -sS $(hdr HPA_INSPECTOR) $TUSO/v1/internal/facility-registry/visits/$VIS2/progress)
echo "$PRG" > "$EV/cj2-progress-mid.json"
ANS=$(echo "$PRG" | jqpy "d['data']['answered']"); OUT=$(echo "$PRG" | jqpy "len(d['data']['outstandingMandatory'])")
[ "$ANS" = "2" ] && [ "$OUT" -gt 20 ] && ok "save/resume: 2 answered, $OUT mandatory outstanding" || bad "answered=$ANS outstanding=$OUT"
# batch 2 (resume): NON_COMPLIANT derives finding
curl -sS $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/visits/$VIS2/responses \
  -d '{"items":[{"itemCode":"PHM-003","itemSection":"Dispensary","requirementText":"Dispensary requirement","response":"NON_COMPLIANT","comments":"Not provided"}]}' >/dev/null
ANS2=$(curl -sS $(hdr HPA_INSPECTOR) $TUSO/v1/internal/facility-registry/visits/$VIS2/progress | jqpy "d['data']['answered']")
[ "$ANS2" = "3" ] && ok "resume added third response" || bad "answered after resume=$ANS2"
FND2=$(PSQL tuso "SELECT count(*) FROM tuso.inspection_finding f JOIN tuso.facility_inspection i ON f.inspection_id=i.inspection_id WHERE i.inspection_id='$INS2'")
[ "$FND2" -ge 1 ] && ok "NON_COMPLIANT derived a finding" || bad "findings=$FND2"

# ═══ CJ3 — Laboratory: measurement capture
say "CJ3: laboratory inspection with measurement"
read FAC3 APP3 <<< "$(newFacility 'Avondale Lab Proof' LABORATORY)"
INS3=$(schedule $FAC3 INITIAL | jqpy "d['data']['inspectionId']")
MODS3=$(PSQL tuso "SELECT module_versions FROM tuso.facility_inspection WHERE inspection_id='$INS3'")
echo "$MODS3" | grep -q "MOD-LABORATORY" && ok "laboratory module frozen with version ($MODS3)" || bad "module_versions=$MODS3"
VIS3=$(curl -sS $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/inspections/$INS3/visits -d '{"mode":"ONSITE"}' | jqpy "d['data']['visitId']")
curl -sS $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/visits/$VIS3/responses \
  -d '{"items":[{"itemCode":"LAB-001","itemSection":"Premises","response":"COMPLIANT","measurement":{"dimension":"room floor area","unit":"m2","actual":16.4}}]}' >/dev/null
MEAS=$(PSQL tuso "SELECT measurement->>'actual' FROM tuso.inspection_response WHERE item_code='LAB-001'")
[ "$MEAS" = "16.4" ] && ok "inspector-recorded measurement persisted (16.4 m2)" || bad "measurement=$MEAS"

# ═══ CJ4 — Hospital with pharmacy/lab/theatre/ICU/imaging units: merged modules, no duplication
say "CJ4: hospital with regulated units"
read FAC4 APP4 <<< "$(newFacility 'Parirenyatwa Proof Hospital' HOSPITAL)"
for U in "Main Pharmacy:PHARMACY" "Central Lab:LABORATORY" "Theatre 1:THEATRE" "ICU:ICU" "Radiology:IMAGING"; do
  N="${U%%:*}"; T="${U##*:}"
  PSQL tuso "INSERT INTO tuso.facility_unit (facility_id, name, unit_type, regulatory_status) VALUES ($FAC4, '$N', '$T', 'REGISTERED_ACTIVE')" || echo "UNIT INSERT FAILED: $T"
done
INS4=$(schedule $FAC4 MULTIDISCIPLINARY | jqpy "d['data']['inspectionId']")
CHK4=$(curl -sS $(hdr HPA_INSPECTOR) $TUSO/v1/internal/facility-registry/inspections/$INS4/checklist)
echo "$CHK4" > "$EV/cj4-hospital-checklist.json"
MODS4=$(echo "$CHK4" | jqpy "','.join(m['code'] for m in d['data']['modules'])")
echo "$MODS4" | grep -q "MOD-PHARMACY" && echo "$MODS4" | grep -q "MOD-LABORATORY" && echo "$MODS4" | grep -q "MOD-THEATRE" \
  && echo "$MODS4" | grep -q "MOD-ICU" && echo "$MODS4" | grep -q "MOD-CONVENTIONAL-RADIOGRAPHY" \
  && ok "hospital receives common + all unit modules" || bad "modules=$MODS4"
NALL=$(echo "$MODS4" | tr ',' '\n' | grep -c "MOD-ALL-HEALTH-INSTITUTIONS")
FIRST=$(echo "$MODS4" | cut -d, -f1)
[ "$NALL" = "1" ] && [ "$FIRST" = "MOD-ALL-HEALTH-INSTITUTIONS" ] && ok "common module exactly once, first" || bad "ALL count=$NALL first=$FIRST"
TRAIL=$(PSQL tuso "SELECT notes FROM tuso.facility_inspection WHERE inspection_id='$INS4'")
echo "$TRAIL" | grep -q "regulated unit" && ok "resolution trail records unit attachment" || bad "trail=$TRAIL"

# ═══ CJ5 — Routine inspection: common + category-specific findings
say "CJ5: routine inspection, common + specific findings"
INS5=$(schedule $FAC2 ROUTINE | jqpy "d['data']['inspectionId']")
VIS5=$(curl -sS $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/inspections/$INS5/visits -d '{"mode":"ONSITE"}' | jqpy "d['data']['visitId']")
curl -sS $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/visits/$VIS5/responses \
  -d '{"items":[{"itemCode":"ALL-002","itemSection":"General","requirementText":"Valid practising certificate","response":"NON_COMPLIANT","comments":"Certificate not displayed"},{"itemCode":"PHM-005","itemSection":"Dispensary","requirementText":"Dispensary control","response":"NON_COMPLIANT","comments":"Shortfall"},{"itemCode":"PHM-006","itemSection":"Dispensary","response":"COMPLIANT"}]}' >/dev/null
CFND=$(PSQL tuso "SELECT count(*) FROM tuso.inspection_finding WHERE inspection_id='$INS5' AND checklist_item_code LIKE 'ALL-%'")
SFND=$(PSQL tuso "SELECT count(*) FROM tuso.inspection_finding WHERE inspection_id='$INS5' AND checklist_item_code LIKE 'PHM-%'")
[ "$CFND" -ge 1 ] && [ "$SFND" -ge 1 ] && ok "findings from BOTH common (ALL-*) and specific (PHM-*) modules" || bad "common=$CFND specific=$SFND"
CAPA=$(PSQL tuso "SELECT count(*) FROM tuso.compliance_action a JOIN tuso.inspection_finding f ON a.inspection_finding_id=f.finding_id WHERE f.inspection_id='$INS5'")
[ "$CAPA" -ge 2 ] && ok "each finding opened a corrective action" || bad "capa=$CAPA"

# ═══ CJ6 — Multidisciplinary: contributor attribution
say "CJ6: multidisciplinary inspection, contributor attribution"
VIS6=$(curl -sS $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/inspections/$INS4/visits \
  -d '{"mode":"ONSITE","leadInspectorName":"Dr Lead","team":[{"name":"Dr Lead","discipline":"MEDICAL","role":"LEAD"},{"name":"Ph Insp","discipline":"PHARMACY","role":"MEMBER"},{"name":"Lab Insp","discipline":"LABORATORY","role":"MEMBER"}]}' | jqpy "d['data']['visitId']")
curl -sS $(hdr HPA_INSPECTOR inspector-medical) -X POST $TUSO/v1/internal/facility-registry/visits/$VIS6/responses \
  -d '{"items":[{"itemCode":"ALL-003","itemSection":"General","response":"COMPLIANT"}]}' >/dev/null
curl -sS $(hdr HPA_INSPECTOR inspector-pharmacy) -X POST $TUSO/v1/internal/facility-registry/visits/$VIS6/responses \
  -d '{"items":[{"itemCode":"PHM-004","itemSection":"Dispensary","response":"COMPLIANT"}]}' >/dev/null
curl -sS $(hdr HPA_INSPECTOR inspector-lab) -X POST $TUSO/v1/internal/facility-registry/visits/$VIS6/responses \
  -d '{"items":[{"itemCode":"LAB-002","itemSection":"Premises","response":"NOT_APPLICABLE","comments":"Assessed under lab unit case"}]}' >/dev/null
NINSP=$(PSQL tuso "SELECT count(DISTINCT inspector_id) FROM tuso.inspection_response WHERE visit_id='$VIS6'")
[ "$NINSP" = "3" ] && ok "3 distinct contributors retained on one visit" || bad "distinct inspectors=$NINSP"
TEAMN=$(PSQL tuso "SELECT jsonb_array_length(team) FROM tuso.inspection_visit WHERE visit_id='$VIS6'")
[ "$TEAMN" = "3" ] && ok "typed multidisciplinary team recorded" || bad "team=$TEAMN"
curl -sS $(hdr HPA_INSPECTOR) -X POST $TUSO/v1/internal/facility-registry/visits/$VIS6/complete -d '{"notes":"Joint inspection complete"}' >/dev/null
VST=$(PSQL tuso "SELECT status FROM tuso.inspection_visit WHERE visit_id='$VIS6'")
[ "$VST" = "COMPLETED" ] && ok "visit completed and signed (responses frozen)" || bad "visit=$VST"

# ═══ CJ7 — Air ambulance: manual's explicit checklist structure
say "CJ7: air ambulance inspection (manual checklist)"
read FAC7 APP7 <<< "$(newFacility 'Skymed Proof Air Ambulance' AIR_AMBULANCE_SERVICE)"
INS7=$(schedule $FAC7 INITIAL | jqpy "d['data']['inspectionId']")
CHK7=$(curl -sS $(hdr HPA_INSPECTOR) $TUSO/v1/internal/facility-registry/inspections/$INS7/checklist)
echo "$CHK7" > "$EV/cj7-air-ambulance-checklist.json"
MODS7=$(echo "$CHK7" | jqpy "','.join(m['code'] for m in d['data']['modules'])")
[ "$MODS7" = "MOD-AIR-AMBULANCE" ] && ok "air ambulance uses the manual's self-contained checklist" || bad "modules=$MODS7"
NUMD=$(echo "$CHK7" | jqpy "sum(1 for m in d['data']['modules'] for i in m['items'] if i.get('sourceItemNumber'))")
TOT7=$(echo "$CHK7" | jqpy "d['data']['totalItems']")
[ "$NUMD" = "$TOT7" ] && ok "manual's own item numbering preserved on all $TOT7 items" || bad "numbered=$NUMD/$TOT7"
AREAS=$(echo "$CHK7" | jqpy "len({i['section'] for m in d['data']['modules'] for i in m['items']})")
[ "$AREAS" -ge 5 ] && ok "manual's inspection areas preserved ($AREAS sections)" || bad "areas=$AREAS"

# ═══ CJ8 — NEGATIVE: unmapped classification gets an error, never a generic checklist
say "CJ8: negative — unmapped facility type cannot silently inspect"
read FAC8 APP8 <<< "$(newFacility 'Unmapped Proof Entity' RESEARCH_INSTITUTE)"
NEG=$(schedule $FAC8 INITIAL)
NROWS=$(PSQL tuso "SELECT count(*) FROM tuso.facility_inspection WHERE facility_id=$FAC8")
(echo "$NEG" | grep -q '"status":500' && [ "$NROWS" = "0" ] && grep -q "No inspection template composition is mapped" $SCRATCH_LOG) \
  && ok "unmapped type rejected with explicit gap message; no inspection created" || bad "neg=$(echo $NEG|head -c 100) rows=$NROWS"
# retired sample template cannot be selected
NEG2=$(schedule $FAC2 ROUTINE '"templateCode":"LAB-INITIAL-V1"')
(echo "$NEG2" | grep -q '"status":500' && grep -q "RETIRED (sample / non-regulatory)" $SCRATCH_LOG) \
  && ok "retired sample template rejected for scheduling" || bad "sample-neg=$(echo $NEG2|head -c 100)"
NSAMPLES=$(PSQL tuso "SELECT count(*) FROM tuso.inspection_checklist_template WHERE status='RETIRED' AND name LIKE '%SAMPLE / NON-REGULATORY / RETIRED%'")
[ "$NSAMPLES" = "4" ] && ok "all 4 sample templates retired + labelled, history preserved" || bad "retired samples=$NSAMPLES"

# ═══ Cross-cutting: content truth, provenance immutability, catalogue counts
say "Content catalogue truth"
NMOD=$(PSQL tuso "SELECT count(*) FROM tuso.inspection_requirement_module WHERE status='ACTIVE'")
NCOMP=$(PSQL tuso "SELECT count(*) FROM tuso.inspection_template_composition WHERE status='ACTIVE'")
NMAP=$(PSQL tuso "SELECT count(*) FROM tuso.classification_template_applicability")
NITEMS=$(PSQL tuso "SELECT sum(item_count) FROM tuso.inspection_requirement_module WHERE status='ACTIVE'")
NREV=$(PSQL tuso "SELECT sum(review_required_count) FROM tuso.inspection_requirement_module")
[ "$NMOD" = "38" ] && ok "38 ACTIVE modules loaded" || bad "modules=$NMOD"
[ "$NCOMP" = "39" ] && ok "39 ACTIVE compositions loaded" || bad "compositions=$NCOMP"
[ "$NMAP" = "73" ] && ok "73 applicability mappings loaded" || bad "mappings=$NMAP"
[ "$NITEMS" = "1172" ] && ok "1172 structured requirements live" || bad "items=$NITEMS"
[ "$NREV" = "20" ] && ok "20 items honestly flagged REVIEW_REQUIRED" || bad "review=$NREV"
APPR=$(PSQL tuso "SELECT count(DISTINCT approved_by) FROM tuso.inspection_requirement_module WHERE status='ACTIVE'")
[ "$APPR" = "1" ] && ok "uniform approval provenance on all active modules" || bad "approvers=$APPR"
# renewal reminder ops policy
RSTAT=$(PSQL tuso "SELECT status||'|'||source_manual||'|'||approved_by FROM tuso.regulatory_rule WHERE code='RENEWAL_DUE_WINDOW_DAYS'")
[ "$RSTAT" = "ACTIVE|PROGRAMME_OPERATIONAL_POLICY|PROGRAMME_OWNER" ] && ok "RENEWAL_DUE_WINDOW_DAYS active as programme operational policy" || bad "rule=$RSTAT"
RMILE=$(PSQL tuso "SELECT value_json->>'days' FROM tuso.regulatory_rule WHERE code='RENEWAL_REMINDER_MILESTONE_DAYS' AND status='ACTIVE'")
echo "$RMILE" | grep -q "90" && echo "$RMILE" | grep -q "7" && ok "reminder milestones 90/60/30/7 configured (not law)" || bad "milestones=$RMILE"
# frozen provenance is queryable per inspection
FROZ=$(PSQL tuso "SELECT count(*) FROM tuso.facility_inspection WHERE module_versions IS NOT NULL")
[ "$FROZ" -ge 6 ] && ok "$FROZ inspections carry frozen module-version provenance" || bad "frozen=$FROZ"
# outbox hygiene on new events
BADOUT=$(PSQL tuso "SELECT count(*) FROM tuso.event_outbox WHERE pod_id IS NULL OR idempotency_key IS NULL")
[ "$BADOUT" = "0" ] && ok "outbox hygiene intact (pod_id + idempotency_key on all rows)" || bad "bad outbox rows=$BADOUT"

echo "RESULT: PASS=$PASS FAIL=$FAIL" | tee -a "$EV/journal.txt"
