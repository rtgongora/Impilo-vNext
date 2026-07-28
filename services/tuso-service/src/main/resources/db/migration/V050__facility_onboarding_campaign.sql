-- FCV-W6 — driving the existing estate through the journey, rather than waiting for it to arrive.
--
-- The 1,774 Ministry facilities will not claim themselves. A campaign is how MoHCC groups a slice of
-- the estate (a province, a district, a facility type), invites it, and watches progress — including
-- the facilities that never respond, which are the ones that actually need a site visit.
--
-- Membership carries NO status column. The brief lists thirteen campaign statuses, but every one of
-- them is derivable from three facts that already exist elsewhere: whether a claim was made and
-- approved, whether a profile submission moved, and whether a legitimacy decision was issued.
-- Storing them would mean thirteen values to keep in step with three rails, and they would drift the
-- first time anyone changed a state machine. The view derives them instead, so the campaign board
-- cannot lie about a facility whose claim was revoked yesterday.

CREATE TABLE IF NOT EXISTS tuso.facility_onboarding_campaign (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    name            VARCHAR(160) NOT NULL,
    description     TEXT,
    -- how the slice was chosen, kept for provenance rather than re-evaluated
    province        VARCHAR(100),
    district        VARCHAR(100),
    facility_type   VARCHAR(64),
    created_by      VARCHAR(64) NOT NULL,
    status          VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at       TIMESTAMPTZ,
    CONSTRAINT chk_facility_campaign_status CHECK (status IN ('ACTIVE', 'CLOSED'))
);

CREATE TABLE IF NOT EXISTS tuso.facility_campaign_member (
    id              BIGSERIAL PRIMARY KEY,
    campaign_id     UUID NOT NULL REFERENCES tuso.facility_onboarding_campaign (id),
    facility_uuid   UUID NOT NULL,
    invited_at      TIMESTAMPTZ,
    invited_by      VARCHAR(64),
    -- Set only when a human decides this facility needs help the platform cannot give: a site visit,
    -- a paper process, an escalation. Everything else is derived.
    assistance_note TEXT,
    escalated_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_facility_campaign_member UNIQUE (campaign_id, facility_uuid)
);

CREATE INDEX IF NOT EXISTS idx_facility_campaign_member_facility
    ON tuso.facility_campaign_member (facility_uuid);

-- The board. Progress is READ from the rails that own each fact, never mirrored into a status
-- column that can go stale.
CREATE OR REPLACE VIEW tuso.facility_campaign_progress AS
SELECT
    m.campaign_id,
    m.facility_uuid,
    f.facility_code,
    f.name AS facility_name,
    f.province,
    f.district,
    m.invited_at,
    m.escalated_at,
    CASE
        WHEN m.escalated_at IS NOT NULL                              THEN 'ESCALATED'
        WHEN d.verdict IS NOT NULL                                   THEN 'LEGITIMACY_ISSUED'
        WHEN s.state IN ('VERIFIED','CONDITIONALLY_VERIFIED')        THEN 'READY_FOR_VERIFICATION'
        WHEN s.state = 'CORRECTION_REQUIRED'                         THEN 'CORRECTION_REQUIRED'
        WHEN s.state IN ('SUBMITTED','RESUBMITTED','UNDER_REVIEW')   THEN 'PROFILE_SUBMITTED'
        WHEN s.state IS NOT NULL                                     THEN 'PROFILE_IN_PROGRESS'
        WHEN a.approval_state = 'ACTIVE'                             THEN 'CLAIM_APPROVED'
        WHEN a.approval_state = 'PENDING'                            THEN 'CLAIM_STARTED'
        WHEN m.invited_at IS NOT NULL                                THEN 'INVITED'
        ELSE 'NOT_CONTACTED'
    END AS derived_status
FROM tuso.facility_campaign_member m
JOIN tuso.facility f ON f.facility_uuid = m.facility_uuid
LEFT JOIN LATERAL (
    SELECT approval_state FROM tuso.facility_admin_appointment
    WHERE facility_uuid = m.facility_uuid
    ORDER BY CASE approval_state WHEN 'ACTIVE' THEN 0 WHEN 'PENDING' THEN 1 ELSE 2 END LIMIT 1
) a ON TRUE
LEFT JOIN LATERAL (
    SELECT state FROM tuso.facility_profile_submission
    WHERE facility_uuid = m.facility_uuid AND superseded_by IS NULL
    ORDER BY created_at DESC LIMIT 1
) s ON TRUE
LEFT JOIN LATERAL (
    SELECT verdict FROM tuso.facility_legitimacy_decision
    WHERE facility_uuid = m.facility_uuid AND superseded_by IS NULL LIMIT 1
) d ON TRUE;

COMMENT ON VIEW tuso.facility_campaign_progress IS
    'Campaign progress DERIVED from the claim, submission and decision rails (FCV-W6). Deliberately '
    'not a stored status: thirteen mirrored values would drift from three state machines.';
