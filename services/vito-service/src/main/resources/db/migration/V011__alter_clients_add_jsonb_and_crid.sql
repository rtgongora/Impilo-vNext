-- V011: Extend client table with CRID, JSONB columns, and impilo_id
-- The CRID is the Client Registry ID (UUID), distinct from health_id.
-- JSONB columns enable flexible PII storage while flat columns remain for indexed queries.

-- Step 1: add nullable columns first
ALTER TABLE vito.client
    ADD COLUMN IF NOT EXISTS crid UUID,
    ADD COLUMN IF NOT EXISTS impilo_id VARCHAR(10),
    ADD COLUMN IF NOT EXISTS demographics JSONB DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS contacts JSONB DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS address JSONB DEFAULT '{}';

-- Step 2: backfill CRID from health_id for existing rows
UPDATE vito.client SET crid = health_id WHERE crid IS NULL;

-- Step 3: add NOT NULL constraint and default for future rows
ALTER TABLE vito.client ALTER COLUMN crid SET NOT NULL;
ALTER TABLE vito.client ALTER COLUMN crid SET DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX IF NOT EXISTS idx_client_crid ON vito.client (crid);
CREATE INDEX IF NOT EXISTS idx_client_impilo_id ON vito.client (impilo_id) WHERE impilo_id IS NOT NULL;
