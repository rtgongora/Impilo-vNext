-- =============================================================================
-- Tuso V031 — Multi-dimensional facility trust (Place Journey Doctrine §4, D-L8)
--
-- No single VERIFIED=TRUE: each trust dimension is tracked separately, graded,
-- and materialised from its underlying signal. facility_source_legitimacy
-- (V016) remains the platform-access GATE ("no source denies AND at least one
-- allows") — it becomes one INPUT here, never the vocabulary.
--
-- Dimensions (doctrine §4): EXISTENCE, GEOSPATIAL, AUTHORITY, LEGAL_IDENTITY,
-- REGULATORY, CAPABILITY, OPERATIONAL, COMPLIANCE, PUBLIC_DISCLOSURE.
-- Statuses: CONFIRMED | PARTIAL | UNKNOWN | FAILED. UNKNOWN is honest — a
-- dimension whose signal is not yet wired (e.g. EXISTENCE before the W6
-- verification cases) says so instead of pretending.
-- =============================================================================

CREATE TABLE IF NOT EXISTS tuso.facility_trust_dimension (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    facility_uuid   UUID            NOT NULL,
    dimension       VARCHAR(32)     NOT NULL,
    status          VARCHAR(16)     NOT NULL DEFAULT 'UNKNOWN',
    evidence_ref    VARCHAR(512),
    source          VARCHAR(64),
    computed_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_facility_trust_dimension CHECK (dimension IN (
        'EXISTENCE', 'GEOSPATIAL', 'AUTHORITY', 'LEGAL_IDENTITY', 'REGULATORY',
        'CAPABILITY', 'OPERATIONAL', 'COMPLIANCE', 'PUBLIC_DISCLOSURE')),
    CONSTRAINT chk_facility_trust_status CHECK (status IN (
        'CONFIRMED', 'PARTIAL', 'UNKNOWN', 'FAILED'))
);

COMMENT ON TABLE tuso.facility_trust_dimension IS
    'Per-dimension facility trust (Place Journey Doctrine §4, D-L8). Materialised from underlying signals; facility_source_legitimacy stays the access gate.';

CREATE UNIQUE INDEX IF NOT EXISTS ux_facility_trust_dimension
    ON tuso.facility_trust_dimension (tenant_id, facility_uuid, dimension);
