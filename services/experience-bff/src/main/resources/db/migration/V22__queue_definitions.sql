-- V22: Queue definitions with service types, operating hours, and routing rules.

CREATE TABLE queue_definitions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    facility_id     UUID,
    name            VARCHAR(200) NOT NULL,
    service_type    VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    queue_type      VARCHAR(30) NOT NULL DEFAULT 'FIFO',
    priority_rules  JSONB NOT NULL DEFAULT '{}',
    operating_hours JSONB NOT NULL DEFAULT '{}',
    routing_rules   JSONB NOT NULL DEFAULT '{}',
    max_capacity    INTEGER,
    sla_target_minutes INTEGER DEFAULT 30,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_queue_defs_facility ON queue_definitions (facility_id, tenant_id);

COMMENT ON COLUMN queue_definitions.service_type IS 'GENERAL, OPD, EMERGENCY, PHARMACY, LABORATORY, TRIAGE, SPECIALIST';
COMMENT ON COLUMN queue_definitions.queue_type IS 'FIFO, PRIORITY, TRIAGE, APPOINTMENT';
COMMENT ON COLUMN queue_definitions.operating_hours IS 'JSON: {"monday":{"open":"08:00","close":"17:00"},...}';
COMMENT ON COLUMN queue_definitions.routing_rules IS 'JSON: {"afterTriage":"OPD","emergency":"EMERGENCY"}';

-- Link queue entries to definitions
ALTER TABLE queue_entries ADD COLUMN IF NOT EXISTS queue_definition_id UUID REFERENCES queue_definitions(id);

-- Seed default queue definitions for each facility
INSERT INTO queue_definitions (id, tenant_id, facility_id, name, service_type, queue_type, sla_target_minutes, operating_hours)
SELECT gen_random_uuid(), 'tenant-moh-zw', f.id, q.name, q.service_type, q.queue_type, q.sla,
       '{"monday":{"open":"08:00","close":"17:00"},"tuesday":{"open":"08:00","close":"17:00"},"wednesday":{"open":"08:00","close":"17:00"},"thursday":{"open":"08:00","close":"17:00"},"friday":{"open":"08:00","close":"17:00"}}'::jsonb
FROM facilities f
CROSS JOIN (VALUES
    ('General OPD', 'OPD', 'PRIORITY', 30),
    ('Emergency', 'EMERGENCY', 'TRIAGE', 10),
    ('Pharmacy Collection', 'PHARMACY', 'FIFO', 15),
    ('Laboratory', 'LABORATORY', 'FIFO', 20)
) AS q(name, service_type, queue_type, sla);
