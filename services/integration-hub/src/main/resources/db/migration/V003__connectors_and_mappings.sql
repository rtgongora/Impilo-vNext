-- V003: Connector kit and mapping templates

-- Add connector type and mapping template reference to route definitions
ALTER TABLE ih_route_definitions
    ADD COLUMN connector_type       VARCHAR(16) DEFAULT 'HTTP',
    ADD COLUMN mapping_template_id  VARCHAR(36);

-- Mapping templates table
CREATE TABLE ih_mapping_templates (
    id                  VARCHAR(36)     NOT NULL PRIMARY KEY,
    name                VARCHAR(256)    NOT NULL,
    tenant_id           VARCHAR(64)     NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL,
    source_format       VARCHAR(64),
    target_format       VARCHAR(64),
    mapping_rules_json  TEXT,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    version             INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ih_mapping_templates_tenant        ON ih_mapping_templates (tenant_id);
CREATE INDEX idx_ih_mapping_templates_tenant_active  ON ih_mapping_templates (tenant_id, active);
CREATE INDEX idx_ih_mapping_templates_name_tenant    ON ih_mapping_templates (name, tenant_id);

-- Add replay_count column to dead-letter queue if not exists
-- (retry_count already exists from V002, this is a safety no-op check)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ih_dead_letter_queue' AND column_name = 'replay_count'
    ) THEN
        ALTER TABLE ih_dead_letter_queue ADD COLUMN replay_count INT NOT NULL DEFAULT 0;
    END IF;
END $$;
