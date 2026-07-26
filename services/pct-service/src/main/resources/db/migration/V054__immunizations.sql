-- Immunisation administered-dose registry.
--
-- Closes the second half of the broken clinical vertical (see V053): experience-bff
-- and the patient immunisations page have called pct-service /v1/immunizations since
-- the Wave 4 slice, and no such endpoint existed. Recorded vaccinations were lost.
--
-- Scope boundary, deliberately narrow: this table holds DOSE FACTS ONLY — what was
-- given, to whom, when, from which vial, by whom. It is not a schedule and it does not
-- decide what is due. The national schedule is governed content and the forecast is
-- evaluated against it elsewhere, so that a change of national policy is a content
-- release rather than a migration, and so historical dose facts are never rewritten
-- when the schedule changes.
--
-- Several statuses exist because "no dose recorded" has clinically distinct causes that
-- must not collapse into one another: a dose given elsewhere and awaiting documentary
-- verification is not the same as a refusal, which is not the same as a deferral for a
-- contraindication, which is not the same as never having been offered. Treating them
-- alike is how children get counted as covered when they are not, or re-vaccinated when
-- they should not be.

CREATE TABLE pct.pct_immunizations (
    immunization_id      UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    subject_cpid         VARCHAR(64) NOT NULL,
    journey_id           VARCHAR(64),
    encounter_id         VARCHAR(64),
    facility_id          UUID,

    -- What was given.
    vaccine_code         VARCHAR(64),                 -- governed antigen/product code when known
    code_system          VARCHAR(128),
    vaccine_name         VARCHAR(255) NOT NULL,       -- display name; the only always-present identifier
    dose_number          INTEGER,
    series               VARCHAR(64),                 -- e.g. OPV, PENTA; free until the code system lands
    occurrence_at        TIMESTAMPTZ NOT NULL,

    -- Vial traceability: required for a recall or an adverse-event investigation.
    lot_number           VARCHAR(64),
    expiry_date          DATE,
    diluent_lot_number   VARCHAR(64),

    -- How and where it was given.
    dose_quantity        VARCHAR(32),
    route                VARCHAR(32),
    body_site            VARCHAR(64),
    administered_by      VARCHAR(128),
    delivery_context     VARCHAR(24) NOT NULL DEFAULT 'ROUTINE',  -- ROUTINE | CAMPAIGN | OUTREACH | SCHOOL
    campaign_ref         VARCHAR(128),
    outreach_location    VARCHAR(255),

    -- Why a dose is or is not on the record.
    status               VARCHAR(48) NOT NULL DEFAULT 'COMPLETED',
    -- COMPLETED | NOT_DONE_REFUSED | NOT_DONE_CONTRAINDICATED | NOT_DONE_DEFERRED
    -- | RECORDED_ELSEWHERE_PENDING_VERIFICATION | ENTERED_IN_ERROR
    status_reason        TEXT,
    deferred_until       DATE,
    source_reference     VARCHAR(255),                -- card, register or facility a reported dose came from
    verified_by          VARCHAR(128),
    verified_at          TIMESTAMPTZ,

    aefi_case_reference  VARCHAR(128),                -- links to the patient-safety AEFI investigation

    notes                TEXT,
    recorded_by          VARCHAR(128) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_immunizations_subject
    ON pct.pct_immunizations(tenant_id, subject_cpid, occurrence_at DESC);
CREATE INDEX idx_pct_immunizations_given
    ON pct.pct_immunizations(tenant_id, subject_cpid, vaccine_code)
    WHERE status = 'COMPLETED';
CREATE INDEX idx_pct_immunizations_pending_verification
    ON pct.pct_immunizations(tenant_id, subject_cpid)
    WHERE status = 'RECORDED_ELSEWHERE_PENDING_VERIFICATION';
