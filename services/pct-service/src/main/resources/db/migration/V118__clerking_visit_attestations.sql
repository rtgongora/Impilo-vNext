-- Wave 3 Phase E — visit-scoped "do not re-document" attestations.
-- Silence ≠ confirmation. STILL_TRUE writes an attestation; CHANGED writes a new fact elsewhere;
-- NOT_ASSESSED records that the clinician chose not to confirm this visit.

CREATE TABLE pct_clerking_visit_attestations (
    attestation_id   UUID          PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    subject_cpid     VARCHAR(128)  NOT NULL,
    journey_id       VARCHAR(128),
    encounter_id     VARCHAR(128),
    section_key      VARCHAR(64)   NOT NULL,
    stance           VARCHAR(32)   NOT NULL,
    prior_fact_ref   VARCHAR(255),
    notes            TEXT,
    recorded_by      VARCHAR(255)  NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pct_clerking_attestation_stance_check CHECK (
        stance IN ('STILL_TRUE', 'CHANGED', 'NOT_ASSESSED')),
    CONSTRAINT pct_clerking_attestation_anchor_check CHECK (
        journey_id IS NOT NULL OR encounter_id IS NOT NULL)
);

CREATE INDEX idx_pct_clerking_attestations_visit
    ON pct_clerking_visit_attestations (tenant_id, subject_cpid, encounter_id, section_key);

COMMENT ON COLUMN pct_clerking_visit_attestations.stance IS
    'Never defaulted to STILL_TRUE. Omitting a stance must not invent confirmation.';
