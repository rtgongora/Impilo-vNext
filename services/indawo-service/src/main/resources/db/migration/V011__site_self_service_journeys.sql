-- =============================================================================
-- Indawo V011 — Site self-service journeys: operator grants + registration
-- cases (SJ1/SJ2, D-L4/D-L5; Provider+Place W6)
--
-- The Indawo mirror of the hardened Tuso rails. Two distinct concepts:
--   * ind_site_operators (existing) = the operator ORGANISATION facts;
--   * ind_site_operator_grant (NEW)  = a PERSON's role-scoped, expiring,
--     evidence-backed management grant on a site (SJ1) — mirror of
--     tuso.facility_admin_appointment. Operators manage approved parts of the
--     record; they can never edit inspection findings or regulator decisions.
--   * ind_site_registration_case (NEW) = SJ2 self-service registration with
--     silent duplicate matching: ONE generic registrant receipt; credible
--     matches silently block creation into steward review; otherwise a DRAFT
--     (non-discoverable, non-operational) provisional site is created.
-- =============================================================================

CREATE TABLE IF NOT EXISTS ind_site_operator_grant (
    id                  BIGSERIAL       PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    site_id             UUID            NOT NULL,
    person_health_id    VARCHAR(128)    NOT NULL,
    role                VARCHAR(32)     NOT NULL,
    approval_state      VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    claim_type          VARCHAR(16)     NOT NULL DEFAULT 'NEW',
    evidence_ref        VARCHAR(512)    NOT NULL,
    valid_from          DATE,
    valid_to            DATE,
    appointed_by        VARCHAR(255),
    notes               TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_site_grant_role CHECK (role IN (
        'SITE_VIEWER', 'SITE_OPERATOR', 'SITE_ADMINISTRATOR', 'REGULATORY_LIAISON')),
    CONSTRAINT chk_site_grant_state CHECK (approval_state IN (
        'PENDING', 'ACTIVE', 'REJECTED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT chk_site_grant_claim_type CHECK (claim_type IN ('NEW', 'RECOVERY'))
);

COMMENT ON TABLE ind_site_operator_grant IS
    'SJ1 (D-L4): person-level role-scoped site management grants — append-only, evidence-backed, expiring. Distinct from ind_site_operators (the operator organisation).';

CREATE UNIQUE INDEX IF NOT EXISTS ux_site_grant_active_role
    ON ind_site_operator_grant (tenant_id, site_id, person_health_id, role)
    WHERE approval_state = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_site_grant_site ON ind_site_operator_grant (tenant_id, site_id);
CREATE INDEX IF NOT EXISTS idx_site_grant_expiry ON ind_site_operator_grant (valid_to)
    WHERE approval_state = 'ACTIVE' AND valid_to IS NOT NULL;

CREATE TABLE IF NOT EXISTS ind_site_registration_case (
    id                          BIGSERIAL       PRIMARY KEY,
    tenant_id                   UUID            NOT NULL,
    case_ref                    VARCHAR(40)     NOT NULL UNIQUE,
    applicant_health_id         VARCHAR(128)    NOT NULL,
    submitted_name              VARCHAR(255)    NOT NULL,
    site_category               VARCHAR(64),
    submitted_payload           JSONB           NOT NULL,
    evidence_ref                VARCHAR(512)    NOT NULL,
    outcome                     VARCHAR(32)     NOT NULL,
    matched_site_id             UUID,
    match_context               JSONB,
    provisional_site_id         UUID,
    reviewed_by                 VARCHAR(255),
    reviewed_at                 TIMESTAMPTZ,
    review_note                 TEXT,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE ind_site_registration_case IS
    'SJ2 (D-L5): site registration cases; credible matches silently block creation (steward-only match_context); no site becomes operational from a web form.';

CREATE INDEX IF NOT EXISTS idx_site_registration_outcome
    ON ind_site_registration_case (tenant_id, outcome);
