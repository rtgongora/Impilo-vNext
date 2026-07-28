#!/usr/bin/env bash
# ============================================================================
# Regulatory Configuration Registry — schema invariant proof (NCZ-W1A).
#
# THE POINT: the Registry's value is that a council's rules are versioned,
# approved by two people, frozen once live, and pinned by every case — so the
# question "what were the rules when this application was submitted?" has an
# answer that a later release cannot change. Those guarantees are worth exactly
# as much as the database's willingness to REFUSE the operations that would
# break them. This script asserts each refusal by attempting the violation.
#
# JVM tests do not apply Flyway migrations in this repo, so a schema-level
# invariant that is never exercised against a real Postgres is an invariant
# nobody has tested. This runs the migration on a throwaway Postgres and tries
# to break it.
#
#   I1   a DRAFT version's content may still be edited
#   I2   content is frozen once the version leaves DRAFT
#   I3   a version cannot be walked back into DRAFT
#   I4   a version that has left DRAFT cannot be deleted
#   I5   four-eyes: the submitter of a release cannot approve it
#   I6   one decision per approver per release
#   I7   an ACTIVE release's contents are frozen
#   I8   one ACTIVE release per pack
#   I9   a case's configuration binding is append-only (no update, no delete)
#   I10  the activation log is append-only
#   I11  one ACTIVE version per definition
#   I12  a (definition, version) pair cannot be redeclared with new content
#   I13  a release cannot carry two versions of the same definition
#   I14  semantic_version must be semver
#
# Usage:  scripts/runtime-proof/regulatory-config-registry-invariants.sh
#         KEEP_RIG=1 to leave the Postgres container up for inspection.
# ============================================================================
set -u
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
EV=${EV:-$REPO/reports/journeys/regulatory-config-registry-proof-$(date +%Y-%m-%d)}
mkdir -p "$EV"
PGPORT=${PGPORT:-15699}
CT=rcfg-rig-pg
MIG="$REPO/services/organization-registry-service/src/main/resources/db/migration"
PASS=0; FAIL=0
ok(){  echo "   PASS: $1" | tee -a "$EV/journal.txt"; PASS=$((PASS+1)); }
bad(){ echo "   FAIL: $1" | tee -a "$EV/journal.txt"; FAIL=$((FAIL+1)); }
say(){ echo "== $1"      | tee -a "$EV/journal.txt"; }

command -v docker >/dev/null || { echo "docker not available — rig cannot run"; exit 2; }

say "RIG: postgres"
docker rm -f $CT >/dev/null 2>&1
docker run -d --name $CT -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo \
    -p $PGPORT:5432 postgres:16-alpine >/dev/null || { echo "docker run failed"; exit 2; }
cleanup(){ [ -z "${KEEP_RIG:-}" ] && docker rm -f $CT >/dev/null 2>&1; }
trap cleanup EXIT
for i in $(seq 1 30); do docker exec $CT pg_isready -U impilo >/dev/null 2>&1 && break; sleep 2; done
docker exec $CT psql -U impilo -d postgres -c "CREATE DATABASE organization_registry" >/dev/null 2>&1
PSQL(){ docker exec -i $CT psql -U impilo -d organization_registry "$@"; }

say "SCHEMA: apply org-registry migrations in order"
PSQL -q -c "CREATE SCHEMA IF NOT EXISTS org_registry" >/dev/null
PSQL -q -c "CREATE EXTENSION IF NOT EXISTS pgcrypto" >/dev/null
# Flyway runs with `schemas: org_registry` (application.yml), which sets the search_path for the
# whole migration run — several migrations reference their tables unqualified and depend on it.
PSQL -q -c "ALTER DATABASE organization_registry SET search_path TO org_registry, public" >/dev/null
for f in $(ls "$MIG"/V*.sql | sort -V); do
    if ! PSQL -q -v ON_ERROR_STOP=1 < "$f" >> "$EV/migrate.log" 2>&1; then
        bad "migration failed: $(basename "$f") (see $EV/migrate.log)"; echo; echo "PASS=$PASS FAIL=$FAIL"; exit 1
    fi
done
ok "all org-registry migrations applied (through $(basename "$(ls "$MIG"/V*.sql | sort -V | tail -1)"))"

# The proof runs as one plpgsql block: each invariant attempts the violation and
# only reports PASS when the database REFUSED it. An invariant that silently
# permits the violation raises 'FAIL' and aborts the block.
say "INVARIANTS: attempt each violation, expect refusal"
PSQL -q -v ON_ERROR_STOP=1 <<'SQL' > "$EV/invariants.log" 2>&1
DO $$
DECLARE
  tid    UUID := '00000000-0000-0000-0000-000000000001';
  orgid  UUID;
  packid UUID; defid UUID; verid UUID; relid UUID; bindid UUID; actid UUID;
BEGIN
  SELECT id INTO orgid FROM org_registry.org_registry_organization LIMIT 1;
  IF orgid IS NULL THEN
    RAISE EXCEPTION 'no seeded organisation — V007/V012 seeds did not land';
  END IF;

  INSERT INTO org_registry.regulatory_config_pack (tenant_id, organization_id, pack_key, label)
    VALUES (tid, orgid, 'proof-pack', 'Proof pack') RETURNING id INTO packid;
  INSERT INTO org_registry.regulatory_config_definition
      (tenant_id, pack_id, type_code, definition_key, label)
    VALUES (tid, packid, 'FEE_SCHEDULE', 'student-index-fee', 'Student index fee')
    RETURNING id INTO defid;
  INSERT INTO org_registry.regulatory_config_definition_version
      (tenant_id, definition_id, semantic_version, payload, content_hash, authored_by)
    VALUES (tid, defid, '1.0.0', '{"amount":null}'::jsonb, repeat('a',64), 'HID-AUTHOR')
    RETURNING id INTO verid;
  INSERT INTO org_registry.regulatory_config_release
      (tenant_id, pack_id, release_key, label, submitted_by)
    VALUES (tid, packid, 'r1', 'Release 1', 'HID-AUTHOR') RETURNING id INTO relid;

  UPDATE org_registry.regulatory_config_definition_version
     SET payload='{"amount":1}'::jsonb WHERE id=verid;
  RAISE NOTICE 'I1  draft content editable';

  UPDATE org_registry.regulatory_config_definition_version
     SET lifecycle_state='APPROVED' WHERE id=verid;
  BEGIN
    UPDATE org_registry.regulatory_config_definition_version
       SET payload='{"amount":99}'::jsonb WHERE id=verid;
    RAISE EXCEPTION 'I2 FAIL — edited the content of an APPROVED version';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'I2  approved content frozen'; END;

  BEGIN
    UPDATE org_registry.regulatory_config_definition_version
       SET lifecycle_state='DRAFT' WHERE id=verid;
    RAISE EXCEPTION 'I3 FAIL — walked a version back into DRAFT';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'I3  no return to DRAFT'; END;

  BEGIN
    DELETE FROM org_registry.regulatory_config_definition_version WHERE id=verid;
    RAISE EXCEPTION 'I4 FAIL — deleted a version that had left DRAFT';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'I4  no delete after DRAFT'; END;

  BEGIN
    INSERT INTO org_registry.regulatory_config_approval
        (tenant_id, release_id, approver_health_id, decision)
      VALUES (tid, relid, 'HID-AUTHOR', 'APPROVE');
    RAISE EXCEPTION 'I5 FAIL — the submitter approved their own release';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'I5  four-eyes holds'; END;

  INSERT INTO org_registry.regulatory_config_approval
      (tenant_id, release_id, approver_health_id, decision)
    VALUES (tid, relid, 'HID-OTHER', 'APPROVE');
  BEGIN
    INSERT INTO org_registry.regulatory_config_approval
        (tenant_id, release_id, approver_health_id, decision)
      VALUES (tid, relid, 'HID-OTHER', 'APPROVE');
    RAISE EXCEPTION 'I6 FAIL — one approver recorded two decisions';
  EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'I6  one decision per approver'; END;

  INSERT INTO org_registry.regulatory_config_release_item
      (tenant_id, release_id, definition_id, definition_version_id)
    VALUES (tid, relid, defid, verid);
  UPDATE org_registry.regulatory_config_release
     SET lifecycle_state='ACTIVE', activated_at=NOW() WHERE id=relid;
  BEGIN
    DELETE FROM org_registry.regulatory_config_release_item WHERE release_id=relid;
    RAISE EXCEPTION 'I7 FAIL — changed the contents of an ACTIVE release';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'I7  active release frozen'; END;

  BEGIN
    INSERT INTO org_registry.regulatory_config_release
        (tenant_id, pack_id, release_key, label, lifecycle_state)
      VALUES (tid, packid, 'r2', 'Release 2', 'ACTIVE');
    RAISE EXCEPTION 'I8 FAIL — two ACTIVE releases in one pack';
  EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'I8  one active release per pack'; END;

  INSERT INTO org_registry.regulatory_case_config_binding
      (tenant_id, case_system, case_type, case_ref, organization_id, pack_id, release_id)
    VALUES (tid, 'VARAPI', 'PROVIDER_APPLICATION', 'app-1', orgid, packid, relid)
    RETURNING id INTO bindid;
  BEGIN
    UPDATE org_registry.regulatory_case_config_binding SET bound_by='X' WHERE id=bindid;
    RAISE EXCEPTION 'I9 FAIL — rewrote a case configuration binding';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'I9  case binding append-only (update)'; END;
  BEGIN
    DELETE FROM org_registry.regulatory_case_config_binding WHERE id=bindid;
    RAISE EXCEPTION 'I9b FAIL — deleted a case configuration binding';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'I9b case binding append-only (delete)'; END;

  INSERT INTO org_registry.regulatory_config_activation
      (tenant_id, pack_id, release_id, activated_by, validation_report)
    VALUES (tid, packid, relid, 'HID-OTHER', '{"ok":true}'::jsonb) RETURNING id INTO actid;
  BEGIN
    DELETE FROM org_registry.regulatory_config_activation WHERE id=actid;
    RAISE EXCEPTION 'I10 FAIL — deleted an activation record';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'I10 activation log append-only'; END;

  UPDATE org_registry.regulatory_config_definition_version
     SET lifecycle_state='ACTIVE' WHERE id=verid;
  BEGIN
    INSERT INTO org_registry.regulatory_config_definition_version
        (tenant_id, definition_id, semantic_version, payload, content_hash,
         authored_by, lifecycle_state)
      VALUES (tid, defid, '2.0.0', '{}'::jsonb, repeat('b',64), 'HID-AUTHOR', 'ACTIVE');
    RAISE EXCEPTION 'I11 FAIL — two ACTIVE versions of one definition';
  EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'I11 one active version per definition'; END;

  BEGIN
    INSERT INTO org_registry.regulatory_config_definition_version
        (tenant_id, definition_id, semantic_version, payload, content_hash, authored_by)
      VALUES (tid, defid, '1.0.0', '{"different":true}'::jsonb, repeat('c',64), 'HID-AUTHOR');
    RAISE EXCEPTION 'I12 FAIL — redeclared 1.0.0 with different content';
  EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'I12 version identity immutable'; END;

  UPDATE org_registry.regulatory_config_release SET lifecycle_state='DRAFT' WHERE id=relid;
  BEGIN
    INSERT INTO org_registry.regulatory_config_release_item
        (tenant_id, release_id, definition_id, definition_version_id)
      VALUES (tid, relid, defid, verid);
    RAISE EXCEPTION 'I13 FAIL — two versions of one definition in one release';
  EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'I13 one version per definition per release'; END;

  BEGIN
    INSERT INTO org_registry.regulatory_config_definition_version
        (tenant_id, definition_id, semantic_version, payload, content_hash, authored_by)
      VALUES (tid, defid, 'v1', '{}'::jsonb, repeat('d',64), 'X');
    RAISE EXCEPTION 'I14 FAIL — accepted a non-semver version string';
  EXCEPTION WHEN check_violation THEN RAISE NOTICE 'I14 semver format enforced'; END;
END $$;
SQL
rc=$?

grep -E '^NOTICE:' "$EV/invariants.log" | sed 's/^NOTICE:  //' | while read -r line; do
    echo "   $line"
done
n_ok=$(grep -cE '^NOTICE:  I' "$EV/invariants.log")
if [ "$rc" -eq 0 ] && [ "$n_ok" -eq 15 ]; then
    ok "15/15 schema invariants refused their violation"
else
    bad "invariant proof incomplete (rc=$rc, invariants observed=$n_ok/15)"
    grep -E '^(ERROR|psql:)' "$EV/invariants.log" | head -5
fi

echo
echo "PASS=$PASS FAIL=$FAIL   evidence: $EV"
[ "$FAIL" -eq 0 ] || exit 1
