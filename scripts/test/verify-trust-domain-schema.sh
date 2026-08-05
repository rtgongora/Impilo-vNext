#!/usr/bin/env bash
# Database execution proof for org-registry V016 (Wave 1A).
#
# The repository's Testcontainers ITs cannot run in every environment: Testcontainers
# 1.21.4 bundles a docker-java that negotiates Docker API 1.32, and a daemon at 1.40+
# refuses it ("client version 1.32 is too old"). The pre-existing clinical ITs skip for
# the same reason. A skipped test is not evidence, so this script proves the schema
# directly: it starts a real PostgreSQL, applies the migrations, and then tries to
# break every constraint the migration claims to enforce.
set -uo pipefail

CT="orgreg-schema-proof-$$"
PGPASS=proof
FAIL=0
ok()   { echo "  PASS  $1"; }
bad()  { echo "  FAIL  $1"; FAIL=1; }

cleanup() { docker rm -f "$CT" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "== starting postgres:16-alpine"
docker run -d --name "$CT" -e POSTGRES_PASSWORD="$PGPASS" -e POSTGRES_DB=orgreg \
  postgres:16-alpine >/dev/null || { echo "cannot start postgres"; exit 2; }
# pg_isready alone is not enough: postgres runs a temporary init server first, and it
# answers ready while the real database does not yet exist. Wait for an actual query.
ready=0
for i in $(seq 1 60); do
  if docker exec "$CT" psql -U postgres -d orgreg -tAc 'SELECT 1' >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
[ "$ready" = "1" ] || { echo "postgres never became queryable"; exit 2; }

psql() { docker exec -i "$CT" psql -v ON_ERROR_STOP=1 -U postgres -d orgreg "$@"; }
# Returns 0 if the statement FAILED (which is what a constraint proof wants).
refused() { ! docker exec -i "$CT" psql -q -v ON_ERROR_STOP=1 -U postgres -d orgreg -c "$1" >/dev/null 2>&1; }

echo "== applying migrations"
psql -c 'CREATE SCHEMA IF NOT EXISTS org_registry;' >/dev/null
# Flyway applies these with search_path set to the configured schema, so several
# migrations reference tables unqualified. Reproduce that, or V011 fails on a table it
# can see perfectly well.
for f in services/organization-registry-service/src/main/resources/db/migration/V*.sql; do
  docker exec -i -e PGOPTIONS='-c search_path=org_registry,public' "$CT" \
    psql -q -v ON_ERROR_STOP=1 -U postgres -d orgreg < "$f" >/dev/null 2>&1 \
    || { echo "  migration failed: $(basename "$f")"; exit 2; }
done
echo "  applied $(ls services/organization-registry-service/src/main/resources/db/migration/V*.sql | wc -l) migrations"

echo "== 1. all eight tables exist"
for t in trust_domain trust_domain_relationship trust_domain_membership \
         service_responsibility_profile service_agreement processing_role_assignment \
         joint_controller_arrangement responsibility_audit_event; do
  n=$(psql -tAqc "SELECT count(*) FROM information_schema.tables WHERE table_schema='org_registry' AND table_name='$t'")
  [ "$n" = "1" ] && ok "table $t" || bad "table $t missing"
done

TD=$(psql -tAqc "INSERT INTO org_registry.trust_domain (trust_domain_id, code, display_name, accreditation_status, key_custody_mode, created_by_actor, version) VALUES (gen_random_uuid(),'TD-PROOF','Proof','PROVISIONAL','NATIONAL','proof',0) RETURNING trust_domain_id")

echo "== 2. a trust domain exists with NO controller identity (ADR-0055 d1)"
n=$(psql -tAqc "SELECT count(*) FROM org_registry.trust_domain WHERE trust_domain_id='$TD' AND controller_type IS NULL AND data_controller_legal_name IS NULL AND data_controller_contact IS NULL AND controller_declaration_state='UNDETERMINED'")
[ "$n" = "1" ] && ok "controller identity optional, state UNDETERMINED" || bad "trust domain forced a controller"

echo "== 3. DECLARED without a name is refused"
refused "INSERT INTO org_registry.trust_domain (trust_domain_id,code,display_name,controller_declaration_state,accreditation_status,key_custody_mode,created_by_actor,version) VALUES (gen_random_uuid(),'TD-BAD','B','DECLARED','PROVISIONAL','NATIONAL','p',0)" \
  && ok "declared-without-name refused" || bad "a DECLARED controller with no name was accepted"

echo "== 4. one active legal controller per (scope, domain, purpose)"
SC=$(psql -tAqc "SELECT gen_random_uuid()")
psql -q -c "INSERT INTO org_registry.processing_role_assignment (assignment_id,scope_type,scope_id,data_domain,purpose,legal_basis,role_type,party_id,effective_from,status,recorded_by) VALUES (gen_random_uuid(),'FACILITY','$SC','encounters','TREATMENT','PUBLIC_TASK','LEGAL_CONTROLLER',gen_random_uuid(),now(),'ACTIVE','p')" >/dev/null
refused "INSERT INTO org_registry.processing_role_assignment (assignment_id,scope_type,scope_id,data_domain,purpose,legal_basis,role_type,party_id,effective_from,status,recorded_by) VALUES (gen_random_uuid(),'FACILITY','$SC','encounters','TREATMENT','PUBLIC_TASK','LEGAL_CONTROLLER',gen_random_uuid(),now(),'ACTIVE','p')" \
  && ok "second active controller refused" || bad "two active legal controllers were accepted"

echo "== 5. joint controllers may coexist"
psql -q -c "INSERT INTO org_registry.processing_role_assignment (assignment_id,scope_type,scope_id,data_domain,purpose,legal_basis,role_type,party_id,effective_from,status,recorded_by) SELECT gen_random_uuid(),'FACILITY','$SC','phr','TREATMENT','CONSENT','JOINT_CONTROLLER',gen_random_uuid(),now(),'ACTIVE','p' FROM generate_series(1,2)" >/dev/null 2>&1
n=$(psql -tAqc "SELECT count(*) FROM org_registry.processing_role_assignment WHERE scope_id='$SC' AND role_type='JOINT_CONTROLLER'")
[ "$n" = "2" ] && ok "joint control is not two controllers" || bad "joint controllers rejected (got $n)"

echo "== 6. one active membership per subject"
SUB=$(psql -tAqc "SELECT gen_random_uuid()")
TD2=$(psql -tAqc "INSERT INTO org_registry.trust_domain (trust_domain_id,code,display_name,accreditation_status,key_custody_mode,created_by_actor,version) VALUES (gen_random_uuid(),'TD-PROOF-2','P2','PROVISIONAL','NATIONAL','p',0) RETURNING trust_domain_id")
psql -q -c "INSERT INTO org_registry.trust_domain_membership (membership_id,trust_domain_id,subject_type,subject_id,status,effective_from,source_authority,provenance,created_by) VALUES (gen_random_uuid(),'$TD','ORGANISATION','$SUB','ACTIVE',now(),'MOHCC_REGISTRY','{}'::jsonb,'p')" >/dev/null
refused "INSERT INTO org_registry.trust_domain_membership (membership_id,trust_domain_id,subject_type,subject_id,status,effective_from,source_authority,provenance,created_by) VALUES (gen_random_uuid(),'$TD2','ORGANISATION','$SUB','ACTIVE',now(),'MOHCC_REGISTRY','{}'::jsonb,'p')" \
  && ok "second active membership refused" || bad "a subject held two active memberships"

echo "== 7. membership evidence must be a recognised class (ADR-0055 d4)"
refused "INSERT INTO org_registry.trust_domain_membership (membership_id,trust_domain_id,subject_type,subject_id,status,source_authority,provenance,created_by) VALUES (gen_random_uuid(),'$TD','ORGANISATION',gen_random_uuid(),'PENDING_REVIEW','ORGANISATION_TYPE','{}'::jsonb,'p')" \
  && ok "'ORGANISATION_TYPE' rejected as evidence" || bad "organisation type was accepted as membership evidence"

echo "== 8. only an ACTIVE membership carries a period"
refused "INSERT INTO org_registry.trust_domain_membership (membership_id,trust_domain_id,subject_type,subject_id,status,effective_from,source_authority,provenance,created_by) VALUES (gen_random_uuid(),'$TD','ORGANISATION',gen_random_uuid(),'PENDING_REVIEW',now(),'MOHCC_REGISTRY','{}'::jsonb,'p')" \
  && ok "dated PENDING_REVIEW refused" || bad "a non-active membership carried a period"

echo "== 9. RESOLVED requires a controller; UNDETERMINED must not carry one"
refused "INSERT INTO org_registry.service_responsibility_profile (responsibility_profile_id,profile_code,hosting_model,controller_resolution_state,status,version,created_by_actor) VALUES (gen_random_uuid(),'P-BAD','NATIONAL_INFRASTRUCTURE','RESOLVED','DRAFT',1,'p')" \
  && ok "RESOLVED-without-controller refused" || bad "RESOLVED with no controller was accepted"
psql -q -c "INSERT INTO org_registry.service_responsibility_profile (responsibility_profile_id,profile_code,hosting_model,controller_resolution_state,status,version,created_by_actor) VALUES (gen_random_uuid(),'P-OK','NATIONAL_INFRASTRUCTURE','UNDETERMINED','DRAFT',1,'p')" >/dev/null \
  && ok "UNDETERMINED profile creatable — the open question is recordable" || bad "could not create an UNDETERMINED profile"

echo "== 10. audit is append-only"
AE=$(psql -tAqc "INSERT INTO org_registry.responsibility_audit_event (audit_event_id,actor_id,operation,target_type,decision) VALUES (gen_random_uuid(),'p','PROOF','TRUST_DOMAIN','PERMITTED') RETURNING audit_event_id")
refused "UPDATE org_registry.responsibility_audit_event SET decision='REFUSED' WHERE audit_event_id='$AE'" \
  && ok "UPDATE refused" || bad "an audit row was updated"
refused "DELETE FROM org_registry.responsibility_audit_event WHERE audit_event_id='$AE'" \
  && ok "DELETE refused" || bad "an audit row was deleted"

echo "== 11. history survives supersession"
P1=$(psql -tAqc "INSERT INTO org_registry.service_responsibility_profile (responsibility_profile_id,profile_code,hosting_model,status,version,created_by_actor) VALUES (gen_random_uuid(),'P-HIST','INSTITUTION_PREMISES','ACTIVE',1,'p') RETURNING responsibility_profile_id")
psql -q -c "INSERT INTO org_registry.service_responsibility_profile (responsibility_profile_id,profile_code,hosting_model,status,version,supersedes_profile_id,supersession_reason,created_by_actor) VALUES (gen_random_uuid(),'P-HIST','INSTITUTION_PREMISES','DRAFT',2,'$P1','operator change','p')" >/dev/null
psql -q -c "UPDATE org_registry.service_responsibility_profile SET status='SUPERSEDED' WHERE responsibility_profile_id='$P1'" >/dev/null
n=$(psql -tAqc "SELECT count(*) FROM org_registry.service_responsibility_profile WHERE profile_code='P-HIST'")
[ "$n" = "2" ] && ok "predecessor retained, not deleted" || bad "history lost on supersession (rows=$n)"

echo "== 12. the migration fabricated nothing"
for q in "SELECT count(*) FROM org_registry.org_registry_organization WHERE trust_domain_id IS NOT NULL|organisation mapped by migration" \
         "SELECT count(*) FROM org_registry.trust_domain WHERE code='MOHCC-ZW'|MOHCC-ZW seeded"; do
  sql="${q%%|*}"; label="${q##*|}"
  n=$(psql -tAqc "$sql")
  [ "$n" = "0" ] && ok "no $label" || bad "$label: $n rows"
done

echo
[ "$FAIL" = "0" ] && echo "SCHEMA PROOF: PASS" || echo "SCHEMA PROOF: FAIL"
exit "$FAIL"
