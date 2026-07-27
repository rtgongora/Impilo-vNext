-- The maternal delivery record: the birth as it happened to the MOTHER.
--
-- WHY THIS TABLE EXISTS. pct_newborn_birth_records (V055) is baby-centred — APGAR, birth weight,
-- resuscitation, the newborn examination — anchored on the baby's CPID. Nothing recorded the birth
-- from the mother's side: the third stage of labour, estimated blood loss, the placenta, perineal
-- trauma and its repair, active management of the third stage, her condition immediately after. For
-- a caesarean that record existed as the theatre episode in inpatient-service. For a normal vaginal
-- birth outside theatre — the majority of births — it existed NOWHERE. A woman's account of her own
-- labour survived only if she happened to go to theatre. This table is that missing record.
--
-- RMNP band V430-V459 (V430/431/432 consumed → this is V433). Anchors on pct_pregnancy_episodes
-- (V059, a lower number, so it applies first on a fresh database too — no cross-band dependency).
-- The link to the baby/babies is through the shared pregnancy_episode_id already on the newborn and
-- fetus rows (V061), so a delivery of twins needs no delivery→baby FK here: one delivery, one
-- episode, many babies.
--
-- CAESAREAN IS REFERENCED, NEVER DUPLICATED. A caesarean's operative detail is the theatre episode
-- in inpatient-service. A caesarean delivery row here is a thin continuum anchor — mode plus a
-- theatre_episode_ref — so the mother's continuum records that a caesarean birth happened without
-- re-recording the operation. The constraint below makes a caesarean row without that reference
-- impossible.

CREATE TABLE IF NOT EXISTS pct.pct_delivery_records (
    delivery_record_id      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    -- The SUBJECT of this record is the mother. Deliberately mother_cpid, not the baby's — this is
    -- her clinical event, and it exists whether the baby lived, died, or was a stillbirth.
    mother_cpid             VARCHAR(64) NOT NULL,
    pregnancy_episode_id    UUID
        REFERENCES pct.pct_pregnancy_episodes (pregnancy_episode_id),

    status                  VARCHAR(24) NOT NULL,
    delivered_at            TIMESTAMPTZ NOT NULL,

    labour_onset            VARCHAR(32) NOT NULL,
    delivery_mode           VARCHAR(32) NOT NULL,
    -- For a caesarean, the inpatient theatre episode this delivery corresponds to. The operative
    -- detail lives there, not here.
    theatre_episode_ref     VARCHAR(128),

    place_of_birth          VARCHAR(48),
    facility_id             UUID,
    birth_attendant         VARCHAR(128),
    attendant_cadre         VARCHAR(48),

    -- Third stage of labour.
    amtsl_given             BOOLEAN,
    uterotonic_given        VARCHAR(48),
    placenta_delivery       VARCHAR(32),
    placenta_complete       BOOLEAN,
    estimated_blood_loss_ml INTEGER,
    pph_suspected           BOOLEAN NOT NULL DEFAULT FALSE,

    -- Perineum.
    perineal_outcome        VARCHAR(24),
    perineal_repair         TEXT,

    immediate_maternal_condition VARCHAR(48),
    babies_delivered        SMALLINT,

    -- CC-5 anchoring.
    journey_id              UUID,
    encounter_ref           VARCHAR(128),

    recorded_by             VARCHAR(128) NOT NULL,
    recorded_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by              VARCHAR(128),
    updated_at              TIMESTAMPTZ,
    client_offline_id       VARCHAR(128),

    CONSTRAINT chk_pct_delivery_status CHECK (
        status IN ('RECORDED', 'AMENDED', 'ENTERED_IN_ERROR')),

    CONSTRAINT chk_pct_delivery_onset CHECK (
        labour_onset IN ('SPONTANEOUS', 'INDUCED', 'AUGMENTED', 'NO_LABOUR_PRELABOUR_CS')),

    CONSTRAINT chk_pct_delivery_mode CHECK (
        delivery_mode IN ('SPONTANEOUS_VAGINAL', 'ASSISTED_VACUUM', 'ASSISTED_FORCEPS',
                          'BREECH_VAGINAL', 'CAESAREAN')),

    -- A caesarean row is a pointer to the theatre episode, never a re-recording of it.
    CONSTRAINT chk_pct_delivery_caesarean_references_theatre CHECK (
        delivery_mode <> 'CAESAREAN' OR theatre_episode_ref IS NOT NULL),

    -- ...and a vaginal birth has no theatre episode to reference — a theatre ref on a vaginal row is
    -- a data error that would double-count against inpatient.
    CONSTRAINT chk_pct_delivery_vaginal_no_theatre CHECK (
        delivery_mode = 'CAESAREAN' OR theatre_episode_ref IS NULL),

    CONSTRAINT chk_pct_delivery_placenta_values CHECK (
        placenta_delivery IS NULL
        OR placenta_delivery IN ('SPONTANEOUS', 'CONTROLLED_CORD_TRACTION', 'MANUAL_REMOVAL',
                                 'NOT_YET_DELIVERED')),

    CONSTRAINT chk_pct_delivery_perineal_values CHECK (
        perineal_outcome IS NULL
        OR perineal_outcome IN ('INTACT', 'TEAR_FIRST_DEGREE', 'TEAR_SECOND_DEGREE',
                               'TEAR_THIRD_DEGREE', 'TEAR_FOURTH_DEGREE', 'EPISIOTOMY',
                               'EPISIOTOMY_WITH_EXTENSION')),

    -- A third- or fourth-degree tear is an obstetric anal sphincter injury: a serious, repairable
    -- injury with long-term consequences if missed. Recording one without recording its repair is
    -- the retained-fragment failure in another form — the injury is noted and the follow-up lost.
    -- IS NOT DISTINCT FROM would wave a NULL through; the explicit NULL-and-value guard does not.
    CONSTRAINT chk_pct_delivery_severe_tear_repaired CHECK (
        perineal_outcome IS NULL
        OR perineal_outcome NOT IN ('TEAR_THIRD_DEGREE', 'TEAR_FOURTH_DEGREE')
        OR (perineal_repair IS NOT NULL AND length(btrim(perineal_repair)) > 0)),

    -- Suspected PPH must carry the blood-loss estimate it was judged on. "She bled" with no number
    -- cannot be triaged, audited, or compared against the 500/1000 ml thresholds.
    CONSTRAINT chk_pct_delivery_pph_has_ebl CHECK (
        pph_suspected IS NOT TRUE OR estimated_blood_loss_ml IS NOT NULL),

    CONSTRAINT chk_pct_delivery_ebl_non_negative CHECK (
        estimated_blood_loss_ml IS NULL OR estimated_blood_loss_ml >= 0),

    CONSTRAINT chk_pct_delivery_babies_positive CHECK (
        babies_delivered IS NULL OR babies_delivered >= 1)
);

-- One delivery event per pregnancy — a pregnancy resolves in one birth, even when it produces more
-- than one baby. A second non-error delivery row for the same episode is a duplicate, not a twin.
CREATE UNIQUE INDEX IF NOT EXISTS uq_pct_delivery_per_pregnancy
    ON pct.pct_delivery_records (tenant_id, pregnancy_episode_id)
    WHERE pregnancy_episode_id IS NOT NULL AND status <> 'ENTERED_IN_ERROR';

-- Store-and-forward idempotency (the V050 precedent): a replayed offline packet returns the
-- existing record rather than recording the birth twice.
CREATE UNIQUE INDEX IF NOT EXISTS uq_pct_delivery_offline
    ON pct.pct_delivery_records (tenant_id, client_offline_id)
    WHERE client_offline_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_pct_delivery_mother
    ON pct.pct_delivery_records (tenant_id, mother_cpid, delivered_at);

COMMENT ON TABLE pct.pct_delivery_records IS
    'The birth from the mother''s side: mode, third stage, blood loss, perineum. The record a normal vaginal birth outside theatre never had. Caesareans reference the inpatient theatre episode, never duplicate it.';
