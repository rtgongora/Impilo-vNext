ALTER TABLE vito.client_identifier
    ADD COLUMN IF NOT EXISTS source_system VARCHAR(100);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_client_identifier'
          AND conrelid = 'vito.client_identifier'::regclass
    ) THEN
        ALTER TABLE vito.client_identifier
            ADD CONSTRAINT uq_client_identifier
                UNIQUE (tenant_id, identifier_type, identifier_value);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_client_identifier_lookup
    ON vito.client_identifier (tenant_id, identifier_type, identifier_value);

ALTER TABLE vito.client
    ADD COLUMN IF NOT EXISTS source_system VARCHAR(100),
    ADD COLUMN IF NOT EXISTS source_system_patient_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_client_source_system
    ON vito.client (source_system, source_system_patient_id)
    WHERE source_system IS NOT NULL;
