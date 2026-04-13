-- V11: Triage records for clinical acuity capture within encounters.
-- Bridges the PCT sovereign triage capability into the experience BFF
-- so triage data is accessible alongside other encounter workspace data.

CREATE TABLE IF NOT EXISTS triage_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL,
    encounter_id    UUID REFERENCES encounters(id),
    queue_entry_id  UUID,
    pct_journey_id  VARCHAR(255),
    acuity          INTEGER NOT NULL CHECK (acuity BETWEEN 1 AND 5),
    chief_complaint TEXT,
    danger_signs    JSONB DEFAULT '[]',
    vitals          JSONB,
    notes           TEXT,
    triaged_by      VARCHAR(255) NOT NULL,
    triaged_by_name VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_triage_records_tenant ON triage_records(tenant_id);
CREATE INDEX IF NOT EXISTS idx_triage_records_patient ON triage_records(tenant_id, patient_id);
CREATE INDEX IF NOT EXISTS idx_triage_records_encounter ON triage_records(encounter_id);
