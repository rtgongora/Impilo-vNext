-- =============================================================================
-- Service : zibo-service
-- Database: zibo
-- Purpose : Load terminology seed data from JSON
-- =============================================================================

\c zibo

-- Create a temporary table to hold the raw JSON data
CREATE TEMP TABLE tmp_zibo_json (content text);

-- Load the JSON file (this assumes the file is accessible to psql, 
-- e.g. via cat and piping or volume mount)
-- In the seed-data container, we will use: 
-- cat /scripts/seed/zibo_seed.json | psql -h postgres -U impilo -d zibo -c "COPY tmp_zibo_json FROM STDIN"

INSERT INTO zibo_artifacts (
    tenant_id, 
    fhir_type, 
    canonical_url, 
    version, 
    name, 
    title, 
    status, 
    content_json, 
    content_hash, 
    created_by
)
SELECT 
    (elem->>'tenant_id')::uuid,
    elem->>'fhir_type',
    elem->>'canonical_url',
    elem->>'version',
    elem->>'name',
    elem->>'title',
    elem->>'status',
    (elem->'content_json')::jsonb,
    encode(sha256((elem->'content_json')::text::bytea), 'hex'),
    elem->>'created_by'
FROM (
    SELECT jsonb_array_elements(content::jsonb) AS elem 
    FROM tmp_zibo_json
) seed_data
ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;

DROP TABLE tmp_zibo_json;
