#!/usr/bin/env bash
# ============================================================================
# Regulatory Configuration Registry — end-to-end journeys (NCZ-W1A).
#
# THE POINT: the acceptance discriminator for this whole wave is one sentence —
# an already-submitted case stays on its old configuration version after a new
# release activates, while a new case uses the new one. Everything else the
# Registry does is machinery in service of that sentence, and the sentence is
# only true if a real service, over HTTP, against a real Postgres, behaves that
# way. This script asserts it.
#
#   RC-1  authoring: a pack, a fee definition whose amount is deliberately unset
#         (NCZ-DEC-002), and a form — content hashed, versions land DRAFT
#   RC-2  checksum immutability: re-authoring 1.0.0 with different content is
#         REFUSED; re-authoring with identical content (keys reordered) is a
#         no-op, so pack imports are idempotent
#   RC-3  validation refuses an incoherent release: a workflow referencing a
#         form the release does not contain, and an applicant-facing definition
#         with no self-service route
#   RC-4  four-eyes: the submitter cannot approve; a fee change needs two
#         approvers; one approver cannot decide twice
#   RC-5  activation: a valid release goes live, an unset policy value is a
#         WARNING that does NOT block it, and the validation report is stored
#   RC-6  a case binds to the active release
#   RC-7  THE DISCRIMINATOR: activate v2 with a different fee, then —
#           * the old case still resolves to fee 1.0.0
#           * a new case resolves to fee 2.0.0
#           * rebinding the old case returns its ORIGINAL binding
#   RC-8  a frozen release cannot be edited, and an unbound case resolves to an
#         error rather than silently falling back to what is active today
#
# Usage:  scripts/runtime-proof/regulatory-config-registry-journeys.sh
#         KEEP_RIG=1 to leave Postgres + the service running for inspection.
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-$REPO/reports/journeys/regulatory-config-registry-journeys-$(date +%Y-%m-%d)}
RIGLOG=${RIGLOG:-/tmp/rcfg-journeys-logs}
mkdir -p "$EV" "$RIGLOG"
PGPORT=${PGPORT:-15698}; PORT=${PORT:-28153}; ORG=http://localhost:$PORT
CT=rcfgj-rig-pg
TEN=00000000-0000-0000-0000-000000000001
NCZ=a5000000-0000-4000-8000-000000000004   # a council seeded by V007
PASS=0; FAIL=0
ok(){  echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1"      | tee -a "$EV/journal.txt"; }

command -v docker >/dev/null || { echo "docker not available — rig cannot run"; exit 2; }
jar(){ ls "$REPO/services/organization-registry-service/target/organization-registry-service-"*.jar \
        2>/dev/null | grep -v original | head -1; }
[ -n "$(jar)" ] || { echo "org-registry jar missing — run: mvn -o -f services/pom.xml -pl organization-registry-service package -DskipTests"; exit 2; }

# The jar is what actually runs, so prove the migration is INSIDE it rather than
# trusting the working tree (a stale jar silently proves the wrong schema).
if ! unzip -l "$(jar)" | grep -q "V013__regulatory_configuration_registry.sql"; then
    echo "jar does not contain V013 — repackage before running this proof"; exit 2
fi

say "RIG: postgres + organization-registry"
docker rm -f $CT >/dev/null 2>&1
docker run -d --name $CT -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo \
    -p $PGPORT:5432 postgres:16-alpine >/dev/null || { echo "docker run failed"; exit 2; }
# pg_isready returns true during the image's initdb phase, BEFORE Postgres restarts for
# real — waiting on it produced a "database system is shutting down" flake. Wait on a
# query that actually succeeds twice in a row instead.
ready=0
for i in $(seq 1 45); do
    if docker exec $CT psql -U impilo -d postgres -tAc "select 1" >/dev/null 2>&1; then
        ready=$((ready+1)); [ "$ready" -ge 2 ] && break
    else ready=0; fi
    sleep 2
done
[ "$ready" -ge 2 ] || { echo "postgres never became ready"; exit 2; }
docker exec $CT psql -U impilo -d postgres -c "CREATE DATABASE org_registry" >/dev/null 2>&1
PSQL(){ docker exec -i $CT psql -U impilo -d org_registry -tAc "$1"; }

env DB_HOST=localhost DB_PORT=$PGPORT DB_NAME=org_registry \
    POSTGRES_USER=impilo POSTGRES_PASSWORD=impilo \
    IMPILO_SECURITY_DISABLE_OAUTH_FOR_TESTS=true SERVER_PORT=$PORT \
    SPRING_KAFKA_LISTENER_AUTO_STARTUP=false \
    nohup java -jar "$(jar)" > "$RIGLOG/org-registry.log" 2>&1 & echo $! > "$RIGLOG/svc.pid"

cleanup(){ if [ -z "${KEEP_RIG:-}" ]; then
    kill "$(cat "$RIGLOG/svc.pid")" >/dev/null 2>&1; docker rm -f $CT >/dev/null 2>&1; fi }
trap cleanup EXIT

c=000
for i in $(seq 1 60); do
    c=$(curl -s -o /dev/null -w '%{http_code}' $ORG/actuator/health 2>/dev/null)
    [ "$c" = 200 ] && break; sleep 3
done
[ "$c" = 200 ] && ok "organization-registry booted (V013 applied by Flyway)" \
    || { bad "service did not boot"; tail -40 "$RIGLOG/org-registry.log"; exit 1; }

hdr(){ local a=${1:-rig-registrar}
  echo "-H Content-Type:application/json -H X-Tenant-ID:$TEN -H X-Pod-ID:national-spine \
-H X-Request-ID:$(uuidgen) -H X-Correlation-ID:$(uuidgen) -H X-Actor-ID:$a \
-H X-Actor-Type:ADMIN -H X-Purpose-Of-Use:REGULATORY_DUTY"; }
jq_(){ python3 -c "import json,sys
try: print(json.load(sys.stdin)$1)
except Exception: print('')"; }
code(){ curl -s -o /dev/null -w '%{http_code}' "$@"; }
# Setup calls must be checked. The first run of this pack passed RC-7 while a 500 on an
# item-replace was being discarded to /dev/null — a swallowed setup failure reads as a green
# journey, which is worse than a red one.
must(){ local want=$1; shift
  local got; got=$(curl -s -o "$RIGLOG/last.json" -w '%{http_code}' "$@")
  if [ "$got" != "$want" ]; then
    bad "setup call expected HTTP $want, got $got: $(head -c 300 "$RIGLOG/last.json")"
  fi
  cat "$RIGLOG/last.json"; }
API=$ORG/v1/regulatory/config

# ═════ RC-1: author a pack and its definitions ══════════════════════════════
say "RC-1: author a pack, a fee with an unset amount, and a form"
PACK=$(curl -sS $(hdr) -X POST "$API/organizations/$NCZ/packs" \
    -d '{"packKey":"nurses-council","label":"Nurses Council of Zimbabwe"}' | jq_ "['id']")
[ -n "$PACK" ] && ok "pack created ($PACK)" || { bad "pack creation failed"; exit 1; }

# NCZ-DEC-002: the Council has not set the student index fee. The seam ships built
# and the value absent — never a guessed number.
FEE1=$(curl -sS $(hdr) -X POST "$API/packs/$PACK/versions" -d '{
  "typeCode":"FEE_SCHEDULE","definitionKey":"student-index-fee","label":"Student index fee",
  "semanticVersion":"1.0.0","payload":{"amount":null,"currency":"USD"},
  "selfServiceRoute":"/professional/regulatory/invoices",
  "policyStatus":"PENDING_REGULATOR_APPROVAL","policyDecisionRef":"NCZ-DEC-002"}' | jq_ "['id']")
FORM1=$(curl -sS $(hdr) -X POST "$API/packs/$PACK/versions" -d '{
  "typeCode":"FORM","definitionKey":"student-registration-form","label":"Student registration form",
  "semanticVersion":"1.0.0","payload":{"sections":["identity","programme","documents"]},
  "selfServiceRoute":"/professional/regulatory/apply/student"}' | jq_ "['id']")
[ -n "$FEE1" ] && [ -n "$FORM1" ] && ok "fee + form authored as DRAFT versions" || bad "authoring failed"
[ "$(PSQL "SELECT count(*) FROM org_registry.regulatory_config_definition_version WHERE lifecycle_state='DRAFT'")" = 2 ] \
    && ok "both versions land DRAFT (nothing is live by authoring alone)" || bad "unexpected version states"

# ═════ RC-2: checksum immutability ══════════════════════════════════════════
say "RC-2: a published version's content cannot change under it"
CONFLICT=$(code $(hdr) -X POST "$API/packs/$PACK/versions" -d '{
  "typeCode":"FEE_SCHEDULE","definitionKey":"student-index-fee","semanticVersion":"1.0.0",
  "payload":{"amount":25,"currency":"USD"},"selfServiceRoute":"/x"}')
[ "$CONFLICT" = 409 ] && ok "re-authoring 1.0.0 with a different amount is refused (409)" \
    || bad "content change under a fixed version was accepted (got $CONFLICT)"

SAME=$(curl -sS $(hdr) -X POST "$API/packs/$PACK/versions" -d '{
  "typeCode":"FEE_SCHEDULE","definitionKey":"student-index-fee","semanticVersion":"1.0.0",
  "payload":{"currency":"USD","amount":null},
  "selfServiceRoute":"/professional/regulatory/invoices"}' | jq_ "['id']")
[ "$SAME" = "$FEE1" ] && ok "re-import with reordered keys is a no-op (idempotent pack import)" \
    || bad "identical content minted a second version"

# ═════ RC-3: an incoherent release cannot be approved ═══════════════════════
say "RC-3: validation refuses an incomplete release"
BADWF=$(curl -sS $(hdr) -X POST "$API/packs/$PACK/versions" -d '{
  "typeCode":"WORKFLOW","definitionKey":"student-flow","label":"Student registration flow",
  "semanticVersion":"1.0.0","payload":{"stages":["SUBMITTED","REVIEW","DECIDED"]},
  "selfServiceRoute":"/professional/regulatory/apply/student",
  "dependencies":[{"typeCode":"FORM","definitionKey":"missing-form","optional":false}]}' | jq_ "['id']")
R0=$(curl -sS $(hdr) -X POST "$API/packs/$PACK/releases" \
    -d '{"releaseKey":"broken","label":"Broken release"}' | jq_ "['id']")
curl -sS $(hdr) -X POST "$API/releases/$R0/items" -d "{\"definitionVersionId\":\"$BADWF\"}" >/dev/null
curl -sS $(hdr registrar-a) -X POST "$API/releases/$R0/submit" >/dev/null
curl -sS $(hdr registrar-b) -X POST "$API/releases/$R0/approvals" -d '{"decision":"APPROVE"}' >/dev/null
curl -sS $(hdr registrar-c) -X POST "$API/releases/$R0/approvals" -d '{"decision":"APPROVE"}' >/dev/null
BROKEN=$(code $(hdr registrar-b) -X POST "$API/releases/$R0/approve")
[ "$BROKEN" = 422 ] && ok "a workflow referencing a form the release lacks is refused (422)" \
    || bad "an incoherent release was approved (got $BROKEN)"

NOROUTE=$(curl -sS $(hdr) -X POST "$API/packs/$PACK/versions" -d '{
  "typeCode":"APPLICATION_TYPE","definitionKey":"student-registration","label":"Student registration",
  "semanticVersion":"1.0.0","payload":{"name":"Student registration"}}' | jq_ "['id']")
R0B=$(curl -sS $(hdr) -X POST "$API/packs/$PACK/releases" \
    -d '{"releaseKey":"no-route","label":"No applicant route"}' | jq_ "['id']")
curl -sS $(hdr) -X POST "$API/releases/$R0B/items" -d "{\"definitionVersionId\":\"$NOROUTE\"}" >/dev/null
curl -sS $(hdr registrar-a) -X POST "$API/releases/$R0B/submit" >/dev/null
curl -sS $(hdr registrar-b) -X POST "$API/releases/$R0B/approvals" -d '{"decision":"APPROVE"}' >/dev/null
NR=$(code $(hdr registrar-b) -X POST "$API/releases/$R0B/approve")
[ "$NR" = 422 ] && ok "an applicant-facing capability with no self-service route is refused (422)" \
    || bad "a regulator-only capability was allowed to activate (got $NR)"

# ═════ RC-4: four-eyes ══════════════════════════════════════════════════════
say "RC-4: fees need two people, and the submitter is not one of them"
R1=$(curl -sS $(hdr) -X POST "$API/packs/$PACK/releases" \
    -d '{"releaseKey":"ncz-v1","label":"NCZ pack v1"}' | jq_ "['id']")
for v in "$FEE1" "$FORM1"; do
    must 200 $(hdr) -X POST "$API/releases/$R1/items" -d "{\"definitionVersionId\":\"$v\"}" >/dev/null
done
curl -sS $(hdr registrar-a) -X POST "$API/releases/$R1/submit" >/dev/null

SELF=$(code $(hdr registrar-a) -X POST "$API/releases/$R1/approvals" -d '{"decision":"APPROVE"}')
[ "$SELF" = 409 ] && ok "the submitter cannot approve their own release (409)" \
    || bad "four-eyes did not hold (got $SELF)"

curl -sS $(hdr registrar-b) -X POST "$API/releases/$R1/approvals" -d '{"decision":"APPROVE"}' >/dev/null
TWICE=$(code $(hdr registrar-b) -X POST "$API/releases/$R1/approvals" -d '{"decision":"APPROVE"}')
[ "$TWICE" = 409 ] && ok "one approver cannot decide twice (409)" || bad "duplicate approval accepted ($TWICE)"

ONEEYE=$(code $(hdr registrar-b) -X POST "$API/releases/$R1/approve")
[ "$ONEEYE" = 422 ] && ok "a fee change with one approval is refused (422)" \
    || bad "a fee change passed on a single approval (got $ONEEYE)"

curl -sS $(hdr registrar-c) -X POST "$API/releases/$R1/approvals" -d '{"decision":"APPROVE"}' >/dev/null
APPROVED=$(code $(hdr registrar-b) -X POST "$API/releases/$R1/approve")
[ "$APPROVED" = 200 ] && ok "two approvals carry the fee change" || bad "approval failed ($APPROVED)"

# ═════ RC-5: activation ═════════════════════════════════════════════════════
say "RC-5: an unset policy value warns, and does not block going live"
ACT=$(curl -sS $(hdr registrar-b) -X POST "$API/releases/$R1/activate")
[ -n "$(echo "$ACT" | jq_ "['id']")" ] && ok "v1 activated" || { bad "activation failed: $ACT"; }
WARN=$(echo "$ACT" | jq_ "['validationReport']['warnings'][0]['code']")
[ "$WARN" = "POLICY_VALUE_UNSET" ] \
    && ok "the unset student index fee is a stored WARNING, not a blocker" \
    || bad "expected POLICY_VALUE_UNSET warning, got '$WARN'"
echo "$ACT" > "$EV/activation-v1.json"
[ "$(PSQL "SELECT count(*) FROM org_registry.regulatory_config_activation")" = 1 ] \
    && ok "the validation report that justified activation is persisted" || bad "no activation record"

# ═════ RC-6: bind a case ════════════════════════════════════════════════════
say "RC-6: an application binds to the configuration active when it opened"
B1=$(curl -sS $(hdr) -X POST "$API/case-bindings" -d "{
  \"caseSystem\":\"VARAPI\",\"caseType\":\"PROVIDER_APPLICATION\",\"caseRef\":\"app-old\",
  \"organizationId\":\"$NCZ\"}" | jq_ "['releaseId']")
[ "$B1" = "$R1" ] && ok "app-old bound to release v1" || bad "binding went to '$B1', expected $R1"

# ═════ RC-7: THE DISCRIMINATOR ══════════════════════════════════════════════
say "RC-7: activate v2 with a different fee — the old case must not move"
FEE2=$(curl -sS $(hdr) -X POST "$API/packs/$PACK/versions" -d '{
  "typeCode":"FEE_SCHEDULE","definitionKey":"student-index-fee","semanticVersion":"2.0.0",
  "payload":{"amount":30,"currency":"USD"},
  "selfServiceRoute":"/professional/regulatory/invoices","policyStatus":"CONFIRMED"}' | jq_ "['id']")
R2=$(curl -sS $(hdr) -X POST "$API/packs/$PACK/releases" \
    -d '{"releaseKey":"ncz-v2","label":"NCZ pack v2","cloneActive":true}' | jq_ "['id']")
# The clone carried fee 1.0.0; this REPLACES it with 2.0.0 in the same release.
must 200 $(hdr) -X POST "$API/releases/$R2/items" -d "{\"definitionVersionId\":\"$FEE2\"}" >/dev/null
R2FEE=$(PSQL "SELECT v.semantic_version FROM org_registry.regulatory_config_release_item i
   JOIN org_registry.regulatory_config_definition_version v ON v.id = i.definition_version_id
   JOIN org_registry.regulatory_config_definition d ON d.id = i.definition_id
  WHERE i.release_id = '$R2' AND d.definition_key = 'student-index-fee'")
[ "$R2FEE" = "2.0.0" ] && ok "cloning then replacing leaves exactly one fee version in v2 (2.0.0)" \
    || bad "v2 carries fee version '$R2FEE' — the replace did not take"
curl -sS $(hdr registrar-a) -X POST "$API/releases/$R2/submit" >/dev/null
curl -sS $(hdr registrar-b) -X POST "$API/releases/$R2/approvals" -d '{"decision":"APPROVE"}' >/dev/null
curl -sS $(hdr registrar-c) -X POST "$API/releases/$R2/approvals" -d '{"decision":"APPROVE"}' >/dev/null
curl -sS $(hdr registrar-b) -X POST "$API/releases/$R2/approve" >/dev/null
A2=$(code $(hdr registrar-b) -X POST "$API/releases/$R2/activate")
[ "$A2" = 200 ] && ok "v2 activated" || bad "v2 activation failed ($A2)"
[ "$(PSQL "SELECT count(*) FROM org_registry.regulatory_config_release WHERE lifecycle_state='ACTIVE'")" = 1 ] \
    && ok "exactly one release is ACTIVE after the switchover" || bad "more than one ACTIVE release"

OLDFEE=$(curl -sS $(hdr) "$API/case-bindings/VARAPI/PROVIDER_APPLICATION/app-old" \
    | python3 -c "import json,sys;print(next((d['semanticVersion'] for d in json.load(sys.stdin) if d['definitionKey']=='student-index-fee'),''))")
[ "$OLDFEE" = "1.0.0" ] \
    && ok "THE DISCRIMINATOR: app-old still resolves the fee it was submitted under (1.0.0)" \
    || bad "app-old moved to fee version '$OLDFEE' — a live case was rewritten by a new release"

curl -sS $(hdr) -X POST "$API/case-bindings" -d "{
  \"caseSystem\":\"VARAPI\",\"caseType\":\"PROVIDER_APPLICATION\",\"caseRef\":\"app-new\",
  \"organizationId\":\"$NCZ\"}" >/dev/null
NEWFEE=$(curl -sS $(hdr) "$API/case-bindings/VARAPI/PROVIDER_APPLICATION/app-new" \
    | python3 -c "import json,sys;print(next((d['semanticVersion'] for d in json.load(sys.stdin) if d['definitionKey']=='student-index-fee'),''))")
[ "$NEWFEE" = "2.0.0" ] && ok "a case opened after activation uses the new fee (2.0.0)" \
    || bad "a new case resolved fee version '$NEWFEE', expected 2.0.0"

REBIND=$(curl -sS $(hdr) -X POST "$API/case-bindings" -d "{
  \"caseSystem\":\"VARAPI\",\"caseType\":\"PROVIDER_APPLICATION\",\"caseRef\":\"app-old\",
  \"organizationId\":\"$NCZ\"}" | jq_ "['releaseId']")
[ "$REBIND" = "$R1" ] && ok "re-binding app-old returns its ORIGINAL binding, never the new release" \
    || bad "re-binding moved a live case onto release '$REBIND'"

# ═════ RC-8: frozen releases and honest failure ═════════════════════════════
say "RC-8: a live release is frozen, and an unbound case is an error not a guess"
FROZEN=$(code $(hdr) -X POST "$API/releases/$R1/items" -d "{\"definitionVersionId\":\"$FORM1\"}")
[ "$FROZEN" = 409 ] && ok "a superseded release's contents cannot be edited (409)" \
    || bad "edited the contents of a release cases are bound to (got $FROZEN)"

GHOST=$(code $(hdr) "$API/case-bindings/VARAPI/PROVIDER_APPLICATION/never-opened")
[ "$GHOST" = 404 ] \
    && ok "an unbound case 404s rather than silently answering with today's configuration" \
    || bad "an unbound case resolved to something (got $GHOST)"

PSQL "SELECT case_ref, release_id FROM org_registry.regulatory_case_config_binding ORDER BY case_ref" \
    > "$EV/case-bindings.txt"
PSQL "SELECT definition_id, semantic_version, lifecycle_state FROM org_registry.regulatory_config_definition_version ORDER BY created_at" \
    > "$EV/versions.txt"

echo
echo "PASS=$PASS FAIL=$FAIL   evidence: $EV"
[ "$FAIL" -eq 0 ] || exit 1
