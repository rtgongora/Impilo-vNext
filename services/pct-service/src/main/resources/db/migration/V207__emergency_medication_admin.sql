-- Emergency medication administration — ED / non-admitted (W8b).
--
-- The second of two deliberately separate medication tables the plan calls for. inpatient's
-- resuscitation_medication (V200, W6a) is the in-resus local write — synchronous, CPR-time-critical,
-- no cross-service call acceptable mid-code. This table is the OTHER case: a patient in the ED who
-- is NOT in an active resuscitation, where there is time for the full authorisation trail a code
-- cannot afford to wait for. Two tables, not one, is the deliberate design (see V200's own header).
--
-- PROVEN NOT A DUPLICATE BEFORE WRITING THIS: grepped pct for any existing MAR/medication-admin
-- concept (none) and read OrosMedicationIntegration.currentMedicationCodes — a READ-ONLY reference
-- lookup for CDS drug-interaction checking, not a write path. Genuinely absent.
--
-- Anchored on emergency_episode (not ed_visit) — the same anchor as every other emergency write
-- table in this pack (V203 alerts, V204 handover, V206 order sets) — with visit_id optional for
-- when a real ed_visit exists, matching V206's own dual-anchor shape exactly.
--
-- THE TWO-PERSON GUARD, deferred here from W6a on purpose: "a full two-person-witness authorisation
-- workflow is emergency-medicines-wave scope, not the in-resus local write." AUTHORISER != WITNESS
-- is enforced as a schema property for high-risk drugs: the person who authorised/prescribed a
-- high-risk medication cannot also be its sole witness, because that collapses two independent
-- checks into one person's judgement — exactly what witnessing exists to prevent. The person who
-- physically administered it is also barred from being its own witness, for the same reason.

CREATE TABLE emergency_medication_admin (
    admin_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID        NOT NULL,
    episode_id           UUID        NOT NULL REFERENCES emergency_episode (episode_id),
    visit_id             UUID,
    trauma_episode_id    UUID,

    drug_name            VARCHAR(128) NOT NULL,
    drug_code            VARCHAR(64),
    dose_value           NUMERIC(10,3) NOT NULL,
    dose_unit            VARCHAR(32) NOT NULL,
    route                VARCHAR(32) NOT NULL,

    -- Flags whether this administration required the two-person guard. A drug-level policy (which
    -- drugs/categories are high-risk) is content, not schema — this column records the FACT that the
    -- guard applied to this row, whatever set it.
    high_risk            BOOLEAN     NOT NULL DEFAULT FALSE,

    administered_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    administered_by      VARCHAR(128) NOT NULL,

    -- Who authorised/prescribed it. Required for a high-risk administration; the CHECK below is what
    -- makes authoriser != witness a schema property, not a convention a form could skip.
    authorised_by        VARCHAR(128),
    witnessed_by         VARCHAR(128),

    -- Idempotent retry: any device mints its own id and may retry blind, same as resuscitation_
    -- medication (V200, inpatient). A double-POST must not become a second real dose.
    client_event_id      VARCHAR(64),

    -- Correct, never delete — same discipline as every write-once clinical record in this pack.
    superseded_by        UUID REFERENCES emergency_medication_admin (admin_id),
    correction_reason    TEXT,

    notes                TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_ema_dose_positive CHECK (dose_value > 0),
    CONSTRAINT chk_ema_correction_pair CHECK (
        superseded_by IS NULL OR (correction_reason IS NOT NULL AND btrim(correction_reason) <> '')),

    -- THE TWO-PERSON GUARD. A high-risk administration requires both an authoriser and a witness,
    -- and neither may be the same person as the other or as whoever administered it.
    CONSTRAINT chk_ema_high_risk_requires_authoriser_and_witness CHECK (
        NOT high_risk OR (authorised_by IS NOT NULL AND witnessed_by IS NOT NULL)),
    CONSTRAINT chk_ema_authoriser_not_witness CHECK (
        NOT high_risk OR authorised_by IS DISTINCT FROM witnessed_by),
    CONSTRAINT chk_ema_administered_not_sole_witness CHECK (
        NOT high_risk OR administered_by IS DISTINCT FROM witnessed_by)
);

CREATE UNIQUE INDEX uq_emergency_medication_admin_client_event
    ON emergency_medication_admin (tenant_id, episode_id, client_event_id)
    WHERE client_event_id IS NOT NULL;

CREATE INDEX idx_emergency_medication_admin_episode
    ON emergency_medication_admin (tenant_id, episode_id, administered_at);
CREATE INDEX idx_emergency_medication_admin_visit
    ON emergency_medication_admin (visit_id) WHERE visit_id IS NOT NULL;

COMMENT ON TABLE emergency_medication_admin IS
    'ED / non-admitted medication administration. Distinct from inpatient.resuscitation_medication '
    '(the in-resus, CPR-time-critical local write) — this is for a patient who is NOT in an active '
    'resuscitation, where the full authoriser/witness trail is affordable.';
COMMENT ON COLUMN emergency_medication_admin.high_risk IS
    'Records that the two-person guard applied to this row. The drug-level policy for which '
    'medications count as high-risk is content, not schema.';
COMMENT ON CONSTRAINT chk_ema_authoriser_not_witness ON emergency_medication_admin IS
    'The plan''s explicit "authoriser != witness" requirement, deferred here from W6a''s '
    'resuscitation_medication on purpose (see this file''s header). A person cannot both prescribe a '
    'high-risk drug and be its sole independent check.';
