ALTER TABLE varapi.provider_qualifications
    ADD COLUMN IF NOT EXISTS qualification_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS qualification_level VARCHAR(50),
    ADD COLUMN IF NOT EXISTS professional_category VARCHAR(100),
    ADD COLUMN IF NOT EXISTS verification_date DATE,
    ADD COLUMN IF NOT EXISTS verification_notes TEXT,
    ADD COLUMN IF NOT EXISTS supersedes_id BIGINT,
    ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 1,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);
