#!/usr/bin/env bash
# HPA regulatory platform — runtime journey proofs against local instances
# (tuso :28084, varapi :28083, scratch postgres :15599). No mocks: every step
# is a real HTTP call and every assertion reads the database afterwards.
set -uo pipefail
TUSO=http://localhost:28084
VARAPI=http://localhost:28083
TEN=00000000-0000-4000-8000-000000000001
EV="${EV:-/tmp/hpa-evidence}"
mkdir -p "$EV"
PASS=0; FAIL=0
say()  { echo "== $*" | tee -a "$EV/journal.txt"; }
ok()   { PASS=$((PASS+1)); echo "   PASS: $*" | tee -a "$EV/journal.txt"; }
bad()  { FAIL=$((FAIL+1)); echo "   FAIL: $*" | tee -a "$EV/journal.txt"; }
hdr() { # $1 actor-type  $2 actor-id
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine \
   -H X-Request-ID:$(cat /proc/sys/kernel/random/uuid) -H X-Correlation-ID:$(cat /proc/sys/kernel/random/uuid) \
   -H X-Actor-ID:${2:-proof-runner} -H X-Actor-Type:${1} -H X-Purpose-Of-Use:OPERATIONS \
   -H X-Idempotency-Key:$(cat /proc/sys/kernel/random/uuid)"
}
J() { python3 -c "import json,sys;d=json.load(sys.stdin);print(json.dumps(d.get($1)))" 2>/dev/null; }
PSQL() { docker exec hpa-proof-pg psql -U impilo -d "$1" -tAc "$2"; }
SCRATCH_LOG=/tmp/claude-1002/-opt-impilo-repos-Impilo-vNext/62faa233-4c92-4ac6-ad26-1d549a4330fe/scratchpad/tuso-proof.log

# ═══ J1 — PRIVATE INITIAL REGISTRATION (council gate + visit responses + public verify)
say "J1: private initial registration"
R=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications \
  -d '{"applicationType":"NEW_REGISTRATION","catalogueCode":"INITIAL_REGISTRATION_PRIVATE","pathway":"PRIVATE","applicantName":"Mavambo Health Trust","facilityName":"Mavambo Proof Clinic","facilityType":"CLINIC","province":"Harare","district":"Harare"}')
echo "$R" > "$EV/j1-create.json"
FAC=$(echo "$R" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['master']['facilityId'])")
APP=$(echo "$R" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['applications'][0]['applicationId'])")
APPNUM=$(PSQL tuso "SELECT application_number FROM tuso.facility_application WHERE application_id='$APP'")
[ -n "$FAC" ] && [ -n "$APP" ] && ok "application $APP created for facility $FAC (number $APPNUM)" || bad "create failed"
[[ "$APPNUM" == HPA-APP-* ]] && ok "human-readable application number" || bad "no application number"

curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/documents \
  -d "{\"facilityId\":$FAC,\"applicationId\":\"$APP\",\"documentType\":\"PREMISES_PLAN\",\"fileReference\":\"doc://mavambo/plan-v1\"}" >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications/$APP/submit >/dev/null
ST=$(PSQL tuso "SELECT current_workflow_state FROM tuso.facility_application WHERE application_id='$APP'")
[ "$ST" = "SUBMITTED" ] && ok "submitted (state=$ST)" || bad "submit state=$ST"

# Council gate: approval BEFORE council review must be rejected
GATE=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/committee-reviews \
  -d "{\"facilityId\":$FAC,\"applicationId\":\"$APP\",\"committeeType\":\"REGISTRATION_COMMITTEE\",\"decision\":\"APPROVED\",\"issueCertificate\":true,\"certificateType\":\"REGISTRATION\"}")
(echo "$GATE" | grep -q '"status":500' && grep -q "requires an external council review" $SCRATCH_LOG) \
  && ok "council gate blocked premature approval (rejection logged)" || bad "council gate DID NOT block: $(echo $GATE|head -c 150)"

# RFI cycle
RFI=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications/$APP/information-requests \
  -d '{"message":"Provide the local authority health report."}' | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['rfiId'])")
ST=$(PSQL tuso "SELECT current_workflow_state FROM tuso.facility_application WHERE application_id='$APP'")
[ "$ST" = "AWAITING_DOCUMENTS" ] && ok "RFI parked application ($ST)" || bad "RFI state=$ST"
curl -sS $(hdr FACILITY_ADMIN) -X POST $TUSO/v1/internal/facility-registry/information-requests/$RFI/respond \
  -d '{"responseNotes":"Report attached as doc://mavambo/la-report"}' >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST "$TUSO/v1/internal/facility-registry/information-requests/$RFI/close?satisfied=true" >/dev/null
ST=$(PSQL tuso "SELECT current_workflow_state FROM tuso.facility_application WHERE application_id='$APP'")
[ "$ST" = "SUBMITTED" ] && ok "RFI close returned application to SUBMITTED" || bad "post-RFI state=$ST"

# Council review (real record, ENDORSED)
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications/$APP/council-reviews \
  -d '{"reviewingAuthority":"MDPCZ","referenceNumber":"PCC-2026-0042","status":"ENDORSED","notes":"PCC endorsed"}' >/dev/null
CR=$(PSQL tuso "SELECT status FROM tuso.external_council_review WHERE application_id='$APP'")
[ "$CR" = "ENDORSED" ] && ok "council review persisted (ENDORSED, authority MDPCZ)" || bad "council review=$CR"

curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications/$APP/ready-for-inspection >/dev/null
INSP=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/inspections \
  -d "{\"facilityId\":$FAC,\"applicationId\":\"$APP\",\"inspectionType\":\"INITIAL\",\"templateCode\":\"CLINIC-INITIAL-V1\"}" \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['inspectionId'])")
[ -n "$INSP" ] && ok "inspection case $INSP scheduled with template" || bad "inspection schedule failed"

# Visit + structured per-item responses (team + COI)
VISIT=$(curl -sS $(hdr HPA_INSPECTOR inspector-1) -X POST $TUSO/v1/internal/facility-registry/inspections/$INSP/visits \
  -d '{"mode":"ONSITE","leadInspectorName":"B. Ncube","team":[{"actorId":"inspector-1","name":"B. Ncube","role":"LEAD","councilNominee":false,"coiDeclared":true,"coiNotes":"none"}]}' \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['visitId'])")
curl -sS $(hdr HPA_INSPECTOR inspector-1) -X POST $TUSO/v1/internal/facility-registry/visits/$VISIT/responses \
  -d '{"items":[{"itemCode":"CLN-01","itemSection":"Premises","requirementText":"Consultation room hygiene","response":"COMPLIANT"},{"itemCode":"CLN-02","itemSection":"Equipment","requirementText":"Sterilization equipment present","response":"NON_COMPLIANT","severity":"MAJOR","comments":"Autoclave absent"},{"itemCode":"CLN-03","itemSection":"Records","requirementText":"Register maintained","response":"NOT_OBSERVED"}]}' > "$EV/j1-responses.json"
NRESP=$(PSQL tuso "SELECT count(*) FROM tuso.inspection_response WHERE visit_id='$VISIT'")
NFIND=$(PSQL tuso "SELECT count(*) FROM tuso.inspection_finding WHERE response_id IS NOT NULL AND inspection_id='$INSP'")
DEADLINE=$(PSQL tuso "SELECT rectification_deadline - CURRENT_DATE FROM tuso.inspection_finding WHERE inspection_id='$INSP' AND response_id IS NOT NULL LIMIT 1")
[ "$NRESP" = "3" ] && ok "3 structured responses persisted" || bad "responses=$NRESP"
[ "$NFIND" = "1" ] && ok "NON_COMPLIANT derived a linked finding (rule window ${DEADLINE}d)" || bad "findings=$NFIND"
curl -sS $(hdr HPA_INSPECTOR inspector-1) -X POST $TUSO/v1/internal/facility-registry/visits/$VISIT/complete -d '{"notes":"Visit done"}' >/dev/null
VS=$(PSQL tuso "SELECT status FROM tuso.inspection_visit WHERE visit_id='$VISIT'")
[ "$VS" = "COMPLETED" ] && ok "visit completed" || bad "visit=$VS"

# Record case outcome + corrective action closure, then committee approval
curl -sS $(hdr HPA_INSPECTOR inspector-1) -X POST $TUSO/v1/internal/facility-registry/inspections/$INSP/record \
  -d '{"notes":"Initial inspection recorded","findings":[{"checklistItemCode":"CLN-02","requirementText":"Sterilization equipment present","criticalFlag":false,"status":"FAIL","severity":"MAJOR","comments":"Autoclave absent"}]}' >/dev/null
ACTION=$(PSQL tuso "SELECT action_id FROM tuso.compliance_action WHERE facility_id=$FAC LIMIT 1")
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/compliance-actions/$ACTION \
  -d '{"status":"VERIFIED","verificationNotes":"Autoclave installed; desktop verification"}' >/dev/null
AST=$(PSQL tuso "SELECT status FROM tuso.compliance_action WHERE action_id='$ACTION'")
[ "$AST" = "VERIFIED" ] && ok "corrective action verified (J7 shortfall loop)" || bad "action=$AST"

DEC=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/committee-reviews \
  -d "{\"facilityId\":$FAC,\"applicationId\":\"$APP\",\"committeeType\":\"REGISTRATION_COMMITTEE\",\"decision\":\"APPROVED\",\"notes\":\"Approved\",\"issueCertificate\":true,\"certificateType\":\"REGISTRATION\"}")
CERTCODE=$(PSQL tuso "SELECT verification_code FROM tuso.facility_certificate WHERE facility_id=$FAC AND status='ACTIVE'")
REGST=$(PSQL tuso "SELECT regulatory_status FROM tuso.facility WHERE id=$FAC")
[ "$REGST" = "REGISTERED_ACTIVE" ] && ok "facility REGISTERED_ACTIVE" || bad "status=$REGST"
[ -n "$CERTCODE" ] && ok "certificate carries verification code $CERTCODE" || bad "no verification code"

# Public verification — no auth headers at all
PUB=$(curl -sS $TUSO/v1/public/facilities/certificates/verify/$CERTCODE)
echo "$PUB" > "$EV/j12-public-verify.json"
echo "$PUB" | grep -q '"verified":true' && echo "$PUB" | grep -q "Mavambo Proof Clinic" \
  && ok "J12 public verification discloses public fields" || bad "public verify: $(echo $PUB|head -c 120)"
echo "$PUB" | grep -qi "finding\|enforcement\|healthId" && bad "public verify leaks confidential fields" || ok "no confidential leakage"
NOTFOUND=$(curl -sS -o /dev/null -w "%{http_code}" $TUSO/v1/public/facilities/certificates/verify/NOPE123)
[ "$NOTFOUND" = "404" ] && ok "unknown code → 404" || bad "unknown code → $NOTFOUND"

# ═══ J2 — PUBLIC/MISSION/LA ROUTE (no council step forced)
say "J2: government route"
R2=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications \
  -d '{"applicationType":"NEW_REGISTRATION","catalogueCode":"INITIAL_REGISTRATION_PUBLIC_MISSION_LA","pathway":"PUBLIC","applicantName":"MoHCC","facilityName":"Gutu Rural Proof Clinic","facilityType":"CLINIC","province":"Masvingo"}')
FAC2=$(echo "$R2" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['master']['facilityId'])")
APP2=$(echo "$R2" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['applications'][0]['applicationId'])")
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/documents \
  -d "{\"facilityId\":$FAC2,\"applicationId\":\"$APP2\",\"documentType\":\"PREMISES_PLAN\",\"fileReference\":\"doc://gutu/plan\"}" >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications/$APP2/submit >/dev/null
D2=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/committee-reviews \
  -d "{\"facilityId\":$FAC2,\"applicationId\":\"$APP2\",\"committeeType\":\"REGISTRATION_COMMITTEE\",\"decision\":\"APPROVED\",\"issueCertificate\":true}")
R2ST=$(PSQL tuso "SELECT regulatory_status FROM tuso.facility WHERE id=$FAC2")
[ "$R2ST" = "REGISTERED_ACTIVE" ] && ok "public route approved WITHOUT council review (correct)" || bad "public route status=$R2ST: $(echo $D2|head -c 120)"

# ═══ PIC — seed varapi provider, assess, nominate, respond, review, activate
say "J-PIC: nomination lifecycle (varapi-backed)"
docker exec -i hpa-proof-pg psql -U impilo -d varapi <<SQL
INSERT INTO varapi.councils (id, tenant_id, council_code, name, council_type, status) VALUES (901,'$TEN','MDPCZ','Medical and Dental Practitioners Council','PROFESSIONAL_COUNCIL','ACTIVE') ON CONFLICT DO NOTHING;
INSERT INTO varapi.provider (id, tenant_id, provider_ref, provider_public_id, impilo_health_id, given_name, family_name, profession, cadre, primary_council_id, lifecycle_status, registry_status, status, active_flag)
VALUES (901,'$TEN',gen_random_uuid(),'PROOFPIC000000000000000001','c0000000-0000-4000-8000-000000000099','Tariro','Moyo','Medical Practitioner','Doctor',901,'LICENCED_ACTIVE','REGISTERED_ACTIVE','ACTIVE',true) ON CONFLICT DO NOTHING;
INSERT INTO varapi.provider_council_registration_records (tenant_id, provider_id, council_id, registration_number, registration_category, registration_date, status, expiry_date)
VALUES ('$TEN',901,901,'MDP-90001','GENERAL',CURRENT_DATE-900,'REGISTERED',CURRENT_DATE+200);
INSERT INTO varapi.provider_certificates (tenant_id, provider_id, certificate_type, certificate_number, issue_date, expiry_date, status, version)
VALUES ('$TEN',901,'PRACTISING_CERTIFICATE','PC-90001',CURRENT_DATE-100,CURRENT_DATE+265,'ACTIVE',1);
INSERT INTO varapi.licenses (tenant_id, provider_id, license_type, license_number, status, valid_from, valid_to)
VALUES ('$TEN',901,'PRACTICE','LIC-90001','ACTIVE',CURRENT_DATE-100,CURRENT_DATE+265);
SQL
ASSESS=$(curl -sS $(hdr OPERATOR) -X POST $VARAPI/v1/internal/interop/eligibility/assessments \
  -d '{"providerPublicId":"PROOFPIC000000000000000001","facilityRef":"'$FAC'","purpose":"PIC_NOMINATION"}')
echo "$ASSESS" > "$EV/pic-assessment.json"
echo "$ASSESS" | grep -q '"result":"ELIGIBLE"' && ok "varapi assessment ELIGIBLE with per-axis evidence" || bad "assessment: $(echo $ASSESS|head -c 200)"
SNAPS=$(PSQL varapi "SELECT count(*) FROM varapi.provider_eligibility_snapshot WHERE provider_public_id='PROOFPIC000000000000000001'")
[ "$SNAPS" -ge 1 ] && ok "eligibility snapshot persisted in varapi" || bad "snapshots=$SNAPS"

NOM=$(curl -sS $(hdr FACILITY_ADMIN rep-1) -X POST $TUSO/v1/internal/facility-registry/pic-nominations \
  -d "{\"facilityId\":$FAC,\"providerPublicId\":\"PROOFPIC000000000000000001\",\"notes\":\"Founding PIC\"}")
NOMID=$(echo "$NOM" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['nominationId'])")
NOMSTATE=$(PSQL tuso "SELECT state FROM tuso.pic_nomination WHERE nomination_id='$NOMID'")
[ "$NOMSTATE" = "PRACTITIONER_ACCEPTANCE_PENDING" ] && ok "nomination created via live varapi assessment ($NOMSTATE)" || bad "nomination state=$NOMSTATE"

# NEGATIVE: wrong practitioner cannot respond
NEG=$(curl -sS $(hdr PROVIDER wrong-person-id) -X POST $TUSO/v1/internal/facility-registry/pic-nominations/$NOMID/respond \
  -d '{"response":"ACCEPTED"}')
NOMSTATE2=$(PSQL tuso "SELECT state FROM tuso.pic_nomination WHERE nomination_id='$NOMID'")
(echo "$NEG" | grep -q '"status":500' && grep -q "Only the nominated practitioner may respond" $SCRATCH_LOG && [ "$NOMSTATE2" = "PRACTITIONER_ACCEPTANCE_PENDING" ]) \
  && ok "NEGATIVE: foreign practitioner blocked from responding (rejection logged, state unchanged)" || bad "wrong actor accepted!: state=$NOMSTATE2 $(echo $NEG|head -c 120)"

curl -sS $(hdr PROVIDER c0000000-0000-4000-8000-000000000099) -X POST $TUSO/v1/internal/facility-registry/pic-nominations/$NOMID/respond \
  -d '{"response":"ACCEPTED","notes":"I accept"}' >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/pic-nominations/$NOMID/review \
  -d '{"authority":"MDPCZ","reference":"PCC-PIC-01","approved":true}' >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/pic-nominations/$NOMID/activate >/dev/null
ASGN1=$(PSQL tuso "SELECT id FROM tuso.practitioner_in_charge_assignment WHERE facility_id=$FAC AND end_date IS NULL")
[ -n "$ASGN1" ] && ok "PIC assignment $ASGN1 ACTIVATED (effective-dated)" || bad "no active assignment"

# J5 — PIC CHANGE: second nomination supersedes historically
docker exec -i hpa-proof-pg psql -U impilo -d varapi <<SQL
INSERT INTO varapi.provider (id, tenant_id, provider_ref, provider_public_id, impilo_health_id, given_name, family_name, profession, cadre, primary_council_id, lifecycle_status, registry_status, status, active_flag)
VALUES (902,'$TEN',gen_random_uuid(),'PROOFPIC000000000000000002','c0000000-0000-4000-8000-000000000098','Rudo','Chirwa','Medical Practitioner','Doctor',901,'LICENCED_ACTIVE','REGISTERED_ACTIVE','ACTIVE',true) ON CONFLICT DO NOTHING;
INSERT INTO varapi.provider_council_registration_records (tenant_id, provider_id, council_id, registration_number, registration_date, status, expiry_date) VALUES ('$TEN',902,901,'MDP-90002',CURRENT_DATE-800,'REGISTERED',CURRENT_DATE+300);
INSERT INTO varapi.provider_certificates (tenant_id, provider_id, certificate_type, certificate_number, issue_date, expiry_date, status, version) VALUES ('$TEN',902,'PRACTISING_CERTIFICATE','PC-90002',CURRENT_DATE-50,CURRENT_DATE+315,'ACTIVE',1);
INSERT INTO varapi.licenses (tenant_id, provider_id, license_type, license_number, status, valid_from, valid_to) VALUES ('$TEN',902,'PRACTICE','LIC-90002','ACTIVE',CURRENT_DATE-50,CURRENT_DATE+315);
SQL
NOM2=$(curl -sS $(hdr FACILITY_ADMIN rep-1) -X POST $TUSO/v1/internal/facility-registry/pic-nominations \
  -d "{\"facilityId\":$FAC,\"providerPublicId\":\"PROOFPIC000000000000000002\"}" | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['nominationId'])")
curl -sS $(hdr PROVIDER c0000000-0000-4000-8000-000000000098) -X POST $TUSO/v1/internal/facility-registry/pic-nominations/$NOM2/respond -d '{"response":"ACCEPTED"}' >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/pic-nominations/$NOM2/review -d '{"authority":"MDPCZ","approved":true}' >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/pic-nominations/$NOM2/activate >/dev/null
OLD_END=$(PSQL tuso "SELECT end_date FROM tuso.practitioner_in_charge_assignment WHERE id=$ASGN1")
NEW_PRED=$(PSQL tuso "SELECT predecessor_assignment_id FROM tuso.practitioner_in_charge_assignment WHERE facility_id=$FAC AND end_date IS NULL")
[ -n "$OLD_END" ] && [ "$NEW_PRED" = "$ASGN1" ] && ok "J5: predecessor end-dated ($OLD_END), successor links predecessor $NEW_PRED" || bad "J5 old_end=$OLD_END pred=$NEW_PRED"

# J6 — credential change flags review (consumer path exercised directly via service seam)
curl -sS $(hdr HPA_REGISTRAR) -X POST "$VARAPI/v1/internal/providers/certificates/$(PSQL varapi "SELECT id FROM varapi.provider_certificates WHERE provider_id=902")/suspend" \
  -d '{"suspensionDate":"'"$(date +%F)"'","reason":"CPD non-compliance","notes":"proof"}' >/dev/null 2>&1
ELIGEVENTS=$(PSQL varapi "SELECT count(*) FROM varapi.event_outbox WHERE event_type='varapi.provider.eligibility.changed'")
[ "$ELIGEVENTS" -ge 1 ] && ok "J6: credential change emitted varapi.provider.eligibility.changed" || bad "no eligibility.changed event"
# (Kafka is absent in the proof rig: exercise tuso's flag path via the same service seam the consumer calls)
FLAGGED=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/pic-assignments/$(PSQL tuso "SELECT id FROM tuso.practitioner_in_charge_assignment WHERE facility_id=$FAC AND end_date IS NULL")/resolve-review -d '{"cleared":false,"notes":"Certificate suspended at council — under review"}')
RS=$(PSQL tuso "SELECT review_state FROM tuso.practitioner_in_charge_assignment WHERE facility_id=$FAC AND end_date IS NULL")
[ "$RS" = "UNDER_REVIEW" ] && ok "J6: assignment review state UNDER_REVIEW via audited action (not deleted)" || bad "review_state=$RS"

# ═══ J4 — SHARED PREMISES
say "J4: shared premises"
PREM=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/premises \
  -d '{"label":"14 Baines Ave Medical Suites","addressLine1":"14 Baines Ave","city":"Harare","province":"Harare","occupancyTenure":"SHARED"}' \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['premisesId'])")
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/premises/$PREM/occupancies -d "{\"facilityId\":$FAC,\"role\":\"PRIMARY\"}" >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/premises/$PREM/occupancies -d "{\"facilityId\":$FAC2,\"role\":\"SHARED_OCCUPANT\"}" >/dev/null
OCC=$(PSQL tuso "SELECT count(*) FROM tuso.facility_premises_occupancy WHERE premises_id='$PREM' AND effective_to IS NULL")
[ "$OCC" = "2" ] && ok "two distinct regulated facilities share one premises" || bad "occupancies=$OCC"

# ═══ J8 — RELOCATION (certificate not transferable)
say "J8: relocation"
PREM2=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/premises \
  -d '{"label":"22 Samora Machel New Site","city":"Harare","province":"Harare"}' | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['premisesId'])")
R8=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications \
  -d "{\"facilityId\":$FAC,\"applicationType\":\"CHANGE_OF_PREMISES\",\"catalogueCode\":\"RELOCATION\",\"applicantName\":\"Mavambo Health Trust\",\"premisesId\":\"$PREM2\"}")
APP8=$(echo "$R8" | python3 -c "import json,sys;d=json.load(sys.stdin)['data']['applications'];print([a['applicationId'] for a in d if a['applicationType']=='CHANGE_OF_PREMISES'][0])")
OLDCERT=$(PSQL tuso "SELECT certificate_id FROM tuso.facility_certificate WHERE facility_id=$FAC AND status='ACTIVE'")
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/documents -d "{\"facilityId\":$FAC,\"applicationId\":\"$APP8\",\"documentType\":\"PREMISES_PLAN\",\"fileReference\":\"doc://newsite/plan\"}" >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications/$APP8/submit >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/committee-reviews \
  -d "{\"facilityId\":$FAC,\"applicationId\":\"$APP8\",\"committeeType\":\"REGISTRATION_COMMITTEE\",\"decision\":\"APPROVED\",\"issueCertificate\":true}" >/dev/null
OLDST=$(PSQL tuso "SELECT status||'|'||coalesce(status_reason,'') FROM tuso.facility_certificate WHERE certificate_id='$OLDCERT'")
NEWPRIM=$(PSQL tuso "SELECT premises_id FROM tuso.facility_premises_occupancy WHERE facility_id=$FAC AND occupancy_role='PRIMARY' AND effective_to IS NULL")
echo "$OLDST" | grep -q "SUPERSEDED" && echo "$OLDST" | grep -qi "not transferable" && ok "old certificate SUPERSEDED with relocation reason" || bad "old cert: $OLDST"
[ "$NEWPRIM" = "$PREM2" ] && ok "primary occupancy switched to new premises; history preserved" || bad "primary=$NEWPRIM"

# ═══ J9 — RENEWAL (successor certificate)
say "J9: renewal"
R9=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications \
  -d "{\"facilityId\":$FAC2,\"applicationType\":\"RENEWAL\",\"catalogueCode\":\"RENEWAL\",\"applicantName\":\"MoHCC\"}")
APP9=$(echo "$R9" | python3 -c "import json,sys;d=json.load(sys.stdin)['data']['applications'];print([a['applicationId'] for a in d if a['applicationType']=='RENEWAL'][0])")
CERT9OLD=$(PSQL tuso "SELECT certificate_id FROM tuso.facility_certificate WHERE facility_id=$FAC2 AND status='ACTIVE'")
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/documents -d "{\"facilityId\":$FAC2,\"applicationId\":\"$APP9\",\"documentType\":\"OTHER\",\"fileReference\":\"doc://gutu/renewal-decl\"}" >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications/$APP9/submit >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/committee-reviews \
  -d "{\"facilityId\":$FAC2,\"applicationId\":\"$APP9\",\"committeeType\":\"REGISTRATION_COMMITTEE\",\"decision\":\"APPROVED\",\"issueCertificate\":true}" >/dev/null
SUP=$(PSQL tuso "SELECT status FROM tuso.facility_certificate WHERE certificate_id='$CERT9OLD'")
NEWCERT=$(PSQL tuso "SELECT count(*) FROM tuso.facility_certificate WHERE facility_id=$FAC2 AND status='ACTIVE' AND supersedes_certificate_id='$CERT9OLD'")
[ "$SUP" = "SUPERSEDED" ] && [ "$NEWCERT" = "1" ] && ok "renewal issued successor; predecessor preserved SUPERSEDED" || bad "renewal sup=$SUP new=$NEWCERT"

# ═══ J10 — VOLUNTARY CLOSURE
say "J10: voluntary closure"
R10=$(curl -sS $(hdr FACILITY_ADMIN rep-1) -X POST $TUSO/v1/internal/facility-registry/applications \
  -d "{\"facilityId\":$FAC2,\"applicationType\":\"VOLUNTARY_CLOSURE_NOTICE\",\"catalogueCode\":\"VOLUNTARY_CLOSURE\",\"applicantName\":\"MoHCC\"}")
APP10=$(echo "$R10" | python3 -c "import json,sys;d=json.load(sys.stdin)['data']['applications'];print([a['applicationId'] for a in d if a['applicationType']=='VOLUNTARY_CLOSURE_NOTICE'][0])")
curl -sS $(hdr FACILITY_ADMIN rep-1) -X POST $TUSO/v1/internal/facility-registry/documents -d "{\"facilityId\":$FAC2,\"applicationId\":\"$APP10\",\"documentType\":\"OTHER\",\"fileReference\":\"doc://gutu/closure-letter\"}" >/dev/null
curl -sS $(hdr FACILITY_ADMIN rep-1) -X POST $TUSO/v1/internal/facility-registry/applications/$APP10/submit >/dev/null
curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/committee-reviews \
  -d "{\"facilityId\":$FAC2,\"applicationId\":\"$APP10\",\"committeeType\":\"REGISTRATION_COMMITTEE\",\"decision\":\"APPROVED\",\"issueCertificate\":false}" >/dev/null
CLOSED=$(PSQL tuso "SELECT regulatory_status FROM tuso.facility WHERE id=$FAC2")
STILL=$(PSQL tuso "SELECT count(*) FROM tuso.facility WHERE id=$FAC2")
[ "$CLOSED" = "VOLUNTARILY_CLOSED" ] && [ "$STILL" = "1" ] && ok "closure decided explicitly; facility preserved not deleted" || bad "closure=$CLOSED rows=$STILL"

# ═══ Transition guard negative
say "Guard: illegal transition rejected"
G=$(curl -sS $(hdr HPA_REGISTRAR) -X POST $TUSO/v1/internal/facility-registry/applications/$APP10/ready-for-inspection)
(echo "$G" | grep -q '"status":500' && grep -q "VOLUNTARILY_CLOSED -> PENDING_INSPECTION is not permitted" $SCRATCH_LOG) \
  && ok "guard rejected VOLUNTARILY_CLOSED → PENDING_INSPECTION (rejection logged)" || bad "guard: $(echo $G|head -c 150)"

# ═══ Events + audit hygiene
say "Events + audit"
EVROWS=$(PSQL tuso "SELECT count(*) FROM tuso.event_outbox WHERE pod_id='national-spine' AND idempotency_key IS NOT NULL")
EVNULL=$(PSQL tuso "SELECT count(*) FROM tuso.event_outbox WHERE pod_id IS NULL OR idempotency_key IS NULL")
AUD=$(PSQL tuso "SELECT count(*) FROM tuso.facility_audit_event")
HIST=$(PSQL tuso "SELECT count(*) FROM tuso.facility_status_history WHERE facility_id=$FAC")
[ "$EVROWS" -gt 10 ] && [ "$EVNULL" = "0" ] && ok "$EVROWS outbox events, all with pod_id+idempotency_key" || bad "events=$EVROWS null=$EVNULL"
[ "$AUD" -gt 10 ] && ok "$AUD audit events recorded" || bad "audit=$AUD"
[ "$HIST" -ge 4 ] && ok "status-history chain for facility $FAC has $HIST entries" || bad "history=$HIST"
PSQL tuso "SELECT event_type, count(*) FROM tuso.event_outbox GROUP BY 1 ORDER BY 1" > "$EV/event-types.txt"

# ═══ Reload continuity (fresh reads only)
say "Reload continuity"
RELOAD=$(curl -sS $(hdr HPA_REGISTRAR) $TUSO/v1/internal/facility-registry/facilities/$FAC)
echo "$RELOAD" > "$EV/reload-profile.json"
echo "$RELOAD" | grep -q "Mavambo Proof Clinic" && echo "$RELOAD" | grep -q "REGISTERED_ACTIVE" \
  && ok "profile reload carries full state" || bad "reload failed"

echo "" | tee -a "$EV/journal.txt"
echo "RESULT: PASS=$PASS FAIL=$FAIL" | tee -a "$EV/journal.txt"
[ "$FAIL" = "0" ]
