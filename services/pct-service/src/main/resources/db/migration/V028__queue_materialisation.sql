-- Queue materialisation provenance: queue definitions are OWNED by TUSO facility service-point/workspace
-- configuration and MATERIALISED into PCT for care operations. These columns record where each queue
-- came from and its materialisation state so PCT/BFF/UI can show the truth (materialised vs seed/demo,
-- stale, retired, failed) and never present seeded queues as production facility truth.

ALTER TABLE pct_queues
    ADD COLUMN IF NOT EXISTS source                 VARCHAR(32)  NOT NULL DEFAULT 'SEED_DEMO',
    ADD COLUMN IF NOT EXISTS source_ref             VARCHAR(128),
    ADD COLUMN IF NOT EXISTS materialization_status VARCHAR(32)  NOT NULL DEFAULT 'SEED_DEMO',
    ADD COLUMN IF NOT EXISTS last_materialized_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS tuso_updated_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at             TIMESTAMPTZ;

-- Existing rows (incl. the V010 demo queue) are seed/demo — labelled honestly, never production truth.
UPDATE pct_queues
   SET source = 'SEED_DEMO', materialization_status = 'SEED_DEMO'
 WHERE source IS NULL OR materialization_status IS NULL;

-- One materialised queue per (tenant, facility, TUSO source reference) — idempotent upsert key.
CREATE UNIQUE INDEX IF NOT EXISTS ux_pct_queues_facility_source_ref
    ON pct_queues (tenant_id, facility_id, source_ref)
 WHERE source_ref IS NOT NULL;
