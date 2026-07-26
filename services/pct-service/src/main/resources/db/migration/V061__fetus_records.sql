-- The fetus record: findings about a fetus, and the link from a fetus to the baby it became.
--
-- WHY A SEPARATE TABLE RATHER THAN COLUMNS ON THE PREGNANCY. Discordant outcomes. In a twin
-- pregnancy one baby may be liveborn and the other stillborn, one may be growth-restricted and the
-- other not, one may be cephalic and the other breech. A pregnancy-level outcome column physically
-- cannot express that, and a system that cannot express it will record the pregnancy as whichever
-- outcome was entered last — which is how a stillborn twin disappears from a register.
--
-- THIS ROW HAS NO IDENTITY COLUMN, AND THAT IS THE POINT.
--
-- There is no subject_cpid, no fetus_cpid, no provisional_identity_ref, and no nullable placeholder
-- for one. A column capable of holding an identity is eventually filled by something: a well-meaning
-- integration, a form that needed a key, a migration that wanted a join. The only person this row is
-- about is the pregnant woman, and ClinicalAccessGuard is called with mother_cpid for every read and
-- write of it.
--
-- Identity begins at pct_newborn_birth_records, which is keyed on a real CPID minted by VITO after
-- birth. Before birth there is no person to register, and the record must be structurally incapable
-- of claiming otherwise.
--
-- Consequences that follow, and which belong in the SHR consumer rather than here: a fetus maps to
-- a FHIR Observation or Condition ON THE MOTHER and must never create a Patient; no fetus row is
-- ever emitted to UBOMI; nothing here participates in a VITO identity flow.
--
-- MATCHING IS RECORDED, NEVER INFERRED. At birth, which fetus became which baby is a clinical
-- assertion someone makes, not an arithmetic the system performs. For a singleton it is
-- unambiguous. For a multiple pregnancy it is not — birth order is the usual basis but is not
-- always the labelling used antenatally, and a mismatched twin is one of the few clinical errors
-- that cannot be corrected later, because the growth history, the anomaly findings and the
-- Doppler results follow the wrong child for life. So the match carries its basis, its confidence,
-- its time and its author, and the CHECK requires all four together.

CREATE TABLE pct.pct_fetuses (
    fetus_id                 UUID PRIMARY KEY,
    tenant_id                UUID NOT NULL,
    pregnancy_episode_id     UUID NOT NULL
                                 REFERENCES pct.pct_pregnancy_episodes(pregnancy_episode_id),

    -- THE ONLY PERSON THIS ROW IS ABOUT. See the header: there is deliberately no identity column
    -- for the fetus, not even a nullable one.
    mother_cpid              VARCHAR(64) NOT NULL,

    -- The obstetric convention: twin A, twin B. Assigned antenatally and kept stable, because
    -- every scan report and growth chart refers to it.
    fetus_label              VARCHAR(4) NOT NULL,

    presentation             VARCHAR(24),
    -- CEPHALIC | BREECH | TRANSVERSE | OBLIQUE | COMPOUND | UNKNOWN
    presentation_assessed_on DATE,
    lie                      VARCHAR(24),
    placental_site           VARCHAR(48),
    amniotic_sac_ref         VARCHAR(24),

    sex_on_ultrasound        VARCHAR(24) NOT NULL DEFAULT 'NOT_ASSESSED',
    -- MALE | FEMALE | INDETERMINATE | NOT_ASSESSED | NOT_DISCLOSED
    -- NOT_DISCLOSED is distinct from NOT_ASSESSED on purpose: it records that the sex was
    -- determined and deliberately not shared, which is a different clinical and social fact.

    anomaly_screening_status VARCHAR(32) NOT NULL DEFAULT 'NOT_ASSESSED',
    -- NOT_ASSESSED | NORMAL | ANOMALY_SUSPECTED | ANOMALY_CONFIRMED | INCOMPLETE
    anomalies_suspected      JSONB,
    estimated_fetal_weight_grams INTEGER,
    efw_assessed_on          DATE,

    viability_status         VARCHAR(32) NOT NULL DEFAULT 'NOT_ASSESSED',
    -- ALIVE | DEMISE_IN_UTERO | VANISHED | NOT_ASSESSED
    demise_confirmed_on      DATE,
    demise_gestational_age_days INTEGER,

    -- Per-fetus outcome. This is what makes a discordant twin pregnancy recordable.
    outcome                  VARCHAR(32) NOT NULL DEFAULT 'ONGOING',
    -- LIVE_BIRTH | STILLBIRTH_ANTEPARTUM | STILLBIRTH_INTRAPARTUM | MISCARRIAGE | TERMINATION
    -- | ECTOPIC | MOLAR | VANISHED | ONGOING | UNKNOWN
    outcome_recorded_at      TIMESTAMPTZ,
    delivered_at             TIMESTAMPTZ,
    birth_order              INTEGER,          -- order of DELIVERY; may differ from the label
    gestational_age_days_at_outcome INTEGER,
    birth_weight_grams       INTEGER,

    -- ── fetus → newborn matching ──────────────────────────────────────────────────────────
    newborn_birth_record_id  UUID REFERENCES pct.pct_newborn_birth_records(birth_record_id),
    match_basis              VARCHAR(32),
    -- SINGLETON_UNAMBIGUOUS | DELIVERY_ORDER | ATTENDANT_ASSERTION | GENETIC | NOT_MATCHED
    match_confidence         VARCHAR(24),      -- CERTAIN | PROBABLE | UNCERTAIN
    matched_at               TIMESTAMPTZ,
    matched_by               VARCHAR(128),
    match_notes              TEXT,

    client_offline_id        VARCHAR(128),
    recorded_by              VARCHAR(128) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_pct_fetus_label UNIQUE (pregnancy_episode_id, fetus_label),

    -- One birth record may be claimed by at most one fetus. Two fetuses matched to one baby
    -- double-counts a birth and orphans a twin. Postgres permits many NULLs in a unique
    -- constraint, which is exactly the semantics wanted: unborn and unmatched fetuses are
    -- unconstrained.
    CONSTRAINT uq_pct_fetus_newborn UNIQUE (newborn_birth_record_id),

    -- A match may only exist against an outcome that produced a baby to match.
    CONSTRAINT chk_pct_fetus_newborn_outcome CHECK (
        newborn_birth_record_id IS NULL
        OR outcome IN ('LIVE_BIRTH', 'STILLBIRTH_ANTEPARTUM', 'STILLBIRTH_INTRAPARTUM')),

    -- All four together or none. A match with no stated basis, time and author cannot be reviewed,
    -- and a mis-matched twin cannot be corrected later.
    CONSTRAINT chk_pct_fetus_match_provenance CHECK (
        newborn_birth_record_id IS NULL
        OR (match_basis IS NOT NULL AND match_confidence IS NOT NULL
            AND matched_at IS NOT NULL AND matched_by IS NOT NULL)),

    CONSTRAINT chk_pct_fetus_demise CHECK (
        viability_status <> 'DEMISE_IN_UTERO' OR demise_confirmed_on IS NOT NULL)
);

CREATE INDEX idx_pct_fetus_episode ON pct.pct_fetuses(pregnancy_episode_id, fetus_label);
CREATE INDEX idx_pct_fetus_mother  ON pct.pct_fetuses(tenant_id, mother_cpid);
CREATE UNIQUE INDEX uq_pct_fetus_offline
    ON pct.pct_fetuses(tenant_id, client_offline_id) WHERE client_offline_id IS NOT NULL;

COMMENT ON TABLE pct.pct_fetuses IS
    'Fetus-specific findings and outcomes within a pregnancy. Has NO identity column by design: '
    'a fetus is not a registered person, identity begins at pct_newborn_birth_records, and a '
    'column able to hold an identity would eventually be filled.';

COMMENT ON COLUMN pct.pct_fetuses.outcome IS
    'Per-fetus, so a twin pregnancy with one live birth and one stillbirth is recordable. A '
    'pregnancy-level outcome column cannot express that.';

-- ──────────────────────────────────────────────────────────────────────────────────────────
-- Attach the existing newborn and labour records to the pregnancy they belong to.
--
-- STRICTLY ADDITIVE. Every column is nullable, every constraint is NOT VALID, nothing is renamed,
-- dropped, tightened or rewritten. V055 and V056 are live and live-proven; a destructive change to
-- either would be a regression in the one part of this domain that already works.
--
-- NULL means "not linked", never "no pregnancy". Nothing may read the absence of a link as the
-- absence of a pregnancy.

ALTER TABLE pct.pct_newborn_birth_records
    ADD COLUMN IF NOT EXISTS pregnancy_episode_id UUID,
    ADD COLUMN IF NOT EXISTS fetus_id             UUID;

ALTER TABLE pct.pct_newborn_birth_records
    ADD CONSTRAINT fk_newborn_pregnancy FOREIGN KEY (pregnancy_episode_id)
        REFERENCES pct.pct_pregnancy_episodes(pregnancy_episode_id) NOT VALID,
    ADD CONSTRAINT fk_newborn_fetus FOREIGN KEY (fetus_id)
        REFERENCES pct.pct_fetuses(fetus_id) NOT VALID;

CREATE INDEX IF NOT EXISTS idx_pct_newborn_pregnancy
    ON pct.pct_newborn_birth_records(pregnancy_episode_id) WHERE pregnancy_episode_id IS NOT NULL;

ALTER TABLE pct.pct_labour_observations ADD COLUMN IF NOT EXISTS pregnancy_episode_id UUID;
ALTER TABLE pct.pct_partograph_sessions ADD COLUMN IF NOT EXISTS pregnancy_episode_id UUID;
ALTER TABLE pct.pct_ctg_sessions        ADD COLUMN IF NOT EXISTS pregnancy_episode_id UUID;

ALTER TABLE pct.pct_labour_observations
    ADD CONSTRAINT fk_labour_obs_pregnancy FOREIGN KEY (pregnancy_episode_id)
        REFERENCES pct.pct_pregnancy_episodes(pregnancy_episode_id) NOT VALID;
ALTER TABLE pct.pct_partograph_sessions
    ADD CONSTRAINT fk_partograph_pregnancy FOREIGN KEY (pregnancy_episode_id)
        REFERENCES pct.pct_pregnancy_episodes(pregnancy_episode_id) NOT VALID;
ALTER TABLE pct.pct_ctg_sessions
    ADD CONSTRAINT fk_ctg_pregnancy FOREIGN KEY (pregnancy_episode_id)
        REFERENCES pct.pct_pregnancy_episodes(pregnancy_episode_id) NOT VALID;

CREATE INDEX IF NOT EXISTS idx_pct_labour_obs_pregnancy
    ON pct.pct_labour_observations(pregnancy_episode_id) WHERE pregnancy_episode_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pct_partograph_pregnancy
    ON pct.pct_partograph_sessions(pregnancy_episode_id) WHERE pregnancy_episode_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pct_ctg_pregnancy
    ON pct.pct_ctg_sessions(pregnancy_episode_id) WHERE pregnancy_episode_id IS NOT NULL;

COMMENT ON COLUMN pct.pct_newborn_birth_records.pregnancy_episode_id IS
    'Nullable and additive. NULL means not linked, never "no pregnancy" — most existing rows '
    'predate the pregnancy register and can never be linked.';
