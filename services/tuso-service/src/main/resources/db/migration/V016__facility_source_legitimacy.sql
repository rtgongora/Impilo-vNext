-- Honest per-source facility legitimacy model (IATG Wave 1, WS-E).
--
-- A facility's legitimacy is NOT one boolean: each authority knows the facility in its own
-- capacity and each verdict is recorded separately, per source, without collapsing them:
--   HPA_LEGAL              — Health Professions Authority legal registration verdict
--   MINISTRY_OPERATIONAL   — MoHCC operational recognition (national facility register)
--   PLATFORM_OPERATIONAL   — Impilo platform verification verdict
--
-- One current row per (facility, source); updates overwrite the current row and the previous
-- values are carried on the tuso.event_outbox event (tuso.facility.source_legitimacy.upserted)
-- for traceability. GOVERNMENT_OPERATIONAL_EXCEPTION requires an explicit reason and an
-- explicit allowed_on_platform decision (enforced in FacilitySourceLegitimacyService).
--
-- facility_id is the CANONICAL facility UUID (tuso.facility.facility_uuid, V013) — the
-- service-neutral identity downstream services key off — not the numeric surrogate PK.

CREATE TABLE IF NOT EXISTS tuso.facility_source_legitimacy (
    id                    BIGSERIAL PRIMARY KEY,
    facility_id           UUID NOT NULL,
    source                VARCHAR(48) NOT NULL,
    status                VARCHAR(64) NOT NULL,
    as_of                 TIMESTAMPTZ NOT NULL,
    evidence_ref          VARCHAR(512),
    allowed_on_platform   BOOLEAN NOT NULL DEFAULT FALSE,
    reason                TEXT,
    recorded_by           VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_facility_source_legitimacy UNIQUE (facility_id, source)
);

CREATE INDEX IF NOT EXISTS idx_facility_source_legitimacy_facility
    ON tuso.facility_source_legitimacy (facility_id);

COMMENT ON TABLE tuso.facility_source_legitimacy IS
    'Per-source facility legitimacy verdicts (HPA_LEGAL / MINISTRY_OPERATIONAL / PLATFORM_OPERATIONAL); one current row per (facility, source). facility_id = tuso.facility.facility_uuid (canonical UUID).';
COMMENT ON COLUMN tuso.facility_source_legitimacy.facility_id IS
    'Canonical facility UUID (tuso.facility.facility_uuid), not the numeric surrogate id.';
COMMENT ON COLUMN tuso.facility_source_legitimacy.allowed_on_platform IS
    'Whether this source''s verdict permits the facility to operate on the platform. Composite access requires no denying row and at least one allowing row.';
