ALTER TABLE varapi.provider_compliance_actions
    ADD COLUMN IF NOT EXISTS requirement TEXT,
    ADD COLUMN IF NOT EXISTS verification_date DATE,
    ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 1,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

ALTER TABLE varapi.provider_practice_contexts
    ADD COLUMN IF NOT EXISTS location_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS authorization_reference VARCHAR(100),
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);
