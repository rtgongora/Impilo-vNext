-- =====================================================================================
-- tuso :: V039 — Pre-licensing practice establishment case (ROM-W6, R7)
-- A prospective practice owner can create a practice profile BEFORE any facility exists. The
-- canonical facility (tuso.facility) is created only on APPROVAL — closing the gap where the
-- FacilityApplicationType enum presupposed an existing facility_id. The enum is NOT extended;
-- this is a new case rail on the V018 spine (it reuses application_information_request,
-- inspection_visit and the V021 fee rail by application_id once an application is opened).
-- created_facility_uuid stays NULL until the establishment is approved (the core invariant).
-- =====================================================================================

CREATE TABLE IF NOT EXISTS tuso.practice_establishment_case (
    establishment_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID        NOT NULL,
    applicant_health_id     UUID        NOT NULL,
    proposed_name           VARCHAR(255) NOT NULL,
    category_code           VARCHAR(80),
    ownership               JSONB,
    directors               JSONB,
    nominated_pic_ref       VARCHAR(128),
    proposed_province       VARCHAR(80),
    proposed_district       VARCHAR(80),
    status                  VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_facility_uuid   UUID,
    application_id          UUID,
    decision_notes          TEXT,
    decided_by              VARCHAR(128),
    decided_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_establishment_status CHECK (status IN
        ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'RFI_OPEN', 'INSPECTION_PENDING',
         'APPROVED', 'REJECTED', 'WITHDRAWN')),
    -- The core invariant: a facility only exists once the case is APPROVED.
    CONSTRAINT chk_facility_only_on_approval CHECK (
        created_facility_uuid IS NULL OR status = 'APPROVED')
);
CREATE INDEX IF NOT EXISTS idx_establishment_applicant
    ON tuso.practice_establishment_case(tenant_id, applicant_health_id);
CREATE INDEX IF NOT EXISTS idx_establishment_status
    ON tuso.practice_establishment_case(tenant_id, status);
