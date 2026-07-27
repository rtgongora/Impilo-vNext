-- Preconception care, and fertility care. The two things that happen before a pregnancy exists.
--
-- RMNP band V430-V459. No cross-band references: both tables here stand alone, and the optional link
-- to a pregnancy episode is to V059, which is numerically lower and so applies first on a fresh
-- database as well as on the estate.

-- ---------------------------------------------------------------------------------------------
-- Preconception care plan
--
-- WHY THIS IS NOT AN ANTENATAL VISIT HELD EARLY. Almost everything of value in preconception care
-- has to happen before conception to work at all. The neural tube closes at about 28 days, which is
-- around the time a woman first misses a period — so folic acid started when the pregnancy test is
-- positive has already missed the thing it was for. The same is true of stopping a teratogenic
-- medicine, of optimising diabetes control, and of rubella immunisation.
--
-- The table therefore records dates rather than flags. A boolean "folic acid given" cannot tell you
-- whether it was given in time, and a plan that reports 100% completion while every action happened
-- too late is worse than one that reports honestly.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pct.pct_preconception_plans (
    preconception_plan_id       UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    subject_cpid                VARCHAR(64) NOT NULL,

    status                      VARCHAR(24) NOT NULL,
    opened_on                   DATE NOT NULL,
    closed_on                   DATE,
    closure_reason              VARCHAR(48),

    -- Folic acid. The date matters, not the fact.
    folic_acid_started_on       DATE,
    folic_acid_dose_mg          NUMERIC(5,2),
    -- 5 mg rather than 0.4 mg where there is a previous neural tube defect, diabetes, obesity, or
    -- an enzyme-inducing anticonvulsant. Recorded because the higher dose is the one most often
    -- missed, and the women who need it are identifiable in advance.
    folic_acid_high_dose_reason VARCHAR(64),

    -- The single highest-value preconception act: finding a teratogenic medicine BEFORE she
    -- conceives. A first-class column, not a note, because a note cannot be counted or queried and
    -- the whole point is to find these women before they are pregnant.
    teratogenic_medication_review_on   DATE,
    teratogenic_medication_found       VARCHAR(24),
    teratogenic_medication_detail      TEXT,
    teratogenic_medication_action      VARCHAR(48),

    chronic_conditions_reviewed_on     DATE,
    diabetes_control_optimised         VARCHAR(24),
    hypertension_control_optimised     VARCHAR(24),

    rubella_immunity_status     VARCHAR(24),
    rubella_vaccinated_on       DATE,
    tetanus_status              VARCHAR(24),

    hiv_status_known            VARCHAR(24),
    hiv_art_optimised           VARCHAR(24),
    partner_hiv_status_known    VARCHAR(24),

    substance_use_reviewed_on   DATE,
    alcohol_use                 VARCHAR(24),
    tobacco_use                 VARCHAR(24),

    bmi                         NUMERIC(5,2),
    anaemia_screened_on         DATE,
    haemoglobin_g_dl            NUMERIC(4,1),

    genetic_or_family_risk      VARCHAR(24),
    genetic_risk_detail         TEXT,

    -- Set when a pregnancy follows, so the plan can be read against what actually happened. NULL
    -- means not linked, never "no pregnancy".
    resulting_pregnancy_episode_id UUID
        REFERENCES pct.pct_pregnancy_episodes (pregnancy_episode_id),

    journey_id                  UUID,
    encounter_ref               VARCHAR(128),
    facility_id                 UUID,
    recorded_by                 VARCHAR(128) NOT NULL,
    recorded_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                  VARCHAR(128),
    updated_at                  TIMESTAMPTZ,
    client_offline_id           VARCHAR(128),

    CONSTRAINT chk_pct_preconception_status CHECK (
        status IN ('ACTIVE', 'COMPLETED', 'SUPERSEDED', 'CLOSED', 'ENTERED_IN_ERROR')),

    CONSTRAINT chk_pct_preconception_closed CHECK (
        status NOT IN ('COMPLETED', 'CLOSED')
        OR (closed_on IS NOT NULL AND closure_reason IS NOT NULL)),

    CONSTRAINT chk_pct_preconception_closed_after_open CHECK (
        closed_on IS NULL OR closed_on >= opened_on),

    -- A teratogenic medicine found and no action recorded is the one outcome this column exists to
    -- prevent. Finding it and doing nothing is worse than not looking, because the record now says
    -- somebody looked.
    CONSTRAINT chk_pct_preconception_teratogen_action CHECK (
        teratogenic_medication_found IS DISTINCT FROM 'FOUND'
        OR (teratogenic_medication_action IS NOT NULL
            AND teratogenic_medication_detail IS NOT NULL)),

    -- A review cannot report a finding it did not make.
    CONSTRAINT chk_pct_preconception_teratogen_reviewed CHECK (
        teratogenic_medication_found IS NULL
        OR teratogenic_medication_review_on IS NOT NULL),

    CONSTRAINT chk_pct_preconception_teratogen_values CHECK (
        teratogenic_medication_found IS NULL
        OR teratogenic_medication_found IN ('FOUND', 'NONE_FOUND', 'NOT_ASSESSED')),

    -- The high dose needs its reason, or nobody can tell a considered 5 mg from a typo.
    CONSTRAINT chk_pct_preconception_folate_high_dose CHECK (
        folic_acid_dose_mg IS NULL
        OR folic_acid_dose_mg <= 1.0
        OR folic_acid_high_dose_reason IS NOT NULL),

    CONSTRAINT chk_pct_preconception_folate_dated CHECK (
        folic_acid_dose_mg IS NULL OR folic_acid_started_on IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pct_preconception_active
    ON pct.pct_preconception_plans (tenant_id, subject_cpid)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX IF NOT EXISTS uq_pct_preconception_offline
    ON pct.pct_preconception_plans (tenant_id, client_offline_id)
    WHERE client_offline_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_pct_preconception_subject
    ON pct.pct_preconception_plans (tenant_id, subject_cpid, status);


-- ---------------------------------------------------------------------------------------------
-- Fertility care episode
--
-- INFERTILITY IS A COUPLE'S DIAGNOSIS, NOT A WOMAN'S. Roughly half of infertility involves a male
-- factor, and in much of the world the investigation still begins and often ends with the woman.
-- Three deliberate consequences in this schema:
--
--   * The episode is anchored on whoever presented, of either sex — `subject_cpid`, not a maternal
--     column. A woman-only table would have made the assumption structural.
--   * `partner_cpid` is optional and carries its own consent column. A partner is a person with
--     their own record and their own say; nothing here reaches into it because a couple attended
--     together.
--   * `male_factor_assessed` is a first-class column, so "was he investigated at all" is a question
--     the data can answer. Left as free text it would be unanswerable, and unanswerable is how it
--     stays unexamined.
--
-- The 12-month threshold (6 months from age 35) is a trigger for INVESTIGATION, not a diagnosis.
-- The column is named accordingly.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pct.pct_fertility_episodes (
    fertility_episode_id        UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    subject_cpid                VARCHAR(64) NOT NULL,

    partner_cpid                VARCHAR(64),
    partner_linkage_consent     VARCHAR(24),

    status                      VARCHAR(24) NOT NULL,
    opened_on                   DATE NOT NULL,
    closed_on                   DATE,
    outcome                     VARCHAR(32),

    months_trying               SMALLINT,
    investigation_threshold_met VARCHAR(24),
    primary_or_secondary        VARCHAR(16),

    -- Investigation, per partner. Both nullable, and both meaning "not assessed" when null.
    female_factor_assessed      VARCHAR(24),
    female_factor_findings      TEXT,
    male_factor_assessed        VARCHAR(24),
    male_factor_findings        TEXT,
    semen_analysis_done_on      DATE,
    tubal_assessment_done_on    DATE,
    ovulation_assessment_done_on DATE,

    referred_on                 DATE,
    referred_to_facility_id     UUID,

    resulting_pregnancy_episode_id UUID
        REFERENCES pct.pct_pregnancy_episodes (pregnancy_episode_id),

    journey_id                  UUID,
    encounter_ref               VARCHAR(128),
    facility_id                 UUID,
    recorded_by                 VARCHAR(128) NOT NULL,
    recorded_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                  VARCHAR(128),
    updated_at                  TIMESTAMPTZ,
    client_offline_id           VARCHAR(128),

    CONSTRAINT chk_pct_fertility_status CHECK (
        status IN ('ACTIVE', 'REFERRED', 'CLOSED', 'ENTERED_IN_ERROR')),

    CONSTRAINT chk_pct_fertility_closed CHECK (
        status <> 'CLOSED' OR (closed_on IS NOT NULL AND outcome IS NOT NULL)),

    CONSTRAINT chk_pct_fertility_closed_after_open CHECK (
        closed_on IS NULL OR closed_on >= opened_on),

    CONSTRAINT chk_pct_fertility_assessed_values CHECK (
        (female_factor_assessed IS NULL
         OR female_factor_assessed IN ('ASSESSED', 'NOT_ASSESSED', 'DECLINED'))
        AND (male_factor_assessed IS NULL
         OR male_factor_assessed IN ('ASSESSED', 'NOT_ASSESSED', 'DECLINED'))),

    -- A partner's record may only be linked with the partner's recorded consent. Naming a partner
    -- is not the same as being permitted to reach into their chart, and the couple attending
    -- together is not consent.
    CONSTRAINT chk_pct_fertility_partner_consent CHECK (
        partner_cpid IS NULL
        OR partner_linkage_consent IN ('GIVEN', 'DECLINED', 'NOT_ASKED')),

    CONSTRAINT chk_pct_fertility_partner_linked_only_with_consent CHECK (
        partner_cpid IS NULL OR partner_linkage_consent IS NOT NULL),

    -- Findings without an assessment is a claim nobody made.
    --
    -- IS NOT DISTINCT FROM, not =. A CHECK constraint rejects only FALSE, and `NULL = 'ASSESSED'`
    -- evaluates to NULL, so the plain comparison ACCEPTS exactly the row it was written to refuse:
    -- findings recorded against an assessment column nobody filled in. Caught by probing this
    -- constraint against a live database rather than reading it — it looks correct on the page.
    CONSTRAINT chk_pct_fertility_findings_need_assessment CHECK (
        (female_factor_findings IS NULL
         OR female_factor_assessed IS NOT DISTINCT FROM 'ASSESSED')
        AND (male_factor_findings IS NULL
         OR male_factor_assessed IS NOT DISTINCT FROM 'ASSESSED')),

    CONSTRAINT chk_pct_fertility_months_non_negative CHECK (
        months_trying IS NULL OR months_trying >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_pct_fertility_active
    ON pct.pct_fertility_episodes (tenant_id, subject_cpid)
    WHERE status IN ('ACTIVE', 'REFERRED');

CREATE UNIQUE INDEX IF NOT EXISTS uq_pct_fertility_offline
    ON pct.pct_fertility_episodes (tenant_id, client_offline_id)
    WHERE client_offline_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_pct_fertility_subject
    ON pct.pct_fertility_episodes (tenant_id, subject_cpid, status);

-- The audit question this table exists to make answerable: of the couples investigated, in how many
-- was the male partner assessed at all.
CREATE INDEX IF NOT EXISTS ix_pct_fertility_male_factor
    ON pct.pct_fertility_episodes (tenant_id, male_factor_assessed)
    WHERE status IN ('ACTIVE', 'REFERRED', 'CLOSED');

COMMENT ON TABLE pct.pct_preconception_plans IS
    'Care before conception. Dates rather than flags, because almost all of it only works if it happened in time.';
COMMENT ON TABLE pct.pct_fertility_episodes IS
    'Fertility care for whoever presented. Infertility is a couple''s diagnosis; male factor is a first-class column.';
