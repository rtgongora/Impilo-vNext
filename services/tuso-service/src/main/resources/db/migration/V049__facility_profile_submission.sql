-- FCV-W5 — a claimant may prepare their facility's profile privately, and it becomes truth only
-- when a steward accepts it.
--
-- Today FacilityProfileCompletionService writes the authoritative facility record directly, so
-- there is no way for a pending claimant to prepare anything and no way for a steward to review a
-- proposed change before it lands. The brief asks for a private draft that does not alter the
-- authoritative record, a submission that can be returned for correction, and field-level
-- provenance on what the facility itself asserted.
--
-- No separate draft table. `facility_field_assertion` (V036) is already per-field with provenance,
-- supersession and a source vocabulary that literally contains FACILITY_CLAIM — a value with no
-- writer since the day it was created. A draft IS a set of unpromoted assertions, so this migration
-- gives assertions a submission and a promotion state instead of duplicating the whole model. That
-- also makes the dead vocabulary value live by construction rather than by a merge step somebody
-- has to remember to write.

CREATE TABLE IF NOT EXISTS tuso.facility_profile_submission (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    facility_uuid       UUID NOT NULL,

    -- who is preparing it, and under what claim
    submitted_by        VARCHAR(64) NOT NULL,
    appointment_id      BIGINT REFERENCES tuso.facility_admin_appointment (id),

    -- §6 completion lifecycle. NOT_STARTED is the absence of a row, not a value.
    state               VARCHAR(32) NOT NULL DEFAULT 'DRAFT',

    submitted_at        TIMESTAMPTZ,
    reviewed_by         VARCHAR(64),
    reviewed_at         TIMESTAMPTZ,
    -- What the reviewer told the claimant to fix. Distinct from any internal assessment: this text
    -- is written to be read by the facility.
    correction_note     TEXT,
    decision_note       TEXT,
    superseded_by       UUID REFERENCES tuso.facility_profile_submission (id),

    correlation_id      VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_facility_profile_submission_state CHECK (state IN (
        'DRAFT', 'IN_PROGRESS', 'READY_FOR_SUBMISSION',
        'SUBMITTED', 'UNDER_REVIEW', 'CORRECTION_REQUIRED', 'RESUBMITTED',
        'VERIFIED', 'CONDITIONALLY_VERIFIED', 'REJECTED', 'SUPERSEDED')),

    -- A correction request that says nothing is not a correction request.
    CONSTRAINT chk_facility_profile_correction_note CHECK (
        state <> 'CORRECTION_REQUIRED'
        OR (correction_note IS NOT NULL AND btrim(correction_note) <> ''))
);

-- One live submission per claimant per facility; the history stays via superseded_by.
CREATE UNIQUE INDEX IF NOT EXISTS uq_facility_profile_submission_open
    ON tuso.facility_profile_submission (facility_uuid, submitted_by)
    WHERE superseded_by IS NULL AND state NOT IN ('VERIFIED', 'CONDITIONALLY_VERIFIED', 'REJECTED', 'SUPERSEDED');

CREATE INDEX IF NOT EXISTS idx_facility_profile_submission_facility
    ON tuso.facility_profile_submission (facility_uuid, created_at DESC);

-- The steward queue: what is waiting on a human.
CREATE INDEX IF NOT EXISTS idx_facility_profile_submission_queue
    ON tuso.facility_profile_submission (state, submitted_at)
    WHERE state IN ('SUBMITTED', 'RESUBMITTED', 'UNDER_REVIEW');

-- ── assertions become draftable ───────────────────────────────────────────────────────────────
-- An assertion belonging to a submission is a PROPOSAL. It is private to the claimant and the
-- steward, it is never `is_current`, and it does not describe the facility until it is promoted.
ALTER TABLE tuso.facility_field_assertion
    ADD COLUMN IF NOT EXISTS submission_id UUID REFERENCES tuso.facility_profile_submission (id);

ALTER TABLE tuso.facility_field_assertion
    ADD COLUMN IF NOT EXISTS promoted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_facility_field_assertion_submission
    ON tuso.facility_field_assertion (submission_id) WHERE submission_id IS NOT NULL;

COMMENT ON COLUMN tuso.facility_field_assertion.submission_id IS
    'Set while this assertion is a PROPOSAL inside a facility profile submission (FCV-W5): private '
    'to the claimant and reviewer, never is_current, and not a description of the facility until '
    'promoted_at is set by an accepted submission.';
