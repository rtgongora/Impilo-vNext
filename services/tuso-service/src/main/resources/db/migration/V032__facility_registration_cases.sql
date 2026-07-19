-- =============================================================================
-- Tuso V032 — Facility registration cases + silent duplicate block (FJ2, D-L5; W6)
--
-- "Search before create, but keep matching private." A self-service facility
-- registration ALWAYS lands as a case with ONE generic outcome shape for the
-- registrant. Internally the live matcher (facility code / governed aliases /
-- trigram name similarity) decides: a credible match silently BLOCKS creation
-- and routes the case to steward review with the match context; no credible
-- match creates a PROVISIONAL facility (never publicly discoverable, never
-- operational) pending verification and regulatory review. The registrant can
-- never tell which path they hit — candidates are steward material only.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS tuso.facility_registration_case (
    id                          BIGSERIAL       PRIMARY KEY,
    tenant_id                   UUID            NOT NULL,
    case_ref                    VARCHAR(40)     NOT NULL UNIQUE,
    applicant_health_id         VARCHAR(128)    NOT NULL,
    submitted_name              VARCHAR(255)    NOT NULL,
    submitted_payload           JSONB           NOT NULL,
    evidence_ref                VARCHAR(512)    NOT NULL,
    -- DUPLICATE_REVIEW (silent block) | PROVISIONAL_CREATED | MATCHED_EXISTING | REJECTED | CONFIRMED
    outcome                     VARCHAR(32)     NOT NULL,
    matched_facility_uuid       UUID,
    -- Steward-only: candidate uuids + match types + scores. NEVER serialised to registrants.
    match_context               JSONB,
    provisional_facility_uuid   UUID,
    reviewed_by                 VARCHAR(255),
    reviewed_at                 TIMESTAMPTZ,
    review_note                 TEXT,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE tuso.facility_registration_case IS
    'FJ2 registration cases (D-L5): every self-service registration lands here; credible matches silently block creation (steward review); match_context is steward-only.';

CREATE INDEX IF NOT EXISTS idx_facility_registration_case_outcome
    ON tuso.facility_registration_case (tenant_id, outcome);
CREATE INDEX IF NOT EXISTS idx_facility_registration_case_applicant
    ON tuso.facility_registration_case (tenant_id, applicant_health_id);
