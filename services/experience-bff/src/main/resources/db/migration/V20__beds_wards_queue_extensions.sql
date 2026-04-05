-- V20: Bed/ward management tables and queue state extensions.
-- Supports inpatient bed tracking, ward census, and richer queue
-- state transitions (IN_SERVICE, NO_SHOW, TRANSFERRED).

-- ── Wards ────────────────────────────────────────────────────────
CREATE TABLE wards (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    facility_id     UUID NOT NULL,
    name            VARCHAR(200) NOT NULL,
    ward_type       VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    floor           VARCHAR(50),
    total_beds      INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_wards_facility ON wards (facility_id, tenant_id);

-- ── Beds ─────────────────────────────────────────────────────────
CREATE TABLE beds (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    facility_id     UUID NOT NULL,
    ward_id         UUID NOT NULL REFERENCES wards(id),
    bed_number      VARCHAR(50) NOT NULL,
    bed_type        VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    status          VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    patient_id      UUID,
    patient_name    VARCHAR(255),
    acuity          VARCHAR(20),
    admitted_at     TIMESTAMPTZ,
    assigned_doctor VARCHAR(255),
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_beds_ward ON beds (ward_id);
CREATE INDEX idx_beds_facility ON beds (facility_id, tenant_id);
CREATE INDEX idx_beds_status ON beds (status);
CREATE INDEX idx_beds_patient ON beds (patient_id) WHERE patient_id IS NOT NULL;

COMMENT ON COLUMN beds.status IS 'AVAILABLE, OCCUPIED, RESERVED, MAINTENANCE, CLEANING';
COMMENT ON COLUMN beds.acuity IS 'CRITICAL, HIGH, MEDIUM, LOW';

-- ── Queue state extensions ───────────────────────────────────────
-- Allow richer queue states beyond WAITING/CALLED/SEEN/COMPLETED.
-- The status column is VARCHAR(50) so no migration needed for the
-- column itself, just document the new valid states.

COMMENT ON COLUMN queue_entries.status IS
    'WAITING, CALLED, SEEN, IN_SERVICE, COMPLETED, NO_SHOW, TRANSFERRED, CANCELLED';

-- Add transfer tracking columns to queue entries
ALTER TABLE queue_entries ADD COLUMN IF NOT EXISTS transferred_to UUID;
ALTER TABLE queue_entries ADD COLUMN IF NOT EXISTS transfer_reason TEXT;
ALTER TABLE queue_entries ADD COLUMN IF NOT EXISTS no_show_at TIMESTAMPTZ;
