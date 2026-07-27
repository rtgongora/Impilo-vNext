-- Pregnancy loss: miscarriage and stillbirth, recorded on the MOTHER's episode.
--
-- PO RULING (2026-07-27), and the shape follows from it: a stillbirth is mother-anchored and mints
-- NO CPID. No pct_newborn_birth_records row, no person. UBOMI receives a civil-notification EVENT,
-- not a person. The reasoning, kept here because the schema encodes it: a CPID would create a person
-- the identity plane can never serve — no consent can be given or withheld, no care delivered, no
-- record history accrue — and a person-shaped record that can never behave like a person is a lie in
-- the identity model that would sit in every count of "people" the system knows. The clinical truth
-- of the loss lives on the mother's episode, which is where this record puts it.
--
-- So this table deliberately has NO baby identity column — not a nullable one, not a "provisional"
-- one, the same discipline as pct_fetuses (V061). It carries what bereavement care and civil
-- notification need — gestation, timing, outcome, certification — and nothing that only makes sense
-- for a person. If a future column only makes sense for a person, the shape is drifting.
--
-- RMNP band; anchors on pct_pregnancy_episodes (V059) and optionally a fetus (V061), both lower
-- numbers, so no cross-band forward dependency. TOP is a loss OUTCOME here but its lawful-
-- authorisation structure lives in its own table (V435), referenced for TOP cases.

CREATE TABLE IF NOT EXISTS pct.pct_pregnancy_loss_records (
    loss_record_id          UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    -- The subject is the mother. There is deliberately no column for the baby's identity.
    mother_cpid             VARCHAR(64) NOT NULL,
    pregnancy_episode_id    UUID
        REFERENCES pct.pct_pregnancy_episodes (pregnancy_episode_id),
    -- For a multiple pregnancy, which fetus was lost (the fetus row also carries no identity).
    fetus_id                UUID REFERENCES pct.pct_fetuses (fetus_id),

    loss_type               VARCHAR(32) NOT NULL,
    occurred_on             DATE NOT NULL,
    gestation_weeks_at_loss NUMERIC(4,1),
    gestation_basis         VARCHAR(32),

    cause_category          VARCHAR(48),
    cause_detail            TEXT,
    management              VARCHAR(32),

    -- Civil notification is an EVENT reference, never a person. A stillbirth certificate is issued
    -- against the mother's episode; this holds the notification/certificate reference, not a CPID.
    stillbirth_certifiable  BOOLEAN NOT NULL DEFAULT FALSE,
    certificate_issued      BOOLEAN NOT NULL DEFAULT FALSE,
    ubomi_notification_ref  VARCHAR(128),

    -- Bereavement care — offered regardless of how the pregnancy ended, and recorded because a loss
    -- with no bereavement follow-up recorded is care omitted, not care not needed.
    bereavement_support_offered BOOLEAN NOT NULL DEFAULT FALSE,
    postloss_contraception_discussed BOOLEAN NOT NULL DEFAULT FALSE,

    -- When the loss was a lawful termination, the authorisation lives in V435; this points to it.
    top_procedure_id        UUID,

    confidentiality_category VARCHAR(48) NOT NULL DEFAULT 'FULL_CLINICAL',

    journey_id              UUID,
    encounter_ref           VARCHAR(128),
    facility_id             UUID,
    recorded_by             VARCHAR(128) NOT NULL,
    recorded_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by              VARCHAR(128),
    updated_at              TIMESTAMPTZ,
    client_offline_id       VARCHAR(128),

    CONSTRAINT chk_pct_loss_type CHECK (
        loss_type IN ('MISCARRIAGE_EARLY', 'MISCARRIAGE_LATE', 'STILLBIRTH_ANTEPARTUM',
                      'STILLBIRTH_INTRAPARTUM', 'TERMINATION', 'ECTOPIC', 'MOLAR')),

    CONSTRAINT chk_pct_loss_management CHECK (
        management IS NULL
        OR management IN ('EXPECTANT', 'MEDICAL', 'SURGICAL', 'NOT_APPLICABLE')),

    -- A stillbirth is defined by gestation; recording one without the gestation it was classified on
    -- leaves the classification ungrounded and the certificate unsupportable.
    CONSTRAINT chk_pct_loss_stillbirth_has_gestation CHECK (
        loss_type NOT IN ('STILLBIRTH_ANTEPARTUM', 'STILLBIRTH_INTRAPARTUM')
        OR gestation_weeks_at_loss IS NOT NULL),

    -- A certificate cannot be issued for a loss that is not certifiable, and a certifiable stillbirth
    -- that was certified must carry its notification reference — the event UBOMI received. This is
    -- the civil-notification-as-event rule made structural.
    CONSTRAINT chk_pct_loss_certificate_certifiable CHECK (
        certificate_issued IS NOT TRUE OR stillbirth_certifiable IS TRUE),
    CONSTRAINT chk_pct_loss_certificate_has_notification CHECK (
        certificate_issued IS NOT TRUE OR ubomi_notification_ref IS NOT NULL),

    -- A termination row must reference its lawful authorisation/procedure (V435). No termination is
    -- recorded here as a bare loss with no authorisation behind it.
    CONSTRAINT chk_pct_loss_termination_has_procedure CHECK (
        loss_type <> 'TERMINATION' OR top_procedure_id IS NOT NULL),

    CONSTRAINT chk_pct_loss_gestation_range CHECK (
        gestation_weeks_at_loss IS NULL
        OR (gestation_weeks_at_loss >= 0 AND gestation_weeks_at_loss <= 45))
);

-- One loss record per fetus per episode (a multiple pregnancy can lose more than one, each its own
-- row keyed on the fetus); one per episode where no fetus is named.
CREATE UNIQUE INDEX IF NOT EXISTS uq_pct_loss_episode_fetus
    ON pct.pct_pregnancy_loss_records (tenant_id, pregnancy_episode_id, fetus_id)
    WHERE pregnancy_episode_id IS NOT NULL AND fetus_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_pct_loss_offline
    ON pct.pct_pregnancy_loss_records (tenant_id, client_offline_id)
    WHERE client_offline_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_pct_loss_mother
    ON pct.pct_pregnancy_loss_records (tenant_id, mother_cpid, occurred_on);

COMMENT ON TABLE pct.pct_pregnancy_loss_records IS
    'Pregnancy loss on the mother''s episode. No baby CPID by design (PO ruling): a stillbirth mints no person; civil notification is an event reference, not a person.';
