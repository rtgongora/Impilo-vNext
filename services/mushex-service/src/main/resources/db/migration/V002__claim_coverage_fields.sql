ALTER TABLE mushex_claims
    ADD COLUMN patient_cpid VARCHAR(255),
    ADD COLUMN plan_code VARCHAR(64),
    ADD COLUMN service_code VARCHAR(64),
    ADD COLUMN preauth_required BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN denial_reason VARCHAR(500);
