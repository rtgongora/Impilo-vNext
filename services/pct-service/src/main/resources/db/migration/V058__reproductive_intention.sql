-- Reproductive intention.
--
-- The pregnancy episode (V059) answers "is she pregnant". This answers the question before it:
-- does she want to be. Family planning had no clinical record in this platform at all — only a
-- facility capability tag, a find-care synonym and a visit-type label.
--
-- The contraceptive episode and device tables are deliberately NOT here. They land in the
-- contraception wave alongside the eligibility engine and the service that writes them, because
-- schema that ships ahead of the code reading it is the exact defect this pack was created to
-- repair: four orphan tables in the experience BFF that nothing ever read.
--
-- WHY INTENTION IS A RECORD AND NOT A FIELD ON A FORM. Reproductive intention is the fact that
-- routes everything else: the same woman with the same medical history needs contraceptive
-- counselling, preconception care, or fertility investigation depending only on what she wants. It
-- changes over time, it is asked at many different contacts, and the previous answer is clinically
-- relevant to the next conversation. A current-value column on some other table would lose all of
-- that, so intention is its own row with its own history and exactly one CURRENT per person.
--
-- unmet_need is a column rather than a derivation because it is a reported national indicator, and
-- an indicator computed on the fly from three other fields is an indicator that quietly changes
-- meaning when one of them does.

CREATE TABLE pct.pct_reproductive_intentions (
    intention_id          UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    subject_cpid          VARCHAR(64) NOT NULL,
    journey_id            VARCHAR(64),
    encounter_id          VARCHAR(64),
    facility_id           UUID,

    recorded_at           TIMESTAMPTZ NOT NULL,
    recorded_by           VARCHAR(128) NOT NULL,

    intention             VARCHAR(32) NOT NULL,
    -- WANTS_PREGNANCY_NOW | WANTS_PREGNANCY_LATER | WANTS_NO_MORE_CHILDREN | UNDECIDED
    -- | DECLINED_TO_STATE | NOT_ASSESSED
    -- DECLINED_TO_STATE is deliberately distinct from NOT_ASSESSED and from UNDECIDED: a woman who
    -- declines to answer has exercised a choice, and recording that as "not asked" erases it while
    -- recording it as "undecided" misrepresents her.
    timeframe_months      INTEGER,
    desired_family_size   INTEGER,

    partner_intention     VARCHAR(32) NOT NULL DEFAULT 'NOT_ASSESSED',
    -- CONCORDANT | DISCORDANT | UNKNOWN | NOT_ASSESSED
    -- Recorded because discordance is a safety-relevant finding — covert contraceptive use is
    -- common where it is, and it changes what may be sent to a shared phone.

    fertility_concern     VARCHAR(32) NOT NULL DEFAULT 'NOT_ASSESSED',
    -- NONE | DIFFICULTY_CONCEIVING | RECURRENT_LOSS | NOT_ASSESSED
    contraception_desired VARCHAR(24) NOT NULL DEFAULT 'NOT_ASSESSED',
    -- YES | NO | UNDECIDED | NOT_ASSESSED
    unmet_need            VARCHAR(24) NOT NULL DEFAULT 'NOT_ASSESSED',
    -- NONE | FOR_SPACING | FOR_LIMITING | NOT_ASSESSED

    counselling_provided  JSONB,
    notes                 TEXT,

    status                VARCHAR(24) NOT NULL DEFAULT 'CURRENT',
    -- CURRENT | SUPERSEDED | ENTERED_IN_ERROR
    superseded_by         UUID REFERENCES pct.pct_reproductive_intentions(intention_id),
    client_offline_id     VARCHAR(128),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_pct_repro_intent_timeframe CHECK (
        intention <> 'WANTS_PREGNANCY_LATER' OR timeframe_months IS NOT NULL)
);

CREATE UNIQUE INDEX uq_pct_repro_intent_current
    ON pct.pct_reproductive_intentions(tenant_id, subject_cpid) WHERE status = 'CURRENT';
CREATE UNIQUE INDEX uq_pct_repro_intent_offline
    ON pct.pct_reproductive_intentions(tenant_id, client_offline_id) WHERE client_offline_id IS NOT NULL;
CREATE INDEX idx_pct_repro_intent_subject
    ON pct.pct_reproductive_intentions(tenant_id, subject_cpid, recorded_at DESC);
