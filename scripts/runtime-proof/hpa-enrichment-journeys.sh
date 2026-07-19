#!/usr/bin/env bash
# =============================================================================
# HPA enrichment — match-or-create runtime-proof rig.
#
# Proves the Tuso HPA match-or-create end-to-end against REAL data on a throwaway
# Postgres, using the SAME pg_trgm engine the production FacilityMatchService uses
# (real GIN trigram index) and the REAL V036 schema + a snapshot of the live
# ~1,776-facility estate + the REAL 6,327-record candidate feed:
#
#   1. load the live preview tuso SCHEMA (real, deployed) + facility DATA + V036
#   2. DRY-RUN classify every candidate into the canonical outcomes (no writes)
#   3. APPLY: create incomplete REGULATOR_LISTED facilities for NEW_* outcomes,
#      stamping a deterministic HPA_INSTITUTION_ID = HPA-<id> idempotency key
#   4. IDEMPOTENCY: a second apply creates ZERO (NOT-EXISTS guards + the key)
#   5. ROLLBACK BY BATCH: delete only HPA-* rows; the canonical estate is intact
#
# This mirrors HpaEnrichmentImportService.reconcile(); the committed Java service
# is the production capability, this rig proves the reconciliation at data scale.
#
# Usage: NS=impilo-full-preview FEED=/path/to/hpa_tuso_candidate_feed.jsonl bash "$0"
# =============================================================================
set -uo pipefail
RIG="${RIG:-hpa-proof-pg}"
NS="${NS:-impilo-full-preview}"
FEED="${FEED:-/home/robert/impilo-imports/tuso-hpa/2026-07-17/bundle/data/hpa_tuso_candidate_feed.jsonl}"
TEN="${TEN:-00000000-0000-0000-0000-000000000001}"
V036="$(cd "$(dirname "$0")/../.." && pwd)/services/tuso-service/src/main/resources/db/migration/V036__hpa_facility_enrichment.sql"
PSQL(){ docker exec "$RIG" psql -U impilo -d tuso -tAc "$1"; }

echo "== 1. rig: real schema + estate + V036 =="
docker rm -f "$RIG" >/dev/null 2>&1 || true
docker run -d --name "$RIG" -e POSTGRES_USER=impilo -e POSTGRES_PASSWORD=impilo -e POSTGRES_DB=tuso postgres:16-alpine >/dev/null
for i in $(seq 1 30); do docker exec "$RIG" pg_isready -U impilo >/dev/null 2>&1 && break; sleep 1; done
kubectl exec -n "$NS" deploy/postgres -- pg_dump -U impilo -d tuso --schema-only --no-owner --no-privileges 2>/dev/null | docker exec -i "$RIG" psql -U impilo -d tuso -q
kubectl exec -n "$NS" deploy/postgres -- pg_dump -U impilo -d tuso --data-only --no-owner -t tuso.facility -t tuso.facility_identifier -t tuso.facility_contact 2>/dev/null | docker exec -i "$RIG" psql -U impilo -d tuso -q
docker cp "$V036" "$RIG:/tmp/v036.sql"; docker exec "$RIG" psql -U impilo -d tuso -q -v ON_ERROR_STOP=1 -f /tmp/v036.sql >/dev/null
echo "estate: $(PSQL 'select count(*) from tuso.facility;') facilities · pg_trgm: $(PSQL "select count(*) from pg_extension where extname='pg_trgm';")"

echo "== 2. load feed =="
python3 - "$FEED" > /tmp/hpa_feed.tsv <<'PY'
import json,sys
cl=lambda s:(s or "").replace("\t"," ").replace("\n"," ").replace("\r"," ").strip()
for line in open(sys.argv[1]):
    if not line.strip():continue
    r=json.loads(line);h=r.get("current_hpa",{})
    print("\t".join([cl(r.get("source_record_key")),str(h.get("hpa_institution_id") or ""),cl(h.get("institution_name_raw")),cl(h.get("registration_number_normalized")),cl(h.get("province")),cl(h.get("site_resolution_status"))]))
PY
docker exec "$RIG" psql -U impilo -d tuso -q -c "DROP TABLE IF EXISTS hpa_feed; CREATE TABLE hpa_feed(srk text,hpa_id bigint,name text,regnum text,province text,site_res text);"
docker cp /tmp/hpa_feed.tsv "$RIG:/tmp/hpa_feed.tsv"
docker exec "$RIG" psql -U impilo -d tuso -q -c "\copy hpa_feed FROM '/tmp/hpa_feed.tsv' WITH (FORMAT text, DELIMITER E'\t', NULL '')"
echo "feed: $(PSQL 'select count(*) from hpa_feed;') candidates"

echo "== 3. DRY-RUN classify (real pg_trgm matcher, no facility writes) =="
docker exec "$RIG" psql -U impilo -d tuso -q <<SQL
SET search_path=tuso,public;
DROP TABLE IF EXISTS tuso.feed_dec CASCADE;
CREATE TABLE tuso.feed_dec AS
SELECT fe.srk,fe.hpa_id,fe.name,fe.regnum,fe.site_res,
  EXISTS(SELECT 1 FROM tuso.facility_identifier fi WHERE fi.system='HPA_INSTITUTION_ID' AND fi.value='HPA-'||fe.hpa_id) idem,
  m.id fid,m.s score,m.cnt
FROM hpa_feed fe LEFT JOIN LATERAL (
  SELECT count(*) OVER () cnt,id,similarity(name::text,fe.name) s
  FROM tuso.facility WHERE tenant_id='$TEN' AND name::text % fe.name
  ORDER BY similarity(name::text,fe.name) DESC LIMIT 1) m ON true;
ALTER TABLE tuso.feed_dec ADD COLUMN outcome text;
UPDATE tuso.feed_dec SET outcome=CASE
  WHEN name IS NULL OR btrim(name)='' THEN 'QUARANTINED_DATA_QUALITY'
  WHEN idem THEN 'CONFIRMED_EXISTING_ENRICH'
  WHEN score>=0.72 AND cnt=1 THEN 'CONFIRMED_EXISTING_ENRICH'
  WHEN fid IS NOT NULL THEN 'POSSIBLE_EXISTING_REVIEW'
  WHEN site_res LIKE '%UNRESOLVED%' THEN 'NEW_ESTABLISHMENT_SITE_UNRESOLVED'
  ELSE 'NEW_REGULATED_ESTABLISHMENT' END;
SQL
docker exec "$RIG" psql -U impilo -d tuso -c "SELECT outcome,count(*) FROM tuso.feed_dec GROUP BY 1 ORDER BY 2 DESC;"
BEFORE=$(PSQL 'select count(*) from tuso.facility;')

echo "== 4. APPLY (create NEW facilities + stamp HPA_INSTITUTION_ID key) =="
cat > /tmp/apply.sql <<SQL
SET search_path=tuso,public;
INSERT INTO tuso.facility (tenant_id,facility_code,name,status)
SELECT '$TEN','HPA-'||d.hpa_id,d.name,'INACTIVE' FROM tuso.feed_dec d
WHERE d.outcome LIKE 'NEW_%' AND NOT EXISTS(SELECT 1 FROM tuso.facility f WHERE f.tenant_id='$TEN' AND f.facility_code='HPA-'||d.hpa_id);
INSERT INTO tuso.facility_identifier (facility_id,system,value,active)
SELECT f.id,'HPA_INSTITUTION_ID','HPA-'||d.hpa_id,true FROM tuso.feed_dec d
JOIN tuso.facility f ON f.facility_code='HPA-'||d.hpa_id AND f.tenant_id='$TEN'
WHERE d.outcome LIKE 'NEW_%' AND NOT EXISTS(SELECT 1 FROM tuso.facility_identifier fi WHERE fi.system='HPA_INSTITUTION_ID' AND fi.value='HPA-'||d.hpa_id);
SQL
docker cp /tmp/apply.sql "$RIG:/tmp/apply.sql"; docker exec "$RIG" psql -U impilo -d tuso -q -f /tmp/apply.sql
A1=$(PSQL 'select count(*) from tuso.facility;'); echo "after apply: $A1 (was $BEFORE; +$((A1-BEFORE)))"

echo "== 5. IDEMPOTENCY (second apply must create 0) =="
docker exec "$RIG" psql -U impilo -d tuso -q -f /tmp/apply.sql
A2=$(PSQL 'select count(*) from tuso.facility;')
[ "$A2" = "$A1" ] && echo "IDEMPOTENT: yes ($A1 stable, 0 new)" || echo "NOT IDEMPOTENT ($A1 -> $A2)"

echo "== 6. ROLLBACK BY BATCH (canonical never hard-deleted) =="
docker exec "$RIG" psql -U impilo -d tuso -q -c "SET search_path=tuso,public; DELETE FROM tuso.facility_identifier WHERE system='HPA_INSTITUTION_ID' AND value LIKE 'HPA-%'; DELETE FROM tuso.facility WHERE tenant_id='$TEN' AND facility_code LIKE 'HPA-%';"
R=$(PSQL 'select count(*) from tuso.facility;')
[ "$R" = "$BEFORE" ] && echo "ROLLBACK: restored to $BEFORE canonical" || echo "ROLLBACK mismatch ($R)"
echo "done. (docker rm -f $RIG to clean up)"
