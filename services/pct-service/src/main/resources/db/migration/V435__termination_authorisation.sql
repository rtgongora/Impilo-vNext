-- Lawful termination of pregnancy: the authorisation, and the procedure that an authorisation makes
-- possible — with "unauthorised termination" made STRUCTURALLY impossible, not merely service-checked.
--
-- Two PO-ruled properties shape this:
--
-- 1. AN UNAUTHORISED PROCEDURE CANNOT EXIST. Not "the service rejects it" — the database cannot hold
--    it. A procedure carries a composite foreign key to (authorisation_id, status) on the
--    authorisation table, and its own status column is fixed to 'AUTHORISED' by a CHECK. So a
--    procedure row can only reference an authorisation that IS authorised, and — the second half —
--    the authorisation cannot be moved out of AUTHORISED while a procedure references it, because the
--    FK target would vanish. Authorisation (the state's decision on the grounds) and consent (hers)
--    are recorded separately: one is lawfulness, the other is autonomy, and conflating them is how
--    coercion hides.
--
-- 2. AGGREGATE-ONLY REPORTING, NO RECORD-LEVEL EMIT, ENFORCED. TOP is counted into facility/period
--    totals and NEVER emitted as a record-level event — no identifier, and no linkable pseudonym,
--    because in a population this size a pseudonym is re-identifiable and the legal exposure falls on
--    the patient. This table therefore has NO event_type/outbox column and no trigger; the intended
--    confidential category is recorded but nothing consumes it yet. A guard
--    (check-top-no-record-level-emit.sh) fails the build if a TOP entity ever appears on an
--    outbox/event path, so a future lane cannot "complete" a wiring that must never exist.
--
-- Conscientious objection is recorded as a REFERRAL (an authorisation status), never as an absence —
-- a declined request that simply vanishes is a woman turned away with no record that she asked.
--
-- RMNP band; V059-anchored (lower number, applies first on fresh boot).

CREATE TABLE IF NOT EXISTS pct.pct_top_authorisations (
    authorisation_id        UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    subject_cpid            VARCHAR(64) NOT NULL,
    pregnancy_episode_id    UUID REFERENCES pct.pct_pregnancy_episodes (pregnancy_episode_id),

    status                  VARCHAR(24) NOT NULL,
    legal_ground            VARCHAR(48) NOT NULL,
    legal_ground_detail     TEXT,
    gestational_limit_weeks NUMERIC(4,1),
    gestation_weeks_at_request NUMERIC(4,1),

    certifying_authority    VARCHAR(128),
    certified_on            DATE,

    -- Consent is hers, recorded apart from the authorisation, which is the state's.
    consent_given           VARCHAR(24) NOT NULL DEFAULT 'NOT_RECORDED',
    consent_recorded_on     DATE,

    -- Conscientious objection is a referral, never a silent decline.
    objection_referral_ref  VARCHAR(128),

    confidentiality_category VARCHAR(48) NOT NULL DEFAULT 'FULL_CLINICAL',

    recorded_by             VARCHAR(128) NOT NULL,
    recorded_at             TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_pct_top_auth_status CHECK (
        status IN ('REQUESTED', 'AUTHORISED', 'DECLINED', 'REFERRED_OBJECTION', 'EXPIRED')),

    CONSTRAINT chk_pct_top_auth_consent CHECK (
        consent_given IN ('GIVEN', 'DECLINED', 'NOT_RECORDED')),

    -- An authorised termination must record her consent as given: lawfulness without consent is not
    -- authorisation, it is coercion.
    CONSTRAINT chk_pct_top_auth_authorised_needs_consent CHECK (
        status <> 'AUTHORISED' OR consent_given = 'GIVEN'),

    -- Conscientious objection must carry the onward referral, or it is a woman turned away unrecorded.
    CONSTRAINT chk_pct_top_auth_objection_referral CHECK (
        status <> 'REFERRED_OBJECTION' OR objection_referral_ref IS NOT NULL),

    -- The target of the procedure's composite FK: exactly one row per (authorisation_id, status).
    CONSTRAINT uq_pct_top_auth_id_status UNIQUE (authorisation_id, status)
);

CREATE TABLE IF NOT EXISTS pct.pct_top_procedures (
    procedure_id            UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    subject_cpid            VARCHAR(64) NOT NULL,

    authorisation_id        UUID NOT NULL,
    -- Fixed to AUTHORISED, and FK'd to the authorisation's (id, status). Together these mean the
    -- referenced authorisation must be AUTHORISED and cannot leave that state while this row exists.
    authorisation_status    VARCHAR(24) NOT NULL DEFAULT 'AUTHORISED',

    method                  VARCHAR(32) NOT NULL,
    gestation_weeks         NUMERIC(4,1),
    performed_on            DATE NOT NULL,
    performed_by            VARCHAR(128),
    complications           TEXT,
    followup_contraception  VARCHAR(64),
    followup_plan           TEXT,

    confidentiality_category VARCHAR(48) NOT NULL DEFAULT 'FULL_CLINICAL',

    recorded_by             VARCHAR(128) NOT NULL,
    recorded_at             TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_pct_top_proc_status_authorised CHECK (authorisation_status = 'AUTHORISED'),

    CONSTRAINT chk_pct_top_proc_method CHECK (
        method IN ('MEDICAL', 'SURGICAL_MVA', 'SURGICAL_D_AND_E', 'SURGICAL_OTHER')),

    -- The structural guarantee: a procedure can only reference an AUTHORISED authorisation, and that
    -- authorisation cannot change status while referenced.
    CONSTRAINT fk_pct_top_proc_authorisation FOREIGN KEY (authorisation_id, authorisation_status)
        REFERENCES pct.pct_top_authorisations (authorisation_id, status)
);

CREATE INDEX IF NOT EXISTS ix_pct_top_auth_subject
    ON pct.pct_top_authorisations (tenant_id, subject_cpid);
CREATE INDEX IF NOT EXISTS ix_pct_top_proc_authorisation
    ON pct.pct_top_procedures (authorisation_id);

COMMENT ON TABLE pct.pct_top_authorisations IS
    'Lawful-termination authorisation. Consent (hers) recorded apart from authorisation (the state''s). Conscientious objection is a referral, never a silent decline.';
COMMENT ON TABLE pct.pct_top_procedures IS
    'Termination procedure. Composite FK to (authorisation_id, AUTHORISED) makes an unauthorised procedure structurally impossible. Aggregate-only reporting: NO event/outbox path exists, by design and by guard.';
