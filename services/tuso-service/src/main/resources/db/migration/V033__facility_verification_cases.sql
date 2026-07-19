-- =============================================================================
-- Tuso V033 — Proof of place: facility verification cases (FJ3/FJ4, D-L6; W6)
--
-- The place equivalent of person proofing: guided geotagged photos, documents,
-- live GPS, remote video walkthrough, in-person inspection. Evidence lives in
-- document-service (refs only — no binary here); for video the DECISION is
-- retained, never the recording (rtc session ref only). The decision feeds the
-- EXISTENCE / GEOSPATIAL trust dimensions (V031) — verification updates
-- dimensions separately, never one global flag. Static uploaded photographs
-- alone are weak evidence and never auto-verify anything: every case is
-- decided by an authorised verifier.
-- =============================================================================

CREATE TABLE IF NOT EXISTS tuso.facility_verification_case (
    id                    BIGSERIAL     PRIMARY KEY,
    tenant_id             UUID          NOT NULL,
    case_ref              VARCHAR(40)   NOT NULL UNIQUE,
    facility_uuid         UUID          NOT NULL,
    -- GUIDED_PHOTO | VIDEO_WALKTHROUGH | IN_PERSON | DOCUMENT
    verification_type     VARCHAR(32)   NOT NULL,
    requested_by          VARCHAR(128)  NOT NULL,
    -- document-service object refs ONLY (evidence + OCR live there)
    evidence_refs         JSONB,
    claimed_latitude      NUMERIC(10,7),
    claimed_longitude     NUMERIC(10,7),
    -- haversine distance between the claimed live position and the registry
    -- coordinates, computed at submission (GPS plausibility signal)
    gps_distance_meters   NUMERIC(12,2),
    -- FJ4: the rtc-gateway session reference; the recording is NOT retained
    rtc_session_ref       VARCHAR(255),
    -- OPEN | VERIFIED | INCONCLUSIVE | MORE_EVIDENCE | FAILED
    status                VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    decided_by            VARCHAR(255),
    decided_at            TIMESTAMPTZ,
    decision_note         TEXT,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_verification_type CHECK (verification_type IN
        ('GUIDED_PHOTO', 'VIDEO_WALKTHROUGH', 'IN_PERSON', 'DOCUMENT')),
    CONSTRAINT chk_verification_status CHECK (status IN
        ('OPEN', 'VERIFIED', 'INCONCLUSIVE', 'MORE_EVIDENCE', 'FAILED'))
);

COMMENT ON TABLE tuso.facility_verification_case IS
    'Proof of place (FJ3/FJ4, D-L6): evidence refs + GPS plausibility + video DECISION records (no recordings). Verifier-decided; feeds EXISTENCE/GEOSPATIAL trust dimensions.';

CREATE INDEX IF NOT EXISTS idx_facility_verification_facility
    ON tuso.facility_verification_case (tenant_id, facility_uuid);
CREATE INDEX IF NOT EXISTS idx_facility_verification_status
    ON tuso.facility_verification_case (tenant_id, status);
