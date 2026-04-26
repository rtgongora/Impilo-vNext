ALTER TABLE varapi.provider_certificates
    ADD COLUMN IF NOT EXISTS certificate_number VARCHAR(50),
    ADD COLUMN IF NOT EXISTS renewal_reference VARCHAR(100),
    ADD COLUMN IF NOT EXISTS suspension_date DATE,
    ADD COLUMN IF NOT EXISTS revocation_date DATE,
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);
