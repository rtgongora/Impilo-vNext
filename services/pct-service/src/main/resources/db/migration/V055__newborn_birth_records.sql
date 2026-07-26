-- Newborn clinical birth record: the child's own anchor in the clinical record.
--
-- Until now a delivery existed only as an intra-operative event on the MOTHER's theatre
-- episode, and UBOMI held the civil birth notification. Neither is a clinical record the
-- baby owns. That gap has a concrete consequence documented in
-- docs/security/clinical-write-subject-relationship-control.md: an APGAR score recorded
-- against the mother's encounter resolves to the mother's CPID and is refused by the
-- care-relationship guard, because the neonate has no encounter of their own.
--
-- This table gives every newborn a birth summary keyed to their own CPID, and the
-- accompanying service opens a journey and encounter for the baby so that subsequent
-- clinical writes — APGAR, examination, weight, feeding — resolve to the child.
--
-- Ownership boundaries are unchanged and deliberate:
--   * VITO still owns identity and the mother-baby relationship. Nothing here mints a CPID.
--   * UBOMI still owns the civil birth notification. This is a clinical record, not a
--     legal registration, and it never auto-submits one: attesting a birth to the state
--     is a human act.
--   * inpatient-service still owns the theatre episode, the delivery event and APGAR.
-- What PCT adds is the child-anchored clinical view that none of them holds.
--
-- The unique constraint on (tenant_id, subject_cpid) makes creation idempotent: a
-- delivery event replayed by Kafka, or a delivery recorded from both theatre and a
-- maternity form, must not produce two birth records for one baby. Twins have distinct
-- CPIDs and so get distinct records.

CREATE TABLE pct.pct_newborn_birth_records (
    birth_record_id        UUID PRIMARY KEY,
    tenant_id              UUID NOT NULL,
    subject_cpid           VARCHAR(64) NOT NULL,     -- the baby
    mother_cpid            VARCHAR(64),              -- linkage only; VITO owns the relationship
    facility_id            UUID,

    born_at                TIMESTAMPTZ,
    sex_at_birth           VARCHAR(16),
    gestational_age_weeks  INTEGER,
    gestational_age_basis  VARCHAR(32),   -- LMP | ULTRASOUND | EXAMINATION | UNKNOWN
    birth_weight_grams     INTEGER,
    birth_length_cm        NUMERIC(5,1),
    head_circumference_cm  NUMERIC(4,1),
    birth_order            INTEGER,
    multiple_birth         BOOLEAN NOT NULL DEFAULT FALSE,

    delivery_mode          VARCHAR(32),   -- SVD | ASSISTED | CAESAREAN | BREECH | OTHER
    place_of_birth         VARCHAR(32),   -- FACILITY | HOME | IN_TRANSIT | OTHER
    birth_attendant        VARCHAR(128),
    birth_outcome          VARCHAR(32) NOT NULL DEFAULT 'LIVE_BIRTH', -- LIVE_BIRTH | STILLBIRTH | NEONATAL_DEATH

    apgar_one_minute       INTEGER,
    apgar_five_minute      INTEGER,
    apgar_ten_minute       INTEGER,
    resuscitation          VARCHAR(64),   -- NONE | STIMULATION | BAG_MASK | INTUBATION | CHEST_COMPRESSIONS
    resuscitation_response VARCHAR(64),

    -- Essential newborn care actually delivered, as opposed to assumed.
    skin_to_skin           VARCHAR(16) NOT NULL DEFAULT 'NOT_ASSESSED',  -- DONE | NOT_DONE | NOT_ASSESSED
    minutes_to_first_breastfeed INTEGER,
    vitamin_k_given        VARCHAR(16) NOT NULL DEFAULT 'NOT_ASSESSED',
    eye_prophylaxis_given  VARCHAR(16) NOT NULL DEFAULT 'NOT_ASSESSED',
    cord_care              VARCHAR(64),
    temperature_celsius    NUMERIC(4,1),

    hiv_exposure           VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN', -- EXPOSED | NOT_EXPOSED | UNKNOWN
    maternal_risk_factors  JSONB,

    -- Structured findings, coded by the forms layer; free text is not the record here.
    newborn_examination    JSONB,
    congenital_anomalies   JSONB,
    birth_trauma           JSONB,
    danger_signs           JSONB,

    admission_destination  VARCHAR(32),   -- MOTHER | POSTNATAL | NEONATAL | NICU | SCBU | KANGAROO_CARE | REFERRED

    -- Provenance: which pathway created this record, and what it points back to.
    delivery_source        VARCHAR(24) NOT NULL DEFAULT 'MANUAL', -- THEATRE | MATERNITY_FORM | MANUAL
    delivery_source_ref    VARCHAR(128),
    ubomi_notification_ref VARCHAR(128),  -- set when the civil notification is later attested

    journey_id             VARCHAR(64),
    encounter_id           VARCHAR(64),

    recorded_by            VARCHAR(128) NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_pct_newborn_birth_subject UNIQUE (tenant_id, subject_cpid)
);

CREATE INDEX idx_pct_newborn_mother
    ON pct.pct_newborn_birth_records(tenant_id, mother_cpid)
    WHERE mother_cpid IS NOT NULL;
CREATE INDEX idx_pct_newborn_born_at
    ON pct.pct_newborn_birth_records(tenant_id, born_at DESC);
